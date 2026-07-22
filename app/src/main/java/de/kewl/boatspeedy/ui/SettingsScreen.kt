package de.kewl.boatspeedy.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.kewl.boatspeedy.R
import de.kewl.boatspeedy.data.Settings
import de.kewl.boatspeedy.data.Smoothing
import de.kewl.boatspeedy.data.SpeedUnit
import de.kewl.boatspeedy.data.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: Settings,
    onUnit: (SpeedUnit) -> Unit,
    onDecimals: (Int) -> Unit,
    onTheme: (ThemeMode) -> Unit,
    onKeepScreenOn: (Boolean) -> Unit,
    onSmoothing: (Smoothing) -> Unit,
    onShowSatDetails: (Boolean) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // Einheit
            SegmentedRow(
                label = stringResource(R.string.unit),
                options = SpeedUnit.entries,
                selected = settings.unit,
                labelOf = {
                    if (it == SpeedUnit.KMH) stringResource(R.string.unit_kmh)
                    else stringResource(R.string.unit_kn)
                },
                onSelect = onUnit,
            )
            Divider()

            // Nachkommastellen
            SegmentedRow(
                label = stringResource(R.string.decimals),
                options = listOf(0, 1, 2),
                selected = settings.decimals,
                labelOf = {
                    when (it) {
                        0 -> stringResource(R.string.decimals_0)
                        1 -> stringResource(R.string.decimals_1)
                        else -> stringResource(R.string.decimals_2)
                    }
                },
                onSelect = onDecimals,
            )
            Divider()

            // Design / Theme
            SegmentedRow(
                label = stringResource(R.string.theme),
                options = ThemeMode.entries,
                selected = settings.theme,
                labelOf = {
                    when (it) {
                        ThemeMode.SYSTEM -> stringResource(R.string.theme_system)
                        ThemeMode.LIGHT -> stringResource(R.string.theme_light)
                        ThemeMode.DARK -> stringResource(R.string.theme_dark)
                    }
                },
                onSelect = onTheme,
            )
            Divider()

            // Glättung
            SegmentedRow(
                label = stringResource(R.string.smoothing),
                options = Smoothing.entries,
                selected = settings.smoothing,
                labelOf = {
                    when (it) {
                        Smoothing.OFF -> stringResource(R.string.smoothing_off)
                        Smoothing.LIGHT -> stringResource(R.string.smoothing_light)
                        Smoothing.STRONG -> stringResource(R.string.smoothing_strong)
                    }
                },
                onSelect = onSmoothing,
            )
            Divider()

            // Schalter
            SwitchRow(
                label = stringResource(R.string.keep_screen_on),
                checked = settings.keepScreenOn,
                onCheckedChange = onKeepScreenOn,
            )
            SwitchRow(
                label = stringResource(R.string.show_sat_details),
                checked = settings.showSatDetails,
                onCheckedChange = onShowSatDetails,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> SegmentedRow(
    label: String,
    options: List<T>,
    selected: T,
    labelOf: @Composable (T) -> String,
    onSelect: (T) -> Unit,
) {
    Column(modifier = Modifier.padding(vertical = 12.dp)) {
        Text(text = label, style = MaterialTheme.typography.titleMedium)
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .selectableGroup(),
        ) {
            options.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = option == selected,
                    onClick = { onSelect(option) },
                    shape = SegmentedButtonDefaults.itemShape(index, options.size),
                ) {
                    Text(labelOf(option))
                }
            }
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, style = MaterialTheme.typography.titleMedium)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
