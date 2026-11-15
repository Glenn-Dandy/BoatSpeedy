package de.kewl.boatspeedy.ui

import android.graphics.Point
import android.view.MotionEvent
import de.kewl.boatspeedy.nav.SeamarkInfo
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay

/**
 * Nimmt den Tipp auf ein Seezeichen entgegen. Gezeichnet wird nichts, das Symbol kommt
 * aus den Kacheln von OpenSeaMap.
 *
 * Vorher hatte jedes Zeichen einen unsichtbaren Marker von 36 dp. Getroffen hat dann
 * nicht das nächste Zeichen, sondern das zuletzt eingehängte, dessen Fläche den Finger
 * berührte. In Häfen und an Schleusen stehen Dalben, Poller und Schilder dicht an dicht,
 * und das Schild, auf das man zielte, war kaum zu treffen. Hier gewinnt das nächste
 * Zeichen im Umkreis, Festmacher und Pfähle nur, wenn sonst nichts in der Nähe ist.
 */
class SeamarkTapOverlay(density: Float) : Overlay() {

    var items: List<Pair<GeoPoint, SeamarkInfo>> = emptyList()
    var types: List<String> = emptyList()
    var onTap: ((SeamarkInfo) -> Unit)? = null

    private val radiusPx = TAP_RADIUS_DP * density
    private val pt = Point()

    override fun onSingleTapConfirmed(e: MotionEvent, mapView: MapView): Boolean {
        val cb = onTap ?: return false
        val pj = mapView.projection
        var best = -1
        var bestScore = Double.MAX_VALUE
        items.forEachIndexed { i, (geo, _) ->
            pj.toPixels(geo, pt)
            val dx = (pt.x - e.x).toDouble()
            val dy = (pt.y - e.y).toDouble()
            val d = Math.hypot(dx, dy)
            if (d > radiusPx) return@forEachIndexed
            val score = if (types.getOrNull(i) in BACKGROUND) d + radiusPx else d
            if (score < bestScore) {
                bestScore = score
                best = i
            }
        }
        if (best < 0) return false
        cb(items[best].second)
        return true
    }

    companion object {
        /** Ein Finger trifft auf etwa einen Zentimeter genau. */
        const val TAP_RADIUS_DP = 28f

        /** Zeichen, die meist dicht neben dem eigentlich gemeinten stehen. */
        private val BACKGROUND = setOf("mooring", "pile", "distance_mark", "rock", "seabed_area")
    }
}
