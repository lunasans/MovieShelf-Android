package info.movieshelf

import android.app.Application
import info.movieshelf.data.api.RetrofitClient
import info.movieshelf.data.local.ImageDownloader
import info.movieshelf.data.local.MediaStore
import info.movieshelf.data.local.db.AppMode
import info.movieshelf.data.local.db.MovieShelfDatabase
import info.movieshelf.data.local.db.SettingKeys
import info.movieshelf.data.repository.ActorRepository
import info.movieshelf.data.repository.ListRepository
import info.movieshelf.data.repository.MovieRepository
import info.movieshelf.data.repository.TmdbRepository
import info.movieshelf.data.api.MovieShelfApi
import info.movieshelf.data.api.TmdbApi
import info.movieshelf.data.local.DataStoreManager
import info.movieshelf.data.model.ListItemRef
import info.movieshelf.data.model.MovieUpdateRequest
import info.movieshelf.data.model.SeasonImportRequest
import info.movieshelf.data.model.TmdbImportRequest
import info.movieshelf.data.sync.ListSyncApi
import info.movieshelf.data.sync.ListSyncEngine
import info.movieshelf.data.sync.SyncApi
import info.movieshelf.data.sync.SyncEngine
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.util.DebugLogger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class MovieShelfApplication : Application(), ImageLoaderFactory {

    val database by lazy { MovieShelfDatabase.getInstance(this) }

    val mediaStore by lazy { MediaStore(filesDir) }

    private val imageDownloader by lazy {
        ImageDownloader(shelfClientProvider = { RetrofitClient.httpClient })
    }

    /**
     * Betriebsmodus, `null` solange nicht gewaehlt. Liegt in der Datenbank,
     * damit er mit ihr verworfen wird — siehe [AppMode].
     */
    val appMode: Flow<AppMode?> by lazy {
        database.settingDao().observe(SettingKeys.MODE).map { AppMode.from(it) }
    }

    suspend fun setAppMode(mode: AppMode) {
        database.settingDao().put(SettingKeys.MODE, mode.key)
    }

    /**
     * Betriebsart wieder offen lassen, damit die Auswahl mit den beiden
     * Kacheln erscheint. Die Sammlung bleibt unangetastet — nur die Frage,
     * woher sie kuenftig kommt, wird neu gestellt.
     */
    suspend fun clearAppMode() {
        database.settingDao().remove(SettingKeys.MODE)
    }




    private suspend fun isShelfMode(): Boolean =
        AppMode.from(database.settingDao().get(SettingKeys.MODE)) != AppMode.STANDALONE

    val movieRepository by lazy {
        MovieRepository(
            movieDao = database.movieDao(),
            actorDao = database.actorDao(),
            seriesDao = database.seriesDao(),
            pendingUploadDao = database.pendingUploadDao(),
            mediaStore = mediaStore,
            imageDownloader = imageDownloader,
            shelfUrlProvider = { RetrofitClient.baseUrl.takeIf { it.isNotBlank() } },
            isShelfMode = { isShelfMode() }
        ) { RetrofitClient.api }
    }

    val listRepository by lazy {
        ListRepository { RetrofitClient.api }
    }

    val actorRepository by lazy {
        ActorRepository { RetrofitClient.api }
    }

    private val dataStoreManager by lazy { DataStoreManager(this) }

    /** Fuer die Oberflaeche, die den Jellyfin-Zugang verwaltet. */
    val dataStore: DataStoreManager get() = dataStoreManager

    val tmdbRepository by lazy {
        TmdbRepository(
            movieRepository = movieRepository,
            tmdbApi = TmdbApi.create(),
            shelfApiProvider = { RetrofitClient.api },
            isShelfMode = { isShelfMode() },
            apiKeyProvider = { dataStoreManager.currentTmdbApiKey() }
        )
    }

    val jellyfinClient by lazy { info.movieshelf.data.jellyfin.JellyfinClient() }

    val jellyfinImporter by lazy {
        info.movieshelf.data.jellyfin.JellyfinImporter(
            client = jellyfinClient,
            movieDao = database.movieDao(),
            actorDao = database.actorDao(),
            seriesDao = database.seriesDao(),
            mediaStore = mediaStore,
            tmdbApi = TmdbApi.create(),
            tmdbApiKeyProvider = { dataStoreManager.currentTmdbApiKey() },
            setCast = { localId, cast -> movieRepository.setCast(localId, cast) },
            downloadArtwork = { movieRepository.downloadMissingArtwork() }
        )
    }

    val syncEngine by lazy {
        SyncEngine(
            movieDao = database.movieDao(),
            settingDao = database.settingDao(),
            apiProvider = { RetrofitApi(RetrofitClient.api) },
            flushPendingUploads = { report -> movieRepository.flushPendingUploads(report) },
            upsertSeries = { localId, seasons ->
                database.seriesDao().upsertSeries(localId, seasons)
            },
            localSeasonNumbers = { localId ->
                database.seriesDao().getSeasons(localId).map { it.seasonNumber }
            },
            upsertCast = { localId, actors -> movieRepository.saveServerCast(localId, actors) },
            pushWatched = { onProgress -> movieRepository.pushWatchedChanges(onProgress) },
            pushUserRatings = { onProgress -> movieRepository.pushUserRatings(onProgress) },
            pruneSeasons = { localId, keep -> database.seriesDao().pruneSeasons(localId, keep) },
            localSeasonSignature = { localId ->
                database.seriesDao().getSeasons(localId)
                    .map { "${it.seasonNumber}:${database.seriesDao().getEpisodes(it.localId).size}" }
                    .sorted()
            },
            downloadMissingArtwork = { report -> movieRepository.downloadMissingArtwork(report) },
            cleanupOrphanedArtwork = { movieRepository.cleanupOrphanedArtwork() },
            queueArtworkUpload = { movieRepository.queueArtworkUpload(it) }
        )
    }

    val listSyncEngine by lazy {
        ListSyncEngine(
            listDao = database.listDao(),
            movieDao = database.movieDao(),
            externalMovieDao = database.externalMovieDao()
        ) { RetrofitListApi(listRepository) }
    }

    /** Bindet die schmale [ListSyncApi] an das Listen-Repository. */
    private class RetrofitListApi(private val repository: ListRepository) : ListSyncApi {
        override suspend fun getLists() = repository.getLists()
        override suspend fun getList(listId: Int) = repository.getList(listId)
        override suspend fun setItems(listId: Int, name: String, items: List<ListItemRef>) {
            repository.setItems(listId, name, items)
        }
    }

    /** Bindet die schmale [SyncApi] an die vollständige Shelf-Schnittstelle. */
    private class RetrofitApi(private val api: MovieShelfApi) : SyncApi {
        override suspend fun exportMovies(since: String?) = api.exportMovies(since)
        override suspend fun createMovie(request: MovieUpdateRequest) = api.createMovie(request)
        override suspend fun updateMovie(id: Int, request: MovieUpdateRequest) = api.updateMovie(id, request)
        override suspend fun deleteMovie(id: Int) { api.deleteMovie(id) }
        override suspend fun importFromTmdb(
            tmdbId: Int,
            type: String,
            inCollection: Boolean,
            seasons: List<Int>?
        ) = api.importFromTmdb(TmdbImportRequest(tmdbId, type, inCollection, seasons))

        override suspend fun remoteSeasonNumbers(remoteId: Int): List<Int> =
            api.getMovie(remoteId).data?.seasons?.map { it.seasonNumber } ?: emptyList()

        override suspend fun importSeasons(remoteId: Int, seasons: List<Int>) {
            api.importSeasons(SeasonImportRequest(remoteId, seasons))
        }

        override suspend fun removeSeasons(remoteId: Int, seasons: List<Int>) {
            api.removeSeasons(SeasonImportRequest(remoteId, seasons))
        }
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .okHttpClient { RetrofitClient.httpClient }
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(this.cacheDir.resolve("image_cache"))
                    .maxSizePercent(0.02)
                    .maxSizeBytes(50L * 1024 * 1024)
                    .build()
            }
            .crossfade(true)
            .logger(DebugLogger())
            .build()
    }
}
