package info.movieshelf.data.jellyfin

import info.movieshelf.data.api.TmdbApi
import info.movieshelf.data.api.TmdbMovieDetails
import info.movieshelf.data.local.ArtworkKind
import info.movieshelf.data.local.MediaStore
import info.movieshelf.data.local.db.ActorDao
import info.movieshelf.data.local.db.EpisodeEntity
import info.movieshelf.data.local.db.MovieDao
import info.movieshelf.data.local.db.MovieEntity
import info.movieshelf.data.local.db.SeasonEntity
import info.movieshelf.data.local.db.SeasonWithEpisodes
import info.movieshelf.data.local.db.SeriesDao
import info.movieshelf.data.local.db.SyncClock
import info.movieshelf.data.repository.LocalCastMember

/**
 * Übernimmt Bibliotheken eines Jellyfin-Servers in die Sammlung.
 *
 * Geschrieben wird ausschließlich lokal — auch im Shelf-Modus. Die neuen Zeilen
 * tragen kein `syncedAt` und gelten damit als abweichend; der nächste Abgleich
 * unter „Synchronisieren" schiebt sie zum Server. Genauso hält es die
 * Desktop-App, und es hat einen praktischen Grund: ein Import über tausend
 * Titel wäre als tausend einzelne Server-Aufrufe weder abbrechbar noch
 * wiederholbar.
 *
 * Fehler einzelner Titel beenden den Lauf nicht. Ein Server mit einem kaputten
 * Eintrag soll nicht die restlichen neunhundert kosten.
 */
