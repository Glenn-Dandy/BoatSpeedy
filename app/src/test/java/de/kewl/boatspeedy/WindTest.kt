package de.kewl.boatspeedy

import de.kewl.boatspeedy.ui.windSector
import de.kewl.boatspeedy.ui.windToDeg
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Windrichtungen werden regelmäßig verkehrt herum gelesen. In der Meteorologie ist die
 * Angabe die Richtung, **aus** der der Wind kommt; der Pfeil auf der Karte zeigt dorthin,
 * **wohin** die Luft zieht. Beides zusammen ergibt eine Drehung um 180 Grad, und genau die
 * stellt irgendwann jemand gut gemeint „richtig".
 */
class WindTest {

    @Test
    fun `der Pfeil zeigt mit dem Wind`() {
        assertEquals("Wind aus Norden zieht nach Süden", 180, windToDeg(0))
        assertEquals("Wind aus Süden zieht nach Norden", 0, windToDeg(180))
        assertEquals("Wind aus Westen zieht nach Osten", 90, windToDeg(270))
        assertEquals(270, windToDeg(90))
    }

    @Test
    fun `die Drehung bleibt im Kreis`() {
        for (d in 0..359) {
            val r = windToDeg(d)
            assert(r in 0..359) { "$d ergab $r" }
        }
        assertEquals(windToDeg(10), windToDeg(370))
    }

    /**
     * Geprüft wird der Sektor, nicht der Buchstabe: Die Abkürzungen stehen in einer
     * Zeichenkettentabelle, weil sie sprachabhängig sind (NO gegen NE, O gegen E).
     */
    @Test
    fun `die Himmelsrichtung trifft die Sektorgrenzen`() {
        assertEquals(0, windSector(0))      // N
        assertEquals(0, windSector(22))
        assertEquals(1, windSector(23))     // NO
        assertEquals(1, windSector(45))
        assertEquals(2, windSector(90))     // O
        assertEquals(4, windSector(180))    // S
        assertEquals(6, windSector(270))    // W
        assertEquals(7, windSector(315))    // NW
        assertEquals(0, windSector(350))
    }

    @Test
    fun `negative und ueberdrehte Werte kippen nicht um`() {
        assertEquals(windSector(10), windSector(370))
        assertEquals(windSector(350), windSector(-10))
        for (d in -720..720) assert(windSector(d) in 0..7) { "$d ergab ${windSector(d)}" }
    }
}
