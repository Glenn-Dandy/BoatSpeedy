package de.kewl.boatspeedy

import de.kewl.boatspeedy.data.Craft
import de.kewl.boatspeedy.nav.LatLon
import de.kewl.boatspeedy.nav.MapTiles
import de.kewl.boatspeedy.nav.RouteResult
import de.kewl.boatspeedy.nav.WaterRouter
import de.kewl.boatspeedy.nav.flowMarks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.GZIPOutputStream

/**
 * OpenStreetMap zeichnet Flüsse **stromab**: Der erste Punkt eines `waterway=river` liegt
 * oberhalb. Bei der Saale laufen 33 von 37 längeren Abschnitten nach Norden, die übrigen
 * sind Mäander. Kanäle stehen still, ihre Zeichenrichtung ist Zufall.
 */
class FlowTest {

    private val west = LatLon(50.5, 11.00)
    private val ost = LatLon(50.5, 11.10)

    /** Ein Fluss, von West nach Ost gezeichnet — er fließt also nach Osten. */
    private val fluss =
        """{"type":"way","tags":{"waterway":"river"},"geometry":[""" +
            (0..10).joinToString(",") { """{"lat":50.5,"lon":${11.0 + it * 0.01}}""" } + "]}"

    private val kanal =
        """{"type":"way","tags":{"waterway":"canal"},"geometry":[""" +
            (0..10).joinToString(",") { """{"lat":50.5,"lon":${11.0 + it * 0.01}}""" } + "]}"

    private fun kacheln(weg: String): File {
        val dir = createTempDir("strom")
        for (id in MapTiles.tilesForRoute(west, ost)) {
            val el = if (id.name == "n50e011") weg else ""
            GZIPOutputStream(File(dir, "${id.name}.json.gz").outputStream()).use {
                it.write("""{"version":1,"tile":"${id.name}","generated":"2026-09-19","elements":[$el]}""".toByteArray())
            }
        }
        return dir
    }

    private fun fahre(weg: String, von: LatLon, nach: LatLon): RouteResult.Ok {
        val dir = kacheln(weg)
        try {
            return WaterRouter.route(von, nach, Craft.CANOE, dir) as RouteResult.Ok
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `mit dem Fluss ist flussab`() {
        val r = fahre(fluss, west, ost)
        assertTrue("flussab war ${r.downstreamM}", r.downstreamM > 6_000)
        assertEquals(0.0, r.upstreamM, 1.0)
    }

    @Test
    fun `gegen den Fluss ist flussauf`() {
        val r = fahre(fluss, ost, west)
        assertTrue("flussauf war ${r.upstreamM}", r.upstreamM > 6_000)
        assertEquals(0.0, r.downstreamM, 1.0)
    }

    /** Ein Kanal steht still. Eine Zahl dafür wäre erfunden. */
    @Test
    fun `ein Kanal zaehlt weder auf noch ab`() {
        val r = fahre(kanal, west, ost)
        assertEquals(0.0, r.upstreamM, 1e-6)
        assertEquals(0.0, r.downstreamM, 1e-6)
    }

    @Test
    fun `nur Fluesse kommen als Stroemung auf die Karte`() {
        val dir = kacheln("$fluss,$kanal")
        try {
            val flüsse = WaterRouter.riversIn(dir, 50.4, 11.0, 50.6, 11.2)
            assertEquals("nur der Fluss, nicht der Kanal", 1, flüsse.size)
            // Die Reihenfolge bleibt stromab: erster Punkt im Westen.
            assertTrue(flüsse.first().first().lon < flüsse.first().last().lon)
        } finally {
            dir.deleteRecursively()
        }
    }

    /**
     * Die Winkel sitzen in gleichen Abständen **auf dem Bildschirm**, nicht in Metern — sonst
     * stünden sie beim Hineinzoomen weit auseinander und beim Herauszoomen als Brei.
     */
    @Test
    fun `Winkel sitzen in gleichen Abstaenden und zeigen stromab`() {
        // 500 Punkte lange Gerade nach rechts, alle 100 ein Winkel, der erste bei 50.
        val m = flowMarks(doubleArrayOf(0.0, 500.0), doubleArrayOf(0.0, 0.0), step = 100.0)
        assertEquals(5, m.size)
        assertEquals(50.0, m.first().x, 1e-9)
        assertEquals(450.0, m.last().x, 1e-9)
        assertTrue("alle zeigen nach rechts", m.all { kotlin.math.abs(it.angleDeg) < 1e-9 })
    }

    /** Über einen Knick hinweg läuft der Abstand weiter, statt an jeder Ecke neu anzufangen. */
    @Test
    fun `der Abstand laeuft ueber Knicke hinweg`() {
        // 150 nach rechts, dann 150 nach unten.
        val m = flowMarks(
            doubleArrayOf(0.0, 150.0, 150.0),
            doubleArrayOf(0.0, 0.0, 150.0),
            step = 100.0,
        )
        assertEquals(3, m.size)
        assertEquals(50.0, m[0].x, 1e-9)
        // Der zweite liegt 100 weiter: 100 nach rechts bis zur Ecke, dann 0 nach unten.
        assertEquals(150.0, m[1].x, 1e-9)
        assertEquals(0.0, m[1].y, 1e-9)
        // Der dritte liegt 100 weiter, also 100 nach unten, und zeigt nach unten.
        assertEquals(100.0, m[2].y, 1e-9)
        assertEquals(90.0, m[2].angleDeg, 1e-9)
    }
}
