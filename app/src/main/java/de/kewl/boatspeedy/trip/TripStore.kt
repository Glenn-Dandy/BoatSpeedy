package de.kewl.boatspeedy.trip

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Dateibasierte Ablage abgeschlossener Fahrten – je Fahrt eine JSON-Datei unter
 * `filesDir/trips/{id}.json` (inkl. Track-Punkte). Bewusst ohne Datenbank; für die
 * überschaubare Zahl an Fahrten völlig ausreichend.
 */
class TripStore(context: Context) {

    private val dir = File(context.applicationContext.filesDir, "trips")

    suspend fun save(trip: SavedTrip) = withContext(Dispatchers.IO) {
        dir.mkdirs()
        File(dir, "${trip.id}.json").writeText(encode(trip))
    }

    /** Alle Fahrten, neueste zuerst. */
    suspend fun list(): List<SavedTrip> = withContext(Dispatchers.IO) {
        (dir.listFiles { f -> f.extension == "json" } ?: emptyArray())
            .mapNotNull { runCatching { decode(it.readText()) }.getOrNull() }
            .sortedByDescending { it.startedAt }
    }

    suspend fun get(id: Long): SavedTrip? = withContext(Dispatchers.IO) {
        val f = File(dir, "$id.json")
        if (f.exists()) runCatching { decode(f.readText()) }.getOrNull() else null
    }

    suspend fun delete(ids: Set<Long>) = withContext(Dispatchers.IO) {
        ids.forEach { File(dir, "$it.json").delete() }
    }

    private fun encode(t: SavedTrip): String {
        val pts = JSONArray()
        for (p in t.points) pts.put(JSONArray().put(p.lat).put(p.lon).put(p.tMs))
        return JSONObject()
            .put("id", t.id)
            .put("startedAt", t.startedAt)
            .put("distanceM", t.distanceM)
            .put("durationMs", t.durationMs)
            .put("avg", t.avgSpeedMs.toDouble())
            .put("max", t.maxSpeedMs.toDouble())
            .put("energyWh", t.energyWh.toDouble())
            .put("chargeAh", t.chargeAh.toDouble())
            .put("points", pts)
            .toString()
    }

    private fun decode(json: String): SavedTrip {
        val o = JSONObject(json)
        val ptsArr = o.optJSONArray("points") ?: JSONArray()
        val pts = ArrayList<TrackPoint>(ptsArr.length())
        for (i in 0 until ptsArr.length()) {
            val a = ptsArr.getJSONArray(i)
            pts.add(TrackPoint(a.getDouble(0), a.getDouble(1), a.getLong(2)))
        }
        return SavedTrip(
            id = o.getLong("id"),
            startedAt = o.getLong("startedAt"),
            distanceM = o.getDouble("distanceM"),
            durationMs = o.getLong("durationMs"),
            avgSpeedMs = o.getDouble("avg").toFloat(),
            maxSpeedMs = o.getDouble("max").toFloat(),
            energyWh = o.getDouble("energyWh").toFloat(),
            chargeAh = o.getDouble("chargeAh").toFloat(),
            points = pts,
        )
    }
}
