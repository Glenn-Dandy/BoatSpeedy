package de.kewl.boatspeedy.trip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Das Zusammenführen hat schon einmal falsch gerechnet: die Lücke zwischen zwei Fahrten
 * (App war gestoppt) landete als Pause in der Gesamtzeit. Das sieht man dem Ergebnis nicht
 * an, solange man nicht nachrechnet — deshalb hier festgehalten.
 */
class TripMergeTest {

    private fun trip(
        start: Long,
        distance: Double,
        duration: Long,
        total: Long = duration,
        charge: Float = 0f,
        points: List<TrackPoint> = emptyList(),
    ) = SavedTrip(
        id = start, startedAt = start, distanceM = distance, durationMs = duration,
        totalMs = total, avgSpeedMs = 0f, maxSpeedMs = 1f, energyWh = 0f,
        chargeAh = charge, points = points,
    )

    @Test
    fun `Luecke zwischen den Fahrten zaehlt nicht als Pause`() {
        val eineStunde = 3_600_000L
        val a = trip(start = 0, distance = 1000.0, duration = 600_000, total = 900_000)
        val b = trip(start = eineStunde, distance = 500.0, duration = 300_000, total = 300_000)

        val m = mergeTrips(listOf(a, b))!!
        assertEquals(1500.0, m.distanceM, 0.001)
        assertEquals(900_000L, m.durationMs)          // 10 + 5 Minuten Fahrzeit
        // Pause = 5 Minuten aus Fahrt A. Die Stunde dazwischen bleibt draußen.
        assertEquals(1_200_000L, m.totalMs)
        assertEquals(300_000L, m.totalMs - m.durationMs)
    }

    @Test
    fun `Reihenfolge der Auswahl ist egal`() {
        val a = trip(start = 0, distance = 1000.0, duration = 600_000)
        val b = trip(start = 10_000_000, distance = 500.0, duration = 300_000)
        assertEquals(mergeTrips(listOf(a, b)), mergeTrips(listOf(b, a)))
    }

    @Test
    fun `Punkte behalten ihren Abstand zur gemeinsamen Startzeit`() {
        val a = trip(
            start = 1000, distance = 0.0, duration = 1000, charge = 2f,
            points = listOf(TrackPoint(lat = 53.0, lon = 7.0, tMs = 0)),
        )
        val b = trip(
            start = 61_000, distance = 0.0, duration = 1000, charge = 3f,
            points = listOf(TrackPoint(lat = 53.1, lon = 7.1, tMs = 0)),
        )
        val m = mergeTrips(listOf(a, b))!!
        assertEquals(1000L, m.startedAt)
        assertEquals(listOf(0L, 60_000L), m.points.map { it.tMs })
        // Der Verbrauch der zweiten Fahrt setzt auf dem der ersten auf.
        assertEquals(listOf(0f, 2f), m.points.map { it.chargeAh })
        assertEquals(5f, m.chargeAh, 0.001f)
    }

    @Test
    fun `eine einzelne Fahrt ergibt nichts zum Zusammenfuehren`() {
        assertNull(mergeTrips(listOf(trip(0, 1.0, 1))))
        assertNull(mergeTrips(emptyList()))
    }
}
