package at.neuhaus.movieshelf.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import at.neuhaus.movieshelf.R

// "Shelf"-Look: Outfit für Überschriften, Plus Jakarta Sans für Fließtext.
// Statisch gebündelt (kein Google-Fonts-Downloadable-API), damit der Look
// auch offline und ohne Play-Services-Handshake beim ersten Start steht.
val OutfitFontFamily = FontFamily(
    Font(R.font.outfit_medium, FontWeight.Medium),
    Font(R.font.outfit_semibold, FontWeight.SemiBold),
    Font(R.font.outfit_bold, FontWeight.Bold),
    Font(R.font.outfit_extrabold, FontWeight.ExtraBold),
    Font(R.font.outfit_black, FontWeight.Black)
)

val PlusJakartaSansFontFamily = FontFamily(
    Font(R.font.plusjakartasans_regular, FontWeight.Normal),
    Font(R.font.plusjakartasans_medium, FontWeight.Medium),
    Font(R.font.plusjakartasans_semibold, FontWeight.SemiBold),
    Font(R.font.plusjakartasans_bold, FontWeight.Bold)
)
