package info.movieshelf.ui.dashboard

import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import info.movieshelf.data.model.Movie
import info.movieshelf.data.repository.CollectionCounts
import info.movieshelf.data.repository.MovieRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import info.movieshelf.R
import info.movieshelf.ui.util.UiText

/** Kategorie einer Dashboard-Shelf-Reihe, für die "Alle anzeigen"-Rasteransicht. */
enum class ShelfCategory(@StringRes val labelRes: Int) {
    NEW(R.string.shelf_new_movies),
    FILME(R.string.shelf_movies),
    SERIEN(R.string.shelf_series)
}

/**
 * Wonach die Sammlung sortiert wird. Dieselben Schluessel wie in der
 * Desktop-App (`MoviesView.vue`), damit dieselbe Sammlung auf beiden Wegen
 * dieselbe Reihenfolge ergibt.
 */
enum class SortKey(@StringRes val labelRes: Int) {
    TITLE(R.string.sort_title),
    YEAR(R.string.sort_year),
    RATING(R.string.sort_rating),
    RUNTIME(R.string.sort_runtime),
    ADDED(R.string.sort_added)
}

class DashboardViewModel(private val repository: MovieRepository) : ViewModel() {

    var movies by mutableStateOf<List<Movie>>(emptyList())
        private set
    var isLoading by mutableStateOf(false)
    var isRefreshing by mutableStateOf(false)
    var isLoadingMore by mutableStateOf(false)
    var hasMore by mutableStateOf(true)
    var error by mutableStateOf<UiText?>(null)
    var isOffline by mutableStateOf(false)

    var searchQuery by mutableStateOf("")

    /**
     * Filme im Hero-Bereich. Eigener Stand statt einer Ableitung aus den
     * Reihen: die Auswahl ist zufaellig und darf sich nicht bei jeder
     * Neuberechnung aendern.
     */
    var heroMovies by mutableStateOf<List<Movie>>(emptyList())
        private set

    // Vertikale "Shelf"-Reihen der Startseite (unabhaengig von der Suche)
    var newMoviesShelf by mutableStateOf<List<Movie>>(emptyList())
        private set
    var filmeShelf by mutableStateOf<List<Movie>>(emptyList())
        private set
    var seriesShelf by mutableStateOf<List<Movie>>(emptyList())
        private set

    /**
     * Groesse der Sammlung fuer die Zahlen neben den Ueberschriften.
     *
     * Bewusst nicht aus den geladenen Reihen abgeleitet: dort steht ein Boxset
     * als ein Eintrag, waehrend die Zahl die enthaltenen Filme meinen soll —
     * so wie die Statistik zaehlt. Kommt aus der Datenbank, nicht aus dem Netz.
     */
    var collectionCounts by mutableStateOf<CollectionCounts?>(null)
        private set

    // "Alle anzeigen" einer Shelf-Reihe: zeigt die Kategorie als Raster
    var selectedShelf by mutableStateOf<ShelfCategory?>(null)

    /** Sortierung der Trefferliste. Standard ist der Titel, wie im Desktop. */
    var sortKey by mutableStateOf(SortKey.TITLE)
        private set
    var sortAscending by mutableStateOf(true)
        private set

    /** Genre-Filter; `null` heisst "alle". */
    var selectedGenre by mutableStateOf<String?>(null)
        private set

    /**
     * Alle Genres der Sammlung, alphabetisch.
     *
     * Die Shelf legt sie als kommagetrennte Liste je Film ab ("Action, Drama"),
     * es gibt also keine Genre-Tabelle, aus der sich das lesen liesse — die
     * Auswahl entsteht aus dem, was tatsaechlich in der Sammlung steht.
     */
    var availableGenres by mutableStateOf<List<String>>(emptyList())
        private set

    private var allLoadedMovies: List<Movie> = emptyList()
    private var currentPage = 1
    private val pageSize = 30
    private var searchJob: Job? = null
    private var autoLoadJob: Job? = null

    init {
        loadMovies()
        loadNewMoviesShelf()
        loadHero()
        loadCollectionCounts()
    }

