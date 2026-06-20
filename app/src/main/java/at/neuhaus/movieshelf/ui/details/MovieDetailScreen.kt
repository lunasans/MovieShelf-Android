package at.neuhaus.movieshelf.ui.details

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import at.neuhaus.movieshelf.data.model.Actor
import at.neuhaus.movieshelf.data.model.Movie
import at.neuhaus.movieshelf.ui.util.resolveImageUrl
import coil.compose.AsyncImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MovieDetailScreen(
    movieId: Int,
    allMovieIds: List<Int> = emptyList(),
    reloadKey: Int = 0,
    onBack: () -> Unit,
    onEditClick: (Int) -> Unit = {},
    onActorClick: (Int) -> Unit = {},
    onActorNameClick: (String) -> Unit = {},
    onMovieClick: (Movie) -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as at.neuhaus.movieshelf.MovieShelfApplication

    if (allMovieIds.isNotEmpty()) {
        val pagerState = androidx.compose.foundation.pager.rememberPagerState(
            initialPage = allMovieIds.indexOf(movieId).coerceAtLeast(0),
            pageCount = { allMovieIds.size }
        )

        androidx.compose.foundation.pager.HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 1
        ) { page ->
            MovieDetailContent(
                movieId = allMovieIds[page],
                reloadKey = reloadKey,
                onBack = onBack,
                onEditClick = onEditClick,
                onActorClick = onActorClick,
                onActorNameClick = onActorNameClick,
                onMovieClick = onMovieClick,
                repository = app.movieRepository
            )
        }
    } else {
        MovieDetailContent(
            movieId = movieId,
            reloadKey = reloadKey,
            onBack = onBack,
            onEditClick = onEditClick,
            onActorClick = onActorClick,
            onActorNameClick = onActorNameClick,
            onMovieClick = onMovieClick,
            repository = app.movieRepository
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MovieDetailContent(
    movieId: Int,
    reloadKey: Int,
    onBack: () -> Unit,
    onEditClick: (Int) -> Unit,
    onActorClick: (Int) -> Unit,
    onActorNameClick: (String) -> Unit,
    onMovieClick: (Movie) -> Unit,
    repository: at.neuhaus.movieshelf.data.repository.MovieRepository
) {
    val viewModel: MovieDetailViewModel = viewModel(
        key = movieId.toString(),
        factory = MovieDetailViewModel.Factory(movieId, repository)
    )
    val movie = viewModel.movie
    val isAdmin = at.neuhaus.movieshelf.data.SessionManager.user?.isAdmin == true

    // Nach erfolgreicher Bearbeitung den Film neu laden
    LaunchedEffect(reloadKey) {
        if (reloadKey > 0) viewModel.loadMovie(movieId)
    }

    val ctx = LocalContext.current
    var showListSheet by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel.listActionMessage) {
        viewModel.listActionMessage?.let {
            Toast.makeText(ctx, it, Toast.LENGTH_SHORT).show()
            viewModel.listActionMessage = null
        }
    }
    LaunchedEffect(viewModel.error) {
        viewModel.error?.let {
            Toast.makeText(ctx, it, Toast.LENGTH_SHORT).show()
            viewModel.error = null
        }
    }
    val scrollState = rememberScrollState()
    val density = LocalDensity.current
    val uriHandler = LocalUriHandler.current

    val headerHeightPx = with(density) { 350.dp.toPx() }
    val toolbarAlpha = (scrollState.value / (headerHeightPx * 0.8f)).coerceIn(0f, 1f)
    val titleStartScroll = with(density) { 250.dp.toPx() }
    val titleEndScroll = with(density) { 320.dp.toPx() }
    val titleAlpha = ((scrollState.value - titleStartScroll) / (titleEndScroll - titleStartScroll)).coerceIn(0f, 1f)
    val titleTranslationY = with(density) { (15.dp * (1f - titleAlpha)).toPx() }

    val surfaceColor = MaterialTheme.colorScheme.surface
    val appBarContainerColor = surfaceColor.copy(alpha = toolbarAlpha)

    val iconContentColor by animateColorAsState(
        targetValue = if (toolbarAlpha > 0.7f) MaterialTheme.colorScheme.onSurface else Color.White,
        animationSpec = tween(300)
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = movie?.title ?: "",
                        maxLines = 1,
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.graphicsLayer {
                            alpha = titleAlpha
                            translationY = titleTranslationY
                        }
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = Color.Black.copy(alpha = 0.4f * (1f - toolbarAlpha)),
                            contentColor = iconContentColor
                        )
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
                actions = {
                    if (movie != null) {
                        IconButton(
                            onClick = {
                                viewModel.loadLists()
                                showListSheet = true
                            },
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = Color.Black.copy(alpha = 0.4f * (1f - toolbarAlpha)),
                                contentColor = iconContentColor
                            )
                        ) {
                            Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = "Zu Liste hinzufügen")
                        }
                    }
                    if (movie != null && isAdmin) {
                        IconButton(
                            onClick = { onEditClick(movieId) },
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = Color.Black.copy(alpha = 0.4f * (1f - toolbarAlpha)),
                                contentColor = iconContentColor
                            )
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = "Bearbeiten")
                        }
                    }
                    if (movie != null) {
                        IconButton(
                            onClick = { viewModel.toggleWishlist() },
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = Color.Black.copy(alpha = 0.4f * (1f - toolbarAlpha)),
                                contentColor = if (movie.isWishlisted == true) Color(0xFFE53935) else iconContentColor
                            )
                        ) {
                            Icon(
                                imageVector = if (movie.isWishlisted == true) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = "Zur Wunschliste"
                            )
                        }
                    }
                    if (movie != null) {
                        IconButton(
                            onClick = { viewModel.toggleWatched() },
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = Color.Black.copy(alpha = 0.4f * (1f - toolbarAlpha)),
                                contentColor = if (movie.isWatched == true) Color(0xFFFFC107) else iconContentColor
                            )
                        ) {
                            Icon(
                                imageVector = if (movie.isWatched == true) Icons.Filled.CheckCircle else Icons.Outlined.CheckCircle,
                                contentDescription = "Gesehen markieren"
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = appBarContainerColor,
                    scrolledContainerColor = appBarContainerColor,
                    navigationIconContentColor = iconContentColor,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = iconContentColor
                )
            )
        }
    ) { padding ->
        if (viewModel.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (movie != null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(scrollState)
            ) {
                MovieBackdropHeader(
                    movie = movie,
                    scrollState = scrollState,
                    headerHeightPx = headerHeightPx,
                    onTrailerClick = { url -> uriHandler.openUri(url) }
                )

                Column(modifier = Modifier.background(MaterialTheme.colorScheme.background).padding(horizontal = 16.dp)) {
                    MovieHeader(
                        movie = movie,
                        scrollState = scrollState,
                        titleStartScroll = titleStartScroll
                    )

                    if (isAdmin && movie.trailerUrl.isNullOrBlank()) {
                        Spacer(Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = { viewModel.fetchTrailer() },
                            enabled = !viewModel.isFetchingTrailer
                        ) {
                            if (viewModel.isFetchingTrailer) {
                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Movie, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Trailer von TMDb holen")
                            }
                        }
                    }

                    Spacer(Modifier.height(24.dp))
                    Text(text = "Handlung", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))

                    MovieDescription(
                        movie = movie,
                        onActorClick = onActorClick,
                        onActorNameClick = onActorNameClick
                    )

                    if (movie.isBoxset == true && !movie.boxsetChildren.isNullOrEmpty()) {
                        Spacer(Modifier.height(32.dp))
                        Text(text = "Enthaltene Filme", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(16.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            movie.boxsetChildren.forEach { childMovie ->
                                BoxsetMovieItem(movie = childMovie, onClick = { onMovieClick(childMovie) })
                            }
                        }
                    }

                    if (!movie.actors.isNullOrEmpty()) {
                        Spacer(Modifier.height(32.dp))
                        Text(text = "Besetzung", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(12.dp))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(bottom = 32.dp, end = 16.dp)
                        ) {
                            items(movie.actors) { actor ->
                                ActorCardItem(actor = actor, onClick = { actor.id?.let { onActorClick(it) } })
                            }
                        }
                    }

                    Spacer(Modifier.height(100.dp))
                }
            }
        }
    }

    if (showListSheet) {
        ModalBottomSheet(onDismissRequest = { showListSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 24.dp)
            ) {
                Text("Zu Liste hinzufügen", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                if (viewModel.availableLists.isEmpty()) {
                    Text(
                        "Keine Listen vorhanden. Lege eine unter „Meine Listen“ (Profil) an.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                } else {
                    viewModel.availableLists.forEach { list ->
                        ListItem(
                            headlineContent = { Text(list.name ?: "Liste") },
                            supportingContent = { Text("${list.movieCount ?: list.movieRemoteIds?.size ?: 0} Filme") },
                            leadingContent = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null) },
                            modifier = Modifier.clickable {
                                viewModel.addToList(list)
                                showListSheet = false
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MovieBackdropHeader(
    movie: Movie,
    scrollState: androidx.compose.foundation.ScrollState,
    headerHeightPx: Float,
    onTrailerClick: (String) -> Unit
) {
    val context = LocalContext.current
    val backdropUrl = movie.backdropUrl ?: movie.coverUrl

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(350.dp)
            .graphicsLayer {
                translationY = scrollState.value * 0.5f
                alpha = 1f - (scrollState.value / headerHeightPx).coerceIn(0f, 0.7f)
            }
    ) {
        if (backdropUrl != null) {
            val model: Any? = remember(backdropUrl) { resolveImageUrl(context, backdropUrl) }
            AsyncImage(
                model = model,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Black.copy(alpha = 0.4f), Color.Transparent, MaterialTheme.colorScheme.background)
                    )
                )
        )

        if (!movie.trailerUrl.isNullOrBlank()) {
            val trailerUrl = movie.trailerUrl
            FilledIconButton(
                onClick = { onTrailerClick(trailerUrl) },
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(64.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = Color.White.copy(alpha = 0.8f),
                    contentColor = Color.Black
                )
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Trailer abspielen",
                    modifier = Modifier.size(40.dp)
                )
            }
        } else {
            FilledTonalButton(
                onClick = {
                    val query = listOfNotNull(movie.title, movie.year?.toString(), "Trailer").joinToString(" ")
                    val searchUrl = "https://www.youtube.com/results?search_query=" +
                        java.net.URLEncoder.encode(query, "UTF-8")
                    onTrailerClick(searchUrl)
                },
                modifier = Modifier.align(Alignment.Center),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = Color.Black.copy(alpha = 0.5f),
                    contentColor = Color.White
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text(text = "Trailer suchen")
            }
        }
    }
}

@Composable
private fun MovieHeader(
    movie: Movie,
    scrollState: androidx.compose.foundation.ScrollState,
    titleStartScroll: Float
) {
    val fadeModifier = Modifier.graphicsLayer {
        alpha = (1f - (scrollState.value / titleStartScroll)).coerceIn(0f, 1f)
    }

    Text(
        text = movie.title ?: "",
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
        modifier = fadeModifier
    )

    Spacer(Modifier.height(8.dp))

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth().then(fadeModifier)
    ) {
        movie.ratingAge?.let { age -> FskBadge(age = age) }
        movie.year?.let { MetadataItem(icon = Icons.Default.CalendarToday, text = it.toString()) }
        movie.runtime?.let { MetadataItem(icon = Icons.Default.AccessTime, text = "$it Min.") }
        if (!movie.rating.isNullOrBlank()) {
            MetadataItem(icon = Icons.Default.Star, text = "${movie.rating}/10", iconColor = Color(0xFFFFC107))
        }
    }

    if (!movie.tag.isNullOrBlank()) {
        Spacer(Modifier.height(8.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = fadeModifier
        ) {
            movie.tag.split(",").map { it.trim() }.filter { it.isNotEmpty() }.forEach { tag ->
                TagBadge(tag = tag)
            }
        }
    }

    if (!movie.director.isNullOrBlank()) {
        Spacer(Modifier.height(8.dp))
        MetadataItem(
            icon = Icons.Default.MovieCreation,
            text = "Regie: ${movie.director}",
            modifier = fadeModifier
        )
    }
}

@Composable
private fun MovieDescription(
    movie: Movie,
    onActorClick: (Int) -> Unit,
    onActorNameClick: (String) -> Unit
) {
    val rawDescription = movie.overview ?: "Keine Beschreibung verfügbar."
    val primaryColor = MaterialTheme.colorScheme.primary

    val annotatedString = buildAnnotatedString {
        val pattern = Regex("\\{!Actor\\}(.*?)\\}")
        var lastIndex = 0

        pattern.findAll(rawDescription).forEach { match ->
            val beforeText = rawDescription.substring(lastIndex, match.range.first)
            if (beforeText.isNotEmpty()) {
                append(AnnotatedString.fromHtml(htmlString = beforeText))
            }

            val actorName = match.groupValues[1]
            val start = length
            append(actorName)
            val end = length

            addLink(
                clickable = LinkAnnotation.Clickable(
                    tag = "actor",
                    styles = TextLinkStyles(
                        style = SpanStyle(
                            color = primaryColor,
                            fontWeight = FontWeight.Bold,
                            textDecoration = TextDecoration.Underline
                        )
                    ),
                    linkInteractionListener = {
                        val localActor = movie.actors?.find { it.name?.equals(actorName, ignoreCase = true) == true }
                        if (localActor?.id != null) {
                            onActorClick(localActor.id)
                        } else {
                            onActorNameClick(actorName)
                        }
                    }
                ),
                start = start,
                end = end
            )

            lastIndex = match.range.last + 1
        }

        val afterText = rawDescription.substring(lastIndex)
        if (afterText.isNotEmpty()) {
            append(AnnotatedString.fromHtml(htmlString = afterText))
        }
    }

    Text(
        text = annotatedString,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
fun FskBadge(age: Int) {
    val (color, textColor) = when {
        age <= 0 -> Color.White to Color.Black
        age <= 6 -> Color(0xFFFFEB3B) to Color.Black
        age <= 12 -> Color(0xFF4CAF50) to Color.White
        age <= 16 -> Color(0xFF2196F3) to Color.White
        age >= 18 -> Color(0xFFF44336) to Color.White
        else -> Color.Gray to Color.White
    }

    Surface(
        color = color,
        shape = RoundedCornerShape(4.dp),
        modifier = Modifier.size(width = 36.dp, height = 24.dp).border(width = 1.dp, color = Color.Black.copy(alpha = 0.1f), shape = RoundedCornerShape(4.dp))
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = age.toString(),
                color = textColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun TagBadge(tag: String) {
    val (bg, label) = at.neuhaus.movieshelf.ui.dashboard.movieTagStyle(tag)
    Surface(
        color = bg,
        shape = RoundedCornerShape(6.dp)
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            letterSpacing = 1.sp
        )
    }
}

@Composable
fun MetadataItem(
    icon: ImageVector,
    text: String,
    modifier: Modifier = Modifier,
    iconColor: Color = MaterialTheme.colorScheme.outline
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = iconColor
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline
        )
    }
}

@Composable
fun BoxsetMovieItem(movie: Movie, onClick: () -> Unit) {
    val context = LocalContext.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp)
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            if (movie.coverUrl != null) {
                val model: Any? = remember(movie.coverUrl) { resolveImageUrl(context, movie.coverUrl) }
                AsyncImage(
                    model = model,
                    contentDescription = movie.title,
                    modifier = Modifier.width(70.dp).fillMaxHeight(),
                    contentScale = ContentScale.Crop
                )
            }
            Column(
                modifier = Modifier
                    .padding(12.dp)
                    .fillMaxSize(),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = movie.title ?: "Unbekannt",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = movie.year?.toString() ?: "",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
fun ActorCardItem(actor: Actor, onClick: () -> Unit) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .width(90.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            modifier = Modifier.size(72.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            if (actor.imageUrl != null) {
                val model: Any? = remember(actor.imageUrl) { resolveImageUrl(context, actor.imageUrl) }
                AsyncImage(
                    model = model,
                    contentDescription = actor.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(contentAlignment = Alignment.Center) {
                    Text(text = actor.name?.take(1) ?: "?", style = MaterialTheme.typography.headlineSmall)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = actor.name ?: "Unbekannt",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        if (!actor.role.isNullOrBlank()) {
            Text(
                text = actor.role,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
fun ActorRowItem(actor: Actor, onClick: () -> Unit) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(56.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            if (actor.imageUrl != null) {
                val model: Any? = remember(actor.imageUrl) { resolveImageUrl(context, actor.imageUrl) }
                AsyncImage(
                    model = model,
                    contentDescription = actor.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(contentAlignment = Alignment.Center) {
                    Text(text = actor.name?.take(1) ?: "?", style = MaterialTheme.typography.titleLarge)
                }
            }
        }

        Spacer(Modifier.width(16.dp))

        Column {
            Text(
                text = actor.name ?: "Unbekannt",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            if (!actor.role.isNullOrBlank()) {
                Text(
                    text = actor.role,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
