package info.movieshelf.ui.stats

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import info.movieshelf.data.repository.MovieRepository
import info.movieshelf.data.model.*
import kotlinx.coroutines.launch

class StatsViewModel(
    private val repository: MovieRepository
) : ViewModel() {
    var stats by mutableStateOf<Stats?>(null)
    var isLoading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)

    init {
        loadStats()
    }

    fun loadStats() {
        viewModelScope.launch {
            isLoading = true
            error = null
            try {
                stats = repository.getStats()
            } catch (e: Exception) {
                error = "Fehler beim Laden der Statistik: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    class Factory(
        private val repository: MovieRepository
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return StatsViewModel(repository) as T
        }
    }
}
