package de.kewl.boatspeedy.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import de.kewl.boatspeedy.R
import de.kewl.boatspeedy.weather.CurrentWeather
import java.util.Locale

/** Himmelsrichtung, aus der der Wind kommt – acht Sektoren reichen fürs Ablesen. */
fun windArrow(deg: Int): String {
    val dirs = listOf("N", "NO", "O", "SO", "S", "SW", "W", "NW")
    return dirs[(((deg % 360) + 360) % 360 + 22) / 45 % 8]
}

/**
 * Wohin die Luft zieht, aus der Richtung, **aus** der sie kommt.
 *
 * Eigene Funktion für eine Addition, weil genau diese Drehung erfahrungsgemäß irgendwann
 * jemand „richtigstellt" und der Pfeil danach andersherum zeigt. Der Test hält fest, wie
 * es gemeint ist: Wind aus Norden lässt den Pfeil nach Süden zeigen.
 */
fun windToDeg(fromDeg: Int): Int = (((fromDeg + 180) % 360) + 360) % 360

/** Wettersymbol als Zeichen – spart eine Grafik je Zustand. */
fun weatherIcon(icon: String?): String = when (icon) {
    "clear-day" -> "☀"
    "clear-night" -> "☾"
    "partly-cloudy-day", "partly-cloudy-night" -> "⛅"
    "cloudy" -> "☁"
    "fog" -> "≡"
    "rain", "sleet" -> "☂"
    "snow" -> "❄"
    "hail" -> "☂"
    "thunderstorm" -> "⚡"
    "wind" -> "≋"
    else -> "·"
}

/**
 * Eine Zeile mit dem, was die nächste DWD-Station gerade misst: Temperatur, Zustand, Wind
 * mit Böen. Steht in einem eigenen Streifen unter der Titelleiste, nicht darin — dort war
 * sie klein und drängte den Titel in eine zweite Zeile.
 */
@Composable
fun WeatherLine(w: CurrentWeather?, fontSize: androidx.compose.ui.unit.TextUnit = 16.sp) {
    if (w == null) {
        Text(stringResource(R.string.weather_loading), fontSize = fontSize)
        return
    }
    val text = buildString {
        w.temperatureC?.let { append(String.format(Locale.getDefault(), "%.1f °C", it)) }
        append("  ").append(weatherIcon(w.icon))
        w.windKmh?.let {
            append("  ")
            append(String.format(Locale.getDefault(), "%.0f", it))
            w.gustKmh?.takeIf { g -> g > it + 1 }?.let { g ->
                append(String.format(Locale.getDefault(), "/%.0f", g))
            }
            append(" km/h")
            w.windDirDeg?.let { d -> append(" ").append(windArrow(d)) }
        }
    }
    Text(
        text,
        fontSize = fontSize,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

/**
 * Woher der Wind kommt — als Pfeil.
 *
 * Der Pfeil zeigt **mit** dem Wind, also dorthin, wohin die Luft zieht; die Buchstaben
 * darunter nennen die Richtung, aus der er kommt. So steht es auf jeder Wetterkarte, und
 * beides zusammen lässt keinen Zweifel: Ein Pfeil allein wird regelmäßig andersherum
 * gelesen, als er gemeint ist.
 *
 * Die Angabe in OpenStreetMap-Manier zu drehen wäre hier falsch — die Wetterkarte steht
 * immer mit Norden oben, es gibt also nichts umzurechnen.
 *
 * @param deg Richtung, **aus** der der Wind weht, rechtweisend.
 */
@Composable
fun WindBadge(deg: Int, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.size(44.dp),
        shape = androidx.compose.foundation.shape.CircleShape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
        tonalElevation = 3.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                Icons.Filled.Navigation,
                contentDescription = "Wind aus ${windArrow(deg)}",
                tint = MaterialTheme.colorScheme.primary,
                // +180°, weil der Pfeil mit dem Wind zeigt: Kommt er aus Norden, zieht die
                // Luft nach Süden.
                modifier = Modifier.size(20.dp).rotate(windToDeg(deg).toFloat()),
            )
            Text(
                windArrow(deg),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.offset(y = 13.dp),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
