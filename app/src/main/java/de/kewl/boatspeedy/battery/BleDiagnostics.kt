package de.kewl.boatspeedy.battery

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock

/** Ein im Diagnose-Scan gefundenes Gerät – ungefiltert, also auch fremde BMS. */
data class DiagDevice(
    val name: String?,
    val address: String,
    val rssi: Int,
    val services: List<String>,
    val manufacturer: String?,
)

/**
 * Werkzeug für Fehlerberichte zu unbekannten BMS: scannt **ohne Filter**, verbindet sich,
 * listet den kompletten GATT-Baum auf und probiert auf jedem Notify-Kanal die Befehle
 * aller unterstützten Protokolle durch. Alles Ein- und Ausgehende landet als Hex im
 * Protokoll, das der Nutzer anschließend an eine Issue hängen kann.
 *
 * Bewusst eigenständig neben [BatteryConnection]: hier wird nichts geparst und nichts
 * gefiltert, damit auch Geräte auftauchen, die keiner der bekannten Typen sind.
 */
@SuppressLint("MissingPermission")
class BleDiagnostics(private val context: Context) {

    private companion object {
        const val SCAN_MS = 12000L
        const val SETTLE_MS = 600L      // Wartezeit nach dem Aktivieren der Notifications
        const val CMD_GAP_MS = 800L     // Abstand zwischen zwei Probe-Befehlen
        const val TAIL_MS = 2000L       // Nachlauf, damit späte Antworten noch ankommen
        const val LISTEN_MS = 3000L     // Horchzeit bei Diensten ohne Schreibkanal
        const val CCCD_TIMEOUT_MS = 2500L
        const val CONNECT_TIMEOUT_MS = 20000L
        /** TI-OAD (Firmware-Update) – hier wird nichts gesucht und schon gar nicht geschrieben. */
        const val OAD_SERVICE_PREFIX = "f000ffc0"
    }

    private val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter get() = manager.adapter
    private val main = Handler(Looper.getMainLooper())
    private val token = Any()
    private val connectToken = Any()   // eigener Token, damit das Verbindungs-Zeitlimit
                                       // gezielt abgeraeumt werden kann

    private val log = StringBuilder()
    private var startedAt = 0L
    private var onLine: (String) -> Unit = {}
    private var onDone: () -> Unit = {}
    private var gatt: BluetoothGatt? = null
    private var finished = true

    /** Bisheriges Protokoll als Text (für Anzeige und Datei). */
    val report: String get() = log.toString()

    /* ----------------------------- Scan ----------------------------- */

    private var scanning = false
    private val found = LinkedHashMap<String, DiagDevice>()

    /** Ungefilterter Scan: zeigt jedes BLE-Gerät in Reichweite. false = Bluetooth aus. */
    fun startScan(onResults: (List<DiagDevice>) -> Unit, onStopped: () -> Unit): Boolean {
        val ad = adapter
        if (ad == null || !ad.isEnabled) return false
        stopScan()
        found.clear()
        onResults(emptyList())
        scanning = true
        val cb = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val rec = result.scanRecord
                val mfg = rec?.manufacturerSpecificData?.let { d ->
                    (0 until d.size()).joinToString(" ") { i ->
                        "0x%04X:%s".format(d.keyAt(i), hex(d.valueAt(i)))
                    }
                }?.takeIf { it.isNotBlank() }
                found[result.device.address] = DiagDevice(
                    name = result.device.name ?: rec?.deviceName,
                    address = result.device.address,
                    rssi = result.rssi,
                    services = rec?.serviceUuids?.map { it.uuid.toString() }.orEmpty(),
                    manufacturer = mfg,
                )
                onResults(found.values.sortedByDescending { it.rssi })
            }

