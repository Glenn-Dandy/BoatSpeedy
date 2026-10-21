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
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.PI
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
 *
 * @param alpha Deckkraft beim Zeichnen, 0..255.
 */
class RadarImageOverlay(alpha: Int = 255) : Overlay() {

    private val paint = Paint().apply {
        // Interpoliert wird beim Aufbereiten (renderRadarWindow) – dort die Messwerte,
        // nicht die Farben. Was hier noch skaliert wird, sind bereits Zwischenwerte;
        // die Filterung macht daraus weiche Ränder statt Treppen.
        isFilterBitmap = true
        isAntiAlias = true
        this.alpha = alpha
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

/**
 * Deckkraft des Regens über der Karte. Ganz deckend verschwanden Orte, Straßen und Flüsse
 * unter der Farbe, und auf dem Wasser ist genau das die Frage: regnet es **hier**? Bei drei
 * Vierteln bleiben sie lesbar, und die Farbstufen sind noch klar zu unterscheiden.
 */
const val RADAR_ALPHA = 190

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

private const val DWD_WMS = "https://maps.dwd.de/geoserver/dwd/wms"

/**
 * Holt ein Radarbild als PNG-Bytes, oder `null`.
 *
 * [refIso] hält den **Vorhersagelauf** fest. Ohne ihn nimmt der DWD für jede Zeit den
 * jeweils neuesten Lauf; kam während des Ladens ein neuer heraus, stammten die Frames einer
 * Schleife aus zwei Läufen, und der Regen sprang beim Abspielen. Der Parameter heißt beim
 * GeoServer `DIM_REFERENCE_TIME`: Eigene Dimensionen tragen dort ein `DIM_` davor, ein
 * bloßes `REFERENCE_TIME` wird stillschweigend übergangen.
 *
 * Die Verbindung wird nicht getrennt, nur der Datenstrom zu Ende gelesen. So bleibt sie
 * für den nächsten Frame offen, statt dass jeder einzeln die TLS-Verbindung neu aufbaut.
 */
fun fetchRadarPng(
    layer: String,
    timeIso: String?,
    box: MercatorBox,
    width: Int,
    height: Int,
    refIso: String? = null,
): ByteArray? = runCatching {
    val c = (URL(radarUrl(layer, timeIso, box, width, height, refIso)).openConnection() as HttpURLConnection).apply {
        connectTimeout = 15_000
        readTimeout = 45_000
        setRequestProperty("User-Agent", "BoatSpeedy")
    }
    try {
        val code = c.responseCode
        // Bei einer Zeit außerhalb des verfügbaren Bereichs antwortet der DWD mit
        // einer XML-Fehlermeldung statt mit einem Bild – die wollen wir nicht malen.
        if (code != 200 || c.contentType?.contains("image") != true) {
            (if (code >= 400) c.errorStream else c.inputStream)?.use { it.readBytes() }
            return@runCatching null
        }
        c.inputStream.use { it.readBytes() }
    } catch (e: IOException) {
        c.disconnect()
        throw e
    }
}.getOrNull()

private fun radarUrl(
    layer: String,
    timeIso: String?,
    box: MercatorBox,
    width: Int,
    height: Int,
    refIso: String?,
): String = buildString {
    append(DWD_WMS)
    append("?SERVICE=WMS&VERSION=1.3.0&REQUEST=GetMap")
    append("&LAYERS=").append(layer)
    append("&STYLES=&FORMAT=image/png&TRANSPARENT=TRUE")
    append("&CRS=EPSG:3857&WIDTH=").append(width).append("&HEIGHT=").append(height)
    append("&BBOX=").append(box.minX).append(',').append(box.minY)
        .append(',').append(box.maxX).append(',').append(box.maxY)
    if (timeIso != null) append("&TIME=").append(timeIso)
    if (refIso != null) append("&DIM_REFERENCE_TIME=").append(refIso)
}

/**
 * Ob der Vorhersagelauf [refIso] beim DWD schon zu haben ist: `true`, `false`, oder `null`,
 * wenn der Server nicht zu erreichen war. Gefragt wird nach einem winzigen Bild; einen
 * Lauf, den es noch nicht gibt, beantwortet er in Sekundenbruchteilen mit einer Fehlermeldung.
 */
fun radarRunExists(refIso: String): Boolean? = runCatching {
    val c = (URL(radarUrl(DWD_RADAR_LAYER, refIso, RADAR_AREA_GER, 8, 8, refIso)).openConnection() as HttpURLConnection).apply {
        connectTimeout = 15_000
        readTimeout = 20_000
        setRequestProperty("User-Agent", "BoatSpeedy")
    }
    try {
        val code = c.responseCode
        val bild = c.contentType?.contains("image") == true
        (if (code >= 400) c.errorStream else c.inputStream)?.use { it.readBytes() }
        when {
            code == 200 && bild -> true
            code == 200 -> false
            else -> null
        }
    } catch (e: IOException) {
        c.disconnect()
        throw e
    }
}.getOrNull()

/**
 * Der neueste Vorhersagelauf, als Zeitpunkt in Millisekunden. Gesucht wird von der
 * abgerundeten Uhrzeit rückwärts; ein Lauf erscheint beim DWD einige Minuten nach seiner
 * Bezugszeit. Ist [known] schon der neueste, endet die Suche dort. Ist der Server nicht
 * erreichbar, bleibt es bei [known].
 */
fun findLatestRadarRun(nowMs: Long, known: Long?): Long? {
    for (ref in radarRunCandidates(nowMs)) {
        if (known != null && ref <= known) return known
        when (radarRunExists(radarIso(ref))) {
            true -> return ref
            false -> continue
            null -> return known
        }
    }
    return known
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

/**
 * Ein Radarbild als **Stufen** statt als Farben: 0 = kein Regen, 1..15 = die Stufen der
 * Leiter. So wird jedes Bild nur einmal entschlüsselt. Vorher geschah das bei jedem
 * Schwenk für alle Frames aufs Neue, mit dem Abgleich von achthunderttausend Bildpunkten
 * gegen die Farbleiter.
 */
class RadarGrid(val width: Int, val height: Int, val classes: ByteArray)

/**
 * Ordnet die Bildpunkte ihrer Stufe zu. Durchsichtig ist kein Regen; was nicht auf der
 * Leiter steht, auch nicht: das halbdurchsichtige Grau für „keine Daten" und der violette
 * Rand des Messgebiets.
 */
fun radarClasses(pixels: IntArray): ByteArray = ByteArray(pixels.size) { i ->
    val p = pixels[i]
    if ((p ushr 24) < 128) {
        0
    } else {
        val rgb = p and 0xFFFFFF
        var k = 0
        while (k < RADAR_RAMP.size && RADAR_RAMP[k] != rgb) k++
        if (k < RADAR_RAMP.size) (k + 1).toByte() else 0
    }
}

fun decodeRadarGrid(png: ByteArray): RadarGrid? {
    val bmp = decodeRadar(png) ?: return null
    return try {
        val px = IntArray(bmp.width * bmp.height)
        bmp.getPixels(px, 0, bmp.width, 0, 0, bmp.width, bmp.height)
        RadarGrid(bmp.width, bmp.height, radarClasses(px))
    } finally {
        bmp.recycle()
    }
}

/** Die Farbe einer Stufe, 0 = durchsichtig. */
fun radarColor(cls: Int): Int =
    if (cls <= 0) 0 else (0xFF shl 24) or RADAR_RAMP[(cls - 1).coerceAtMost(RADAR_RAMP.size - 1)]

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

/** Gewichte der Catmull-Rom-Kurve für den Abstand [t] (0..1) hinter dem linken Punkt. */
private fun catmullRom(t: Float, into: FloatArray, at: Int) {
    val t2 = t * t
    val t3 = t2 * t
    into[at] = -0.5f * t3 + t2 - 0.5f * t
    into[at + 1] = 1.5f * t3 - 2.5f * t2 + 1f
    into[at + 2] = -1.5f * t3 + 2f * t2 + 0.5f * t
    into[at + 3] = 0.5f * t3 - 0.5f * t2
}

/**
 * Die Stufen für ein Fenster des Rasters, vergrößert um [factor]. Das Fenster beginnt bei
 * den Rasterkoordinaten [fx0]/[fy0] und ist [ow]×[oh] Bildpunkte groß.
 *
 * Interpoliert werden die **Messwerte**, die Einfärbung bleibt in ihren fünfzehn Stufen:
 * weiche Umrisse, harte Grenzen. Bilinear ergab dabei Rauten und Treppen entlang des
 * Kilometerrasters; bei starkem Zoom sah der Regen aus wie aus Kacheln gelegt. Hier ist es
 * eine **Catmull-Rom-Kurve** über vier mal vier Zellen, und die Umrisse werden rund.
 *
 * **Begrenzt auf die vier Zellen ringsum.** Die Kurve schießt sonst über: Ein Kern aus vier
 * Zellen der Stufe 8 wurde zur 10, ein Gewitter sähe stärker aus, als es gemessen ist. Ein
 * Weichzeichner hätte das Gegenteil getan und eine einzelne Zelle der Stufe 10 auf unter 3
 * gedrückt. So bleibt jede Zelle in ihrer Mitte genau, was sie ist.
 */
fun interpolateRadar(
    grid: RadarGrid,
    fx0: Double,
    fy0: Double,
    factor: Double,
    ow: Int,
    oh: Int,
): ByteArray {
    val sw = grid.width
    val sh = grid.height
    val c = grid.classes
    if (factor <= 1.0) return nearestRadar(grid, fx0, fy0, factor, ow, oh)
    val xs = IntArray(ow)
    val wx = FloatArray(ow * 4)
    for (x in 0 until ow) {
        val sx = fx0 + (x + 0.5) / factor - 0.5
        val x0 = kotlin.math.floor(sx).toInt()
        xs[x] = x0
        catmullRom((sx - x0).toFloat(), wx, x * 4)
    }
    val wy = FloatArray(4)
    val rows = IntArray(4)
    val cols = IntArray(4)
    val out = ByteArray(ow * oh)
    for (y in 0 until oh) {
        val sy = fy0 + (y + 0.5) / factor - 0.5
        val y0 = kotlin.math.floor(sy).toInt()
        catmullRom((sy - y0).toFloat(), wy, 0)
        for (j in 0 until 4) rows[j] = (y0 - 1 + j).coerceIn(0, sh - 1) * sw
        val row = y * ow
        for (x in 0 until ow) {
            val x0 = xs[x]
            for (i in 0 until 4) cols[i] = (x0 - 1 + i).coerceIn(0, sw - 1)
            // Die vier Zellen ringsum geben den Rahmen vor. Sind alle trocken, ist es der
            // Punkt auch — das ist fast überall der Fall und spart die ganze Rechnung.
            val a = c[rows[1] + cols[1]].toInt()
            val b = c[rows[1] + cols[2]].toInt()
            val d = c[rows[2] + cols[1]].toInt()
            val e = c[rows[2] + cols[2]].toInt()
            val hi = maxOf(maxOf(a, b), maxOf(d, e))
            if (hi == 0) continue
            val lo = minOf(minOf(a, b), minOf(d, e))
            var v = 0f
            for (j in 0 until 4) {
                val r = rows[j]
                val zeile = wx[x * 4] * c[r + cols[0]] + wx[x * 4 + 1] * c[r + cols[1]] +
                    wx[x * 4 + 2] * c[r + cols[2]] + wx[x * 4 + 3] * c[r + cols[3]]
                v += wy[j] * zeile
            }
            val stufe = kotlin.math.floor(v.coerceIn(lo.toFloat(), hi.toFloat()) + 0.5f).toInt()
            out[row + x] = stufe.toByte()
        }
    }
    return out
}

/**
 * Ohne Vergrößerung genügt die nächste Zelle. In der Übersicht über ganz Deutschland ist
 * eine Messzelle kaum größer als ein Bildpunkt, runde Umrisse sieht dort niemand. Die Kurve
 * kostete gerade in diesem Fall am meisten, weil das ganze Raster durchgerechnet wurde.
 */
private fun nearestRadar(grid: RadarGrid, fx0: Double, fy0: Double, factor: Double, ow: Int, oh: Int): ByteArray {
    val sw = grid.width
    val sh = grid.height
    val xs = IntArray(ow) { x -> kotlin.math.floor(fx0 + (x + 0.5) / factor).toInt().coerceIn(0, sw - 1) }
    val out = ByteArray(ow * oh)
    for (y in 0 until oh) {
        val r = kotlin.math.floor(fy0 + (y + 0.5) / factor).toInt().coerceIn(0, sh - 1) * sw
        val row = y * ow
        for (x in 0 until ow) out[row + x] = grid.classes[r + xs[x]]
    }
    return out
}

/**
 * Rechnet aus dem Deutschlandbild den sichtbaren Ausschnitt heraus und vergrößert ihn
 * dabei, siehe [interpolateRadar].
 *
 * Der Vergrößerungsfaktor richtet sich nach dem *Fenster*, nicht nach der gesamten
 * Fläche, deshalb ist die Vergrößerung bei jedem Zoom passend. Lohnt sie nicht, weil
 * ohnehin etwa ein Bildpunkt je Rasterzelle bliebe, wird der Ausschnitt in der
 * Messauflösung ausgegeben; die Filterung beim Zeichnen erledigt den Rest.
 *
 * Gibt `null` zurück, wenn das Fenster das Radargebiet nicht berührt.
 */
fun renderRadarWindow(
    grid: RadarGrid,
    srcBox: MercatorBox,
    window: MercatorBox,
    maxEdge: Int = RADAR_RENDER_EDGE,
): Bitmap? {
    val sw = grid.width
    val sh = grid.height
    if (sw < 2 || sh < 2) return null

    // Fenster in Rasterkoordinaten umrechnen (Web-Mercator ist linear, also einfach).
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

    val zoom = radarZoomFactor(kotlin.math.max(spanX, spanY), maxEdge)
    val factor = if (zoom < RADAR_MIN_FACTOR) 1.0 else zoom
    val ow = kotlin.math.round(spanX * factor).toInt().coerceIn(1, 4096)
    val oh = kotlin.math.round(spanY * factor).toInt().coerceIn(1, 4096)

    val stufen = interpolateRadar(grid, fx0, fy0, factor, ow, oh)
    val out = IntArray(ow * oh) { radarColor(stufen[it].toInt()) }
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
 * Der Schlüssel ist **Vorhersagelauf und Zeit**. Das Bild für 15:00 Uhr sieht anders aus,
 * je nachdem ob es um 14:00 oder um 14:30 berechnet wurde; zusammen mit dem Lauf ändert es
 * sich aber nie mehr. Deshalb darf ein Eintrag so lange liegen, wie seine Vorhersage reicht.
 */
const val RADAR_CACHE_MS = 3 * 60 * 60_000L
fun radarCacheDir(context: android.content.Context): java.io.File =
    java.io.File(context.cacheDir, "radar").apply { mkdirs() }

private fun cacheName(layer: String, refIso: String?, timeIso: String?): String =
    (layer + "_" + (refIso ?: "latest") + "_" + (timeIso ?: "now"))
        .replace(Regex("[^A-Za-z0-9_.-]"), "_") + ".png"

fun readCachedRadar(
    dir: java.io.File,
    layer: String,
    refIso: String?,
    timeIso: String?,
    maxAgeMs: Long = RADAR_CACHE_MS,
): ByteArray? = runCatching {
    java.io.File(dir, cacheName(layer, refIso, timeIso))
        .takeIf { it.isFile && System.currentTimeMillis() - it.lastModified() < maxAgeMs }
        ?.readBytes()
}.getOrNull()

fun writeCachedRadar(dir: java.io.File, layer: String, refIso: String?, timeIso: String?, png: ByteArray) {
    runCatching { java.io.File(dir, cacheName(layer, refIso, timeIso)).writeBytes(png) }
}

/**
 * Wirft weg, was älter als [maxAgeMs] ist. Jeder Vorhersagelauf bringt eine neue
 * Schleife; ohne Aufräumen sammelt sich der Speicher voll mit Bildern, die niemand mehr
 * zeigt.
 */
fun pruneRadarCache(dir: java.io.File, maxAgeMs: Long = RADAR_CACHE_MS) {
    runCatching {
        val cutoff = System.currentTimeMillis() - maxAgeMs
        dir.listFiles()?.forEach { if (it.isFile && it.lastModified() < cutoff) it.delete() }
    }
}