    /** Die Zahlen neben den Ueberschriften — ein Zaehlen in der Datenbank. */
    private fun loadCollectionCounts() {
        viewModelScope.launch {
            try {
                collectionCounts = repository.getCollectionCounts()
            } catch (_: Exception) {
                // Ohne Zahl bleibt die Ueberschrift fuer sich stehen.
            }
        }
    }

    /** Zufaellige Auswahl fuer den Hero-Bereich, einmal je Sitzung gezogen. */
    private fun loadHero() {
        viewModelScope.launch {
            try {
                heroMovies = repository.getFeatured(5)
            } catch (_: Exception) {
                // Ohne Hero laesst sich die Startseite weiterhin benutzen.
            }
        }
    }

    fun loadMovies(refresh: Boolean = false) {
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
                // Nach einem Abgleich stehen andere Zahlen in der Datenbank.
                loadCollectionCounts()
                allLoadedMovies = result
                currentPage = 1
                hasMore = result.size >= pageSize && !isOffline
                recompute()
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
        if (isOffline) return
        autoLoadJob?.cancel()
        autoLoadJob = viewModelScope.launch {
            while (hasMore) {
                try {
                    val nextPage = currentPage + 1
                    val raw = repository.getMovies(page = nextPage, perPage = pageSize, tag = null)
                    if (repository.isOffline) { isOffline = true; break }
                    currentPage = nextPage
                    val existing = allLoadedMovies.mapTo(HashSet()) { it.id }
                    val newItems = raw.filter { it.id !in existing }
                    if (newItems.isNotEmpty()) {
                        allLoadedMovies = allLoadedMovies + newItems
                        recompute()
                    }
                    // Rohgröße prüfen, nicht die entdoppelte: eine volle Seite
                    // bereits bekannter Titel darf die Pagination nicht
                    // vorzeitig beenden.
                    hasMore = raw.size >= pageSize
                } catch (_: Exception) {
                    break // Reihen zeigen dann den bisher geladenen Stand
                }
            }
        }
    }

