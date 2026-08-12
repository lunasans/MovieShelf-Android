package info.movieshelf.ui.create

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.res.stringResource
import info.movieshelf.R
import info.movieshelf.MovieShelfApplication
import info.movieshelf.ui.components.ShelfFormSection
import info.movieshelf.ui.components.ShelfSectionSpacing
import info.movieshelf.ui.components.ShelfTextField
import info.movieshelf.ui.theme.PillShape

// Kanonisches Schema wie in der Shelf: Typ = Film | Serie, das Medium steht im Tag.
private val COMMON_COLLECTION_TYPES = listOf("Film", "Serie")
private val MEDIA_TAGS = listOf("DVD", "BluRay", "4K", "Streaming", "Digital", "VHS", "Leihe")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateMovieScreen(
    onBack: () -> Unit,
    onCreated: (Long) -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as MovieShelfApplication
    val viewModel: CreateMovieViewModel = viewModel(
        factory = CreateMovieViewModel.Factory(app.movieRepository)
    )

    val snackbarHostState = remember { SnackbarHostState() }

    val snackbarContext = LocalContext.current

    LaunchedEffect(viewModel.createdMovieId) {
        viewModel.createdMovieId?.let { onCreated(it) }
    }
    LaunchedEffect(viewModel.error) {
        viewModel.error?.let {
            snackbarHostState.showSnackbar(it.asString(snackbarContext))
            viewModel.error = null
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.create_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
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
                            Icon(Icons.Default.Save, contentDescription = stringResource(R.string.common_save))
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
            ShelfFormSection(title = stringResource(R.string.form_section_basics), icon = Icons.Default.Info) {
                ShelfTextField(
                    value = viewModel.title,
                    onValueChange = { viewModel.title = it },
                    label = stringResource(R.string.form_title),
                    modifier = Modifier.fillMaxWidth(),
                    isError = viewModel.title.isBlank()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ShelfTextField(
                        value = viewModel.year,
                        onValueChange = { v -> viewModel.year = v.filter { it.isDigit() }.take(4) },
                        label = stringResource(R.string.form_year),
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    ShelfTextField(
                        value = viewModel.runtime,
                        onValueChange = { v -> viewModel.runtime = v.filter { it.isDigit() }.take(4) },
                        label = stringResource(R.string.form_runtime),
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
                    label = stringResource(R.string.form_genre),
                    placeholder = "Komma-getrennt",
                    modifier = Modifier.fillMaxWidth()
                )

                ShelfTextField(
                    value = viewModel.director,
                    onValueChange = { viewModel.director = it },
                    label = stringResource(R.string.form_director),
                    modifier = Modifier.fillMaxWidth()
                )

                ShelfTextField(
                    value = viewModel.rating,
                    onValueChange = { viewModel.rating = it },
                    label = stringResource(R.string.form_rating),
                    placeholder = "0–10",
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
            }

            ShelfFormSection(title = stringResource(R.string.form_section_plot), icon = Icons.AutoMirrored.Filled.Notes) {
                ShelfTextField(
                    value = viewModel.overview,
                    onValueChange = { viewModel.overview = it },
                    label = stringResource(R.string.form_plot),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                    singleLine = false,
                    minLines = 4
                )

                ShelfTextField(
                    value = viewModel.trailerUrl,
                    onValueChange = { viewModel.trailerUrl = it },
                    label = stringResource(R.string.form_trailer),
                    placeholder = "https://www.youtube.com/watch?v=...",
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next)
                )
            }

            ShelfFormSection(title = stringResource(R.string.form_section_physical), icon = Icons.Default.Collections) {
                ShelfTextField(
                    value = viewModel.edition,
                    onValueChange = { viewModel.edition = it },
                    label = stringResource(R.string.form_edition),
                    placeholder = "z.B. Steelbook, Director's Cut",
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                )

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ShelfTextField(
                        value = viewModel.regionCode,
                        onValueChange = { viewModel.regionCode = it },
                        label = stringResource(R.string.form_region),
                        placeholder = "z.B. 2, B, Free",
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                    )
                    ShelfTextField(
                        value = viewModel.discLocation,
                        onValueChange = { viewModel.discLocation = it },
                        label = stringResource(R.string.form_shelf_location),
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
                        label = stringResource(R.string.form_purchase_date),
                        placeholder = "JJJJ-MM-TT",
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                    )
                    ShelfTextField(
                        value = viewModel.purchasePrice,
                        onValueChange = { viewModel.purchasePrice = it },
                        label = stringResource(R.string.form_purchase_price),
                        placeholder = "0,00",
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next)
                    )
                }
            }

            ShelfFormSection(title = stringResource(R.string.form_section_status), icon = Icons.Default.Inventory2) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.create_in_collection), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
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
                    Text(stringResource(R.string.common_create))
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
            label = stringResource(R.string.form_type),
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
// Gespeichert wird der Enum-Wert, angezeigt ein uebersetzter Text. Deshalb
// stehen hier Ressourcen-Verweise: eine Konstante auf oberster Ebene hat
// keinen Composable-Kontext, in dem sich Texte aufloesen liessen.
private val CONDITION_OPTIONS = listOf(
    "" to R.string.condition_none,
    "new" to R.string.condition_new,
    "like_new" to R.string.condition_like_new,
    "good" to R.string.condition_good,
    "acceptable" to R.string.condition_acceptable,
    "damaged" to R.string.condition_damaged
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConditionDropdown(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val label = CONDITION_OPTIONS.firstOrNull { it.first == value }?.let { stringResource(it.second) } ?: value

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        ShelfTextField(
            value = label,
            onValueChange = {},
            readOnly = true,
            label = stringResource(R.string.form_condition),
            trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            CONDITION_OPTIONS.forEach { (optValue, optLabelRes) ->
                DropdownMenuItem(
                    text = { Text(stringResource(optLabelRes)) },
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
            label = stringResource(R.string.form_format),
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
