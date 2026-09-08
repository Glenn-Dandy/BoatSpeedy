package de.kewl.boatspeedy

import de.kewl.boatspeedy.nav.markerBearingDeg
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Der Pfeil hing bei „Fahrtrichtung oben" in einer beliebigen Richtung fest.
 *
 * osmdroid rechnet die Kartendrehung beim Marker wieder heraus, sodass am Ende `-Peilung`
 * auf dem Schirm steht — die Drehung fällt also heraus. Mit `-Kurs` als Peilung stimmte
 * das bei „Norden oben"; sobald sich die Karte drehte, zeigte der Pfeil weiter in die
 * Himmelsrichtung, während sich alles darunter mitdrehte.
 */
class MarkerBearingTest {

    /** Was osmdroid am Ende auf den Schirm bringt: Zeichenfläche plus Marker-Ausgleich. */
    private fun aufDemSchirm(bearing: Float, mapRotation: Float): Float {
        val ausgleich = -mapRotation - bearing   // Marker.draw, nicht flach
        return ((mapRotation + ausgleich) % 360f + 360f) % 360f
    }

    @Test
    fun `bei Norden oben zeigt der Pfeil in den Kurs`() {
        for (kurs in listOf(0f, 45f, 90f, 180f, 270f, 359f)) {
            val b = markerBearingDeg(kurs, 0f)
            assertEquals("Kurs $kurs", kurs, aufDemSchirm(b, 0f), 0.01f)
        }
    }

    @Test
    fun `bei Fahrtrichtung oben zeigt der Pfeil senkrecht nach oben`() {
        for (kurs in listOf(0f, 45f, 90f, 180f, 270f, 359f)) {
            // Die Karte steht dann auf -Kurs.
            val b = markerBearingDeg(kurs, -kurs)
            assertEquals("Kurs $kurs", 0f, aufDemSchirm(b, -kurs), 0.01f)
        }
    }

    @Test
    fun `das alte Vorgehen zeigte bei gedrehter Karte daneben`() {
        // So stand es vorher da: Peilung = -Kurs, unabhängig von der Kartendrehung.
        val kurs = 90f
        assertEquals(90f, aufDemSchirm(-kurs, -kurs), 0.01f)
        // Statt senkrecht nach oben zeigte der Pfeil um den vollen Kurs daneben.
    }

    @Test
    fun `das Ergebnis liegt immer zwischen 0 und 360`() {
        for (kurs in 0..359) {
            val b = markerBearingDeg(kurs.toFloat(), -kurs.toFloat() / 2f)
            assert(b in 0f..360f) { "Kurs $kurs ergab $b" }
        }
    }
}
