package de.kewl.boatspeedy.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeveloperMode
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import de.kewl.boatspeedy.R
import de.kewl.boatspeedy.data.AlarmSound
import de.kewl.boatspeedy.data.NotifField
import de.kewl.boatspeedy.data.RangeSmoothing
import de.kewl.boatspeedy.data.Settings
import de.kewl.boatspeedy.data.Smoothing
import de.kewl.boatspeedy.data.SpeedUnit
import de.kewl.boatspeedy.data.ThemeMode
import de.kewl.boatspeedy.data.TrackColor
import de.kewl.boatspeedy.data.TrackWidth
import de.kewl.boatspeedy.location.GpsState
import de.kewl.boatspeedy.util.AppLanguage
import kotlin.math.roundToInt

/* ----------------------------- Übersicht ----------------------------- */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsHomeScreen(
    onDashboard: () -> Unit,
    onNotifications: () -> Unit,
    onGeneral: () -> Unit,
    onAppearance: () -> Unit,
    onTracks: () -> Unit,
    onGps: () -> Unit,
    onOpenMenu: () -> Unit,
    showDeveloper: Boolean = false,
    onDeveloper: () -> Unit = {},
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
            Icons.Filled.Notifications,
            stringResource(R.string.notif_section),
            stringResource(R.string.cat_notif_desc),
            onNotifications,
        )
        HorizontalDivider()
        CategoryRow(
            Icons.Filled.Tune,
            stringResource(R.string.group_general),
            stringResource(R.string.cat_general_desc),
            onGeneral,
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
            Icons.Filled.Route,
            stringResource(R.string.group_tracks),
            stringResource(R.string.cat_tracks_desc),
            onTracks,
        )
        HorizontalDivider()
        CategoryRow(
            Icons.Filled.GpsFixed,
            stringResource(R.string.group_gps),
            stringResource(R.string.cat_gps_desc),
            onGps,
        )
        // Nur nach dem Freischalten in „Über" (im DEV-Build immer sichtbar).
        if (showDeveloper) {
            HorizontalDivider()
            CategoryRow(
                Icons.Filled.DeveloperMode,
                stringResource(R.string.group_developer),
                stringResource(R.string.cat_developer_desc),
                onDeveloper,
            )
        }
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
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        SwitchRow(stringResource(R.string.tile_battery), settings.showBatteryTile, onShowBatteryTile)
        SwitchRow(stringResource(R.string.tile_range), settings.showRangeTile, onShowRangeTile)
        SwitchRow(stringResource(R.string.tile_map), settings.showMapTile, onShowMapTile)
        SwitchRow(stringResource(R.string.show_sat_details), settings.showSatDetails, onShowSatDetails)

    }
}

/* -------------------------- Benachrichtigung -------------------------- */

