package de.kewl.boatspeedy.nav

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Prozessweiter Halter des gesetzten Ziels — nach dem Vorbild von `AnchorRepository`.
 *
 * Der Weg gehört nicht in den Bildschirm, sondern zur Fahrt: er soll bestehen bleiben,
 * wenn man zwischen Dashboard und Karte wechselt, und erst verschwinden, wenn man ihn
 * beendet oder ankommt.
 */
object NavRepository {

    /** Wie nah man dem Ziel kommen muss, damit es als erreicht gilt. */
    const val ARRIVE_M = 5.0

    private val _target = MutableStateFlow<NavTarget?>(null)
    val target: StateFlow<NavTarget?> = _target.asStateFlow()

    fun set(target: NavTarget) {
        _target.value = target
    }

    fun clear() {
        _target.value = null
    }

    /**
     * Aktuelle Position einspeisen. Räumt das Ziel ab, sobald es erreicht ist, und gibt
     * dann true zurück.
     */
    fun onLocation(lat: Double, lon: Double): Boolean {
        val t = _target.value ?: return false
        if (distanceM(LatLon(lat, lon), t.target) > ARRIVE_M) return false
        _target.value = null
        return true
    }
}
