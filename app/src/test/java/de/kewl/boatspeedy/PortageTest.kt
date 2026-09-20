package de.kewl.boatspeedy

import de.kewl.boatspeedy.data.Craft
import de.kewl.boatspeedy.nav.LandingKind
import de.kewl.boatspeedy.nav.LatLon
import de.kewl.boatspeedy.nav.MapTiles
import de.kewl.boatspeedy.nav.ObstacleKind
import de.kewl.boatspeedy.nav.RouteResult
import de.kewl.boatspeedy.nav.WaterRouter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.GZIPOutputStream

/**
 * Am Wehr ist über Wasser Schluss. Das Kanu kommt trotzdem weiter: außen herum, zu Fuß.
 *
 * OSM pflegt das gut. An der Saale liegen an jedem Wehr ein Ausstieg und ein Einstieg als
 * Knoten am Ufer und dazwischen ein Weg mit `whitewater=portage_way`, `canoe=portage`
 * oder `portage=designated`. Der Weg berührt den Fluss nirgends, er endet am Ufer —
 * deshalb muss die Wegsuche ihn selbst ans Wasser anschließen.
 */
class PortageTest {

    private val west = LatLon(50.5, 11.000)
    private val ost = LatLon(50.5, 11.010)

    /** Der Fluss mit Stützpunkten alle 70 m. */
    private val fluss =
        """{"type":"way","tags":{"waterway":"river","name":"Saale"},"geometry":[""" +
            (0..10).joinToString(",") { """{"lat":50.5,"lon":${11.0 + it * 0.001}}""" } +
            """]}"""

    /** Ein Wehr quer über den Fluss, das einen Flusspunkt teilt — dort ist Schluss. */
    private val wehr =
        """{"type":"way","tags":{"waterway":"weir","name":"Wehr Kahla"},"geometry":[""" +
            """{"lat":50.4999,"lon":11.005},{"lat":50.5,"lon":11.005},""" +
            """{"lat":50.5001,"lon":11.005}]}"""

    /** Der Weg außen herum. Seine Enden liegen 22 m neben dem Fluss, nicht auf ihm. */
    private val umtrageweg =
        """{"type":"way","tags":{"whitewater":"portage_way","surface":"ground"},"geometry":[""" +
            """{"lat":50.5002,"lon":11.004},{"lat":50.5004,"lon":11.005},""" +
            """{"lat":50.5002,"lon":11.006}]}"""

    private val ausstieg =
        """{"type":"node","lat":50.50015,"lon":11.0041,"tags":{"leisure":"slipway",""" +
            """"whitewater":"put_in;egress","name":"Uhlstädter Wehr"}}"""

    private fun fahre(craft: Craft, vararg elemente: String): RouteResult {
        val dir = createTempDir("umtragen")
        try {
            for (id in MapTiles.tilesForRoute(west, ost)) {
                val inhalt = if (id.name == "n50e011") elemente.joinToString(",") else ""
                GZIPOutputStream(File(dir, "${id.name}.json.gz").outputStream()).use {
                    it.write(
                        ("""{"version":1,"tile":"${id.name}","generated":"2026-09-20",""" +
                            """"elements":[$inhalt]}""").toByteArray(),
                    )
                }
            }
            return WaterRouter.route(west, ost, craft, dir)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `ohne Wehr faehrt das Kanu geradeaus, ohne zu tragen`() {
        val r = fahre(Craft.CANOE, fluss, umtrageweg) as RouteResult.Ok
        assertEquals(0.0, r.portageM, 0.5)
        assertTrue(r.portage.isEmpty())
    }

    @Test
    fun `am Wehr traegt das Kanu aussen herum`() {
        val r = fahre(Craft.CANOE, fluss, wehr, umtrageweg) as RouteResult.Ok
        assertTrue("nichts getragen: ${r.portageM} m", r.portageM > 50)
        assertTrue("der getragene Teil fehlt auf der Karte", r.portage.isNotEmpty())
        // Der Weg führt am Wehr vorbei, also nördlich daran vorbei.
        assertTrue(r.path.any { it.lat > 50.5003 })
    }

    /**
     * Ein Motorboot trägt niemand, und der Umtrageweg hilft ihm nicht. Bleibt nur das
     * Wehr: Die Strecke geht hindurch und meldet es. Eine Route, die vorher aufhört, ist
     * unbrauchbar; wer im Boot sitzt, muss selbst entscheiden, ob er umkehrt.
     */
    @Test
    fun `das Motorboot faehrt als letzter Ausweg durchs Wehr`() {
        val r = fahre(Craft.MOTORBOAT, fluss, wehr, umtrageweg) as RouteResult.Ok
        assertEquals(0.0, r.portageM, 0.5)
        assertTrue("die Strecke hört vor dem Wehr auf", r.water.any { it.lon > 11.006 })
        assertTrue("kein Wehr gemeldet", r.obstacles.any { it.kind == ObstacleKind.WEIR })
    }

    /** Gibt es einen Weg drumherum, wird er genommen — das Wehr ist der letzte Ausweg. */
    @Test
    fun `das Kanu nimmt den Weg drumherum statt durchs Wehr`() {
        val r = fahre(Craft.CANOE, fluss, wehr, umtrageweg) as RouteResult.Ok
        assertTrue("durchs Wehr statt drumherum", r.portageM > 50)
    }

    /** Das Wehr selbst wird gemeldet, auch wenn es als Weg eingetragen ist. */
    @Test
    fun `ein Wehr als Weg wird gemeldet`() {
        val r = fahre(Craft.CANOE, fluss, wehr, umtrageweg) as RouteResult.Ok
        assertTrue("kein Wehr gemeldet: ${r.obstacles}", r.obstacles.any { it.kind == ObstacleKind.WEIR })
    }

    @Test
    fun `Ein- und Ausstiege stehen auf der Karte und sind antippbar`() {
        val dir = createTempDir("ufer")
        try {
            GZIPOutputStream(File(dir, "n50e011.json.gz").outputStream()).use {
                it.write(
                    ("""{"version":1,"tile":"n50e011","generated":"2026-09-20",""" +
                        """"elements":[$ausstieg]}""").toByteArray(),
                )
            }
            val o = WaterRouter.obstaclesIn(dir, 50.4, 11.0, 50.6, 11.2).single()
            assertEquals(ObstacleKind.LANDING, o.kind)
            assertEquals(LandingKind.PUT_IN_EGRESS, o.landingKind)
            assertTrue(o.hasInfo)
        } finally {
            dir.deleteRecursively()
        }
    }
}
