package de.kewl.boatspeedy.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.kewl.boatspeedy.data.Settings
import de.kewl.boatspeedy.data.SettingsRepository
import de.kewl.boatspeedy.data.SpeedUnit
import de.kewl.boatspeedy.data.Smoothing
import de.kewl.boatspeedy.data.ThemeMode
import de.kewl.boatspeedy.battery.BatteryRepository
import de.kewl.boatspeedy.battery.BatteryState
import de.kewl.boatspeedy.location.GpsState
import de.kewl.boatspeedy.location.LocationProvider
import de.kewl.boatspeedy.trip.LocationService
import de.kewl.boatspeedy.trip.TripRepository
import de.kewl.boatspeedy.trip.TripStats
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.ArrayDeque
import java.util.Locale
import kotlin.math.roundToInt

class SpeedViewModel(app: Application) : AndroidViewModel(app) {

    private val settingsRepo = SettingsRepository(app)
    private val locationProvider = LocationProvider(app)

    val settings: StateFlow<Settings> =
        settingsRepo.settings.stateIn(viewModelScope, SharingStarted.Eagerly, Settings())

    private val _gps = MutableStateFlow(GpsState())
    val gps: StateFlow<GpsState> = _gps.asStateFlow()

    // Fahrt-Zustand aus dem prozessweiten TripRepository (vom Dienst gespeist).
    val tracking: StateFlow<Boolean> = TripRepository.tracking
    val tripStats: StateFlow<TripStats> = TripRepository.stats

    // Batterie-Zustand (BLE).
    val battery: StateFlow<BatteryState> = BatteryRepository.state

    // Gleitender Mittelwert der rohen Geschwindigkeit (m/s).
    private val speedWindow = ArrayDeque<Float>()

    /** Fertig formatierter Anzeigewert (bereits geglättet & umgerechnet). */
    val displaySpeed: StateFlow<String> =
        combine(_gps, settings) { gps, settings -> smoothAndFormat(gps, settings) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, NO_FIX)

    private var collectJob: Job? = null

    /** GPS-Updates starten (Aufruf in onResume, nachdem die Berechtigung da ist). */
    fun startUpdates() {
        if (collectJob?.isActive == true) return
        speedWindow.clear()
        collectJob = viewModelScope.launch {
            locationProvider.state.collect { _gps.value = it }
        }
    }

    /** GPS-Updates für den Vordergrund-Tacho stoppen (onPause). */
    fun stopUpdates() {
        collectJob?.cancel()
        collectJob = null
    }

    /** Fahrt starten (Vordergrunddienst, misst auch im Hintergrund weiter). */
    fun startTrip() = LocationService.start(getApplication<Application>())

    /** Fahrt stoppen – Kennzahlen bleiben stehen. */
    fun stopTrip() = LocationService.stop(getApplication<Application>())

    // --- Settings-Schreibzugriffe ---
    fun setUnit(v: SpeedUnit) = viewModelScope.launch { settingsRepo.setUnit(v) }
    fun setDecimals(v: Int) = viewModelScope.launch { settingsRepo.setDecimals(v) }
    fun setTheme(v: ThemeMode) = viewModelScope.launch { settingsRepo.setTheme(v) }
    fun setKeepScreenOn(v: Boolean) = viewModelScope.launch { settingsRepo.setKeepScreenOn(v) }
    fun setSmoothing(v: Smoothing) = viewModelScope.launch { settingsRepo.setSmoothing(v) }
    fun setShowSatDetails(v: Boolean) = viewModelScope.launch { settingsRepo.setShowSatDetails(v) }
    fun setBatteryManufacturer(v: String) = viewModelScope.launch { settingsRepo.setBatteryManufacturer(v) }
    fun setBatteryType(v: String) = viewModelScope.launch { settingsRepo.setBatteryType(v) }
    fun setBatteryCapacityAh(v: Int) = viewModelScope.launch { settingsRepo.setBatteryCapacityAh(v) }

    // --- Batterie BLE ---
    fun connectBattery() = BatteryRepository.connect(getApplication<Application>())
    fun disconnectBattery() = BatteryRepository.disconnect()

    private fun smoothAndFormat(gps: GpsState, settings: Settings): String {
        val speedMs = gps.speedMs ?: run {
            speedWindow.clear()
            return NO_FIX
        }
        val window = settings.smoothing.window
        speedWindow.addLast(speedMs)
        while (speedWindow.size > window) speedWindow.removeFirst()
        val avgMs = speedWindow.average().toFloat()
        val converted = avgMs * settings.unit.factorFromMs
        return formatNumber(converted, settings.decimals)
    }

    private fun formatNumber(value: Double, decimals: Int): String =
        if (decimals <= 0) value.roundToInt().toString()
        else String.format(Locale.getDefault(), "%.${decimals}f", value)

    companion object {
        const val NO_FIX = "--"
    }
}
