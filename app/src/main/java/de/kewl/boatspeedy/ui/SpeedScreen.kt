package de.kewl.boatspeedy.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.kewl.boatspeedy.R
import de.kewl.boatspeedy.battery.BatteryData
import de.kewl.boatspeedy.battery.ChargeState
import de.kewl.boatspeedy.battery.RangeEstimate
import de.kewl.boatspeedy.data.Settings
import de.kewl.boatspeedy.location.GpsState
import de.kewl.boatspeedy.trip.TrackPoint
import de.kewl.boatspeedy.trip.TripStats
import de.kewl.boatspeedy.ui.theme.SpeedTextStyle
import de.kewl.boatspeedy.ui.theme.StatusGood
import de.kewl.boatspeedy.ui.theme.StatusNone
import de.kewl.boatspeedy.ui.theme.StatusWeak
import de.kewl.boatspeedy.weather.WeatherWarning
import java.text.SimpleDateFormat
import java.util.Date
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
    tripPaused: Boolean,
    autoPauseOverride: Boolean,
    batteryData: BatteryData?,
    range: RangeEstimate?,
    charge: ChargeState,
    weatherWarnings: List<WeatherWarning>,
    batteryOptions: List<BatteryOption>,
    selectedBattery: String,
    livePoints: List<TrackPoint>,
    onSelectBattery: (String) -> Unit,
    onAutoPauseOverride: (Boolean) -> Unit,
    onStartTrip: () -> Unit,
    onStopTrip: () -> Unit,
    onOpenMenu: () -> Unit,
    onOpenMap: () -> Unit,
) {
    Scaffold { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {

            // --- Fixer Kopf: Menü, optional Karten-Button, Geschwindigkeit ---
            Box(modifier = Modifier.fillMaxWidth()) {
                IconButton(
                    onClick = onOpenMenu,
                    modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
                ) {
                    Icon(Icons.Filled.Menu, contentDescription = stringResource(R.string.menu))
                }
                if (!settings.showMapTile) {
                    IconButton(
                        onClick = onOpenMap,
                        modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                    ) {
                        Icon(Icons.Filled.Map, contentDescription = stringResource(R.string.live_map))
                    }
                }
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(Modifier.height(56.dp))
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
                }
            }

            // --- Scrollbarer Rest ---
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(16.dp))

                // Zielzeile – erscheint nur, wenn ein Ziel gesetzt ist, und sitzt damit
                // direkt unter der großen Zahl, wo beim Fahren ohnehin hingeschaut wird.
                NavRow(
                    lat = gps.latitude,
                    lon = gps.longitude,
                    tripDistanceM = tripStats.distanceM,
                    tripChargeAh = tripStats.chargeAh,
                )

                if (weatherWarnings.isNotEmpty()) {
                    WeatherBanner(weatherWarnings)
                    Spacer(Modifier.height(12.dp))
                }

                if (settings.showRangeTile) {
                    if (charge.charging) ChargeTile(charge) else RangeTile(range)
                    Spacer(Modifier.height(12.dp))
                }
                if (settings.showBatteryTile) {
                    BatterySelectorRow(batteryOptions, selectedBattery, onSelectBattery)
                    BatteryTile(batteryData, settings.lowSocPercent)
                    Spacer(Modifier.height(12.dp))
                }
                if (settings.showMapTile) {
                    MapMiniTile(livePoints, gps.latitude, gps.longitude, onOpenMap)
                    Spacer(Modifier.height(12.dp))
                }

                if (tracking || tripStats.hasData) {
                    StatsPanel(stats = tripStats, settings = settings, showConsumption = batteryData != null)
                    Spacer(Modifier.height(12.dp))
                }
                // Auto-Pause-Status als kompakter Chip – antippen schaltet um
                // (links: aktueller Zustand, rechts: was das Antippen bewirkt).
                if (tracking && (settings.autoPauseOn || autoPauseOverride)) {
                    AutoPauseChip(
                        paused = tripPaused,
                        onToggle = { onAutoPauseOverride(!autoPauseOverride) },
                    )
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
private fun MapMiniTile(points: List<TrackPoint>, lat: Double?, lon: Double?, onOpenMap: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Box(modifier = Modifier.fillMaxWidth().height(220.dp)) {
            // Der Weg zum Ziel gehört auch auf die kleine Kachel – sonst müsste man für
            // einen Blick darauf jedes Mal die große Karte öffnen.
            val navTarget by de.kewl.boatspeedy.nav.NavRepository.target.collectAsStateWithLifecycle()
            val mapCourse by de.kewl.boatspeedy.nav.NavRepository.course.collectAsStateWithLifecycle()
            OsmMap(
                points = points,
                currentLat = lat,
                currentLon = lon,
                interactive = false,
                navPath = navTarget?.path.orEmpty(),
                navWaterPath = navTarget?.water.orEmpty(),
                courseDeg = mapCourse?.deg,
                modifier = Modifier.matchParentSize(),
            )
            // Nicht-interaktive Vorschau: Overlay fängt den Tap (→ große Karte),
            // vertikales Ziehen wandert an das Dashboard-Scrollen weiter.
            Box(modifier = Modifier.matchParentSize().clickable(onClick = onOpenMap))
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
private fun BatteryTile(d: BatteryData?, lowSocPercent: Int) {
    val socLow = d != null && lowSocPercent > 0 && d.soc <= lowSocPercent
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.battery), style = MaterialTheme.typography.titleSmall)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                TileStat(stringResource(R.string.bat_power), d?.let { watts(kotlin.math.abs(it.powerW)) } ?: PLACEHOLDER)
                TileStat(stringResource(R.string.bat_voltage), d?.let { num(it.voltage, "V") } ?: PLACEHOLDER)
                TileStat(stringResource(R.string.bat_current), d?.let { num(it.currentA, "A") } ?: PLACEHOLDER)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                TileStat(stringResource(R.string.soc_short), d?.let { "${it.soc} %" } ?: PLACEHOLDER, alert = socLow)
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
private fun ChargeTile(charge: ChargeState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                stringResource(R.string.charge_mode),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                TileStat(stringResource(R.string.charge_current), num(charge.chargeA, "A"))
                TileStat(stringResource(R.string.soc_short), "${charge.soc} %")
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                TileStat(
                    stringResource(R.string.charge_time_to_full),
                    charge.hoursToFull?.let { formatDuration((it * 3600_000).toLong()) } ?: PLACEHOLDER,
                    big = true,
                )
                TileStat(
                    stringResource(R.string.charge_full_at),
                    charge.fullAtEpochMs?.let { clockTime(it) } ?: PLACEHOLDER,
                    big = true,
                )
            }
        }
    }
}

