package at.neuhaus.movieshelf.data.repository

import at.neuhaus.movieshelf.data.api.MovieShelfApi
import at.neuhaus.movieshelf.data.local.db.ActorDao
import at.neuhaus.movieshelf.data.model.ApiEpisode
import at.neuhaus.movieshelf.data.model.ApiSeason
import at.neuhaus.movieshelf.data.local.db.ActorEntity
import at.neuhaus.movieshelf.data.local.db.FilmActorCrossRef
import at.neuhaus.movieshelf.data.local.db.MovieDao
import at.neuhaus.movieshelf.data.local.db.SeriesDao
import at.neuhaus.movieshelf.data.local.db.MovieEntity
import at.neuhaus.movieshelf.data.local.ArtworkKind
import at.neuhaus.movieshelf.data.local.ImageDownloader
import at.neuhaus.movieshelf.data.local.LocalStats
import at.neuhaus.movieshelf.data.local.MediaHosts
import at.neuhaus.movieshelf.data.local.MediaStore
import at.neuhaus.movieshelf.data.local.db.PendingUploadDao
import at.neuhaus.movieshelf.data.local.db.PendingUploadEntity
import at.neuhaus.movieshelf.data.local.db.SyncClock
import at.neuhaus.movieshelf.data.local.db.UploadKind
import at.neuhaus.movieshelf.data.model.Movie
import at.neuhaus.movieshelf.data.model.MovieUpdateRequest
import at.neuhaus.movieshelf.data.model.Stats
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody

/** Ein Darsteller, wie ihn TMDb liefert — Zwischenform fuer [MovieRepository.setCast]. */
data class LocalCastMember(
    val name: String,
    val role: String? = null,
    val imageUrl: String? = null,
    val tmdbId: Int? = null
)

private const val CACHE_MAX_AGE_MS = 30 * 60 * 1000L // 30 Minuten

