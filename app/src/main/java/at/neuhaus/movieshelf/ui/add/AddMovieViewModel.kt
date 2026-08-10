package at.neuhaus.movieshelf.ui.add

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import at.neuhaus.movieshelf.data.repository.TmdbRepository
import at.neuhaus.movieshelf.data.model.TmdbSearchItem
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AddMovieViewModel(
    private val repository: TmdbRepository
) : ViewModel() {

    /** Ohne Shelf und ohne Schluessel gibt es nichts zu suchen. */
    var searchUnavailable by mutableStateOf(false)
        private set
    var searchQuery by mutableStateOf("")
    var searchResults by mutableStateOf<List<Map<String, Any>>>(emptyList())
    var isLoading by mutableStateOf(false)
    var isImporting by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var successMessage by mutableStateOf<String?>(null)
    var importToCollection by mutableStateOf(true)

    init {
        viewModelScope.launch { searchUnavailable = !repository.isSearchAvailable() }
    }

    private var searchJob: Job? = null

    fun onSearchQueryChange(newQuery: String) {
        searchQuery = newQuery
        searchJob?.cancel()
        if (newQuery.length < 2) {
            searchResults = emptyList()
            return
        }
        searchJob = viewModelScope.launch {
            delay(500)
            performTmdbSearch(newQuery)
        }
    }

    private suspend fun performTmdbSearch(query: String) {
        isLoading = true
        error = null
        try {
            val response = repository.search(query)
            searchResults = (response.results ?: emptyList()).map { it.toUiMap() }
        } catch (e: Exception) {
            error = "TMDb-Suche fehlgeschlagen: ${e.message}"
        } finally {
            isLoading = false
        }
    }

    fun importMovie(tmdbId: Int, onComplete: () -> Unit) {
        viewModelScope.launch {
            isImporting = true
            error = null
            try {
                repository.import(tmdbId, importToCollection)
                successMessage = "Film erfolgreich importiert!"
                delay(1500)
                onComplete()
            } catch (e: Exception) {
                error = "Import fehlgeschlagen: ${e.message}"
            } finally {
                isImporting = false
            }
        }
    }

    class Factory(
        private val repository: TmdbRepository
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return AddMovieViewModel(repository) as T
        }
    }
}

/**
 * Bildet das typisierte DTO auf die von AddMovieScreen/TmdbMovieItem erwartete
 * Map ab. Damit bleibt das UI (Map-Zugriffe via "id", "title"/"name",
 * "release_date"/"first_air_date", "poster_path", "overview") unverändert und
 * das Laufzeitverhalten identisch. Null-Werte werden weggelassen, sodass die
 * `as? ...`-Fallbacks im UI genauso greifen wie zuvor.
 */
private fun TmdbSearchItem.toUiMap(): Map<String, Any> {
    val map = mutableMapOf<String, Any>()
    id?.let { map["id"] = it }
    // Über `alternate` zusammengeführter Titel: unter beiden Keys ablegen,
    // damit der title/name-Fallback im UI weiterhin funktioniert.
    title?.let {
        map["title"] = it
        map["name"] = it
    }
    releaseDate?.let {
        map["release_date"] = it
        map["first_air_date"] = it
    }
    posterPath?.let { map["poster_path"] = it }
    overview?.let { map["overview"] = it }
    return map
}
