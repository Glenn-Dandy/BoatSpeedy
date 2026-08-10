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
        for (trip in trips) {
            sb.append("  <trk>\n    <name>")
            sb.append(escape(name.format(Date(trip.startedAt))))
            sb.append("</name>\n    <trkseg>\n")
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
                sb.append("</extensions>")
                sb.append("</trkpt>\n")
            }
            sb.append("    </trkseg>\n  </trk>\n")
        }
        sb.append("</gpx>\n")
        return sb.toString()
    }

    private fun fmt(v: Double) = String.format(Locale.US, "%.6f", v)
    private fun fmt3(v: Float) = String.format(Locale.US, "%.3f", v)

    private fun escape(s: String) = s
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}
