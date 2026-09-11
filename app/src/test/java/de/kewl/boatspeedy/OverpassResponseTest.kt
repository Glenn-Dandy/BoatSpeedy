package de.kewl.boatspeedy

import de.kewl.boatspeedy.nav.WaterRouter
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Overpass meldet Überlast nicht immer mit einem Fehlercode: gemessen am 2026-09-04 kam
 * einmal eine XML-Seite mit „Dispatcher_Client::request_read_and_idx::timeout" unter
 * Status 200. Reicht man die als Ergebnis durch, zeigt die App „hier sind keine
 * Wasserwege verzeichnet" — obwohl nur der Server müde war.
 */
class OverpassResponseTest {

    @Test
    fun `echte Antwort wird angenommen`() {
        assertTrue(WaterRouter.looksLikeJson("""{"elements":[]}"""))
        assertTrue(WaterRouter.looksLikeJson("\n  {\"elements\": [1]}"))
    }

    @Test
    fun `XML-Fehlerseite wird verworfen`() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <osm-derived><note>The data included in this document is from www.openstreetmap.org.</note>
            <remark> runtime error: Dispatcher_Client::request_read_and_idx::timeout.</remark>
            </osm-derived>
        """.trimIndent()
        assertFalse(WaterRouter.looksLikeJson(xml))
    }

    @Test
    fun `leere Antwort wird verworfen`() {
        assertFalse(WaterRouter.looksLikeJson(null))
        assertFalse(WaterRouter.looksLikeJson(""))
        assertFalse(WaterRouter.looksLikeJson("   "))
    }
}
