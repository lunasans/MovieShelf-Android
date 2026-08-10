package at.neuhaus.movieshelf.data.local.db

import androidx.room.*

@Dao
interface MovieDao {

    // ── Lesen ────────────────────────────────────────────────────────────────
    // Gelöschte Zeilen bleiben bis zum nächsten Push als Grabstein liegen und
    // sind deshalb überall auszufiltern.
    //
    // Von einem Boxset zählen die Teile, nicht die Hülle: sie sind die Filme,
    // die man tatsächlich besitzt. Damit stimmen die Zahlen an den Kategorien
    // mit der Gesamtzahl der Statistik überein, die genauso zählt.

    @Query("""
        SELECT * FROM movies
        WHERE isDeleted = 0 AND (isBoxset = 0 OR isBoxset IS NULL)
          AND (inCollection = 1 OR inCollection IS NULL)
    """)
    suspend fun getAllMovies(): List<MovieEntity>

    @Query("SELECT * FROM movies WHERE localId = :localId LIMIT 1")
    suspend fun getByLocalId(localId: Long): MovieEntity?

    /**
     * Neuzugaenge — was die Shelf frueher ueber `tag=new` lieferte, kommt
     * jetzt aus der eigenen Sammlung: die zuletzt hinzugekommenen Filme.
     */
    @Query("""
        SELECT * FROM movies
        WHERE isDeleted = 0 AND (isBoxset = 0 OR isBoxset IS NULL)
          AND (inCollection = 1 OR inCollection IS NULL)
        ORDER BY COALESCE(createdAt, '') DESC, localId DESC
        LIMIT :limit
    """)
    suspend fun getNewest(limit: Int): List<MovieEntity>

    @Query("SELECT * FROM movies WHERE remoteId = :remoteId LIMIT 1")
    suspend fun getByRemoteId(remoteId: Int): MovieEntity?

    @Query("SELECT localId FROM movies WHERE remoteId = :remoteId LIMIT 1")
    suspend fun findLocalIdByRemoteId(remoteId: Int): Long?

    /**
     * Suche über die ganze Sammlung — anders als die Listen **mit** Boxsets.
     *
     * Ein Boxset taucht in den Kategorien nicht auf, weil dort seine Teile
     * stehen. Über die Suche bleibt es erreichbar; genauso hebt die
     * Desktop-App bei einer Suche ihren Boxset-Filter auf.
     */
    @Query("""
        SELECT * FROM movies
        WHERE isDeleted = 0
          AND (inCollection = 1 OR inCollection IS NULL)
          AND (title LIKE '%' || :query || '%'
               OR director LIKE '%' || :query || '%'
               OR genre LIKE '%' || :query || '%')
    """)
    suspend fun searchMovies(query: String): List<MovieEntity>

    /**
     * Alle Zeilen fuer die Statistik — anders als [getAllMovies] ohne den
     * Boxset-Filter, weil ein Film in einem Boxset trotzdem zur Sammlung zaehlt.
     */
    @Query("SELECT * FROM movies WHERE isDeleted = 0")
    suspend fun getAllForStats(): List<MovieEntity>

    /** Alle Serien, die die Shelf kennt — Grundlage des Staffel-Abgleichs. */
    @Query("SELECT * FROM movies WHERE isDeleted = 0 AND collectionType = 'Serie' AND remoteId IS NOT NULL")
    suspend fun getSyncedSeries(): List<MovieEntity>

    /** Nur die IDs — Grundlage fuer das Aufraeumen der Bildablage. */
    @Query("SELECT localId FROM movies")
    suspend fun getAllLocalIds(): List<Long>

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
            val existing = getByRemoteId(remoteId)
            if (existing == null) {
                insert(movie)
                continue
            }
            // Aktualisieren, nicht ersetzen: INSERT OR REPLACE ist in SQLite ein
            // Loeschen mit anschliessendem Einfuegen, und die Fremdschluessel
            // stehen auf CASCADE. Ein Ersetzen wuerde Besetzung, Staffeln und
            // vorgemerkte Bild-Uploads dieses Films stillschweigend mitloeschen.
            update(
                movie.copy(
                    localId = existing.localId,
                    // Bereits heruntergeladene Bilder behalten: sonst ersetzte
                    // der Pull den Dateipfad wieder durch die Adresse und das
                    // Bild wuerde bei jedem Abgleich erneut geholt.
                    coverUrl = existing.coverUrl.takeIf { isLocalFile(it) } ?: movie.coverUrl,
                    backdropUrl = existing.backdropUrl.takeIf { isLocalFile(it) } ?: movie.backdropUrl
                )
            )
        }
    }

    private fun isLocalFile(value: String?): Boolean = value?.startsWith("/") == true

    @Query("UPDATE movies SET isWatched = :isWatched, updatedAt = :now WHERE localId = :localId")
    suspend fun updateWatched(localId: Long, isWatched: Boolean, now: String)

    @Query("UPDATE movies SET coverUrl = :url, updatedAt = :now WHERE localId = :localId")
    suspend fun updateCoverUrl(localId: Long, url: String?, now: String)

    @Query("UPDATE movies SET backdropUrl = :url, updatedAt = :now WHERE localId = :localId")
    suspend fun updateBackdropUrl(localId: Long, url: String?, now: String)

    /**
     * Bildpfad eintragen, **ohne** [MovieEntity.updatedAt] anzufassen.
     *
     * Fuer heruntergeladene Bilder: dort ersetzt der Abgleich nur die Adresse
     * durch die lokale Datei. Wuerde dabei updatedAt gesetzt, galte jede Zeile
     * anschliessend als lokal geaendert - und der naechste Lauf schoebe die
     * ganze Sammlung zur Shelf zurueck, obwohl sich inhaltlich nichts geaendert
     * hat.
     */
    @Query("UPDATE movies SET coverUrl = :path WHERE localId = :localId")
    suspend fun setCoverPath(localId: Long, path: String)

    @Query("UPDATE movies SET backdropUrl = :path WHERE localId = :localId")
    suspend fun setBackdropPath(localId: Long, path: String)

    /**
     * Zeilen, deren Bilder noch nicht heruntergeladen sind.
     *
     * Nur echte Adressen zaehlen — ein Film ohne Cover braucht keinen Anlauf.
     */
    @Query("""
        SELECT * FROM movies
        WHERE isDeleted = 0
          AND ((coverUrl LIKE 'http%') OR (backdropUrl LIKE 'http%'))
    """)
    suspend fun getMoviesMissingArtwork(): List<MovieEntity>

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

    // ── Kennzahlen ───────────────────────────────────────────────────────────

    @Query("""
        SELECT COUNT(*) FROM movies
        WHERE isDeleted = 0 AND (isBoxset = 0 OR isBoxset IS NULL)
          AND (inCollection = 1 OR inCollection IS NULL)
    """)
    suspend fun getMovieCount(): Int

    @Query("SELECT MAX(cachedAt) FROM movies")
    suspend fun getLastCacheTime(): Long?

}
