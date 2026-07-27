package de.kewl.boatspeedy.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.kewl.boatspeedy.R
import de.kewl.boatspeedy.data.Settings
import de.kewl.boatspeedy.trip.SavedTrip
import de.kewl.boatspeedy.trip.TrackPoint
import java.util.Locale

/** Vollbild-Track auf Karte: Richtungspfeile + Antippen zeigt die Track-Daten am Punkt. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripMapScreen(trip: SavedTrip, settings: Settings, onBack: () -> Unit) {
    var selected by remember { mutableStateOf<TrackPoint?>(null) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.show_on_map)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            TrackMap(
                trip = trip,
                interactive = true,
                showArrows = true,
                onPointTap = { selected = it },
                modifier = Modifier.fillMaxSize(),
            )
            selected?.let { p ->
                PointInfoCard(
                    p = p,
                    settings = settings,
                    onClose = { selected = null },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(12.dp),
                )
            }
        }
    }
}

@Composable
private fun PointInfoCard(p: TrackPoint, settings: Settings, onClose: () -> Unit, modifier: Modifier) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.trip_point_title), fontWeight = FontWeight.SemiBold)
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.back))
                }
            }
            InfoRow(stringResource(R.string.stat_total_time), formatDuration(p.tMs))
            InfoRow(
                stringResource(R.string.speed_label),
                "${formatSpeed(p.speedMs, settings.unit, settings.decimals)} ${settings.unit.label}",
            )
            if (p.chargeAh > 0f) {
                InfoRow(stringResource(R.string.stat_consumed), String.format(Locale.getDefault(), "%.1f Ah", p.chargeAh))
            }
            if (p.soc >= 0) {
                InfoRow(stringResource(R.string.soc_short), "${p.soc} %")
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
        Text(value, fontWeight = FontWeight.SemiBold)
    }
}
