package de.kewl.boatspeedy.nav

import de.kewl.boatspeedy.data.Craft
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.PriorityQueue

/** Ein Punkt auf der Karte – bewusst ohne osmdroid-Typen, damit das hier testbar bleibt. */
data class LatLon(val lat: Double, val lon: Double)

/** Was auf dem Weg liegen kann. Ein Wehr heißt in aller Regel: hier ist Schluss. */
enum class ObstacleKind { LOCK, WEIR, SLUICE, DAM }

/** Eine Schleuse, ein Wehr oder Ähnliches auf der Route. */
data class Obstacle(val lat: Double, val lon: Double, val kind: ObstacleKind, val name: String?)

/** Wie zum Ziel gerechnet wird. */
enum class NavMode { LINE, ROUTE }

/**
 * Ein gesetztes Ziel samt Weg dorthin.
 * [path] enthält bei [NavMode.LINE] nur Start und Ziel, bei [NavMode.ROUTE] den
 * Verlauf entlang des Fahrwassers.
 */
data class NavTarget(
    val target: LatLon,
    val mode: NavMode,
    val path: List<LatLon>,
    val distanceM: Double,
    /** Der Teil entlang des Fahrwassers; der Rest davor und danach ist Luftlinie. */
    val water: List<LatLon> = emptyList(),
    /** Schleusen und Wehre, die auf dem Weg liegen. */
    val obstacles: List<Obstacle> = emptyList(),
)

/**
 * Entfernung in Metern (Haversine). Bewusst selbst gerechnet statt über
 * `Location.distanceBetween`: das ist eine Android-Klasse und im Unit-Test nur eine
 * Attrappe, die 0 zurückgibt. So bleibt die ganze Routenrechnung ohne Android prüfbar,
 * und auf den Entfernungen, um die es hier geht, ist der Unterschied kleiner als ein Meter.
 */
fun distanceM(a: LatLon, b: LatLon): Double {
    val r = 6_371_000.0
    val p1 = Math.toRadians(a.lat)
    val p2 = Math.toRadians(b.lat)
    val dp = Math.toRadians(b.lat - a.lat)
    val dl = Math.toRadians(b.lon - a.lon)
    val h = kotlin.math.sin(dp / 2).let { it * it } +
        kotlin.math.cos(p1) * kotlin.math.cos(p2) * kotlin.math.sin(dl / 2).let { it * it }
    return 2 * r * kotlin.math.asin(kotlin.math.sqrt(h).coerceAtMost(1.0))
}

/**
 * Rechtweisende Peilung von [a] nach [b] in Grad (0 = Nord, im Uhrzeigersinn).
 */
fun bearingDeg(a: LatLon, b: LatLon): Float {
    val p1 = Math.toRadians(a.lat)
    val p2 = Math.toRadians(b.lat)
    val dl = Math.toRadians(b.lon - a.lon)
    val y = kotlin.math.sin(dl) * kotlin.math.cos(p2)
    val x = kotlin.math.cos(p1) * kotlin.math.sin(p2) -
        kotlin.math.sin(p1) * kotlin.math.cos(p2) * kotlin.math.cos(dl)
    val deg = Math.toDegrees(kotlin.math.atan2(y, x))
    return (((deg % 360) + 360) % 360).toFloat()
}

/**
 * Wie weit man drehen muss, um vom aktuellen Kurs auf die Peilung zum Ziel zu kommen:
 * −180 … +180 Grad, negativ = nach backbord, positiv = nach steuerbord.
 *
 * Genau das zeigt der Pfeil an. Steht er senkrecht, stimmt der Kurs; zeigt er nach
 * rechts, muss man nach rechts — man muss die Karte dafür nicht lesen.
 */
fun relativeBearing(courseDeg: Float, targetBearingDeg: Float): Float {
    var d = (targetBearingDeg - courseDeg) % 360f
    if (d > 180f) d -= 360f
    if (d < -180f) d += 360f
    return d
}

