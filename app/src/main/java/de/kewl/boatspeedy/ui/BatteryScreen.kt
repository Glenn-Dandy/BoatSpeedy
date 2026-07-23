package de.kewl.boatspeedy.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.kewl.boatspeedy.R
import de.kewl.boatspeedy.battery.BatteryData
import de.kewl.boatspeedy.battery.BatteryState
import de.kewl.boatspeedy.battery.BmsType
import de.kewl.boatspeedy.battery.ConnectionState
import de.kewl.boatspeedy.battery.ScanDevice
import de.kewl.boatspeedy.battery.estimateRange
import de.kewl.boatspeedy.data.Settings
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatteryScreen(
    state: BatteryState,
    settings: Settings,
    currentSpeedMs: Float?,
    onScan: () -> Unit,
    onConnect: (String) -> Unit,
    onDisconnect: () -> Unit,
    onBms: (BmsType) -> Unit,
    onOpenMenu: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.battery)) },
                navigationIcon = {
                    IconButton(onClick = onOpenMenu) {
                        Icon(Icons.Filled.Menu, contentDescription = stringResource(R.string.menu))
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BmsSelector(settings.batteryBms, connected = state.connection != ConnectionState.DISCONNECTED, onBms)

            when (state.connection) {
                ConnectionState.CONNECTED -> {
                    ConnectedHeader(state, onDisconnect, onScan)
                    state.data?.let { d ->
                        LiveValues(d)
                        RangeCard(d, currentSpeedMs)
                    }
                }
                ConnectionState.SCANNING -> ScanList(state, scanning = true, onScan, onConnect)
                ConnectionState.CONNECTING -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.width(22.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.bat_connecting))
                }
                ConnectionState.DISCONNECTED -> ScanList(state, scanning = false, onScan, onConnect)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BmsSelector(selected: BmsType, connected: Boolean, onBms: (BmsType) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.bms), style = MaterialTheme.typography.titleSmall)
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            BmsType.entries.forEachIndexed { i, t ->
                SegmentedButton(
                    selected = selected == t,
                    onClick = { if (!connected) onBms(t) },
                    enabled = !connected,
                    shape = SegmentedButtonDefaults.itemShape(i, BmsType.entries.size),
                ) { Text(if (t.tested) t.display.substringBefore(" ") else t.display.substringBefore(" ")) }
            }
        }
        if (!selected.tested) {
            Text(
                stringResource(R.string.bms_experimental, selected.display),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun ConnectedHeader(state: BatteryState, onDisconnect: () -> Unit, onScan: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(stringResource(R.string.bat_connected), style = MaterialTheme.typography.titleMedium)
                state.deviceName?.let {
                    Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                OutlinedButton(onClick = onDisconnect) { Text(stringResource(R.string.disconnect)) }
                Spacer(Modifier.width(4.dp))
                Text(
                    stringResource(R.string.switch_battery),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable(onClick = onScan).padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun ScanList(
    state: BatteryState,
    scanning: Boolean,
    onScan: () -> Unit,
    onConnect: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                stringResource(R.string.scan_hint),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.weight(1f),
            )
            if (scanning) {
                CircularProgressIndicator(modifier = Modifier.width(22.dp), strokeWidth = 2.dp)
            } else {
                Button(onClick = onScan) {
                    Text(stringResource(if (state.scanResults.isEmpty()) R.string.scan else R.string.rescan))
                }
            }
        }

        state.error?.let { Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.error) }

        state.scanResults.forEach { dev -> DeviceRow(dev, onConnect) }

        if (!scanning && state.scanResults.isEmpty() && state.error == null) {
            Text(
                stringResource(R.string.no_devices),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
        }
    }
}

@Composable
private fun DeviceRow(dev: ScanDevice, onConnect: (String) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onConnect(dev.address) },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(dev.name ?: dev.address, fontWeight = FontWeight.SemiBold)
                Text(dev.address, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }
            Text("${dev.rssi} dBm", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        }
    }
}

@Composable
private fun LiveValues(d: BatteryData) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ValueRow(stringResource(R.string.bat_soc), "${d.soc} %")
            ValueRow(stringResource(R.string.bat_voltage), fmt(d.voltage, "V"))
            ValueRow(stringResource(R.string.bat_current), fmt(d.currentA, "A"))
            if (d.remainingAh > 0f || d.nominalAh > 0f) {
                ValueRow(stringResource(R.string.bat_remaining), fmt(d.remainingAh, "Ah") + " / " + fmt(d.nominalAh, "Ah"))
            }
            d.tempC?.let { ValueRow(stringResource(R.string.bat_temp), fmt(it, "°C")) }
            if (d.cells.isNotEmpty()) {
                ValueRow(
                    stringResource(R.string.bat_cells),
                    d.cells.joinToString("  ") { String.format(Locale.getDefault(), "%.3f", it) },
                )
            }
        }
    }
}

@Composable
private fun RangeCard(d: BatteryData, currentSpeedMs: Float?) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.bat_range_title), style = MaterialTheme.typography.titleMedium)
            val est = estimateRange(d, currentSpeedMs)
            if (est != null) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Stat(stringResource(R.string.bat_est_range), formatDistance(est.km * 1000.0))
                    Stat(stringResource(R.string.bat_est_time), formatDuration((est.hours * 3600_000).toLong()))
                }
            } else {
                Text(
                    stringResource(R.string.bat_range_hint),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
        }
    }
}

@Composable
private fun ValueRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
        Text(value, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
    }
}

private fun fmt(v: Float, unit: String) = String.format(Locale.getDefault(), "%.2f %s", v, unit)
