package de.kewl.boatspeedy.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import de.kewl.boatspeedy.battery.BmsType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** Liest/schreibt die App-Einstellungen über Jetpack DataStore. */
class SettingsRepository(private val context: Context) {

    private object Keys {
        val UNIT = stringPreferencesKey("unit")
        val DECIMALS = intPreferencesKey("decimals")
        val THEME = stringPreferencesKey("theme")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val SMOOTHING = stringPreferencesKey("smoothing")
        val RANGE_SMOOTHING = stringPreferencesKey("range_smoothing")
        val LOW_SOC_PERCENT = intPreferencesKey("low_soc_percent")
        val SHOW_SAT_DETAILS = booleanPreferencesKey("show_sat_details")
        val SHOW_BATTERY_TILE = booleanPreferencesKey("show_battery_tile")
        val SHOW_RANGE_TILE = booleanPreferencesKey("show_range_tile")
        val SHOW_MAP_TILE = booleanPreferencesKey("show_map_tile")
        val TRACK_COLOR = stringPreferencesKey("track_color")
        val TRACK_WIDTH = stringPreferencesKey("track_width")
        val TRACK_ARROWS = booleanPreferencesKey("track_arrows")
        val BAT_BMS = stringPreferencesKey("bat_bms")
        val BANK_MODE = stringPreferencesKey("bank_mode")
        val BATTERIES = stringPreferencesKey("batteries") // JSON-Array
        val DASH_BATTERY = stringPreferencesKey("dashboard_battery")
        val AUTO_PAUSE_ON = booleanPreferencesKey("auto_pause_on")
        val AUTO_PAUSE_AMPS = floatPreferencesKey("auto_pause_amps")
        val AUTO_PAUSE_SPEED = floatPreferencesKey("auto_pause_speed_ms")
        val NOTIF_FIELDS = stringPreferencesKey("notif_fields")
        val NOTIF_ENABLED = booleanPreferencesKey("notif_enabled")
        val NOTIF_ALWAYS = booleanPreferencesKey("notif_always")
        val ANCHOR_ALARM_ON = booleanPreferencesKey("anchor_alarm_on")
        val ANCHOR_SOUND = stringPreferencesKey("anchor_sound")
        val SOC_ALARM_ON = booleanPreferencesKey("soc_alarm_on")
        val SOC_SOUND = stringPreferencesKey("soc_sound")
        val CHARGE_TARGET_SOC = intPreferencesKey("charge_target_soc")
        val ANCHOR_RADIUS = intPreferencesKey("anchor_radius")
        val WEATHER_ENABLED = booleanPreferencesKey("weather_enabled")
        val WEATHER_ALARM_ON = booleanPreferencesKey("weather_alarm_on")
        val WEATHER_SOUND = stringPreferencesKey("weather_sound")
        val DEV_MODE = booleanPreferencesKey("dev_mode")
        val CRAFT = stringPreferencesKey("craft")
        val SEAMARKS = booleanPreferencesKey("seamarks")
    }

