package info.movieshelf.ui.jellyfin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import info.movieshelf.MovieShelfApplication
import info.movieshelf.R
import info.movieshelf.data.jellyfin.JellyfinProgress
import info.movieshelf.ui.components.ShelfFormSection
import info.movieshelf.ui.theme.PillShape

/**
 * Jellyfin-Import: anmelden, Bibliotheken wählen, übernehmen.
 *
 * Die übernommenen Titel landen zunächst in der lokalen Sammlung. Im
 * Shelf-Modus gehen sie erst beim nächsten Abgleich zum Server — darauf weist
 * der Screen hin, sonst sucht man sie dort vergeblich.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JellyfinScreen(
    onBack: () -> Unit,
    isShelfMode: Boolean
) {
    val context = LocalContext.current
    val app = context.applicationContext as MovieShelfApplication
    val viewModel: JellyfinViewModel = viewModel(
        factory = JellyfinViewModel.Factory(
            client = app.jellyfinClient,
            importer = app.jellyfinImporter,
            dataStoreManager = app.dataStore
        )
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.jellyfin_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back)
                        )
                    }
                },
                actions = {
                    if (viewModel.account != null && !viewModel.isBusy) {
                        TextButton(onClick = { viewModel.logout() }) {
                            Text(stringResource(R.string.jellyfin_disconnect))
                        }
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            viewModel.error?.let { error ->
                Card(
                    shape = PillShape,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            error.asString(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            if (viewModel.account == null) {
                LoginSection(viewModel)
            } else {
                ConnectedSection(viewModel, isShelfMode)
            }
        }
    }

    viewModel.result?.let { result ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissResult() },
            title = { Text(stringResource(R.string.jellyfin_result_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.jellyfin_result_imported, result.imported))
                    Text(stringResource(R.string.jellyfin_result_skipped, result.skipped))
                    if (result.failed > 0) {
                        Text(stringResource(R.string.jellyfin_result_failed, result.failed))
                    }
                    if (isShelfMode && result.imported > 0) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.jellyfin_result_sync_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    // Nur die ersten Meldungen: bei einem kaputten Server
                    // stünden hier sonst hunderte Zeilen.
                    result.errors.take(5).forEach { message ->
                        Text(
                            message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (result.errors.size > 5) {
                        Text(
                            stringResource(R.string.jellyfin_result_more_errors, result.errors.size - 5),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissResult() }) {
                    Text(stringResource(R.string.common_ok))
                }
            }
        )
    }
}

@Composable
private fun LoginSection(viewModel: JellyfinViewModel) {
    ShelfFormSection(title = stringResource(R.string.jellyfin_server)) {
        Text(
            stringResource(R.string.jellyfin_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = viewModel.serverUrl,
            onValueChange = { viewModel.serverUrl = it },
            label = { Text(stringResource(R.string.jellyfin_url)) },
            placeholder = { Text("http://192.168.1.10:8096") },
            singleLine = true,
            enabled = !viewModel.isBusy,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Next
            ),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = viewModel.username,
            onValueChange = { viewModel.username = it },
            label = { Text(stringResource(R.string.jellyfin_username)) },
            singleLine = true,
            enabled = !viewModel.isBusy,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = viewModel.password,
            onValueChange = { viewModel.password = it },
            label = { Text(stringResource(R.string.jellyfin_password)) },
            singleLine = true,
            enabled = !viewModel.isBusy,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { viewModel.login() },
            enabled = !viewModel.isBusy && viewModel.serverUrl.isNotBlank() && viewModel.username.isNotBlank(),
            shape = PillShape,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (viewModel.isBusy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text(stringResource(R.string.jellyfin_connect), fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ConnectedSection(viewModel: JellyfinViewModel, isShelfMode: Boolean) {
    val account = viewModel.account ?: return

    ShelfFormSection(title = stringResource(R.string.jellyfin_connected)) {
        Text(account.baseUrl, style = MaterialTheme.typography.bodyMedium)
        Text(
            account.userName,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    ShelfFormSection(title = stringResource(R.string.jellyfin_libraries)) {
        if (viewModel.libraries.isEmpty()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (viewModel.isBusy) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                }
                Text(
                    stringResource(
                        if (viewModel.isBusy) R.string.jellyfin_loading_libraries
                        else R.string.jellyfin_no_libraries
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            viewModel.libraries.forEach { library ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = library.id in viewModel.selectedLibraries,
                        onCheckedChange = { viewModel.toggleLibrary(library.id) },
                        enabled = !viewModel.isBusy
                    )
                    Icon(
                        imageVector = if (library.type == "tvshows") Icons.Default.Tv else Icons.Default.Movie,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(library.name, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }

    ShelfFormSection(title = stringResource(R.string.jellyfin_options)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.jellyfin_verify_tmdb), style = MaterialTheme.typography.bodyLarge)
                Text(
                    stringResource(
                        if (viewModel.hasTmdbKey) R.string.jellyfin_verify_tmdb_hint
                        else R.string.jellyfin_verify_tmdb_no_key
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = viewModel.verifyWithTmdb && viewModel.hasTmdbKey,
                onCheckedChange = { viewModel.verifyWithTmdb = it },
                enabled = viewModel.hasTmdbKey && !viewModel.isBusy
            )
        }

        if (isShelfMode) {
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.jellyfin_shelf_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    val progress = viewModel.progress
    if (progress != null) {
        ShelfFormSection(title = stringResource(R.string.jellyfin_running)) {
            Text(
                text = when (progress.phase) {
                    JellyfinProgress.Phase.LIBRARIES -> stringResource(R.string.jellyfin_phase_libraries)
                    JellyfinProgress.Phase.ITEMS -> progress.title
                    JellyfinProgress.Phase.DONE -> stringResource(R.string.jellyfin_phase_done)
                },
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(8.dp))
            if (progress.total > 0) {
                LinearProgressIndicator(
                    progress = { progress.current.toFloat() / progress.total.toFloat() },
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                    strokeCap = StrokeCap.Round,
                    gapSize = 0.dp,
                    drawStopIndicator = {}
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.jellyfin_progress_count, progress.current, progress.total),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                    strokeCap = StrokeCap.Round
                )
            }
        }
    }

    Button(
        onClick = { viewModel.startImport() },
        enabled = !viewModel.isBusy && viewModel.selectedLibraries.isNotEmpty(),
        shape = PillShape,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.jellyfin_start_import), fontWeight = FontWeight.Bold)
    }

    Spacer(Modifier.height(80.dp))
}
