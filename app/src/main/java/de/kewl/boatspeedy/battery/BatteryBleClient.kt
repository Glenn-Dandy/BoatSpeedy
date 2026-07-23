package de.kewl.boatspeedy.battery

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid

/**
 * Protokoll-agnostischer BLE-Client für BMS-Batterien. Ablauf:
 *  1. [scan] mit dem gewählten [BmsType] → Liste gefundener Geräte (kein Auto-Connect)
 *  2. [connectTo] mit der gewählten Adresse → verbinden, Notify abonnieren, zyklisch pollen
 *  3. [disconnect] / neuer Scan zum Wechseln der Batterie
 *
 * Setzt voraus, dass BLUETOOTH_SCAN/CONNECT erteilt sind (die UI fragt sie ab).
 */
@SuppressLint("MissingPermission")
class BatteryBleClient(
    private val context: Context,
    private val onState: (BatteryState) -> Unit,
) {
    private companion object {
        const val POLL_MS = 2000L
        const val SCAN_TIMEOUT_MS = 15000L
    }

    private val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter get() = manager.adapter
    private val main = Handler(Looper.getMainLooper())

    private var gatt: BluetoothGatt? = null
    private var writeChar: BluetoothGattCharacteristic? = null
    private var protocol: BmsProtocol = JbdProtocol()
    private var cycle = 0
    private var scanning = false
    private val found = LinkedHashMap<String, ScanDevice>()

    private var state = BatteryState()
    private var data = BatteryData()

    private fun update(s: BatteryState) {
        state = s
        onState(s)
    }

    // --- Scan ---
    fun scan(bms: BmsType) {
        val ad = adapter
        if (ad == null || !ad.isEnabled) {
            update(state.copy(connection = ConnectionState.DISCONNECTED, error = "Bluetooth aus"))
            return
        }
        // Vollständiger Teardown: alte Verbindung trennen, alten Scan sicher stoppen,
        // ausstehende Poll-/Timeout-Callbacks löschen. Verhindert „Scan-Fehler 1"
        // (SCAN_FAILED_ALREADY_STARTED) beim „Batterie wechseln".
        main.removeCallbacksAndMessages(null)
        gatt?.let { runCatching { it.disconnect(); it.close() } }
        gatt = null
        writeChar = null
        runCatching { ad.bluetoothLeScanner?.stopScan(scanCallback) }
        scanning = true

        protocol = BmsProtocol.of(bms)
        found.clear()
        update(BatteryState(connection = ConnectionState.SCANNING, scanResults = emptyList()))
        val filter = ScanFilter.Builder().setServiceUuid(ParcelUuid(protocol.serviceUuid)).build()
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        // Kurz verzögert starten, damit stopScan im Stack durchgreift.
        main.postDelayed({
            if (scanning) ad.bluetoothLeScanner?.startScan(listOf(filter), settings, scanCallback)
        }, 300)
        main.postDelayed({ stopScan() }, SCAN_TIMEOUT_MS)
    }

    private fun stopScan() {
        if (!scanning) return
        scanning = false
        runCatching { adapter?.bluetoothLeScanner?.stopScan(scanCallback) }
        update(state.copy(connection = ConnectionState.DISCONNECTED))
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val dev = result.device
            found[dev.address] = ScanDevice(dev.name, dev.address, result.rssi)
            update(state.copy(scanResults = found.values.sortedByDescending { it.rssi }))
        }

        override fun onScanFailed(errorCode: Int) {
            scanning = false
            update(state.copy(connection = ConnectionState.DISCONNECTED, error = "Scan-Fehler $errorCode"))
        }
    }

    // --- Connect ---
    fun connectTo(address: String, bms: BmsType) {
        main.removeCallbacksAndMessages(null)
        scanning = false
        runCatching { adapter?.bluetoothLeScanner?.stopScan(scanCallback) }
        val device = runCatching { adapter?.getRemoteDevice(address) }.getOrNull() ?: return
        protocol = BmsProtocol.of(bms)
        cycle = 0
        data = BatteryData()
        update(state.copy(connection = ConnectionState.CONNECTING, deviceName = device.name, scanResults = emptyList()))
        gatt = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
    }

    fun disconnect() {
        main.removeCallbacksAndMessages(null)
        if (scanning) { scanning = false; runCatching { adapter?.bluetoothLeScanner?.stopScan(scanCallback) } }
        gatt?.let { it.disconnect(); it.close() }
        gatt = null
        writeChar = null
        update(BatteryState(connection = ConnectionState.DISCONNECTED))
    }

    // --- GATT ---
    private val callback = object : android.bluetooth.BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> g.discoverServices()
                BluetoothProfile.STATE_DISCONNECTED -> {
                    main.removeCallbacksAndMessages(null)
                    runCatching { g.close() }
                    if (gatt === g) gatt = null
                    update(BatteryState(connection = ConnectionState.DISCONNECTED))
                }
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            val service = g.getService(protocol.serviceUuid) ?: run {
                update(state.copy(connection = ConnectionState.DISCONNECTED, error = "Dienst nicht gefunden"))
                return
            }
            writeChar = service.getCharacteristic(protocol.writeUuid)
            val notifyChar = service.getCharacteristic(protocol.notifyUuid) ?: return
            g.setCharacteristicNotification(notifyChar, true)
            val cccd = notifyChar.getDescriptor(protocol.cccdUuid) ?: return
            g.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
        }

        override fun onDescriptorWrite(g: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            update(state.copy(connection = ConnectionState.CONNECTED, error = null))
            startPolling()
        }

        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            if (characteristic.uuid != protocol.notifyUuid) return
            protocol.onChunk(value, data)?.let {
                data = it
                update(state.copy(connection = ConnectionState.CONNECTED, data = it))
            }
        }
    }

    private fun startPolling() {
        main.removeCallbacksAndMessages(null)
        val poll = object : Runnable {
            override fun run() {
                val g = gatt ?: return
                val ch = writeChar ?: return
                val cmds = protocol.pollCommands(cycle)
                cmds.forEachIndexed { i, cmd ->
                    main.postDelayed({
                        gatt?.let { gg -> writeChar?.let { write(gg, it, cmd) } }
                    }, i * 250L)
                }
                cycle++
                main.postDelayed(this, POLL_MS)
            }
        }
        main.post(poll)
    }

    private fun write(g: BluetoothGatt, ch: BluetoothGattCharacteristic, value: ByteArray) {
        val type = if (protocol.writeNoResponse)
            BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        else BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        runCatching { g.writeCharacteristic(ch, value, type) }
    }
}
