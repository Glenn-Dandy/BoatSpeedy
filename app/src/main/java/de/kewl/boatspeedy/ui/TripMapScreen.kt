package de.kewl.boatspeedy.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import de.kewl.boatspeedy.R
import de.kewl.boatspeedy.data.Settings
import de.kewl.boatspeedy.trip.SavedTrip
import de.kewl.boatspeedy.trip.TrackPoint
import java.util.Locale

/** Vollbild-Track auf Karte: Richtungspfeile + Sprechblase am angetippten Punkt. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripMapScreen(trip: SavedTrip, settings: Settings, onBack: () -> Unit) {
    val context = LocalContext.current
    val bubble: (TrackPoint) -> String = { p ->
        buildString {
            append(context.getString(R.string.stat_total_time)).append(": ").append(formatDuration(p.tMs))
            append("\n").append(context.getString(R.string.speed_label)).append(": ")
                .append(formatSpeed(p.speedMs, settings.unit, settings.decimals)).append(" ").append(settings.unit.label)
            if (p.chargeAh > 0f) {
                append("\n").append(context.getString(R.string.stat_consumed)).append(": ")
                    .append(String.format(Locale.getDefault(), "%.1f Ah", p.chargeAh))
            }
            if (p.soc >= 0) {
                append("\n").append(context.getString(R.string.soc_short)).append(": ").append("${p.soc} %")
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(tripDate(trip.startedAt)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { innerPadding ->
        TrackMap(
            trip = trip,
            interactive = true,
            showArrows = settings.trackArrows,
            bubbleText = bubble,
            color = settings.trackColor.argb,
            strokeWidth = settings.trackWidth.px,
            modifier = Modifier.padding(innerPadding).fillMaxSize(),
        )
    }
}
