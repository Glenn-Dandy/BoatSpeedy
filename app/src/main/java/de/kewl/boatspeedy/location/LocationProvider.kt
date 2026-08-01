package de.kewl.boatspeedy.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart

/**
 * Bündelt zwei Quellen zu einem [GpsState]-Flow:
 *  - [LocationManager] GPS-Provider (AOSP) → Geschwindigkeit, Genauigkeit, Kurs, Höhe, Position
 *  - GnssStatus                            → Satellitenzahl, Signalstärke, Konstellationen
 *
 * Bewusst **ohne Google Play Services** (nur AOSP-`LocationManager`), damit die App
 * vollständig frei ist (F-Droid). Der GPS-Provider moderner Geräte nutzt intern bereits
 * mehrere GNSS-Systeme (GPS, GLONASS, Galileo, BeiDou …).
 *
 * Der Aufrufer muss ACCESS_FINE_LOCATION bereits erteilt haben, bevor [state] gesammelt wird.
 */
class LocationProvider(private val context: Context) {

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

    private fun Location.toSample() = LocSample(
        speedMs = if (hasSpeed()) speed else null,
        accuracyM = if (hasAccuracy()) accuracy else null,
        latitude = latitude,
        longitude = longitude,
        bearingDeg = if (hasBearing()) bearing else null,
        altitudeM = if (hasAltitude()) altitude else null,
    )

    @SuppressLint("MissingPermission")
    private val locationFlow: Flow<LocSample> = callbackFlow {
        val listener = LocationListener { loc -> trySend(loc.toSample()) }

        // Sofort den letzten bekannten Fix schicken (schnellerer Start).
        runCatching { locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER) }
            .getOrNull()?.let { trySend(it.toSample()) }

        locationManager.requestLocationUpdates(
            LocationManager.GPS_PROVIDER,
            1000L, // min. 1 s
            0f, // jede Bewegung
            listener,
            Looper.getMainLooper(),
        )
        awaitClose { locationManager.removeUpdates(listener) }
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

    // Satelliten-Flow mit leerem Startwert: so kommt die Position auch dann durch,
    // wenn (noch) kein Satelliten-Status kommt – u. a. bei Fake-GPS / Mock-Location.
    private val gnssFlowOrEmpty: Flow<GnssSample> =
        gnssFlow.onStart { emit(GnssSample(used = 0, visible = 0, cn0DbHz = null, constellations = emptyList())) }

    val state: Flow<GpsState> = combine(locationFlow, gnssFlowOrEmpty) { loc, gnss ->
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
