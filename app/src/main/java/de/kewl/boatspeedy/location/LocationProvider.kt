package de.kewl.boatspeedy.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.GnssStatus
import android.location.LocationManager
import android.os.Looper
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine

/**
 * Bündelt zwei Quellen zu einem [GpsState]-Flow:
 *  - FusedLocationProvider → Geschwindigkeit & Genauigkeit
 *  - GnssStatus            → Satellitenzahl (Fused liefert die nicht)
 *
 * Der Aufrufer muss ACCESS_FINE_LOCATION bereits erteilt haben, bevor
 * [state] gesammelt wird.
 */
class LocationProvider(private val context: Context) {

    private val fused = LocationServices.getFusedLocationProviderClient(context)
    private val locationManager =
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    /** Reduzierter Location-Snapshot aus dem FusedLocationProvider. */
    private data class LocSample(
        val speedMs: Float?,
        val accuracyM: Float?,
        val latitude: Double?,
        val longitude: Double?,
    )

    @SuppressLint("MissingPermission")
    private val locationFlow: Flow<LocSample> = callbackFlow {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L)
            .setMinUpdateIntervalMillis(500L)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                trySend(
                    LocSample(
                        speedMs = if (loc.hasSpeed()) loc.speed else null,
                        accuracyM = if (loc.hasAccuracy()) loc.accuracy else null,
                        latitude = loc.latitude,
                        longitude = loc.longitude,
                    ),
                )
            }
        }

        fused.requestLocationUpdates(request, callback, Looper.getMainLooper())
        awaitClose { fused.removeLocationUpdates(callback) }
    }

    @SuppressLint("MissingPermission")
    private val gnssFlow: Flow<Pair<Int, Int>> = callbackFlow {
        val callback = object : GnssStatus.Callback() {
            override fun onSatelliteStatusChanged(status: GnssStatus) {
                var used = 0
                val visible = status.satelliteCount
                for (i in 0 until visible) {
                    if (status.usedInFix(i)) used++
                }
                trySend(used to visible)
            }
        }

        locationManager.registerGnssStatusCallback(context.mainExecutor, callback)
        awaitClose { locationManager.unregisterGnssStatusCallback(callback) }
    }

    /** Zusammengeführter Zustand aus Location- und GNSS-Quelle. */
    val state: Flow<GpsState> = combine(locationFlow, gnssFlow) { loc, (used, visible) ->
        GpsState(
            speedMs = loc.speedMs,
            accuracyM = loc.accuracyM,
            latitude = loc.latitude,
            longitude = loc.longitude,
            satellitesUsed = used,
            satellitesVisible = visible,
            hasFix = loc.speedMs != null,
        )
    }
}
