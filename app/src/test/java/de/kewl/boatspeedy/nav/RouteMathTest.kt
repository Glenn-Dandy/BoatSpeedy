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
    fun `Weg beginnt an der Position und wird kuerzer`() {
        val ziel = LatLon(53.2500, 7.5000)
        NavRepository.set(NavTarget(ziel, NavMode.LINE, listOf(LatLon(53.2000, 7.5000), ziel), 0.0))

        NavRepository.onLocation(53.2000, 7.5000)
        val weit = NavRepository.target.value!!.distanceM

        NavRepository.onLocation(53.2200, 7.5000)   // ein Stück näher
        val naeher = NavRepository.target.value!!.distanceM
        assert(naeher < weit) { "Entfernung muss beim Näherkommen kleiner werden" }

        NavRepository.onLocation(53.1900, 7.5000)   // wieder weiter weg
        assert(NavRepository.target.value!!.distanceM > weit) { "und beim Entfernen größer" }

        // Die Linie hängt am Boot, nicht am ursprünglichen Startpunkt.
        val p = NavRepository.target.value!!.path
        assertEquals(53.1900, p.first().lat, 0.0001)
        assertEquals(ziel, p.last())
        NavRepository.clear()
    }

    @Test
    fun `im Stand bleibt die Route unveraendert`() {
        val weg = listOf(
            LatLon(53.2000, 7.5000), LatLon(53.2100, 7.5000),
            LatLon(53.2200, 7.5000), LatLon(53.2300, 7.5000),
        )
        val ziel = weg.last()
        NavRepository.set(NavTarget(ziel, NavMode.ROUTE, weg, pathLengthM(weg), weg))

        val stand = LatLon(53.2000, 7.5000)
        repeat(10) { NavRepository.onLocation(stand.lat, stand.lon) }

        // Früher wurde je Meldung ein Punkt abgeschnitten – nach zehn war nichts mehr da.
        assertEquals(weg, NavRepository.target.value!!.path)
        assertEquals(pathLengthM(weg), NavRepository.target.value!!.distanceM, 1.0)
        NavRepository.clear()
    }

    @Test
    fun `entlang der Route wird die Reststrecke kuerzer`() {
        val weg = listOf(
            LatLon(53.2000, 7.5000), LatLon(53.2100, 7.5000),
            LatLon(53.2200, 7.5000), LatLon(53.2300, 7.5000),
        )
        val ganz = remainingAlong(weg, weg.first())
        val halb = remainingAlong(weg, weg[2])
        assert(halb < ganz) { "Reststrecke muss beim Folgen kleiner werden" }
        assertEquals(0.0, remainingAlong(weg, weg.last()), 1.0)
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

/** Windrichtung als Himmelsrichtung – acht Sektoren, jeder 45 Grad breit. */
class WindDirectionTest {
    @org.junit.Test
    fun `Grad werden zur richtigen Himmelsrichtung`() {
        fun f(d: Int) = de.kewl.boatspeedy.ui.windArrow(d)
        org.junit.Assert.assertEquals("N", f(0))
        org.junit.Assert.assertEquals("N", f(350))    // über den Nullpunkt hinweg
        org.junit.Assert.assertEquals("NO", f(45))
        org.junit.Assert.assertEquals("O", f(90))
        org.junit.Assert.assertEquals("S", f(180))
        org.junit.Assert.assertEquals("W", f(270))    // der Wind aus der Messung oben
        org.junit.Assert.assertEquals("NW", f(315))
    }
}
