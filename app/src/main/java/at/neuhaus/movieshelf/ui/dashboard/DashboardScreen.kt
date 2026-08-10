package at.neuhaus.movieshelf.ui.dashboard

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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import at.neuhaus.movieshelf.MovieShelfApplication
import at.neuhaus.movieshelf.R
import at.neuhaus.movieshelf.data.model.Movie
import at.neuhaus.movieshelf.ui.components.FloatingNavBar
import at.neuhaus.movieshelf.ui.components.PosterCard
import at.neuhaus.movieshelf.ui.components.RatingBadge
import at.neuhaus.movieshelf.ui.theme.BackgroundDark
import at.neuhaus.movieshelf.ui.theme.HeroBannerShape
import at.neuhaus.movieshelf.ui.theme.NavAccentRed
import at.neuhaus.movieshelf.ui.theme.NavAccentRose500
import kotlinx.coroutines.delay
import at.neuhaus.movieshelf.ui.util.MovieCardSkeleton
import at.neuhaus.movieshelf.ui.util.resolveImageUrl
import coil.compose.AsyncImage

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DashboardScreen(
    onMovieClick: (Movie, List<Long>) -> Unit,
    onAboutClick: () -> Unit,
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
            val isBrowsing = viewModel.searchQuery.isBlank() && viewModel.selectedShelf == null

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
                            Text("Noch keine Filme", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = if (isShelfMode) {
                                    "Deine Sammlung wird beim Synchronisieren von der Shelf geholt."
                                } else {
                                    "Lege deinen ersten Film über das Plus an."
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            if (isShelfMode) {
                                Spacer(Modifier.height(16.dp))
                                Button(onClick = onSyncClick, shape = at.neuhaus.movieshelf.ui.theme.PillShape) {
                                    Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Jetzt synchronisieren", fontWeight = FontWeight.Bold)
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
                        // Hero speist sich aus den Neuzugängen; ohne Backdrop
                        // wirkt der Banner beschnitten, daher nur solche Filme.
                        val heroMovies = remember(viewModel.newMoviesShelf) {
                            viewModel.newMoviesShelf
                                .filter { !it.backdropUrl.isNullOrBlank() }
                                .take(5)
                        }
                        if (heroMovies.isNotEmpty()) {
                            HeroSlider(
                                movies = heroMovies,
                                onClick = { movie ->
                                    onMovieClick(movie, heroMovies.map { it.localId })
                                }
                            )
                            Spacer(Modifier.height(8.dp))
                        }

                        if (viewModel.newMoviesShelf.isNotEmpty()) {
                            MovieShelfRow(
                                title = "Neue Filme",
                                icon = Icons.Default.NewReleases,
                                movies = viewModel.newMoviesShelf,
                                onClick = { movie ->
                                    onMovieClick(movie, viewModel.newMoviesShelf.map { it.localId })
                                },
                                onShowAll = { viewModel.onShelfSelected(ShelfCategory.NEW) }
                            )
                        }
                        if (viewModel.filmeShelf.isNotEmpty()) {
                            MovieShelfRow(
                                title = "Filme",
                                icon = Icons.Default.Movie,
                                movies = viewModel.filmeShelf,
                                onClick = { movie ->
                                    onMovieClick(movie, viewModel.filmeShelf.map { it.localId })
                                },
                                onShowAll = { viewModel.onShelfSelected(ShelfCategory.FILME) }
                            )
                        }
                        if (viewModel.seriesShelf.isNotEmpty()) {
                            MovieShelfRow(
                                title = "Serien",
                                icon = Icons.Default.Tv,
                                movies = viewModel.seriesShelf,
                                onClick = { movie ->
                                    onMovieClick(movie, viewModel.seriesShelf.map { it.localId })
                                },
                                onShowAll = { viewModel.onShelfSelected(ShelfCategory.SERIEN) }
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
                        if (viewModel.searchQuery.isNotEmpty() || viewModel.selectedShelf != null) {
                            TextButton(onClick = {
                                viewModel.onSearchQueryChange("")
                                viewModel.clearShelf()
                            }) {
                                Text("Auswahl aufheben")
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
                    // "Alle anzeigen"-Modus: Kategorie-Chip als Rückweg zu den Shelf-Reihen
                    viewModel.selectedShelf?.let { shelf ->
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Row(modifier = Modifier.padding(horizontal = 8.dp)) {
                                InputChip(
                                    selected = true,
                                    onClick = { viewModel.clearShelf() },
                                    label = { Text("${shelf.label} · ${viewModel.movies.size}") },
                                    trailingIcon = { Icon(Icons.Default.Close, null, Modifier.size(14.dp)) }
                                )
                            }
                        }
                    }
                    items(viewModel.movies, key = { it.id }) { movie ->
                        MovieItem(
                            movie = movie,
                            onClick = { onMovieClick(movie, viewModel.movies.map { it.localId }) },
                            onWatchedToggle = { viewModel.toggleWatched(movie.localId) }
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
    modifier: Modifier = Modifier
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
            .height(420.dp)
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
                        Text("Details", fontWeight = FontWeight.Black)
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
 * Der Header folgt dort dem Muster „Gradient-Kachel + fetter Titel + Anzahl",
 * nicht dem gedimmten Versal-Header der Formulare.
 */
@Composable
fun MovieShelfRow(
    title: String,
    movies: List<Movie>,
    icon: ImageVector,
    onClick: (Movie) -> Unit,
    onShowAll: (() -> Unit)? = null
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
                Box(
                    modifier = Modifier
                        .shadow(
                            elevation = 8.dp,
                            shape = RoundedCornerShape(12.dp),
                            ambientColor = NavAccentRed,
                            spotColor = NavAccentRed
                        )
                        .clip(RoundedCornerShape(12.dp))
                        // Alle Reihen tragen den Marken-Verlauf; unterschieden
                        // wird über das Icon, nicht über die Farbe.
                        .background(Brush.linearGradient(listOf(NavAccentRose500, NavAccentRed)))
                        .size(36.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(12.dp))
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
                        text = movies.size.toString(),
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
                        "Alle anzeigen".uppercase(),
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
