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
        // Geglättet wird bereits beim Aufbereiten (smoothRadar) – dort werden die
        // Messwerte interpoliert, nicht die Farben. Was hier noch skaliert wird, sind
        // schon Zwischenwerte, deshalb ist die Filterung an dieser Stelle richtig.
        isFilterBitmap = true
        isAntiAlias = true
    }
    private val nw = Point()
    private val se = Point()
    private val dst = Rect()

    /**
     * Das aktuell gezeigte Bild samt der Fläche, die es abdeckt. Beides gehört zusammen
     * und wird nur gemeinsam gesetzt — ein Bild auf einer anderen Fläche sitzt falsch auf
     * der Karte und wandert beim Schwenken sichtbar mit dem Finger statt mit dem Boden.
     */
    var image: Bitmap? = null
        private set
    var area: BoundingBox? = null
        private set

    fun setImage(bitmap: Bitmap?, box: BoundingBox?) {
        image = bitmap
        area = box
    }

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

/**
 * Beschneidet den Ausschnitt auf die Welt. Beim starken Herauszoomen wächst der Rand aus
 * [expand] über die Ränder von Web-Mercator hinaus; der WMS bekommt dann ein Rechteck,
 * das es nicht gibt, und liefert ein Bild, das nicht mehr zur Fläche passt — das Overlay
 * saß verzerrt auf der Karte.
 */
fun MercatorBox.clampToWorld(): MercatorBox = MercatorBox(
    minX.coerceIn(-MERCATOR_ORIGIN, MERCATOR_ORIGIN),
    minY.coerceIn(-MERCATOR_ORIGIN, MERCATOR_ORIGIN),
    maxX.coerceIn(-MERCATOR_ORIGIN, MERCATOR_ORIGIN),
    maxY.coerceIn(-MERCATOR_ORIGIN, MERCATOR_ORIGIN),
)

val MercatorBox.width: Double get() = maxX - minX
val MercatorBox.height: Double get() = maxY - minY

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

/**
 * Die Farbleiter des DWD-Stils, von schwach nach stark. Aus dem SLD des Layers
 * (`REQUEST=GetStyles`) übernommen, damit die Zuordnung stimmt und nicht geraten ist:
 * 0.1–0.2 mm/h bis ≥ 150 mm/h.
 */
private val RADAR_RAMP = intArrayOf(
    0x33FFFF, 0x1ACC9A, 0x019934, 0x4DB31B, 0x99CC01, 0xCCE601, 0xFFFF01, 0xFFC401,
    0xFF8901, 0xFF4501, 0xFE0000, 0xE5004C, 0xCC0098, 0x6600CB, 0x0000FE,
)

/** Ordnet eine Bildfarbe ihrer Klasse zu: 0 = kein Regen, 1..15 = die Stufen der Leiter. */
private fun classOf(pixel: Int): Float {
    if ((pixel ushr 24) < 128) return 0f            // durchsichtig = kein Niederschlag
    val rgb = pixel and 0xFFFFFF
    for (i in RADAR_RAMP.indices) if (RADAR_RAMP[i] == rgb) return (i + 1).toFloat()
    // Unbekannt (z. B. das Grau für „keine Daten") – wie kein Regen behandeln.
    return 0f
}

/**
 * Farbe für einen interpolierten Wert — gerundet auf die **nächste Stufe** der Leiter,
 * nicht dazwischen gemischt.
 *
 * Das ist der Punkt, an dem ein erster Versuch danebenlag: mischt man die Farben
 * stufenlos, verschwimmt alles zu Farbnebel. Die DWD-App macht es anders, und man sieht
 * es ihrem Bild an — die Farbflächen haben **weiche Umrisse, aber harte Grenzen**.
 * Interpoliert werden also die Messwerte, die Einfärbung bleibt in ihren fünfzehn Stufen.
 */
private fun classColor(v: Float): Int {
    val i = kotlin.math.round(v).toInt()
    if (i <= 0) return 0
    return (0xFF shl 24) or RADAR_RAMP[(i - 1).coerceAtMost(RADAR_RAMP.size - 1)]
}

/**
 * Glättet das Radarbild so, wie es die DWD-App zeigt: nicht das Bild wird weichgezeichnet,
 * sondern die **Messwerte** werden zwischen den Rasterzellen interpoliert und danach neu
 * eingefärbt. Ein Weichzeichner über das fertige Bild verwischt nur die Farben und macht
 * Matsch; hier entstehen echte Zwischenwerte, deshalb bleiben Kerne und Ränder erkennbar.
 *
 * Erwartet [src] in der Auflösung der Messdaten (ein Bildpunkt ≈ ein Kilometer).
 */
fun smoothRadar(src: Bitmap, maxEdge: Int): Bitmap {
    val sw = src.width
    val sh = src.height
    // Entscheidend ist die **absolute** Ausgabegröße, nicht der Vergrößerungsfaktor.
    // Mit festem Faktor wächst das Bild mit dem Ausschnitt: bei hundert Kilometern Blick
    // wären es acht Megabyte je Frame und über hundertsiebzig für die ganze Schleife.
    // Der Zwischenspeicher läuft dann über, jeder Wechsel rechnet neu — und es ruckelt.
    // Diese Kante reicht für den Bildschirm; den Rest erledigt die Skalierung beim
    // Zeichnen, die ohnehin nur noch leicht glättet.
    val longest = kotlin.math.max(sw, sh).coerceAtLeast(1)
    val factor = (maxEdge / longest).coerceIn(1, 12)
    val ow = (sw * factor).coerceAtLeast(1)
    val oh = (sh * factor).coerceAtLeast(1)
    val srcPx = IntArray(sw * sh)
    src.getPixels(srcPx, 0, sw, 0, 0, sw, sh)
    val values = FloatArray(sw * sh) { classOf(srcPx[it]) }

    val out = IntArray(ow * oh)
    for (y in 0 until oh) {
        val fy = ((y + 0.5f) / factor) - 0.5f
        val y0 = kotlin.math.floor(fy).toInt().coerceIn(0, sh - 1)
        val y1 = (y0 + 1).coerceAtMost(sh - 1)
        val wy = (fy - y0).coerceIn(0f, 1f)
        for (x in 0 until ow) {
            val fx = ((x + 0.5f) / factor) - 0.5f
            val x0 = kotlin.math.floor(fx).toInt().coerceIn(0, sw - 1)
            val x1 = (x0 + 1).coerceAtMost(sw - 1)
            val wx = (fx - x0).coerceIn(0f, 1f)
            val v00 = values[y0 * sw + x0]; val v10 = values[y0 * sw + x1]
            val v01 = values[y1 * sw + x0]; val v11 = values[y1 * sw + x1]
            val top = v00 + (v10 - v00) * wx
            val bot = v01 + (v11 - v01) * wx
            out[y * ow + x] = classColor(top + (bot - top) * wy)
        }
    }
    return Bitmap.createBitmap(out, ow, oh, Bitmap.Config.ARGB_8888)
}
