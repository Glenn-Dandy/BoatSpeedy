package de.kewl.boatspeedy.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import de.kewl.boatspeedy.nav.GaugeLevel
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Ein Schild mit einer Zahl darin, für Pegel und Brückenhöhen. Die Zahl ist das, weswegen
 * man hinsieht; ein Symbol, das man erst antippen muss, um sie zu lesen, wäre ein Umweg.
 *
 * Mit durchsichtigem Rand ringsum auf mindestens 40 dp: osmdroid prüft den Treffer gegen
 * die Fläche des Bildes, und ein Finger ist ungenauer als ein Zeiger.
 */
private fun labelDrawable(context: Context, label: String, fillColor: Int, textColor: Int): Drawable {
    val dp = context.resources.displayMetrics.density
    val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = textColor
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
        textSize = 12f * dp
    }
    val border = 2f * dp
    val w = text.measureText(label) + 10f * dp + 2 * border
    val h = 18f * dp + 2 * border
    val size = (40 * dp).roundToInt()
    val bw = maxOf(size, w.roundToInt())
    val bh = maxOf(size, h.roundToInt())
    val bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val rect = RectF((bw - w) / 2f, (bh - h) / 2f, (bw + w) / 2f, (bh + h) / 2f)
    canvas.drawRoundRect(rect, 5f * dp, 5f * dp, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
    rect.inset(border, border)
    canvas.drawRoundRect(rect, 4f * dp, 4f * dp, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fillColor })
    val m = text.fontMetrics
    canvas.drawText(label, bw / 2f, bh / 2f - (m.ascent + m.descent) / 2f, text)
    return BitmapDrawable(context.resources, bmp)
}

/** Pegel mit Wasserstand in Zentimetern. Blau, bei Hochwasser Orange, über HSW Rot. */
fun gaugeDrawable(context: Context, valueCm: Double?, level: GaugeLevel): Drawable {
    val fill = when (level) {
        GaugeLevel.ABOVE_HSW -> Color.parseColor("#C62828")
        GaugeLevel.HIGH -> Color.parseColor("#EF6C00")
        else -> Color.parseColor("#1565C0")
    }
    val label = valueCm?.let { "${it.roundToInt()} cm" } ?: "? cm"
    return labelDrawable(context, label, fill, Color.WHITE)
}

/**
 * Brücke mit ihrer Durchfahrtshöhe, in Bernstein wie das bisherige Brückensymbol. Nur
 * wenn die Seezeichen aus sind: Sonst zeichnet OpenSeaMap die Höhe schon selbst, und es
 * stünden zwei Schilder übereinander.
 */
fun bridgeHeightDrawable(context: Context, clearanceM: String): Drawable =
    labelDrawable(context, bridgeHeightLabel(clearanceM), Color.parseColor("#EF6C00"), Color.WHITE)

/**
 * „4.4“ wird je nach Sprache „4,4 m“ oder „4.4 m“. Nicht gerundet: Aus 4,45 darf keine 4,5
 * werden, bei einer Brückenhöhe wäre das der falsche Zentimeter. Was sich nicht als Zahl
 * lesen lässt, steht so da, wie es in OSM eingetragen ist.
 */
fun bridgeHeightLabel(clearanceM: String): String {
    val raw = clearanceM.trim().removeSuffix("m").trim().replace(',', '.')
    val v = raw.toBigDecimalOrNull() ?: return clearanceM.trim()
    val komma = java.text.DecimalFormatSymbols.getInstance(Locale.getDefault()).decimalSeparator
    return v.stripTrailingZeros().toPlainString().replace('.', komma) + " m"
}
