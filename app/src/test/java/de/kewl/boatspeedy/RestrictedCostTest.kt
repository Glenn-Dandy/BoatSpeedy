package de.kewl.boatspeedy

import de.kewl.boatspeedy.data.Craft
import de.kewl.boatspeedy.nav.LatLon
import de.kewl.boatspeedy.nav.MapTiles
import de.kewl.boatspeedy.nav.ObstacleKind
import de.kewl.boatspeedy.nav.RouteResult
import de.kewl.boatspeedy.nav.WaterRouter
import de.kewl.boatspeedy.nav.pathLengthM
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.GZIPOutputStream

/**
 * Gesperrte Abschnitte kosten bei der Wegsuche das Dreifache — nur beim Vergleichen.
 *
 * Bei Wettin an der Saale nahm die Kanu-Route den **Kraftwerksgraben** (`boat=no`,
 * 1598 m) statt des Schleusenarms, weil der 27 m länger war. Der Wegsuche sind Meter
 * gleich viel wert; dass auf dem einen ein Bootsverbot liegt, spielte bei der Wahl keine
 * Rolle, seit es fürs Kanu nur noch ein Hinweis ist.
 *
 * Hier nachgebaut: zwei Wege zwischen denselben zwei Punkten, der gesperrte etwas kürzer.
 */
class RestrictedCostTest {

    private val west = LatLon(50.5, 11.00)
    private val ost = LatLon(50.5, 11.10)

    /** Der kurze Weg — geradeaus, aber mit Bootsverbot. */
    private fun graben() =
        """{"type":"way","tags":{"waterway":"river","name":"Kraftwerksgraben","boat":"no"},""" +
            """"geometry":[{"lat":50.5,"lon":11.0},{"lat":50.5,"lon":11.1}]}"""

    /** Der freie Weg — über einen Bogen nach Norden, also etwas länger. */
    private fun arm(nord: Double = 50.508) =
        """{"type":"way","tags":{"waterway":"canal","name":"Schleusenarm","boat":"yes"},""" +
            """"geometry":[{"lat":50.5,"lon":11.0},{"lat":$nord,"lon":11.05},""" +
            """{"lat":50.5,"lon":11.1}]}"""

    private fun kacheln(vararg wege: String): File {
        val dir = createTempDir("aufschlag")
        for (id in MapTiles.tilesForRoute(west, ost)) {
            val elemente = if (id.name == "n50e011") wege.joinToString(",") else ""
            GZIPOutputStream(File(dir, "${id.name}.json.gz").outputStream()).use {
                it.write(
                    ("""{"version":1,"tile":"${id.name}","generated":"2026-09-09",""" +
                        """"elements":[$elemente]}""").toByteArray(),
                )
            }
        }
        return dir
    }