    val settings: Flow<Settings> = context.dataStore.data.map { p ->
        Settings(
            unit = p[Keys.UNIT]?.let { enumOrNull<SpeedUnit>(it) } ?: SpeedUnit.KMH,
            decimals = (p[Keys.DECIMALS] ?: 1).coerceIn(0, 2),
            theme = p[Keys.THEME]?.let { enumOrNull<ThemeMode>(it) } ?: ThemeMode.SYSTEM,
            keepScreenOn = p[Keys.KEEP_SCREEN_ON] ?: true,
            smoothing = p[Keys.SMOOTHING]?.let { enumOrNull<Smoothing>(it) } ?: Smoothing.LIGHT,
            rangeSmoothing = p[Keys.RANGE_SMOOTHING]?.let { enumOrNull<RangeSmoothing>(it) } ?: RangeSmoothing.S30,
            lowSocPercent = (p[Keys.LOW_SOC_PERCENT] ?: 20).coerceIn(0, 50),
            showSatDetails = p[Keys.SHOW_SAT_DETAILS] ?: true,
            showBatteryTile = p[Keys.SHOW_BATTERY_TILE] ?: true,
            showRangeTile = p[Keys.SHOW_RANGE_TILE] ?: true,
            showMapTile = p[Keys.SHOW_MAP_TILE] ?: true,
            trackColor = p[Keys.TRACK_COLOR]?.let { enumOrNull<TrackColor>(it) } ?: TrackColor.BLUE,
            trackWidth = p[Keys.TRACK_WIDTH]?.let { enumOrNull<TrackWidth>(it) } ?: TrackWidth.NORMAL,
            trackArrows = p[Keys.TRACK_ARROWS] ?: true,
            batteryBms = p[Keys.BAT_BMS]?.let { enumOrNull<BmsType>(it) } ?: BmsType.JBD,
            bankMode = p[Keys.BANK_MODE]?.let { enumOrNull<BankMode>(it) } ?: BankMode.SINGLE,
            batteries = p[Keys.BATTERIES]?.let { decodeBatteries(it) } ?: emptyList(),
            dashboardBattery = p[Keys.DASH_BATTERY] ?: COMBINED_SELECTION,
            autoPauseOn = p[Keys.AUTO_PAUSE_ON] ?: true,
            autoPauseAmps = (p[Keys.AUTO_PAUSE_AMPS] ?: 0.05f).coerceIn(0f, 50f),
            autoPauseSpeedMs = (p[Keys.AUTO_PAUSE_SPEED] ?: 0.14f).coerceIn(0f, 10f),
            notifEnabled = p[Keys.NOTIF_ENABLED] ?: true,
            notifAlways = p[Keys.NOTIF_ALWAYS] ?: false,
            notifFields = p[Keys.NOTIF_FIELDS]?.let { decodeNotifFields(it) }
                ?: setOf(NotifField.SPEED, NotifField.DISTANCE, NotifField.CHARGE_AH, NotifField.SOC),
            anchorAlarmOn = p[Keys.ANCHOR_ALARM_ON] ?: true,
            anchorSound = p[Keys.ANCHOR_SOUND]?.let { enumOrNull<AlarmSound>(it) } ?: AlarmSound.SIRENE,
            socAlarmOn = p[Keys.SOC_ALARM_ON] ?: false,
            socSound = p[Keys.SOC_SOUND]?.let { enumOrNull<AlarmSound>(it) } ?: AlarmSound.PIEP,
            chargeTargetSoc = (p[Keys.CHARGE_TARGET_SOC] ?: 0).coerceIn(0, 100),
            anchorRadiusM = (p[Keys.ANCHOR_RADIUS] ?: 30).coerceIn(5, 1000),
            weatherWarnEnabled = p[Keys.WEATHER_ENABLED] ?: false,
            weatherAlarmOn = p[Keys.WEATHER_ALARM_ON] ?: true,
            weatherSound = p[Keys.WEATHER_SOUND]?.let { enumOrNull<AlarmSound>(it) } ?: AlarmSound.SIRENE,
            devMode = p[Keys.DEV_MODE] ?: false,
            craft = p[Keys.CRAFT]?.let { enumOrNull<Craft>(it) } ?: Craft.MOTORBOAT,
            seamarks = p[Keys.SEAMARKS] ?: true,
        )
    }

