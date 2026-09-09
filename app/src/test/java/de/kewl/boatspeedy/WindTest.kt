package de.kewl.boatspeedy

import de.kewl.boatspeedy.ui.windArrow
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

    @Test
    fun `die Himmelsrichtung trifft die Sektorgrenzen`() {
        assertEquals("N", windArrow(0))
        assertEquals("N", windArrow(22))
        assertEquals("NO", windArrow(23))
        assertEquals("NO", windArrow(45))
        assertEquals("O", windArrow(90))
        assertEquals("S", windArrow(180))
        assertEquals("W", windArrow(270))
        assertEquals("NW", windArrow(315))
        assertEquals("N", windArrow(350))
    }

    @Test
    fun `negative und ueberdrehte Werte kippen nicht um`() {
        assertEquals(windArrow(10), windArrow(370))
        assertEquals(windArrow(350), windArrow(-10))
    }
}