@Composable
fun NotificationSettingsScreen(
    settings: Settings,
    onNotifEnabled: (Boolean) -> Unit,
    onNotifAlways: (Boolean) -> Unit,
    onNotifFields: (Set<NotifField>) -> Unit,
    onBack: () -> Unit,
) {
    SettingsScaffold(stringResource(R.string.notif_section), Icons.AutoMirrored.Filled.ArrowBack, onBack) {
        SwitchRow(stringResource(R.string.notif_enabled), settings.notifEnabled, onNotifEnabled)
        if (settings.notifEnabled) {
            SwitchRow(stringResource(R.string.notif_always), settings.notifAlways, onNotifAlways)
            Text(
                stringResource(R.string.notif_always_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SectionLabel(stringResource(R.string.notif_fields_title))
            Text(
                stringResource(R.string.notif_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
            NotifField.entries.forEach { f ->
                SwitchRow(notifFieldLabel(f), f in settings.notifFields) { on ->
                    onNotifFields(if (on) settings.notifFields + f else settings.notifFields - f)
                }
            }
        }
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        Text(
            stringResource(R.string.notif_others),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
    }
}

@Composable
private fun notifFieldLabel(f: NotifField): String = when (f) {
    NotifField.SPEED -> stringResource(R.string.speed_label)
    NotifField.DISTANCE -> stringResource(R.string.stat_distance)
    NotifField.TIME -> stringResource(R.string.stat_time)
    NotifField.CHARGE_AH -> stringResource(R.string.stat_consumed)
    NotifField.ENERGY_WH -> stringResource(R.string.stat_energy)
    NotifField.SOC -> stringResource(R.string.soc_short)
    NotifField.RANGE -> stringResource(R.string.bat_est_range)
    NotifField.TIME_LEFT -> stringResource(R.string.bat_est_time)
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

/* ------------------------------ Tracks ------------------------------- */

@Composable
fun TracksSettingsScreen(
    settings: Settings,
    onTrackColor: (TrackColor) -> Unit,
    onTrackWidth: (TrackWidth) -> Unit,
    onTrackArrows: (Boolean) -> Unit,
    onAutoPauseOn: (Boolean) -> Unit,
    onAutoPauseAmps: (Float) -> Unit,
    onAutoPauseSpeedMs: (Float) -> Unit,
    onBack: () -> Unit,
) {
    SettingsScaffold(stringResource(R.string.group_tracks), Icons.AutoMirrored.Filled.ArrowBack, onBack) {
        SegmentedRow(
            label = stringResource(R.string.track_color),
            options = TrackColor.entries,
            selected = settings.trackColor,
            labelOf = {
                when (it) {
                    TrackColor.BLUE -> stringResource(R.string.color_blue)
                    TrackColor.RED -> stringResource(R.string.color_red)
                    TrackColor.BLACK -> stringResource(R.string.color_black)
                }
            },
            onSelect = onTrackColor,
        )
        SegmentedRow(
            label = stringResource(R.string.track_width),
            options = TrackWidth.entries,
            selected = settings.trackWidth,
            labelOf = {
                when (it) {
                    TrackWidth.THIN -> stringResource(R.string.width_thin)
                    TrackWidth.NORMAL -> stringResource(R.string.width_normal)
                    TrackWidth.THICK -> stringResource(R.string.width_thick)
                }
            },
            onSelect = onTrackWidth,
        )
        SwitchRow(stringResource(R.string.track_arrows), settings.trackArrows, onTrackArrows)

        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        SwitchRow(stringResource(R.string.auto_pause_on), settings.autoPauseOn, onAutoPauseOn)
        if (settings.autoPauseOn) {
            AutoPauseField(settings.autoPauseAmps, onAutoPauseAmps)
            AutoPauseSpeedField(settings, onAutoPauseSpeedMs)
        }
    }
}

/* ---------------------------- Appearance ----------------------------- */

@Composable
fun AppearanceSettingsScreen(
    settings: Settings,
    onTheme: (ThemeMode) -> Unit,
    onKeepScreenOn: (Boolean) -> Unit,
    language: AppLanguage,
    onLanguage: (AppLanguage) -> Unit,
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
        LanguageRow(language, onLanguage)
    }
}

/** Wiederverwendbarer Sprach-Umschalter (Darstellung + „Über"). */
@Composable
fun LanguageRow(language: AppLanguage, onLanguage: (AppLanguage) -> Unit) {
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

/* ----------------------------- Allgemein ----------------------------- */

@Composable
fun GeneralSettingsScreen(
    settings: Settings,
    onLowSocPercent: (Int) -> Unit,
    onAnchorAlarmOn: (Boolean) -> Unit,
    onAnchorSound: (AlarmSound) -> Unit,
    onSocAlarmOn: (Boolean) -> Unit,
    onSocSound: (AlarmSound) -> Unit,
    onChargeTargetSoc: (Int) -> Unit,
    onWeatherEnabled: (Boolean) -> Unit,
    onWeatherAlarmOn: (Boolean) -> Unit,
    onWeatherSound: (AlarmSound) -> Unit,
    onTestAnchor: () -> Unit,
    onTestSoc: () -> Unit,
    onTestWeather: () -> Unit,
    onBack: () -> Unit,
) {
    SettingsScaffold(stringResource(R.string.group_general), Icons.AutoMirrored.Filled.ArrowBack, onBack) {
        // --- Ladestand (Warnung, SoC-Alarmton, Lade-Meldung gehören zusammen) ---
        SectionLabel(stringResource(R.string.section_charge))
        LowSocSliderRow(settings.lowSocPercent, onLowSocPercent)
        ChargeTargetSliderRow(settings.chargeTargetSoc, onChargeTargetSoc)
        SwitchRow(stringResource(R.string.soc_alarm_sound), settings.socAlarmOn, onSocAlarmOn)
        AlarmSoundRow(stringResource(R.string.soc_sound_label), settings.socSound, onSocSound)
        Button(onClick = onTestSoc) { Text(stringResource(R.string.alarm_test)) }

        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        // --- Anker ---
        SectionLabel(stringResource(R.string.anchor_watch_title))
        SwitchRow(stringResource(R.string.anchor_alarm_sound), settings.anchorAlarmOn, onAnchorAlarmOn)
        AlarmSoundRow(stringResource(R.string.anchor_sound_label), settings.anchorSound, onAnchorSound)
        Button(onClick = onTestAnchor) { Text(stringResource(R.string.alarm_test)) }

        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        // --- Wetter ---
        SectionLabel(stringResource(R.string.group_weather))
        SwitchRow(stringResource(R.string.weather_enabled), settings.weatherWarnEnabled, onWeatherEnabled)
        Text(
            stringResource(R.string.weather_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
        if (settings.weatherWarnEnabled) {
            SwitchRow(stringResource(R.string.weather_alarm_sound), settings.weatherAlarmOn, onWeatherAlarmOn)
            AlarmSoundRow(stringResource(R.string.weather_sound_label), settings.weatherSound, onWeatherSound)
            Button(onClick = onTestWeather) { Text(stringResource(R.string.alarm_test)) }
        }
    }
}

@Composable
private fun AlarmSoundRow(label: String, selected: AlarmSound, onSelect: (AlarmSound) -> Unit) {
    SegmentedRow(
        label = label,
        options = AlarmSound.entries,
        selected = selected,
        labelOf = {
            when (it) {
                AlarmSound.PIEP -> stringResource(R.string.sound_piep)
                AlarmSound.GLOCKE -> stringResource(R.string.sound_glocke)
                AlarmSound.SIRENE -> stringResource(R.string.sound_sirene)
            }
        },
        onSelect = onSelect,
    )
}

/* ------------------------------ Helpers ------------------------------ */

/** Bewegungs-Schwelle der Auto-Pause in der gewählten Einheit (km/h oder kn); 0 = ignorieren. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AutoPauseSpeedField(settings: Settings, onChange: (Float) -> Unit) {
    val factor = settings.unit.factorFromMs
    val shown = (settings.autoPauseSpeedMs * factor).toFloat()
    var text by remember(settings.autoPauseSpeedMs, settings.unit) {
        mutableStateOf(if (shown <= 0f) "" else String.format(java.util.Locale.getDefault(), "%.1f", shown))
    }
    Column(modifier = Modifier.padding(vertical = 10.dp)) {
        OutlinedTextField(
            value = text,
            onValueChange = { new ->
                val filtered = new.replace(',', '.').filter { it.isDigit() || it == '.' }.take(5)
                text = filtered
                val value = filtered.toFloatOrNull() ?: 0f
                onChange((value / factor).toFloat().coerceIn(0f, 10f))
            },
            label = { Text(stringResource(R.string.auto_pause_speed)) },
            suffix = { Text(settings.unit.label) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            stringResource(R.string.auto_pause_speed_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AutoPauseField(amps: Float, onChange: (Float) -> Unit) {
    var text by remember(amps) { mutableStateOf(if (amps <= 0f) "" else amps.toString()) }
    Column(modifier = Modifier.padding(vertical = 10.dp)) {
        OutlinedTextField(
            value = text,
            onValueChange = { new ->
                val filtered = new.replace(',', '.').filter { it.isDigit() || it == '.' }.take(5)
                text = filtered
                onChange((filtered.toFloatOrNull() ?: 0f).coerceIn(0f, 50f))
            },
            label = { Text(stringResource(R.string.auto_pause)) },
            suffix = { Text("A") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            stringResource(R.string.auto_pause_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

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
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
    )
}

@Composable
private fun ChargeTargetSliderRow(percent: Int, onChange: (Int) -> Unit) {
    var pos by remember(percent) { mutableFloatStateOf(percent.toFloat()) }
    val current = pos.toInt()
    Column(modifier = Modifier.padding(vertical = 10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(stringResource(R.string.charge_target_label), style = MaterialTheme.typography.titleMedium)
            Text(if (current <= 0) stringResource(R.string.smoothing_off) else "$current %")
        }
        Slider(
            value = pos,
            onValueChange = { pos = it },
            onValueChangeFinished = { onChange(pos.toInt()) },
            valueRange = 0f..100f,
            steps = 19, // 0,5,…,100
        )
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
