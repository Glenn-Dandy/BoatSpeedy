package de.kewl.boatspeedy.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Point
import android.graphics.Rect
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.Projection
import org.osmdroid.views.overlay.Overlay
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.tan

/**
 * Zeichnet **ein** Radarbild für einen festen Ausschnitt, statt es aus Kacheln
 * zusammenzusetzen.
 *
 * Hintergrund: eine WMS-Anfrage an den DWD kostet 2–4 s, und zwar fast unabhängig von
 * der angeforderten Fläche — eine Kachel 256×256 dauert so lange wie ein Bild über
 * zwanzig Kachelflächen. Über Kacheln geladen braucht ein einzelner Frame darum rund
 * zwanzig Anfragen, alle 21 Frames über 400; die Warteschlange lief über, Anfragen
 * wurden verworfen und der sichtbare Frame blieb minutenlang leer.
 *
 * Web-Mercator ist in der Projektion linear, die Karte wird nicht gedreht — deshalb
 * genügt es, die beiden Eckpunkte auf den Bildschirm zu rechnen und das Bild in das
 * entstehende Rechteck zu zeichnen.
 */
class RadarImageOverlay : Overlay() {

    private val paint = Paint().apply {
        // Bewusst ungeglättet: die Daten liegen im 1-km-Raster, und beim starken Zoom
        // sind das nur noch wenige Zellen im Bild. Weichgezeichnet verschwimmen sie zu
        // Farbnebel, in dem sich nicht mehr erkennen lässt, ob es regnet. Harte Kanten
        // zeigen wenigstens ehrlich, wie grob die Messung ist.
        isFilterBitmap = false
        isAntiAlias = false
    }
    private val nw = Point()
    private val se = Point()
    private val dst = Rect()

    /** Das aktuell gezeigte Bild samt der Fläche, die es abdeckt. */
    var image: Bitmap? = null
    var area: BoundingBox? = null

    override fun draw(canvas: Canvas, projection: Projection) {
        val bmp = image ?: return
        val box = area ?: return
        if (bmp.isRecycled) return
        projection.toPixels(GeoPoint(box.latNorth, box.lonWest), nw)
        projection.toPixels(GeoPoint(box.latSouth, box.lonEast), se)
        if (se.x <= nw.x || se.y <= nw.y) return
        dst.set(nw.x, nw.y, se.x, se.y)
        canvas.drawBitmap(bmp, null, dst, paint)
    }
}

/** Rechteck in Web-Mercator-Metern (so verlangt es der WMS mit CRS=EPSG:3857). */
data class MercatorBox(val minX: Double, val minY: Double, val maxX: Double, val maxY: Double)

private const val MERCATOR_ORIGIN = 20037508.342789244

fun BoundingBox.toMercator(): MercatorBox {
    fun x(lon: Double) = lon / 180.0 * MERCATOR_ORIGIN
    fun y(lat: Double): Double {
        val r = lat.coerceIn(-85.05, 85.05) * PI / 180.0
        return ln(tan(PI / 4 + r / 2)) / PI * MERCATOR_ORIGIN
    }
    return MercatorBox(x(lonWest), y(latSouth), x(lonEast), y(latNorth))
}

/** Umgekehrt – für das Overlay, das in Grad rechnet. */
fun MercatorBox.toBoundingBox(): BoundingBox {
    fun lon(x: Double) = x / MERCATOR_ORIGIN * 180.0
    fun lat(y: Double): Double {
        val t = y / MERCATOR_ORIGIN * PI
        return (2.0 * atan(exp(t)) - PI / 2) * 180.0 / PI
    }
    return BoundingBox(lat(maxY), lon(maxX), lat(minY), lon(minX))
}

/**
 * Vergrößert den Ausschnitt um [factor] (1.0 = unverändert). Der Abruf holt bewusst
 * mehr als sichtbar ist, damit kleine Schwenks kein Nachladen auslösen.
 */
fun MercatorBox.expand(factor: Double): MercatorBox {
    val cx = (minX + maxX) / 2
    val cy = (minY + maxY) / 2
    val w = (maxX - minX) / 2 * factor
    val h = (maxY - minY) / 2 * factor
    return MercatorBox(cx - w, cy - h, cx + w, cy + h)
}

fun MercatorBox.contains(other: MercatorBox): Boolean =
    minX <= other.minX && minY <= other.minY && maxX >= other.maxX && maxY >= other.maxY

/**
 * Holt ein Radarbild als PNG-Bytes. Absichtlich nur die Rohbytes: 21 Frames als
 * entpackte Bitmaps wären zweistellige Megabyte, komprimiert sind es einige hundert
 * Kilobyte. Dekodiert wird erst der Frame, der gezeigt wird.
 */
fun fetchRadarPng(layer: String, timeIso: String?, box: MercatorBox, width: Int, height: Int): ByteArray? {
    val url = buildString {
        append("https://maps.dwd.de/geoserver/dwd/wms")
        append("?SERVICE=WMS&VERSION=1.3.0&REQUEST=GetMap")
        append("&LAYERS=").append(layer)
        append("&STYLES=&FORMAT=image/png&TRANSPARENT=TRUE")
        append("&CRS=EPSG:3857&WIDTH=").append(width).append("&HEIGHT=").append(height)
        append("&BBOX=").append(box.minX).append(',').append(box.minY)
            .append(',').append(box.maxX).append(',').append(box.maxY)
        if (timeIso != null) append("&TIME=").append(timeIso)
    }
    return runCatching {
        val c = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 45_000
            setRequestProperty("User-Agent", "BoatSpeedy")
        }
        try {
            // Bei einer Zeit außerhalb des verfügbaren Bereichs antwortet der DWD mit
            // einer XML-Fehlermeldung statt mit einem Bild – die wollen wir nicht malen.
            if (c.responseCode != 200) return@runCatching null
            if (c.contentType?.contains("image") != true) return@runCatching null
            c.inputStream.use { it.readBytes() }
        } finally {
            c.disconnect()
        }
    }.getOrNull()
}

fun decodeRadar(png: ByteArray): Bitmap? =
    runCatching { BitmapFactory.decodeByteArray(png, 0, png.size) }.getOrNull()
