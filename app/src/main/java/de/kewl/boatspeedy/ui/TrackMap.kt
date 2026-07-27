package de.kewl.boatspeedy.ui

import android.graphics.Color
import android.location.Location
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import de.kewl.boatspeedy.R
import de.kewl.boatspeedy.trip.SavedTrip
import de.kewl.boatspeedy.trip.TrackPoint
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.io.File

/**
 * Karte eines gespeicherten Tracks: Linie, dezente Richtungspfeile und – wenn
 * [bubbleText] gesetzt ist – eine **Sprechblase direkt am angetippten Punkt** mit den
 * dort formatierten Track-Daten.
 */
@Composable
fun TrackMap(
    trip: SavedTrip,
    interactive: Boolean,
    showArrows: Boolean,
    bubbleText: ((TrackPoint) -> String)?,
    color: Int = Color.parseColor("#1E88E5"),
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val geo = remember(trip.id) { trip.points.map { GeoPoint(it.lat, it.lon) } }
    val mapView = remember {
        Configuration.getInstance().apply {
            userAgentValue = context.packageName
            osmdroidBasePath = File(context.cacheDir, "osmdroid")
            osmdroidTileCache = File(osmdroidBasePath, "tiles")
        }
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(interactive)
            setTilesScaledToDpi(true)
            zoomController.setVisibility(
                if (interactive) CustomZoomButtonsController.Visibility.SHOW_AND_FADEOUT
                else CustomZoomButtonsController.Visibility.NEVER,
            )
        }
    }
    // Wiederverwendeter Marker + Sprechblase am angetippten Punkt.
    val bubbleMarker = remember(mapView) {
        Marker(mapView).apply {
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            icon = ContextCompat.getDrawable(context, R.drawable.ic_track_dot)
            infoWindow = TrackInfoWindow(mapView)
        }
    }

    fun showBubble(p: TrackPoint) {
        val text = bubbleText ?: return
        bubbleMarker.position = GeoPoint(p.lat, p.lon)
        bubbleMarker.title = text(p)
        if (!mapView.overlays.contains(bubbleMarker)) mapView.overlays.add(bubbleMarker)
        bubbleMarker.showInfoWindow()
        mapView.invalidate()
    }

    DisposableEffect(Unit) {
        mapView.onResume()
        onDispose { mapView.onPause(); mapView.onDetach() }
    }

    LaunchedEffect(trip.id) {
        mapView.overlays.clear()
        if (geo.size >= 2) {
            val line = Polyline(mapView).apply {
                setPoints(geo)
                outlinePaint.color = color
                outlinePaint.strokeWidth = 9f
            }
            if (bubbleText != null) {
                line.setOnClickListener { _, _, eventPos ->
                    nearestPoint(trip.points, eventPos)?.let { showBubble(it) }
                    true
                }
            }
            mapView.overlays.add(line)

            if (showArrows && trip.points.size >= 4) {
                val arrow = ContextCompat.getDrawable(context, R.drawable.ic_track_arrow)?.mutate()
                arrow?.setTint(color)
                val pts = trip.points
                val n = pts.size
                val step = (n / 12).coerceAtLeast(2)
                var i = step
                while (i < n - 1) {
                    // Kurs über ~25 m echten Track (nicht geglättet); zu verrauschte
                    // Stellen bekommen keinen Pfeil statt einem zufälligen.
                    val bearing = bearingAt(pts, i)
                    if (bearing != null) {
                        val a = pts[i]
                        val marker = Marker(mapView).apply {
                            position = GeoPoint(a.lat, a.lon)
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                            icon = arrow
                            rotation = bearing
                            setInfoWindow(null)
                            if (bubbleText != null) setOnMarkerClickListener { _, _ -> showBubble(a); true }
                        }
                        mapView.overlays.add(marker)
                    }
                    i += step
                }
            }

            mapView.post {
                runCatching { mapView.zoomToBoundingBox(BoundingBox.fromGeoPointsSafe(geo), false, 48) }
            }
        } else if (geo.size == 1) {
            mapView.controller.setZoom(16.0)
            mapView.controller.setCenter(geo.first())
        }
        mapView.invalidate()
    }

    AndroidView(factory = { mapView }, modifier = modifier)
}

// ~25 m Basislinie (12 m je Seite); darunter zeigt die Positions-Differenz nur Rauschen.
private const val ARROW_HALF_BASELINE_M = 12.0
private const val ARROW_MIN_DISPLACEMENT_M = 10f

private fun segDist(a: TrackPoint, b: TrackPoint): Double {
    val r = FloatArray(1)
    Location.distanceBetween(a.lat, a.lon, b.lat, b.lon, r)
    return r[0].toDouble()
}

/** Kurs am Punkt i über eine Mindeststrecke im echten Track; null, wenn zu kurz/verrauscht. */
private fun bearingAt(points: List<TrackPoint>, i: Int): Float? {
    var j = i
    var back = 0.0
    while (j > 0 && back < ARROW_HALF_BASELINE_M) { back += segDist(points[j], points[j - 1]); j-- }
    var k = i
    var fwd = 0.0
    val last = points.size - 1
    while (k < last && fwd < ARROW_HALF_BASELINE_M) { fwd += segDist(points[k], points[k + 1]); k++ }
    val res = FloatArray(2)
    Location.distanceBetween(points[j].lat, points[j].lon, points[k].lat, points[k].lon, res)
    return if (res[0] < ARROW_MIN_DISPLACEMENT_M) null else res[1]
}

private fun nearestPoint(points: List<TrackPoint>, at: GeoPoint): TrackPoint? {
    var best: TrackPoint? = null
    var bestD = Double.MAX_VALUE
    for (p in points) {
        val dLat = p.lat - at.latitude
        val dLon = p.lon - at.longitude
        val d = dLat * dLat + dLon * dLon
        if (d < bestD) { bestD = d; best = p }
    }
    return best
}
