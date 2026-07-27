package de.kewl.boatspeedy.ui

import android.widget.TextView
import de.kewl.boatspeedy.R
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.infowindow.InfoWindow

/** Sprechblase am Track-Punkt; Text kommt aus [Marker.getTitle]. Tippen schließt sie. */
class TrackInfoWindow(mapView: MapView) : InfoWindow(R.layout.track_bubble, mapView) {
    override fun onOpen(item: Any?) {
        val marker = item as? Marker ?: return
        mView.findViewById<TextView>(R.id.bubble_text)?.text = marker.title
        mView.setOnClickListener { close() }
    }

    override fun onClose() {}
}
