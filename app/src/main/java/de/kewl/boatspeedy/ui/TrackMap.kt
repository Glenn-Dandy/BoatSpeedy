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
                val n = trip.points.size
                val step = (n / 12).coerceAtLeast(2)
                var i = step
                while (i < n - 1) {
                    val a = trip.points[i]
                    // Richtung über ein kleines Fenster mitteln → weniger GPS-Jitter.
                    val from = trip.points[(i - 2).coerceAtLeast(0)]
                    val to = trip.points[(i + 2).coerceAtMost(n - 1)]
                    val res = FloatArray(2)
                    Location.distanceBetween(from.lat, from.lon, to.lat, to.lon, res)
                    val marker = Marker(mapView).apply {
                        position = GeoPoint(a.lat, a.lon)
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        icon = arrow
                        rotation = res[1]
                        setInfoWindow(null)
                        if (bubbleText != null) setOnMarkerClickListener { _, _ -> showBubble(a); true }
                    }
                    mapView.overlays.add(marker)
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
