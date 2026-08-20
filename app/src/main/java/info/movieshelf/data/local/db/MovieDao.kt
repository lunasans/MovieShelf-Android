package info.movieshelf.data.local.db

import androidx.room.*

@Dao
interface MovieDao {

    // ── Lesen ────────────────────────────────────────────────────────────────
    // Gelöschte Zeilen bleiben bis zum nächsten Push als Grabstein liegen und
    // sind deshalb überall auszufiltern.
    //
    // Boxsets: **Listen zeigen die Hülle, Kennzahlen zählen die Teile.** Zwei
    // verschiedene Regeln, und sie nicht zu verwechseln ist der ganze Punkt.
    // Die Web-Oberfläche macht es genauso: die Filmliste filtert
    // `whereNull('boxset_parent')` — ein Boxset steht dort als ein Eintrag,
    // seine Teile stecken darin. Die Statistik dagegen wirft mit
    // `whereDoesntHave('boxsetChildren')` die Hülle raus und zählt die Teile,
    // denn das sind die Filme, die man tatsächlich besitzt (siehe [LocalStats]).
    //
    // Gelöschte Zeilen bleiben bis zum nächsten Push als Grabstein liegen und
    // sind deshalb überall auszufiltern.

    /**
     * Die Filmliste. Boxset-Teile bleiben draussen — sie sind über ihre Hülle
     * erreichbar, und ohne diesen Filter stünde jede Sammlung doppelt in der
     * Liste: einmal als Boxset, einmal in ihre Einzelteile zerlegt.
     *
     * Geprüft werden beide Elternspalten: die lokale ID steht erst, wenn der
     * Pull sie auflösen konnte, und ein selbst angelegtes Boxset hat gar keine
     * Server-ID.
     */
    @Query("""
        SELECT * FROM movies
        WHERE isDeleted = 0
          AND boxsetParentLocalId IS NULL AND boxsetParentRemoteId IS NULL
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
        WHERE isDeleted = 0
          AND boxsetParentLocalId IS NULL AND boxsetParentRemoteId IS NULL
          AND (inCollection = 1 OR inCollection IS NULL)
        ORDER BY COALESCE(createdAt, '') DESC, localId DESC
        LIMIT :limit
    """)
    suspend fun getNewest(limit: Int): List<MovieEntity>

    /**
     * Filme fuer den Hero-Bereich — zufaellig, wie in der Web-Oberflaeche
     * (`inRandomOrder()->limit(5)`).
     *
     * Nur mit Hintergrundbild: ohne eines wirkt der Banner beschnitten. Die
     * Auswahl wird einmal je Aufruf gezogen, nicht bei jeder Neuberechnung —
     * sonst spraenge der Banner bei jeder Kleinigkeit um.
     */
    @Query("""
        SELECT * FROM movies
        WHERE isDeleted = 0
          AND boxsetParentLocalId IS NULL AND boxsetParentRemoteId IS NULL
          AND (inCollection = 1 OR inCollection IS NULL)
          AND backdropUrl IS NOT NULL AND backdropUrl != ''
        ORDER BY RANDOM()
        LIMIT :limit
    """)
    suspend fun getFeatured(limit: Int): List<MovieEntity>

    @Query("SELECT * FROM movies WHERE remoteId = :remoteId LIMIT 1")
    suspend fun getByRemoteId(remoteId: Int): MovieEntity?

    @Query("SELECT localId FROM movies WHERE remoteId = :remoteId LIMIT 1")
    suspend fun findLocalIdByRemoteId(remoteId: Int): Long?

    /**
     * Fuer die Duplikatpruefung beim Jellyfin-Import: die TMDb-ID ist der
     * eindeutige Weg. Geloeschte Zeilen zaehlen mit — sie warten auf die
     * Bestaetigung der Shelf und duerfen nicht ungefragt zurueckkehren.
     */
    /**
     * Ein zufaelliger Titel aus der Sammlung — fuer die Auslosung, wenn man
     * sich nicht entscheiden kann.
     *
     * Boxsets bleiben aussen vor: eine Huelle schaut niemand, gemeint sind die
     * Filme darin. Gleiche Regel wie in der Desktop-App (`randomMovie`).
     *
     * @param collectionType "Film" oder "Serie", oder `null` fuer beides.
     */
    @Query("""
        SELECT * FROM movies
        WHERE isDeleted = 0
          AND (inCollection = 1 OR inCollection IS NULL)
          AND (isBoxset IS NULL OR isBoxset = 0)
          AND (:collectionType IS NULL OR collectionType = :collectionType)
        ORDER BY RANDOM()
        LIMIT 1
    """)
    suspend fun randomMovie(collectionType: String?): MovieEntity?

