package de.kewl.boatspeedy

import de.kewl.boatspeedy.data.Craft
import de.kewl.boatspeedy.nav.LatLon
import de.kewl.boatspeedy.nav.MapTiles
import de.kewl.boatspeedy.nav.RouteResult
import de.kewl.boatspeedy.nav.WaterRouter
import de.kewl.boatspeedy.nav.distanceM
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.GZIPOutputStream

/**
 * Am alten Zeiss-Wehr in Saalfeld, mit den Daten der Kachel n50e011 vom 05.10.2026.
 *
 * Ein Wehr steht dort nicht mehr in OSM, nur ein kurzes Stück Saale mit `canoe=no`. Oberhalb
 * und unterhalb liegt je ein Anleger, über `waterway=link` an den Fluss angebunden. Der
 * Umtrageweg dazwischen hat in dieser Kachel eine Lücke von 200 m. Die Route soll trotzdem
 * von Anleger zu Anleger kommen, statt als Luftlinie zu enden.
 */
class BlockedStretchTest {

    /** Oberhalb, gut 500 m vor dem gesperrten Stück. */
    private val oben = LatLon(50.64649, 11.36981)

    /** Unterhalb, gut 500 m dahinter. */
    private val unten = LatLon(50.65611, 11.36195)

    private fun fahre(craft: Craft): RouteResult {
        val inhalt = javaClass.classLoader!!.getResource("zeisswehr.json")!!.readText()
        val dir = createTempDir("zeisswehr")
        try {
            for (id in MapTiles.tilesForRoute(oben, unten)) {
                val text = if (id.name == "n50e011") {
                    inhalt
                } else {
                    """{"version":1,"tile":"${id.name}","generated":"2026-10-05","elements":[]}"""
                }
                GZIPOutputStream(File(dir, "${id.name}.json.gz").outputStream()).use {
                    it.write(text.toByteArray())
                }
            }
            return WaterRouter.route(oben, unten, craft, dir)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `das Kanu traegt von Anleger zu Anleger um das gesperrte Stueck`() {
        val r = fahre(Craft.CANOE) as RouteResult.Ok
        assertTrue("nichts getragen: ${r.portageM} m", r.portageM > 0)
        assertTrue(
            "das Fahrwasser endet vor dem Ziel: ${r.water.last()}",
            distanceM(r.water.last(), unten) < 50,
        )
    }
}
