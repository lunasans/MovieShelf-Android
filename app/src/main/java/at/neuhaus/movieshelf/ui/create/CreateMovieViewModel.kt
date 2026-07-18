package at.neuhaus.movieshelf.ui.create

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.neuhaus.movieshelf.data.api.RetrofitClient
import at.neuhaus.movieshelf.data.model.MovieUpdateRequest
import kotlinx.coroutines.launch
import retrofit2.HttpException

class CreateMovieViewModel : ViewModel() {

    var isSaving by mutableStateOf(false)
        private set
    var createdMovieId by mutableStateOf<Int?>(null)
        private set
    var error by mutableStateOf<String?>(null)

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
    var inCollection by mutableStateOf(true)

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
                val response = RetrofitClient.api.createMovie(request)
                createdMovieId = response.data?.id
                if (createdMovieId == null) {
                    error = "Film wurde angelegt, aber es wurde keine ID zurückgegeben."
                }
            } catch (e: HttpException) {
                error = when (e.code()) {
                    403 -> "Keine Berechtigung zum Anlegen."
                    422 -> "Eingaben ungültig. Bitte prüfe die Felder."
                    else -> "Anlegen fehlgeschlagen (Fehler ${e.code()})."
                }
            } catch (e: Exception) {
                error = "Verbindungsfehler: ${e.message}"
            } finally {
                isSaving = false
            }
        }
    }
}
