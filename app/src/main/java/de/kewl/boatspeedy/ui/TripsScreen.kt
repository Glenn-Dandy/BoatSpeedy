package de.kewl.boatspeedy.ui

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.rememberCoroutineScope
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
import de.kewl.boatspeedy.trip.SavedTrip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun tripDate(epochMs: Long): String =
    SimpleDateFormat("dd.MM.yyyy · HH:mm", Locale.getDefault()).format(Date(epochMs))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripsScreen(
    trips: List<SavedTrip>,
    onOpenDetail: (SavedTrip) -> Unit,
    onDelete: (Set<Long>) -> Unit,
    onOpenMenu: () -> Unit,
) {
    var selection by remember { mutableStateOf<Set<Long>>(emptySet()) }
    val selecting = selection.isNotEmpty()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            if (selecting) {
                TopAppBar(
                    title = { Text("${selection.size}") },
                    navigationIcon = {
                        IconButton(onClick = { selection = emptySet() }) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.back))
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            val chosen = trips.filter { it.id in selection }
                            scope.launch {
                                val uri = withContext(Dispatchers.IO) { GpxExport.write(context, chosen) }
                                if (uri == null) {
                                    Toast.makeText(context, context.getString(R.string.no_track), Toast.LENGTH_SHORT).show()
                                } else {
                                    val send = Intent(Intent.ACTION_SEND).apply {
                                        type = "application/gpx+xml"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(
                                        Intent.createChooser(send, context.getString(R.string.export)),
                                    )
                                }
                            }
                        }) {
                            Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.export))
                        }
                        IconButton(onClick = {
                            onDelete(selection)
                            selection = emptySet()
                        }) {
                            Icon(Icons.Filled.DeleteOutline, contentDescription = stringResource(R.string.remove))
                        }
                    },
                )
            } else {
                TopAppBar(
                    title = { Text(stringResource(R.string.nav_trips)) },
                    navigationIcon = {
                        IconButton(onClick = onOpenMenu) {
                            Icon(Icons.Filled.Menu, contentDescription = stringResource(R.string.menu))
                        }
                    },
                )
            }
        },
    ) { innerPadding ->
        if (trips.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(innerPadding).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    stringResource(R.string.trips_empty),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            trips.forEach { trip ->
                TripRow(
                    trip = trip,
                    selected = trip.id in selection,
                    onToggle = {
                        selection = if (trip.id in selection) selection - trip.id else selection + trip.id
                    },
                    onClick = { if (selecting) {
                        selection = if (trip.id in selection) selection - trip.id else selection + trip.id
                    } else onOpenDetail(trip) },
                )
            }
        }
    }
}

@Composable
private fun TripRow(trip: SavedTrip, selected: Boolean, onToggle: () -> Unit, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = selected, onCheckedChange = { onToggle() })
            Column(modifier = Modifier.weight(1f).padding(start = 4.dp)) {
                Text(tripDate(trip.startedAt), fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(
                    buildString {
                        append(formatDistance(trip.distanceM))
                        append(" · ")
                        append(formatDuration(trip.durationMs))
                        if (trip.chargeAh > 0f) append(String.format(Locale.getDefault(), " · %.1f Ah", trip.chargeAh))
                    },
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    maxLines = 1,
                )
            }
            if (trip.hasTrack) {
                Icon(
                    Icons.Filled.Route,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripDetailScreen(trip: SavedTrip, settings: Settings, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(tripDate(trip.startedAt)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    if (trip.hasTrack) {
                        IconButton(onClick = {
                            scope.launch {
                                val uri = withContext(Dispatchers.IO) { GpxExport.write(context, listOf(trip)) }
                                if (uri != null) {
                                    val send = Intent(Intent.ACTION_SEND).apply {
                                        type = "application/gpx+xml"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(
                                        Intent.createChooser(send, context.getString(R.string.export)),
                                    )
                                }
                            }
                        }) {
                            Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.export))
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            DetailRow(stringResource(R.string.stat_distance), formatDistance(trip.distanceM))
            DetailRow(stringResource(R.string.stat_time), formatDuration(trip.durationMs))
            DetailRow(
                stringResource(R.string.stat_avg),
                "${formatSpeed(trip.avgSpeedMs, settings.unit, settings.decimals)} ${settings.unit.label}",
            )
            DetailRow(
                stringResource(R.string.stat_max),
                "${formatSpeed(trip.maxSpeedMs, settings.unit, settings.decimals)} ${settings.unit.label}",
            )
            if (trip.chargeAh > 0f || trip.energyWh > 0f) {
                HorizontalDivider()
                DetailRow(stringResource(R.string.stat_consumed), String.format(Locale.getDefault(), "%.1f Ah", trip.chargeAh))
                DetailRow(stringResource(R.string.stat_energy), String.format(Locale.getDefault(), "%.0f Wh", trip.energyWh))
                val km = trip.distanceM / 1000.0
                if (trip.energyWh > 0f && km > 0.05) {
                    DetailRow(
                        stringResource(R.string.stat_efficiency),
                        String.format(Locale.getDefault(), "%.0f Wh/km", trip.energyWh / km),
                    )
                }
            }
            if (trip.hasTrack) {
                HorizontalDivider()
                DetailRow(stringResource(R.string.trip_points), "${trip.points.size}")
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
        Text(value, fontWeight = FontWeight.SemiBold)
    }
}
