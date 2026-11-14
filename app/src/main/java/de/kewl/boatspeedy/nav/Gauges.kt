package de.kewl.boatspeedy.nav

import org.json.JSONArray
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.OffsetDateTime

/** Wie der Wasserstand im Vergleich zu den langjährigen Werten steht. */
enum class GaugeLevel {
    /** Unter dem mittleren Niedrigwasser. */
    LOW,
    NORMAL,
    /** Über dem mittleren Hochwasser. */
    HIGH,
    /** Über dem höchsten Schifffahrtswasserstand: Die Schifffahrt ist eingestellt. */
    ABOVE_HSW,
    UNKNOWN,
}

/** Ein Pegel der WSV mit seinem letzten Wasserstand. */
data class Gauge(
    val uuid: String,
    val name: String,
    val water: String?,
    /** Fluss-Kilometer, wo ihn die WSV führt. */
    val km: Double?,
    val lat: Double,
    val lon: Double,
    val valueCm: Double?,
    val time: OffsetDateTime?,
    val level: GaugeLevel,
)

/**
 * Pegel von PEGELONLINE, der Schnittstelle der WSV. Frei nutzbar (DL-DE Zero 2.0).
 *
 * Alle rund 740 Pegel auf einmal sind gepackt nur 60 kB und kommen in einer halben
 * Sekunde. Deshalb direkt vom Handy und ohne Umweg über den Kachelserver. Es gibt nur
 * Bundeswasserstraßen; Landespegel an kleinen Flüssen fehlen.
 */
object Gauges {

    private const val BASE = "https://www.pegelonline.wsv.de/webservices/rest-api/v2/stations"
    private const val ALL = "$BASE.json?includeTimeseries=true&includeCurrentMeasurement=true&timeseries=W"
    private const val FILE = "gauges.json"

    /** Die meisten Pegel melden alle 15 Minuten. Öfter zu fragen brächte nichts Neues. */
    private const val FRESH_MS = 15 * 60_000L

    fun load(cacheDir: File, nowMs: Long = System.currentTimeMillis()): List<Gauge> {
        val f = File(cacheDir, FILE)
        if (!f.exists() || nowMs - f.lastModified() > FRESH_MS) {
            get(ALL)?.let { text ->
                if (runCatching { parse(text) }.isSuccess) {
                    val tmp = File(cacheDir, "$FILE.tmp")
                    tmp.writeText(text)
                    tmp.renameTo(f)
                }
            }
        }
        return if (f.exists()) runCatching { parse(f.readText()) }.getOrDefault(emptyList()) else emptyList()
    }

    fun parse(json: String): List<Gauge> {
        val arr = JSONArray(json)
        return (0 until arr.length()).mapNotNull { i ->
            val s = arr.optJSONObject(i) ?: return@mapNotNull null
            // Rund 50 Pegel haben keine Lage, die lassen sich nicht zeigen.
            if (!s.has("latitude") || !s.has("longitude")) return@mapNotNull null
            val ts = s.optJSONArray("timeseries")?.let { t ->
                (0 until t.length()).map { t.getJSONObject(it) }.firstOrNull { it.optString("shortname") == "W" }
            }
            val m = ts?.optJSONObject("currentMeasurement")
            Gauge(
                uuid = s.optString("uuid"),
                name = readableName(s.optString("longname")),
                water = s.optJSONObject("water")?.optString("longname")?.takeIf { it.isNotBlank() }?.let(::readableName),
                km = s.optDouble("km").takeIf { !it.isNaN() },
                lat = s.getDouble("latitude"),
                lon = s.getDouble("longitude"),
                valueCm = m?.optDouble("value")?.takeIf { !it.isNaN() },
                time = m?.optString("timestamp")?.let { runCatching { OffsetDateTime.parse(it) }.getOrNull() },
                level = when {
                    m == null -> GaugeLevel.UNKNOWN
                    m.optString("stateNswHsw") == "high" -> GaugeLevel.ABOVE_HSW
                    else -> when (m.optString("stateMnwMhw")) {
                        "low" -> GaugeLevel.LOW
                        "normal" -> GaugeLevel.NORMAL
                        "high" -> GaugeLevel.HIGH
                        else -> GaugeLevel.UNKNOWN
                    }
                },
            )
        }
    }

    fun inBox(all: List<Gauge>, south: Double, west: Double, north: Double, east: Double): List<Gauge> =
        all.filter { it.lat in south..north && it.lon in west..east }

    /**
     * Wie sich der Wasserstand in der letzten Stunde bewegt hat, in Zentimetern. Steigt
     * der Fluss, ist das vor dem Ablegen die wichtigere Zahl als der Stand selbst.
     */
    fun changeLastHourCm(uuid: String): Double? {
        val text = get("$BASE/$uuid/W/measurements.json?start=PT2H") ?: return null
        return runCatching { changeLastHour(text) }.getOrNull()
    }

    fun changeLastHour(json: String): Double? {
        val arr = JSONArray(json)
        val werte = (0 until arr.length()).mapNotNull { i ->
            val o = arr.getJSONObject(i)
            val t = runCatching { OffsetDateTime.parse(o.getString("timestamp")) }.getOrNull()
            val v = o.optDouble("value").takeIf { !it.isNaN() }
            if (t != null && v != null) t to v else null
        }
        val (tLetzt, vLetzt) = werte.maxByOrNull { it.first } ?: return null
        val vorher = werte.filter { !it.first.isAfter(tLetzt.minusMinutes(60)) }.maxByOrNull { it.first } ?: return null
        // Liegt der Wert davor deutlich weiter zurück, ist es keine Stunde mehr.
        if (vorher.first.isBefore(tLetzt.minusMinutes(90))) return null
        return vLetzt - vorher.second
    }

    /**
     * „HAMBURG ST. PAULI“ wird „Hamburg St. Pauli“. Kurze Zusätze wie UP und OP (Unter-
     * und Oberpegel an einer Schleuse) bleiben groß, so stehen sie auch am Bauwerk.
     */
    fun readableName(s: String): String = s.trim().split(" ").joinToString(" ") { wort ->
        if (wort.length <= 2) {
            wort
        } else {
            wort.split("-").joinToString("-") { teil ->
                teil.lowercase().replaceFirstChar { it.titlecase() }
            }
        }
    }

    private fun get(url: String): String? = runCatching {
        val c = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 20_000
            setRequestProperty("User-Agent", "BoatSpeedy")
        }
        try {
            if (c.responseCode != 200) return@runCatching null
            c.inputStream.use { String(it.readBytes()) }
        } finally {
            c.disconnect()
        }
    }.getOrNull()
}
