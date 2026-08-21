package info.movieshelf.ui.actors

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import info.movieshelf.data.model.Actor
import info.movieshelf.data.repository.ActorRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import info.movieshelf.R
import info.movieshelf.ui.util.UiText

class ActorListViewModel(
    private val repository: ActorRepository
) : ViewModel() {
    var actors by mutableStateOf<List<Actor>>(emptyList())
    var isLoading by mutableStateOf(false)
    var isRefreshing by mutableStateOf(false)
    var error by mutableStateOf<UiText?>(null)

    var searchQuery by mutableStateOf("")
    private var searchJob: Job? = null

    init {
        loadActors()
    }

    /**
     * Erst die eigene Sammlung, dann der Server.
     *
     * Die lokale Liste ist die eigentliche Antwort auf „wer spielt in meinen
     * Filmen" — der Server kennt daneben noch Personen aus fremden Sammlungen.
     * Ist lokal nichts da (frische Installation vor dem ersten Abgleich),
     * bleibt der Serverbestand die Rueckfallebene.
     */
    fun loadActors(refresh: Boolean = false) {
        viewModelScope.launch {
            if (refresh) isRefreshing = true else isLoading = true
            error = null
            try {
                val local = repository.getLocalActors()
                if (local.isNotEmpty()) {
                    actors = local
                } else {
                    actors = repository.getRemoteActors(page = 1, perPage = 100)
                }
            } catch (e: Exception) {
                if (actors.isEmpty()) {
                    error = UiText.of(R.string.error_actors_load, e.message ?: "")
                }
            } finally {
                isLoading = false
                isRefreshing = false
            }
        }
    }

    fun onSearchQueryChange(newQuery: String) {
        searchQuery = newQuery
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(500)
            if (newQuery.isBlank()) {
                loadActors()
            } else {
                performSearch(newQuery)
            }
        }
    }

    private suspend fun performSearch(query: String) {
        isLoading = true
        error = null
        try {
            val local = repository.searchLocalActors(query)
            actors = if (local.isNotEmpty()) local else repository.searchRemoteActors(query)
        } catch (e: Exception) {
            error = UiText.of(R.string.error_search_failed, e.message ?: "")
        } finally {
            isLoading = false
        }
    }

    class Factory(
        private val repository: ActorRepository
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return ActorListViewModel(repository) as T
        }
    }
}
