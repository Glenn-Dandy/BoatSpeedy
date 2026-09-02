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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
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
import de.kewl.boatspeedy.battery.MeterProtocol
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
    onAdd: (ScanDevice, BmsType?) -> Unit,
    onConnect: (String) -> Unit,
    onDisconnect: (String) -> Unit,
    onToggleActive: (String, Boolean) -> Unit,
    onRemove: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onBatteryBms: (String, BmsType) -> Unit,
    onBankMode: (BankMode) -> Unit,
    onMeterCommand: (String, ByteArray) -> Unit,
    onOpenMenu: () -> Unit,
) {
    // Welche Batterie ist gerade aufgeklappt (Detailkarte sichtbar)?
    var expanded by remember { mutableStateOf<String?>(null) }
    var renaming by remember { mutableStateOf<SavedBattery?>(null) }
    var adding by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.battery)) },
                navigationIcon = {
                    IconButton(onClick = onOpenMenu) {
                        Icon(Icons.Filled.Menu, contentDescription = stringResource(R.string.menu))
                    }
                },
                actions = {
                    // Hinzufügen oben rechts – wie der Import-Knopf bei den Fahrten.
                    IconButton(onClick = { adding = true; onScan() }) {
                        Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.add_battery))
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
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
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
                            onRename = { renaming = saved },
                            onRemove = {
                                if (expanded == saved.address) expanded = null
                                onRemove(saved.address)
                            },
                        )
                        if (expanded == saved.address) {
                            BatteryDetailCard(
                                saved = saved,
                                d = hub.links[saved.address]?.data,
                                onBms = { onBatteryBms(saved.address, it) },
                                onMeterCommand = onMeterCommand,
                            )
                        }
                    }
                }
            }

            // --- Hinzufügen / Scannen ---
        }

        if (adding) {
            AddDialog(
                hub = hub,
                alreadySaved = settings.batteries.map { it.address }.toSet(),
                onScan = onScan,
                onAdd = onAdd,
                onDismiss = { adding = false; onStopScan() },
            )
        }

        renaming?.let { target ->
            RenameDialog(
                current = target.name,
                onDismiss = { renaming = null },
                onConfirm = { newName -> onRename(target.address, newName); renaming = null },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BmsSelector(selected: BmsType, locked: Boolean, onBms: (BmsType) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.bms), style = MaterialTheme.typography.titleSmall)
        // Ausklappliste statt Balkenreihe: die Namen passen ausgeschrieben hinein, und
        // ein fünfter Typ sprengt die Reihe nicht mehr.
        ExposedDropdownMenuBox(
            expanded = open && !locked,
            onExpandedChange = { if (!locked) open = !open },
        ) {
            OutlinedTextField(
                value = selected.display,
                onValueChange = {},
                readOnly = true,
                enabled = !locked,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = open) },
                modifier = Modifier
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth(),
            )
            ExposedDropdownMenu(expanded = open && !locked, onDismissRequest = { open = false }) {
                BmsType.entries.forEach { t ->
                    DropdownMenuItem(
                        text = { Text(t.display) },
                        onClick = { onBms(t); open = false },
                    )
                }
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
    onRename: () -> Unit,
    onRemove: () -> Unit,
) {
    val link = live?.link ?: LinkState.DISCONNECTED
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
            // Zeile 1: aktiv + Name + BMS-Typ
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = saved.active, onCheckedChange = onToggleActive)
                Text(
                    saved.name,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    modifier = Modifier.weight(1f).padding(start = 4.dp),
                )
                Text(
                    saved.bms.display,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
            // Zeile 2: Live-Werte
            Text(
                liveSummary(link, live),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                modifier = Modifier.padding(start = 48.dp, top = 2.dp),
            )
            // Zeile 3: Verbinden + Aktionen – große Ziele, nichts gequetscht.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(start = 44.dp, top = 4.dp),
            ) {
                if (link == LinkState.CONNECTING) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                } else {
                    Switch(
                        checked = link == LinkState.CONNECTED,
                        onCheckedChange = { on -> if (on) onConnect() else onDisconnect() },
                    )
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onRename) {
                    Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.rename))
                }
                IconButton(onClick = onRemove) {
                    Icon(Icons.Filled.DeleteOutline, contentDescription = stringResource(R.string.remove))
                }
                IconButton(onClick = onClick) {
                    Icon(
                        if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = null,
                    )
                }
            }
        }
    }
}
/** Die drei Einstellbefehle des Shunt-Messgeräts. */
private enum class MeterCmd { ZERO, FULL, CLEAR }

