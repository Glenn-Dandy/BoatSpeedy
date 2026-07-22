package de.kewl.boatspeedy.trip

/** Kennzahlen einer Fahrt. Geschwindigkeiten in m/s, Distanz in Metern, Zeit in ms. */
data class TripStats(
    val distanceM: Double = 0.0,
    val maxSpeedMs: Float = 0f,
    val avgSpeedMs: Float = 0f,
    val elapsedMs: Long = 0L,
) {
    /** True, sobald eine Fahrt gelaufen ist (Werte anzeigen, auch nach Stopp). */
    val hasData: Boolean get() = elapsedMs > 0L || distanceM > 0.0
}
