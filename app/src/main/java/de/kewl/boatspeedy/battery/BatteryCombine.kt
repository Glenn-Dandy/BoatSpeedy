package de.kewl.boatspeedy.battery

import de.kewl.boatspeedy.data.BankMode
import de.kewl.boatspeedy.data.Settings
import kotlin.math.roundToInt

/**
 * Live-Werte der aktiven, aktuell verbundenen Batterien.
 * Nicht-aktive oder nicht-verbundene Akkus werden ignoriert.
 */
fun activeBatteryData(settings: Settings, hub: BatteryHub): List<BatteryData> =
    settings.batteries
        .filter { it.active }
        .mapNotNull { hub.links[it.address]?.data }

/**
 * Die auf dem Dashboard anzuzeigenden Werte gemäß Auswahl:
 *  - eine bestimmte Batterie (dashboardBattery == Adresse) → deren Werte
 *  - sonst „kombiniert" → alle aktiven Akkus nach [Settings.bankMode] zusammengerechnet
 * Null, wenn keine passenden Live-Werte vorliegen.
 */
fun selectedBatteryData(settings: Settings, hub: BatteryHub): BatteryData? {
    val active = settings.batteries.filter { it.active }
    val chosen = active.firstOrNull { it.address == settings.dashboardBattery }
    if (chosen != null) return hub.links[chosen.address]?.data
    val datas = active.mapNotNull { hub.links[it.address]?.data }
    return if (datas.isEmpty()) null else combineBatteries(datas, settings.bankMode)
}

/**
 * Rechnet mehrere Batterien zu einem Ersatz-Akku zusammen.
 *  - PARALLEL / SINGLE: Kapazität & Strom addieren, Spannung mitteln (mehr Ah)
 *  - SERIES:            Spannung addieren, Kapazität = schwächste, Strom mitteln (mehr V)
 */
fun combineBatteries(datas: List<BatteryData>, mode: BankMode): BatteryData {
    if (datas.isEmpty()) return BatteryData()
    if (datas.size == 1) return datas.first()

    val temps = datas.mapNotNull { it.tempC }
    val avgTemp = if (temps.isEmpty()) null else temps.average().toFloat()
    val cellCount = datas.maxOf { it.cellCount }

    return when (mode) {
        BankMode.SERIES -> {
            val volts = datas.filter { it.voltage > 0f }.map { it.voltage.toDouble() }
            val remainings = datas.filter { it.remainingAh > 0f }.map { it.remainingAh }
            val nominals = datas.filter { it.nominalAh > 0f }.map { it.nominalAh }
            BatteryData(
                voltage = volts.sum().toFloat(),
                currentA = datas.map { it.currentA.toDouble() }.average().toFloat(),
                soc = datas.minOf { it.soc },
                remainingAh = remainings.minOrNull() ?: 0f,
                nominalAh = nominals.minOrNull() ?: 0f,
                cellCount = cellCount,
                tempC = avgTemp,
            )
        }
        BankMode.PARALLEL, BankMode.SINGLE -> {
            val volts = datas.filter { it.voltage > 0f }.map { it.voltage.toDouble() }
            val remaining = datas.sumOf { it.remainingAh.toDouble() }.toFloat()
            val nominal = datas.sumOf { it.nominalAh.toDouble() }.toFloat()
            val soc = if (nominal > 0f) (remaining / nominal * 100f).roundToInt()
                      else datas.map { it.soc }.average().roundToInt()
            BatteryData(
                voltage = if (volts.isEmpty()) 0f else volts.average().toFloat(),
                currentA = datas.sumOf { it.currentA.toDouble() }.toFloat(),
                soc = soc,
                remainingAh = remaining,
                nominalAh = nominal,
                cellCount = cellCount,
                tempC = avgTemp,
            )
        }
    }
}
