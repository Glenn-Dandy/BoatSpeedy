package de.kewl.boatspeedy

import de.kewl.boatspeedy.update.UpdateChecker.compareVersions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Wer die Entwicklungsbauten mitgetestet hat, bekam die Veröffentlichung nie angeboten.
 *
 * `1.4.0-dev267` wurde an den Punkten zerlegt, das letzte Stück `0-dev267` ließ sich nicht
 * als Zahl lesen und zählte als 0. Damit war der Entwicklungsbau genauso neu wie das
 * fertige 1.4.0, und die Prüfung meldete "aktuell".
 */
class UpdateCompareTest {

    @Test
    fun `ein Entwicklungsbau ist aelter als die fertige Fassung`() {
        assertTrue("1.4.0 muss neuer sein als 1.4.0-dev267", compareVersions("1.4.0", "1.4.0-dev267") > 0)
        assertTrue(compareVersions("1.4.0-dev267", "1.4.0") < 0)
    }

    @Test
    fun `Zahlen schlagen den Zusatz`() {
        assertTrue(compareVersions("1.4.1", "1.4.0-dev267") > 0)
        assertTrue(compareVersions("1.4.0-dev267", "1.3.9") > 0)
        assertTrue(compareVersions("1.10.0", "1.9.9") > 0)
    }

    @Test
    fun `gleich bleibt gleich`() {
        assertEquals(0, compareVersions("1.4.0", "1.4.0"))
        assertEquals(0, compareVersions("1.4.0-dev267", "1.4.0-dev267"))
    }

    @Test
    fun `zwei Entwicklungsbauten lassen sich ordnen`() {
        assertTrue(compareVersions("1.4.0-dev268", "1.4.0-dev267") > 0)
    }

    /**
     * Die Laufnummer wird irgendwann vierstellig. Als Zeichenkette verglichen stünde
     * `dev999` über `dev1000`, und der Entwicklungsbau würde ab da nie wieder angeboten.
     */
    @Test
    fun `die Laufnummer wird als Zahl verglichen`() {
        assertTrue("dev1000 muss neuer sein als dev999", compareVersions("1.4.1-dev1000", "1.4.1-dev999") > 0)
        assertTrue(compareVersions("1.4.1-dev280", "1.4.1-dev99") > 0)
    }
}
