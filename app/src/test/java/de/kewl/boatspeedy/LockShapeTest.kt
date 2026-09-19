package de.kewl.boatspeedy

import de.kewl.boatspeedy.data.Craft
import de.kewl.boatspeedy.nav.LatLon
import de.kewl.boatspeedy.nav.MapTiles
import de.kewl.boatspeedy.nav.Obstacle
import de.kewl.boatspeedy.nav.ObstacleKind
import de.kewl.boatspeedy.nav.RouteResult
import de.kewl.boatspeedy.nav.WaterRouter
import de.kewl.boatspeedy.nav.distanceM
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.GZIPOutputStream

/**
 * Eine Schleuse ist **ein** Symbol, in der Mitte ihrer Kammer.
 *
 * Am Dortmund-Ems-Kanal standen zwei Symbole je Schleuse, beide auf den Toren, und oft
 * war nur eines anklickbar. Die Daten hier sind aus OSM übernommen (Kachel n52e007):
 *
 * - **Hanekenfähr:** Kammer aus nur zwei Punkten, das Symbol lag auf ihrem Ende.
 * - **Hüntel:** zwei Kammern mit eigenen Maßen, die zweite wurde verschluckt.
 * - **Hilter:** die Tore tragen den Namen und gewannen gegen die Kammer mit den Maßen.
 */
class LockShapeTest {

    private val hanekenfaehr = listOf(
        """{"type":"node","tags":{"seamark:type":"gate","waterway":"lock_gate"},"lat":52.47413,"lon":7.30287}""",
        """{"type":"node","tags":{"seamark:type":"gate","waterway":"lock_gate"},"lat":52.47582,"lon":7.30175}""",
        """{"type":"way","tags":{"CEMT":"IV","lock":"yes","name":"Sperrschleuse Hanekenfähr","waterway":"canal"},"geometry":[{"lat":52.47413,"lon":7.30287},{"lat":52.47582,"lon":7.30175}]}""",
    ).joinToString(",")

    private val huentel = listOf(
        """{"type":"way","tags":{"CEMT":"IV","lock":"yes","lock_name":"Schleuse Hüntel","maxlength":"165","maxwidth":"10","name":"Dortmund-Ems-Kanal","opening_hours":"Mo-Sa 06:00-22:00; Su 06:00-14:00","phone":"+49 5931 848110","vhf":"82","waterway":"canal"},"geometry":[{"lat":52.75591,"lon":7.25818},{"lat":52.75746,"lon":7.25768}]}""",
        """{"type":"way","tags":{"CEMT":"IV","lock":"yes","lock_name":"Schleuse Hüntel","maxlength":"225","maxwidth":"12","name":"Dortmund-Ems-Kanal","opening_hours":"Mo-Sa 06:00-22:00; Su 06:00-14:00","phone":"+49 5931 848110","vhf":"82","waterway":"canal"},"geometry":[{"lat":52.75551,"lon":7.25925},{"lat":52.75756,"lon":7.25858}]}""",
        """{"type":"way","tags":{"waterway":"lock_gate"},"geometry":[{"lat":52.75592,"lon":7.25808},{"lat":52.75591,"lon":7.25818},{"lat":52.75594,"lon":7.25825}]}""",
        """{"type":"way","tags":{"waterway":"lock_gate"},"geometry":[{"lat":52.7576,"lon":7.25868},{"lat":52.75756,"lon":7.25858},{"lat":52.75757,"lon":7.25848}]}""",
        """{"type":"way","tags":{"waterway":"lock_gate"},"geometry":[{"lat":52.75554,"lon":7.25934},{"lat":52.75551,"lon":7.25925},{"lat":52.75552,"lon":7.25914}]}""",
        """{"type":"way","tags":{"waterway":"lock_gate"},"geometry":[{"lat":52.75747,"lon":7.25759},{"lat":52.75746,"lon":7.25768},{"lat":52.7575,"lon":7.25777}]}""",
    ).joinToString(",")

    private val hilter = listOf(
        """{"type":"node","tags":{"name":"Schleuse Hilter","seamark:type":"gate","waterway":"lock_gate"},"lat":52.8314,"lon":7.27633}""",
        """{"type":"node","tags":{"name":"Schleuse Hilter","seamark:type":"gate","waterway":"lock_gate"},"lat":52.83156,"lon":7.2759}""",
        """{"type":"node","tags":{"name":"Schleuse Hilter","seamark:type":"gate","waterway":"lock_gate"},"lat":52.83292,"lon":7.27716}""",
        """{"type":"node","tags":{"seamark:type":"gate","waterway":"lock_gate"},"lat":52.83277,"lon":7.27761}""",
        """{"type":"way","tags":{"CEMT":"IV","lock":"yes","lock_name":"Schleuse Hilter","maxlength":"165","maxwidth":"10","name":"Dortmund-Ems-Kanal","opening_hours":"Mo-Sa 06:00-22:00; Su 06:00-14:00","phone":"+49 5933 1684","vhf":"81","waterway":"river"},"geometry":[{"lat":52.8316,"lon":7.27593},{"lat":52.83292,"lon":7.27716},{"lat":52.83302,"lon":7.27725}]}""",
        """{"type":"way","tags":{"CEMT":"IV","lock":"yes","lock_name":"Schleuse Hilter","maxlength":"165","maxwidth":"12","name":"Dortmund-Ems-Kanal","opening_hours":"Mo-Sa 06:00-22:00; Su 06:00-14:00","phone":"+49 5933 1684","vhf":"81","waterway":"river"},"geometry":[{"lat":52.83144,"lon":7.27637},{"lat":52.83277,"lon":7.27761},{"lat":52.83288,"lon":7.27771}]}""",
        """{"type":"way","tags":{"waterway":"lock_gate"},"geometry":[{"lat":52.83298,"lon":7.27735},{"lat":52.83302,"lon":7.27725},{"lat":52.83305,"lon":7.27717}]}""",
        """{"type":"way","tags":{"waterway":"lock_gate"},"geometry":[{"lat":52.83283,"lon":7.27783},{"lat":52.83288,"lon":7.27771},{"lat":52.83291,"lon":7.27761}]}""",
        """{"type":"way","tags":{"waterway":"lock_gate"},"geometry":[{"lat":52.83142,"lon":7.27651},{"lat":52.83144,"lon":7.27637},{"lat":52.83149,"lon":7.2763}]}""",
        """{"type":"way","tags":{"waterway":"lock_gate"},"geometry":[{"lat":52.83156,"lon":7.27601},{"lat":52.8316,"lon":7.27593},{"lat":52.83163,"lon":7.27585}]}""",
    ).joinToString(",")

