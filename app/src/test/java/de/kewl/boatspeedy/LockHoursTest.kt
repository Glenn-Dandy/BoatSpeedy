package de.kewl.boatspeedy

import de.kewl.boatspeedy.nav.LockHoursSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LockHoursTest {

    /**
     * Echte Einträge aus locks.json vom 15.11.2026, so wie locks.py sie aus den
     * ELWIS-Seiten und den Kacheln baut: Mühlendamm mit seinen beiden Kammern, Kostheim mit
     * zwei Kammern und der Sportbootschleuse, die nur im Sommer läuft. Am Ende ein kaputter
     * Eintrag mit sechs Tagen.
     */
    private val json = """
        {"version":1,"generated":"2026-11-15T14:00Z","source":"www.elwis.de","locks":[
         {"id":"452","name":"Mühlendamm","fairway":"Spree-Oder-Wasserstraße","points":[[52.51468,13.41024],[52.51487,13.41029]],
          "rows":[{"period":"01.01. - 31.03.","days":["06:00 - 20:00","06:00 - 20:00","06:00 - 20:00","06:00 - 20:00","06:00 - 20:00","06:00 - 20:00","07:00 - 19:00"]},
                  {"period":"01.04. - 31.10.","days":["06:00 - 24:00","06:00 - 24:00","06:00 - 24:00","06:00 - 24:00","06:00 - 24:00","06:00 - 24:00","07:00 - 24:00"]}],
          "remarks":"Pegel: +49 30 23459770"},
         {"id":"453","name":"Mühlendamm Nordkammer","fairway":"Spree-Oder-Wasserstraße","points":[[52.51468,13.41024],[52.51487,13.41029]],
          "rows":[{"period":"01.01. - 31.03.","days":["06:00 - 20:00","06:00 - 20:00","06:00 - 20:00","06:00 - 20:00","06:00 - 20:00","06:00 - 20:00","07:00 - 19:00"]},
                  {"period":"01.04. - 31.10.","days":["06:00 - 24:00","06:00 - 24:00","06:00 - 24:00","06:00 - 24:00","06:00 - 24:00","06:00 - 24:00","07:00 - 24:00"]}],
          "remarks":"Pegel: +49 30 23459770"},
         {"id":"454","name":"Mühlendamm Südkammer","fairway":"Spree-Oder-Wasserstraße","points":[[52.51468,13.41024],[52.51487,13.41029]],
          "rows":[{"period":"01.01. - 31.03.","days":["06:00 - 20:00","06:00 - 20:00","06:00 - 20:00","06:00 - 20:00","06:00 - 20:00","06:00 - 20:00","07:00 - 19:00"]},
                  {"period":"01.04. - 31.10.","days":["06:00 - 24:00","06:00 - 24:00","06:00 - 24:00","06:00 - 24:00","06:00 - 24:00","06:00 - 24:00","07:00 - 24:00"]}],
          "remarks":"Pegel: +49 30 23459770"},
         {"id":"270","name":"Kostheim","fairway":"Main","points":[[49.99909,8.33442],[49.9994,8.33462],[49.99983,8.33398]],
          "rows":[{"period":"01.01. - 31.12.","days":["00:00 - 24:00","00:00 - 24:00","00:00 - 24:00","00:00 - 24:00","00:00 - 24:00","00:00 - 24:00","00:00 - 24:00"]}]},
         {"id":"271","name":"Kostheim Nordschleuse obere Kammer","fairway":"Main","points":[[49.99909,8.33442],[49.9994,8.33462],[49.99983,8.33398]],
          "rows":[{"period":"01.01. - 31.12.","days":["00:00 - 24:00","00:00 - 24:00","00:00 - 24:00","00:00 - 24:00","00:00 - 24:00","00:00 - 24:00","00:00 - 24:00"]}]},
         {"id":"559","name":"Kostheim Sportbootschleuse","fairway":"Main","points":[[49.99909,8.33442],[49.9994,8.33462],[49.99983,8.33398]],
          "rows":[{"period":"01.04. - 31.10.","days":["00:00 - 24:00","00:00 - 24:00","00:00 - 24:00","00:00 - 24:00","00:00 - 24:00","00:00 - 24:00","00:00 - 24:00"]}],
          "remarks":"Die Bootsschleuse ist als eine Selbstbedienschleuse, nur in der Zeit vom 01.04. bis 31.10. in Betrieb."},
         {"id":"999","name":"Kaputt","points":[[50.0,8.0]],"rows":[{"period":"01.01. - 31.12.","days":["a","b","c","d","e","f"]}]}
        ]}
    """.trimIndent()

    private val all = LockHoursSource.parse(json)

    @Test
    fun `liest die Tabelle unveraendert und laesst kaputte Eintraege weg`() {
        assertEquals(6, all.size)
        val nord = all.first { it.id == "453" }
        assertEquals("01.01. - 31.03.", nord.rows[0].period)
        assertEquals("07:00 - 19:00", nord.rows[0].days[6])
        assertEquals("Pegel: +49 30 23459770", nord.remarks)
        assertEquals("Spree-Oder-Wasserstraße", nord.fairway)
    }

    @Test
    fun `Schleuse Muehlendamm bekommt ihre Zeiten, Kostheim nicht`() {
        // Das Symbol der Schleuse Mühlendamm auf der Karte, aus OSM.
        val treffer = LockHoursSource.atLock(all, 52.5148, 13.4103)
        assertEquals(setOf("452", "453", "454"), treffer.map { it.id }.toSet())
        // 1 km weiter flussab ist keine Schleuse mehr.
        assertTrue(LockHoursSource.atLock(all, 52.5180, 13.4240).isEmpty())
    }

    @Test
    fun `gleiche Kammern stehen einmal da, unter dem Namen der Schleuse`() {
        val g = LockHoursSource.grouped(LockHoursSource.atLock(all, 52.5148, 13.4103))
        assertEquals(1, g.size)
        assertEquals(listOf("Mühlendamm"), g[0].first)
    }

    @Test
    fun `abweichende Sportbootschleuse bekommt eine eigene Tabelle`() {
        val g = LockHoursSource.grouped(LockHoursSource.atLock(all, 49.9994, 8.3345))
        assertEquals(2, g.size)
        assertEquals(listOf("Kostheim"), g[0].first)
        assertEquals(listOf("Kostheim Sportbootschleuse"), g[1].first)
        assertEquals("01.04. - 31.10.", g[1].second.rows.single().period)
    }

    @Test
    fun `gleiche Tage nebeneinander werden ein Bereich`() {
        val mo = all.first { it.id == "453" }.rows[0].days
        assertEquals(
            listOf(Triple(0, 5, "06:00 - 20:00"), Triple(6, 6, "07:00 - 19:00")),
            LockHoursSource.dayRuns(mo),
        )
        val immer = all.first { it.id == "270" }.rows[0].days
        assertEquals(listOf(Triple(0, 6, "00:00 - 24:00")), LockHoursSource.dayRuns(immer))
        val wechselnd = listOf("a", "a", "b", "a", "a", "kein Betrieb", "kein Betrieb")
        assertEquals(
            listOf(Triple(0, 1, "a"), Triple(2, 2, "b"), Triple(3, 4, "a"), Triple(5, 6, "kein Betrieb")),
            LockHoursSource.dayRuns(wechselnd),
        )
    }
}
