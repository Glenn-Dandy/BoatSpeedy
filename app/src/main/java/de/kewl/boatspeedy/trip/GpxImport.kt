package de.kewl.boatspeedy.trip

import android.content.Context
import android.net.Uri
import android.util.Xml
import de.kewl.boatspeedy.nav.LatLon
import de.kewl.boatspeedy.nav.distanceM
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
 * Verbrauch/SoC sind bei fremden Tracks unbekannt. Bei eigenen Dateien kommen Strecke,
 * Zeiten, Verbrauch und Energie aus den BoatSpeedy-Erweiterungen zurück.
 */
object GpxImport {

    suspend fun import(context: Context, uri: Uri, store: TripStore): SavedTrip? =
        withContext(Dispatchers.IO) {
            val trip = context.contentResolver.openInputStream(uri)?.use {
                fromGpx(it, Xml.newPullParser())
            } ?: return@withContext null
            store.save(trip)
            trip
        }

    /**
     * Liest eine GPX-Datei zu einer Fahrt, oder `null`, wenn sie keine zwei Punkte hat.
     * Der Leser wird übergeben, damit der Import auch ohne Android geprüft werden kann.
     */
    internal fun fromGpx(input: java.io.InputStream, parser: XmlPullParser): SavedTrip? {
        val parsed = parse(input, parser)
        if (parsed.points.size < 2) return null
        return toTrip(parsed.points, parsed.meta)
    }

    /**
     * Aus <trk><extensions> gelesene Werte der ganzen Fahrt (nur bei BoatSpeedy-GPX).
     * Strecke und Energie gehören dazu: Ohne sie standen nach dem Import Energie und
     * Effizienz auf null, und die Strecke wurde aus den Punkten neu gerechnet und wich
     * von der aufgezeichneten ab.
     */
    private data class TripMeta(
        val movingS: Long?,
        val pauseS: Long?,
        val totalS: Long?,
        val distanceM: Double? = null,
        val energyWh: Float? = null,
        val name: String? = null,
    )

    private data class Parsed(val points: List<Raw>, val meta: TripMeta)

    private data class Raw(
        val lat: Double,
        val lon: Double,
        val epochMs: Long?,
        val speedMs: Float? = null,
        val soc: Int? = null,
        val chargeAh: Float? = null,
        val currentA: Float? = null,
        val powerW: Float? = null,
    )

    private fun parse(input: java.io.InputStream, parser: XmlPullParser): Parsed {
        val out = ArrayList<Raw>()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(input, null)
        var lat: Double? = null
        var lon: Double? = null
        var time: Long? = null
        var speed: Float? = null
        var soc: Int? = null
        var chargeAh: Float? = null
        var currentA: Float? = null
        var powerW: Float? = null
        var cur: String? = null // aktuell offenes Text-Element
        var movingS: Long? = null
        var pauseS: Long? = null
        var totalS: Long? = null
        var fahrtStrecke: Double? = null
        var fahrtEnergie: Float? = null
        var eigenerName: String? = null
        var trkName: String? = null
        var ausBoatSpeedy = false
        var inTrk = false
        var inPunkt = false
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    val n = parser.name.lowercase()
                    if (n == "trk") inTrk = true
                    if (n.startsWith("boatspeedy:")) ausBoatSpeedy = true
                    if (n == "trkpt" || n == "rtept" || n == "wpt") {
                        inPunkt = true
                        lat = parser.getAttributeValue(null, "lat")?.toDoubleOrNull()
                        lon = parser.getAttributeValue(null, "lon")?.toDoubleOrNull()
                        time = null; speed = null; soc = null; chargeAh = null; currentA = null; powerW = null
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
                        "boatspeedy:currenta" -> currentA = t.trim().toFloatOrNull()
                        "boatspeedy:powerw" -> powerW = t.trim().toFloatOrNull()
                        // Fahrt-Zeiten aus <trk><extensions> (eigene BoatSpeedy-GPX).
                        "boatspeedy:movingtimes" -> movingS = t.trim().toLongOrNull()
                        "boatspeedy:pausetimes" -> pauseS = t.trim().toLongOrNull()
                        "boatspeedy:totaltimes" -> totalS = t.trim().toLongOrNull()
                        "boatspeedy:distancem" -> fahrtStrecke = t.trim().toDoubleOrNull()
                        "boatspeedy:energywh" -> fahrtEnergie = t.trim().toFloatOrNull()
                        "boatspeedy:name" -> eigenerName = t.trim().takeIf { it.isNotEmpty() }
                        // Der Name der Spur, nicht der eines Punktes.
                        "name" -> if (inTrk && !inPunkt && trkName == null) {
                            trkName = t.trim().takeIf { it.isNotEmpty() }
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    val n = parser.name.lowercase()
                    if (n == "trkpt" || n == "rtept" || n == "wpt") {
                        inPunkt = false
                        val la = lat; val lo = lon
                        if (la != null && lo != null) out.add(Raw(la, lo, time, speed, soc, chargeAh, currentA, powerW))
                        lat = null; lon = null
                    }
                    cur = null
                }
            }
            event = parser.next()
        }
        // Aus einer BoatSpeedy-Datei zählt nur der eigene Name: Ihr <name> ist ohne ihn
        // das Datum, und das stünde sonst zweimal da. Fremde Dateien bringen ihren Namen mit.
        val name = eigenerName ?: trkName.takeIf { !ausBoatSpeedy }
        return Parsed(out, TripMeta(movingS, pauseS, totalS, fahrtStrecke, fahrtEnergie, name))
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
        var gerechnet = 0.0
        var maxSpeed = 0f
        var prev: Raw? = null
        for (r in raw) {
            val tMs = r.epochMs?.let { it - startEpoch }?.coerceAtLeast(0L) ?: 0L
            var speed = r.speedMs ?: 0f // aus GPX übernehmen, falls vorhanden
            val p = prev
            if (p != null) {
                val schritt = distanceM(LatLon(p.lat, p.lon), LatLon(r.lat, r.lon))
                gerechnet += schritt
                if (r.speedMs == null) { // nur ableiten, wenn nicht im GPX
                    val dtMs = (r.epochMs ?: 0L) - (p.epochMs ?: 0L)
                    if (dtMs in 1..60_000) speed = (schritt / (dtMs / 1000.0)).toFloat()
                }
            }
            if (speed > maxSpeed) maxSpeed = speed
            points.add(
                TrackPoint(
                    lat = r.lat, lon = r.lon, tMs = tMs,
                    speedMs = speed,
                    soc = r.soc ?: -1,
                    chargeAh = r.chargeAh ?: 0f,
                    currentA = r.currentA ?: Float.NaN,
                    powerW = r.powerW ?: Float.NaN,
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
        // Die aufgezeichnete Strecke, wenn die Datei sie mitbringt. Aus den Punkten neu
        // gerechnet weicht sie ab, und mit ihr die Effizienz in Wh/km.
        val strecke = meta.distanceM ?: gerechnet
        val avg = if (duration > 0) (strecke / (duration / 1000.0)).toFloat() else 0f
        val tripCharge = points.maxOfOrNull { it.chargeAh } ?: 0f // kumuliert → Endwert = Gesamt
        return SavedTrip(
            id = startEpoch,
            startedAt = startEpoch,
            distanceM = strecke,
            durationMs = duration,
            totalMs = total,
            avgSpeedMs = avg,
            maxSpeedMs = maxSpeed,
            energyWh = meta.energyWh ?: 0f,
            name = meta.name,
            chargeAh = tripCharge,
            points = points,
        )
    }
}
