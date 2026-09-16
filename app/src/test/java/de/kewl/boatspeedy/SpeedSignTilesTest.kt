package de.kewl.boatspeedy

import de.kewl.boatspeedy.nav.SpeedSignSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.GZIPOutputStream

/**
 * Die Werte der Schilder liegen längst in den Kacheln: `tile.py` behält an den Knoten
 * `maxspeed`, `waterway:maxspeed` und alles unter `seamark:`. Gefragt wurde trotzdem bei
 * Overpass, bei jedem Öffnen der Karte — der einzige fremde Server, der beim Kartenbild
 * noch auftauchte, obwohl die Route längst ohne auskam.
 */
class SpeedSignTilesTest {

    private fun kachel(dir: File, name: String, elemente: String) {
        GZIPOutputStream(File(dir, "$name.json.gz").outputStream()).use {
            it.write(
                ("""{"version":1,"tile":"$name","generated":"2026-09-16",""" +
                    """"elements":[$elemente]}""").toByteArray(),
            )
        }
    }

    private val schild =
        """{"type":"node","lat":50.5,"lon":11.5,"tags":{""" +
            """"seamark:type":"notice","seamark:notice:category":"speed_limit",""" +
            """"seamark:notice:information":"6 km/h"}}"""

    @Test
    fun `ein Schild kommt aus der Kachel`() {
        val dir = createTempDir("schilder")
        try {
            kachel(dir, "n50e011", schild)
            val found = SpeedSignSource.fromTiles(dir, 50.4, 11.4, 50.6, 11.6)
            assertEquals(1, found?.size)
            assertEquals(6.0, found!!.first().kmh, 1e-6)
        } finally {
            dir.deleteRecursively()
        }
    }

    /** Fehlt eine Kachel, muss `null` zurück — der Aufrufer geht dann über Overpass. */
    @Test
    fun `ohne Kachel gibt es kein Ergebnis`() {
        val dir = createTempDir("schilder")
        try {
            assertNull(SpeedSignSource.fromTiles(dir, 50.4, 11.4, 50.6, 11.6))
        } finally {
            dir.deleteRecursively()
        }
    }

    /** Eine Kachel ist ein ganzes Grad breit, der sichtbare Ausschnitt viel kleiner. */
    @Test
    fun `was ausserhalb des Ausschnitts liegt faellt weg`() {
        val dir = createTempDir("schilder")
        try {
            kachel(dir, "n50e011", schild)
            assertTrue(SpeedSignSource.fromTiles(dir, 50.0, 11.0, 50.2, 11.2)!!.isEmpty())
        } finally {
            dir.deleteRecursively()
        }
    }

    /**
     * Bei Overpass filtert die Abfrage, aus einer Kachel kommt alles. Ohne Prüfung würde
     * aus einem Hinweisschild mit einer Zahl im Text ein Tempolimit.
     */
    @Test
    fun `ein Hinweis ohne Tempo wird nicht zum Schild`() {
        val dir = createTempDir("schilder")
        try {
            kachel(
                dir, "n50e011",
                """{"type":"node","lat":50.5,"lon":11.5,"tags":{""" +
                    """"seamark:type":"notice","seamark:notice:category":"no_entry",""" +
                    """"seamark:notice:information":"Einfahrt verboten ab 3 t"}}""",
            )
            assertTrue(SpeedSignSource.fromTiles(dir, 50.4, 11.4, 50.6, 11.6)!!.isEmpty())
        } finally {
            dir.deleteRecursively()
        }
    }
}
