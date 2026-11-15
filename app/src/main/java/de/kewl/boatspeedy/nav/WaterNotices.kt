package de.kewl.boatspeedy.nav

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import kotlin.math.cos

/** Ein Zeitraum, in dem eine Einschränkung gilt. Uhrzeiten als „13:20“, so wie ELWIS sie führt. */
data class NoticePeriod(
    val from: LocalDate?,
    val to: LocalDate?,
    val timeFrom: String?,
    val timeTo: String?,
    /** CON durchgehend, DAY täglich, MON bis SUN, WRK/WRD werktags, WKN am Wochenende. */
    val interval: String?,
)

/** Was an einem Ort eingeschränkt ist, als Code nach NtS, etwa OBSTRU für gesperrt. */
data class NoticeLimit(
    val code: String?,
    val value: String?,
    val unit: String?,
    val periods: List<NoticePeriod>,
)

/** Ein Ort einer Meldung: ein Objekt (Schleuse, Brücke) oder ein Streckenabschnitt. */
data class NoticePlace(
    val name: String?,
    /** LCK Schleuse, BRI Brücke, FWY Fahrwasser und so weiter. */
    val type: String?,
    val fairway: String?,
    /** Ein Abschnitt hat Anfang und Ende, ein Objekt einen Punkt. */
    val section: Boolean,
    val points: List<LatLon>,
    val limits: List<NoticeLimit>,
)

/**
 * Eine Nachricht für die Binnenschifffahrt von ELWIS.
 *
 * Der [text] bleibt, wie ELWIS ihn liefert: Die Nutzungsbedingungen erlauben die
 * Weitergabe nur unverändert und mit www.elwis.de als Quelle.
 */
data class WaterNotice(
    val id: String,
    val from: LocalDate?,
    val to: LocalDate?,
    val text: String?,
    /** Die ausgebende Stelle, etwa „WSA Spree-Havel“. */
    val by: String?,
    val places: List<NoticePlace>,
) {
    /** Gesperrt, nicht nur eingeschränkt. Das Wort, auf das es vor der Fahrt ankommt. */
    val isClosure: Boolean get() = places.any { p -> p.limits.any { it.code == "OBSTRU" } }

    /** Wo das Symbol auf der Karte steht: am ersten Ort, beim Abschnitt an seinem Anfang. */
    val anchor: LatLon get() = places.first().points.first()
}

/**
 * Schifffahrtsmeldungen von ELWIS, über den Kachelserver.
 *
 * ELWIS bietet sie nur als SOAP an, rund 1,5 MB XML in einer halben Minute. Der
 * Kachelserver holt sie alle 30 Minuten ab und legt sie gekürzt als `nts.json` ab, rund
 * 70 kB (boatspeedy-mapdata, `nts/nts.py`). Das gibt es nur für Bundeswasserstraßen.
 */
object WaterNotices {

    private const val FILE = "nts.json"

    /** So lange gilt eine geholte Fassung. Der Server holt alle 30 Minuten neu. */
    private const val FRESH_MS = 15 * 60_000L

    /**
     * Wie weit eine Meldung von der Route entfernt liegen darf. ELWIS setzt Schleusen und
     * Abschnittsenden auf die Achse der Wasserstraße, die Route läuft aus OSM-Linien:
     * Beide liegen selten mehr als ein paar Dutzend Meter auseinander, an breiten Flüssen
     * und Schleusenkanälen auch mehr.
     */
    const val ROUTE_RADIUS_M = 300.0

    /**
     * Bei einem Abschnitt reicht es nicht, nur die Enden zu prüfen: Wer mitten darin
     * ablegt, kommt an keinem vorbei. Dann zählt die gerade Linie zwischen den Enden, mit
     * einem Spielraum für die Krümmung des Flusses. Gedeckelt, sonst fiele ein paralleler
     * Kanal neben einem langen Abschnitt mit hinein.
     */
    private const val SECTION_SLACK_MAX_M = 1_000.0

