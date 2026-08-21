package info.movieshelf.ui.access

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import info.movieshelf.MovieShelfApplication
import info.movieshelf.R
import info.movieshelf.data.model.AccessToken
import info.movieshelf.ui.components.ShelfFormSection
import info.movieshelf.ui.theme.PillShape

/**
 * Zugriffe auf das eigene Konto: angemeldete Geräte und verbundene Apps.
 *
 * Gegenstück zum gleichnamigen Abschnitt der Weboberfläche. Wer ein Gerät
 * verliert, soll es hier abmelden können, ohne an einen Rechner zu müssen.
 *
 * Browser-Sitzungen und Freigabe-Links fehlen bewusst: für sie gibt es keine
 * API, und sie gehören eher an den Ort, an dem sie entstehen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccessScreen(onBack: () -> Unit) {
    val app = LocalContext.current.applicationContext as MovieShelfApplication
    val viewModel: AccessViewModel = viewModel(factory = AccessViewModel.Factory(app.accessRepository))

    var revokeTarget by remember { mutableStateOf<AccessToken?>(null) }
    var confirmOthers by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.load() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.access_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back)
                        )
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
            when {
                viewModel.isLoading && viewModel.tokens.isEmpty() -> Box(
                    Modifier.fillMaxWidth().padding(top = 64.dp),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }

                viewModel.error != null -> ErrorBlock(
                    message = viewModel.error!!.asString(),
                    onRetry = { viewModel.load() }
                )

                else -> {
                    val devices = viewModel.tokens.filter { it.type != "oauth" }
                    val apps = viewModel.tokens.filter { it.type == "oauth" }

                    ShelfFormSection(title = stringResource(R.string.access_devices)) {
                        if (devices.isEmpty()) {
                            Text(
                                stringResource(R.string.access_no_devices),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        devices.forEach { token ->
                            AccessRow(
                                token = token,
                                icon = Icons.Default.Smartphone,
                                onRevoke = { revokeTarget = token }
                            )
                        }
                    }

                    if (apps.isNotEmpty()) {
                        ShelfFormSection(title = stringResource(R.string.access_apps)) {
                            apps.forEach { token ->
                                AccessRow(
                                    token = token,
                                    icon = Icons.AutoMirrored.Filled.Login,
                                    onRevoke = { revokeTarget = token }
                                )
                            }
                        }
                    }

                    // Nur anbieten, wenn es überhaupt etwas anderes gibt als
                    // dieses Gerät — sonst wäre der Knopf wirkungslos.
                    if (viewModel.tokens.any { !it.isCurrent }) {
                        OutlinedButton(
                            onClick = { confirmOthers = true },
                            enabled = !viewModel.isBusy,
                            shape = PillShape,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.access_revoke_others))
                        }
                    }

                    Text(
                        stringResource(R.string.access_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(64.dp))
        }
    }

    revokeTarget?.let { token ->
        AlertDialog(
            onDismissRequest = { revokeTarget = null },
            title = { Text(stringResource(R.string.access_revoke_title)) },
            text = {
                Text(
                    stringResource(
                        if (token.isCurrent) R.string.access_revoke_current_question
                        else R.string.access_revoke_question,
                        token.name.orEmpty()
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.revoke(token)
                    revokeTarget = null
                }) {
                    Text(stringResource(R.string.access_revoke), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { revokeTarget = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    if (confirmOthers) {
        AlertDialog(
            onDismissRequest = { confirmOthers = false },
            title = { Text(stringResource(R.string.access_revoke_others)) },
            text = { Text(stringResource(R.string.access_revoke_others_question)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.revokeOthers()
                    confirmOthers = false
                }) {
                    Text(stringResource(R.string.access_revoke), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmOthers = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }
}

@Composable
private fun AccessRow(
    token: AccessToken,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onRevoke: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = token.name.orEmpty(),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    // Ohne weight nimmt der Name die ganze Zeile und laesst dem
                    // Etikett eine Spalte von wenigen Pixeln, in die es sich
                    // Buchstabe fuer Buchstabe umbricht. `fill = false`, damit
                    // ein kurzer Name das Etikett nicht nach rechts schiebt.
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (token.isCurrent) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.access_this_device),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary,
                        // Das Etikett gibt nicht nach: lieber kuerzt der Name.
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
            Text(
                text = token.lastUsedAt?.take(10)
                    ?.let { stringResource(R.string.access_last_used, it) }
                    ?: stringResource(R.string.access_never_used),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        TextButton(onClick = onRevoke) {
            Text(stringResource(R.string.access_revoke), color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun ErrorBlock(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.CloudOff,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.outline
        )
        Spacer(Modifier.height(16.dp))
        Text(message, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRetry, shape = PillShape) {
            Text(stringResource(R.string.common_retry), fontWeight = FontWeight.Bold)
        }
    }
}