class JellyfinImporter(
    private val client: JellyfinClient,
    private val movieDao: MovieDao,
    private val actorDao: ActorDao,
    private val seriesDao: SeriesDao,
    private val mediaStore: MediaStore,
    private val tmdbApi: TmdbApi,
    private val tmdbApiKeyProvider: () -> String?,
    private val setCast: suspend (Long, List<LocalCastMember>) -> Unit,
    private val downloadArtwork: suspend () -> Unit
) {

    /**
     * @param libraryIds ausgewählte Bibliotheken.
     * @param verifyWithTmdb Metadaten gegen TMDb prüfen, sofern ein Schlüssel
     *   hinterlegt ist.
     * @param onProgress wird zwischen den Titeln gerufen.
     */
    suspend fun import(
        session: JellyfinSession,
        deviceId: String,
        libraryIds: List<String>,
        verifyWithTmdb: Boolean,
        onProgress: (JellyfinProgress) -> Unit
    ): JellyfinImportResult {
        var imported = 0
        var skipped = 0
        var failed = 0
        val errors = mutableListOf<String>()

        onProgress(JellyfinProgress(JellyfinProgress.Phase.LIBRARIES))

        val collected = mutableListOf<JellyfinItem>()
        for (libraryId in libraryIds) {
            collected += client.items(session, deviceId, libraryId)
        }

        // Derselbe Titel kann in mehreren Bibliotheken liegen; innerhalb eines
        // Laufs zählt jede Jellyfin-ID nur einmal.
        val items = collected.distinctBy { it.id }

        val apiKey = tmdbApiKeyProvider()?.takeIf { verifyWithTmdb && it.isNotBlank() }

        items.forEachIndexed { index, item ->
            val now = SyncClock.now()
            var mapped = mapJellyfinItem(item, now)
            var details: TmdbMovieDetails? = null

            onProgress(
                JellyfinProgress(
                    phase = JellyfinProgress.Phase.ITEMS,
                    current = index + 1,
                    total = items.size,
                    title = mapped.title.orEmpty(),
                    imported = imported,
                    skipped = skipped,
                    failed = failed
                )
            )

            try {
                // Vor der Duplikatprüfung: der Abgleich kann eine TMDb-ID
                // nachliefern, über die ein vorhandener Titel überhaupt erst
                // als derselbe erkannt wird.
                if (apiKey != null) {
                    details = verifyAgainstTmdb(apiKey, mapped)
                    if (details != null) mapped = mergeTmdbDetails(mapped, details)
                }

                if (findDuplicate(mapped) != null) {
                    skipped++
                    return@forEachIndexed
                }

                val localId = movieDao.insert(mapped)

                downloadImages(session, deviceId, item, localId)

                // TMDb liefert Rollennamen und Profilbilder; ohne Treffer
                // bleibt Jellyfin die Quelle.
                val cast = castFromTmdb(details).ifEmpty { castFromJellyfin(item) }
                if (cast.isNotEmpty()) {
                    runCatching { setCast(localId, cast) }
                        .onFailure { errors += "${mapped.title}: ${it.message}" }
                    downloadJellyfinPortraits(session, deviceId, item, cast)
                }

                if (item.type == "Series") {
                    runCatching { importSeries(session, deviceId, item.id, localId) }
                        .onFailure { errors += "${mapped.title}: ${it.message}" }
                }

                imported++
            } catch (e: Exception) {
                failed++
                errors += "${mapped.title}: ${e.message ?: e::class.java.simpleName}"
            }
        }

        // Profilbilder von TMDb stehen als Adresse in der Zeile; der bestehende
        // Weg holt sie herunter und ersetzt sie durch den Dateipfad.
        runCatching { downloadArtwork() }

        onProgress(
            JellyfinProgress(
                phase = JellyfinProgress.Phase.DONE,
                current = items.size,
                total = items.size,
                imported = imported,
                skipped = skipped,
                failed = failed
            )
        )

        return JellyfinImportResult(imported, skipped, failed, errors)
    }

    /**
     * Ist der Titel schon da?
     *
     * Erst über die TMDb-ID, die eindeutig ist, sonst über Titel und Jahr.
     * Gelöschte Zeilen zählen mit: sie warten darauf, dass die Shelf die
     * Löschung bestätigt: würde der Import sie wieder aufnehmen, käme ein
     * bewusst entfernter Titel ungefragt zurück.
     */
    private suspend fun findDuplicate(candidate: MovieEntity): MovieEntity? {
        candidate.tmdbId?.takeIf { it.isNotBlank() }?.let { tmdbId ->
            movieDao.findByTmdbId(tmdbId)?.let { return it }
        }

        val wanted = normalizeTitle(candidate.title)
        if (wanted.isEmpty()) return null

        return movieDao.findByYear(candidate.year)
            .firstOrNull { normalizeTitle(it.title) == wanted }
    }

    /**
     * Metadaten gegen TMDb prüfen.
     *
     * Schlägt es fehl — kein Treffer, kein Netz, Kontingent erschöpft —, bleiben
     * die Jellyfin-Daten stehen und der Import läuft weiter.
     */
    private suspend fun verifyAgainstTmdb(apiKey: String, mapped: MovieEntity): TmdbMovieDetails? {
        val isSeries = mapped.collectionType == "Serie"
        val tmdbId = mapped.tmdbId?.toIntOrNull()

        return runCatching {
            if (tmdbId != null) {
                if (isSeries) seriesAsMovieDetails(tmdbId, apiKey) else tmdbApi.getMovie(tmdbId, apiKey)
            } else {
                findByTitle(apiKey, isSeries, mapped.title.orEmpty(), mapped.year)
            }
        }.getOrNull()
    }

    private suspend fun findByTitle(
        apiKey: String,
        isSeries: Boolean,
        title: String,
        year: Int?
    ): TmdbMovieDetails? {
        if (title.isBlank()) return null
        val results = if (isSeries) {
            tmdbApi.searchSeries(apiKey, title).results.orEmpty()
        } else {
            tmdbApi.searchMovies(apiKey, title).results.orEmpty()
        }

        val match = pickTmdbMatch(
            results = results,
            title = title,
            year = year,
            // TmdbSearchItem fasst Film und Serie zusammen: `title` traegt
            // auch `name`, `releaseDate` auch `first_air_date`.
            titleOf = { it.title },
            yearOf = { it.releaseDate?.take(4)?.toIntOrNull() }
        ) ?: return null

        val id = match.id ?: return null
        return if (isSeries) seriesAsMovieDetails(id, apiKey) else tmdbApi.getMovie(id, apiKey)
    }

    /**
     * Serien-Details in der Form der Filmdetails.
     *
     * Die beiden TMDb-Endpunkte unterscheiden sich nur in wenigen Feldnamen;
     * die Zusammenführung hier erspart dem restlichen Import eine
     * Fallunterscheidung an jeder Stelle.
     */
    private suspend fun seriesAsMovieDetails(id: Int, apiKey: String): TmdbMovieDetails {
        val tv = tmdbApi.getSeries(id, apiKey)
        return TmdbMovieDetails(
            id = tv.id,
            title = tv.name,
            overview = tv.overview,
            runtime = tv.episodeRunTime?.firstOrNull(),
            releaseDate = tv.firstAirDate,
            posterPath = tv.posterPath,
            backdropPath = tv.backdropPath,
            voteAverage = tv.voteAverage,
            genres = tv.genres,
            credits = tv.credits,
            videos = tv.videos
        )
    }

    /** Cover und Backdrop holen und als Dateipfad eintragen. */
    private suspend fun downloadImages(
        session: JellyfinSession,
        deviceId: String,
        item: JellyfinItem,
        localId: Long
    ) {
        if (item.imageTags?.containsKey("Primary") == true) {
            client.image(session, deviceId, item.id, "Primary", JellyfinClient.COVER_MAX_WIDTH)
                ?.let { (bytes, mime) ->
                    mediaStore.saveArtwork(localId, ArtworkKind.COVER, bytes, mime)
                        ?.let { movieDao.setCoverPath(localId, it.absolutePath) }
                }
        }
        if (!item.backdropImageTags.isNullOrEmpty()) {
            client.image(session, deviceId, item.id, "Backdrop", JellyfinClient.BACKDROP_MAX_WIDTH)
                ?.let { (bytes, mime) ->
                    mediaStore.saveArtwork(localId, ArtworkKind.BACKDROP, bytes, mime)
                        ?.let { movieDao.setBackdropPath(localId, it.absolutePath) }
                }
        }
    }

    /**
     * Portraits für Schauspieler, die Jellyfin kennt und TMDb nicht.
     *
     * Die Bilder von TMDb stehen als Adresse in der Zeile und werden am Ende
     * über den regulären Weg geholt; Jellyfins Portraits liegen hinter der
     * Anmeldung und müssen hier heruntergeladen werden. Nur für Personen ohne
     * Bild — ein wiederholter Lauf lädt sonst dieselben Portraits erneut.
     */
    private suspend fun downloadJellyfinPortraits(
        session: JellyfinSession,
        deviceId: String,
        item: JellyfinItem,
        cast: List<LocalCastMember>
    ) {
        val personIds = item.people.orEmpty()
            .filter { it.type == "Actor" && it.primaryImageTag != null && it.id != null }
            .associateBy({ normalizeTitle(it.name) }, { it.id!! })
        if (personIds.isEmpty()) return

        for (member in cast.filter { it.imageUrl == null }) {
            val personId = personIds[normalizeTitle(member.name)] ?: continue
            val actorLocalId = actorDao.findLocalIdByName(member.name) ?: continue
            if (!actorDao.getByLocalId(actorLocalId)?.imagePath.isNullOrBlank()) continue

            client.image(session, deviceId, personId, "Primary", JellyfinClient.PORTRAIT_MAX_WIDTH)
                ?.let { (bytes, mime) ->
                    mediaStore.saveArtwork(actorLocalId, ArtworkKind.ACTOR, bytes, mime)
                        ?.let { actorDao.updateImagePath(actorLocalId, it.absolutePath) }
                }
        }
    }

    /** Staffeln und Episoden einer Serie übernehmen. */
    private suspend fun importSeries(
        session: JellyfinSession,
        deviceId: String,
        seriesId: String,
        localId: Long
    ) {
        val now = SyncClock.now()
        val seasons = client.seasons(session, deviceId, seriesId)
        val episodes = client.episodes(session, deviceId, seriesId)

        val mapped = seasons
            // Staffel 0 sind die Specials; die Shelf führt sie nicht.
            .filter { (it.indexNumber ?: 0) > 0 }
            .map { season ->
                val number = season.indexNumber!!
                SeasonWithEpisodes(
                    season = SeasonEntity(
                        movieLocalId = localId,
                        seasonNumber = number,
                        title = season.name,
                        overview = season.overview?.takeIf { it.isNotBlank() },
                        createdAt = now,
                        updatedAt = now
                    ),
                    episodes = episodes
                        .filter { it.parentIndexNumber == number && it.indexNumber != null }
                        .map { episode ->
                            EpisodeEntity(
                                seasonLocalId = 0,
                                episodeNumber = episode.indexNumber!!,
                                title = episode.name,
                                overview = episode.overview?.takeIf { it.isNotBlank() },
                                createdAt = now,
                                updatedAt = now
                            )
                        }
                )
            }

        if (mapped.isNotEmpty()) seriesDao.upsertSeries(localId, mapped)
    }
}
