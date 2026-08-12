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
import info.movieshelf.R
import info.movieshelf.ui.util.UiText

class StatsViewModel(
    private val repository: MovieRepository
) : ViewModel() {
    var stats by mutableStateOf<Stats?>(null)
    var isLoading by mutableStateOf(false)
    var error by mutableStateOf<UiText?>(null)

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
                error = UiText.of(R.string.error_stats_load, e.message ?: "")
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
