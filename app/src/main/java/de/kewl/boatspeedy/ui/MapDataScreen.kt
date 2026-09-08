package de.kewl.boatspeedy.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.kewl.boatspeedy.R
import de.kewl.boatspeedy.nav.MapTiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Kartendaten für unterwegs: einmal laden, danach rechnet das Routing ohne Netz.
 *
 * Der Umkreis ist bewusst großzügig — auf dem Wasser ist der nächste Empfang weit, und
 * Nachladen dort, wo man es braucht, ist genau das, was nicht funktioniert.
 */
@Composable
fun MapDataScreen(
    lat: Double?,
    lon: Double?,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val dir = remember { MapTiles.dir(context.filesDir) }
    val scope = rememberCoroutineScope()

    var stored by remember { mutableStateOf(MapTiles.stored(dir)) }
    var index by remember { mutableStateOf<MapTiles.Index?>(null) }
    var busy by remember { mutableStateOf(false) }
    var done by remember { mutableIntStateOf(0) }
    var total by remember { mutableIntStateOf(0) }
    var failed by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        index = withContext(Dispatchers.IO) { MapTiles.fetchIndex() }
    }

    val wanted = if (lat != null && lon != null) {
        MapTiles.tilesWithin(lat, lon, MapTiles.DEFAULT_RADIUS_KM)
    } else {
        emptyList()
    }
    val missing = remember(stored, wanted) { MapTiles.missing(dir, wanted) }
    val estimate = MapTiles.sizeOf(index, missing)
    // Veraltete Kacheln sind gefährlicher als fehlende: Sie sehen vollständig aus, und
    // das Routing rechnet stillschweigend mit einem Netz, das Lücken hat.
    val veraltet = remember(stored, index) { MapTiles.outdated(dir, index) }

    SettingsScaffold(
        stringResource(R.string.group_mapdata),
        Icons.AutoMirrored.Filled.ArrowBack,
        onBack,
    ) {
        Text(
            stringResource(R.string.mapdata_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
            modifier = Modifier.padding(bottom = 12.dp),
        )

        if (lat == null || lon == null) {
            Text(
                stringResource(R.string.mapdata_no_position),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(bottom = 12.dp),
            )
        }

        if (busy) {
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Text(
                    stringResource(R.string.mapdata_loading, done, total),
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                )
                Spacer(Modifier.size(8.dp))
                LinearProgressIndicator(
                    progress = { if (total == 0) 0f else done.toFloat() / total },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        } else {
            Button(
                onClick = {
                    if (lat == null || lon == null) return@Button
                    failed = false
                    busy = true
                    done = 0
                    total = missing.size
                    scope.launch {
                        var ok = true
                        for (id in missing) {
                            val got = withContext(Dispatchers.IO) { MapTiles.download(dir, id) }
                            if (got == null) ok = false
                            done += 1
                        }
                        stored = withContext(Dispatchers.IO) { MapTiles.stored(dir) }
                        failed = !ok
                        busy = false
                    }
                },
                enabled = lat != null && lon != null && missing.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Download, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text(
                    when {
                        lat == null || lon == null -> stringResource(R.string.mapdata_load)
                        missing.isEmpty() -> stringResource(R.string.mapdata_complete)
                        estimate > 0 -> stringResource(
                            R.string.mapdata_load_size,
                            missing.size,
                            MapTiles.roundUpMb(estimate),
                        )
                        else -> stringResource(R.string.mapdata_load_count, missing.size)
                    },
                )
            }
        }

        if (failed) {
            Text(
                stringResource(R.string.mapdata_failed),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        if (veraltet.isNotEmpty() && !busy) {
            Spacer(Modifier.size(12.dp))
            Text(
                stringResource(R.string.mapdata_outdated, veraltet.size),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.size(8.dp))
            Button(
                onClick = {
                    failed = false
                    busy = true
                    done = 0
                    total = veraltet.size
                    scope.launch {
                        var ok = true
                        for (t in veraltet) {
                            val got = withContext(Dispatchers.IO) { MapTiles.download(dir, t.id) }
                            if (got == null) ok = false
                            done += 1
                        }
                        stored = withContext(Dispatchers.IO) { MapTiles.stored(dir) }
                        failed = !ok
                        busy = false
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.mapdata_refresh, veraltet.size)) }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

        Text(
            stringResource(R.string.mapdata_stored),
            style = MaterialTheme.typography.titleMedium,
        )
        if (stored.isEmpty()) {
            Text(
                stringResource(R.string.mapdata_nothing),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.padding(top = 4.dp),
            )
        } else {
            val bytes = stored.sumOf { it.bytes }
            val age = stored.mapNotNull { it.generated }.minOrNull()
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(
                            R.string.mapdata_summary,
                            stored.size,
                            MapTiles.roundUpMb(bytes),
                        ),
                    )
                    if (age != null) {
                        Text(
                            stringResource(R.string.mapdata_generated, age),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        )
                    }
                }
                TextButton(onClick = {
                    scope.launch {
                        withContext(Dispatchers.IO) { MapTiles.deleteAll(dir) }
                        stored = emptyList()
                    }
                }) { Text(stringResource(R.string.mapdata_delete)) }
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

        Text(
            stringResource(R.string.mapdata_attribution),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )

        if (index == null) {
            Row(
                modifier = Modifier.padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.mapdata_index_loading),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
        }
    }
}
