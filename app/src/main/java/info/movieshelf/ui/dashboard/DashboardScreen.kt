package info.movieshelf.ui.dashboard

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items as lazyRowItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.res.stringResource
import info.movieshelf.MovieShelfApplication
import info.movieshelf.R
import info.movieshelf.data.local.CollectionViewMode
import info.movieshelf.data.local.DataStoreManager
import info.movieshelf.data.model.Movie
import info.movieshelf.ui.components.FloatingNavBar
import info.movieshelf.ui.components.MovieListRow
import info.movieshelf.ui.components.PosterCard
import info.movieshelf.ui.components.RatingBadge
import info.movieshelf.ui.theme.BackgroundDark
import info.movieshelf.ui.theme.HeroBannerShape
import info.movieshelf.ui.theme.NavAccentRed
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import info.movieshelf.ui.util.MovieCardSkeleton
import info.movieshelf.ui.util.resolveImageUrl
import coil.compose.AsyncImage

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DashboardScreen(
    onMovieClick: (Movie, List<Long>) -> Unit,
    /** Fuehrt zum Abgleich — die Sammlung fuellt sich nur auf Knopfdruck. */
    onSyncClick: () -> Unit = {},
    isShelfMode: Boolean = false,
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

    val gridState = rememberLazyGridState()

    // Raster oder Zeilen — die Wahl haelt ueber Neustarts, sie ist eine
    // Gewohnheit und keine Momententscheidung.
    val dataStoreManager = remember { DataStoreManager(context) }
    val viewMode by dataStoreManager.collectionViewMode.collectAsState(initial = CollectionViewMode.GRID)
    val scope = rememberCoroutineScope()

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

    // Zufaellige Auswahl aus der ganzen Sammlung, wie im Web — nicht die
    // Neuzugaenge, die stehen ohnehin gleich darunter in ihrer eigenen Reihe.
    val heroMovies = viewModel.heroMovies
    val isBrowsing = viewModel.searchQuery.isBlank() && viewModel.selectedShelf == null
    // Der Hero reicht hinter die Kopfzeile, wie im Web. Solange er zu sehen ist,
    // bekommt die Kopfzeile deshalb keinen eigenen Grund — das Logo liegt dann
    // direkt auf dem oberen Scrim des Banners, der dort ohnehin dunkel ist.
    val heroBehindTopBar = isBrowsing && heroMovies.isNotEmpty()

    // Die Kopfzeile schiebt sich beim Scrollen mit nach oben aus dem Bild und
    // kommt beim Zurückscrollen wieder. Ohne das bliebe das Logo stehen und
    // laege auf Postern und Titeln, sobald der Hero durchgelaufen ist.
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(
        rememberTopAppBarState()
    )

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            Column {
                // Logo linksbündig wie im Web; stringResource(R.string.about_title) sitzt
                // jetzt in den Einstellungen und braucht hier keinen Platz.
                TopAppBar(
                    title = {
                        Image(
                            painter = painterResource(id = R.drawable.logo),
                            contentDescription = stringResource(R.string.app_name),
                            modifier = Modifier.height(32.dp)
                        )
                    },
                    // Über dem Hero durchsichtig, damit das Logo auf dem Bild
                    // liegt; sobald Inhalt darunter durchläuft, bekommt sie
                    // ihren Grund.
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = MaterialTheme.colorScheme
                            .surfaceColorAtElevation(3.dp)
                    ),
                    scrollBehavior = scrollBehavior
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
                                stringResource(R.string.dashboard_offline),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = viewModel.isRefreshing,
            onRefresh = { viewModel.loadMovies(refresh = true) },
            modifier = Modifier.padding(
                top = if (heroBehindTopBar) 0.dp else innerPadding.calculateTopPadding(),
                bottom = innerPadding.calculateBottomPadding()
            )
        ) {
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
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(horizontal = 32.dp)
                        ) {
                            Icon(Icons.Default.SearchOff, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.outline)
                            Spacer(Modifier.height(16.dp))
                            Text(stringResource(R.string.dashboard_no_movies), style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = if (isShelfMode) {
                                    stringResource(R.string.dashboard_sync_hint)
                                } else {
                                    stringResource(R.string.dashboard_add_first)
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            if (isShelfMode) {
                                Spacer(Modifier.height(16.dp))
                                Button(onClick = onSyncClick, shape = info.movieshelf.ui.theme.PillShape) {
                                    Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(stringResource(R.string.dashboard_sync_now), fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(bottom = 100.dp)
                    ) {
                        if (heroMovies.isNotEmpty()) {
                            HeroSlider(
                                movies = heroMovies,
                                onClick = { movie ->
                                    onMovieClick(movie, heroMovies.map { it.localId })
                                },
                                // Der Banner liegt unter der Kopfzeile und wächst
                                // um deren Höhe, damit sichtbar so viel Bild
                                // übrig bleibt wie zuvor.
                                extraTopHeight = innerPadding.calculateTopPadding()
                            )
                            Spacer(Modifier.height(8.dp))
                        }

                        DashboardSearchField(
                            viewModel = viewModel,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )

                        if (viewModel.newMoviesShelf.isNotEmpty()) {
                            MovieShelfRow(
                                title = stringResource(R.string.shelf_new_movies),
                                movies = viewModel.newMoviesShelf,
                                onClick = { movie ->
                                    onMovieClick(movie, viewModel.newMoviesShelf.map { it.localId })
                                },
                                onShowAll = { viewModel.onShelfSelected(ShelfCategory.NEW) }
                            )
                        }
                        if (viewModel.filmeShelf.isNotEmpty()) {
                            MovieShelfRow(
                                title = stringResource(R.string.shelf_movies),
                                movies = viewModel.filmeShelf,
                                onClick = { movie ->
                                    onMovieClick(movie, viewModel.filmeShelf.map { it.localId })
                                },
                                onShowAll = { viewModel.onShelfSelected(ShelfCategory.FILME) },
                                count = viewModel.collectionCounts?.films
                            )
                        }
                        if (viewModel.seriesShelf.isNotEmpty()) {
                            MovieShelfRow(
                                title = stringResource(R.string.shelf_series),
                                movies = viewModel.seriesShelf,
                                onClick = { movie ->
                                    onMovieClick(movie, viewModel.seriesShelf.map { it.localId })
                                },
                                onShowAll = { viewModel.onShelfSelected(ShelfCategory.SERIEN) },
                                count = viewModel.collectionCounts?.series
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
                Column(Modifier.fillMaxSize()) {
                    // Ohne Feld liesse sich ein Tippfehler nicht mehr berichtigen:
                    // die Trefferliste ist leer, das Feld waere verschwunden.
                    DashboardSearchField(
                        viewModel = viewModel,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                    Column(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.SearchOff, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.outline)
                        Spacer(Modifier.height(16.dp))
                        Text(stringResource(R.string.dashboard_no_results), style = MaterialTheme.typography.titleMedium)
                        if (viewModel.searchQuery.isNotEmpty() || viewModel.selectedShelf != null) {
                            TextButton(onClick = {
                                viewModel.onSearchQueryChange("")
                                viewModel.clearShelf()
                            }) {
                                Text(stringResource(R.string.dashboard_clear_filter))
                            }
                        }
                    }
                }
            } else {
                val isListMode = viewMode == CollectionViewMode.LIST
                // Auch die Zeilenansicht laeuft ueber das Raster, nur einspaltig:
                // so gelten Pagination (`gridState`), Suchfeld und Kategorie-Chip
                // unveraendert fuer beide Darstellungen.
                LazyVerticalGrid(
                    state = gridState,
                    columns = if (isListMode) GridCells.Fixed(1) else GridCells.Adaptive(minSize = 150.dp),
                    contentPadding = PaddingValues(top = 8.dp, start = 8.dp, end = 8.dp, bottom = 100.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Auch hier, sonst verschwaende das Feld beim ersten Zeichen -
                    // die Trefferliste ersetzt ja die Reihen, zwischen denen es steht.
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        DashboardSearchField(
                            viewModel = viewModel,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    // Zeile ueber der Liste: links der Kategorie-Chip als Rueckweg
                    // zu den Shelf-Reihen (nur im "Alle anzeigen"-Modus), rechts
                    // der Umschalter Raster/Zeilen. Er sitzt hier statt in der
                    // Kopfzeile, weil die sich beim Scrollen einfaehrt — und weil
                    // er dort steht, wo seine Wirkung eintritt.
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            viewModel.selectedShelf?.let { shelf ->
                                InputChip(
                                    selected = true,
                                    onClick = { viewModel.clearShelf() },
                                    label = { Text("${stringResource(shelf.labelRes)} · ${viewModel.movies.size}") },
                                    trailingIcon = { Icon(Icons.Default.Close, null, Modifier.size(14.dp)) }
                                )
                            } ?: Spacer(Modifier.width(0.dp))

                            ViewModeChip(
                                viewMode = viewMode,
                                onToggle = {
                                    scope.launch {
                                        dataStoreManager.saveCollectionViewMode(
                                            if (viewMode == CollectionViewMode.GRID) {
                                                CollectionViewMode.LIST
                                            } else {
                                                CollectionViewMode.GRID
                                            }
                                        )
                                    }
                                }
                            )
                        }
                    }
                    items(viewModel.movies, key = { it.id }) { movie ->
                        if (isListMode) {
                            MovieListRow(
                                movie = movie,
                                imageUrl = resolveImageUrl(context, movie.coverUrl ?: ""),
                                onClick = { onMovieClick(movie, viewModel.movies.map { it.localId }) },
                                onWatchedToggle = { viewModel.toggleWatched(movie.localId) }
                            )
                        } else {
                            MovieItem(
                                movie = movie,
                                onClick = { onMovieClick(movie, viewModel.movies.map { it.localId }) },
                                onWatchedToggle = { viewModel.toggleWatched(movie.localId) }
                            )
                        }
                    }
                    if (viewModel.isLoadingMore) {
                        if (isListMode) {
                            // Zwei Poster-Platzhalter waeren in der Zeilenansicht
                            // zwei halbe Bildschirme hoch.
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                }
                            }
                        } else {
                            items(2) { MovieCardSkeleton() }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Umschalter zwischen Poster-Raster und Zeilenansicht.
 *
 * Zeigt als Beschriftung die Darstellung, zu der getippt wird — nicht die
 * gerade aktive: welche aktiv ist, sieht man ja an der Liste darunter.
 */
@Composable
private fun ViewModeChip(viewMode: CollectionViewMode, onToggle: () -> Unit) {
    val toList = viewMode == CollectionViewMode.GRID
    AssistChip(
        onClick = onToggle,
        label = {
            Text(
                stringResource(if (toList) R.string.view_mode_list else R.string.view_mode_grid),
                style = MaterialTheme.typography.labelMedium
            )
        },
        leadingIcon = {
            Icon(
                imageVector = if (toList) Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
        },
        shape = info.movieshelf.ui.theme.PillShape,
        colors = AssistChipDefaults.assistChipColors(
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            leadingIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    )
}

/**
 * Suchfeld des Dashboards.
 *
 * Sitzt zwischen Hero und der ersten Reihe statt in der Kopfzeile: dort nahm es
 * dauerhaft Platz weg, obwohl gesucht selten wird — und der Hero soll das erste
 * sein, was man sieht. Beim Tippen ersetzt die Trefferliste ohnehin die Reihen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DashboardSearchField(viewModel: DashboardViewModel, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = viewModel.searchQuery,
        onValueChange = { viewModel.onSearchQueryChange(it) },
        placeholder = { Text(stringResource(R.string.dashboard_search_hint)) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = {
            if (viewModel.searchQuery.isNotEmpty()) {
                IconButton(onClick = { viewModel.onSearchQueryChange("") }) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.common_clear))
                }
            }
        },
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        shape = MaterialTheme.shapes.medium,
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            unfocusedBorderColor = Color.Transparent
        )
    )
}

/** Sichtbare Höhe des Hero-Banners unterhalb der Kopfzeile. */
private val HERO_HEIGHT = 420.dp

/** Wechselintervall des Hero-Sliders — wie im Web (`setInterval(..., 8000)`). */
private const val HERO_AUTO_ADVANCE_MS = 8_000L

/**
 * Hero-Banner über den Shelf-Reihen, nachgebaut nach dem Slider im Web
 * (`tenant/movies/partials/streaming-layout.blade.php`): Backdrop mit drei
 * Gradient-Scrims nach `#0c0c0e`, "Featured"-Badge, großer Titel, Kurztext,
 * heller Details-Button und Slider-Punkte. Wechselt automatisch, solange der
 * Nutzer nicht selbst wischt.
 *
 * Die Scrims sind bewusst in beiden Themes dunkel: sie liegen auf einem Foto,
 * die Schrift darauf ist immer weiß.
 */
@Composable
fun HeroSlider(
    movies: List<Movie>,
    onClick: (Movie) -> Unit,
    modifier: Modifier = Modifier,
    /** Höhe der darüberliegenden Kopfzeile, hinter die der Banner reicht. */
    extraTopHeight: Dp = 0.dp
) {
    if (movies.isEmpty()) return
    val context = LocalContext.current
    val pagerState = rememberPagerState(pageCount = { movies.size })

    if (movies.size > 1) {
        LaunchedEffect(pagerState) {
            while (true) {
                delay(HERO_AUTO_ADVANCE_MS)
                // Ein laufender Wisch des Nutzers hat Vorrang.
                if (!pagerState.isScrollInProgress) {
                    pagerState.animateScrollToPage(
                        page = (pagerState.currentPage + 1) % movies.size,
                        animationSpec = tween(durationMillis = 900)
                    )
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(HERO_HEIGHT + extraTopHeight)
            .clip(HeroBannerShape)
    ) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            val movie = movies[page]
            Box(Modifier.fillMaxSize()) {
                AsyncImage(
                    model = resolveImageUrl(context, movie.backdropUrl ?: movie.coverUrl ?: ""),
                    contentDescription = movie.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0f to BackgroundDark,
                                0.25f to Color.Transparent,
                                0.55f to BackgroundDark.copy(alpha = 0.6f),
                                1f to BackgroundDark
                            )
                        )
                )
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                listOf(BackgroundDark, Color.Transparent, Color.Transparent)
                            )
                        )
                )

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(horizontal = 20.dp, vertical = 28.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Featured",
                            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.5.sp),
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(NavAccentRed)
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = listOfNotNull(movie.year?.toString(), movie.collectionType)
                                .joinToString(" • "),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                    }

                    Spacer(Modifier.height(10.dp))

                    Text(
                        text = movie.title ?: "",
                        style = MaterialTheme.typography.displaySmall.copy(
                            fontWeight = FontWeight.Black,
                            fontSize = 34.sp,
                            lineHeight = 38.sp,
                            letterSpacing = (-1).sp
                        ),
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (!movie.overview.isNullOrBlank()) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            // Die Beschreibung kommt als HTML aus dem Quill-Editor.
                            text = AnnotatedString.fromHtml(movie.overview).text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.7f),
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(Modifier.height(18.dp))

                    Button(
                        onClick = { onClick(movie) },
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = Color.Black
                        ),
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.common_details), fontWeight = FontWeight.Black)
                    }
                }
            }
        }

        if (movies.size > 1) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 20.dp, bottom = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                repeat(movies.size) { index ->
                    val selected = pagerState.currentPage == index
                    Box(
                        Modifier
                            .height(6.dp)
                            .width(if (selected) 20.dp else 6.dp)
                            .clip(CircleShape)
                            .background(if (selected) Color.White else Color.White.copy(alpha = 0.35f))
                    )
                }
            }
        }
    }
}

/**
 * "Shelf"-Gruppierung: eine horizontal scrollbare, betitelte Filmreihe
 * ("Neue Filme" / "Filme" / "Serien"), analog zu den Sektionen im Web-Dashboard.
 * Der Header besteht aus fettem Titel und Anzahl, nicht aus dem gedimmten
 * Versal-Header der Formulare.
 */
@Composable
fun MovieShelfRow(
    title: String,
    movies: List<Movie>,
    onClick: (Movie) -> Unit,
    onShowAll: (() -> Unit)? = null,
    /**
     * Zahl neben dem Titel. Ohne Angabe die Laenge der Reihe — fuer eine
     * Auswahl wie "Neue Filme" ist das die richtige Antwort. Die Kategorien
     * geben stattdessen die Groesse der Sammlung mit: dort steht ein Boxset
     * als ein Eintrag in der Reihe, gemeint sind aber die Filme darin.
     */
    count: Int? = null
) {
    val context = LocalContext.current
    Column(modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Black,
                        fontSize = 20.sp
                    ),
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (movies.isNotEmpty()) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = (count ?: movies.size).toString(),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (onShowAll != null) {
                TextButton(
                    onClick = onShowAll,
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    Text(
                        stringResource(R.string.common_show_all).uppercase(),
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
                        fontWeight = FontWeight.Black
                    )
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(14.dp))
                }
            }
        }
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
                    contentDescription = stringResource(if (movie.isWatched == true) R.string.detail_mark_unwatched else R.string.detail_mark_watched),
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
