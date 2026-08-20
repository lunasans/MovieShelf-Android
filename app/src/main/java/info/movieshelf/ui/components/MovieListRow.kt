package info.movieshelf.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import info.movieshelf.R
import info.movieshelf.data.model.Movie
import info.movieshelf.ui.theme.PosterCardShape

/** Breite des kleinen Covers; die Hoehe ergibt sich aus dem 2:3-Verhaeltnis. */
private val ROW_COVER_WIDTH = 52.dp
private val ROW_COVER_HEIGHT = 78.dp

/**
 * Kompakte Zeile fuer die Sammlungsliste — die Alternative zum Poster-Raster.
 *
 * Zeigt bewusst mehr Text als die Karte: neben Titel und Jahr auch Genre und
 * vor allem den **Regalstandort**, denn das ist der Grund, vor dem Regal zu
 * suchen. Das Cover bleibt klein, damit acht bis zehn Titel auf den Schirm
 * passen statt vier.
 */
@Composable
fun MovieListRow(
    movie: Movie,
    imageUrl: Any?,
    onClick: () -> Unit,
    onWatchedToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .width(ROW_COVER_WIDTH)
                .height(ROW_COVER_HEIGHT)
                .clip(PosterCardShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Movie,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.size(20.dp)
            )
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = movie.title ?: "",
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            // Jahr und Genre in eine Zeile: einzeln stehen sie zu duenn da, und
            // die Zeile soll flach bleiben.
            val meta = listOfNotNull(
                movie.year?.toString(),
                movie.genre?.takeIf { it.isNotBlank() }
            ).joinToString(" · ")
            if (meta.isNotBlank()) {
                Text(
                    text = meta,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            movie.discLocation?.takeIf { it.isNotBlank() }?.let { location ->
                Text(
                    text = stringResource(R.string.list_row_shelf_location, location),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (!movie.rating.isNullOrBlank()) {
            RatingBadge(rating = movie.rating)
        }

        IconButton(
            onClick = onWatchedToggle,
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(
                    color = if (movie.isWatched == true) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    shape = CircleShape
                )
        ) {
            Icon(
                imageVector = if (movie.isWatched == true) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                contentDescription = stringResource(
                    if (movie.isWatched == true) R.string.detail_mark_unwatched else R.string.detail_mark_watched
                ),
                tint = if (movie.isWatched == true) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
