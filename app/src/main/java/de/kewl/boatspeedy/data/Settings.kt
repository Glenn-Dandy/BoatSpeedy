package de.kewl.boatspeedy.data

import de.kewl.boatspeedy.battery.BmsType

/** Anzeige-Einheit der Geschwindigkeit. */
/* Hinweis: Die App-Sprache läuft über die System-Pro-App-Sprache (LocaleManager),
   nicht über diese Settings. */
enum class SpeedUnit(val factorFromMs: Double, val label: String) {
    KMH(3.6, "km/h"),
    KNOTS(1.943844, "kn"),
}

/** Design-Modus. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Stärke der Glättung (gleitender Mittelwert über N Messungen). */
enum class Smoothing(val window: Int) {
    OFF(1),
    LIGHT(3),
    STRONG(6),
}

/** Alle persistierten Einstellungen als unveränderliches Bündel. */
data class Settings(
    val unit: SpeedUnit = SpeedUnit.KMH,
    val decimals: Int = 1,          // 0, 1 oder 2 → xx / xx.x / xx.xx
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val keepScreenOn: Boolean = true,
    val smoothing: Smoothing = Smoothing.LIGHT,
    val showSatDetails: Boolean = true,
    // Dashboard-Kacheln
    val showBatteryTile: Boolean = true,
    val showRangeTile: Boolean = true,
    // Batterie
    val batteryBms: BmsType = BmsType.JBD,
)
