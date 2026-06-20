package at.neuhaus.movieshelf.ui.lists

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.neuhaus.movieshelf.data.api.RetrofitClient
import at.neuhaus.movieshelf.data.model.MovieListSummary
import kotlinx.coroutines.launch

class ListsViewModel : ViewModel() {

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
                lists = RetrofitClient.api.getLists().lists ?: emptyList()
            } catch (e: Exception) {
                error = "Listen konnten nicht geladen werden."
            } finally {
                isLoading = false
            }
        }
    }
}
