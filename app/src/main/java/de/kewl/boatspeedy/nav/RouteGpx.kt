package de.kewl.boatspeedy.nav

import de.kewl.boatspeedy.data.Craft
import org.xmlpull.v1.XmlPullParser
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Gespeicherte Routen als GPX: die Strecke als `<rte>`, die Hindernisse als `<wpt>`. So
 * lesen andere Programme sie als Route mit Wegpunkten.
 *
 * Für BoatSpeedy selbst steht die ganze Route zusätzlich als JSON in den Erweiterungen.
 * Aus `<rte>` allein ließe sich nicht zurückholen, welcher Teil Fahrwasser ist, wo
 * umgetragen wird oder was eine Schleuse an Öffnungszeiten hat.
 */
object RouteGpx {

    fun build(routes: List<SavedRoute>): String {
        val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val datum = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append(
            "<gpx version=\"1.1\" creator=\"BoatSpeedy\" " +
                "xmlns=\"http://www.topografix.com/GPX/1/1\" " +
                "xmlns:boatspeedy=\"https://github.com/Glenn-Dandy/BoatSpeedy\">\n",
        )
        routes.minByOrNull { it.createdAt }?.let {
            sb.append("  <metadata><time>").append(iso.format(Date(it.createdAt))).append("</time></metadata>\n")
        }
        // GPX verlangt die Wegpunkte vor den Routen.
        for (r in routes) {
            for (o in r.obstacles) {
                sb.append("  <wpt lat=\"").append(fmt(o.lat)).append("\" lon=\"").append(fmt(o.lon)).append("\">")
                o.name?.let { sb.append("<name>").append(escape(it)).append("</name>") }
                sb.append("<type>").append(o.kind.name).append("</type>")
                sb.append("</wpt>\n")
            }
        }
        for (r in routes) {
            sb.append("  <rte>\n    <name>")
            sb.append(escape(r.name ?: datum.format(Date(r.createdAt))))
            sb.append("</name>\n    <extensions><boatspeedy:route>")
            sb.append(escape(RouteJson.encode(r)))
            sb.append("</boatspeedy:route></extensions>\n")
            for (p in r.path) {
                sb.append("    <rtept lat=\"").append(fmt(p.lat)).append("\" lon=\"").append(fmt(p.lon)).append("\"/>\n")
            }
            sb.append("  </rte>\n")
        }
        sb.append("</gpx>\n")
        return sb.toString()
    }

    /**
     * Die Routen einer GPX-Datei, oder leer, wenn sie keine hat. Ein Track (`<trk>`) ist
     * eine Fahrt und keine Route; Dateien mit Track bleiben deshalb beim Fahrten-Import.
     *
     * Eigene Routen kommen vollständig zurück. Eine fremde `<rte>` wird zur Route entlang
     * ihrer Punkte, gerechnet für [craft], ohne Hindernisse; „Neu berechnen" holt sie.
     */
    fun parse(input: java.io.InputStream, parser: XmlPullParser, craft: Craft, jetzt: Long): List<SavedRoute> {
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(input, null)
        val out = ArrayList<SavedRoute>()
        var hatTrack = false
        var inRte = false
        var punkte = ArrayList<LatLon>()
        var name: String? = null
        var json: String? = null
        var cur: String? = null
        val text = StringBuilder()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    val n = parser.name.lowercase()
                    when (n) {
                        "trk" -> hatTrack = true
                        "rte" -> {
                            inRte = true
                            punkte = ArrayList()
                            name = null
                            json = null
                        }
                        "rtept" -> if (inRte) {
                            val la = parser.getAttributeValue(null, "lat")?.toDoubleOrNull()
                            val lo = parser.getAttributeValue(null, "lon")?.toDoubleOrNull()
                            if (la != null && lo != null) punkte.add(LatLon(la, lo))
                        }
                    }
                    cur = n
                    text.setLength(0)
                }
                XmlPullParser.TEXT -> text.append(parser.text)
                XmlPullParser.END_TAG -> {
                    val n = parser.name.lowercase()
                    if (inRte) {
                        when (n) {
                            // Der Name der Route, nicht der eines Punktes.
                            "name" -> if (cur == "name" && name == null) {
                                name = text.toString().trim().takeIf { it.isNotEmpty() }
                            }
                            "boatspeedy:route" -> json = text.toString()
                            "rte" -> {
                                inRte = false
                                val eigene = json?.let { runCatching { RouteJson.decode(it) }.getOrNull() }
                                val r = when {
                                    eigene != null -> eigene.copy(id = jetzt + out.size)
                                    punkte.size >= 2 -> SavedRoute(
                                        id = jetzt + out.size,
                                        createdAt = jetzt,
                                        name = name,
                                        craft = craft,
                                        target = punkte.last(),
                                        path = punkte,
                                        water = punkte,
                                    )
                                    else -> null
                                }
                                r?.let { out.add(it) }
                            }
                        }
                    }
                    cur = null
                    text.setLength(0)
                }
            }
            event = parser.next()
        }
        return if (hatTrack) emptyList() else out
    }

    private fun fmt(v: Double) = String.format(Locale.US, "%.6f", v)

    private fun escape(s: String) = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}
