package info.movieshelf.ui.sync

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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import info.movieshelf.MovieShelfApplication
import info.movieshelf.data.sync.SyncPhase
import info.movieshelf.data.local.db.SyncClock
import info.movieshelf.data.sync.SyncAction
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.runtime.remember
import info.movieshelf.data.sync.SyncDirection
import info.movieshelf.data.sync.SyncPreview
import info.movieshelf.data.sync.SyncPreviewItem
import info.movieshelf.data.sync.SyncResult
import info.movieshelf.ui.components.ShelfFormSection
import info.movieshelf.ui.components.ShelfSectionSpacing
import info.movieshelf.ui.theme.PillShape

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as MovieShelfApplication
    val notifier = remember(app) { SyncNotifier(app) }
    val viewModel: SyncViewModel = viewModel(
        factory = SyncViewModel.Factory(
            app.syncEngine,
            app.listSyncEngine,
            app.database.settingDao(),
            notifier
        )
    )

    // Ab Android 13 muss man um Meldungen bitten. Gefragt wird erst beim
    // Starten eines Abgleichs, nicht beim Oeffnen des Bildschirms: dort waere
    // nicht zu erkennen, wofuer. Die Antwort haelt nichts auf — ohne Erlaubnis
    // laeuft der Abgleich einfach ohne Anzeige in der Statusleiste.
    val askForNotifications = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    fun startingSync(start: () -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            askForNotifications.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        start()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Synchronisation") },
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
                    text = SyncClock.formatForDisplay(viewModel.lastSyncAt)
                        ?.let { "Zuletzt synchronisiert: $it" }
                        ?: "Noch nie synchronisiert — der erste Lauf holt den vollständigen Bestand.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            viewModel.progress?.let { progress ->
                ShelfFormSection(title = "Läuft", icon = Icons.Default.Sync) {
                    Text(
                        text = progress.phase.label,
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
                    onClick = { startingSync { viewModel.runSync() } },
                    modifier = Modifier.weight(1f),
                    shape = PillShape,
                    // Bewusst erst nach der Vorschau: niemand soll Löschungen
                    // bestätigen, die er nicht gesehen hat.
                    enabled = !viewModel.isBusy && viewModel.preview != null
                ) {
                    Text("Synchronisieren", fontWeight = FontWeight.Bold)
                }
            }

            ShelfFormSection(title = "Einzelne Richtung", icon = Icons.Default.Sync) {
                Text(
                    "Beide Richtungen sind der Regelfall. Einzeln ist nützlich, wenn " +
                        "nur eine Seite stimmen soll — etwa nach einem Fehlversuch.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = { startingSync { viewModel.runPullOnly() } },
                        modifier = Modifier.weight(1f),
                        shape = PillShape,
                        enabled = !viewModel.isBusy
                    ) {
                        Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Shelf → App")
                    }
                    OutlinedButton(
                        onClick = { startingSync { viewModel.runPushOnly() } },
                        modifier = Modifier.weight(1f),
                        shape = PillShape,
                        enabled = !viewModel.isBusy
                    ) {
                        Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("App → Shelf")
                    }
                }
            }

            if (viewModel.preview == null && !viewModel.isBusy) {
                Text(
                    "Die Synchronisation startet erst nach einer Vorschau.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            TextButton(
                onClick = { viewModel.loadPreview(full = true) },
                enabled = !viewModel.isBusy
            ) {
                Text("Vollständig synchronisieren")
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

        CountRow(Icons.Default.CloudUpload, "App → Shelf", preview.outgoing)
        if (preview.toCreate > 0) DetailRow("neu", preview.toCreate)
        if (preview.toUpdate > 0) DetailRow("geändert", preview.toUpdate)
        if (preview.toDeleteRemote > 0) DetailRow("gelöscht", preview.toDeleteRemote)
        if (preview.toPushWatched > 0) DetailRow("gesehen", preview.toPushWatched)

        Spacer(Modifier.height(4.dp))
        CountRow(Icons.Default.CloudDownload, "Shelf → App", preview.incoming)
        if (preview.incomingNew > 0) DetailRow("neu", preview.incomingNew)
        if (preview.incomingUpdated > 0) DetailRow("geändert", preview.incomingUpdated)
        if (preview.incomingDeleted > 0) DetailRow("gelöscht", preview.incomingDeleted)

        if (preview.keptLocal > 0) {
            Spacer(Modifier.height(4.dp))
            CountRow(Icons.Default.Shield, "Behält lokale Änderung", preview.keptLocal)
        }

        if (preview.hasDeletions) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Die Synchronisation entfernt Filme. Das lässt sich nicht rückgängig machen.",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.error
            )
        }

        if (preview.items.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))
            preview.items.forEach { PreviewItemRow(it) }
            if (preview.overflow > 0) {
                Text(
                    "… und ${preview.overflow} weitere",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Ein Posten der Vorschau. Die Richtung steht als Pfeil davor, damit auf einen
 * Blick erkennbar ist, welche Seite sich ändert.
 */
@Composable
private fun PreviewItemRow(item: SyncPreviewItem) {
    Row(modifier = Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.Top) {
        Icon(
            imageVector = if (item.direction == SyncDirection.PULL) Icons.Default.CloudDownload
            else Icons.Default.CloudUpload,
            contentDescription = null,
            modifier = Modifier.size(14.dp).padding(top = 3.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = buildString {
                    append(item.title ?: "Ohne Titel")
                    item.year?.takeIf { it > 0 }?.let { append(" ($it)") }
                },
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val detail = when (item.action) {
                SyncAction.NEW -> "Neu"
                SyncAction.DELETED -> "Gelöscht"
                SyncAction.KEPT_LOCAL -> "lokale Änderung behalten"
                SyncAction.UPDATED -> if (item.changes.isEmpty()) "Update"
                else item.changes.joinToString(", ")
            }
            Text(
                detail,
                style = MaterialTheme.typography.labelSmall,
                color = if (item.action == SyncAction.DELETED) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ResultSection(result: SyncResult, viewModel: SyncViewModel) {
    ShelfFormSection(title = "Ergebnis", icon = Icons.Default.Sync) {
        CountRow(Icons.Default.CloudUpload, "Hochgeladen", result.push.total)
        // Eigene Zeile: in der Summe ginge unter, ob eine gesetzte Markierung
        // tatsaechlich hinausgegangen ist.
        if (result.push.watched > 0) DetailRow("davon gesehen", result.push.watched)
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
