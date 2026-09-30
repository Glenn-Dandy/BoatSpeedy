package de.kewl.boatspeedy.trip

import android.location.Location
import android.os.SystemClock
import de.kewl.boatspeedy.location.GpsState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Prozessweiter Halter des Fahrt-Zustands. Der Vordergrunddienst
 * ([de.kewl.boatspeedy.trip.LocationService]) speist GPS-Updates ein; die App speist
 * über [onBankSample] die Batterie-Werte (Strom/Leistung) ein. Die UI liest [stats],
 * [tracking] und [paused].
 *
 * **Auto-Pause:** Fließt kein nennenswerter Strom (< 0,05 A → Motor aus) und ist eine
 * Batterie verbunden, ruht die gesamte Fahrt – Zeit, Distanz und Verbrauch werden nicht
 * weitergezählt. Sobald wieder Strom fließt, läuft alles weiter. Ohne verbundene
 * Batterie gibt es keine Auto-Pause (Motorzustand unbekannt).
 *
 * Zugriffe erfolgen vom Main-Thread (Location- und Battery-Collector laufen dort).
 */
object TripRepository {

    private val _stats = MutableStateFlow(TripStats())
    val stats: StateFlow<TripStats> = _stats.asStateFlow()

    private val _tracking = MutableStateFlow(false)
    val tracking: StateFlow<Boolean> = _tracking.asStateFlow()

    /** True, wenn die Fahrt gerade automatisch pausiert ist (0 A bei verbundener Batterie). */
    private val _paused = MutableStateFlow(false)
    val paused: StateFlow<Boolean> = _paused.asStateFlow()

    /** Zuletzt beendete Fahrt zum Speichern (vom ViewModel abgeholt & mit [consumeFinished] geleert). */
    private val _justFinished = MutableStateFlow<SavedTrip?>(null)
    val justFinished: StateFlow<SavedTrip?> = _justFinished.asStateFlow()

    // Live-Geschwindigkeit für Tacho/Notification, auch im Hintergrund.
    private val _gps = MutableStateFlow(GpsState())
    val gps: StateFlow<GpsState> = _gps.asStateFlow()

    /** Live aufgezeichneter Track der laufenden/letzten Fahrt (für Mini-/Live-Karte). */
    private val _livePoints = MutableStateFlow<List<TrackPoint>>(emptyList())
    val livePoints: StateFlow<List<TrackPoint>> = _livePoints.asStateFlow()

    // Akkumulatoren der laufenden Fahrt.
    private var distanceM = 0.0
    private var maxSpeedMs = 0f
    private var energyWh = 0f
    private var chargeAh = 0f
    private var lastLat: Double? = null
    private var lastLon: Double? = null

    // Track-Aufzeichnung.
    private val points = TrackBuffer(MAX_POINTS)

    /**
     * Zeit des zuletzt verarbeiteten Fixes. Der Dienst meldet sich bei jeder Änderung von
     * GPS, Batterie oder Einstellungen, und jedes Mal kam dieselbe Position erneut an: In
     * einer Aufzeichnung vom 27.09. war jeder zweite Punkt eine exakte Wiederholung, und
     * die Grenze der Punkte war nach anderthalb statt nach drei Stunden erreicht.
     */
    private var lastFixNanos: Long? = null
    private var tripStartEpoch = 0L
    private var tripStartRealtime = 0L
    private var lastSoc = -1

    // Aktivzeit (ohne Pausen) und Integrations-Zeitstempel.
    private var accumulatedMs = 0L
    private var segmentStart = 0L
    private var running = false
    private var lastSampleTs = 0L

    private const val MIN_SPEED_MS = 0.5f      // unter ~1,8 km/h nicht als Fahrt zählen
    private const val MAX_ACCURACY_M = 25f     // schlechte Fixes für Distanz ignorieren
    private const val MAX_STEP_M = 200.0       // Ausreißer (Sprünge) verwerfen

    /** Auto-Pause-Schwelle in A (0 = aus); vom ViewModel aus den Settings gesetzt. */
    @Volatile var autoPauseAmps: Float = 0.05f

    /** Bewegungs-Schwelle der Auto-Pause (m/s); 0 = Geschwindigkeit ignorieren. */
    @Volatile var autoPauseSpeedMs: Float = 0.14f

    /** Nutzer hat die Auto-Pause für diese Fahrt überstimmt („Weiter aufzeichnen"). */
    private val _autoPauseOverride = MutableStateFlow(false)
    val autoPauseOverride: StateFlow<Boolean> = _autoPauseOverride.asStateFlow()

