package at.neuhaus.movieshelf.ui.dashboard

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items as lazyRowItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import at.neuhaus.movieshelf.MovieShelfApplication
import at.neuhaus.movieshelf.R
import at.neuhaus.movieshelf.data.model.Movie
import at.neuhaus.movieshelf.ui.components.FloatingNavBar
import at.neuhaus.movieshelf.ui.components.HeadingText
import at.neuhaus.movieshelf.ui.components.PosterCard
import at.neuhaus.movieshelf.ui.components.RatingBadge
import at.neuhaus.movieshelf.ui.util.MovieCardSkeleton
import at.neuhaus.movieshelf.ui.util.resolveImageUrl
import coil.compose.AsyncImage

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DashboardScreen(
    onMovieClick: (Movie, List<Int>) -> Unit,
    onAboutClick: () -> Unit,
    reloadKey: Int = 0
) {
    val context = LocalContext.current
    val app = context.applicationContext as MovieShelfApplication
    val viewModel: DashboardViewModel = viewModel(
        factory = DashboardViewModel.Factory(app.movieRepository)
    )

    // Nach dem Löschen eines Films (vom Edit-Screen) die Liste neu laden
    LaunchedEffect(reloadKey) {
        if (reloadKey > 0) viewModel.loadMovies(refresh = true)
    }

    var showSortMenu by remember { mutableStateOf(false) }
    var showFilterSheet by remember { mutableStateOf(false) }
    val gridState = rememberLazyGridState()

    // Pagination: lade mehr wenn nahe am Ende
    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisible = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            lastVisible >= viewModel.movies.size - 6
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) viewModel.loadMore()
    }

    if (showFilterSheet) {
        FilterBottomSheet(
            viewModel = viewModel,
            onDismiss = { showFilterSheet = false }
        )
    }

    Scaffold(
        topBar = {
            Surface(tonalElevation = 2.dp) {
                Column {
                    CenterAlignedTopAppBar(
                        navigationIcon = {
                            IconButton(onClick = onAboutClick) {
                                Icon(Icons.Default.Info, contentDescription = "Über MovieShelf")
                            }
                        },
                        title = {
                            Image(
                                painter = painterResource(id = R.drawable.logo),
                                contentDescription = "MovieShelf",
                                modifier = Modifier.height(32.dp)
                            )
                        },
                        actions = {
                            // Filter-Button (hervorgehoben wenn aktiv)
                            IconButton(onClick = { showFilterSheet = true }) {
                                BadgedBox(
                                    badge = {
                                        if (viewModel.filterState.isActive) {
                                            Badge()
                                        }
                                    }
                                ) {
                                    Icon(Icons.Default.FilterList, contentDescription = "Filter")
                                }
                            }
                            // Sort-Dropdown
                            Box {
                                IconButton(onClick = { showSortMenu = true }) {
                                    Icon(Icons.Default.SortByAlpha, contentDescription = "Sortierung")
                                }
                                DropdownMenu(
                                    expanded = showSortMenu,
                                    onDismissRequest = { showSortMenu = false }
                                ) {
                                    SortOption.entries.forEach { option ->
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    text = option.label,
                                                    fontWeight = if (viewModel.sortOption == option) FontWeight.Bold else FontWeight.Normal
                                                )
                                            },
                                            leadingIcon = {
                                                if (viewModel.sortOption == option)
                                                    Icon(Icons.Default.Check, null, modifier = Modifier.size(18.dp))
                                                else
                                                    Spacer(Modifier.size(18.dp))
                                            },
                                            onClick = {
                                                viewModel.onSortSelected(option)
                                                showSortMenu = false
                                            }
                                        )
                                    }
                                }
                            }
                        },
                        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                            containerColor = Color.Transparent
                        )
                    )

                    // Offline-Banner
                    if (viewModel.isOffline) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Default.WifiOff,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Text(
                                    "Offline — zwischengespeicherte Daten",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }

                    // Aktive Filter-Chips
                    if (viewModel.filterState.isActive) {
                        ActiveFilterChips(
                            filterState = viewModel.filterState,
                            onClearAll = { viewModel.clearFilters() },
                            onRemoveGenre = { genre ->
                                viewModel.onFilterChanged(
                                    viewModel.filterState.copy(
                                        selectedGenres = viewModel.filterState.selectedGenres - genre
                                    )
                                )
                            },
                            onRemoveDirector = { director ->
                                viewModel.onFilterChanged(
                                    viewModel.filterState.copy(
                                        selectedDirectors = viewModel.filterState.selectedDirectors - director
                                    )
                                )
                            },
                            onClearYear = {
                                viewModel.onFilterChanged(
                                    viewModel.filterState.copy(yearFrom = null, yearTo = null)
                                )
                            }
                        )
                    }

                    OutlinedTextField(
                        value = viewModel.searchQuery,
                        onValueChange = { viewModel.onSearchQueryChange(it) },
                        placeholder = { Text("Filme suchen...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (viewModel.searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.onSearchQueryChange("") }) {
                                    Icon(Icons.Default.Close, contentDescription = "Löschen")
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 8.dp),
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium,
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            unfocusedBorderColor = Color.Transparent
                        )
                    )

                }
            }
        }
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = viewModel.isRefreshing,
            onRefresh = { viewModel.loadMovies(refresh = true) },
            modifier = Modifier.padding(innerPadding)
        ) {
            val isBrowsing = !viewModel.filterState.isActive && viewModel.searchQuery.isBlank()

            if (isBrowsing) {
                // "Shelf"-Gruppierung: vertikal gestapelte, horizontal scrollbare Reihen
                // ("Neue Filme" / "Filme" / "Serien"), wie im Web-Dashboard.
                val hasAnyShelfContent = viewModel.newMoviesShelf.isNotEmpty() ||
                    viewModel.filmeShelf.isNotEmpty() || viewModel.seriesShelf.isNotEmpty()

                if (viewModel.isLoading && !hasAnyShelfContent) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (!hasAnyShelfContent && !viewModel.isLoading) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.SearchOff, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.outline)
                            Spacer(Modifier.height(16.dp))
                            Text("Keine Filme gefunden", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(bottom = 100.dp)
                    ) {
                        if (viewModel.newMoviesShelf.isNotEmpty()) {
                            MovieShelfRow(
                                title = "Neue Filme",
                                movies = viewModel.newMoviesShelf,
                                onClick = { movie ->
                                    onMovieClick(movie, viewModel.newMoviesShelf.map { it.id })
                                }
                            )
                        }
                        if (viewModel.filmeShelf.isNotEmpty()) {
                            MovieShelfRow(
                                title = "Filme",
                                movies = viewModel.filmeShelf,
                                onClick = { movie ->
                                    onMovieClick(movie, viewModel.filmeShelf.map { it.id })
                                }
                            )
                        }
                        if (viewModel.seriesShelf.isNotEmpty()) {
                            MovieShelfRow(
                                title = "Serien",
                                movies = viewModel.seriesShelf,
                                onClick = { movie ->
                                    onMovieClick(movie, viewModel.seriesShelf.map { it.id })
                                }
                            )
                        }
                    }
                }
            } else if (viewModel.isLoading && viewModel.movies.isEmpty()) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 150.dp),
                    contentPadding = PaddingValues(top = 8.dp, start = 8.dp, end = 8.dp, bottom = 100.dp)
                ) {
                    items(6) { MovieCardSkeleton() }
                }
            } else if (viewModel.movies.isEmpty() && !viewModel.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.SearchOff, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.outline)
                        Spacer(Modifier.height(16.dp))
                        Text("Keine Filme gefunden", style = MaterialTheme.typography.titleMedium)
                        if (viewModel.filterState.isActive || viewModel.searchQuery.isNotEmpty()) {
                            TextButton(onClick = {
                                viewModel.onSearchQueryChange("")
                                viewModel.clearFilters()
                            }) {
                                Text("Filter zurücksetzen")
                            }
                        }
                    }
                }
            } else {
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Adaptive(minSize = 150.dp),
                    contentPadding = PaddingValues(top = 8.dp, start = 8.dp, end = 8.dp, bottom = 100.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(viewModel.movies, key = { it.id }) { movie ->
                        MovieItem(
                            movie = movie,
                            onClick = { onMovieClick(movie, viewModel.movies.map { it.id }) },
                            onWatchedToggle = { viewModel.toggleWatched(movie.id) }
                        )
                    }
                    if (viewModel.isLoadingMore) {
                        items(2) { MovieCardSkeleton() }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun FilterBottomSheet(
    viewModel: DashboardViewModel,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var localFilter by remember { mutableStateOf(viewModel.filterState) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Filter", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                if (localFilter.isActive) {
                    TextButton(onClick = { localFilter = FilterState() }) { Text("Zurücksetzen") }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Erscheinungsjahr
            Text("Erscheinungsjahr", style = MaterialTheme.typography.labelLarge)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                YearPicker(
                    label = "Von",
                    value = localFilter.yearFrom,
                    onValueChange = { localFilter = localFilter.copy(yearFrom = it) }
                )
                Text("bis")
                YearPicker(
                    label = "Bis",
                    value = localFilter.yearTo,
                    onValueChange = { localFilter = localFilter.copy(yearTo = it) }
                )
            }

            HorizontalDivider(Modifier.padding(vertical = 12.dp))

            // Genre Multi-Select
            Text("Genres", style = MaterialTheme.typography.labelLarge)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                viewModel.availableGenres.forEach { genre ->
                    val isSelected = localFilter.selectedGenres.contains(genre)
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            localFilter = localFilter.copy(
                                selectedGenres = if (isSelected) localFilter.selectedGenres - genre else localFilter.selectedGenres + genre
                            )
                        },
                        label = { Text(genre, fontSize = 12.sp) }
                    )
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 12.dp))

            // Regisseur Multi-Select
            Text("Regisseure", style = MaterialTheme.typography.labelLarge)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                viewModel.availableDirectors.take(15).forEach { director ->
                    val isSelected = localFilter.selectedDirectors.contains(director)
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            localFilter = localFilter.copy(
                                selectedDirectors = if (isSelected) localFilter.selectedDirectors - director else localFilter.selectedDirectors + director
                            )
                        },
                        label = { Text(director, fontSize = 12.sp) }
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = {
                    viewModel.onFilterChanged(localFilter)
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium
            ) {
                Text("Anwenden")
            }
        }
    }
}

