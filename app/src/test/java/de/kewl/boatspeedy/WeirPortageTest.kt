package de.kewl.boatspeedy

import de.kewl.boatspeedy.data.Craft
import de.kewl.boatspeedy.nav.LatLon
import de.kewl.boatspeedy.nav.MapTiles
import de.kewl.boatspeedy.nav.ObstacleKind
import de.kewl.boatspeedy.nav.RouteResult
import de.kewl.boatspeedy.nav.WaterRouter
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.GZIPOutputStream

/**
 * Drei Wehre an der Saale, mit den Daten aus OpenStreetMap (Kachel n50e011).
 *
 * Beide liegen als **Weg** quer über den Fluss und teilen einen Punkt mit ihm. Genau die
 * fehlten in unseren Kacheln: Der Filter holte Wehre nur als Knoten, und so stand vor dem
 * Saale-Wehr Uhlstädt und dem Paradieswehr in Jena kein Wort.
 *
 * Die drei Stellen gehen verschieden aus. In Uhlstädt führt ein Kraftwerkskanal ums Wehr
 * herum, dort wird nichts getragen. In Jena gibt es nur den Weg über Land, mit
 * `portage=designated` an den beiden Rampen. In Kahla steht überhaupt kein Weg in OSM,
 * sondern nur zwei Slipanlagen mit dem Wehr dazwischen.
 */
class WeirPortageTest {

    /** Oberhalb des Wehrs auf der Saale. */
    private val oben = LatLon(50.74232, 11.45835)

    /** Unterhalb, rund 300 m hinter der Wehrschwelle. */
    private val unten = LatLon(50.73854, 11.46342)

    /** Oberhalb des Wehrs Kahla. */
    private val kahlaOben = LatLon(50.79508, 11.57369)

    /** Unterhalb, gut einen halben Kilometer weiter. */
    private val kahlaUnten = LatLon(50.80113, 11.59127)

    /** Oberhalb des Paradieswehrs in Jena. */
    private val jenaOben = LatLon(50.92299, 11.58690)

    /** Unterhalb, hinter der Wehrschwelle. */
    private val jenaUnten = LatLon(50.92700, 11.59300)

    private fun fahre(craft: Craft, kachel: String = "uhlstaedt.json"): RouteResult =
        fahre(craft, kachel, oben, unten)

    private fun fahre(craft: Craft, kachel: String, von: LatLon, nach: LatLon): RouteResult {
        val inhalt = javaClass.classLoader!!.getResource(kachel)!!.readText()
        val dir = createTempDir("uhlstaedt")
        try {
            for (id in MapTiles.tilesForRoute(von, nach)) {
                val text = if (id.name == "n50e011") {
                    inhalt
                } else {
                    """{"version":1,"tile":"${id.name}","generated":"2026-09-20","elements":[]}"""
                }
                GZIPOutputStream(File(dir, "${id.name}.json.gz").outputStream()).use {
                    it.write(text.toByteArray())
                }
            }
            return WaterRouter.route(von, nach, craft, dir)
        } finally {
            dir.deleteRecursively()
        }
    }

    /**
     * In Jena führt kein Wasser ums Wehr. Die Route geht über die beiden Rampen an Land,
     * rund fünfzig Meter am Ufer entlang und wieder hinein.
     */
    @Test
    fun `in Jena traegt das Kanu ums Paradieswehr`() {
        val r = fahre(Craft.CANOE, "jena.json", jenaOben, jenaUnten) as RouteResult.Ok
        assertTrue("nichts getragen: ${r.portageM} m", r.portageM > 50)
        assertTrue("der getragene Teil fehlt auf der Karte", r.portage.isNotEmpty())
        // Oberhalb losgefahren und unterhalb angekommen, nicht am Wehr stehen geblieben.
        assertTrue("Route endet zu früh: ${r.water.last()}", r.water.last().lat > 50.9260)
    }

    /**
     * Für ein Motorboot gibt es in Jena keine Strecke: Die Saale trägt dort `boat=no`,
     * und über Land wird nur ein Kanu getragen.
     */
    @Test
    fun `in Jena faehrt kein Motorboot`() {
        assertTrue(fahre(Craft.MOTORBOAT, "jena.json", jenaOben, jenaUnten) is RouteResult.Failed)
    }

    /**
     * In Kahla gibt es keinen Umtrageweg, nur zwei Slipanlagen mit dem Wehr dazwischen.
     * Ohne eine Verbindung zwischen ihnen brach die Route am Wehr ab.
     */
    @Test
    fun `in Kahla traegt das Kanu von Anleger zu Anleger`() {
        val r = fahre(Craft.CANOE, "kahla.json", kahlaOben, kahlaUnten) as RouteResult.Ok
        assertTrue("nichts getragen: ${r.portageM} m", r.portageM > 30)
        assertTrue("Route endet zu früh: ${r.water.last()}", r.water.last().lat > 50.800)
    }

    /** In Uhlstädt führt der Kraftwerkskanal ums Wehr: Das Kanu fährt, statt zu tragen. */
    @Test
    fun `in Uhlstaedt fuehrt Wasser ums Wehr`() {
        val r = fahre(Craft.CANOE) as RouteResult.Ok
        assertTrue("hier wird nicht getragen: ${r.portageM} m", r.portageM == 0.0)
        assertTrue("Route endet zu früh: ${r.water.last()}", r.water.last().lat < 50.7395)
    }

    @Test
    fun `das Wehr wird gemeldet`() {
        val r = fahre(Craft.CANOE) as RouteResult.Ok
        assertTrue("kein Wehr: ${r.obstacles}", r.obstacles.any { it.kind == ObstacleKind.WEIR })
    }

    /** Unterhalb von Uhlstädt liegt auf der Saale ein Bootsverbot: Dort endet das Motorboot. */
    @Test
    fun `das Motorboot endet in Uhlstaedt am Kraftwerkskanal`() {
        val r = fahre(Craft.MOTORBOAT) as RouteResult.Ok
        assertTrue("getragen wurde nichts", r.portageM == 0.0)
        assertTrue("zu weit gekommen: ${r.water.last()}", r.water.last().lat > 50.7400)
    }
}