@Composable
private fun BatteryDetailCard(
    saved: SavedBattery,
    d: BatteryData?,
    onBms: (BmsType) -> Unit,
    onMeterCommand: (String, ByteArray) -> Unit = { _, _ -> },
) {
    var confirmCmd by remember { mutableStateOf<MeterCmd?>(null) }

    confirmCmd?.let { cmd ->
        AlertDialog(
            onDismissRequest = { confirmCmd = null },
            title = { Text(stringResource(R.string.meter_actions)) },
            text = {
                Text(
                    stringResource(
                        when (cmd) {
                            MeterCmd.ZERO -> R.string.meter_zero_confirm
                            MeterCmd.FULL -> R.string.meter_full_confirm
                            MeterCmd.CLEAR -> R.string.meter_clear_confirm
                        },
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onMeterCommand(
                        saved.address,
                        when (cmd) {
                            MeterCmd.ZERO -> MeterProtocol.Commands.ZERO_CURRENT
                            MeterCmd.FULL -> MeterProtocol.Commands.SET_FULL
                            MeterCmd.CLEAR -> MeterProtocol.Commands.CLEAR_ENERGY
                        },
                    )
                    confirmCmd = null
                }) { Text(stringResource(R.string.meter_send)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmCmd = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // BMS zuerst – falls die automatische Erkennung danebenlag, hier umstellen.
            BmsSelector(saved.bms, locked = false, onBms)
            // Danach die Gerätedaten: Typ (BLE-Name) und MAC-Adresse.
            saved.bleName?.takeIf { it.isNotBlank() }?.let {
                ValueRow(stringResource(R.string.bat_type), it)
            }
            // Nennkapazität als Typenschild-Angabe: abgeschnitten, nicht gerundet. Das BMS
            // hinterlegt sie krumm (Redodo: 100,99 Ah), gemeint sind die glatten 100 Ah.
            d?.nominalAh?.takeIf { it > 0f }?.let {
                ValueRow(
                    stringResource(R.string.bat_nominal),
                    String.format(Locale.getDefault(), "%d Ah", it.toInt()),
                )
            }
            ValueRow(stringResource(R.string.bat_mac), saved.address)
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
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
            // Rest und Temperatur immer zeigen – „--", solange das BMS nichts liefert.
            // Die Nennkapazität nur als Bezug zeigen, solange sie einer sein kann.
            // Manche BMS (Redodo) haben eine zu klein hinterlegte Kapazität, dann steht
            // dort mehr Rest als Maximum – das liest sich wie ein Fehler und hilft niemandem.
            ValueRow(
                stringResource(R.string.bat_remaining),
                when {
                    d.remainingAh <= 0f && d.nominalAh <= 0f -> "--"
                    d.nominalAh >= d.remainingAh && d.nominalAh > 0f ->
                        fmt(d.remainingAh, "Ah") + " / " + fmt(d.nominalAh, "Ah")
                    else -> fmt(d.remainingAh, "Ah")
                },
            )
            ValueRow(
                stringResource(R.string.bat_temp),
                d.tempC?.let { fmt(it, "°C") } ?: "--",
            )
            d.energyKWh?.let {
                ValueRow(stringResource(R.string.bat_energy_total), fmt(it, "kWh"))
            }
            if (saved.bms == BmsType.METER) {
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                // Die Befehle verstellen den Zähler im Gerät und lassen sich nicht
                // zurücknehmen – deshalb wird vorher gefragt, wie beim Löschen von Fahrten.
                Text(stringResource(R.string.meter_actions), style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { confirmCmd = MeterCmd.ZERO }) {
                        Text(stringResource(R.string.meter_zero))
                    }
                    TextButton(onClick = { confirmCmd = MeterCmd.FULL }) {
                        Text(stringResource(R.string.meter_full))
                    }
                    TextButton(onClick = { confirmCmd = MeterCmd.CLEAR }) {
                        Text(stringResource(R.string.meter_clear))
                    }
                }
            }
            if (d.cycles != null || d.dischargedAhTotal != null) {
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                Text(stringResource(R.string.bat_history), style = MaterialTheme.typography.titleSmall)
                d.cycles?.let { ValueRow(stringResource(R.string.bat_cycles), "$it") }
                d.dischargedAhTotal?.let {
                    ValueRow(stringResource(R.string.bat_discharged_total), fmt0(it, "Ah"))
                }
            }
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

/** Umbenennen/Entfernen – im aufgeklappten Detail, damit die Zeile schlank bleibt. */
@Composable
private fun RenameDialog(current: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.rename)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(24) },
                singleLine = true,
                label = { Text(stringResource(R.string.bat_name)) },
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(text) }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/**
 * „+"-Dialog: sucht alle unterstützten BMS gleichzeitig. Der Typ wird aus der beworbenen
 * Bluetooth-Kennung erkannt; ist er unbekannt, fragt der Dialog beim Antippen nach.
 */
@Composable
private fun AddDialog(
    hub: BatteryHub,
    alreadySaved: Set<String>,
    onScan: () -> Unit,
    onAdd: (ScanDevice, BmsType?) -> Unit,
    onDismiss: () -> Unit,
) {
    var askType by remember { mutableStateOf<ScanDevice?>(null) }
    var showOthers by remember { mutableStateOf(false) }
    val results = hub.scanResults.filter { it.address !in alreadySaved }
    // Erkannte zuerst; alles Übrige wandert hinter „Weitere Geräte". Seit ungefiltert
    // gescannt wird, liegen dort auch Kopfhörer und Uhren — aber eben auch Akkus und
    // Messgeräte, die keine Service-UUID bewerben und sonst unsichtbar blieben.
    val known = results.filter { it.bms != null }
    val others = results.filter { it.bms == null }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_battery)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (hub.scanning) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text(stringResource(R.string.bat_scanning), modifier = Modifier.padding(start = 8.dp))
                    }
                }
                hub.error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }
                if (results.isEmpty() && !hub.scanning) {
                    Text(
                        stringResource(R.string.no_devices),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                }
                known.forEach { dev ->
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable {
                            if (dev.bms != null) { onAdd(dev, dev.bms); onDismiss() } else askType = dev
                        },
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    de.kewl.boatspeedy.data.shortBatteryName(dev.name, dev.address),
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    dev.address,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                )
                            }
                            Text(
                                dev.bms?.display ?: stringResource(R.string.bms_unknown),
                                fontSize = 12.sp,
                                color = if (dev.bms != null) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            )
                        }
                    }
                }

                if (others.isNotEmpty()) {
                    TextButton(onClick = { showOthers = !showOthers }) {
                        Text(
                            stringResource(
                                if (showOthers) R.string.bat_hide_others else R.string.bat_show_others,
                                others.size,
                            ),
                        )
                    }
                    if (showOthers) {
                        others.forEach { dev ->
                            Card(
                                modifier = Modifier.fillMaxWidth().clickable { askType = dev },
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(dev.name ?: dev.address, fontWeight = FontWeight.SemiBold)
                                        Text(
                                            dev.address,
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                        )
                                    }
                                    Text(
                                        "${dev.rssi} dBm",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onScan) { Text(stringResource(R.string.rescan)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.back)) } },
    )

    // Typ nicht erkannt -> nachfragen.
    askType?.let { dev ->
        AlertDialog(
            onDismissRequest = { askType = null },
            title = { Text(stringResource(R.string.bms)) },
            text = {
                Column {
                    Text(stringResource(R.string.bms_pick_hint), fontSize = 13.sp)
                    BmsType.entries.forEach { t ->
                        TextButton(onClick = { onAdd(dev, t); askType = null; onDismiss() }) { Text(t.display) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { askType = null }) { Text(stringResource(R.string.cancel)) } },
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