    /** Liest die Meldungen, holt sie neu, wenn die gespeicherte Fassung zu alt ist. */
    fun load(cacheDir: File, nowMs: Long = System.currentTimeMillis()): List<WaterNotice> {
        val f = File(cacheDir, FILE)
        if (!f.exists() || nowMs - f.lastModified() > FRESH_MS) {
            MapTiles.fetchFile(FILE)?.let { bytes ->
                // Nur was sich lesen lässt, ersetzt die alte Fassung.
                if (runCatching { parse(String(bytes)) }.isSuccess) {
                    val tmp = File(cacheDir, "$FILE.tmp")
                    tmp.writeBytes(bytes)
                    tmp.renameTo(f)
                }
            }
        }
        // Ohne Netz bleibt die letzte Fassung. Eine Sperrung von gestern ist mehr wert
        // als gar keine Auskunft.
        return if (f.exists()) runCatching { parse(f.readText()) }.getOrDefault(emptyList()) else emptyList()
    }

    /**
     * Wann der Server die Meldungen zuletzt bei ELWIS geholt hat, aus der gespeicherten
     * Fassung. Ohne Netz zeigt die App diese Fassung weiter, und dann muss man sehen
     * können, wie alt sie ist.
     */
    fun generatedAt(cacheDir: File): java.time.Instant? {
        val f = File(cacheDir, FILE)
        if (!f.exists()) return null
        return runCatching { generated(f.readText()) }.getOrNull()
    }

    internal fun generated(json: String): java.time.Instant? = runCatching {
        java.time.OffsetDateTime.parse(JSONObject(json).optString("generated")).toInstant()
    }.getOrNull()

    /** Ab diesem Alter steht der Stand der Meldungen im Fenster. */
    const val STALE_MS = 60 * 60 * 1000L

