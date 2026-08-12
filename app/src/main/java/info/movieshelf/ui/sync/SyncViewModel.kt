package info.movieshelf.ui.sync

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import info.movieshelf.data.local.db.SettingDao
import info.movieshelf.data.local.db.SettingKeys
import info.movieshelf.data.sync.ListSyncEngine
import info.movieshelf.data.sync.ListSyncResult
import info.movieshelf.data.sync.SyncEngine
import info.movieshelf.data.sync.SyncPreview
import info.movieshelf.data.sync.SyncProgress
import info.movieshelf.data.sync.SyncResult
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** Welche Richtungen ein Lauf bedient. */
enum class SyncDirection { PULL, PUSH, BOTH }

class SyncViewModel(
    private val syncEngine: SyncEngine,
    private val listSyncEngine: ListSyncEngine,
    private val settingDao: SettingDao,
    /** Zeigt den Stand in der Statusleiste; ohne Melder laeuft alles wie bisher. */
    private val notifier: SyncNotifier? = null
) : ViewModel() {

    var lastSyncAt by mutableStateOf<String?>(null)
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
        }
        viewModelScope.launch {
            syncEngine.progress.collectLatest {
                progress = it
                it?.let { current -> notifier?.show(current) }
            }
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
    fun runSync(full: Boolean = false) = run(SyncDirection.BOTH, full)

    /** Nur laden: der Server bleibt unangetastet. */
    fun runPullOnly(full: Boolean = false) = run(SyncDirection.PULL, full)

    /** Nur hochladen: der Serverstand wird nicht geholt. */
    fun runPushOnly() = run(SyncDirection.PUSH, false)

    private fun run(direction: SyncDirection, full: Boolean) {
        if (isBusy) return
        viewModelScope.launch {
            isBusy = true
            error = null
            try {
                result = when (direction) {
                    SyncDirection.BOTH -> syncEngine.runSync(full)
                    SyncDirection.PULL -> syncEngine.runPullOnly(full)
                    SyncDirection.PUSH -> syncEngine.runPushOnly()
                }
                // Listen in derselben Richtung: ein reiner Ladelauf darf den
                // Server nicht anfassen, ein reiner Ladelauf umgekehrt nichts
                // lokal entfernen.
                listResult = when (direction) {
                    SyncDirection.BOTH -> listSyncEngine.sync()
                    SyncDirection.PULL -> listSyncEngine.pullLists()
                    SyncDirection.PUSH -> listSyncEngine.pushLists()
                }
                lastSyncAt = syncEngine.lastSyncAt()
                preview = null
            } catch (e: Exception) {
                error = "Abgleich fehlgeschlagen: ${e.message}"
            } finally {
                isBusy = false
                progress = null
                // Auch nach einem Fehlschlag: eine stehengebliebene Meldung
                // liesse einen Abgleich vortaeuschen, der laengst vorbei ist.
                notifier?.clear()
            }
        }
    }


    fun dismissResult() {
        result = null
        listResult = null
    }

    override fun onCleared() {
        notifier?.clear()
        super.onCleared()
    }

    class Factory(
        private val syncEngine: SyncEngine,
        private val listSyncEngine: ListSyncEngine,
        private val settingDao: SettingDao,
        private val notifier: SyncNotifier? = null
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return SyncViewModel(syncEngine, listSyncEngine, settingDao, notifier) as T
        }
    }
}
