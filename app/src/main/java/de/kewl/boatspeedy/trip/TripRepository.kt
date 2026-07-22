package de.kewl.boatspeedy.trip

import android.location.Location
import android.os.SystemClock
import de.kewl.boatspeedy.location.GpsState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Prozessweiter Halter des Fahrt-Zustands. Der Vordergrunddienst
 * ([de.kewl.boatspeedy.trip.LocationService]) speist hier die GPS-Updates ein und
 * rechnet die Kennzahlen hoch; die UI liest [stats] und [tracking].
 *
 * Zugriffe erfolgen vom Main-Thread (GNSS-/Location-Callback laufen über den
 * Main-Executor), daher kein zusätzliches Locking.
 */
object TripRepository {

    private val _stats = MutableStateFlow(TripStats())
    val stats: StateFlow<TripStats> = _stats.asStateFlow()

    private val _tracking = MutableStateFlow(false)
    val tracking: StateFlow<Boolean> = _tracking.asStateFlow()

    // Live-Geschwindigkeit für Tacho/Notification, auch im Hintergrund.
    private val _gps = MutableStateFlow(GpsState())
    val gps: StateFlow<GpsState> = _gps.asStateFlow()

    // Akkumulator-Zustand der laufenden Fahrt.
    private var startElapsed = 0L
    private var lastLat: Double? = null
    private var lastLon: Double? = null

    private const val MIN_SPEED_MS = 0.5f      // unter ~1,8 km/h nicht als Fahrt zählen
    private const val MAX_ACCURACY_M = 25f     // schlechte Fixes für Distanz ignorieren
    private const val MAX_STEP_M = 200.0       // Ausreißer (Sprünge) verwerfen

    /** Neue Fahrt starten – Kennzahlen zurücksetzen. */
    fun beginTrip() {
        startElapsed = SystemClock.elapsedRealtime()
        lastLat = null
        lastLon = null
        _stats.value = TripStats()
        _tracking.value = true
    }

    /** Fahrt beenden – Werte bleiben stehen. */
    fun endTrip() {
        _tracking.value = false
    }

    /** Jede GPS-Aktualisierung (aus dem Dienst) einspeisen. */
    fun onLocation(gps: GpsState) {
        _gps.value = gps
        if (!_tracking.value) return

        val elapsed = SystemClock.elapsedRealtime() - startElapsed
        val current = _stats.value

        val speed = gps.speedMs
        var distance = current.distanceM
        var maxSpeed = current.maxSpeedMs

        if (speed != null) {
            if (speed > maxSpeed) maxSpeed = speed

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
                    if (step in 0.0..MAX_STEP_M) distance += step
                }
                lastLat = lat
                lastLon = lon
            }
        }

        val avg = if (elapsed > 0) (distance / (elapsed / 1000.0)).toFloat() else 0f
        _stats.value = TripStats(
            distanceM = distance,
            maxSpeedMs = maxSpeed,
            avgSpeedMs = avg,
            elapsedMs = elapsed,
        )
    }
}
