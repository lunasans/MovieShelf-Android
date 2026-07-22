package at.neuhaus.movieshelf.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import at.neuhaus.movieshelf.ui.theme.ChipShape
import at.neuhaus.movieshelf.ui.theme.MediaFormat4k
import at.neuhaus.movieshelf.ui.theme.MediaFormatBluray
import at.neuhaus.movieshelf.ui.theme.MediaFormatDigital
import at.neuhaus.movieshelf.ui.theme.MediaFormatDvd
import at.neuhaus.movieshelf.ui.theme.MediaFormatRental
import at.neuhaus.movieshelf.ui.theme.MediaFormatStreaming

/**
 * Medienformat einer Edition ("Shelf"-Look: kleine farbige Pill-Badges).
 * Deckt die gleichen Formate wie die Web-Oberfläche ab (DVD/Blu-ray/4K/
 * Streaming/Digital/Leihe).
 */
enum class MediaFormat(val label: String, val color: Color) {
    DVD("DVD", MediaFormatDvd),
    BLU_RAY("BLU-RAY", MediaFormatBluray),
    UHD_4K("4K", MediaFormat4k),
    STREAMING("STREAMING", MediaFormatStreaming),
    DIGITAL("DIGITAL", MediaFormatDigital),
    RENTAL("LEIHE", MediaFormatRental);

    companion object {
        fun fromTag(tag: String): MediaFormat = when (tag.lowercase().trim()) {
            "blu-ray", "bluray" -> BLU_RAY
            "dvd" -> DVD
            "uhd", "4k", "uhd-4k" -> UHD_4K
            "streaming" -> STREAMING
            "digital" -> DIGITAL
            "leihe", "rental" -> RENTAL
            else -> DIGITAL
        }
    }
}

/** Kleiner Text-Pill für ein Medienformat, z.B. auf Poster-Karten. */
@Composable
fun MediaFormatBadge(
    format: MediaFormat,
    modifier: Modifier = Modifier
) {
    Text(
        text = format.label,
        modifier = modifier
            .clip(ChipShape)
            .background(format.color)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        color = Color.White,
        fontSize = 10.sp,
        fontWeight = FontWeight.Black,
        letterSpacing = 0.8.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

/** Bewertungs-Pill mit Stern-Icon, z.B. oben links auf einer Poster-Karte. */
@Composable
fun RatingBadge(
    rating: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(ChipShape)
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 7.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Star,
            contentDescription = null,
            tint = Color(0xFFFBBF24),
            modifier = Modifier.size(13.dp)
        )
        Text(
            text = rating,
            modifier = Modifier.padding(start = 3.dp),
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Black,
            style = MaterialTheme.typography.labelSmall
        )
    }
}
