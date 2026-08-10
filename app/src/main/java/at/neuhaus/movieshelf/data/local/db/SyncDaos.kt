package at.neuhaus.movieshelf.data.local.db

import androidx.room.*

/**
 * DAOs der übrigen Sync-Tabellen. Alle folgen demselben Muster wie [MovieDao]:
 * Nachschlagen über die Server-ID, Einspielen unter Beibehaltung der lokalen ID.
 */

@Dao
interface ActorDao {

    @Query("SELECT * FROM actors ORDER BY name")
    suspend fun getAll(): List<ActorEntity>

    @Query("SELECT * FROM actors WHERE localId = :localId LIMIT 1")
    suspend fun getByLocalId(localId: Long): ActorEntity?

    @Query("SELECT localId FROM actors WHERE remoteId = :remoteId LIMIT 1")
    suspend fun findLocalIdByRemoteId(remoteId: Int): Long?

    @Query("SELECT localId FROM actors WHERE name = :name LIMIT 1")
    suspend fun findLocalIdByName(name: String): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(actor: ActorEntity): Long

    @Transaction
    suspend fun upsertFromServer(actors: List<ActorEntity>) {
        for (actor in actors) {
            val existingId = actor.remoteId?.let { findLocalIdByRemoteId(it) }
                // Ohne Server-ID über den Namen zusammenführen: sonst entstünde
                // bei jedem Import ein zweiter Eintrag derselben Person.
                ?: findLocalIdByName(actor.name)
            insert(if (existingId != null) actor.copy(localId = existingId) else actor)
        }
    }

    @Query("SELECT * FROM actors WHERE syncedAt IS NULL OR updatedAt > syncedAt")
    suspend fun getDirty(): List<ActorEntity>

    @Query("SELECT localId FROM actors")
    suspend fun getAllLocalIds(): List<Long>

    /** Darsteller, deren Bild noch eine Adresse statt einer Datei ist. */
    @Query("SELECT * FROM actors WHERE imagePath LIKE 'http%'")
    suspend fun getActorsMissingArtwork(): List<ActorEntity>

    @Query("UPDATE actors SET imagePath = :path WHERE localId = :localId")
    suspend fun updateImagePath(localId: Long, path: String?)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setCast(refs: List<FilmActorCrossRef>)

    @Query("DELETE FROM film_actor WHERE movieLocalId = :movieLocalId")
    suspend fun clearCast(movieLocalId: Long)

    @Transaction
    suspend fun replaceCast(movieLocalId: Long, refs: List<FilmActorCrossRef>) {
        clearCast(movieLocalId)
        if (refs.isNotEmpty()) setCast(refs)
    }

    @Query("""
        SELECT a.* FROM actors a
        JOIN film_actor fa ON fa.actorLocalId = a.localId
        WHERE fa.movieLocalId = :movieLocalId
        ORDER BY fa.sortOrder
    """)
    suspend fun getCastOf(movieLocalId: Long): List<ActorEntity>
}

@Dao
interface ListDao {

    @Query("SELECT * FROM lists ORDER BY name")
    suspend fun getAll(): List<ListEntity>

    @Query("SELECT * FROM lists WHERE localId = :localId LIMIT 1")
    suspend fun getByLocalId(localId: Long): ListEntity?

    @Query("SELECT localId FROM lists WHERE remoteId = :remoteId LIMIT 1")
    suspend fun findLocalIdByRemoteId(remoteId: Int): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(list: ListEntity): Long

    @Transaction
    suspend fun upsertFromServer(lists: List<ListEntity>) {
        for (list in lists) {
            val remoteId = list.remoteId ?: continue
            val existingId = findLocalIdByRemoteId(remoteId)
            insert(if (existingId != null) list.copy(localId = existingId) else list)
        }
    }

    @Query("SELECT * FROM lists WHERE syncedAt IS NULL OR updatedAt > syncedAt")
    suspend fun getDirty(): List<ListEntity>

    @Query("DELETE FROM lists WHERE localId = :localId")
    suspend fun delete(localId: Long)

    // ── Inhalte ──────────────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addItem(item: ListItemEntity)

    @Query("SELECT * FROM list_items WHERE listLocalId = :listLocalId ORDER BY addedAt")
    suspend fun getItems(listLocalId: Long): List<ListItemEntity>

    @Query("""
        DELETE FROM list_items
        WHERE listLocalId = :listLocalId AND itemType = :itemType AND itemLocalId = :itemLocalId
    """)
    suspend fun removeItem(listLocalId: Long, itemType: String, itemLocalId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addTombstone(tombstone: ListItemTombstoneEntity)

    @Query("SELECT * FROM list_item_tombstones WHERE listLocalId = :listLocalId")
    suspend fun getTombstones(listLocalId: Long): List<ListItemTombstoneEntity>

    @Query("DELETE FROM list_item_tombstones WHERE listLocalId = :listLocalId")
    suspend fun clearTombstones(listLocalId: Long)

    /**
     * Item entfernen und den Merker setzen, falls es bereits auf dem Server
     * bekannt war. Ohne Merker holte der nächste Abgleich es zurück.
     */
    @Transaction
    suspend fun removeItemTracked(
        listLocalId: Long,
        itemType: String,
        itemLocalId: Long,
        itemRemoteId: Int?,
        now: String
    ) {
        removeItem(listLocalId, itemType, itemLocalId)
        if (itemRemoteId != null) {
            addTombstone(ListItemTombstoneEntity(listLocalId, itemType, itemRemoteId, now))
        }
    }
}

@Dao
interface SeriesDao {

