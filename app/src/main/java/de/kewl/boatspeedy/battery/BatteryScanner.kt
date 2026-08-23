package de.kewl.boatspeedy.battery

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.os.SystemClock

/**
 * Reiner BLE-Scanner (kein Auto-Connect). Meldet gefundene Geräte, Fehler und
 * das Ende des Scans zurück. Nutzt einen eigenen Handler-Token, damit er die
 * Poll-Callbacks der parallel laufenden Verbindungen nicht anrührt.
 */
@SuppressLint("MissingPermission")
class BatteryScanner(
    private val context: Context,
    private val onResults: (List<ScanDevice>) -> Unit,
    private val onError: (Int) -> Unit,
    private val onStopped: () -> Unit,
) {
    private companion object {
        const val SCAN_TIMEOUT_MS = 15000L
        const val START_DELAY_MS = 300L
    }

    private val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter get() = manager.adapter
    private val main = Handler(Looper.getMainLooper())
    private val token = Any()

    private var scanning = false
    private val found = LinkedHashMap<String, ScanDevice>()

    /**
     * Scannt auf **alle** unterstützten BMS gleichzeitig (ein Filter je Service-UUID) und
     * erkennt den Typ aus der beworbenen UUID. false = Bluetooth aus.
     */
    fun start(): Boolean {
        val ad = adapter
        if (ad == null || !ad.isEnabled) return false
        // Sauberer Neustart – verhindert „Scan-Fehler 1" (ALREADY_STARTED).
        stopInternal()
        found.clear()
        onResults(emptyList())
        scanning = true

        val filters = BmsType.entries.map { t ->
            ScanFilter.Builder().setServiceUuid(ParcelUuid(BmsProtocol.of(t).serviceUuid)).build()
        }
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        val now = SystemClock.uptimeMillis()
        // Kurz verzögert starten, damit ein vorheriges stopScan im Stack durchgreift.
        main.postAtTime({
            if (scanning) ad.bluetoothLeScanner?.startScan(filters, settings, cb)
        }, token, now + START_DELAY_MS)
        main.postAtTime({ stop() }, token, now + SCAN_TIMEOUT_MS)
        return true
    }

    fun stop() {
        if (!scanning) return
        stopInternal()
        onStopped()
    }

    private fun stopInternal() {
        scanning = false
        main.removeCallbacksAndMessages(token)
        runCatching { adapter?.bluetoothLeScanner?.stopScan(cb) }
    }

    /**
     * FFE0 bewerben JK *und* Redodo/LiTime – dieselbe UUID eines gaengigen
     * Seriell-ueber-BLE-Moduls. Der Geraetename entscheidet: Redodo meldet sich als
     * "R-…", LiTime als "LT-…". Ohne Namen bleibt es bei der Reihenfolge der Typen.
     */
    private fun typeOf(advertised: List<java.util.UUID>, name: String?): BmsType? {
        val byUuid = BmsType.entries.filter { t -> BmsProtocol.of(t).serviceUuid in advertised }
        if (byUuid.size > 1 && name != null) {
            val n = name.uppercase()
            if (n.startsWith("R-") || n.startsWith("LT-")) {
                byUuid.firstOrNull { it == BmsType.REDODO }?.let { return it }
            }
        }
        return byUuid.firstOrNull()
    }

    private val cb = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val dev = result.device
            // Typ aus den beworbenen Service-UUIDs ableiten (null, wenn nicht eindeutig).
            val advertised = result.scanRecord?.serviceUuids?.map { it.uuid }.orEmpty()
            val name = dev.name ?: result.scanRecord?.deviceName
            val type = typeOf(advertised, name)
            found[dev.address] = ScanDevice(dev.name, dev.address, result.rssi, type)
            onResults(found.values.sortedByDescending { it.rssi })
        }

        override fun onScanFailed(errorCode: Int) {
            scanning = false
            onError(errorCode)
        }
    }
}
