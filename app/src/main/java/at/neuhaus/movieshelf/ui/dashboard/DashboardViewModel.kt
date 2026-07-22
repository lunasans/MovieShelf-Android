package at.neuhaus.movieshelf.ui.dashboard

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import at.neuhaus.movieshelf.data.SessionManager
import at.neuhaus.movieshelf.data.model.Movie
import at.neuhaus.movieshelf.data.repository.MovieRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.Collator
import java.util.Locale

enum class SortOption(val label: String) {
    BY_NEWEST("Zuletzt hinzugefügt"),
    BY_ALPHA("Alphabetisch"),
    BY_YEAR("Nach Jahr"),
    BY_RATING("Nach Bewertung")
}

/** Kategorie einer Dashboard-Shelf-Reihe, für die "Alle anzeigen"-Rasteransicht. */
enum class ShelfCategory(val label: String) {
    NEW("Neue Filme"),
    FILME("Filme"),
    SERIEN("Serien")
}

data class FilterState(
    val selectedGenres: Set<String> = emptySet(),
    val selectedDirectors: Set<String> = emptySet(),
    val yearFrom: Int? = null,
    val yearTo: Int? = null
) {
    val isActive: Boolean
        get() = selectedGenres.isNotEmpty() || selectedDirectors.isNotEmpty() || yearFrom != null || yearTo != null
}

class DashboardViewModel(private val repository: MovieRepository) : ViewModel() {

    var movies by mutableStateOf<List<Movie>>(emptyList())
        private set
    var isLoading by mutableStateOf(false)
    var isRefreshing by mutableStateOf(false)
    var isLoadingMore by mutableStateOf(false)
    var hasMore by mutableStateOf(true)
    var error by mutableStateOf<String?>(null)
    var isOffline by mutableStateOf(false)

    var searchQuery by mutableStateOf("")
    var sortOption by mutableStateOf(SortOption.BY_NEWEST)
    var filterState by mutableStateOf(FilterState())

    // Für den Filter-BottomSheet
    var availableGenres by mutableStateOf<List<String>>(emptyList())
        private set
    var availableDirectors by mutableStateOf<List<String>>(emptyList())
        private set
    var yearRange by mutableStateOf<Pair<Int, Int>?>(null)
        private set

    // Vertikale "Shelf"-Reihen der Startseite (unabhängig von Suche/Filter)
    var newMoviesShelf by mutableStateOf<List<Movie>>(emptyList())
        private set
    var filmeShelf by mutableStateOf<List<Movie>>(emptyList())
        private set
    var seriesShelf by mutableStateOf<List<Movie>>(emptyList())
        private set

    // "Alle anzeigen" einer Shelf-Reihe: zeigt die Kategorie als Raster
    var selectedShelf by mutableStateOf<ShelfCategory?>(null)
        private set

    private var allLoadedMovies: List<Movie> = emptyList()
    private var currentPage = 1
    private val pageSize = 30
    private var searchJob: Job? = null
    private var autoLoadJob: Job? = null

    init {
        loadMovies()
        loadNewMoviesShelf()
    }

    fun loadMovies(refresh: Boolean = false) {
        if (SessionManager.isDemo) {
            loadDemoMovies()
            return
        }
        autoLoadJob?.cancel()
        viewModelScope.launch {
            if (refresh) {
                isRefreshing = true
                currentPage = 1
            } else {
                isLoading = true
            }
            error = null
            try {
                val result = repository.getMovies(page = 1, perPage = pageSize, tag = null)
                isOffline = repository.isOffline
                allLoadedMovies = result.filter { it.boxsetParentId == null }
                currentPage = 1
                hasMore = result.size >= pageSize && !isOffline
                recompute()
                refreshFilterOptions()
                loadAllRemainingPages()
            } catch (e: Exception) {
                error = friendlyError(e)
            } finally {
                isLoading = false
                isRefreshing = false
            }
        }
    }

