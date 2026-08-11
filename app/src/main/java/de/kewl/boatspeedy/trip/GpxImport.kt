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
            if (parsed.points.size < 2) return@withContext null
            val trip = toTrip(parsed.points, parsed.meta)
            store.save(trip)
            trip
        }

    /** Aus <trk><extensions> gelesene Fahrt-Zeiten (nur bei BoatSpeedy-GPX vorhanden). */
    private data class TripMeta(val movingS: Long?, val pauseS: Long?, val totalS: Long?)

    private data class Parsed(val points: List<Raw>, val meta: TripMeta)

    private data class Raw(
        val lat: Double,
        val lon: Double,
        val epochMs: Long?,
        val speedMs: Float? = null,
        val soc: Int? = null,
        val chargeAh: Float? = null,
    )

    private fun parse(input: java.io.InputStream): Parsed {
        val out = ArrayList<Raw>()
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(input, null)
        var lat: Double? = null
        var lon: Double? = null
        var time: Long? = null
        var speed: Float? = null
        var soc: Int? = null
        var chargeAh: Float? = null
        var cur: String? = null // aktuell offenes Text-Element
        var movingS: Long? = null
        var pauseS: Long? = null
        var totalS: Long? = null
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    val n = parser.name.lowercase()
                    if (n == "trkpt" || n == "rtept" || n == "wpt") {
                        lat = parser.getAttributeValue(null, "lat")?.toDoubleOrNull()
                        lon = parser.getAttributeValue(null, "lon")?.toDoubleOrNull()
                        time = null; speed = null; soc = null; chargeAh = null
                    }
                    cur = n
                }
                XmlPullParser.TEXT -> {
                    val t = parser.text
                    when (cur) {
                        "time" -> time = parseTime(t)
                        "speed", "boatspeedy:speed" -> if (speed == null) speed = t.trim().toFloatOrNull()
                        "boatspeedy:soc" -> soc = t.trim().toIntOrNull()
                        "boatspeedy:chargeah" -> chargeAh = t.trim().toFloatOrNull()
                        // Fahrt-Zeiten aus <trk><extensions> (eigene BoatSpeedy-GPX).
                        "boatspeedy:movingtimes" -> movingS = t.trim().toLongOrNull()
                        "boatspeedy:pausetimes" -> pauseS = t.trim().toLongOrNull()
                        "boatspeedy:totaltimes" -> totalS = t.trim().toLongOrNull()
                    }
                }
                XmlPullParser.END_TAG -> {
                    val n = parser.name.lowercase()
                    if (n == "trkpt" || n == "rtept" || n == "wpt") {
                        val la = lat; val lo = lon
                        if (la != null && lo != null) out.add(Raw(la, lo, time, speed, soc, chargeAh))
                        lat = null; lon = null
                    }
                    cur = null
                }
            }
            event = parser.next()
        }
        return Parsed(out, TripMeta(movingS, pauseS, totalS))
    }

    /**
     * Fahrzeit aus den Zeitstempeln: Abstände zwischen Punkten zählen als Fahrt, größere
     * Lücken als Pause. Die Schwelle richtet sich nach dem üblichen Aufzeichnungstakt,
     * damit auch grob abgetastete Fremd-GPX (z. B. alle 30 s) korrekt bleiben.
     */
    private fun movingFromGaps(points: List<TrackPoint>, span: Long): Long {
        if (points.size < 2) return span
        val deltas = (1 until points.size).map { points[it].tMs - points[it - 1].tMs }.filter { it > 0 }
        if (deltas.isEmpty()) return span
        val median = deltas.sorted()[deltas.size / 2]
        val limit = maxOf(10_000L, median * 4) // Lücke darüber = Pause
        return deltas.filter { it <= limit }.sum()
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

    private fun toTrip(raw: List<Raw>, meta: TripMeta): SavedTrip {
        val startEpoch = raw.firstOrNull { it.epochMs != null }?.epochMs ?: System.currentTimeMillis()
        val points = ArrayList<TrackPoint>(raw.size)
        var distanceM = 0.0
        var maxSpeed = 0f
        var prev: Raw? = null
        for (r in raw) {
            val tMs = r.epochMs?.let { it - startEpoch }?.coerceAtLeast(0L) ?: 0L
            var speed = r.speedMs ?: 0f // aus GPX übernehmen, falls vorhanden
            val p = prev
            if (p != null) {
                val res = FloatArray(1)
                Location.distanceBetween(p.lat, p.lon, r.lat, r.lon, res)
                distanceM += res[0]
                if (r.speedMs == null) { // nur ableiten, wenn nicht im GPX
                    val dtMs = (r.epochMs ?: 0L) - (p.epochMs ?: 0L)
                    if (dtMs in 1..60_000) speed = (res[0] / (dtMs / 1000.0)).toFloat()
                }
            }
            if (speed > maxSpeed) maxSpeed = speed
            points.add(
                TrackPoint(
                    lat = r.lat, lon = r.lon, tMs = tMs,
                    speedMs = speed,
                    soc = r.soc ?: -1,
                    chargeAh = r.chargeAh ?: 0f,
                ),
            )
            prev = r
        }
        val span = points.lastOrNull()?.tMs ?: 0L
        // Gesamtzeit = Spanne der Zeitstempel. Fahrzeit: aus der GPX übernehmen, sonst aus
        // den Aufzeichnungslücken ableiten (Pausen = Lücken, in denen nichts aufgezeichnet wurde).
        val total = meta.totalS?.times(1000) ?: span
        val moving = meta.movingS?.times(1000) ?: movingFromGaps(points, span)
        val duration = moving.coerceIn(0L, total)
        val avg = if (duration > 0) (distanceM / (duration / 1000.0)).toFloat() else 0f
        val tripCharge = points.maxOfOrNull { it.chargeAh } ?: 0f // kumuliert → Endwert = Gesamt
        return SavedTrip(
            id = startEpoch,
            startedAt = startEpoch,
            distanceM = distanceM,
            durationMs = duration,
            totalMs = total,
            avgSpeedMs = avg,
            maxSpeedMs = maxSpeed,
            energyWh = 0f,
            chargeAh = tripCharge,
            points = points,
        )
    }
}
