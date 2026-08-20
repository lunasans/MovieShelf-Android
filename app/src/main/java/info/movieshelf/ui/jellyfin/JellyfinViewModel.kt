package info.movieshelf.ui.jellyfin

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import info.movieshelf.R
import info.movieshelf.data.jellyfin.JellyfinClient
import info.movieshelf.data.jellyfin.JellyfinError
import info.movieshelf.data.jellyfin.JellyfinImportResult
import info.movieshelf.data.jellyfin.JellyfinImporter
import info.movieshelf.data.jellyfin.JellyfinLibrary
import info.movieshelf.data.jellyfin.JellyfinProgress
import info.movieshelf.data.jellyfin.JellyfinSession
import info.movieshelf.data.jellyfin.normalizeBaseUrl
import info.movieshelf.data.local.DataStoreManager
import info.movieshelf.data.local.JellyfinAccount
import info.movieshelf.ui.util.UiText
import kotlinx.coroutines.launch

/**
 * Zustand des Jellyfin-Imports: anmelden, Bibliotheken wählen, importieren.
 *
 * Der Ablauf ist bewusst dreistufig und nicht als Assistent gebaut — die
 * Anmeldung hält, die Bibliotheksauswahl ändert sich selten, und der Import
 * wird wiederholt. Wer schon angemeldet ist, landet direkt bei der Auswahl.
 */
class JellyfinViewModel(
    private val client: JellyfinClient,
    private val importer: JellyfinImporter,
    private val dataStoreManager: DataStoreManager
) : ViewModel() {

    var serverUrl by mutableStateOf("")
    var username by mutableStateOf("")
    var password by mutableStateOf("")

    var account by mutableStateOf<JellyfinAccount?>(null)
        private set

    var libraries by mutableStateOf<List<JellyfinLibrary>>(emptyList())
        private set
    var selectedLibraries by mutableStateOf<Set<String>>(emptySet())
        private set

    /** Metadaten gegen TMDb prüfen — nur sinnvoll mit hinterlegtem Schlüssel. */
    var verifyWithTmdb by mutableStateOf(true)
    val hasTmdbKey: Boolean get() = !dataStoreManager.currentTmdbApiKey().isNullOrBlank()

    var isBusy by mutableStateOf(false)
        private set
    var progress by mutableStateOf<JellyfinProgress?>(null)
        private set
    var result by mutableStateOf<JellyfinImportResult?>(null)
        private set
    var error by mutableStateOf<UiText?>(null)
        private set

    init {
        account = dataStoreManager.currentJellyfinAccount()
        account?.let {
            serverUrl = it.baseUrl
            username = it.userName
            loadLibraries()
        }
    }

    private fun session(): JellyfinSession? = account?.let {
        JellyfinSession(baseUrl = it.baseUrl, token = it.token, userId = it.userId)
    }

    fun login() {
        if (isBusy) return
        error = null
        isBusy = true
        viewModelScope.launch {
            try {
                val session = client.authenticate(
                    baseUrl = normalizeBaseUrl(serverUrl),
                    username = username.trim(),
                    password = password,
                    deviceId = dataStoreManager.jellyfinDeviceId()
                )
                val saved = JellyfinAccount(
                    baseUrl = session.baseUrl,
                    userName = username.trim(),
                    token = session.token,
                    userId = session.userId
                )
                dataStoreManager.saveJellyfinAccount(saved)
                account = saved
                // Das Passwort wird nirgends abgelegt; nach der Anmeldung hat
                // es in der Oberfläche nichts mehr verloren.
                password = ""
                loadLibraries()
            } catch (e: Exception) {
                error = messageFor(e)
            } finally {
                isBusy = false
            }
        }
    }

    fun logout() {
        dataStoreManager.clearJellyfinAccount()
        account = null
        libraries = emptyList()
        selectedLibraries = emptySet()
        result = null
        progress = null
        password = ""
    }

    fun loadLibraries() {
        val session = session() ?: return
        error = null
        isBusy = true
        viewModelScope.launch {
            try {
                libraries = client.libraries(session, dataStoreManager.jellyfinDeviceId())
                // Ohne Vorauswahl müsste man vor dem ersten Import zweimal
                // tippen; alles zu wählen ist der erwartbare Fall.
                selectedLibraries = libraries.map { it.id }.toSet()
            } catch (e: Exception) {
                error = messageFor(e)
            } finally {
                isBusy = false
            }
        }
    }

    fun toggleLibrary(id: String) {
        selectedLibraries = if (id in selectedLibraries) selectedLibraries - id else selectedLibraries + id
    }

    fun startImport() {
        val session = session() ?: return
        if (isBusy || selectedLibraries.isEmpty()) return
        error = null
        result = null
        isBusy = true
        viewModelScope.launch {
            try {
                result = importer.import(
                    session = session,
                    deviceId = dataStoreManager.jellyfinDeviceId(),
                    libraryIds = selectedLibraries.toList(),
                    verifyWithTmdb = verifyWithTmdb,
                    onProgress = { progress = it }
                )
            } catch (e: Exception) {
                error = messageFor(e)
            } finally {
                isBusy = false
                progress = null
            }
        }
    }

    fun dismissResult() {
        result = null
    }

    private fun messageFor(e: Exception): UiText = when (e) {
        is JellyfinError.InvalidUrl -> UiText.of(R.string.jellyfin_error_url)
        is JellyfinError.BadCredentials -> UiText.of(R.string.jellyfin_error_credentials)
        is JellyfinError.NoToken -> UiText.of(R.string.jellyfin_error_no_token)
        is JellyfinError.NotAuthenticated -> UiText.of(R.string.jellyfin_error_session)
        is JellyfinError.Unreachable -> UiText.of(R.string.jellyfin_error_unreachable, e.reason ?: "")
        else -> UiText.of(R.string.jellyfin_error_unreachable, e.message ?: "")
    }

    class Factory(
        private val client: JellyfinClient,
        private val importer: JellyfinImporter,
        private val dataStoreManager: DataStoreManager
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            JellyfinViewModel(client, importer, dataStoreManager) as T
    }
}
