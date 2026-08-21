package info.movieshelf.ui.actors

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import info.movieshelf.data.model.Actor
import info.movieshelf.data.repository.ActorRepository
import kotlinx.coroutines.launch
import info.movieshelf.R
import info.movieshelf.ui.util.UiText

/**
 * Zeigt zuerst, was in der Datenbank steht, und frischt danach vom Server auf.
 *
 * Der Fehler wird nur gemeldet, wenn wirklich nichts anzuzeigen ist. Ein
 * unerreichbarer Server (etwa 521) darf ein Profil nicht verdecken, das
 * vollstaendig lokal vorliegt.
 */
class ActorDetailViewModel(
    private val localId: Long,
    private val remoteId: Int?,
    private val repository: ActorRepository
) : ViewModel() {
    var actor by mutableStateOf<Actor?>(null)
    var isLoading by mutableStateOf(false)
    var error by mutableStateOf<UiText?>(null)

    init {
        loadActor()
    }

    fun loadActor() {
        viewModelScope.launch {
            error = null
            val local = if (localId != 0L) {
                runCatching { repository.getLocalActor(localId) }.getOrNull()
            } else null
            if (local != null) actor = local

            // Ohne Server-ID gibt es nichts aufzufrischen; im eigenstaendigen
            // Betrieb ist die lokale Zeile ohnehin die einzige Quelle.
            if (remoteId == null) {
                if (actor == null) error = UiText.of(R.string.error_actor_not_found)
                return@launch
            }

            isLoading = actor == null
            try {
                repository.refreshActor(remoteId, localId)?.let { actor = it }
            } catch (e: Exception) {
                // Nur melden, wenn es nichts zu zeigen gibt.
                if (actor == null) {
                    error = UiText.of(R.string.error_actor_load, e.message ?: "")
                }
            } finally {
                isLoading = false
            }
        }
    }

    class Factory(
        private val localId: Long,
        private val remoteId: Int?,
        private val repository: ActorRepository
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return ActorDetailViewModel(localId, remoteId, repository) as T
        }
    }
}
