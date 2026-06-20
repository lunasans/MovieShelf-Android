package at.neuhaus.movieshelf.ui.setup

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import at.neuhaus.movieshelf.R
import at.neuhaus.movieshelf.data.api.RetrofitClient
import at.neuhaus.movieshelf.data.local.DataStoreManager
import kotlinx.coroutines.launch
import java.util.Calendar

class SetupViewModel(private val dataStoreManager: DataStoreManager) : ViewModel() {
    var url by mutableStateOf("")
    var isSaving by mutableStateOf(false)
    var isTesting by mutableStateOf(false)
    var connectionError by mutableStateOf<String?>(null)
    var connectionSuccess by mutableStateOf(false)

    val showEmulatorHint by derivedStateOf {
        url.contains("localhost") || url.contains("127.0.0.1")
    }

    private fun formatUrl(input: String): String {
        val trimmed = input.trim()
        var formatted = when {
            trimmed.startsWith("http://", ignoreCase = true) ||
                trimmed.startsWith("https://", ignoreCase = true) -> trimmed
            // Lokale Adressen via HTTP, alles andere standardmäßig über HTTPS.
            isLocalHost(trimmed.substringBefore("/").substringBefore(":")) -> "http://$trimmed"
            else -> "https://$trimmed"
        }
        if (formatted.contains("127.0.0.1")) {
            formatted = formatted.replace("127.0.0.1", "10.0.2.2")
        } else if (formatted.contains("localhost")) {
            formatted = formatted.replace("localhost", "10.0.2.2")
        }
        return if (formatted.endsWith("/")) formatted else "$formatted/"
    }

    /**
     * Private/lokale Adressen -> HTTP-Default (selbst gehosteter LAN-Server),
     * alles andere -> HTTPS-Default. Nur eine Voreinstellung; der Nutzer kann
     * jederzeit explizit http:// oder https:// eintippen.
     */
    private fun isLocalHost(host: String): Boolean {
        if (host == "localhost" || host == "127.0.0.1" || host == "10.0.2.2") return true
        if (host.endsWith(".local")) return true
        // Private IPv4-Bereiche (RFC 1918) + Link-Local
        if (host.startsWith("192.168.") || host.startsWith("10.") || host.startsWith("169.254.")) return true
        if (host.startsWith("172.")) {
            val second = host.removePrefix("172.").substringBefore(".").toIntOrNull()
            if (second != null && second in 16..31) return true
        }
        return false
    }

    fun testConnection() {
        if (url.isBlank()) return
        val testUrl = formatUrl(url)
        
        viewModelScope.launch {
            isTesting = true
            connectionError = null
            connectionSuccess = false
            try {
                if (RetrofitClient.initialize(testUrl)) {
                    RetrofitClient.api.getServerInfo()
                    connectionSuccess = true
                } else {
                    connectionError = "Ungültiges URL-Format"
                }
            } catch (e: Exception) {
                connectionError = "Server nicht erreichbar: ${e.message}"
            } finally {
                isTesting = false
            }
        }
    }

    fun onSaveClick(onSuccess: () -> Unit) {
        if (url.isBlank()) return
        val finalUrl = formatUrl(url)
        
        viewModelScope.launch {
            isSaving = true
            dataStoreManager.saveServerUrl(finalUrl)
            RetrofitClient.initialize(finalUrl)
            isSaving = false
            onSuccess()
        }
    }

    class Factory(private val dataStoreManager: DataStoreManager) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return SetupViewModel(dataStoreManager) as T
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(
    dataStoreManager: DataStoreManager,
    onSetupComplete: () -> Unit
) {
    val currentYear = Calendar.getInstance().get(Calendar.YEAR)
    val viewModel: SetupViewModel = viewModel(factory = SetupViewModel.Factory(dataStoreManager))
    
    // Prüfen, ob bereits eine URL gespeichert ist, um den Zurück-Button anzuzeigen
    val savedUrl by dataStoreManager.serverUrl.collectAsState(initial = null)
    val canGoBack = !savedUrl.isNullOrBlank()

    Scaffold(
        topBar = {
            if (canGoBack) {
                TopAppBar(
                    title = { },
                    navigationIcon = {
                        IconButton(onClick = onSetupComplete) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Image(
                    painter = painterResource(id = R.drawable.logo),
                    contentDescription = "MovieShelf Logo",
                    modifier = Modifier
                        .height(120.dp)
                        .padding(bottom = 32.dp)
                )

                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Willkommen!",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Bitte gib die URL deines MovieShelf-Servers ein, um zu starten.",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        Spacer(Modifier.height(24.dp))
                        
                        OutlinedTextField(
                            value = viewModel.url,
                            onValueChange = { 
                                viewModel.url = it
                                viewModel.connectionSuccess = false
                                viewModel.connectionError = null
                            },
                            label = { Text("Server URL") },
                            placeholder = { Text("z.B. 10.0.2.2:8000") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Default.Link, contentDescription = null) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                            isError = viewModel.connectionError != null,
                            supportingText = {
                                if (viewModel.showEmulatorHint) {
                                    Text("Tipp: Nutze '10.0.2.2' für den Emulator.")
                                }
                            }
                        )
                        
                        AnimatedVisibility(visible = viewModel.connectionError != null) {
                            Row(
                                modifier = Modifier.padding(top = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(viewModel.connectionError ?: "", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                            }
                        }

                        AnimatedVisibility(visible = viewModel.connectionSuccess) {
                            Row(
                                modifier = Modifier.padding(top = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF4CAF50), modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Verbindung erfolgreich!", color = Color(0xFF4CAF50), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(Modifier.height(24.dp))
                        
                        Row(modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = { viewModel.testConnection() },
                                modifier = Modifier.weight(1f),
                                enabled = viewModel.url.isNotBlank() && !viewModel.isTesting && !viewModel.isSaving
                            ) {
                                if (viewModel.isTesting) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                } else {
                                    Text("Testen")
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Button(
                                onClick = { viewModel.onSaveClick(onSetupComplete) },
                                modifier = Modifier.weight(1f),
                                enabled = viewModel.url.isNotBlank() && !viewModel.isSaving
                            ) {
                                if (viewModel.isSaving) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                                } else {
                                    Text("Speichern")
                                }
                            }
                        }
                    }
                }
                
                Spacer(Modifier.height(32.dp))
                Text(
                    text = "© $currentYear René Neuhaus",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}
