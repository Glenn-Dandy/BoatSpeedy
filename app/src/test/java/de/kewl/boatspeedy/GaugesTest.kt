package de.kewl.boatspeedy

import de.kewl.boatspeedy.nav.GaugeLevel
import de.kewl.boatspeedy.nav.Gauges
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GaugesTest {

    /** Aus der Antwort von PEGELONLINE vom 14.11.2026, auf drei Pegel gekürzt. */
    private val json = """
        [{"uuid":"b475386c-30cc-453a-b3b7-1d17ace13595","number":"48300105","shortname":"CELLE","longname":"CELLE",
          "km":1.74,"agency":"VERDEN","longitude":10.062164,"latitude":52.622706,
          "water":{"shortname":"ALLER","longname":"ALLER"},
          "timeseries":[{"shortname":"W","longname":"WASSERSTAND ROHDATEN","unit":"cm","equidistance":15,
            "currentMeasurement":{"timestamp":"2026-11-14T22:15:00+01:00","value":115.0,"stateMnwMhw":"low","stateNswHsw":"normal"}}]},
         {"uuid":"x1","longname":"WEHR GEESTHACHT UP","longitude":10.31,"latitude":53.42,
          "water":{"longname":"ELBE"},
          "timeseries":[{"shortname":"W","currentMeasurement":{"timestamp":"2026-11-14T22:15:00+01:00","value":480.0,"stateMnwMhw":"high","stateNswHsw":"high"}}]},
         {"uuid":"x2","longname":"OHNE LAGE","timeseries":[]}]
    """.trimIndent()

    @Test
    fun `liest Wert, Stand und Lage, Pegel ohne Lage fallen weg`() {
        val g = Gauges.parse(json)
        assertEquals(2, g.size)
        val celle = g[0]
        assertEquals("Celle", celle.name)
        assertEquals("Aller", celle.water)
        assertEquals(115.0, celle.valueCm!!, 0.0)
        assertEquals(GaugeLevel.LOW, celle.level)
        assertEquals(1.74, celle.km!!, 0.0)
        // Über dem höchsten Schifffahrtswasserstand geht vor „hoch“.
        assertEquals(GaugeLevel.ABOVE_HSW, g[1].level)
    }

    @Test
    fun `Namen lesbar, kurze Zusaetze bleiben gross`() {
        assertEquals("Wehr Geesthacht UP", Gauges.readableName("WEHR GEESTHACHT UP"))
        assertEquals("Hamburg St. Pauli", Gauges.readableName("HAMBURG ST. PAULI"))
        assertEquals("Hamburg-Harburg", Gauges.readableName("HAMBURG-HARBURG"))
        assertEquals("Schöpfstelle", Gauges.readableName("SCHÖPFSTELLE"))
    }

    @Test
    fun `Aenderung in der letzten Stunde`() {
        val m = """[
            {"timestamp":"2026-11-14T20:30:00+01:00","value":110.0},
            {"timestamp":"2026-11-14T21:00:00+01:00","value":112.0},
            {"timestamp":"2026-11-14T21:30:00+01:00","value":115.0},
            {"timestamp":"2026-11-14T22:00:00+01:00","value":118.0}]"""
        assertEquals(6.0, Gauges.changeLastHour(m)!!, 0.0)
    }

    @Test
    fun `ohne Wert vor einer Stunde keine Aenderung`() {
        val m = """[{"timestamp":"2026-11-14T21:45:00+01:00","value":112.0},
            {"timestamp":"2026-11-14T22:00:00+01:00","value":118.0}]"""
        assertNull(Gauges.changeLastHour(m))
    }
}