            override fun onScanFailed(errorCode: Int) {
                scanning = false
                onStopped()
            }
        }
        scanCb = cb
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        ad.bluetoothLeScanner?.startScan(null, settings, cb)
        main.postAtTime({ stopScan(); onStopped() }, token, SystemClock.uptimeMillis() + SCAN_MS)
        return true
    }

    private var scanCb: ScanCallback? = null

    fun stopScan() {
        if (!scanning) return
        scanning = false
        main.removeCallbacksAndMessages(token)
        scanCb?.let { cb -> runCatching { adapter?.bluetoothLeScanner?.stopScan(cb) } }
        scanCb = null
    }

    /* --------------------------- Diagnose --------------------------- */

    /** Verbindet, liest den GATT-Baum aus und probiert alle bekannten Protokolle durch. */
    fun run(device: DiagDevice, onLine: (String) -> Unit, onDone: () -> Unit) {
        this.onLine = onLine
        this.onDone = onDone
        finished = false
        // Zustand zurücksetzen – dieselbe Instanz wird für mehrere Läufe benutzt.
        probes = emptyList()
        svcIndex = -1
        notifyIndex = 0
        phase = 0
        log.setLength(0)
        startedAt = SystemClock.elapsedRealtime()

        line("BoatSpeedy BLE-Diagnose")
        line("App ${de.kewl.boatspeedy.BuildConfig.VERSION_NAME} · Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})")
        line("Gerät ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
        line("")
        line("Batterie: ${device.name ?: "(kein Name)"} · ${device.address} · RSSI ${device.rssi} dBm")
        line("Beworbene Services: ${device.services.ifEmpty { listOf("(keine)") }.joinToString(", ")}")
        line("Manufacturer Data: ${device.manufacturer ?: "(keine)"}")
        val guessed = BmsType.entries.firstOrNull { t ->
            BmsProtocol.of(t).serviceUuid.toString() in device.services
        }
        line("Erkannter Typ: ${guessed?.display ?: "(keiner – unbekanntes Modul)"}")
        line("")

        val dev = runCatching { adapter?.getRemoteDevice(device.address) }.getOrNull()
        if (dev == null) {
            line("FEHLER: Adresse nicht auflösbar.")
            finish()
            return
        }
        line("Verbinde …")
        gatt = dev.connectGatt(context, false, callback, android.bluetooth.BluetoothDevice.TRANSPORT_LE)
        main.postAtTime({
            if (!finished) { line("FEHLER: Zeitüberschreitung beim Verbinden."); finish() }
        }, connectToken, SystemClock.uptimeMillis() + CONNECT_TIMEOUT_MS)
    }

    /** Bricht eine laufende Diagnose ab und gibt die Verbindung frei. */
    fun cancel() {
        if (finished) return
        line("Abgebrochen.")
        finish()
    }

    private fun finish() {
        if (finished) return
        finished = true
        main.removeCallbacksAndMessages(token)
        main.removeCallbacksAndMessages(connectToken)
        gatt?.let { runCatching { it.disconnect(); it.close() } }
        gatt = null
        line("")
        line("Ende der Diagnose.")
        main.post { onDone() }
    }

    private fun line(text: String) {
        val t = if (startedAt == 0L) 0f else (SystemClock.elapsedRealtime() - startedAt) / 1000f
        val entry = if (text.isEmpty()) "" else "[%7.3f] %s".format(t, text)
        log.append(entry).append('\n')
        main.post { onLine(entry) }
    }

    /* ------------------------- GATT-Callback ------------------------- */

    /**
     * Ein Dienst mit allen Kanaelen, ueber die er sprechen koennte. Es werden alle
     * Notify-Kanaele gleichzeitig abonniert und danach ueber *jeden* Schreibkanal die
     * Befehle geschickt – bei Modulen mit getrennten Schreib-/Lesekanaelen (etwa
     * FFE2 raus, FFE3 rein) faende man die Antwort sonst nie.
     */
    private data class ServiceProbe(
        val uuid: String,
        val notifies: List<BluetoothGattCharacteristic>,
        val writes: List<BluetoothGattCharacteristic>,
    )

    private var probes: List<ServiceProbe> = emptyList()
    private var svcIndex = -1
    private var notifyIndex = 0
    private var phase = 0            // erhoeht sich bei jedem Schritt; entwertet alte Rueckfalluhren

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    // Verbindungs-Zeitlimit abraeumen, sonst bricht es den laufenden
                    // Durchlauf spaeter mitten im Probieren ab.
                    main.removeCallbacksAndMessages(connectToken)
                    line("Verbunden (status $status). Frage MTU an …")
                    if (!g.requestMtu(517)) g.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    if (!finished) { line("Verbindung getrennt (status $status)."); finish() }
                }
            }
        }

        override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
            line("MTU: $mtu (status $status)")
            g.discoverServices()
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            if (finished) return
            if (status != BluetoothGatt.GATT_SUCCESS) {
                line("FEHLER: Dienstsuche fehlgeschlagen (status $status)."); finish(); return
            }
            line("")
            line("--- GATT-Baum ---")
            val list = mutableListOf<ServiceProbe>()
            for (s in g.services) {
                val isOad = s.uuid.toString().startsWith(OAD_SERVICE_PREFIX)
                line("Service ${s.uuid}" + if (isOad) "   (Firmware-Update, wird uebersprungen)" else "")
                val notifies = mutableListOf<BluetoothGattCharacteristic>()
                val writes = mutableListOf<BluetoothGattCharacteristic>()
                for (c in s.characteristics) {
                    val p = c.properties
                    val props = buildList {
                        if (p and BluetoothGattCharacteristic.PROPERTY_READ != 0) add("READ")
                        if (p and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) add("WRITE")
                        if (p and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0) add("WRITE_NR")
                        if (p and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) add("NOTIFY")
                        if (p and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0) add("INDICATE")
                    }
                    val cccd = c.getDescriptor(BmsProtocol.CCCD) != null
                    line("  Char ${c.uuid}  [${props.joinToString("|").ifEmpty { "-" }}]${if (cccd) " +CCCD" else ""}")
                    if (isOad) continue
                    if (p and (BluetoothGattCharacteristic.PROPERTY_NOTIFY or
                            BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0
                    ) notifies.add(c)
                    if (p and (BluetoothGattCharacteristic.PROPERTY_WRITE or
                            BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0
                    ) writes.add(c)
                }
                if (notifies.isNotEmpty()) list.add(ServiceProbe(s.uuid.toString(), notifies, writes))
            }
            probes = list
            line("")
            if (probes.isEmpty()) {
                line("Kein Notify-Kanal gefunden – von diesem Geraet ist ohne weitere Angaben nichts zu holen.")
                finish(); return
            }
            line("--- Probe-Durchlauf (${probes.size} Dienst(e)) ---")
            nextService()
        }

        override fun onDescriptorWrite(g: BluetoothGatt, d: BluetoothGattDescriptor, status: Int) {
            if (finished) return
            line("  CCCD ${short(d.characteristic.uuid.toString())} aktiviert (status $status)")
            notifyIndex++
            enableNextNotify()
        }

        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            line("  <-- ${short(characteristic.uuid.toString())}  ${hex(value)}")
        }

        override fun onCharacteristicWrite(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            if (status != BluetoothGatt.GATT_SUCCESS) line("  (Schreiben quittiert mit status $status)")
        }
    }

    private fun nextService() {
        svcIndex++
        val g = gatt
        if (finished || g == null) return
        if (svcIndex >= probes.size) {
            main.postAtTime({ finish() }, token, SystemClock.uptimeMillis() + TAIL_MS)
            return
        }
        val p = probes[svcIndex]
        line("")
        line("Dienst ${svcIndex + 1}/${probes.size}: ${short(p.uuid)} · " +
            "Notify ${p.notifies.joinToString(", ") { short(it.uuid.toString()) }} · " +
            "Write ${p.writes.joinToString(", ") { short(it.uuid.toString()) }.ifEmpty { "(keiner)" }}")
        notifyIndex = 0
        enableNextNotify()
    }

    /** Abonniert die Notify-Kanaele nacheinander – Android erlaubt nur eine GATT-Operation zugleich. */
    private fun enableNextNotify() {
        val g = gatt ?: return
        if (finished) return
        val p = probes.getOrNull(svcIndex) ?: return
        val seq = ++phase
        if (notifyIndex >= p.notifies.size) {
            main.postAtTime({ sendProbes(seq) }, token, SystemClock.uptimeMillis() + SETTLE_MS)
            return
        }
        val ch = p.notifies[notifyIndex]
        g.setCharacteristicNotification(ch, true)
        val cccd = ch.getDescriptor(BmsProtocol.CCCD)
        if (cccd == null) {
            line("  (${short(ch.uuid.toString())} ohne CCCD – Notifications nicht anforderbar)")
            notifyIndex++
            enableNextNotify()
            return
        }
        val value = if (ch.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0)
            BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        else BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
        runCatching { g.writeDescriptor(cccd, value) }
        // Rueckfalluhr, falls die Quittung ausbleibt.
        main.postAtTime({
            if (!finished && phase == seq) { notifyIndex++; enableNextNotify() }
        }, token, SystemClock.uptimeMillis() + CCCD_TIMEOUT_MS)
    }

    /** Schickt ueber jeden Schreibkanal des Dienstes die Poll-Befehle aller bekannten Protokolle. */
    private fun sendProbes(seq: Int) {
        val g = gatt ?: return
        if (finished || phase != seq) return
        val p = probes.getOrNull(svcIndex) ?: return
        if (p.writes.isEmpty()) {
            line("  (kein Schreibkanal – warte nur auf unaufgeforderte Meldungen)")
            main.postAtTime({ nextService() }, token, SystemClock.uptimeMillis() + LISTEN_MS)
            return
        }
        val commands = BmsType.entries.flatMap { t -> BmsProtocol.of(t).pollCommands(0).map { t to it } }
        var at = SystemClock.uptimeMillis()
        for (write in p.writes) {
            at += CMD_GAP_MS
            val w = write
            main.postAtTime({ if (!finished) line("  ueber ${short(w.uuid.toString())}:") }, token, at)
            for ((type, cmd) in commands) {
                at += CMD_GAP_MS
                main.postAtTime({
                    if (finished) return@postAtTime
                    line("  --> ${type.name}: ${hex(cmd)}")
                    val proto = BmsProtocol.of(type)
                    val wt = if (proto.writeNoResponse) BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                    else BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                    runCatching { g.writeCharacteristic(w, cmd, wt) }
                        .onFailure { line("  (Schreiben nicht moeglich: ${it.message})") }
                }, token, at)
            }
        }
        main.postAtTime({ nextService() }, token, at + CMD_GAP_MS)
    }

    private fun short(uuid: String): String =
        if (uuid.length == 36 && uuid.endsWith("-0000-1000-8000-00805f9b34fb"))
            "0x" + uuid.substring(4, 8).uppercase() else uuid

    private fun hex(bytes: ByteArray): String =
        bytes.joinToString(" ") { "%02X".format(it) }
}
