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
        const val CMD_GAP_MS = 900L     // Abstand zwischen zwei Probe-Befehlen
        const val TAIL_MS = 1500L       // Nachlauf, damit späte Antworten noch ankommen
        const val CONNECT_TIMEOUT_MS = 20000L
    }

    private val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter get() = manager.adapter
    private val main = Handler(Looper.getMainLooper())
    private val token = Any()

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
        candidates = emptyList()
        current = -1
        probedFor = -1
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
        }, token, SystemClock.uptimeMillis() + CONNECT_TIMEOUT_MS)
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

    /** Ein Kandidat: ein Notify-Kanal und der dazu passende Schreibkanal. */
    private data class Candidate(
        val service: String,
        val notify: BluetoothGattCharacteristic,
        val write: BluetoothGattCharacteristic?,
    )

    private var candidates: List<Candidate> = emptyList()
    private var current = -1
    private var probedFor = -1

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
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
            val list = mutableListOf<Candidate>()
            for (s in g.services) {
                line("Service ${s.uuid}")
                var notify: BluetoothGattCharacteristic? = null
                var write: BluetoothGattCharacteristic? = null
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
                    if (notify == null && p and (BluetoothGattCharacteristic.PROPERTY_NOTIFY or
                            BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0
                    ) notify = c
                    if (write == null && p and (BluetoothGattCharacteristic.PROPERTY_WRITE or
                            BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0
                    ) write = c
                }
                notify?.let { list.add(Candidate(s.uuid.toString(), it, write)) }
            }
            candidates = list
            line("")
            if (candidates.isEmpty()) {
                line("Kein Notify-Kanal gefunden – von diesem Gerät ist ohne weitere Angaben nichts zu holen.")
                finish(); return
            }
            line("--- Probe-Durchlauf (${candidates.size} Kanal/Kanäle) ---")
            nextCandidate()
        }

        override fun onDescriptorWrite(g: BluetoothGatt, d: BluetoothGattDescriptor, status: Int) {
            if (finished) return
            line("CCCD geschrieben (status $status) – sende Befehle …")
            main.postAtTime({ sendProbes() }, token, SystemClock.uptimeMillis() + SETTLE_MS)
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

    private fun nextCandidate() {
        current++
        val g = gatt
        if (finished || g == null) return
        if (current >= candidates.size) {
            main.postAtTime({ finish() }, token, SystemClock.uptimeMillis() + TAIL_MS)
            return
        }
        val c = candidates[current]
        line("")
        line("Kanal ${current + 1}/${candidates.size}: Service ${short(c.service)}, " +
            "Notify ${short(c.notify.uuid.toString())}, " +
            "Write ${c.write?.uuid?.toString()?.let { short(it) } ?: "(keiner)"}")
        g.setCharacteristicNotification(c.notify, true)
        val cccd = c.notify.getDescriptor(BmsProtocol.CCCD)
        if (cccd == null) {
            line("  (kein CCCD – Notifications lassen sich nicht anfordern)")
            main.postAtTime({ sendProbes() }, token, SystemClock.uptimeMillis() + SETTLE_MS)
        } else {
            val value = if (c.notify.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0)
                BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            else BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
            runCatching { g.writeDescriptor(cccd, value) }
            // Manche Module quittieren das CCCD nie – nach kurzer Wartezeit trotzdem senden.
            main.postAtTime({ sendProbes() }, token, SystemClock.uptimeMillis() + 2500L)
        }
    }

    /** Schickt der Reihe nach die Poll-Befehle aller bekannten Protokolle. */
    private fun sendProbes() {
        val g = gatt ?: return
        if (finished) return
        val c = candidates.getOrNull(current) ?: return
        if (probedFor == current) return
        probedFor = current
        val write = c.write
        if (write == null) {
            line("  (kein Schreibkanal – warte nur auf unaufgeforderte Meldungen)")
            main.postAtTime({ nextCandidate() }, token, SystemClock.uptimeMillis() + 3000L)
            return
        }
        val probes = BmsType.entries.flatMap { t -> BmsProtocol.of(t).pollCommands(0).map { t to it } }
        var at = SystemClock.uptimeMillis()
        probes.forEach { (type, cmd) ->
            at += CMD_GAP_MS
            main.postAtTime({
                if (finished) return@postAtTime
                line("  --> ${type.name}: ${hex(cmd)}")
                val proto = BmsProtocol.of(type)
                val wt = if (proto.writeNoResponse) BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                else BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                runCatching { g.writeCharacteristic(write, cmd, wt) }
                    .onFailure { line("  (Schreiben nicht möglich: ${it.message})") }
            }, token, at)
        }
        main.postAtTime({ nextCandidate() }, token, at + CMD_GAP_MS)
    }

    private fun short(uuid: String): String =
        if (uuid.length == 36 && uuid.endsWith("-0000-1000-8000-00805f9b34fb"))
            "0x" + uuid.substring(4, 8).uppercase() else uuid

    private fun hex(bytes: ByteArray): String =
        bytes.joinToString(" ") { "%02X".format(it) }
}
