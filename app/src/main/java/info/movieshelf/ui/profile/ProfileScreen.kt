package info.movieshelf.ui.profile

import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.foundation.layout.PaddingValues
import android.os.Build
import info.movieshelf.R
import info.movieshelf.data.local.DataStoreManager
import info.movieshelf.data.local.ThemeMode
import info.movieshelf.ui.theme.PillShape
import kotlinx.coroutines.launch

/**
 * Profil und Einstellungen.
 *
 * Gegliedert in Abschnitte statt als eine Reihe gleich aussehender Karten:
 * oben die Kontodaten mit ihrer Schaltfläche direkt darunter, dann Sammlung,
 * Sicherheit, Darstellung und zuletzt die App selbst. Was nur in einer
 * Betriebsart gilt, steht in einem eigenen Zweig und nicht als wiederholtes
 * `if` vor jeder einzelnen Zeile.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onBack: () -> Unit,
    onListsClick: () -> Unit = {},
    onTwoFactorClick: () -> Unit = {},
    onJellyfinClick: () -> Unit = {},
    onWishlistClick: () -> Unit = {},
    onAccessClick: () -> Unit = {},
    onSyncClick: () -> Unit = {},
    /** Eigenstaendiger Betrieb: kein Konto, kein Abgleich, keine 2FA. */
    isStandalone: Boolean = false,
    onConnectShelfClick: () -> Unit = {},
    onAboutClick: () -> Unit = {}
) {
    val viewModel: ProfileViewModel = viewModel()

    // Nur mit Shelf: ohne Server gibt es kein Profil zu laden, und der Versuch
    // endete jedes Mal in der Meldung stringResource(R.string.error_profile_not_loaded).
    LaunchedEffect(isStandalone) {
        if (!isStandalone) viewModel.loadProfile()
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val dataStoreManager = remember { DataStoreManager(context) }
    val scope = rememberCoroutineScope()
    val dynamicColor by dataStoreManager.dynamicColor.collectAsState(initial = false)
    val themeMode by dataStoreManager.themeMode.collectAsState(initial = ThemeMode.DARK)
    val tmdbApiKey by dataStoreManager.tmdbApiKey.collectAsState(initial = null)
    val supportsDynamic = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    val snackbarContext = LocalContext.current

    LaunchedEffect(viewModel.error) {
        viewModel.error?.let {
            snackbarHostState.showSnackbar(it.asString(snackbarContext))
            viewModel.error = null
        }
    }

    LaunchedEffect(viewModel.successMessage) {
        viewModel.successMessage?.let {
            snackbarHostState.showSnackbar(it.asString(snackbarContext))
            viewModel.successMessage = null
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                // Ohne Konto gibt es kein Profil, nur Einstellungen.
                title = { Text(if (isStandalone) stringResource(R.string.profile_settings) else stringResource(R.string.profile_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                }
            )
        }
    ) { padding ->
        if (viewModel.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 8.dp, bottom = 120.dp)
        ) {
            if (isStandalone) {
                StandaloneSection(
                    onConnectShelfClick = onConnectShelfClick,
                    tmdbApiKey = tmdbApiKey,
                    onSaveKey = { dataStoreManager.saveTmdbApiKey(it) }
                )
            } else {
                AccountSection(viewModel = viewModel)
            }

            SectionTitle(stringResource(R.string.section_collection))
            SettingsCard {
                SettingsRow(
                    icon = Icons.AutoMirrored.Filled.PlaylistPlay,
                    title = stringResource(R.string.profile_my_lists),
                    subtitle = stringResource(R.string.profile_my_lists_sub),
                    onClick = onListsClick
                )
                HorizontalDivider(Modifier.padding(start = 56.dp))
                SettingsRow(
                    icon = Icons.Default.FavoriteBorder,
                    title = stringResource(R.string.wishlist_title),
                    subtitle = stringResource(R.string.wishlist_subtitle),
                    onClick = onWishlistClick
                )
                if (!isStandalone) {
                    HorizontalDivider(Modifier.padding(start = 56.dp))
                    SettingsRow(
                        icon = Icons.Default.Sync,
                        title = stringResource(R.string.sync_title),
                        subtitle = stringResource(R.string.profile_sync_sub),
                        onClick = onSyncClick
                    )
                }
                // In beiden Betriebsarten: der Import schreibt lokal, im
                // Shelf-Modus geht das Ergebnis beim naechsten Abgleich raus.
                HorizontalDivider(Modifier.padding(start = 56.dp))
                SettingsRow(
                    icon = Icons.Default.CloudDownload,
                    title = stringResource(R.string.profile_jellyfin),
                    subtitle = stringResource(R.string.profile_jellyfin_hint),
                    onClick = onJellyfinClick
                )
            }

            if (!isStandalone) {
                SectionTitle(stringResource(R.string.section_security))
                SettingsCard {
                    SettingsRow(
                        icon = Icons.Default.Security,
                        title = stringResource(R.string.twofactor_title),
                        subtitle = if (viewModel.twoFactorEnabled) {
                            stringResource(R.string.twofactor_active_tap)
                        } else {
                            stringResource(R.string.twofactor_inactive_tap)
                        },
                        onClick = onTwoFactorClick
                    )
                    HorizontalDivider(Modifier.padding(start = 56.dp))
                    SettingsRow(
                        icon = Icons.Default.Devices,
                        title = stringResource(R.string.access_title),
                        subtitle = stringResource(R.string.access_subtitle),
                        onClick = onAccessClick
                    )
                }
            }

            SectionTitle(stringResource(R.string.section_appearance))
            SettingsCard {
                Column(Modifier.padding(16.dp)) {
                    RowHeader(
                        icon = Icons.Default.DarkMode,
                        title = stringResource(R.string.appearance_theme),
                        // Standard ist dunkel wie die Web-Oberfläche;
                        // stringResource(R.string.appearance_system) bleibt für alle, die es Android-üblich wollen.
                        subtitle = stringResource(R.string.appearance_hint)
                    )
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
                                Text(stringResource(mode.labelRes))
                            }
                        }
                    }
                }
                HorizontalDivider(Modifier.padding(start = 56.dp))
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.weight(1f)) {
                        RowHeader(
                            icon = Icons.Default.Palette,
                            title = stringResource(R.string.appearance_material_you),
                            subtitle = if (supportsDynamic) {
                                stringResource(R.string.appearance_use_system_colors)
                            } else {
                                stringResource(R.string.appearance_android12_only)
                            }
                        )
                    }
                    Switch(
                        checked = dynamicColor && supportsDynamic,
                        enabled = supportsDynamic,
                        onCheckedChange = { scope.launch { dataStoreManager.saveDynamicColor(it) } }
                    )
                }
            }

            SectionTitle(stringResource(R.string.section_app))
            SettingsCard {
                SettingsRow(
                    icon = Icons.Default.Info,
                    title = stringResource(R.string.about_title),
                    subtitle = stringResource(R.string.about_subtitle),
                    onClick = onAboutClick
                )
            }
        }
    }
}

