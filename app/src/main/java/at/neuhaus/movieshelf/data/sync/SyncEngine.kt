package at.neuhaus.movieshelf.data.sync

import at.neuhaus.movieshelf.data.local.db.MovieDao
import at.neuhaus.movieshelf.data.local.db.MovieEntity
import at.neuhaus.movieshelf.data.local.db.SettingDao
import at.neuhaus.movieshelf.data.local.db.SettingKeys
import at.neuhaus.movieshelf.data.local.db.SyncClock
import at.neuhaus.movieshelf.data.model.ExportResponse
import at.neuhaus.movieshelf.data.model.MovieUpdateRequest
import at.neuhaus.movieshelf.data.model.SingleMovieResponse

/**
 * Zweiseitiger Abgleich zwischen lokaler Sammlung und Shelf.
 *
 * Portiert aus der Desktop-App (`useSyncEngine.ts`), inklusive der Regeln, die
 * dort teuer erkauft wurden. Die drei wichtigsten:
 *
 * 1. **Das Wasserzeichen kommt vom Server.** `exported_at` wird dort *vor* der
 *    Abfrage genommen, ist also nie neuer als die Daten. Die Geräteuhr darf
 *    darüber nicht entscheiden — bei falsch gestellter Uhr gingen sonst
 *    Änderungen verloren.
 * 2. **Der Pull fasst abweichende Zeilen nicht an.** Sonst stempelt er lokale,
 *    noch nicht hochgeladene Änderungen als synchronisiert, sie fallen aus der
 *    Warteschlange und divergieren still vom Server.
 * 3. **Erst hochladen, dann herunterladen.** Andersherum überschriebe der Pull
 *    genau die Zeilen, die gleich hätten hochgeladen werden sollen.
 */
