package de.kewl.boatspeedy

import de.kewl.boatspeedy.ui.RadarGrid
import de.kewl.boatspeedy.ui.interpolateRadar
import de.kewl.boatspeedy.ui.radarClasses
import de.kewl.boatspeedy.ui.radarFramesFor
import de.kewl.boatspeedy.ui.radarIso
import de.kewl.boatspeedy.ui.radarRunCandidates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RadarLoopTest {

    /** 20.10.2026 22:10 UTC, ein echter Lauf. */
    private val ref = 1_792_534_200_000L

    @Test
    fun `die Schleife eines Laufs reicht von der Messung bis plus zwei Stunden`() {
        val frames = radarFramesFor(ref)
        assertEquals(25, frames.size)
        assertEquals("2026-10-20T22:10:00Z", frames.first().timeIso)
        assertEquals("2026-10-21T00:10:00Z", frames.last().timeIso)
        assertEquals("", frames.first().label)
        assertEquals("+5 min", frames[1].label)
        assertEquals("+120 min", frames.last().label)
    }

    @Test
    fun `gesucht wird von der abgerundeten Uhrzeit rueckwaerts`() {
        // 22:17:52, wie bei der Messung am DWD: Der Lauf von 22:15 war noch nicht da.
        val now = ref + 7 * 60_000L + 52_000L
        val c = radarRunCandidates(now)
        assertEquals("2026-10-20T22:15:00Z", radarIso(c[0]))
        assertEquals("2026-10-20T22:10:00Z", radarIso(c[1]))
        assertEquals(7, c.size)
        assertTrue(c.zipWithNext().all { (a, b) -> a - b == 5 * 60_000L })
    }

    @Test
    fun `Farben der Leiter werden zu Stufen, alles andere ist kein Regen`() {
        val px = intArrayOf(
            0xFF33FFFF.toInt(), // schwächste Stufe
            0xFF0000FE.toInt(), // stärkste Stufe
            0xFFFFFF01.toInt(), // Stufe 7
            0x00FFFFFF, // durchsichtig
            0x4D7E7E7E, // Grau für „keine Daten", halbdurchsichtig
            0xFFFB00FF.toInt(), // violetter Rand des Messgebiets
            0x7F33FFFF, // Leiterfarbe, aber unter halber Deckkraft
        )
        assertEquals(listOf(1, 15, 7, 0, 0, 0, 0), radarClasses(px).map { it.toInt() })
    }

    private fun grid(w: Int, h: Int, vararg cells: Triple<Int, Int, Int>): RadarGrid {
        val c = ByteArray(w * h)
        for ((x, y, v) in cells) c[y * w + x] = v.toByte()
        return RadarGrid(w, h, c)
    }

    /** Die Stufe am vergrößerten Bildpunkt, der der Mitte der Zelle (x, y) entspricht. */
    private fun mitte(out: ByteArray, ow: Int, factor: Int, x: Int, y: Int): Int =
        out[(y * factor + factor / 2) * ow + x * factor + factor / 2].toInt()

    /**
     * Eine einzelne starke Zelle bleibt, was sie ist. Ein Weichzeichner hatte im Versuch
     * aus Stufe 10 eine 3 gemacht: ein Gewitter, das auf der Karte als Nieselregen erscheint.
     */
    @Test
    fun `eine einzelne starke Zelle behaelt ihre Stufe`() {
        val g = grid(20, 20, Triple(10, 10, 10))
        val f = 10
        val out = interpolateRadar(g, 0.0, 0.0, f.toDouble(), 200, 200)
        assertEquals(10, mitte(out, 200, f, 10, 10))
        assertEquals(0, mitte(out, 200, f, 2, 2))
    }

    /**
     * Ohne Begrenzung schießt die Kurve über: Ein Kern aus vier Zellen der Stufe 8 wurde
     * zur 10. Nirgends darf eine Stufe stehen, die keine der Zellen ringsum hat.
     */
    @Test
    fun `ein Kern wird nicht staerker als gemessen`() {
        val g = grid(
            20, 20,
            Triple(9, 9, 8), Triple(10, 9, 8), Triple(9, 10, 8), Triple(10, 10, 8),
        )
        val out = interpolateRadar(g, 0.0, 0.0, 10.0, 200, 200)
        assertEquals(8, out.maxOf { it.toInt() })
        assertTrue("der Kern ist zu sehen", out.count { it.toInt() == 8 } > 100)
    }

    /** Wo alle vier Zellen ringsum trocken sind, bleibt es trocken: kein Saum um Regen. */
    @Test
    fun `trockene Flaeche bleibt trocken`() {
        val g = grid(20, 20, Triple(15, 15, 6))
        val out = interpolateRadar(g, 0.0, 0.0, 8.0, 160, 160)
        // Zwei Zellen Abstand vom Regen: ganz trocken.
        for (y in 0 until 80) for (x in 0 until 160) assertEquals(0, out[y * 160 + x].toInt())
    }

    /** Ohne Vergrößerung wird jede Zelle genau übernommen, auch mit Versatz. */
    @Test
    fun `ohne Vergroesserung steht jede Zelle an ihrem Platz`() {
        val g = grid(10, 10, Triple(3, 4, 9), Triple(7, 2, 1))
        val out = interpolateRadar(g, 2.0, 1.0, 1.0, 6, 5)
        assertEquals(9, out[3 * 6 + 1].toInt())
        assertEquals(1, out[1 * 6 + 5].toInt())
        assertEquals(2, out.count { it.toInt() != 0 })
    }

    /** Ein Fenster mitten im Raster greift auf die richtigen Zellen zu. */
    @Test
    fun `Fenster mit Versatz trifft die richtigen Zellen`() {
        val g = grid(30, 30, Triple(20, 12, 5))
        val f = 4
        // Fenster ab Zelle (18, 10), 6 × 6 Zellen.
        val out = interpolateRadar(g, 18.0, 10.0, f.toDouble(), 24, 24)
        assertEquals(5, mitte(out, 24, f, 2, 2))
        assertEquals(0, mitte(out, 24, f, 5, 5))
    }
}
