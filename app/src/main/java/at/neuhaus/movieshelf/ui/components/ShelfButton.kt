package at.neuhaus.movieshelf.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import at.neuhaus.movieshelf.ui.theme.PillShape

/**
 * Primärer Button im "Shelf"-Look: blauer Akzent, sehr runde Form.
 * Dünner Wrapper um M3 `Button`, damit Farbe/Form app-weit konsistent bleiben.
 */
@Composable
fun ShelfButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = PillShape,
        colors = ButtonDefaults.buttonColors(),
        contentPadding = contentPadding
    ) {
        Text(text = text, fontWeight = FontWeight.Bold)
    }
}

/** Sekundäre Variante (Outline) im gleichen Formfaktor. */
@Composable
fun ShelfOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = PillShape
    ) {
        Text(text = text, fontWeight = FontWeight.SemiBold)
    }
}