@Composable
fun YearPicker(label: String, value: Int?, onValueChange: (Int?) -> Unit) {
    var text by remember(value) { mutableStateOf(value?.toString() ?: "") }
    OutlinedTextField(
        value = text,
        onValueChange = {
            if (it.isEmpty()) {
                text = ""
                onValueChange(null)
            } else if (it.length <= 4 && it.all { char -> char.isDigit() }) {
                text = it
                if (it.length == 4) onValueChange(it.toInt())
            }
        },
        label = { Text(label) },
        modifier = Modifier.width(90.dp),
        singleLine = true,
        textStyle = MaterialTheme.typography.bodySmall
    )
}

/**
 * "Shelf"-Gruppierung: eine horizontal scrollbare, betitelte Filmreihe
 * ("Neue Filme" / "Filme" / "Serien"), analog zu den Sektionen im Web-Dashboard.
 */
@Composable
fun MovieShelfRow(
    title: String,
    movies: List<Movie>,
    onClick: (Movie) -> Unit
) {
    val context = LocalContext.current
    Column(modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)) {
        HeadingText(
            text = title,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            style = MaterialTheme.typography.titleMedium
        )
        LazyRow(
            modifier = Modifier.height(180.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            lazyRowItems(movies, key = { it.id }) { movie ->
                PosterCard(
                    imageUrl = resolveImageUrl(context, movie.coverUrl ?: ""),
                    title = movie.title ?: "",
                    subtitle = movie.year?.toString(),
                    modifier = Modifier.width(110.dp),
                    onClick = { onClick(movie) }
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ActiveFilterChips(
    filterState: FilterState,
    onClearAll: () -> Unit,
    onRemoveGenre: (String) -> Unit,
    onRemoveDirector: (String) -> Unit,
    onClearYear: () -> Unit
) {
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        InputChip(
            selected = true,
            onClick = onClearAll,
            label = { Text("Alle löschen") },
            trailingIcon = { Icon(Icons.Default.Close, null, Modifier.size(14.dp)) }
        )

        filterState.selectedGenres.forEach { genre ->
            InputChip(
                selected = true,
                onClick = { onRemoveGenre(genre) },
                label = { Text(genre) },
                trailingIcon = { Icon(Icons.Default.Close, null, Modifier.size(14.dp)) }
            )
        }

        filterState.selectedDirectors.forEach { director ->
            InputChip(
                selected = true,
                onClick = { onRemoveDirector(director) },
                label = { Text(director) },
                trailingIcon = { Icon(Icons.Default.Close, null, Modifier.size(14.dp)) }
            )
        }

        if (filterState.yearFrom != null || filterState.yearTo != null) {
            InputChip(
                selected = true,
                onClick = onClearYear,
                label = { Text("${filterState.yearFrom ?: "..."}-${filterState.yearTo ?: "..."}") },
                trailingIcon = { Icon(Icons.Default.Close, null, Modifier.size(14.dp)) }
            )
        }
    }
}

@Composable
fun MovieItem(
    movie: Movie,
    onClick: () -> Unit,
    onWatchedToggle: () -> Unit
) {
    val context = LocalContext.current
    PosterCard(
        imageUrl = resolveImageUrl(context, movie.coverUrl ?: ""),
        title = movie.title ?: "",
        subtitle = movie.year?.toString(),
        modifier = Modifier.padding(6.dp),
        onClick = onClick,
        topStart = {
            if (!movie.rating.isNullOrBlank()) {
                RatingBadge(rating = movie.rating)
            }
        },
        topEnd = {
            IconButton(
                onClick = onWatchedToggle,
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(
                        color = if (movie.isWatched == true) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.4f),
                        shape = CircleShape
                    )
            ) {
                Icon(
                    imageVector = if (movie.isWatched == true) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                    contentDescription = if (movie.isWatched == true) "Als ungesehen markieren" else "Als gesehen markieren",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    )
}

fun movieTagStyle(tag: String): Pair<Color, String> {
    return when (tag.lowercase().trim()) {
        "blu-ray" -> Color(0xFF2196F3) to "BLU-RAY"
        "dvd" -> Color(0xFFF44336) to "DVD"
        "uhd", "4k" -> Color(0xFF4CAF50) to "4K UHD"
        "digital" -> Color(0xFF9C27B0) to "DIGITAL"
        else -> Color(0xFF607D8B) to tag.uppercase()
    }
}
