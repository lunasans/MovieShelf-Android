package info.movieshelf.ui.add

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import info.movieshelf.data.repository.TmdbRepository
import info.movieshelf.data.model.TmdbSearchItem
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import info.movieshelf.R
import info.movieshelf.ui.util.UiText
import info.movieshelf.data.repository.MissingTmdbKeyException

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
    var error by mutableStateOf<UiText?>(null)
    var successMessage by mutableStateOf<UiText?>(null)
    var importToCollection by mutableStateOf(true)

    /** Suche nach Serien statt nach Filmen. */
    var searchSeries by mutableStateOf(false)
        private set

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
            val response = repository.search(query, searchSeries)
            searchResults = (response.results ?: emptyList()).map { it.toUiMap() }
        } catch (e: MissingTmdbKeyException) {
            // Kein Fehler der Suche, sondern eine fehlende Voraussetzung —
            // dafuer gibt es einen eigenen, uebersetzten Hinweis.
            error = UiText.of(R.string.add_no_tmdb_key)
        } catch (e: Exception) {
            error = UiText.of(R.string.error_tmdb_search, e.message ?: "")
        } finally {
            isLoading = false
        }
    }

    fun onTypeChanged(series: Boolean) {
        if (searchSeries == series) return
        searchSeries = series
        // Ergebnisse gehoeren zum alten Typ und waeren jetzt irrefuehrend.
        searchResults = emptyList()
        if (searchQuery.length >= 2) {
            searchJob?.cancel()
            searchJob = viewModelScope.launch { performTmdbSearch(searchQuery) }
        }
    }

    /**
     * Treffer übernehmen.
     *
     * [onComplete] bekommt die lokale ID des angelegten Eintrags, damit der
     * Aufrufer direkt in die Bearbeitung springen kann — TMDb liefert nicht
     * alles, was man am Ende sehen will, und ohne diesen Schritt müsste man
     * den Film erst in der Sammlung wiederfinden.
     */
    fun importMovie(tmdbId: Int, onComplete: (Long?) -> Unit) {
        viewModelScope.launch {
            isImporting = true
            error = null
            try {
                val localId = repository.import(tmdbId, importToCollection, searchSeries)
                successMessage = UiText.of(R.string.message_import_ok)
                delay(1500)
                onComplete(localId)
            } catch (e: Exception) {
                error = UiText.of(R.string.error_import_failed, e.message ?: "")
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
