package at.neuhaus.movieshelf.ui.add

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.neuhaus.movieshelf.data.api.RetrofitClient
import at.neuhaus.movieshelf.data.model.TmdbImportRequest
import at.neuhaus.movieshelf.data.model.TmdbSearchItem
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AddMovieViewModel : ViewModel() {
    var searchQuery by mutableStateOf("")
    var searchResults by mutableStateOf<List<Map<String, Any>>>(emptyList())
    var isLoading by mutableStateOf(false)
    var isImporting by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var successMessage by mutableStateOf<String?>(null)
    var importToCollection by mutableStateOf(true)

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
            val response = RetrofitClient.api.searchTmdb(query)
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
                RetrofitClient.api.importFromTmdb(TmdbImportRequest(
                    tmdbId = tmdbId, 
                    type = "movie",
                    inCollection = importToCollection
                ))
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