/**
 * Reststrecke entlang eines Weges: von hier bis zum nächstgelegenen Punkt des Weges und
 * von dort bis ans Ende.
 *
 * Der Weg selbst bleibt unangetastet. Ein früherer Versuch schnitt das Zurückgelegte
 * tatsächlich ab — und kürzte dabei bei **jeder** Meldung um einen Punkt, auch im Stand.
 * Nach ein paar Sekunden war die Route aufgefressen.
 */
fun remainingAlong(path: List<LatLon>, from: LatLon): Double {
    if (path.isEmpty()) return 0.0
    if (path.size == 1) return distanceM(from, path[0])
    var best = 0
    var bestD = Double.MAX_VALUE
    path.forEachIndexed { i, p ->
        val d = distanceM(p, from)
        if (d < bestD) { bestD = d; best = i }
    }
    val rest = path.subList(best, path.size)
    return bestD + pathLengthM(rest)
}

fun pathLengthM(path: List<LatLon>): Double =
    path.zipWithNext().sumOf { (a, b) -> distanceM(a, b) }

/** Was beim Routen schiefgehen kann – jeder Fall bekommt in der UI seinen eigenen Text. */
enum class RouteError { TOO_FAR, NO_NETWORK, NO_WATERWAYS, NOT_ON_WATER, NO_CONNECTION }

sealed interface RouteResult {
    data class Ok(
        val path: List<LatLon>,
        val water: List<LatLon>,
        val obstacles: List<Obstacle>,
    ) : RouteResult
    data class Failed(val reason: RouteError) : RouteResult
}

/**
 * Routet entlang der Wasserwege aus OpenStreetMap.
 *
 * Es gibt keinen fertigen Routendienst fürs Wasser — die üblichen kennen Straßen. Also
 * holen wir die Wasserwege des Gebiets von der Overpass-Schnittstelle (ODbL, dieselbe
 * Datenquelle wie die Karte), bauen daraus ein Wegenetz und suchen den kürzesten Weg.
 *
 * Grenzen, die der Nutzer kennen muss und die die UI auch nennt: das braucht **Netz**,
 * die Daten sind unterschiedlich vollständig, und sie enthalten weder Tiefen noch
 * Durchfahrtshöhen. Die Route ist ein Vorschlag, kein Fahrwasser.
 */
object WaterRouter {

    /** Weiter entfernte Ziele würden eine riesige Abfrage auslösen. */
    private const val MAX_DISTANCE_M = 60_000.0

    /** Rand um die Strecke, damit ein Bogen im Kanal nicht abgeschnitten wird. */
    private const val BBOX_PADDING_DEG = 0.05

    /** Für das Zusammenfügen der Wege: OSM teilt Knoten, die Koordinaten sind identisch. */
    private const val SNAP = 1_000_000.0

    /**
     * Mehrere Overpass-Server, der Reihe nach. Ein einzelner fest verdrahteter reicht
     * nicht: Am 2026-09-04 war `overpass-api.de` von hier aus **gar nicht** erreichbar —
     * drei Versuche, keine Verbindung —, während ein Spiegel dieselbe Abfrage in zwei
     * Sekunden beantwortete. Das Routing meldete daraufhin „keine Verbindung zu den
     * Kartendaten", obwohl das Gerät online war und die Daten es hergaben.
     *
     * Es sind öffentliche, gespendete Server. Deshalb wird immer erst der nächste
     * versucht, wenn der vorige nicht antwortet, und nie parallel angefragt.
     */
    private val OVERPASS_HOSTS = listOf(
        "https://overpass-api.de/api/interpreter",
        "https://overpass.kumi.systems/api/interpreter",
        "https://overpass.private.coffee/api/interpreter",
    )

    /**
     * Nur befahrbares Wasser. Gräben und Entwässerungen (`ditch`, `drain`, `stream`) sind
     * in OSM zahlreich, hängen kaum zusammen und taugen für kein Boot — im Testgebiet
     * blähten sie das Netz von 220 auf 2633 Wege auf, ohne eine einzige Fahrtstrecke
     * hinzuzufügen.
     */
    private val WATERWAYS = "river|canal|fairway"

