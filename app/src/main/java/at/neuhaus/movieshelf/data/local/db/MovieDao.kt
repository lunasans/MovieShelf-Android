package at.neuhaus.movieshelf.data.local.db

import androidx.room.*

@Dao
interface MovieDao {

    // ── Lesen ────────────────────────────────────────────────────────────────
    // Gelöschte Zeilen bleiben bis zum nächsten Push als Grabstein liegen und
    // sind deshalb überall auszufiltern.

    @Query("""
        SELECT * FROM movies
        WHERE isDeleted = 0 AND boxsetParentLocalId IS NULL
          AND (inCollection = 1 OR inCollection IS NULL)
    """)
    suspend fun getAllMovies(): List<MovieEntity>

    @Query("SELECT * FROM movies WHERE localId = :localId LIMIT 1")
    suspend fun getByLocalId(localId: Long): MovieEntity?

    @Query("SELECT * FROM movies WHERE remoteId = :remoteId LIMIT 1")
    suspend fun getByRemoteId(remoteId: Int): MovieEntity?

    @Query("SELECT localId FROM movies WHERE remoteId = :remoteId LIMIT 1")
    suspend fun findLocalIdByRemoteId(remoteId: Int): Long?

    @Query("""
        SELECT * FROM movies
        WHERE isDeleted = 0 AND boxsetParentLocalId IS NULL
          AND (inCollection = 1 OR inCollection IS NULL)
          AND (title LIKE '%' || :query || '%'
               OR director LIKE '%' || :query || '%'
               OR genre LIKE '%' || :query || '%')
    """)
    suspend fun searchMovies(query: String): List<MovieEntity>

    /** Alle Zeilen mit lokalen, noch nicht übertragenen Änderungen. */
    @Query("SELECT * FROM movies WHERE syncedAt IS NULL OR updatedAt > syncedAt")
    suspend fun getDirtyMovies(): List<MovieEntity>

    // ── Schreiben ────────────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(movie: MovieEntity): Long

    @Update
    suspend fun update(movie: MovieEntity)

    /**
     * Server-Zeilen einspielen, ohne lokale IDs zu verlieren.
     *
     * Eine bekannte [MovieEntity.remoteId] wird auf ihre bestehende lokale Zeile
     * gehoben, statt eine zweite anzulegen — sonst bekäme derselbe Film bei
     * jedem Abgleich eine neue ID und jede gemerkte Navigation liefe ins Leere.
     */
    @Transaction
    suspend fun upsertFromServer(movies: List<MovieEntity>) {
        for (movie in movies) {
            val remoteId = movie.remoteId ?: continue
            val existingId = findLocalIdByRemoteId(remoteId)
            insert(if (existingId != null) movie.copy(localId = existingId) else movie)
        }
    }

    /**
     * Vollstand einspielen: einspielen und danach alles entfernen, was der
     * Server nicht mehr kennt. Zeilen ohne [MovieEntity.syncedAt] bleiben
     * unangetastet — sie existieren nur lokal und wären sonst verloren, bevor
     * sie je hochgeladen wurden.
     */
    @Transaction
    suspend fun replaceServerState(movies: List<MovieEntity>) {
        upsertFromServer(movies)
        val keep = movies.mapNotNull { it.remoteId }
        if (keep.isNotEmpty()) deleteVanishedServerRows(keep)
    }

    @Query("DELETE FROM movies WHERE syncedAt IS NOT NULL AND remoteId NOT IN (:keep)")
    suspend fun deleteVanishedServerRows(keep: List<Int>)

    @Query("UPDATE movies SET isWatched = :isWatched, updatedAt = :now WHERE localId = :localId")
    suspend fun updateWatched(localId: Long, isWatched: Boolean, now: String)

    @Query("UPDATE movies SET coverUrl = :url, updatedAt = :now WHERE localId = :localId")
    suspend fun updateCoverUrl(localId: Long, url: String?, now: String)

    @Query("UPDATE movies SET backdropUrl = :url, updatedAt = :now WHERE localId = :localId")
    suspend fun updateBackdropUrl(localId: Long, url: String?, now: String)

    /** Zeile als übertragen stempeln — nach erfolgreichem Push oder Direktaufruf. */
    @Query("UPDATE movies SET syncedAt = :syncedAt, remoteId = COALESCE(:remoteId, remoteId) WHERE localId = :localId")
    suspend fun markSynced(localId: Long, syncedAt: String, remoteId: Int? = null)

    /** Löschung vormerken. Endgültig entfernt wird erst nach erfolgreichem Push. */
    @Query("UPDATE movies SET isDeleted = 1, updatedAt = :now WHERE localId = :localId")
    suspend fun markDeleted(localId: Long, now: String)

    @Query("DELETE FROM movies WHERE localId = :localId")
    suspend fun hardDelete(localId: Long)

    @Query("DELETE FROM movies")
    suspend fun deleteAll()

    // ── Boxsets ──────────────────────────────────────────────────────────────

    /**
     * Zweiter Durchgang nach dem Pull: die roh abgelegten Server-IDs der
     * Boxsets in lokale IDs übersetzen. Getrennt vom Einspielen, weil das
     * Elternteil erst nach dem Kind angekommen sein kann.
     */
    @Query("""
        UPDATE movies SET boxsetParentLocalId = (
            SELECT p.localId FROM movies p
            WHERE p.remoteId = movies.boxsetParentRemoteId AND p.isBoxset = 1
        )
        WHERE boxsetParentRemoteId IS NOT NULL
    """)
    suspend fun resolveBoxsetParents()

    @Query("SELECT * FROM movies WHERE isDeleted = 0 AND boxsetParentLocalId = :boxsetLocalId")
    suspend fun getBoxsetChildren(boxsetLocalId: Long): List<MovieEntity>

    // ── Kennzahlen für Filter und Statistik ──────────────────────────────────

    @Query("""
        SELECT COUNT(*) FROM movies
        WHERE isDeleted = 0 AND boxsetParentLocalId IS NULL
          AND (inCollection = 1 OR inCollection IS NULL)
    """)
    suspend fun getMovieCount(): Int

    @Query("SELECT MAX(cachedAt) FROM movies")
    suspend fun getLastCacheTime(): Long?

    @Query("""
        SELECT DISTINCT genre FROM movies
        WHERE isDeleted = 0 AND genre IS NOT NULL AND genre != ''
          AND boxsetParentLocalId IS NULL AND (inCollection = 1 OR inCollection IS NULL)
        ORDER BY genre
    """)
    suspend fun getDistinctGenres(): List<String>

    @Query("""
        SELECT DISTINCT director FROM movies
        WHERE isDeleted = 0 AND director IS NOT NULL AND director != ''
          AND boxsetParentLocalId IS NULL AND (inCollection = 1 OR inCollection IS NULL)
        ORDER BY director
    """)
    suspend fun getDistinctDirectors(): List<String>

    @Query("""
        SELECT MIN(year) FROM movies
        WHERE isDeleted = 0 AND year IS NOT NULL AND boxsetParentLocalId IS NULL
          AND (inCollection = 1 OR inCollection IS NULL)
    """)
    suspend fun getMinYear(): Int?

    @Query("""
        SELECT MAX(year) FROM movies
        WHERE isDeleted = 0 AND year IS NOT NULL AND boxsetParentLocalId IS NULL
          AND (inCollection = 1 OR inCollection IS NULL)
    """)
    suspend fun getMaxYear(): Int?
}
