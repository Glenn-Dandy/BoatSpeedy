package de.kewl.boatspeedy.ui

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Kayaking
import androidx.compose.material.icons.filled.DirectionsBoat
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import de.kewl.boatspeedy.data.Craft
import de.kewl.boatspeedy.data.Settings
import de.kewl.boatspeedy.nav.ObstacleKind
import de.kewl.boatspeedy.nav.RouteError
import de.kewl.boatspeedy.nav.SavedRoute
import de.kewl.boatspeedy.trip.SavedTrip
import de.kewl.boatspeedy.trip.TrackPoint
import kotlin.math.roundToInt

/** Warum eine Route nicht zustande kam, als Text. */
fun routeErrorRes(err: RouteError): Int = when (err) {
    RouteError.TOO_FAR -> R.string.nav_err_far
    RouteError.NO_NETWORK -> R.string.nav_err_offline
    RouteError.SERVICE_BUSY -> R.string.nav_err_busy
    RouteError.NO_WATERWAYS -> R.string.nav_err_nodata
    RouteError.NOT_ON_WATER -> R.string.nav_err_notwater
    RouteError.NO_CONNECTION -> R.string.nav_err_unconnected
}

/**
 * Name groß, Datum und Uhrzeit klein darunter. Ohne Namen steht das Datum allein, so
 * wie bisher.
 */
@Composable
fun NameUndDatum(name: String?, epochMs: Long, groesse: Int = 16) {
    Column {
        Text(name ?: tripDate(epochMs), fontWeight = FontWeight.SemiBold, fontSize = groesse.sp, maxLines = 1)
        if (name != null) {
            Text(
                tripDate(epochMs),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                maxLines = 1,
            )
        }
    }
}

/** Nach einem Namen fragen. Leer lassen heißt: ohne Namen, dann zählt das Datum. */
@Composable
fun NameDialog(title: String, initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(60) },
                singleLine = true,
                placeholder = { Text(stringResource(R.string.name_hint)) },
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(text) }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Die Liste der gespeicherten Routen, im zweiten Reiter der Fahrten. */
@Composable
fun RouteList(routes: List<SavedRoute>, onOpen: (SavedRoute) -> Unit, modifier: Modifier = Modifier) {
    if (routes.isEmpty()) {
        Column(
            modifier = modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                stringResource(R.string.routes_empty),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
        }
        return
    }
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        routes.forEach { r ->
            Card(modifier = Modifier.fillMaxWidth().clickable { onOpen(r) }) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        NameUndDatum(r.name, r.createdAt)
                        Text(
                            formatDistance(r.distanceM),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        )
                    }
                    CraftIcon(r.craft)
                }
            }
        }
    }
}

@Composable
private fun CraftIcon(craft: Craft) {
    Icon(
        if (craft == Craft.CANOE) Icons.Filled.Kayaking else Icons.Filled.DirectionsBoat,
        contentDescription = stringResource(if (craft == Craft.CANOE) R.string.craft_canoe else R.string.craft_motorboat),
        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
    )
}

/** Die Strecke als Pseudo-Fahrt, damit die kleine Karte sie zeichnen kann. */
private fun alsFahrt(r: SavedRoute) = SavedTrip(
    id = r.id,
    startedAt = r.createdAt,
    distanceM = r.distanceM,
    durationMs = 0L,
    avgSpeedMs = 0f,
    maxSpeedMs = 0f,
    energyWh = 0f,
    chargeAh = 0f,
    points = r.path.map { TrackPoint(it.lat, it.lon, 0L) },
)

/**
 * Eine gespeicherte Route. Sie wird so gezeigt, wie sie gespeichert wurde, und nicht
 * still neu gerechnet; das macht erst „Neu berechnen".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteDetailScreen(
    route: SavedRoute,
    settings: Settings,
    onNavigate: () -> Unit,
    onRecalc: ((RouteError?) -> Unit) -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var rename by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var rechnet by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { NameUndDatum(route.name, route.createdAt, groesse = 20) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = { rename = true }) {
                        Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.rename))
                    }
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Filled.DeleteOutline, contentDescription = stringResource(R.string.remove))
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
            // Nach „Neu berechnen" bleibt die Kennung gleich; die Karte soll trotzdem neu zeichnen.
            Box(modifier = Modifier.fillMaxWidth().height(240.dp).clipToBounds()) {
                androidx.compose.runtime.key(route.path) {
                TrackMap(
                    trip = alsFahrt(route),
                    interactive = false,
                    showArrows = true,
                    bubbleText = null,
                    color = android.graphics.Color.parseColor("#FB8C00"),
                    strokeWidth = settings.trackWidth.px,
                    modifier = Modifier.matchParentSize(),
                )
                }
            }
            RouteRow(stringResource(R.string.route_length), formatDistance(route.distanceM))
            RouteRow(
                stringResource(R.string.route_craft),
                stringResource(if (route.craft == Craft.CANOE) R.string.craft_canoe else R.string.craft_motorboat),
            )
            val locks = route.obstacles.count { it.kind == ObstacleKind.LOCK || it.kind == ObstacleKind.SLUICE }
            val weirs = route.obstacles.count { it.kind == ObstacleKind.WEIR || it.kind == ObstacleKind.DAM }
            if (locks > 0) RouteRow(stringResource(R.string.route_locks), "$locks")
            if (weirs > 0) RouteRow(stringResource(R.string.route_weirs), "$weirs")
            if (route.portageM >= 1.0) RouteRow(stringResource(R.string.route_portage), "${route.portageM.roundToInt()} m")
            if (route.upstreamM >= 500.0) RouteRow(stringResource(R.string.route_upstream), formatDistance(route.upstreamM))
            if (route.downstreamM >= 500.0) RouteRow(stringResource(R.string.route_downstream), formatDistance(route.downstreamM))
            if (route.craft != settings.craft) {
                HorizontalDivider()
                Text(
                    stringResource(
                        R.string.route_other_craft,
                        stringResource(if (route.craft == Craft.CANOE) R.string.craft_canoe else R.string.craft_motorboat),
                        stringResource(if (settings.craft == Craft.CANOE) R.string.craft_canoe else R.string.craft_motorboat),
                    ),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
            }
            Spacer(Modifier.height(4.dp))
            Button(onClick = onNavigate, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Navigation, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.route_navigate))
            }
            OutlinedButton(
                onClick = {
                    rechnet = true
                    onRecalc { err ->
                        rechnet = false
                        Toast.makeText(
                            context,
                            if (err == null) context.getString(R.string.route_recalculated) else context.getString(routeErrorRes(err)),
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                },
                enabled = !rechnet,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (rechnet) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Filled.Refresh, contentDescription = null)
                }
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.route_recalc))
            }
        }
    }

    if (rename) {
        NameDialog(
            title = stringResource(R.string.rename),
            initial = route.name.orEmpty(),
            onDismiss = { rename = false },
            onConfirm = { onRename(it); rename = false },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.remove)) },
            text = { Text(stringResource(R.string.route_delete_confirm)) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }) { Text(stringResource(R.string.delete_do)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun RouteRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
        Text(value, fontWeight = FontWeight.SemiBold)
    }
}
