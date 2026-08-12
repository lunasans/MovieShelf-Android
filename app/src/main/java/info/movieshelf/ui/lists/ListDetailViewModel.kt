package info.movieshelf.ui.lists

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import info.movieshelf.data.model.ListItemRef
import info.movieshelf.data.model.Movie
import info.movieshelf.data.repository.ListRepository
import kotlinx.coroutines.launch
import info.movieshelf.R
import info.movieshelf.ui.util.UiText

class ListDetailViewModel(
    private val listId: Int,
    private val repository: ListRepository
) : ViewModel() {

    var name by mutableStateOf("")
        private set
    var movies by mutableStateOf<List<Movie>>(emptyList())
        private set
    var isLoading by mutableStateOf(false)
        private set
    var error by mutableStateOf<UiText?>(null)
        private set

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            isLoading = true
            error = null
            try {
                val response = repository.getList(listId)
                name = response.name ?: ""
                movies = response.items ?: emptyList()
            } catch (e: Exception) {
                error = UiText.of(R.string.error_list_load)
            } finally {
                isLoading = false
            }
        }
    }

    fun removeMovie(movieId: Int) {
        viewModelScope.launch {
            error = null
            try {
                val newItems = movies.filter { it.id != movieId }
                    .map { ListItemRef(it.itemType ?: "movie", it.id) }
                repository.setItems(listId, name, newItems)
                load()
            } catch (e: Exception) {
                error = UiText.of(R.string.error_list_remove_movie)
            }
        }
    }

    class Factory(
        private val listId: Int,
        private val repository: ListRepository
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return ListDetailViewModel(listId, repository) as T
        }
    }
}
