package de.kewl.boatspeedy.battery

import java.util.UUID

/** JBD/Jiabaida-BMS (getestet). Service FF00, notify FF01, write FF02. */
class JbdProtocol : BmsProtocol() {
    override val type = BmsType.JBD
    override val serviceUuid: UUID = uuid16("ff00")
    override val notifyUuid: UUID = uuid16("ff01")
    override val writeUuid: UUID = uuid16("ff02")

    private val assembler = FrameAssembler()

    override fun pollCommands(cycle: Int): List<ByteArray> =
        if (cycle % 3 == 0) listOf(Jbd.readBasicInfo, Jbd.readCells)
        else listOf(Jbd.readBasicInfo)

    override fun onChunk(chunk: ByteArray, current: BatteryData): BatteryData? {
        var out: BatteryData? = null
        for (frame in assembler.add(chunk)) {
            val parsed = Jbd.parseFrame(frame) ?: continue
            if (parsed.status != 0) continue
            val base = out ?: current
            out = when (parsed.register) {
                Jbd.REG_BASIC -> Jbd.parseBasic(parsed.payload)?.let { b ->
                    base.copy(
                        voltage = b.voltage,
                        currentA = b.currentA,
                        soc = b.soc,
                        remainingAh = b.remainingAh,
                        nominalAh = b.nominalAh,
                        cellCount = b.cellCount,
                        tempC = b.tempC,
                        chargingFet = b.chargingFet,
                        dischargingFet = b.dischargingFet,
                    )
                } ?: base
                Jbd.REG_CELLS -> base.copy(cells = Jbd.parseCells(parsed.payload))
                else -> base
            }
        }
        return out
    }
}
