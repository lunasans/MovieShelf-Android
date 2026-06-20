package at.neuhaus.movieshelf.ui.lists

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MovieFilter
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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

    var removeTarget by remember { mutableStateOf<Movie?>(null) }

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
                            Box {
                                MovieItem(
                                    movie = movie,
                                    onClick = { onMovieClick(movie) },
                                    onWatchedToggle = {}
                                )
                                IconButton(
                                    onClick = { removeTarget = movie },
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(4.dp)
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.45f))
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Aus Liste entfernen",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    removeTarget?.let { movie ->
        AlertDialog(
            onDismissRequest = { removeTarget = null },
            title = { Text("Film entfernen") },
            text = { Text("\"${movie.title ?: "Dieser Film"}\" aus der Liste entfernen?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.removeMovie(movie.id)
                    removeTarget = null
                }) { Text("Entfernen") }
            },
            dismissButton = {
                TextButton(onClick = { removeTarget = null }) { Text("Abbrechen") }
            }
        )
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
