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
 *  - FusedLocationProvider → Geschwindigkeit, Genauigkeit, Kurs, Höhe, Position
 *  - GnssStatus            → Satellitenzahl, Signalstärke, genutzte Konstellationen
 *
 * Der FusedLocationProvider fusioniert alle vom Gerät unterstützten GNSS-Systeme
 * (GPS, GLONASS, Galileo, BeiDou …) — nicht nur GPS.
 *
 * Der Aufrufer muss ACCESS_FINE_LOCATION bereits erteilt haben, bevor
 * [state] gesammelt wird.
 */
class LocationProvider(private val context: Context) {

    private val fused = LocationServices.getFusedLocationProviderClient(context)
    private val locationManager =
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    private data class LocSample(
        val speedMs: Float?,
        val accuracyM: Float?,
        val latitude: Double?,
        val longitude: Double?,
        val bearingDeg: Float?,
        val altitudeM: Double?,
    )

    private data class GnssSample(
        val used: Int,
        val visible: Int,
        val cn0DbHz: Float?,
        val constellations: List<String>,
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
                        bearingDeg = if (loc.hasBearing()) loc.bearing else null,
                        altitudeM = if (loc.hasAltitude()) loc.altitude else null,
                    ),
                )
            }
        }

        fused.requestLocationUpdates(request, callback, Looper.getMainLooper())
        awaitClose { fused.removeLocationUpdates(callback) }
    }

    @SuppressLint("MissingPermission")
    private val gnssFlow: Flow<GnssSample> = callbackFlow {
        val callback = object : GnssStatus.Callback() {
            override fun onSatelliteStatusChanged(status: GnssStatus) {
                var used = 0
                var cn0Sum = 0f
                var cn0Count = 0
                val systems = LinkedHashSet<String>()
                val visible = status.satelliteCount
                for (i in 0 until visible) {
                    if (status.usedInFix(i)) {
                        used++
                        val cn0 = status.getCn0DbHz(i)
                        if (cn0 > 0f) { cn0Sum += cn0; cn0Count++ }
                        constellationName(status.getConstellationType(i))?.let { systems.add(it) }
                    }
                }
                trySend(
                    GnssSample(
                        used = used,
                        visible = visible,
                        cn0DbHz = if (cn0Count > 0) cn0Sum / cn0Count else null,
                        constellations = systems.toList(),
                    ),
                )
            }
        }

        locationManager.registerGnssStatusCallback(context.mainExecutor, callback)
        awaitClose { locationManager.unregisterGnssStatusCallback(callback) }
    }

    val state: Flow<GpsState> = combine(locationFlow, gnssFlow) { loc, gnss ->
        GpsState(
            speedMs = loc.speedMs,
            accuracyM = loc.accuracyM,
            latitude = loc.latitude,
            longitude = loc.longitude,
            satellitesUsed = gnss.used,
            satellitesVisible = gnss.visible,
            hasFix = loc.speedMs != null,
            bearingDeg = loc.bearingDeg,
            altitudeM = loc.altitudeM,
            cn0DbHz = gnss.cn0DbHz,
            constellations = gnss.constellations,
        )
    }

    private fun constellationName(type: Int): String? = when (type) {
        GnssStatus.CONSTELLATION_GPS -> "GPS"
        GnssStatus.CONSTELLATION_GLONASS -> "GLONASS"
        GnssStatus.CONSTELLATION_GALILEO -> "Galileo"
        GnssStatus.CONSTELLATION_BEIDOU -> "BeiDou"
        GnssStatus.CONSTELLATION_QZSS -> "QZSS"
        GnssStatus.CONSTELLATION_SBAS -> "SBAS"
        GnssStatus.CONSTELLATION_IRNSS -> "IRNSS"
        else -> null
    }
}
