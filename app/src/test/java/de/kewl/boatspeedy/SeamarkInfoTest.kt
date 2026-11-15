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

    /**
     * An der Oeblitzschleuse tragen die beiden Tore `seamark:type=gate` und sonst nichts.
     * Sie liegen 24 m neben der Kammer, die Name, Zeiten und Telefon kennt. Antippbar
     * öffneten sie ein Fenster mit dem Wort „gate" und nahmen der Schleuse den Tipp weg.
     */
    @Test
    fun `ein Schleusentor bekommt keine eigene Auskunft`() {
        assertNull(
            SeamarkSource.describe(
                mapOf("seamark:type" to "gate", "waterway" to "lock_gate"),
            ),
        )
    }

    /** Ein Sperrwerk oder Sturmflutwehr ist etwas anderes und bleibt antippbar. */
    @Test
    fun `ein Tor ohne Schleuse bleibt`() {
        assertTrue(SeamarkSource.describe(mapOf("seamark:type" to "gate")) != null)
    }

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
        assertEquals("Höchstgeschwindigkeit", info.title)
        assertEquals(listOf("12 km/h", "gilt zu Berg"), info.lines)
    }

    /** Berlin, OSM-Knoten 1842510682: Ölsperre, in beide Richtungen. */
    @Test
    fun `Schild zeigt Funktion und Bedeutung im Titel`() {
        val info = SeamarkSource.describe(mapOf(
            "seamark:type" to "notice",
            "seamark:notice:addition" to "bottom_board",
            "seamark:notice:category" to "no_anchoring",
            "seamark:notice:function" to "prohibition",
            "seamark:notice:impact" to "upstream;downstream",
            "seamark:notice:information" to "Ölsperre 20m",
        ))!!
        assertEquals("Verbot: Ankern verboten", info.title)
        assertEquals(listOf("Ölsperre 20m", "gilt zu Berg und zu Tal"), info.lines)
        // Die Zusatztafel ist am Schild zu sehen und gehört nicht in die Auskunft.
        assertTrue(info.raw.toString(), info.raw.isEmpty())
    }

    @Test
    fun `Wenden verboten`() {
        val info = SeamarkSource.describe(mapOf(
            "seamark:type" to "notice",
            "seamark:notice:category" to "no_turning",
            "seamark:notice:function" to "prohibition",
        ))!!
        assertEquals("Verbot: Wenden verboten", info.title)
        assertTrue(info.lines.isEmpty())
    }

    /** Berlin, OSM-Knoten 926774490: drei Schilder an einem Mast. */
    @Test
    fun `mehrere Schilder an einem Mast stehen einzeln da`() {
        val info = SeamarkSource.describe(mapOf(
            "seamark:type" to "notice",
            "seamark:notice:1:category" to "speed_limit",
            "seamark:notice:1:function" to "regulation",
            "seamark:notice:1:impact" to "downstream",
            "seamark:notice:1:information" to "5 km/h",
            "seamark:notice:2:addition" to "bottom_board",
            "seamark:notice:2:category" to "keep_lookout",
            "seamark:notice:2:function" to "regulation",
            "seamark:notice:2:impact" to "downstream",
            "seamark:notice:2:national_information" to "Fähre",
            "seamark:notice:3:category" to "prohibition_ends",
            "seamark:notice:3:function" to "information",
            "seamark:notice:3:impact" to "upstream",
        ))!!
        assertEquals("Schifffahrtszeichen (3)", info.title)
        assertEquals(
            listOf("Gebot: Höchstgeschwindigkeit", "Gebot: Besondere Vorsicht", "Hinweis: Ende des Verbots"),
            info.groups.map { it.heading },
        )
        assertEquals(listOf("5 km/h", "gilt zu Tal"), info.groups[0].lines)
        assertEquals(listOf("Fähre", "gilt zu Tal"), info.groups[1].lines)
        assertTrue(info.raw.toString(), info.raw.isEmpty())
    }

    /** Ohne Funktion bleibt die Bedeutung, ohne Angaben kein englisches Kürzel. */
    @Test
    fun `Schild ohne Funktion`() {
        val info = SeamarkSource.describe(mapOf(
            "seamark:type" to "notice",
            "seamark:notice:category" to "limited_depth",
        ))!!
        assertEquals("Begrenzte Wassertiefe", info.title)
    }

    /** Unbekannte Bedeutungen bleiben stehen, statt erfunden zu werden. */
    @Test
    fun `unbekannte Bedeutung bleibt roh`() {
        val info = SeamarkSource.describe(mapOf(
            "seamark:type" to "notice",
            "seamark:notice:category" to "etwas_neues",
            "seamark:notice:function" to "prohibition",
        ))!!
        assertEquals("Verbot: etwas_neues", info.title)
    }

    /**
     * Berlin, OSM-Knoten 1868692528: eine Tonne, die auch die Merkmale einer Bake trägt.
     * Vorher erschien die Hälfte davon als `beacon_lateral:… = …`.
     */
    @Test
    fun `Tonne mit Merkmalen einer Bake`() {
        val info = SeamarkSource.describe(mapOf(
            "seamark:type" to "buoy_lateral",
            "seamark:beacon_lateral:category" to "waterway_right",
            "seamark:beacon_lateral:colour" to "red;white",
            "seamark:beacon_lateral:colour_pattern" to "horizontal",
            "seamark:beacon_lateral:reflectivity" to "conspicuous",
            "seamark:beacon_lateral:shape" to "stake",
            "seamark:buoy_lateral:colour" to "red",
            "seamark:topmark:colour" to "red",
            "seamark:topmark:shape" to "cone, point down",
        ))!!
        assertEquals(
            listOf("Wasserstraße, rechte Seite", "Farbe: rot", "Stangenbake", "Toppzeichen: Kegel, Spitze unten (rot)"),
            info.lines,
        )
        // Was sich widerspricht oder nicht sicher zu deuten ist, bleibt roh.
        assertEquals(
            listOf("beacon_lateral:colour = red;white", "beacon_lateral:reflectivity = conspicuous"),
            info.raw,
        )
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
