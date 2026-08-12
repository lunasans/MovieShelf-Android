package info.movieshelf.ui.add

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.res.stringResource
import info.movieshelf.R
import info.movieshelf.ui.util.UiText
import info.movieshelf.MovieShelfApplication
import coil.compose.AsyncImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddMovieScreen(
    onBack: () -> Unit,
    onMovieImported: () -> Unit,
    onCreateManual: () -> Unit = {}
) {
    val app = LocalContext.current.applicationContext as MovieShelfApplication
    val viewModel: AddMovieViewModel = viewModel(
        factory = AddMovieViewModel.Factory(app.tmdbRepository)
    )
    val isAdmin = info.movieshelf.data.SessionManager.user?.isAdmin == true
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    
    var showScanner by remember { mutableStateOf(false) }
    
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            showScanner = true
        } else {
            viewModel.error = UiText.of(R.string.error_camera_permission)
        }
    }

    val snackbarContext = LocalContext.current

    LaunchedEffect(viewModel.error) {
        viewModel.error?.let {
            snackbarHostState.showSnackbar(it.asString(snackbarContext))
            viewModel.error = null
        }
    }

    if (showScanner) {
        CameraScanner(
            onDetected = { text ->
                viewModel.onSearchQueryChange(text)
                showScanner = false
            },
            onClose = { showScanner = false }
        )
        return
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Surface(tonalElevation = 3.dp) {
                Column {
                    TopAppBar(
                        title = { Text(stringResource(R.string.add_title)) },
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                            }
                        },
                        actions = {
                            if (isAdmin) {
                                IconButton(onClick = onCreateManual) {
                                    Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.add_manual))
                                }
                            }
                        }
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = viewModel.searchQuery,
                            onValueChange = { viewModel.onSearchQueryChange(it) },
                            placeholder = { Text(stringResource(R.string.add_search_tmdb)) },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            trailingIcon = {
                                if (viewModel.searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.onSearchQueryChange("") }) {
                                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.common_clear))
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            shape = MaterialTheme.shapes.medium
                        )
                        
                        Spacer(Modifier.width(8.dp))
                        
                        FilledIconButton(
                            onClick = {
                                when (PackageManager.PERMISSION_GRANTED) {
                                    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) -> {
                                        showScanner = true
                                    }
                                    else -> {
                                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                    }
                                }
                            },
                            modifier = Modifier.size(56.dp)
                        ) {
                            Icon(Icons.Default.CameraAlt, contentDescription = stringResource(R.string.add_scan_title))
                        }
                    }
                    
                    if (viewModel.searchUnavailable) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                            )
                        ) {
                            Text(
                                stringResource(R.string.add_no_tmdb_key),
                                modifier = Modifier.padding(16.dp),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        SegmentedButton(
                            selected = !viewModel.searchSeries,
                            onClick = { viewModel.onTypeChanged(false) },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                        ) { Text(stringResource(R.string.type_film)) }
                        SegmentedButton(
                            selected = viewModel.searchSeries,
                            onClick = { viewModel.onTypeChanged(true) },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                        ) { Text(stringResource(R.string.type_series)) }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 0.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End
                    ) {
                        Text(
                            stringResource(R.string.add_include_in_collection),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(12.dp))
                        Switch(
                            checked = viewModel.importToCollection,
                            onCheckedChange = { viewModel.importToCollection = it },
                            thumbContent = if (viewModel.importToCollection) {
                                {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(SwitchDefaults.IconSize),
                                    )
                                }
                            } else null
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (viewModel.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (viewModel.searchResults.isEmpty() && viewModel.searchQuery.length >= 2) {
                Text(
                    stringResource(R.string.add_no_results),
                    modifier = Modifier.align(Alignment.Center),
                    style = MaterialTheme.typography.bodyLarge
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(viewModel.searchResults) { item ->
                        TmdbMovieItem(
                            item = item,
                            onImport = {
                                val id = (item["id"] as? Number)?.toInt()
                                if (id != null) {
                                    viewModel.importMovie(id, onMovieImported)
                                }
                            }
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    }
                }
            }

            if (viewModel.isImporting) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Black.copy(alpha = 0.5f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Card {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator()
                                Spacer(Modifier.height(16.dp))
                                Text(stringResource(R.string.add_importing))
                            }
                        }
                    }
                }
            }

            if (viewModel.successMessage != null) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Black.copy(alpha = 0.5f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                            Text(
                                viewModel.successMessage!!.asString(),
                                modifier = Modifier.padding(24.dp),
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TmdbMovieItem(item: Map<String, Any>, onImport: () -> Unit) {
    val title = item["title"] as? String ?: (item["name"] as? String) ?: stringResource(R.string.common_unknown)
    val overview = item["overview"] as? String ?: ""
    val releaseDate = item["release_date"] as? String ?: (item["first_air_date"] as? String) ?: ""
    val posterPath = item["poster_path"] as? String
    val posterUrl = if (posterPath != null) "https://image.tmdb.org/t/p/w200$posterPath" else null

    ListItem(
        headlineContent = { Text(title, fontWeight = FontWeight.Bold) },
        supportingContent = {
            Column {
                if (releaseDate.isNotEmpty()) {
                    Text(releaseDate, style = MaterialTheme.typography.bodySmall)
                }
                Text(
                    overview,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        },
        leadingContent = {
            Surface(
                modifier = Modifier.size(60.dp, 90.dp),
                shape = MaterialTheme.shapes.extraSmall,
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                if (posterUrl != null) {
                    AsyncImage(
                        model = posterUrl,
                        contentDescription = title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            }
        },
        trailingContent = {
            IconButton(onClick = onImport) {
                Icon(Icons.Default.Download, contentDescription = stringResource(R.string.add_import), tint = MaterialTheme.colorScheme.primary)
            }
        }
    )
}
