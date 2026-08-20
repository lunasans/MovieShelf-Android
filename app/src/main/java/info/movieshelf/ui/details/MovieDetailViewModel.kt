package info.movieshelf.ui.details

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import info.movieshelf.data.model.Actor
import info.movieshelf.data.model.ListItemRef
import info.movieshelf.data.model.Movie
import info.movieshelf.data.model.MovieListSummary
import info.movieshelf.data.model.TmdbSeasonOption
import info.movieshelf.data.repository.ListRepository
import info.movieshelf.data.repository.MovieRepository
import kotlinx.coroutines.launch
import info.movieshelf.R
import info.movieshelf.ui.util.UiText

/**
 * @param initialLocalId ID der lokalen Zeile — der Regelfall.
 * @param initialRemoteId Server-ID als Ausweichweg für Filme, die noch keine
 *   lokale Zeile haben (Listen-Inhalte, Darsteller-Filmografie, Boxset-Kinder).
 *   Wird nur ausgewertet, wenn [initialLocalId] 0 ist.
 */
class MovieDetailViewModel(
    private var initialLocalId: Long,
    private val initialRemoteId: Int = 0,
    private val repository: MovieRepository,
    private val listRepository: ListRepository
) : ViewModel() {
    var movie by mutableStateOf<Movie?>(null)

    /** Lokale ID des angezeigten Films — auch nach dem Nachschlagen gültig. */
    var localId by mutableStateOf(initialLocalId)
        private set
    var isLoading by mutableStateOf(false)
    var error by mutableStateOf<UiText?>(null)
    var isFetchingTrailer by mutableStateOf(false)
        private set
    var availableLists by mutableStateOf<List<MovieListSummary>>(emptyList())
        private set
    var listActionMessage by mutableStateOf<UiText?>(null)

    init {
        reload()
    }

    fun reload() {
        viewModelScope.launch {
            isLoading = true
            error = null
            try {
                movie = load()
                if (movie == null) error = UiText.of(R.string.error_movie_not_found)
                // Kam der Film über die Server-ID herein, ist ab jetzt seine
                // lokale ID bekannt — sonst liefe "Bearbeiten" gegen 0.
                movie?.localId?.takeIf { it != 0L }?.let { localId = it }
            } catch (e: Exception) {
                error = UiText.of(R.string.error_movie_not_loaded)
            } finally {
                isLoading = false
            }
        }
    }

    private suspend fun load(): Movie? {
        val repo = repository
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


    fun toggleWatched() {
        val currentMovie = movie ?: return
        val currentState = currentMovie.isWatched ?: false

        // Optimistisches UI-Update
        movie = currentMovie.copy(isWatched = !currentState)

        viewModelScope.launch {
            try {
                repository.toggleWatchedByLocalId(localId, currentState)
            } catch (e: Exception) {
                // Kein Rollback: die Änderung steht lokal und geht beim
                // nächsten Abgleich raus.
                error = UiText.of(R.string.error_update_failed, e.message ?: "")
            }
        }
    }

    fun toggleWishlist() {
        val currentMovie = movie ?: return
        val newState = !(currentMovie.isWishlisted ?: false)

        // Optimistisches UI-Update
        movie = currentMovie.copy(isWishlisted = newState)

        viewModelScope.launch {
            try {
                val wishlisted = repository.toggleWishlist(currentMovie.id)
                movie = movie?.copy(isWishlisted = wishlisted ?: newState)
            } catch (e: Exception) {
                movie = currentMovie // Rollback bei Fehler
                error = UiText.of(R.string.error_wishlist_failed, e.message ?: "")
            }
        }
    }

    /**
     * Eigene Bewertung setzen — 1 bis 5 Sterne, erneutes Tippen auf denselben
     * Stern entfernt sie wieder.
     *
     * Optimistisch wie der Wunschlisten-Schalter: die Sterne fuellen sich
     * sofort. Das Repository schreibt lokal und versucht den Versand; scheitert
     * er, bleibt die Bewertung als offen stehen und geht beim naechsten
     * Abgleich raus — ein Rollback waere hier also falsch.
     */
    fun setUserRating(stars: Int) {
        val current = movie ?: return
        val next = if (current.userRating == stars) null else stars
        movie = current.copy(userRating = next)

        viewModelScope.launch {
            repository.setUserRating(current.localId, next)
        }
    }

    /**
     * Gesehen-Stand einer Folge umschalten.
     *
     * Optimistisch wie die uebrigen Markierungen: das Haekchen steht sofort.
     * Danach wird der Film neu geladen, damit die Staffelliste den Stand aus
     * der Datenbank zeigt — der Server kann korrigiert haben.
     */
    fun toggleEpisodeWatched(episodeLocalId: Long, watched: Boolean) {
        if (episodeLocalId == 0L) return
        movie = movie?.let { current ->
            current.copy(
                seasons = current.seasons?.map { season ->
                    season.copy(
                        episodes = season.episodes?.map { episode ->
                            if (episode.localId == episodeLocalId) {
                                episode.copy(isWatched = watched)
                            } else {
                                episode
                            }
                        }
                    )
                }
            )
        }

        viewModelScope.launch {
            repository.toggleEpisodeWatched(episodeLocalId, watched)
            movie = load()
        }
    }

    /** Trailer von TMDb holen & speichern (Admin). */
    fun fetchTrailer() {
        val current = movie ?: return
        viewModelScope.launch {
            isFetchingTrailer = true
            error = null
            try {
                val trailerUrl = repository.fetchTrailer(current.id)
                if (!trailerUrl.isNullOrBlank()) {
                    movie = current.copy(trailerUrl = trailerUrl)
                    listActionMessage = UiText.of(R.string.message_trailer_found)
                } else {
                    error = UiText.of(R.string.error_no_trailer)
                }
            } catch (e: Exception) {
                error = UiText.of(R.string.error_trailer_failed, e.message ?: "")
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
        get() = movie?.collectionType == "Serie" && movie?.tmdbId?.toIntOrNull() != null

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
                val details = repository.getTmdbTvDetails(tmdbId)
                seasonOptions = (details.seasons ?: emptyList()).filter { it.seasonNumber > 0 }
            } catch (e: Exception) {
                error = UiText.of(R.string.error_seasons_load)
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
                    repository.importSeasons(current.id, toAdd)
                }
                if (toRemove.isNotEmpty()) {
                    repository.removeSeasons(current.id, toRemove)
                }
                // Drei Faelle statt zusammengesetzter Zeichenkette: eine
                // Aufzaehlung aus Bruchstuecken laesst sich nicht uebersetzen,
                // weil Wortstellung und Kommaregeln je Sprache anders sind.
                listActionMessage = when {
                    toAdd.isNotEmpty() && toRemove.isNotEmpty() ->
                        UiText.of(R.string.message_seasons_both, toAdd.size, toRemove.size)
                    toAdd.isNotEmpty() -> UiText.of(R.string.message_seasons_added, toAdd.size)
                    else -> UiText.of(R.string.message_seasons_removed, toRemove.size)
                }
                showSeasonDialog = false
                selectedSeasons = emptySet()
                reload()
            } catch (e: Exception) {
                error = UiText.of(R.string.error_season_change_failed, e.message ?: "")
            } finally {
                seasonImporting = false
            }
        }
    }

    /** Eigene Listen des Nutzers laden (für „Zu Liste hinzufügen"). */
    fun loadLists() {
        viewModelScope.launch {
            try {
                availableLists = listRepository.getLists()
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
                listRepository.addMovieToList(list, current)
                listActionMessage = UiText.of(R.string.message_added_to_list, list.name ?: "")
            } catch (e: Exception) {
                error = UiText.of(R.string.error_list_add_failed)
            }
        }
    }

    class Factory(
        private val localId: Long,
        private val remoteId: Int = 0,
        private val repository: MovieRepository,
        private val listRepository: ListRepository
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return MovieDetailViewModel(localId, remoteId, repository, listRepository) as T
        }
    }
}
