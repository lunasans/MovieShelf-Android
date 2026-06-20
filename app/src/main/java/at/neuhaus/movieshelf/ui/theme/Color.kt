package at.neuhaus.movieshelf.ui.theme

import androidx.compose.ui.graphics.Color

// Modern Movie Dark Theme (Deep Navy & Vibrant Amber)
val PrimaryDark = Color(0xFFFFC107) // Amber (TMDB Style)
val OnPrimaryDark = Color(0xFF3E2723)
val SecondaryDark = Color(0xFFCC1111) // Marken-Rot (harmonisiert mit Nav)
val OnSecondaryDark = Color(0xFFFFFFFF)
val SurfaceDark = Color(0xFF101418) // Deep Black-Navy
val BackgroundDark = Color(0xFF080A0C) // Near Black
val ErrorDark = Color(0xFFCF6679)

// Dark – ergänzte M3-Tokens (Amber/Navy-Look)
val OnSurfaceDark = Color(0xFFECEFF1) // Helle Schrift auf dunklen Surfaces
val OnBackgroundDark = Color(0xFFECEFF1)
val SurfaceVariantDark = Color(0xFF1C232B) // Etwas hellere Navy-Fläche (Karten/Chips)
val OnSurfaceVariantDark = Color(0xFFB0BEC5) // Gedämpftes Blue Grey für Metadaten
val PrimaryContainerDark = Color(0xFF4A3500) // Tiefes Amber-Braun
val OnPrimaryContainerDark = Color(0xFFFFE082) // Helles Amber
val SecondaryContainerDark = Color(0xFF5A0E0E) // Tiefroter Container (passt zum Nav-Rot)
val OnSecondaryContainerDark = Color(0xFFFFDAD6)
val TertiaryDark = Color(0xFFE57373) // Sanftes, gedämpftes Rot als Tertiär-Akzent
val OnTertiaryDark = Color(0xFF3E0A0A)
val TertiaryContainerDark = Color(0xFF7A1A1A)
val OnTertiaryContainerDark = Color(0xFFFFDAD6)
val OutlineDark = Color(0xFF5F6A72) // Subtile Konturen
val OutlineVariantDark = Color(0xFF2E363D)
val OnErrorDark = Color(0xFF370B0E)
val ErrorContainerDark = Color(0xFF8C1D26)
val OnErrorContainerDark = Color(0xFFFFDAD7)
val InverseSurfaceDark = Color(0xFFECEFF1)
val InverseOnSurfaceDark = Color(0xFF101418)
val ScrimDark = Color(0xFF000000)

// Light Theme (Clean & Soft)
val PrimaryLight = Color(0xFFE91E63) // Cinema Red
val OnPrimaryLight = Color(0xFFFFFFFF)
val SecondaryLight = Color(0xFFCC1111) // Marken-Rot (harmonisiert mit Nav)
val OnSecondaryLight = Color(0xFFFFFFFF)
val BackgroundLight = Color(0xFFF5F5F5)
val SurfaceLight = Color(0xFFFFFFFF)

// Light – ergänzte M3-Tokens (Cinema-Red-Look)
val OnSurfaceLight = Color(0xFF1A1A1A)
val OnBackgroundLight = Color(0xFF1A1A1A)
val SurfaceVariantLight = Color(0xFFEDE0E2) // Leicht rosé-graue Fläche (Karten/Chips)
val OnSurfaceVariantLight = Color(0xFF5C5458) // Gedämpft für Metadaten
val PrimaryContainerLight = Color(0xFFFFD9E2) // Sanftes Rosé
val OnPrimaryContainerLight = Color(0xFF3E001D) // Dunkles Weinrot
val SecondaryContainerLight = Color(0xFFFFDAD6) // Sanftes Rosé-Rot (passt zum Nav-Rot)
val OnSecondaryContainerLight = Color(0xFF410002)
val TertiaryLight = Color(0xFFB71C1C) // Tiefes Rot als Akzent (statt Amber)
val OnTertiaryLight = Color(0xFFFFFFFF)
val TertiaryContainerLight = Color(0xFFFFDAD6)
val OnTertiaryContainerLight = Color(0xFF410002)
val OutlineLight = Color(0xFF8A8285)
val OutlineVariantLight = Color(0xFFD8CCCF)
val ErrorLight = Color(0xFFB3261E)
val OnErrorLight = Color(0xFFFFFFFF)
val ErrorContainerLight = Color(0xFFF9DEDC)
val OnErrorContainerLight = Color(0xFF410E0B)
val InverseSurfaceLight = Color(0xFF2F2B2C)
val InverseOnSurfaceLight = Color(0xFFF5EEEF)
val ScrimLight = Color(0xFF000000)

// === Marken-Akzent: EIN kohärentes Kino-Rot (beide Themes) ===
// Einziger Akzent-Rotton der App. Wird in der Navigation und für die
// rot-harmonisierten M3-Tokens (secondary/tertiary) verwendet.
val NavAccentRed     = Color(0xFFCC1111) // Sattes Marken-Rot (Hauptakzent)
val NavAccentRedDark = Color(0xFF9B0000) // Tiefes Dunkelrot (Glow/Schatten/Gradient-Enden)
val NavAccentRedLight = Color(0xFFE12727) // Leicht hellerer Rotton (Gradient-Mitte, ersetzt 0xFFE53935)
