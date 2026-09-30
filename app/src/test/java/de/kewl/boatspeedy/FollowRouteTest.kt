package de.kewl.boatspeedy

import de.kewl.boatspeedy.data.Craft
import de.kewl.boatspeedy.nav.LatLon
import de.kewl.boatspeedy.nav.NavMode
import de.kewl.boatspeedy.nav.NavRepository
import de.kewl.boatspeedy.nav.NavTarget
import de.kewl.boatspeedy.nav.Obstacle
import de.kewl.boatspeedy.nav.ObstacleKind
import de.kewl.boatspeedy.nav.RouteJson
import de.kewl.boatspeedy.nav.SavedRoute
import de.kewl.boatspeedy.nav.einstieg
import de.kewl.boatspeedy.nav.folgen
import de.kewl.boatspeedy.nav.pathLengthM
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Gespeicherte Routen und Fahrten nachfahren. */
class FollowRouteTest {

    /** Hin und zurück: 1 km nach Osten und wieder zurück, Start und Ende am selben Ort. */
    private val rundfahrt = (0..10).map { LatLon(50.5, 11.0 + it * 0.0014) } +
        (9 downTo 0).map { LatLon(50.5001, 11.0 + it * 0.0014) }

    @After
    fun aufraeumen() = NavRepository.clearAll()

    @Test
    fun `eine Rundfahrt ist am Start nicht schon angekommen`() {
        val s = folgen(rundfahrt, 0, rundfahrt.first(), 10.0)
        assertFalse(s.angekommen)
        assertTrue("Rest ${s.restM}", s.restM > 1_500)
    }

    @Test
    fun `angekommen erst am Ende`() {
        var index = 0
        for (p in rundfahrt.drop(1)) {
            val s = folgen(rundfahrt, index, p, 10.0)
            if (p != rundfahrt.last()) assertFalse("zu früh bei $p", s.angekommen)
            index = s.index
        }
        assertTrue(folgen(rundfahrt, index, rundfahrt.last(), 10.0).angekommen)
    }

    @Test
    fun `wer mitten einsteigt, folgt ab dort`() {
        assertEquals(5, einstieg(rundfahrt, LatLon(50.5, 11.0 + 5.5 * 0.0014), 30.0))
        assertNull(einstieg(rundfahrt, LatLon(50.6, 11.0), 30.0))
    }

    /** Erst die Anfahrt vom Boot, dann der Strecke folgen. */
    @Test
    fun `Anfahrt zum Start, dann folgen`() {
        val strecke = rundfahrt.take(11)
        NavRepository.set(
            NavTarget(
                target = strecke.last(), mode = NavMode.ROUTE, path = strecke,
                distanceM = pathLengthM(strecke), water = strecke,
                anfahrt = strecke.first(), folge = 0,
            ),
        )
        // 1 km südlich: noch Anfahrt, Entfernung = Luftlinie + Strecke.
        NavRepository.onLocation(50.491, 11.0)
        val unterwegs = NavRepository.target.value!!
        assertNotNull(unterwegs.anfahrt)
        assertTrue(unterwegs.distanceM > 1_900)
        // Am Start: Anfahrt vorbei.
        NavRepository.onLocation(50.5, 11.0)
        assertNull(NavRepository.target.value!!.anfahrt)
        // Am Ende: angekommen, Ziel weg.
        NavRepository.onLocation(strecke.last().lat, strecke.last().lon)
        assertNull(NavRepository.target.value)
    }

    @Test
    fun `eine Route kommt aus der Ablage unveraendert zurueck`() {
        val r = SavedRoute(
            id = 1L, createdAt = 1L, name = "Alter zur Linkenmühle", craft = Craft.CANOE,
            target = rundfahrt.last(), path = rundfahrt, water = rundfahrt.drop(1).dropLast(1),
            obstacles = listOf(
                Obstacle(50.5, 11.005, ObstacleKind.LOCK, "Schleuse Wettin", openingHours = "Mo-Fr 08:00-18:00", maxLengthM = "103"),
            ),
            portageM = 120.0, portage = listOf(rundfahrt.take(3)),
        )
        val zurueck = RouteJson.decode(RouteJson.encode(r))
        assertEquals(r.name, zurueck.name)
        assertEquals(r.craft, zurueck.craft)
        assertEquals(r.path.size, zurueck.path.size)
        assertEquals(r.obstacles.single().openingHours, zurueck.obstacles.single().openingHours)
        assertEquals(r.portage, zurueck.portage)
        assertEquals(r.distanceM, zurueck.distanceM, 0.5)
    }
}
