package de.kewl.boatspeedy.nav

import android.content.Context
import de.kewl.boatspeedy.data.Craft
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Eine gespeicherte Route: die fertig gerechnete Strecke samt allem, was unterwegs zählt.
 *
 * Gespeichert wird das Ergebnis, nicht die Frage. So lässt sie sich später ohne Netz und
 * ohne Kacheln nachfahren, und sie sieht genauso aus wie beim Planen. Was sich seitdem in
 * OpenStreetMap geändert hat, holt „Neu berechnen".
 */
data class SavedRoute(
    val id: Long,
    val createdAt: Long,
    /** Selbst vergebener Name; ohne ihn zählen Datum und Uhrzeit. */
    val name: String? = null,
    /** Mit welchem Fahrzeug gerechnet wurde: Das Kanu trägt um, das Motorboot nicht. */
    val craft: Craft,
    val target: LatLon,
    /** Die ganze Strecke vom Start zum Ziel, samt Luftlinie an beiden Enden. */
    val path: List<LatLon>,
    /** Der Teil entlang des Fahrwassers. */
    val water: List<LatLon>,
    val obstacles: List<Obstacle> = emptyList(),
    val restrictedM: Double = 0.0,
    val restricted: List<List<LatLon>> = emptyList(),
    val upstreamM: Double = 0.0,
    val downstreamM: Double = 0.0,
    val portageM: Double = 0.0,
    val portage: List<List<LatLon>> = emptyList(),
) {
    val start: LatLon get() = path.first()
    val distanceM: Double get() = pathLengthM(path)

    /** Als Ziel zum Nachfahren: mit Anfahrt vom Boot zum Startpunkt. */
    fun alsZiel(): NavTarget = NavTarget(
        target = target,
        mode = NavMode.ROUTE,
        path = path,
        distanceM = distanceM,
        water = water,
        obstacles = obstacles,
        restrictedM = restrictedM,
        restricted = restricted,
        upstreamM = upstreamM,
        downstreamM = downstreamM,
        portageM = portageM,
        portage = portage,
        craft = craft,
        anfahrt = start,
        folge = 0,
    )

    companion object {
        /** Aus einer gerechneten Route; null, wenn es keine Route entlang des Wassers ist. */
        fun aus(t: NavTarget, craft: Craft, jetzt: Long, name: String?): SavedRoute? {
            if (t.mode != NavMode.ROUTE || t.path.size < 2) return null
            return SavedRoute(
                id = jetzt,
                createdAt = jetzt,
                name = name?.trim()?.takeIf { it.isNotEmpty() },
                craft = t.craft ?: craft,
                target = t.target,
                path = t.path,
                water = t.water,
                obstacles = t.obstacles,
                restrictedM = t.restrictedM,
                restricted = t.restricted,
                upstreamM = t.upstreamM,
                downstreamM = t.downstreamM,
                portageM = t.portageM,
                portage = t.portage,
            )
        }
    }
}

/** Liest und schreibt [SavedRoute] als JSON. Getrennt von der Ablage, damit es prüfbar ist. */
object RouteJson {

    fun encode(r: SavedRoute): String = JSONObject()
        .put("id", r.id)
        .put("createdAt", r.createdAt)
        .put("craft", r.craft.name)
        .put("target", punkte(listOf(r.target)))
        .put("path", punkte(r.path))
        .put("water", punkte(r.water))
        .put("obstacles", JSONArray().apply { r.obstacles.forEach { put(hindernis(it)) } })
        .put("restrictedM", r.restrictedM)
        .put("restricted", JSONArray().apply { r.restricted.forEach { put(punkte(it)) } })
        .put("upstreamM", r.upstreamM)
        .put("downstreamM", r.downstreamM)
        .put("portageM", r.portageM)
        .put("portage", JSONArray().apply { r.portage.forEach { put(punkte(it)) } })
        .apply { r.name?.let { put("name", it) } }
        .toString()

