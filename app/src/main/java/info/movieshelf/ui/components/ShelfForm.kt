package info.movieshelf.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import info.movieshelf.ui.theme.ChipShape
import info.movieshelf.ui.theme.OutfitFontFamily

/**
 * Formular-Bausteine im "Shelf"-Look, 1:1 an das Web-Formular
 * (`admin/movies/edit.blade.php`) angelehnt: Glas-Karte pro Sektion mit
 * Rosé-Verlauf, winziger Versal-Header mit Rosé-Icon, runde Felder mit
 * Rosé-Fokus. Die Werte leben hier zentral, damit Create- und Edit-Screen
 * nicht auseinanderlaufen.
 */

/** Abstand zwischen zwei Sektions-Karten (Web: `space-y-10`). */
val ShelfSectionSpacing = 20.dp

/**
 * Sektions-Überschrift: sehr kleine, fette Versalien mit weitem Sperrsatz und
 * Rosé-Icon. Im Web ist der Text bewusst stark gedimmt (`text-white/20`) — die
 * Farbe trägt das Icon, nicht die Schrift.
 */
@Composable
fun ShelfSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(10.dp))
        }
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = OutfitFontFamily,
                fontWeight = FontWeight.Black,
                fontSize = 11.sp,
                letterSpacing = 2.5.sp
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Glas-Karte pro Themenblock — das Gegenstück zu `.glass p-10 rounded-[3rem]`
 * im Web, inklusive des diagonalen Rosé-Schleiers.
 */
@Composable
fun ShelfFormSection(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = MaterialTheme.shapes.extraLarge
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            // Themenneutrale Glasfläche: das Web ist dark-only und nutzt ein
            // weißes 8%-Overlay, das im Light-Theme unsichtbar wäre.
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .background(
                Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.secondary.copy(alpha = 0.06f),
                        Color.Transparent
                    )
                )
            )
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            ShelfSectionHeader(title = title, icon = icon)
            Spacer(Modifier.size(2.dp))
            content()
        }
    }
}

/**
 * Eingabefeld im Shelf-Look: runde Form, gedämpfte Glasfläche, Rosé-Fokus.
 * Das Label steht im Web über dem Feld — in Compose bleibt es das schwebende
 * M3-Label, weil das mit Tastatur und TalkBack besser zusammenspielt.
 */
@Composable
fun ShelfTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    isError: Boolean = false,
    readOnly: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    trailingIcon: @Composable (() -> Unit)? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, style = MaterialTheme.typography.labelLarge) },
        placeholder = placeholder?.let {
            {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        },
        modifier = modifier,
        singleLine = singleLine,
        minLines = minLines,
        isError = isError,
        readOnly = readOnly,
        keyboardOptions = keyboardOptions,
        trailingIcon = trailingIcon,
        shape = ChipShape,
        textStyle = MaterialTheme.typography.bodyMedium,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.secondary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            focusedLabelColor = MaterialTheme.colorScheme.secondary,
            unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            cursorColor = MaterialTheme.colorScheme.secondary,
            focusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
            unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.4f)
        )
    )
}
