package de.kewl.boatspeedy

import de.kewl.boatspeedy.data.Craft
import de.kewl.boatspeedy.nav.LatLon
import de.kewl.boatspeedy.nav.Obstacle
import de.kewl.boatspeedy.nav.ObstacleKind
import de.kewl.boatspeedy.nav.RouteGpx
import de.kewl.boatspeedy.nav.SavedRoute
import de.kewl.boatspeedy.trip.GpxImport
import de.kewl.boatspeedy.ui.GpxExport
import de.kewl.boatspeedy.trip.SavedTrip
import de.kewl.boatspeedy.trip.TrackPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.kxml2.io.KXmlParser

/** Routen als GPX teilen und wieder einlesen. */
class RouteGpxTest {

    private val strecke = listOf(LatLon(53.37, 12.76), LatLon(53.36, 12.80), LatLon(53.30, 12.81))

    private val route = SavedRoute(
        id = 1L, createdAt = 1_758_000_000_000L, name = "Bolter Kanal & Mirow", craft = Craft.CANOE,
        target = strecke.last(), path = strecke, water = strecke.drop(1),
        obstacles = listOf(Obstacle(53.371, 12.777, ObstacleKind.WEIR, "Alte Bolter Mühle")),
        portageM = 80.0, portage = listOf(strecke.take(2)),
    )

    private fun lies(gpx: String) =
        RouteGpx.parse(gpx.byteInputStream(), KXmlParser(), Craft.MOTORBOAT, 42L)

    @Test
    fun `eine eigene Route kommt vollstaendig zurueck`() {
        val r = lies(RouteGpx.build(listOf(route))).single()
        assertEquals(route.name, r.name)
        assertEquals(Craft.CANOE, r.craft)
        assertEquals(route.water, r.water)
        assertEquals(route.portage, r.portage)
        assertEquals("Alte Bolter Mühle", r.obstacles.single().name)
        assertEquals(42L, r.id)
    }

    @Test
    fun `eine fremde rte wird zur Route entlang ihrer Punkte`() {
        val gpx = """<?xml version="1.0"?><gpx version="1.1"><rte><name>Havel</name>
            <rtept lat="53.37" lon="12.76"><name>A</name></rtept><rtept lat="53.30" lon="12.81"/></rte></gpx>"""
        val r = lies(gpx).single()
        assertEquals("Havel", r.name)
        assertEquals(Craft.MOTORBOAT, r.craft)
        assertEquals(2, r.path.size)
    }

    @Test
    fun `eine Fahrt ist keine Route`() {
        val fahrt = SavedTrip(
            id = 1L, startedAt = 1L, distanceM = 100.0, durationMs = 1L, avgSpeedMs = 1f, maxSpeedMs = 1f,
            energyWh = 0f, chargeAh = 0f,
            points = listOf(TrackPoint(53.37, 12.76, 0L), TrackPoint(53.36, 12.80, 1000L)),
        )
        val gpx = GpxExport.buildGpx(listOf(fahrt))
        assertTrue(lies(gpx).isEmpty())
        // Und umgekehrt bleibt der Fahrten-Import bei seiner Sache.
        assertTrue(GpxImport.fromGpx(gpx.byteInputStream(), KXmlParser()) != null)
    }
}
