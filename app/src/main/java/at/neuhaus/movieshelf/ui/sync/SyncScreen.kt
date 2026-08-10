package at.neuhaus.movieshelf.ui.sync

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import at.neuhaus.movieshelf.MovieShelfApplication
import at.neuhaus.movieshelf.data.sync.SyncPhase
import at.neuhaus.movieshelf.data.sync.SyncPreview
import at.neuhaus.movieshelf.data.sync.SyncResult
import at.neuhaus.movieshelf.ui.components.ShelfFormSection
import at.neuhaus.movieshelf.ui.components.ShelfSectionSpacing
import at.neuhaus.movieshelf.ui.theme.PillShape

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncScreen(onBack: () -> Unit) {
    val app = LocalContext.current.applicationContext as MovieShelfApplication
    val viewModel: SyncViewModel = viewModel(
        factory = SyncViewModel.Factory(
            app.syncEngine,
            app.listSyncEngine,
            app.database.settingDao()
        )
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Abgleich") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(ShelfSectionSpacing)
        ) {
            ShelfFormSection(title = "Stand", icon = Icons.Default.Sync) {
                Text(
                    text = viewModel.lastSyncAt?.let { "Zuletzt abgeglichen: $it" }
                        ?: "Noch nie abgeglichen — der erste Abgleich holt den vollständigen Bestand.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Der Hintergrundlauf kann nichts melden, waehrend er laeuft —
                // deshalb steht sein Ergebnis hier.
                viewModel.backgroundSummary?.let { summary ->
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Im Hintergrund${viewModel.backgroundAt?.let { " ($it)" } ?: ""}: $summary",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            viewModel.progress?.let { progress ->
                ShelfFormSection(title = "Läuft", icon = Icons.Default.Sync) {
                    Text(
                        text = when (progress.phase) {
                            SyncPhase.PUSH -> "Lokale Änderungen werden hochgeladen"
                            SyncPhase.PULL -> "Serverstand wird geholt"
                            SyncPhase.MEDIA -> "Vorgemerkte Bilder werden nachgereicht"
                            SyncPhase.DONE -> "Fertig"
                        },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    progress.subject?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    val fraction = progress.fraction
                    if (fraction != null) {
                        LinearProgressIndicator(
                            progress = { fraction },
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
            }

            viewModel.preview?.let { preview ->
                PreviewSection(preview)
            }

            viewModel.result?.let { result ->
                ResultSection(result, viewModel)
            }

            viewModel.error?.let { message ->
                ShelfFormSection(title = "Fehler", icon = Icons.Default.ErrorOutline) {
                    Text(message, style = MaterialTheme.typography.bodyMedium)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { viewModel.loadPreview() },
                    modifier = Modifier.weight(1f),
                    shape = PillShape,
                    enabled = !viewModel.isBusy
                ) {
                    Text("Vorschau", fontWeight = FontWeight.SemiBold)
                }
                Button(
                    onClick = { viewModel.runSync() },
                    modifier = Modifier.weight(1f),
                    shape = PillShape,
                    // Bewusst erst nach der Vorschau: niemand soll Löschungen
                    // bestätigen, die er nicht gesehen hat.
                    enabled = !viewModel.isBusy && viewModel.preview != null
                ) {
                    Text("Abgleichen", fontWeight = FontWeight.Bold)
                }
            }

            if (viewModel.preview == null && !viewModel.isBusy) {
                Text(
                    "Der Abgleich startet erst nach einer Vorschau.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            TextButton(
                onClick = { viewModel.loadPreview(full = true) },
                enabled = !viewModel.isBusy
            ) {
                Text("Vollständigen Abgleich vorbereiten")
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun PreviewSection(preview: SyncPreview) {
    ShelfFormSection(
        title = if (preview.isDelta) "Vorschau (nur Änderungen)" else "Vorschau (Vollstand)",
        icon = Icons.Default.Sync
    ) {
        if (preview.isEmpty) {
            Text("Alles auf dem gleichen Stand.", style = MaterialTheme.typography.bodyMedium)
            return@ShelfFormSection
        }

        CountRow(Icons.Default.CloudUpload, "Wird hochgeladen", preview.outgoing)
        if (preview.toCreate > 0) DetailRow("davon neu angelegt", preview.toCreate)
        if (preview.toUpdate > 0) DetailRow("davon geändert", preview.toUpdate)
        if (preview.toDeleteRemote > 0) DetailRow("davon gelöscht", preview.toDeleteRemote)

        Spacer(Modifier.height(4.dp))
        CountRow(Icons.Default.CloudDownload, "Kommt herunter", preview.incoming)
        if (preview.incomingNew > 0) DetailRow("davon neu", preview.incomingNew)
        if (preview.incomingUpdated > 0) DetailRow("davon aktualisiert", preview.incomingUpdated)
        if (preview.incomingDeleted > 0) DetailRow("davon lokal entfernt", preview.incomingDeleted)

        if (preview.keptLocal > 0) {
            Spacer(Modifier.height(4.dp))
            CountRow(Icons.Default.Shield, "Behält lokale Änderung", preview.keptLocal)
        }

        if (preview.hasDeletions) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Der Abgleich entfernt Filme. Das lässt sich nicht rückgängig machen.",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun ResultSection(result: SyncResult, viewModel: SyncViewModel) {
    ShelfFormSection(title = "Ergebnis", icon = Icons.Default.Sync) {
        CountRow(Icons.Default.CloudUpload, "Hochgeladen", result.push.total)
        CountRow(Icons.Default.CloudDownload, "Übernommen", result.pull.applied)
        if (result.pull.deleted > 0) CountRow(Icons.Default.DeleteOutline, "Lokal entfernt", result.pull.deleted)
        if (result.pull.skipped > 0) CountRow(Icons.Default.Shield, "Lokale Änderung behalten", result.pull.skipped)

        viewModel.listResult?.let { lists ->
            if (lists.itemsAdded > 0 || lists.itemsRemoved > 0) {
                Spacer(Modifier.height(4.dp))
                DetailRow("Listen-Einträge ergänzt", lists.itemsAdded)
                DetailRow("Listen-Einträge entfernt", lists.itemsRemoved)
            }
        }

        val errors = result.errors + (viewModel.listResult?.errors ?: emptyList())
        if (errors.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Fehlerprotokoll",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error
            )
            errors.forEach { syncError ->
                Text(
                    "${syncError.subject}: ${syncError.message}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                "Diese Einträge bleiben vorgemerkt und werden beim nächsten Abgleich erneut versucht.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        TextButton(onClick = { viewModel.dismissResult() }) { Text("Ausblenden") }
    }
}

@Composable
private fun CountRow(icon: ImageVector, label: String, count: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.secondary)
        Spacer(Modifier.width(10.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(count.toString(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun DetailRow(label: String, count: Int) {
    Row(modifier = Modifier.padding(start = 26.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(count.toString(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