    /**
     * Lädt nach der ersten Seite alle weiteren Seiten im Hintergrund nach, damit die
     * Shelf-Reihen ("Filme"/"Serien") die komplette Sammlung zeigen — die Reihen haben
     * keinen Scroll-Trigger für Pagination, und Serien tauchen sonst u.U. gar nicht auf,
     * wenn sie nicht unter den neuesten [pageSize] Titeln sind.
     */
    private fun loadAllRemainingPages() {
        if (SessionManager.isDemo || isOffline) return
        autoLoadJob?.cancel()
        autoLoadJob = viewModelScope.launch {
            while (hasMore) {
                try {
                    val nextPage = currentPage + 1
                    val raw = repository.getMovies(page = nextPage, perPage = pageSize, tag = null)
                    if (repository.isOffline) { isOffline = true; break }
                    currentPage = nextPage
                    val existing = allLoadedMovies.mapTo(HashSet()) { it.id }
                    val newItems = raw.filter { it.boxsetParentId == null && it.id !in existing }
                    if (newItems.isNotEmpty()) {
                        allLoadedMovies = allLoadedMovies + newItems
                        recompute()
                    }
                    // Rohgröße prüfen, nicht die gefilterte: eine volle Seite aus
                    // Boxset-Kindern darf die Pagination nicht vorzeitig beenden.
                    hasMore = raw.size >= pageSize
                } catch (_: Exception) {
                    break // Reihen zeigen dann den bisher geladenen Stand
                }
            }
        }
    }

    /** Lädt die "Neue Filme"-Shelf-Reihe (Server-Tag "new"), unabhängig von der Haupt-Pagination. */
    private fun loadNewMoviesShelf() {
        if (SessionManager.isDemo) return
        viewModelScope.launch {
            try {
                newMoviesShelf = repository.getMovies(page = 1, perPage = 20, tag = "new")
                    .filter { it.boxsetParentId == null }
            } catch (_: Exception) {
                // Shelf bleibt leer, kein kritischer Fehler
            }
        }
    }

    fun loadMore() {
        if (!hasMore || isLoadingMore || isLoading || searchQuery.isNotBlank() || isOffline) return
        // Der Hintergrund-Nachlader füllt bereits alle Seiten auf — nicht doppelt laden
        if (autoLoadJob?.isActive == true) return
        viewModelScope.launch {
            isLoadingMore = true
            try {
                val nextPage = currentPage + 1
                val raw = repository.getMovies(page = nextPage, perPage = pageSize, tag = null)
                currentPage = nextPage
                val existing = allLoadedMovies.mapTo(HashSet()) { it.id }
                val newItems = raw.filter { it.boxsetParentId == null && it.id !in existing }
                if (newItems.isNotEmpty()) {
                    allLoadedMovies = allLoadedMovies + newItems
                    recompute()
                }
                hasMore = raw.size >= pageSize
            } catch (_: Exception) {
                // Pagination-Fehler still ignorieren
            } finally {
                isLoadingMore = false
            }
        }
    }

    /** "Alle anzeigen" einer Shelf-Reihe: Kategorie als Raster öffnen. */
    fun onShelfSelected(category: ShelfCategory) {
        selectedShelf = category
        sortOption = if (category == ShelfCategory.NEW) SortOption.BY_NEWEST else SortOption.BY_ALPHA
        recompute()
    }

    /** Rasteransicht schließen, zurück zu den Shelf-Reihen. */
    fun clearShelf() {
        selectedShelf = null
        recompute()
    }

    fun onSortSelected(option: SortOption) {
        sortOption = option
        recompute()
    }

    fun onFilterChanged(newFilter: FilterState) {
        filterState = newFilter
        recompute()
    }

    fun clearFilters() {
        filterState = FilterState()
        recompute()
    }

