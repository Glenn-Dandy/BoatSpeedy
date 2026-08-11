package de.kewl.boatspeedy.trip

/**
 * Führt mehrere Fahrten zu einer zusammen (z. B. wenn versehentlich gestoppt wurde).
 * Die Fahrten werden nach Startzeit sortiert; die Track-Punkte behalten ihre echten
 * Zeitpunkte (auf den gemeinsamen Start umgerechnet).
 *
 * Zeiten: **Fahrzeiten** und **Pausen der Einzelfahrten** werden getrennt addiert; die
 * Lücke zwischen den Fahrten (App war gestoppt) zählt nicht mit.
 */
fun mergeTrips(trips: List<SavedTrip>): SavedTrip? {
    val sorted = trips.sortedBy { it.startedAt }
    if (sorted.size < 2) return null

    val start = sorted.first().startedAt
    val points = ArrayList<TrackPoint>()
    var chargeBase = 0f
    for (t in sorted) {
        val offset = t.startedAt - start // Versatz zur gemeinsamen Startzeit
        t.points.forEach { p ->
            points.add(p.copy(tMs = p.tMs + offset, chargeAh = p.chargeAh + chargeBase))
        }
        chargeBase += t.chargeAh
    }

    val distance = sorted.sumOf { it.distanceM }
    // Fahrzeiten addieren, Pausen der Einzelfahrten addieren – die Lücke ZWISCHEN den
    // Fahrten (App war gestoppt) zählt bewusst NICHT mit.
    val duration = sorted.sumOf { it.durationMs }
    val pause = sorted.sumOf { (it.totalMs - it.durationMs).coerceAtLeast(0L) }
    val total = duration + pause
    val avg = if (duration > 0) (distance / (duration / 1000.0)).toFloat() else 0f

    return SavedTrip(
        id = start,
        startedAt = start,
        distanceM = distance,
        durationMs = duration,
        totalMs = total,
        avgSpeedMs = avg,
        maxSpeedMs = sorted.maxOf { it.maxSpeedMs },
        energyWh = sorted.map { it.energyWh }.sum(),
        chargeAh = sorted.map { it.chargeAh }.sum(),
        points = points,
    )
}
