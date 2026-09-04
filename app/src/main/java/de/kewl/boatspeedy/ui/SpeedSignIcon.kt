package de.kewl.boatspeedy.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import kotlin.math.roundToInt

/**
 * Zeichnet das Geschwindigkeitszeichen mit seiner Zahl.
 *
 * Absichtlich in derselben Form wie das Symbol der OpenSeaMap-Kacheln — rot umrandetes
 * Quadrat auf Weiß —, denn es liegt genau darauf. Deren Symbol bleibt leer; dieses hier
 * deckt es ab und trägt den Wert. Zwei verschieden aussehende Schilder übereinander wären
 * verwirrender als eines.
 */
fun speedSignDrawable(context: Context, kmh: Double): Drawable {
    val dp = context.resources.displayMetrics.density
    val size = (30 * dp).roundToInt().coerceAtLeast(24)
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)

    val border = 3f * dp
    val inset = border / 2f
    val rect = RectF(inset, inset, size - inset, size - inset)

    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }
    canvas.drawRoundRect(rect, 2f * dp, 2f * dp, fill)

    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = border
        color = Color.parseColor("#D32F2F")
    }
    canvas.drawRoundRect(rect, 2f * dp, 2f * dp, stroke)

    // Ganze Zahlen ohne Komma – „12" statt „12,0". Krumme Werte (Knoten umgerechnet)
    // behalten eine Stelle, sonst stünde dort etwas anderes als auf dem Schild.
    val label = if (kmh % 1.0 == 0.0) kmh.roundToInt().toString()
    else String.format(java.util.Locale.getDefault(), "%.1f", kmh)

    val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
        // Dreistelliges passt sonst nicht zwischen die Ränder.
        textSize = if (label.length >= 3) 12f * dp else 15f * dp
    }
    val metrics = text.fontMetrics
    val baseline = size / 2f - (metrics.ascent + metrics.descent) / 2f
    canvas.drawText(label, size / 2f, baseline, text)

    return BitmapDrawable(context.resources, bmp)
}
