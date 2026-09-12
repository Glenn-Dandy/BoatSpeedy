package de.kewl.boatspeedy

import de.kewl.boatspeedy.nav.openingHoursLines
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Schleusenzeiten stehen in OpenStreetMap als eine Zeile mit Semikolons. Bei Schleusen
 * sind es meist drei Regeln: Sperrzeit im Winter, Saison, Sperrzeit danach. Hintereinander
 * geschrieben liest sie niemand, gerade nicht vom Boot aus.
 */
class OpeningHoursTest {

    @Test
    fun `das Semikolon trennt die Regeln`() {
        val roh = "1. Jan - 30. Apr kein Betrieb; 1. Mai - 16. Okt Mo - So 09:30 - 19:00; " +
            "17. Okt - 31. Dez kein Betrieb"
        assertEquals(
            "1. Jan - 30. Apr kein Betrieb\n" +
                "1. Mai - 16. Okt Mo - So 09:30 - 19:00\n" +
                "17. Okt - 31. Dez kein Betrieb",
            openingHoursLines(roh),
        )
    }

    /** Das Komma trennt die Tage **innerhalb** einer Regel und gehört zusammen. */
    @Test
    fun `das Komma bleibt stehen`() {
        val roh = "Apr-Oct Mo-Th 07:00-19:00, Fr-Su 07:00-21:00; Nov-Mar Mo-Sa 07:00-17:00"
        assertEquals(
            "Apr-Oct Mo-Th 07:00-19:00, Fr-Su 07:00-21:00\nNov-Mar Mo-Sa 07:00-17:00",
            openingHoursLines(roh),
        )
    }

    @Test
    fun `eine einzelne Regel bleibt eine Zeile`() {
        assertEquals("24/7", openingHoursLines("24/7"))
    }

    @Test
    fun `leere Stuecke fallen weg`() {
        assertEquals("Mo-Fr 08:00-16:00", openingHoursLines("Mo-Fr 08:00-16:00;  ; "))
    }
}
