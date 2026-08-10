package de.kewl.boatspeedy.util

import android.content.Context
import de.kewl.boatspeedy.R
import de.kewl.boatspeedy.battery.BatteryHub
import de.kewl.boatspeedy.battery.activeBatteryData
import de.kewl.boatspeedy.battery.combineBatteries
import de.kewl.boatspeedy.battery.estimateRange
import de.kewl.boatspeedy.data.NotifField
import de.kewl.boatspeedy.data.Settings
import de.kewl.boatspeedy.location.GpsState
import de.kewl.boatspeedy.trip.TripStats
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Baut die (bis zu) zwei Zeilen der Fahrt-/Status-Benachrichtigung aus den in den
 * Einstellungen gewählten Werten. Zeile 1 ist eingeklappt sichtbar, Zeile 2 nur aufgeklappt.
 * Wird von [de.kewl.boatspeedy.trip.LocationService] und der Dauer-Statusmeldung genutzt.
 */
fun notificationLines(
    context: Context,
    gps: GpsState,
    s: Settings,
    hub: BatteryHub,
    stats: TripStats,
): Pair<String, String> {
    val bank = activeBatteryData(s, hub).takeIf { it.isNotEmpty() }
        ?.let { combineBatteries(it, s.bankMode) }
    val range = estimateRange(bank, gps.speedMs)

    fun value(f: NotifField): String? = when (f) {
        NotifField.SPEED -> gps.speedMs?.let {
            String.format(Locale.getDefault(), "%.1f %s", it * s.unit.factorFromMs, s.unit.label)
        } ?: "--"
        NotifField.DISTANCE -> formatDistance(stats.distanceM)
        NotifField.TIME -> formatDuration(stats.elapsedMs)
        NotifField.CHARGE_AH -> String.format(Locale.getDefault(), "%.1f Ah", stats.chargeAh)
        NotifField.ENERGY_WH -> String.format(Locale.getDefault(), "%.0f Wh", stats.energyWh)
        NotifField.SOC -> bank?.takeIf { it.voltage > 0f }?.let { "${context.getString(R.string.soc_short)} ${it.soc} %" }
        NotifField.RANGE -> range?.let { formatDistance(it.km * 1000.0) }
        NotifField.TIME_LEFT -> range?.let { formatDuration((it.hours * 3600_000).toLong()) }
    }

    fun line(n: Int) = NotifField.entries
        .filter { it.line == n && it in s.notifFields }
        .mapNotNull { value(it) }
        .joinToString(" · ")

    return line(1) to line(2)
}

private fun formatDistance(m: Double): String =
    if (m < 1000) "${m.roundToInt()} m"
    else String.format(Locale.getDefault(), "%.2f km", m / 1000.0)

private fun formatDuration(ms: Long): String {
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val sec = total % 60
    return if (h > 0) String.format(Locale.getDefault(), "%d:%02d:%02d", h, m, sec)
    else String.format(Locale.getDefault(), "%d:%02d", m, sec)
}
