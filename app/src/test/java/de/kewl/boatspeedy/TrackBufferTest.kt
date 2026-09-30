package de.kewl.boatspeedy

import de.kewl.boatspeedy.trip.TrackBuffer
import de.kewl.boatspeedy.trip.TrackPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Der Track hört nicht mehr auf, wenn die Grenze erreicht ist.
 *
 * Am 27.09. endete eine Aufzeichnung nach 1:29:22 mitten in der Fahrt, genau beim
 * zehntausendsten Punkt. Zeit und Strecke liefen weiter, der Track nicht.
 */
class TrackBufferTest {

    private fun punkt(i: Int) = TrackPoint(lat = 50.0 + i * 1e-5, lon = 11.0, tMs = i * 1000L)

    @Test
    fun `unter der Grenze wird jeder Punkt aufgenommen`() {
        val b = TrackBuffer(100)
        repeat(99) { assertTrue(b.add(punkt(it))) }
        assertEquals(99, b.size)
    }

    @Test
    fun `an der Grenze wird ausgeduennt statt aufgehoert`() {
        val b = TrackBuffer(100)
        repeat(100) { b.add(punkt(it)) }
        // Jeder zweite bleibt, und der letzte immer: Er ist die aktuelle Position.
        assertEquals("halbiert", 51, b.size)
        assertEquals(99_000L, b.toList().last().tMs)
        // Danach nur jeder zweite Fix — aber es geht weiter.
        repeat(20) { b.add(punkt(100 + it)) }
        assertEquals(61, b.size)
    }

    /** Eine sehr lange Fahrt: Der Track reicht bis zum Ende und bleibt unter der Grenze. */
    @Test
    fun `der Track reicht bis zum Ende einer langen Fahrt`() {
        val b = TrackBuffer(1_000)
        val fixe = 50_000
        repeat(fixe) { b.add(punkt(it)) }
        val liste = b.toList()
        assertTrue("über der Grenze: ${liste.size}", liste.size < 1_000)
        assertTrue("reicht nicht bis zum Ende: ${liste.last().tMs}", liste.last().tMs >= (fixe - 64) * 1000L)
        assertEquals("der Anfang bleibt erhalten", 0L, liste.first().tMs)
        // Zeitlich geordnet, ohne Doppel.
        assertTrue(liste.zipWithNext().all { (a, c) -> c.tMs > a.tMs })
    }

    @Test
    fun `nach dem Leeren beginnt es wieder bei voller Aufloesung`() {
        val b = TrackBuffer(10)
        repeat(30) { b.add(punkt(it)) }
        b.clear()
        repeat(5) { assertTrue(b.add(punkt(it))) }
        assertEquals(5, b.size)
    }
}
