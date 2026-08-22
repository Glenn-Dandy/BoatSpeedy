package de.kewl.boatspeedy.ui

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.location.LocationManager
import android.os.SystemClock
import de.kewl.boatspeedy.R
import androidx.lifecycle.AndroidViewModel
import de.kewl.boatspeedy.alarm.AlarmController
import de.kewl.boatspeedy.alarm.AlarmPlayer
import de.kewl.boatspeedy.anchor.AnchorRepository
import de.kewl.boatspeedy.anchor.AnchorService
import de.kewl.boatspeedy.anchor.AnchorState
import de.kewl.boatspeedy.data.AlarmSound
import androidx.lifecycle.viewModelScope
import de.kewl.boatspeedy.battery.BatteryData
import de.kewl.boatspeedy.battery.BatteryHub
import de.kewl.boatspeedy.battery.BatteryRepository
import de.kewl.boatspeedy.battery.ChargeState
import de.kewl.boatspeedy.battery.BmsType
import de.kewl.boatspeedy.battery.RangeEstimate
import de.kewl.boatspeedy.battery.ScanDevice
import de.kewl.boatspeedy.battery.TimedAverage
import de.kewl.boatspeedy.battery.activeBatteryData
import de.kewl.boatspeedy.battery.combineBatteries
import de.kewl.boatspeedy.battery.estimateRange
import de.kewl.boatspeedy.battery.selectedBatteryData
import de.kewl.boatspeedy.data.BankMode
import de.kewl.boatspeedy.data.RangeSmoothing
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
import de.kewl.boatspeedy.trip.SavedTrip
import de.kewl.boatspeedy.trip.TripRepository
import de.kewl.boatspeedy.trip.TripStats
import de.kewl.boatspeedy.trip.TripStore
import de.kewl.boatspeedy.util.Notifier
import de.kewl.boatspeedy.weather.WeatherRepository
import de.kewl.boatspeedy.weather.WeatherWarning
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.ArrayDeque
import java.util.Locale
import kotlin.math.roundToInt

class SpeedViewModel(app: Application) : AndroidViewModel(app) {

    private val settingsRepo = SettingsRepository(app)
    private val locationProvider = LocationProvider(app)
    private val tripStore = TripStore(app)

    private val _trips = MutableStateFlow<List<SavedTrip>>(emptyList())
    val trips: StateFlow<List<SavedTrip>> = _trips.asStateFlow()

    val settings: StateFlow<Settings> =
        settingsRepo.settings.stateIn(viewModelScope, SharingStarted.Eagerly, Settings())

    private val _gps = MutableStateFlow(GpsState())
    val gps: StateFlow<GpsState> = _gps.asStateFlow()

    // Fahrt-Zustand aus dem prozessweiten TripRepository (vom Dienst gespeist).
    val tracking: StateFlow<Boolean> = TripRepository.tracking
    val tripStats: StateFlow<TripStats> = TripRepository.stats
    val tripPaused: StateFlow<Boolean> = TripRepository.paused
    val autoPauseOverride: StateFlow<Boolean> = TripRepository.autoPauseOverride
    val livePoints: StateFlow<List<de.kewl.boatspeedy.trip.TrackPoint>> = TripRepository.livePoints

    // Ankeralarm.
    val anchor: StateFlow<AnchorState> = AnchorRepository.state

    private var lastSocLow = false

    // Batterie-Laufzeitzustand (alle offenen BLE-Links + Scan).
    val battery: StateFlow<BatteryHub> = BatteryRepository.state