    private fun schleusen(elemente: String): List<Obstacle> {
        val dir = createTempDir("schleusen")
        try {
            GZIPOutputStream(File(dir, "n52e007.json.gz").outputStream()).use {
                it.write(
                    ("""{"version":1,"tile":"n52e007","generated":"2026-09-19",""" +
                        """"elements":[$elemente]}""").toByteArray(),
                )
            }
            return WaterRouter.obstaclesIn(dir, 52.1, 7.1, 52.9, 7.9)
                .filter { it.kind == ObstacleKind.LOCK }
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun Obstacle.abstand(lat: Double, lon: Double) = distanceM(LatLon(this.lat, this.lon), LatLon(lat, lon))

    @Test
    fun `Hanekenfaehr liegt in der Mitte der Kammer, nicht auf dem Tor`() {
        val s = schleusen(hanekenfaehr).single()
        assertEquals("Sperrschleuse Hanekenfähr", s.name)
        // Mitte zwischen den beiden Kammerenden, die zugleich die Tore sind.
        assertTrue("${s.lat},${s.lon}", s.abstand(52.474975, 7.30231) < 5)
        assertTrue(s.abstand(52.47413, 7.30287) > 90)
    }

    @Test
    fun `Huentel ist eine Schleuse mit beiden Kammern`() {
        val s = schleusen(huentel).single()
        assertEquals("Schleuse Hüntel", s.name)
        assertEquals(listOf("165 × 10", "225 × 12"), s.chamberSizes.sorted())
        assertTrue(s.hasInfo)
    }

    @Test
    fun `Hilter zeigt die Kammer und nicht das Tor`() {
        val s = schleusen(hilter).single()
        assertEquals("Schleuse Hilter", s.name)
        assertEquals(2, s.chamberSizes.size)
        assertTrue("Tore dürfen nicht übrig bleiben", !s.isGate)
    }

    /** Ganz ohne Kammer bleiben die Tore — aber als eine Schleuse zwischen ihnen. */
    @Test
    fun `Tore ohne Kammer werden ein Symbol in ihrer Mitte`() {
        val tore =
            """{"type":"node","tags":{"waterway":"lock_gate"},"lat":52.5,"lon":7.3},""" +
                """{"type":"node","tags":{"waterway":"lock_gate"},"lat":52.502,"lon":7.3}"""
        val s = schleusen(tore).single()
        assertTrue(s.abstand(52.501, 7.3) < 5)
    }

    /**
     * Die Route fährt nur durch **eine** Kammer. Trotzdem gehört die andere dazu, und das
     * Symbol der Route muss dort sitzen, wo die Karte es ohne Route zeigt.
     */
    @Test
    fun `die Route zeigt Huentel an derselben Stelle wie die Karte`() {
        val start = LatLon(52.7505, 7.2600)
        val ziel = LatLon(52.7645, 7.2562)
        val zufahrten =
            """{"type":"way","tags":{"waterway":"canal"},"geometry":[""" +
                """{"lat":52.7505,"lon":7.2600},{"lat":52.75551,"lon":7.25925}]},""" +
                """{"type":"way","tags":{"waterway":"canal"},"geometry":[""" +
                """{"lat":52.75756,"lon":7.25858},{"lat":52.7645,"lon":7.2562}]}"""
        val dir = createTempDir("route")
        try {
            for (id in MapTiles.tilesForRoute(start, ziel)) {
                val elemente = if (id.name == "n52e007") "$zufahrten,$huentel" else ""
                GZIPOutputStream(File(dir, "${id.name}.json.gz").outputStream()).use {
                    it.write(
                        ("""{"version":1,"tile":"${id.name}","generated":"2026-09-19",""" +
                            """"elements":[$elemente]}""").toByteArray(),
                    )
                }
            }
            val r = WaterRouter.route(start, ziel, Craft.MOTORBOAT, dir) as RouteResult.Ok
            val aufRoute = r.obstacles.single { it.kind == ObstacleKind.LOCK }
            val aufKarte = WaterRouter.obstaclesIn(dir, 52.1, 7.1, 52.9, 7.9)
                .single { it.kind == ObstacleKind.LOCK }
            assertEquals(2, aufRoute.chamberSizes.size)
            assertTrue(aufRoute.abstand(aufKarte.lat, aufKarte.lon) < 1)
        } finally {
            dir.deleteRecursively()
        }
    }
}
