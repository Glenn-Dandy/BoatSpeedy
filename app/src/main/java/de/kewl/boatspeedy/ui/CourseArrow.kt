package de.kewl.boatspeedy.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Zeigt, wohin gedreht werden muss, um auf das Ziel zuzuhalten: senkrecht = Kurs stimmt,
 * nach rechts = nach steuerbord. Nicht die Himmelsrichtung, sondern die **Abweichung** —
 * so muss man beim Steuern die Karte nicht lesen.
 *
 * Ohne Fahrt kennt das GPS keinen Kurs. Der Pfeil wird dann ausgegraut und behält seine
 * letzte Richtung, statt zu verschwinden oder wild zu kreiseln.
 *
 * Gedreht wird weich: das GPS liefert etwa einmal je Sekunde, und ein Sprung von zehn
 * Grad je Meldung ruckelt sichtbar. Der Weg wird dabei **fortlaufend** gerechnet, nicht
 * auf 0–360 zurückgefaltet — sonst nimmt der Pfeil beim Übergang von 359° auf 1° den
 * langen Weg und dreht einmal ganz herum.
 */
@Composable
fun CourseArrow(relativeDeg: Float, stale: Boolean, size: Dp = 26.dp) {
    var continuous by remember { mutableFloatStateOf(relativeDeg) }
    continuous = remember(relativeDeg) { continuous + shortestTurn(continuous, relativeDeg) }
    val shown by animateFloatAsState(
        targetValue = continuous,
        animationSpec = tween(durationMillis = ROTATE_MS),
        label = "Kurspfeil",
    )
    Icon(
        Icons.Filled.Navigation,
        contentDescription = null,
        modifier = Modifier.size(size).rotate(shown),
        tint = if (stale) {
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
        } else {
            MaterialTheme.colorScheme.primary
        },
    )
}

/**
 * Kürzester Drehwinkel von [from] nach [to], im Bereich −180…+180. Damit dreht der Pfeil
 * immer über die kurze Seite.
 */
internal fun shortestTurn(from: Float, to: Float): Float {
    var d = (to - from) % 360f
    if (d > 180f) d -= 360f
    if (d < -180f) d += 360f
    return d
}

/** Knapp unter dem GPS-Takt, damit sich zwei Drehungen nicht überholen. */
private const val ROTATE_MS = 900
