package de.kewl.boatspeedy.nav

import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor

/** Eine Kachel, benannt nach ihrer Südwestecke. */
data class TileId(val lat: Int, val lon: Int) {
    /** Etwa `n50e011` — dieselbe Schreibweise wie bei Höhendaten. */
    val name: String
        get() {
            val ns = if (lat >= 0) "n" else "s"
            val ew = if (lon >= 0) "e" else "w"
            return "%s%02d%s%03d".format(ns, abs(lat), ew, abs(lon))
        }
}

/** Was auf dem Gerät liegt. */
data class StoredTile(val id: TileId, val bytes: Long, val generated: String?)

/**
 * Die Wasserwege als Kacheln auf dem Gerät — damit Routing ohne Netz auskommt.
 *
 * Bisher fragte jede Route bei Overpass nach: öffentliche Server, von aller Welt
 * genutzt, und an manchen Tagen scheiterte jede zweite Anfrage. Auf dem Wasser, wo
 * Routing gebraucht wird, gibt es ohnehin oft keinen Empfang.
 *
 * Eine Kachel ist ein Grad breit und hoch, am Boden etwa 110 x 70 km. Ihr Inhalt hat
 * dieselbe Gestalt wie eine Overpass-Antwort, deshalb liest der Router beides mit
 * demselben Code — nur die Herkunft wechselt.
 */
object MapTiles {

    /** Wo die Kacheln liegen. Erzeugt aus OpenStreetMap, siehe boatspeedy-mapdata. */
    const val DEFAULT_BASE = "https://boatspeedy.wozise.de/mapdata/"

    private const val CONNECT_MS = 10_000
    private const val READ_MS = 60_000

    /* ------------------------------ Geometrie ------------------------------ */

    fun tilesFor(south: Double, west: Double, north: Double, east: Double): List<TileId> {
        val out = ArrayList<TileId>()
        var la = floor(south).toInt()
        while (la <= floor(north).toInt()) {
            var lo = floor(west).toInt()
            while (lo <= floor(east).toInt()) {
                out.add(TileId(la, lo))
                lo++
            }
            la++
        }
        return out
    }

    /**
     * Kacheln im Umkreis von [radiusKm] um einen Punkt. Der Umweg über ein Rechteck ist
     * Absicht: Kacheln sind Rechtecke, und ein exakter Kreis würde am Rand einzelne
     * herausfallen lassen, die man beim nächsten Kilometer doch braucht.
     */
    fun tilesWithin(lat: Double, lon: Double, radiusKm: Double): List<TileId> {
        val dLat = radiusKm / 111.32
        val dLon = radiusKm / (111.32 * cos(Math.toRadians(lat)).coerceAtLeast(0.01))
        return tilesFor(lat - dLat, lon - dLon, lat + dLat, lon + dLon)
    }

    /** Grobe Abschätzung, wie viele Kacheln ein Umkreis umfasst — für die Anzeige. */
    fun tileCountWithin(lat: Double, lon: Double, radiusKm: Double): Int =
        tilesWithin(lat, lon, radiusKm).size

    /* ------------------------------ Ablage ------------------------------ */

    fun dir(filesDir: File): File = File(filesDir, "mapdata").apply { mkdirs() }

    private fun file(dir: File, id: TileId) = File(dir, "${id.name}.json.gz")

    fun have(dir: File, id: TileId): Boolean = file(dir, id).isFile

    fun missing(dir: File, ids: List<TileId>): List<TileId> = ids.filterNot { have(dir, it) }

    fun stored(dir: File): List<StoredTile> =
        dir.listFiles { f -> f.name.endsWith(".json.gz") }?.mapNotNull { f ->
            val name = f.name.removeSuffix(".json.gz")
            parseName(name)?.let { StoredTile(it, f.length(), readGenerated(f)) }
        }?.sortedBy { it.id.name } ?: emptyList()

    fun storedBytes(dir: File): Long = stored(dir).sumOf { it.bytes }

    fun delete(dir: File, id: TileId): Boolean = file(dir, id).delete()

    fun deleteAll(dir: File): Int {
        val files = dir.listFiles { f -> f.name.endsWith(".json.gz") } ?: return 0
        return files.count { it.delete() }
    }

