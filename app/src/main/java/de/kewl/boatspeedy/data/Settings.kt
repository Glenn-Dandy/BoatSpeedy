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
 * Zeitfenster für die Glättung der Reichweiten-/Restzeit-Anzeige (Mittelwert des
 * Entladestroms und der Geschwindigkeit), damit die Werte nicht mitzappeln.
 */
enum class RangeSmoothing(val windowMs: Long) {
    OFF(0L),
    S15(15_000L),
    S30(30_000L),
    S60(60_000L),
}


/** Mitgelieferte Alarmtöne (res/raw). */
enum class AlarmSound { PIEP, GLOCKE, SIRENE }

/**
 * Werte, die in der Fahrt-Benachrichtigung stehen können. [line] = 1 (immer sichtbar,
 * eingeklappt) oder 2 (nur aufgeklappt).
 */
enum class NotifField(val line: Int) {
    SPEED(1), DISTANCE(1), TIME(1),
    CHARGE_AH(2), ENERGY_WH(2), SOC(2), RANGE(2), TIME_LEFT(2),
}

/** Farbe der Track-Linie auf der Karte (ARGB). */
/** Womit gefahren wird – bestimmt, welche Wasserwege für die Route in Frage kommen. */
enum class Craft { MOTORBOAT, CANOE }

enum class TrackColor(val argb: Int) {
    BLUE(0xFF1E88E5.toInt()),
    RED(0xFFE53935.toInt()),
    BLACK(0xFF222222.toInt()),
}

/** Strichstärke der Track-Linie (px); NORMAL = bisheriger Wert. */
enum class TrackWidth(val px: Float) {
    THIN(6.5f),
    NORMAL(9f),
    THICK(12f),
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
    /** Anzeigename – frei umbenennbar. */
    val name: String,
    val active: Boolean = true,
    /** Ursprünglicher BLE-Name (Typ, z. B. „DP04S007L4S100A"); bleibt beim Umbenennen erhalten. */
    val bleName: String? = null,
    /** BMS-Protokoll **dieser** Batterie – erlaubt gemischte Bänke (z. B. JBD + Daly). */
    val bms: BmsType = BmsType.JBD,
)

/**
 * Kurzer Vorschlagsname beim Hinzufügen: 3 Zeichen des Typs + letzte 4 Zeichen der MAC,
 * z. B. „DP0-5F3A".
 */
fun shortBatteryName(bleName: String?, address: String): String {
    val prefix = bleName?.filter { it.isLetterOrDigit() }?.take(3)?.uppercase()?.takeIf { it.isNotBlank() } ?: "BAT"
    val suffix = address.filter { it.isLetterOrDigit() }.takeLast(4).uppercase()
    return if (suffix.isBlank()) prefix else "$prefix-$suffix"
}

/** Kennung für „kombinierte" Auswahl auf dem Dashboard (statt einer einzelnen Adresse). */
const val COMBINED_SELECTION = ""

/** Alle persistierten Einstellungen als unveränderliches Bündel. */
data class Settings(
    val unit: SpeedUnit = SpeedUnit.KMH,
    val decimals: Int = 1,          // 0, 1 oder 2 → xx / xx.x / xx.xx
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val keepScreenOn: Boolean = true,
    val smoothing: Smoothing = Smoothing.LIGHT,
    val rangeSmoothing: RangeSmoothing = RangeSmoothing.S30,
    /** Schwelle (%), ab der der Ladezustand auf dem Dashboard rot wird; 0 = aus. */
    val lowSocPercent: Int = 20,
    val showSatDetails: Boolean = true,
    // Dashboard-Kacheln
    val showBatteryTile: Boolean = true,
    val showRangeTile: Boolean = true,
    val showMapTile: Boolean = true,
    // Tracks / Karte
    val trackColor: TrackColor = TrackColor.BLUE,
    val trackWidth: TrackWidth = TrackWidth.NORMAL,
    val trackArrows: Boolean = true,
    // Batterie
    val batteryBms: BmsType = BmsType.JBD,
    val bankMode: BankMode = BankMode.SINGLE,
    val batteries: List<SavedBattery> = emptyList(),
    /** Ausgewählte Anzeige auf dem Dashboard: Adresse einer Batterie oder [COMBINED_SELECTION]. */
    val dashboardBattery: String = COMBINED_SELECTION,
    // Fahrt / Alarme
    /** Auto-Pause aktiv? (Schwelle darunter). */
    val autoPauseOn: Boolean = true,
    /** Auto-Pause-Schwelle in A; 0 = aus (Fahrt läuft ohne Auto-Pause). */
    val autoPauseAmps: Float = 0.05f,
    /**
     * Bewegungs-Schwelle der Auto-Pause in m/s: darüber gilt das Boot als „in Fahrt"
     * (Treiben wird aufgezeichnet). 0 = Geschwindigkeit ignorieren, nur Strom zählt.
     */
    val autoPauseSpeedMs: Float = 0.14f,
    /** Fahrt-/Status-Benachrichtigung überhaupt anzeigen. */
    val notifEnabled: Boolean = true,
    /** Auch ohne laufende Fahrt anzeigen (solange die App im Hintergrund lebt). */
    val notifAlways: Boolean = false,
    /** Werte in der Fahrt-Benachrichtigung. */
    val notifFields: Set<NotifField> = setOf(
        NotifField.SPEED, NotifField.DISTANCE, NotifField.CHARGE_AH, NotifField.SOC,
    ),
    /** Ankeralarm mit Ton (sonst nur Benachrichtigung). */
    val anchorAlarmOn: Boolean = true,
    val anchorSound: AlarmSound = AlarmSound.SIRENE,
    /** SoC-Warnung zusätzlich mit Ton (sonst nur rot). */
    val socAlarmOn: Boolean = false,
    val socSound: AlarmSound = AlarmSound.PIEP,
    /** Beim Laden Meldung, wenn dieser Ladestand (%) erreicht ist; 0 = aus. */
    val chargeTargetSoc: Int = 0,
    /** Zuletzt genutzter Anker-Radius in Metern. */
    val anchorRadiusM: Int = 30,
    // Wetterwarnungen (DWD)
    /** DWD-Wetterwarnungen (Gewitter/Sturm) prüfen und melden. */
    val weatherWarnEnabled: Boolean = false,
    /** Wetterwarnung zusätzlich mit Ton. */
    val weatherAlarmOn: Boolean = true,
    val weatherSound: AlarmSound = AlarmSound.SIRENE,
    /** Versteckte Entwicklerwerkzeuge (BLE-Diagnose); über 7 Tipper in „Über" freigeschaltet. */
    val devMode: Boolean = false,
    // Navigation
    /** Fahrzeugart für die Routenberechnung. */
    val craft: Craft = Craft.MOTORBOAT,
    /** Seezeichen (OpenSeaMap) über der Karte einblenden. */
    val seamarks: Boolean = true,
)
