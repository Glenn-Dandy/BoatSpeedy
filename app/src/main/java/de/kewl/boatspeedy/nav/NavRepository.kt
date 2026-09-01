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

    /**
     * Wie nah man dem Ziel kommen muss, damit es als erreicht gilt. Zehn Meter, weil die
     * GPS-Genauigkeit im Betrieb bei drei bis zehn Metern liegt: bei einem engeren Kreis
     * bliebe die Route stehen, obwohl man längst da ist.
     */
    const val ARRIVE_M = 10.0

    private val _target = MutableStateFlow<NavTarget?>(null)
    val target: StateFlow<NavTarget?> = _target.asStateFlow()

    /** Zählt hoch, sobald ein Ziel erreicht wurde – die Oberfläche meldet es dann einmal. */
    private val _arrived = MutableStateFlow(0)
    val arrived: StateFlow<Int> = _arrived.asStateFlow()

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
        _arrived.value = _arrived.value + 1
        return true
    }
}