    /**
     * Für ein Kanu zählt zusätzlich der Bach: schmal, oft nur zeitweise befahrbar, für ein
     * Motorboot aber wertlos. Nur deshalb hängt die Abfrage überhaupt am Fahrzeug.
     */
    private val WATERWAYS_CANOE = "river|canal|fairway|stream"

    private fun waterwaysFor(craft: Craft) =
        if (craft == Craft.CANOE) WATERWAYS_CANOE else WATERWAYS

    private fun navigableFor(craft: Craft) =
        if (craft == Craft.CANOE) NAVIGABLE + "stream" else NAVIGABLE

    /**
     * Was den Weg versperren oder aufhalten kann. Schleusen kosten Zeit, ein Wehr ist in
     * aller Regel das Ende der Fahrt — und die Route allein würde beides verschweigen.
     */
    private val OBSTACLES = "lock_gate|weir|dam|sluice_gate"

    /** Bis zu dieser Entfernung vom Weg zählt ein Hindernis als „liegt darauf". */
    private const val OBSTACLE_NEAR_M = 40.0

    /**
     * Wege, die als Fluss oder Kanal getaggt sind, aber nicht befahren werden dürfen oder
     * können. Ohne diese Prüfung schickt die Route durch **Rohrdurchlässe** und über
     * gesperrte Abschnitte — im Testgebiet trugen von 307 befahrbar getaggten Wegen 65 ein
     * `tunnel=culvert`, 43 ein `boat=no` und 29 ein `motorboat=no`. Genau so kommt eine
     * Route zustande, die an der Schleuse vorbeiführt statt hindurch.
     *
     * Die Verbote hängen am Fahrzeug: `motorboat=no` sperrt 45 Wege im Testgebiet, `canoe=no`
     * andere 26 — beide pauschal zu verwerfen nähme jedem Fahrzeug Strecken weg, die ihm
     * ausdrücklich offenstehen.
     */
    private fun isForbidden(tags: JSONObject?, craft: Craft): Boolean {
        if (tags == null) return false
        if (tags.optString("boat") == "no") return true
        if (tags.optString("access") in setOf("no", "private")) return true
        // Ein Rohr unter einer Straße ist kein Fahrwasser.
        if (tags.optString("tunnel") in setOf("culvert", "pipe", "building_passage")) return true
        return when (craft) {
            Craft.MOTORBOAT -> tags.optString("motorboat") == "no" || tags.optString("ship") == "no"
            Craft.CANOE -> tags.optString("canoe") == "no"
        }
    }

    /**
     * Wie weit Start und Ziel vom verzeichneten Wasserweg entfernt liegen dürfen. Diese
     * Strecken werden als Luftlinie gefahren — vom Liegeplatz aufs Fahrwasser hinaus und
     * am Ende wieder heran.
     *
     * Fest auf 300 m war zu streng: an einem See oder einer breiten Stelle ist gar keine
     * Linie verzeichnet (Seen sind Flächen, keine Wasserwege), und die Route wurde
     * abgelehnt, statt die Anfahrt einfach gerade zu nehmen. Der erlaubte Abstand wächst
     * deshalb mit der Gesamtstrecke — bei einer langen Fahrt fällt ein Kilometer Anfahrt
     * kaum ins Gewicht, bei einer kurzen schon.
     */
    private fun maxSnapM(directM: Double) = (directM * 0.35).coerceIn(800.0, 5_000.0)