class MovieRepository(
    private val movieDao: MovieDao,
    private val actorDao: ActorDao,
    private val seriesDao: SeriesDao,
    private val pendingUploadDao: PendingUploadDao,
    private val mediaStore: MediaStore,
    private val imageDownloader: ImageDownloader,
    /** Basisadresse der Shelf — entscheidet ueber Freigabe und Anmeldung. */
    private val shelfUrlProvider: () -> String?,
    /**
     * Ob die App an eine Shelf gebunden ist. Im eigenstaendigen Betrieb
     * unterbleibt jeder Netzaufruf — nicht nur, weil er scheitern wuerde,
     * sondern weil ein Fehlschlag sonst faelschlich als "offline" gilt.
     */
    private val isShelfMode: suspend () -> Boolean = { true },
    // Provider statt fester Instanz: so wird nach einem Server-Wechsel
    // (RetrofitClient.initialize) immer die aktuelle API benutzt.
    private val apiProvider: () -> MovieShelfApi
) {
    private val api: MovieShelfApi get() = apiProvider()

    var isOffline: Boolean = false
        private set

    /**
     * Die Sammlung lesen — ausschliesslich aus der lokalen Datenbank.
     *
     * Frueher holte dieser Aufruf bei jedem Oeffnen den Serverstand und
     * spiegelte ihn. Das war zweifach falsch: es erzeugte Verkehr, den niemand
     * angefordert hat, und die Spiegelung loeschte jede synchronisierte Zeile,
     * die nicht auf der ersten Seite stand — die Folgeseiten legten sie mit
     * neuen lokalen IDs wieder an, wodurch heruntergeladene Bilder verwaisten.
     *
     * Daten kommen jetzt nur noch ueber den Abgleich herein, und der laeuft auf
     * Knopfdruck oder im Hintergrund. Genauso macht es die Desktop-App.
     */
    suspend fun getMovies(page: Int = 1, perPage: Int = 30, tag: String? = null): List<Movie> {
        if (tag == "new") return movieDao.getNewest(perPage).map { it.toMovie() }
        val local = movieDao.getAllMovies().map { it.toMovie() }
        return if (page <= 1) local.take(perPage) else local.drop((page - 1) * perPage).take(perPage)
    }

    /** Suche in der lokalen Sammlung. */
    suspend fun searchMovies(query: String): List<Movie> =
        movieDao.searchMovies(query).map { it.toMovie() }

    // ── Lokale Identität ─────────────────────────────────────────────────────
    // Ab hier arbeitet die Oberfläche mit lokalen IDs. Die Server-ID wird erst
    // unmittelbar vor dem Netzaufruf nachgeschlagen; fehlt sie, existiert die
    // Zeile nur lokal und der Aufruf entfällt (statt gegen ID 0 zu laufen).

    /**
     * Film ueber seine lokale ID lesen. Kein Netzaufruf: der angezeigte Stand
     * ist der, den der letzte Abgleich hinterlassen hat.
     */
    suspend fun getMovieByLocalId(localId: Long): Movie? =
        movieDao.getByLocalId(localId)?.let { withLocalDetails(it) }

    /**
     * Besetzung und Staffeln aus den lokalen Tabellen nachreichen.
     *
     * Filme von der Shelf bringen ihre Darsteller als JSON mit; lokal
     * angelegte haben dort nichts stehen und beziehen sie ueber `film_actor`.
     * Staffeln und Episoden stehen dagegen immer in eigenen Tabellen — ohne
     * diesen Schritt kaemen sie zwar beim Abgleich herein, waeren in der
     * Detailansicht aber nirgends zu sehen.
     */
    private suspend fun withLocalDetails(entity: MovieEntity): Movie {
        var movie = entity.toMovie()

        if (movie.actors.isNullOrEmpty()) {
            val cast = actorDao.getCastOf(entity.localId)
            if (cast.isNotEmpty()) {
                movie = movie.copy(
                    actors = cast.map { actor ->
                        at.neuhaus.movieshelf.data.model.Actor(
                            id = actor.remoteId,
                            name = actor.name,
                            imageUrl = actor.imagePath,
                            biography = actor.bio,
                            birthDate = actor.birthday,
                            placeOfBirth = actor.placeOfBirth
                        )
                    }
                )
            }
        }

        if (movie.collectionType == "Serie") {
            movie = movie.copy(seasons = localSeasons(entity.localId))
        }
        return movie
    }

    /** Staffeln samt Episoden einer Serie aus der lokalen Datenbank. */
    private suspend fun localSeasons(movieLocalId: Long): List<ApiSeason> =
        seriesDao.getSeasons(movieLocalId).map { season ->
            ApiSeason(
                // Ohne Server-ID eine eigene, negative Kennung: die Ansicht
                // benutzt sie nur zum Auseinanderhalten der Staffeln und darf
                // dabei nicht mit echten Server-IDs kollidieren.
                id = season.remoteId ?: -season.localId.toInt(),
                seasonNumber = season.seasonNumber,
                title = season.title,
                overview = season.overview,
                episodes = seriesDao.getEpisodes(season.localId).map { episode ->
                    ApiEpisode(
                        id = episode.remoteId ?: -episode.localId.toInt(),
                        episodeNumber = episode.episodeNumber,
                        title = episode.title,
                        overview = episode.overview
                    )
                }
            )
        }

    /**
     * Besetzung aus der Server-Antwort uebernehmen.
     *
     * Darsteller werden ueber ihre Server-ID zusammengefuehrt, ersatzweise
     * ueber den Namen — sonst entstuende dieselbe Person bei jedem Abgleich
     * erneut. Die Bild-Adresse landet zunaechst als Adresse in der Zeile und
     * wird beim Medien-Durchgang durch die heruntergeladene Datei ersetzt.
     */
    suspend fun saveServerCast(movieLocalId: Long, actors: List<at.neuhaus.movieshelf.data.model.Actor>) {
        val now = SyncClock.now()
        val refs = actors.mapIndexedNotNull { index, actor ->
            val name = actor.name?.takeIf { it.isNotBlank() } ?: return@mapIndexedNotNull null
            val existing = actor.id?.let { actorDao.findLocalIdByRemoteId(it) }
                ?: actorDao.findLocalIdByName(name)
            val entity = ActorEntity(
                localId = existing ?: 0,
                remoteId = actor.id,
                name = name,
                bio = actor.biography,
                birthday = actor.birthDate,
                placeOfBirth = actor.placeOfBirth,
                // Einen bereits heruntergeladenen Pfad nicht durch die Adresse
                // ersetzen, sonst laedt jeder Abgleich das Bild erneut.
                imagePath = existing
                    ?.let { actorDao.getByLocalId(it)?.imagePath }
                    ?.takeIf { mediaStore.isLocalPath(it) }
                    ?: actor.imageUrl,
                updatedAt = now,
                syncedAt = now
            )
            // Aktualisieren statt ersetzen, sonst faellt die Person aus allen
            // anderen Filmen heraus (CASCADE auf film_actor).
            val actorLocalId = if (existing == null) actorDao.insert(entity) else {
                actorDao.update(entity)
                existing
            }
            FilmActorCrossRef(
                movieLocalId = movieLocalId,
                actorLocalId = actorLocalId,
                role = actor.role,
                isMainRole = actor.isMainRole ?: (index < 3),
                sortOrder = index
            )
        }
        actorDao.replaceCast(movieLocalId, refs)
    }

    /** Besetzung eines lokal angelegten Films setzen. */
    suspend fun setCast(localId: Long, cast: List<LocalCastMember>) {
        val now = SyncClock.now()
        val refs = cast.mapIndexedNotNull { index, member ->
            val existing = actorDao.findLocalIdByName(member.name)
            val actorLocalId = existing ?: actorDao.insert(
                ActorEntity(
                    name = member.name,
                    imagePath = member.imageUrl,
                    tmdbId = member.tmdbId,
                    createdAt = now,
                    updatedAt = now
                )
            )
            FilmActorCrossRef(
                movieLocalId = localId,
                actorLocalId = actorLocalId,
                role = member.role,
                isMainRole = index < 3,
                sortOrder = index
            )
        }
        actorDao.replaceCast(localId, refs)
    }

    suspend fun getRemoteId(localId: Long): Int? = movieDao.getByLocalId(localId)?.remoteId

    /** Lokale ID zu einer Server-ID, etwa nach einer Suche über das Netz. */
    suspend fun getLocalId(remoteId: Int): Long? = movieDao.findLocalIdByRemoteId(remoteId)

    /**
     * Gesehen-Status umschalten. Bewusst ohne Rueckabwicklung bei Netzfehlern:
     * die lokale Aenderung ist gueltig und wartet als abweichende Zeile auf
     * ihren Push.
     */
    suspend fun toggleWatchedByLocalId(localId: Long, currentState: Boolean) {
        val entity = movieDao.getByLocalId(localId) ?: return
        val now = SyncClock.now()
        movieDao.updateWatched(localId, !currentState, now)

        val remoteId = entity.remoteId ?: return
        if (!isShelfMode()) return
        try {
            api.toggleWatched(remoteId)
            movieDao.markSynced(localId, now)
            isOffline = false
        } catch (e: Exception) {
            isOffline = true
        }
    }

    // ── Schreiben: erst lokal, dann übertragen ───────────────────────────────
    // Jede Änderung landet zuerst in der Datenbank und gilt damit als
    // abweichend. Der Netzaufruf ist nur noch der Versuch, sie loszuwerden —
    // schlägt er fehl, bleibt die Änderung erhalten und wartet auf den nächsten
    // Abgleich, statt wie bisher verloren zu gehen.

    /**
     * Film bearbeiten. Gibt den lokalen Stand zurück, auch wenn der Server
     * gerade nicht erreichbar war.
     */
    suspend fun updateMovieByLocalId(localId: Long, request: MovieUpdateRequest): Movie? {
        val existing = movieDao.getByLocalId(localId) ?: return null
        val now = SyncClock.now()
        movieDao.update(existing.withRequest(request, now))

        val remoteId = existing.remoteId
        if (remoteId != null && isShelfMode()) {
            try {
                api.updateMovie(remoteId, request)
                movieDao.markSynced(localId, now)
                isOffline = false
            } catch (e: Exception) {
                // Bleibt abweichend und geht beim nächsten Abgleich raus.
                isOffline = true
            }
        }
        return movieDao.getByLocalId(localId)?.toMovie()
    }

    /**
     * Film löschen. Eine Zeile, die der Server kennt, wird zunächst nur als
     * gelöscht vorgemerkt — endgültig entfernt wird sie erst, wenn der Server
     * die Löschung bestätigt hat. Sonst wüsste nach einem Fehlschlag niemand
     * mehr, dass dort etwas zu löschen war.
     */
    suspend fun deleteMovieByLocalId(localId: Long) {
        val existing = movieDao.getByLocalId(localId) ?: return
        val remoteId = existing.remoteId
        // Ohne Server-ID oder ohne Shelf gibt es nichts zu melden — die Zeile
        // kann sofort weg statt als Grabstein liegen zu bleiben.
        if (remoteId == null || !isShelfMode()) {
            mediaStore.deleteArtworkOf(localId)
            movieDao.hardDelete(localId)
            return
        }
        movieDao.markDeleted(localId, SyncClock.now())
        try {
            api.deleteMovie(remoteId)
            mediaStore.deleteArtworkOf(localId)
            movieDao.hardDelete(localId)
            isOffline = false
        } catch (e: retrofit2.HttpException) {
            // Auf der Shelf schon weg: Ziel erreicht, also lokal ebenfalls raus.
            // Sonst bliebe die Zeile fuer immer als abweichend liegen.
            if (e.code() == 404) {
                mediaStore.deleteArtworkOf(localId)
                movieDao.hardDelete(localId)
            } else {
                isOffline = true
            }
        } catch (e: Exception) {
            isOffline = true
        }
    }

    /**
     * Film anlegen. Die lokale Zeile entsteht sofort und wird zurückgegeben,
     * damit die Oberfläche auch ohne Netz weiterarbeiten kann; der Server holt
     * sie sich beim nächsten Abgleich.
     */
    suspend fun createMovie(request: MovieUpdateRequest): Long? {
        val now = SyncClock.now()
        val localId = movieDao.insert(MovieEntity.fromRequest(request, now))
        if (!isShelfMode()) return localId

        try {
            val created = api.createMovie(request).data
            if (created != null) {
                // Server-ID nachtragen und als übertragen stempeln, statt die
                // Antwort als neue Zeile einzuspielen — sonst stünde der Film
                // doppelt da.
                movieDao.markSynced(localId, now, created.id)
                movieDao.upsertFromServer(listOf(MovieEntity.fromServerMovie(created, now)))
            }
            isOffline = false
        } catch (e: Exception) {
            isOffline = true
        }
        return localId
    }

    /** Film bearbeiten (Admin). Schreibt das Ergebnis in die lokale Sammlung. */
    suspend fun updateMovie(id: Int, request: MovieUpdateRequest): Movie? {
        val response = api.updateMovie(id, request)
        return response.data?.also {
            movieDao.upsertFromServer(listOf(MovieEntity.fromServerMovie(it, SyncClock.now())))
        }
    }

    /** Film löschen (Admin). Entfernt ihn bei Erfolg auch lokal. */
    suspend fun deleteMovie(id: Int) {
        api.deleteMovie(id)
        movieDao.findLocalIdByRemoteId(id)?.let { movieDao.hardDelete(it) }
    }

    // ── Aktionen am einzelnen Film ───────────────────────────────────────────
    // Noch reine Netzaufrufe. Sie liegen hier statt in den ViewModels, damit
    // Phase 3 sie an einer Stelle auf lokales Schreiben umstellen kann.

    suspend fun toggleWishlist(remoteId: Int): Boolean? =
        api.toggleWishlist(remoteId).wishlisted

    suspend fun fetchTrailer(remoteId: Int): String? {
        val response = api.fetchTrailer(remoteId)
        return if (response.found == true) response.trailerUrl else null
    }

    suspend fun getTmdbTvDetails(tmdbId: Int) = api.getTmdbTvDetails(tmdbId)

    suspend fun importSeasons(remoteId: Int, seasons: List<Int>) {
        api.importSeasons(at.neuhaus.movieshelf.data.model.SeasonImportRequest(remoteId, seasons))
    }

    suspend fun removeSeasons(remoteId: Int, seasons: List<Int>) {
        api.removeSeasons(at.neuhaus.movieshelf.data.model.SeasonImportRequest(remoteId, seasons))
    }

    /**
     * Statistik. Wird lokal gerechnet — die Zahlen liegen vollstaendig in der
     * Datenbank, und ohne Shelf gibt es den Endpunkt gar nicht.
     */
    suspend fun getStats(): Stats = LocalStats.from(movieDao.getAllForStats())

    suspend fun searchTmdb(query: String) = api.searchTmdb(query)

    suspend fun importFromTmdb(tmdbId: Int, inCollection: Boolean) {
        api.importFromTmdb(
            at.neuhaus.movieshelf.data.model.TmdbImportRequest(
                tmdbId = tmdbId,
                type = "movie",
                inCollection = inCollection
            )
        )
    }

    /**
     * Bild setzen.
     *
     * Die Datei wird zuerst lokal abgelegt und sofort als Bildquelle des Films
     * eingetragen — so ist die Auswahl unmittelbar sichtbar, auch ohne Netz.
     * Erst danach wird der Upload versucht; gelingt er nicht, bleibt er als
     * [PendingUploadEntity] vorgemerkt und geht beim nächsten Abgleich raus.
     *
     * @return sichtbare Bildquelle: die Server-URL nach erfolgreichem Upload,
     *   sonst der lokale Dateipfad.
     */
    suspend fun setMovieImage(
        localId: Long,
        bytes: ByteArray,
        mimeType: String,
        kind: String
    ): String? {
        val movie = movieDao.getByLocalId(localId) ?: return null
        val now = SyncClock.now()

        val file = mediaStore.savePending(localId, kind, bytes, mimeType)
        val isCover = kind == UploadKind.COVER
        if (isCover) movieDao.updateCoverUrl(localId, file.absolutePath, now)
        else movieDao.updateBackdropUrl(localId, file.absolutePath, now)

        pendingUploadDao.put(
            PendingUploadEntity(
                movieLocalId = localId,
                kind = kind,
                filePath = file.absolutePath,
                mimeType = mimeType,
                createdAt = now
            )
        )

        val remoteId = movie.remoteId?.takeIf { isShelfMode() } ?: return file.absolutePath
        return try {
            val part = imagePart(bytes, mimeType, kind)
            val url = if (isCover) uploadCover(remoteId, part) else uploadBackdrop(remoteId, part)
            if (url != null) {
                if (isCover) movieDao.updateCoverUrl(localId, url, now)
                else movieDao.updateBackdropUrl(localId, url, now)
                movieDao.markSynced(localId, now)
            }
            pendingUploadDao.remove(localId, kind)
            mediaStore.delete(file.absolutePath)
            isOffline = false
            url ?: file.absolutePath
        } catch (e: Exception) {
            isOffline = true
            file.absolutePath
        }
    }

    /**
     * Bild-Adressen setzen, ohne etwas hochzuladen — fuer Filme, die aus TMDb
     * uebernommen wurden und deren Bilder dort liegen bleiben.
     */
    suspend fun setImageUrls(localId: Long, coverUrl: String?, backdropUrl: String?) {
        val now = SyncClock.now()
        if (coverUrl != null) movieDao.updateCoverUrl(localId, coverUrl, now)
        if (backdropUrl != null) movieDao.updateBackdropUrl(localId, backdropUrl, now)
        // Sofort holen, damit der Film gleich ein Bild hat und nicht erst nach
        // dem naechsten Abgleich.
        downloadMissingArtwork()
    }

    /**
     * Fehlende Bilder herunterladen.
     *
     * Betrifft beide Betriebsarten: Cover der Shelf genauso wie die von TMDb
     * uebernommenen. Die Adresse in [MovieEntity.coverUrl] bleibt stehen — sie
     * ist der Stand, den Server und TMDb kennen; die Datei tritt daneben und
     * wird angezeigt. Wuerde die Adresse ersetzt, holte der naechste Abgleich
     * sie zurueck und der Download begaenne von vorn.
     *
     * @return wie viele Bilder neu abgelegt wurden.
     */
    suspend fun downloadMissingArtwork(
        /**
         * Fortschritt: erledigt, gesamt, gerade bearbeiteter Titel.
         *
         * Ohne Gesamtzahl kann die Oberflaeche nur einen wandernden Balken
         * zeigen - und diese Phase dauert bei tausend Bildern am laengsten.
         */
        onProgress: (Int, Int, String?) -> Unit = { _, _, _ -> }
    ): Int {
        val shelfUrl = shelfUrlProvider()
        var stored = 0

        val movies = movieDao.getMoviesMissingArtwork()
        val actors = actorDao.getActorsMissingArtwork()

        // Vorab zaehlen, was tatsaechlich anfaellt: ein Film ohne Backdrop
        // ergibt einen Schritt, nicht zwei.
        val total = movies.sumOf { entity ->
            (if (entity.coverUrl.isNullOrBlank()) 0 else 1) +
                (if (entity.backdropUrl.isNullOrBlank()) 0 else 1)
        } + actors.size
        var done = 0

        for (entity in movies) {
            if (!entity.coverUrl.isNullOrBlank()) {
                if (fetchArtwork(entity.localId, entity.coverUrl, ArtworkKind.COVER, shelfUrl)) stored++
                onProgress(++done, total, entity.title)
            }
            if (!entity.backdropUrl.isNullOrBlank()) {
                if (fetchArtwork(entity.localId, entity.backdropUrl, ArtworkKind.BACKDROP, shelfUrl)) stored++
                onProgress(++done, total, entity.title)
            }
        }

        // Darstellerbilder ebenso — die Desktop-App laedt sie mit.
        for (actor in actors) {
            if (fetchArtwork(actor.localId, actor.imagePath, ArtworkKind.ACTOR, shelfUrl)) stored++
            onProgress(++done, total, actor.name)
        }
        return stored
    }

    private suspend fun fetchArtwork(
        localId: Long,
        rawUrl: String?,
        kind: String,
        shelfUrl: String?
    ): Boolean {
        val url = absoluteUrl(rawUrl, shelfUrl) ?: return false
        if (!MediaHosts.isAllowed(url, shelfUrl)) return false

        val (bytes, mimeType) = imageDownloader.download(
            url = url,
            authenticated = MediaHosts.needsShelfAuth(url, shelfUrl)
        ) ?: return false

        val file = mediaStore.saveArtwork(localId, kind, bytes, mimeType) ?: return false
        // Die Adresse wird durch den Dateipfad ersetzt, wie `cover_path` in der
        // Desktop-App. Damit kann sie nicht zum Rueckfall werden.
        //
        // Bewusst ohne updatedAt: das Bild liegt jetzt lokal, inhaltlich hat
        // sich nichts geaendert. Wuerde die Zeile dadurch als abweichend
        // gelten, schoebe der naechste Lauf die ganze Sammlung zurueck.
        when (kind) {
            ArtworkKind.COVER -> movieDao.setCoverPath(localId, file.absolutePath)
            ArtworkKind.BACKDROP -> movieDao.setBackdropPath(localId, file.absolutePath)
            ArtworkKind.ACTOR -> actorDao.updateImagePath(localId, file.absolutePath)
        }
        return true
    }

    /**
     * Relative Shelf-Pfade zu einer vollstaendigen Adresse ergaenzen; alles
     * andere unveraendert lassen. Bereits lokale Pfade sind keine Adressen.
     */
    private fun absoluteUrl(rawUrl: String?, shelfUrl: String?): String? {
        val trimmed = rawUrl?.trim()?.takeIf { it.isNotBlank() } ?: return null
        // Bereits eine lokale Datei: nichts zu holen.
        if (mediaStore.isLocalPath(trimmed)) return null
        if (trimmed.startsWith("/")) {
            val base = shelfUrl?.trimEnd('/') ?: return null
            return base + trimmed
        }
        return trimmed.takeIf { it.startsWith("http") }
    }

    /**
     * Bildablage aufraeumen: Dateien ohne zugehoerige Zeile entfernen.
     *
     * Laeuft nach dem Abgleich, weil erst dann feststeht, welche Zeilen es
     * wirklich noch gibt — vorher koennte eine gerade geloeschte Zeile beim
     * naechsten Pull zurueckkommen.
     */
    suspend fun cleanupOrphanedArtwork(): Int = mediaStore.removeOrphans(
        movieLocalIds = movieDao.getAllLocalIds().toSet(),
        actorLocalIds = actorDao.getAllLocalIds().toSet()
    )

    /**
     * Bilder eines lokal angelegten Films zum Hochladen vormerken.
     *
     * Wird nach dem ersten erfolgreichen Push aufgerufen: der Film ist dann in
     * der Shelf, sein Cover aber noch nicht — ohne diesen Schritt bliebe er
     * dort ohne Bild stehen.
     */
    suspend fun queueArtworkUpload(localId: Long) {
        val now = SyncClock.now()
        for (kind in listOf(ArtworkKind.COVER, ArtworkKind.BACKDROP)) {
            val file = mediaStore.artworkFile(localId, kind) ?: continue
            pendingUploadDao.put(
                PendingUploadEntity(
                    movieLocalId = localId,
                    kind = kind,
                    filePath = file.absolutePath,
                    mimeType = if (file.extension == "png") "image/png" else "image/jpeg",
                    createdAt = now
                )
            )
        }
    }

    /** Vorgemerkte Bilder nachreichen. Wird ab Phase 4 vom Abgleich aufgerufen. */
    suspend fun flushPendingUploads(
        onProgress: (Int, Int, String?) -> Unit = { _, _, _ -> }
    ) {
        if (!isShelfMode()) return
        val queued = pendingUploadDao.getAll()
        var done = 0
        for (pending in queued) {
            onProgress(++done, queued.size, null)
            val remoteId = movieDao.getByLocalId(pending.movieLocalId)?.remoteId ?: continue
            val file = java.io.File(pending.filePath)
            if (!file.exists()) {
                pendingUploadDao.remove(pending.movieLocalId, pending.kind)
                continue
            }
            try {
                val part = imagePart(file.readBytes(), pending.mimeType, pending.kind)
                val url = if (pending.kind == UploadKind.COVER) uploadCover(remoteId, part)
                else uploadBackdrop(remoteId, part)
                if (url != null) {
                    val now = SyncClock.now()
                    if (pending.kind == UploadKind.COVER) movieDao.updateCoverUrl(pending.movieLocalId, url, now)
                    else movieDao.updateBackdropUrl(pending.movieLocalId, url, now)
                    movieDao.markSynced(pending.movieLocalId, now)
                }
                pendingUploadDao.remove(pending.movieLocalId, pending.kind)
                // Nur die Vormerk-Datei entfernen. Zeigt der Eintrag auf die
                // dauerhafte Ablage (nach einem Push aus dem eigenstaendigen
                // Betrieb), bleibt sie liegen — sie ist das angezeigte Bild.
                if (mediaStore.artworkFile(pending.movieLocalId, pending.kind)?.absolutePath != pending.filePath) {
                    mediaStore.delete(pending.filePath)
                }
            } catch (e: Exception) {
                // Bleibt vorgemerkt; der nächste Anlauf versucht es erneut.
                isOffline = true
            }
        }
    }

    private fun imagePart(bytes: ByteArray, mimeType: String, kind: String): okhttp3.MultipartBody.Part {
        val body = bytes.toRequestBody(mimeType.toMediaTypeOrNull())
        val extension = if (mimeType.contains("png")) "png" else "jpg"
        return okhttp3.MultipartBody.Part.createFormData(kind, "$kind.$extension", body)
    }

    /** Cover hochladen (Admin). Gibt die neue Cover-URL zurück. */
    suspend fun uploadCover(id: Int, part: okhttp3.MultipartBody.Part): String? =
        api.uploadCover(id, part).coverUrl

    /** Backdrop hochladen (Admin). Gibt die neue Backdrop-URL zurück. */
    suspend fun uploadBackdrop(id: Int, part: okhttp3.MultipartBody.Part): String? =
        api.uploadBackdrop(id, part).backdropUrl

    /** Einzelnen Film laden — aus Cache wenn offline. */
    suspend fun getMovie(id: Int): Movie? {
        return try {
            val response = api.getMovie(id)
            response.data?.also { movie ->
                movieDao.upsertFromServer(listOf(MovieEntity.fromServerMovie(movie, SyncClock.now())))
            }
        } catch (e: Exception) {
            movieDao.getByRemoteId(id)?.toMovie()
        }
    }

    suspend fun getCachedMovies(): List<Movie> =
        movieDao.getAllMovies().map { it.toMovie() }

    suspend fun isCacheAvailable(): Boolean =
        movieDao.getMovieCount() > 0

}
