package de.kewl.boatspeedy.battery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Gegen einen echten Frame des Shunt-Messgeräts (`WBMS`) geprüft, mitgeschnitten mit der
 * BLE-Diagnose am 01.09.2026. Die 13,3 V passen zu einer ruhenden 12-V-LiFePO4 — das ist
 * die Bestätigung, dass die Byte-Positionen stimmen und nicht nur plausibel aussehen.
 */
class MeterParserTest {

    private fun bytes(hex: String) =
        hex.trim().split(" ").map { it.toInt(16).toByte() }.toByteArray()

    private val frame = bytes("B5 5B 01 01 5A 03 8D 00 85 00 00 00 00 00 00 00 78 00 00 A8 5E")

    @Test
    fun `Messwerte werden richtig gelesen`() {
        val d = MeterProtocol().onChunk(frame, BatteryData())
        assertNotNull("Frame wurde nicht ausgewertet", d)
        assertEquals(13.3f, d!!.voltage, 0.01f)
        assertEquals(0.0f, d.currentA, 0.01f)
        assertEquals(0.120f, d.energyKWh!!, 0.0001f)
        // SoC und Rest wurden nachträglich gefunden. Beleg: 90,9 Ah von 100 Ah sind
        // 90,9 % — und genau 90 % meldet das Gerät auf Byte 4. Das passt nicht zufällig.
        assertEquals(90, d.soc)
        assertEquals(90.9f, d.remainingAh, 0.01f)
    }

    @Test
    fun `Entladen wird negativ gemeldet`() {
        // Betrag 9,5 A im Frame → −9,5 A in der App (BoatSpeedy: negativ = Entladen).
        val f = bytes("B5 5B 01 01 5B 03 91 00 84 00 5F 00 00 00 00 00 79 00 00 00 00")
        val d = MeterProtocol().onChunk(f, BatteryData())!!
        assertEquals(-9.5f, d.currentA, 0.01f)
        assertEquals(91, d.soc)
        assertEquals(91.3f, d.remainingAh, 0.01f)
        assertEquals(13.2f, d.voltage, 0.01f)
    }

    @Test
    fun `stueckweise Zustellung ergibt dasselbe`() {
        val p = MeterProtocol()
        var last: BatteryData? = null
        frame.toList().chunked(8).forEach { part ->
            p.onChunk(part.toByteArray(), BatteryData())?.let { last = it }
        }
        assertNotNull(last)
        assertEquals(13.3f, last!!.voltage, 0.01f)
    }

    @Test
    fun `Muell vor dem Frame wird abgeschnitten`() {
        val p = MeterProtocol()
        val d = p.onChunk(bytes("00 FF 12") + frame, BatteryData())
        assertNotNull("Auf den Kopf B5 5B wurde nicht ausgerichtet", d)
        assertEquals(13.3f, d!!.voltage, 0.01f)
    }

    @Test
    fun `unvollstaendiger Frame liefert noch nichts`() {
        assertNull(MeterProtocol().onChunk(bytes("B5 5B 01 01"), BatteryData()))
    }

    @Test
    fun `das Geraet wird nicht abgefragt`() {
        // Es sendet von selbst – ein Poll-Befehl waere nutzlos.
        assertTrue(MeterProtocol().pollCommands(0).isEmpty())
    }

    @Test
    fun `Einstellbefehle haben die Bytes aus dem Original`() {
        assertEquals(
            "A5 5A 00 0F 00 00 00 00 F1 00 00 00 00",
            MeterProtocol.Commands.CLEAR_ENERGY.joinToString(" ") { "%02X".format(it) },
        )
        assertEquals(
            "A5 5A 00 03 00 00 00 00 FD 00",
            MeterProtocol.Commands.ZERO_CURRENT.joinToString(" ") { "%02X".format(it) },
        )
    }
}
