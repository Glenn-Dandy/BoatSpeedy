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
        // Interpoliert wird beim Aufbereiten (renderRadarWindow) – dort die Messwerte,
        // nicht die Farben. Was hier noch skaliert wird, sind bereits Zwischenwerte;
        // die Filterung macht daraus weiche Ränder statt Treppen.
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

/**
 * Das Gebiet, für das der DWD Radardaten liefert — etwa Deutschland mit Rand. **Fest**,
 * und das ist der Kern der Sache: solange die Bilder immer für dieselbe Fläche geholt
 * werden, ändert Zoomen und Schwenken nichts am Abruf. Vorher hing die Fläche am
 * sichtbaren Ausschnitt, jede Zoomstufe warf alle Frames weg und lud sie neu — deshalb
 * stand nach jedem Zoom sekundenlang das alte, hochgezogene Übersichtsbild auf der Karte.
 */
val RADAR_AREA_GER = MercatorBox(500_000.0, 5_850_000.0, 1_750_000.0, 7_450_000.0)

/**
 * Bildgröße für den Abruf des festen Gebiets: **ein Bildpunkt je Kilometer**, also genau
 * die Auflösung der Messdaten. Gemessen: 87 kB und 0,7 s je Frame.
 */
const val RADAR_AREA_W = 790
const val RADAR_AREA_H = 1010

