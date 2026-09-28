package de.kewl.boatspeedy

import de.kewl.boatspeedy.trip.GpxImport
import de.kewl.boatspeedy.trip.SavedTrip
import de.kewl.boatspeedy.trip.TrackPoint
import de.kewl.boatspeedy.ui.GpxExport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.kxml2.io.KXmlParser

/**
 * Eine Fahrt exportieren und wieder einlesen: Heraus kommt dieselbe Fahrt.
 *
 * Nach dem Import stand der Verbrauch in Ah wieder da, Energie und Effizienz aber nicht.
 * Der Export schrieb die Energie gar nicht erst, und der Import setzte sie fest auf null.
 * Die Effizienz in Wh/km gibt es nur aus der Energie, sie fehlte also mit.
 */
class GpxRoundTripTest {

    private val fahrt = SavedTrip(
        id = 1_758_000_000_000L,
        startedAt = 1_758_000_000_000L,
        distanceM = 12_345.6,
        durationMs = 3_600_000L,
        totalMs = 4_200_000L,
        avgSpeedMs = 3.43f,
        maxSpeedMs = 5.1f,
        energyWh = 812.5f,
        chargeAh = 31.25f,
        points = listOf(
            TrackPoint(50.8000, 11.5800, 0L, 3.0f, 95, 0f),
            TrackPoint(50.8100, 11.5850, 1_800_000L, 3.5f, 90, 15.5f),
            TrackPoint(50.8200, 11.5900, 4_200_000L, 3.2f, 84, 31.25f),
        ),
    )

    private fun zurueck(trip: SavedTrip): SavedTrip {
        val gpx = GpxExport.buildGpx(listOf(trip))
        return GpxImport.fromGpx(gpx.byteInputStream(), KXmlParser())
            ?: error("Import lieferte keine Fahrt")
    }

    @Test
    fun `Energie und Verbrauch kommen zurueck`() {
        val t = zurueck(fahrt)
        assertEquals(812.5f, t.energyWh, 0.01f)
        assertEquals(31.25f, t.chargeAh, 0.01f)
    }

    /**
     * Die aufgezeichnete Strecke, nicht die aus den Punkten neu gerechnete. Sonst weicht
     * die Effizienz nach dem Import ab, obwohl die Energie wieder stimmt.
     */
    @Test
    fun `Strecke und damit die Effizienz bleiben gleich`() {
        val t = zurueck(fahrt)
        assertEquals(12_345.6, t.distanceM, 0.1)
        val vorher = fahrt.energyWh / (fahrt.distanceM / 1000.0)
        val nachher = t.energyWh / (t.distanceM / 1000.0)
        assertEquals(vorher, nachher, 0.01)
    }

    @Test
    fun `Zeiten und Punkte kommen zurueck`() {
        val t = zurueck(fahrt)
        assertEquals(3_600_000L, t.durationMs)
        assertEquals(4_200_000L, t.totalMs)
        assertEquals(3, t.points.size)
        assertEquals(84, t.points.last().soc)
    }

    /** Eine Fahrt ohne Batterie: keine Energie, und das bleibt so. */
    @Test
    fun `ohne Batterie bleibt die Energie null`() {
        val t = zurueck(fahrt.copy(energyWh = 0f, chargeAh = 0f))
        assertEquals(0f, t.energyWh, 0f)
    }

    /** Fremde GPX-Dateien ohne unsere Erweiterungen gehen weiter. */
    @Test
    fun `fremde GPX ohne Erweiterungen`() {
        val gpx = """<?xml version="1.0"?><gpx version="1.1"><trk><trkseg>
            <trkpt lat="50.80" lon="11.58"><time>2026-09-20T10:00:00Z</time></trkpt>
            <trkpt lat="50.81" lon="11.58"><time>2026-09-20T10:05:00Z</time></trkpt>
            </trkseg></trk></gpx>"""
        val t = GpxImport.fromGpx(gpx.byteInputStream(), KXmlParser())
        assertNotNull(t)
        assertEquals(0f, t!!.energyWh, 0f)
        assertEquals(1112.0, t.distanceM, 5.0)
    }
}