class SyncEngine(
    private val movieDao: MovieDao,
    private val settingDao: SettingDao,
    private val apiProvider: () -> SyncApi,
    /**
     * Vorgemerkte Bilder nachreichen. Als Funktion statt als Repository
     * hereingereicht, damit der Abgleich nur von dem abhaengt, was er wirklich
     * braucht — und im Test ohne Datei- und Netzschicht auskommt.
     */
    private val flushPendingUploads: suspend () -> Unit = {}
) {
    private val api: SyncApi get() = apiProvider()

    /**
     * Vollständiger Abgleich: erst lokale Änderungen hoch, dann den Serverstand
     * herunter, dann die vorgemerkten Bilder.
     */
    suspend fun runFullSync(full: Boolean = false): SyncResult {
        val pushed = push()
        val pulled = pull(full)
        flushPendingUploads()
        return SyncResult(push = pushed, pull = pulled)
    }

    // ── Hochladen ────────────────────────────────────────────────────────────

    /**
     * Alle abweichenden Zeilen zum Server bringen.
     *
     * Fehler einzelner Zeilen brechen den Durchgang nicht ab: eine Zeile, die
     * der Server ablehnt, darf die übrigen nicht blockieren. Sie bleibt
     * abweichend und wird beim nächsten Anlauf erneut versucht.
     */
    suspend fun push(): PushResult {
        var created = 0
        var updated = 0
        var deleted = 0
        val errors = mutableListOf<SyncError>()

        for (entity in movieDao.getDirtyMovies()) {
            val now = SyncClock.now()
            try {
                when {
                    entity.isDeleted && entity.remoteId != null -> {
                        api.deleteMovie(entity.remoteId)
                        movieDao.hardDelete(entity.localId)
                        deleted++
                    }

                    // Nur lokal angelegt und schon wieder gelöscht: der Server
                    // hat davon nie erfahren, also gibt es nichts zu melden.
                    entity.isDeleted -> {
                        movieDao.hardDelete(entity.localId)
                        deleted++
                    }

                    entity.remoteId == null -> {
                        val response = api.createMovie(entity.toUpdateRequest()).data
                        if (response != null) {
                            movieDao.markSynced(entity.localId, now, response.id)
                            created++
                        }
                    }

                    else -> {
                        api.updateMovie(entity.remoteId, entity.toUpdateRequest())
                        movieDao.markSynced(entity.localId, now)
                        updated++
                    }
                }
            } catch (e: Exception) {
                errors += SyncError(entity.title ?: "Film ${entity.localId}", e.message ?: "Unbekannter Fehler")
            }
        }
        return PushResult(created, updated, deleted, errors)
    }

    // ── Herunterladen ────────────────────────────────────────────────────────

    /**
     * Serverstand einspielen. Ohne [full] nur, was sich seit dem letzten
     * erfolgreichen Abgleich geändert hat.
     */
    suspend fun pull(full: Boolean = false): PullResult {
        val since = if (full) null else settingDao.get(SettingKeys.LAST_SYNC_AT)
        val response = api.exportMovies(since)
        val movies = response.movies ?: emptyList()

        var applied = 0
        var skipped = 0
        var deleted = 0
        val errors = mutableListOf<SyncError>()

        for (movie in movies) {
            try {
                if (movie.isDeleted == true) {
                    movieDao.findLocalIdByRemoteId(movie.id)?.let {
                        movieDao.hardDelete(it)
                        deleted++
                    }
                    continue
                }

                val existing = movieDao.getByRemoteId(movie.id)
                if (existing != null && existing.isDirty) {
                    // Lokale Änderung hat Vorrang — sie wurde eben erst
                    // vergeblich hochgeladen und darf nicht überschrieben werden.
                    skipped++
                    continue
                }

                val entity = MovieEntity.fromServerMovie(movie)
                movieDao.upsertFromServer(
                    listOf(if (existing != null) entity.copy(localId = existing.localId) else entity)
                )
                applied++
            } catch (e: Exception) {
                errors += SyncError(movie.title ?: "Film ${movie.id}", e.message ?: "Unbekannter Fehler")
            }
        }

        // Zweiter Durchgang: Boxset-Eltern können erst zugeordnet werden, wenn
        // alle Zeilen liegen — das Elternteil kann nach dem Kind gekommen sein.
        movieDao.resolveBoxsetParents()

        // Wasserzeichen nur bei fehlerfreiem Durchgang fortschreiben. Sonst
        // gälten die fehlgeschlagenen Zeilen beim nächsten Mal als erledigt.
        val exportedAt = response.exportedAt
        if (errors.isEmpty() && exportedAt != null) {
            settingDao.put(SettingKeys.LAST_SYNC_AT, exportedAt)
        }

        return PullResult(
            applied = applied,
            skipped = skipped,
            deleted = deleted,
            isDelta = response.isDelta == true,
            exportedAt = exportedAt,
            errors = errors
        )
    }

    /** Wann zuletzt erfolgreich abgeglichen wurde — `null` heißt: noch nie. */
    suspend fun lastSyncAt(): String? = settingDao.get(SettingKeys.LAST_SYNC_AT)
}

/**
 * Der Ausschnitt der Shelf-API, den der Abgleich braucht.
 *
 * Bewusst schmal gehalten: die vollstaendige Schnittstelle hat ueber dreissig
 * Methoden, von denen hier vier gebraucht werden. So ist am Typ ablesbar, was
 * der Abgleich am Server anfassen kann.
 */
interface SyncApi {
    suspend fun exportMovies(since: String?): ExportResponse
    suspend fun createMovie(request: MovieUpdateRequest): SingleMovieResponse
    suspend fun updateMovie(id: Int, request: MovieUpdateRequest): SingleMovieResponse
    suspend fun deleteMovie(id: Int)
}

data class SyncError(val subject: String, val message: String)

data class PushResult(
    val created: Int = 0,
    val updated: Int = 0,
    val deleted: Int = 0,
    val errors: List<SyncError> = emptyList()
) {
    val total: Int get() = created + updated + deleted
}

data class PullResult(
    val applied: Int = 0,
    val skipped: Int = 0,
    val deleted: Int = 0,
    val isDelta: Boolean = false,
    val exportedAt: String? = null,
    val errors: List<SyncError> = emptyList()
)

data class SyncResult(
    val push: PushResult,
    val pull: PullResult
) {
    val errors: List<SyncError> get() = push.errors + pull.errors
    val hasErrors: Boolean get() = errors.isNotEmpty()
}
