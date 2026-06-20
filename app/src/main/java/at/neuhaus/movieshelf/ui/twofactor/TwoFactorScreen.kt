package at.neuhaus.movieshelf.ui.twofactor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TwoFactorScreen(
    onBack: () -> Unit,
    viewModel: TwoFactorViewModel = viewModel()
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var showDisableDialog by remember { mutableStateOf(false) }

    // Fehler als Snackbar anzeigen und danach zurücksetzen.
    LaunchedEffect(viewModel.error) {
        viewModel.error?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Zwei-Faktor-Authentifizierung") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Zurück"
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            val inSetup = viewModel.secret != null

            when {
                // Einrichtungsschritt: Secret + Code-Eingabe
                inSetup -> {
                    SetupStep(viewModel = viewModel)
                }

                // Aktiv und nicht gerade im Einrichten
                viewModel.isEnabled -> {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "2FA ist aktiv",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Dein Konto ist durch Zwei-Faktor-Authentifizierung geschützt.",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }

                    // Wiederherstellungscodes direkt nach der Bestätigung anzeigen.
                    if (viewModel.justConfirmed) {
                        RecoveryCodes(codes = viewModel.recoveryCodes)
                    }

                    OutlinedButton(
                        onClick = { showDisableDialog = true },
                        enabled = !viewModel.isLoading,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("2FA deaktivieren")
                    }
                }

                // Nicht aktiv, noch nicht im Einrichten
                else -> {
                    Text(
                        text = "Zwei-Faktor-Authentifizierung (2FA) schützt dein Konto " +
                            "zusätzlich: Neben deinem Passwort benötigst du beim Login einen " +
                            "Einmal-Code aus einer Authenticator-App.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Button(
                        onClick = { viewModel.startEnable() },
                        enabled = !viewModel.isLoading,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("2FA einrichten")
                    }
                }
            }

            if (viewModel.isLoading) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }

    if (showDisableDialog) {
        AlertDialog(
            onDismissRequest = { showDisableDialog = false },
            title = { Text("2FA deaktivieren?") },
            text = {
                Text(
                    "Möchtest du die Zwei-Faktor-Authentifizierung wirklich deaktivieren? " +
                        "Dein Konto ist danach nur noch durch das Passwort geschützt."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDisableDialog = false
                        viewModel.disable()
                    }
                ) {
                    Text("Deaktivieren")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDisableDialog = false }) {
                    Text("Abbrechen")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SetupStep(viewModel: TwoFactorViewModel) {
    Text(
        text = "Einrichtung",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold
    )
    Text(
        text = "Diesen Schlüssel in deiner Authenticator-App " +
            "(z. B. Google Authenticator) eintragen:",
        style = MaterialTheme.typography.bodyMedium
    )

    val secret = viewModel.secret
    if (secret != null) {
        Card(modifier = Modifier.fillMaxWidth()) {
            SelectionContainer {
                Text(
                    text = secret,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }

    viewModel.otpauthUrl?.let { url ->
        Text(
            text = "Oder per Setup-Link:",
            style = MaterialTheme.typography.bodySmall
        )
        SelectionContainer {
            Text(
                text = url,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace
            )
        }
    }

    Spacer(modifier = Modifier.height(4.dp))

    OutlinedTextField(
        value = viewModel.code,
        onValueChange = { input ->
            // Nur Ziffern, maximal 6 Stellen.
            if (input.length <= 6 && input.all { it.isDigit() }) {
                viewModel.code = input
            }
        },
        label = { Text("6-stelliger Code") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth()
    )

    Button(
        onClick = { viewModel.confirm() },
        enabled = !viewModel.isLoading && viewModel.code.length == 6,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("Bestätigen")
    }
}

@Composable
private fun RecoveryCodes(codes: List<String>?) {
    if (codes.isNullOrEmpty()) return
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Wiederherstellungscodes",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Sicher aufbewahren! Mit diesen Codes kannst du dich anmelden, " +
                    "falls du keinen Zugriff auf deine Authenticator-App hast. " +
                    "Jeder Code ist nur einmal gültig.",
                style = MaterialTheme.typography.bodySmall
            )
            SelectionContainer {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    codes.forEach { code ->
                        Text(
                            text = code,
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}
