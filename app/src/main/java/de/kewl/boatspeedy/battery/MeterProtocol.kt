package de.kewl.boatspeedy.battery

import java.util.UUID

/**
 * Coulomb-Zähler mit Shunt (meldet sich als `WBMS`, Funkmodul CH9141).
 *
 * **Kein BMS.** Das Gerät misst nur, was durch den Shunt fließt: Spannung, Strom und die
 * aufsummierte Energie. Ladestand, Zellspannungen, Temperatur und Kapazität kennt es nicht.
 *
 * Es **sendet von selbst** alle rund 2,7 Sekunden — abgefragt werden muss nichts. Deshalb
 * ist [pollCommands] leer; die Befehle hier sind Einstellungen, die der Nutzer auslöst.
 *
 * Protokoll aus einem App-Inventor-Projekt gelesen (E-Odin) und gegen einen Mitschnitt
 * geprüft. Anders als bei den BMS ist alles **Big-Endian**.
 */
class MeterProtocol : BmsProtocol() {
    override val type = BmsType.METER
    override val serviceUuid: UUID = uuid16("ffe0")
    override val notifyUuid: UUID = uuid16("ffe1")
    override val writeUuid: UUID = uuid16("ffe2")

    private companion object {
        const val FRAME_LEN = 21
        val HEADER = byteArrayOf(0xB5.toByte(), 0x5B)
    }

    private val buf = ArrayList<Byte>(64)

    /** Nichts anfragen – das Gerät meldet sich von allein. */
    override fun pollCommands(cycle: Int): List<ByteArray> = emptyList()

    override fun onChunk(chunk: ByteArray, current: BatteryData): BatteryData? {
        for (b in chunk) buf.add(b)
        // Auf den Kopf ausrichten; alles davor ist Müll aus einem angebrochenen Frame.
        while (buf.size >= 2 && !(buf[0] == HEADER[0] && buf[1] == HEADER[1])) {
            buf.removeAt(0)
            if (buf.size > 4 * FRAME_LEN) { buf.clear(); return null }
        }
        if (buf.size < FRAME_LEN) return null
        val f = ByteArray(FRAME_LEN) { buf[it] }
        repeat(FRAME_LEN) { buf.removeAt(0) }
        return parse(f, current)
    }

    private fun parse(f: ByteArray, current: BatteryData): BatteryData = current.copy(
        voltage = be(f, 7) / 10f,
        currentA = be(f, 9) / 10f,
        energyKWh = be(f, 15) / 1000f,
    )

    /** Big-Endian, zwei Byte ab [i] (0-basiert). */
    private fun be(b: ByteArray, i: Int): Int =
        ((b[i].toInt() and 0xFF) shl 8) or (b[i + 1].toInt() and 0xFF)

    /** Einstellbefehle des Geräts – vom Nutzer ausgelöst, nicht im Poll-Takt. */
    object Commands {
        /** Aufsummierte Energie auf null setzen. */
        val CLEAR_ENERGY = bytes(0xA5, 0x5A, 0x00, 0x0F, 0x00, 0x00, 0x00, 0x00, 0xF1, 0x00, 0x00, 0x00, 0x00)

        /** Ladestand des Zählers auf 100 % setzen. */
        val SET_FULL = bytes(0xA5, 0x5A, 0x00, 0x0B, 0x64, 0x00, 0x00, 0x00, 0x91, 0x01, 0x48, 0x00, 0x00, 0x00)

        /** Stromanzeige auf null abgleichen (Nullpunkt des Shunts). */
        val ZERO_CURRENT = bytes(0xA5, 0x5A, 0x00, 0x03, 0x00, 0x00, 0x00, 0x00, 0xFD, 0x00)

        private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }
    }
}
