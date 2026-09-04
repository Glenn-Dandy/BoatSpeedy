package de.kewl.boatspeedy.ui

import android.graphics.Color
import android.view.MotionEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import de.kewl.boatspeedy.R
import de.kewl.boatspeedy.nav.LatLon
import de.kewl.boatspeedy.trip.TrackPoint
import org.osmdroid.config.Configuration
import androidx.compose.runtime.withFrameNanos
import de.kewl.boatspeedy.nav.DeadReckoner
import org.osmdroid.tileprovider.MapTileProviderBasic
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.views.overlay.TilesOverlay
import org.osmdroid.util.GeoPoint
import kotlinx.coroutines.delay
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.util.BoundingBox
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.Polyline
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
    /** Langer Druck auf die Karte – für das Setzen eines Ziels. */
    onLongPress: ((Double, Double) -> Unit)? = null,
    /** Weg zum Ziel (Luftlinie oder Route); leer = kein Ziel gesetzt. */
    navPath: List<LatLon> = emptyList(),
    /** Der Abschnitt entlang des Fahrwassers; davor und danach wird frei gefahren. */
    navWaterPath: List<LatLon> = emptyList(),
    /** Kurs über Grund in Grad; dreht den Positionsmarker in Fahrtrichtung. */
    courseDeg: Float? = null,
    /** Fahrt über Grund in m/s – damit rechnet die Koppelnavigation zwischen den Fixes. */
    speedMs: Float? = null,
    /**
     * Zähler zum einmaligen Zurückspringen auf die eigene Position. Gedacht für Karten,
     * die der Position **nicht** folgen: dort schiebt man weit weg und findet sonst nicht
     * zurück. Jede Erhöhung mittet einmal ein, ohne das Folgen einzuschalten.
     */
    recenterKey: Int = 0,
    /** Schleusen und Wehre auf der Route. */
    obstacles: List<de.kewl.boatspeedy.nav.Obstacle> = emptyList(),
    /** Tonnen, Baken und Hinweiszeichen von OpenSeaMap einblenden. */
    showSeamarks: Boolean = false,
    /** Geschwindigkeitszeichen mit ihrem Wert; die Kacheln zeigen nur das leere Schild. */
    speedSigns: List<de.kewl.boatspeedy.nav.SpeedSign> = emptyList(),
    /**
     * Meldet den sichtbaren Ausschnitt samt Zoomstufe — aber nur, wenn er sich wirklich
     * geändert hat. Bei jedem Durchlauf zu melden würde den ganzen Bildschirm im
     * Sekundentakt neu zeichnen lassen, ohne dass sich etwas bewegt hat.
     */
    onViewport: ((org.osmdroid.util.BoundingBox, Double) -> Unit)? = null,
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
    /**
     * OpenSeaMap liefert die Seezeichen als fertige, durchsichtige Kachelebene — eigene
     * Symbole zu zeichnen wäre viel Arbeit für ein schlechteres Ergebnis. Die Kacheln
     * stehen unter CC-BY-SA, der Hinweis dazu steht in den Einstellungen.
     */
    val seamarkOverlay = remember(mapView) {
        val source = XYTileSource(
            "OpenSeaMap", 3, 18, 256, ".png",
            arrayOf("https://tiles.openseamap.org/seamark/"),
            "© OpenSeaMap (CC-BY-SA)",
        )
        TilesOverlay(MapTileProviderBasic(context, source), context).apply {
            // Ohne das legt osmdroid graue Platzhalter über die Grundkarte.
            loadingBackgroundColor = Color.TRANSPARENT
            loadingLineColor = Color.TRANSPARENT
        }
    }

    var centered by remember { mutableStateOf(false) }
    val line = remember(mapView) {
        Polyline(mapView).apply {
            outlinePaint.color = Color.parseColor("#1E88E5")
            outlinePaint.strokeWidth = 9f
        }
    }
    // Richtungspfeil statt Stecknadel: er dreht sich in Fahrtrichtung, also muss er um
    // seinen Mittelpunkt hängen – eine Nadel mit Spitze unten würde beim Drehen wandern.
    val marker = remember(mapView) {
        Marker(mapView).apply {
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            icon = ContextCompat.getDrawable(context, R.drawable.ic_nav_arrow)
        }
    }
    // Zwei Linien, weil zwei verschiedene Dinge gemeint sind: gestrichelt, wo man selbst
    // navigiert (Anfahrt und Auslauf), durchgezogen entlang des Fahrwassers.
    val navLine = remember(mapView) {
        Polyline(mapView).apply {
            outlinePaint.color = Color.parseColor("#FF6D00")
            outlinePaint.strokeWidth = 8f
            outlinePaint.pathEffect = android.graphics.DashPathEffect(floatArrayOf(18f, 12f), 0f)
        }
    }
    val navWaterLine = remember(mapView) {
        Polyline(mapView).apply {
            outlinePaint.color = Color.parseColor("#FF6D00")
            outlinePaint.strokeWidth = 9f
        }
    }
    // Zielfahne; der Fuß der Stange sitzt auf dem Zielpunkt.
    val navMarker = remember(mapView) {
        Marker(mapView).apply {
            setAnchor(Marker.ANCHOR_LEFT, Marker.ANCHOR_BOTTOM)
            icon = ContextCompat.getDrawable(context, R.drawable.ic_dest_flag)
        }
    }
    val bubbleMarker = remember(mapView) {
        Marker(mapView).apply {
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            icon = ContextCompat.getDrawable(context, R.drawable.ic_track_dot)
            infoWindow = TrackInfoWindow(mapView)
        }
    }
    // Ein Overlay für den Regen, eines für die Blitze – jeweils EIN Bild für den
    // ganzen Ausschnitt statt zwanzig Kacheln (siehe RadarImage.kt).
    val radarOverlay = remember(mapView) { RadarImageOverlay() }
    val lightningOverlay = remember(mapView) { RadarImageOverlay() }

    // PNG-Bytes je Frame, immer für das feste Gebiet. Dekodiert wird nur, was gezeigt
    // wird. Der Ausschnitt spielt hier keine Rolle mehr – deshalb löst Zoomen und
    // Schwenken auch keinen Abruf mehr aus.
    val framePngs = remember(radarTimes) { mutableStateMapOf<Int, ByteArray>() }
    var lightningPng by remember { mutableStateOf<ByteArray?>(null) }
    // Zähler für das *Darstellungsfenster*. Steigt, wenn sich der Blick so weit geändert
    // hat, dass neu gerechnet werden soll — ohne Netzverkehr.
    var windowEpoch by remember { mutableIntStateOf(0) }
    var renderWindow by remember { mutableStateOf<MercatorBox?>(null) }
    // Fertig gerechnete Frames für das aktuelle Fenster, mit der Fläche, auf der sie
    // sitzen. Ohne den Zwischenspeicher wird bei jedem Wechsel neu interpoliert – beim
    // Schieben des Reglers reiht sich das auf und ruckelt.
    val renderedFrames = remember(radarTimes) {
        mutableMapOf<Int, Triple<Int, android.graphics.Bitmap, org.osmdroid.util.BoundingBox>>()
    }
    // Welcher Frame gerade als Bild im Overlay liegt (-1 = keiner) und zu welchem Fenster.
    var shownFrame by remember { mutableIntStateOf(-1) }
    var shownEpoch by remember { mutableIntStateOf(-1) }
    var lightningArea by remember { mutableStateOf<MercatorBox?>(null) }
    val radarCache = remember(context) { radarCacheDir(context) }

    DisposableEffect(Unit) {
        mapView.onResume()
        onDispose {
            mapView.onPause()
            mapView.onDetach()
        }
    }

    // Overlays ein-/aushängen.
    DisposableEffect(showRadar) {
        if (showRadar) {
            if (!mapView.overlays.contains(radarOverlay)) mapView.overlays.add(0, radarOverlay)
        } else {
            mapView.overlays.remove(radarOverlay)
            radarOverlay.setImage(null, null)
            shownFrame = -1
            shownEpoch = -1
        }
        mapView.invalidate()
        onDispose { }
    }

    DisposableEffect(showSeamarks) {
        if (showSeamarks) {
            if (!mapView.overlays.contains(seamarkOverlay)) {
                var at = 0
                if (mapView.overlays.contains(radarOverlay)) at++
                if (mapView.overlays.contains(lightningOverlay)) at++
                mapView.overlays.add(at.coerceAtMost(mapView.overlays.size), seamarkOverlay)
            }
        } else {
            mapView.overlays.remove(seamarkOverlay)
        }
        mapView.invalidate()
        onDispose { }
    }

    DisposableEffect(showLightning) {
        if (showLightning) {
            if (!mapView.overlays.contains(lightningOverlay)) {
                val at = if (mapView.overlays.contains(radarOverlay)) 1 else 0
                mapView.overlays.add(at, lightningOverlay)
            }
        } else {
            mapView.overlays.remove(lightningOverlay)
            lightningOverlay.setImage(null, null)
        }
        mapView.invalidate()
        onDispose { }
    }

    val followState = rememberUpdatedState(follow)
    val viewportListener = rememberUpdatedState(onViewport)
    LaunchedEffect(mapView) {
        while (!awaitMapReady(mapView) { centered }) delay(500)
        var lastBox: org.osmdroid.util.BoundingBox? = null
        var lastZoom = Double.NaN
        while (true) {
            val cb = viewportListener.value
            if (cb != null) {
                val box = runCatching { mapView.boundingBox }.getOrNull()
                val zoom = mapView.zoomLevelDouble
                if (box != null && box.latitudeSpan > 0) {
                    val moved = lastBox == null ||
                        kotlin.math.abs(zoom - lastZoom) >= 0.5 ||
                        !lastBox!!.contains(box.latNorth, box.lonEast) ||
                        !lastBox!!.contains(box.latSouth, box.lonWest)
                    if (moved) {
                        lastBox = box
                        lastZoom = zoom
                        cb(box, zoom)
                    }
                }
            }
            delay(700)
        }
    }

    // Merkt, wenn der Blick das gerechnete Fenster verlässt oder deutlich näher
    // herangeht → neu **rechnen**. Geholt wird dabei nichts: die Bilder decken ohnehin
    // das ganze Gebiet ab.
    LaunchedEffect(showRadar) {
        if (!showRadar) return@LaunchedEffect
        while (!awaitMapReady(mapView) { centered }) delay(500)
        while (showRadar) {
            val now = runCatching { mapView.boundingBox.toMercator() }.getOrNull()
            if (now != null) {
                val w = renderWindow
                // Verglichen wird gegen das **unbeschnittene** Fenster. Gegen das auf das
                // Radargebiet beschnittene verglichen wäre die Bedingung an der Grenze
                // Deutschlands nie erfüllbar – es würde alle 400 ms neu gerechnet.
                val wanderedOff = w == null || !w.contains(now)
                val zoomedIn = w != null && now.width > 0 && w.width / now.width > 2.2
                if (wanderedOff || zoomedIn) {
                    renderWindow = now.expand(RENDER_PAD)
                    windowEpoch++
                }
            }
            delay(400)
        }
    }

    // Alle Frames holen – für das **feste Gebiet**, unabhängig vom Ausschnitt, und nur
    // einmal. Der sichtbare zuerst, damit sofort etwas zu sehen ist, der Rest danach in
    // kleinen Gruppen. Was auf der Platte liegt, wird nicht noch einmal geholt.
    LaunchedEffect(showRadar, radarTimes) {
        if (!showRadar || radarTimes.isEmpty()) return@LaunchedEffect
        withContext(Dispatchers.IO) { pruneRadarCache(radarCache) }

        suspend fun load(i: Int) {
            if (framePngs.containsKey(i)) return
            val time = radarTimes[i]
            val png = withContext(Dispatchers.IO) {
                readCachedRadar(radarCache, DWD_RADAR_LAYER, time)
                    ?: fetchRadarPng(DWD_RADAR_LAYER, time, RADAR_AREA_GER, RADAR_AREA_W, RADAR_AREA_H)
                        ?.also { writeCachedRadar(radarCache, DWD_RADAR_LAYER, time, it) }
            }
            if (png != null) framePngs[i] = png
        }

        val first = radarFrameIndex.coerceIn(0, radarTimes.lastIndex)
        load(first)
        mapView.invalidate()
        radarTimes.indices.filter { it != first }.chunked(4).forEach { group ->
            coroutineScope { group.forEach { i -> launch { load(i) } } }
        }
    }

    // Die Frames für das aktuelle Fenster im Voraus rechnen, solange niemand hinschaut.
    // Sonst rechnet der erste Durchlauf der Schleife bei jedem Wechsel neu und ruckelt.
    LaunchedEffect(showRadar, radarTimes, framePngs.size, windowEpoch) {
        if (!showRadar || radarTimes.isEmpty()) return@LaunchedEffect
        val window = renderWindow ?: return@LaunchedEffect
        val epoch = windowEpoch
        for (i in radarTimes.indices) {
            if (renderedFrames[i]?.first == epoch) continue
            if (epoch != windowEpoch) return@LaunchedEffect // Blick hat sich weitergedreht
            val png = framePngs[i] ?: continue
            val made = withContext(Dispatchers.Default) { buildFrame(png, window) } ?: continue
            renderedFrames[i] = Triple(epoch, made.first, made.second)
            trimRendered(renderedFrames, shownFrame)
        }
    }

    // Blitze (nur Ist-Zeit).
    LaunchedEffect(showLightning, windowEpoch) {
        if (!showLightning) { lightningPng = null; return@LaunchedEffect }
        while (!awaitMapReady(mapView) { centered }) delay(500)
        val view = runCatching { mapView.boundingBox.toMercator() }.getOrNull() ?: return@LaunchedEffect
        val area = view.expand(RADAR_PAD).clampToWorld()
        val px = radarPixels(area, area.toBoundingBox().centerLatitude)
        lightningArea = area
        lightningPng = withContext(Dispatchers.IO) {
            fetchRadarPng(DWD_LIGHTNING_LAYER, null, area, px.first, px.second)
        }
    }

    // Nur den gezeigten Frame aufbereiten und das alte Bild danach freigeben. Fehlt der
    // Frame noch, bleibt das bisherige Bild stehen – sonst blinkt die Schleife leer. Weil
    // Bild und Fläche zusammengehören, werden sie immer gemeinsam gesetzt: getrennt zeigt
    // das Overlay zwischendurch das alte Bild auf der neuen Fläche und springt.
    LaunchedEffect(showRadar, radarFrameIndex, framePngs.size, windowEpoch) {
        if (!showRadar) { shownFrame = -1; return@LaunchedEffect }
        val idx = radarFrameIndex.coerceIn(0, (radarTimes.size - 1).coerceAtLeast(0))
        if (idx == shownFrame && shownEpoch == windowEpoch && radarOverlay.image != null) {
            return@LaunchedEffect
        }
        val window = renderWindow ?: return@LaunchedEffect

        // Schon gerechnet? Dann sofort zeigen – ohne Umweg über einen Hintergrundlauf,
        // damit das Durchschieben des Reglers unmittelbar folgt.
        renderedFrames[idx]?.takeIf { it.first == windowEpoch }?.let { (_, bmp, box) ->
            radarOverlay.setImage(bmp, box)
            shownFrame = idx
            shownEpoch = windowEpoch
            mapView.invalidate()
            return@LaunchedEffect
        }

        val png = framePngs[idx] ?: return@LaunchedEffect
        val made = withContext(Dispatchers.Default) { buildFrame(png, window) } ?: return@LaunchedEffect
        renderedFrames[idx] = Triple(windowEpoch, made.first, made.second)
        trimRendered(renderedFrames, idx)
        radarOverlay.setImage(made.first, made.second)
        shownFrame = idx
        shownEpoch = windowEpoch
        mapView.invalidate()
    }

    LaunchedEffect(lightningPng) {
        val bmp = lightningPng?.let { withContext(Dispatchers.Default) { decodeRadar(it) } }
        lightningOverlay.setImage(bmp, lightningArea?.toBoundingBox())
        mapView.invalidate()
    }

    // Schleusen und Wehre als eigene Marker; Wehre in Rot, weil sie meist das Ende sind.
    val obstacleMarkers = remember(mapView) { mutableListOf<Marker>() }
    LaunchedEffect(obstacles) {
        obstacleMarkers.forEach { mapView.overlays.remove(it) }
        obstacleMarkers.clear()
        obstacles.forEach { o ->
            val m = Marker(mapView).apply {
                position = GeoPoint(o.lat, o.lon)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                icon = ContextCompat.getDrawable(
                    context,
                    if (o.kind == de.kewl.boatspeedy.nav.ObstacleKind.WEIR ||
                        o.kind == de.kewl.boatspeedy.nav.ObstacleKind.DAM
                    ) R.drawable.ic_obstacle_weir else R.drawable.ic_obstacle_lock,
                )
                title = o.name
            }
            obstacleMarkers.add(m)
            mapView.overlays.add(m)
        }
        mapView.invalidate()
    }

    // Geschwindigkeitszeichen: eigene Marker, weil die Kacheln zwar das Schild zeichnen,
    // aber die Zahl darin frei lassen. Unsere liegen genau darauf und decken es ab.
    val signMarkers = remember(mapView) { mutableListOf<Marker>() }
    DisposableEffect(speedSigns, showSeamarks) {
        signMarkers.forEach { mapView.overlays.remove(it) }
        signMarkers.clear()
        if (showSeamarks) {
            speedSigns.forEach { sign ->
                val m = Marker(mapView).apply {
                    position = GeoPoint(sign.lat, sign.lon)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    icon = speedSignDrawable(context, sign.kmh)
                    title = sign.raw
                }
                signMarkers.add(m)
                mapView.overlays.add(m)
            }
        }
        mapView.invalidate()
        onDispose { }
    }

    // Langer Druck auf die Karte → Ziel setzen.
    DisposableEffect(onLongPress) {
        val overlay = onLongPress?.let { cb ->
            org.osmdroid.views.overlay.MapEventsOverlay(
                object : org.osmdroid.events.MapEventsReceiver {
                    override fun singleTapConfirmedHelper(p: GeoPoint?) = false
                    override fun longPressHelper(p: GeoPoint?): Boolean {
                        p?.let { cb(it.latitude, it.longitude) }
                        return true
                    }
                },
            ).also { mapView.overlays.add(it) }
        }
        onDispose { overlay?.let { mapView.overlays.remove(it) } }
    }

    // Weg zum Ziel zeichnen.
    LaunchedEffect(navPath, navWaterPath) {
        if (navPath.size >= 2) {
            navLine.setPoints(navPath.map { GeoPoint(it.lat, it.lon) })
            if (!mapView.overlays.contains(navLine)) mapView.overlays.add(navLine)
            navMarker.position = GeoPoint(navPath.last().lat, navPath.last().lon)
            if (!mapView.overlays.contains(navMarker)) mapView.overlays.add(navMarker)
        } else {
            mapView.overlays.remove(navLine)
            mapView.overlays.remove(navMarker)
        }
        if (navWaterPath.size >= 2) {
            navWaterLine.setPoints(navWaterPath.map { GeoPoint(it.lat, it.lon) })
            if (!mapView.overlays.contains(navWaterLine)) mapView.overlays.add(navWaterLine)
        } else {
            mapView.overlays.remove(navWaterLine)
        }
        mapView.invalidate()
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

    /**
     * Der Marker **gleitet**, statt von Fix zu Fix zu wandern.
     *
     * Interpoliert man zwischen zwei Messungen, läuft der Marker auf eine Stelle zu, an
     * der man längst vorbei ist, und setzt danach neu an — das war das Stocken. Hier
     * rechnet [DeadReckoner] aus Fahrt und Kurs fortlaufend weiter, wo das Boot jetzt
     * sein müsste, und zieht die Schätzung sanft nach, wenn eine Messung eintrifft.
     *
     * Gezeichnet wird im Takt des Bildschirms, nicht in einer festen 16-ms-Schleife.
     */
    val reckoner = remember(mapView) { DeadReckoner() }

    LaunchedEffect(recenterKey) {
        if (recenterKey <= 0) return@LaunchedEffect
        val la = reckoner.lat ?: currentLat ?: return@LaunchedEffect
        val lo = reckoner.lon ?: currentLon ?: return@LaunchedEffect
        mapView.controller.animateTo(GeoPoint(la, lo), mapView.zoomLevelDouble, 600L)
    }
    LaunchedEffect(currentLat, currentLon, courseDeg, speedMs) {
        if (currentLat == null || currentLon == null) return@LaunchedEffect
        reckoner.onFix(currentLat, currentLon, courseDeg, speedMs, System.currentTimeMillis())
    }

    LaunchedEffect(mapView, currentLat == null) {
        if (currentLat == null) return@LaunchedEffect
        var lastNanos = 0L
        while (true) {
            // Steht alles still, kostet das Zeichnen nur Strom. Dann warten wir auf die
            // nächste Messung, statt jedes Bild durchzurechnen.
            if (!reckoner.isBusy(System.currentTimeMillis())) {
                lastNanos = 0L
                delay(200)
                continue
            }
            withFrameNanos { now ->
                val dt = if (lastNanos == 0L) 0.0 else (now - lastNanos) / 1_000_000_000.0
                lastNanos = now
                if (dt > 0.0) reckoner.advance(dt, System.currentTimeMillis())
                val la = reckoner.lat
                val lo = reckoner.lon
                if (la != null && lo != null) {
                    val at = GeoPoint(la, lo)
                    marker.position = at
                    // osmdroid dreht gegen den Uhrzeigersinn, der Kompasskurs mit.
                    marker.rotation = ((-reckoner.headingDeg % 360f) + 360f) % 360f
                    if (followState.value && centered) mapView.controller.setCenter(at)
                    mapView.invalidate()
                }
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

        // Die Position setzt der Animationslauf oben; hier wird der Marker nur eingehängt.
        if (currentLat != null && currentLon != null) {
            if (!mapView.overlays.contains(marker)) mapView.overlays.add(marker)
        }

        val target = when {
            currentLat != null && currentLon != null -> GeoPoint(currentLat, currentLon)
            geo.isNotEmpty() -> geo.last()
            else -> null
        }
        // Nur das erste Einmitten passiert hier. Danach führt der Bildlauf oben die Karte
        // an der geglätteten Position nach – zwei Stellen, die zentrieren, kämen sich in
        // die Quere und ergäben genau das Ruckeln, das wir loswerden wollen.
        if (target != null && !centered) {
            mapView.controller.setZoom(zoom)
            mapView.controller.setCenter(target)
            centered = true
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

/**
 * Bildgröße für den Blitz-Abruf: **ein Bildpunkt je Kilometer**, also die Auflösung der
 * Messdaten. Mehr anzufordern bringt nichts — der DWD vergrößert dann selbst mit harten
 * Kanten. Das Radar rechnet nicht mehr so: es holt immer das feste Gebiet.
 */
private fun radarPixels(box: MercatorBox, centerLat: Double): Pair<Int, Int> {
    // In Web-Mercator ist ein Meter am Boden 1/cos(Breite) Mercator-Meter.
    val metersPerCell = 1000.0 / kotlin.math.cos(Math.toRadians(centerLat.coerceIn(-80.0, 80.0)))
    val w = (box.width / metersPerCell).toInt().coerceIn(16, 600)
    val h = (box.height / metersPerCell).toInt().coerceIn(16, 600)
    return w to h
}

/**
 * Bereitet einen Frame für das Fenster auf: Bild dekodieren, den sichtbaren Teil
 * herausrechnen und vergrößern. Lohnt die Vergrößerung nicht — weil ohnehin etwa ein
 * Bildpunkt je Rasterzelle bliebe —, wird das Deutschlandbild unverändert genommen und
 * die Filterung beim Zeichnen erledigt den Rest.
 */
private fun buildFrame(
    png: ByteArray,
    window: MercatorBox,
): Pair<android.graphics.Bitmap, org.osmdroid.util.BoundingBox>? {
    val src = decodeRadar(png) ?: return null
    val zoomed = runCatching { renderRadarWindow(src, RADAR_AREA_GER, window) }.getOrNull()
    return if (zoomed != null) {
        src.recycle()
        zoomed to renderedWindowBox(RADAR_AREA_GER, window).toBoundingBox()
    } else {
        src to RADAR_AREA_GER.toBoundingBox()
    }
}

/** Rand um den sichtbaren Ausschnitt – so viel Schwenk verträgt der Blitz-Abruf. */
private const val RADAR_PAD = 1.8

/**
 * Rand um das gerechnete Fenster. Kleiner als der Abruf-Rand früher, weil hier nichts
 * mehr geladen wird: ein größeres Fenster kostet nur Rechenzeit und Auflösung.
 */
private const val RENDER_PAD = 1.4

/**
 * Wartet, bis die Karte vermessen und auf die Position zentriert ist. Ohne das liefert
 * `boundingBox` einen unbrauchbaren Ausschnitt (Größe 0, Mittelpunkt 0/0), das Radar
 * würde für die falsche Fläche geladen und erst die nächste Prüfung holt das Richtige —
 * genau der eine leere Durchlauf, den man vorher gesehen hat.
 */
private suspend fun awaitMapReady(map: MapView, centered: () -> Boolean): Boolean {
    repeat(60) { // höchstens ~6 s
        val box = runCatching { map.boundingBox }.getOrNull()
        val span = if (box == null) 0.0 else box.latitudeSpan * box.longitudeSpanWithDateLine
        if (map.width > 0 && map.height > 0 && centered() && span > 0.0) return true
        delay(100)
    }
    return false
}

/**
 * Hält den Speicher der geglätteten Frames im Rahmen. Bei einem engen Ausschnitt sind das
 * unter einem Megabyte je Frame und alle einundzwanzig passen bequem hinein; bei einem
 * weiten Blick wird ein Frame um ein Vielfaches größer, und dann würden alle zusammen
 * dreistellige Megabyte belegen. Geworfen wird der jeweils älteste Eintrag, nie der
 * gerade gezeigte.
 */
private fun trimRendered(
    cache: MutableMap<Int, Triple<Int, android.graphics.Bitmap, org.osmdroid.util.BoundingBox>>,
    keep: Int,
) {
    val budget = 40 * 1024 * 1024
    var used = cache.values.sumOf { it.second.allocationByteCount }
    if (used <= budget) return
    val order = cache.keys.filter { it != keep }.toMutableList()
    while (used > budget && order.isNotEmpty()) {
        val victim = order.removeAt(0)
        used -= cache.remove(victim)?.second?.allocationByteCount ?: 0
    }
}

