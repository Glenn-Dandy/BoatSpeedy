package de.kewl.boatspeedy.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
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
        val SHOW_SAT_DETAILS = booleanPreferencesKey("show_sat_details")
        val SHOW_BATTERY_TILE = booleanPreferencesKey("show_battery_tile")
        val SHOW_RANGE_TILE = booleanPreferencesKey("show_range_tile")
        val BAT_BMS = stringPreferencesKey("bat_bms")
        val BANK_MODE = stringPreferencesKey("bank_mode")
        val BATTERIES = stringPreferencesKey("batteries") // JSON-Array
        val DASH_BATTERY = stringPreferencesKey("dashboard_battery")
    }

    val settings: Flow<Settings> = context.dataStore.data.map { p ->
        Settings(
            unit = p[Keys.UNIT]?.let { enumOrNull<SpeedUnit>(it) } ?: SpeedUnit.KMH,
            decimals = (p[Keys.DECIMALS] ?: 1).coerceIn(0, 2),
            theme = p[Keys.THEME]?.let { enumOrNull<ThemeMode>(it) } ?: ThemeMode.SYSTEM,
            keepScreenOn = p[Keys.KEEP_SCREEN_ON] ?: true,
            smoothing = p[Keys.SMOOTHING]?.let { enumOrNull<Smoothing>(it) } ?: Smoothing.LIGHT,
            showSatDetails = p[Keys.SHOW_SAT_DETAILS] ?: true,
            showBatteryTile = p[Keys.SHOW_BATTERY_TILE] ?: true,
            showRangeTile = p[Keys.SHOW_RANGE_TILE] ?: true,
            batteryBms = p[Keys.BAT_BMS]?.let { enumOrNull<BmsType>(it) } ?: BmsType.JBD,
            bankMode = p[Keys.BANK_MODE]?.let { enumOrNull<BankMode>(it) } ?: BankMode.SINGLE,
            batteries = p[Keys.BATTERIES]?.let { decodeBatteries(it) } ?: emptyList(),
            dashboardBattery = p[Keys.DASH_BATTERY] ?: COMBINED_SELECTION,
        )
    }

    suspend fun setUnit(value: SpeedUnit) = edit { it[Keys.UNIT] = value.name }
    suspend fun setDecimals(value: Int) = edit { it[Keys.DECIMALS] = value.coerceIn(0, 2) }
    suspend fun setTheme(value: ThemeMode) = edit { it[Keys.THEME] = value.name }
    suspend fun setKeepScreenOn(value: Boolean) = edit { it[Keys.KEEP_SCREEN_ON] = value }
    suspend fun setSmoothing(value: Smoothing) = edit { it[Keys.SMOOTHING] = value.name }
    suspend fun setShowSatDetails(value: Boolean) = edit { it[Keys.SHOW_SAT_DETAILS] = value }
    suspend fun setShowBatteryTile(value: Boolean) = edit { it[Keys.SHOW_BATTERY_TILE] = value }
    suspend fun setShowRangeTile(value: Boolean) = edit { it[Keys.SHOW_RANGE_TILE] = value }
    suspend fun setBatteryBms(value: BmsType) = edit { it[Keys.BAT_BMS] = value.name }
    suspend fun setBankMode(value: BankMode) = edit { it[Keys.BANK_MODE] = value.name }
    suspend fun setDashboardBattery(value: String) = edit { it[Keys.DASH_BATTERY] = value }
    suspend fun setBatteries(value: List<SavedBattery>) = edit { it[Keys.BATTERIES] = encodeBatteries(value) }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit(block)
    }

    private fun encodeBatteries(list: List<SavedBattery>): String {
        val arr = JSONArray()
        for (b in list) {
            arr.put(
                JSONObject()
                    .put("address", b.address)
                    .put("name", b.name)
                    .put("active", b.active),
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
            )
        }
    }.getOrDefault(emptyList())
}

private inline fun <reified T : Enum<T>> enumOrNull(name: String): T? =
    runCatching { enumValueOf<T>(name) }.getOrNull()
