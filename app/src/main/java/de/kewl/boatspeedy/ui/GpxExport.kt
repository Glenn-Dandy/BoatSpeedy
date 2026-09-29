package de.kewl.boatspeedy.ui

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import de.kewl.boatspeedy.trip.SavedTrip
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Exportiert aufgezeichnete Fahrten als GPX-Datei und liefert eine teilbare content://-URI. */
object GpxExport {

    /** Je Fahrt eine eigene GPX-Datei (zum Teilen mehrerer Tracks als Einzeldateien). */
    fun writeEach(context: Context, trips: List<SavedTrip>): List<Uri> =
        trips.filter { it.hasTrack }.mapNotNull { write(context, listOf(it)) }

    /** Je Route eine eigene GPX-Datei, wie bei den Fahrten. */
    fun writeRoutes(context: Context, routes: List<de.kewl.boatspeedy.nav.SavedRoute>): List<Uri> {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        return routes.map { r ->
            val file = File(dir, "boatspeedy-route-${r.id}.gpx")
            file.writeText(de.kewl.boatspeedy.nav.RouteGpx.build(listOf(r)))
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }
    }

    /**
     * Teilt GPX-Dateien über den Android-Dialog. Ohne ClipData gilt die Leseerlaubnis
     * nicht für alle URIs, und die Ziel-App kann die Dateien sonst nicht öffnen.
     */
    fun share(context: Context, uris: List<Uri>, title: String) {
        if (uris.isEmpty()) return
        val send = if (uris.size == 1) {
            android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "application/gpx+xml"
                putExtra(android.content.Intent.EXTRA_STREAM, uris.first())
            }
        } else {
            android.content.Intent(android.content.Intent.ACTION_SEND_MULTIPLE).apply {
                type = "application/gpx+xml"
                putParcelableArrayListExtra(android.content.Intent.EXTRA_STREAM, ArrayList(uris))
            }
        }
        send.clipData = android.content.ClipData(
            "GPX",
            arrayOf("application/gpx+xml"),
            android.content.ClipData.Item(uris.first()),
        ).also { clip -> uris.drop(1).forEach { u -> clip.addItem(android.content.ClipData.Item(u)) } }
        send.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(android.content.Intent.createChooser(send, title))
    }

    /** Baut aus den Fahrten eine GPX-Datei im Cache und gibt ihre FileProvider-URI zurück. */
    fun write(context: Context, trips: List<SavedTrip>): Uri? {
        val withTrack = trips.filter { it.hasTrack }
        if (withTrack.isEmpty()) return null

        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val name = if (withTrack.size == 1) "boatspeedy-${withTrack.first().id}.gpx"
        else "boatspeedy-${System.currentTimeMillis()}.gpx"
        val file = File(dir, name)
        file.writeText(buildGpx(withTrack))

        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    fun buildGpx(trips: List<SavedTrip>): String {
        val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val name = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append(
            "<gpx version=\"1.1\" creator=\"BoatSpeedy\" " +
                "xmlns=\"http://www.topografix.com/GPX/1/1\" " +
                "xmlns:boatspeedy=\"https://github.com/Glenn-Dandy/BoatSpeedy\">\n",
        )
        // Startzeit der (ersten) Fahrt als Metadatum – Standardfeld vieler GPX-Werkzeuge.
        trips.minByOrNull { it.startedAt }?.let {
            sb.append("  <metadata><time>").append(iso.format(Date(it.startedAt))).append("</time></metadata>\n")
        }
        for (trip in trips) {
            sb.append("  <trk>\n    <name>")
            sb.append(escape(trip.name ?: name.format(Date(trip.startedAt))))
            sb.append("</name>\n")
            // Die Zeiten der App explizit mitgeben: Werkzeuge rechnen sonst nur die
            // Gesamtspanne (inkl. Pausen) aus den Zeitstempeln.
            val pause = (trip.totalMs - trip.durationMs).coerceAtLeast(0L)
            sb.append("    <desc>")
            sb.append(escape(
                "Fahrzeit " + hms(trip.durationMs) +
                    " · Pause " + hms(pause) +
                    " · Gesamt " + hms(trip.totalMs) +
                    String.format(Locale.US, " · %.2f km", trip.distanceM / 1000.0) +
                    String.format(Locale.US, " · %.1f Ah", trip.chargeAh) +
                    (if (trip.energyWh > 0f) String.format(Locale.US, " · %.0f Wh", trip.energyWh) else ""),
            ))
            sb.append("</desc>\n")
            sb.append("    <extensions>")
            sb.append("<boatspeedy:movingTimeS>").append(trip.durationMs / 1000).append("</boatspeedy:movingTimeS>")
            sb.append("<boatspeedy:pauseTimeS>").append(pause / 1000).append("</boatspeedy:pauseTimeS>")
            sb.append("<boatspeedy:totalTimeS>").append(trip.totalMs / 1000).append("</boatspeedy:totalTimeS>")
            sb.append("<boatspeedy:distanceM>").append(String.format(Locale.US, "%.1f", trip.distanceM)).append("</boatspeedy:distanceM>")
            sb.append("<boatspeedy:chargeAh>").append(fmt3(trip.chargeAh)).append("</boatspeedy:chargeAh>")
            // Die Energie fehlte hier. Nach dem Import stand der Verbrauch in Ah wieder da,
            // Energie und Effizienz (Wh/km) aber nicht: Die gibt es nur aus diesem Wert.
            sb.append("<boatspeedy:energyWh>").append(fmt3(trip.energyWh)).append("</boatspeedy:energyWh>")
            // Der eigene Name eigens: <name> trägt ohne ihn das Datum, und das soll beim
            // Einlesen nicht zum Namen werden.
            trip.name?.let { sb.append("<boatspeedy:name>").append(escape(it)).append("</boatspeedy:name>") }
            sb.append("</extensions>\n")
            sb.append("    <trkseg>\n")
            for (p in trip.points) {
                sb.append("      <trkpt lat=\"")
                    .append(fmt(p.lat)).append("\" lon=\"").append(fmt(p.lon)).append("\">")
                sb.append("<time>").append(iso.format(Date(trip.startedAt + p.tMs))).append("</time>")
                // Geschwindigkeit (m/s) + Verbrauch/SoC als GPX-Erweiterungen.
                sb.append("<extensions>")
                sb.append("<speed>").append(fmt3(p.speedMs)).append("</speed>")
                sb.append("<boatspeedy:speed>").append(fmt3(p.speedMs)).append("</boatspeedy:speed>")
                if (p.chargeAh > 0f) {
                    sb.append("<boatspeedy:chargeAh>").append(fmt3(p.chargeAh)).append("</boatspeedy:chargeAh>")
                }
                if (p.soc >= 0) {
                    sb.append("<boatspeedy:soc>").append(p.soc).append("</boatspeedy:soc>")
                }
                if (!p.currentA.isNaN()) {
                    sb.append("<boatspeedy:currentA>").append(fmt3(p.currentA)).append("</boatspeedy:currentA>")
                }
                if (!p.powerW.isNaN()) {
                    sb.append("<boatspeedy:powerW>").append(fmt3(p.powerW)).append("</boatspeedy:powerW>")
                }
                sb.append("</extensions>")
                sb.append("</trkpt>\n")
            }
            sb.append("    </trkseg>\n  </trk>\n")
        }
        sb.append("</gpx>\n")
        return sb.toString()
    }

    private fun fmt(v: Double) = String.format(Locale.US, "%.6f", v)

    private fun hms(ms: Long): String {
        val t = ms / 1000
        return String.format(Locale.US, "%d:%02d:%02d", t / 3600, (t % 3600) / 60, t % 60)
    }
    private fun fmt3(v: Float) = String.format(Locale.US, "%.3f", v)

    private fun escape(s: String) = s
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}