/** Gemeinsame Fläche, oder `null` wenn sie sich nicht überschneiden. */
fun MercatorBox.intersect(other: MercatorBox): MercatorBox? {
    val x0 = maxOf(minX, other.minX)
    val y0 = maxOf(minY, other.minY)
    val x1 = minOf(maxX, other.maxX)
    val y1 = minOf(maxY, other.maxY)
    return if (x1 > x0 && y1 > y0) MercatorBox(x0, y0, x1, y1) else null
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
 * Kante des dargestellten Ausschnitts. Das Ergebnis wird beim Zeichnen noch auf die
 * Bildschirmgröße gezogen — mit Filterung, was die Farbgrenzen weich macht statt
 * treppig. Genau so sehen die Bilder der Wetter-Apps aus: weiche Umrisse, harte Stufen.
 */
const val RADAR_RENDER_EDGE = 768

/**
 * Weiter als das zu vergrößern bringt nichts: über die Messauflösung hinaus entsteht nur
 * größerer Weichzeichner, kostet aber Speicher in der ganzen Schleife.
 */
private const val MAX_ZOOM_FACTOR = 16.0

/**
 * Vergrößerungsfaktor für ein Fenster von [longestSpan] Rasterzellen. Ausgelagert, weil
 * genau hier der Fehler saß: vorher `640 / breite` als **ganzzahlige** Division, die ab
 * etwa 180 km Blickbreite 1 ergab — die Glättung lief dann, tat aber nichts.
 */
fun radarZoomFactor(longestSpan: Double, maxEdge: Int = RADAR_RENDER_EDGE): Double =
    if (longestSpan <= 0.0) 0.0 else (maxEdge / longestSpan).coerceAtMost(MAX_ZOOM_FACTOR)

/** Unterhalb dieser Vergrößerung lohnt das Aufbereiten nicht. */
const val RADAR_MIN_FACTOR = 1.2

/**
 * Rechnet aus dem Deutschlandbild den sichtbaren Ausschnitt heraus und vergrößert ihn
 * dabei — nicht das Bild wird weichgezeichnet, sondern die **Messwerte** werden zwischen
 * den Rasterzellen interpoliert und danach neu eingefärbt.
 *
 * Der Vergrößerungsfaktor war vorher eine **ganzzahlige** Division `640 / Bildbreite`.
 * Ab etwa 180 km Blickbreite kam dabei 1 heraus: die Glättung lief, tat aber nichts, und
 * das Rohraster wurde ungefiltert über den Bildschirm gezogen — die sichtbaren Würfel.
 * Hier ist der Faktor eine Fließkommazahl und richtet sich nach dem *Fenster*, nicht
 * nach der gesamten Fläche, deshalb ist die Vergrößerung bei jedem Zoom passend.
 *
 * Gibt `null` zurück, wenn im Fenster ohnehin ungefähr ein Bildpunkt je Rasterzelle
 * übrig bliebe — dann ist nichts zu gewinnen und der Aufrufer zeigt das Bild direkt.
 */
fun renderRadarWindow(
    src: Bitmap,
    srcBox: MercatorBox,
    window: MercatorBox,
    maxEdge: Int = RADAR_RENDER_EDGE,
): Bitmap? {
    val sw = src.width
    val sh = src.height
    if (sw < 2 || sh < 2) return null

    // Fenster in Quell-Bildpunkte umrechnen (Web-Mercator ist linear, also einfach).
    val pxPerX = sw / srcBox.width
    val pxPerY = sh / srcBox.height
    // Auf die Bildkanten begrenzt – ein Fenster, das über das Radargebiet hinausragt,
    // wird beschnitten. Genau dieselbe Beschneidung liefert renderedWindowBox als Fläche,
    // sonst säße das Bild versetzt auf der Karte.
    val fx0 = ((window.minX - srcBox.minX) * pxPerX).coerceIn(0.0, sw.toDouble())
    val fx1 = ((window.maxX - srcBox.minX) * pxPerX).coerceIn(0.0, sw.toDouble())
    // Bildzeilen laufen von oben (maxY) nach unten.
    val fy0 = ((srcBox.maxY - window.maxY) * pxPerY).coerceIn(0.0, sh.toDouble())
    val fy1 = ((srcBox.maxY - window.minY) * pxPerY).coerceIn(0.0, sh.toDouble())
    val spanX = fx1 - fx0
    val spanY = fy1 - fy0
    if (spanX < 1.0 || spanY < 1.0) return null

    val longest = kotlin.math.max(spanX, spanY)
    val factor = radarZoomFactor(longest, maxEdge)
    // Bei kleiner Vergrößerung lohnt der Aufwand nicht: das Quellbild hat dann schon
    // ungefähr Bildschirmauflösung und wird beim Zeichnen ohnehin gefiltert.
    if (factor < RADAR_MIN_FACTOR) return null
    val ow = kotlin.math.round(spanX * factor).toInt().coerceIn(1, 4096)
    val oh = kotlin.math.round(spanY * factor).toInt().coerceIn(1, 4096)

    val srcPx = IntArray(sw * sh)
    src.getPixels(srcPx, 0, sw, 0, 0, sw, sh)
    val values = FloatArray(sw * sh) { classOf(srcPx[it]) }

    val out = IntArray(ow * oh)
    for (y in 0 until oh) {
        val sy = fy0 + (y + 0.5) / factor - 0.5
        val y0 = kotlin.math.floor(sy).toInt().coerceIn(0, sh - 1)
        val y1 = (y0 + 1).coerceAtMost(sh - 1)
        val wy = (sy - y0).coerceIn(0.0, 1.0).toFloat()
        val row = y * ow
        for (x in 0 until ow) {
            val sx = fx0 + (x + 0.5) / factor - 0.5
            val x0 = kotlin.math.floor(sx).toInt().coerceIn(0, sw - 1)
            val x1 = (x0 + 1).coerceAtMost(sw - 1)
            val wx = (sx - x0).coerceIn(0.0, 1.0).toFloat()
            val v00 = values[y0 * sw + x0]; val v10 = values[y0 * sw + x1]
            val v01 = values[y1 * sw + x0]; val v11 = values[y1 * sw + x1]
            val top = v00 + (v10 - v00) * wx
            val bot = v01 + (v11 - v01) * wx
            out[row + x] = classColor(top + (bot - top) * wy)
        }
    }
    return Bitmap.createBitmap(out, ow, oh, Bitmap.Config.ARGB_8888)
}

/** Die Fläche, die [renderRadarWindow] für dieses Fenster tatsächlich abdeckt. */
fun renderedWindowBox(srcBox: MercatorBox, window: MercatorBox): MercatorBox =
    window.intersect(srcBox) ?: srcBox

/* ----------------------- Plattenspeicher für die Frames ----------------------- */

/**
 * Zwischenspeicher auf der Platte, damit Bildschirm verlassen und zurückkommen nicht die
 * ganze Schleife noch einmal kostet — über Mobilfunk wurde sie vorher nie fertig.
 *
 * **Kurz gültig, und das mit Absicht.** Die Schleife zeigt eine Vorhersage von jetzt bis
 * +100 Minuten, und der DWD rechnet sie alle fünf Minuten neu: das Bild für 15:00 Uhr
 * sieht anders aus, je nachdem ob es um 14:00 oder um 14:30 berechnet wurde. Nur der
 * Zeitstempel als Schlüssel würde deshalb alte Vorhersagen ausliefern. Deshalb gilt ein
 * Eintrag nur, solange derselbe Vorhersagelauf aktuell ist.
 */
const val RADAR_CACHE_MS = 5 * 60_000L
fun radarCacheDir(context: android.content.Context): java.io.File =
    java.io.File(context.cacheDir, "radar").apply { mkdirs() }

private fun cacheName(layer: String, timeIso: String?): String =
    (layer + "_" + (timeIso ?: "now")).replace(Regex("[^A-Za-z0-9_.-]"), "_") + ".png"

fun readCachedRadar(
    dir: java.io.File,
    layer: String,
    timeIso: String?,
    maxAgeMs: Long = RADAR_CACHE_MS,
): ByteArray? = runCatching {
    java.io.File(dir, cacheName(layer, timeIso))
        .takeIf { it.isFile && System.currentTimeMillis() - it.lastModified() < maxAgeMs }
        ?.readBytes()
}.getOrNull()

fun writeCachedRadar(dir: java.io.File, layer: String, timeIso: String?, png: ByteArray) {
    runCatching { java.io.File(dir, cacheName(layer, timeIso)).writeBytes(png) }
}

/**
 * Wirft weg, was älter als [maxAgeMs] ist. Das Vorhersageraster wandert alle fünf Minuten
 * weiter; ohne Aufräumen sammelt sich der Speicher voll mit Bildern, die kein Abruf mehr
 * annimmt.
 */
fun pruneRadarCache(dir: java.io.File, maxAgeMs: Long = 30 * 60_000L) {
    runCatching {
        val cutoff = System.currentTimeMillis() - maxAgeMs
        dir.listFiles()?.forEach { if (it.isFile && it.lastModified() < cutoff) it.delete() }
    }
}
