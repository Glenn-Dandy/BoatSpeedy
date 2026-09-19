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
 * - **Hüntel:** zwei Kammern mit eigenen Maßen, die zweite wurde verschluckt. Jede
 *   bekommt ihr eigenes Symbol, sonst läge es zwischen beiden statt auf der Route.
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
    fun `Huentel zeigt beide Kammern, jede in ihrer Mitte`() {
        val s = schleusen(huentel)
        assertEquals(2, s.size)
        assertEquals(listOf("165", "225"), s.mapNotNull { it.maxLengthM }.sorted())
        assertTrue(s.all { it.hasInfo && it.name == "Schleuse Hüntel" })
        val klein = s.single { it.maxLengthM == "165" }
        assertTrue(klein.abstand((52.75591 + 52.75746) / 2, (7.25818 + 7.25768) / 2) < 3)
    }

    @Test
    fun `Hilter zeigt die Kammern und nicht die Tore`() {
        val s = schleusen(hilter)
        assertEquals(2, s.size)
        assertTrue("Tore dürfen nicht übrig bleiben", s.none { it.isGate })
        assertEquals(listOf("10", "12"), s.mapNotNull { it.maxWidthM }.sorted())
    }

    /** Dieselbe Kammer als Linie und als Fläche ist ein Symbol, auf der Linie. */
    @Test
    fun `Linie und Flaeche derselben Kammer werden eins`() {
        val linie =
            """{"type":"way","tags":{"waterway":"canal","lock":"yes","lock_name":"Schleuse X"},""" +
                """"geometry":[{"lat":52.50015,"lon":7.3},{"lat":52.50015,"lon":7.302}]}"""
        val becken =
            """{"type":"way","tags":{"seamark:type":"lock_basin","seamark:lock_basin:category":"x"},""" +
                """"geometry":[{"lat":52.5,"lon":7.3},{"lat":52.5,"lon":7.302},""" +
                """{"lat":52.5003,"lon":7.302},{"lat":52.5003,"lon":7.3},{"lat":52.5,"lon":7.3}]}"""
        val s = schleusen("$linie,$becken").single()
        assertEquals("Schleuse X", s.name)
        assertTrue(s.abstand(52.50015, 7.301) < 3)
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
     * Die Kaiserschleuse etwa ist nur als Fläche mit `seamark:type=lock_basin` erfasst,
     * ohne `lock=yes`, der Name steht unter `seamark:name`.
     */
    @Test
    fun `eine Kammer als Flaeche zaehlt und sitzt in ihrer Mitte`() {
        val becken =
            """{"type":"way","tags":{"seamark:type":"lock_basin","seamark:name":"Kaiserschleuse"},""" +
                """"geometry":[{"lat":52.5,"lon":7.3},{"lat":52.5,"lon":7.302},""" +
                """{"lat":52.5003,"lon":7.302},{"lat":52.5003,"lon":7.3},{"lat":52.5,"lon":7.3}]}"""
        val tor = """{"type":"node","tags":{"waterway":"lock_gate"},"lat":52.50015,"lon":7.3}"""
        val s = schleusen("$becken,$tor").single()
        assertEquals("Kaiserschleuse", s.name)
        assertTrue("${s.lat},${s.lon}", s.abstand(52.50015, 7.301) < 5)
    }

    /**
     * Die Route fährt durch die große Kammer. Ihr Symbol liegt auf der Route, an derselben
     * Stelle wie auf der Karte, und die kleine daneben bleibt als eigenes Symbol stehen.
     */
    @Test
    fun `die Route zeigt die befahrene Kammer von Huentel`() {
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
            assertEquals("225", aufRoute.maxLengthM)
            assertTrue(aufRoute.abstand((52.75551 + 52.75756) / 2, (7.25925 + 7.25858) / 2) < 3)
            val aufKarte = WaterRouter.obstaclesIn(dir, 52.1, 7.1, 52.9, 7.9)
                .filter { it.kind == ObstacleKind.LOCK }
            assertEquals(2, aufKarte.size)
            assertTrue(aufKarte.any { it.abstand(aufRoute.lat, aufRoute.lon) < 1 })
        } finally {
            dir.deleteRecursively()
        }
    }
}
