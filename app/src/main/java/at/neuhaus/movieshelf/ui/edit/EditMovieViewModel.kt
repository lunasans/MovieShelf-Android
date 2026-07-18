package at.neuhaus.movieshelf.ui.edit

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import at.neuhaus.movieshelf.data.model.MovieUpdateRequest
import at.neuhaus.movieshelf.data.repository.MovieRepository
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException

class EditMovieViewModel(
    private val movieId: Int,
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
    var uploadMessage by mutableStateOf<String?>(null)
    var error by mutableStateOf<String?>(null)

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
            inCollection != initialInCollection
        )

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            isLoading = true
            val movie = try {
                repository.getMovie(movieId)
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
                initialInCollection   = inCollection
            }
            isLoading = false
        }
    }

    fun save() {
        val yearInt = year.trim().toIntOrNull()
        val ratingNum = rating.trim().replace(',', '.').toDoubleOrNull()

        when {
            title.isBlank() -> { error = "Titel darf nicht leer sein."; return }
            yearInt == null -> { error = "Bitte ein gültiges Jahr eingeben."; return }
            collectionType.isBlank() -> { error = "Bitte einen Typ wählen."; return }
            rating.isNotBlank() && (ratingNum == null || ratingNum < 0 || ratingNum > 100) -> {
                error = "Bewertung muss zwischen 0 und 100 liegen."; return
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
                    inCollection   = inCollection
                )
                repository.updateMovie(movieId, request)
                saved = true
            } catch (e: HttpException) {
                error = when (e.code()) {
                    403 -> "Keine Berechtigung zum Bearbeiten."
                    422 -> "Eingaben ungültig. Bitte prüfe die Felder."
                    else -> "Speichern fehlgeschlagen (Fehler ${e.code()})."
                }
            } catch (e: Exception) {
                error = "Verbindungsfehler: ${e.message}"
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
                repository.deleteMovie(movieId)
                deleted = true
            } catch (e: HttpException) {
                error = when (e.code()) {
                    403 -> "Keine Berechtigung zum Löschen."
                    404 -> "Film nicht gefunden."
                    else -> "Löschen fehlgeschlagen (Fehler ${e.code()})."
                }
            } catch (e: Exception) {
                error = "Verbindungsfehler: ${e.message}"
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
                val mediaType = mime.toMediaTypeOrNull()
                val body = bytes.toRequestBody(mediaType)
                val ext = if (mime.contains("png")) "png" else "jpg"
                val field = if (isCover) "cover" else "backdrop"
                val part = MultipartBody.Part.createFormData(field, "$field.$ext", body)
                if (isCover) repository.uploadCover(movieId, part) else repository.uploadBackdrop(movieId, part)
                uploadMessage = if (isCover) "Cover aktualisiert." else "Backdrop aktualisiert."
            } catch (e: HttpException) {
                error = when (e.code()) {
                    403 -> "Keine Berechtigung."
                    422 -> "Bild ungültig oder zu groß."
                    else -> "Upload fehlgeschlagen (Fehler ${e.code()})."
                }
            } catch (e: Exception) {
                error = "Verbindungsfehler: ${e.message}"
            } finally {
                if (isCover) isUploadingCover = false else isUploadingBackdrop = false
            }
        }
    }

    class Factory(
        private val movieId: Int,
        private val repository: MovieRepository
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return EditMovieViewModel(movieId, repository) as T
        }
    }
}