    @Query("SELECT * FROM movies WHERE tmdbId = :tmdbId LIMIT 1")
    suspend fun findByTmdbId(tmdbId: String): MovieEntity?

    /**
     * Alle Titel eines Jahrgangs — der zweite Weg der Duplikatpruefung, wenn
     * keine TMDb-ID vorliegt. Der Titelvergleich geschieht danach im Kotlin-Code,
     * weil er normalisiert (Gross-/Kleinschreibung, Leerraum) und nicht als
     * SQL-Vergleich ausgedrueckt werden kann.
     */
    @Query("SELECT * FROM movies WHERE (:year IS NULL AND year IS NULL) OR year = :year")
    suspend fun findByYear(year: Int?): List<MovieEntity>

    /**
     * Suche über die ganze Sammlung — anders als die Listen **mit** den
     * Boxset-Teilen.
     *
     * Ein einzelner Film aus einem Boxset steht in der Liste nicht, weil dort
     * die Hülle steht. Über die Suche bleibt er trotzdem auffindbar; genauso
     * hebt die Web-Oberfläche ihren Boxset-Filter auf, sobald gefiltert oder
     * gesucht wird (`if (! $hasFilters) whereNull('boxset_parent')`).
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
     * Alle Zeilen fuer die Statistik — ungefiltert, weil dort die umgekehrte
     * Regel gilt: [LocalStats] wirft die Huellen weg und zaehlt die Teile.
     * Ein Film in einem Boxset zaehlt zur Sammlung, das Boxset selbst nicht.
     */
    /**
     * Die Wunschliste: alles, was vorgemerkt und noch nicht in der Sammlung
     * ist. Die Shelf unterscheidet beides ueber `in_collection` — es sind
     * dieselben Zeilen, nur ohne Besitz.
     */
    @Query("SELECT * FROM movies WHERE isDeleted = 0 AND inCollection = 0 ORDER BY title COLLATE NOCASE")
    suspend fun getWishlist(): List<MovieEntity>

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

    /**
     * Filme, deren "gesehen"-Markierung noch nicht beim Server ist.
     *
     * `IS NOT` statt `!=`, damit auch der Wechsel von "unbekannt" auf gesetzt
     * erkannt wird — mit `!=` faellt in SQL jeder Vergleich mit NULL durch.
     */
    @Query("""
        SELECT * FROM movies
        WHERE isDeleted = 0 AND remoteId IS NOT NULL
          AND COALESCE(isWatched, 0) IS NOT COALESCE(syncedWatched, 0)
    """)
    suspend fun getPendingWatched(): List<MovieEntity>

    /** Den vom Server bestaetigten Stand festhalten — ohne updatedAt zu ruehren. */
    @Query("UPDATE movies SET syncedWatched = :isWatched WHERE localId = :localId")
    suspend fun markWatchedSynced(localId: Long, isWatched: Boolean)

    /**
     * Eigene Bewertung setzen. `null` entfernt sie wieder — der Server kennt
     * dafuer die 0, lokal ist es die Abwesenheit eines Wertes.
     *
     * `updatedAt` wird bewusst mitgesetzt, damit die Zeile beim Abgleich
     * ueberhaupt betrachtet wird; der eigentliche Versand laeuft danach ueber
     * den eigenen Endpunkt.
     */
    @Query("UPDATE movies SET userRating = :rating, updatedAt = :now WHERE localId = :localId")
    suspend fun updateUserRating(localId: Long, rating: Int?, now: String)

    /**
     * Bewertungen, die noch zum Server sollen — der Gegenpart zu
     * [getPendingWatched]. Nur Zeilen mit Server-ID: ein Film, den die Shelf
     * noch nicht kennt, bekommt seine Bewertung nach dem Anlegen.
     */
    @Query("""
        SELECT * FROM movies
        WHERE remoteId IS NOT NULL
          AND isDeleted = 0
          AND ((userRating IS NULL) != (syncedUserRating IS NULL) OR userRating != syncedUserRating)
    """)
    suspend fun getPendingUserRatings(): List<MovieEntity>

