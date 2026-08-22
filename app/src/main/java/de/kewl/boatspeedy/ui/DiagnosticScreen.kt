package de.kewl.boatspeedy.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import de.kewl.boatspeedy.R
import de.kewl.boatspeedy.battery.BleDiagnostics
import de.kewl.boatspeedy.battery.DiagDevice
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Versteckte BLE-Diagnose für Fehlerberichte zu unbekannten BMS. Scannt ungefiltert,
 * probiert auf dem gewählten Gerät alle bekannten Protokolle durch und liefert ein
 * Protokoll zum Teilen – damit Melder ohne Scanner-App brauchbare Daten liefern können.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticScreen(
    onScanPermission: (() -> Unit) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val diag = remember { BleDiagnostics(context) }
    val devices = remember { mutableStateListOf<DiagDevice>() }
    val lines = remember { mutableStateListOf<String>() }
    var scanning by remember { mutableStateOf(false) }
    var running by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(false) }
    var chosen by remember { mutableStateOf<DiagDevice?>(null) }

    DisposableEffect(Unit) {
        onDispose { diag.stopScan(); diag.cancel() }
    }

    val logState = rememberScrollState()
    LaunchedEffect(lines.size) { logState.animateScrollTo(logState.maxValue) }

    fun scan() = onScanPermission {
        devices.clear()
        scanning = true
        val ok = diag.startScan(
            onResults = { list -> devices.clear(); devices.addAll(list) },
            onStopped = { scanning = false },
        )
        if (!ok) scanning = false
    }

    fun start(device: DiagDevice) = onScanPermission {
        diag.stopScan()
        scanning = false
        chosen = device
        lines.clear()
        running = true
        done = false
        diag.run(
            device = device,
            onLine = { lines.add(it) },
            onDone = { running = false; done = true },
        )
    }

    fun share() {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, "boatspeedy-ble-$stamp.txt")
        file.writeText(diag.report)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, context.getString(R.string.diag_share)))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.diag_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { inner ->
        Column(
            modifier = Modifier
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Text(
                stringResource(R.string.diag_intro),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 12.dp),
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { scan() }, enabled = !scanning && !running) {
                    Icon(Icons.AutoMirrored.Filled.BluetoothSearching, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.scan))
                }
                if (scanning || running) {
                    Spacer(Modifier.size(16.dp))
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                }
            }

            if (devices.isNotEmpty() && !running && !done) {
                Spacer(Modifier.size(8.dp))
                Text(
                    stringResource(R.string.diag_pick),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
                devices.forEach { d ->
                    HorizontalDivider()
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { start(d) }
                            .padding(vertical = 10.dp),
                    ) {
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text(d.name ?: stringResource(R.string.diag_no_name), style = MaterialTheme.typography.titleSmall)
                            Text("${d.rssi} dBm", style = MaterialTheme.typography.bodySmall)
                        }
                        Text(
                            d.address,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        )
                        if (d.services.isNotEmpty()) {
                            Text(
                                d.services.joinToString(", "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            )
                        }
                    }
                }
            }

            if (lines.isNotEmpty()) {
                Spacer(Modifier.size(16.dp))
                chosen?.let {
                    Text(
                        "${it.name ?: stringResource(R.string.diag_no_name)} · ${it.address}",
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
                Spacer(Modifier.size(8.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 200.dp, max = 420.dp)
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant,
                            RoundedCornerShape(12.dp),
                        )
                        .verticalScroll(logState)
                        .padding(10.dp),
                ) {
                    Column(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        lines.forEach { l ->
                            Text(
                                l,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                maxLines = 1,
                                softWrap = false,
                            )
                        }
                    }
                }
                Spacer(Modifier.size(12.dp))
                Row {
                    OutlinedButton(onClick = { share() }, enabled = !running) {
                        Icon(Icons.Filled.Share, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text(stringResource(R.string.diag_share))
                    }
                    if (running) {
                        Spacer(Modifier.size(12.dp))
                        OutlinedButton(onClick = { diag.cancel() }) { Text(stringResource(R.string.cancel)) }
                    }
                }
                Spacer(Modifier.size(24.dp))
            }
        }
    }
}