    internal fun parseName(name: String): TileId? {
        val m = Regex("^([ns])(\\d{2})([ew])(\\d{3})$").find(name) ?: return null
        val (ns, la, ew, lo) = m.destructured
        val lat = la.toInt() * if (ns == "n") 1 else -1
        val lon = lo.toInt() * if (ew == "e") 1 else -1
        return TileId(lat, lon)
    }

    private fun readGenerated(f: File): String? = runCatching {
        // Nur den Kopf lesen: Das Datum steht vorn, die Objekte dahinter sind Megabyte.
        GZIPInputStream(f.inputStream()).use { gz ->
            val head = ByteArray(200)
            val n = gz.read(head)
            if (n <= 0) return@use null
            Regex("\"generated\":\"([^\"]+)\"").find(String(head, 0, n))?.groupValues?.get(1)
        }
    }.getOrNull()

    /* ------------------------------ Lesen ------------------------------ */

    /**
     * Fügt die genannten Kacheln zu **einer** Antwort zusammen, in der Gestalt, die der
     * Router ohnehin von Overpass kennt. Fehlt auch nur eine, gibt es `null` — dann
     * bliebe ein Loch im Netz, und eine Route mit Loch wäre schlimmer als keine.
     */
    fun read(dir: File, ids: List<TileId>): String? {
        if (ids.isEmpty()) return null
        val sb = StringBuilder("""{"elements":[""")
        var first = true
        for (id in ids) {
            val f = file(dir, id)
            if (!f.isFile) return null
            val text = runCatching {
                GZIPInputStream(f.inputStream()).bufferedReader().use { it.readText() }
            }.getOrNull() ?: return null
            val arr = runCatching { JSONObject(text).optJSONArray("elements") }.getOrNull() ?: continue
            for (i in 0 until arr.length()) {
                if (!first) sb.append(',')
                sb.append(arr.getJSONObject(i).toString())
                first = false
            }
        }
        sb.append("]}")
        return sb.toString()
    }

    /* ------------------------------ Laden ------------------------------ */

    data class Index(val generated: String, val tiles: Map<String, Long>)

    fun fetchIndex(base: String = DEFAULT_BASE): Index? = runCatching {
        val body = get("${base.trimEnd('/')}/index.json") ?: return null
        val o = JSONObject(String(body))
        val tiles = o.optJSONObject("tiles") ?: return null
        val sizes = HashMap<String, Long>()
        for (k in tiles.keys()) {
            sizes[k] = tiles.getJSONObject(k).optLong("bytes")
        }
        Index(o.optString("generated"), sizes)
    }.getOrNull()

    /** Lädt eine Kachel und legt sie gepackt ab. Liefert die Größe, oder `null`. */
    fun download(dir: File, id: TileId, base: String = DEFAULT_BASE): Long? {
        val body = get("${base.trimEnd('/')}/${id.name}.json") ?: return null
        return runCatching {
            val tmp = File(dir, "${id.name}.part")
            GZIPOutputStream(tmp.outputStream()).use { it.write(body) }
            // Erst umbenennen, wenn alles heil ist — sonst bliebe nach einem Abbruch
            // eine halbe Kachel liegen, die beim Lesen als vorhanden gilt.
            if (!tmp.renameTo(file(dir, id))) {
                tmp.delete()
                return null
            }
            file(dir, id).length()
        }.getOrNull()
    }

    private fun get(url: String): ByteArray? = runCatching {
        val c = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_MS
            readTimeout = READ_MS
            setRequestProperty("User-Agent", "BoatSpeedy")
        }
        try {
            if (c.responseCode != 200) return@runCatching null
            c.inputStream.use { it.readBytes() }
        } finally {
            c.disconnect()
        }
    }.getOrNull()

    /** Wie viele Kacheln ein Umkreis kostet, in Byte — aus dem Verzeichnis des Servers. */
    fun sizeOf(index: Index?, ids: List<TileId>): Long =
        if (index == null) 0L else ids.sumOf { index.tiles[it.name] ?: 0L }

    /** Voreingestellter Umkreis beim Laden. */
    const val DEFAULT_RADIUS_KM = 150.0

    fun roundUpMb(bytes: Long): Double = ceil(bytes / 1048576.0 * 10) / 10
}
