package info.movieshelf.ui.create

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import info.movieshelf.data.model.MovieUpdateRequest
import info.movieshelf.data.repository.MovieRepository
import kotlinx.coroutines.launch
import retrofit2.HttpException
import info.movieshelf.R
import info.movieshelf.ui.util.UiText

class CreateMovieViewModel(
    private val repository: MovieRepository
) : ViewModel() {

    var isSaving by mutableStateOf(false)
        private set
    /** Lokale ID des angelegten Films — damit navigiert der Screen weiter. */
    var createdMovieId by mutableStateOf<Long?>(null)
        private set
    var error by mutableStateOf<UiText?>(null)

    // Eingabefelder
    var title by mutableStateOf("")
    var year by mutableStateOf("")
    var collectionType by mutableStateOf("")
    var genre by mutableStateOf("")
    var director by mutableStateOf("")
    var runtime by mutableStateOf("")
    var rating by mutableStateOf("")
    var overview by mutableStateOf("")
    var tag by mutableStateOf("")
    var trailerUrl by mutableStateOf("")
    var edition by mutableStateOf("")
    var regionCode by mutableStateOf("")
    var discLocation by mutableStateOf("")
    var purchaseDate by mutableStateOf("")
    var purchasePrice by mutableStateOf("")
    var condition by mutableStateOf("")
    var inCollection by mutableStateOf(true)

    fun save() {
        val yearInt = year.trim().toIntOrNull()
        val ratingNum = rating.trim().replace(',', '.').toDoubleOrNull()

        when {
            title.isBlank() -> { error = UiText.of(R.string.error_title_empty); return 
    class Factory(
        private val repository: MovieRepository
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return CreateMovieViewModel(repository) as T
        }
    }
}
            yearInt == null -> { error = UiText.of(R.string.error_year_invalid); return }
            collectionType.isBlank() -> { error = UiText.of(R.string.error_type_missing); return }
            rating.isNotBlank() && (ratingNum == null || ratingNum < 0 || ratingNum > 100) -> {
                error = UiText.of(R.string.error_rating_range); return
            }
        }

        viewModelScope.launch {
            isSaving = true
            error = null
            try {
                val request = MovieUpdateRequest(
                    title          = title.trim(),
                    year           = yearInt!!,
                    collectionType = collectionType.trim(),
                    genre          = genre.trim().ifBlank { null },
                    director       = director.trim().ifBlank { null },
                    runtime        = runtime.trim().toIntOrNull(),
                    rating         = ratingNum,
                    overview       = overview.trim().ifBlank { null },
                    tag            = tag.trim().ifBlank { null },
                    trailerUrl     = trailerUrl.trim().ifBlank { null },
                    edition        = edition.trim().ifBlank { null },
                    regionCode     = regionCode.trim().ifBlank { null },
                    discLocation   = discLocation.trim().ifBlank { null },
                    purchaseDate   = purchaseDate.trim().ifBlank { null },
                    purchasePrice  = purchasePrice.trim().replace(',', '.').toDoubleOrNull(),
                    condition      = condition.trim().ifBlank { null },
                    inCollection   = inCollection
                )
                createdMovieId = repository.createMovie(request)
                if (createdMovieId == null) {
                    error = UiText.of(R.string.error_no_id_returned)
                }
            } catch (e: HttpException) {
                error = when (e.code()) {
                    403 -> UiText.of(R.string.error_no_permission_create)
                    422 -> UiText.of(R.string.error_invalid_input)
                    else -> UiText.of(R.string.error_create_failed_code, e.code())
                }
            } catch (e: Exception) {
                error = UiText.of(R.string.error_connection, e.message ?: "")
            } finally {
                isSaving = false
            }
        }
    }

    class Factory(
        private val repository: MovieRepository
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return CreateMovieViewModel(repository) as T
        }
    }
}
