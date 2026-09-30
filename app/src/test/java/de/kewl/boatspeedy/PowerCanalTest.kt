package de.kewl.boatspeedy

import de.kewl.boatspeedy.data.Craft
import de.kewl.boatspeedy.nav.LatLon
import de.kewl.boatspeedy.nav.MapTiles
import de.kewl.boatspeedy.nav.RouteResult
import de.kewl.boatspeedy.nav.WaterRouter
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.GZIPOutputStream

/**
 * Nachgestellt nach Dorndorf an der Saale: Ein Kanal ohne Angaben führt gerade zur
 * Wasserkraftanlage, mit einem Umtrageweg drumherum. Die Saale macht einen Bogen, hat ein
 * Wehr und am Wehr Aus- und Einstieg. Im Kanu soll es die Saale sein.
 */
class PowerCanalTest {

    private val west = LatLon(50.5, 11.00)
    private val ost = LatLon(50.5, 11.02)

    private fun weg(tags: String, vararg p: Pair<Double, Double>) =
        """{"type":"way","tags":{$tags},"geometry":[""" +
            p.joinToString(",") { """{"lat":${it.first},"lon":${it.second}}""" } + "]}"

    private fun knoten(tags: String, lat: Double, lon: Double) =
        """{"type":"node","lat":$lat,"lon":$lon,"tags":{$tags}}"""

    private val elemente = listOf(
        // Die Saale im Bogen nach Norden, über das Wehr.
        weg(""""waterway":"river","canoe":"yes"""", 50.5 to 11.00, 50.502 to 11.01, 50.5 to 11.02),
        weg(""""waterway":"weir"""", 50.5017 to 11.01, 50.502 to 11.01, 50.5023 to 11.01),
        knoten(""""canoe":"egress"""", 50.50148, 11.0092),
        knoten(""""canoe":"put_in"""", 50.50148, 11.0108),
        // Der Kanal gerade durch, die Anlage in der Mitte, drumherum getragen.
        weg(""""waterway":"canal"""", 50.5 to 11.00, 50.5 to 11.0095, 50.5 to 11.0105, 50.5 to 11.02),
        knoten(""""power":"generator","generator:source":"hydro"""", 50.5, 11.01),
        weg(""""canoe":"portage"""", 50.5 to 11.0095, 50.4997 to 11.0095, 50.4997 to 11.0105, 50.5 to 11.0105),
    )

    private fun fahre(): RouteResult.Ok {
        val dir = createTempDir("werkkanal")
        try {
            for (id in MapTiles.tilesForRoute(west, ost)) {
                val inhalt = if (id.name == "n50e011") elemente.joinToString(",") else ""
                GZIPOutputStream(File(dir, "${id.name}.json.gz").outputStream()).use {
                    it.write(
                        ("""{"version":1,"tile":"${id.name}","generated":"2026-09-30","elements":[$inhalt]}""")
                            .toByteArray(),
                    )
                }
            }
            return WaterRouter.route(west, ost, Craft.CANOE, dir) as RouteResult.Ok
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `im Kanu die Saale mit dem Wehr, nicht der Kanal zur Turbine`() {
        val r = fahre()
        assertTrue(
            "die Route muss über die Saale im Norden führen: ${r.water}",
            r.water.any { it.lat > 50.5015 },
        )
        assertTrue("und nicht über den Kanal: ${r.water}", r.water.none { it.lat < 50.4999 })
    }
}
