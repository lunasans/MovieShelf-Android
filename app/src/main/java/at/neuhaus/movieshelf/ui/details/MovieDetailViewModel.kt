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
import at.neuhaus.movieshelf.data.model.ListItemRef
import at.neuhaus.movieshelf.data.model.ListMutationRequest
import at.neuhaus.movieshelf.data.model.Movie
import at.neuhaus.movieshelf.data.model.MovieListSummary
import at.neuhaus.movieshelf.data.model.SeasonImportRequest
import at.neuhaus.movieshelf.data.model.TmdbSeasonOption
import at.neuhaus.movieshelf.data.repository.MovieRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * @param initialLocalId ID der lokalen Zeile — der Regelfall.
 * @param initialRemoteId Server-ID als Ausweichweg für Filme, die noch keine
 *   lokale Zeile haben (Listen-Inhalte, Darsteller-Filmografie, Boxset-Kinder).
 *   Wird nur ausgewertet, wenn [initialLocalId] 0 ist.
 */
class MovieDetailViewModel(
    private var initialLocalId: Long,
    private val initialRemoteId: Int = 0,
    private val repository: MovieRepository? = null
) : ViewModel() {
    var movie by mutableStateOf<Movie?>(null)

    /** Lokale ID des angezeigten Films — auch nach dem Nachschlagen gültig. */
    var localId by mutableStateOf(initialLocalId)
        private set
    var isLoading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var isFetchingTrailer by mutableStateOf(false)
        private set
    var availableLists by mutableStateOf<List<MovieListSummary>>(emptyList())
        private set
    var listActionMessage by mutableStateOf<String?>(null)

    init {
        reload()
    }

    fun reload() {
        viewModelScope.launch {
            isLoading = true
            error = null
            try {
                if (SessionManager.isDemo) {
                    delay(300)
                    // Demo-Filme haben keine lokalen IDs — ohne die Fallunter-
                    // scheidung würde `localId == 0` auf jeden von ihnen passen
                    // und immer der erste Film erscheinen.
                    movie = getDemoMovies().find {
                        if (localId != 0L) it.localId == localId else it.id == initialRemoteId
                    }
                    if (movie == null) error = "Film nicht gefunden"
                } else {
                    movie = load()
                    if (movie == null) error = "Film nicht gefunden"
                    // Kam der Film über die Server-ID herein, ist ab jetzt seine
                    // lokale ID bekannt — sonst liefe "Bearbeiten" gegen 0.
                    movie?.localId?.takeIf { it != 0L }?.let { localId = it }
                }
            } catch (e: Exception) {
                error = "Film konnte nicht geladen werden."
            } finally {
                isLoading = false
            }
        }
    }

    private suspend fun load(): Movie? {
        val repo = repository
            ?: return RetrofitClient.api.getMovie(if (localId != 0L) 0 else initialRemoteId).data

        if (localId != 0L) return repo.getMovieByLocalId(localId)

        // Ohne lokale ID: erst nachschlagen, ob der Film lokal doch bekannt ist,
        // sonst direkt vom Server holen.
        if (initialRemoteId != 0) {
            repo.getLocalId(initialRemoteId)?.let { resolved ->
                localId = resolved
                return repo.getMovieByLocalId(resolved)
            }
            return repo.getMovie(initialRemoteId)
        }
        return null
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

    fun toggleWishlist() {
        if (SessionManager.isDemo) {
            movie = movie?.copy(isWishlisted = !(movie?.isWishlisted ?: false))
            return
        }
        val currentMovie = movie ?: return
        val newState = !(currentMovie.isWishlisted ?: false)

        // Optimistisches UI-Update
        movie = currentMovie.copy(isWishlisted = newState)

        viewModelScope.launch {
            try {
                val response = RetrofitClient.api.toggleWishlist(currentMovie.id)
                movie = movie?.copy(isWishlisted = response.wishlisted ?: newState)
            } catch (e: Exception) {
                movie = currentMovie // Rollback bei Fehler
                error = "Fehler bei der Wunschliste: ${e.message}"
            }
        }
    }

    /** Trailer von TMDb holen & speichern (Admin). */
    fun fetchTrailer() {
        val current = movie ?: return
        viewModelScope.launch {
            isFetchingTrailer = true
            error = null
            try {
                val response = RetrofitClient.api.fetchTrailer(current.id)
                if (response.found == true && !response.trailerUrl.isNullOrBlank()) {
                    movie = current.copy(trailerUrl = response.trailerUrl)
                    listActionMessage = "Trailer gefunden und gespeichert."
                } else {
                    error = "Kein Trailer gefunden."
                }
            } catch (e: Exception) {
                error = "Trailer konnte nicht geholt werden: ${e.message}"
            } finally {
                isFetchingTrailer = false
            }
        }
    }

    // --- Staffeln verwalten (wie in der Shelf): fehlende anhaken zum Nachladen,
    // vorhandene abwählen zum Entfernen ---
    var showSeasonDialog by mutableStateOf(false)
    var seasonOptions by mutableStateOf<List<TmdbSeasonOption>>(emptyList())
        private set
    var seasonDialogLoading by mutableStateOf(false)
        private set
    var seasonImporting by mutableStateOf(false)
        private set
    var selectedSeasons by mutableStateOf<Set<Int>>(emptySet())
        private set

    val existingSeasonNumbers: List<Int>
        get() = movie?.seasons?.map { it.seasonNumber } ?: emptyList()

    val canBackfillSeasons: Boolean
        get() = movie?.collectionType == "Serie" && movie?.tmdbId?.toIntOrNull() != null && !SessionManager.isDemo

    val seasonsToAdd: List<Int>
        get() = selectedSeasons.filter { it !in existingSeasonNumbers }.sorted()

    val seasonsToRemove: List<Int>
        get() = existingSeasonNumbers.filter { it !in selectedSeasons }.sorted()

    val hasSeasonChanges: Boolean
        get() = seasonsToAdd.isNotEmpty() || seasonsToRemove.isNotEmpty()

    fun openSeasonDialog() {
        val tmdbId = movie?.tmdbId?.toIntOrNull() ?: return
        showSeasonDialog = true
        // Vorhandene Staffeln vorbelegen: Abwählen = Entfernen, Anhaken = Nachladen
        selectedSeasons = existingSeasonNumbers.toSet()
        if (seasonOptions.isNotEmpty()) return
        viewModelScope.launch {
            seasonDialogLoading = true
            try {
                val details = RetrofitClient.api.getTmdbTvDetails(tmdbId)
                seasonOptions = (details.seasons ?: emptyList()).filter { it.seasonNumber > 0 }
            } catch (e: Exception) {
                error = "Staffeln konnten nicht geladen werden."
                showSeasonDialog = false
            } finally {
                seasonDialogLoading = false
            }
        }
    }

    fun toggleSeasonSelection(seasonNumber: Int) {
        selectedSeasons = if (selectedSeasons.contains(seasonNumber)) {
            selectedSeasons - seasonNumber
        } else {
            selectedSeasons + seasonNumber
        }
    }

    fun applySeasonChanges() {
        val current = movie ?: return
        val toAdd = seasonsToAdd
        val toRemove = seasonsToRemove
        if ((toAdd.isEmpty() && toRemove.isEmpty()) || seasonImporting) return
        viewModelScope.launch {
            seasonImporting = true
            try {
                if (toAdd.isNotEmpty()) {
                    RetrofitClient.api.importSeasons(SeasonImportRequest(current.id, toAdd))
                }
                if (toRemove.isNotEmpty()) {
                    RetrofitClient.api.removeSeasons(SeasonImportRequest(current.id, toRemove))
                }
                val parts = mutableListOf<String>()
                if (toAdd.isNotEmpty()) parts.add("${toAdd.size} nachgeladen")
                if (toRemove.isNotEmpty()) parts.add("${toRemove.size} entfernt")
                listActionMessage = "Staffeln: ${parts.joinToString(", ")}."
                showSeasonDialog = false
                selectedSeasons = emptySet()
                reload()
            } catch (e: Exception) {
                error = "Staffel-Änderung fehlgeschlagen: ${e.message}"
            } finally {
                seasonImporting = false
            }
        }
    }

    /** Eigene Listen des Nutzers laden (für „Zu Liste hinzufügen"). */
    fun loadLists() {
        viewModelScope.launch {
            try {
                availableLists = RetrofitClient.api.getLists().lists ?: emptyList()
            } catch (e: Exception) {
                // still ignorieren – Sheet zeigt dann leere Liste
            }
        }
    }

    /** Aktuellen Film zu einer Liste hinzufügen (ersetzt die ID-Menge inkl. neuem Film). */
    fun addToList(list: MovieListSummary) {
        val current = movie ?: return
        viewModelScope.launch {
            try {
                val items = ((list.items ?: emptyList()) + ListItemRef("movie", current.id))
                    .distinctBy { it.type to it.id }
                RetrofitClient.api.updateList(list.id, ListMutationRequest(list.name ?: "Liste", items))
                listActionMessage = "Zu \"${list.name ?: "Liste"}\" hinzugefügt."
            } catch (e: Exception) {
                error = "Konnte nicht zur Liste hinzufügen."
            }
        }
    }

    class Factory(
        private val localId: Long,
        private val remoteId: Int = 0,
        private val repository: MovieRepository? = null
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return MovieDetailViewModel(localId, remoteId, repository) as T
        }
    }
}
