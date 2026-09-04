package de.kewl.boatspeedy.nav

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Ein Geschwindigkeitszeichen am Wasser, wie OpenStreetMap es kennt.
 *
 * [kmh] ist der abgelesene Wert, [raw] der ursprüngliche Text — die Schilder sind nicht
 * einheitlich getaggt, und wenn wir uns beim Umrechnen vertun, soll wenigstens das
 * Original noch zu sehen sein.
 */
data class SpeedSign(
    val lat: Double,
    val lon: Double,
    val kmh: Double,
    val raw: String,
    /** Für welche Fahrtrichtung das Schild gilt (`upstream`/`downstream`), falls angegeben. */
    val impact: String? = null,
)

/**
 * Holt die Geschwindigkeitszeichen eines Ausschnitts von der Overpass-Schnittstelle.
 *
 * Warum überhaupt selbst holen: die Seezeichen-Kacheln von OpenSeaMap **zeichnen** das
 * Schild — ein rot umrandetes Quadrat —, aber sie schreiben die Zahl nicht hinein. Man
 * sieht, dass dort eine Begrenzung gilt, nicht welche. Antippen geht auch nicht, es sind
 * Bilder. Für den Wert führt kein Weg an den Rohdaten vorbei.
 */
object SpeedSignSource {

    private const val OVERPASS = "https://overpass-api.de/api/interpreter"

    /** Unterhalb dieser Zoomstufe stehen zu viele Schilder zu dicht – dann lieber keine. */
    const val MIN_ZOOM = 12.0

    /**
     * Liest den Zahlenwert aus den Tags eines Schildes.
     *
     * Die Daten sind uneinheitlich: im Testgebiet stand zehnmal `5 km/h`, dreimal
     * schlicht `8`, und vier Schilder trugen den Wert überhaupt nicht unter
     * `seamark:notice:information`, sondern unter `waterway:maxspeed`. Fünf Schilder
     * hatten gar keinen Wert — die haben nichts anzuzeigen und fallen weg.
     *
     * Knoten statt Kilometer je Stunde kommt an der Küste vor; ohne Umrechnung stünde
     * dort eine Zahl, die um den Faktor 1,85 danebenliegt.
     */
    fun parseSpeed(vararg candidates: String?): Pair<Double, String>? {
        for (c in candidates) {
            val text = c?.trim().orEmpty()
            if (text.isEmpty()) continue
            val number = Regex("""\d+(?:[.,]\d+)?""").find(text)?.value ?: continue
            val value = number.replace(',', '.').toDoubleOrNull() ?: continue
            if (value <= 0.0) continue
            val isKnots = Regex("""\b(kn|kt|kts|knot|knoten)\b""", RegexOption.IGNORE_CASE)
                .containsMatchIn(text)
            return (if (isKnots) value * 1.852 else value) to text
        }
        return null
    }

    fun fetch(south: Double, west: Double, north: Double, east: Double): List<SpeedSign>? {
        val json = query(south, west, north, east) ?: return null
        return parse(json)
    }

    internal fun parse(json: String): List<SpeedSign> = runCatching {
        val elements = JSONObject(json).optJSONArray("elements") ?: return emptyList()
        (0 until elements.length()).mapNotNull { i ->
            val el = elements.getJSONObject(i)
            val tags = el.optJSONObject("tags") ?: return@mapNotNull null
            val (kmh, raw) = parseSpeed(
                tags.optString("seamark:notice:information").takeIf { it.isNotEmpty() },
                tags.optString("waterway:maxspeed").takeIf { it.isNotEmpty() },
                tags.optString("maxspeed").takeIf { it.isNotEmpty() },
            ) ?: return@mapNotNull null
            val lat = if (el.has("lat")) el.getDouble("lat")
            else el.optJSONObject("center")?.optDouble("lat") ?: return@mapNotNull null
            val lon = if (el.has("lon")) el.getDouble("lon")
            else el.optJSONObject("center")?.optDouble("lon") ?: return@mapNotNull null
            SpeedSign(
                lat = lat,
                lon = lon,
                kmh = kmh,
                raw = raw,
                impact = tags.optString("seamark:notice:impact").takeIf { it.isNotEmpty() },
            )
        }
    }.getOrDefault(emptyList())

    private fun query(south: Double, west: Double, north: Double, east: Double): String? {
        val q = """
            [out:json][timeout:30];
            (
              node["seamark:notice:category"="speed_limit"]($south,$west,$north,$east);
              way["seamark:notice:category"="speed_limit"]($south,$west,$north,$east);
            );
            out tags center;
        """.trimIndent()
        return runCatching {
            val c = (URL(OVERPASS).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 15_000
                readTimeout = 40_000
                setRequestProperty("User-Agent", "BoatSpeedy")
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            }
            try {
                c.outputStream.use { it.write(("data=" + URLEncoder.encode(q, "UTF-8")).toByteArray()) }
                if (c.responseCode != 200) return@runCatching null
                c.inputStream.bufferedReader().use { it.readText() }
            } finally {
                c.disconnect()
            }
        }.getOrNull()
    }
}