    fun route(from: LatLon, to: LatLon, craft: Craft = Craft.MOTORBOAT): RouteResult {
        val direct = distanceM(from, to)
        if (direct > MAX_DISTANCE_M) return RouteResult.Failed(RouteError.TOO_FAR)
        val maxSnap = maxSnapM(direct)

        val json = fetch(from, to, craft) ?: return RouteResult.Failed(RouteError.NO_NETWORK)
        val ways = parseWays(json, craft)
        if (ways.isEmpty()) return RouteResult.Failed(RouteError.NO_WATERWAYS)

        val graph = buildGraph(ways, barrierNodes(json))
        if (graph.isEmpty()) return RouteResult.Failed(RouteError.NO_WATERWAYS)

        // Nicht einfach den nächsten Knoten nehmen: der liegt schnell auf einem
        // abgehängten Stichkanal, und dann gibt es nie eine Verbindung. Stattdessen das
        // Teilnetz suchen, das *beide* Punkte bedient.
        val ends = pickComponent(graph, from, to, maxSnap) ?: return if (
            nearestNode(graph.keys, from)?.let { distanceM(it.toLatLon(), from) > maxSnap } != false ||
            nearestNode(graph.keys, to)?.let { distanceM(it.toLatLon(), to) > maxSnap } != false
        ) {
            RouteResult.Failed(RouteError.NOT_ON_WATER)
        } else {
            RouteResult.Failed(RouteError.NO_CONNECTION)
        }

        val water = shortestPath(graph, ends.first, ends.second)?.map { it.toLatLon() }
            ?: return RouteResult.Failed(RouteError.NO_CONNECTION)
        // Anfahrt und Auslauf sind Luftlinie – sie werden getrennt zurückgegeben, damit die
        // Karte sie anders zeichnen kann: dort fährt man auf eigene Rechnung.
        val full = listOf(from) + water + listOf(to)
        return RouteResult.Ok(
            path = full,
            water = water,
            obstacles = onPath(parseObstacles(json), water),
        )
    }

    /* ------------------------------ Daten holen ------------------------------ */

    private fun fetch(from: LatLon, to: LatLon, craft: Craft): String? {
        val south = minOf(from.lat, to.lat) - BBOX_PADDING_DEG
        val north = maxOf(from.lat, to.lat) + BBOX_PADDING_DEG
        val west = minOf(from.lon, to.lon) - BBOX_PADDING_DEG
        val east = maxOf(from.lon, to.lon) + BBOX_PADDING_DEG
        // Wasserwege und Hindernisse in **einer** Anfrage – eine zweite würde noch einmal
        // zwei bis vier Sekunden kosten.
        val query = """
            [out:json][timeout:30];
            (
              way["waterway"~"^(${waterwaysFor(craft)})${'$'}"]($south,$west,$north,$east);
              node["waterway"~"^($OBSTACLES)${'$'}"]($south,$west,$north,$east);
              way["waterway"~"^($OBSTACLES)${'$'}"]($south,$west,$north,$east);
              node["seamark:notice:category"="no_entry"]($south,$west,$north,$east);
              node["seamark:notice:function"="prohibition"]($south,$west,$north,$east);
            );
            out geom;
        """.trimIndent()
        return postOverpass(query)
    }

    /**
     * Schickt die Abfrage an den ersten Server, der antwortet.
     *
     * „Antwortet" heißt: HTTP 200 **und** JSON. Overpass liefert bei Überlast gern 504
     * oder eine XML-Fehlerseite mit Status 200 — beides als Ergebnis durchzureichen
     * hieße, dem Nutzer „hier sind keine Wasserwege verzeichnet" zu zeigen, wo in
     * Wirklichkeit nur der Server müde war.
     */
    internal fun postOverpass(query: String): String? {
        for (host in OVERPASS_HOSTS) {
            val body = runCatching {
                val c = (URL(host).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 10_000
                    readTimeout = 60_000
                    doOutput = true
                    setRequestProperty("User-Agent", "BoatSpeedy")
                    setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                }
                try {
                    c.outputStream.use { it.write(("data=" + URLEncoder.encode(query, "UTF-8")).toByteArray()) }
                    if (c.responseCode != 200) return@runCatching null
                    c.inputStream.bufferedReader().use { it.readText() }
                } finally {
                    c.disconnect()
                }
            }.getOrNull()
            if (looksLikeJson(body)) return body
        }
        return null
    }

    /** Overpass antwortet im Fehlerfall mit XML, teils sogar unter Status 200. */
    internal fun looksLikeJson(body: String?): Boolean =
        body != null && body.trimStart().startsWith("{")

