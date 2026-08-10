package at.neuhaus.movieshelf.data.sync

import at.neuhaus.movieshelf.data.local.db.MovieDao
import at.neuhaus.movieshelf.data.local.db.MovieEntity
import at.neuhaus.movieshelf.data.local.db.SettingDao
import at.neuhaus.movieshelf.data.local.db.SettingEntity
import at.neuhaus.movieshelf.data.model.ExportResponse
import at.neuhaus.movieshelf.data.model.Movie
import at.neuhaus.movieshelf.data.model.MovieUpdateRequest
import at.neuhaus.movieshelf.data.model.SingleMovieResponse

/**
 * Handgeschriebene Doubles statt einer Mocking-Bibliothek: der Abgleich hängt
 * nur an drei schmalen Schnittstellen, und eine Liste im Speicher zeigt
 * deutlicher, was eine Regel tatsächlich mit den Daten macht.
 *
 * Nicht überschriebene Methoden werfen bewusst — taucht eine davon in einem
 * Test auf, ist die Annahme über den Abgleich falsch, nicht das Double.
 */
open class FakeMovieDao : MovieDao {

    val rows = mutableListOf<MovieEntity>()

    override suspend fun getDirtyMovies(): List<MovieEntity> = rows.filter { it.isDirty }

    override suspend fun getByRemoteId(remoteId: Int): MovieEntity? =
        rows.firstOrNull { it.remoteId == remoteId }

    override suspend fun getByLocalId(localId: Long): MovieEntity? =
        rows.firstOrNull { it.localId == localId }

    override suspend fun findLocalIdByRemoteId(remoteId: Int): Long? =
        rows.firstOrNull { it.remoteId == remoteId }?.localId

    override suspend fun insert(movie: MovieEntity): Long {
        val id = if (movie.localId != 0L) movie.localId else (rows.maxOfOrNull { it.localId } ?: 0L) + 1
        rows.removeAll { it.localId == id }
        rows += movie.copy(localId = id)
        return id
    }

    override suspend fun upsertFromServer(movies: List<MovieEntity>) {
        for (movie in movies) {
            val existing = movie.remoteId?.let { findLocalIdByRemoteId(it) }
            insert(if (existing != null) movie.copy(localId = existing) else movie)
        }
    }

    override suspend fun markSynced(localId: Long, syncedAt: String, remoteId: Int?) {
        val index = rows.indexOfFirst { it.localId == localId }
        if (index >= 0) {
            rows[index] = rows[index].copy(syncedAt = syncedAt, remoteId = remoteId ?: rows[index].remoteId)
        }
    }

    override suspend fun hardDelete(localId: Long) {
        rows.removeAll { it.localId == localId }
    }

    override suspend fun resolveBoxsetParents() = Unit

    // ── Vom Abgleich nicht benutzt ───────────────────────────────────────────

    override suspend fun getAllMovies(): List<MovieEntity> = unused()
    override suspend fun getAllForStats(): List<MovieEntity> = unused()
    override suspend fun searchMovies(query: String): List<MovieEntity> = unused()
    override suspend fun update(movie: MovieEntity) = unused()
    override suspend fun replaceServerState(movies: List<MovieEntity>) = unused()
    override suspend fun deleteVanishedServerRows(keep: List<Int>) = unused()
    override suspend fun updateWatched(localId: Long, isWatched: Boolean, now: String) = unused()
    override suspend fun updateCoverUrl(localId: Long, url: String?, now: String) = unused()
    override suspend fun updateBackdropUrl(localId: Long, url: String?, now: String) = unused()
    override suspend fun getMoviesMissingArtwork(): List<MovieEntity> = unused()
    override suspend fun markDeleted(localId: Long, now: String) = unused()
    override suspend fun deleteAll() = unused()
    override suspend fun getBoxsetChildren(boxsetLocalId: Long): List<MovieEntity> = unused()
    override suspend fun getMovieCount(): Int = unused()
    override suspend fun getLastCacheTime(): Long? = unused()
    override suspend fun getDistinctGenres(): List<String> = unused()
    override suspend fun getDistinctDirectors(): List<String> = unused()
    override suspend fun getMinYear(): Int? = unused()
    override suspend fun getMaxYear(): Int? = unused()

    private fun unused(): Nothing =
        throw AssertionError("Der Abgleich sollte diese Methode nicht aufrufen")
}

open class FakeSettingDao : SettingDao {

    val values = mutableMapOf<String, String?>()

    override suspend fun get(key: String): String? = values[key]

    override suspend fun put(setting: SettingEntity) {
        values[setting.key] = setting.value
    }

    override suspend fun remove(key: String) {
        values.remove(key)
    }

    override fun observe(key: String): kotlinx.coroutines.flow.Flow<String?> =
        kotlinx.coroutines.flow.flowOf(values[key])
}

open class FakeSyncApi(
    private val export: ExportResponse = ExportResponse(exportedAt = "t", movies = emptyList()),
    private val created: Movie? = null
) : SyncApi {

    /** Mit welchem `since` zuletzt exportiert wurde — `null` heißt Vollstand. */
    var lastSince: String? = null
        private set

    val deletedIds = mutableListOf<Int>()
    val updatedIds = mutableListOf<Int>()

    override suspend fun exportMovies(since: String?): ExportResponse {
        lastSince = since
        return export
    }

    override suspend fun createMovie(request: MovieUpdateRequest): SingleMovieResponse =
        SingleMovieResponse(data = created)

    override suspend fun updateMovie(id: Int, request: MovieUpdateRequest): SingleMovieResponse {
        updatedIds += id
        return SingleMovieResponse(data = null)
    }

    override suspend fun deleteMovie(id: Int) {
        deletedIds += id
    }
}