    /** Zuletzt gemessene Geschwindigkeit – für die „schlaue" Auto-Pause. */
    @Volatile private var lastSpeedMs: Float = 0f
    /** zuletzt gemessener Strom – für sofortige Neubewertung beim Umschalten. */
    @Volatile private var lastAmps: Float = 0f

    /** Letzte Bank-Werte für den Track und wann sie kamen (elapsedRealtime, 0 = nie). */
    @Volatile private var trackAmps: Float = Float.NaN
    @Volatile private var trackWatts: Float = Float.NaN
    @Volatile private var trackSampleTs = 0L
    /**
     * Obergrenze der Track-Punkte; darüber wird ausgedünnt, siehe [TrackBuffer]. Bei einem
     * Fix je Sekunde reicht das für knapp 14 Stunden am Stück in voller Auflösung.
     */
    private const val MAX_POINTS = 50_000

    /** So alt darf ein Bank-Wert sein, um noch in den Track zu kommen. */
    private const val BANK_FRISCH_MS = 30_000L
    private const val MIN_SAVE_DISTANCE_M = 10.0
    private const val MIN_SAVE_DURATION_MS = 10_000L

    /** Neue Fahrt starten – Kennzahlen zurücksetzen. */
    fun beginTrip() {
        distanceM = 0.0
        maxSpeedMs = 0f
        energyWh = 0f
        chargeAh = 0f
        lastLat = null
        lastLon = null
        points.clear()
        lastFixNanos = null
        _livePoints.value = emptyList()
        _autoPauseOverride.value = false
        lastSpeedMs = 0f
        lastAmps = 0f
        trackAmps = Float.NaN
        trackWatts = Float.NaN
        trackSampleTs = 0L
        lastSoc = -1
        tripStartEpoch = System.currentTimeMillis()
        tripStartRealtime = SystemClock.elapsedRealtime()
        accumulatedMs = 0L
        segmentStart = tripStartRealtime
        running = true
        lastSampleTs = 0L
        _paused.value = false
        _tracking.value = true
        emit()
    }

    /** Fahrt beenden – Werte bleiben stehen; sinnvolle Fahrten werden zum Speichern bereitgestellt. */
    fun endTrip() {
        if (running) {
            accumulatedMs += SystemClock.elapsedRealtime() - segmentStart
            running = false
        }
        _paused.value = false
        _tracking.value = false
        emit()

        val duration = accumulatedMs
        val total = if (tripStartRealtime > 0L) SystemClock.elapsedRealtime() - tripStartRealtime else duration
        if (distanceM >= MIN_SAVE_DISTANCE_M || duration >= MIN_SAVE_DURATION_MS) {
            val avg = if (duration > 0) (distanceM / (duration / 1000.0)).toFloat() else 0f
            _justFinished.value = SavedTrip(
                id = tripStartEpoch,
                startedAt = tripStartEpoch,
                distanceM = distanceM,
                durationMs = duration,
                totalMs = total,
                avgSpeedMs = avg,
                maxSpeedMs = maxSpeedMs,
                energyWh = energyWh,
                chargeAh = chargeAh,
                points = points.toList(),
            )
        }
    }

    /** Bestätigt, dass die zuletzt beendete Fahrt gespeichert wurde. */
    fun consumeFinished() {
        _justFinished.value = null
    }

