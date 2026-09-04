package de.kewl.boatspeedy

import de.kewl.boatspeedy.nav.DeadReckoner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot

class DeadReckoningTest {

    private val t0 = 1_000_000L

    /** Abstand in Metern – dieselbe Näherung wie im Rechner selbst. */
    private fun metres(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = (lat2 - lat1) * DeadReckoner.M_PER_DEG_LAT
        val dLon = (lon2 - lon1) * DeadReckoner.M_PER_DEG_LAT * cos(Math.toRadians(lat1))
        return hypot(dLat, dLon)
    }

    @Test
    fun `erste Messung wird uebernommen`() {
        val r = DeadReckoner()
        r.onFix(52.0, 13.0, 90f, 3f, t0)
        assertEquals(52.0, r.lat!!, 1e-9)
        assertEquals(13.0, r.lon!!, 1e-9)
        assertEquals(90f, r.headingDeg, 1e-3f)
    }

    /** Der Kern der Sache: zwischen zwei Messungen läuft die Position weiter. */
    @Test
    fun `zwischen den Messungen wird weitergerechnet`() {
        val r = DeadReckoner()
        r.onFix(52.0, 13.0, 0f, 5f, t0) // 5 m/s nach Norden
        val startLat = r.lat!!
        // eine halbe Sekunde in Schritten von 0,1 s
        repeat(5) { r.advance(0.1, t0 + 100L * it) }
        val moved = (r.lat!! - startLat) * DeadReckoner.M_PER_DEG_LAT
        // 5 m/s * 0,5 s = 2,5 m – etwas weniger, weil keine Korrektur anliegt.
        assertEquals(2.5, moved, 0.1)
    }

    @Test
    fun `im Stand wird nicht vorausgerechnet`() {
        val r = DeadReckoner()
        r.onFix(52.0, 13.0, 0f, 0.1f, t0) // unter MIN_SPEED_MS
        val startLat = r.lat!!
        repeat(10) { r.advance(0.1, t0 + 100L * it) }
        assertEquals(0.0, (r.lat!! - startLat) * DeadReckoner.M_PER_DEG_LAT, 0.01)
    }

    /**
     * Ohne frische Messung darf die Schätzung nicht ins Blaue davonlaufen — sonst wandert
     * der Marker bei Empfangsverlust minutenlang weiter über die Karte.
     */
    @Test
    fun `ohne Messung wird eingefroren`() {
        val r = DeadReckoner()
        r.onFix(52.0, 13.0, 0f, 10f, t0)
        val atFreeze = t0 + DeadReckoner.STALE_MS + 1
        r.advance(0.1, atFreeze)
        val frozen = r.lat!!
        repeat(50) { r.advance(0.1, atFreeze + 100L * it) }
        assertEquals(frozen, r.lat!!, 1e-9)
    }

    /** Ein Ausreißer wird gesetzt, nicht über Sekunden eingeblendet. */
    @Test
    fun `weit entfernte Messung wird sofort uebernommen`() {
        val r = DeadReckoner()
        r.onFix(52.0, 13.0, 0f, 2f, t0)
        r.onFix(52.01, 13.01, 0f, 2f, t0 + 1000) // gut ein Kilometer weiter
        assertEquals(52.01, r.lat!!, 1e-9)
        assertEquals(13.01, r.lon!!, 1e-9)
    }

    /** Eine nahe Messung wird dagegen weich eingerechnet. */
    @Test
    fun `nahe Messung wird eingeblendet statt gesetzt`() {
        val r = DeadReckoner()
        r.onFix(52.0, 13.0, 0f, 0f, t0)
        val ziel = 52.0 + 10.0 / DeadReckoner.M_PER_DEG_LAT // 10 m nördlich
        r.onFix(ziel, 13.0, 0f, 0f, t0 + 1000)
        // Direkt nach der Messung steht der Marker noch am alten Ort.
        assertEquals(52.0, r.lat!!, 1e-9)
        r.advance(0.1, t0 + 1000)
        val rest = metres(r.lat!!, r.lon!!, ziel, 13.0)
        assertTrue("nach einem Schritt schon dort? Rest $rest m", rest in 1.0..10.0)
        // Nach ein paar Sekunden ist er angekommen.
        repeat(200) { r.advance(0.1, t0 + 1000) }
        assertTrue(metres(r.lat!!, r.lon!!, ziel, 13.0) < 0.2)
    }

    @Test
    fun `Drehung ist begrenzt`() {
        val r = DeadReckoner()
        r.onFix(52.0, 13.0, 0f, 3f, t0)
        r.onFix(52.0, 13.0, 90f, 3f, t0 + 1000)
        r.advance(0.1, t0 + 1000)
        // Höchstens MAX_TURN_DEG_PER_S * 0,1 s = 12 Grad in einem Schritt.
        assertTrue("drehte ${r.headingDeg}° auf einmal", r.headingDeg <= 12.001f)
    }

    /**
     * Kommt die App aus dem Hintergrund, liegen zwischen zwei Bildern Minuten. Ohne
     * Deckel schösse die Schätzung kilometerweit davon.
     */
    @Test
    fun `grosser Zeitsprung wird gedeckelt`() {
        val r = DeadReckoner()
        r.onFix(52.0, 13.0, 0f, 10f, t0)
        r.advance(600.0, t0) // zehn Minuten auf einmal
        val moved = abs(r.lat!! - 52.0) * DeadReckoner.M_PER_DEG_LAT
        // 10 m/s * MAX_STEP_S statt 10 m/s * 600 s
        assertTrue("lief $moved m weit", moved <= 10.0 * DeadReckoner.MAX_STEP_S + 0.1)
    }

    @Test
    fun `ohne Bewegung und ohne Korrektur ist nichts zu tun`() {
        val r = DeadReckoner()
        r.onFix(52.0, 13.0, 0f, 0f, t0)
        assertFalse("ohne Fahrt und ohne Rest darf der Bildlauf schlafen", r.isBusy(t0))
    }

    @Test
    fun `in Fahrt gibt es immer etwas zu tun`() {
        val r = DeadReckoner()
        r.onFix(52.0, 13.0, 0f, 4f, t0)
        assertTrue(r.isBusy(t0))
    }
}
