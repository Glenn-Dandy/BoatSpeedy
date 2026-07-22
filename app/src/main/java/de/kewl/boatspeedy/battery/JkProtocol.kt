package de.kewl.boatspeedy.battery

import java.util.UUID

/**
 * JK-BMS (Jikong), JK02-Protokoll (EXPERIMENTELL, ungetestet). Service FFE0,
 * notify/write FFE1. Antworten sind 300-Byte-Records mit Header 55 AA EB 90,
 * Little-Endian. Offsets nach öffentlichen Integrationen (JK02_32S) — bei realer
 * Hardware ggf. zu justieren.
 */
class JkProtocol : BmsProtocol() {
    override val type = BmsType.JK
    override val serviceUuid: UUID = uuid16("ffe0")
    override val notifyUuid: UUID = uuid16("ffe1")
    override val writeUuid: UUID = uuid16("ffe1")

    private val buf = ArrayList<Byte>()

    private companion object {
        val HEADER = intArrayOf(0x55, 0xAA, 0xEB, 0x90)
        const val RECORD_LEN = 300
        const val TYPE_CELL_INFO = 0x02
        // Offsets im 300-Byte-Record (JK02_32S) – experimentell:
        const val OFF_VOLTAGE = 150   // u32 LE, mV
        const val OFF_CURRENT = 158   // i32 LE, mA
        const val OFF_TEMP1 = 162     // i16 LE, 0,1 °C
        const val OFF_SOC = 141       // u8, %
    }

    private fun command(cmd: Int): ByteArray {
        val f = ByteArray(20)
        f[0] = 0xAA.toByte(); f[1] = 0x55.toByte(); f[2] = 0x90.toByte(); f[3] = 0xEB.toByte()
        f[4] = cmd.toByte()
        var sum = 0
        for (i in 0 until 19) sum += f[i].toInt() and 0xFF
        f[19] = (sum and 0xFF).toByte()
        return f
    }

    override fun pollCommands(cycle: Int): List<ByteArray> = listOf(command(0x96)) // cell info

    override fun onChunk(chunk: ByteArray, current: BatteryData): BatteryData? {
        for (b in chunk) buf.add(b)
        var out: BatteryData? = null
        while (true) {
            val start = headerIndex()
            if (start < 0) {
                // Nur letzte 3 Bytes behalten (könnten Header-Anfang sein).
                while (buf.size > 3) buf.removeAt(0)
                break
            }
            if (start > 0) repeat(start) { buf.removeAt(0) }
            if (buf.size < RECORD_LEN) break
            val rec = ByteArray(RECORD_LEN) { buf[it] }
            repeat(RECORD_LEN) { buf.removeAt(0) }
            if ((rec[4].toInt() and 0xFF) == TYPE_CELL_INFO) out = parse(rec, out ?: current)
        }
        return out
    }

    private fun headerIndex(): Int {
        outer@ for (i in 0..buf.size - HEADER.size) {
            for (j in HEADER.indices) if ((buf[i + j].toInt() and 0xFF) != HEADER[j]) continue@outer
            return i
        }
        return -1
    }

    private fun u16le(b: ByteArray, i: Int) = (b[i].toInt() and 0xFF) or ((b[i + 1].toInt() and 0xFF) shl 8)
    private fun u32le(b: ByteArray, i: Int): Long {
        var v = 0L
        for (k in 0 until 4) v = v or ((b[i + k].toLong() and 0xFF) shl (8 * k))
        return v
    }
    private fun s32le(b: ByteArray, i: Int): Int = u32le(b, i).toInt()
    private fun s16le(b: ByteArray, i: Int): Int {
        val v = u16le(b, i)
        return if (v >= 0x8000) v - 0x10000 else v
    }

    private fun parse(rec: ByteArray, base: BatteryData): BatteryData = base.copy(
        voltage = u32le(rec, OFF_VOLTAGE) / 1000.0f,
        currentA = s32le(rec, OFF_CURRENT) / 1000.0f,
        soc = rec[OFF_SOC].toInt() and 0xFF,
        tempC = s16le(rec, OFF_TEMP1) * 0.1f,
    )
}
