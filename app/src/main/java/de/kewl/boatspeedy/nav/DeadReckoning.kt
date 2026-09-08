package de.kewl.boatspeedy.nav

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Koppelnavigation für die **Anzeige** des Positionsmarkers.
 *
 * Bisher wurde zwischen zwei GPS-Meldungen interpoliert: der Marker lief auf die zuletzt
 * gemessene Stelle zu, an der man längst vorbei war, und setzte danach neu an. Das ist
 * Nachziehen, kein Gleiten.
 *
 * Hier wird stattdessen fortlaufend gerechnet, wo das Boot **jetzt** sein müsste — aus
 * Geschwindigkeit und Kurs. Trifft eine Messung ein, wird die Schätzung sanft
 * nachgezogen, statt neu anzusetzen. So bewegt sich der Marker ohne Unterbrechung und
 * hängt nicht eine Sekunde hinterher. Genau das machen Navigationsgeräte.
 *
 * **Nur für die Anzeige.** Aufgezeichnete Fahrt, Strecke und Verbrauch rechnen weiter mit
 * den rohen Messungen — eine geschätzte Position darf niemals in die Fahrtdaten geraten,
 * sonst wären Kilometer und Amperestunden geraten statt gemessen.
 */
class DeadReckoner {

    var lat: Double? = null
        private set
    var lon: Double? = null
        private set
    var headingDeg: Float = 0f
        private set

    /** Noch nicht eingerechneter Unterschied zwischen Schätzung und letzter Messung. */
    private var errLat = 0.0
    private var errLon = 0.0

    private var speedMs = 0f
    private var targetHeading = 0f
    private var lastFixMs = 0L

    /** Ob die Schätzung gerade etwas zu tun hat – sonst kann der Bildlauf schlafen. */
    fun isBusy(nowMs: Long): Boolean {
        if (lat == null) return false
        if (errorMeters() > SETTLED_M) return true
        if (abs(shortestTurn(headingDeg, targetHeading)) > SETTLED_DEG) return true
        return moving(nowMs)
    }

    private fun moving(nowMs: Long) = speedMs >= MIN_SPEED_MS && nowMs - lastFixMs <= STALE_MS

    private fun errorMeters(): Double =
        hypot(errLat * M_PER_DEG_LAT, errLon * M_PER_DEG_LAT * cos(Math.toRadians(lat ?: 0.0)))

    /** Eine neue GPS-Messung einarbeiten. */
    fun onFix(fixLat: Double, fixLon: Double, courseDeg: Float?, speed: Float?, nowMs: Long) {
        speedMs = speed ?: 0f
        lastFixMs = nowMs
        courseDeg?.let { targetHeading = it }

        val curLat = lat
        val curLon = lon
        if (curLat == null || curLon == null) {
            lat = fixLat
            lon = fixLon
            headingDeg = targetHeading
            errLat = 0.0
            errLon = 0.0
            return
        }

        errLat = fixLat - curLat
        errLon = fixLon - curLon
        // Ein Ausreißer oder ein wiedergefundener Fix liegt weit weg. Den langsam
        // einzublenden hieße, den Marker sekundenlang quer über die Karte zu schieben —
        // dann lieber sofort umsetzen und ehrlich springen.
        if (errorMeters() > SNAP_M) {
            lat = fixLat
            lon = fixLon
            headingDeg = targetHeading
            errLat = 0.0
            errLon = 0.0
        }
    }

    /**
     * Einen Zeitschritt weiterrechnen. [dtSec] ist die Zeit seit dem letzten Aufruf.
     * Liefert `true`, wenn sich etwas bewegt hat.
     */
    fun advance(dtSec: Double, nowMs: Long): Boolean {
        val curLat = lat ?: return false
        val curLon = lon ?: return false
        if (dtSec <= 0.0) return false
        val dt = dtSec.coerceAtMost(MAX_STEP_S)

        var newLat = curLat
        var newLon = curLon

        // Ohne frische Messung wird nicht mehr vorausgerechnet: die Schätzung liefe sonst
        // ins Blaue davon. Die offene Korrektur darf trotzdem auslaufen.
        if (moving(nowMs)) {
            val rad = Math.toRadians(headingDeg.toDouble())
            val metres = speedMs * dt
            newLat += metres * cos(rad) / M_PER_DEG_LAT
            val lonScale = cos(Math.toRadians(newLat)).coerceAtLeast(0.01)
            newLon += metres * sin(rad) / (M_PER_DEG_LAT * lonScale)
        }

        // Rest des Unterschieds anteilig einrechnen – exponentiell, damit es keinen Knick
        // gibt, wenn während der Korrektur die nächste Messung eintrifft.
        val tau = if (moving(nowMs)) TAU_MOVING_S else TAU_STOPPED_S
        val k = 1.0 - exp(-dt / tau)
        newLat += errLat * k
        newLon += errLon * k
        errLat -= errLat * k
        errLon -= errLon * k

        // Der Kurs wird **weich** herangeführt, nicht mit fester Drehrate.
        //
        // Vorher war das ein reiner Geschwindigkeitsbegrenzer: Bei einer neuen Messung
        // drehte die Schätzung mit voller Rate los und blieb schlagartig stehen, sobald sie
        // den Wert erreicht hatte. Am kleinen Pfeil fiel das kaum auf; seit sich die ganze
        // Karte danach richtet, ist genau das das Ruckeln — drehen, stehen, drehen. Ein
        // Zeitglied wie bei der Position läuft dagegen an und wieder aus. Der Deckel bleibt
        // trotzdem: Er fängt ab, wenn das GPS den Kurs auf einmal um hundert Grad umwirft.
        val turn = shortestTurn(headingDeg, targetHeading)
        val kHeading = (1.0 - exp(-dt / TAU_HEADING_S)).toFloat()
        val maxTurn = (MAX_TURN_DEG_PER_S * dt).toFloat()
        headingDeg = normalize(headingDeg + (turn * kHeading).coerceIn(-maxTurn, maxTurn))

        val moved = abs(newLat - curLat) > 1e-9 || abs(newLon - curLon) > 1e-9
        lat = newLat
        lon = newLon
        return moved || abs(turn) > SETTLED_DEG
    }

