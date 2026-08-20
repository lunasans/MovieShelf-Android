package info.movieshelf.data.sync

import info.movieshelf.data.local.db.MovieDao
import info.movieshelf.data.local.db.MovieEntity
import info.movieshelf.data.local.db.SettingDao
import info.movieshelf.data.local.db.SettingKeys
import info.movieshelf.data.local.db.SeasonWithEpisodes
import info.movieshelf.data.local.db.SyncClock
import info.movieshelf.data.model.ExportResponse
import info.movieshelf.data.model.Movie
import info.movieshelf.data.model.MovieUpdateRequest
import info.movieshelf.data.model.SingleMovieResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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
    private val flushPendingUploads: suspend ((Int, Int, String?) -> Unit) -> Unit = {},
    /**
     * Offene "gesehen"-Markierungen zur Shelf bringen.
     *
     * Eigener Schritt, weil "gesehen" am Benutzer haengt und nicht am Film:
     * es hat einen eigenen Endpunkt und steht nicht in [MovieEntity.toUpdateRequest].
     */
    private val pushWatched: suspend ((Int, Int, String?) -> Unit) -> Int = { 0 },
    /** Offene eigene Bewertungen, eigener Endpunkt wie beim Gesehen-Stand. */
    private val pushUserRatings: suspend ((Int, Int, String?) -> Unit) -> Int = { 0 },
    /** Offene Folgen-Markierungen, eigener Endpunkt wie die uebrigen. */
    private val pushEpisodeWatched: suspend ((Int, Int, String?) -> Unit) -> Int = { 0 },
    /**
     * Staffeln und Episoden einer Serie einspielen. Wie [flushPendingUploads]
     * als Funktion hereingereicht, damit der Film-Abgleich nicht an der
     * Serien-Tabelle haengt.
     */
    private val upsertSeries: suspend (Long, List<SeasonWithEpisodes>) -> Unit = { _, _ -> },
    /**
     * Staffeln entfernen, die der Server nicht mehr kennt — gerichtetes
     * Spiegeln: beim Pull bestimmt die Shelf.
     */
    private val pruneSeasons: suspend (Long, List<Int>) -> Unit = { _, _ -> },
    /** Lokal vorhandene Staffelnummern einer Serie — fuer den TMDb-Import. */
    private val localSeasonNumbers: suspend (Long) -> List<Int> = { emptyList() },
    /** Besetzung eines Films aus der Server-Antwort uebernehmen. */
    private val upsertCast: suspend (Long, List<info.movieshelf.data.model.Actor>) -> Unit = { _, _ -> },
    /** Lokale Staffeln als "Nummer:Episodenzahl", sortiert - fuer die Vorschau. */
    private val localSeasonSignature: suspend (Long) -> List<String> = { emptyList() },
    /** Fehlende Bilder holen — laeuft in der Medien-Phase. */
    private val downloadMissingArtwork: suspend ((Int, Int, String?) -> Unit) -> Int = { 0 },
    /** Bilddateien ohne zugehoerige Zeile entfernen. */
    private val cleanupOrphanedArtwork: suspend () -> Int = { 0 },
    /**
     * Bilder eines neu hochgeladenen Films zum Upload vormerken. Ohne diesen
     * Schritt stuende ein eigenstaendig angelegter Film in der Shelf ohne
     * Cover, weil der Anlege-Aufruf nur Textfelder uebertraegt.
     */
    private val queueArtworkUpload: suspend (Long) -> Unit = {}
) {
    private val api: SyncApi get() = apiProvider()

    private suspend fun remoteSeasonNumbers(remoteId: Int): List<Int> = api.remoteSeasonNumbers(remoteId)

    private val _progress = MutableStateFlow<SyncProgress?>(null)

    /**
     * Fortschritt des laufenden Abgleichs, `null` wenn keiner laeuft.
     *
     * Als Flow statt als Rueckruf, weil der Abgleich die Oberflaeche
     * ueberleben soll: wer den Bildschirm dreht oder kurz woandershin
     * wechselt, findet den laufenden Abgleich wieder vor.
     */
    val progress: StateFlow<SyncProgress?> = _progress.asStateFlow()

    private fun onProgress(value: SyncProgress) {
        _progress.value = value
    }

    /**
     * Beide Richtungen: erst den Serverstand holen, dann lokale Aenderungen
     * hoch, dann die Bilder. Reihenfolge wie in der Desktop-App.
     *
     * Dass zuerst gezogen wird, kostet nichts: der Pull laesst abweichende
     * Zeilen unangetastet, und der anschliessende Push traegt sie nach. Lokale
     * Aenderungen gewinnen also in beiden Reihenfolgen — aber nur in dieser
     * sieht man den Serverstand, bevor man ihn ueberschreibt.
     */
    suspend fun runSync(full: Boolean = false): SyncResult {
        val pulled = pull(full)
        val pushed = push()
        val artwork = media()
        return finish(SyncResult(push = pushed, pull = pulled, artworkStored = artwork))
    }

    /**
     * Nur laden. Der Server bleibt unangetastet; lokale Aenderungen bleiben
     * liegen, bis jemand hochlaedt.
     */
    suspend fun runPullOnly(full: Boolean = false): SyncResult {
        val pulled = pull(full)
        val artwork = media()
        return finish(SyncResult(push = PushResult(), pull = pulled, artworkStored = artwork))
    }

    /**
     * Nur hochladen.
     *
     * Ruecke das Wasserzeichen dabei **nicht** vor — es steht fuer "bis hierhin
     * ist der Serverstand bekannt", und den hat dieser Lauf nicht geholt. Es
     * vorzuruecken hiesse, den naechsten Delta-Pull um alles zu bringen, was
     * inzwischen serverseitig passiert ist. Da [pull] das Wasserzeichen selbst
     * schreibt und hier nicht laeuft, ergibt sich das von allein.
     */
    suspend fun runPushOnly(): SyncResult {
        val pushed = push()
        onProgress(SyncProgress(SyncPhase.MEDIA))
        flushPendingUploads { current, total, subject ->
            onProgress(SyncProgress(SyncPhase.MEDIA, current, total, subject))
        }
        return finish(SyncResult(push = pushed, pull = PullResult()))
    }

    /** Bilder: erst hochladen, dann fehlende holen, dann verwaiste entfernen. */
    private suspend fun media(): Int {
        onProgress(SyncProgress(SyncPhase.MEDIA))
        // Ein gerade hochgeladenes Cover soll nicht im selben Durchgang noch
        // einmal geholt werden.
        flushPendingUploads { current, total, subject ->
            onProgress(SyncProgress(SyncPhase.MEDIA, current, total, subject))
        }
        val stored = downloadMissingArtwork { current, total, subject ->
            onProgress(SyncProgress(SyncPhase.MEDIA, current, total, subject))
        }
        cleanupOrphanedArtwork()
        return stored
    }

    private fun finish(result: SyncResult): SyncResult {
        onProgress(SyncProgress(SyncPhase.DONE))
        _progress.value = null
        return result
    }

    /**
     * Was ein Abgleich tun wuerde, ohne etwas zu tun.
     *
     * Holt den Serverstand und zaehlt beide Richtungen durch. Der Export wird
     * damit zweimal geholt — einmal fuer die Vorschau, einmal beim Anwenden.
     * Das ist der Preis dafuer, dass niemand Loeschungen bestaetigt, die er
     * nicht gesehen hat.
     */
    suspend fun preview(full: Boolean = false): SyncPreview {
        val dirty = movieDao.getDirtyMovies()
        val toCreate = dirty.count { !it.isDeleted && it.remoteId == null }
        val toUpdate = dirty.count { !it.isDeleted && it.remoteId != null }
        val toDeleteRemote = dirty.count { it.isDeleted }

        val since = if (full) null else settingDao.get(SettingKeys.LAST_SYNC_AT)
        val response = api.exportMovies(since)
        val movies = response.movies ?: emptyList()

        var incomingNew = 0
        var incomingUpdated = 0
        var incomingDeleted = 0
        var keptLocal = 0
        val items = mutableListOf<SyncPreviewItem>()

        fun note(item: SyncPreviewItem) {
            if (items.size < PREVIEW_LIMIT) items += item
        }

        for (movie in movies) {
            val existing = movieDao.getByRemoteId(movie.id)
            when {
                movie.isDeleted == true -> if (existing != null) {
                    incomingDeleted++
                    note(SyncPreviewItem(movie.title, movie.year, SyncAction.DELETED, SyncDirection.PULL))
                }
                existing == null -> {
                    incomingNew++
                    note(SyncPreviewItem(movie.title, movie.year, SyncAction.NEW, SyncDirection.PULL))
                }
                existing.isDirty -> {
                    keptLocal++
                    note(SyncPreviewItem(movie.title, movie.year, SyncAction.KEPT_LOCAL, SyncDirection.PULL))
                }
                else -> {
                    val changes = changedFields(movie, existing)
                    if (changes.isNotEmpty()) {
                        incomingUpdated++
                        note(SyncPreviewItem(movie.title, movie.year, SyncAction.UPDATED, SyncDirection.PULL, changes))
                    }
                }
            }
        }

        for (entity in dirty) {
            val action = when {
                entity.isDeleted -> SyncAction.DELETED
                entity.remoteId == null -> SyncAction.NEW
                else -> SyncAction.UPDATED
            }
            note(SyncPreviewItem(entity.title, entity.year, action, SyncDirection.PUSH))
        }

        val total = incomingNew + incomingUpdated + incomingDeleted + keptLocal + dirty.size
        val pendingWatched = movieDao.getPendingWatched().size

        return SyncPreview(
            toPushWatched = pendingWatched,
            items = items,
            overflow = maxOf(0, total - items.size),
            toCreate = toCreate,
            toUpdate = toUpdate,
            toDeleteRemote = toDeleteRemote,
            incomingNew = incomingNew,
            incomingUpdated = incomingUpdated,
            incomingDeleted = incomingDeleted,
            keptLocal = keptLocal,
            isDelta = response.isDelta == true,
            lastSyncAt = since
        )
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

        val dirty = movieDao.getDirtyMovies()
        for ((index, entity) in dirty.withIndex()) {
            onProgress(SyncProgress(SyncPhase.PUSH, index, dirty.size, entity.title))
            val now = SyncClock.now()
            try {
                when {
                    entity.isDeleted && entity.remoteId != null -> {
                        deleteTolerantly(entity.remoteId)
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
                        val response = createOnServer(entity)
                        if (response != null) {
                            movieDao.markSynced(entity.localId, now, response.id)
                            queueArtworkUpload(entity.localId)
                            created++
                        }
                    }

                    else -> {
                        api.updateMovie(entity.remoteId, entity.toUpdateRequest())
                        movieDao.markSynced(entity.localId, now)
                        if (entity.collectionType == "Serie") {
                            pushSeasons(entity.localId, entity.remoteId)
                        }
                        updated++
                    }
                }
            } catch (e: Exception) {
                errors += SyncError(entity.title ?: "Film ${entity.localId}", e.message ?: "Unbekannter Fehler")
            }
        }
        // Staffel-Abgleich fuer alle synchronisierten Serien, nicht nur fuer
        // geaenderte — siehe pushSeasons().
        for (serie in movieDao.getSyncedSeries()) {
            val remoteId = serie.remoteId ?: continue
            try {
                pushSeasons(serie.localId, remoteId)
            } catch (e: Exception) {
                errors += SyncError(serie.title ?: "Serie ${serie.localId}", e.message ?: "Unbekannter Fehler")
            }
        }

        val watched = try {
            pushWatched { current, total, subject ->
                onProgress(SyncProgress(SyncPhase.PUSH, current, total, subject))
            }
        } catch (e: Exception) {
            errors += SyncError("Gesehen-Markierungen", e.message ?: "Unbekannter Fehler")
            0
        }

        // Nach dem Film-Push, damit ein soeben angelegter Film seine Server-ID
        // schon hat — ohne sie liesse sich seine Bewertung nicht senden.
        val ratings = try {
            pushUserRatings { current, total, subject ->
                onProgress(SyncProgress(SyncPhase.PUSH, current, total, subject))
            }
        } catch (e: Exception) {
            errors += SyncError("Bewertungen", e.message ?: "Unbekannter Fehler")
            0
        }

        val episodes = try {
            pushEpisodeWatched { current, total, subject ->
                onProgress(SyncProgress(SyncPhase.PUSH, current, total, subject))
            }
        } catch (e: Exception) {
            errors += SyncError("Folgen-Markierungen", e.message ?: "Unbekannter Fehler")
            0
        }

        return PushResult(created, updated, deleted, errors, watched, ratings, episodes)
    }

    /**
     * Film serverseitig anlegen.
     *
     * Kennt die Zeile eine TMDb-ID, laesst sie der Server von dort importieren:
     * er holt dann Bilder, Besetzung und Metadaten selbst. Nur ohne TMDb-Bezug
     * werden die Felder roh uebertragen.
     */
    private suspend fun createOnServer(entity: MovieEntity): Movie? {
        val tmdbId = entity.tmdbId?.toIntOrNull()
            ?: return api.createMovie(entity.toUpdateRequest()).data

        val isSeries = entity.collectionType == "Serie"
        return api.importFromTmdb(
            tmdbId = tmdbId,
            type = if (isSeries) "tv" else "movie",
            inCollection = entity.inCollection != false,
            // Nur die lokal vorhandenen Staffeln, sonst importiert der Server
            // alles, was TMDb kennt.
            seasons = if (isSeries) localSeasonNumbers(entity.localId) else null
        ).data
    }

    /**
     * Staffeln der Shelf an den lokalen Stand angleichen.
     *
     * Gegenstueck zum Zurueckschneiden im Pull: beim Push bestimmt die App.
     * Die Desktop-App macht das fuer **alle** synchronisierten Serien, nicht
     * nur fuer geaenderte — eine Serie ohne Metadaten-Aenderung wuerde sonst
     * nie auf Staffel-Abweichungen geprueft, und genau dafuer ist der Schritt
     * da.
     */
    private suspend fun pushSeasons(localId: Long, remoteId: Int) {
        val local = localSeasonNumbers(localId).toSet()
        val remote = remoteSeasonNumbers(remoteId).toSet()

        val missing = (local - remote).sorted()
        val extra = (remote - local).sorted()
        if (missing.isNotEmpty()) api.importSeasons(remoteId, missing)
        if (extra.isNotEmpty()) api.removeSeasons(remoteId, extra)
    }

    /** Loeschen, bei dem ein bereits geloeschter Film kein Fehler ist. */
    private suspend fun deleteTolerantly(remoteId: Int) {
        try {
            api.deleteMovie(remoteId)
        } catch (e: retrofit2.HttpException) {
            if (e.code() != 404) throw e
        }
    }

    // ── Herunterladen ────────────────────────────────────────────────────────

    /**
     * Serverstand einspielen. Ohne [full] nur, was sich seit dem letzten
     * erfolgreichen Abgleich geändert hat.
     */
    suspend fun pull(full: Boolean = false): PullResult {
        onProgress(SyncProgress(SyncPhase.PULL))
        val since = if (full) null else settingDao.get(SettingKeys.LAST_SYNC_AT)
        val response = api.exportMovies(since)
        val movies = response.movies ?: emptyList()

        var applied = 0
        var skipped = 0
        var deleted = 0
        val errors = mutableListOf<SyncError>()

        for ((index, movie) in movies.withIndex()) {
            onProgress(SyncProgress(SyncPhase.PULL, index, movies.size, movie.title))
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

                val fromServer = MovieEntity.fromServerMovie(movie)
                val entity = when {
                    existing == null -> fromServer
                    else -> {
                        var merged = fromServer.copy(localId = existing.localId)
                        // Offene "gesehen"-Markierung und offene Bewertung
                        // ueberleben den Pull. Beide haengen nicht an
                        // updatedAt/syncedAt, also gilt die Zeile nach dem
                        // Film-Push als sauber, obwohl sie es nicht ist — ohne
                        // diese Ausnahme waeren sie hier still weg.
                        if (existing.hasPendingWatched) {
                            merged = merged.copy(
                                isWatched = existing.isWatched,
                                syncedWatched = existing.syncedWatched
                            )
                        }
                        if (existing.hasPendingUserRating) {
                            merged = merged.copy(
                                userRating = existing.userRating,
                                syncedUserRating = existing.syncedUserRating
                            )
                        }
                        merged
                    }
                }
                movieDao.upsertFromServer(listOf(entity))
                applied++

                // Besetzung und Staffeln haengen an der lokalen ID, die erst
                // nach dem Einspielen feststeht.
                val localId = movieDao.findLocalIdByRemoteId(movie.id)
                if (localId != null) {
                    movie.actors?.takeIf { it.isNotEmpty() }?.let { upsertCast(localId, it) }
                    movie.seasons?.let { seasons ->
                        if (seasons.isNotEmpty()) upsertSeries(localId, seasons.toEntities())
                        // Auch bei leerer Liste: die Shelf kennt dann keine
                        // Staffeln mehr, also duerfen lokal auch keine bleiben.
                        pruneSeasons(localId, seasons.map { it.seasonNumber })
                    }
                }
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

        /**
     * Welche Felder sich gegenueber dem lokalen Stand unterscheiden.
     *
     * Nur Felder, die der Nutzer auch sieht - sonst listet die Vorschau
     * Unterschiede auf, die niemandem etwas sagen. Verglichen wird als Text,
     * weil Zahl und Zeichenkette je nach Quelle unterschiedlich ankommen.
     */
    private suspend fun changedFields(server: Movie, local: MovieEntity): List<String> {
        val changes = mutableListOf<String>()
        fun compare(label: String, a: Any?, b: Any?) {
            if (a?.toString().orEmpty() != b?.toString().orEmpty()) changes += label
        }

        compare("Titel", server.title, local.title)
        compare("Jahr", server.year, local.year)
        compare("Genre", server.genre, local.genre)
        compare("Regisseur", server.director, local.director)
        compare("Laufzeit", server.runtime, local.runtime)
        compare("Bewertung", server.rating, local.rating)
        compare("FSK", server.ratingAge, local.ratingAge)
        compare("Beschreibung", server.overview, local.overview)
        compare("Typ", server.collectionType, local.collectionType)
        compare("Format", server.tag, local.tag)
        compare("Trailer", server.trailerUrl, local.trailerUrl)

        // "Gesehen" nicht ueber compare(): der Vergleich dort laeuft ueber
        // toString(), und `null` gegen `false` gaebe einen Unterschied, den es
        // nicht gibt. Ohne diese Zeile blieb ein Film, an dem sich nur der
        // Gesehen-Stand geaendert hat, in der Vorschau unsichtbar — der
        // Abgleich uebertraegt ihn trotzdem.
        if ((server.isWatched == true) != (local.isWatched == true)) {
            changes += "Gesehen"
        }

        if (server.collectionType == "Serie" && seasonsDiffer(local.localId, server.seasons)) {
            changes += "Staffeln"
        }
        return changes
    }

    /**
     * Ob sich die Staffeln unterscheiden - verglichen wird nur Nummer und
     * Episodenzahl, kein Feldabgleich. Das genuegt, um eine auf der Shelf
     * entfernte Staffel als Aenderung zu erkennen.
     */
    private suspend fun seasonsDiffer(
        localId: Long,
        serverSeasons: List<info.movieshelf.data.model.ApiSeason>?
    ): Boolean {
        val server = serverSeasons.orEmpty()
            .map { "${it.seasonNumber}:${it.episodes.orEmpty().size}" }
            .sorted()
        return localSeasonSignature(localId) != server
    }

    /**
     * Staffeln der Shelf in lokale Zeilen uebersetzen. Die lokalen IDs bleiben
     * offen — [SeriesDao.upsertSeries] hebt bekannte Nummern auf ihre
     * bestehende Zeile, damit ein wiederholter Import keine zweite Folge 1
     * anlegt.
     */
    private fun List<info.movieshelf.data.model.ApiSeason>.toEntities(): List<SeasonWithEpisodes> =
        map { season ->
            SeasonWithEpisodes(
                season = info.movieshelf.data.local.db.SeasonEntity(
                    remoteId = season.id,
                    movieLocalId = 0,
                    seasonNumber = season.seasonNumber,
                    title = season.title,
                    overview = season.overview
                ),
                episodes = (season.episodes ?: emptyList()).map { episode ->
                    info.movieshelf.data.local.db.EpisodeEntity(
                        remoteId = episode.id,
                        seasonLocalId = 0,
                        episodeNumber = episode.episodeNumber,
                        title = episode.title,
                        overview = episode.overview,
                        // Was vom Server kommt, gilt dort als bekannt.
                        isWatched = episode.isWatched == true,
                        syncedWatched = episode.isWatched == true
                    )
                }
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

    /**
     * Loeschen. Ein bereits geloeschter Film (404) gilt als Erfolg — das Ziel
     * ist erreicht, und ein Fehler wuerde die Zeile fuer immer abweichend
     * halten.
     */
    suspend fun deleteMovie(id: Int)

    /**
     * Film ueber TMDb anlegen lassen. Der Server holt dabei Bilder, Besetzung
     * und Metadaten selbst — ueber [createMovie] angelegt, stuende er auf der
     * Shelf ohne all das da.
     */
    suspend fun importFromTmdb(
        tmdbId: Int,
        type: String,
        inCollection: Boolean,
        seasons: List<Int>?
    ): SingleMovieResponse

    /** Staffelnummern, die die Shelf zu dieser Serie kennt. */
    suspend fun remoteSeasonNumbers(remoteId: Int): List<Int>

    suspend fun importSeasons(remoteId: Int, seasons: List<Int>)
    suspend fun removeSeasons(remoteId: Int, seasons: List<Int>)
}

enum class SyncPhase { PUSH, PULL, MEDIA, DONE }

data class SyncProgress(
    val phase: SyncPhase,
    val current: Int = 0,
    val total: Int = 0,
    /** Titel des gerade bearbeiteten Films, sofern es einen gibt. */
    val subject: String? = null
) {
    val fraction: Float? get() = if (total > 0) current.toFloat() / total else null
}

/** Wie viele Einzelposten die Vorschau hoechstens auffuehrt. */
private const val PREVIEW_LIMIT = 100

enum class SyncAction { NEW, UPDATED, DELETED, KEPT_LOCAL }

enum class SyncDirection { PULL, PUSH }

/** Ein einzelner Posten der Vorschau. */
data class SyncPreviewItem(
    val title: String?,
    val year: Int?,
    val action: SyncAction,
    val direction: SyncDirection,
    /** Bei [SyncAction.UPDATED]: welche Felder sich unterscheiden. */
    val changes: List<String> = emptyList()
)

/** Was ein Abgleich tun wuerde. Grundlage der Bestaetigung vor dem Loeschen. */
data class SyncPreview(
    val items: List<SyncPreviewItem> = emptyList(),
    /** Wie viele Posten ueber PREVIEW_LIMIT hinaus anfallen. */
    val overflow: Int = 0,
    val toCreate: Int = 0,
    val toUpdate: Int = 0,
    val toDeleteRemote: Int = 0,
    val incomingNew: Int = 0,
    val incomingUpdated: Int = 0,
    val incomingDeleted: Int = 0,
    /** Zeilen, die lokale Aenderungen behalten und deshalb nicht ueberschrieben werden. */
    val keptLocal: Int = 0,
    /**
     * Offene "gesehen"-Markierungen. Eigener Posten, weil sie ueber einen
     * eigenen Endpunkt gehen und in [toUpdate] nicht auftauchen — ohne sie
     * meldete die Vorschau "nichts zu tun", waehrend sehr wohl etwas anstand.
     */
    val toPushWatched: Int = 0,
    val isDelta: Boolean = false,
    val lastSyncAt: String? = null
) {
    val outgoing: Int get() = toCreate + toUpdate + toDeleteRemote + toPushWatched
    val incoming: Int get() = incomingNew + incomingUpdated + incomingDeleted
    val hasDeletions: Boolean get() = toDeleteRemote > 0 || incomingDeleted > 0
    val isEmpty: Boolean get() = outgoing == 0 && incoming == 0
}

data class SyncError(val subject: String, val message: String)

data class PushResult(
    val created: Int = 0,
    val updated: Int = 0,
    val deleted: Int = 0,
    val errors: List<SyncError> = emptyList(),
    /** Uebertragene "gesehen"-Markierungen. */
    val watched: Int = 0,
    /** Uebertragene eigene Bewertungen. */
    val userRatings: Int = 0,
    /** Uebertragene Folgen-Markierungen. */
    val episodesWatched: Int = 0
) {
    val total: Int get() = created + updated + deleted + watched + userRatings + episodesWatched
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
    val pull: PullResult,
    /** Wie viele Bilder in dieser Runde dauerhaft abgelegt wurden. */
    val artworkStored: Int = 0
) {
    val errors: List<SyncError> get() = push.errors + pull.errors
    val hasErrors: Boolean get() = errors.isNotEmpty()
}
