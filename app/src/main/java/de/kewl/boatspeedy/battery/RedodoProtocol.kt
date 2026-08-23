package de.kewl.boatspeedy.battery

import java.util.UUID

/**
 * Redodo / LiTime / Power Queen — dieselbe Hardware unter drei Namen.
 *
 * Service FFE0, **geschrieben wird auf FFE2** (mit Antwort), gemeldet wird auf FFE1.
 * Der Schreibkanal kann selbst nichts melden; wer den Notify-Kanal auch zum Schreiben
 * nimmt, bekommt vom BMS keinen Ton – daran ist die erste Feldmessung gescheitert.
 *
 * Protokoll aus zwei unabhängigen Nachbauten der Hersteller-App:
 * https://github.com/calledit/LiTime_BMS_bluetooth
 * https://github.com/rubenmuehlhans/litime-ble-hacs
 *
 * Befehl: 8 Byte `00 00 04 01 <cmd> 55 AA <prüfsumme>`, Prüfsumme = 0x04 + cmd.
 * Antwort: mindestens 104 Byte, gültig wenn Byte 2 == 0x65, alles Little-Endian.
 */
class RedodoProtocol : BmsProtocol() {
    override val type = BmsType.REDODO
    override val serviceUuid: UUID = uuid16("ffe0")
    override val notifyUuid: UUID = uuid16("ffe1")
    override val writeUuid: UUID = uuid16("ffe2")

    /** Die Hersteller-App schreibt mit Quittung – ohne Antwort bleibt das BMS stumm. */
    override val writeNoResponse: Boolean = false

    private companion object {
        const val CMD_QUERY_STATUS = 0x13
        const val MIN_RESPONSE = 104
        const val MARKER_OFFSET = 2
        const val MARKER_VALUE = 0x65
        const val MAX_CELLS = 16
    }

    private val buf = ArrayList<Byte>(160)

    override fun pollCommands(cycle: Int): List<ByteArray> = listOf(command(CMD_QUERY_STATUS))

    private fun command(cmd: Int): ByteArray = byteArrayOf(
        0x00, 0x00, 0x04, 0x01, cmd.toByte(), 0x55, 0xAA.toByte(), ((0x04 + cmd) and 0xFF).toByte(),
    )

    override fun onChunk(chunk: ByteArray, current: BatteryData): BatteryData? {
        // Bei üblicher MTU (>= 107) kommt die Antwort am Stück; bei kleinerer MTU
        // stückweise. Deshalb sammeln und ab der Mindestlänge auswerten.
        for (b in chunk) buf.add(b)
        // Vor den Marker gelaufenen Müll abschneiden.
        while (buf.size > MARKER_OFFSET && buf[MARKER_OFFSET] != MARKER_VALUE.toByte()) {
            buf.removeAt(0)
            if (buf.size > 2 * MIN_RESPONSE) { buf.clear(); return null }
        }
        if (buf.size < MIN_RESPONSE) return null

        val f = ByteArray(MIN_RESPONSE) { buf[it] }
        repeat(MIN_RESPONSE) { buf.removeAt(0) }
        return parse(f, current)
    }

    private fun parse(f: ByteArray, current: BatteryData): BatteryData {
        val cells = ArrayList<Float>(MAX_CELLS)
        for (i in 0 until MAX_CELLS) {
            val mv = u16(f, 16 + i * 2)
            if (mv > 0) cells.add(mv / 1000f)
        }
        val remaining = u16(f, 62) / 100f
        val nominal = u16(f, 64) / 100f
        val soc = u16(f, 90).coerceIn(0, 100)
        // Byte 88: 0 = Ruhe/Entladen, 1 = Laden, 4 = Laden gesperrt.
        val state = u16(f, 88)
        return current.copy(
            voltage = u32(f, 12) / 1000f,
            currentA = s32(f, 48) / 1000f,
            soc = soc,
            remainingAh = remaining,
            nominalAh = nominal,
            cellCount = cells.size,
            tempC = s16(f, 52).toFloat(),
            chargingFet = state != 4,
            dischargingFet = true,
            cells = cells,
        )
    }

    private fun u16(b: ByteArray, i: Int): Int =
        (b[i].toInt() and 0xFF) or ((b[i + 1].toInt() and 0xFF) shl 8)

    private fun s16(b: ByteArray, i: Int): Int = u16(b, i).let { if (it > 0x7FFF) it - 0x10000 else it }

    private fun u32(b: ByteArray, i: Int): Long =
        (b[i].toLong() and 0xFF) or ((b[i + 1].toLong() and 0xFF) shl 8) or
            ((b[i + 2].toLong() and 0xFF) shl 16) or ((b[i + 3].toLong() and 0xFF) shl 24)

    private fun s32(b: ByteArray, i: Int): Int = u32(b, i).toInt()
}
