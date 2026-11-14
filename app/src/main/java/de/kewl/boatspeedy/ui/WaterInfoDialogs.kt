package de.kewl.boatspeedy.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.kewl.boatspeedy.R
import de.kewl.boatspeedy.nav.Gauge
import de.kewl.boatspeedy.nav.GaugeLevel
import de.kewl.boatspeedy.nav.Gauges
import de.kewl.boatspeedy.nav.NoticeLimit
import de.kewl.boatspeedy.nav.NoticePeriod
import de.kewl.boatspeedy.nav.WaterNotice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

@Composable
private fun Zeile(label: String, wert: String) {
    Row(modifier = Modifier.padding(vertical = 2.dp)) {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(104.dp))
        Text(wert, fontSize = 13.sp)
    }
}

@Composable
private fun Quelle(text: String) {
    Text(
        text,
        fontSize = 11.sp,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        modifier = Modifier.padding(top = 8.dp),
    )
}

private fun km(v: Double): String =
    if (v % 1.0 == 0.0) v.roundToInt().toString() else String.format(Locale.getDefault(), "%.2f", v)

/** Auskunft zu einem Pegel. Die Änderung der letzten Stunde wird erst beim Öffnen geholt. */
@Composable
fun GaugeDialog(g: Gauge, onDismiss: () -> Unit) {
    var aenderung by remember(g.uuid) { mutableStateOf<Double?>(null) }
    LaunchedEffect(g.uuid) {
        aenderung = withContext(Dispatchers.IO) { Gauges.changeLastHourCm(g.uuid) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(g.name) },
        text = {
            Column {
                g.valueCm?.let { Zeile(stringResource(R.string.gauge_level), "${it.roundToInt()} cm") }
                aenderung?.let { d ->
                    val r = d.roundToInt()
                    Zeile(stringResource(R.string.gauge_change), (if (r > 0) "+" else "") + "$r cm")
                }
                val lage = when (g.level) {
                    GaugeLevel.LOW -> R.string.gauge_low
                    GaugeLevel.NORMAL -> R.string.gauge_normal
                    GaugeLevel.HIGH -> R.string.gauge_high
                    GaugeLevel.ABOVE_HSW -> R.string.gauge_above_hsw
                    GaugeLevel.UNKNOWN -> null
                }
                lage?.let { Zeile(stringResource(R.string.gauge_state), stringResource(it)) }
                g.time?.let { t ->
                    val lokal = t.atZoneSameInstant(ZoneId.systemDefault())
                    val uhr = lokal.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
                    val wert = if (lokal.toLocalDate() == LocalDate.now()) {
                        uhr
                    } else {
                        lokal.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)) + ", " + uhr
                    }
                    Zeile(stringResource(R.string.gauge_time), wert)
                }
                g.water?.let { Zeile(stringResource(R.string.gauge_water), it) }
                g.km?.let { Zeile(stringResource(R.string.gauge_km), km(it)) }
                Quelle(stringResource(R.string.gauge_source))
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
        },
    )
}

/**
 * Eine oder mehrere Meldungen von ELWIS. Der Text steht im Wortlaut da, so verlangen es die
 * Nutzungsbedingungen. Darüber, was eingeschränkt ist und wann, aus den Codes der Meldung.
 */
@Composable
fun NoticeDialog(notices: List<WaterNotice>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (notices.size == 1) {
                    notices[0].places.first().name ?: stringResource(R.string.notice_title)
                } else {
                    stringResource(R.string.notice_route_title)
                },
            )
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                notices.forEachIndexed { i, n ->
                    if (i > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                    NoticeBody(n, mitName = notices.size > 1)
                }
                Quelle(stringResource(R.string.notice_source))
                Quelle(stringResource(R.string.notice_disclaimer))
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
        },
    )
}

