package de.kewl.boatspeedy.ui

import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.util.MapTileIndex
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** DWD-Radar-Layer (offener GeoServer, CC BY 4.0). Achtung: exakter (groß geschriebener)
 *  Layer-Name — der kleingeschriebene existiert nicht („LayerNotDefined"). */
const val DWD_RADAR_LAYER = "Radar_rv_product_1x1km_ger" // RADOLAN-RV: −1 h … +2 h, PT5M
const val DWD_LIGHTNING_LAYER = "Blitzdichte" // nur Ist-Zeit (keine Zukunft)

/** Ein Radar-Frame: Zeitpunkt für den WMS, Versatz zu jetzt und die Uhrzeit zum Ablesen. */
data class RadarFrame(val timeIso: String, val label: String, val clock: String)

/**
 * osmdroid-Kachelquelle, die pro XYZ-Kachel eine DWD-WMS-GetMap-Anfrage baut
 * (EPSG:3857 = die Projektion der OSM-Karte). [time] = ISO-8601 (UTC) oder null = aktuell.
 * Der Zeitwert steckt im Quellennamen, damit osmdroid die Frames getrennt cacht.
 */
class DwdWmsTileSource(
    private val layer: String,
    private val time: String?,
) : OnlineTileSourceBase(
    "dwd_${layer}_${time ?: "current"}", 3, 14, 256, ".png",
    arrayOf("https://maps.dwd.de/geoserver/dwd/wms"),
) {
    override fun getTileURLString(pMapTileIndex: Long): String {
        val z = MapTileIndex.getZoom(pMapTileIndex)
        val x = MapTileIndex.getX(pMapTileIndex)
        val y = MapTileIndex.getY(pMapTileIndex)
        val tile = (2.0 * ORIGIN) / (1 shl z)
        val minX = -ORIGIN + x * tile
        val maxX = -ORIGIN + (x + 1) * tile
        val maxY = ORIGIN - y * tile
        val minY = ORIGIN - (y + 1) * tile
        val sb = StringBuilder(baseUrl)
            .append("?SERVICE=WMS&VERSION=1.3.0&REQUEST=GetMap")
            .append("&LAYERS=").append(layer)
            .append("&STYLES=&FORMAT=image/png&TRANSPARENT=TRUE")
            .append("&CRS=EPSG:3857&WIDTH=256&HEIGHT=256")
            .append("&BBOX=").append("$minX,$minY,$maxX,$maxY")
        if (time != null) sb.append("&TIME=").append(time)
        return sb.toString()
    }

    private companion object {
        const val ORIGIN = 20037508.342789244 // halbe Web-Mercator-Weltbreite
    }
}

/** Abstand der Frames: das Raster der RV-Vorhersage (PT5M). */
const val RADAR_STEP_MIN = 5

/** So weit reicht ein Vorhersagelauf: zwei Stunden ab seiner Bezugszeit. */
const val RADAR_RANGE_MIN = 120

private const val STEP_MS = RADAR_STEP_MIN * 60_000L

/** Zeitpunkt in der Schreibweise des WMS, in UTC. */
fun radarIso(ms: Long): String =
    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }.format(Date(ms))

/**
 * Mögliche Bezugszeiten des neuesten Laufs, neueste zuerst: die Uhrzeit auf fünf Minuten
 * abgerundet und von dort eine halbe Stunde zurück. Ein Lauf erscheint beim DWD einige
 * Minuten nach seiner Bezugszeit, der abgerundete Zeitpunkt selbst ist meist noch nicht da.
 */
fun radarRunCandidates(nowMs: Long, count: Int = 7): List<Long> {
    val base = nowMs - (nowMs % STEP_MS)
    return (0 until count).map { base - it * STEP_MS }
}

/**
 * Die Frames eines Vorhersagelaufs: die Messung zu seiner Bezugszeit, danach die
 * Vorhersage in Fünf-Minuten-Schritten bis +2 h.
 *
 * Vorher entstand die Liste aus der Uhrzeit, auf fünf Minuten abgerundet. Der neueste Lauf
 * ist aber meist einen Schritt älter, und dann gab es den letzten Frame bei ihm noch gar
 * nicht: +120 min blieb leer und fiel deshalb weg. Gerechnet ab dem Lauf selbst ist jeder
 * Frame vorhanden.
 */
fun radarFramesFor(refMs: Long): List<RadarFrame> {
    // Ortszeit zum Ablesen – ein „+30 min" allein sagt nichts, wenn die Liste älter ist
    // als der Blick auf den Bildschirm.
    val clockFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
    return (0..RADAR_RANGE_MIN / RADAR_STEP_MIN).map { i ->
        val offMin = i * RADAR_STEP_MIN
        val at = refMs + offMin * 60_000L
        RadarFrame(
            timeIso = radarIso(at),
            label = if (offMin == 0) "" else "+$offMin min",
            clock = clockFmt.format(Date(at)),
        )
    }
}
