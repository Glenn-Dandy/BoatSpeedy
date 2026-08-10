package de.kewl.boatspeedy.ui

import android.graphics.Color
import android.view.MotionEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import de.kewl.boatspeedy.R
import de.kewl.boatspeedy.trip.TrackPoint
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.MapTileProviderBasic
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.tileprovider.util.SimpleInvalidationHandler
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.MapTileIndex
import kotlinx.coroutines.delay
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.util.BoundingBox
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.TilesOverlay
import java.io.File

/**
 * OpenStreetMap-Karte (osmdroid) mit Live-Track + Positionsmarker, folgt der aktuellen
 * Position. [interactive]=false → schwenkfrei (für die Mini-Kachel; Tap/​Scroll regelt
 * ein Overlay in der aufrufenden UI).
 */
@Composable
fun OsmMap(
    points: List<TrackPoint>,
    currentLat: Double?,
    currentLon: Double?,
    interactive: Boolean,
    modifier: Modifier = Modifier,
    zoom: Double = 16.0,
    follow: Boolean = true,
    onUserPan: () -> Unit = {},
    bubbleText: ((TrackPoint) -> String)? = null,
    showRadar: Boolean = false,
    radarTimes: List<String> = emptyList(),
    radarFrameIndex: Int = 0,
    showLightning: Boolean = false,
) {
    val context = LocalContext.current
    val pointsState = rememberUpdatedState(points)
    val onPan = rememberUpdatedState(onUserPan)
    val mapView = remember {
        Configuration.getInstance().apply {
            userAgentValue = context.packageName
            osmdroidBasePath = File(context.cacheDir, "osmdroid")
            osmdroidTileCache = File(osmdroidBasePath, "tiles")
            // Die Radar-Frames brauchen viele Kacheln; mit der kleinen Standard-Warteschlange
            // werden Anfragen verworfen (Frames bleiben leer / laden erst nach mehreren Runden).
            tileDownloadThreads = 6
            tileDownloadMaxQueueSize = 400
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
    var centered by remember { mutableStateOf(false) }
    val line = remember(mapView) {
        Polyline(mapView).apply {
            outlinePaint.color = Color.parseColor("#1E88E5")
            outlinePaint.strokeWidth = 9f
        }
    }
    val marker = remember(mapView) {
        Marker(mapView).apply { setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM) }
    }
    val bubbleMarker = remember(mapView) {
        Marker(mapView).apply {
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            icon = ContextCompat.getDrawable(context, R.drawable.ic_track_dot)
            infoWindow = TrackInfoWindow(mapView)
        }
    }
    // Ein Overlay je Radar-Frame – jedes mit EIGENEM Cache (osmdroid cacht nach z/x/y,
    // nicht nach Zeit; nur so zeigt der Frame-Wechsel wirklich neue Daten).
    val radarLayers = remember(radarTimes) {
        radarTimes.map { t ->
            val p = MapTileProviderBasic(context).apply {
                setTileSource(DwdWmsTileSource(DWD_RADAR_LAYER, t))
                // Fertige Downloads sollen die Karte neu zeichnen (sonst erst beim Antippen).
                setTileRequestCompleteHandler(SimpleInvalidationHandler(mapView))
            }
            val ov = TilesOverlay(p, context).apply {
                loadingBackgroundColor = android.graphics.Color.TRANSPARENT
                isEnabled = false
            }
            ov to p
        }
    }
    val lightningProvider = remember(mapView) {
        MapTileProviderBasic(context).apply { setTileRequestCompleteHandler(SimpleInvalidationHandler(mapView)) }
    }
    val lightningOverlay = remember(mapView) { TilesOverlay(lightningProvider, context).apply { loadingBackgroundColor = android.graphics.Color.TRANSPARENT } }

    DisposableEffect(Unit) {
        mapView.onResume()
        onDispose {
            mapView.onPause()
            mapView.onDetach()
            radarLayers.forEach { (_, p) -> p.detach() }
            lightningProvider.detach()
        }
    }

    // Alle Frame-Overlays unten einhängen/aushängen (nur einer ist jeweils sichtbar).
    DisposableEffect(showRadar, radarLayers) {
        radarLayers.forEach { (ov, _) ->
            if (showRadar) { if (!mapView.overlays.contains(ov)) mapView.overlays.add(0, ov) }
            else mapView.overlays.remove(ov)
        }
        mapView.invalidate()
        onDispose { }
    }

    // Nur den aktuellen Frame sichtbar schalten.
    LaunchedEffect(showRadar, radarFrameIndex, radarLayers) {
        radarLayers.forEachIndexed { i, (ov, _) -> ov.isEnabled = showRadar && i == radarFrameIndex }
        mapView.invalidate()
    }

    // Alle Frames für den sichtbaren Bereich im Hintergrund vorladen (Kachel-Cache wärmen),
    // damit die Schleife beim Drücken von „Play" sofort flüssig läuft.
    // Frames NACHEINANDER vorladen – der sichtbare zuerst. Alles auf einmal anzufordern
    // sprengt die Download-Warteschlange, dann werden Kacheln verworfen und der Frame
    // („jetzt") bleibt leer bzw. füllt sich erst nach mehreren Durchläufen.
    LaunchedEffect(showRadar, radarLayers) {
        if (!showRadar || radarLayers.isEmpty()) return@LaunchedEffect
        delay(400) // Karte zentrieren/zoomen lassen
        val z = mapView.zoomLevelDouble.toInt().coerceIn(3, 14)
        val n = 1 shl z
        val bb = mapView.boundingBox
        fun lon2x(lon: Double) = (((lon + 180.0) / 360.0) * n).toInt()
        fun lat2y(lat: Double): Int {
            val r = Math.toRadians(lat)
            return (((1.0 - Math.log(Math.tan(r) + 1.0 / Math.cos(r)) / Math.PI) / 2.0) * n).toInt()
        }
        val x0 = (lon2x(bb.lonWest) - 1).coerceIn(0, n - 1)
        val x1 = (lon2x(bb.lonEast) + 1).coerceIn(0, n - 1)
        val y0 = (lat2y(bb.latNorth) - 1).coerceIn(0, n - 1)
        val y1 = (lat2y(bb.latSouth) + 1).coerceIn(0, n - 1)

        fun request(index: Int) {
            val p = radarLayers.getOrNull(index)?.second ?: return
            for (x in x0..x1) for (y in y0..y1) {
                runCatching { p.getMapTile(MapTileIndex.getTileIndex(z, x, y)) }
            }
        }

        // 1) sichtbarer Frame sofort, damit „jetzt" gleich erscheint
        val first = radarFrameIndex.coerceIn(0, radarLayers.lastIndex)
        request(first)
        mapView.invalidate()
        delay(900)
        // 2) die übrigen Frames der Reihe nach
        radarLayers.indices.filter { it != first }.forEach { i ->
            request(i)
            delay(450)
        }
    }

    // Blitze (Ist-Zeit), über dem Regen, unter den Markern.
    DisposableEffect(showLightning, radarLayers) {
        if (showLightning) {
            lightningProvider.setTileSource(DwdWmsTileSource(DWD_LIGHTNING_LAYER, null))
            if (!mapView.overlays.contains(lightningOverlay)) {
                val radarCount = radarLayers.count { mapView.overlays.contains(it.first) }
                mapView.overlays.add(radarCount, lightningOverlay)
            }
        } else {
            mapView.overlays.remove(lightningOverlay)
        }
        mapView.invalidate()
        onDispose { }
    }

    // Nutzer-Schwenk erkennen (→ Folgen aussetzen).
    LaunchedEffect(interactive) {
        if (interactive) {
            mapView.setOnTouchListener { _, ev ->
                if (ev.actionMasked == MotionEvent.ACTION_MOVE) onPan.value()
                false
            }
        }
    }

    // Track antippen → gleiche Sprechblase wie bei den Trips.
    LaunchedEffect(bubbleText) {
        if (bubbleText != null) {
            line.setOnClickListener { _, _, eventPos ->
                nearestPoint(pointsState.value, eventPos)?.let { p ->
                    bubbleMarker.position = GeoPoint(p.lat, p.lon)
                    bubbleMarker.title = bubbleText(p)
                    if (!mapView.overlays.contains(bubbleMarker)) mapView.overlays.add(bubbleMarker)
                    bubbleMarker.showInfoWindow()
                    mapView.invalidate()
                }
                true
            }
        }
    }

    LaunchedEffect(points, currentLat, currentLon, follow) {
        val geo = points.map { GeoPoint(it.lat, it.lon) }
        line.setPoints(geo)
        if (geo.size >= 2) {
            if (!mapView.overlays.contains(line)) mapView.overlays.add(line)
        } else {
            mapView.overlays.remove(line)
        }

        if (currentLat != null && currentLon != null) {
            marker.position = GeoPoint(currentLat, currentLon)
            if (!mapView.overlays.contains(marker)) mapView.overlays.add(marker)
        }

        val target = when {
            currentLat != null && currentLon != null -> GeoPoint(currentLat, currentLon)
            geo.isNotEmpty() -> geo.last()
            else -> null
        }
        if (target != null) {
            if (!centered) {
                mapView.controller.setZoom(zoom)
                mapView.controller.setCenter(target)
                centered = true
            } else if (follow) {
                mapView.controller.setCenter(target) // ohne Animation → kein Dauer-„Gleiten"
            }
        }
        mapView.invalidate()
    }

    AndroidView(factory = { mapView }, modifier = modifier)
}

/** Karte für die Anker-Wache: Ankerpunkt, Radius-Kreis und aktuelle Bootsposition. */
@Composable
fun AnchorMap(
    anchorLat: Double,
    anchorLon: Double,
    radiusM: Int,
    boatLat: Double?,
    boatLon: Double?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val mapView = remember {
        Configuration.getInstance().apply {
            userAgentValue = context.packageName
            osmdroidBasePath = File(context.cacheDir, "osmdroid")
            osmdroidTileCache = File(osmdroidBasePath, "tiles")
        }
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            setTilesScaledToDpi(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
        }
    }
    var centered by remember { mutableStateOf(false) }
    val boatMarker = remember(mapView) {
        Marker(mapView).apply { setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM) }
    }

    DisposableEffect(Unit) {
        mapView.onResume()
        onDispose { mapView.onPause(); mapView.onDetach() }
    }

    LaunchedEffect(anchorLat, anchorLon, radiusM, boatLat, boatLon) {
        mapView.overlays.clear()
        val center = GeoPoint(anchorLat, anchorLon)
        val circle = Polygon(mapView).apply {
            points = Polygon.pointsAsCircle(center, radiusM.toDouble())
            fillPaint.color = 0x221E88E5
            outlinePaint.color = Color.parseColor("#1E88E5")
            outlinePaint.strokeWidth = 4f
        }
        mapView.overlays.add(circle)
        mapView.overlays.add(Marker(mapView).apply { position = center })
        if (boatLat != null && boatLon != null) {
            boatMarker.position = GeoPoint(boatLat, boatLon)
            mapView.overlays.add(boatMarker)
        }
        if (!centered) {
            mapView.post {
                runCatching { mapView.zoomToBoundingBox(BoundingBox.fromGeoPointsSafe(circle.points), false, 48) }
            }
            centered = true
        }
        mapView.invalidate()
    }

    AndroidView(factory = { mapView }, modifier = modifier)
}
