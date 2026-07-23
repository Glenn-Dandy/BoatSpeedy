package de.kewl.boatspeedy.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.kewl.boatspeedy.R
import de.kewl.boatspeedy.data.Settings
import de.kewl.boatspeedy.data.Smoothing
import de.kewl.boatspeedy.data.SpeedUnit
import de.kewl.boatspeedy.data.ThemeMode
import de.kewl.boatspeedy.util.AppLanguage

/* ----------------------------- Übersicht ----------------------------- */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsHomeScreen(
    onDashboard: () -> Unit,
    onAppearance: () -> Unit,
    onLanguage: () -> Unit,
    onOpenMenu: () -> Unit,
) {
    SettingsScaffold(
        title = stringResource(R.string.settings),
        navigationIcon = Icons.Filled.Menu,
        onNav = onOpenMenu,
    ) {
        CategoryRow(
            Icons.Filled.Dashboard,
            stringResource(R.string.group_dashboard),
            stringResource(R.string.cat_dashboard_desc),
            onDashboard,
        )
        HorizontalDivider()
        CategoryRow(
            Icons.Filled.DarkMode,
            stringResource(R.string.appearance),
            stringResource(R.string.cat_appearance_desc),
            onAppearance,
        )
        HorizontalDivider()
        CategoryRow(
            Icons.Filled.Language,
            stringResource(R.string.language),
            stringResource(R.string.cat_language_desc),
            onLanguage,
        )
    }
}

@Composable
private fun CategoryRow(icon: ImageVector, title: String, desc: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column(modifier = Modifier.weight(1f).padding(start = 16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
    }
}

/* ----------------------------- Dashboard ----------------------------- */

@Composable
fun DashboardSettingsScreen(
    settings: Settings,
    onUnit: (SpeedUnit) -> Unit,
    onDecimals: (Int) -> Unit,
    onSmoothing: (Smoothing) -> Unit,
    onShowBatteryTile: (Boolean) -> Unit,
    onShowRangeTile: (Boolean) -> Unit,
    onShowSatDetails: (Boolean) -> Unit,
    onBack: () -> Unit,
) {
    SettingsScaffold(stringResource(R.string.group_dashboard), Icons.AutoMirrored.Filled.ArrowBack, onBack) {
        SegmentedRow(
            label = stringResource(R.string.unit),
            options = SpeedUnit.entries,
            selected = settings.unit,
            labelOf = { if (it == SpeedUnit.KMH) stringResource(R.string.unit_kmh) else stringResource(R.string.unit_kn) },
            onSelect = onUnit,
        )
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
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        SwitchRow(stringResource(R.string.tile_battery), settings.showBatteryTile, onShowBatteryTile)
        SwitchRow(stringResource(R.string.tile_range), settings.showRangeTile, onShowRangeTile)
        SwitchRow(stringResource(R.string.show_sat_details), settings.showSatDetails, onShowSatDetails)
    }
}

/* ---------------------------- Appearance ----------------------------- */

@Composable
fun AppearanceSettingsScreen(
    settings: Settings,
    onTheme: (ThemeMode) -> Unit,
    onKeepScreenOn: (Boolean) -> Unit,
    onBack: () -> Unit,
) {
    SettingsScaffold(stringResource(R.string.appearance), Icons.AutoMirrored.Filled.ArrowBack, onBack) {
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
        SwitchRow(stringResource(R.string.keep_screen_on), settings.keepScreenOn, onKeepScreenOn)
    }
}

/* ----------------------------- Language ------------------------------ */

@Composable
fun LanguageSettingsScreen(
    language: AppLanguage,
    onLanguage: (AppLanguage) -> Unit,
    onBack: () -> Unit,
) {
    SettingsScaffold(stringResource(R.string.language), Icons.AutoMirrored.Filled.ArrowBack, onBack) {
        SegmentedRow(
            label = stringResource(R.string.language),
            options = AppLanguage.entries,
            selected = language,
            labelOf = {
                when (it) {
                    AppLanguage.ENGLISH -> stringResource(R.string.language_english)
                    AppLanguage.GERMAN -> stringResource(R.string.language_german)
                }
            },
            onSelect = onLanguage,
        )
    }
}

/* ------------------------------ Helpers ------------------------------ */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScaffold(
    title: String,
    navigationIcon: ImageVector,
    onNav: () -> Unit,
    content: @Composable () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onNav) {
                        Icon(navigationIcon, contentDescription = stringResource(R.string.back))
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
        ) { content() }
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
    Column(modifier = Modifier.padding(vertical = 10.dp)) {
        Text(text = label, style = MaterialTheme.typography.titleMedium)
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp).selectableGroup(),
        ) {
            options.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = option == selected,
                    onClick = { onSelect(option) },
                    shape = SegmentedButtonDefaults.itemShape(index, options.size),
                ) { Text(labelOf(option)) }
            }
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, style = MaterialTheme.typography.titleMedium)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
