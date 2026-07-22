package at.neuhaus.movieshelf.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import at.neuhaus.movieshelf.R
import at.neuhaus.movieshelf.ui.theme.PosterCardShape
import coil.compose.AsyncImage

/**
 * Wiederverwendbare Poster-Karte im "Shelf"-Look: Aspect 2:3, sehr runde Ecken,
 * dunkler Verlauf-Scrim für Titel/Untertitel, optionale Badge-Slots oben.
 * Ersetzt die bisher pro Screen duplizierte Karten-Gestaltung (z.B.
 * `DashboardScreen.MovieItem`).
 */
@Composable
fun PosterCard(
    imageUrl: Any?,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    topStart: (@Composable BoxScope.() -> Unit)? = null,
    topEnd: (@Composable BoxScope.() -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 1.05f else 1f,
        animationSpec = tween(durationMillis = 150),
        label = "posterCardScale"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(2f / 3f)
            .scale(scale)
            .clip(PosterCardShape)
            .then(
                if (onClick != null) {
                    Modifier.clickableWithoutRipple(interactionSource, onClick)
                } else Modifier
            )
    ) {
        // Fallback-Fläche für fehlendes/nicht ladbares Cover, damit die Karte nie
        // wie eine leere schwarze Fläche wirkt.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Movie,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.fillMaxSize(0.35f)
            )
        }
        AsyncImage(
            model = imageUrl,
            contentDescription = title,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            placeholder = painterResource(id = R.drawable.ic_launcher_background)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                    )
                )
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.75f)
                    )
                }
            }
        }

        if (topStart != null) {
            Box(modifier = Modifier.align(Alignment.TopStart).padding(6.dp)) {
                topStart()
            }
        }
        if (topEnd != null) {
            Box(modifier = Modifier.align(Alignment.TopEnd).padding(6.dp)) {
                topEnd()
            }
        }
    }
}

private fun Modifier.clickableWithoutRipple(
    interactionSource: MutableInteractionSource,
    onClick: () -> Unit
): Modifier = this.clickable(
    interactionSource = interactionSource,
    indication = null,
    onClick = onClick
)
