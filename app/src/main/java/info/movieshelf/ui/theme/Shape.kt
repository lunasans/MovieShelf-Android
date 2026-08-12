package info.movieshelf.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// "Shelf"-Look: sehr runde Ecken. Zentrale Radius-Skala statt Magic Numbers
// pro Screen.
val ChipShape = RoundedCornerShape(12.dp)
val PillShape = RoundedCornerShape(20.dp)
val PosterCardShape = RoundedCornerShape(24.dp)
// Hero: nur unten abgerundet, wie `rounded-b-[3rem]` im Web-Slider — oben
// schließt der Banner bündig an die App-Bar an.
val HeroBannerShape = RoundedCornerShape(bottomStart = 40.dp, bottomEnd = 40.dp)

val Shapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = ChipShape,
    medium = RoundedCornerShape(16.dp),
    large = PosterCardShape,
    extraLarge = RoundedCornerShape(32.dp)
)
