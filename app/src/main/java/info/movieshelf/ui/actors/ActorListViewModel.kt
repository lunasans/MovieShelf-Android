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

class ActorListViewModel(
    private val repository: ActorRepository
) : ViewModel() {
    var actors by mutableStateOf<List<Actor>>(emptyList())
    var isLoading by mutableStateOf(false)
    var isRefreshing by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)

    var searchQuery by mutableStateOf("")
    private var searchJob: Job? = null

    init {
        loadActors()
    }

    fun loadActors(refresh: Boolean = false) {
        viewModelScope.launch {
            if (refresh) isRefreshing = true else isLoading = true
            error = null
            try {
                actors = repository.getActors(page = 1, perPage = 100)
            } catch (e: Exception) {
                error = "Fehler beim Laden der Schauspieler: ${e.message}"
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
            actors = repository.searchActors(query)
        } catch (e: Exception) {
            error = "Suche fehlgeschlagen: ${e.message}"
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
