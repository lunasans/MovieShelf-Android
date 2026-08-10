package at.neuhaus.movieshelf.ui.sync

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import at.neuhaus.movieshelf.data.local.db.SettingDao
import at.neuhaus.movieshelf.data.local.db.SettingKeys
import at.neuhaus.movieshelf.data.sync.ListSyncEngine
import at.neuhaus.movieshelf.data.sync.ListSyncResult
import at.neuhaus.movieshelf.data.sync.SyncEngine
import at.neuhaus.movieshelf.data.sync.SyncPreview
import at.neuhaus.movieshelf.data.sync.SyncProgress
import at.neuhaus.movieshelf.data.sync.SyncResult
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class SyncViewModel(
    private val syncEngine: SyncEngine,
    private val listSyncEngine: ListSyncEngine,
    private val settingDao: SettingDao
) : ViewModel() {

    var lastSyncAt by mutableStateOf<String?>(null)
        private set

    /** Was der letzte Hintergrundlauf getan hat, `null` wenn noch keiner lief. */
    var backgroundSummary by mutableStateOf<String?>(null)
        private set
    var backgroundAt by mutableStateOf<String?>(null)
        private set
    var preview by mutableStateOf<SyncPreview?>(null)
        private set
    var progress by mutableStateOf<SyncProgress?>(null)
        private set
    var result by mutableStateOf<SyncResult?>(null)
        private set
    var listResult by mutableStateOf<ListSyncResult?>(null)
        private set
    var isBusy by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)

    init {
        viewModelScope.launch {
            lastSyncAt = syncEngine.lastSyncAt()
            backgroundAt = settingDao.get(SettingKeys.LAST_BACKGROUND_SYNC)
            backgroundSummary = settingDao.get(SettingKeys.LAST_BACKGROUND_RESULT)
        }
        viewModelScope.launch {
            syncEngine.progress.collectLatest { progress = it }
        }
    }

    /** Vorschau holen. Aendert nichts — sie ist die Grundlage der Bestaetigung. */
    fun loadPreview(full: Boolean = false) {
        if (isBusy) return
        viewModelScope.launch {
            isBusy = true
            error = null
            result = null
            try {
                preview = syncEngine.preview(full)
            } catch (e: Exception) {
                error = "Vorschau nicht moeglich: ${e.message}"
            } finally {
                isBusy = false
            }
        }
    }

    /** Abgleich starten. Erst nach einer gesehenen Vorschau erreichbar. */
    fun runSync(full: Boolean = false) {
        if (isBusy) return
        viewModelScope.launch {
            isBusy = true
            error = null
            try {
                result = syncEngine.runFullSync(full)
                listResult = listSyncEngine.sync()
                lastSyncAt = syncEngine.lastSyncAt()
                preview = null
            } catch (e: Exception) {
                error = "Abgleich fehlgeschlagen: ${e.message}"
            } finally {
                isBusy = false
                progress = null
            }
        }
    }

    fun dismissResult() {
        result = null
        listResult = null
    }

    class Factory(
        private val syncEngine: SyncEngine,
        private val listSyncEngine: ListSyncEngine,
        private val settingDao: SettingDao
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return SyncViewModel(syncEngine, listSyncEngine, settingDao) as T
        }
    }
}
