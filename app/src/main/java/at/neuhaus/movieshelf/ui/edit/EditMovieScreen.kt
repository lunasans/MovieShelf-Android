package at.neuhaus.movieshelf.ui.edit

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Save
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

private val COMMON_COLLECTION_TYPES = listOf("DVD", "Blu-ray", "4K UHD", "Digital", "Serie")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditMovieScreen(
    movieId: Int,
    onBack: () -> Unit,
    onSaved: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as MovieShelfApplication
    val viewModel: EditMovieViewModel = viewModel(
        factory = EditMovieViewModel.Factory(movieId, app.movieRepository)
    )

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel.saved) {
        if (viewModel.saved) onSaved()
    }
    LaunchedEffect(viewModel.error) {
        viewModel.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.error = null
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Film bearbeiten") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
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
                        OutlinedTextField(
                            value = viewModel.tag,
                            onValueChange = { viewModel.tag = it },
                            label = { Text("Tag") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
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
                        enabled = !viewModel.isSaving
                    ) {
                        if (viewModel.isSaving) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        } else {
                            Text("Speichern")
                        }
                    }

                    Spacer(Modifier.height(32.dp))
                }
            }
        }
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
            label = { Text("Sammlungstyp *") },
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
