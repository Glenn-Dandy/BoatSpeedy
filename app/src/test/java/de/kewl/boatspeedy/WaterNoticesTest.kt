package de.kewl.boatspeedy

import de.kewl.boatspeedy.nav.LatLon
import de.kewl.boatspeedy.nav.WaterNotices
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class WaterNoticesTest {

    /**
     * Zwei echte Meldungen aus nts.json vom 14.11.2026, gekürzt auf das Nötige: die
     * Sperrung der Schleuse Charlottenburg (ein Punkt) und der Havelkanal (ein Abschnitt
     * von gut 15 km).
     */
    private val json = """
        {"version":1,"generated":"2026-11-14T21:40Z","source":"www.elwis.de","messages":[
         {"id":"2026-3164","subject":"ANNOUN","from":"2026-11-13","to":"2026-11-20",
          "text":"Aus technischen Gründen ist die Schleuse Charlottenburg bis auf Weiteres für den gesamten Schiffsverkehr gesperrt.",
          "by":"WSA Spree-Havel","reason":"LIMITA",
          "places":[{"name":"Charlottenburg (neu)","type":"LCK","fairway":"Spree-Oder-Wasserstraße",
            "points":[[52.53105,13.286217]],
            "limits":[{"code":"OBSTRU","periods":[{"from":"2026-11-13","to":"2026-11-20","tfrom":"13:20","interval":"CON"}]}]}]},
         {"id":"2011-1425","subject":"INFSER","from":"2023-08-23","to":"2028-08-31",
          "text":"Beibehaltung größerer Abmessungen und einer Abladetiefe von 2,30 m.",
          "by":"WSA Spree-Havel","reason":"OTHER",
          "places":[{"name":"Havelkanal","type":"FWY","fairway":"Havelkanal","section":true,
            "points":[[52.595667,12.98045],[52.462367,12.883617]]}]}
        ]}
    """.trimIndent()

    private val all = WaterNotices.parse(json)
    private val schleuse = all.first { it.id == "2026-3164" }
    private val kanal = all.first { it.id == "2011-1425" }

    @Test
    fun `liest Text, Stelle, Ort und Sperrung`() {
        assertEquals(2, all.size)
        assertTrue(schleuse.isClosure)
        assertFalse(kanal.isClosure)
        assertEquals("WSA Spree-Havel", schleuse.by)
        assertEquals(LatLon(52.53105, 13.286217), schleuse.anchor)
        assertEquals("13:20", schleuse.places[0].limits[0].periods[0].timeFrom)
        assertTrue(kanal.places[0].section)
    }

    @Test
    fun `der Text bleibt wortgleich`() {
        assertEquals(
            "Aus technischen Gründen ist die Schleuse Charlottenburg bis auf Weiteres für den gesamten Schiffsverkehr gesperrt.",
            schleuse.text,
        )
    }

    @Test
    fun `gilt nur im Zeitraum der Einschraenkung`() {
        val d = LocalDate.of(2026, 11, 14)
        assertTrue(WaterNotices.activeBetween(schleuse, d, d.plusDays(2)))
        assertFalse(WaterNotices.activeBetween(schleuse, d.plusDays(7), d.plusDays(9)))
        assertFalse(WaterNotices.activeBetween(schleuse, d.minusDays(5), d.minusDays(3)))
        // Ohne eigene Zeiträume gilt die ganze Meldung.
        assertTrue(WaterNotices.activeBetween(kanal, d, d))
    }

    @Test
    fun `eine Route durch die Schleuse trifft die Meldung, eine daneben nicht`() {
        // Spree, gut 100 m an der Schleuse vorbei.
        val durch = listOf(LatLon(52.5300, 13.2800), LatLon(52.5315, 13.2870), LatLon(52.5330, 13.2950))
        assertEquals(listOf("2026-3164"), WaterNotices.alongRoute(all, durch).map { it.id })
        // Gut einen Kilometer südlich.
        val daneben = durch.map { LatLon(it.lat - 0.012, it.lon) }
        assertTrue(WaterNotices.alongRoute(all, daneben).isEmpty())
    }

    @Test
    fun `wer mitten im Abschnitt ablegt, bekommt ihn trotzdem`() {
        // Start und Ziel im Havelkanal, beide Enden des Abschnitts mehrere Kilometer weg.
        val mitten = listOf(LatLon(52.540, 12.940), LatLon(52.520, 12.925))
        assertTrue(WaterNotices.distanceToPathM(kanal.places[0].points[0], mitten) > 5_000)
        assertEquals(listOf("2011-1425"), WaterNotices.alongRoute(all, mitten).map { it.id })
    }

    @Test
    fun `ein Gewaesser weit neben dem Abschnitt zaehlt nicht`() {
        // Parallel, gut 5 km östlich.
        val parallel = listOf(LatLon(52.540, 13.020), LatLon(52.520, 13.005))
        assertTrue(WaterNotices.alongRoute(all, parallel).isEmpty())
    }

    /**
     * Die Sperrung des Landwehrkanals vom 15.11.2026. Sein westliches Ende liegt 45 m neben
     * der Spree. Eine Fahrt auf der Spree daran vorbei bekam die Sperrung trotzdem.
     * Der Weg ist das echte Stück Spree aus den Kartendaten (Charlottenburg, Mündung des
     * Landwehrkanals, Moabit).
     */
    @Test
    fun `ein Kanal, der an der Route beginnt, zaehlt nur, wenn man hineinfaehrt`() {
        val all = WaterNotices.parse(
            """
            {"messages":[{"id":"2026-362","subject":"ANNOUN","from":"2026-01-01","to":"2027-03-31",
              "places":[{"name":"Landwehrkanal","type":"FWY","fairway":"Landwehrkanal","section":true,
                "points":[[52.5203,13.3177],[52.5114,13.3363]],"limits":[{"code":"OBSTRU"}]}]}]}
            """.trimIndent(),
        )
        val lwk = all.single()
        val spree = listOf(LatLon(52.5265, 13.2905), LatLon(52.5214, 13.3150), LatLon(52.5207, 13.3174),
            LatLon(52.5212, 13.3250), LatLon(52.5230, 13.3400))
        assertFalse(lwk in WaterNotices.alongRoute(all, spree))
        // Durch den Kanal hindurch: beide Enden an der Route.
        val hinein = spree.take(3) + listOf(LatLon(52.5160, 13.3270), LatLon(52.5114, 13.3363), LatLon(52.5080, 13.3450))
        assertTrue(lwk in WaterNotices.alongRoute(all, hinein))
        // Im Kanal endet die Fahrt: das Ziel liegt im Abschnitt.
        val bisMitte = spree.take(3) + listOf(LatLon(52.5160, 13.3270))
        assertTrue(lwk in WaterNotices.alongRoute(all, bisMitte))
    }

    @Test
    fun `Ausschnitt nach Lage`() {
        assertEquals(listOf("2026-3164"), WaterNotices.inBox(all, 52.5, 13.2, 52.6, 13.4).map { it.id })
    }

    /**
     * Schleuse Mühlendamm, Meldungen vom 15.11.2026: die Sperrung der Nordkammer (LKB) und
     * eine Brücke 150 m daneben (BRI), gekürzt. Am Symbol der Schleuse gehört nur die
     * Kammer dazu.
     */
    @Test
    fun `Meldungen zur Schleuse`() {
        val all = WaterNotices.parse(
            """
            {"generated":"2026-11-15T09:30Z","messages":[
             {"id":"2026-3099","subject":"ANNOUN","from":"2026-11-11","to":"2026-11-30",
              "places":[{"name":"Mühlendamm, Nordkammer","type":"LKB","points":[[52.5149,13.4102]],
                "limits":[{"code":"OBSTRU"}]}]},
             {"id":"brücke","subject":"ANNOUN","from":"2026-11-11","to":"2026-11-30",
              "places":[{"name":"Mühlendammbrücke","type":"BRI","points":[[52.5151,13.4080]],
                "limits":[{"code":"CAUTIO"}]}]}
            ]}
            """.trimIndent(),
        )
        assertEquals(listOf("2026-3099"), WaterNotices.atLock(all, 52.5148, 13.4105).map { it.id })
        // Eine andere Schleuse 2 km weiter bekommt sie nicht.
        assertTrue(WaterNotices.atLock(all, 52.5300, 13.4105).isEmpty())
        // Liegt die Kammer als Linie vor, zählt der Abstand zu ihr.
        val kammer = listOf(LatLon(52.5160, 13.4060), LatLon(52.5150, 13.4100))
        assertEquals(1, WaterNotices.atLock(all, 52.5200, 13.4000, kammer).size)
    }

    @Test
    fun `Stand der Meldungen`() {
        assertEquals(java.time.Instant.parse("2026-11-14T21:40:00Z"), WaterNotices.generated(json))
        assertEquals(null, WaterNotices.generated("{}"))
    }
}
