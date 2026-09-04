package de.kewl.boatspeedy

import de.kewl.boatspeedy.nav.shortestTurn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Der Kurspfeil und der Positionsmarker drehen sich jetzt über knapp eine Sekunde statt
 * zu springen. Dabei muss der **kurze** Weg genommen werden — sonst dreht der Pfeil beim
 * Übergang von 359° auf 1° einmal ganz herum.
 */
class TurnTest {

    @Test
    fun `kleine Drehung bleibt wie sie ist`() {
        assertEquals(10f, shortestTurn(20f, 30f), 1e-4f)
        assertEquals(-10f, shortestTurn(30f, 20f), 1e-4f)
    }

    @Test
    fun `ueber Norden geht es kurz herum`() {
        // 359° -> 1° sind zwei Grad nach rechts, nicht 358 nach links.
        assertEquals(2f, shortestTurn(359f, 1f), 1e-4f)
        assertEquals(-2f, shortestTurn(1f, 359f), 1e-4f)
    }

    @Test
    fun `nie mehr als eine halbe Umdrehung`() {
        for (from in 0 until 360 step 7) {
            for (to in 0 until 360 step 11) {
                val d = shortestTurn(from.toFloat(), to.toFloat())
                assertTrue("$from -> $to ergab $d", d >= -180f && d <= 180f)
            }
        }
    }

    @Test
    fun `fortlaufender Winkel wandert weiter statt zurueckzuspringen`() {
        // So rechnet der Pfeil: er addiert immer nur die kurze Drehung auf.
        var continuous = 350f
        for (target in listOf(355f, 0f, 5f, 10f)) {
            continuous += shortestTurn(continuous, target)
        }
        // Nach vier Schritten über Norden steht er bei 370°, nicht bei 10° –
        // die Animation läuft dadurch vorwärts durch und nicht rückwärts herum.
        assertEquals(370f, continuous, 1e-3f)
    }

    @Test
    fun `gegenueber liegender Kurs dreht hoechstens halb`() {
        assertEquals(180f, kotlin.math.abs(shortestTurn(0f, 180f)), 1e-4f)
    }
}
