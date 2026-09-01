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

/** Der Kurspfeil: Peilung und die Drehung, die er anzeigt. */
class BearingTest {

    @org.junit.Test
    fun `Peilung zeigt in die richtige Himmelsrichtung`() {
        val hier = LatLon(53.2000, 7.5000)
        org.junit.Assert.assertEquals(0f, bearingDeg(hier, LatLon(53.2100, 7.5000)), 0.5f)   // Nord
        org.junit.Assert.assertEquals(90f, bearingDeg(hier, LatLon(53.2000, 7.5200)), 0.5f)  // Ost
        org.junit.Assert.assertEquals(180f, bearingDeg(hier, LatLon(53.1900, 7.5000)), 0.5f) // Süd
        org.junit.Assert.assertEquals(270f, bearingDeg(hier, LatLon(53.2000, 7.4800)), 0.5f) // West
    }

    @org.junit.Test
    fun `Drehung ist kurz und vorzeichenrichtig`() {
        // Kurs Nord, Ziel im Osten → 90 Grad nach steuerbord.
        org.junit.Assert.assertEquals(90f, relativeBearing(0f, 90f), 0.01f)
        // Kurs Nord, Ziel im Westen → 90 Grad nach backbord.
        org.junit.Assert.assertEquals(-90f, relativeBearing(0f, 270f), 0.01f)
        // Über den Nullpunkt hinweg immer den kurzen Weg, nicht 350 Grad herum.
        org.junit.Assert.assertEquals(-20f, relativeBearing(10f, 350f), 0.01f)
        org.junit.Assert.assertEquals(20f, relativeBearing(350f, 10f), 0.01f)
        // Kurs stimmt.
        org.junit.Assert.assertEquals(0f, relativeBearing(123f, 123f), 0.01f)
    }
}