    /** Jede GPS-Aktualisierung (aus dem Dienst) einspeisen. */
    fun onLocation(gps: GpsState) {
        _gps.value = gps
        if (!_tracking.value) return

        val speed = gps.speedMs
        val accOk = (gps.accuracyM ?: Float.MAX_VALUE) <= MAX_ACCURACY_M
        if (accOk) lastSpeedMs = speed ?: 0f
        if (speed != null && accOk && speed > maxSpeedMs) maxSpeedMs = speed

        // Nur ein neuer Fix zählt; eine Wiederholung derselben Position ist keiner.
        val neuerFix = gps.fixNanos == null || gps.fixNanos != lastFixNanos
        if (neuerFix) lastFixNanos = gps.fixNanos

        // Distanz nur zählen, wenn nicht pausiert.
        if (running && speed != null && neuerFix) {
            val acc = gps.accuracyM ?: Float.MAX_VALUE
            val lat = gps.latitude
            val lon = gps.longitude
            if (speed >= MIN_SPEED_MS && acc <= MAX_ACCURACY_M && lat != null && lon != null) {
                val pLat = lastLat
                val pLon = lastLon
                if (pLat != null && pLon != null) {
                    val out = FloatArray(1)
                    Location.distanceBetween(pLat, pLon, lat, lon, out)
                    val step = out[0].toDouble()
                    if (step in 0.0..MAX_STEP_M) distanceM += step
                }
                lastLat = lat
                lastLon = lon
                val aufgenommen = points.add(
                    TrackPoint(
                        lat = lat,
                        lon = lon,
                        tMs = SystemClock.elapsedRealtime() - tripStartRealtime,
                        speedMs = speed,
                        soc = lastSoc,
                        chargeAh = chargeAh,
                        // Nur frische Werte: Reißt die Verbindung zur Batterie ab, steht
                        // sonst minutenlang der letzte Strom im Track.
                        currentA = if (bankFrisch()) trackAmps else Float.NaN,
                        powerW = if (bankFrisch()) trackWatts else Float.NaN,
                    ),
                )
                if (aufgenommen) _livePoints.value = points.toList()
            }
        }
        evaluateAutoPause(SystemClock.elapsedRealtime())
        emit()
    }

    /**
     * Momentane Bank-Werte (Strom A, Leistung W) einspeisen; integriert sie über die Zeit
     * zu Ah/Wh und steuert die Auto-Pause. Nur während einer Fahrt.
     */
    fun onBankSample(amps: Float, watts: Float, soc: Int) {
        lastSoc = soc
        if (!_tracking.value) return
        val now = SystemClock.elapsedRealtime()
        lastAmps = amps
        trackAmps = kotlin.math.abs(amps)
        trackWatts = kotlin.math.abs(watts)
        trackSampleTs = now
        evaluateAutoPause(now)

        if (running) {
            if (lastSampleTs != 0L) {
                val dtHours = (now - lastSampleTs) / 3_600_000f
                energyWh += kotlin.math.abs(watts) * dtHours
                chargeAh += kotlin.math.abs(amps) * dtHours
            }
            lastSampleTs = now
        } else {
            lastSampleTs = 0L
        }
        emit()
    }

    /** Ob der letzte Bank-Wert jünger als [BANK_FRISCH_MS] ist. */
    private fun bankFrisch(): Boolean =
        trackSampleTs != 0L && SystemClock.elapsedRealtime() - trackSampleTs <= BANK_FRISCH_MS

    /**
     * Schlaue Auto-Pause: pausiert nur, wenn wenig Strom fließt UND das Boot steht.
     * Treiben (Motor aus, aber in Bewegung) wird weiter aufgezeichnet.
     */
    private fun evaluateAutoPause(now: Long) {
        if (!_tracking.value) return
        val threshold = autoPauseAmps
        val speedLimit = autoPauseSpeedMs
        val moving = speedLimit > 0f && lastSpeedMs >= speedLimit
        val shouldRun = _autoPauseOverride.value || threshold <= 0f ||
            kotlin.math.abs(lastAmps) >= threshold || moving

        if (shouldRun && !running) resumeAt(now)
        else if (!shouldRun && running) pauseAt(now)
    }

    /** „Weiter aufzeichnen" bzw. Auto-Pause wieder aktivieren – sofort neu bewerten. */
    fun overrideAutoPause(enabled: Boolean) {
        _autoPauseOverride.value = enabled
        evaluateAutoPause(SystemClock.elapsedRealtime())
    }

    private fun resumeAt(now: Long) {
        segmentStart = now
        running = true
        _paused.value = false
        // Distanz neu verankern, damit Drift während der Pause nicht zählt.
        lastLat = null
        lastLon = null
    }

    private fun pauseAt(now: Long) {
        accumulatedMs += now - segmentStart
        running = false
        _paused.value = true
        lastSampleTs = 0L
    }

    private fun activeElapsed(): Long =
        accumulatedMs + if (running) SystemClock.elapsedRealtime() - segmentStart else 0L

    private fun emit() {
        val elapsed = activeElapsed()
        val total = if (tripStartRealtime > 0L) SystemClock.elapsedRealtime() - tripStartRealtime else 0L
        val avg = if (elapsed > 0) (distanceM / (elapsed / 1000.0)).toFloat() else 0f
        _stats.value = TripStats(
            distanceM = distanceM,
            maxSpeedMs = maxSpeedMs,
            avgSpeedMs = avg,
            elapsedMs = elapsed,
            totalMs = total,
            energyWh = energyWh,
            chargeAh = chargeAh,
        )
    }
}
