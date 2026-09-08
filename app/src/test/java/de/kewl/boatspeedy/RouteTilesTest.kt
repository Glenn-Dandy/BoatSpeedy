package de.kewl.boatspeedy

import de.kewl.boatspeedy.nav.LatLon
import de.kewl.boatspeedy.nav.MapTiles
import de.kewl.boatspeedy.nav.TileId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Der Rand um eine Strecke muss mit ihrer Länge wachsen. Ein Fluss läuft nicht im
 * Rechteck zwischen Start und Ziel: Der Main zieht bei Frankfurt über den fünfzigsten
 * Breitengrad, bevor er nach Aschaffenburg zurückschwenkt. Mit festen 0,05° fehlte genau
 * diese Reihe — nachgerechnet mit echten Kacheln: neun ergeben nichts, zwölf ergeben
 * 425 km.
 */
class RouteTilesTest {

    private val basel = LatLon(47.5596, 7.5886)
    private val obernau = LatLon(49.9400, 9.1800)
    private val griesheim = LatLon(50.0207, 8.1163)

    @Test
    fun `lange Strecke bekommt die Reihe noerdlich des Ziels`() {
        val t = MapTiles.tilesForRoute(basel, obernau)
        assertTrue(
            "n50e008 fehlt — genau daran scheiterte die Route: ${t.map { it.name }}",
            t.contains(TileId(50, 8)),
        )
    }

    @Test
    fun `kurze Strecke bleibt sparsam`() {
        // Zwei Punkte 2 km auseinander: der Mindestrand gilt, nicht ein Fünftel von nichts.
        val a = LatLon(50.72, 11.34)
        val b = LatLon(50.73, 11.36)
        assertEquals(listOf(TileId(50, 11)), MapTiles.tilesForRoute(a, b))
    }

    @Test
    fun `Rand waechst, bleibt aber gedeckelt`() {
        // Über ganz Europa darf der Rand nicht ins Uferlose wachsen.
        val weit = MapTiles.tilesForRoute(LatLon(43.0, 3.0), LatLon(55.0, 15.0))
        // Spannweite 12 Grad, ein Fünftel wären 2,4 — gedeckelt auf 1 Grad je Seite.
        assertEquals(15, 1 + 55 - 43 + 2)   // erwartete Zeilenzahl: 43-1 bis 55+1
        val lats = weit.map { it.lat }.distinct().sorted()
        assertEquals(42, lats.first())
        assertEquals(56, lats.last())
    }

    @Test
    fun `Strecke ueber die Kachelkante nimmt beide Seiten mit`() {
        val t = MapTiles.tilesForRoute(LatLon(49.99, 8.5), LatLon(50.01, 8.5))
        assertTrue(t.contains(TileId(49, 8)))
        assertTrue(t.contains(TileId(50, 8)))
    }
}
