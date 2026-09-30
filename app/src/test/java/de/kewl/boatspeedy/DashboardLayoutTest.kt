package de.kewl.boatspeedy

import de.kewl.boatspeedy.ui.DashboardTile.BATTERY
import de.kewl.boatspeedy.ui.DashboardTile.GPS
import de.kewl.boatspeedy.ui.DashboardTile.MAP
import de.kewl.boatspeedy.ui.DashboardTile.RANGE
import de.kewl.boatspeedy.ui.DashboardTile.TRIP
import de.kewl.boatspeedy.ui.STANDARD_REIHENFOLGE
import de.kewl.boatspeedy.ui.naechsteKartenStufe
import de.kewl.boatspeedy.ui.reihenfolgeAus
import de.kewl.boatspeedy.ui.reihenfolgeText
import de.kewl.boatspeedy.ui.verschoben
import org.junit.Assert.assertEquals
import org.junit.Test

class DashboardLayoutTest {

    @Test
    fun `ohne gespeicherte Reihenfolge gilt der Standard`() {
        assertEquals(STANDARD_REIHENFOLGE, reihenfolgeAus(""))
    }

    @Test
    fun `gespeicherte Reihenfolge kommt zurueck`() {
        val r = listOf(TRIP, GPS, MAP, RANGE, BATTERY)
        assertEquals(r, reihenfolgeAus(reihenfolgeText(r)))
    }

    /** Eine Kachel, die beim Speichern noch nicht existierte, darf nicht fehlen. */
    @Test
    fun `eine neue Kachel wird an ihrer Stelle ergaenzt`() {
        assertEquals(listOf(MAP, RANGE, BATTERY, TRIP, GPS), reihenfolgeAus("map,range,battery"))
        // Wer vor der GPS-Kachel angeordnet hat, findet sie unten, wo die Zeile vorher stand.
        assertEquals(listOf(TRIP, MAP, RANGE, BATTERY, GPS), reihenfolgeAus("trip,map,range,battery"))
    }

    @Test
    fun `Unsinn und Doppeltes werden uebergangen`() {
        assertEquals(listOf(MAP, RANGE, BATTERY, TRIP, GPS), reihenfolgeAus("map,foo,map, range ,battery"))
    }

    @Test
    fun `verschieben nach oben und unten`() {
        val r = STANDARD_REIHENFOLGE
        assertEquals(listOf(TRIP, RANGE, BATTERY, MAP, GPS), verschoben(r, 3, 0))
        assertEquals(listOf(BATTERY, MAP, RANGE, TRIP, GPS), verschoben(r, 0, 2))
        assertEquals(r, verschoben(r, 1, 7))
    }

    @Test
    fun `die Karte rastet auf der naechsten Stufe ein`() {
        assertEquals(0, naechsteKartenStufe(100f))
        assertEquals(1, naechsteKartenStufe(250f))
        assertEquals(2, naechsteKartenStufe(300f))
        assertEquals(3, naechsteKartenStufe(900f))
    }
}
