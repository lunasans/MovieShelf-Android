package info.movieshelf.ui.edit

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import info.movieshelf.data.model.MovieUpdateRequest
import info.movieshelf.data.local.db.UploadKind
import info.movieshelf.data.repository.MovieRepository
import kotlinx.coroutines.launch
import retrofit2.HttpException
import info.movieshelf.R
import info.movieshelf.ui.util.UiText

class EditMovieViewModel(
    private val movieLocalId: Long,
    private val repository: MovieRepository
) : ViewModel() {

    var isLoading by mutableStateOf(true)
        private set
    var loadError by mutableStateOf(false)
        private set
    var isSaving by mutableStateOf(false)
        private set
    var saved by mutableStateOf(false)
        private set
    var isDeleting by mutableStateOf(false)
        private set
    var deleted by mutableStateOf(false)
        private set
    var isUploadingCover by mutableStateOf(false)
        private set
    var isUploadingBackdrop by mutableStateOf(false)
        private set
    var uploadMessage by mutableStateOf<UiText?>(null)
    var error by mutableStateOf<UiText?>(null)

    // Bearbeitbare Felder
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

    // Momentaufnahme der Anfangswerte (nach dem Laden)
    private var initialTitle = ""
    private var initialYear = ""
    private var initialCollectionType = ""
    private var initialGenre = ""
    private var initialDirector = ""
    private var initialRuntime = ""
    private var initialRating = ""
    private var initialOverview = ""
    private var initialTag = ""
    private var initialTrailerUrl = ""
    private var initialEdition = ""
    private var initialRegionCode = ""
    private var initialDiscLocation = ""
    private var initialPurchaseDate = ""
    private var initialPurchasePrice = ""
    private var initialCondition = ""
    private var initialInCollection = true

    val hasUnsavedChanges: Boolean
        get() = !isLoading && (
            title != initialTitle ||
            year != initialYear ||
            collectionType != initialCollectionType ||
            genre != initialGenre ||
            director != initialDirector ||
            runtime != initialRuntime ||
            rating != initialRating ||
            overview != initialOverview ||
            tag != initialTag ||
            trailerUrl != initialTrailerUrl ||
            edition != initialEdition ||
            regionCode != initialRegionCode ||
            discLocation != initialDiscLocation ||
            purchaseDate != initialPurchaseDate ||
            purchasePrice != initialPurchasePrice ||
            condition != initialCondition ||
            inCollection != initialInCollection
        )

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            isLoading = true
            val movie = try {
                repository.getMovieByLocalId(movieLocalId)
            } catch (e: Exception) {
                null
            }
            if (movie == null) {
                loadError = true
            } else {
                title          = movie.title ?: ""
                year           = movie.year?.toString() ?: ""
                collectionType = movie.collectionType ?: ""
                genre          = movie.genre ?: ""
                director       = movie.director ?: ""
                runtime        = movie.runtime?.toString() ?: ""
                rating         = movie.rating ?: ""
                overview       = movie.overview ?: ""
                tag            = movie.tag ?: ""
                trailerUrl     = movie.trailerUrl ?: ""
                edition        = movie.edition ?: ""
                regionCode     = movie.regionCode ?: ""
                discLocation   = movie.discLocation ?: ""
                purchaseDate   = movie.purchaseDate ?: ""
                purchasePrice  = movie.purchasePrice?.toString() ?: ""
                condition      = movie.condition ?: ""
                inCollection   = movie.inCollection ?: true

                initialTitle          = title
                initialYear           = year
                initialCollectionType = collectionType
                initialGenre          = genre
                initialDirector       = director
                initialRuntime        = runtime
                initialRating         = rating
                initialOverview       = overview
                initialTag            = tag
                initialTrailerUrl     = trailerUrl
                initialEdition        = edition
                initialRegionCode     = regionCode
                initialDiscLocation   = discLocation
                initialPurchaseDate   = purchaseDate
                initialPurchasePrice  = purchasePrice
                initialCondition      = condition
                initialInCollection   = inCollection
            }
            isLoading = false
        }
    }

    fun save() {
        val yearInt = year.trim().toIntOrNull()
        val ratingNum = rating.trim().replace(',', '.').toDoubleOrNull()

        when {
            title.isBlank() -> { error = UiText.of(R.string.error_title_empty); return }
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
                repository.updateMovieByLocalId(movieLocalId, request)
                saved = true
            } catch (e: HttpException) {
                error = when (e.code()) {
                    403 -> UiText.of(R.string.error_no_permission_edit)
                    422 -> UiText.of(R.string.error_invalid_input)
                    else -> UiText.of(R.string.error_save_failed_code, e.code())
                }
            } catch (e: Exception) {
                error = UiText.of(R.string.error_connection, e.message ?: "")
            } finally {
                isSaving = false
            }
        }
    }

    fun deleteMovie() {
        viewModelScope.launch {
            isDeleting = true
            error = null
            try {
                repository.deleteMovieByLocalId(movieLocalId)
                deleted = true
            } catch (e: HttpException) {
                error = when (e.code()) {
                    403 -> UiText.of(R.string.error_no_permission_delete)
                    404 -> UiText.of(R.string.error_movie_not_found_dot)
                    else -> UiText.of(R.string.error_delete_failed, e.code())
                }
            } catch (e: Exception) {
                error = UiText.of(R.string.error_connection, e.message ?: "")
            } finally {
                isDeleting = false
            }
        }
    }

    fun uploadCover(bytes: ByteArray, mime: String) = uploadImage(bytes, mime, isCover = true)
    fun uploadBackdrop(bytes: ByteArray, mime: String) = uploadImage(bytes, mime, isCover = false)

    private fun uploadImage(bytes: ByteArray, mime: String, isCover: Boolean) {
        viewModelScope.launch {
            if (isCover) isUploadingCover = true else isUploadingBackdrop = true
            error = null
            try {
                val kind = if (isCover) UploadKind.COVER else UploadKind.BACKDROP
                repository.setMovieImage(movieLocalId, bytes, mime, kind)
                uploadMessage = if (repository.isOffline) {
                    UiText.of(R.string.message_image_saved)
                } else if (isCover) UiText.of(R.string.message_cover_updated) else UiText.of(R.string.message_backdrop_updated)
            } catch (e: HttpException) {
                error = when (e.code()) {
                    403 -> UiText.of(R.string.error_no_permission)
                    422 -> UiText.of(R.string.error_image_invalid)
                    else -> UiText.of(R.string.error_upload_failed_code, e.code())
                }
            } catch (e: Exception) {
                error = UiText.of(R.string.error_connection, e.message ?: "")
            } finally {
                if (isCover) isUploadingCover = false else isUploadingBackdrop = false
            }
        }
    }

    class Factory(
        private val movieLocalId: Long,
        private val repository: MovieRepository
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return EditMovieViewModel(movieLocalId, repository) as T
        }
    }
}