    fun onSearchQueryChange(newQuery: String) {
        searchQuery = newQuery
        searchJob?.cancel()
        // Suche ersetzt allLoadedMovies — paralleles Anhängen des Nachladers verhindern
        if (newQuery.isNotBlank()) autoLoadJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(500)
            if (newQuery.isBlank()) {
                loadMovies()
            } else {
                performSearch(newQuery)
            }
        }
    }

    fun toggleWatched(movieId: Int) {
        val movie = allLoadedMovies.find { it.id == movieId } ?: return
        val currentState = movie.isWatched ?: false

        // Optimistisches Update
        allLoadedMovies = allLoadedMovies.map {
            if (it.id == movieId) it.copy(isWatched = !currentState) else it
        }
        recompute()

        if (SessionManager.isDemo) return

        viewModelScope.launch {
            try {
                repository.toggleWatched(movieId, currentState)
            } catch (_: Exception) {
                // Zurückrollen bei Fehler
                allLoadedMovies = allLoadedMovies.map {
                    if (it.id == movieId) it.copy(isWatched = currentState) else it
                }
                recompute()
            }
        }
    }

    private fun recompute() {
        seriesShelf = allLoadedMovies.filter { it.collectionType == "Serie" }
        filmeShelf = allLoadedMovies.filter { it.collectionType != "Serie" }
        viewModelScope.launch {
            val result = withContext(Dispatchers.Default) { computeFilteredSorted() }
            movies = result
        }
    }

    /**
     * Reine Berechnung: filtert + sortiert aus [allLoadedMovies], [filterState] und [sortOption]
     * und gibt die fertige Liste zurück (kein State-Schreiben). Thread-sicher, da der nicht
     * thread-sichere [Collator] und der Titel-Comparator lokal erzeugt werden.
     */
    private fun computeFilteredSorted(): List<Movie> {
        // Locale-bewusster Vergleich: 'ä' wird wie 'a' einsortiert (statt hinter 'z'),
        // Groß-/Kleinschreibung ignoriert. Lokal erzeugt -> nichts wird zwischen parallelen Läufen geteilt.
        val collator: Collator = Collator.getInstance(Locale.GERMAN).apply {
            strength = Collator.SECONDARY
        }
        val byTitle: Comparator<Movie> =
            Comparator { a, b -> collator.compare(titleSortKey(a.title), titleSortKey(b.title)) }

        var filtered = allLoadedMovies

        // Shelf-Kategorie ("Alle anzeigen" einer Reihe)
        when (selectedShelf) {
            ShelfCategory.FILME -> filtered = filtered.filter { it.collectionType != "Serie" }
            ShelfCategory.SERIEN -> filtered = filtered.filter { it.collectionType == "Serie" }
            ShelfCategory.NEW -> {
                val newIds = newMoviesShelf.mapTo(HashSet()) { it.id }
                filtered = filtered.filter { it.id in newIds }
            }
            null -> {}
        }

        // Genre-Filter (Komma-separierte Genre-Spalte)
        if (filterState.selectedGenres.isNotEmpty()) {
            filtered = filtered.filter { movie ->
                val movieGenres = movie.genre?.split(",")?.map { it.trim().lowercase() } ?: emptyList()
                filterState.selectedGenres.any { selected -> movieGenres.contains(selected.lowercase()) }
            }
        }

        // Regisseur-Filter
        if (filterState.selectedDirectors.isNotEmpty()) {
            filtered = filtered.filter { movie ->
                filterState.selectedDirectors.contains(movie.director)
            }
        }

        // Jahr-Filter
        filterState.yearFrom?.let { from ->
            filtered = filtered.filter { (it.year ?: 0) >= from }
        }
        filterState.yearTo?.let { to ->
            filtered = filtered.filter { (it.year ?: Int.MAX_VALUE) <= to }
        }

        return when (sortOption) {
            // Zuletzt hinzugefügt: nach Server-Datum (created_at, ISO-8601 sortiert lexikografisch),
            // fehlendes Datum ans Ende, bei Gleichstand höchste ID zuerst.
            SortOption.BY_NEWEST -> filtered.sortedWith(
                compareByDescending<Movie> { it.createdAt ?: "" }.thenByDescending { it.id }
            )
            // Alphabetisch: deutscher Collator (Umlaute korrekt), führende Artikel ignoriert
            SortOption.BY_ALPHA  -> filtered.sortedWith(byTitle.thenByDescending { it.year ?: 0 })
            // Nach Jahr (neueste zuerst), bei Gleichstand alphabetisch
            SortOption.BY_YEAR   -> filtered.sortedWith(
                compareByDescending<Movie> { it.year ?: Int.MIN_VALUE }.then(byTitle)
            )
            // Nach Bewertung (höchste zuerst), bei Gleichstand alphabetisch
            SortOption.BY_RATING -> filtered.sortedWith(
                compareByDescending<Movie> { parseRating(it.rating) }.then(byTitle)
            )
        }
    }

    /** Entfernt führende Artikel und Leerzeichen für die Sortierung (wie in Mediatheken). */
    private fun titleSortKey(title: String?): String {
        val t = title?.trim().orEmpty()
        val lower = t.lowercase(Locale.GERMAN)
        val articles = listOf("der ", "die ", "das ", "the ", "ein ", "eine ", "an ", "a ")
        val article = articles.firstOrNull { lower.startsWith(it) }
        return (if (article != null) t.substring(article.length) else t).trim()
    }

    /** Bewertung tolerant parsen: akzeptiert "8.5" und "8,5"; ungültig -> nach unten. */
    private fun parseRating(rating: String?): Double =
        rating?.replace(',', '.')?.toDoubleOrNull() ?: -1.0

    private fun refreshFilterOptions() {
        viewModelScope.launch {
            availableGenres = repository.getDistinctGenres()
            availableDirectors = repository.getDistinctDirectors()
            yearRange = repository.getYearRange()
        }
    }

    private suspend fun performSearch(query: String) {
        isLoading = true
        error = null
        try {
            val result = if (SessionManager.isDemo) {
                delay(300)
                getDemoMovies().filter { it.title?.contains(query, ignoreCase = true) == true }
            } else {
                repository.searchMovies(query)
            }
            allLoadedMovies = result.filter { it.boxsetParentId == null }
            isOffline = repository.isOffline
            recompute()
            hasMore = false
        } catch (e: Exception) {
            error = friendlyError(e)
        } finally {
            isLoading = false
        }
    }

    private fun loadDemoMovies() {
        viewModelScope.launch {
            isLoading = true
            delay(500)
            allLoadedMovies = getDemoMovies()
            isOffline = false
            hasMore = false
            recompute()
            availableGenres = listOf("Sci-Fi", "Action")
            availableDirectors = listOf("Christopher Nolan")
            yearRange = 2008 to 2014
            newMoviesShelf = allLoadedMovies.take(2)
            isLoading = false
        }
    }

    private fun friendlyError(e: Exception): String {
        val msg = e.message ?: ""
        return when {
            msg.contains("Unable to resolve host", ignoreCase = true) ||
            msg.contains("failed to connect", ignoreCase = true) ->
                "Server nicht erreichbar. Zeige zwischengespeicherte Daten."
            msg.contains("timeout", ignoreCase = true) ->
                "Zeitüberschreitung. Zeige zwischengespeicherte Daten."
            msg.contains("401") || msg.contains("Unauthorized", ignoreCase = true) ->
                "Sitzung abgelaufen. Bitte erneut anmelden."
            msg.contains("403") -> "Zugriff verweigert."
            msg.contains("404") -> "Inhalte nicht gefunden."
            msg.contains("500") || msg.contains("502") || msg.contains("503") ->
                "Serverfehler. Bitte später erneut versuchen."
            else -> "Filme konnten nicht geladen werden."
        }
    }

    private fun getDemoMovies(): List<Movie> = listOf(
        Movie(id = 1, title = "Inception", year = 2010, rating = "8.8", genre = "Sci-Fi",
            overview = "Ein Dieb, der Geheimnisse aus dem Unterbewusstsein stiehlt.",
            coverUrl = "res:inception_cover", backdropUrl = "res:inception_backdrop",
            runtime = 148, director = "Christopher Nolan", actors = emptyList(),
            viewCount = 5, isWatched = true, tmdbId = "27205", tag = "blu-ray",
            trailerUrl = "https://www.youtube.com/watch?v=YoHD9XEInc0"),
        Movie(id = 2, title = "The Dark Knight", year = 2008, rating = "9.0", genre = "Action",
            overview = "Batman kämpft gegen den Joker in Gotham City.",
            coverUrl = "res:dark_knight_cover", backdropUrl = "res:dark_knight_backdrop",
            runtime = 152, director = "Christopher Nolan", actors = emptyList(),
            viewCount = 10, isWatched = true, tmdbId = "155", tag = "dvd",
            trailerUrl = "https://www.youtube.com/watch?v=EXeTwQWaywY"),
        Movie(id = 3, title = "Interstellar", year = 2014, rating = "8.7", genre = "Sci-Fi",
            overview = "Eine Reise durch ein Wurmloch.", coverUrl = null, backdropUrl = null,
            runtime = 169, director = "Christopher Nolan", actors = emptyList(),
            viewCount = 8, isWatched = false, tmdbId = "157336", tag = "4k",
            trailerUrl = "https://www.youtube.com/watch?v=zSWdZVtXT7E"),
        Movie(id = 4, title = "True Detective", year = 2014, rating = "9.0", genre = "Krimi",
            overview = "Zwei Detectives jagen einen Serienmörder in Louisiana.",
            coverUrl = null, backdropUrl = null,
            runtime = 55, director = "Cary Fukunaga", actors = emptyList(),
            viewCount = 3, isWatched = false, tmdbId = "46648", tag = "streaming",
            collectionType = "Serie")
    )

    class Factory(private val repository: MovieRepository) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return DashboardViewModel(repository) as T
        }
    }
}
