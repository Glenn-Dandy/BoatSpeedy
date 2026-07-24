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
import androidx.compose.material.icons.filled.GpsFixed
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
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.kewl.boatspeedy.R
import de.kewl.boatspeedy.data.RangeSmoothing
import de.kewl.boatspeedy.data.Settings
import de.kewl.boatspeedy.data.Smoothing
import de.kewl.boatspeedy.data.SpeedUnit
import de.kewl.boatspeedy.data.ThemeMode
import de.kewl.boatspeedy.location.GpsState
import de.kewl.boatspeedy.util.AppLanguage
import kotlin.math.roundToInt

/* ----------------------------- Übersicht ----------------------------- */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsHomeScreen(
    onDashboard: () -> Unit,
    onGps: () -> Unit,
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
            Icons.Filled.GpsFixed,
            stringResource(R.string.group_gps),
            stringResource(R.string.cat_gps_desc),
            onGps,
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
    onRangeSmoothing: (RangeSmoothing) -> Unit,
    onLowSocPercent: (Int) -> Unit,
    onShowBatteryTile: (Boolean) -> Unit,
    onShowRangeTile: (Boolean) -> Unit,
    onShowMapTile: (Boolean) -> Unit,
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
        SegmentedRow(
            label = stringResource(R.string.range_smoothing),
            options = RangeSmoothing.entries,
            selected = settings.rangeSmoothing,
            labelOf = {
                if (it == RangeSmoothing.OFF) stringResource(R.string.smoothing_off)
                else "${it.windowMs / 1000} s"
            },
            onSelect = onRangeSmoothing,
        )
        LowSocSliderRow(settings.lowSocPercent, onLowSocPercent)
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        SwitchRow(stringResource(R.string.tile_battery), settings.showBatteryTile, onShowBatteryTile)
        SwitchRow(stringResource(R.string.tile_range), settings.showRangeTile, onShowRangeTile)
        SwitchRow(stringResource(R.string.tile_map), settings.showMapTile, onShowMapTile)
        SwitchRow(stringResource(R.string.show_sat_details), settings.showSatDetails, onShowSatDetails)
    }
}

/* -------------------------------- GPS -------------------------------- */

@Composable
fun GpsSettingsScreen(
    gps: GpsState,
    onBack: () -> Unit,
) {
    SettingsScaffold(stringResource(R.string.group_gps), Icons.AutoMirrored.Filled.ArrowBack, onBack) {
        Text(
            stringResource(R.string.gps_live_title),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
        )
        InfoValueRow(
            stringResource(R.string.gps_satellites),
            if (gps.satellitesVisible > 0) "${gps.satellitesUsed}/${gps.satellitesVisible}" else "--",
        )
        InfoValueRow(
            stringResource(R.string.accuracy_label_short),
            gps.accuracyM?.let { "±${it.roundToInt()} m" } ?: "--",
        )
        InfoValueRow(
            stringResource(R.string.gps_course),
            gps.bearingDeg?.let { "${it.roundToInt()}° ${compass(it)}" } ?: "--",
        )
        InfoValueRow(
            stringResource(R.string.gps_signal),
            gps.cn0DbHz?.let { "${it.roundToInt()} dB-Hz" } ?: "--",
        )
        InfoValueRow(
            stringResource(R.string.gps_constellations),
            gps.constellations.takeIf { it.isNotEmpty() }?.joinToString(" · ") ?: "--",
        )
        InfoValueRow(
            stringResource(R.string.gps_altitude),
            gps.altitudeM?.let { "${it.roundToInt()} m" } ?: "--",
        )
        Text(
            stringResource(R.string.gps_provider_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

@Composable
private fun compass(bearing: Float): String {
    val points = stringResource(R.string.compass_points).split(",")
    val idx = (((bearing % 360f) + 360f) % 360f / 45f).roundToInt() % points.size
    return points.getOrElse(idx) { "" }
}

@Composable
private fun InfoValueRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
        Text(value, style = MaterialTheme.typography.titleMedium)
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
private fun LowSocSliderRow(percent: Int, onChange: (Int) -> Unit) {
    var pos by remember(percent) { mutableFloatStateOf(percent.toFloat()) }
    val current = pos.toInt()
    Column(modifier = Modifier.padding(vertical = 10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(stringResource(R.string.low_soc_warn), style = MaterialTheme.typography.titleMedium)
            Text(if (current <= 0) stringResource(R.string.smoothing_off) else "$current %")
        }
        Slider(
            value = pos,
            onValueChange = { pos = it },
            onValueChangeFinished = { onChange(pos.toInt()) },
            valueRange = 0f..50f,
            steps = 9, // 0,5,10,…,50
        )
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
