package at.neuhaus.movieshelf.ui.lists

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.MovieFilter
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import at.neuhaus.movieshelf.data.model.Movie
import at.neuhaus.movieshelf.ui.dashboard.MovieItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListDetailScreen(
    listId: Int,
    onBack: () -> Unit,
    onMovieClick: (Movie) -> Unit
) {
    val viewModel: ListDetailViewModel = viewModel(
        key = "list_$listId",
        factory = ListDetailViewModel.Factory(listId)
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(viewModel.name.ifBlank { "Liste" }) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                }
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                viewModel.isLoading && viewModel.movies.isEmpty() -> {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                }
                viewModel.error != null && viewModel.movies.isEmpty() -> {
                    StateMessage(Icons.Default.CloudOff, viewModel.error!!, onRetry = { viewModel.load() })
                }
                viewModel.movies.isEmpty() -> {
                    StateMessage(Icons.Default.MovieFilter, "Diese Liste ist leer.")
                }
                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 150.dp),
                        contentPadding = PaddingValues(8.dp)
                    ) {
                        items(viewModel.movies, key = { it.id }) { movie ->
                            MovieItem(
                                movie = movie,
                                onClick = { onMovieClick(movie) },
                                onWatchedToggle = {}
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StateMessage(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    message: String,
    onRetry: (() -> Unit)? = null
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.outline)
            Spacer(Modifier.height(16.dp))
            Text(message, style = MaterialTheme.typography.titleMedium)
            if (onRetry != null) {
                Spacer(Modifier.height(8.dp))
                Button(onClick = onRetry) { Text("Erneut versuchen") }
            }
        }
    }
}
