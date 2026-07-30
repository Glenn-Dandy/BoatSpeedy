package de.kewl.boatspeedy.battery

/**
 * Ladezustand fürs Dashboard, wenn die Batterie geladen wird (Strom positiv).
 * [hoursToFull]/[fullAtEpochMs] sind aus dem aktuellen Ladestrom hochgerechnet.
 */
data class ChargeState(
    val charging: Boolean = false,
    val chargeA: Float = 0f,
    val soc: Int = 0,
    val hoursToFull: Double? = null,
    val fullAtEpochMs: Long? = null,
)
