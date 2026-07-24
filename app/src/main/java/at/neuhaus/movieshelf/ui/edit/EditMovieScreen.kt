package at.neuhaus.movieshelf.ui.edit

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import at.neuhaus.movieshelf.MovieShelfApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Kanonisches Schema wie in der Shelf: Typ = Film | Serie, das Medium steht im Tag.
private val COMMON_COLLECTION_TYPES = listOf("Film", "Serie")
private val MEDIA_TAGS = listOf("DVD", "BluRay", "4K", "Streaming", "Digital", "VHS", "Leihe")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditMovieScreen(
    movieId: Int,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    onDeleted: () -> Unit = {}
) {
    val context = LocalContext.current
    val app = context.applicationContext as MovieShelfApplication
    val viewModel: EditMovieViewModel = viewModel(
        factory = EditMovieViewModel.Factory(movieId, app.movieRepository)
    )

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showDiscardDialog by remember { mutableStateOf(false) }

    // Bild aus Uri lesen (off-main) und hochladen
    fun pickAndUpload(uri: Uri?, isCover: Boolean) {
        if (uri == null) return
        scope.launch {
            val (bytes, mime) = withContext(Dispatchers.IO) {
                val type = context.contentResolver.getType(uri) ?: "image/jpeg"
                val data = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                data to type
            }
            if (bytes == null) {
                snackbarHostState.showSnackbar("Bild konnte nicht gelesen werden.")
            } else if (isCover) {
                viewModel.uploadCover(bytes, mime)
            } else {
                viewModel.uploadBackdrop(bytes, mime)
            }
        }
    }

    val coverPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> pickAndUpload(uri, isCover = true) }

    val backdropPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> pickAndUpload(uri, isCover = false) }

    val requestBack = {
        if (viewModel.hasUnsavedChanges && !viewModel.saved && !viewModel.deleted) {
            showDiscardDialog = true
        } else {
            onBack()
        }
    }

    BackHandler(enabled = viewModel.hasUnsavedChanges && !viewModel.saved && !viewModel.deleted) {
        showDiscardDialog = true
    }

    LaunchedEffect(viewModel.saved) {
        if (viewModel.saved) onSaved()
    }
    LaunchedEffect(viewModel.deleted) {
        if (viewModel.deleted) onDeleted()
    }
    LaunchedEffect(viewModel.error) {
        viewModel.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.error = null
        }
    }
    LaunchedEffect(viewModel.uploadMessage) {
        viewModel.uploadMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.uploadMessage = null
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Film bearbeiten") },
                navigationIcon = {
                    IconButton(onClick = { requestBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
                actions = {
                    if (viewModel.isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp).padding(end = 16.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        IconButton(onClick = { viewModel.save() }, enabled = !viewModel.isLoading) {
                            Icon(Icons.Default.Save, contentDescription = "Speichern")
                        }
                    }
                }
            )
        }
    ) { padding ->
        when {
            viewModel.isLoading -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            viewModel.loadError -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text("Film konnte nicht geladen werden.")
                }
            }
            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    OutlinedTextField(
                        value = viewModel.title,
                        onValueChange = { viewModel.title = it },
                        label = { Text("Titel *") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        isError = viewModel.title.isBlank()
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        OutlinedTextField(
                            value = viewModel.year,
                            onValueChange = { v -> viewModel.year = v.filter { it.isDigit() }.take(4) },
                            label = { Text("Jahr *") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                        OutlinedTextField(
                            value = viewModel.runtime,
                            onValueChange = { v -> viewModel.runtime = v.filter { it.isDigit() }.take(4) },
                            label = { Text("Laufzeit (Min.)") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                    }

                    CollectionTypeDropdown(
                        value = viewModel.collectionType,
                        onValueChange = { viewModel.collectionType = it }
                    )

                    OutlinedTextField(
                        value = viewModel.genre,
                        onValueChange = { viewModel.genre = it },
                        label = { Text("Genre (Komma-getrennt)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = viewModel.director,
                        onValueChange = { viewModel.director = it },
                        label = { Text("Regisseur") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        OutlinedTextField(
                            value = viewModel.rating,
                            onValueChange = { viewModel.rating = it },
                            label = { Text("Bewertung (0–10)") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                        )
                        TagDropdown(
                            value = viewModel.tag,
                            onValueChange = { viewModel.tag = it },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    OutlinedTextField(
                        value = viewModel.trailerUrl,
                        onValueChange = { viewModel.trailerUrl = it },
                        label = { Text("Trailer-URL") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next)
                    )

                    OutlinedTextField(
                        value = viewModel.overview,
                        onValueChange = { viewModel.overview = it },
                        label = { Text("Beschreibung") },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                        minLines = 4
                    )

                    // Physische Sammlung
                    Text("Physische Sammlung", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)

                    OutlinedTextField(
                        value = viewModel.edition,
                        onValueChange = { viewModel.edition = it },
                        label = { Text("Edition / Auflage") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = viewModel.regionCode,
                            onValueChange = { viewModel.regionCode = it },
                            label = { Text("Regionalcode") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                        )
                        OutlinedTextField(
                            value = viewModel.discLocation,
                            onValueChange = { viewModel.discLocation = it },
                            label = { Text("Standort im Regal") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = viewModel.purchaseDate,
                            onValueChange = { viewModel.purchaseDate = it },
                            label = { Text("Kaufdatum (JJJJ-MM-TT)") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                        )
                        OutlinedTextField(
                            value = viewModel.purchasePrice,
                            onValueChange = { viewModel.purchasePrice = it },
                            label = { Text("Kaufpreis (€)") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next)
                        )
                    }

                    ConditionDropdown(
                        value = viewModel.condition,
                        onValueChange = { viewModel.condition = it },
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Bilder
                    Text("Bilder", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(
                            onClick = {
                                coverPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                            modifier = Modifier.weight(1f),
                            enabled = !viewModel.isUploadingCover
                        ) {
                            if (viewModel.isUploadingCover) {
                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Cover")
                            }
                        }
                        OutlinedButton(
                            onClick = {
                                backdropPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                            modifier = Modifier.weight(1f),
                            enabled = !viewModel.isUploadingBackdrop
                        ) {
                            if (viewModel.isUploadingBackdrop) {
                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Wallpaper, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Backdrop")
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("In Sammlung", fontWeight = FontWeight.Bold)
                            Text(
                                "Film ist Teil der Sammlung (nicht nur Wunschliste)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = viewModel.inCollection,
                            onCheckedChange = { viewModel.inCollection = it }
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    Button(
                        onClick = { viewModel.save() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !viewModel.isSaving && !viewModel.isDeleting
                    ) {
                        if (viewModel.isSaving) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        } else {
                            Text("Speichern")
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    TextButton(
                        onClick = { showDeleteDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !viewModel.isSaving && !viewModel.isDeleting,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        if (viewModel.isDeleting) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.error)
                        } else {
                            Icon(Icons.Default.DeleteOutline, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Film löschen")
                        }
                    }

                    Spacer(Modifier.height(32.dp))
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Film löschen?") },
            text = { Text("„${viewModel.title}\" wird dauerhaft aus der Sammlung entfernt. Das kann nicht rückgängig gemacht werden.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.deleteMovie()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Löschen") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Abbrechen") }
            }
        )
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Änderungen verwerfen?") },
            text = { Text("Du hast ungespeicherte Änderungen. Wirklich verwerfen?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDiscardDialog = false
                        onBack()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Verwerfen") }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) { Text("Abbrechen") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CollectionTypeDropdown(
    value: String,
    onValueChange: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    // Bestehenden Wert mit aufnehmen, falls er nicht in der Standardliste ist
    val options = remember(value) {
        (COMMON_COLLECTION_TYPES + value).filter { it.isNotBlank() }.distinct()
    }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text("Typ *") },
            trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
            isError = value.isBlank(),
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryEditable)
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onValueChange(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

// Zustand: gespeichert wird der Enum-Wert, angezeigt das deutsche Label.
private val CONDITION_OPTIONS = listOf(
    "" to "—",
    "new" to "Neu",
    "like_new" to "Wie neu",
    "good" to "Gut",
    "acceptable" to "Akzeptabel",
    "damaged" to "Beschädigt"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConditionDropdown(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val label = CONDITION_OPTIONS.firstOrNull { it.first == value }?.second ?: value

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = label,
            onValueChange = {},
            readOnly = true,
            label = { Text("Zustand") },
            trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            CONDITION_OPTIONS.forEach { (optValue, optLabel) ->
                DropdownMenuItem(
                    text = { Text(optLabel) },
                    onClick = {
                        onValueChange(optValue)
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TagDropdown(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    // Bestehenden Wert mit aufnehmen, falls er nicht in der Standardliste ist
    val options = remember(value) {
        (MEDIA_TAGS + value).filter { it.isNotBlank() }.distinct()
    }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text("Medium / Format") },
            trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryEditable)
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onValueChange(option)
                        expanded = false
                    }
                )
            }
        }
    }
}
