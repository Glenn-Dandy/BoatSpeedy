package de.kewl.boatspeedy

import de.kewl.boatspeedy.nav.WaterRouter
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Overpass bricht eine zu große Abfrage nach seiner Zeitgrenze ab und antwortet
 * **trotzdem** mit Status 200 und gültigem JSON — nur mit den Daten, die bis dahin
 * zusammenkamen, und einem `remark` daneben. Gemessen am 2026-09-08 über den Rhein von
 * Basel bis Mainz: 371 Byte, null Elemente, „Query timed out".
 *
 * Ungeprüft durchgereicht ergibt das ein Wegenetz mit Löchern, und die App meldet dann
 * „kein durchgehender Wasserweg" — ehrlich, aber mit falscher Begründung.
 */
class OverpassCompleteTest {

    @Test
    fun `Antwort ohne Bemerkung gilt als vollstaendig`() {
        assertTrue(WaterRouter.isComplete("""{"version":0.6,"elements":[]}"""))
    }

    @Test
    fun `abgebrochene Abfrage wird erkannt`() {
        val echt = """{"version":0.6,"generator":"Overpass API","elements":[],""" +
            """"remark":"runtime error: Query timed out in \"query\" at line 2 after 2 seconds."}"""
        assertFalse(WaterRouter.isComplete(echt))
    }

    @Test
    fun `Teilantwort mit Daten wird ebenfalls verworfen`() {
        // Der gefährliche Fall: Es sind Daten da, nur eben nicht alle.
        val teil = """{"elements":[{"type":"way","tags":{"waterway":"river"}}],""" +
            """"remark":"runtime error: Query timed out"}"""
        assertFalse(WaterRouter.isComplete(teil))
    }

    @Test
    fun `harmlose Bemerkung laesst die Antwort gelten`() {
        // Overpass benutzt remark auch für Hinweise ohne Datenverlust.
        val hinweis = """{"elements":[],"remark":"considered 3 areas"}"""
        assertTrue(WaterRouter.isComplete(hinweis))
    }

    @Test
    fun `Unsinn stuerzt nicht ab`() {
        assertTrue(WaterRouter.isComplete("kein json"))
    }

    /**
     * `remark` gibt es auch als OSM-Merkmal an Wegen. Eine Route darf daran nicht
     * scheitern — deshalb wird auf Overpass' eigenen Wortlaut geprüft, nicht auf jede
     * Bemerkung.
     */
    @Test
    fun `remark als Merkmal eines Weges stoert nicht`() {
        val weg = """{"elements":[{"type":"way","tags":{"waterway":"river",""" +
            """"remark":"Fahrrinne wird jährlich neu ausgebaggert"}}]}"""
        assertTrue(WaterRouter.isComplete(weg))
    }
}
