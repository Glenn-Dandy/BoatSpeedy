package de.kewl.boatspeedy.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.kewl.boatspeedy.R
import de.kewl.boatspeedy.battery.BatteryData
import de.kewl.boatspeedy.battery.RangeEstimate
import de.kewl.boatspeedy.data.Settings
import de.kewl.boatspeedy.location.GpsState
import de.kewl.boatspeedy.trip.TripStats
import de.kewl.boatspeedy.ui.theme.SpeedTextStyle
import de.kewl.boatspeedy.ui.theme.StatusGood
import de.kewl.boatspeedy.ui.theme.StatusNone
import de.kewl.boatspeedy.ui.theme.StatusWeak
import java.util.Locale

private const val PLACEHOLDER = "--"

/** Eine wählbare Batterie-Anzeige auf dem Dashboard (id == Adresse, oder leer für „kombiniert"). */
data class BatteryOption(val id: String, val label: String)

@Composable
fun DashboardScreen(
    speedText: String,
    gps: GpsState,
    settings: Settings,
    tracking: Boolean,
    tripStats: TripStats,
    batteryData: BatteryData?,
    range: RangeEstimate?,
    batteryOptions: List<BatteryOption>,
    selectedBattery: String,
    onSelectBattery: (String) -> Unit,
    onStartTrip: () -> Unit,
    onStopTrip: () -> Unit,
    onOpenMenu: () -> Unit,
) {
    Scaffold { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {

            IconButton(
                onClick = onOpenMenu,
                modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
            ) {
                Icon(Icons.Filled.Menu, contentDescription = stringResource(R.string.menu))
            }

            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(56.dp))

                // Haupt-Kachel: Geschwindigkeit.
                Text(
                    text = speedText,
                    style = SpeedTextStyle,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = settings.unit.label,
                    fontSize = 28.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                )

                Spacer(Modifier.height(16.dp))

                // Reihenfolge: Reichweite, dann Batterie-Status. Beide immer sichtbar
                // (Platzhalter ohne Werte), außer in den Einstellungen ausgeblendet.
                if (settings.showRangeTile) {
                    RangeTile(range)
                    Spacer(Modifier.height(12.dp))
                }
                if (settings.showBatteryTile) {
                    BatterySelectorRow(batteryOptions, selectedBattery, onSelectBattery)
                    BatteryTile(batteryData)
                    Spacer(Modifier.height(12.dp))
                }

                Spacer(Modifier.weight(1f))

                if (tracking || tripStats.hasData) {
                    StatsPanel(stats = tripStats, settings = settings)
                    Spacer(Modifier.height(12.dp))
                }
                TripButton(tracking = tracking, onStart = onStartTrip, onStop = onStopTrip)
                Spacer(Modifier.height(16.dp))
                StatusRow(gps = gps, showSatDetails = settings.showSatDetails)
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun BatterySelectorRow(options: List<BatteryOption>, selected: String, onSelect: (String) -> Unit) {
    if (options.size < 2) return
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        options.forEach { opt ->
            FilterChip(
                selected = opt.id == selected,
                onClick = { onSelect(opt.id) },
                label = { Text(opt.label) },
            )
        }
    }
}

@Composable
private fun BatteryTile(d: BatteryData?) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.battery), style = MaterialTheme.typography.titleSmall)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                TileStat(stringResource(R.string.bat_power), d?.let { watts(kotlin.math.abs(it.powerW)) } ?: PLACEHOLDER)
                TileStat(stringResource(R.string.bat_voltage), d?.let { num(it.voltage, "V") } ?: PLACEHOLDER)
                TileStat(stringResource(R.string.bat_current), d?.let { num(it.currentA, "A") } ?: PLACEHOLDER)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                TileStat(stringResource(R.string.soc_short), d?.let { "${it.soc} %" } ?: PLACEHOLDER)
                TileStat(
                    stringResource(R.string.bat_remaining),
                    d?.takeIf { it.remainingAh > 0f }?.let { num(it.remainingAh, "Ah") } ?: PLACEHOLDER,
                )
                TileStat(
                    stringResource(R.string.bat_temp),
                    d?.tempC?.let { num(it, "°C") } ?: PLACEHOLDER,
                )
            }
        }
    }
}

@Composable
private fun RangeTile(range: RangeEstimate?) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            TileStat(
                stringResource(R.string.bat_est_range),
                range?.let { formatDistance(it.km * 1000.0) } ?: PLACEHOLDER,
                big = true,
            )
            TileStat(
                stringResource(R.string.bat_est_time),
                range?.let { formatDuration((it.hours * 3600_000).toLong()) } ?: PLACEHOLDER,
                big = true,
            )
        }
    }
}

@Composable
private fun TileStat(label: String, value: String, big: Boolean = false) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        Text(
            value,
            fontSize = if (big) 22.sp else 17.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (big) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun StatsPanel(stats: TripStats, settings: Settings) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        StatItem(stringResource(R.string.stat_distance), formatDistance(stats.distanceM))
        StatItem(
            stringResource(R.string.stat_max),
            "${formatSpeed(stats.maxSpeedMs, settings.unit, settings.decimals)} ${settings.unit.label}",
        )
        StatItem(
            stringResource(R.string.stat_avg),
            "${formatSpeed(stats.avgSpeedMs, settings.unit, settings.decimals)} ${settings.unit.label}",
        )
        StatItem(stringResource(R.string.stat_time), formatDuration(stats.elapsedMs))
    }
}

@Composable
private fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
    }
}

@Composable
private fun TripButton(tracking: Boolean, onStart: () -> Unit, onStop: () -> Unit) {
    if (tracking) {
        Button(
            onClick = onStop,
            colors = ButtonDefaults.buttonColors(
                containerColor = StatusNone,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
            Icon(Icons.Filled.Stop, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.trip_stop))
        }
    } else {
        Button(onClick = onStart) {
            Icon(Icons.Filled.PlayArrow, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.trip_start))
        }
    }
}

@Composable
private fun StatusRow(gps: GpsState, showSatDetails: Boolean) {
    val statusColor = when {
        !gps.hasFix -> StatusNone
        (gps.accuracyM ?: Float.MAX_VALUE) <= 10f && gps.satellitesUsed >= 4 -> StatusGood
        else -> StatusWeak
    }

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(statusColor))
        Spacer(Modifier.width(8.dp))
        Text(
            text = if (gps.hasFix) stringResource(R.string.status_fix) else stringResource(R.string.status_no_fix),
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
        )
        if (showSatDetails) {
            Spacer(Modifier.width(16.dp))
            Text(
                text = stringResource(R.string.sat_label, gps.satellitesUsed, gps.satellitesVisible),
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
            )
            gps.accuracyM?.let { acc ->
                Spacer(Modifier.width(16.dp))
                Text(
                    text = stringResource(R.string.accuracy_label, acc.toInt()),
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                )
            }
        }
    }
}

private fun num(v: Float, unit: String) = String.format(Locale.getDefault(), "%.2f %s", v, unit)
private fun watts(v: Float) = String.format(Locale.getDefault(), "%.0f W", v)
