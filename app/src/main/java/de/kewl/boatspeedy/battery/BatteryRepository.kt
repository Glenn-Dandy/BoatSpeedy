package de.kewl.boatspeedy.battery

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Prozessweiter Halter des Batterie-Zustands; kapselt den BLE-Client. */
object BatteryRepository {

    private val _state = MutableStateFlow(BatteryState())
    val state: StateFlow<BatteryState> = _state.asStateFlow()

    private var client: BatteryBleClient? = null

    private fun client(context: Context): BatteryBleClient =
        client ?: BatteryBleClient(context.applicationContext) { s -> _state.value = s }.also { client = it }

    fun scan(context: Context, bms: BmsType) = client(context).scan(bms)

    fun connectTo(context: Context, address: String, bms: BmsType) =
        client(context).connectTo(address, bms)

    fun disconnect() {
        client?.disconnect()
    }
}
