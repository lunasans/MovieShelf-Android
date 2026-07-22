package at.neuhaus.movieshelf.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp

/**
 * Überschriften-Helper im "Shelf"-Look: Outfit-Schriftschnitt (bereits über
 * `Typography.headlineX`/`titleLarge` verdrahtet), zusätzlich Uppercase +
 * weiter Letter-Spacing für Sektions-Header, analog zur Web-Oberfläche.
 */
@Composable
fun HeadingText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.titleLarge,
    color: Color = MaterialTheme.colorScheme.onSurface
) {
    Text(
        text = text.uppercase(),
        modifier = modifier,
        style = style.copy(letterSpacing = 0.5.sp),
        color = color
    )
}
