package de.kewl.boatspeedy.trip

import android.content.Context
import android.location.Location
import android.net.Uri
import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Liest eine GPX-Datei (auch von anderen Programmen) und erzeugt daraus eine [SavedTrip].
 * Übernommen werden Wegpunkte (`trkpt`/`rtept`/`wpt`) mit lat/lon und – falls vorhanden –
 * `time`. Geschwindigkeit wird aus Strecke/Zeit zwischen den Punkten abgeleitet;
 * Verbrauch/SoC sind bei fremden Tracks unbekannt.
 */
object GpxImport {

    suspend fun import(context: Context, uri: Uri, store: TripStore): SavedTrip? =
        withContext(Dispatchers.IO) {
            val parsed = context.contentResolver.openInputStream(uri)?.use { parse(it) } ?: return@withContext null
            if (parsed.size < 2) return@withContext null
            val trip = toTrip(parsed)
            store.save(trip)
            trip
        }

    private data class Raw(val lat: Double, val lon: Double, val epochMs: Long?)

    private fun parse(input: java.io.InputStream): List<Raw> {
        val out = ArrayList<Raw>()
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(input, null)
        var lat: Double? = null
        var lon: Double? = null
        var time: Long? = null
        var inTime = false
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name.lowercase()) {
                    "trkpt", "rtept", "wpt" -> {
                        lat = parser.getAttributeValue(null, "lat")?.toDoubleOrNull()
                        lon = parser.getAttributeValue(null, "lon")?.toDoubleOrNull()
                        time = null
                    }
                    "time" -> inTime = true
                }
                XmlPullParser.TEXT -> if (inTime) time = parseTime(parser.text)
                XmlPullParser.END_TAG -> when (parser.name.lowercase()) {
                    "time" -> inTime = false
                    "trkpt", "rtept", "wpt" -> {
                        val la = lat; val lo = lon
                        if (la != null && lo != null) out.add(Raw(la, lo, time))
                        lat = null; lon = null
                    }
                }
            }
            event = parser.next()
        }
        return out
    }

    private val isoFormats = listOf(
        "yyyy-MM-dd'T'HH:mm:ss'Z'",
        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
        "yyyy-MM-dd'T'HH:mm:ssXXX",
    )

    private fun parseTime(raw: String?): Long? {
        val s = raw?.trim().orEmpty()
        if (s.isEmpty()) return null
        for (f in isoFormats) {
            runCatching {
                val sdf = SimpleDateFormat(f, Locale.US)
                if (f.endsWith("'Z'")) sdf.timeZone = TimeZone.getTimeZone("UTC")
                return sdf.parse(s)?.time
            }
        }
        return null
    }

    private fun toTrip(raw: List<Raw>): SavedTrip {
        val startEpoch = raw.firstOrNull { it.epochMs != null }?.epochMs ?: System.currentTimeMillis()
        val points = ArrayList<TrackPoint>(raw.size)
        var distanceM = 0.0
        var maxSpeed = 0f
        var prev: Raw? = null
        for (r in raw) {
            val tMs = r.epochMs?.let { it - startEpoch }?.coerceAtLeast(0L) ?: 0L
            var speed = 0f
            val p = prev
            if (p != null) {
                val res = FloatArray(1)
                Location.distanceBetween(p.lat, p.lon, r.lat, r.lon, res)
                distanceM += res[0]
                val dtMs = (r.epochMs ?: 0L) - (p.epochMs ?: 0L)
                if (dtMs in 1..60_000) {
                    speed = (res[0] / (dtMs / 1000.0)).toFloat()
                    if (speed > maxSpeed) maxSpeed = speed
                }
            }
            points.add(TrackPoint(lat = r.lat, lon = r.lon, tMs = tMs, speedMs = speed))
            prev = r
        }
        val duration = points.lastOrNull()?.tMs ?: 0L
        val avg = if (duration > 0) (distanceM / (duration / 1000.0)).toFloat() else 0f
        return SavedTrip(
            id = startEpoch,
            startedAt = startEpoch,
            distanceM = distanceM,
            durationMs = duration,
            totalMs = duration,
            avgSpeedMs = avg,
            maxSpeedMs = maxSpeed,
            energyWh = 0f,
            chargeAh = 0f,
            points = points,
        )
    }
}
