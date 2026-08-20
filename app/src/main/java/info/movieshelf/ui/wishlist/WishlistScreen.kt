package info.movieshelf.ui.wishlist

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import info.movieshelf.MovieShelfApplication
import info.movieshelf.R
import info.movieshelf.data.model.Movie
import info.movieshelf.data.repository.MovieRepository
import info.movieshelf.ui.dashboard.MovieItem
import kotlinx.coroutines.launch

/**
 * Die Wunschliste: vorgemerkte Titel, die noch nicht in der Sammlung stehen.
 *
 * Bisher liess sich die Wunschliste per Herz im Detail füllen, aber nirgends
 * ansehen — ein Topf ohne Deckel. Die Shelf unterscheidet Sammlung und
 * Wunschliste über `in_collection`; es sind dieselben Zeilen, nur ohne Besitz.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WishlistScreen(
    onBack: () -> Unit,
    onMovieClick: (Movie) -> Unit
) {
    val app = LocalContext.current.applicationContext as MovieShelfApplication
    val viewModel: WishlistViewModel = viewModel(
        factory = WishlistViewModel.Factory(app.movieRepository)
    )

    // Nach dem Zurückkehren aus der Detailansicht neu laden: dort kann das Herz
    // gerade umgeschaltet worden sein, und ein Titel, der nicht mehr vorgemerkt
    // ist, hat hier nichts verloren.
    LaunchedEffect(Unit) { viewModel.load() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.wishlist_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        when {
            viewModel.isLoading -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }

            viewModel.movies.isEmpty() -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 32.dp)
                ) {
                    Icon(
                        Icons.Default.FavoriteBorder,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        stringResource(R.string.wishlist_empty),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.wishlist_empty_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }

            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 150.dp),
                contentPadding = PaddingValues(
                    top = padding.calculateTopPadding() + 8.dp,
                    start = 8.dp,
                    end = 8.dp,
                    bottom = 100.dp
                ),
                modifier = Modifier.fillMaxSize()
            ) {
                items(viewModel.movies, key = { it.id }) { movie ->
                    MovieItem(
                        movie = movie,
                        onClick = { onMovieClick(movie) },
                        // Was man noch nicht besitzt, hat man auch nicht
                        // gesehen — der Schalter bliebe hier ohne Bedeutung.
                        onWatchedToggle = {}
                    )
                }
            }
        }
    }
}

class WishlistViewModel(private val repository: MovieRepository) : ViewModel() {

    var movies by mutableStateOf<List<Movie>>(emptyList())
        private set
    var isLoading by mutableStateOf(true)
        private set

    fun load() {
        viewModelScope.launch {
            isLoading = true
            movies = runCatching { repository.getWishlist() }.getOrDefault(emptyList())
            isLoading = false
        }
    }

    class Factory(private val repository: MovieRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            WishlistViewModel(repository) as T
    }
}