    private fun parseWays(json: String, craft: Craft): List<List<Node>> = runCatching {
        val elements = JSONObject(json).optJSONArray("elements") ?: return@runCatching emptyList()
        (0 until elements.length()).mapNotNull { i ->
            val el = elements.getJSONObject(i)
            val tags = el.optJSONObject("tags")
            if (tags?.optString("waterway") !in navigableFor(craft)) return@mapNotNull null
            if (isForbidden(tags, craft)) return@mapNotNull null
            val geom = el.optJSONArray("geometry") ?: return@mapNotNull null
            (0 until geom.length()).map { g ->
                val p = geom.getJSONObject(g)
                Node.of(p.getDouble("lat"), p.getDouble("lon"))
            }.takeIf { it.size >= 2 }
        }
    }.getOrDefault(emptyList())

    private val NAVIGABLE = setOf("river", "canal", "fairway")

    /** Hindernisse aus derselben Antwort lesen; Wege werden auf ihren Mittelpunkt reduziert. */
    private fun parseObstacles(json: String): List<Obstacle> = runCatching {
        val elements = JSONObject(json).optJSONArray("elements") ?: return@runCatching emptyList()
        (0 until elements.length()).mapNotNull { i ->
            val el = elements.getJSONObject(i)
            val tags = el.optJSONObject("tags") ?: return@mapNotNull null
            val kind = when (tags.optString("waterway")) {
                "lock_gate" -> ObstacleKind.LOCK
                "weir" -> ObstacleKind.WEIR
                "sluice_gate" -> ObstacleKind.SLUICE
                "dam" -> ObstacleKind.DAM
                else -> return@mapNotNull null
            }
            val lat: Double
            val lon: Double
            if (el.has("lat")) {
                lat = el.getDouble("lat"); lon = el.getDouble("lon")
            } else {
                val geom = el.optJSONArray("geometry") ?: return@mapNotNull null
                if (geom.length() == 0) return@mapNotNull null
                val mid = geom.getJSONObject(geom.length() / 2)
                lat = mid.getDouble("lat"); lon = mid.getDouble("lon")
            }
            Obstacle(lat, lon, kind, tags.optString("name").takeIf { it.isNotBlank() })
        }
    }.getOrDefault(emptyList())

    /** Welche Hindernisse dicht genug am Weg liegen, um ihn zu betreffen. */
    private fun onPath(all: List<Obstacle>, path: List<LatLon>): List<Obstacle> =
        all.filter { o ->
            val p = LatLon(o.lat, o.lon)
            path.any { distanceM(it, p) <= OBSTACLE_NEAR_M }
        }.distinctBy { "%.5f,%.5f".format(it.lat, it.lon) }

    /* ------------------------------ Wegenetz ------------------------------ */

    /** Ein Knoten des Netzes; auf ganze Mikrograd gerundet, damit geteilte Punkte zusammenfallen. */
    private data class Node(val lat: Int, val lon: Int) {
        fun toLatLon() = LatLon(lat / SNAP, lon / SNAP)
        companion object {
            fun of(lat: Double, lon: Double) =
                Node(Math.round(lat * SNAP).toInt(), Math.round(lon * SNAP).toInt())
        }
    }

    /**
     * Punkte, an denen das Netz aufgetrennt wird: Wehre und Dämme sind nicht passierbar,
     * ebenso ein Einfahrtsverbot. Schleusentore gehören **nicht** dazu — durch eine
     * Schleuse kommt man, sie kostet nur Zeit.
     */
    private fun barrierNodes(json: String): Set<Node> = runCatching {
        val elements = JSONObject(json).optJSONArray("elements") ?: return@runCatching emptySet()
        (0 until elements.length()).mapNotNull { i ->
            val el = elements.getJSONObject(i)
            val tags = el.optJSONObject("tags") ?: return@mapNotNull null
            val blocking = tags.optString("waterway") in setOf("weir", "dam") ||
                tags.optString("seamark:notice:category") == "no_entry" ||
                tags.optString("seamark:notice:function") == "prohibition"
            if (!blocking) return@mapNotNull null
            when {
                el.has("lat") -> Node.of(el.getDouble("lat"), el.getDouble("lon"))
                else -> el.optJSONArray("geometry")?.takeIf { it.length() > 0 }?.let { geom ->
                    val p = geom.getJSONObject(geom.length() / 2)
                    Node.of(p.getDouble("lat"), p.getDouble("lon"))
                }
            }
        }.toSet()
    }.getOrDefault(emptySet())

