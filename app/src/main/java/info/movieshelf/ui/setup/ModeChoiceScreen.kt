package info.movieshelf.ui.setup

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import info.movieshelf.R
import info.movieshelf.data.local.db.AppMode
import info.movieshelf.ui.theme.PillShape

/**
 * Erster Start: eigenständig oder mit Shelf.
 *
 * Die Wahl lässt sich später ändern — deshalb ist sie hier bewusst knapp
 * gehalten und ohne Warnungen. Wer eigenständig beginnt und später eine Shelf
 * verbindet, behält seinen Bestand; er wird dann hochgeladen.
 */
@Composable
fun ModeChoiceScreen(onModeChosen: (AppMode) -> Unit) {
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(24.dp))

            Image(
                painter = painterResource(id = R.drawable.logo),
                contentDescription = "MovieShelf",
                modifier = Modifier.height(48.dp)
            )

            Text(
                text = stringResource(R.string.mode_question),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Text(
                text = stringResource(R.string.mode_change_later),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(16.dp))

            ModeCard(
                icon = Icons.Default.PhoneAndroid,
                title = stringResource(R.string.mode_standalone),
                description = stringResource(R.string.mode_standalone_sub),
                actionLabel = stringResource(R.string.mode_standalone_action),
                onClick = { onModeChosen(AppMode.STANDALONE) }
            )

            ModeCard(
                icon = Icons.Default.CloudSync,
                title = stringResource(R.string.mode_shelf),
                description = stringResource(R.string.mode_shelf_sub),
                actionLabel = stringResource(R.string.mode_shelf_action),
                onClick = { onModeChosen(AppMode.SHELF) }
            )
        }
    }
}

@Composable
private fun ModeCard(
    icon: ImageVector,
    title: String,
    description: String,
    actionLabel: String,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                Spacer(Modifier.width(12.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Text(
                description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = onClick,
                modifier = Modifier.fillMaxWidth(),
                shape = PillShape
            ) {
                Text(actionLabel, fontWeight = FontWeight.Bold)
            }
        }
    }
}