    @Query("SELECT * FROM seasons WHERE movieLocalId = :movieLocalId ORDER BY seasonNumber")
    suspend fun getSeasons(movieLocalId: Long): List<SeasonEntity>

    @Query("SELECT localId FROM seasons WHERE movieLocalId = :movieLocalId AND seasonNumber = :number LIMIT 1")
    suspend fun findSeasonLocalId(movieLocalId: Long, number: Int): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSeason(season: SeasonEntity): Long

    @Query("SELECT * FROM episodes WHERE seasonLocalId = :seasonLocalId ORDER BY episodeNumber")
    suspend fun getEpisodes(seasonLocalId: Long): List<EpisodeEntity>

    @Query("SELECT localId FROM episodes WHERE seasonLocalId = :seasonLocalId AND episodeNumber = :number LIMIT 1")
    suspend fun findEpisodeLocalId(seasonLocalId: Long, number: Int): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEpisode(episode: EpisodeEntity): Long

    @Query("DELETE FROM seasons WHERE movieLocalId = :movieLocalId")
    suspend fun deleteSeasonsOf(movieLocalId: Long)

    /**
     * Staffeln entfernen, die der Server nicht mehr kennt.
     *
     * Gerichtetes Spiegeln wie in der Desktop-App: beim Pull bestimmt die
     * Shelf, welche Staffeln es gibt. Ohne diesen Schritt bliebe eine dort
     * entfernte Staffel lokal fuer immer stehen.
     */
    @Query("DELETE FROM seasons WHERE movieLocalId = :movieLocalId AND seasonNumber NOT IN (:keep)")
    suspend fun pruneSeasons(movieLocalId: Long, keep: List<Int>)

    /**
     * Staffeln und Episoden einer Serie einspielen. Vorhandene Nummern werden
     * auf ihre bestehende Zeile gehoben — ein wiederholter Import darf keine
     * zweite Folge 1 anlegen.
     */
    @Transaction
    suspend fun upsertSeries(movieLocalId: Long, seasons: List<SeasonWithEpisodes>) {
        for (entry in seasons) {
            val existingSeasonId = findSeasonLocalId(movieLocalId, entry.season.seasonNumber)
            val seasonId = insertSeason(
                entry.season.copy(
                    localId = existingSeasonId ?: 0,
                    movieLocalId = movieLocalId
                )
            ).let { if (existingSeasonId != null) existingSeasonId else it }

            for (episode in entry.episodes) {
                val existingEpisodeId = findEpisodeLocalId(seasonId, episode.episodeNumber)
                insertEpisode(
                    episode.copy(
                        localId = existingEpisodeId ?: 0,
                        seasonLocalId = seasonId
                    )
                )
            }
        }
    }
}

/** Staffel samt Folgen — Übergabeform für [SeriesDao.upsertSeries]. */
data class SeasonWithEpisodes(
    val season: SeasonEntity,
    val episodes: List<EpisodeEntity>
)

@Dao
interface ExternalMovieDao {

    @Query("SELECT * FROM external_movies WHERE localId = :localId LIMIT 1")
    suspend fun getByLocalId(localId: Long): ExternalMovieEntity?

    @Query("SELECT localId FROM external_movies WHERE remoteId = :remoteId LIMIT 1")
    suspend fun findLocalIdByRemoteId(remoteId: Int): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(movie: ExternalMovieEntity): Long

    @Query("SELECT * FROM external_movies WHERE syncedAt IS NULL OR updatedAt > syncedAt")
    suspend fun getDirty(): List<ExternalMovieEntity>

    @Query("DELETE FROM external_movies WHERE localId = :localId")
    suspend fun delete(localId: Long)
}

@Dao
interface SettingDao {

    @Query("SELECT value FROM settings WHERE key = :key LIMIT 1")
    suspend fun get(key: String): String?

    /**
     * Beobachtbare Variante fuer Werte, an denen die Oberflaeche haengt —
     * allen voran der Betriebsmodus, der ueber den Startbildschirm entscheidet.
     */
    @Query("SELECT value FROM settings WHERE key = :key LIMIT 1")
    fun observe(key: String): kotlinx.coroutines.flow.Flow<String?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(setting: SettingEntity)

    suspend fun put(key: String, value: String?) = put(SettingEntity(key, value))

    @Query("DELETE FROM settings WHERE key = :key")
    suspend fun remove(key: String)
}
