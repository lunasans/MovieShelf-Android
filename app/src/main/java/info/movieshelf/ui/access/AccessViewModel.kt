package info.movieshelf.ui.access

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import info.movieshelf.R
import info.movieshelf.data.model.AccessToken
import info.movieshelf.data.repository.AccessRepository
import info.movieshelf.ui.util.UiText
import kotlinx.coroutines.launch

class AccessViewModel(private val repository: AccessRepository) : ViewModel() {

    var tokens by mutableStateOf<List<AccessToken>>(emptyList())
        private set
    var isLoading by mutableStateOf(false)
        private set

    /** Ein Widerruf läuft; die Knöpfe bleiben so lange gesperrt. */
    var isBusy by mutableStateOf(false)
        private set
    var error by mutableStateOf<UiText?>(null)
        private set

    fun load() {
        viewModelScope.launch {
            isLoading = true
            error = null
            try {
                tokens = repository.tokens()
            } catch (e: AccessRepository.OutdatedShelfException) {
                error = UiText.of(R.string.access_needs_newer_shelf)
            } catch (e: Exception) {
                error = UiText.of(R.string.access_load_failed, e.message ?: "")
            } finally {
                isLoading = false
            }
        }
    }

    /**
     * Einen Zugang widerrufen.
     *
     * Danach wird die Liste neu geholt statt lokal gestrichen: der Server
     * entscheidet, was noch gilt, und ein fehlgeschlagener Widerruf soll den
     * Eintrag nicht verschwinden lassen.
     */
    fun revoke(token: AccessToken) {
        if (isBusy) return
        viewModelScope.launch {
            isBusy = true
            try {
                repository.revoke(token.id)
                load()
            } catch (e: Exception) {
                error = UiText.of(R.string.access_revoke_failed, e.message ?: "")
            } finally {
                isBusy = false
            }
        }
    }

    fun revokeOthers() {
        if (isBusy) return
        viewModelScope.launch {
            isBusy = true
            try {
                repository.revokeOthers()
                load()
            } catch (e: Exception) {
                error = UiText.of(R.string.access_revoke_failed, e.message ?: "")
            } finally {
                isBusy = false
            }
        }
    }

    class Factory(private val repository: AccessRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            AccessViewModel(repository) as T
    }
}
