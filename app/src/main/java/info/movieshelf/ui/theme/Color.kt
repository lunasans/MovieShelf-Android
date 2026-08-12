package info.movieshelf.ui.theme

import androidx.compose.ui.graphics.Color

// "Shelf"-Look: Cinematic Near-Black mit Rosé-Marke (angelehnt an die
// MovieShelf-Web-Oberfläche).
//
// WICHTIG: Markenfarbe von MovieShelf ist Rose-600 (#E11D48) und gilt
// flächendeckend — `primary`, `secondary` und `tertiary` sind deshalb alle
// Rosé-Töne. Blau kommt in der App nicht mehr als Akzent vor.
val PrimaryDark = Color(0xFFE11D48) // Rose-600 — Markenfarbe
val OnPrimaryDark = Color(0xFFFFFFFF)
val SecondaryDark = Color(0xFFE11D48) // Rose-600 — Markenfarbe
val OnSecondaryDark = Color(0xFFFFFFFF)
val SurfaceDark = Color(0xFF151519) // Dunkle Glass-Trägerfläche
val BackgroundDark = Color(0xFF0C0C0E) // Near Black
val ErrorDark = Color(0xFFCF6679)

// Dark – ergänzte M3-Tokens (Blue/Near-Black-Look)
val OnSurfaceDark = Color(0xFFF4F4F5) // Helle Schrift auf dunklen Surfaces
val OnBackgroundDark = Color(0xFFF4F4F5)
val SurfaceVariantDark = Color(0xFF1E1E23) // Etwas hellere Fläche (Karten/Chips)
val OnSurfaceVariantDark = Color(0xFFA1A1AA) // Gedämpftes Grau für Metadaten
val PrimaryContainerDark = Color(0xFF5A0E22) // Tiefes Rosé
val OnPrimaryContainerDark = Color(0xFFFFD9E2) // Helles Rosé
val SecondaryContainerDark = Color(0xFF5A0E22) // Tiefes Rosé-Rot
val OnSecondaryContainerDark = Color(0xFFFFD9E2)
val TertiaryDark = Color(0xFFFB7185) // Rose-400 als heller Tertiär-Akzent
val OnTertiaryDark = Color(0xFF410010)
val TertiaryContainerDark = Color(0xFF9F1239) // Rose-800
val OnTertiaryContainerDark = Color(0xFFFFD9E2)
val OutlineDark = Color(0xFF5C5C66) // Subtile Konturen
val OutlineVariantDark = Color(0xFF2E2E35)
val OnErrorDark = Color(0xFF370B0E)
val ErrorContainerDark = Color(0xFF8C1D26)
val OnErrorContainerDark = Color(0xFFFFDAD7)
val InverseSurfaceDark = Color(0xFFF4F4F5)
val InverseOnSurfaceDark = Color(0xFF151519)
val ScrimDark = Color(0xFF000000)

// Light Theme (Clean & Soft, gleiche Farbrollen wie Dark)
val PrimaryLight = Color(0xFFE11D48) // Rose-600 — Markenfarbe
val OnPrimaryLight = Color(0xFFFFFFFF)
val SecondaryLight = Color(0xFFE11D48) // Rose-600 — Markenfarbe
val OnSecondaryLight = Color(0xFFFFFFFF)
val BackgroundLight = Color(0xFFF5F5F7)
val SurfaceLight = Color(0xFFFFFFFF)

// Light – ergänzte M3-Tokens
val OnSurfaceLight = Color(0xFF1A1A1A)
val OnBackgroundLight = Color(0xFF1A1A1A)
val SurfaceVariantLight = Color(0xFFE4E7EC) // Leicht blau-graue Fläche (Karten/Chips)
val OnSurfaceVariantLight = Color(0xFF54565C) // Gedämpft für Metadaten
val PrimaryContainerLight = Color(0xFFFFD9E2) // Sanftes Rosé
val OnPrimaryContainerLight = Color(0xFF410010) // Tiefes Rosé
val SecondaryContainerLight = Color(0xFFFFD9E2) // Sanftes Rosé-Rot
val OnSecondaryContainerLight = Color(0xFF410010)
val TertiaryLight = Color(0xFFBE123C) // Rose-700 als tiefer Akzent
val OnTertiaryLight = Color(0xFFFFFFFF)
val TertiaryContainerLight = Color(0xFFFFD9E2)
val OnTertiaryContainerLight = Color(0xFF410010)
val OutlineLight = Color(0xFF83868C)
val OutlineVariantLight = Color(0xFFCFD2D8)
val ErrorLight = Color(0xFFB3261E)
val OnErrorLight = Color(0xFFFFFFFF)
val ErrorContainerLight = Color(0xFFF9DEDC)
val OnErrorContainerLight = Color(0xFF410E0B)
val InverseSurfaceLight = Color(0xFF2B2B2D)
val InverseOnSurfaceLight = Color(0xFFF5EEEF)
val ScrimLight = Color(0xFF000000)

// === Marken-Akzent (Rose), beide Themes ===
// Einziger Akzent der App: Navigation, Gradient-Kacheln, Glow und Schatten.
// Blau gibt es hier bewusst nicht mehr — Rose gilt flächendeckend.
val NavAccentRed = Color(0xFFE11D48) // Rose-600 — Markenfarbe
val NavAccentRedDark = Color(0xFF9F1239) // Rose-800 (Glow/Schatten/Gradient-Enden)
val NavAccentRedLight = Color(0xFFFB7185) // Rose-400 (Gradient-Mitte)
val NavAccentRose500 = Color(0xFFF43F5E) // Rose-500 (helles Ende des Marken-Verlaufs)

// Glass-Tokens für frosted-glass Panels (Nav-Bar, Badges, Overlays)
val GlassWhite08 = Color(0x14FFFFFF) // 8% weißes Overlay
val GlassBorder18 = Color(0x40FFFFFF) // 25% weißer Rand — hebt die Kante auch vor hellen Covern ab
val GlassScrimDark = Color(0xF0101014) // 94% deckender Scrim: bei hellen Covern war die Schrift sonst nicht lesbar

/**
 * Verlauf hinter der schwebenden Navigationsleiste. Ohne ihn steht die Pill
 * unvermittelt auf dem Poster; je nach Cover verschwimmt ihre Kante mit dem
 * Bild. Der Verlauf dunkelt nur den unteren Rand ab und laesst den Rest der
 * Ansicht unangetastet.
 */
val NavScrimTop = Color(0x00000000)
val NavScrimBottom = Color(0xB3000000)

// Medienformat-Farben (DVD/Blu-ray/4K/Streaming/Digital/Leihe)
val MediaFormatDvd = Color(0xFFF97316) // Orange
val MediaFormatBluray = Color(0xFF3B82F6) // Blau
val MediaFormat4k = Color(0xFF22D3EE) // Cyan
val MediaFormatStreaming = Color(0xFF10B981) // Smaragd
val MediaFormatDigital = Color(0xFF8B5CF6) // Violett
val MediaFormatRental = Color(0xFFF59E0B) // Amber