    suspend fun setUnit(value: SpeedUnit) = edit { it[Keys.UNIT] = value.name }
    suspend fun setDecimals(value: Int) = edit { it[Keys.DECIMALS] = value.coerceIn(0, 2) }
    suspend fun setTheme(value: ThemeMode) = edit { it[Keys.THEME] = value.name }
    suspend fun setKeepScreenOn(value: Boolean) = edit { it[Keys.KEEP_SCREEN_ON] = value }
    suspend fun setSmoothing(value: Smoothing) = edit { it[Keys.SMOOTHING] = value.name }
    suspend fun setRangeSmoothing(value: RangeSmoothing) = edit { it[Keys.RANGE_SMOOTHING] = value.name }
    suspend fun setLowSocPercent(value: Int) = edit { it[Keys.LOW_SOC_PERCENT] = value.coerceIn(0, 50) }
    suspend fun setShowSatDetails(value: Boolean) = edit { it[Keys.SHOW_SAT_DETAILS] = value }
    suspend fun setShowBatteryTile(value: Boolean) = edit { it[Keys.SHOW_BATTERY_TILE] = value }
    suspend fun setShowRangeTile(value: Boolean) = edit { it[Keys.SHOW_RANGE_TILE] = value }
    suspend fun setShowMapTile(value: Boolean) = edit { it[Keys.SHOW_MAP_TILE] = value }
    suspend fun setTrackColor(value: TrackColor) = edit { it[Keys.TRACK_COLOR] = value.name }
    suspend fun setTrackWidth(value: TrackWidth) = edit { it[Keys.TRACK_WIDTH] = value.name }
    suspend fun setTrackArrows(value: Boolean) = edit { it[Keys.TRACK_ARROWS] = value }
    suspend fun setBatteryBms(value: BmsType) = edit { it[Keys.BAT_BMS] = value.name }
    suspend fun setBankMode(value: BankMode) = edit { it[Keys.BANK_MODE] = value.name }
    suspend fun setDashboardBattery(value: String) = edit { it[Keys.DASH_BATTERY] = value }
    suspend fun setBatteries(value: List<SavedBattery>) = edit { it[Keys.BATTERIES] = encodeBatteries(value) }
    suspend fun setAutoPauseOn(value: Boolean) = edit { it[Keys.AUTO_PAUSE_ON] = value }
    suspend fun setAutoPauseAmps(value: Float) = edit { it[Keys.AUTO_PAUSE_AMPS] = value.coerceIn(0f, 50f) }
    suspend fun setAutoPauseSpeedMs(value: Float) = edit { it[Keys.AUTO_PAUSE_SPEED] = value.coerceIn(0f, 10f) }
    suspend fun setNotifEnabled(value: Boolean) = edit { it[Keys.NOTIF_ENABLED] = value }
    suspend fun setNotifAlways(value: Boolean) = edit { it[Keys.NOTIF_ALWAYS] = value }
    suspend fun setNotifFields(value: Set<NotifField>) = edit {
        it[Keys.NOTIF_FIELDS] = value.joinToString(",") { f -> f.name }
    }
    suspend fun setAnchorAlarmOn(value: Boolean) = edit { it[Keys.ANCHOR_ALARM_ON] = value }
    suspend fun setAnchorSound(value: AlarmSound) = edit { it[Keys.ANCHOR_SOUND] = value.name }
    suspend fun setSocAlarmOn(value: Boolean) = edit { it[Keys.SOC_ALARM_ON] = value }
    suspend fun setSocSound(value: AlarmSound) = edit { it[Keys.SOC_SOUND] = value.name }
    suspend fun setChargeTargetSoc(value: Int) = edit { it[Keys.CHARGE_TARGET_SOC] = value.coerceIn(0, 100) }
    suspend fun setAnchorRadius(value: Int) = edit { it[Keys.ANCHOR_RADIUS] = value.coerceIn(5, 1000) }
    suspend fun setWeatherEnabled(value: Boolean) = edit { it[Keys.WEATHER_ENABLED] = value }
    suspend fun setWeatherAlarmOn(value: Boolean) = edit { it[Keys.WEATHER_ALARM_ON] = value }
    suspend fun setDevMode(value: Boolean) = edit { it[Keys.DEV_MODE] = value }
    suspend fun setCraft(value: Craft) = edit { it[Keys.CRAFT] = value.name }
    suspend fun setSeamarks(value: Boolean) = edit { it[Keys.SEAMARKS] = value }
    suspend fun setWeatherSound(value: AlarmSound) = edit { it[Keys.WEATHER_SOUND] = value.name }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit(block)
    }

    private fun decodeNotifFields(raw: String): Set<NotifField> =
        raw.split(',').mapNotNull { enumOrNull<NotifField>(it.trim()) }.toSet()

    private fun encodeBatteries(list: List<SavedBattery>): String {
        val arr = JSONArray()
        for (b in list) {
            arr.put(
                JSONObject()
                    .put("address", b.address)
                    .put("name", b.name)
                    .put("active", b.active)
                    .put("bleName", b.bleName ?: "")
                    .put("bms", b.bms.name),
            )
        }
        return arr.toString()
    }

    private fun decodeBatteries(json: String): List<SavedBattery> = runCatching {
        val arr = JSONArray(json)
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            SavedBattery(
                address = o.getString("address"),
                name = o.optString("name", o.getString("address")),
                active = o.optBoolean("active", true),
                bleName = o.optString("bleName").takeIf { it.isNotBlank() },
                bms = enumOrNull<BmsType>(o.optString("bms")) ?: BmsType.JBD,
            )
        }
    }.getOrDefault(emptyList())
}

private inline fun <reified T : Enum<T>> enumOrNull(name: String): T? =
    runCatching { enumValueOf<T>(name) }.getOrNull()