    private fun buildGraph(ways: List<List<Node>>, barriers: Set<Node>): Map<Node, List<Pair<Node, Double>>> {
        val g = HashMap<Node, MutableList<Pair<Node, Double>>>()
        for (way in ways) {
            for ((a, b) in way.zipWithNext()) {
                if (a == b) continue
                // Kein Weg durch ein Wehr oder an einem Einfahrtsverbot vorbei.
                if (a in barriers || b in barriers) continue
                val d = distanceM(a.toLatLon(), b.toLatLon())
                g.getOrPut(a) { mutableListOf() }.add(b to d)
                g.getOrPut(b) { mutableListOf() }.add(a to d)
            }
        }
        return g
    }

    private fun nearestNode(nodes: Collection<Node>, to: LatLon): Node? =
        nodes.minByOrNull { distanceM(it.toLatLon(), to) }

    /** Zerlegt das Netz in zusammenhängende Teile. */
    private fun components(graph: Map<Node, List<Pair<Node, Double>>>): List<List<Node>> {
        val seen = HashSet<Node>()
        val out = ArrayList<List<Node>>()
        for (s in graph.keys) {
            if (!seen.add(s)) continue
            val comp = ArrayList<Node>()
            val stack = ArrayDeque<Node>().apply { add(s) }
            while (stack.isNotEmpty()) {
                val n = stack.removeLast()
                comp.add(n)
                for ((m, _) in graph[n].orEmpty()) if (seen.add(m)) stack.add(m)
            }
            out.add(comp)
        }
        return out
    }

    /**
     * Sucht das Teilnetz, das Start und Ziel gemeinsam am besten bedient, und liefert die
     * beiden Einstiegspunkte. null, wenn keines beide innerhalb [maxSnap] erreicht.
     */
    private fun pickComponent(
        graph: Map<Node, List<Pair<Node, Double>>>,
        from: LatLon,
        to: LatLon,
        maxSnap: Double,
    ): Pair<Node, Node>? {
        var best: Triple<Double, Node, Node>? = null
        for (comp in components(graph)) {
            val s = comp.minByOrNull { distanceM(it.toLatLon(), from) } ?: continue
            val z = comp.minByOrNull { distanceM(it.toLatLon(), to) } ?: continue
            val ds = distanceM(s.toLatLon(), from)
            val dz = distanceM(z.toLatLon(), to)
            if (ds > maxSnap || dz > maxSnap) continue
            if (best == null || ds + dz < best!!.first) best = Triple(ds + dz, s, z)
        }
        return best?.let { it.second to it.third }
    }

    private fun shortestPath(
        graph: Map<Node, List<Pair<Node, Double>>>,
        start: Node,
        goal: Node,
    ): List<Node>? {
        if (start == goal) return listOf(start)
        val dist = HashMap<Node, Double>().apply { put(start, 0.0) }
        val prev = HashMap<Node, Node>()
        val seen = HashSet<Node>()
        val queue = PriorityQueue<Pair<Node, Double>>(compareBy { it.second })
        queue.add(start to 0.0)

        while (queue.isNotEmpty()) {
            val (node, d) = queue.poll()!!
            if (!seen.add(node)) continue
            if (node == goal) break
            for ((next, w) in graph[node].orEmpty()) {
                if (next in seen) continue
                val nd = d + w
                if (nd < (dist[next] ?: Double.MAX_VALUE)) {
                    dist[next] = nd
                    prev[next] = node
                    queue.add(next to nd)
                }
            }
        }
        if (goal !in dist) return null
        val out = ArrayList<Node>()
        var cur: Node? = goal
        while (cur != null) { out.add(cur); cur = prev[cur] }
        return out.reversed()
    }
}
