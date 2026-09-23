package de.kewl.boatspeedy.nav

import de.kewl.boatspeedy.data.Craft
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.PriorityQueue

/** Ein Punkt auf der Karte – bewusst ohne osmdroid-Typen, damit das hier testbar bleibt. */
data class LatLon(val lat: Double, val lon: Double)

/** Was auf dem Weg liegen kann. Ein Wehr heißt in aller Regel: hier ist Schluss. */
enum class ObstacleKind { LOCK, WEIR, SLUICE, DAM, BRIDGE, LANDING, POWER }

/**
 * Wozu ein Ein- oder Ausstieg am Ufer taugt, so wie OSM ihn führt: eine Slipanlage zum
 * Einsetzen, ein Kanu-Einstieg, ein Ausstieg vor dem Wehr — oft beides an einem Punkt.
 */
enum class LandingKind { SLIPWAY, PUT_IN, EGRESS, PUT_IN_EGRESS }

/**
 * Eine Schleuse, ein Wehr oder Ähnliches auf der Route.
 *
 * Die Angaben stammen aus OpenStreetMap und werden **unverändert** weitergereicht — die
 * Öffnungszeiten stehen dort in einer festen Schreibweise, und sie zu übersetzen hieße,
 * sie zu raten. Wer vor einer Schleuse steht, will lesen, was dort gilt, nicht eine
 * Auslegung davon.
 */
data class Obstacle(
    val lat: Double,
    val lon: Double,
    val kind: ObstacleKind,
    val name: String?,
    val openingHours: String? = null,
    val phone: String? = null,
    /** Funkkanal, auf dem die Schleuse gerufen wird. */
    val vhf: String? = null,
    val maxLengthM: String? = null,
    val maxWidthM: String? = null,
    /** Durchfahrtshöhe einer Brücke in Metern, so wie OSM sie führt. */
    val clearanceHeightM: String? = null,
    /** Durchfahrtsbreite in Metern. */
    val clearanceWidthM: String? = null,
    /** Wasserstraßenklasse nach CEMT. */
    val cemt: String? = null,
    /**
     * Die Kammer als Linie, sofern es eine ist. Daran wird entschieden, welche Tore zu
     * ihr gehören — ein fester Abstand vom Symbol reichte nicht, am Dortmund-Ems-Kanal
     * sind die Kammern 165 bis 225 m lang.
     */
    val line: List<LatLon> = emptyList(),
    /** Ein Schleusentor, keine Kammer. Tore sprechen nie für die Schleuse. */
    val isGate: Boolean = false,
    /** Bei [ObstacleKind.LANDING]: wozu die Stelle am Ufer taugt. */
    val landingKind: LandingKind? = null,
) {
    /** Ob es überhaupt etwas zu lesen gibt — sonst lohnt kein Antippen. */
    val hasInfo: Boolean
        // Ein Ein- oder Ausstieg hat auch ohne Namen etwas zu sagen: wozu er taugt.
        get() = kind == ObstacleKind.LANDING || kind == ObstacleKind.POWER ||
            !name.isNullOrBlank() || !openingHours.isNullOrBlank() ||
            !phone.isNullOrBlank() || !vhf.isNullOrBlank() ||
            !maxLengthM.isNullOrBlank() || !cemt.isNullOrBlank() ||
            !clearanceHeightM.isNullOrBlank() || !clearanceWidthM.isNullOrBlank()
}

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
    /** Strecke über Abschnitte mit allgemeinem Bootsverbot, in Metern. */
    val restrictedM: Double = 0.0,
    /** Dieselben Abschnitte als Linienzüge, für die rote Linie auf der Karte. */
    val restricted: List<List<LatLon>> = emptyList(),
    /** Strecke gegen die Strömung, in Metern. */
    val upstreamM: Double = 0.0,
    /** Strecke mit der Strömung, in Metern. */
    val downstreamM: Double = 0.0,
    /** Strecke, die über Land getragen wird, in Metern. */
    val portageM: Double = 0.0,
    /** Dieselben Stücke als Linienzüge, für die Karte. */
    val portage: List<List<LatLon>> = emptyList(),
    /**
     * Gesetzt, wenn die Strecke von einem **festgelegten Startpunkt** aus geplant wurde
     * und nicht vom Boot. Eine geplante Strecke hängt nicht am eigenen Fahren: Sie wird
     * nicht mitgeführt und nicht beim Ankommen abgeräumt, sondern bleibt liegen, bis man
     * sie verwirft — man plant sie ja im Voraus.
     */
    val plannedFrom: LatLon? = null,
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

/** Der Punkt auf halber Länge einer Linie. */
fun mitteEntlang(linie: List<LatLon>): LatLon {
    if (linie.size < 2) return linie.first()
    val haelfte = pathLengthM(linie) / 2
    var bisher = 0.0
    for (i in 1 until linie.size) {
        val a = linie[i - 1]
        val b = linie[i]
        val d = distanceM(a, b)
        if (bisher + d >= haelfte && d > 0) {
            val t = (haelfte - bisher) / d
            return LatLon(a.lat + (b.lat - a.lat) * t, a.lon + (b.lon - a.lon) * t)
        }
        bisher += d
    }
    return linie.last()
}

/**
 * Kürzester Abstand von [p] zu einer Linie in Metern, auch zwischen ihren Punkten.
 * Auf ein paar hundert Metern genügt die flache Näherung um [p].
 */
