package de.kewl.boatspeedy.trip

/**
 * Ein aufgezeichneter Wegpunkt (tMs = ms seit Fahrtbeginn) samt Telemetrie an dieser
 * Stelle: Geschwindigkeit, Ladezustand (−1 = unbekannt) und kumulativer Verbrauch.
 */
data class TrackPoint(
    val lat: Double,
    val lon: Double,
    val tMs: Long,
    val speedMs: Float = 0f,
    val soc: Int = -1,
    val chargeAh: Float = 0f,
)

/** Eine gespeicherte, abgeschlossene Fahrt inkl. optionalem Track. */
data class SavedTrip(
    val id: Long,            // == startedAt (Epoch-ms), stabiler Schlüssel/Dateiname
    val startedAt: Long,     // Epoch-ms
    val distanceM: Double,
    val durationMs: Long,        // reine Fahrzeit (ohne Auto-Pause)
    val totalMs: Long = durationMs, // Gesamtzeit inkl. Pausen; Pause = total − duration
    val avgSpeedMs: Float,
    val maxSpeedMs: Float,
    val energyWh: Float,
    val chargeAh: Float,
    val points: List<TrackPoint> = emptyList(),
) {
    val hasTrack: Boolean get() = points.size >= 2
}
