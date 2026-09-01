package de.kewl.boatspeedy.nav

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Nur die Rechnung, die ohne Android und ohne Netz auskommt. Das Routen selbst hängt an
 * OSM-Daten und lässt sich hier nicht sinnvoll prüfen — die Streckenlänge dagegen schon,
 * und aus ihr folgt der angezeigte Verbrauch.
 */
class RouteMathTest {

    @Test
    fun `Weglaenge ist die Summe der Abschnitte`() {
        // Drei Punkte auf einer Linie: die Summe muss dem Ende-zu-Ende-Abstand entsprechen.
        val a = LatLon(53.2000, 7.5000)
        val b = LatLon(53.2100, 7.5000)
        val c = LatLon(53.2200, 7.5000)
        val ganz = distanceM(a, c)
        assertEquals(ganz, pathLengthM(listOf(a, b, c)), 1.0)
    }

    @Test
    fun `Umweg ist laenger als die Luftlinie`() {
        val a = LatLon(53.2000, 7.5000)
        val ziel = LatLon(53.2000, 7.5300)
        val umweg = listOf(a, LatLon(53.2200, 7.5150), ziel)
        assert(pathLengthM(umweg) > distanceM(a, ziel))
    }

    @Test
    fun `Ziel gilt erst im Umkreis von fuenf Metern als erreicht`() {
        val ziel = LatLon(53.2000, 7.5000)
        NavRepository.set(NavTarget(ziel, NavMode.LINE, listOf(ziel), 0.0))
        // gut 20 m daneben – noch unterwegs
        assert(!NavRepository.onLocation(53.20018, 7.5000))
        assert(NavRepository.target.value != null)
        // knapp 3 m daneben – angekommen, Ziel wird abgeräumt
        assert(NavRepository.onLocation(53.200025, 7.5000))
        assert(NavRepository.target.value == null)
    }

    @Test
    fun `ein einzelner Punkt hat keine Laenge`() {
        assertEquals(0.0, pathLengthM(listOf(LatLon(53.0, 7.0))), 0.0)
        assertEquals(0.0, pathLengthM(emptyList()), 0.0)
    }
}
