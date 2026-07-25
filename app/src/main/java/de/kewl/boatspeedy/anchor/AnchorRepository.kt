package de.kewl.boatspeedy.anchor

import android.location.Location
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Zustand des Ankeralarms. */
data class AnchorState(
    val active: Boolean = false,
    val lat: Double = 0.0,
    val lon: Double = 0.0,
    val radiusM: Int = 30,
    val distanceM: Float = 0f,
    val alarming: Boolean = false,
    val acknowledged: Boolean = false, // per „Stumm" quittiert – re-armt beim Zurückdriften
)

/** Prozessweiter Halter des Ankeralarm-Zustands; wird vom [AnchorService] gefüttert. */
object AnchorRepository {

    private val _state = MutableStateFlow(AnchorState())
    val state: StateFlow<AnchorState> = _state.asStateFlow()

    /** Anker am gegebenen Punkt setzen. */
    fun setAnchor(lat: Double, lon: Double, radiusM: Int) {
        _state.value = AnchorState(active = true, lat = lat, lon = lon, radiusM = radiusM)
    }

    /** Anker einholen (Alarm aus). */
    fun clear() {
        _state.value = AnchorState()
    }

    /** Aktuelle Position einspeisen. Gibt true zurück, wenn der Alarm *neu* auslöst. */
    fun onLocation(lat: Double, lon: Double): Boolean {
        val s = _state.value
        if (!s.active) return false
        val out = FloatArray(1)
        Location.distanceBetween(s.lat, s.lon, lat, lon, out)
        val dist = out[0]
        val outside = dist > s.radiusM
        val ack = if (!outside) false else s.acknowledged // beim Zurückdriften re-armen
        val alarming = outside && !ack
        val newly = alarming && !s.alarming
        _state.value = s.copy(distanceM = dist, alarming = alarming, acknowledged = ack)
        return newly
    }

    /** Alarm stummschalten, Anker bleibt gesetzt (re-armt, wenn das Boot zurückkehrt). */
    fun silence() {
        _state.value = _state.value.copy(alarming = false, acknowledged = true)
    }
}