    fun decode(json: String): SavedRoute {
        val o = JSONObject(json)
        return SavedRoute(
            id = o.getLong("id"),
            createdAt = o.getLong("createdAt"),
            name = o.optString("name").takeIf { it.isNotBlank() },
            craft = runCatching { Craft.valueOf(o.getString("craft")) }.getOrDefault(Craft.MOTORBOAT),
            target = punkteAus(o.getJSONArray("target")).first(),
            path = punkteAus(o.getJSONArray("path")),
            water = punkteAus(o.getJSONArray("water")),
            obstacles = o.optJSONArray("obstacles")?.let { a ->
                (0 until a.length()).mapNotNull { runCatching { hindernisAus(a.getJSONObject(it)) }.getOrNull() }
            }.orEmpty(),
            restrictedM = o.optDouble("restrictedM", 0.0),
            restricted = linien(o.optJSONArray("restricted")),
            upstreamM = o.optDouble("upstreamM", 0.0),
            downstreamM = o.optDouble("downstreamM", 0.0),
            portageM = o.optDouble("portageM", 0.0),
            portage = linien(o.optJSONArray("portage")),
        )
    }

    /** Punkte flach als [lat, lon, lat, lon, …], auf sechs Stellen: rund zehn Zentimeter. */
    private fun punkte(p: List<LatLon>) = JSONArray().apply {
        p.forEach {
            put(Math.round(it.lat * 1e6) / 1e6)
            put(Math.round(it.lon * 1e6) / 1e6)
        }
    }

    private fun punkteAus(a: JSONArray): List<LatLon> =
        (0 until a.length() / 2).map { LatLon(a.getDouble(2 * it), a.getDouble(2 * it + 1)) }

    private fun linien(a: JSONArray?): List<List<LatLon>> =
        if (a == null) emptyList() else (0 until a.length()).map { punkteAus(a.getJSONArray(it)) }

    private fun hindernis(o: Obstacle) = JSONObject()
        .put("kind", o.kind.name)
        .put("lat", o.lat)
        .put("lon", o.lon)
        .apply {
            o.name?.let { put("name", it) }
            o.openingHours?.let { put("hours", it) }
            o.phone?.let { put("phone", it) }
            o.vhf?.let { put("vhf", it) }
            o.maxLengthM?.let { put("maxLength", it) }
            o.maxWidthM?.let { put("maxWidth", it) }
            o.clearanceHeightM?.let { put("clearanceHeight", it) }
            o.clearanceWidthM?.let { put("clearanceWidth", it) }
            o.cemt?.let { put("cemt", it) }
            o.landingKind?.let { put("landing", it.name) }
        }

    private fun hindernisAus(o: JSONObject): Obstacle {
        fun text(k: String) = o.optString(k).takeIf { it.isNotBlank() }
        return Obstacle(
            lat = o.getDouble("lat"),
            lon = o.getDouble("lon"),
            kind = ObstacleKind.valueOf(o.getString("kind")),
            name = text("name"),
            openingHours = text("hours"),
            phone = text("phone"),
            vhf = text("vhf"),
            maxLengthM = text("maxLength"),
            maxWidthM = text("maxWidth"),
            clearanceHeightM = text("clearanceHeight"),
            clearanceWidthM = text("clearanceWidth"),
            cemt = text("cemt"),
            landingKind = text("landing")?.let { k -> runCatching { LandingKind.valueOf(k) }.getOrNull() },
        )
    }
}

/**
 * Ablage der gespeicherten Routen, je Route eine JSON-Datei unter `filesDir/routes`,
 * wie bei den Fahrten.
 */
class RouteStore(context: Context) {

    private val dir = File(context.applicationContext.filesDir, "routes")

    suspend fun save(route: SavedRoute) = withContext(Dispatchers.IO) {
        dir.mkdirs()
        File(dir, "${route.id}.json").writeText(RouteJson.encode(route))
    }

    /** Alle Routen, neueste zuerst. */
    suspend fun list(): List<SavedRoute> = withContext(Dispatchers.IO) {
        (dir.listFiles { f -> f.extension == "json" } ?: emptyArray())
            .mapNotNull { runCatching { RouteJson.decode(it.readText()) }.getOrNull() }
            .sortedByDescending { it.createdAt }
    }

    suspend fun delete(ids: Set<Long>) = withContext(Dispatchers.IO) {
        ids.forEach { File(dir, "$it.json").delete() }
    }
}
