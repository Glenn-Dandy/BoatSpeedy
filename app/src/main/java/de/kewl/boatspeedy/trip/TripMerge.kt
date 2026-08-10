package de.kewl.boatspeedy.trip

/**
 * Führt mehrere Fahrten zu einer zusammen (z. B. wenn versehentlich gestoppt wurde).
 * Die Fahrten werden nach Startzeit sortiert; Zeitstempel der Track-Punkte werden auf
 * den gemeinsamen Start umgerechnet, Kennzahlen addiert.
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
    val duration = sorted.sumOf { it.durationMs }
    // Gesamtzeit: vom ersten Start bis zum Ende der letzten Fahrt (Lücken zählen als Pause).
    val last = sorted.last()
    val total = ((last.startedAt - start) + last.totalMs).coerceAtLeast(duration)
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
