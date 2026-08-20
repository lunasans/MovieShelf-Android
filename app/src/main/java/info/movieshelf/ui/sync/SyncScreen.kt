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
import androidx.compose.ui.res.stringResource
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
import info.movieshelf.R
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
                title = { Text(stringResource(R.string.sync_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
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
            ShelfFormSection(title = stringResource(R.string.sync_status), icon = Icons.Default.Sync) {
                Text(
                    text = SyncClock.formatForDisplay(viewModel.lastSyncAt)
                        ?.let { stringResource(R.string.sync_last, it) }
                        ?: stringResource(R.string.sync_never),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            viewModel.progress?.let { progress ->
                ShelfFormSection(title = stringResource(R.string.sync_running), icon = Icons.Default.Sync) {
                    Text(
                        text = stringResource(progress.phase.labelRes),
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
                ShelfFormSection(title = stringResource(R.string.sync_errors), icon = Icons.Default.ErrorOutline) {
                    Text(message.asString(), style = MaterialTheme.typography.bodyMedium)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { viewModel.loadPreview() },
                    modifier = Modifier.weight(1f),
                    shape = PillShape,
                    enabled = !viewModel.isBusy
                ) {
                    Text(stringResource(R.string.sync_preview), fontWeight = FontWeight.SemiBold)
                }
                Button(
                    onClick = { startingSync { viewModel.runSync() } },
                    modifier = Modifier.weight(1f),
                    shape = PillShape,
                    // Bewusst erst nach der Vorschau: niemand soll Löschungen
                    // bestätigen, die er nicht gesehen hat.
                    enabled = !viewModel.isBusy && viewModel.preview != null
                ) {
                    // Zeigt an, was der Lauf tun wird: nach einer vollen
                    // Vorschau ist es ein voller Abgleich.
                    Text(
                        stringResource(
                            if (viewModel.previewIsFull) R.string.sync_full else R.string.sync_start
                        ),
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            ShelfFormSection(title = stringResource(R.string.sync_single_direction), icon = Icons.Default.Sync) {
                Text(
                    stringResource(R.string.sync_direction_hint),
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
                        Text(stringResource(R.string.sync_shelf_to_app))
                    }
                    OutlinedButton(
                        onClick = { startingSync { viewModel.runPushOnly() } },
                        modifier = Modifier.weight(1f),
                        shape = PillShape,
                        enabled = !viewModel.isBusy
                    ) {
                        Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.sync_app_to_shelf))
                    }
                }
            }

            if (viewModel.preview == null && !viewModel.isBusy) {
                Text(
                    stringResource(R.string.sync_needs_preview),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            TextButton(
                onClick = { viewModel.loadPreview(full = true) },
                enabled = !viewModel.isBusy
            ) {
                Text(stringResource(R.string.sync_full_preview))
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun PreviewSection(preview: SyncPreview) {
    ShelfFormSection(
        title = stringResource(if (preview.isDelta) R.string.sync_preview_delta else R.string.sync_preview_full),
        icon = Icons.Default.Sync
    ) {
        if (preview.isEmpty) {
            Text(stringResource(R.string.sync_up_to_date), style = MaterialTheme.typography.bodyMedium)
            return@ShelfFormSection
        }

        CountRow(Icons.Default.CloudUpload, stringResource(R.string.sync_app_to_shelf), preview.outgoing)
        if (preview.toCreate > 0) DetailRow(stringResource(R.string.sync_new), preview.toCreate)
        if (preview.toUpdate > 0) DetailRow(stringResource(R.string.sync_changed), preview.toUpdate)
        if (preview.toDeleteRemote > 0) DetailRow(stringResource(R.string.sync_deleted), preview.toDeleteRemote)
        if (preview.toPushWatched > 0) DetailRow(stringResource(R.string.sync_watched), preview.toPushWatched)
        if (preview.toPushUserRatings > 0) {
            DetailRow(stringResource(R.string.sync_ratings), preview.toPushUserRatings)
        }
        if (preview.toPushEpisodesWatched > 0) {
            DetailRow(stringResource(R.string.sync_episodes_watched), preview.toPushEpisodesWatched)
        }

        Spacer(Modifier.height(4.dp))
        CountRow(Icons.Default.CloudDownload, stringResource(R.string.sync_shelf_to_app), preview.incoming)
        if (preview.incomingNew > 0) DetailRow(stringResource(R.string.sync_new), preview.incomingNew)
        if (preview.incomingUpdated > 0) DetailRow(stringResource(R.string.sync_changed), preview.incomingUpdated)
        if (preview.incomingDeleted > 0) DetailRow(stringResource(R.string.sync_deleted), preview.incomingDeleted)

        if (preview.keptLocal > 0) {
            Spacer(Modifier.height(4.dp))
            CountRow(Icons.Default.Shield, stringResource(R.string.sync_keeps_local), preview.keptLocal)
        }

        if (preview.hasDeletions) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.sync_deletes_warning),
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
                    stringResource(R.string.sync_and_more, preview.overflow),
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
                    append(item.title ?: stringResource(R.string.common_no_title))
                    item.year?.takeIf { it > 0 }?.let { append(" ($it)") }
                },
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val detail = when (item.action) {
                SyncAction.NEW -> stringResource(R.string.sync_action_new)
                SyncAction.DELETED -> stringResource(R.string.sync_action_deleted)
                SyncAction.KEPT_LOCAL -> stringResource(R.string.sync_action_kept_local)
                SyncAction.UPDATED -> if (item.changes.isEmpty()) stringResource(R.string.sync_action_updated)
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
    ShelfFormSection(title = stringResource(R.string.sync_result), icon = Icons.Default.Sync) {
        CountRow(Icons.Default.CloudUpload, stringResource(R.string.sync_uploaded), result.push.total)
        // Eigene Zeile: in der Summe ginge unter, ob eine gesetzte Markierung
        // tatsaechlich hinausgegangen ist.
        if (result.push.watched > 0) DetailRow(stringResource(R.string.sync_of_which_watched), result.push.watched)
        CountRow(Icons.Default.CloudDownload, stringResource(R.string.sync_applied), result.pull.applied)
        if (result.pull.deleted > 0) CountRow(Icons.Default.DeleteOutline, stringResource(R.string.sync_removed_locally), result.pull.deleted)
        if (result.pull.skipped > 0) CountRow(Icons.Default.Shield, stringResource(R.string.sync_kept_local), result.pull.skipped)

        viewModel.listResult?.let { lists ->
            if (lists.itemsAdded > 0 || lists.itemsRemoved > 0) {
                Spacer(Modifier.height(4.dp))
                DetailRow(stringResource(R.string.sync_list_items_added), lists.itemsAdded)
                DetailRow(stringResource(R.string.sync_list_items_removed), lists.itemsRemoved)
            }
        }

        val errors = result.errors + (viewModel.listResult?.errors ?: emptyList())
        if (errors.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.sync_error_log),
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
                stringResource(R.string.sync_errors_retry),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        TextButton(onClick = { viewModel.dismissResult() }) { Text(stringResource(R.string.common_hide)) }
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
