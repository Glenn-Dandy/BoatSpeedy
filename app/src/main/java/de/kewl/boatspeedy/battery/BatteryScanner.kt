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

    /** Startet einen Scan auf das Service-UUID des gewählten BMS. false = Bluetooth aus. */
    fun start(bms: BmsType): Boolean {
        val ad = adapter
        if (ad == null || !ad.isEnabled) return false
        // Sauberer Neustart – verhindert „Scan-Fehler 1" (ALREADY_STARTED).
        stopInternal()
        found.clear()
        onResults(emptyList())
        scanning = true

        val protocol = BmsProtocol.of(bms)
        val filter = ScanFilter.Builder().setServiceUuid(ParcelUuid(protocol.serviceUuid)).build()
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        val now = SystemClock.uptimeMillis()
        // Kurz verzögert starten, damit ein vorheriges stopScan im Stack durchgreift.
        main.postAtTime({
            if (scanning) ad.bluetoothLeScanner?.startScan(listOf(filter), settings, cb)
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

    private val cb = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val dev = result.device
            found[dev.address] = ScanDevice(dev.name, dev.address, result.rssi)
            onResults(found.values.sortedByDescending { it.rssi })
        }

        override fun onScanFailed(errorCode: Int) {
            scanning = false
            onError(errorCode)
        }
    }
}
