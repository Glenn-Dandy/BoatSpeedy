package de.kewl.boatspeedy.weather

import android.content.Context
import de.kewl.boatspeedy.R
import de.kewl.boatspeedy.alarm.AlarmPlayer
import de.kewl.boatspeedy.data.AlarmSound
import de.kewl.boatspeedy.util.Notifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/** Eine aktive DWD-Wetterwarnung (relevant für Boote: Gewitter / Sturm / Wind). */
data class WeatherWarning(
    val id: String,
    val event: String,
    val headline: String,
    val severity: String,
    val expiresMs: Long?,
)

/**
 * Fragt DWD-Warnungen für eine Position ab (über Bright Sky, `api.brightsky.dev/alerts`,
 * DWD-Datenbasis, kein API-Key) und benachrichtigt bei neuen Gewitter-/Sturmwarnungen.
 * Prozessweit, damit Dashboard-Prüfung und Fahrtdienst denselben „schon gemeldet"-Stand teilen.
 */
object WeatherRepository {

    private val _active = MutableStateFlow<List<WeatherWarning>>(emptyList())
    val active: StateFlow<List<WeatherWarning>> = _active.asStateFlow()

    private val notified = HashSet<String>()

    private const val CHANNEL = "weather"
    private const val NOTIF_ID = 4

    // Nur für Boote gefährliche Lagen; weniger Fehlalarme.
    private val EVENT_KEYS = listOf(
        "gewitter", "thunderstorm", "sturm", "wind", "böe", "boe", "orkan",
        "gale", "squall", "hurricane", "storm",
    )
    private val SEVERITIES = listOf("moderate", "severe", "extreme")

    /** Prüft & meldet neue Warnungen. Rückgabe: Anzahl aktiver relevanter Warnungen, -1 = Netzfehler. */
    suspend fun check(
        context: Context,
        lat: Double,
        lon: Double,
        alarmOn: Boolean,
        sound: AlarmSound,
    ): Int = withContext(Dispatchers.IO) {
        val warnings = runCatching { fetch(lat, lon) }.getOrNull() ?: return@withContext -1
        _active.value = warnings
        val fresh = warnings.filter { it.id !in notified }
        if (fresh.isNotEmpty()) {
            fresh.forEach { notified.add(it.id) }
            val w = fresh.first()
            Notifier.notify(
                context,
                CHANNEL,
                context.getString(R.string.weather_channel),
                NOTIF_ID,
                context.getString(R.string.weather_notif_title),
                w.headline.ifBlank { w.event },
                high = true,
            )
            if (alarmOn) AlarmPlayer.play(context, sound, loop = false)
        }
        // „Schon gemeldet"-Liste auf die noch aktiven Warnungen eindampfen.
        notified.retainAll(warnings.map { it.id }.toSet())
        warnings.size
    }

    fun clear() {
        _active.value = emptyList()
        // Auch die „schon gemeldet"-Merkliste leeren, damit nach Aus/Ein (oder beim
        // erneuten Testen) dieselbe Warnung wieder Benachrichtigung + Ton auslöst.
        notified.clear()
    }

    private fun fetch(lat: Double, lon: Double): List<WeatherWarning> {
        val url = "https://api.brightsky.dev/alerts?lat=$lat&lon=$lon"
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8000
            readTimeout = 8000
            setRequestProperty("User-Agent", "BoatSpeedy")
        }
        val body = try {
            if (conn.responseCode != HttpURLConnection.HTTP_OK) return emptyList()
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
        val arr = JSONObject(body).optJSONArray("alerts") ?: return emptyList()
        val out = ArrayList<WeatherWarning>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val severity = o.optString("severity").lowercase()
            if (severity !in SEVERITIES) continue
            val eventDe = o.optString("event_de")
            val eventEn = o.optString("event_en")
            val evLower = "$eventDe $eventEn".lowercase()
            if (EVENT_KEYS.none { evLower.contains(it) }) continue
            val id = o.optString("id").ifBlank { "$eventDe|${o.optString("expires")}" }
            out.add(
                WeatherWarning(
                    id = id,
                    event = eventDe.ifBlank { eventEn },
                    headline = o.optString("headline_de").ifBlank { o.optString("headline_en") },
                    severity = severity,
                    expiresMs = parseTime(o.optString("expires")),
                ),
            )
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
}
