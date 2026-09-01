package de.kewl.boatspeedy.ui

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
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
 */
@Composable
fun CourseArrow(relativeDeg: Float, stale: Boolean, size: Dp = 26.dp) {
    Icon(
        Icons.Filled.Navigation,
        contentDescription = null,
        modifier = Modifier.size(size).rotate(relativeDeg),
        tint = if (stale) {
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
        } else {
            MaterialTheme.colorScheme.primary
        },
    )
}
