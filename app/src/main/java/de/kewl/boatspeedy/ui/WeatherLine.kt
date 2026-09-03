package de.kewl.boatspeedy.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
 * mit Böen. Absichtlich knapp — sie sitzt in der Titelleiste.
 */
@Composable
fun WeatherLine(w: CurrentWeather?) {
    if (w == null) {
        Text(stringResource(R.string.weather_loading), fontSize = 12.sp)
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
        fontSize = 13.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
    )
}
