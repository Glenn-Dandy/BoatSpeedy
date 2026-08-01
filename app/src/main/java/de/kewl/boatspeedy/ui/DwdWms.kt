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

/** Ein Radar-Frame (Zeitpunkt für WMS + Anzeige-Label). */
data class RadarFrame(val timeIso: String, val label: String)

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

/**
 * Frames von jetzt bis +90 Min in 15-Min-Schritten (RV-Nowcast, auf 5 Min gerundet).
 * +120 wird bewusst weggelassen – am Vorhersage-Rand liefert der DWD teils kein Bild.
 */
fun radarFrames(): List<RadarFrame> {
    val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    val now = System.currentTimeMillis()
    val base = now - (now % (5 * 60_000L)) // auf 5 Min abrunden
    return (0..6).map { i ->
        val offMin = i * 15
        RadarFrame(
            timeIso = fmt.format(Date(base + offMin * 60_000L)),
            label = if (offMin == 0) "" else "+$offMin min",
        )
    }
}
