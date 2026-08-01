package de.kewl.boatspeedy.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import org.osmdroid.tileprovider.MapTileProviderBase
import org.osmdroid.views.overlay.TilesOverlay

/**
 * Wie [TilesOverlay], zeichnet die Kacheln aber **erzwungen bilinear gefiltert**.
 * osmdroid skaliert Kacheln sonst hart (nearest) – zusammen mit bewusst kleiner
 * angeforderten WMS-Kacheln (z. B. 64 px) werden so die harten 1-km-Kanten des
 * DWD-Radars weichgezeichnet (Ansatz A).
 */
class SmoothTilesOverlay(provider: MapTileProviderBase, context: Context) : TilesOverlay(provider, context) {

    private val smoothPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG).apply {
        isAntiAlias = true
    }
    private val dst = RectF()

    override fun onTileReadyToDraw(c: Canvas, currentMapTile: Drawable, tileRect: Rect) {
        val bmp = (currentMapTile as? BitmapDrawable)?.bitmap
        if (bmp != null && !bmp.isRecycled) {
            dst.set(tileRect)
            c.drawBitmap(bmp, null, dst, smoothPaint)
        } else {
            currentMapTile.setBounds(tileRect.left, tileRect.top, tileRect.right, tileRect.bottom)
            currentMapTile.draw(c)
        }
    }
}
