package de.kewl.boatspeedy

import de.kewl.boatspeedy.data.Craft
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
 * Nachgestellt nach dem Burgauer Wehr in Jena: Aus- und Einstieg liegen dicht beieinander,
 * das Wehr dazwischen, und ein eingetragener Umtrageweg führt im Bogen von einem zum
 * anderen. Die Route soll den Pfad nehmen und nicht die gerade Linie über das Wehr.
 */
class PortagePathTest {

    private val sued = LatLon(50.495, 11.00)
    private val nord = LatLon(50.505, 11.00)

    private fun weg(tags: String, vararg p: Pair<Double, Double>) =
        """{"type":"way","tags":{$tags},"geometry":[""" +
            p.joinToString(",") { """{"lat":${it.first},"lon":${it.second}}""" } + "]}"

    private fun knoten(tags: String, lat: Double, lon: Double) =
        """{"type":"node","lat":$lat,"lon":$lon,"tags":{$tags}}"""

    private val elemente = listOf(
        // Der Fluss nach Norden; das Wehr quert ihn und teilt einen Punkt mit ihm.
        weg(""""waterway":"river","canoe":"yes"""", 50.495 to 11.00, 50.4995 to 11.00, 50.500 to 11.00, 50.5005 to 11.00, 50.505 to 11.00),
        weg(""""waterway":"weir"""", 50.500 to 10.9995, 50.500 to 11.00, 50.500 to 11.0005),
        // Ausstieg und Einstieg am Ostufer, direkt am Fluss.
        knoten(""""canoe":"egress"""", 50.4995, 11.00015),
        knoten(""""canoe":"put_in"""", 50.5005, 11.00015),
        // Der Pfad holt nach Osten aus, rund 190 m.
        weg(
            """"canoe":"portage"""",
            50.4995 to 11.00015, 50.4995 to 11.0008, 50.5005 to 11.0008, 50.5005 to 11.00015,
        ),
    )

    private fun fahre(): RouteResult.Ok {
        val dir = createTempDir("umtragepfad")
        try {
            for (id in MapTiles.tilesForRoute(sued, nord)) {
                val inhalt = if (id.name == "n50e011") elemente.joinToString(",") else ""
                GZIPOutputStream(File(dir, "${id.name}.json.gz").outputStream()).use {
                    it.write(
                        ("""{"version":1,"tile":"${id.name}","generated":"2026-09-30","elements":[$inhalt]}""")
                            .toByteArray(),
                    )
                }
            }
            return WaterRouter.route(sued, nord, Craft.CANOE, dir) as RouteResult.Ok
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `der eingetragene Pfad schlaegt die gerade Linie`() {
        val r = fahre()
        assertTrue("über den Pfad im Osten: ${r.water}", r.water.any { it.lon > 11.0007 })
        assertEquals(
            "das umgetragene Wehr zählt einmal: ${r.obstacles}",
            1, r.obstacles.count { it.kind == ObstacleKind.WEIR },
        )
    }
}
