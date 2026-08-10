package at.neuhaus.movieshelf

import android.app.Application
import at.neuhaus.movieshelf.data.api.RetrofitClient
import at.neuhaus.movieshelf.data.local.ImageDownloader
import at.neuhaus.movieshelf.data.local.MediaStore
import at.neuhaus.movieshelf.data.local.db.AppMode
import at.neuhaus.movieshelf.data.local.db.MovieShelfDatabase
import at.neuhaus.movieshelf.data.local.db.SettingKeys
import at.neuhaus.movieshelf.data.repository.ActorRepository
import at.neuhaus.movieshelf.data.repository.ListRepository
import at.neuhaus.movieshelf.data.repository.MovieRepository
import at.neuhaus.movieshelf.data.repository.TmdbRepository
import at.neuhaus.movieshelf.data.api.MovieShelfApi
import at.neuhaus.movieshelf.data.api.TmdbApi
import at.neuhaus.movieshelf.data.local.DataStoreManager
import at.neuhaus.movieshelf.data.model.ListItemRef
import at.neuhaus.movieshelf.data.model.MovieUpdateRequest
import at.neuhaus.movieshelf.data.sync.ListSyncApi
import at.neuhaus.movieshelf.data.sync.ListSyncEngine
import at.neuhaus.movieshelf.data.sync.SyncApi
import at.neuhaus.movieshelf.data.sync.SyncEngine
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

    val tmdbRepository by lazy {
        TmdbRepository(
            movieRepository = movieRepository,
            tmdbApi = TmdbApi.create(),
            shelfApiProvider = { RetrofitClient.api },
            isShelfMode = { isShelfMode() },
            apiKeyProvider = { dataStoreManager.currentTmdbApiKey() }
        )
    }

    val syncEngine by lazy {
        SyncEngine(
            movieDao = database.movieDao(),
            settingDao = database.settingDao(),
            apiProvider = { RetrofitApi(RetrofitClient.api) },
            flushPendingUploads = { movieRepository.flushPendingUploads() },
            upsertSeries = { localId, seasons ->
                database.seriesDao().upsertSeries(localId, seasons)
            },
            downloadMissingArtwork = { movieRepository.downloadMissingArtwork() },
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
