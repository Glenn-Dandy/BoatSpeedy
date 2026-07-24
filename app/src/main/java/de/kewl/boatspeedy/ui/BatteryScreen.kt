package de.kewl.boatspeedy.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.kewl.boatspeedy.R
import de.kewl.boatspeedy.battery.BatteryData
import de.kewl.boatspeedy.battery.BatteryHub
import de.kewl.boatspeedy.battery.BatteryLive
import de.kewl.boatspeedy.battery.BmsType
import de.kewl.boatspeedy.battery.LinkState
import de.kewl.boatspeedy.battery.ScanDevice
import de.kewl.boatspeedy.data.BankMode
import de.kewl.boatspeedy.data.SavedBattery
import de.kewl.boatspeedy.data.Settings
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatteryScreen(
    settings: Settings,
    hub: BatteryHub,
    onScan: () -> Unit,
    onStopScan: () -> Unit,
    onAdd: (ScanDevice) -> Unit,
    onConnect: (String) -> Unit,
    onDisconnect: (String) -> Unit,
    onToggleActive: (String, Boolean) -> Unit,
    onRemove: (String) -> Unit,
    onBms: (BmsType) -> Unit,
    onBankMode: (BankMode) -> Unit,
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
        // Welche Batterie ist gerade aufgeklappt (Detailkarte sichtbar)?
        var expanded by remember { mutableStateOf<String?>(null) }

        Column(
            modifier = Modifier
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val anyLink = hub.links.isNotEmpty()

            BmsSelector(settings.batteryBms, locked = anyLink, onBms)
            BankModeSelector(settings.bankMode, onBankMode)

            // --- Gespeicherte Batterien ---
            Text(stringResource(R.string.bat_list_title), style = MaterialTheme.typography.titleSmall)
            if (settings.batteries.isEmpty()) {
                Text(
                    stringResource(R.string.bat_none),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    settings.batteries.forEach { saved ->
                        BatteryRow(
                            saved = saved,
                            live = hub.links[saved.address],
                            expanded = expanded == saved.address,
                            onClick = { expanded = if (expanded == saved.address) null else saved.address },
                            onToggleActive = { onToggleActive(saved.address, it) },
                            onConnect = { onConnect(saved.address) },
                            onDisconnect = { onDisconnect(saved.address) },
                            onRemove = {
                                if (expanded == saved.address) expanded = null
                                onRemove(saved.address)
                            },
                        )
                        if (expanded == saved.address) {
                            BatteryDetailCard(hub.links[saved.address]?.data)
                        }
                    }
                }
            }

            // --- Hinzufügen / Scannen ---
            AddSection(hub, alreadySaved = settings.batteries.map { it.address }.toSet(), onScan, onStopScan, onAdd)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BmsSelector(selected: BmsType, locked: Boolean, onBms: (BmsType) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.bms), style = MaterialTheme.typography.titleSmall)
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            BmsType.entries.forEachIndexed { i, t ->
                SegmentedButton(
                    selected = selected == t,
                    onClick = { if (!locked) onBms(t) },
                    enabled = !locked,
                    shape = SegmentedButtonDefaults.itemShape(i, BmsType.entries.size),
                ) { Text(t.display.substringBefore(" ")) }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BankModeSelector(selected: BankMode, onBankMode: (BankMode) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.bank_mode), style = MaterialTheme.typography.titleSmall)
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            BankMode.entries.forEachIndexed { i, m ->
                SegmentedButton(
                    selected = selected == m,
                    onClick = { onBankMode(m) },
                    shape = SegmentedButtonDefaults.itemShape(i, BankMode.entries.size),
                ) { Text(bankModeLabel(m)) }
            }
        }
    }
}

