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

    /**
     * Unterhalb dieser Fahrt liefert das GPS keinen brauchbaren Kurs mehr, sondern
     * Rauschen — der Pfeil würde im Stand kreiseln.
     */
    const val MIN_COURSE_SPEED_MS = 0.5f

    /** Zuletzt bekannter Kurs über Grund und ob er noch aktuell ist. */
    data class Course(val deg: Float, val stale: Boolean)

    private val _course = MutableStateFlow<Course?>(null)
    val course: StateFlow<Course?> = _course.asStateFlow()

    /** Kurs einspeisen. Ohne Fahrt bleibt der letzte stehen, nur als „veraltet" markiert. */
    fun onCourse(bearingDeg: Float?, speedMs: Float?) {
        val moving = speedMs != null && speedMs >= MIN_COURSE_SPEED_MS
        _course.value = when {
            moving && bearingDeg != null -> Course(bearingDeg, stale = false)
            else -> _course.value?.copy(stale = true)
        }
    }

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
    /**
     * Aktuelle Position einspeisen. Räumt das Ziel ab, sobald es erreicht ist, und gibt
     * dann true zurück.
     *
     * Nebenbei wird der Weg **nachgeführt**: er beginnt immer an der aktuellen Position,
     * Zurückgelegtes fällt weg, und die Entfernung schrumpft entsprechend — beim Näherkommen
     * wie beim Entfernen. Vorher stand beides starr, wie es einmal berechnet worden war.
     */
    fun onLocation(lat: Double, lon: Double): Boolean {
        val t = _target.value ?: return false
        val here = LatLon(lat, lon)
        if (distanceM(here, t.target) <= ARRIVE_M) {
            _target.value = null
            _arrived.value = _arrived.value + 1
            return true
        }
        _target.value = when (t.mode) {
            // Luftlinie hängt am Boot: sie beginnt immer dort, wo man gerade ist.
            NavMode.LINE -> t.copy(
                path = listOf(here, t.target),
                distanceM = distanceM(here, t.target),
            )
            // Die Route bleibt stehen, wie sie berechnet wurde – nur die Reststrecke
            // schrumpft, während man ihr folgt.
            NavMode.ROUTE -> t.copy(distanceM = remainingAlong(t.path, here))
        }
        return false
    }
}