/** Kontodaten mit der Schaltfläche direkt bei den Feldern, zu denen sie gehört. */
@Composable
private fun AccountSection(viewModel: ProfileViewModel) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            modifier = Modifier.size(72.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.Person,
                    contentDescription = null,
                    modifier = Modifier.size(44.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        OutlinedTextField(
            value = viewModel.name,
            onValueChange = { viewModel.name = it },
            label = { Text(stringResource(R.string.common_name)) },
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
            singleLine = true
        )

        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = viewModel.email,
            onValueChange = { viewModel.email = it },
            label = { Text(stringResource(R.string.login_email)) },
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
            singleLine = true
        )

        Spacer(Modifier.height(12.dp))

        // Die Schaltflaeche steht bei den Feldern und nicht ganz unten hinter
        // allen Einstellungen: dort war nicht zu erkennen, worauf sie sich
        // bezieht, und im Betrieb ohne Konto stand sie ohne jeden Zweck da.
        Button(
            onClick = { viewModel.updateProfile() },
            modifier = Modifier.fillMaxWidth(),
            shape = PillShape,
            enabled = !viewModel.isSaving
        ) {
            if (viewModel.isSaving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
            } else {
                Text(stringResource(R.string.profile_save_changes), fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** Was nur ohne Konto gilt: Shelf verbinden und der eigene TMDb-Schlüssel. */
@Composable
private fun StandaloneSection(
    onConnectShelfClick: () -> Unit,
    tmdbApiKey: String?,
    onSaveKey: (String?) -> Unit
) {
    val uriHandler = LocalUriHandler.current
    SectionTitle(stringResource(R.string.section_mode))
    SettingsCard {
        Column(Modifier.padding(16.dp)) {
            RowHeader(
                icon = Icons.Default.CloudOff,
                title = stringResource(R.string.mode_standalone),
                subtitle = stringResource(R.string.mode_standalone_sub_long)
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onConnectShelfClick,
                modifier = Modifier.fillMaxWidth(),
                shape = PillShape
            ) {
                Text(stringResource(R.string.mode_shelf_action), fontWeight = FontWeight.Bold)
            }
        }
    }

    // TMDb-Schluessel: nur ohne Shelf noetig, weil dort deren Proxy die
    // Suche uebernimmt.
    SectionTitle(stringResource(R.string.section_film_search))
    SettingsCard {
        Column(Modifier.padding(16.dp)) {
            RowHeader(
                icon = Icons.Default.Key,
                title = stringResource(R.string.profile_tmdb_key),
                subtitle = stringResource(R.string.profile_tmdb_hint)
            )
            Spacer(Modifier.height(8.dp))
            // Ohne den Weg zum Schluessel bleibt der Hinweis oben folgenlos —
            // TMDb versteckt ihn hinter Konto und Einstellungen.
            Text(
                text = stringResource(R.string.profile_tmdb_where),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(
                onClick = { uriHandler.openUri("https://www.themoviedb.org/settings/api") },
                contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp)
            ) {
                Text(
                    text = stringResource(R.string.profile_tmdb_get_key),
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(12.dp))
            var keyInput by remember(tmdbApiKey) { mutableStateOf(tmdbApiKey.orEmpty()) }
            OutlinedTextField(
                value = keyInput,
                onValueChange = { keyInput = it },
                label = { Text(stringResource(R.string.profile_api_key)) },
                placeholder = { Text(stringResource(R.string.profile_api_key_hint)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = { onSaveKey(keyInput) },
                    modifier = Modifier.weight(1f),
                    shape = PillShape,
                    enabled = keyInput.isNotBlank() && keyInput != tmdbApiKey
                ) {
                    Text(stringResource(R.string.common_save), fontWeight = FontWeight.Bold)
                }
                if (!tmdbApiKey.isNullOrBlank()) {
                    OutlinedButton(
                        onClick = {
                            onSaveKey(null)
                            keyInput = ""
                        },
                        modifier = Modifier.weight(1f),
                        shape = PillShape
                    ) {
                        Text(stringResource(R.string.common_remove))
                    }
                }
            }
        }
    }
}

/** Überschrift einer Gruppe. */
@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = 24.dp, bottom = 8.dp)
    )
}

/**
 * Eine Gruppe. Zusammengehöriges steht in **einer** Karte mit Trennlinien statt
 * in mehreren gleich aussehenden — sonst ist an nichts zu erkennen, was
 * zusammengehört.
 */
@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        content = content
    )
}

/** Antippbare Zeile mit Symbol, Text und Pfeil. */
@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(16.dp)
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.weight(1f)) {
            RowHeader(icon = icon, title = title, subtitle = subtitle)
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline
        )
    }
}

/** Symbol, fette Zeile, erklärende Zeile — das Muster aller Einträge. */
@Composable
private fun RowHeader(icon: ImageVector, title: String, subtitle: String) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Column {
            Text(title, fontWeight = FontWeight.Bold)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
