package de.kewl.boatspeedy.battery

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.ConcurrentHashMap

/**
 * Prozessweiter Halter des Batterie-Zustands. Verwaltet den Scanner und beliebig
 * viele gleichzeitige BLE-Verbindungen (mehrere Akkus parallel). Die *gespeicherte*
 * Batterie-Liste (Aktiv-Flag/Name) liegt in den Settings – hier stehen nur die
 * laufenden Links.
 */
object BatteryRepository {

    private val _state = MutableStateFlow(BatteryHub())
    val state: StateFlow<BatteryHub> = _state.asStateFlow()

    private var scanner: BatteryScanner? = null
    private val connections = ConcurrentHashMap<String, BatteryConnection>()

    // --- Scan ---
    fun scan(context: Context, bms: BmsType) {
        val sc = scanner ?: BatteryScanner(
            context.applicationContext,
            onResults = { r -> _state.update { it.copy(scanResults = r) } },
            onError = { code -> _state.update { it.copy(scanning = false, error = "Scan error $code") } },
            onStopped = { _state.update { it.copy(scanning = false) } },
        ).also { scanner = it }

        _state.update { it.copy(scanning = true, error = null, scanResults = emptyList()) }
        if (!sc.start(bms)) {
            _state.update { it.copy(scanning = false, error = "Bluetooth off") }
        }
    }

    fun stopScan() {
        scanner?.stop()
    }

    // --- Verbindungen ---
    fun connect(context: Context, address: String, name: String, bms: BmsType) {
        if (connections.containsKey(address)) return
        _state.update {
            it.copy(links = it.links + (address to BatteryLive(address, name, LinkState.CONNECTING)))
        }
        val conn = BatteryConnection(context.applicationContext, address, bms) { link, data ->
            if (link == LinkState.DISCONNECTED) {
                connections.remove(address)
                _state.update { it.copy(links = it.links - address) }
            } else {
                _state.update { s ->
                    val prev = s.links[address]?.data
                    val live = BatteryLive(address, name, link, data ?: prev)
                    s.copy(links = s.links + (address to live))
                }
            }
        }
        connections[address] = conn
        conn.connect()
    }

    fun disconnect(address: String) {
        connections.remove(address)?.close()
        _state.update { it.copy(links = it.links - address) }
    }

    fun disconnectAll() {
        connections.values.toList().forEach { it.close() }
        connections.clear()
        _state.update { it.copy(links = emptyMap()) }
    }
}
