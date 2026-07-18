package at.neuhaus.movieshelf.ui.stats

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.neuhaus.movieshelf.data.SessionManager
import at.neuhaus.movieshelf.data.api.RetrofitClient
import at.neuhaus.movieshelf.data.model.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class StatsViewModel : ViewModel() {
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
                if (SessionManager.isDemo) {
                    delay(500)
                    stats = getDemoStats()
                } else {
                    stats = RetrofitClient.api.getStats()
                }
            } catch (e: Exception) {
                error = "Fehler beim Laden der Statistik: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    private fun getDemoStats(): Stats {
        return Stats(
            totalFilms = 2,
            totalRuntimeMinutes = 300,
            totalRuntimeHours = 5.0,
            totalRuntimeDays = 0.2,
            avgRuntime = 150.0,
            watched = WatchedStats(count = 2, percentage = 100.0),
            years = YearStats(avgYear = 2009.0, oldestYear = 2008, newestYear = 2010),
            collections = listOf(
                CollectionStats(collectionType = "Film", count = 2, percentage = 100.0)
            ),
            ratings = listOf(
                RatingStats(ratingAge = 12, count = 1),
                RatingStats(ratingAge = 16, count = 1)
            ),
            genres = listOf(
                GenreStats(genre = "Sci-Fi", count = 1),
                GenreStats(genre = "Action", count = 1)
            ),
            yearDistribution = mapOf("2008" to 1, "2010" to 1),
            decades = listOf(
                DecadeStats(decade = 2000, count = 1, avgRuntime = 152.0),
                DecadeStats(decade = 2010, count = 1, avgRuntime = 148.0)
            )
        )
    }
}
