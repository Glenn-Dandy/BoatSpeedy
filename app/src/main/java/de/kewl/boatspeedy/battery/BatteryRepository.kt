package de.kewl.boatspeedy.battery

import android.content.Context
import android.os.Handler
import android.os.Looper
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
 *
 * **Auto-Reconnect:** Verbindungen, die per [connect] gewünscht wurden, gelten so
 * lange als „gewollt", bis [disconnect] sie explizit beendet. Bricht ein Link
 * unerwartet ab, wird nach kurzer Wartezeit automatisch neu verbunden.
 */
object BatteryRepository {

    private const val RECONNECT_DELAY_MS = 5000L

    private val _state = MutableStateFlow(BatteryHub())
    val state: StateFlow<BatteryHub> = _state.asStateFlow()

    private var scanner: BatteryScanner? = null
    private val connections = ConcurrentHashMap<String, BatteryConnection>()
    private val desired = ConcurrentHashMap<String, Wanted>()
    private val tokens = ConcurrentHashMap<String, Any>()
    private val main = Handler(Looper.getMainLooper())
    private var appContext: Context? = null

    private data class Wanted(val name: String, val bms: BmsType)

    // --- Scan ---
    fun scan(context: Context, bms: BmsType) {
        appContext = context.applicationContext
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
        appContext = context.applicationContext
        desired[address] = Wanted(name, bms)
        connectInternal(address)
    }

    private fun connectInternal(address: String) {
        val ctx = appContext ?: return
        val wanted = desired[address] ?: return
        if (connections.containsKey(address)) return

        _state.update {
            it.copy(links = it.links + (address to BatteryLive(address, wanted.name, LinkState.CONNECTING)))
        }
        val conn = BatteryConnection(ctx, address, wanted.bms) { link, data ->
            if (link == LinkState.DISCONNECTED) {
                connections.remove(address)
                if (desired.containsKey(address)) {
                    // Ungewollter Abbruch → als „verbinde…" anzeigen und neu versuchen.
                    val name = desired[address]?.name ?: address
                    _state.update { s -> s.copy(links = s.links + (address to BatteryLive(address, name, LinkState.CONNECTING))) }
                    main.postDelayed({ connectInternal(address) }, tokenFor(address), RECONNECT_DELAY_MS)
                } else {
                    _state.update { it.copy(links = it.links - address) }
                }
            } else {
                _state.update { s ->
                    val prev = s.links[address]?.data
                    s.copy(links = s.links + (address to BatteryLive(address, wanted.name, link, data ?: prev)))
                }
            }
        }
        connections[address] = conn
        conn.connect()
    }

    fun disconnect(address: String) {
        desired.remove(address)
        main.removeCallbacksAndMessages(tokenFor(address))
        connections.remove(address)?.close()
        _state.update { it.copy(links = it.links - address) }
    }

    fun disconnectAll() {
        desired.clear()
        tokens.values.forEach { main.removeCallbacksAndMessages(it) }
        connections.values.toList().forEach { it.close() }
        connections.clear()
        _state.update { it.copy(links = emptyMap()) }
    }

    private fun tokenFor(address: String): Any = tokens.getOrPut(address) { Any() }
}
