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

/**
 * Wie mehrere aktive Batterien elektrisch zusammengerechnet werden.
 *  - [SINGLE]   physisch getrennte Akkus, nacheinander genutzt → Kapazität summiert sich
 *  - [PARALLEL] parallel verschaltet → mehr Ah (Kapazität summiert, Spannung gleich)
 *  - [SERIES]   in Reihe verschaltet → mehr Volt (Spannung summiert, Ah bleibt)
 */
enum class BankMode { SINGLE, PARALLEL, SERIES }

/** Eine dauerhaft gespeicherte Batterie (Adresse ist der stabile Schlüssel). */
data class SavedBattery(
    val address: String,
    val name: String,
    val active: Boolean = true,
)

/** Kennung für „kombinierte" Auswahl auf dem Dashboard (statt einer einzelnen Adresse). */
const val COMBINED_SELECTION = ""

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
    val bankMode: BankMode = BankMode.SINGLE,
    val batteries: List<SavedBattery> = emptyList(),
    /** Ausgewählte Anzeige auf dem Dashboard: Adresse einer Batterie oder [COMBINED_SELECTION]. */
    val dashboardBattery: String = COMBINED_SELECTION,
)
