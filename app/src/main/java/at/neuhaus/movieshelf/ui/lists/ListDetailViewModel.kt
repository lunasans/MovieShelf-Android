package at.neuhaus.movieshelf.ui.lists

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import at.neuhaus.movieshelf.data.api.RetrofitClient
import at.neuhaus.movieshelf.data.model.ListMutationRequest
import at.neuhaus.movieshelf.data.model.Movie
import kotlinx.coroutines.launch

class ListDetailViewModel(private val listId: Int) : ViewModel() {

    var name by mutableStateOf("")
        private set
    var movies by mutableStateOf<List<Movie>>(emptyList())
        private set
    var isLoading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            isLoading = true
            error = null
            try {
                val response = RetrofitClient.api.getList(listId)
                name = response.name ?: ""
                movies = response.movies ?: emptyList()
            } catch (e: Exception) {
                error = "Liste konnte nicht geladen werden."
            } finally {
                isLoading = false
            }
        }
    }

    fun removeMovie(movieId: Int) {
        viewModelScope.launch {
            error = null
            try {
                val newIds = movies.map { it.id } - movieId
                RetrofitClient.api.updateList(listId, ListMutationRequest(name, newIds))
                load()
            } catch (e: Exception) {
                error = "Film konnte nicht aus der Liste entfernt werden."
            }
        }
    }

    class Factory(private val listId: Int) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return ListDetailViewModel(listId) as T
        }
    }
}
