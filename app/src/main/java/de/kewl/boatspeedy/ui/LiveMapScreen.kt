package de.kewl.boatspeedy.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.kewl.boatspeedy.R
import de.kewl.boatspeedy.data.Settings
import de.kewl.boatspeedy.trip.TrackPoint

/** Vollbild-Live-Karte: Position + Track (folgt/verlassen) und ein-/ausschaltbares DWD-Wetterradar. */
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
    var playing by remember { mutableStateOf(true) }
    var frameLabel by remember { mutableStateOf("") }
    val bubble: (TrackPoint) -> String = { p -> buildTrackBubble(context, settings, p) }

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
                showLightning = showWeather && showLightning,
                radarPlaying = playing,
                onRadarFrame = { frameLabel = it },
                modifier = Modifier.fillMaxSize(),
            )

            if (showWeather) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .background(
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                                RoundedCornerShape(24.dp),
                            )
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                    ) {
                        IconButton(onClick = { playing = !playing }) {
                            Icon(
                                if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = stringResource(R.string.weather_play),
                            )
                        }
                        Text(
                            if (frameLabel.isBlank()) stringResource(R.string.radar_now) else frameLabel,
                            fontWeight = FontWeight.SemiBold,
                        )
                        FilledIconToggleButton(checked = showLightning, onCheckedChange = { showLightning = it }) {
                            Icon(Icons.Filled.FlashOn, contentDescription = stringResource(R.string.lightning_toggle))
                        }
                    }
                    Text(
                        stringResource(R.string.weather_radar_source),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        modifier = Modifier
                            .background(
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                                RoundedCornerShape(6.dp),
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
        }
    }
}