fun abstandZurLinie(p: LatLon, linie: List<LatLon>): Double {
    if (linie.isEmpty()) return Double.MAX_VALUE
    if (linie.size == 1) return distanceM(p, linie[0])
    val mProGradLat = 111_320.0
    val mProGradLon = 111_320.0 * kotlin.math.cos(Math.toRadians(p.lat))
    var best = Double.MAX_VALUE
    for (i in 1 until linie.size) {
        val ax = (linie[i - 1].lon - p.lon) * mProGradLon
        val ay = (linie[i - 1].lat - p.lat) * mProGradLat
        val bx = (linie[i].lon - p.lon) * mProGradLon
        val by = (linie[i].lat - p.lat) * mProGradLat
        val dx = bx - ax
        val dy = by - ay
        val l2 = dx * dx + dy * dy
        val t = if (l2 == 0.0) 0.0 else (-(ax * dx + ay * dy) / l2).coerceIn(0.0, 1.0)
        val x = ax + dx * t
        val y = ay + dy * t
        best = minOf(best, kotlin.math.sqrt(x * x + y * y))
    }
    return best
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
enum class RouteError { TOO_FAR, NO_NETWORK, SERVICE_BUSY, NO_WATERWAYS, NOT_ON_WATER, NO_CONNECTION }

/** Was bei der Overpass-Abfrage herauskam. */
internal sealed interface OverpassResult {
    data class Ok(val body: String) : OverpassResult
    /** Server haben geantwortet, aber nichts Brauchbares geliefert. */
    data object Busy : OverpassResult
    /** Keiner hat überhaupt geantwortet. */
    data object Unreachable : OverpassResult
}

sealed interface RouteResult {
    data class Ok(
        val path: List<LatLon>,
        val water: List<LatLon>,
        val obstacles: List<Obstacle>,
        /**
         * Länge der Abschnitte mit allgemeinem Bootsverbot (`boat=no`), die auf der
         * Strecke liegen. Beim Kanu sperrt das nicht, aber es gehört gesagt.
         */
        val restrictedM: Double = 0.0,
        /** Dieselben Abschnitte als Linienzüge — die Karte zeichnet sie rot. */
        val restricted: List<List<LatLon>> = emptyList(),
        /** Strecke gegen die Strömung eines Flusses, in Metern. */
        val upstreamM: Double = 0.0,
        /** Strecke mit der Strömung. Kanäle zählen zu keinem von beiden. */
        val downstreamM: Double = 0.0,
        /** Strecke, die über Land getragen wird, in Metern. */
        val portageM: Double = 0.0,
        /** Dieselben Stücke als Linienzüge, für die Karte. */
        val portage: List<List<LatLon>> = emptyList(),
    ) : RouteResult
    data class Failed(val reason: RouteError) : RouteResult
}

/**
 * Routet entlang der Wasserwege aus OpenStreetMap.
 *
 * Es gibt keinen fertigen Routendienst fürs Wasser — die üblichen kennen Straßen. Also
 * nehmen wir die Wasserwege aus den geladenen Kacheln (ODbL, dieselbe Datenquelle wie
 * die Karte), bauen daraus ein Wegenetz und suchen den günstigsten Weg. Fehlen die
 * Kacheln, fragt die App stattdessen die Overpass-Schnittstelle, und nur dann braucht
 * sie Netz.
 *
 * Grenzen, die der Nutzer kennen muss: Die Daten sind unterschiedlich vollständig, und
 * sie enthalten keine Tiefen. Die Route ist ein Vorschlag, kein Fahrwasser.
 */
object WaterRouter {

    /**
     * Wie weit ein Ziel entfernt sein darf — und das hängt davon ab, woher die Daten
     * kommen.
     *
     * **Über Overpass** würde ein Ziel in 200 km Entfernung eine Abfrage über ein
     * Rechteck von halb Deutschland auslösen; der Server lehnt das ab oder rechnet
     * minutenlang. Dort bleibt es bei sechzig Kilometern.
     *
     * **Aus den Kacheln** entfällt der Grund vollständig: Die Daten liegen auf dem
     * Gerät, Lesen kostet nichts, und die Wegsuche über ein paar hunderttausend Knoten
     * ist eine Sache von Millisekunden. Die alte Grenze hätte dort nur noch grundlos
     * gebremst.
     */
    private const val MAX_DISTANCE_ONLINE_M = 60_000.0
    private const val MAX_DISTANCE_TILES_M = 600_000.0

    /**
     * Über so viele Kacheln geht keine Route mehr. Nicht die Rechenzeit ist die Grenze,
     * sondern der Speicher: Bei tausend Kilometern sind es rund 160 Kacheln, und deren
     * Wegenetz will als Objektbaum gehalten werden. Darüber hinaus wäre es geraten.
     */
    private const val MAX_TILES = 260

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
     *
     * **Nur weltweite Instanzen.** `overpass.osm.ch` etwa antwortet in 0,17 s mit
     * gültigem JSON und null Elementen, weil es nur die Schweiz enthält — die App würde
     * daraufhin überzeugt „hier sind keine Wasserwege verzeichnet" melden. Ein regionaler
     * Spiegel ist schlimmer als gar keiner.
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
     * Bäche standen hier eine Zeit lang zusätzlich für das Kanu. Sie sind es nicht wert:
     * kaum einer ist befahrbar, sie machten 87 % der Datenmenge aus, und sie vermischten
     * zwei verschiedene Fragen. **Welche Gewässerart** ins Netz kommt, hängt nicht vom
     * Fahrzeug ab — sondern nur, **was dort verboten ist**. Siehe [isForbidden].
     */
    private fun navigableFor(craft: Craft) = NAVIGABLE

    /**
     * Was den Weg versperren oder aufhalten kann. Schleusen kosten Zeit, ein Wehr ist in
     * aller Regel das Ende der Fahrt — und die Route allein würde beides verschweigen.
     */
    private val OBSTACLES = "lock_gate|weir|dam|sluice_gate"

    /** Bis zu dieser Entfernung vom Weg zählt ein Hindernis als „liegt darauf". */
    private const val OBSTACLE_NEAR_M = 40.0

    /** Was zu einer Schleuse an der Route gehören kann, auch die Kammer daneben. */
    private const val OBSTACLE_UMGEBUNG_M = 400.0

    /** Wie ein Weg für das gewählte Fahrzeug einzustufen ist. */
    internal enum class Zugang {
        /** Befahrbar. */
        FREI,

        /** Nicht befahrbar — kommt gar nicht erst ins Netz. */
        GESPERRT,

        /**
         * Befahrbar, aber mit einem allgemeinen Bootsverbot belegt. Die Strecke wird
         * gerechnet und ihre Länge unten auf der Karte angezeigt; entscheiden muss es,
         * wer im Boot sitzt.
         */
        EINGESCHRAENKT,
    }

    private val VERBOTEN = setOf("no", "private")
    private val ERLAUBT = setOf("yes", "designated", "permissive", "destination")

    /**
     * Wege, die als Fluss oder Kanal getaggt sind, aber nicht befahren werden dürfen oder
     * können. Ohne diese Prüfung schickt die Route durch **Rohrdurchlässe** und über
     * gesperrte Abschnitte — im Testgebiet trugen von 307 befahrbar getaggten Wegen 65 ein
     * `tunnel=culvert`, 43 ein `boat=no` und 29 ein `motorboat=no`. Genau so kommt eine
     * Route zustande, die an der Schleuse vorbeiführt statt hindurch.
     *
     * Die Zugangsmerkmale sind in OpenStreetMap **gestuft**: `access` gilt für alles,
     * `boat` für Boote, `motorboat`/`ship`/`canoe` für die einzelne Art. Das Genauere
     * schlägt das Allgemeinere — `boat=no` + `canoe=yes` heißt „Boote nein, Kanu ja".
     *
     * Vorher wurde flach geprüft und `boat=no` sperrte unbedingt. Auf der oberen Saale, wo
     * Motorboote verboten sind und Kanus fahren dürfen, riss das Netz damit genau an der
     * Einsetzstelle Zeutsch: 44 km Saale allein in der Kachel `n50e011` tragen `boat=no`,
     * nur ein Teil davon zusätzlich `canoe=yes`.
     */
    private fun zugang(tags: JSONObject?, craft: Craft): Zugang {
        if (tags == null) return Zugang.FREI
        // Ein Rohr unter einer Straße ist kein Fahrwasser — daran ändert kein Merkmal etwas.
        if (tags.optString("tunnel") in setOf("culvert", "pipe", "building_passage")) {
            return Zugang.GESPERRT
        }
        val kette = when (craft) {
            Craft.MOTORBOAT -> listOf("access", "boat", "ship", "motorboat")
            Craft.CANOE -> listOf("access", "boat", "canoe")
        }
        // Von allgemein nach genau; das zuletzt gefundene Verbot zählt, eine ausdrückliche
        // Erlaubnis hebt es auf. Werte, die weder das eine noch das andere sind
        // (`unknown`, `seasonal`), lassen den Stand, wie er ist.
        var sperre: String? = null
        for (k in kette) {
            when (tags.optString(k)) {
                in VERBOTEN -> sperre = k
                in ERLAUBT -> sperre = null
            }
        }
        return when {
            sperre == null -> Zugang.FREI
            // Beim Kanu ist ein allgemeines `boat=no` **kein** Ausschluss. Es ist fast immer
            // gegen Motorboote gemeint; wo Paddeln wirklich untersagt ist, steht `canoe=no`
            // oder `access=no`. Ein hartes Nein nähme dem Kanu die halben Oberläufe.
            craft == Craft.CANOE && sperre == "boat" -> Zugang.EINGESCHRAENKT
            else -> Zugang.GESPERRT
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

    /**
     * @param tileDir Wo die heruntergeladenen Kacheln liegen. Ist der Ausschnitt davon
     *   vollständig abgedeckt, wird **gar nicht** gefragt — kein Netz, keine Wartezeit,
     *   keine überlasteten Server. Fehlt eine Kachel, geht es wie bisher über Overpass.
     */
    fun route(
        from: LatLon,
        to: LatLon,
        craft: Craft = Craft.MOTORBOAT,
        tileDir: java.io.File? = null,
    ): RouteResult {
        val direct = distanceM(from, to)
        // Zuerst nachsehen, ob die Kacheln reichen — davon hängt ab, wie weit das Ziel
        // liegen darf. Umgekehrt hätte eine Fahrt über hundert Kilometer abgelehnt, was
        // vollständig auf dem Gerät liegt.
        val offline = fromTiles(from, to, craft, tileDir)
        val limit = if (offline != null) MAX_DISTANCE_TILES_M else MAX_DISTANCE_ONLINE_M
        if (direct > limit) return RouteResult.Failed(RouteError.TOO_FAR)
        val maxSnap = maxSnapM(direct)

        val quelle = offline ?: when (val r = askOverpass(buildQuery(from, to))) {
            is OverpassResult.Ok -> elemente(r.body).let { el ->
                val w = parseWays(el, craft)
                Quelle(
                    w.ways, parseObstacles(el), barrierNodes(el),
                    w.eingeschraenkt, w.stromab, w.umtrage, w.kanal,
                )
            }
            OverpassResult.Busy -> return RouteResult.Failed(RouteError.SERVICE_BUSY)
            OverpassResult.Unreachable -> return RouteResult.Failed(RouteError.NO_NETWORK)
        }
        val ways = quelle.ways
        if (ways.isEmpty()) return RouteResult.Failed(RouteError.NO_WATERWAYS)

        // **Das Wasser als Suchraster, einmal.** Es wurde vorher dreimal mit gleichem
        // Inhalt gebaut: für die Anleger, für die Anschlüsse und für die Prüfung, ob
        // eine Verbindung den Fluss quert. Beim Kanu war das Netz dadurch dreimal so
        // teuer wie beim Motorboot. Gebraucht wird es nur zum Umtragen.
        val wasserRaster = if (craft == Craft.CANOE) segmentRaster(ways, emptySet()) else null
        // Umtragen nur im Kanu. Ein Motorboot trägt niemand um ein Wehr.
        val umtrage = if (craft == Craft.CANOE) {
            quelle.umtrage + uferbruecken(quelle.obstacles, quelle.barriers, wasserRaster!!)
        } else {
            emptyList()
        }
        val graph = buildGraph(
            ways,
            quelle.barriers,
            quelle.eingeschraenkt,
            umtrage,
            sperrlinien(quelle.obstacles),
            kraftwerke(quelle.obstacles),
            quelle.kanal,
            if (craft == Craft.CANOE) KANAL_KOSTEN_KANU else 1.0,
            wasserRaster,
        )
        if (graph.adj.isEmpty()) return RouteResult.Failed(RouteError.NO_WATERWAYS)

        // Nicht einfach den nächsten Knoten nehmen: der liegt schnell auf einem
        // abgehängten Stichkanal, und dann gibt es nie eine Verbindung. Stattdessen das
        // Teilnetz suchen, das *beide* Punkte bedient.
        val ends = pickComponent(graph.adj, from, to, maxSnap) ?: return if (
            nearestNode(graph.adj.keys, from)?.let { distanceM(it.toLatLon(), from) > maxSnap } != false ||
            nearestNode(graph.adj.keys, to)?.let { distanceM(it.toLatLon(), to) > maxSnap } != false
        ) {
            RouteResult.Failed(RouteError.NOT_ON_WATER)
        } else {
            RouteResult.Failed(RouteError.NO_CONNECTION)
        }

        val knoten = besterWeg(graph.adj, ends.first, to, maxSnap)
            ?: shortestPath(graph.adj, ends.first, ends.second)
            ?: return RouteResult.Failed(RouteError.NO_CONNECTION)
        val water = knoten.map { it.toLatLon() }
        // Anfahrt und Auslauf sind Luftlinie – sie werden getrennt zurückgegeben, damit die
        // Karte sie anders zeichnen kann: dort fährt man auf eigene Rechnung.
        val full = listOf(from) + water + listOf(to)
        val (flussauf, flussab) = stroemung(knoten, quelle.stromab)
        return RouteResult.Ok(
            path = full,
            water = water,
            // Über die **ganze** Strecke, samt Anfahrt und Auslauf. Am Wehr endet das
            // Fahrwasser; von dort läuft die Route als Luftlinie weiter, und das Wehr
            // liegt genau darauf. Nur das Fahrwasser zu prüfen hieße: kein Hinweis
            // ausgerechnet dort, wo die Fahrt aufhört.
            obstacles = onPath(quelle.obstacles, full),
            restrictedM = eingeschraenkteLaenge(knoten, quelle.eingeschraenkt),
            restricted = eingeschraenkteZuege(knoten, quelle.eingeschraenkt),
            upstreamM = flussauf,
            downstreamM = flussab,
            portageM = kantenLaenge(knoten, graph.umtrage),
            portage = kantenZuege(knoten, graph.umtrage),
        )
    }

    /** So weit dürfen zwei Anleger auseinanderliegen, um ein Paar an einem Wehr zu sein. */
    private const val UFER_PAAR_M = 300.0

    /** So nah muss die Sperre dazwischenliegen, damit es eine Umtragung ist. */
    private const val SPERRE_DAZWISCHEN_M = 150.0

    /**
     * Umtragungen, die nur aus zwei Anlegern bestehen.
     *
     * Am Wehr Kahla steht kein Weg in OSM, sondern nur zwei Slipanlagen mit
     * `whitewater=put_in;egress`, 40 m auseinander, das Wehr dazwischen. Sie sind genau
     * dafür eingetragen, und ohne eine Verbindung zwischen ihnen endet die Route am Wehr.
     *
     * Gezogen wird die Linie nur, wenn eine **Sperre zwischen ihnen liegt**. Sonst wäre
     * jede zweite Slipanlage am gegenüberliegenden Ufer eine Abkürzung über Land, und die
     * Route spränge quer über den Fluss.
     */
    private fun uferbruecken(
        obstacles: List<Obstacle>,
        barriers: Set<Node>,
        wasser: Map<Long, MutableList<Pair<Node, Node>>>,
    ): List<List<Node>> {
        if (barriers.isEmpty()) return emptyList()
        val ufer = obstacles.filter { it.kind == ObstacleKind.LANDING }
            .distinctBy { "%.5f,%.5f".format(it.lat, it.lon) }
        if (ufer.size < 2) return emptyList()
        val sperren = raster(barriers.toList())
        // Geprüft wird nur gegen das **Wasser**, nicht gegen das Wehr. Am Wehr Reschwitz
        // liegen Aus- und Einstieg beide am selben Ufer, und die Linie zwischen ihnen
        // streift das Wehr an seinem Ende — dort, wo es an Land stößt und wo man
        // vorbeiträgt. Diese Prüfung hatte genau die Umtragungen verhindert, um die es
        // geht.
        val raus = ArrayList<List<Node>>()
        val lagen = ufer.map { LatLon(it.lat, it.lon) }
        for ((i, j) in paareInDerNaehe(lagen, UFER_PAAR_M)) {
            val a = lagen[i]
            val b = lagen[j]
            val mitte = Node.of((a.lat + b.lat) / 2, (a.lon + b.lon) / 2)
            if (naechster(sperren, mitte, SPERRE_DAZWISCHEN_M) == null) continue
            val von = Node.of(a.lat, a.lon)
            val nach = Node.of(b.lat, b.lon)
            // Getragen wird an Land. Eine Linie, die den Fluss quert, ist keine
            // Umtragung, sondern sieht auf der Karte aus wie ein Sprung übers Wasser.
            if (kreuztWasser(wasser, von, nach)) continue
            raus.add(listOf(von, nach))
        }
        return raus
    }

    /**
     * Alle Paare `(i, j)` mit `i < j`, deren Punkte höchstens [grenze] Meter auseinander
     * liegen. Über Zellen statt jedes mit jedem: Auf Kahla nach Lübeck liegen Tausende
     * Anleger und Umtrageweg-Enden im Korridor, und der Vergleich aller Paare kostete
     * über eine Sekunde.
     */
    private fun paareInDerNaehe(punkte: List<LatLon>, grenze: Double): List<Pair<Int, Int>> {
        if (punkte.size < 2) return emptyList()
        val zelle = 0.005
        fun y(p: LatLon) = kotlin.math.floor(p.lat / zelle).toInt()
        fun x(p: LatLon) = kotlin.math.floor(p.lon / zelle).toInt()
        val zellen = HashMap<Long, MutableList<Int>>()
        punkte.forEachIndexed { i, p ->
            zellen.getOrPut(y(p).toLong() * 10_000_000L + x(p)) { mutableListOf() }.add(i)
        }
        val raus = ArrayList<Pair<Int, Int>>()
        punkte.forEachIndexed { i, p ->
            val dy = kotlin.math.ceil(grenze / (zelle * 111_320.0)).toInt()
            val dx = kotlin.math.ceil(grenze / (zelle * 111_320.0 * kotlin.math.cos(Math.toRadians(p.lat)))).toInt()
            for (yy in y(p) - dy..y(p) + dy) {
                for (xx in x(p) - dx..x(p) + dx) {
                    for (j in zellen[yy.toLong() * 10_000_000L + xx].orEmpty()) {
                        if (j > i && distanceM(p, punkte[j]) <= grenze) raus.add(i to j)
                    }
                }
            }
        }
        return raus
    }

    /** Wie viel der Strecke über die angegebenen Kanten läuft, in Metern. */
    private fun kantenLaenge(path: List<Node>, kanten: Set<Kante>): Double {
        if (kanten.isEmpty()) return 0.0
        var m = 0.0
        for ((a, b) in path.zipWithNext()) {
            if (Kante(a, b) in kanten) m += distanceM(a.toLatLon(), b.toLatLon())
        }
        return m
    }

    /** Dieselben Kanten als zusammenhängende Züge, damit die Karte sie zeichnen kann. */
    private fun kantenZuege(path: List<Node>, kanten: Set<Kante>): List<List<LatLon>> {
        if (kanten.isEmpty()) return emptyList()
        val zuege = ArrayList<List<LatLon>>()
        var lauf: ArrayList<LatLon>? = null
        for ((a, b) in path.zipWithNext()) {
            if (Kante(a, b) in kanten) {
                val z = lauf ?: ArrayList<LatLon>().also { it.add(a.toLatLon()); lauf = it }
                z.add(b.toLatLon())
            } else {
                lauf?.let { zuege.add(it) }
                lauf = null
            }
        }
        lauf?.let { zuege.add(it) }
        return zuege
    }

    /**
     * Wie viel der gefundenen Strecke über Abschnitte mit allgemeinem Bootsverbot läuft.
     *
     * Gezählt wird nur, wenn **beide** Enden eines Stücks auf einem solchen Abschnitt
     * liegen. Ein Punkt allein sagt nichts: An der Naht zweier Wege gehört er beiden, und
     * eine einzelne Kante würde sonst dem falschen zugeschlagen.
     */
    private fun eingeschraenkteLaenge(path: List<Node>, punkte: Set<Node>): Double {
        if (punkte.isEmpty()) return 0.0
        var m = 0.0
        for ((a, b) in path.zipWithNext()) {
            if (a in punkte && b in punkte) m += distanceM(a.toLatLon(), b.toLatLon())
        }
        return m
    }

    /**
     * Wie viel der Strecke gegen und wie viel mit der Strömung läuft: (flussauf, flussab).
     *
     * Das Netz ist ungerichtet, die Route also eine Folge von Punkten ohne Richtung. Für
     * jedes Stück wird nachgesehen, ob es in Zeichenrichtung eines Flusses liegt — dann
     * flussab — oder umgekehrt. Liegt es auf keinem Fluss, zählt es nirgends mit: Ein
     * Kanal steht still, und eine Zahl dafür wäre erfunden.
     */
    private fun stroemung(path: List<Node>, stromab: Set<Kante>): Pair<Double, Double> {
        if (stromab.isEmpty()) return 0.0 to 0.0
        var auf = 0.0
        var ab = 0.0
        for ((a, b) in path.zipWithNext()) {
            when {
                Kante(a, b) in stromab -> ab += distanceM(a.toLatLon(), b.toLatLon())
                Kante(b, a) in stromab -> auf += distanceM(a.toLatLon(), b.toLatLon())
            }
        }
        return auf to ab
    }

    /**
     * Dieselben Stücke als **zusammenhängende Züge**, damit die Karte sie zeichnen kann.
     *
     * Eine Kilometerzahl sagt, wie viel gesperrt ist, aber nicht wo. Als eigene Linie über
     * der Route sieht man auf einen Blick, welcher Teil der Fahrt es betrifft — und ob er
     * am Anfang liegt, in der Mitte oder kurz vor dem Ziel.
     */
    private fun eingeschraenkteZuege(path: List<Node>, punkte: Set<Node>): List<List<LatLon>> {
        if (punkte.isEmpty()) return emptyList()
        val zuege = ArrayList<List<LatLon>>()
        var lauf: ArrayList<LatLon>? = null
        for ((a, b) in path.zipWithNext()) {
            if (a in punkte && b in punkte) {
                val z = lauf ?: ArrayList<LatLon>().also { it.add(a.toLatLon()); lauf = it }
                z.add(b.toLatLon())
            } else {
                lauf?.let { zuege.add(it) }
                lauf = null
            }
        }
        lauf?.let { zuege.add(it) }
        return zuege
    }

    /**
     * Schleusen und Wehre eines Ausschnitts aus den Kacheln — ohne gesetzte Route.
     *
     * Bisher gab es sie nur entlang einer gerechneten Strecke. Wer wissen wollte, wann
     * eine Schleuse öffnet, musste erst ein Ziel setzen; dabei liegen alle Schleusen des
     * Gebiets längst auf dem Gerät. Fehlt eine Kachel, kommt eben nichts — geholt wird
     * dafür nichts, das entscheidet der Aufrufer.
     */
    fun obstaclesIn(
        dir: java.io.File?,
        south: Double,
        west: Double,
        north: Double,
        east: Double,
    ): List<Obstacle> {
        if (dir == null || !dir.isDirectory) return emptyList()
        val ids = MapTiles.tilesFor(south, west, north, east)
        if (ids.isEmpty() || MapTiles.missing(dir, ids).isNotEmpty()) return emptyList()
        val alle = ArrayList<Obstacle>()
        val ok = MapTiles.forEach(dir, ids) { json ->
            parseObstacles(elemente(json)).filterTo(alle) {
                it.lat in south..north && it.lon in west..east
            }
        }
        if (!ok) return emptyList()
        return zusammenlegen(alle.distinctBy { "%.5f,%.5f".format(it.lat, it.lon) })
    }

    /**
     * Die Flüsse eines Ausschnitts, jeder **stromab** gezeichnet — für die Winkel, die
     * auf der Karte die Fließrichtung zeigen.
     *
     * Nur `waterway=river`. Kanäle stehen still, und ihre Zeichenrichtung ist Zufall; ein
     * Winkel darauf würde eine Strömung behaupten, die es nicht gibt.
     */
    fun riversIn(
        dir: java.io.File?,
        south: Double,
        west: Double,
        north: Double,
        east: Double,
    ): List<List<LatLon>> {
        if (dir == null || !dir.isDirectory) return emptyList()
        val ids = MapTiles.tilesFor(south, west, north, east)
        if (ids.isEmpty() || MapTiles.missing(dir, ids).isNotEmpty()) return emptyList()
        val out = ArrayList<List<LatLon>>()
        val gesehen = HashSet<String>()
        MapTiles.forEach(dir, ids) { json ->
            val elements = runCatching { JSONObject(json).optJSONArray("elements") }
                .getOrNull() ?: return@forEach
            for (i in 0 until elements.length()) {
                val el = elements.getJSONObject(i)
                if (el.optString("type") != "way") continue
                if (el.optJSONObject("tags")?.optString("waterway") != "river") continue
                val geom = el.optJSONArray("geometry") ?: continue
                val punkte = (0 until geom.length()).map {
                    val p = geom.getJSONObject(it)
                    LatLon(p.getDouble("lat"), p.getDouble("lon"))
                }
                if (punkte.size < 2) continue
                // Nur, was den Ausschnitt berührt. Ein Weg, der über eine Kachelkante
                // läuft, steht in beiden — einmal reicht.
                if (punkte.none { it.lat in south..north && it.lon in west..east }) continue
                val schluessel = "${punkte.first()}|${punkte.last()}|${punkte.size}"
                if (gesehen.add(schluessel)) out.add(punkte)
            }
        }
        return out
    }

    /* ------------------------------ Daten holen ------------------------------ */

    /**
     * Liest den benötigten Ausschnitt aus den Kacheln — oder `null`, wenn auch nur eine
     * fehlt. Halb aus Kacheln und halb vom Server zusammenzusetzen wäre der schlechteste
     * Fall: Die Naht läge irgendwo im Netz, und die Route bräche genau dort ab.
     */
    /** Was der Router zum Rechnen braucht — egal woher es kommt. */
    private class Quelle(
        val ways: List<List<Node>>,
        val obstacles: List<Obstacle>,
        val barriers: Set<Node>,
        /** Punkte auf Abschnitten mit allgemeinem Bootsverbot — befahrbar, aber gemeldet. */
        val eingeschraenkt: Set<Node> = emptySet(),
        val stromab: Set<Kante> = emptySet(),
        /** Wege über Land um ein Wehr herum, für das Kanu. */
        val umtrage: List<List<Node>> = emptyList(),
        /** Stücke, die zu einem Kanal gehören. */
        val kanal: Set<Kante> = emptySet(),
    )

    /**
     * Liest den benötigten Ausschnitt aus den Kacheln — oder `null`, wenn auch nur eine
     * fehlt. Halb aus Kacheln und halb vom Server zusammenzusetzen wäre der schlechteste
     * Fall: Die Naht läge irgendwo im Netz, und die Route bräche genau dort ab.
     *
     * Ausgewertet wird **Kachel für Kachel**, nicht am Stück: Nur so bleibt der Speicher
     * bei langen Strecken im Rahmen.
     */
    private fun fromTiles(
        from: LatLon,
        to: LatLon,
        craft: Craft,
        tileDir: java.io.File?,
    ): Quelle? {
        if (tileDir == null || !tileDir.isDirectory) return null
        val ids = MapTiles.tilesForRoute(from, to)
        if (ids.size > MAX_TILES) return null
        if (MapTiles.missing(tileDir, ids).isNotEmpty()) return null

        val ways = ArrayList<List<Node>>()
        val obstacles = ArrayList<Obstacle>()
        val barriers = HashSet<Node>()
        val eingeschraenkt = HashSet<Node>()
        val stromab = HashSet<Kante>()
        val umtrage = ArrayList<List<Node>>()
        val kanal = HashSet<Kante>()
        val ok = MapTiles.forEach(tileDir, ids) { json ->
            // **Einmal lesen, dreimal auswerten.** Vorher las jede der drei Auswertungen
            // die Kachel selbst ein; bei Kahla nach Lübeck waren das 4,6 von 10,6
            // Sekunden, zwei Drittel davon doppelte Arbeit.
            val el = elemente(json)
            val w = parseWays(el, craft)
            ways.addAll(w.ways)
            eingeschraenkt.addAll(w.eingeschraenkt)
            stromab.addAll(w.stromab)
            umtrage.addAll(w.umtrage)
            kanal.addAll(w.kanal)
            obstacles.addAll(parseObstacles(el))
            barriers.addAll(barrierNodes(el))
        }
        return if (ok) {
            Quelle(ways, obstacles, barriers, eingeschraenkt, stromab, umtrage, kanal)
        } else {
            null
        }
    }

    private fun buildQuery(from: LatLon, to: LatLon): String {
        val south = minOf(from.lat, to.lat) - BBOX_PADDING_DEG
        val north = maxOf(from.lat, to.lat) + BBOX_PADDING_DEG
        val west = minOf(from.lon, to.lon) - BBOX_PADDING_DEG
        val east = maxOf(from.lon, to.lon) + BBOX_PADDING_DEG
        // Wasserwege und Hindernisse in **einer** Anfrage – eine zweite würde noch einmal
        // zwei bis vier Sekunden kosten.
        val query = """
            [out:json][timeout:30];
            (
              way["waterway"~"^($WATERWAYS)${'$'}"]($south,$west,$north,$east);
              node["waterway"~"^($OBSTACLES)${'$'}"]($south,$west,$north,$east);
              way["waterway"~"^($OBSTACLES)${'$'}"]($south,$west,$north,$east);
              way["whitewater"="portage_way"]($south,$west,$north,$east);
              way["canoe"="portage"]($south,$west,$north,$east);
              way["portage"]($south,$west,$north,$east);
              node["leisure"="slipway"]($south,$west,$north,$east);
              node["canoe"="put_in"]($south,$west,$north,$east);
              node["whitewater"]($south,$west,$north,$east);
              node["seamark:notice:category"="no_entry"]($south,$west,$north,$east);
              node["seamark:notice:function"="prohibition"]($south,$west,$north,$east);
            );
            out geom;
        """.trimIndent()
        return query
    }

    /**
     * Schickt die Abfrage an den ersten Server, der antwortet.
     *
     * „Antwortet" heißt: HTTP 200 **und** JSON. Overpass liefert bei Überlast gern 504
     * oder eine XML-Fehlerseite mit Status 200 — beides als Ergebnis durchzureichen
     * hieße, dem Nutzer „hier sind keine Wasserwege verzeichnet" zu zeigen, wo in
     * Wirklichkeit nur der Server müde war.
     */
    /**
     * Wie oft die ganze Liste durchgegangen wird, und wie lange insgesamt höchstens.
     *
     * Ein Anlauf reicht nicht: gemessen am 2026-09-04 scheiterten bei beiden erreichbaren
     * Spiegeln drei von fünf Anfragen mit einem 504. Bei etwa jeder zweiten Anfrage sinkt
     * die Aussicht auf Misserfolg mit sechs Versuchen unter zwei Prozent. Die Frist
     * verhindert, dass daraus anderthalb Minuten Warten werden.
     */
    private const val ROUNDS = 2
    private const val DEADLINE_MS = 45_000L
    private const val PAUSE_MS = 1_000L

    /**
     * Der zuletzt erfolgreiche Server wird zuerst gefragt. Sonst kostet ein toter erster
     * Eintrag bei **jeder** Route erneut die volle Wartezeit.
     */
    @Volatile
    private var lastGoodHost: String? = null

    /**
     * Für Beiwerk wie die Geschwindigkeitsschilder: **ein** Durchgang, keine Wiederholung.
     * Fehlen die Schilder, ist nichts verloren — die Server aber sind knapp, und eine
     * Route, die daneben ansteht, soll sie nicht mit Nebensachen belegt vorfinden.
     */
    internal fun postOverpass(query: String): String? =
        (askOverpass(query, rounds = 1) as? OverpassResult.Ok)?.body

    /**
     * Fragt die Server der Reihe nach, in mehreren Anläufen und mit einer Gesamtfrist.
     *
     * Unterscheidet dabei, **warum** es nicht geklappt hat: Hat überhaupt kein Server
     * geantwortet, ist es ein Verbindungsproblem. Kamen Antworten, waren aber unbrauchbar
     * (504, XML-Fehlerseite), ist der Dienst überlastet — und dann ist „keine Verbindung
     * zu den Kartendaten" schlicht die falsche Auskunft.
     */
    internal fun askOverpass(query: String, rounds: Int = ROUNDS): OverpassResult {
        val order = (listOfNotNull(lastGoodHost) + OVERPASS_HOSTS).distinct()
        val until = System.currentTimeMillis() + DEADLINE_MS
        var answered = false
        repeat(rounds) { round ->
            for (host in order) {
                if (System.currentTimeMillis() >= until) {
                    return if (answered) OverpassResult.Busy else OverpassResult.Unreachable
                }
                val reply = post(host, query)
                if (reply.reached) answered = true
                if (looksLikeJson(reply.body)) {
                    if (!isComplete(reply.body!!)) {
                        // Teilantwort: der Server hat abgebrochen. Wie ein Fehlschlag
                        // behandeln — der nächste Server hat vielleicht mehr Luft.
                        answered = true
                        continue
                    }
                    lastGoodHost = host
                    return OverpassResult.Ok(reply.body)
                }
            }
            if (round < rounds - 1) runCatching { Thread.sleep(PAUSE_MS) }
        }
        return if (answered) OverpassResult.Busy else OverpassResult.Unreachable
    }

    /** [reached] sagt, ob der Server überhaupt geantwortet hat – egal mit was. */
    private class Reply(val body: String?, val reached: Boolean)

    private fun post(host: String, query: String): Reply {
        var reached = false
        val body = runCatching {
            val c = (URL(host).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = CONNECT_MS
                // Kurz gehalten: erfolgreiche Antworten kamen in 2,5 bis 9 s, ein 504 erst
                // nach 35 bis 40. Ohne diesen Schnitt wartet man auf jeden müden Server
                // eine halbe Minute, bevor der nächste drankommt.
                readTimeout = READ_MS
                doOutput = true
                setRequestProperty("User-Agent", "BoatSpeedy")
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            }
            try {
                c.outputStream.use { it.write(("data=" + URLEncoder.encode(query, "UTF-8")).toByteArray()) }
                // Hier steht die Verbindung und die Abfrage ist draußen — der Server ist
                // also erreichbar. Was danach passiert, ist seine Sache.
                //
                // Vorher stand diese Zeile hinter `responseCode`, und das war falsch: ein
                // 504 kommt erst nach 35 bis 40 s, unsere Lesefrist liegt bei 15. Der
                // Abbruch flog als Zeitüberschreitung heraus, `reached` blieb falsch, und
                // die App meldete „keine Verbindung zu den Kartendaten" — obwohl alle drei
                // Server erreichbar und bloß überlastet waren.
                reached = true
                val code = c.responseCode
                if (code != 200) return@runCatching null
                c.inputStream.bufferedReader().use { it.readText() }
            } finally {
                c.disconnect()
            }
        }.getOrNull()
        return Reply(body, reached)
    }

    private const val CONNECT_MS = 8_000
    private const val READ_MS = 15_000

    /** Overpass antwortet im Fehlerfall mit XML, teils sogar unter Status 200. */
    internal fun looksLikeJson(body: String?): Boolean =
        body != null && body.trimStart().startsWith("{")

    /**
     * Ist die Antwort **vollständig**, oder nur so weit der Server kam?
     *
     * Overpass bricht eine zu große Abfrage nach seiner Zeitgrenze ab und liefert
     * trotzdem Status 200 mit gültigem JSON — nur eben mit den Daten, die bis dahin
     * zusammengekommen sind, und einem `remark` daneben. Gemessen: eine Abfrage über
     * den Rhein von Basel bis Mainz kam mit *null* Elementen und
     * „runtime error: Query timed out" zurück.
     *
     * Ungeprüft durchgereicht ergibt das ein Wegenetz mit Löchern, und die App meldet
     * dann „kein durchgehender Wasserweg" — ehrlich, aber mit falscher Begründung: Der
     * Weg ist da, nur die Antwort war es nicht. Eine unvollständige Auskunft ist
     * schlimmer als gar keine, weil sie wie ein Ergebnis aussieht.
     */
    internal fun isComplete(body: String): Boolean {
        // Bewusst über den Text und nicht über JSONObject: Die Antwort ist bei langen
        // Strecken megabytegroß, und sie nur wegen einer Bemerkung vollständig zu
        // zerlegen wäre Verschwendung. Nebenbei bleibt es damit im Unit-Test prüfbar —
        // dort ist org.json nur eine Attrappe, die immer Leerwerte liefert.
        return REMARK.findAll(body).none { m ->
            val remark = m.groupValues[1]
            remark.contains("runtime error", ignoreCase = true) ||
                remark.contains("timed out", ignoreCase = true)
        }
    }

    /**
     * Ohne Zeilenanker, weil Overpass alles in eine Zeile schreibt. Gesucht wird nach
     * **Overpass' eigenem Wortlaut**, nicht nach jeder Bemerkung: `remark` gibt es auch
     * als OSM-Merkmal an Wegen, und das darf eine Route nicht scheitern lassen.
     */
    private val REMARK = Regex(""""remark"\s*:\s*"((?:[^"\\]|\\.)*)"""")

    /**
     * Die Wege eines Ausschnitts, dazu die Punkte auf eingeschränkten Abschnitten.
     *
     * Getrennt gehalten statt am Weg vermerkt: Das Netz wird auf gerundete Punkte gebaut,
     * und danach ist der einzelne Weg nicht mehr zu erkennen. Über die Punktmenge lässt
     * sich hinterher an der fertigen Strecke ablesen, wie viel davon eingeschränkt war —
     * ohne den Graphen dafür umzubauen.
     */
    private class Wege(
        val ways: List<List<Node>>,
        val eingeschraenkt: Set<Node>,
        /** Kanten von Flüssen, in Zeichenrichtung — also stromab. */
        val stromab: Set<Kante> = emptySet(),
        /** Wege über Land um ein Wehr herum. */
        val umtrage: List<List<Node>> = emptyList(),
        /** Stücke, die zu einem Kanal gehören — für das Kanu ein Umweg zweiter Wahl. */
        val kanal: Set<Kante> = emptySet(),
    )

    /**
     * Ein Stück zwischen zwei Punkten, **mit Richtung**. Das Wegenetz selbst ist
     * ungerichtet; für die Frage „flussauf oder flussab" zählt aber, in welcher Folge die
     * Punkte in OSM stehen.
     */
    private data class Kante(val von: Node, val nach: Node)

    /** Die Elemente einer Antwort oder Kachel, oder `null`, wenn sie nicht lesbar ist. */
    private fun elemente(json: String): JSONArray? =
        runCatching { JSONObject(json).optJSONArray("elements") }.getOrNull()

    private fun parseWays(elements: JSONArray?, craft: Craft): Wege = runCatching {
        if (elements == null) return@runCatching Wege(emptyList(), emptySet(), emptySet())
        val ways = ArrayList<List<Node>>()
        val eingeschraenkt = HashSet<Node>()
        val stromab = HashSet<Kante>()
        val umtrage = ArrayList<List<Node>>()
        val kanal = HashSet<Kante>()
        for (i in 0 until elements.length()) {
            val el = elements.getJSONObject(i)
            val tags = el.optJSONObject("tags")
            if (istUmtrageweg(tags)) {
                punkte(el)?.takeIf { it.size >= 2 }?.let { umtrage.add(it) }
                continue
            }
            if (tags?.optString("waterway") !in navigableFor(craft)) continue
            val zugang = zugang(tags, craft)
            if (zugang == Zugang.GESPERRT) continue
            val geom = el.optJSONArray("geometry") ?: continue
            val nodes = (0 until geom.length()).map { g ->
                val p = geom.getJSONObject(g)
                Node.of(p.getDouble("lat"), p.getDouble("lon"))
            }
            if (nodes.size < 2) continue
            ways.add(nodes)
            if (zugang == Zugang.EINGESCHRAENKT) eingeschraenkt.addAll(nodes)
            // **Nur Flüsse haben eine Fließrichtung.** OSM zeichnet sie stromab; bei der
            // Saale laufen 33 von 37 längeren Abschnitten nach Norden, die übrigen sind
            // Mäander. Kanäle stehen still, und ihre Zeichenrichtung ist Zufall — sie
            // zählen weder flussauf noch flussab.
            if (tags?.optString("waterway") == "river") {
                for ((a, b) in nodes.zipWithNext()) if (a != b) stromab.add(Kante(a, b))
            }
            // **Mühlgräben und Seitenarme sind Kanäle.** Die Lache bei Porstendorf etwa
            // schneidet die Saaleschleife ab und ist kürzer — im Kanu will man trotzdem
            // den Fluss. Ein Kanal ohne Alternative bleibt davon unberührt: Dort ändert
            // ein gleichmäßiger Aufschlag an der Wahl nichts.
            if (tags?.optString("waterway") == "canal") {
                for ((a, b) in nodes.zipWithNext()) {
                    if (a == b) continue
                    kanal.add(Kante(a, b))
                    kanal.add(Kante(b, a))
                }
            }
        }
        Wege(ways, eingeschraenkt, stromab, umtrage, kanal)
    }.getOrDefault(Wege(emptyList(), emptySet(), emptySet()))

    /**
     * Ein Weg über Land, auf dem das Boot am Wehr vorbeigetragen wird.
     *
     * OSM führt ihn auf drei Arten, je nachdem wer ihn eingetragen hat:
     * `whitewater=portage_way` bei den Wildwasserleuten, `canoe=portage`, und
     * `portage=designated` an Rampen und Treppen, die eigens dafür da sind.
     */
    private fun istUmtrageweg(tags: JSONObject?): Boolean {
        if (tags == null) return false
        return tags.optString("whitewater") == "portage_way" ||
            tags.optString("canoe") == "portage" ||
            tags.optString("portage").isNotBlank()
    }

    /** Die Punkte eines Weges, wie sie in der Antwort stehen. */
    private fun punkte(el: JSONObject): List<Node>? {
        val geom = el.optJSONArray("geometry") ?: return null
        return (0 until geom.length()).map {
            val p = geom.getJSONObject(it)
            Node.of(p.getDouble("lat"), p.getDouble("lon"))
        }
    }

    private val NAVIGABLE = setOf("river", "canal", "fairway")

    /** Hindernisse aus derselben Antwort lesen; Wege werden auf ihren Mittelpunkt reduziert. */
    private fun parseObstacles(elements: JSONArray?): List<Obstacle> = runCatching {
        if (elements == null) return@runCatching emptyList()
        (0 until elements.length()).mapNotNull { i ->
            val el = elements.getJSONObject(i)
            val tags = el.optJSONObject("tags") ?: return@mapNotNull null
            // `lock=yes` gehört dazu, und zwar als Erstes: Die Schleusenkammer trägt in
            // OSM `waterway=canal` und daneben `lock=yes` — an ihr hängen Name,
            // Öffnungszeiten und Telefon. Wer nur auf `waterway` schaut, findet höchstens
            // die Tore, und die wissen nichts.
            // **Brücken mit Durchfahrtshöhe.** Am Elbe-Lübeck-Kanal hängt an fast jeder
            // eine Tafel mit der Höhe; in OSM steht sie als `seamark:type=bridge` mit
            // `clearance_height`. Bisher war das nur ein Seezeichen unter vielen, mit
            // durchsichtiger Trefferfläche und ohne eigenes Symbol — und bei gesetzter
            // Route lag die Linie darüber. Wer wissen muss, ob er druntergeht, soll es
            // sehen, ohne zu suchen.
            val brueckenhoehe = tags.optString("seamark:bridge:clearance_height")
                .takeIf { it.isNotBlank() }
            val brueckenbreite = tags.optString("seamark:bridge:clearance_width")
                .takeIf { it.isNotBlank() }
            val ufer = landungsart(tags)
            val kind = when {
                // **Wasserkraft.** Der Kanal durch die Turbinen ist in OSM ein
                // gewöhnlicher Kanal, und die Route nahm ihn gern, weil er am Wehr
                // vorbeiführt. Dort fährt niemand durch.
                tags.optString("generator:source") == "hydro" ||
                    tags.optString("plant:source") == "hydro" -> ObstacleKind.POWER
                // Ein- und Ausstiege zuerst: Eine Slipanlage trägt oft zugleich
                // `portage=designated`, und als Umtrageweg wäre sie kein Symbol.
                ufer != null -> ObstacleKind.LANDING
                tags.optString("seamark:type") == "bridge" -> {
                    // Ohne Maß ist eine Brücke keine Auskunft, nur ein Punkt mehr.
                    if (brueckenhoehe == null && brueckenbreite == null) return@mapNotNull null
                    ObstacleKind.BRIDGE
                }
                tags.optString("lock") == "yes" -> ObstacleKind.LOCK
                // Manche Kammern tragen nur das Seezeichen, nicht `lock=yes`.
                tags.optString("seamark:type") == "lock_basin" -> ObstacleKind.LOCK
                else -> when (tags.optString("waterway")) {
                    "lock_gate" -> ObstacleKind.LOCK
                    "weir" -> ObstacleKind.WEIR
                    "sluice_gate" -> ObstacleKind.SLUICE
                    "dam" -> ObstacleKind.DAM
                    else -> return@mapNotNull null
                }
            }
            val istTor = kind == ObstacleKind.LOCK && tags.optString("lock") != "yes" &&
                tags.optString("seamark:type") != "lock_basin"
            val linie: List<LatLon>
            if (el.has("lat")) {
                linie = listOf(LatLon(el.getDouble("lat"), el.getDouble("lon")))
            } else {
                val geom = el.optJSONArray("geometry") ?: return@mapNotNull null
                if (geom.length() == 0) return@mapNotNull null
                linie = (0 until geom.length()).map {
                    val g = geom.getJSONObject(it)
                    LatLon(g.getDouble("lat"), g.getDouble("lon"))
                }
            }
            // **Die Mitte entlang der Linie, nicht der mittlere Punkt.** Eine Kammer ist in
            // OSM oft nur zwei Punkte lang; `geometry[2 / 2]` ist dann der zweite, also das
            // Ende — und genau dort sitzt ein Tor. Darum lagen die Symbole auf den Toren.
            // Eine Kammer als Fläche (`seamark:type=lock_basin`) ist ein geschlossener
            // Ring. Halbe Länge läge da auf der Gegenseite des Umrisses, also Schwerpunkt.
            val mitte = if (linie.size >= 4 && linie.first() == linie.last()) {
                val ecken = linie.dropLast(1)
                LatLon(ecken.map { it.lat }.average(), ecken.map { it.lon }.average())
            } else {
                mitteEntlang(linie)
            }
            val lat = mitte.lat
            val lon = mitte.lon
            fun tag(key: String) = tags.optString(key).takeIf { it.isNotBlank() }
            Obstacle(
                lat, lon, kind,
                // `lock_name` ist der genauere: `name` trägt an einem Schleusenkanal
                // gelegentlich den Namen des Kanals statt den der Schleuse.
                name = tag("lock_name") ?: tag("name") ?: tag("seamark:name"),
                // `service_times` ist bei Schleusen genauso verbreitet wie
                // `opening_hours` — die Oeblitzschleuse führt das eine, die Schleuse
                // Wettin das andere. Wer nur nach einem sucht, findet die Hälfte nicht.
                openingHours = tag("opening_hours") ?: tag("service_times"),
                phone = tag("phone"),
                vhf = tag("vhf"),
                maxLengthM = tag("maxlength"),
                maxWidthM = tag("maxwidth"),
                cemt = tag("CEMT"),
                clearanceHeightM = brueckenhoehe,
                clearanceWidthM = brueckenbreite,
                // Auch ein Wehr ist meist ein Weg quer über den Fluss. Seine Linie
                // entscheidet, ob die Route hindurchführt, nicht nur sein Mittelpunkt.
                line = if (istTor || kind == ObstacleKind.LANDING) emptyList() else linie,
                // Ein Kraftwerk hat keine Auskunft, aber es steht im Weg.
                isGate = istTor,
                landingKind = ufer,
            )
        }
    }.getOrDefault(emptyList())

    /**
     * Wozu eine Stelle am Ufer taugt, oder `null`, wenn sie kein Ein- oder Ausstieg ist.
     *
     * OSM führt das doppelt: `leisure=slipway` für die Rampe, `whitewater=put_in;egress`
     * oder `canoe=put_in` für den Kanugebrauch. Das Semikolon heißt „beides".
     */
    private fun landungsart(tags: JSONObject): LandingKind? {
        val wild = tags.optString("whitewater")
        val ein = wild.contains("put_in") || tags.optString("canoe") == "put_in"
        val aus = wild.contains("egress")
        return when {
            ein && aus -> LandingKind.PUT_IN_EGRESS
            ein -> LandingKind.PUT_IN
            aus -> LandingKind.EGRESS
            tags.optString("leisure") == "slipway" -> LandingKind.SLIPWAY
            else -> null
        }
    }

    /** Welche Hindernisse dicht genug am Weg liegen, um ihn zu betreffen. */
    private fun onPath(all: List<Obstacle>, path: List<LatLon>): List<Obstacle> {
        // Erst grob die Umgebung, dann zusammenlegen, dann fein prüfen. Andersherum fielen
        // Tore und Kammerflächen neben der Route vorher heraus, und das Symbol der Route
        // säße woanders als das der Karte.
        //
        // Die Strecke bekommt dafür ein Raster. Vorher wurde jedes Hindernis mit jedem
        // ihrer Stücke verglichen, bei Kahla nach Lübeck 10.600 mal 5.100.
        val strecke = StreckenRaster(path)
        val nah = all.filter { strecke.abstand(LatLon(it.lat, it.lon), OBSTACLE_UMGEBUNG_M) <= OBSTACLE_UMGEBUNG_M }
            .distinctBy { "%.5f,%.5f".format(it.lat, it.lon) }
        // Zur **Strecke**, nicht zu ihren Stützpunkten: Das Symbol sitzt in der Mitte der
        // Kammer, bei 225 m Länge über 100 m von jedem Punkt entfernt.
        return zusammenlegen(nah).filter { o ->
            val nahGenug = strecke.abstand(LatLon(o.lat, o.lon), OBSTACLE_NEAR_M) <= OBSTACLE_NEAR_M ||
                o.line.any { strecke.abstand(it, OBSTACLE_NEAR_M) <= OBSTACLE_NEAR_M }
            // **Ein Wehr zählt nur, wenn die Strecke es kreuzt.** Vierzig Meter Nähe
            // reichen dafür nicht: Wo umtragen wird, liegt das Wehr daneben, und es als
            // Hindernis zu melden hieße, vor etwas zu warnen, an dem man vorbeigeht.
            if (o.kind == ObstacleKind.WEIR || o.kind == ObstacleKind.DAM) {
                nahGenug && kreuzt(o, strecke)
            } else {
                nahGenug
            }
        }
    }

    /**
     * Ob die Strecke dieses Hindernis wirklich kreuzt, statt nur daran vorbeizuführen.
     *
     * Zwei Fälle: Das Wehr kreuzt den Fluss frei, oder es teilt einen Punkt mit ihm —
     * dann berühren sich die Linien nur, und ein strenges Kreuzen gäbe es nie. Wer
     * umträgt, kommt dem Wehr nahe, ohne hindurchzufahren; deshalb zählen nur wenige
     * Meter als Berührung.
     */
    private fun kreuzt(o: Obstacle, strecke: StreckenRaster): Boolean {
        if (o.line.size >= 2) {
            if (o.line.any { strecke.abstand(it, WEHR_BERUEHRT_M) <= WEHR_BERUEHRT_M }) return true
            for ((a, b) in o.line.zipWithNext()) {
                for ((c, d) in strecke.stueckeNahe(a, b)) {
                    if (kreuzen(a, b, c, d)) return true
                }
            }
            return false
        }
        // Ein Wehr als einzelner Punkt hat keine Linie; dann zählt die Nähe zur Strecke.
        return strecke.abstand(LatLon(o.lat, o.lon), WEHR_AUF_STRECKE_M) <= WEHR_AUF_STRECKE_M
    }

    /**
     * Die Stücke einer Strecke in Zellen, damit die Frage „wie weit liegt dieser Punkt
     * davon" nicht jedes Stück abgehen muss. Die Zellen sind klein; lange Stücke, etwa die
     * Luftlinie am Anfang, stehen in jeder Zelle, die sie überspannen.
     */
    private class StreckenRaster(private val path: List<LatLon>) {
        private val zellen = HashMap<Long, MutableList<Int>>()

        init {
            for (i in 0 until path.size - 1) {
                val a = path[i]
                val b = path[i + 1]
                for (y in zeile(minOf(a.lat, b.lat))..zeile(maxOf(a.lat, b.lat))) {
                    for (x in spalte(minOf(a.lon, b.lon))..spalte(maxOf(a.lon, b.lon))) {
                        zellen.getOrPut(schluessel(y, x)) { mutableListOf() }.add(i)
                    }
                }
            }
        }

        /** Kürzester Abstand von [p] zur Strecke; weiter als [grenze] zählt als „weit". */
        fun abstand(p: LatLon, grenze: Double): Double {
            var best = Double.MAX_VALUE
            for (i in stueckeUm(p, grenze)) {
                best = minOf(best, abstandZurLinie(p, listOf(path[i], path[i + 1])))
            }
            return best
        }

        /** Die Stücke der Strecke in der Nähe der Linie [a]–[b]. */
        fun stueckeNahe(a: LatLon, b: LatLon): List<Pair<LatLon, LatLon>> {
            val mitte = LatLon((a.lat + b.lat) / 2, (a.lon + b.lon) / 2)
            val grenze = distanceM(a, b) / 2 + 10.0
            return stueckeUm(mitte, grenze).map { path[it] to path[it + 1] }
        }

        private fun stueckeUm(p: LatLon, grenze: Double): Set<Int> {
            val dy = kotlin.math.ceil(grenze / (ZELLE * 111_320.0)).toInt()
            val dx = kotlin.math.ceil(
                grenze / (ZELLE * 111_320.0 * kotlin.math.cos(Math.toRadians(p.lat))),
            ).toInt()
            val y0 = zeile(p.lat)
            val x0 = spalte(p.lon)
            val raus = HashSet<Int>()
            for (y in y0 - dy..y0 + dy) {
                for (x in x0 - dx..x0 + dx) zellen[schluessel(y, x)]?.let { raus.addAll(it) }
            }
            return raus
        }

        private fun zeile(lat: Double) = kotlin.math.floor(lat / ZELLE).toInt()
        private fun spalte(lon: Double) = kotlin.math.floor(lon / ZELLE).toInt()
        private fun schluessel(y: Int, x: Int) = y.toLong() * 10_000_000L + x

        private companion object {
            /** Kantenlänge einer Zelle in Grad, rund 500 m. */
            const val ZELLE = 0.005
        }
    }

    /** So nah heißt: Die Strecke führt durch das Wehr, nicht daran vorbei. */
    private const val WEHR_BERUEHRT_M = 3.0

    /** So nah an der Strecke liegt ein Wehr, das nur als Punkt erfasst ist, auf ihr. */
    private const val WEHR_AUF_STRECKE_M = 15.0

    /** Ob sich zwei Strecken kreuzen; Berührungen an den Enden zählen nicht. */
    private fun kreuzen(a: LatLon, b: LatLon, c: LatLon, d: LatLon): Boolean {
        fun seite(p: LatLon, q: LatLon, r: LatLon): Double =
            (q.lon - p.lon) * (r.lat - p.lat) - (q.lat - p.lat) * (r.lon - p.lon)
        return seite(a, b, c) * seite(a, b, d) < 0 && seite(c, d, a) * seite(c, d, b) < 0
    }

    /** So nah an ihrer Kammer liegt ein Tor, das zu ihr gehört. */
    private const val TOR_AN_KAMMER_M = 60.0

    /**
     * So nah an der Linie einer anderen Kammer ist es **dieselbe**: dieselbe Kammer
     * einmal als Linie mit `lock=yes` und einmal als Fläche mit `lock_basin`. Die
     * Nachbarkammer einer Doppelschleuse liegt 30 m und mehr daneben.
     */
    private const val KAMMER_DOPPELT_M = 15.0

    /** So dicht beieinander ist es dieselbe Stelle am Ufer. */
    private const val UFER_DOPPELT_M = 30.0

    /** Tore ohne Kammer bis zu diesem Abstand gehören zu **einer** Schleuse. */
    private const val TORE_EINE_SCHLEUSE_M = 350.0

    /**
     * Aus Kammern und Toren wird je **Kammer** ein Symbol, in ihrer Mitte.
     *
     * In OSM besteht eine Schleuse aus einer Kammer (`lock=yes`, oft auch
     * `seamark:type=lock_basin`) mit Name, Zeiten, Telefon und Maßen, und aus je einem Tor
     * an beiden Enden. Die Tore wissen nichts, auch wenn sie, wie an der Schleuse Hilter,
     * den Namen tragen. Vorher reichte ein Name, damit ein Tor als „hat Auskunft" galt und
     * gegen die Kammer gewann; die Kammer mit Zeiten und Telefon flog dann heraus.
     *
     * Doppelschleusen wie Hüntel bleiben **zwei** Symbole: Jede Kammer hat eigene Maße,
     * und das Symbol der befahrenen soll auf der Route liegen, nicht zwischen beiden.
     *
     * 1. Ein Tor, das an einer Kammer liegt, gehört zu ihr und verschwindet.
     * 2. Dieselbe Kammer als Linie und als Fläche wird eins, an der Stelle der Linie.
     * 3. Tore ohne Kammer werden zu einem Symbol in ihrer Mitte.
     */
    private fun zusammenlegen(alle: List<Obstacle>): List<Obstacle> {
        val kammern = alle.filter { it.kind == ObstacleKind.LOCK && !it.isGate }
        val tore = alle.filter { it.kind == ObstacleKind.LOCK && it.isGate }
        val ufer = alle.filter { it.kind == ObstacleKind.LANDING }
        val rest = alle.filter { it.kind != ObstacleKind.LOCK && it.kind != ObstacleKind.LANDING }

        // Am Paradieswehr in Jena steht dieselbe Rampe als Weg und als Knoten in OSM,
        // ein paar Meter daneben. Das sind nicht zwei Einstiege.
        val uferstellen = gruppieren(ufer) { a, b ->
            distanceM(LatLon(a.lat, a.lon), LatLon(b.lat, b.lon)) <= UFER_DOPPELT_M
        }.map { zuEiner(it) }

        val freieTore = tore.filter { t ->
            val p = LatLon(t.lat, t.lon)
            kammern.none { k -> abstandZurLinie(p, k.line) <= TOR_AN_KAMMER_M }
        }

        val einzelne = gruppieren(kammern) { a, b ->
            abstandZurLinie(LatLon(a.lat, a.lon), b.line) <= KAMMER_DOPPELT_M ||
                abstandZurLinie(LatLon(b.lat, b.lon), a.line) <= KAMMER_DOPPELT_M
        }.map { gruppe ->
            // Die Linie liegt im Fahrwasser, die Fläche drumherum: Die Linie gibt den Ort.
            val ort = gruppe.firstOrNull { !it.istRing } ?: gruppe.first()
            zuEiner(gruppe).copy(lat = ort.lat, lon = ort.lon)
        }

        val torSchleusen = gruppieren(freieTore) { a, b ->
            distanceM(LatLon(a.lat, a.lon), LatLon(b.lat, b.lon)) <= TORE_EINE_SCHLEUSE_M
        }.map { zuEiner(it) }

        return rest + einzelne + torSchleusen + uferstellen
    }

    private val Obstacle.istRing: Boolean
        get() = line.size >= 4 && line.first() == line.last()

    /** Fasst zusammen, was beieinander liegt — auch über Zwischenglieder hinweg. */
    private fun gruppieren(
        alle: List<Obstacle>,
        gehoertZusammen: (Obstacle, Obstacle) -> Boolean,
    ): List<List<Obstacle>> {
        val offen = alle.toMutableList()
        val gruppen = ArrayList<List<Obstacle>>()
        while (offen.isNotEmpty()) {
            val gruppe = mutableListOf(offen.removeAt(0))
            var i = 0
            while (i < gruppe.size) {
                val weitere = offen.filter { gehoertZusammen(gruppe[i], it) }
                offen.removeAll(weitere)
                gruppe.addAll(weitere)
                i++
            }
            gruppen.add(gruppe)
        }
        return gruppen
    }

    /** Eine Gruppe wird ein Symbol: in der Mitte, mit dem, was eines davon weiß. */
    private fun zuEiner(gruppe: List<Obstacle>): Obstacle {
        if (gruppe.size == 1) return gruppe.first()
        fun <T> erstes(f: (Obstacle) -> T?): T? = gruppe.firstNotNullOfOrNull(f)
        return gruppe.first().copy(
            lat = gruppe.map { it.lat }.average(),
            lon = gruppe.map { it.lon }.average(),
            name = erstes { it.name },
            openingHours = erstes { it.openingHours },
            phone = erstes { it.phone },
            vhf = erstes { it.vhf },
            maxLengthM = erstes { it.maxLengthM },
            maxWidthM = erstes { it.maxWidthM },
            cemt = erstes { it.cemt },
            line = gruppe.flatMap { it.line },
        )
    }

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
     * Punkte, die teuer sind: Wehre und Dämme, ebenso ein Einfahrtsverbot. Getrennt wird
     * an ihnen nicht mehr, sie kosten einen Aufschlag, damit jede Schleuse, jeder Umweg
     * und jede Umtragung gewinnt. Schleusentore gehören **nicht** dazu — durch eine
     * Schleuse kommt man, sie kostet nur Zeit.
     */
    private fun barrierNodes(elements: JSONArray?): Set<Node> = runCatching {
        if (elements == null) return@runCatching emptySet()
        (0 until elements.length()).mapNotNull<Int, List<Node>> { i ->
            val el = elements.getJSONObject(i)
            val tags = el.optJSONObject("tags") ?: return@mapNotNull null
            val blocking = tags.optString("waterway") in setOf("weir", "dam") ||
                tags.optString("seamark:notice:category") == "no_entry" ||
                tags.optString("seamark:notice:function") == "prohibition"
            if (!blocking) return@mapNotNull null
            // **Alle** Punkte, nicht nur die Mitte. Ein Wehr ist quer über den Fluss
            // gezeichnet; gesperrt ist es dort, wo es den Fluss kreuzt, und das ist
            // irgendeiner seiner Punkte — bei zwei Punkten nie der mittlere.
            when {
                el.has("lat") -> listOf(Node.of(el.getDouble("lat"), el.getDouble("lon")))
                else -> el.optJSONArray("geometry")?.let { geom ->
                    (0 until geom.length()).map {
                        val p = geom.getJSONObject(it)
                        Node.of(p.getDouble("lat"), p.getDouble("lon"))
                    }
                }
            }
        }.flatten().toSet()
    }.getOrDefault(emptySet())

    /**
     * Aufschlag auf Abschnitte mit allgemeinem Bootsverbot — **nur beim Vergleichen**.
     *
     * Ein solcher Abschnitt ist fürs Kanu befahrbar, aber er soll nicht gewählt werden,
     * wenn es eine freie Möglichkeit gibt. Der Wegsuche sind Meter sonst gleich viel wert:
     * Bei Wettin an der Saale nahm sie den Kraftwerksgraben (`boat=no`, 1598 m) statt des
     * Schleusenarms, weil der 27 m länger war. Zählt der Graben dreifach, gewinnt der
     * Schleusenarm — und die Fahrt ist tatsächlich nur 30 m länger.
     *
     * Der Faktor beantwortet die Frage „wie weit darf der Umweg sein, damit er sich
     * lohnt": bis zum Dreifachen. Genug Luft für eine Schleuse, die einen Bogen macht,
     * ohne dass jemand zehn Kilometer paddelt, um 500 m Verbot auszuweichen.
     *
     * **Er verbietet nichts.** Gibt es nur den gesperrten Weg — bei Zeutsch sind es 44 km
     * am Stück —, wird er genommen. Und er verfälscht keine Anzeige: Länge, Verbrauch und
     * die roten Abschnitte werden hinterher aus den echten Koordinaten gerechnet.
     */
    private const val RESTRICTED_COST = 3.0

    /**
     * Das Wegenetz, und welche seiner Kanten über Land führen.
     *
     * Umtragen ist keine Fahrt: Es gehört in die Wegsuche, damit ein Kanu am Wehr
     * weiterkommt, muss aber unterscheidbar bleiben — auf der Karte, in der Länge und in
     * dem, was unten im Kasten steht.
     */
    private class Graph(
        val adj: Map<Node, List<Pair<Node, Double>>>,
        val umtrage: Set<Kante> = emptySet(),
    )

    /**
     * Was das Tragen kostet. Nicht das Verbot, sondern die Mühe: Ein Umtrageweg wird nur
     * genommen, wenn der Umweg über Wasser mehr als das Achtfache kostet — meistens also
     * dann, wenn es über Wasser gar nicht weitergeht.
     */
    /**
     * Was ein Kanal im Kanu kostet. Seitenarme und Mühlgräben sind in OSM Kanäle und oft
     * kürzer als die Flussschleife daneben; im Kanu will man den Fluss. Wo es keinen
     * Fluss gibt, ändert der gleichmäßige Aufschlag an der Wahl nichts.
     */
    private const val KANAL_KOSTEN_KANU = 2.5

    private const val UMTRAGE_KOSTEN = 8.0

    /**
     * Was eine **erfundene** Verbindung kostet: der Anschluss ans Ufer, die Linie von
     * Anleger zu Anleger, der Lückenschluss. Teurer als ein eingetragener Umtrageweg,
     * damit ein vorhandener Pfad benutzt wird und nicht quer daran vorbei abgekürzt.
     */
    private const val ERFUNDEN_KOSTEN = 30.0

    /**
     * So weit darf der Anschluss ans Wasser sein. Ein Umtrageweg endet am Ufer, nicht auf
     * der Flusslinie; in OSM teilen sie fast nie einen Punkt. Ohne diese Brücke hinge der
     * Weg in der Luft und die Wegsuche fände ihn nie.
     */
    private const val UMTRAGE_ANSCHLUSS_M = 80.0

    /** Kantenlänge des Suchrasters, rund 200 m. */
    private const val RASTER = 2_000

    /** Die Wasserkraftanlagen: Knoten als einzelner Punkt, Gebäude als Umriss. */
    private fun kraftwerke(obstacles: List<Obstacle>): List<List<Node>> =
        obstacles.filter { it.kind == ObstacleKind.POWER }
            .map { o ->
                o.line.takeIf { it.isNotEmpty() }?.map { Node.of(it.lat, it.lon) }
                    ?: listOf(Node.of(o.lat, o.lon))
            }

    /** Die Wehre und Dämme als Linien — was eine erfundene Verbindung nicht queren darf. */
    private fun sperrlinien(obstacles: List<Obstacle>): List<List<Node>> =
        obstacles.filter { it.kind == ObstacleKind.WEIR || it.kind == ObstacleKind.DAM }
            .map { o -> o.line.map { Node.of(it.lat, it.lon) } }
            .filter { it.size >= 2 }

    private fun buildGraph(
        ways: List<List<Node>>,
        barriers: Set<Node>,
        eingeschraenkt: Set<Node> = emptySet(),
        umtrage: List<List<Node>> = emptyList(),
        sperrwege: List<List<Node>> = emptyList(),
        kraftwerke: List<List<Node>> = emptyList(),
        kanal: Set<Kante> = emptySet(),
        kanalFaktor: Double = 1.0,
        wasserRaster: Map<Long, MutableList<Pair<Node, Node>>>? = null,
    ): Graph {
        val g = HashMap<Node, MutableList<Pair<Node, Double>>>()
        val kraftPunkte = if (kraftwerke.isEmpty()) null else raster(kraftwerke.flatten())
        // Die Wehre als Linien. **Ein Wehr teilt oft keinen Punkt mit dem Fluss.** Am
        // Burgauer Wehr in Jena kreuzt es ihn nur geometrisch, und über die Punkte
        // gesperrt war dort gar nichts: Die Strecke fuhr mitten hindurch, und die
        // Umtragung daneben blieb ungenutzt.
        val wehrLinien = if (sperrwege.isEmpty()) null else segmentRaster(sperrwege, emptySet())
        // **Wo überhaupt etwas zu prüfen ist.** Die genaue Prüfung auf Wehr und Turbine
        // kostet gut fünfzig Nachschlagen je Kante, und von über einer halben Million
        // Kanten liegt kaum eine in der Nähe von beidem. Vorab werden die Zellen um jedes
        // Wehr und jedes Kraftwerk markiert; anderswo entfällt die Prüfung ganz.
        val heiss = heisseZellen(sperrwege + kraftwerke)
        val kraftUmrisse =
            if (kraftwerke.isEmpty()) null else segmentRaster(kraftwerke.filter { it.size >= 2 }, emptySet())
        /**
         * Was ein Stück Wasser kostet — **an einer Stelle** gerechnet, damit kein
         * zweiter Weg daran vorbeiführt. Beim Anschluss einer Umtragung wird ein Stück
         * geteilt; die Hälften wurden vorher ohne Aufschlag eingesetzt, und schon lief
         * ein billiger Nebenweg um Wehr und Turbine herum.
         *
         * Ein Wehr sperrt nicht, es kostet: Sonst blieb die Wegsuche davor stehen und
         * meldete „kein durchgängiger Wasserweg", wofür auf hunderten Kilometern ein
         * einziges falsch erfasstes Wehr reichte. Eine Wasserkraftanlage kostet das
         * Zehnfache — über ein Wehr kommt man notfalls, durch eine Turbine niemand.
         */
        fun wasserkosten(a: Node, b: Node): Double {
            val laenge = distanceM(a.toLatLon(), b.toLatLon())
            var d = laenge
            if (a in eingeschraenkt && b in eingeschraenkt) d *= RESTRICTED_COST
            if (kanalFaktor != 1.0 && Kante(a, b) in kanal) d *= kanalFaktor
            // **Ohne Rand.** Ein Wehr trifft den Fluss oft genau in einem seiner Punkte;
            // am Burgauer Wehr liegt der Schnitt 30 cm hinter dem Kantenanfang. Die
            // Toleranz, die einen Anschluss an seinem Ende erlaubt, verschluckte das.
            // Lange Kanten werden immer genau geprüft: Sie könnten eine markierte Zelle
            // überspannen, ohne mit einem Ende darin zu liegen.
            val genau = heiss.isNotEmpty() && (
                laenge > HEISS_LANGE_KANTE_M ||
                    zelle(a.lat / RASTER, a.lon / RASTER) in heiss ||
                    zelle(b.lat / RASTER, b.lon / RASTER) in heiss
                )
            val ueberWehr = genau && wehrLinien != null && kreuztWasser(wehrLinien, a, b, 0.0)
            if (a in barriers || b in barriers || ueberWehr) d += SPERRE_AUFSCHLAG_M
            if (genau && kraftPunkte != null && durchKraftwerk(kraftPunkte, kraftUmrisse, a, b)) {
                d += KRAFTWERK_AUFSCHLAG_M
            }
            return d
        }
        for (way in ways) {
            for ((a, b) in way.zipWithNext()) {
                if (a == b) continue
                val d = wasserkosten(a, b)
                g.getOrPut(a) { mutableListOf() }.add(b to d)
                g.getOrPut(b) { mutableListOf() }.add(a to d)
            }
        }
        if (umtrage.isEmpty() || g.isEmpty()) return Graph(g)

        val kanten = HashSet<Kante>()
        fun verbinde(a: Node, b: Node, faktor: Double = UMTRAGE_KOSTEN) {
            if (a == b) return
            val d = distanceM(a.toLatLon(), b.toLatLon()) * faktor
            g.getOrPut(a) { mutableListOf() }.add(b to d)
            g.getOrPut(b) { mutableListOf() }.add(a to d)
            kanten.add(Kante(a, b))
            kanten.add(Kante(b, a))
        }
        // Das Wasser **vor** den Umtragewegen: Sonst hinge der Anschluss an ihnen selbst.
        // Geprüft wird auch nur gegen das Wasser, nicht gegen das Wehr: An Reschwitz und
        // Fischersdorf liegen Aus- und Einstieg am selben Ufer, und jede Verbindung
        // dorthin streift das Wehr an seinem Ende, genau dort, wo man vorbeiträgt.
        val wasser = wasserRaster ?: segmentRaster(ways, emptySet())
        for (weg in umtrage) {
            for ((a, b) in weg.zipWithNext()) verbinde(a, b)
            for (ende in listOf(weg.first(), weg.last())) {
                // Angeschlossen wird an den nächsten Punkt **auf der Linie**, nicht an den
                // nächsten eingetragenen Punkt, und an jedes Gewässer in Reichweite. Am
                // Wehr Kahla liegt der Ausstieg 34 m von der Saale oberhalb, aber 60 m
                // von ihrem nächsten Punkt: Über Punkte hingen beide Enden unterhalb des
                // Wehrs, und die Route brach dort ab.
                for ((a, b) in segmenteNah(wasser, ende, UMTRAGE_ANSCHLUSS_M)) {
                    // Ein Stück zwischen zwei Sperrpunkten führt nirgendwohin.
                    if (a in barriers && b in barriers) continue
                    // **Nicht auf die Sperre selbst.** Oberhalb des Wehrs Kahla beginnt
                    // die Saale genau im gesperrten Punkt; der nächste Punkt der Linie
                    // ist er selbst, und der Anschluss fiel jedes Mal weg. Ein Stück
                    // dahinter liegt das Wasser, in das man das Boot wieder setzt.
                    val laenge = distanceM(a.toLatLon(), b.toLatLon())
                    val rand = if (laenge > 0) (HINTER_DER_SPERRE_M / laenge).coerceAtMost(0.5) else 0.0
                    val auf = aufSegment(
                        ende,
                        a,
                        b,
                        if (a in barriers) rand else 0.0,
                        if (b in barriers) 1.0 - rand else 1.0,
                    )
                    if (auf in barriers) continue
                    // Der Anschluss darf das Wasser nur an seinem Ende berühren. Quert er
                    // es, liefe die Umtragung quer über den Fluss — genau das sah auf der
                    // Karte bei Kahla aus wie ein Sprung über das Wehr.
                    if (kreuztWasser(wasser, ende, auf)) continue
                    // **Über die Sperre führt nichts.** Das Stück Saale oberhalb des Wehrs
                    // Kahla hängt am gesperrten Punkt; angeschlossen wird es trotzdem,
                    // aber nur an seinem freien Ende. Sonst wäre der Anschluss zugleich
                    // ein Weg durch das Wehr.
                    for (ecke in listOf(a, b)) {
                        if (ecke in barriers || ecke == auf) continue
                        val d = wasserkosten(ecke, auf)
                        g.getOrPut(ecke) { mutableListOf() }.add(auf to d)
                        g.getOrPut(auf) { mutableListOf() }.add(ecke to d)
                    }
                    verbinde(ende, auf, ERFUNDEN_KOSTEN)
                }
            }
        }
        // **Bruchstücke zusammenhalten.** In Bad Kösen liegt der Umtrageweg in zwei
        // Teilen, dazwischen 38 m gewöhnlicher Fußweg, den wir nicht laden. Ohne diesen
        // Lückenschluss endet die Umtragung im Nichts.
        val enden = umtrage.flatMap { listOf(it.first() to it, it.last() to it) }
        for ((i, j) in paareInDerNaehe(enden.map { it.first.toLatLon() }, UMTRAGE_LUECKE_M)) {
            val (a, wegA) = enden[i]
            val (b, wegB) = enden[j]
            if (wegA === wegB || a == b) continue
            if (kreuztWasser(wasser, a, b)) continue
            verbinde(a, b, ERFUNDEN_KOSTEN)
        }
        return Graph(g, kanten)
    }

    /**
     * Ob die Strecke [a]–[b] Wasser quert, statt es nur an einem Ende zu berühren.
     *
     * Getragen wird an Land. Eine erfundene Verbindung, die quer über den Fluss läuft,
     * ist keine Umtragung; sie sieht aus wie ein Sprung über das Wehr und führt auch
     * dorthin, wo niemand laufen kann.
     */
    private fun kreuztWasser(
        raster: Map<Long, MutableList<Pair<Node, Node>>>,
        von: Node,
        bis: Node,
        rand: Double = ENDEN_RAND_M,
    ): Boolean {
        // **Ohne die letzten zwei Meter an beiden Enden.** Ein Anschluss endet auf dem
        // Fluss, oft mitten auf einem Stück und nicht auf einem seiner Punkte. Ohne diese
        // Verkürzung galt genau das als Queren, und der Ausstieg am Wehr Fischersdorf
        // bekam keine Verbindung zum Wasser oberhalb.
        val a = einwaerts(von, bis, rand)
        val b = einwaerts(bis, von, rand)
        val zellen = setOf(
            zelle(a.lat / RASTER, a.lon / RASTER),
            zelle(b.lat / RASTER, b.lon / RASTER),
            zelle((a.lat + b.lat) / 2 / RASTER, (a.lon + b.lon) / 2 / RASTER),
        )
        for (z in zellen) {
            for (dy in -1..1) {
                for (dx in -1..1) {
                    val nachbar = z + dy.toLong() * 1_000_000L + dx
                    for ((c, d) in raster[nachbar].orEmpty()) {
                        if (schneidet(a, b, c, d)) return true
                    }
                }
            }
        }
        return false
    }

    /** So viel wird an beiden Enden nicht mitgeprüft, wenn eine Linie dort enden darf. */
    private const val ENDEN_RAND_M = 2.0

    /** [meter] von [p] aus in Richtung [ziel]. */
    private fun einwaerts(p: Node, ziel: Node, meter: Double): Node {
        if (meter <= 0.0) return p
        val laenge = distanceM(p.toLatLon(), ziel.toLatLon())
        if (laenge < meter * 3) return p
        val t = meter / laenge
        val a = p.toLatLon()
        val b = ziel.toLatLon()
        return Node.of(a.lat + (b.lat - a.lat) * t, a.lon + (b.lon - a.lon) * t)
    }

    /** Ob sich zwei Strecken kreuzen — Berührungen an den Enden zählen nicht. */
    private fun schneidet(a: Node, b: Node, c: Node, d: Node): Boolean {
        fun seite(p: Node, q: Node, r: Node): Double {
            val x1 = (q.lon - p.lon).toDouble()
            val y1 = (q.lat - p.lat).toDouble()
            val x2 = (r.lon - p.lon).toDouble()
            val y2 = (r.lat - p.lat).toDouble()
            return x1 * y2 - y1 * x2
        }
        // Ein Ende auf der anderen Linie ist der erlaubte Fall: Genau dort wird das Boot
        // ins Wasser gesetzt.
        if (a == c || a == d || b == c || b == d) return false
        val s1 = seite(a, b, c)
        val s2 = seite(a, b, d)
        val s3 = seite(c, d, a)
        val s4 = seite(c, d, b)
        return s1 * s2 < 0 && s3 * s4 < 0
    }

    /**
     * Was eine Wasserkraftanlage kostet: das Zehnfache eines Wehrs. Über ein Wehr kommt
     * man notfalls, durch eine Turbine niemand. Der Kanal durch die Anlage führt oft am
     * Wehr vorbei und war damit der kürzere Weg — genau das soll er nie sein.
     */
    private const val KRAFTWERK_AUFSCHLAG_M = 1_000_000.0

    /** Ab dieser Länge wird eine Kante auch außerhalb markierter Zellen genau geprüft. */
    private const val HEISS_LANGE_KANTE_M = 150.0

    /**
     * Die Rasterzellen, in denen eine Wasserkante ein Wehr kreuzen oder an einem
     * Kraftwerk liegen kann: jede Zelle, die eine dieser Linien berührt, samt ihren
     * Nachbarn. Lange Stücke werden alle 50 m abgetastet, damit keine Zelle dazwischen
     * fehlt.
     */
    private fun heisseZellen(linien: List<List<Node>>): Set<Long> {
        val raus = HashSet<Long>()
        fun markiere(lat: Double, lon: Double) {
            val n = Node.of(lat, lon)
            val y = n.lat / RASTER
            val x = n.lon / RASTER
            for (dy in -1..1) for (dx in -1..1) raus.add(zelle(y + dy, x + dx))
        }
        for (linie in linien) {
            if (linie.size == 1) {
                linie[0].toLatLon().let { markiere(it.lat, it.lon) }
                continue
            }
            for ((a, b) in linie.zipWithNext()) {
                val pa = a.toLatLon()
                val pb = b.toLatLon()
                val schritte = (distanceM(pa, pb) / 50.0).toInt() + 1
                for (i in 0..schritte) {
                    val t = i.toDouble() / schritte
                    markiere(pa.lat + (pb.lat - pa.lat) * t, pa.lon + (pb.lon - pa.lon) * t)
                }
            }
        }
        return raus
    }

    /** So nah an einer Wasserkraftanlage führt das Wasser durch sie hindurch. */
    private const val KRAFTWERK_NAH_M = 25.0

    /**
     * Ob dieses Stück Wasser durch eine Wasserkraftanlage läuft.
     *
     * Am Saale-Wehr Uhlstädt führt ein Kanal am Wehr vorbei, mitten durch die Turbinen —
     * für die Wegsuche der bequemste Weg, in Wirklichkeit keiner. In OSM steht die Anlage
     * als Knoten daneben oder als Gebäude, durch das der Kanal läuft.
     */
    private fun durchKraftwerk(
        punkte: Map<Long, MutableList<Node>>,
        umrisse: Map<Long, MutableList<Pair<Node, Node>>>?,
        a: Node,
        b: Node,
    ): Boolean {
        val mitte = Node.of(
            (a.toLatLon().lat + b.toLatLon().lat) / 2,
            (a.toLatLon().lon + b.toLatLon().lon) / 2,
        )
        if (listOf(a, b, mitte).any { naechster(punkte, it, KRAFTWERK_NAH_M) != null }) return true
        return umrisse != null && kreuztWasser(umrisse, a, b)
    }

    /** Knoten in Zellen von rund 200 m, damit die Suche nach dem nächsten nicht alles abgeht. */
    private fun raster(knoten: List<Node>): Map<Long, MutableList<Node>> {
        val r = HashMap<Long, MutableList<Node>>()
        for (n in knoten) {
            r.getOrPut(zelle(n.lat / RASTER, n.lon / RASTER)) { mutableListOf() }.add(n)
        }
        return r
    }

    private fun zelle(y: Int, x: Int): Long = y.toLong() * 1_000_000L + x

    /** Die Wasserstücke in Zellen von rund 200 m, für die Suche nach dem Anschluss. */
    private fun segmentRaster(
        ways: List<List<Node>>,
        barriers: Set<Node>,
    ): Map<Long, MutableList<Pair<Node, Node>>> {
        val r = HashMap<Long, MutableList<Pair<Node, Node>>>()
        for (way in ways) {
            for ((a, b) in way.zipWithNext()) {
                // Ein Stück, dessen **beide** Enden gesperrt sind, führt nirgendwohin.
                if (a == b || (a in barriers && b in barriers)) continue
                val cells = setOf(
                    zelle(a.lat / RASTER, a.lon / RASTER),
                    zelle(b.lat / RASTER, b.lon / RASTER),
                    zelle((a.lat + b.lat) / 2 / RASTER, (a.lon + b.lon) / 2 / RASTER),
                )
                for (c in cells) r.getOrPut(c) { mutableListOf() }.add(a to b)
            }
        }
        return r
    }

    /** Alle Wasserstücke, die höchstens [grenze] Meter von [p] entfernt sind. */
    private fun segmenteNah(
        raster: Map<Long, MutableList<Pair<Node, Node>>>,
        p: Node,
        grenze: Double,
    ): List<Pair<Node, Node>> {
        val y = p.lat / RASTER
        val x = p.lon / RASTER
        val raus = LinkedHashSet<Pair<Node, Node>>()
        val punkt = p.toLatLon()
        for (dy in -1..1) {
            for (dx in -1..1) {
                for (seg in raster[zelle(y + dy, x + dx)].orEmpty()) {
                    val d = abstandZurLinie(punkt, listOf(seg.first.toLatLon(), seg.second.toLatLon()))
                    if (d <= grenze) raus.add(seg)
                }
            }
        }
        return raus.toList()
    }

    /**
     * Was eine Sperre kostet: hundert Kilometer. Kein Verbot, sondern der letzte Ausweg.
     * Jeder Umweg, jede Schleuse und jede Umtragung darunter wird vorgezogen.
     */
    private const val SPERRE_AUFSCHLAG_M = 100_000.0

    /** So groß darf die Lücke zwischen zwei Stücken eines Umtragewegs sein. */
    private const val UMTRAGE_LUECKE_M = 80.0

    /** So weit hinter einer Sperre wird wieder eingesetzt. */
    private const val HINTER_DER_SPERRE_M = 10.0

    /**
     * Der Punkt auf dem Stück [a]–[b], der [p] am nächsten liegt, begrenzt auf den
     * Abschnitt zwischen [tMin] und [tMax] — damit er nicht auf einer Sperre landet.
     */
    private fun aufSegment(p: Node, a: Node, b: Node, tMin: Double = 0.0, tMax: Double = 1.0): Node {
        val mProLat = 111_320.0
        val mProLon = 111_320.0 * kotlin.math.cos(Math.toRadians(p.toLatLon().lat))
        val ax = (a.lon - p.lon) / SNAP * mProLon
        val ay = (a.lat - p.lat) / SNAP * mProLat
        val bx = (b.lon - p.lon) / SNAP * mProLon
        val by = (b.lat - p.lat) / SNAP * mProLat
        val dx = bx - ax
        val dy = by - ay
        val l2 = dx * dx + dy * dy
        val t = if (l2 == 0.0) tMin else (-(ax * dx + ay * dy) / l2).coerceIn(tMin, tMax)
        val la = a.toLatLon()
        val lb = b.toLatLon()
        return Node.of(la.lat + (lb.lat - la.lat) * t, la.lon + (lb.lon - la.lon) * t)
    }

    /** Der nächste Knoten im Raster, höchstens [grenze] Meter entfernt. */
    private fun naechster(
        raster: Map<Long, MutableList<Node>>,
        p: Node,
        grenze: Double,
    ): Node? {
        val y = p.lat / RASTER
        val x = p.lon / RASTER
        var best: Node? = null
        var bestD = grenze
        for (dy in -1..1) {
            for (dx in -1..1) {
                for (n in raster[zelle(y + dy, x + dx)].orEmpty()) {
                    val d = distanceM(n.toLatLon(), p.toLatLon())
                    if (d < bestD) {
                        bestD = d
                        best = n
                    }
                }
            }
        }
        return best
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

    /**
     * So nah am Ziel darf ein Punkt liegen, damit unter mehreren der **günstigste**
     * gewählt wird statt des nächsten. Weiter draußen zählt wieder allein die Nähe: Wer
     * ein Ziel mitten im Feld setzt, will dorthin, so nah es eben geht.
     */
    private const val ZIEL_NAH_M = 150.0

    /** Wie schwer das letzte Stück Luftlinie dabei wiegt. Es ist geraten, nicht gefahren. */
    private const val LUFT_GEWICHT = 10.0

    /**
     * Der günstigste Weg von [start] zu einem Punkt, der nahe genug an [ziel] liegt.
     *
     * Das Ziel an den **nächstgelegenen** Punkt zu hängen war falsch, seit ein Wehr die
     * Wegsuche nicht mehr trennt: Liegt am anderen Ufer ein Punkt fünfzehn Meter näher,
     * fuhr die Strecke durch das Wehr hindurch, um ihn zu erreichen. Jetzt zählt, was der
     * Weg dorthin kostet, plus das Stück Luftlinie am Ende — und der Ausstieg diesseits
     * des Wehrs gewinnt.
     */
    private fun besterWeg(
        graph: Map<Node, List<Pair<Node, Double>>>,
        start: Node,
        ziel: LatLon,
        maxSnap: Double,
    ): List<Node>? {
        val (dist, prev) = dijkstra(graph, start)
        var best: Node? = null
        var bestKosten = Double.MAX_VALUE
        for ((n, d) in dist) {
            val luft = distanceM(n.toLatLon(), ziel)
            if (luft > ZIEL_NAH_M) continue
            val kosten = d + luft * LUFT_GEWICHT
            if (kosten < bestKosten) {
                bestKosten = kosten
                best = n
            }
        }
        return best?.let { zurueck(prev, it) }
    }

    private fun shortestPath(
        graph: Map<Node, List<Pair<Node, Double>>>,
        start: Node,
        goal: Node,
    ): List<Node>? {
        if (start == goal) return listOf(start)
        val (dist, prev) = dijkstra(graph, start, goal)
        if (goal !in dist) return null
        return zurueck(prev, goal)
    }

    /** Dijkstra von [start]; mit [goal] hört er auf, sobald der Punkt feststeht. */
    private fun dijkstra(
        graph: Map<Node, List<Pair<Node, Double>>>,
        start: Node,
        goal: Node? = null,
    ): Pair<Map<Node, Double>, Map<Node, Node>> {
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
        return dist to prev
    }

    /** Aus den Vorgängern den Weg zurück zum Anfang. */
    private fun zurueck(prev: Map<Node, Node>, bis: Node): List<Node> {
        val out = ArrayList<Node>()
        var cur: Node? = bis
        while (cur != null) {
            out.add(cur)
            cur = prev[cur]
        }
        return out.reversed()
    }
}
