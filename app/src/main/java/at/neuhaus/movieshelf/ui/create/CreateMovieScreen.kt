package at.neuhaus.movieshelf.ui.create

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import at.neuhaus.movieshelf.ui.components.ShelfFormSection
import at.neuhaus.movieshelf.ui.components.ShelfSectionSpacing
import at.neuhaus.movieshelf.ui.components.ShelfTextField
import at.neuhaus.movieshelf.ui.theme.PillShape

// Kanonisches Schema wie in der Shelf: Typ = Film | Serie, das Medium steht im Tag.
private val COMMON_COLLECTION_TYPES = listOf("Film", "Serie")
private val MEDIA_TAGS = listOf("DVD", "BluRay", "4K", "Streaming", "Digital", "VHS", "Leihe")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateMovieScreen(
    onBack: () -> Unit,
    onCreated: (Int) -> Unit
) {
    val viewModel: CreateMovieViewModel = viewModel()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel.createdMovieId) {
        viewModel.createdMovieId?.let { onCreated(it) }
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
                title = { Text("Film anlegen") },
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
                        IconButton(onClick = { viewModel.save() }) {
                            Icon(Icons.Default.Save, contentDescription = "Speichern")
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
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(ShelfSectionSpacing)
        ) {
            // Sektions-Zuschnitt und Beschriftungen folgen dem Web-Formular
            // (`admin/movies/edit.blade.php`), damit beide Oberflächen dieselbe
            // Gliederung haben.
            ShelfFormSection(title = "Stammdaten", icon = Icons.Default.Info) {
                ShelfTextField(
                    value = viewModel.title,
                    onValueChange = { viewModel.title = it },
                    label = "Originaltitel / Titel *",
                    modifier = Modifier.fillMaxWidth(),
                    isError = viewModel.title.isBlank()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ShelfTextField(
                        value = viewModel.year,
                        onValueChange = { v -> viewModel.year = v.filter { it.isDigit() }.take(4) },
                        label = "Erscheinungsjahr *",
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    ShelfTextField(
                        value = viewModel.runtime,
                        onValueChange = { v -> viewModel.runtime = v.filter { it.isDigit() }.take(4) },
                        label = "Laufzeit (Min.)",
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }

                CollectionTypeDropdown(
                    value = viewModel.collectionType,
                    onValueChange = { viewModel.collectionType = it }
                )

                TagDropdown(
                    value = viewModel.tag,
                    onValueChange = { viewModel.tag = it },
                    modifier = Modifier.fillMaxWidth()
                )

                ShelfTextField(
                    value = viewModel.genre,
                    onValueChange = { viewModel.genre = it },
                    label = "Genre / Kategorien",
                    placeholder = "Komma-getrennt",
                    modifier = Modifier.fillMaxWidth()
                )

                ShelfTextField(
                    value = viewModel.director,
                    onValueChange = { viewModel.director = it },
                    label = "Regisseur",
                    modifier = Modifier.fillMaxWidth()
                )

                ShelfTextField(
                    value = viewModel.rating,
                    onValueChange = { viewModel.rating = it },
                    label = "TMDb Bewertung",
                    placeholder = "0–10",
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
            }

            ShelfFormSection(title = "Handlung & Details", icon = Icons.AutoMirrored.Filled.Notes) {
                ShelfTextField(
                    value = viewModel.overview,
                    onValueChange = { viewModel.overview = it },
                    label = "Filmhandlung / Storyline",
                    modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                    singleLine = false,
                    minLines = 4
                )

                ShelfTextField(
                    value = viewModel.trailerUrl,
                    onValueChange = { viewModel.trailerUrl = it },
                    label = "Trailer URL (YouTube)",
                    placeholder = "https://www.youtube.com/watch?v=...",
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next)
                )
            }

            ShelfFormSection(title = "Physische Sammlung", icon = Icons.Default.Collections) {
                ShelfTextField(
                    value = viewModel.edition,
                    onValueChange = { viewModel.edition = it },
                    label = "Edition / Auflage",
                    placeholder = "z.B. Steelbook, Director's Cut",
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                )

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ShelfTextField(
                        value = viewModel.regionCode,
                        onValueChange = { viewModel.regionCode = it },
                        label = "Regionalcode",
                        placeholder = "z.B. 2, B, Free",
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                    )
                    ShelfTextField(
                        value = viewModel.discLocation,
                        onValueChange = { viewModel.discLocation = it },
                        label = "Standort im Regal",
                        placeholder = "z.B. Regal 3, Fach B",
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                    )
                }

                ConditionDropdown(
                    value = viewModel.condition,
                    onValueChange = { viewModel.condition = it },
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ShelfTextField(
                        value = viewModel.purchaseDate,
                        onValueChange = { viewModel.purchaseDate = it },
                        label = "Kaufdatum",
                        placeholder = "JJJJ-MM-TT",
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                    )
                    ShelfTextField(
                        value = viewModel.purchasePrice,
                        onValueChange = { viewModel.purchasePrice = it },
                        label = "Kaufpreis (€)",
                        placeholder = "0,00",
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next)
                    )
                }
            }

            ShelfFormSection(title = "Status", icon = Icons.Default.Inventory2) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("In Sammlung", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
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
            }

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = { viewModel.save() },
                modifier = Modifier.fillMaxWidth(),
                shape = PillShape,
                enabled = !viewModel.isSaving
            ) {
                if (viewModel.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("Anlegen")
                }
            }

            Spacer(Modifier.height(32.dp))
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
    val options = remember(value) {
        (COMMON_COLLECTION_TYPES + value).filter { it.isNotBlank() }.distinct()
    }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        ShelfTextField(
            value = value,
            onValueChange = onValueChange,
            label = "Typ *",
            trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
            isError = value.isBlank(),
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
        ShelfTextField(
            value = label,
            onValueChange = {},
            readOnly = true,
            label = "Zustand",
            trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
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
    val options = remember(value) {
        (MEDIA_TAGS + value).filter { it.isNotBlank() }.distinct()
    }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        ShelfTextField(
            value = value,
            onValueChange = onValueChange,
            label = "Medium / Format",
            trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
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
