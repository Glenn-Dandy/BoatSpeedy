package de.kewl.boatspeedy.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import java.util.Locale
import de.kewl.boatspeedy.R
import de.kewl.boatspeedy.data.Settings
import de.kewl.boatspeedy.nav.LatLon
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.kewl.boatspeedy.nav.NavMode
import de.kewl.boatspeedy.nav.NavRepository
import de.kewl.boatspeedy.nav.NavTarget
import de.kewl.boatspeedy.nav.ObstacleKind
import de.kewl.boatspeedy.nav.SpeedSign
import de.kewl.boatspeedy.nav.SpeedSignSource
import de.kewl.boatspeedy.nav.RouteError
import de.kewl.boatspeedy.nav.RouteResult
import de.kewl.boatspeedy.nav.WaterRouter
import de.kewl.boatspeedy.nav.bearingDeg
import de.kewl.boatspeedy.nav.distanceM
import de.kewl.boatspeedy.nav.relativeBearing
import de.kewl.boatspeedy.nav.pathLengthM
import de.kewl.boatspeedy.trip.TrackPoint
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** Vollbild-Live-Karte: Position + Track (folgt/verlassen) und DWD-Wetterradar (Regen + optional Blitze). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveMapScreen(
    currentLat: Double?,
    currentLon: Double?,
    /** Fahrt über Grund; die Karte rechnet damit zwischen den GPS-Meldungen weiter. */
    speedMs: Float?,
    points: List<TrackPoint>,
    settings: Settings,
    /** Verbrauch der laufenden Fahrt, um den Bedarf bis zum Ziel zu schätzen. */
    tripDistanceM: Double = 0.0,
    tripChargeAh: Float = 0f,
    /**
     * Wetteransicht: dieselbe Karte, aber das Radar ist von vornherein an und lässt sich
     * nicht abschalten. So gibt es die Karte einmal und nicht zweimal fast gleich.
     */
    weatherMode: Boolean = false,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var follow by remember { mutableStateOf(true) }
    var showWeather by remember { mutableStateOf(weatherMode) }
    var showLightning by remember { mutableStateOf(false) }
    // Start pausiert auf „Jetzt": während der Pause lädt der Preload alle Frames im
    // Hintergrund; „Play" läuft dann sofort flüssig.
    var playing by remember { mutableStateOf(false) }
    var frameIndex by remember { mutableIntStateOf(0) }
    // Die Frame-Liste altert: bleibt die Karte offen, zeigt „jetzt" sonst irgendwann den
    // Stand von vor einer halben Stunde. Deshalb im 5-Minuten-Takt neu bilden – das ist
    // genau der Takt, in dem der DWD neue Daten veröffentlicht.
    var framesEpoch by remember { mutableIntStateOf(0) }
    LaunchedEffect(showWeather) {
        while (showWeather) {
            val now = System.currentTimeMillis()
            delay(5 * 60_000L - (now % (5 * 60_000L)) + 20_000L) // kurz nach dem Wechsel
            framesEpoch++
        }
    }
    val frames = remember(framesEpoch) { radarFrames() }
    val radarTimes = remember(frames) { frames.map { it.timeIso } }
    val bubble: (TrackPoint) -> String = { p -> buildTrackBubble(context, settings, p) }

    // --- Ziel setzen (langer Druck auf die Karte) ---
    var askTarget by remember { mutableStateOf<LatLon?>(null) }
    // Der zuletzt gewählte Punkt, damit man nach einer gescheiterten Route direkt die
    // Luftlinie nehmen kann, ohne noch einmal zu zielen.
    var navTargetFallback by remember { mutableStateOf<LatLon?>(null) }
    val navTarget by NavRepository.target.collectAsStateWithLifecycle()
    val course by NavRepository.course.collectAsStateWithLifecycle()
    // Aktuelle Lage der nächsten DWD-Station; alle zehn Minuten frisch, das ist der Takt,
    // in dem die Stationen selbst melden.
    val currentWeather by de.kewl.boatspeedy.weather.WeatherRepository.current.collectAsStateWithLifecycle()
    LaunchedEffect(weatherMode, currentLat != null) {
        if (!weatherMode) return@LaunchedEffect
        while (true) {
            val la = currentLat
            val lo = currentLon
            if (la != null && lo != null) {
                de.kewl.boatspeedy.weather.WeatherRepository.refreshCurrent(la, lo)
            }
            delay(10 * 60_000L)
        }
    }
    var routing by remember { mutableStateOf(false) }
    var routeError by remember { mutableStateOf<RouteError?>(null) }
    val scope = rememberCoroutineScope()

    // Verbrauch je Kilometer aus der laufenden Fahrt; erst ab etwas Strecke sinnvoll.
    val ahPerKm: Float? = if (tripDistanceM > 300.0 && tripChargeAh > 0f) {
        (tripChargeAh / (tripDistanceM / 1000.0)).toFloat()
    } else {
        null
    }

    fun setTarget(mode: NavMode, at: LatLon) {
        val from = if (currentLat != null && currentLon != null) LatLon(currentLat, currentLon) else null
        if (from == null) return
        routeError = null
        navTargetFallback = at
        if (mode == NavMode.LINE) {
            NavRepository.set(NavTarget(at, mode, listOf(from, at), distanceM(from, at)))
            return
        }
        routing = true
        scope.launch {
            val result = withContext(Dispatchers.IO) { WaterRouter.route(from, at, settings.craft) }
            routing = false
            when (result) {
                is RouteResult.Ok -> NavRepository.set(
                    NavTarget(
                        at, mode, result.path, pathLengthM(result.path),
                        result.water, result.obstacles,
                    ),
                )
                is RouteResult.Failed -> routeError = result.reason
            }
        }
    }

    // Geschwindigkeitszeichen für den sichtbaren Ausschnitt. Nachgeladen wird erst, wenn
    // der Blick den geholten Bereich verlässt — Overpass ist eine gemeinsam genutzte
    // Schnittstelle, und die Schilder wandern nicht.
    var speedSigns by remember { mutableStateOf<List<SpeedSign>>(emptyList()) }
    var signArea by remember { mutableStateOf<org.osmdroid.util.BoundingBox?>(null) }
    var recenterKey by remember { mutableIntStateOf(0) }
    var mapBox by remember { mutableStateOf<org.osmdroid.util.BoundingBox?>(null) }
    var zoomLevel by remember { mutableStateOf(0.0) }
    LaunchedEffect(settings.seamarks, weatherMode, mapBox, zoomLevel) {
        if (!settings.seamarks || weatherMode) { speedSigns = emptyList(); signArea = null; return@LaunchedEffect }
        val box = mapBox ?: return@LaunchedEffect
        // Zu weit draußen stehen zu viele Schilder zu dicht beieinander, um lesbar zu sein.
        if (zoomLevel < SpeedSignSource.MIN_ZOOM) { speedSigns = emptyList(); signArea = null; return@LaunchedEffect }
        val have = signArea
        val covered = have != null &&
            have.latNorth >= box.latNorth && have.latSouth <= box.latSouth &&
            have.lonEast >= box.lonEast && have.lonWest <= box.lonWest
        if (covered) return@LaunchedEffect
        // Deutlich größer holen als sichtbar: in Fahrt wandert der Ausschnitt ständig,
        // und jede Bildschirmbreite eine Overpass-Anfrage wäre unhöflich. So ist erst
        // nach zwei Bildschirmbreiten Fahrt wieder eine nötig.
        val padLat = box.latitudeSpan
        val padLon = box.longitudeSpanWithDateLine
        val south = box.latSouth - padLat
        val north = box.latNorth + padLat
        val west = box.lonWest - padLon
        val east = box.lonEast + padLon
        val found = withContext(Dispatchers.IO) { SpeedSignSource.fetch(south, west, north, east) }
        if (found != null) {
            speedSigns = found
            signArea = org.osmdroid.util.BoundingBox(north, east, south, west)
        }
    }

    // Vorhersage-Schleife (jetzt → +2 h), solange „Abspielen" aktiv.
    LaunchedEffect(showWeather, playing) {
        if (showWeather && playing) {
            while (true) {
                delay(if (frameIndex == frames.lastIndex) 1400 else 550)
                frameIndex = (frameIndex + 1) % frames.size
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(if (weatherMode) R.string.nav_weather else R.string.live_map),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                // Kein Wetter-Schalter mehr: das Radar hat sein eigenes Menü, und die
                // Live-Karte ist zum Fahren da.
                actions = {},
            )
        },
        floatingActionButton = {
            // In der Wetteransicht folgt die Karte nicht — gerade deshalb braucht es den
            // Knopf: man schiebt beim Betrachten weit weg und fände sonst nicht zurück.
            // Dort mittet er einmalig ein, in der Live-Karte schaltet er das Folgen an.
            if (weatherMode) {
                if (currentLat != null && currentLon != null) {
                    FloatingActionButton(onClick = { recenterKey++ }) {
                        Icon(Icons.Filled.MyLocation, contentDescription = stringResource(R.string.follow_position))
                    }
                }
            } else if (!follow) {
                FloatingActionButton(onClick = { follow = true }) {
                    Icon(Icons.Filled.MyLocation, contentDescription = stringResource(R.string.follow_position))
                }
            }
        },
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            // Eigener Streifen statt einer zweiten Zeile in der Titelleiste: dort war die
            // Schrift klein und der Titel wurde zweizeilig.
            if (weatherMode) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                    ) { WeatherLine(currentWeather) }
                }
            }
            Box(modifier = Modifier.fillMaxSize()) {
                OsmMap(
                    points = points,
                    currentLat = currentLat,
                    currentLon = currentLon,
                    interactive = true,
                    follow = follow && !weatherMode,
                    onUserPan = { follow = false },
                    bubbleText = bubble,
                    showRadar = showWeather,
                    radarTimes = radarTimes,
                    radarFrameIndex = frameIndex.coerceIn(0, frames.lastIndex),
                    showLightning = showWeather && showLightning,
                    // In der Wetteransicht wird nicht navigiert: kein Ziel setzen, keine
                    // Route zeichnen. Und die Karte bleibt stehen, wo man sie hingeschoben
                    // hat – sonst zieht sie einem beim Betrachten unter der Hand weg.
                    onLongPress = if (weatherMode) null else { lat, lon -> askTarget = LatLon(lat, lon) },
                    navPath = if (weatherMode) emptyList() else navTarget?.path.orEmpty(),
                    navWaterPath = if (weatherMode) emptyList() else navTarget?.water.orEmpty(),
                    obstacles = if (weatherMode) emptyList() else navTarget?.obstacles.orEmpty(),
                    courseDeg = course?.deg,
                    // In der Wetteransicht wird nicht gefolgt, also auch nicht weitergerechnet.
                    speedMs = if (weatherMode) null else speedMs,
                    showSeamarks = settings.seamarks && !weatherMode,
                    speedSigns = speedSigns,
                    onViewport = { box, zoom -> mapBox = box; zoomLevel = zoom },
                    recenterKey = recenterKey,
                    modifier = Modifier.fillMaxSize(),
                )

                // Entfernung und geschätzter Verbrauch bis zum Ziel.
                navTarget?.takeIf { !weatherMode }?.let { t ->
                    Surface(
                        modifier = Modifier.align(Alignment.TopCenter).padding(8.dp),
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                        tonalElevation = 3.dp,
                    ) {
                        Row(
                            modifier = Modifier.padding(start = 10.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // Pfeil nur, wenn überhaupt einmal ein Kurs bekannt war.
                            if (currentLat != null && currentLon != null) {
                                course?.let { c ->
                                    CourseArrow(
                                        relativeDeg = relativeBearing(
                                            c.deg,
                                            bearingDeg(LatLon(currentLat, currentLon), t.target),
                                        ),
                                        stale = c.stale,
                                    )
                                    Spacer(Modifier.size(8.dp))
                                }
                            }
                            Text(
                                buildString {
                                    append(String.format(Locale.getDefault(), "%.2f km", t.distanceM / 1000.0))
                                    val ah = ahPerKm?.let { it * (t.distanceM / 1000.0) }
                                    if (ah != null) {
                                        append(" · ~")
                                        append(String.format(Locale.getDefault(), "%.1f Ah", ah))
                                    }
                                },
                                fontWeight = FontWeight.SemiBold,
                            )
                            IconButton(onClick = { NavRepository.clear() }) {
                                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.nav_clear))
                            }
                        }
                    }
                }

                // Schleusen und Wehre stehen für sich, nicht neben den Kilometern: dort war
                // nur Platz für eine Zeile, und die zeigte das Wehr statt der Schleuse, durch
                // die man tatsächlich fährt. Seit die Route an Wehren getrennt wird, kann ein
                // Wehr gar nicht mehr auf ihr liegen – es steht daneben, meist neben der
                // Schleuse. Deshalb zwei getrennte Angaben mit unterschiedlichem Gewicht.
                navTarget?.takeIf { !weatherMode && it.mode == NavMode.ROUTE }?.let { t ->
                    val locks = t.obstacles.count { it.kind == ObstacleKind.LOCK || it.kind == ObstacleKind.SLUICE }
                    val weirs = t.obstacles.count { it.kind == ObstacleKind.WEIR || it.kind == ObstacleKind.DAM }
                    if (locks > 0 || weirs > 0) {
                        Surface(
                            modifier = Modifier.align(Alignment.BottomStart).padding(start = 12.dp, bottom = 16.dp),
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                            tonalElevation = 3.dp,
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                                if (locks > 0) {
                                    ObstacleLine(
                                        iconRes = R.drawable.ic_obstacle_lock,
                                        text = if (locks == 1) stringResource(R.string.nav_obstacles_lock_one)
                                        else stringResource(R.string.nav_obstacles_locks, locks),
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                                if (weirs > 0) {
                                    ObstacleLine(
                                        iconRes = R.drawable.ic_obstacle_weir,
                                        text = if (weirs == 1) stringResource(R.string.nav_obstacles_weir_one)
                                        else stringResource(R.string.nav_obstacles_weirs, weirs),
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                        }
                    }
                }

                if (routing) {
                    Surface(
                        modifier = Modifier.align(Alignment.Center).padding(16.dp),
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                        tonalElevation = 3.dp,
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp))
                            Spacer(Modifier.size(12.dp))
                            Text(stringResource(R.string.nav_routing))
                        }
                    }
                }

                if (showWeather) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            // höher, damit der GPS-Folgen-Knopf den Regler nicht verdeckt
                            .padding(start = 12.dp, end = 12.dp, bottom = 84.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                                    RoundedCornerShape(24.dp),
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        ) {
                            IconButton(onClick = { playing = !playing }) {
                                Icon(
                                    if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                    contentDescription = stringResource(R.string.weather_play),
                                )
                            }
                            val frame = frames[frameIndex.coerceIn(0, frames.lastIndex)]
                            // feste Breite → der Regler bleibt immer gleich lang
                            // Feste Breite hält den Regler gleich lang. 64 dp reichten für
                            // „+100 min" nicht – die Beschriftung brach um.
                            Column(
                                modifier = Modifier.width(78.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    frame.clock,
                                    fontWeight = FontWeight.SemiBold,
                                    textAlign = TextAlign.Center,
                                )
                                Text(
                                    frame.label.ifBlank { stringResource(R.string.radar_now) },
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    softWrap = false,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                    textAlign = TextAlign.Center,
                                )
                            }
                            Slider(
                                value = frameIndex.toFloat(),
                                onValueChange = { playing = false; frameIndex = it.roundToInt().coerceIn(0, frames.lastIndex) },
                                valueRange = 0f..frames.lastIndex.toFloat(),
                                steps = (frames.size - 2).coerceAtLeast(0),
                                modifier = Modifier.weight(1f),
                            )
                            FilledIconToggleButton(checked = showLightning, onCheckedChange = { showLightning = it }) {
                                Icon(Icons.Filled.FlashOn, contentDescription = stringResource(R.string.lightning_toggle))
                            }
                        }
                        Text(
                            stringResource(R.string.weather_radar_source),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                            modifier = Modifier
                                .background(
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                                    RoundedCornerShape(6.dp),
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
            }
        }
    }

    // Langer Druck → fragen, wie gerechnet werden soll.
    askTarget?.let { at ->
        AlertDialog(
            onDismissRequest = { askTarget = null },
            title = { Text(stringResource(R.string.nav_target)) },
            text = { Text(stringResource(R.string.nav_pick_hint)) },
            confirmButton = {
                TextButton(onClick = { setTarget(NavMode.ROUTE, at); askTarget = null }) {
                    Text(stringResource(R.string.nav_route))
                }
            },
            dismissButton = {
                TextButton(onClick = { setTarget(NavMode.LINE, at); askTarget = null }) {
                    Text(stringResource(R.string.nav_line))
                }
            },
        )
    }

    // Routen kann aus mehreren Gründen scheitern – jeder bekommt seinen eigenen Satz,
    // damit man weiß, ob es am Empfang, an der Entfernung oder an den Daten lag.
    routeError?.let { err ->
        AlertDialog(
            onDismissRequest = { routeError = null },
            title = { Text(stringResource(R.string.nav_route)) },
            text = {
                Text(
                    stringResource(
                        when (err) {
                            RouteError.TOO_FAR -> R.string.nav_err_far
                            RouteError.NO_NETWORK -> R.string.nav_err_offline
                            RouteError.NO_WATERWAYS -> R.string.nav_err_nodata
                            RouteError.NOT_ON_WATER -> R.string.nav_err_notwater
                            RouteError.NO_CONNECTION -> R.string.nav_err_unconnected
                        },
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val at = navTargetFallback
                    routeError = null
                    if (at != null) setTarget(NavMode.LINE, at)
                }) { Text(stringResource(R.string.nav_line)) }
            },
            dismissButton = {
                TextButton(onClick = { routeError = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

/** Eine Zeile im Hinderniskasten: Symbol plus Anzahl. */
@Composable
private fun ObstacleLine(iconRes: Int, text: String, color: androidx.compose.ui.graphics.Color) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 1.dp)) {
        Icon(
            painterResource(iconRes),
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.size(6.dp))
        Text(text, fontSize = 13.sp, color = color)
    }
}
