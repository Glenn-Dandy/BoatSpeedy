package de.kewl.boatspeedy

import de.kewl.boatspeedy.nav.ObstacleKind
import de.kewl.boatspeedy.nav.WaterRouter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.GZIPOutputStream

/**
 * Schleusen gab es bisher nur entlang einer gerechneten Route. Wer wissen wollte, wann
 * eine öffnet, musste erst ein Ziel setzen — dabei liegen alle Schleusen des Gebiets
 * längst in den Kacheln auf dem Gerät.
 */
class ObstaclesInAreaTest {

    private fun kachel(dir: File, name: String, elemente: String) {
        GZIPOutputStream(File(dir, "$name.json.gz").outputStream()).use {
            it.write(
                ("""{"version":1,"tile":"$name","generated":"2026-09-16",""" +
                    """"elements":[$elemente]}""").toByteArray(),
            )
        }
    }

    private val kammer =
        """{"type":"way","tags":{"waterway":"canal","lock":"yes",""" +
            """"lock_name":"Schleuse Wettin","opening_hours":"Apr-Oct Mo-Th 07:00-19:00"},""" +
            """"geometry":[{"lat":50.5,"lon":11.05},{"lat":50.5,"lon":11.051}]}"""

    private val wehr =
        """{"type":"node","lat":50.52,"lon":11.07,"tags":{"waterway":"weir"}}"""

    @Test
    fun `Schleuse und Wehr kommen ohne Route aus der Kachel`() {
        val dir = createTempDir("gebiet")
        try {
            kachel(dir, "n50e011", "$kammer,$wehr")
            val found = WaterRouter.obstaclesIn(dir, 50.4, 11.0, 50.6, 11.2)
            assertEquals(2, found.size)
            val schleuse = found.single { it.kind == ObstacleKind.LOCK }
            assertEquals("Schleuse Wettin", schleuse.name)
            assertEquals("Apr-Oct Mo-Th 07:00-19:00", schleuse.openingHours)
            assertTrue(found.any { it.kind == ObstacleKind.WEIR })
        } finally {
            dir.deleteRecursively()
        }
    }

    /** Eine Kachel ist ein ganzes Grad breit, der Blick darauf viel enger. */
    @Test
    fun `was ausserhalb des Blickfelds liegt faellt weg`() {
        val dir = createTempDir("gebiet")
        try {
            kachel(dir, "n50e011", "$kammer,$wehr")
            assertTrue(WaterRouter.obstaclesIn(dir, 50.0, 11.0, 50.2, 11.2).isEmpty())
        } finally {
            dir.deleteRecursively()
        }
    }

    /** Ohne Kacheln wird nichts geholt — geladen wird nur beim Routing, auf Nachfrage. */
    @Test
    fun `ohne Kachel bleibt die Karte leer`() {
        val dir = createTempDir("gebiet")
        try {
            assertTrue(WaterRouter.obstaclesIn(dir, 50.4, 11.0, 50.6, 11.2).isEmpty())
        } finally {
            dir.deleteRecursively()
        }
    }

    /**
     * Brücken mit Durchfahrtshöhe. Am Elbe-Lübeck-Kanal hängt an fast jeder eine Tafel;
     * in OSM steht sie als `seamark:type=bridge` mit `clearance_height`. Ohne Maß ist eine
     * Brücke keine Auskunft, sondern nur ein Punkt mehr auf der Karte.
     */
    @Test
    fun `eine Bruecke mit Hoehe wird zum Hindernis`() {
        val dir = createTempDir("gebiet")
        try {
            kachel(
                dir, "n50e011",
                """{"type":"node","lat":50.5,"lon":11.06,"tags":{""" +
                    """"seamark:type":"bridge","seamark:name":"Rehderbrücke",""" +
                    """"seamark:bridge:category":"fixed",""" +
                    """"seamark:bridge:clearance_height":"6.10"}}""",
            )
            val b = WaterRouter.obstaclesIn(dir, 50.4, 11.0, 50.6, 11.2)
                .single { it.kind == ObstacleKind.BRIDGE }
            assertEquals("Rehderbrücke", b.name)
            assertEquals("6.10", b.clearanceHeightM)
            assertTrue(b.hasInfo)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `eine Bruecke ohne Mass bleibt weg`() {
        val dir = createTempDir("gebiet")
        try {
            kachel(
                dir, "n50e011",
                """{"type":"node","lat":50.5,"lon":11.06,"tags":{""" +
                    """"seamark:type":"bridge","seamark:bridge:category":"fixed"}}""",
            )
            assertTrue(WaterRouter.obstaclesIn(dir, 50.4, 11.0, 50.6, 11.2).isEmpty())
        } finally {
            dir.deleteRecursively()
        }
    }

    /** Kammer und Tore liegen übereinander; übrig bleibt die mit der Auskunft. */
    @Test
    fun `Kammer und Tore werden auch hier zusammengelegt`() {
        val dir = createTempDir("gebiet")
        try {
            val tor =
                """{"type":"way","tags":{"waterway":"lock_gate"},"geometry":[""" +
                    """{"lat":50.5,"lon":11.04995},{"lat":50.5,"lon":11.05005}]}"""
            kachel(dir, "n50e011", "$kammer,$tor")
            val locks = WaterRouter.obstaclesIn(dir, 50.4, 11.0, 50.6, 11.2)
                .filter { it.kind == ObstacleKind.LOCK }
            assertEquals("eine Schleuse, nicht zwei: $locks", 1, locks.size)
            assertEquals("Schleuse Wettin", locks.first().name)
        } finally {
            dir.deleteRecursively()
        }
    }
}
