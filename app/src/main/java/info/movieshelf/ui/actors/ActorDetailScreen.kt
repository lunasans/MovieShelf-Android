package info.movieshelf.ui.actors

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.res.stringResource
import info.movieshelf.R
import info.movieshelf.ui.components.MovieListRow
import info.movieshelf.MovieShelfApplication
import info.movieshelf.data.model.Movie
import info.movieshelf.ui.dashboard.MovieItem
import info.movieshelf.ui.util.resolveImageUrl
import coil.compose.AsyncImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActorDetailScreen(
    actorId: Int,
    onBack: () -> Unit,
    onMovieClick: (Movie) -> Unit
) {
    val app = LocalContext.current.applicationContext as MovieShelfApplication
    val viewModel: ActorDetailViewModel = viewModel(
        factory = ActorDetailViewModel.Factory(actorId, app.actorRepository)
    )
    val actor = viewModel.actor

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(actor?.name ?: stringResource(R.string.actor_title)) },
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
        } else if (viewModel.error != null && actor == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.CloudOff,
                        null,
                        Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        viewModel.error?.asString() ?: stringResource(R.string.error_generic_load),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { viewModel.loadActor() }) { Text(stringResource(R.string.common_retry)) }
                }
            }
        } else if (actor != null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Profilbild
                val context = LocalContext.current
                Surface(
                    modifier = Modifier.size(150.dp),
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
                    }
                }

                Spacer(Modifier.height(16.dp))

                Text(
                    text = actor.name ?: stringResource(R.string.common_unknown),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )

                if (!actor.birthDate.isNullOrBlank()) {
                    Text(
                        // Zwei Fassungen statt angehaengtem Zusatz: die
                        // Wortstellung von Datum und Ort ist nicht in jeder
                        // Sprache dieselbe.
                        text = actor.placeOfBirth
                            ?.let { stringResource(R.string.actor_born_in, actor.birthDate, it) }
                            ?: stringResource(R.string.actor_born, actor.birthDate),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                Spacer(Modifier.height(24.dp))

                if (!actor.biography.isNullOrBlank()) {
                    Text(
                        text = stringResource(R.string.actor_biography),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = actor.biography,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Justify
                    )
                    Spacer(Modifier.height(24.dp))
                }

                if (!actor.movies.isNullOrEmpty()) {
                    Text(
                        text = stringResource(R.string.actor_known_for),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(16.dp))
                    
                    // Wir nutzen hier eine Column statt Grid, da wir uns in einem vertikalen Scrollview befinden
                    actor.movies.forEach { movie ->
                        MovieListRow(
                            movie = movie,
                            imageUrl = resolveImageUrl(context, movie.coverUrl ?: ""),
                            onClick = { onMovieClick(movie) }
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}
