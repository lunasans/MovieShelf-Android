package at.neuhaus.movieshelf

import android.app.Application
import at.neuhaus.movieshelf.data.api.RetrofitClient
import at.neuhaus.movieshelf.data.local.MediaStore
import at.neuhaus.movieshelf.data.local.db.MovieShelfDatabase
import at.neuhaus.movieshelf.data.repository.ActorRepository
import at.neuhaus.movieshelf.data.repository.ListRepository
import at.neuhaus.movieshelf.data.repository.MovieRepository
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.util.DebugLogger

class MovieShelfApplication : Application(), ImageLoaderFactory {

    val database by lazy { MovieShelfDatabase.getInstance(this) }

    val mediaStore by lazy { MediaStore(filesDir) }

    val movieRepository by lazy {
        MovieRepository(
            movieDao = database.movieDao(),
            pendingUploadDao = database.pendingUploadDao(),
            mediaStore = mediaStore
        ) { RetrofitClient.api }
    }

    val listRepository by lazy {
        ListRepository { RetrofitClient.api }
    }

    val actorRepository by lazy {
        ActorRepository { RetrofitClient.api }
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
