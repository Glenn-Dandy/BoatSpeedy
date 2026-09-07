package de.kewl.boatspeedy

import de.kewl.boatspeedy.nav.LatLon
import de.kewl.boatspeedy.nav.RouteError
import de.kewl.boatspeedy.nav.RouteResult
import de.kewl.boatspeedy.nav.WaterRouter
import de.kewl.boatspeedy.nav.distanceM
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Die Reichweite hängt daran, woher die Daten kommen. Über Overpass würde ein weit
 * entferntes Ziel eine Abfrage über halb Deutschland auslösen; aus den Kacheln kostet
 * dieselbe Strecke nichts, weil alles auf dem Gerät liegt.
 */
class RouteRangeTest {

    /** Rudolstadt nach Hamburg – gut 300 km, früher pauschal abgelehnt. */
    private val rudolstadt = LatLon(50.7206, 11.3428)
    private val hamburg = LatLon(53.5511, 9.9937)

    @Test
    fun `weites Ziel ohne Kacheln wird abgelehnt`() {
        val d = distanceM(rudolstadt, hamburg)
        assertTrue("Testfall soll über 60 km liegen, war ${d / 1000} km", d > 60_000)
        val r = WaterRouter.route(rudolstadt, hamburg, tileDir = null)
        assertEquals(RouteResult.Failed(RouteError.TOO_FAR), r)
    }

    @Test
    fun `knapp unter der Grenze wird nicht wegen Entfernung abgelehnt`() {
        // 50 km nördlich – unter sechzig, also darf TOO_FAR nicht der Grund sein.
        val nah = LatLon(rudolstadt.lat + 0.45, rudolstadt.lon)
        assertTrue(distanceM(rudolstadt, nah) < 60_000)
        val r = WaterRouter.route(rudolstadt, nah, tileDir = null)
        // Ohne Netz im Test scheitert es an den Daten, aber nicht an der Entfernung.
        assertTrue(
            "unerwartet: $r",
            r !is RouteResult.Failed || (r as RouteResult.Failed).reason != RouteError.TOO_FAR,
        )
    }
}
