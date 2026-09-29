package de.kewl.boatspeedy.ui

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Merge
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
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

fun tripDate(epochMs: Long): String =
    SimpleDateFormat("dd.MM.yyyy · HH:mm", Locale.getDefault()).format(Date(epochMs))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripsScreen(
    trips: List<SavedTrip>,
    onOpenDetail: (SavedTrip) -> Unit,
    onDelete: (Set<Long>) -> Unit,
    onMerge: (Set<Long>) -> Unit,
    onImport: (android.net.Uri) -> Unit,
    onOpenMenu: () -> Unit,
    routes: List<de.kewl.boatspeedy.nav.SavedRoute> = emptyList(),
    onOpenRoute: (de.kewl.boatspeedy.nav.SavedRoute) -> Unit = {},
    /** 0 = Fahrten, 1 = Routen. Außen gehalten, damit „zurück" im richtigen Reiter landet. */
    tab: Int = 0,
    onTab: (Int) -> Unit = {},
    onDeleteRoutes: (Set<Long>) -> Unit = {},
) {
    var selection by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var confirmMerge by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val selecting = selection.isNotEmpty()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) onImport(uri) }

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
                        if (tab == 1) {
                            // Routen: teilen und löschen, zusammenführen gibt es nicht.
                            IconButton(onClick = {
                                val chosen = routes.filter { it.id in selection }
                                scope.launch {
                                    val uris = withContext(Dispatchers.IO) { GpxExport.writeRoutes(context, chosen) }
                                    GpxExport.share(context, uris, context.getString(R.string.export))
                                }
                            }) {
                                Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.export))
                            }
                            IconButton(onClick = { confirmDelete = true }) {
                                Icon(Icons.Filled.DeleteOutline, contentDescription = stringResource(R.string.remove))
                            }
                            return@TopAppBar
                        }
                        // Mehrere Fahrten zu einer zusammenführen.
                        if (selection.size >= 2) {
                            IconButton(onClick = { confirmMerge = true }) {
                                Icon(Icons.Filled.Merge, contentDescription = stringResource(R.string.merge_trips))
                            }
                        }
                        IconButton(onClick = {
                            val chosen = trips.filter { it.id in selection }
                            scope.launch {
                                // Je Fahrt eine eigene GPX-Datei teilen.
                                val uris = withContext(Dispatchers.IO) { GpxExport.writeEach(context, chosen) }
                                if (uris.isEmpty()) {
                                    Toast.makeText(context, context.getString(R.string.no_track), Toast.LENGTH_SHORT).show()
                                } else {
                                    val send = if (uris.size == 1) {
                                        Intent(Intent.ACTION_SEND).apply {
                                            type = "application/gpx+xml"
                                            putExtra(Intent.EXTRA_STREAM, uris.first())
                                        }
                                    } else {
                                        Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                                            type = "application/gpx+xml"
                                            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
                                        }
                                    }
                                    // Ohne ClipData gilt die Leseerlaubnis nicht für alle URIs –
                                    // die Ziel-App kann die Dateien sonst nicht öffnen.
                                    send.clipData = android.content.ClipData(
                                        "GPX",
                                        arrayOf("application/gpx+xml"),
                                        android.content.ClipData.Item(uris.first()),
                                    ).also { clip ->
                                        uris.drop(1).forEach { u -> clip.addItem(android.content.ClipData.Item(u)) }
                                    }
                                    send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    context.startActivity(
                                        Intent.createChooser(send, context.getString(R.string.export)),
                                    )
                                }
                            }
                        }) {
                            Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.export))
                        }
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Filled.DeleteOutline, contentDescription = stringResource(R.string.remove))
                        }
                    },
                )
            } else {
                Column {
                TopAppBar(
                    title = { Text(stringResource(R.string.nav_trips)) },
                    navigationIcon = {
                        IconButton(onClick = onOpenMenu) {
                            Icon(Icons.Filled.Menu, contentDescription = stringResource(R.string.menu))
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            // GPX/XML zulassen; manche Dateimanager melden GPX als */*.
                            importLauncher.launch(arrayOf("application/gpx+xml", "application/xml", "text/xml", "*/*"))
                        }) {
                            Icon(Icons.Filled.FileDownload, contentDescription = stringResource(R.string.import_gpx))
                        }
                    },
                )
                TabRow(selectedTabIndex = tab) {
                    Tab(selected = tab == 0, onClick = { selection = emptySet(); onTab(0) }, text = { Text(stringResource(R.string.nav_trips)) })
                    Tab(selected = tab == 1, onClick = { selection = emptySet(); onTab(1) }, text = { Text(stringResource(R.string.routes)) })
                }
                }
            }
        },
    ) { innerPadding ->
        if (tab == 1) {
            RouteList(
                routes = routes,
                selection = selection,
                onToggle = { id -> selection = if (id in selection) selection - id else selection + id },
                onOpen = { r ->
                    if (selecting) {
                        selection = if (r.id in selection) selection - r.id else selection + r.id
                    } else {
                        onOpenRoute(r)
                    }
                },
                modifier = Modifier.padding(innerPadding),
            )
            return@Scaffold
        }
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

    // Zusammenführen lässt sich nicht rückgängig machen – vorher fragen.
    if (confirmMerge) {
        val count = selection.size
        AlertDialog(
            onDismissRequest = { confirmMerge = false },
            title = { Text(stringResource(R.string.merge_trips)) },
            text = { Text(stringResource(R.string.merge_confirm, count)) },
            confirmButton = {
                TextButton(onClick = {
                    onMerge(selection)
                    selection = emptySet()
                    confirmMerge = false
                }) { Text(stringResource(R.string.merge_do)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmMerge = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    // Löschen ebenso – markierte Fahrten waren mit einem Fingertipp weg.
    if (confirmDelete) {
        val count = selection.size
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.remove)) },
            text = {
                Text(
                    if (tab == 1) stringResource(R.string.routes_delete_confirm, count)
                    else stringResource(R.string.delete_confirm, count),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (tab == 1) onDeleteRoutes(selection) else onDelete(selection)
                    selection = emptySet()
                    confirmDelete = false
                }) { Text(stringResource(R.string.delete_do)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
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
                NameUndDatum(trip.name, trip.startedAt)
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
fun TripDetailScreen(
    trip: SavedTrip,
    settings: Settings,
    onShowMap: () -> Unit,
    onBack: () -> Unit,
    onRename: (String) -> Unit = {},
    onNavigate: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var rename by remember { mutableStateOf(false) }
    if (rename) {
        NameDialog(
            title = stringResource(R.string.rename),
            initial = trip.name.orEmpty(),
            onDismiss = { rename = false },
            onConfirm = { onRename(it); rename = false },
        )
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { NameUndDatum(trip.name, trip.startedAt, groesse = 20) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = { rename = true }) {
                        Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.rename))
                    }
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
            DetailRow(stringResource(R.string.stat_total_time), formatDuration(trip.totalMs))
            DetailRow(stringResource(R.string.stat_moving_time), formatDuration(trip.durationMs))
            DetailRow(
                stringResource(R.string.stat_pause),
                formatDuration((trip.totalMs - trip.durationMs).coerceAtLeast(0L)),
            )
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
                // Track direkt als kleine Karte; Antippen öffnet die große Ansicht.
                Box(modifier = Modifier.fillMaxWidth().height(220.dp).clipToBounds()) {
                    TrackMap(
                        trip = trip,
                        interactive = false,
                        showArrows = settings.trackArrows,
                        bubbleText = null,
                        color = settings.trackColor.argb,
                        strokeWidth = settings.trackWidth.px,
                        modifier = Modifier.matchParentSize(),
                    )
                    Box(modifier = Modifier.matchParentSize().clickable(onClick = onShowMap))
                }
                // Die Fahrt noch einmal fahren: erst zum Start, dann dem Track nach.
                Button(onClick = onNavigate, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Navigation, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.route_navigate))
                }
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
