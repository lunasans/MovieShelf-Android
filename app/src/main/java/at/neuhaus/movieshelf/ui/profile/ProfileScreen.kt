package at.neuhaus.movieshelf.ui.profile

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import android.os.Build
import at.neuhaus.movieshelf.data.local.DataStoreManager
import at.neuhaus.movieshelf.data.local.ThemeMode
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onBack: () -> Unit,
    onListsClick: () -> Unit = {},
    onTwoFactorClick: () -> Unit = {},
    onSyncClick: () -> Unit = {},
    /** Eigenstaendiger Betrieb: kein Konto, kein Abgleich, keine 2FA. */
    isStandalone: Boolean = false,
    onConnectShelfClick: () -> Unit = {},
    onAboutClick: () -> Unit = {}
) {
    val viewModel: ProfileViewModel = viewModel()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val dataStoreManager = remember { DataStoreManager(context) }
    val scope = rememberCoroutineScope()
    val dynamicColor by dataStoreManager.dynamicColor.collectAsState(initial = false)
    val themeMode by dataStoreManager.themeMode.collectAsState(initial = ThemeMode.DARK)
    val tmdbApiKey by dataStoreManager.tmdbApiKey.collectAsState(initial = null)
    val supportsDynamic = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    LaunchedEffect(viewModel.error) {
        viewModel.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.error = null
        }
    }

    LaunchedEffect(viewModel.successMessage) {
        viewModel.successMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.successMessage = null
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Profil") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
                actions = {
                    if (isStandalone) {
                        // Ohne Konto gibt es nichts zu speichern.
                    } else if (viewModel.isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp).padding(end = 16.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        IconButton(onClick = { viewModel.updateProfile() }) {
                            Icon(Icons.Default.Save, contentDescription = "Speichern")
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (viewModel.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
                    .padding(top = 24.dp, bottom = 120.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    modifier = Modifier.size(100.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                Spacer(Modifier.height(32.dp))

                if (isStandalone) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text("Nur auf diesem Gerät", fontWeight = FontWeight.Bold)
                            Text(
                                "Deine Sammlung liegt auf dem Telefon. Verbindest du eine Shelf, " +
                                    "wird dein Bestand hochgeladen — er geht dabei nicht verloren.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(
                                onClick = onConnectShelfClick,
                                modifier = Modifier.fillMaxWidth(),
                                shape = at.neuhaus.movieshelf.ui.theme.PillShape
                            ) {
                                Text("Shelf verbinden", fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // TMDb-Schluessel: nur ohne Shelf noetig, weil dort deren
                    // Proxy die Suche uebernimmt.
                    var keyInput by remember(tmdbApiKey) { mutableStateOf(tmdbApiKey.orEmpty()) }
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text("TMDb-Schlüssel", fontWeight = FontWeight.Bold)
                            Text(
                                "Für die Filmsuche brauchst du einen eigenen, kostenlosen Schlüssel " +
                                    "von themoviedb.org. Er wird verschlüsselt auf diesem Gerät gespeichert " +
                                    "und nur an TMDb geschickt.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            OutlinedTextField(
                                value = keyInput,
                                onValueChange = { keyInput = it },
                                label = { Text("API-Schlüssel") },
                                placeholder = { Text("z.B. 8f2c…") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Button(
                                    onClick = { dataStoreManager.saveTmdbApiKey(keyInput) },
                                    modifier = Modifier.weight(1f),
                                    shape = at.neuhaus.movieshelf.ui.theme.PillShape,
                                    enabled = keyInput.isNotBlank() && keyInput != tmdbApiKey
                                ) {
                                    Text("Speichern", fontWeight = FontWeight.Bold)
                                }
                                if (!tmdbApiKey.isNullOrBlank()) {
                                    OutlinedButton(
                                        onClick = {
                                            dataStoreManager.saveTmdbApiKey(null)
                                            keyInput = ""
                                        },
                                        modifier = Modifier.weight(1f),
                                        shape = at.neuhaus.movieshelf.ui.theme.PillShape
                                    ) {
                                        Text("Entfernen")
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                }

                if (!isStandalone) OutlinedTextField(
                    value = viewModel.name,
                    onValueChange = { viewModel.name = it },
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                    singleLine = true
                )

                if (!isStandalone) Spacer(Modifier.height(16.dp))

                if (!isStandalone) OutlinedTextField(
                    value = viewModel.email,
                    onValueChange = { viewModel.email = it },
                    label = { Text("E-Mail") },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                    singleLine = true
                )

                Spacer(Modifier.height(32.dp))

                // Meine Listen / Wunschliste
                Card(
                    onClick = onListsClick,
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.AutoMirrored.Filled.PlaylistPlay, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Meine Listen", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                            Text(
                                "Eigene Listen & Wunschliste ansehen",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                            )
                        }
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Abgleich mit der Shelf
                if (!isStandalone) Card(
                    onClick = onSyncClick,
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Sync, contentDescription = null)
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Synchronisation", fontWeight = FontWeight.Bold)
                            Text(
                                "Lokale Sammlung mit deiner MovieShelf abgleichen",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
                    }
                }

                Spacer(Modifier.height(16.dp))

                if (!isStandalone) Card(
                    onClick = onTwoFactorClick,
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Security, contentDescription = null)
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Zwei-Faktor-Authentifizierung", fontWeight = FontWeight.Bold)
                            Text(
                                if (viewModel.twoFactorEnabled) "Aktiv – tippen zum Verwalten" else "Nicht aktiv – tippen zum Einrichten",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Hell/Dunkel. Standard ist dunkel wie die Web-Oberfläche;
                // "System" bleibt für alle, die es Android-üblich wollen.
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.DarkMode, contentDescription = null)
                            Spacer(Modifier.width(16.dp))
                            Column {
                                Text("Erscheinungsbild", fontWeight = FontWeight.Bold)
                                Text(
                                    "MovieShelf ist auf den dunklen Look ausgelegt",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                            ThemeMode.entries.forEachIndexed { index, mode ->
                                SegmentedButton(
                                    selected = themeMode == mode,
                                    onClick = { scope.launch { dataStoreManager.saveThemeMode(mode) } },
                                    shape = SegmentedButtonDefaults.itemShape(
                                        index = index,
                                        count = ThemeMode.entries.size
                                    )
                                ) {
                                    Text(mode.label)
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Material You (Dynamic Color)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Palette, contentDescription = null)
                            Spacer(Modifier.width(16.dp))
                            Column {
                                Text("Material You", fontWeight = FontWeight.Bold)
                                Text(
                                    if (supportsDynamic) "Systemfarben verwenden" else "Erst ab Android 12 verfügbar",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = dynamicColor && supportsDynamic,
                            enabled = supportsDynamic,
                            onCheckedChange = { scope.launch { dataStoreManager.saveDynamicColor(it) } }
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                Card(
                    onClick = onAboutClick,
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null)
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Über MovieShelf", fontWeight = FontWeight.Bold)
                            Text(
                                "Fassung, Lizenzen und Hinweise",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline
                        )
                    }
                }

                Spacer(Modifier.height(48.dp))

                Button(
                    onClick = { viewModel.updateProfile() },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    enabled = !viewModel.isSaving
                ) {
                    if (viewModel.isSaving) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Text("Änderungen speichern")
                    }
                }
            }
        }
    }
}