@Composable
private fun BatteryRow(
    saved: SavedBattery,
    live: BatteryLive?,
    expanded: Boolean,
    onClick: () -> Unit,
    onToggleActive: (Boolean) -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onRemove: () -> Unit,
) {
    val link = live?.link ?: LinkState.DISCONNECTED
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = saved.active, onCheckedChange = onToggleActive)
            Column(modifier = Modifier.weight(1f).padding(start = 4.dp)) {
                Text(saved.name, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(
                    liveSummary(link, live),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    maxLines = 1,
                )
            }
            when (link) {
                LinkState.CONNECTED ->
                    OutlinedButton(onClick = onDisconnect) { Text(stringResource(R.string.disconnect)) }
                LinkState.CONNECTING ->
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                LinkState.DISCONNECTED ->
                    Button(onClick = onConnect) { Text(stringResource(R.string.connect)) }
            }
            Icon(
                if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = null,
                modifier = Modifier.padding(start = 4.dp),
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
            IconButton(onClick = onRemove) {
                Icon(Icons.Filled.DeleteOutline, contentDescription = stringResource(R.string.remove))
            }
        }
    }
}

/** Ausführlicher Batterie-Status (aufgeklappt): alle Werte plus Zellspannungen. */
@Composable
private fun BatteryDetailCard(d: BatteryData?) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (d == null) {
                Text(
                    stringResource(R.string.bat_detail_hint),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
                return@Column
            }
            ValueRow(stringResource(R.string.bat_power), fmt0(kotlin.math.abs(d.powerW), "W"))
            ValueRow(stringResource(R.string.bat_voltage), fmt(d.voltage, "V"))
            ValueRow(stringResource(R.string.bat_current), fmt(d.currentA, "A"))
            ValueRow(stringResource(R.string.bat_soc), "${d.soc} %")
            if (d.remainingAh > 0f || d.nominalAh > 0f) {
                ValueRow(stringResource(R.string.bat_remaining), fmt(d.remainingAh, "Ah") + " / " + fmt(d.nominalAh, "Ah"))
            }
            d.tempC?.let { ValueRow(stringResource(R.string.bat_temp), fmt(it, "°C")) }
            if (d.cells.isNotEmpty()) {
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                Text(stringResource(R.string.bat_cells), style = MaterialTheme.typography.titleSmall)
                d.cells.forEachIndexed { i, v ->
                    ValueRow(
                        stringResource(R.string.bat_cell_n, i + 1),
                        String.format(Locale.getDefault(), "%.3f V", v),
                    )
                }
            }
        }
    }
}

@Composable
private fun AddSection(
    hub: BatteryHub,
    alreadySaved: Set<String>,
    onScan: () -> Unit,
    onStopScan: () -> Unit,
    onAdd: (ScanDevice) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            if (hub.scanning) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.bat_scanning), fontSize = 13.sp)
                }
                OutlinedButton(onClick = onStopScan) { Text(stringResource(R.string.trip_stop)) }
            } else {
                Text(
                    stringResource(R.string.scan_hint),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.weight(1f),
                )
                Button(onClick = onScan) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.add_battery))
                }
            }
        }

        hub.error?.let { Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.error) }

        // Gefundene, noch nicht gespeicherte Geräte → antippen zum Übernehmen.
        hub.scanResults.filter { it.address !in alreadySaved }.forEach { dev ->
            Card(modifier = Modifier.fillMaxWidth().clickable { onAdd(dev) }) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
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
private fun liveSummary(link: LinkState, live: BatteryLive?): String = when (link) {
    LinkState.CONNECTING -> stringResource(R.string.bat_connecting)
    LinkState.DISCONNECTED -> stringResource(R.string.bat_disconnected)
    LinkState.CONNECTED -> live?.data?.let { d ->
        String.format(Locale.getDefault(), "%.2f V · %.1f A · %d %%", d.voltage, d.currentA, d.soc)
    } ?: stringResource(R.string.bat_connected)
}

@Composable
private fun bankModeLabel(mode: BankMode): String = stringResource(
    when (mode) {
        BankMode.SINGLE -> R.string.mode_single
        BankMode.PARALLEL -> R.string.mode_parallel
        BankMode.SERIES -> R.string.mode_series
    },
)

private fun fmt(v: Float, unit: String) = String.format(Locale.getDefault(), "%.2f %s", v, unit)
private fun fmt0(v: Float, unit: String) = String.format(Locale.getDefault(), "%.0f %s", v, unit)