@Composable
private fun WeatherBanner(warnings: List<WeatherWarning>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            warnings.forEach { w ->
                Text(
                    "⚠ ${w.event}",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                if (w.headline.isNotBlank()) {
                    Text(
                        w.headline,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f),
                    )
                }
                w.expiresMs?.let { exp ->
                    Text(
                        stringResource(R.string.weather_valid_until, clockTime(exp)),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f),
                    )
                }
            }
        }
    }
}

private fun clockTime(epochMs: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(epochMs))

@Composable
private fun TileStat(label: String, value: String, big: Boolean = false, alert: Boolean = false) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        Text(
            value,
            fontSize = if (big) 22.sp else 17.sp,
            fontWeight = FontWeight.SemiBold,
            color = when {
                alert -> MaterialTheme.colorScheme.error
                big -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurface
            },
        )
    }
}

@Composable
private fun StatsPanel(stats: TripStats, settings: Settings, showConsumption: Boolean) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
        if (showConsumption || stats.chargeAh > 0f || stats.energyWh > 0f) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                StatItem(
                    stringResource(R.string.stat_consumed),
                    String.format(Locale.getDefault(), "%.1f Ah", stats.chargeAh),
                )
                StatItem(
                    stringResource(R.string.stat_energy),
                    String.format(Locale.getDefault(), "%.0f Wh", stats.energyWh),
                )
                StatItem(
                    stringResource(R.string.stat_efficiency),
                    stats.whPerKm?.let { String.format(Locale.getDefault(), "%.0f Wh/km", it) } ?: PLACEHOLDER,
                )
            }
        }
    }
}

@Composable
private fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
    }
}

/**
 * Zustand der Auto-Pause als antippbarer Chip. Bewusst dezenter als der Start/Stopp-Knopf,
 * damit dieser der einzige „große" Bedienknopf auf dem Dashboard bleibt.
 */
@Composable
private fun AutoPauseChip(paused: Boolean, onToggle: () -> Unit) {
    val tint = if (paused) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
    AssistChip(
        onClick = onToggle,
        label = {
            Text(
                stringResource(if (paused) R.string.trip_paused else R.string.trip_recording),
                fontWeight = FontWeight.SemiBold,
            )
        },
        leadingIcon = {
            Icon(
                if (paused) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
        },
        trailingIcon = {
            Icon(
                if (paused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                contentDescription = stringResource(
                    if (paused) R.string.trip_keep_recording else R.string.auto_pause_reenable,
                ),
                modifier = Modifier.size(18.dp),
            )
        },
        colors = AssistChipDefaults.assistChipColors(
            labelColor = tint,
            leadingIconContentColor = tint,
            trailingIconContentColor = tint,
        ),
    )
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

/**
 * Entfernung, geschätzter Verbrauch und der Kurspfeil zum gesetzten Ziel.
 *
 * Zeigt sich nur, solange ein Ziel gesetzt ist — ohne Ziel bleibt das Dashboard
 * unverändert. Der Pfeil zeigt die Drehung zum Ziel, nicht die Himmelsrichtung.
 */
@Composable
private fun NavRow(lat: Double?, lon: Double?, tripDistanceM: Double, tripChargeAh: Float) {
    val target by de.kewl.boatspeedy.nav.NavRepository.target.collectAsStateWithLifecycle()
    val course by de.kewl.boatspeedy.nav.NavRepository.course.collectAsStateWithLifecycle()
    val t = target ?: return

    val ahPerKm = if (tripDistanceM > 300.0 && tripChargeAh > 0f) {
        (tripChargeAh / (tripDistanceM / 1000.0)).toFloat()
    } else {
        null
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (lat != null && lon != null) {
            course?.let { c ->
                CourseArrow(
                    relativeDeg = de.kewl.boatspeedy.nav.relativeBearing(
                        c.deg,
                        de.kewl.boatspeedy.nav.bearingDeg(de.kewl.boatspeedy.nav.LatLon(lat, lon), t.target),
                    ),
                    stale = c.stale,
                    size = 30.dp,
                )
                Spacer(Modifier.width(10.dp))
            }
        }
        Text(
            buildString {
                append(String.format(Locale.getDefault(), "%.2f km", t.distanceM / 1000.0))
                ahPerKm?.let {
                    append(" · ~")
                    append(String.format(Locale.getDefault(), "%.1f Ah", it * (t.distanceM / 1000.0)))
                }
            },
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
