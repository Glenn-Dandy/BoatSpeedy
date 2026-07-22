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
import java.util.UUID

/**
 * BLE-Client für das JBD-BMS der Batterie. Scannt nach dem Gerät (Service 0xFF00),
 * verbindet, abonniert 0xFF01 und pollt zyklisch die Basisinfos über 0xFF02.
 *
 * Setzt voraus, dass BLUETOOTH_SCAN/CONNECT erteilt sind (die UI fragt sie ab).
 */
@SuppressLint("MissingPermission")
class BatteryBleClient(
    private val context: Context,
    private val onState: (BatteryState) -> Unit,
) {
    private companion object {
        val SERVICE: UUID = UUID.fromString("0000ff00-0000-1000-8000-00805f9b34fb")
        val NOTIFY: UUID = UUID.fromString("0000ff01-0000-1000-8000-00805f9b34fb")
        val WRITE: UUID = UUID.fromString("0000ff02-0000-1000-8000-00805f9b34fb")
        val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        const val POLL_MS = 2000L
        const val SCAN_TIMEOUT_MS = 15000L
    }

    private val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter get() = manager.adapter
    private val main = Handler(Looper.getMainLooper())

    private var gatt: BluetoothGatt? = null
    private var writeChar: BluetoothGattCharacteristic? = null
    private val assembler = FrameAssembler()

    private var state = BatteryState()
    private var data = BatteryData()
    private var cycle = 0
    private var scanning = false

    private fun update(newState: BatteryState) {
        state = newState
        onState(newState)
    }

    fun connect() {
        val ad = adapter
        if (ad == null || !ad.isEnabled) {
            update(state.copy(connection = ConnectionState.DISCONNECTED, error = "Bluetooth aus"))
            return
        }
        if (scanning || gatt != null) return
        startScan()
    }

    fun disconnect() {
        stopScan()
        main.removeCallbacksAndMessages(null)
        gatt?.let {
            it.disconnect()
            it.close()
        }
        gatt = null
        writeChar = null
        assembler.reset()
        update(BatteryState(connection = ConnectionState.DISCONNECTED))
    }

    // --- Scan ---
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            stopScan()
            val device = result.device
            update(state.copy(connection = ConnectionState.CONNECTING, deviceName = device.name))
            gatt = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
        }

        override fun onScanFailed(errorCode: Int) {
            scanning = false
            update(state.copy(connection = ConnectionState.DISCONNECTED, error = "Scan-Fehler $errorCode"))
        }
    }

    private fun startScan() {
        val scanner = adapter?.bluetoothLeScanner ?: return
        scanning = true
        update(state.copy(connection = ConnectionState.SCANNING, error = null))
        val filter = ScanFilter.Builder().setServiceUuid(ParcelUuid(SERVICE)).build()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        scanner.startScan(listOf(filter), settings, scanCallback)
        main.postDelayed({
            if (scanning) {
                stopScan()
                update(state.copy(connection = ConnectionState.DISCONNECTED, error = "Batterie nicht gefunden"))
            }
        }, SCAN_TIMEOUT_MS)
    }

    private fun stopScan() {
        if (!scanning) return
        scanning = false
        runCatching { adapter?.bluetoothLeScanner?.stopScan(scanCallback) }
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
            val service = g.getService(SERVICE) ?: run {
                update(state.copy(connection = ConnectionState.DISCONNECTED, error = "Kein FF00-Dienst"))
                return
            }
            writeChar = service.getCharacteristic(WRITE)
            val notifyChar = service.getCharacteristic(NOTIFY) ?: return
            g.setCharacteristicNotification(notifyChar, true)
            val cccd = notifyChar.getDescriptor(CCCD) ?: return
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
            if (characteristic.uuid != NOTIFY) return
            for (frame in assembler.add(value)) handleFrame(frame)
        }
    }

    private fun handleFrame(frame: ByteArray) {
        val parsed = Jbd.parseFrame(frame) ?: return
        if (parsed.status != 0) return
        when (parsed.register) {
            Jbd.REG_BASIC -> Jbd.parseBasic(parsed.payload)?.let { b ->
                data = data.copy(
                    voltage = b.voltage,
                    currentA = b.currentA,
                    soc = b.soc,
                    remainingAh = b.remainingAh,
                    nominalAh = b.nominalAh,
                    cellCount = b.cellCount,
                    tempC = b.tempC,
                    chargingFet = b.chargingFet,
                    dischargingFet = b.dischargingFet,
                )
                update(state.copy(connection = ConnectionState.CONNECTED, data = data))
            }
            Jbd.REG_CELLS -> {
                data = data.copy(cells = Jbd.parseCells(parsed.payload))
                update(state.copy(data = data))
            }
        }
    }

    private fun startPolling() {
        main.removeCallbacksAndMessages(null)
        val poll = object : Runnable {
            override fun run() {
                val g = gatt ?: return
                val ch = writeChar ?: return
                write(g, ch, Jbd.readBasicInfo)
                // Zellspannungen seltener abfragen.
                if (cycle % 3 == 0) main.postDelayed({
                    gatt?.let { gg -> writeChar?.let { c -> write(gg, c, Jbd.readCells) } }
                }, 400)
                cycle++
                main.postDelayed(this, POLL_MS)
            }
        }
        main.post(poll)
    }

    private fun write(g: BluetoothGatt, ch: BluetoothGattCharacteristic, value: ByteArray) {
        runCatching {
            g.writeCharacteristic(ch, value, BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE)
        }
    }
}
