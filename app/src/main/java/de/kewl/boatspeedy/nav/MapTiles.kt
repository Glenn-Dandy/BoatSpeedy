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

/** Ein Seezeichen aus den Kacheln, mit seinen Rohmerkmalen. */
data class SeamarkPoi(val lat: Double, val lon: Double, val tags: Map<String, String>)

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
     * Kacheln für eine Strecke von [from] nach [to].
     *
     * Der Rand wächst **mit der Länge**, und das ist der Kern: Ein Fluss läuft nicht im
     * Rechteck zwischen Start und Ziel. Der Main zieht bei Frankfurt über den fünfzigsten
     * Breitengrad nach Norden, bevor er nach Aschaffenburg zurückschwenkt — mit festen
     * 0,05° Rand fehlte genau diese Reihe, und die Route von Basel scheiterte mit „kein
     * durchgehender Wasserweg", obwohl der Weg existiert. Nachgerechnet: mit neun Kacheln
     * findet sich nichts, mit zwölf sind es 425 km.
     *
     * Ein Fünftel der Spannweite deckt auch großzügige Bögen ab; bei kurzen Strecken
     * bleibt es bei den 0,05°, damit ein Ziel um die Ecke nicht ein halbes Land lädt.
     */
    fun tilesForRoute(from: LatLon, to: LatLon): List<TileId> {
        val latSpan = kotlin.math.abs(from.lat - to.lat)
        val lonSpan = kotlin.math.abs(from.lon - to.lon)
        val rand = (0.2 * maxOf(latSpan, lonSpan)).coerceIn(MIN_ROUTE_PAD, MAX_ROUTE_PAD)
        return tilesFor(
            minOf(from.lat, to.lat) - rand,
            minOf(from.lon, to.lon) - rand,
            maxOf(from.lat, to.lat) + rand,
            maxOf(from.lon, to.lon) + rand,
        )
    }

    private const val MIN_ROUTE_PAD = 0.05
    private const val MAX_ROUTE_PAD = 1.0

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

    /**
     * Kacheln, die zwar daliegen, aber älter sind als der Stand auf dem Server.
     *
     * Ohne diese Prüfung wird eine einmal geholte Kachel **nie** aufgefrischt: [missing]
     * fragt nur, ob die Datei existiert. Genau daran ist eine Fahrt gescheitert — auf dem
     * Gerät lagen Kacheln aus dem Deutschland-Lauf, in denen der Grand Canal d'Alsace
     * fehlte. Ab Basel läuft die Fahrrinne aber über die französische Seite, und so riss
     * das Netz mitten im Rhein, während der Server die vollständigen Daten längst hatte.
     *
     * Verglichen wird **je Kachel**, nicht gegen ein Gesamtdatum.
     *
     * Ein Lauf über ein einzelnes Land schneidet trotzdem alle Kacheln neu — ein Fluss
     * läuft über die Grenze, und die Kachel braucht beide Seiten. Am Gesamtdatum gemessen
     * galt danach alles als veraltet: Nach einem Deutschland-Lauf wollte die App auch die
     * portugiesischen Kacheln neu holen. Der Server datiert eine Kachel jetzt nur um, wenn
     * sich ihr Inhalt geändert hat, und schreibt das Datum in den Index-Eintrag.
     *
     * Fehlt es dort — ein Server mit älterem Verzeichnis —, gilt weiter das Gesamtdatum.
     * Lieber einmal zu viel geladen als eine Lücke im Netz übersehen.
     */
    fun outdated(dir: File, index: Index?): List<StoredTile> {
        val stand = index?.generated?.takeIf { it.isNotBlank() } ?: return emptyList()
        return stored(dir).filter {
            val soll = index.dates[it.id.name]?.takeIf { d -> d.isNotBlank() } ?: stand
            it.generated == null || it.generated < soll
        }
    }

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
     * Gibt die genannten Kacheln **einzeln** heraus, nicht zu einer Antwort verklebt.
     *
     * Zusammengeklebt lag der gesamte Ausschnitt als eine Zeichenkette im Speicher und
     * beim Auswerten ein zweites Mal als Objektbaum: bei tausend Kilometern zusammen
     * über dreihundert Megabyte, womit die App abstürzt. So wird immer nur eine Kachel
     * gehalten, und der Aufrufer sammelt daraus, was er braucht.
     *
     * Liefert `false`, wenn eine Kachel fehlt — dann bliebe ein Loch im Netz, und eine
     * Route mit Loch wäre schlimmer als keine.
     */
    fun forEach(dir: File, ids: List<TileId>, block: (String) -> Unit): Boolean {
        if (ids.isEmpty()) return false
        for (id in ids) {
            val f = file(dir, id)
            if (!f.isFile) return false
            val text = runCatching {
                GZIPInputStream(f.inputStream()).bufferedReader().use { it.readText() }
            }.getOrNull() ?: return false
            block(text)
        }
        return true
    }

    /* ------------------------------ Laden ------------------------------ */

    /**
     * Das Verzeichnis des Servers.
     *
     * @param generated Stand des ganzen Bestands — greift nur noch dort, wo eine Kachel
     *   kein eigenes Datum hat.
     * @param tiles Größe je Kachel, für die Frage „was kostet der Download".
     * @param dates Stand je Kachel. Danach entscheidet sich, was aufzufrischen ist.
     */
    data class Index(
        val generated: String,
        val tiles: Map<String, Long>,
        val dates: Map<String, String> = emptyMap(),
    )

    fun fetchIndex(base: String = DEFAULT_BASE): Index? = runCatching {
        val body = get("${base.trimEnd('/')}/index.json") ?: return null
        val o = JSONObject(String(body))
        val tiles = o.optJSONObject("tiles") ?: return null
        val sizes = HashMap<String, Long>()
        val dates = HashMap<String, String>()
        for (k in tiles.keys()) {
            val e = tiles.getJSONObject(k)
            sizes[k] = e.optLong("bytes")
            e.optString("generated").takeIf { it.isNotBlank() }?.let { dates[k] = it }
        }
        Index(o.optString("generated"), sizes, dates)
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

    /**
     * Liest die Seezeichen eines Ausschnitts aus den vorhandenen Kacheln.
     *
     * Fehlende Kacheln werden übergangen, nicht als Fehler behandelt: Anders als beim
     * Routen ist ein Loch hier harmlos — dann fehlen eben ein paar Zeichen, statt dass
     * eine Route falsch abbricht.
     */
    fun readSeamarks(
        dir: File,
        south: Double,
        west: Double,
        north: Double,
        east: Double,
    ): List<SeamarkPoi> {
        val out = ArrayList<SeamarkPoi>()
        for (id in tilesFor(south, west, north, east)) {
            val f = file(dir, id)
            if (!f.isFile) continue
            val text = runCatching {
                GZIPInputStream(f.inputStream()).bufferedReader().use { it.readText() }
            }.getOrNull() ?: continue
            val arr = runCatching { JSONObject(text).optJSONArray("elements") }.getOrNull() ?: continue
            for (i in 0 until arr.length()) {
                val el = arr.getJSONObject(i)
                if (el.optString("type") != "node") continue
                val tags = el.optJSONObject("tags") ?: continue
                if (!tags.has("seamark:type")) continue
                val lat = el.optDouble("lat", Double.NaN)
                val lon = el.optDouble("lon", Double.NaN)
                if (lat.isNaN() || lon.isNaN()) continue
                if (lat < south || lat > north || lon < west || lon > east) continue
                val map = HashMap<String, String>()
                for (k in tags.keys()) map[k] = tags.getString(k)
                out.add(SeamarkPoi(lat, lon, map))
            }
        }
        return out
    }

    /** Wie viele Kacheln ein Umkreis kostet, in Byte — aus dem Verzeichnis des Servers. */
    fun sizeOf(index: Index?, ids: List<TileId>): Long =
        if (index == null) 0L else ids.sumOf { index.tiles[it.name] ?: 0L }

    /** Voreingestellter Umkreis beim Laden. */
    const val DEFAULT_RADIUS_KM = 150.0

    fun roundUpMb(bytes: Long): Double = ceil(bytes / 1048576.0 * 10) / 10
}
