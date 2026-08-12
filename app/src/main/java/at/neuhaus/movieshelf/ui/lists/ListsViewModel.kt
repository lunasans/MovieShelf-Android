package at.neuhaus.movieshelf.ui.lists

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import at.neuhaus.movieshelf.data.repository.ListRepository
import at.neuhaus.movieshelf.data.model.MovieListSummary
import kotlinx.coroutines.launch

class ListsViewModel(
    private val repository: ListRepository
) : ViewModel() {

    var lists by mutableStateOf<List<MovieListSummary>>(emptyList())
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
                lists = repository.getLists()
            } catch (e: Exception) {
                error = "Listen konnten nicht geladen werden."
            } finally {
                isLoading = false
            }
        }
    }

    fun createList(name: String) {
        viewModelScope.launch {
            error = null
            try {
                repository.createList(name)
                load()
            } catch (e: Exception) {
                error = "Liste konnte nicht angelegt werden."
            }
        }
    }

    fun renameList(summary: MovieListSummary, newName: String) {
        viewModelScope.launch {
            error = null
            try {
                repository.renameList(summary.id, newName, summary.items ?: emptyList())
                load()
            } catch (e: Exception) {
                error = "Liste konnte nicht umbenannt werden."
            }
        }
    }

    fun deleteList(id: Int) {
        viewModelScope.launch {
            error = null
            try {
                repository.deleteList(id)
                load()
            } catch (e: Exception) {
                error = "Liste konnte nicht gelöscht werden."
            }
        }
    }

    class Factory(
        private val repository: ListRepository
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return ListsViewModel(repository) as T
        }
    }
}