    private fun fahre(vararg wege: String): RouteResult {
        val dir = kacheln(*wege)
        try {
            return WaterRouter.route(west, ost, Craft.CANOE, dir)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `bei freier Alternative wird der gesperrte Weg gemieden`() {
        val r = fahre(graben(), arm()) as RouteResult.Ok
        assertEquals("die Route darf nicht durch den gesperrten Weg gehen", 0.0, r.restrictedM, 1.0)
        // Der Bogen ist länger als die Gerade — genau das nimmt sie in Kauf.
        assertTrue("war ${pathLengthM(r.path)} m", pathLengthM(r.path) > 7_000)
    }

    @Test
    fun `ohne Alternative wird der gesperrte Weg genommen`() {
        val r = fahre(graben()) as RouteResult.Ok
        assertTrue("die Strecke muss als gesperrt gemeldet werden", r.restrictedM > 6_000)
        assertEquals(1, r.restricted.size)
    }

    /**
     * Der Aufschlag ist kein Verbot. Ist der freie Weg mehr als dreimal so lang, bleibt es
     * beim gesperrten — sonst paddelte man zehn Kilometer, um 500 m Verbot auszuweichen.
     */
    @Test
    fun `ein unverhaeltnismaessiger Umweg wird nicht genommen`() {
        // Bogen weit nach Norden: rund das Sechsfache der Geraden.
        val r = fahre(graben(), arm(nord = 50.66)) as RouteResult.Ok
        assertTrue("hier hätte der Graben gewinnen müssen", r.restrictedM > 6_000)
    }

    /**
     * Der durchgehende Kanal mit Stützpunkten alle 1,1 km. Ein Hindernis gilt als „liegt
     * auf der Route", wenn es nahe an einem **Stützpunkt** liegt — mit nur zwei Punkten
     * am Anfang und Ende fiele die Schleuse in der Mitte durch, obwohl man mitten durch
     * sie hindurchfährt.
     */
    private val kanal =
        """{"type":"way","tags":{"waterway":"canal"},"geometry":[""" +
            (0..10).joinToString(",") { """{"lat":50.5,"lon":${11.0 + it * 0.01}}""" } +
            """,{"lat":50.5,"lon":11.051}]}"""

    /**
     * Schleusen liegen in OSM als **Weg** vor: die Kammer als `waterway=canal` mit
     * `lock=yes`, daran Name, Öffnungszeiten und Telefon. Wer nur auf `waterway` schaut,
     * findet höchstens die Tore — und die wissen nichts.
     */
    @Test
    fun `eine Schleuse als Weg wird samt Auskunft erkannt`() {
        val schleuse =
            """{"type":"way","tags":{"waterway":"canal","lock":"yes",""" +
                """"lock_name":"Schleuse Wettin","opening_hours":"Apr-Oct Mo-Th 07:00-19:00",""" +
                """"phone":"+49 3471 34664081","vhf":"21","maxlength":"103","maxwidth":"12",""" +
                """"CEMT":"IV"},"geometry":[{"lat":50.5,"lon":11.05},{"lat":50.5,"lon":11.051}]}"""
        val r = fahre(kanal, schleuse) as RouteResult.Ok
        val o = r.obstacles.singleOrNull { it.kind == ObstacleKind.LOCK }
        assertTrue("keine Schleuse gefunden: ${r.obstacles}", o != null)
        assertEquals("Schleuse Wettin", o!!.name)
        assertEquals("Apr-Oct Mo-Th 07:00-19:00", o.openingHours)
        assertEquals("+49 3471 34664081", o.phone)
        assertEquals("21", o.vhf)
        assertEquals("103", o.maxLengthM)
        assertEquals("IV", o.cemt)
        assertTrue(o.hasInfo)
    }

    /**
     * Kammer und beide Tore stünden sonst dreimal übereinander, und zwei davon wüssten
     * nichts. Übrig bleibt die mit der Auskunft.
     */
    @Test
    fun `Kammer und Tore werden zu einer Schleuse zusammengelegt`() {
        // Eine Kammer von rund 70 m, an jedem Ende ein Tor — so sieht es in OSM aus.
        val kammer =
            """{"type":"way","tags":{"waterway":"canal","lock":"yes","lock_name":"Schleuse Wettin"},""" +
                """"geometry":[{"lat":50.5,"lon":11.05},{"lat":50.5,"lon":11.051}]}"""
        val tor1 =
            """{"type":"way","tags":{"waterway":"lock_gate"},"geometry":[""" +
                """{"lat":50.5,"lon":11.04995},{"lat":50.5,"lon":11.05005}]}"""
        val tor2 =
            """{"type":"way","tags":{"waterway":"lock_gate"},"geometry":[""" +
                """{"lat":50.5,"lon":11.05095},{"lat":50.5,"lon":11.05105}]}"""
        val r = fahre(kanal, kammer, tor1, tor2) as RouteResult.Ok
        val locks = r.obstacles.filter { it.kind == ObstacleKind.LOCK }
        assertEquals("eine Schleuse, nicht drei: $locks", 1, locks.size)
        assertEquals("Schleuse Wettin", locks.first().name)
    }
}
