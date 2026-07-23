package de.kewl.boatspeedy.battery

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Handler
import android.os.Looper

/**
 * Genau *eine* GATT-Verbindung zu einer Batterie. Mehrere Instanzen laufen parallel
 * (mehrere Akkus gleichzeitig verbunden). Jede Instanz hat ihre eigene Protokoll-
 * Instanz (eigenes Framing) und einen eigenen Handler-Token, sodass sich die
 * Poll-Zyklen der Verbindungen nicht gegenseitig löschen.
 *
 * Setzt voraus, dass BLUETOOTH_CONNECT erteilt ist (die UI fragt sie ab).
 */
@SuppressLint("MissingPermission")
class BatteryConnection(
    private val context: Context,
    val address: String,
    bms: BmsType,
    private val onUpdate: (LinkState, BatteryData?) -> Unit,
) {
    private companion object {
        const val POLL_MS = 2000L
        const val CMD_STAGGER_MS = 250L
    }

    private val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter get() = manager.adapter
    private val main = Handler(Looper.getMainLooper())
    private val token = Any()

    private val protocol: BmsProtocol = BmsProtocol.of(bms)
    private var gatt: BluetoothGatt? = null
    private var writeChar: BluetoothGattCharacteristic? = null
    private var cycle = 0
    private var data = BatteryData()
    @Volatile private var closed = false

    fun connect() {
        val device: BluetoothDevice = runCatching { adapter?.getRemoteDevice(address) }.getOrNull()
            ?: run { onUpdate(LinkState.DISCONNECTED, null); return }
        onUpdate(LinkState.CONNECTING, null)
        gatt = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
    }

    fun close() {
        if (closed) return
        closed = true
        main.removeCallbacksAndMessages(token)
        gatt?.let { runCatching { it.disconnect(); it.close() } }
        gatt = null
        writeChar = null
        onUpdate(LinkState.DISCONNECTED, null)
    }

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> if (!closed) g.discoverServices()
                BluetoothProfile.STATE_DISCONNECTED -> {
                    main.removeCallbacksAndMessages(token)
                    runCatching { g.close() }
                    if (gatt === g) gatt = null
                    if (!closed) { closed = true; onUpdate(LinkState.DISCONNECTED, null) }
                }
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            if (closed) return
            val service = g.getService(protocol.serviceUuid) ?: run {
                onUpdate(LinkState.DISCONNECTED, null); return
            }
            writeChar = service.getCharacteristic(protocol.writeUuid)
            val notifyChar = service.getCharacteristic(protocol.notifyUuid) ?: return
            g.setCharacteristicNotification(notifyChar, true)
            val cccd = notifyChar.getDescriptor(protocol.cccdUuid) ?: return
            g.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
        }

        override fun onDescriptorWrite(g: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            if (closed) return
            onUpdate(LinkState.CONNECTED, data)
            startPolling()
        }

        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            if (closed || characteristic.uuid != protocol.notifyUuid) return
            protocol.onChunk(value, data)?.let {
                data = it
                onUpdate(LinkState.CONNECTED, it)
            }
        }
    }

    private fun startPolling() {
        main.removeCallbacksAndMessages(token)
        poll()
    }

    private fun poll() {
        if (closed) return
        val g = gatt
        val ch = writeChar
        if (g != null && ch != null) {
            protocol.pollCommands(cycle).forEachIndexed { i, cmd ->
                main.postDelayed({
                    if (!closed) gatt?.let { gg -> writeChar?.let { write(gg, it, cmd) } }
                }, token, i * CMD_STAGGER_MS)
            }
            cycle++
        }
        main.postDelayed({ poll() }, token, POLL_MS)
    }

    private fun write(g: BluetoothGatt, ch: BluetoothGattCharacteristic, value: ByteArray) {
        val type = if (protocol.writeNoResponse)
            BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        else BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        runCatching { g.writeCharacteristic(ch, value, type) }
    }
}
