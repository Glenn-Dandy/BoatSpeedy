package de.kewl.boatspeedy

import de.kewl.boatspeedy.nav.SpeedSignSource.parseSpeed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Die Werte stammen aus echten OSM-Daten. In Berlin/Brandenburg standen zehnmal
 * `5 km/h`, dreimal schlicht `8`, viermal lag der Wert gar nicht unter
 * `seamark:notice:information`, sondern unter `waterway:maxspeed`, und fünf Schilder
 * trugen überhaupt keinen Wert.
 */
class SpeedSignTest {

    @Test
    fun `Wert mit Einheit`() {
        val (kmh, raw) = parseSpeed("12 km/h")!!
        assertEquals(12.0, kmh, 1e-9)
        assertEquals("12 km/h", raw)
    }

    @Test
    fun `nackte Zahl`() {
        assertEquals(8.0, parseSpeed("8")!!.first, 1e-9)
    }

    @Test
    fun `zweiter Schluessel wenn der erste leer ist`() {
        // So sehen die vier Schilder aus, die nur waterway:maxspeed tragen.
        val (kmh, _) = parseSpeed(null, "10")!!
        assertEquals(10.0, kmh, 1e-9)
    }

    @Test
    fun `Knoten werden in Kilometer je Stunde umgerechnet`() {
        val (kmh, raw) = parseSpeed("5 kn")!!
        assertEquals(9.26, kmh, 0.01)
        assertEquals("5 kn", raw) // Original bleibt erhalten
    }

    @Test
    fun `Komma als Dezimaltrenner`() {
        assertEquals(7.5, parseSpeed("7,5 km/h")!!.first, 1e-9)
    }

    @Test
    fun `ohne Wert kein Schild`() {
        assertNull(parseSpeed(null, null))
        assertNull(parseSpeed(""))
        assertNull(parseSpeed("   "))
        // Ein Text ohne Zahl ist kein Tempolimit.
        assertNull(parseSpeed("Wasserflaeche gesperrt"))
    }

    @Test
    fun `null als Wert wird verworfen`() {
        // 0 km/h ist kein Tempolimit, sondern ein Fehler in den Daten.
        assertNull(parseSpeed("0 km/h"))
    }

    @Test
    fun `Text um die Zahl herum stoert nicht`() {
        assertNotNull(parseSpeed("max 6 km/h"))
        assertEquals(6.0, parseSpeed("max 6 km/h")!!.first, 1e-9)
    }
}
