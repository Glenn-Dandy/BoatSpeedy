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

    fun connect(context: Context) {
        if (client == null) {
            client = BatteryBleClient(context.applicationContext) { s -> _state.value = s }
        }
        client?.connect()
    }

    fun disconnect() {
        client?.disconnect()
    }
}
