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
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
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

    // PNG-Bytes je Frame; dekodiert wird nur der sichtbare.
    val framePngs = remember(radarTimes) { mutableStateMapOf<Int, ByteArray>() }
    var fetchedArea by remember(radarTimes) { mutableStateOf<MercatorBox?>(null) }
    var lightningPng by remember { mutableStateOf<ByteArray?>(null) }
    // Ein Zähler, der einen Neu-Abruf auslöst, wenn der Ausschnitt weggewandert ist.
    var viewEpoch by remember { mutableIntStateOf(0) }
    // Fertig geglättete Frames. Ohne den Zwischenspeicher wird bei jedem Wechsel neu
    // interpoliert – beim Schieben des Reglers reiht sich das auf und ruckelt.
    val smoothedFrames = remember(radarTimes) { mutableMapOf<Int, Pair<Int, android.graphics.Bitmap>>() }
    // Welcher Frame gerade als Bild im Overlay liegt (-1 = keiner) und zu welchem Abruf.
    var shownFrame by remember { mutableIntStateOf(-1) }
    var shownEpoch by remember { mutableIntStateOf(-1) }
    var lightningArea by remember { mutableStateOf<MercatorBox?>(null) }

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

    // Merkt, wenn der sichtbare Bereich den geladenen verlässt → neu holen.
    LaunchedEffect(showRadar || showLightning) {
        while (showRadar || showLightning) {
            delay(1200)
            val loaded = fetchedArea
            val now = runCatching { mapView.boundingBox.toMercator() }.getOrNull()
            // Neu holen, wenn der Blick den geladenen Bereich verlässt – aber auch, wenn
            // er deutlich kleiner geworden ist: sonst bleibt nach dem Herauszoomen und
            // Wiederhineinzoomen das grobe Übersichtsbild stehen und wirkt „kaputt".
            val wanderedOff = loaded != null && now != null && !loaded.contains(now)
            val zoomedIn = loaded != null && now != null && now.width > 0 &&
                loaded.width / now.width > RADAR_PAD * 2.2
            // Nur nachfordern. Das vorhandene Bild bleibt stehen, bis das neue fertig
            // ist – es sitzt ja weiterhin richtig auf dem Boden, nur gröber.
            if (wanderedOff || zoomedIn) viewEpoch++
        }
    }

    // Alle Frames holen – der sichtbare zuerst, damit sofort etwas zu sehen ist,
    // der Rest danach in kleinen Gruppen parallel. Seriell wären 21 Anfragen à 2–4 s
    // über eine Minute; alle auf einmal überlastet den DWD-Server.
    LaunchedEffect(showRadar, radarTimes, viewEpoch) {
        if (!showRadar || radarTimes.isEmpty()) return@LaunchedEffect
        while (!awaitMapReady(mapView) { centered }) delay(500) // nicht aufgeben
        val view = runCatching { mapView.boundingBox.toMercator() }.getOrNull() ?: return@LaunchedEffect
        // Großzügiger Rand: kleine Schwenks sollen kein Nachladen auslösen.
        val area = view.expand(RADAR_PAD).clampToWorld()
        val px = radarPixels(area, area.toBoundingBox().centerLatitude)
        framePngs.clear()
        smoothedFrames.clear()
        fetchedArea = area

        suspend fun load(i: Int) {
            val png = withContext(Dispatchers.IO) {
                fetchRadarPng(DWD_RADAR_LAYER, radarTimes[i], area, px.first, px.second)
            }
            if (png != null) framePngs[i] = png
        }

        val first = radarFrameIndex.coerceIn(0, radarTimes.lastIndex)
        load(first)
        mapView.invalidate()
        radarTimes.indices.filter { it != first }.chunked(4).forEach { group ->
            coroutineScope { group.forEach { i -> launch { load(i) } } }
        }

        // Die Frames im Voraus glätten, solange niemand hinschaut. Sonst rechnet der
        // erste Durchlauf der Schleife bei jedem Wechsel neu und ruckelt.
        for (i in radarTimes.indices) {
            if (smoothedFrames[i]?.first == viewEpoch) continue
            val png = framePngs[i] ?: continue
            val bmp = withContext(Dispatchers.Default) {
                decodeRadar(png)?.let { raw ->
                    runCatching { smoothRadar(raw, RADAR_EDGE) }.getOrDefault(raw)
                }
            } ?: continue
            smoothedFrames[i] = viewEpoch to bmp
            trimSmoothed(smoothedFrames, shownFrame)
        }
    }

    // Blitze (nur Ist-Zeit).
    LaunchedEffect(showLightning, viewEpoch) {
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

    // Nur den gezeigten Frame dekodieren und das alte Bild danach freigeben. Fehlt der
    // Frame noch, bleibt das bisherige Bild stehen – sonst blinkt die Schleife leer.
    LaunchedEffect(showRadar, radarFrameIndex, framePngs.size, viewEpoch) {
        if (!showRadar) { shownFrame = -1; return@LaunchedEffect }
        val idx = radarFrameIndex.coerceIn(0, (radarTimes.size - 1).coerceAtLeast(0))
        if (idx == shownFrame && shownEpoch == viewEpoch && radarOverlay.image != null) {
            return@LaunchedEffect
        }
        val area = fetchedArea ?: return@LaunchedEffect
        val box = area.toBoundingBox()

        // Schon geglättet? Dann sofort zeigen – ohne Umweg über einen Hintergrundlauf,
        // damit das Durchschieben des Reglers unmittelbar folgt.
        smoothedFrames[idx]?.takeIf { it.first == viewEpoch }?.let { (_, ready) ->
            radarOverlay.setImage(ready, box)
            shownFrame = idx
            shownEpoch = viewEpoch
            mapView.invalidate()
            return@LaunchedEffect
        }

        val png = framePngs[idx] ?: return@LaunchedEffect
        val bmp = withContext(Dispatchers.Default) {
            // Sollte der Speicher einmal nicht reichen, lieber das ungeglättete Bild
            // zeigen als abstürzen.
            decodeRadar(png)?.let { raw ->
                runCatching { smoothRadar(raw, RADAR_EDGE) }.getOrDefault(raw)
            }
        } ?: return@LaunchedEffect
        smoothedFrames[idx] = viewEpoch to bmp
        trimSmoothed(smoothedFrames, idx)
        // Bild und Fläche gehören zusammen und werden deshalb gemeinsam gesetzt. Getrennt
        // gesetzt zeigt das Overlay zwischendurch das alte Bild auf der neuen Fläche –
        // es springt und wirkt, als hinge es am Finger statt am Boden.
        radarOverlay.setImage(bmp, box)
        shownFrame = idx
        shownEpoch = viewEpoch
        mapView.invalidate()
    }

    LaunchedEffect(lightningPng) {
        val bmp = lightningPng?.let { withContext(Dispatchers.Default) { decodeRadar(it) } }
        lightningOverlay.setImage(bmp, lightningArea?.toBoundingBox())
        mapView.invalidate()
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

    // Marker in Fahrtrichtung drehen. osmdroid dreht gegen den Uhrzeigersinn, der
    // Kompasskurs läuft mit – deshalb das umgekehrte Vorzeichen. Steht das Boot, kommt
    // hier nichts an und die letzte Richtung bleibt stehen, statt zu zappeln.
    LaunchedEffect(courseDeg) {
        courseDeg?.let {
            marker.rotation = ((-it % 360f) + 360f) % 360f
            mapView.invalidate()
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

/**
 * Bildgröße für einen Radarabruf: **ein Bildpunkt je Kilometer**, also genau die
 * Auflösung der Messdaten.
 *
 * Mehr anzufordern bringt nichts — der DWD vergrößert dann selbst mit harten Kanten, und
 * genau die wollen wir nicht. Weniger würde Daten wegwerfen. Aus diesem kleinen Bild
 * rechnet [smoothRadar] die Zwischenwerte, und das Ergebnis wird beim Zeichnen auf die
 * Karte skaliert. Nebenbei sinkt die Downloadgröße auf wenige Kilobyte je Frame.
 */
private fun radarPixels(box: MercatorBox, centerLat: Double): Pair<Int, Int> {
    // In Web-Mercator ist ein Meter am Boden 1/cos(Breite) Mercator-Meter.
    val metersPerCell = 1000.0 / kotlin.math.cos(Math.toRadians(centerLat.coerceIn(-80.0, 80.0)))
    // Nach oben begrenzt: ein Ausschnitt über hunderte Kilometer braucht die
    // Kilometer-Auflösung nicht, und das Glätten danach kostet sonst zu viel Speicher.
    val w = (box.width / metersPerCell).toInt().coerceIn(16, 600)
    val h = (box.height / metersPerCell).toInt().coerceIn(16, 600)
    return w to h
}

/**
 * Längste Kante des geglätteten Frames. Am Bildschirm ausprobiert: bei dieser Größe sind
 * die Umrisse rund und die Farbgrenzen scharf, und ein Frame belegt rund anderthalb
 * Megabyte — so passt die ganze Schleife in den Speicher, statt bei jedem Wechsel neu
 * gerechnet werden zu müssen. Was danach beim Zeichnen noch vergrößert wird, wirkt wie
 * Kantenglättung, nicht wie Unschärfe.
 */
private const val RADAR_EDGE = 640

/** Rand um den sichtbaren Ausschnitt – so viel Schwenk verträgt ein Abruf ohne Nachladen. */
private const val RADAR_PAD = 1.8

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
private fun trimSmoothed(cache: MutableMap<Int, Pair<Int, android.graphics.Bitmap>>, keep: Int) {
    val budget = 40 * 1024 * 1024
    var used = cache.values.sumOf { it.second.allocationByteCount }
    if (used <= budget) return
    val order = cache.keys.filter { it != keep }.toMutableList()
    while (used > budget && order.isNotEmpty()) {
        val victim = order.removeAt(0)
        used -= cache.remove(victim)?.second?.allocationByteCount ?: 0
    }
}