@Composable
private fun NoticeBody(n: WaterNotice, mitName: Boolean) {
    val ort = n.places.first()
    if (mitName) {
        Text(
            ort.name ?: stringResource(R.string.notice_title),
            fontWeight = FontWeight.Bold,
            color = if (n.isClosure) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
    }
    ort.fairway?.takeIf { it != ort.name }?.let {
        Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
    }
    Spacer(Modifier.height(4.dp))
    n.places.flatMap { it.limits }.distinct().forEach { l ->
        Text(
            limitLabel(l),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = if (l.code == "OBSTRU") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
        l.periods.forEach { p -> periodText(p)?.let { Text(it, fontSize = 13.sp) } }
    }
    zeitraum(n.from, n.to)?.let { Zeile(stringResource(R.string.notice_valid), it) }
    n.text?.let {
        Spacer(Modifier.height(4.dp))
        SelectionContainer { Text(it.trim(), fontSize = 13.sp) }
    }
    n.by?.let { Quelle(it) }
}

@Composable
private fun limitLabel(l: NoticeLimit): String {
    val name = stringResource(
        when (l.code) {
            "OBSTRU" -> R.string.limit_OBSTRU
            "PAROBS" -> R.string.limit_PAROBS
            "NOSERV" -> R.string.limit_NOSERV
            "SERVIC" -> R.string.limit_SERVIC
            "DELAY" -> R.string.limit_DELAY
            "CLEWID" -> R.string.limit_CLEWID
            "CLEHEI" -> R.string.limit_CLEHEI
            "AVADEP" -> R.string.limit_AVADEP
            "VESDRA" -> R.string.limit_VESDRA
            "VESBRE" -> R.string.limit_VESBRE
            "VESLEN", "CONLEN" -> R.string.limit_VESLEN
            "CAUTIO" -> R.string.limit_CAUTIO
            "PASSIN" -> R.string.limit_PASSIN
            "OVRTAK" -> R.string.limit_OVRTAK
            "WAVWAS" -> R.string.limit_WAVWAS
            "NOMOOR" -> R.string.limit_NOMOOR
            "NOBERT" -> R.string.limit_NOBERT
            "NOSHORE" -> R.string.limit_NOSHORE
            "ANCHOR" -> R.string.limit_ANCHOR
            "SPEED" -> R.string.limit_SPEED
            "TURNIN" -> R.string.limit_TURNIN
            else -> R.string.limit_other
        },
    )
    val wert = limitValue(l) ?: return name
    return "$name: $wert"
}

/** Zentimeter als Meter, so wie Tiefgang und Höhen sonst in der App stehen. */
private fun limitValue(l: NoticeLimit): String? {
    val v = l.value ?: return null
    if (l.unit == "cm") {
        val m = v.toDoubleOrNull()?.div(100) ?: return "$v cm"
        return String.format(Locale.getDefault(), "%.2f m", m)
    }
    return listOfNotNull(v, l.unit).joinToString(" ")
}

@Composable
private fun periodText(p: NoticePeriod): String? {
    val f = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    val bis = stringResource(R.string.notice_until)
    // Durchgehend: Die Uhrzeiten gehören zu Anfang und Ende, nicht zu jedem Tag.
    // „13.11.2026 13:20 bis 20.11.2026 14:51“, nicht „täglich 13:20 bis 14:51“.
    if (p.interval == null || p.interval == "CON") {
        val anfang = listOfNotNull(p.from?.format(f), p.timeFrom).joinToString(" ")
        val ende = listOfNotNull(p.to?.format(f), p.timeTo).joinToString(" ")
        return when {
            anfang.isNotEmpty() && ende.isNotEmpty() && anfang != ende -> "$anfang $bis $ende"
            anfang.isNotEmpty() -> anfang
            ende.isNotEmpty() -> "$bis $ende"
            else -> null
        }
    }
    val teile = mutableListOf<String>()
    zeitraum(p.from, p.to)?.let { teile += it }
    when (p.interval) {
        "DAY" -> teile += stringResource(R.string.notice_daily)
        "WRK", "WRD" -> teile += stringResource(R.string.notice_workdays)
        "WKN" -> teile += stringResource(R.string.notice_weekend)
        "MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN" -> {
            val tag = DayOfWeek.entries.first { it.name.startsWith(p.interval) }
            teile += tag.getDisplayName(TextStyle.FULL, Locale.getDefault())
        }
    }
    when {
        p.timeFrom != null && p.timeTo != null -> teile += "${p.timeFrom} $bis ${p.timeTo}"
        p.timeFrom != null -> teile += p.timeFrom
    }
    return teile.takeIf { it.isNotEmpty() }?.joinToString(", ")
}

@Composable
private fun zeitraum(from: LocalDate?, to: LocalDate?): String? {
    val f = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    val bis = stringResource(R.string.notice_until)
    return when {
        from != null && to != null && from == to -> from.format(f)
        from != null && to != null -> "${from.format(f)} $bis ${to.format(f)}"
        from != null -> from.format(f)
        to != null -> "$bis ${to.format(f)}"
        else -> null
    }
}
