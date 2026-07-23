package de.kewl.boatspeedy.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.kewl.boatspeedy.battery.BatteryData
import de.kewl.boatspeedy.battery.BatteryHub
import de.kewl.boatspeedy.battery.BatteryRepository
import de.kewl.boatspeedy.battery.BmsType
import de.kewl.boatspeedy.battery.RangeEstimate
import de.kewl.boatspeedy.battery.ScanDevice
import de.kewl.boatspeedy.battery.estimateRange
import de.kewl.boatspeedy.battery.selectedBatteryData
import de.kewl.boatspeedy.data.BankMode
import de.kewl.boatspeedy.data.COMBINED_SELECTION
import de.kewl.boatspeedy.data.SavedBattery
import de.kewl.boatspeedy.data.Settings
import de.kewl.boatspeedy.data.SettingsRepository
import de.kewl.boatspeedy.data.SpeedUnit
import de.kewl.boatspeedy.data.Smoothing
import de.kewl.boatspeedy.data.ThemeMode
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

    // Batterie-Laufzeitzustand (alle offenen BLE-Links + Scan).
    val battery: StateFlow<BatteryHub> = BatteryRepository.state

    /** Auf dem Dashboard anzuzeigende Werte (einzelne Batterie oder kombiniert). */
    val dashboardBattery: StateFlow<BatteryData?> =
        combine(settings, battery) { s, hub -> selectedBatteryData(s, hub) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Reichweite/Restzeit aus den ausgewählten Batterie-Werten + aktueller Geschwindigkeit. */
    val dashboardRange: StateFlow<RangeEstimate?> =
        combine(settings, battery, _gps) { s, hub, gps ->
            estimateRange(selectedBatteryData(s, hub), gps.speedMs)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

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
    fun setShowBatteryTile(v: Boolean) = viewModelScope.launch { settingsRepo.setShowBatteryTile(v) }
    fun setShowRangeTile(v: Boolean) = viewModelScope.launch { settingsRepo.setShowRangeTile(v) }
    fun setBms(v: BmsType) = viewModelScope.launch { settingsRepo.setBatteryBms(v) }
    fun setBankMode(v: BankMode) = viewModelScope.launch { settingsRepo.setBankMode(v) }
    fun setDashboardBattery(v: String) = viewModelScope.launch { settingsRepo.setDashboardBattery(v) }

    // --- Batterie BLE / Verwaltung ---
    fun scanBattery() = BatteryRepository.scan(getApplication<Application>(), settings.value.batteryBms)
    fun stopScan() = BatteryRepository.stopScan()

    /** Gefundenes Gerät dauerhaft übernehmen (aktiv) und gleich verbinden. */
    fun addBattery(device: ScanDevice) {
        val name = device.name?.takeIf { it.isNotBlank() } ?: device.address
        val current = settings.value.batteries
        if (current.none { it.address == device.address }) {
            viewModelScope.launch {
                settingsRepo.setBatteries(current + SavedBattery(device.address, name, active = true))
            }
        }
        connectBattery(device.address)
    }

    fun removeBattery(address: String) {
        BatteryRepository.disconnect(address)
        val s = settings.value
        viewModelScope.launch {
            settingsRepo.setBatteries(s.batteries.filterNot { it.address == address })
            if (s.dashboardBattery == address) settingsRepo.setDashboardBattery(COMBINED_SELECTION)
        }
    }

    fun setBatteryActive(address: String, active: Boolean) {
        val s = settings.value
        viewModelScope.launch {
            settingsRepo.setBatteries(
                s.batteries.map { if (it.address == address) it.copy(active = active) else it },
            )
        }
    }

    fun connectBattery(address: String) {
        val name = settings.value.batteries.firstOrNull { it.address == address }?.name
            ?: battery.value.scanResults.firstOrNull { it.address == address }?.name
            ?: address
        BatteryRepository.connect(getApplication<Application>(), address, name, settings.value.batteryBms)
    }

    fun disconnectBattery(address: String) = BatteryRepository.disconnect(address)

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
