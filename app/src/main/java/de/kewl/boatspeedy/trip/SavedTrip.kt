package de.kewl.boatspeedy.trip

/** Ein aufgezeichneter Wegpunkt (tMs = ms seit Fahrtbeginn). */
data class TrackPoint(val lat: Double, val lon: Double, val tMs: Long)

/** Eine gespeicherte, abgeschlossene Fahrt inkl. optionalem Track. */
data class SavedTrip(
    val id: Long,            // == startedAt (Epoch-ms), stabiler Schlüssel/Dateiname
    val startedAt: Long,     // Epoch-ms
    val distanceM: Double,
    val durationMs: Long,
    val avgSpeedMs: Float,
    val maxSpeedMs: Float,
    val energyWh: Float,
    val chargeAh: Float,
    val points: List<TrackPoint> = emptyList(),
) {
    val hasTrack: Boolean get() = points.size >= 2
}
