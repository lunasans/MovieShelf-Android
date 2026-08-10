package at.neuhaus.movieshelf.ui.actors

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import at.neuhaus.movieshelf.data.api.RetrofitClient
import at.neuhaus.movieshelf.data.model.Actor
import at.neuhaus.movieshelf.data.model.Movie
import kotlinx.coroutines.launch

class ActorDetailViewModel(private val actorId: Int) : ViewModel() {
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
                    val response = RetrofitClient.api.getActor(actorId)
                    actor = response.data
            } catch (e: Exception) {
                error = "Fehler beim Laden des Schauspielers: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }


    class Factory(private val actorId: Int) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return ActorDetailViewModel(actorId) as T
        }
    }
}
