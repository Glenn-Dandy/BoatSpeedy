package de.kewl.boatspeedy.battery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Gegen einen **echten** Frame geprüft, mitgeschnitten mit der BLE-Diagnose an der
 * EcoWorthy/JBD 100 Ah (`DP04S007L4S100A`, 23.08.2026).
 *
 * Der Sinn: Die Werte hängen an festen Byte-Positionen. Verrutscht eine davon, zeigt die
 * App weiterhin plausible Zahlen — nur falsche. Das fällt beim Draufschauen nicht auf,
 * sondern erst, wenn die Reichweite auf dem Wasser nicht stimmt.
 */
class JbdParserTest {

    private fun bytes(hex: String) =
        hex.trim().split(" ").map { it.toInt(16).toByte() }.toByteArray()

    private val basicFrame = bytes(
        "DD 03 00 22 05 50 00 00 26 D6 27 10 00 02 34 48 00 00 00 00 " +
            "00 00 66 63 03 04 01 0B 92 00 00 00 27 10 26 D6 00 00 FB 37 77",
    )

    private val cellFrame = bytes("DD 04 00 08 0D 49 0D 4A 0D 4A 0D 43 FE A4 77")

    @Test
    fun `Basisinfos werden richtig gelesen`() {
        val parsed = Jbd.parseFrame(basicFrame)
        assertNotNull("Frame wurde nicht erkannt", parsed)
        assertEquals(Jbd.REG_BASIC, parsed!!.register)
        assertEquals(0, parsed.status)

        val info = Jbd.parseBasic(parsed.payload)
        assertNotNull("Basisinfos nicht auswertbar", info)
        info!!
        assertEquals(13.60f, info.voltage, 0.001f)
        assertEquals(0.00f, info.currentA, 0.001f)
        assertEquals(99, info.soc)
        assertEquals(99.42f, info.remainingAh, 0.001f)
        assertEquals(100.00f, info.nominalAh, 0.001f)
        assertEquals(4, info.cellCount)
        assertEquals(2, info.cycles)
        assertEquals(23.05f, info.tempC!!, 0.01f)
        assertTrue("Laden sollte freigegeben sein", info.chargingFet)
        assertTrue("Entladen sollte freigegeben sein", info.dischargingFet)
    }

    @Test
    fun `Zellspannungen werden richtig gelesen`() {
        val parsed = Jbd.parseFrame(cellFrame)
        assertNotNull(parsed)
        val cells = Jbd.parseCells(parsed!!.payload)
        assertEquals(listOf(3.401f, 3.402f, 3.402f, 3.395f), cells)
    }

    @Test
    fun `kurzer Frame liefert trotzdem die Grundwerte`() {
        // Manche Firmware schickt weniger; früher wurde der Frame komplett verworfen und
        // in der App fehlten alle Werte außer den Zellspannungen.
        val short = bytes("05 50 00 00 26 D6 27 10")
        val info = Jbd.parseBasic(short)
        assertNotNull(info)
        assertEquals(13.60f, info!!.voltage, 0.001f)
        assertEquals(99.42f, info.remainingAh, 0.001f)
        // SoC ohne eigenes Feld aus Rest/Nenn hochgerechnet.
        assertEquals(99, info.soc)
    }
}
