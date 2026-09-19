package de.kewl.boatspeedy.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Point
import de.kewl.boatspeedy.nav.LatLon
import de.kewl.boatspeedy.nav.flowMarks
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.Projection
import org.osmdroid.views.overlay.Overlay

/**
 * Zeichnet die Fließrichtung als offene Winkel `›` auf die Flüsse.
 *
 * Bewusst anders als die Pfeile der eigenen Spur, damit beides auf demselben Fluss nicht
 * zu verwechseln ist: offen statt gefüllt, blasses Wasserblau statt Spurfarbe, halb
 * durchsichtig, und **unter** Route und Spur eingehängt — die Strömung ist Hintergrund,
 * die eigene Fahrt ist die Hauptsache.
 *
 * Kein Marker je Winkel: bei einem langen Fluss wären das Hunderte Objekte, die osmdroid
 * einzeln verwaltet. Hier wird in einem Durchgang gezeichnet und nichts behalten.
 */
class FlowOverlay(density: Float) : Overlay() {

    /** Die Flüsse, jeder stromab geordnet. */
    var rivers: List<List<LatLon>> = emptyList()

    private val step = 110.0 * density
    private val arm = 6f * density

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x994FC3F7.toInt()   // Wasserblau, 60 % deckend
        style = Paint.Style.STROKE
        strokeWidth = 3f * density
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val path = Path()
    private val punkt = Point()

    override fun draw(c: Canvas, pj: Projection) {
        if (rivers.isEmpty()) return
        for (fluss in rivers) {
            val xs = DoubleArray(fluss.size)
            val ys = DoubleArray(fluss.size)
            for ((i, p) in fluss.withIndex()) {
                pj.toPixels(GeoPoint(p.lat, p.lon), punkt)
                xs[i] = punkt.x.toDouble()
                ys[i] = punkt.y.toDouble()
            }
            for (m in flowMarks(xs, ys, step)) {
                c.save()
                c.translate(m.x.toFloat(), m.y.toFloat())
                c.rotate(m.angleDeg.toFloat())
                // Die Spitze zeigt stromab, die Schenkel nach hinten.
                path.reset()
                path.moveTo(-arm, -arm)
                path.lineTo(0f, 0f)
                path.lineTo(-arm, arm)
                c.drawPath(path, paint)
                c.restore()
            }
        }
    }

    override fun draw(c: Canvas, osmv: MapView, shadow: Boolean) {
        if (!shadow) draw(c, osmv.projection)
    }
}
