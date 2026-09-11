package de.kewl.boatspeedy

import de.kewl.boatspeedy.nav.MapTiles
import de.kewl.boatspeedy.nav.TileId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MapTilesTest {

    @Test
    fun `Name folgt der Suedwestecke`() {
        assertEquals("n50e011", TileId(50, 11).name)
        assertEquals("n53e008", TileId(53, 8).name)
        assertEquals("s34w019", TileId(-34, -19).name)
        assertEquals("n00e000", TileId(0, 0).name)
    }

    @Test
    fun `Name laesst sich zurueckrechnen`() {
        for (id in listOf(TileId(50, 11), TileId(-34, -19), TileId(0, 0), TileId(71, 179))) {
            assertEquals(id, MapTiles.parseName(id.name))
        }
        assertNull(MapTiles.parseName("unsinn"))
        assertNull(MapTiles.parseName("n5e11"))
    }

    @Test
    fun `Ausschnitt innerhalb eines Feldes ergibt ein Feld`() {
        val t = MapTiles.tilesFor(50.61, 11.38, 50.63, 11.40)
        assertEquals(listOf(TileId(50, 11)), t)
    }

    @Test
    fun `Ausschnitt ueber eine Kante ergibt beide Felder`() {
        // Von 11,9 nach 12,1 Grad Länge – das ist die Kante zwischen e011 und e012.
        val t = MapTiles.tilesFor(50.5, 11.9, 50.6, 12.1)
        assertEquals(listOf(TileId(50, 11), TileId(50, 12)), t)
    }

    @Test
    fun `Ausschnitt ueber eine Ecke ergibt vier Felder`() {
        val t = MapTiles.tilesFor(50.9, 11.9, 51.1, 12.1)
        assertEquals(4, t.size)
        assertTrue(t.containsAll(listOf(
            TileId(50, 11), TileId(50, 12), TileId(51, 11), TileId(51, 12),
        )))
    }

    /**
     * Der gemessene Fall: 150 km um Rudolstadt. Zu wenige Felder hieße Löcher beim
     * Routen, zu viele hieße unnötiger Download.
     */
    @Test
    fun `150 km Umkreis bleibt eine handhabbare Zahl`() {
        val t = MapTiles.tilesWithin(50.72, 11.34, 150.0)
        assertTrue("erwartet zwischen 6 und 20, waren ${t.size}", t.size in 6..20)
        assertTrue("das eigene Feld muss dabei sein", t.contains(TileId(50, 11)))
    }

    @Test
    fun `Umkreis waechst zu hohen Breiten hin in der Laenge`() {
        // Bei 70 Grad Nord sind 150 km deutlich mehr Längengrade als am Äquator.
        val nord = MapTiles.tilesWithin(70.0, 20.0, 150.0)
        val mitte = MapTiles.tilesWithin(50.0, 20.0, 150.0)
        assertTrue("hoch im Norden braucht es mehr Felder", nord.size > mitte.size)
    }

    @Test
    fun `fehlende Felder werden benannt`() {
        val dir = createTempDir()
        try {
            val ids = listOf(TileId(50, 11), TileId(50, 12))
            assertEquals(ids, MapTiles.missing(dir, ids))
            java.io.File(dir, "n50e011.json.gz").writeText("x")
            assertEquals(listOf(TileId(50, 12)), MapTiles.missing(dir, ids))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `Groesse wird aufgerundet angezeigt`() {
        assertEquals(1.1, MapTiles.roundUpMb(1_048_577), 1e-9)
        assertEquals(0.1, MapTiles.roundUpMb(1), 1e-9)
        assertEquals(0.0, MapTiles.roundUpMb(0), 1e-9)
    }
}