    fun parse(json: String): List<WaterNotice> {
        val arr = JSONObject(json).optJSONArray("messages") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val m = arr.getJSONObject(i)
            val places = m.optJSONArray("places").objects().mapNotNull(::place)
            if (places.isEmpty()) return@mapNotNull null
            WaterNotice(
                id = m.optString("id"),
                from = date(m.str("from")),
                to = date(m.str("to")),
                text = m.str("text"),
                by = m.str("by"),
                places = places,
            )
        }
    }

    private fun place(o: JSONObject): NoticePlace? {
        val pts = o.optJSONArray("points") ?: return null
        val points = (0 until pts.length()).mapNotNull { j ->
            val p = pts.optJSONArray(j) ?: return@mapNotNull null
            LatLon(p.optDouble(0), p.optDouble(1)).takeIf { !it.lat.isNaN() && !it.lon.isNaN() }
        }
        if (points.isEmpty()) return null
        return NoticePlace(
            name = o.str("name"),
            type = o.str("type"),
            fairway = o.str("fairway"),
            section = o.optBoolean("section"),
            points = points,
            limits = o.optJSONArray("limits").objects().map { l ->
                NoticeLimit(
                    code = l.str("code"),
                    value = l.str("value"),
                    unit = l.str("unit"),
                    periods = l.optJSONArray("periods").objects().map { p ->
                        NoticePeriod(
                            from = date(p.str("from")),
                            to = date(p.str("to")),
                            timeFrom = p.str("tfrom"),
                            timeTo = p.str("tto"),
                            interval = p.str("interval"),
                        )
                    },
                )
            },
        )
    }

    /**
     * Gilt die Meldung irgendwann zwischen [from] und [to]? Erst der Gültigkeitszeitraum
     * der Meldung, dann, falls angegeben, die Zeiträume ihrer Einschränkungen: Eine
     * Anordnung gilt oft jahrelang, die Sperrung darin nur an zwei Tagen.
     */
    fun activeBetween(n: WaterNotice, from: LocalDate, to: LocalDate): Boolean {
        if (!overlaps(n.from, n.to, from, to)) return false
        val periods = n.places.flatMap { p -> p.limits.flatMap { it.periods } }
        if (periods.isEmpty()) return true
        return periods.any { overlaps(it.from, it.to, from, to) }
    }

    private fun overlaps(a: LocalDate?, b: LocalDate?, from: LocalDate, to: LocalDate): Boolean =
        (a == null || !a.isAfter(to)) && (b == null || !b.isBefore(from))

    /** Die Meldungen mit einem Ort im Ausschnitt. */
    fun inBox(
        all: List<WaterNotice>,
        south: Double,
        west: Double,
        north: Double,
        east: Double,
    ): List<WaterNotice> = all.filter { n ->
        n.places.any { p -> p.points.any { it.lat in south..north && it.lon in west..east } }
    }

    /** So weit darf eine Schleusenmeldung vom Symbol oder der Kammer in OSM entfernt sein. */
    const val LOCK_RADIUS_M = 300.0

    /** Arten von Orten in ELWIS, die eine Schleuse oder eine ihrer Kammern meinen. */
    private val LOCK_TYPES = setOf("LCK", "LKB")

    /**
     * Die Meldungen zu einer Schleuse. Gesucht wird an der Kammer, wenn OSM sie als Linie
     * kennt, sonst am Symbol. Nur Orte, die ELWIS selbst als Schleuse oder Kammer führt:
     * Eine Brücke 200 m daneben ist eine andere Auskunft.
     */
    fun atLock(all: List<WaterNotice>, lat: Double, lon: Double, line: List<LatLon> = emptyList()): List<WaterNotice> {
        val ziel = line.ifEmpty { listOf(LatLon(lat, lon)) }
        return all.filter { n ->
            n.places.any { p ->
                p.type in LOCK_TYPES && p.points.any { distanceToPathM(it, ziel) <= LOCK_RADIUS_M }
            }
        }
    }

    /** Die Meldungen, die an der Strecke [path] liegen. */
    fun alongRoute(all: List<WaterNotice>, path: List<LatLon>): List<WaterNotice> {
        if (path.isEmpty()) return emptyList()
        return all.filter { n -> n.places.any { touches(it, path) } }
    }

    private fun touches(p: NoticePlace, path: List<LatLon>): Boolean {
        val nah = { pt: LatLon -> distanceToPathM(pt, path) <= ROUTE_RADIUS_M }
        if (!p.section || p.points.size < 2) return p.points.any(nah)
        val a = p.points.first()
        val b = p.points.last()
        // **Ein Ende an der Route reicht nicht.** Landwehrkanal und Charlottenburger
        // Verbindungskanal beginnen an der Spree; wer auf der Spree vorbeifährt, kam
        // sonst an ihren Sperrungen vorbei, ohne je hineinzufahren. Die Route muss durch
        // den Abschnitt führen: beide Enden an ihr, oder Start oder Ziel liegen darin.
        if (nah(a) && nah(b)) return true
        val slack = ROUTE_RADIUS_M + (distanceM(a, b) * 0.1).coerceAtMost(SECTION_SLACK_MAX_M)
        // Anfang oder Ende der Fahrt liegen im Abschnitt.
        return listOf(path.first(), path.last()).any { q ->
            val (d, t) = segmentDistance(q, a, b)
            t in 0.0..1.0 && d <= slack
        }
    }

    /** Kürzeste Entfernung eines Punkts zu einer Linie, in Metern. */
    fun distanceToPathM(p: LatLon, path: List<LatLon>): Double {
        if (path.size == 1) return distanceM(p, path[0])
        return path.zipWithNext().minOf { (a, b) -> segmentDistance(p, a, b).first }
    }

    /**
     * Abstand von [p] zur Strecke [a]–[b] und wo auf ihr der nächste Punkt liegt
     * (0 = bei a, 1 = bei b, außerhalb davon jenseits der Enden). Eben gerechnet um [p]
     * herum, das ist auf diesen Entfernungen genau genug.
     */
    private fun segmentDistance(p: LatLon, a: LatLon, b: LatLon): Pair<Double, Double> {
        val mLat = 111_320.0
        val mLon = 111_320.0 * cos(Math.toRadians(p.lat))
        val ax = (a.lon - p.lon) * mLon
        val ay = (a.lat - p.lat) * mLat
        val bx = (b.lon - p.lon) * mLon
        val by = (b.lat - p.lat) * mLat
        val dx = bx - ax
        val dy = by - ay
        val len2 = dx * dx + dy * dy
        val t = if (len2 == 0.0) 0.0 else -(ax * dx + ay * dy) / len2
        val c = t.coerceIn(0.0, 1.0)
        val x = ax + c * dx
        val y = ay + c * dy
        return Pair(kotlin.math.sqrt(x * x + y * y), t)
    }

    private fun date(s: String?): LocalDate? = s?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    private fun JSONObject.str(key: String): String? =
        if (has(key) && !isNull(key)) optString(key).takeIf { it.isNotBlank() } else null

    private fun JSONArray?.objects(): List<JSONObject> =
        if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }
}
