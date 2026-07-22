package de.kewl.boatspeedy.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
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
import androidx.compose.material3.Button
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.kewl.boatspeedy.R
import de.kewl.boatspeedy.battery.BatteryState
import de.kewl.boatspeedy.battery.ConnectionState
import de.kewl.boatspeedy.data.Settings
import java.util.Locale

private val BATTERY_TYPES = listOf("LiFePO4", "Li-Ion", "Blei/AGM")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatteryScreen(
    state: BatteryState,
    settings: Settings,
    currentSpeedMs: Float?,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onManufacturer: (String) -> Unit,
    onType: (String) -> Unit,
    onCapacity: (Int) -> Unit,
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
            ConnectionCard(state, onConnect, onDisconnect)

            state.data?.let { d ->
                LiveValues(d, settings)
                RangeCard(d, currentSpeedMs)
            }

            HorizontalDivider()
            ConfigSection(settings, onManufacturer, onType, onCapacity)
        }
    }
}

@Composable
private fun ConnectionCard(state: BatteryState, onConnect: () -> Unit, onDisconnect: () -> Unit) {
    val statusText = when (state.connection) {
        ConnectionState.SCANNING -> stringResource(R.string.bat_scanning)
        ConnectionState.CONNECTING -> stringResource(R.string.bat_connecting)
        ConnectionState.CONNECTED -> stringResource(R.string.bat_connected)
        ConnectionState.DISCONNECTED -> stringResource(R.string.bat_disconnected)
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(statusText, style = MaterialTheme.typography.titleMedium)
                state.deviceName?.let { Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)) }
                state.error?.let { Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.error) }
            }
            when (state.connection) {
                ConnectionState.CONNECTED ->
                    OutlinedButton(onClick = onDisconnect) { Text(stringResource(R.string.disconnect)) }
                ConnectionState.SCANNING, ConnectionState.CONNECTING ->
                    CircularProgressIndicator(modifier = Modifier.width(24.dp), strokeWidth = 2.dp)
                ConnectionState.DISCONNECTED ->
                    Button(onClick = onConnect) { Text(stringResource(R.string.connect)) }
            }
        }
    }
}

@Composable
private fun LiveValues(d: de.kewl.boatspeedy.battery.BatteryData, settings: Settings) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ValueRow(stringResource(R.string.bat_soc), "${d.soc} %")
            ValueRow(stringResource(R.string.bat_voltage), fmt(d.voltage, "V"))
            ValueRow(stringResource(R.string.bat_current), fmt(d.currentA, "A"))
            ValueRow(stringResource(R.string.bat_remaining), fmt(d.remainingAh, "Ah") + " / " + fmt(d.nominalAh, "Ah"))
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
private fun RangeCard(d: de.kewl.boatspeedy.battery.BatteryData, currentSpeedMs: Float?) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.bat_range_title), style = MaterialTheme.typography.titleMedium)

            val speedKmh = (currentSpeedMs ?: 0f) * 3.6f
            val dischargeA = d.dischargeA
            if (dischargeA > 0.1f && speedKmh > 0.1f) {
                val hours = d.remainingAh / dischargeA
                val km = hours * speedKmh
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Stat(stringResource(R.string.bat_est_range), formatDistance(km * 1000.0))
                    Stat(stringResource(R.string.bat_est_time), formatDuration((hours * 3600_000).toLong()))
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConfigSection(
    settings: Settings,
    onManufacturer: (String) -> Unit,
    onType: (String) -> Unit,
    onCapacity: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = settings.batteryManufacturer,
            onValueChange = onManufacturer,
            label = { Text(stringResource(R.string.bat_manufacturer)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Text(stringResource(R.string.bat_type), style = MaterialTheme.typography.titleSmall)
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            BATTERY_TYPES.forEachIndexed { i, t ->
                SegmentedButton(
                    selected = settings.batteryType == t,
                    onClick = { onType(t) },
                    shape = SegmentedButtonDefaults.itemShape(i, BATTERY_TYPES.size),
                ) { Text(t) }
            }
        }

        OutlinedTextField(
            value = settings.batteryCapacityAh.toString(),
            onValueChange = { v -> v.filter { it.isDigit() }.take(4).toIntOrNull()?.let(onCapacity) },
            label = { Text(stringResource(R.string.bat_capacity)) },
            suffix = { Text("Ah") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
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
