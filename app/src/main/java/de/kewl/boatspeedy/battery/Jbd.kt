package de.kewl.boatspeedy.battery

/**
 * JBD/Jiabaida-BMS-Protokoll (reverse-engineered für EcoWorthy LiFePO4 100 Ah,
 * Gerät „DP04S007L4S100A"). Siehe boatspeedy-battery-protocol.md.
 *
 * Frame: DD <reg> <status> <len> <len Bytes Daten> <cksum_hi> <cksum_lo> 77
 * Lesebefehl: DD A5 <reg> 00 <cksum_hi> <cksum_lo> 77, cksum = 0x10000 - reg.
 */
import kotlin.math.roundToInt

object Jbd {
    const val REG_BASIC = 0x03
    const val REG_CELLS = 0x04

    private fun readCommand(reg: Int): ByteArray {
        val chk = (0x10000 - reg) and 0xFFFF
        return byteArrayOf(
            0xDD.toByte(), 0xA5.toByte(), reg.toByte(), 0x00,
            ((chk shr 8) and 0xFF).toByte(), (chk and 0xFF).toByte(), 0x77.toByte(),
        )
    }

    val readBasicInfo: ByteArray get() = readCommand(REG_BASIC)
    val readCells: ByteArray get() = readCommand(REG_CELLS)

    /** Ein vollständiger Antwort-Frame. */
    data class Frame(val register: Int, val status: Int, val payload: ByteArray)

    /** Parst genau einen kompletten Frame (Start 0xDD … Ende 0x77). Null bei Fehler. */
    fun parseFrame(frame: ByteArray): Frame? {
        if (frame.size < 7) return null
        if (frame.first() != 0xDD.toByte() || frame.last() != 0x77.toByte()) return null
        val reg = frame[1].toInt() and 0xFF
        val status = frame[2].toInt() and 0xFF
        val len = frame[3].toInt() and 0xFF
        if (frame.size < 4 + len + 3) return null
        val payload = frame.copyOfRange(4, 4 + len)
        return Frame(reg, status, payload)
    }

    private fun u16(b: ByteArray, i: Int) = ((b[i].toInt() and 0xFF) shl 8) or (b[i + 1].toInt() and 0xFF)
    private fun s16(b: ByteArray, i: Int): Int {
        val v = u16(b, i)
        return if (v >= 0x8000) v - 0x10000 else v
    }

    /** Basisinfos (Reg 0x03) → BatteryData-Teile. Null bei zu kurzem Payload. */
    /**
     * Basis-Frame (Reg 0x03). Bewusst **tolerant**: manche BMS/Firmwares liefern einen
     * kürzeren Payload – dann werden nur die vorhandenen Felder gelesen, statt alles zu
     * verwerfen (sonst blieben Spannung/SoC/Rest/Temperatur leer, obwohl Zellen ankommen).
     * Layout: 0-1 U, 2-3 I, 4-5 Rest, 6-7 Nenn, 19 SoC, 20 FET, 21 Zellzahl, 22 NTC-Anzahl, ab 23 NTC-Werte.
     */
    fun parseBasic(payload: ByteArray): BasicInfo? {
        if (payload.size < 8) return null // Minimum: Spannung, Strom, Rest, Nennkapazität
        val voltage = u16(payload, 0) / 100.0f                 // 10 mV
        val current = s16(payload, 2) / 100.0f                 // 10 mA (signed)
        val remaining = u16(payload, 4) / 100.0f               // 10 mAh → Ah
        val nominal = u16(payload, 6) / 100.0f                 // 10 mAh → Ah
        // Ladezyklen ab Offset 8; kurze Frames mancher Firmware haben sie nicht.
        val cycles = if (payload.size >= 10) u16(payload, 8) else null
        // SoC: aus dem Byte, sonst aus Rest/Nenn hochgerechnet.
        val soc = if (payload.size > 19) payload[19].toInt() and 0xFF
        else if (nominal > 0f) ((remaining / nominal) * 100f).roundToInt().coerceIn(0, 100)
        else 0
        val fet = if (payload.size > 20) payload[20].toInt() and 0xFF else 0
        val cellCount = if (payload.size > 21) payload[21].toInt() and 0xFF else 0
        val ntc = if (payload.size > 22) payload[22].toInt() and 0xFF else 0
        val temp = if (ntc >= 1 && payload.size >= 25) u16(payload, 23) * 0.1f - 273.15f else null
        return BasicInfo(
            voltage = voltage,
            currentA = current,
            soc = soc,
            remainingAh = remaining,
            nominalAh = nominal,
            cellCount = cellCount,
            tempC = temp,
            chargingFet = fet and 0x01 != 0,
            dischargingFet = fet and 0x02 != 0,
            cycles = cycles,
        )
    }

    /** Zellspannungen (Reg 0x04) in Volt. */
    fun parseCells(payload: ByteArray): List<Float> {
        val out = ArrayList<Float>(payload.size / 2)
        var i = 0
        while (i + 1 < payload.size) {
            out.add(u16(payload, i) / 1000.0f)
            i += 2
        }
        return out
    }

    data class BasicInfo(
        val voltage: Float,
        val currentA: Float,
        val soc: Int,
        val remainingAh: Float,
        val nominalAh: Float,
        val cellCount: Int,
        val tempC: Float?,
        val chargingFet: Boolean,
        val dischargingFet: Boolean,
        val cycles: Int?,
    )
}
