package at.neuhaus.movieshelf.ui.details

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import at.neuhaus.movieshelf.data.SessionManager
import at.neuhaus.movieshelf.data.api.RetrofitClient
import at.neuhaus.movieshelf.data.model.Actor
import at.neuhaus.movieshelf.data.model.Movie
import at.neuhaus.movieshelf.data.repository.MovieRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MovieDetailViewModel(
    private var initialMovieId: Int,
    private val repository: MovieRepository? = null
) : ViewModel() {
    var movie by mutableStateOf<Movie?>(null)
    var isLoading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)

    init {
        loadMovie(initialMovieId)
    }

    fun loadMovie(movieId: Int) {
        viewModelScope.launch {
            isLoading = true
            error = null
            try {
                if (SessionManager.isDemo) {
                    delay(300)
                    movie = getDemoMovies().find { it.id == movieId }
                    if (movie == null) error = "Film nicht gefunden"
                } else if (repository != null) {
                    movie = repository.getMovie(movieId)
                    if (movie == null) error = "Film nicht gefunden"
                } else {
                    val response = RetrofitClient.api.getMovie(movieId)
                    movie = response.data
                }
            } catch (e: Exception) {
                error = "Film konnte nicht geladen werden."
            } finally {
                isLoading = false
            }
        }
    }

    private fun getDemoMovies(): List<Movie> {
        return listOf(
            Movie(
                id = 1,
                title = "Inception",
                year = 2010,
                rating = "8.8",
                genre = "Sci-Fi",
                overview = "Ein Dieb, der Geheimnisse aus dem Unterbewusstsein stiehlt. {!Actor}Leonardo DiCaprio} spielt die Hauptrolle.",
                coverUrl = "res:inception_cover",
                backdropUrl = "res:inception_backdrop",
                runtime = 148,
                director = "Christopher Nolan",
                actors = listOf(Actor(id = 1, name = "Leonardo DiCaprio", role = "Dom Cobb")),
                viewCount = 5,
                isWatched = true,
                tmdbId = "27205",
                trailerUrl = "https://www.youtube.com/watch?v=YoHD9XEInc0"
            ),
            Movie(
                id = 2,
                title = "The Dark Knight",
                year = 2008,
                rating = "9.0",
                genre = "Action",
                overview = "Batman kämpft gegen den Joker in Gotham City. {!Actor}Christian Bale} ist Batman.",
                coverUrl = "res:dark_knight_cover",
                backdropUrl = "res:dark_knight_backdrop",
                runtime = 152,
                director = "Christopher Nolan",
                actors = listOf(Actor(id = 2, name = "Christian Bale", role = "Bruce Wayne / Batman")),
                viewCount = 10,
                isWatched = true,
                tmdbId = "155",
                trailerUrl = "https://www.youtube.com/watch?v=EXeTwQWaywY"
            ),
            Movie(
                id = 3,
                title = "Interstellar",
                year = 2014,
                rating = "8.7",
                genre = "Sci-Fi",
                overview = "Eine Reise durch ein Wurmloch zur Rettung der Menschheit. {!Actor}Matthew McConaughey} führt die Mission an.",
                coverUrl = null,
                backdropUrl = null,
                runtime = 169,
                director = "Christopher Nolan",
                actors = listOf(Actor(id = 3, name = "Matthew McConaughey", role = "Cooper")),
                viewCount = 8,
                isWatched = true,
                tmdbId = "157336",
                trailerUrl = "https://www.youtube.com/watch?v=zSWdZVtXT7E"
            )
        )
    }

    fun toggleWatched() {
        if (SessionManager.isDemo) {
            movie = movie?.copy(isWatched = !(movie?.isWatched ?: false))
            return
        }
        val currentMovie = movie ?: return
        val currentState = currentMovie.isWatched ?: false

        // Optimistisches UI-Update
        movie = currentMovie.copy(isWatched = !currentState)

        viewModelScope.launch {
            try {
                // Über das Repository, damit auch der lokale Room-Cache aktualisiert wird.
                if (repository != null) {
                    repository.toggleWatched(currentMovie.id, currentState)
                } else {
                    RetrofitClient.api.toggleWatched(currentMovie.id)
                }
            } catch (e: Exception) {
                movie = currentMovie // Rollback bei Fehler
                error = "Fehler beim Aktualisieren: ${e.message}"
            }
        }
    }

    class Factory(
        private val movieId: Int,
        private val repository: MovieRepository? = null
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return MovieDetailViewModel(movieId, repository) as T
        }
    }
}