    @Query("UPDATE movies SET syncedUserRating = :rating WHERE localId = :localId")
    suspend fun markUserRatingSynced(localId: Long, rating: Int?)

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

    /**
     * Die Teile eines Boxsets — Reihenfolge und Filter wie in der Relation
     * `Movie::boxsetChildren()` der Shelf: nach Jahr, dann nach Titel.
     */
    @Query("""
        SELECT * FROM movies
        WHERE isDeleted = 0 AND boxsetParentLocalId = :boxsetLocalId
          AND (inCollection = 1 OR inCollection IS NULL)
        ORDER BY COALESCE(year, 0), title
    """)
    suspend fun getBoxsetChildren(boxsetLocalId: Long): List<MovieEntity>

    /**
     * Wie viele Teile jedes Boxsets gesehen sind.
     *
     * Grundlage fuer den abgeleiteten Stand der Huelle: ein Boxset schaut
     * niemand, man schaut die Filme darin. Der eigene "gesehen"-Wert der
     * Huelle bleibt deshalb ungenutzt — er stuende sonst als zweite Wahrheit
     * neben dieser hier.
     */
    @Query("""
        SELECT boxsetParentLocalId AS parentLocalId,
               COUNT(*) AS total,
               SUM(CASE WHEN isWatched = 1 THEN 1 ELSE 0 END) AS watched
        FROM movies
        WHERE isDeleted = 0 AND boxsetParentLocalId IS NOT NULL
          AND (inCollection = 1 OR inCollection IS NULL)
        GROUP BY boxsetParentLocalId
    """)
    suspend fun getBoxsetWatchStates(): List<BoxsetWatchState>

    // ── Kennzahlen ───────────────────────────────────────────────────────────

    @Query("""
        SELECT COUNT(*) FROM movies
        WHERE isDeleted = 0 AND (isBoxset = 0 OR isBoxset IS NULL)
          AND (inCollection = 1 OR inCollection IS NULL)
    """)
    suspend fun getMovieCount(): Int

    /**
     * Wie viele Filme die Sammlung enthaelt — die Zahl neben der Ueberschrift.
     *
     * Gezaehlt wird nach der Kennzahl-Regel, nicht nach der Listen-Regel: ein
     * Boxset ist kein Film, seine Teile sind welche. Die Zahl liegt deshalb
     * ueber der Anzahl der Kacheln darunter, wo das Boxset als ein Eintrag
     * steht — sie beantwortet "wie viele Filme habe ich", nicht "wie viele
     * Zeilen zeigt die Liste", und stimmt so mit der Statistik ueberein.
     */
    @Query("""
        SELECT COUNT(*) FROM movies
        WHERE isDeleted = 0 AND (isBoxset = 0 OR isBoxset IS NULL)
          AND (inCollection = 1 OR inCollection IS NULL)
          AND (collectionType IS NULL OR collectionType <> 'Serie')
    """)
    suspend fun countFilmsInCollection(): Int

    /** Dasselbe fuer Serien. Boxsets gibt es dort nicht, der Filter schadet aber nicht. */
    @Query("""
        SELECT COUNT(*) FROM movies
        WHERE isDeleted = 0 AND (isBoxset = 0 OR isBoxset IS NULL)
          AND (inCollection = 1 OR inCollection IS NULL)
          AND collectionType = 'Serie'
    """)
    suspend fun countSeriesInCollection(): Int

    @Query("SELECT MAX(cachedAt) FROM movies")
    suspend fun getLastCacheTime(): Long?

}

/**
 * Gesehen-Stand der Teile eines Boxsets — siehe [MovieDao.getBoxsetWatchStates].
 *
 * [isFullyWatched] ist bewusst streng: erst wenn wirklich jeder Teil gesehen
 * ist, gilt das Boxset als gesehen. Ein halb geschautes Boxset als "gesehen"
 * auszuweisen waere die unangenehmere Luege.
 */
data class BoxsetWatchState(
    val parentLocalId: Long,
    val total: Int,
    val watched: Int
) {
    val isFullyWatched: Boolean get() = total > 0 && watched == total
}
