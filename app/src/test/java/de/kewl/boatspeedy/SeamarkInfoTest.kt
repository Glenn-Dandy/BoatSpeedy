package de.kewl.boatspeedy

import de.kewl.boatspeedy.nav.SeamarkSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Die Merkmale stammen aus echten OSM-Daten von der Kieler Förde und der
 * Bleilochtalsperre — abgefragt, nicht ausgedacht.
 */
class SeamarkInfoTest {

    @Test
    fun `Tonne mit Feuer wird lesbar`() {
        val info = SeamarkSource.describe(mapOf(
            "seamark:type" to "buoy_lateral",
            "seamark:name" to "K 6",
            "seamark:buoy_lateral:category" to "port",
            "seamark:buoy_lateral:colour" to "red",
            "seamark:buoy_lateral:shape" to "pillar",
            "seamark:buoy_lateral:system" to "iala-a",
            "seamark:light:character" to "Oc",
            "seamark:light:colour" to "red",
            "seamark:light:group" to "2",
            "seamark:light:period" to "9",
            "seamark:topmark:shape" to "cylinder",
            "seamark:topmark:colour" to "red",
        ))!!
        assertTrue(info.title.contains("K 6"))
        assertTrue(info.title.contains("Seitenzeichen"))
        val text = info.lines.joinToString("\n")
        assertTrue("Backbord fehlt: $text", text.contains("Backbord"))
        assertTrue("Farbe fehlt: $text", text.contains("rot"))
        assertTrue("Feuer fehlt: $text", text.contains("Oc(2) rot, Wiederkehr 9 s"))
        assertTrue("Toppzeichen fehlt: $text", text.contains("Toppzeichen"))
    }

    @Test
    fun `Kardinalzeichen wird benannt`() {
        val info = SeamarkSource.describe(mapOf(
            "seamark:type" to "buoy_cardinal",
            "seamark:buoy_cardinal:category" to "west",
            "seamark:buoy_cardinal:colour" to "yellow;black;yellow",
        ))!!
        val text = info.lines.joinToString("\n")
        assertTrue(text.contains("West-Kardinalzeichen"))
        assertTrue("Farbfolge fehlt: $text", text.contains("gelb, schwarz, gelb"))
    }

    /** Das Schild von Saalburg an der Bleilochtalsperre. */
    @Test
    fun `Geschwindigkeitsschild zeigt seinen Wert und die Richtung`() {
        val info = SeamarkSource.describe(mapOf(
            "seamark:type" to "notice",
            "seamark:notice:category" to "speed_limit",
            "seamark:notice:information" to "12 km/h",
            "seamark:notice:impact" to "upstream",
        ))!!
        val text = info.lines.joinToString("\n")
        assertTrue(text.contains("Geschwindigkeitsbegrenzung"))
        assertTrue(text.contains("12 km/h"))
        assertTrue(text.contains("zu Berg"))
    }

    /**
     * Was nicht sicher zu deuten ist, muss **roh** erscheinen. Eine erfundene Bedeutung
     * wäre auf dem Wasser schlimmer als ein unübersetztes Kürzel.
     */
    @Test
    fun `Unbekanntes wird gezeigt statt verschwiegen`() {
        val info = SeamarkSource.describe(mapOf(
            "seamark:type" to "buoy_special_purpose",
            "seamark:buoy_special_purpose:colour" to "yellow",
            "seamark:radar_reflector" to "yes",
            "seamark:fog_signal:category" to "bell",
        ))!!
        val raw = info.raw.joinToString("\n")
        assertTrue("Radarreflektor fehlt: $raw", raw.contains("radar_reflector = yes"))
        assertTrue("Nebelsignal fehlt: $raw", raw.contains("fog_signal:category = bell"))
    }

    @Test
    fun `ohne Typ kein Eintrag`() {
        assertNull(SeamarkSource.describe(mapOf("name" to "irgendwas")))
        assertNull(SeamarkSource.describe(mapOf("seamark:type" to "sounding")))
    }

    @Test
    fun `Name bleibt im Titel erhalten`() {
        val info = SeamarkSource.describe(mapOf(
            "seamark:type" to "light_major",
            "seamark:name" to "Kiel Leuchtturm",
        ))!!
        assertEquals("Leuchtfeuer „Kiel Leuchtturm\"", info.title)
    }
}
