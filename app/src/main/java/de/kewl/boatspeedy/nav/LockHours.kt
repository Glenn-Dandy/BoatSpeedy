package de.kewl.boatspeedy.nav

import org.json.JSONObject
import java.io.File

/** Eine Zeile der Tabelle: Zeitraum und je Wochentag, Montag zuerst, was ELWIS dort schreibt. */
data class LockHoursRow(val period: String, val days: List<String>)

/** Die regulären Betriebszeiten einer Schleuse oder Kammer laut ELWIS. */
data class LockHours(
    val id: String,
    val name: String,
    val fairway: String?,
    val points: List<LatLon>,
    val rows: List<LockHoursRow>,
    val remarks: String?,
)

/**
 * Betriebszeiten der Schleusen von ELWIS, über den Kachelserver.
 *
 * ELWIS hat dafür nur eine Seite je Schleuse. Der Kachelserver liest sie einmal am Tag,
 * verortet die Schleusen über die OSM-Daten und legt alles als `locks.json` ab
 * (boatspeedy-mapdata, `locks/locks.py`). Nur Bundeswasserstraßen.
 *
 * Die Zellen bleiben, wie ELWIS sie schreibt. So verlangen es die Nutzungsbedingungen,
 * und eine Uhrzeit, die beim Umformen verrutscht, wäre schlimmer als gar keine.
 */
object LockHoursSource {

    private const val FILE = "locks.json"

    /** Der Server holt einmal am Tag. Öfter nachzufragen bringt nichts. */
    private const val FRESH_MS = 12 * 3600_000L

    /** Liest die Zeiten, holt sie neu, wenn die gespeicherte Fassung zu alt ist. */
    fun load(cacheDir: File, nowMs: Long = System.currentTimeMillis()): List<LockHours> {
        val f = File(cacheDir, FILE)
        if (!f.exists() || nowMs - f.lastModified() > FRESH_MS) {
            MapTiles.fetchFile(FILE)?.let { bytes ->
                if (runCatching { parse(String(bytes)) }.isSuccess) {
                    val tmp = File(cacheDir, "$FILE.tmp")
                    tmp.writeBytes(bytes)
                    tmp.renameTo(f)
                }
            }
        }
        return if (f.exists()) runCatching { parse(f.readText()) }.getOrDefault(emptyList()) else emptyList()
    }

    fun parse(json: String): List<LockHours> {
        val arr = JSONObject(json).optJSONArray("locks") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.getJSONObject(i)
            val pts = o.optJSONArray("points") ?: return@mapNotNull null
            val points = (0 until pts.length()).map { j ->
                val p = pts.getJSONArray(j)
                LatLon(p.getDouble(0), p.getDouble(1))
            }
            val rowsArr = o.optJSONArray("rows") ?: return@mapNotNull null
            val rows = (0 until rowsArr.length()).mapNotNull { j ->
                val r = rowsArr.getJSONObject(j)
                val d = r.optJSONArray("days") ?: return@mapNotNull null
                if (d.length() != 7) return@mapNotNull null
                LockHoursRow(r.optString("period"), (0 until 7).map { d.optString(it) })
            }
            if (points.isEmpty() || rows.isEmpty()) return@mapNotNull null
            LockHours(
                id = o.optString("id"),
                name = o.optString("name"),
                fairway = o.optString("fairway").ifBlank { null },
                points = points,
                rows = rows,
                remarks = o.optString("remarks").ifBlank { null },
            )
        }
    }

    /**
     * Die Einträge zu einer Schleuse auf der Karte: alles, was höchstens
     * [WaterNotices.LOCK_RADIUS_M] von ihrer Kammer entfernt liegt, sonst vom Symbol.
     */
    fun atLock(all: List<LockHours>, lat: Double, lon: Double, line: List<LatLon> = emptyList()): List<LockHours> {
        val ziel = line.ifEmpty { listOf(LatLon(lat, lon)) }
        return all.filter { h ->
            h.points.any { WaterNotices.distanceToPathM(it, ziel) <= WaterNotices.LOCK_RADIUS_M }
        }
    }

    /**
     * Fasst Kammern mit gleichen Zeiten zusammen. Meist gelten an einer Schleuse für alle
     * Kammern dieselben, dann soll die Tabelle nicht dreimal dastehen. Die Namen bleiben,
     * damit man sieht, wofür eine abweichende Tabelle gilt.
     */
    fun grouped(entries: List<LockHours>): List<Pair<List<String>, LockHours>> =
        entries.groupBy { it.rows to it.remarks }.values.map { g -> names(g.map { it.name }) to g.first() }

    /**
     * ELWIS führt oft die Schleuse selbst und dazu jede Kammer. Gilt für alle dasselbe,
     * reicht der Name der Schleuse: „Kostheim" statt fünfmal „Kostheim Nordschleuse …".
     */
    private fun names(all: List<String>): List<String> {
        val dach = all.minByOrNull { it.length } ?: return all
        return if (all.all { it == dach || it.startsWith("$dach ") }) listOf(dach) else all
    }

    /**
     * Gleiche Zellen nebeneinander zusammen: aus sieben mal „06:00 - 20:00" wird ein
     * Bereich. Gibt Paare (erster Tag, letzter Tag, Inhalt), Tage von 0 (Montag) bis 6.
     */
    fun dayRuns(days: List<String>): List<Triple<Int, Int, String>> {
        val out = ArrayList<Triple<Int, Int, String>>()
        var start = 0
        for (i in 1..days.size) {
            if (i == days.size || days[i] != days[start]) {
                out.add(Triple(start, i - 1, days[start]))
                start = i
            }
        }
        return out
    }
}
