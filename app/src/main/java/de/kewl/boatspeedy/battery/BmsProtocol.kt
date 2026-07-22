package de.kewl.boatspeedy.battery

import java.util.UUID

/** Unterstützte BMS-Typen. `tested` = real gegen echte Hardware verifiziert. */
enum class BmsType(val display: String, val tested: Boolean) {
    JBD("JBD / Jiabaida", true),
    DALY("Daly", false),
    JK("JK (Jikong)", false),
}

/**
 * Abstraktion über verschiedene BMS-BLE-Protokolle. Jedes Protokoll kennt seine
 * GATT-UUIDs, die zyklisch zu sendenden Befehle und dekodiert eingehende
 * Notification-Chunks (mit eigenem Puffer/Framing) zu [BatteryData].
 */
abstract class BmsProtocol {
    abstract val type: BmsType
    abstract val serviceUuid: UUID
    abstract val notifyUuid: UUID
    abstract val writeUuid: UUID
    open val cccdUuid: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    open val writeNoResponse: Boolean = true

    /** Befehle, die pro Poll-Zyklus (alle ~2 s) gesendet werden. */
    abstract fun pollCommands(cycle: Int): List<ByteArray>

    /**
     * Verarbeitet einen rohen Notification-Chunk. Gibt aktualisierte Daten zurück,
     * wenn eine vollständige Nachricht dekodiert wurde, sonst null.
     */
    abstract fun onChunk(chunk: ByteArray, current: BatteryData): BatteryData?

    companion object {
        val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        fun of(type: BmsType): BmsProtocol = when (type) {
            BmsType.JBD -> JbdProtocol()
            BmsType.DALY -> DalyProtocol()
            BmsType.JK -> JkProtocol()
        }

        internal fun uuid16(short: String): UUID =
            UUID.fromString("0000$short-0000-1000-8000-00805f9b34fb")
    }
}
