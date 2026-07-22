package de.kewl.boatspeedy.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

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
    }

    val settings: Flow<Settings> = context.dataStore.data.map { p ->
        Settings(
            unit = p[Keys.UNIT]?.let { enumOrNull<SpeedUnit>(it) } ?: SpeedUnit.KMH,
            decimals = (p[Keys.DECIMALS] ?: 1).coerceIn(0, 2),
            theme = p[Keys.THEME]?.let { enumOrNull<ThemeMode>(it) } ?: ThemeMode.SYSTEM,
            keepScreenOn = p[Keys.KEEP_SCREEN_ON] ?: true,
            smoothing = p[Keys.SMOOTHING]?.let { enumOrNull<Smoothing>(it) } ?: Smoothing.LIGHT,
            showSatDetails = p[Keys.SHOW_SAT_DETAILS] ?: true,
        )
    }

    suspend fun setUnit(value: SpeedUnit) = edit { it[Keys.UNIT] = value.name }
    suspend fun setDecimals(value: Int) = edit { it[Keys.DECIMALS] = value.coerceIn(0, 2) }
    suspend fun setTheme(value: ThemeMode) = edit { it[Keys.THEME] = value.name }
    suspend fun setKeepScreenOn(value: Boolean) = edit { it[Keys.KEEP_SCREEN_ON] = value }
    suspend fun setSmoothing(value: Smoothing) = edit { it[Keys.SMOOTHING] = value.name }
    suspend fun setShowSatDetails(value: Boolean) = edit { it[Keys.SHOW_SAT_DETAILS] = value }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit(block)
    }
}

private inline fun <reified T : Enum<T>> enumOrNull(name: String): T? =
    runCatching { enumValueOf<T>(name) }.getOrNull()
