package de.kewl.boatspeedy

import de.kewl.boatspeedy.ui.MercatorBox
import de.kewl.boatspeedy.ui.RADAR_AREA_GER
import de.kewl.boatspeedy.ui.RADAR_MIN_FACTOR
import de.kewl.boatspeedy.ui.contains
import de.kewl.boatspeedy.ui.expand
import de.kewl.boatspeedy.ui.intersect
import de.kewl.boatspeedy.ui.radarZoomFactor
import de.kewl.boatspeedy.ui.width
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RadarWindowTest {

    /**
     * Der eigentliche Fehler: `640 / breite` als ganzzahlige Division ergab ab etwa
     * 180 km Blickbreite genau 1 — die Glättung lief, veränderte aber nichts, und das
     * Rohraster stand in Würfeln auf dem Bildschirm.
     */
    @Test
    fun `weiter Blick wird nicht mehr ungeglaettet durchgereicht`() {
        // Fenstergrößen in Rasterzellen (1 Zelle = 1 km), die früher auf Faktor 1 fielen.
        for (span in listOf(324.0, 450.0, 600.0)) {
            val alt = 640 / span.toInt() // die alte Rechnung
            assertEquals("alte Rechnung ergab keine 1 bei $span", 1, alt)
            assertTrue(
                "Faktor bei $span Zellen muss aufbereiten lassen, war ${radarZoomFactor(span)}",
                radarZoomFactor(span) >= RADAR_MIN_FACTOR,
            )
        }
    }

    @Test
    fun `naher Blick wird begrenzt statt unbegrenzt vergroessert`() {
        // 30 Zellen Fenster: ohne Deckel wären das über 25-fach, das kostet nur Speicher.
        assertEquals(16.0, radarZoomFactor(30.0), 1e-9)
    }

    @Test
    fun `leeres Fenster liefert keinen Faktor`() {
        assertEquals(0.0, radarZoomFactor(0.0), 1e-9)
    }

    @Test
    fun `Schnittmenge beschneidet auf das Radargebiet`() {
        // Fenster ragt links und unten über Deutschland hinaus.
        val window = MercatorBox(200_000.0, 5_000_000.0, 900_000.0, 6_500_000.0)
        val cut = window.intersect(RADAR_AREA_GER)
        assertNotNull(cut)
        assertEquals(RADAR_AREA_GER.minX, cut!!.minX, 1e-6)
        assertEquals(RADAR_AREA_GER.minY, cut.minY, 1e-6)
        assertEquals(900_000.0, cut.maxX, 1e-6)
        assertEquals(6_500_000.0, cut.maxY, 1e-6)
    }

    @Test
    fun `Schnittmenge ist null wenn nichts gemeinsam ist`() {
        assertNull(MercatorBox(0.0, 0.0, 100.0, 100.0).intersect(RADAR_AREA_GER))
    }

    /**
     * An der Grenze des Radargebiets darf die Prüfung nicht dauerhaft anschlagen: das
     * gemerkte Fenster wird deshalb **unbeschnitten** verglichen. Beschnitten könnte es
     * den Ausschnitt nie enthalten, und es würde alle 400 ms neu gerechnet.
     */
    @Test
    fun `Fenster am Gebietsrand loest keine Dauerneuberechnung aus`() {
        // Ausschnitt an der deutschen Westgrenze, halb außerhalb des Radargebiets.
        val view = MercatorBox(400_000.0, 6_000_000.0, 700_000.0, 6_300_000.0)
        val unbeschnitten = view.expand(1.4)
        assertTrue("unbeschnitten muss den Ausschnitt enthalten", unbeschnitten.contains(view))

        val beschnitten = unbeschnitten.intersect(RADAR_AREA_GER)!!
        assertTrue(
            "beschnitten kann ihn nicht enthalten - genau deshalb wird unbeschnitten gemerkt",
            !beschnitten.contains(view),
        )
    }

    @Test
    fun `expand vergroessert um den Mittelpunkt`() {
        val b = MercatorBox(0.0, 0.0, 100.0, 200.0)
        val e = b.expand(2.0)
        assertEquals(200.0, e.width, 1e-9)
        assertEquals(-50.0, e.minX, 1e-9)
        assertEquals(150.0, e.maxX, 1e-9)
    }
}
