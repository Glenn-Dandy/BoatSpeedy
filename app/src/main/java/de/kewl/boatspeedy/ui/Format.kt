package de.kewl.boatspeedy.ui

import de.kewl.boatspeedy.data.SpeedUnit
import java.util.Locale
import kotlin.math.roundToInt

/** Geschwindigkeit (m/s) in der gewählten Einheit, mit Nachkommastellen. */
fun formatSpeed(speedMs: Float, unit: SpeedUnit, decimals: Int): String {
    val v = speedMs * unit.factorFromMs
    return if (decimals <= 0) v.roundToInt().toString()
    else String.format(Locale.getDefault(), "%.${decimals}f", v)
}

/** Distanz in Metern → "123 m" oder "1.23 km". */
fun formatDistance(m: Double): String =
    if (m < 1000) "${m.roundToInt()} m"
    else String.format(Locale.getDefault(), "%.2f km", m / 1000.0)

/** Dauer in ms → "m:ss" oder "h:mm:ss". */
fun formatDuration(ms: Long): String {
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) String.format(Locale.getDefault(), "%d:%02d:%02d", h, m, s)
    else String.format(Locale.getDefault(), "%d:%02d", m, s)
}
