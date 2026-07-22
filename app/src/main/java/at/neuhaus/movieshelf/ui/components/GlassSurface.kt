package at.neuhaus.movieshelf.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import at.neuhaus.movieshelf.ui.theme.GlassBorder18
import at.neuhaus.movieshelf.ui.theme.GlassScrimDark
import at.neuhaus.movieshelf.ui.theme.PillShape

/**
 * Frosted-glass Träger-Fläche ("Shelf"-Look): dunkler, halbtransparenter Scrim
 * mit hellem Rand statt echtem Backdrop-Blur. Ein echter Blur-Effekt (der nur
 * den Hintergrund hinter der Fläche weichzeichnet, nicht deren eigenen Inhalt)
 * ließe sich unter Compose ohne zusätzliche Library (z.B. Haze) nicht sauber
 * umsetzen — `Modifier.blur()` würde stattdessen auch Icons/Text in `content`
 * verwischen. Diese Entscheidung lebt zentral hier, damit ein späterer Wechsel
 * auf eine Blur-Library nur diese eine Datei betrifft.
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = PillShape,
    borderColor: androidx.compose.ui.graphics.Color = GlassBorder18,
    tint: Brush = SolidColor(GlassScrimDark),
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(tint)
            .border(width = 1.dp, color = borderColor, shape = shape)
    ) {
        content()
    }
}
