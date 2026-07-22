package de.kewl.boatspeedy.battery

import java.util.UUID

/**
 * Daly-Smart-BMS (EXPERIMENTELL, ungetestet). Nach öffentlichen Protokoll-Docs:
 * Service FFF0, notify FFF1, write FFF2. Feste 13-Byte-Frames, Start 0xA5.
 *
 * Befehl:  A5 40 <DataID> 08 00×8 <chk>, chk = Summe der ersten 12 Bytes & 0xFF.
 * Antwort: A5 01 <DataID> 08 <d0..d7> <chk>.
 */
class DalyProtocol : BmsProtocol() {
    override val type = BmsType.DALY
    override val serviceUuid: UUID = uuid16("fff0")
    override val notifyUuid: UUID = uuid16("fff1")
    override val writeUuid: UUID = uuid16("fff2")

    private val buf = ArrayList<Byte>()

    private fun command(dataId: Int): ByteArray {
        val f = ByteArray(13)
        f[0] = 0xA5.toByte(); f[1] = 0x40; f[2] = dataId.toByte(); f[3] = 0x08
        var sum = 0
        for (i in 0 until 12) sum += f[i].toInt() and 0xFF
        f[12] = (sum and 0xFF).toByte()
        return f
    }

    override fun pollCommands(cycle: Int): List<ByteArray> =
        if (cycle % 3 == 0) listOf(command(0x90), command(0x92), command(0x94))
        else listOf(command(0x90))

    override fun onChunk(chunk: ByteArray, current: BatteryData): BatteryData? {
        for (b in chunk) buf.add(b)
        var out: BatteryData? = null
        while (true) {
            val start = buf.indexOfFirst { it == 0xA5.toByte() }
            if (start < 0) { buf.clear(); break }
            if (start > 0) repeat(start) { buf.removeAt(0) }
            if (buf.size < 13) break
            val frame = ByteArray(13) { buf[it] }
            repeat(13) { buf.removeAt(0) }
            if (!checksumOk(frame)) continue
            out = parse(frame, out ?: current)
        }
        return out
    }

    private fun checksumOk(f: ByteArray): Boolean {
        var sum = 0
        for (i in 0 until 12) sum += f[i].toInt() and 0xFF
        return (sum and 0xFF).toByte() == f[12]
    }

    private fun u16(f: ByteArray, i: Int) = ((f[i].toInt() and 0xFF) shl 8) or (f[i + 1].toInt() and 0xFF)

    private fun parse(f: ByteArray, base: BatteryData): BatteryData {
        val d = 4 // Daten beginnen bei Byte 4
        return when (f[2].toInt() and 0xFF) {
            0x90 -> base.copy(
                voltage = u16(f, d) * 0.1f,
                currentA = (u16(f, d + 4) - 30000) * 0.1f,   // Offset 30000
                soc = (u16(f, d + 6) * 0.1f).toInt(),
            )
            0x92 -> base.copy(tempC = (f[d].toInt() and 0xFF) - 40f) // max-Temp, Offset 40
            0x94 -> base.copy(cellCount = f[d].toInt() and 0xFF)
            else -> base
        }
    }
}
