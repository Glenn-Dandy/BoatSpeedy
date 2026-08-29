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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.kewl.boatspeedy.R
import de.kewl.boatspeedy.data.Settings
import de.kewl.boatspeedy.trip.TrackPoint
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** Vollbild-Live-Karte: Position + Track (folgt/verlassen) und DWD-Wetterradar (Regen + optional Blitze). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveMapScreen(
    currentLat: Double?,
    currentLon: Double?,
    points: List<TrackPoint>,
    settings: Settings,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var follow by remember { mutableStateOf(true) }
    var showWeather by remember { mutableStateOf(false) }
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
                title = { Text(stringResource(R.string.live_map)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconToggleButton(checked = showWeather, onCheckedChange = { showWeather = it }) {
                        Icon(Icons.Filled.Cloud, contentDescription = stringResource(R.string.weather_show))
                    }
                },
            )
        },
        floatingActionButton = {
            if (!follow) {
                FloatingActionButton(onClick = { follow = true }) {
                    Icon(Icons.Filled.MyLocation, contentDescription = stringResource(R.string.follow_position))
                }
            }
        },
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            OsmMap(
                points = points,
                currentLat = currentLat,
                currentLon = currentLon,
                interactive = true,
                follow = follow,
                onUserPan = { follow = false },
                bubbleText = bubble,
                showRadar = showWeather,
                radarTimes = radarTimes,
                radarFrameIndex = frameIndex.coerceIn(0, frames.lastIndex),
                showLightning = showWeather && showLightning,
                modifier = Modifier.fillMaxSize(),
            )

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
                        Column(
                            modifier = Modifier.width(64.dp),
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