    /** Lädt die "Neue Filme"-Shelf-Reihe (Server-Tag "new"), unabhängig von der Haupt-Pagination. */
    private fun loadNewMoviesShelf() {
        viewModelScope.launch {
            try {
                newMoviesShelf = repository.getMovies(page = 1, perPage = 20, tag = "new")
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
                val newItems = raw.filter { it.id !in existing }
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
    // ── Mehrfachauswahl ──────────────────────────────────────────────────────

    /**
     * Ausgewaehlte Titel. Leer heisst: kein Auswahlmodus.
     *
     * Ueber die lokale ID, nicht ueber die Server-ID: nur sie gilt fuer jeden
     * Titel, auch fuer einen, den die Shelf noch nicht kennt.
     */
    var selection by mutableStateOf<Set<Long>>(emptySet())
        private set

    /** Laeuft gerade eine Sammelaktion? Solange bleiben die Knoepfe gesperrt. */
    var isBulkRunning by mutableStateOf(false)
        private set

    val selectionActive: Boolean get() = selection.isNotEmpty()

    fun toggleSelection(localId: Long) {
        selection = if (localId in selection) selection - localId else selection + localId
    }

    fun clearSelection() {
        selection = emptySet()
    }

    /** Alles auswaehlen, was gerade in der Liste steht — nicht die ganze Sammlung. */
    fun selectAllVisible() {
        selection = movies.map { it.localId }.toSet()
    }

    /**
     * Gesehen-Stand fuer die Auswahl setzen.
     *
     * Gesetzt, nicht umgeschaltet: bei gemischter Auswahl waere ein Umschalter
     * sinnlos, weil die Haelfte in die falsche Richtung liefe. Titel, die schon
     * richtig stehen, werden uebergangen.
     */
    fun bulkSetWatched(watched: Boolean) = runBulk(
        // Regel in MovieSorting.kt — dort ohne ViewModel pruefbar.
        auswahl = { moviesNeedingWatchedChange(it, watched) }
    ) { movie ->
        repository.toggleWatchedByLocalId(movie.localId, movie.isWatched == true)
    }

    // Eine Sammelaktion fuer die Wunschliste fehlt bewusst: das lokale Setzen
    // der Vormerkung kommt erst mit PR #41. Ueber den blossen Server-Aufruf
    // waere sie ohne Netz wirkungslos.

    fun bulkDelete() = runBulk { movie -> repository.deleteMovieByLocalId(movie.localId) }

    /**
     * Eine Aktion auf jeden ausgewaehlten Titel anwenden.
     *
     * Nacheinander statt nebenlaeufig: die Shelf kennt keinen Sammelaufruf, und
     * fuenfzig gleichzeitige Anfragen waeren fuer sie nur eine Last mehr. Ein
     * Fehler bei einem Titel haelt die uebrigen nicht auf — er steht danach
     * weiterhin als abweichende Zeile da und geht beim naechsten Abgleich raus.
     */
    private fun runBulk(
        auswahl: (List<Movie>) -> List<Movie> = { it },
        action: suspend (Movie) -> Unit
    ) {
        if (isBulkRunning || selection.isEmpty()) return
        val betroffen = auswahl(movies.filter { it.localId in selection })
        isBulkRunning = true

        viewModelScope.launch {
            for (movie in betroffen) {
                runCatching { action(movie) }
            }
            clearSelection()
            isBulkRunning = false
            loadMovies(refresh = true)
        }
    }

    // ── Zufallsauswahl ───────────────────────────────────────────────────────

    /** Der gezogene Titel, solange das Blatt offen ist. */
    var randomPick by mutableStateOf<Movie?>(null)
        private set
    var isRolling by mutableStateOf(false)
        private set
    var randomPickEmpty by mutableStateOf(false)
        private set

    /**
     * Einen Titel auslosen.
     *
     * Die Auswahl folgt der gerade sichtbaren Kategorie: wer "Serien" geöffnet
     * hat, will keinen Film vorgeschlagen bekommen. Der Suchtext bleibt
     * bewusst aussen vor — er ist eine Suche nach etwas Bestimmtem, und dabei
     * hilft eine Auslosung nicht.
     */
    fun rollRandom() {
        if (isRolling) return
        isRolling = true
        randomPickEmpty = false
        viewModelScope.launch {
            val type = when (selectedShelf) {
                ShelfCategory.FILME -> "Film"
                ShelfCategory.SERIEN -> "Serie"
                // "Neue Filme" ist keine Art, sondern ein Ausschnitt.
                else -> null
            }
            val picked = runCatching { repository.randomMovie(type) }.getOrNull()
            randomPick = picked
            randomPickEmpty = picked == null
            isRolling = false
        }
    }

    fun dismissRandom() {
        randomPick = null
        randomPickEmpty = false
    }

    fun onShelfSelected(category: ShelfCategory) {
        selectedShelf = category
        recompute()
    }

    /** Rasteransicht schließen, zurück zu den Shelf-Reihen. */
    fun clearShelf() {
        selectedShelf = null
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

    fun toggleWatched(localId: Long) {
        val movie = allLoadedMovies.find { it.localId == localId } ?: return
        val currentState = movie.isWatched ?: false

        // Optimistisches Update
        allLoadedMovies = allLoadedMovies.map {
            if (it.localId == localId) it.copy(isWatched = !currentState) else it
        }
        recompute()

        viewModelScope.launch {
            try {
                repository.toggleWatchedByLocalId(localId, currentState)
            } catch (_: Exception) {
                // Kein Zurückrollen mehr: die Änderung steht lokal und wartet
                // als abweichende Zeile auf den nächsten Abgleich. Sie hier
                // zurückzudrehen würde sie verwerfen, obwohl sie gültig ist.
            }
        }
    }

    private fun recompute() {
        availableGenres = genresOf(allLoadedMovies)

        // Ein Genre, das es nach einem Abgleich nicht mehr gibt, darf nicht als
        // Filter stehen bleiben — sonst waere die Liste dauerhaft leer.
        if (selectedGenre != null && selectedGenre !in availableGenres) selectedGenre = null

        // Alphabetisch wie das Raster hinter "Alle anzeigen" — sonst stuenden
        // dieselben Titel in der Reihe und im Raster in anderer Folge. Ohne
        // eigene Sortierung kaeme hier die Reihenfolge der Datenbank heraus.
        val byTitle = compareBy(String.CASE_INSENSITIVE_ORDER) { movie: Movie -> movie.title.orEmpty() }
        seriesShelf = allLoadedMovies.filter { it.collectionType == "Serie" }.sortedWith(byTitle)
        filmeShelf = allLoadedMovies.filter { it.collectionType != "Serie" }.sortedWith(byTitle)
        viewModelScope.launch {
            val result = withContext(Dispatchers.Default) { sortedForDisplay() }
            movies = result
        }
    }

    /**
     * Reine Berechnung: waehlt die Reihe aus [allLoadedMovies] und sortiert sie;
     * gibt die fertige Liste zurueck (kein State-Schreiben).
     *
     * Sortiert wird nach dem, was die Auswahl bedeutet: "Neue Filme" nach
     * Zugangsdatum, alles andere alphabetisch — wie in der Desktop-App, die
     * `title ASC` als Vorgabe hat. Nach Zugangsdatum zu sortieren sagt bei
     * "Filme" oder "Serien" nichts aus und macht einen Titel unauffindbar.
     */
    private fun sortedForDisplay(): List<Movie> {
        var filtered = allLoadedMovies
        var byDateAdded = false

        // Shelf-Kategorie ("Alle anzeigen" einer Reihe)
        when (selectedShelf) {
            ShelfCategory.FILME -> filtered = filtered.filter { it.collectionType != "Serie" }
            ShelfCategory.SERIEN -> filtered = filtered.filter { it.collectionType == "Serie" }
            ShelfCategory.NEW -> {
                val newIds = newMoviesShelf.mapTo(HashSet()) { it.id }
                filtered = filtered.filter { it.id in newIds }
                byDateAdded = true
            }
            null -> {}
        }

        // Regeln in MovieSorting.kt — dort sind sie ohne ViewModel pruefbar.
        filtered = filterByGenre(filtered, selectedGenre)

        // "Neue Filme" ist eine Auswahl nach Zugang; dort ist die Reihenfolge
        // Teil der Bedeutung und nicht frei waehlbar.
        if (byDateAdded) {
            return filtered.sortedWith(
                compareByDescending<Movie> { it.createdAt ?: "" }.thenByDescending { it.id }
            )
        }

        return sortMovies(filtered, sortKey, sortAscending)
    }

    fun onSortKeyChange(key: SortKey) {
        sortKey = key
        recompute()
    }

    fun toggleSortDirection() {
        sortAscending = !sortAscending
        recompute()
    }

    fun onGenreSelected(genre: String?) {
        selectedGenre = genre
        recompute()
    }



    private suspend fun performSearch(query: String) {
        isLoading = true
        error = null
        try {
            val result = repository.searchMovies(query)
            allLoadedMovies = result
            isOffline = repository.isOffline
            recompute()
            hasMore = false
        } catch (e: Exception) {
            error = friendlyError(e)
        } finally {
            isLoading = false
        }
    }


    private fun friendlyError(e: Exception): UiText {
        val msg = e.message ?: ""
        return when {
            msg.contains("Unable to resolve host", ignoreCase = true) ||
            msg.contains("failed to connect", ignoreCase = true) ->
                UiText.of(R.string.error_no_connection)
            msg.contains("timeout", ignoreCase = true) ->
                UiText.of(R.string.error_timeout)
            msg.contains("401") || msg.contains("Unauthorized", ignoreCase = true) ->
                UiText.of(R.string.error_session_expired)
            msg.contains("403") -> UiText.of(R.string.error_access_denied)
            msg.contains("404") -> UiText.of(R.string.error_content_not_found)
            msg.contains("500") || msg.contains("502") || msg.contains("503") ->
                UiText.of(R.string.error_server)
            else -> UiText.of(R.string.error_movie_not_loaded)
        }
    }


    class Factory(private val repository: MovieRepository) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return DashboardViewModel(repository) as T
        }
    }
}
