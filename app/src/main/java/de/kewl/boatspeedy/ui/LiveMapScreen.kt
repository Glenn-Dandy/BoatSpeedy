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
import androidx.compose.ui.res.stringResource
import de.kewl.boatspeedy.R
import de.kewl.boatspeedy.trip.TrackPoint

/** Vollbild-Live-Karte: aktuelle Position + bisher aufgezeichneter Track, folgt der Position. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveMapScreen(
    currentLat: Double?,
    currentLon: Double?,
    points: List<TrackPoint>,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.live_map)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { innerPadding ->
        OsmMap(
            points = points,
            currentLat = currentLat,
            currentLon = currentLon,
            interactive = true,
            modifier = Modifier.padding(innerPadding).fillMaxSize(),
        )
    }
}
