package at.neuhaus.movieshelf.ui.theme

import androidx.compose.ui.graphics.Color

// "Shelf"-Look: Cinematic Near-Black & Blue Accent (angelehnt an die
// MovieShelf-Web-Oberfläche)
val PrimaryDark = Color(0xFF3B82F6) // Blau (Haupt-Interaktionsfarbe)
val OnPrimaryDark = Color(0xFF00224D)
val SecondaryDark = Color(0xFFE11D48) // Rosé-Rot (Marken-/Admin-Akzent)
val OnSecondaryDark = Color(0xFFFFFFFF)
val SurfaceDark = Color(0xFF151519) // Dunkle Glass-Trägerfläche
val BackgroundDark = Color(0xFF0C0C0E) // Near Black
val ErrorDark = Color(0xFFCF6679)

// Dark – ergänzte M3-Tokens (Blue/Near-Black-Look)
val OnSurfaceDark = Color(0xFFF4F4F5) // Helle Schrift auf dunklen Surfaces
val OnBackgroundDark = Color(0xFFF4F4F5)
val SurfaceVariantDark = Color(0xFF1E1E23) // Etwas hellere Fläche (Karten/Chips)
val OnSurfaceVariantDark = Color(0xFFA1A1AA) // Gedämpftes Grau für Metadaten
val PrimaryContainerDark = Color(0xFF11305F) // Tiefes Blau
val OnPrimaryContainerDark = Color(0xFFBFDBFE) // Helles Blau
val SecondaryContainerDark = Color(0xFF5A0E22) // Tiefes Rosé-Rot
val OnSecondaryContainerDark = Color(0xFFFFD9E2)
val TertiaryDark = Color(0xFF60A5FA) // Helles Blau als Tertiär-Akzent
val OnTertiaryDark = Color(0xFF00224D)
val TertiaryContainerDark = Color(0xFF1E4785)
val OnTertiaryContainerDark = Color(0xFFDBEAFE)
val OutlineDark = Color(0xFF5C5C66) // Subtile Konturen
val OutlineVariantDark = Color(0xFF2E2E35)
val OnErrorDark = Color(0xFF370B0E)
val ErrorContainerDark = Color(0xFF8C1D26)
val OnErrorContainerDark = Color(0xFFFFDAD7)
val InverseSurfaceDark = Color(0xFFF4F4F5)
val InverseOnSurfaceDark = Color(0xFF151519)
val ScrimDark = Color(0xFF000000)

// Light Theme (Clean & Soft, gleiche Blau/Rosé-Markenfarben)
val PrimaryLight = Color(0xFF2563EB) // Blau
val OnPrimaryLight = Color(0xFFFFFFFF)
val SecondaryLight = Color(0xFFE11D48) // Rosé-Rot
val OnSecondaryLight = Color(0xFFFFFFFF)
val BackgroundLight = Color(0xFFF5F5F7)
val SurfaceLight = Color(0xFFFFFFFF)

// Light – ergänzte M3-Tokens
val OnSurfaceLight = Color(0xFF1A1A1A)
val OnBackgroundLight = Color(0xFF1A1A1A)
val SurfaceVariantLight = Color(0xFFE4E7EC) // Leicht blau-graue Fläche (Karten/Chips)
val OnSurfaceVariantLight = Color(0xFF54565C) // Gedämpft für Metadaten
val PrimaryContainerLight = Color(0xFFDBEAFE) // Sanftes Blau
val OnPrimaryContainerLight = Color(0xFF00224D) // Dunkles Blau
val SecondaryContainerLight = Color(0xFFFFD9E2) // Sanftes Rosé-Rot
val OnSecondaryContainerLight = Color(0xFF410010)
val TertiaryLight = Color(0xFF1D4ED8) // Tiefes Blau als Akzent
val OnTertiaryLight = Color(0xFFFFFFFF)
val TertiaryContainerLight = Color(0xFFDBEAFE)
val OnTertiaryContainerLight = Color(0xFF00224D)
val OutlineLight = Color(0xFF83868C)
val OutlineVariantLight = Color(0xFFCFD2D8)
val ErrorLight = Color(0xFFB3261E)
val OnErrorLight = Color(0xFFFFFFFF)
val ErrorContainerLight = Color(0xFFF9DEDC)
val OnErrorContainerLight = Color(0xFF410E0B)
val InverseSurfaceLight = Color(0xFF2B2B2D)
val InverseOnSurfaceLight = Color(0xFFF5EEEF)
val ScrimLight = Color(0xFF000000)

// === Marken-Akzent: EIN kohärentes Blau (beide Themes) ===
// Haupt-Interaktionsakzent der App. Wird in der Navigation und für
// blau-harmonisierte M3-Tokens (primary/tertiary) verwendet.
val NavAccentBlue = Color(0xFF3B82F6) // Sattes Marken-Blau (Hauptakzent)
val NavAccentBlueDark = Color(0xFF1D4ED8) // Tiefes Dunkelblau (Glow/Schatten/Gradient-Enden)
val NavAccentBlueLight = Color(0xFF60A5FA) // Leicht hellerer Blauton (Gradient-Mitte)

// Sekundärer/Admin-Akzent (Rosé-Rot), analog zur Web-Version
val NavAccentRed = Color(0xFFE11D48)
val NavAccentRedDark = Color(0xFF9F1239)
val NavAccentRedLight = Color(0xFFFB7185)

// Glass-Tokens für frosted-glass Panels (Nav-Bar, Badges, Overlays)
val GlassWhite08 = Color(0x14FFFFFF) // 8% weißes Overlay
val GlassBorder18 = Color(0x2EFFFFFF) // 18% weißer Rand
val GlassScrimDark = Color(0xB3101014) // Fallback-Scrim für Geräte ohne Blur-Support

// Medienformat-Farben (DVD/Blu-ray/4K/Streaming/Digital/Leihe)
val MediaFormatDvd = Color(0xFFF97316) // Orange
val MediaFormatBluray = Color(0xFF3B82F6) // Blau
val MediaFormat4k = Color(0xFF22D3EE) // Cyan
val MediaFormatStreaming = Color(0xFF10B981) // Smaragd
val MediaFormatDigital = Color(0xFF8B5CF6) // Violett
val MediaFormatRental = Color(0xFFF59E0B) // Amber
