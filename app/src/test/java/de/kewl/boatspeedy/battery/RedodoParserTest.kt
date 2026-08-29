package de.kewl.boatspeedy.battery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Gegen einen **echten** Frame geprüft, mitgeschnitten von @toontoon1 an einer
 * Redodo 12 V 100 Ah (`R-12100BNNA70-A02017`, Issue #44, 23.08.2026). Die gelesenen
 * Werte decken sich mit dem, was die Redodo-App zur selben Zeit zeigte.
 */
class RedodoParserTest {

    private fun bytes(hex: String) =
        hex.trim().split(" ").map { it.toInt(16).toByte() }.toByteArray()

    private val statusFrame = bytes(
        "00 00 65 01 93 55 AA 00 46 34 00 00 24 34 00 00 0A 0D 0A 0D 08 0D 08 0D " +
            "00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 " +
            "00 00 00 00 15 00 15 00 00 00 00 00 00 00 76 2B 73 27 00 00 00 00 00 00 " +
            "00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 63 00 64 00 00 00 " +
            "02 00 00 00 DE 00 00 00 2E",
    )

    @Test
    fun `Statusantwort wird richtig gelesen`() {
        val p = RedodoProtocol()
        val data = p.onChunk(statusFrame, BatteryData())
        assertNotNull("Frame wurde nicht ausgewertet", data)
        data!!
        // Die Hersteller-App zeigte zeitgleich 13,3 V · 0,0 A · 99 %.
        assertEquals(13.348f, data.voltage, 0.001f)
        assertEquals(0.0f, data.currentA, 0.001f)
        assertEquals(99, data.soc)
        assertEquals(111.26f, data.remainingAh, 0.01f)
        assertEquals(100.99f, data.nominalAh, 0.01f)
        assertEquals(21.0f, data.tempC!!, 0.01f)
        assertEquals(4, data.cellCount)
        assertEquals(listOf(3.338f, 3.338f, 3.336f, 3.336f), data.cells)
        assertEquals(2, data.cycles)
        assertEquals(222f, data.dischargedAhTotal!!, 0.01f)
    }

    @Test
    fun `stueckweise Zustellung ergibt dasselbe`() {
        // Ohne größere MTU kommt die Antwort in 20-Byte-Häppchen an.
        val p = RedodoProtocol()
        var last: BatteryData? = null
        statusFrame.toList().chunked(20).forEach { part ->
            p.onChunk(part.toByteArray(), BatteryData())?.let { last = it }
        }
        assertNotNull("Zusammengesetzter Frame wurde nicht ausgewertet", last)
        assertEquals(99, last!!.soc)
        assertEquals(13.348f, last!!.voltage, 0.001f)
    }

    @Test
    fun `Fremdes Geplapper wird nicht als Frame gedeutet`() {
        val p = RedodoProtocol()
        // Die Antwort des AT-Kanals FFE3 auf der Redodo: ASCII "+ER".
        assertNull(p.onChunk(bytes("2B 45 52"), BatteryData()))
    }

    @Test
    fun `Befehl traegt die richtige Pruefsumme`() {
        val cmd = RedodoProtocol().pollCommands(0).single()
        assertEquals(
            listOf(0x00, 0x00, 0x04, 0x01, 0x13, 0x55, 0xAA, 0x17),
            cmd.map { it.toInt() and 0xFF },
        )
    }
}
