package de.kewl.boatspeedy.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.kewl.boatspeedy.R
import de.kewl.boatspeedy.battery.RangeEstimate
import de.kewl.boatspeedy.data.Settings
import de.kewl.boatspeedy.location.GpsState
import de.kewl.boatspeedy.trip.TripStats
import de.kewl.boatspeedy.ui.theme.SpeedTextStyle
import de.kewl.boatspeedy.ui.theme.StatusGood
import de.kewl.boatspeedy.ui.theme.StatusNone
import de.kewl.boatspeedy.ui.theme.StatusWeak

@Composable
fun SpeedScreen(
    speedText: String,
    gps: GpsState,
    settings: Settings,
    tracking: Boolean,
    tripStats: TripStats,
    batterySoc: Int? = null,
    range: RangeEstimate? = null,
    onStartTrip: () -> Unit,
    onStopTrip: () -> Unit,
    onOpenMenu: () -> Unit,
) {
    Scaffold { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {

            IconButton(
                onClick = onOpenMenu,
                modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
            ) {
                Icon(Icons.Filled.Menu, contentDescription = stringResource(R.string.menu))
            }

            // Große Geschwindigkeitszahl mittig.
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = speedText,
                    style = SpeedTextStyle,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = settings.unit.label,
                    fontSize = 28.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                )

                if (batterySoc != null) {
                    val parts = buildList {
                        add("${stringResource(R.string.soc_short)} $batterySoc %")
                        range?.let {
                            add(formatDistance(it.km * 1000.0))
                            add(formatDuration((it.hours * 3600_000).toLong()))
                        }
                    }
                    Text(
                        text = parts.joinToString("  ·  "),
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }

            // Unterer Bereich: Statistik, Fahrt-Knopf, Status.
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (tracking || tripStats.hasData) {
                    StatsPanel(stats = tripStats, settings = settings)
                }

                TripButton(tracking = tracking, onStart = onStartTrip, onStop = onStopTrip)

                StatusRow(gps = gps, showSatDetails = settings.showSatDetails)
            }
        }
    }
}

@Composable
private fun StatsPanel(stats: TripStats, settings: Settings) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        StatItem(stringResource(R.string.stat_distance), formatDistance(stats.distanceM))
        StatItem(
            stringResource(R.string.stat_max),
            "${formatSpeed(stats.maxSpeedMs, settings.unit, settings.decimals)} ${settings.unit.label}",
        )
        StatItem(
            stringResource(R.string.stat_avg),
            "${formatSpeed(stats.avgSpeedMs, settings.unit, settings.decimals)} ${settings.unit.label}",
        )
        StatItem(stringResource(R.string.stat_time), formatDuration(stats.elapsedMs))
    }
}

@Composable
private fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
        )
        Text(
            text = value,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

@Composable
private fun TripButton(tracking: Boolean, onStart: () -> Unit, onStop: () -> Unit) {
    if (tracking) {
        Button(
            onClick = onStop,
            colors = ButtonDefaults.buttonColors(
                containerColor = StatusNone,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
            Icon(Icons.Filled.Stop, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.trip_stop))
        }
    } else {
        Button(onClick = onStart) {
            Icon(Icons.Filled.PlayArrow, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.trip_start))
        }
    }
}

@Composable
private fun StatusRow(gps: GpsState, showSatDetails: Boolean, modifier: Modifier = Modifier) {
    val statusColor = when {
        !gps.hasFix -> StatusNone
        (gps.accuracyM ?: Float.MAX_VALUE) <= 10f && gps.satellitesUsed >= 4 -> StatusGood
        else -> StatusWeak
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(statusColor))
        Spacer(Modifier.width(8.dp))
        Text(
            text = if (gps.hasFix) stringResource(R.string.status_fix)
            else stringResource(R.string.status_no_fix),
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
        )

        if (showSatDetails) {
            Spacer(Modifier.width(16.dp))
            Text(
                text = stringResource(R.string.sat_label, gps.satellitesUsed, gps.satellitesVisible),
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
            )
            gps.accuracyM?.let { acc ->
                Spacer(Modifier.width(16.dp))
                Text(
                    text = stringResource(R.string.accuracy_label, acc.toInt()),
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                )
            }
        }
    }
}
