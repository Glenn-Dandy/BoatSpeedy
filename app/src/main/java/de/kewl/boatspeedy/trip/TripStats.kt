package de.kewl.boatspeedy.trip

/** Kennzahlen einer Fahrt. Geschwindigkeiten in m/s, Distanz in Metern, Zeit in ms. */
data class TripStats(
    val distanceM: Double = 0.0,
    val maxSpeedMs: Float = 0f,
    val avgSpeedMs: Float = 0f,
    val elapsedMs: Long = 0L,
    /** Verbrauchte Energie in Wh (aus der Batterie-Leistung integriert), 0 wenn kein Akku verbunden. */
    val energyWh: Float = 0f,
    /** Verbrauchte Ladung in Ah (aus dem Strom integriert), 0 wenn kein Akku verbunden. */
    val chargeAh: Float = 0f,
) {
    /** True, sobald eine Fahrt gelaufen ist (Werte anzeigen, auch nach Stopp). */
    val hasData: Boolean get() = elapsedMs > 0L || distanceM > 0.0

    /** Effizienz in Wh/km; null, solange keine sinnvolle Distanz/Energie vorliegt. */
    val whPerKm: Float? get() {
        val km = distanceM / 1000.0
        return if (energyWh > 0f && km > 0.05) (energyWh / km).toFloat() else null
    }
}
