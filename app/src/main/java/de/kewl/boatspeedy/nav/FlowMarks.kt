package de.kewl.boatspeedy.nav

import kotlin.math.atan2
import kotlin.math.hypot

/** Wo ein Strömungswinkel sitzt und wohin er zeigt, in Bildschirmpunkten. */
data class FlowMark(val x: Double, val y: Double, val angleDeg: Double)

/**
 * Setzt die Winkel für die Fließrichtung entlang eines Flusses, **in gleichen Abständen auf
 * dem Bildschirm** statt in Metern.
 *
 * In Metern gemessen stünden sie beim Hineinzoomen weit auseinander und beim Herauszoomen
 * als Brei aufeinander. So bleibt das Muster bei jeder Zoomstufe gleich ruhig — das ist der
 * Unterschied zur eigenen Spur, deren Pfeile an Messpunkten hängen.
 *
 * Reine Rechnung ohne Android, damit sie prüfbar bleibt: die Zeichnung auf der Karte
 * wandelt die Koordinaten vorher in Bildschirmpunkte um und malt hinterher nur noch.
 *
 * @param xs, ys der Fluss in Bildschirmpunkten, **stromab** geordnet.
 * @param step Abstand zwischen zwei Winkeln.
 * @param offset wie weit vom Anfang der erste sitzt — halb so weit wie [step], sonst
 *   klebte an jedem Wegende ein Winkel, und wo zwei Wege aneinanderstoßen, zwei.
 * @return Winkel in Grad, 0 = nach rechts, im Uhrzeigersinn wie auf dem Bildschirm.
 */
fun flowMarks(
    xs: DoubleArray,
    ys: DoubleArray,
    step: Double,
    offset: Double = step / 2,
): List<FlowMark> {
    require(xs.size == ys.size)
    if (xs.size < 2 || step <= 0.0) return emptyList()
    val out = ArrayList<FlowMark>()
    var bisZumNaechsten = offset
    for (i in 0 until xs.size - 1) {
        val dx = xs[i + 1] - xs[i]
        val dy = ys[i + 1] - ys[i]
        val len = hypot(dx, dy)
        if (len <= 0.0) continue
        val winkel = Math.toDegrees(atan2(dy, dx))
        var t = bisZumNaechsten
        while (t <= len) {
            out.add(FlowMark(xs[i] + dx * t / len, ys[i] + dy * t / len, winkel))
            t += step
        }
        bisZumNaechsten = t - len
    }
    return out
}
