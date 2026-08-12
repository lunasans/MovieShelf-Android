package info.movieshelf.ui.actors

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import info.movieshelf.data.model.Actor
import info.movieshelf.data.model.Movie
import info.movieshelf.data.repository.ActorRepository
import kotlinx.coroutines.launch

class ActorDetailViewModel(
    private val actorId: Int,
    private val repository: ActorRepository
) : ViewModel() {
    var actor by mutableStateOf<Actor?>(null)
    var isLoading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)

    init {
        loadActor()
    }

    fun loadActor() {
        viewModelScope.launch {
            isLoading = true
            error = null
            try {
                actor = repository.getActor(actorId)
            } catch (e: Exception) {
                error = "Fehler beim Laden des Schauspielers: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }


    class Factory(
        private val actorId: Int,
        private val repository: ActorRepository
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return ActorDetailViewModel(actorId, repository) as T
        }
    }
}