    /** Auf dem Dashboard anzuzeigende Werte (einzelne Batterie oder kombiniert). */
    val dashboardBattery: StateFlow<BatteryData?> =
        combine(settings, battery) { s, hub -> selectedBatteryData(s, hub) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /**
     * Reichweite/Restzeit aus den ausgewählten Batterie-Werten + Geschwindigkeit.
     * Entladestrom und Geschwindigkeit werden über ein Zeitfenster gemittelt
     * ([Settings.rangeSmoothing]), damit die Anzeige nicht mit dem Momentanstrom zappelt.
     */
    private val currentAvg = TimedAverage()
    private val speedAvg = TimedAverage()
    private val _dashboardRange = MutableStateFlow<RangeEstimate?>(null)
    val dashboardRange: StateFlow<RangeEstimate?> = _dashboardRange.asStateFlow()

    private data class RangeSample(val data: BatteryData?, val speedMs: Float?, val mode: RangeSmoothing)

    // Ladezustand fürs Dashboard (Lademodus).
    private val _charge = MutableStateFlow(ChargeState())
    val charge: StateFlow<ChargeState> = _charge.asStateFlow()
    private var chargeSessionActive = false
    private var chargeTargetNotified = false

    // Aktive DWD-Wetterwarnungen (prozessweit gepflegt).
    val weatherWarnings: StateFlow<List<WeatherWarning>> = WeatherRepository.active

    /** Text des quittierpflichtigen Alarms (null = keiner aktiv). */
    val pendingAlarm: StateFlow<String?> = AlarmController.active

    /** Alarm quittieren (Banner, Lautstärketaste …). */
    fun acknowledgeAlarm() = AlarmController.stop(getApplication())

    init {
        viewModelScope.launch {
            combine(settings, battery, _gps) { s, hub, gps ->
                RangeSample(selectedBatteryData(s, hub), gps.speedMs, s.rangeSmoothing)
            }.collect { updateRange(it) }
        }
        // Während einer Fahrt Strom/Leistung der aktiven Bank einspeisen (Ah/Wh + Auto-Pause).
        viewModelScope.launch {
            combine(settings, battery, tracking) { s, hub, isTracking ->
                if (isTracking) activeBatteryData(s, hub).takeIf { it.isNotEmpty() }
                    ?.let { combineBatteries(it, s.bankMode) } else null
            }.collect { d -> if (d != null) TripRepository.onBankSample(d.currentA, d.powerW, d.soc) }
        }
        // Beendete Fahrten dauerhaft speichern.
        viewModelScope.launch {
            TripRepository.justFinished.collect { finished ->
                if (finished != null) {
                    tripStore.save(finished)
                    TripRepository.consumeFinished()
                    _trips.value = tripStore.list()
                }
            }
        }
        // Auto-Pause-Schalter/-Schwelle aus den Settings in das TripRepository spiegeln.
        viewModelScope.launch {
            settings.collect {
                TripRepository.autoPauseAmps = if (it.autoPauseOn) it.autoPauseAmps else 0f
                TripRepository.autoPauseSpeedMs = it.autoPauseSpeedMs
            }
        }
        // SoC-Alarm-Ton bei fallender Flanke unter die Schwelle.
        // voltage>0 & soc>=1 schließt die kurzen 0-Werte direkt nach dem Verbinden aus.
        viewModelScope.launch {
            combine(settings, dashboardBattery) { s, data ->
                val low = s.lowSocPercent > 0 && data != null &&
                    data.voltage > 0f && data.soc in 1..s.lowSocPercent
                Triple(low, s.socAlarmOn, s.socSound)
            }.collect { (low, on, sound) ->
                // Dauer-Alarm: läuft, bis der Nutzer quittiert (Lautstärketaste / Banner / „Stumm").
                if (low && !lastSocLow && on) {
                    AlarmController.trigger(
                        getApplication(),
                        getString(R.string.low_soc_warn),
                        getString(R.string.soc_alarm_text, settings.value.lowSocPercent),
                        sound,
                        withSound = true,
                    )
                }
                lastSocLow = low
            }
        }
        // Ladeerkennung (Strom positiv) → Lademodus + GPS aus + „voll"-Meldung.
        viewModelScope.launch {
            dashboardBattery.collect { d -> updateCharge(d) }
        }
        // DWD-Wetterwarnungen periodisch prüfen, solange keine Fahrt läuft
        // (während der Fahrt übernimmt das der Vordergrunddienst).
        viewModelScope.launch {
            while (true) {
                if (!settings.value.weatherWarnEnabled) WeatherRepository.clear() else maybeWeather(force = false)
                delay(WEATHER_INTERVAL_MS)
            }
        }
        // Dauer-Statusmeldung ohne laufende Fahrt („immer anzeigen").
        // Während einer Fahrt zeigt der Vordergrunddienst seine eigene Meldung.
        viewModelScope.launch {
            combine(settings, battery, _gps, tracking) { s, hub, gps, isTracking ->
                if (s.notifEnabled && s.notifAlways && !isTracking) {
                    de.kewl.boatspeedy.util.notificationLines(
                        getApplication(), gps, s, hub, TripRepository.stats.value,
                    )
                } else {
                    null
                }
            }.collect { lines ->
                if (lines == null) {
                    Notifier.cancel(getApplication(), STATUS_NOTIF_ID)
                } else {
                    val text = listOf(lines.first, lines.second).filter { it.isNotBlank() }.joinToString("\n")
                    Notifier.ongoing(
                        getApplication(), "status", getString(R.string.notif_section), STATUS_NOTIF_ID,
                        getString(R.string.app_name), text.ifBlank { "–" },
                    )
                }
            }
        }
        // Sobald eine Position da ist (GPS-Fix), sofort prüfen – beim Rausfahren
        // soll eine Warnung gleich kommen, nicht erst beim nächsten 10-Min-Tick.
        viewModelScope.launch {
            _gps.map { it.latitude != null && it.longitude != null }
                .distinctUntilChanged()
                .collect { hasPos -> if (hasPos) maybeWeather(force = true) }
        }
    }

    private var lastWeatherMs = 0L

    /** Prüft DWD-Warnungen für die beste verfügbare Position (throttled, außer [force]). */
    private suspend fun maybeWeather(force: Boolean) {
        val s = settings.value
        if (!s.weatherWarnEnabled || tracking.value) return // Fahrt: Dienst übernimmt
        val pos = bestPosition() ?: return
        val now = SystemClock.elapsedRealtime()
        if (!force && now - lastWeatherMs < WEATHER_INTERVAL_MS) return
        lastWeatherMs = now
        WeatherRepository.check(getApplication(), pos.first, pos.second, s.weatherAlarmOn, s.weatherSound)
    }

    /** Ladezustand aus den ausgewählten Batteriewerten ableiten. Positiver Strom = Laden. */
    private fun updateCharge(d: BatteryData?) {
        // Während einer Fahrt kein Lademodus (GPS soll laufen).
        if (d == null || d.voltage <= 0f || tracking.value) {
            if (chargeSessionActive) { chargeSessionActive = false; applyGps(); cancelChargeNotif() }
            _charge.value = ChargeState(soc = d?.soc ?: 0)
            return
        }
        val cur = d.currentA
        if (!chargeSessionActive && cur >= CHARGE_ON_A) {
            chargeSessionActive = true
            chargeTargetNotified = false // neue Ladesitzung → Ziel-Meldung wieder scharf
            applyGps() // GPS deaktivieren, solange geladen wird
        }
        if (chargeSessionActive) {
            when {
                cur < 0f -> { // wieder Entladen → Ladevorgang beendet (abgeklemmt)
                    chargeSessionActive = false; applyGps(); cancelChargeNotif()
                    _charge.value = ChargeState(soc = d.soc)
                }
                cur < CHARGE_FULL_A -> { // Strom auf ~0 abgeklungen → voll
                    chargeSessionActive = false; applyGps(); cancelChargeNotif()
                    if (d.soc >= FULL_SOC_MIN) notifyFull(d.soc)
                    _charge.value = ChargeState(soc = d.soc)
                }
                else -> { // lädt (Bulk oder Nachladen)
                    val missingAh = when {
                        d.nominalAh > 0f && d.remainingAh > 0f -> (d.nominalAh - d.remainingAh).coerceAtLeast(0f)
                        d.nominalAh > 0f -> d.nominalAh * (100 - d.soc) / 100f
                        else -> 0f
                    }
                    val hours = if (cur > 0.1f && missingAh > 0f) missingAh / cur.toDouble() else null
                    val fullAt = hours?.let { System.currentTimeMillis() + (it * 3600_000).toLong() }
                    _charge.value = ChargeState(charging = true, chargeA = cur, soc = d.soc, hoursToFull = hours, fullAtEpochMs = fullAt)
                    notifyCharging(d.soc, hours)
                    // Individuelle Meldung, wenn der eingestellte Ladestand erreicht ist.
                    val target = settings.value.chargeTargetSoc
                    if (target in 1..100 && d.soc >= target && !chargeTargetNotified) {
                        chargeTargetNotified = true
                        // Wie beim niedrigen Ladestand: dauerhaft, bis quittiert.
                        AlarmController.trigger(
                            getApplication(),
                            getString(R.string.charge_reached_title),
                            getString(R.string.charge_reached_text, d.soc),
                            settings.value.socSound,
                            withSound = settings.value.socAlarmOn,
                        )
                    }
                }
            }
        } else {
            _charge.value = ChargeState(soc = d.soc)
        }
    }

    /** Laufende Lade-Meldung mit SoC (und ggf. Restdauer). */
    private fun notifyCharging(soc: Int, hoursToFull: Double?) {
        val time = hoursToFull?.let { formatDuration((it * 3600_000).toLong()) }
        val text = if (time != null) {
            "${getString(R.string.soc_short)} $soc % · ${getString(R.string.charge_time_to_full)} $time"
        } else {
            "${getString(R.string.soc_short)} $soc %"
        }
        Notifier.ongoing(
            getApplication(), "charge_status", getString(R.string.charge_status_channel), CHARGE_NOTIF_ID,
            getString(R.string.charge_notif_title), text,
        )
    }

    private fun cancelChargeNotif() = Notifier.cancel(getApplication(), CHARGE_NOTIF_ID)

    private fun notifyFull(soc: Int) {
        Notifier.notify(
            getApplication(), "charge", getString(R.string.charge_channel), 5,
            getString(R.string.charge_full_title),
            getString(R.string.charge_full_text, soc),
            high = false,
        )
    }

    private fun getString(resId: Int, vararg args: Any): String =
        getApplication<Application>().getString(resId, *args)

    /** Fahrten-Liste aus dem Speicher laden (z. B. beim Öffnen des Historie-Screens). */
    fun refreshTrips() = viewModelScope.launch { _trips.value = tripStore.list() }

    /** Markierte Fahrten zu einer zusammenführen (Originale werden ersetzt). */
    fun mergeTrips(ids: Set<Long>, onDone: (Boolean) -> Unit = {}) = viewModelScope.launch {
        val chosen = _trips.value.filter { it.id in ids }
        val merged = de.kewl.boatspeedy.trip.mergeTrips(chosen)
        if (merged == null) { onDone(false); return@launch }
        tripStore.delete(ids)
        tripStore.save(merged)
        _trips.value = tripStore.list()
        onDone(true)
    }

    fun deleteTrips(ids: Set<Long>) = viewModelScope.launch {
        tripStore.delete(ids)
        _trips.value = tripStore.list()
    }

    /** Eine GPX-Datei importieren; ruft [onDone] mit true/false (Erfolg) auf dem Main-Thread. */
    fun importGpx(uri: android.net.Uri, onDone: (Boolean) -> Unit = {}) = viewModelScope.launch {
        val trip = de.kewl.boatspeedy.trip.GpxImport.import(getApplication(), uri, tripStore)
        if (trip != null) _trips.value = tripStore.list()
        onDone(trip != null)
    }

    private fun updateRange(sample: RangeSample) {
        val (data, speed, mode) = sample
        if (mode == RangeSmoothing.OFF || data == null) {
            _dashboardRange.value = estimateRange(data, speed)
            return
        }
        val now = SystemClock.elapsedRealtime()
        if (data.dischargeA > 0.1f) currentAvg.add(data.dischargeA, now)
        if (speed != null && speed > 0.1f) speedAvg.add(speed, now)
        val avgCurrent = currentAvg.average(mode.windowMs, now)
        val avgSpeed = speedAvg.average(mode.windowMs, now) ?: speed
        _dashboardRange.value = if (avgCurrent == null) {
            estimateRange(data, speed)
        } else {
            estimateRange(data.remainingAh, data.nominalAh, data.soc, avgCurrent, avgSpeed)
        }
    }

    // Gleitender Mittelwert der rohen Geschwindigkeit (m/s).
    private val speedWindow = ArrayDeque<Float>()
    // A+D: letzten Anzeigewert halten und schlechte Fixes fürs Tempo ignorieren.
    private var lastDisplayMs: Float? = null
    private var badTicks = 0

    /** Fertig formatierter Anzeigewert (bereits geglättet & umgerechnet). Im Lademodus „--". */
    val displaySpeed: StateFlow<String> =
        combine(_gps, settings, _charge) { gps, settings, charge ->
            if (charge.charging) NO_FIX else smoothAndFormat(gps, settings)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, NO_FIX)

    private var collectJob: Job? = null
    private var wantGps = false // Vordergrund (onResume) will GPS

    /** GPS-Updates starten (Aufruf in onResume, nachdem die Berechtigung da ist). */
    fun startUpdates() {
        wantGps = true
        applyGps()
    }

    /** GPS-Updates für den Vordergrund-Tacho stoppen (onPause). */
    fun stopUpdates() {
        wantGps = false
        applyGps()
    }

    /** GPS läuft, wenn der Vordergrund es will UND nicht gerade geladen wird. */
    private fun applyGps() {
        if (wantGps && !chargeSessionActive) startCollect() else stopCollect()
    }

    private fun startCollect() {
        if (collectJob?.isActive == true) return
        speedWindow.clear()
        lastDisplayMs = null
        badTicks = 0
        collectJob = viewModelScope.launch {
            locationProvider.state.collect { _gps.value = it }
        }
    }

    private fun stopCollect() {
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
    fun setRangeSmoothing(v: RangeSmoothing) = viewModelScope.launch { settingsRepo.setRangeSmoothing(v) }
    fun setLowSocPercent(v: Int) = viewModelScope.launch { settingsRepo.setLowSocPercent(v) }
    fun setShowSatDetails(v: Boolean) = viewModelScope.launch { settingsRepo.setShowSatDetails(v) }
    fun setShowBatteryTile(v: Boolean) = viewModelScope.launch { settingsRepo.setShowBatteryTile(v) }
    fun setShowRangeTile(v: Boolean) = viewModelScope.launch { settingsRepo.setShowRangeTile(v) }
    fun setShowMapTile(v: Boolean) = viewModelScope.launch { settingsRepo.setShowMapTile(v) }
    fun setTrackColor(v: de.kewl.boatspeedy.data.TrackColor) = viewModelScope.launch { settingsRepo.setTrackColor(v) }
    fun setTrackWidth(v: de.kewl.boatspeedy.data.TrackWidth) = viewModelScope.launch { settingsRepo.setTrackWidth(v) }
    fun setTrackArrows(v: Boolean) = viewModelScope.launch { settingsRepo.setTrackArrows(v) }
    fun setAutoPauseAmps(v: Float) = viewModelScope.launch { settingsRepo.setAutoPauseAmps(v) }
    fun setAutoPauseOn(v: Boolean) = viewModelScope.launch { settingsRepo.setAutoPauseOn(v) }
    fun setAutoPauseSpeedMs(v: Float) = viewModelScope.launch { settingsRepo.setAutoPauseSpeedMs(v) }
    fun setNotifFields(v: Set<de.kewl.boatspeedy.data.NotifField>) = viewModelScope.launch { settingsRepo.setNotifFields(v) }
    fun setNotifEnabled(v: Boolean) = viewModelScope.launch { settingsRepo.setNotifEnabled(v) }
    fun setNotifAlways(v: Boolean) = viewModelScope.launch { settingsRepo.setNotifAlways(v) }

    /** „Weiter aufzeichnen" bzw. Auto-Pause wieder aktivieren. */
    fun setAutoPauseOverride(v: Boolean) = TripRepository.overrideAutoPause(v)
    fun setAnchorAlarmOn(v: Boolean) = viewModelScope.launch { settingsRepo.setAnchorAlarmOn(v) }
    fun setAnchorSound(v: AlarmSound) = viewModelScope.launch { settingsRepo.setAnchorSound(v) }
    fun setSocAlarmOn(v: Boolean) = viewModelScope.launch { settingsRepo.setSocAlarmOn(v) }
    fun setSocSound(v: AlarmSound) = viewModelScope.launch { settingsRepo.setSocSound(v) }
    fun setChargeTargetSoc(v: Int) = viewModelScope.launch { settingsRepo.setChargeTargetSoc(v) }
    fun setAnchorRadius(v: Int) = viewModelScope.launch { settingsRepo.setAnchorRadius(v) }
    fun setWeatherEnabled(v: Boolean) = viewModelScope.launch {
        settingsRepo.setWeatherEnabled(v)
        if (!v) WeatherRepository.clear() else maybeWeather(force = true)
    }

    /** Beste verfügbare Position: Fused-GPS aus dem State, sonst LocationManager-Last-Known
     *  (den Fake-GPS-Apps meist füttern, auch wenn der Fused-Provider sie nicht durchreicht). */
    @SuppressLint("MissingPermission")
    private fun bestPosition(): Pair<Double, Double>? {
        val g = _gps.value
        if (g.latitude != null && g.longitude != null) return g.latitude!! to g.longitude!!
        val lm = getApplication<Application>().getSystemService(Context.LOCATION_SERVICE) as LocationManager
        for (p in listOf(LocationManager.GPS_PROVIDER, LocationManager.FUSED_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
            val loc = runCatching { lm.getLastKnownLocation(p) }.getOrNull()
            if (loc != null) return loc.latitude to loc.longitude
        }
        return null
    }
    fun setWeatherAlarmOn(v: Boolean) = viewModelScope.launch { settingsRepo.setWeatherAlarmOn(v) }
    fun setDevMode(v: Boolean) = viewModelScope.launch { settingsRepo.setDevMode(v) }
    fun setWeatherSound(v: AlarmSound) = viewModelScope.launch { settingsRepo.setWeatherSound(v) }
    fun testWeatherSound() = AlarmPlayer.play(getApplication(), settings.value.weatherSound, loop = false)

    // --- Ankeralarm ---
    /** Anker an der aktuellen Position setzen und die Wache (Vordergrunddienst) starten. */
    fun setAnchor() {
        val g = _gps.value
        val lat = g.latitude ?: return
        val lon = g.longitude ?: return
        val s = settings.value
        AnchorRepository.setAnchor(lat, lon, s.anchorRadiusM)
        AnchorService.start(getApplication(), s.anchorSound, s.anchorAlarmOn)
    }

    fun raiseAnchor() = AnchorService.stop(getApplication())
    fun silenceAnchor() = AnchorService.silence(getApplication())

    fun testAnchorSound() = AlarmPlayer.play(getApplication(), settings.value.anchorSound, loop = false)
    fun testSocSound() = AlarmPlayer.play(getApplication(), settings.value.socSound, loop = false)
    fun setBms(v: BmsType) = viewModelScope.launch { settingsRepo.setBatteryBms(v) }
    fun setBankMode(v: BankMode) = viewModelScope.launch { settingsRepo.setBankMode(v) }
    fun setDashboardBattery(v: String) = viewModelScope.launch { settingsRepo.setDashboardBattery(v) }

    // --- Batterie BLE / Verwaltung ---
    fun scanBattery() = BatteryRepository.scan(getApplication<Application>())
    fun stopScan() = BatteryRepository.stopScan()

    /** Gefundenes Gerät dauerhaft übernehmen (aktiv) und gleich verbinden. */
    /** Gefundenes Gerät übernehmen. [bms] überschreibt die automatische Erkennung. */
    fun addBattery(device: ScanDevice, bms: BmsType? = null) {
        // Kurzer, sprechender Name: 3 Zeichen des Typs + letzte 4 der MAC (z. B. „DP0-5F3A").
        val name = de.kewl.boatspeedy.data.shortBatteryName(device.name, device.address)
        val type = bms ?: device.bms ?: BmsType.JBD
        val current = settings.value.batteries
        if (current.none { it.address == device.address }) {
            viewModelScope.launch {
                settingsRepo.setBatteries(
                    current + SavedBattery(device.address, name, active = true, bleName = device.name, bms = type),
                )
                connectBattery(device.address)
            }
        } else {
            connectBattery(device.address)
        }
    }

    /** BMS-Protokoll einer gespeicherten Batterie ändern (falls die Erkennung danebenlag). */
    fun setBatteryBms(address: String, bms: BmsType) {
        val s = settings.value
        viewModelScope.launch {
            settingsRepo.setBatteries(s.batteries.map { if (it.address == address) it.copy(bms = bms) else it })
            // Mit neuem Protokoll frisch verbinden.
            if (battery.value.links[address] != null) {
                BatteryRepository.disconnect(address)
                connectBattery(address)
            }
        }
    }

    /** Batterie umbenennen (Typ/MAC bleiben erhalten). */
    fun renameBattery(address: String, name: String) {
        val clean = name.trim().take(24)
        if (clean.isEmpty()) return
        val s = settings.value
        viewModelScope.launch {
            settingsRepo.setBatteries(
                s.batteries.map { if (it.address == address) it.copy(name = clean) else it },
            )
        }
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

    /** Alle aktiven gespeicherten Akkus verbinden (App-Start), die noch keinen Link haben. */
    fun autoConnectActive() {
        val app = getApplication<Application>()
        settings.value.batteries.filter { it.active }.forEach { b ->
            if (battery.value.links[b.address] == null) {
                BatteryRepository.connect(app, b.address, b.name, b.bms)
            }
        }
    }

    fun connectBattery(address: String) {
        val saved = settings.value.batteries.firstOrNull { it.address == address }
        val name = saved?.name
            ?: battery.value.scanResults.firstOrNull { it.address == address }?.name
            ?: address
        // Jede Batterie bringt ihr eigenes BMS-Protokoll mit.
        val bms = saved?.bms
            ?: battery.value.scanResults.firstOrNull { it.address == address }?.bms
            ?: BmsType.JBD
        BatteryRepository.connect(getApplication<Application>(), address, name, bms)
    }

    fun disconnectBattery(address: String) = BatteryRepository.disconnect(address)

    private fun smoothAndFormat(gps: GpsState, settings: Settings): String {
        val raw = gps.speedMs
        val acc = gps.accuracyM
        // D: nur Fixes mit brauchbarer Genauigkeit fürs Tempo verwenden.
        val good = raw != null && (acc == null || acc <= MAX_ACCURACY_M)

        if (!good) {
            // A: kurze Aussetzer/schlechte Fixes überbrücken – letzten Wert halten,
            // erst nach längerem Verlust auf „--" fallen.
            badTicks++
            if (badTicks > MAX_HOLD_TICKS) {
                speedWindow.clear()
                lastDisplayMs = null
                return NO_FIX
            }
            return lastDisplayMs?.let { formatMs(it, settings) } ?: NO_FIX
        }

        badTicks = 0
        val window = settings.smoothing.window
        speedWindow.addLast(raw!!)
        while (speedWindow.size > window) speedWindow.removeFirst()
        val avgMs = speedWindow.average().toFloat()
        lastDisplayMs = avgMs
        return formatMs(avgMs, settings)
    }

    private fun formatMs(speedMs: Float, settings: Settings): String =
        formatNumber(speedMs * settings.unit.factorFromMs, settings.decimals)

    private fun formatNumber(value: Double, decimals: Int): String =
        if (decimals <= 0) value.roundToInt().toString()
        else String.format(Locale.getDefault(), "%.${decimals}f", value)

    companion object {
        const val NO_FIX = "--"
        private const val MAX_ACCURACY_M = 25f  // schlechtere Fixes fürs Tempo ignorieren (D)
        private const val MAX_HOLD_TICKS = 5    // so viele schlechte Fixes den letzten Wert halten (A)
        private const val CHARGE_ON_A = 0.5f    // ab diesem positiven Strom gilt „lädt"
        private const val CHARGE_FULL_A = 0.1f  // darunter: Strom abgeklungen → voll
        private const val FULL_SOC_MIN = 90     // „voll" nur ab diesem Ladestand melden
        private const val CHARGE_NOTIF_ID = 6
        private const val STATUS_NOTIF_ID = 9   // laufende Lade-Meldung
        private const val WEATHER_INTERVAL_MS = 10 * 60_000L // DWD-Prüfintervall
    }
}