    fun reset() {
        lat = null
        lon = null
        errLat = 0.0
        errLon = 0.0
        speedMs = 0f
    }

    companion object {
        /** Ein Breitengrad in Metern – für die kurzen Strecken hier genau genug. */
        const val M_PER_DEG_LAT = 111_320.0

        /** Darunter gilt das Boot als stehend; vorausgerechnet wird dann nicht. */
        const val MIN_SPEED_MS = 0.5f

        /** So lange ohne Messung wird noch weitergerechnet, danach eingefroren. */
        const val STALE_MS = 3_000L

        /** Weiter entfernte Messungen werden gesetzt statt eingeblendet. */
        const val SNAP_M = 50.0

        /** Zeitkonstante der Korrektur in Fahrt und im Stand. */
        const val TAU_MOVING_S = 0.6
        const val TAU_STOPPED_S = 2.0

        /**
         * Zeitkonstante der Drehung. Etwas träger als die Korrektur der Position: Der Kurs
         * aus dem GPS zappelt, und seit sich die ganze Karte danach richtet, wird jedes
         * Zappeln zur Bewegung des ganzen Bildes.
         */
        const val TAU_HEADING_S = 0.7

        /** Wie schnell der Marker höchstens dreht — der Deckel über dem Zeitglied. */
        const val MAX_TURN_DEG_PER_S = 120.0

        /** Darunter gilt es als angekommen bzw. ausgerichtet. */
        const val SETTLED_M = 0.2
        const val SETTLED_DEG = 0.2f

        /**
         * Größter Zeitschritt. Kommt die App aus dem Hintergrund, liegen zwischen zwei
         * Bildern Minuten — ohne Deckel schösse die Schätzung kilometerweit davon.
         */
        const val MAX_STEP_S = 0.25
    }
}

/** Kürzester Drehwinkel von [from] nach [to], im Bereich −180…+180. */
fun shortestTurn(from: Float, to: Float): Float {
    var d = (to - from) % 360f
    if (d > 180f) d -= 360f
    if (d < -180f) d += 360f
    return d
}

fun normalize(deg: Float): Float = ((deg % 360f) + 360f) % 360f

/**
 * Welche Peilung der Positionsmarker bekommen muss, damit er auf dem Schirm in
 * Fahrtrichtung zeigt — bei gedrehter Karte ebenso wie bei „Norden oben".
 *
 * osmdroid dreht die Zeichenfläche um die Ausrichtung der Karte und rechnet sie beim
 * Marker wieder heraus (`-Ausrichtung - Peilung`), sodass unterm Strich `-Peilung` auf
 * dem Schirm steht — die Kartendrehung fällt heraus. Mit `-Kurs` stimmte das bei
 * „Norden oben"; sobald die Karte der Fahrt folgte, zeigte der Pfeil weiter stur in die
 * Himmelsrichtung, während sich alles darunter drehte. Er schien in einer beliebigen
 * Richtung festzuhängen.
 *
 * Gewollt ist ein Schirmwinkel von `Kurs + Kartendrehung`: bei „Norden oben" der Kurs,
 * bei „Fahrtrichtung oben" (Drehung `-Kurs`) senkrecht nach oben.
 *
 * @param headingDeg Kurs über Grund, rechtweisend.
 * @param mapRotationDeg wie die Karte steht — 0 bei „Norden oben", sonst `-headingDeg`.
 */
fun markerBearingDeg(headingDeg: Float, mapRotationDeg: Float): Float =
    normalize(-(headingDeg + mapRotationDeg))
