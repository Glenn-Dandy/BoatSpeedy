package de.kewl.boatspeedy.battery

/** Verbindungszustand einer einzelnen Batterie. */
enum class LinkState { DISCONNECTED, CONNECTING, CONNECTED }

/** Aufbereitete Live-Werte der Batterie. */
data class BatteryData(
    val voltage: Float = 0f,
    val currentA: Float = 0f,        // signed; Vorzeichen firmware-abhängig (Feldtest!)
    val soc: Int = 0,                // %
    val remainingAh: Float = 0f,
    val nominalAh: Float = 0f,
    val cellCount: Int = 0,
    val tempC: Float? = null,
    val chargingFet: Boolean = false,
    val dischargingFet: Boolean = false,
    val cells: List<Float> = emptyList(),
) {
    /** Entladestrom in A (Betrag), 0 wenn geladen wird / kein Verbrauch. */
    val dischargeA: Float get() = if (currentA < 0) -currentA else 0f
}

/** Ein beim Scan gefundenes BLE-Gerät. */
data class ScanDevice(val name: String?, val address: String, val rssi: Int)

/** Laufzeit-Zustand einer verbundenen/verbindenden Batterie (nach Adresse). */
data class BatteryLive(
    val address: String,
    val name: String,
    val link: LinkState,
    val data: BatteryData? = null,
)

/**
 * Prozessweiter Laufzeitzustand aller Batterie-Verbindungen und des Scanners.
 * Die *gespeicherte* Liste (mit Aktiv-Flag/Name) liegt in den Settings; hier stehen
 * nur die aktuell offenen BLE-Links samt Live-Werten.
 */
data class BatteryHub(
    val scanning: Boolean = false,
    val scanResults: List<ScanDevice> = emptyList(),
    val error: String? = null,
    val links: Map<String, BatteryLive> = emptyMap(),
)

/**
 * Setzt fragmentierte Notifications (MTU 23) zu kompletten JBD-Frames zusammen
 * (Start 0xDD … Ende 0x77) und gibt fertige Frames zurück.
 */
class FrameAssembler {
    private val buf = ArrayList<Byte>()

    fun add(chunk: ByteArray): List<ByteArray> {
        val frames = ArrayList<ByteArray>()
        for (b in chunk) buf.add(b)
        while (true) {
            val start = buf.indexOfFirst { it == 0xDD.toByte() }
            if (start < 0) { buf.clear(); break }
            if (start > 0) repeat(start) { buf.removeAt(0) }
            val end = buf.indexOfFirst { it == 0x77.toByte() }
            if (end < 0) break
            val frame = ByteArray(end + 1) { buf[it] }
            repeat(end + 1) { buf.removeAt(0) }
            frames.add(frame)
        }
        return frames
    }

    fun reset() = buf.clear()
}
