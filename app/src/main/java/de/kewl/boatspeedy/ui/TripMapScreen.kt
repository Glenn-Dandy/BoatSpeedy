package de.kewl.boatspeedy.ui

import android.graphics.Color
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import de.kewl.boatspeedy.R
import de.kewl.boatspeedy.trip.SavedTrip
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripMapScreen(trip: SavedTrip, onBack: () -> Unit) {
    val context = LocalContext.current

    // osmdroid: eigener User-Agent (sonst blockt der Tile-Server) und Cache im App-Verzeichnis.
    val mapView = remember {
        Configuration.getInstance().apply {
            userAgentValue = context.packageName
            osmdroidBasePath = File(context.cacheDir, "osmdroid")
            osmdroidTileCache = File(osmdroidBasePath, "tiles")
        }
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)

            val pts = trip.points.map { GeoPoint(it.lat, it.lon) }
            if (pts.isNotEmpty()) {
                val line = Polyline(this).apply {
                    setPoints(pts)
                    outlinePaint.color = Color.parseColor("#1E88E5")
                    outlinePaint.strokeWidth = 10f
                }
                overlays.add(line)
                post {
                    if (pts.size >= 2) {
                        zoomToBoundingBox(BoundingBox.fromGeoPointsSafe(pts), false, 64)
                    } else {
                        controller.setZoom(16.0)
                        controller.setCenter(pts.first())
                    }
                }
            }
        }
    }

    DisposableEffect(Unit) {
        mapView.onResume()
        onDispose {
            mapView.onPause()
            mapView.onDetach()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.show_on_map)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { innerPadding ->
        AndroidView(
            modifier = Modifier.padding(innerPadding).fillMaxSize(),
            factory = { mapView },
        )
    }
}
