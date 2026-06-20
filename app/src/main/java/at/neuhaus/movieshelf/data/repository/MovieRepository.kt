package at.neuhaus.movieshelf.data.repository

import at.neuhaus.movieshelf.data.api.MovieShelfApi
import at.neuhaus.movieshelf.data.local.db.MovieDao
import at.neuhaus.movieshelf.data.local.db.MovieEntity
import at.neuhaus.movieshelf.data.model.Movie
import at.neuhaus.movieshelf.data.model.MovieUpdateRequest

private const val CACHE_MAX_AGE_MS = 30 * 60 * 1000L // 30 Minuten

class MovieRepository(
    private val movieDao: MovieDao,
    // Provider statt fester Instanz: so wird nach einem Server-Wechsel
    // (RetrofitClient.initialize) immer die aktuelle API benutzt.
    private val apiProvider: () -> MovieShelfApi
) {
    private val api: MovieShelfApi get() = apiProvider()

    var isOffline: Boolean = false
        private set

    /**
     * Versucht alle Filme vom Server zu laden und cached sie.
     * Fällt bei Fehler auf den lokalen Cache zurück.
     * Bei pageSize=0 wird alles geladen (für Offline-Vollsync).
     */
    suspend fun getMovies(page: Int = 1, perPage: Int = 30, tag: String? = null): List<Movie> {
        return try {
            val response = api.getMovies(page = page, perPage = perPage, tag = tag)
            val movies = response.data ?: emptyList()
            val entities = movies.map { MovieEntity.fromMovie(it) }
            // Erste Seite ersetzt den Cache atomar, weitere Seiten werden ergänzt
            if (page == 1) {
                movieDao.replaceAll(entities)
            } else {
                movieDao.insertMovies(entities)
            }
            isOffline = false
            movies
        } catch (e: Exception) {
            isOffline = true
            // Offline: konsistent aus dem Cache paginieren, statt bei Seite > 1
            // eine leere Liste zu liefern (was die UI als "Ende erreicht" deutet).
            val cached = movieDao.getAllMovies().map { it.toMovie() }
            if (page <= 1) cached else cached.drop((page - 1) * perPage).take(perPage)
        }
    }

    /**
     * Suche — online wenn möglich, sonst lokale Suche.
     */
    suspend fun searchMovies(query: String): List<Movie> {
        return try {
            val response = api.searchMovies(query)
            isOffline = false
            response.data ?: emptyList()
        } catch (e: Exception) {
            isOffline = true
            movieDao.searchMovies(query).map { it.toMovie() }
        }
    }

    /** Watched-Status toggeln — optimistisch lokal + Remote-Aufruf. */
    suspend fun toggleWatched(movieId: Int, currentState: Boolean) {
        val newState = !currentState
        movieDao.updateWatched(movieId, newState)
        try {
            api.toggleWatched(movieId)
        } catch (e: Exception) {
            // Bei Fehler Lokal-State zurücksetzen
            movieDao.updateWatched(movieId, currentState)
            throw e
        }
    }

    /** Film bearbeiten (Admin). Aktualisiert bei Erfolg auch den lokalen Cache. */
    suspend fun updateMovie(id: Int, request: MovieUpdateRequest): Movie? {
        val response = api.updateMovie(id, request)
        return response.data?.also { movieDao.insertMovie(MovieEntity.fromMovie(it)) }
    }

    /** Film löschen (Admin). Entfernt ihn bei Erfolg auch aus dem lokalen Cache. */
    suspend fun deleteMovie(id: Int) {
        api.deleteMovie(id)
        movieDao.deleteById(id)
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
                movieDao.insertMovie(MovieEntity.fromMovie(movie))
            }
        } catch (e: Exception) {
            movieDao.getMovieById(id)?.toMovie()
        }
    }

    suspend fun getCachedMovies(): List<Movie> =
        movieDao.getAllMovies().map { it.toMovie() }

    suspend fun isCacheAvailable(): Boolean =
        movieDao.getMovieCount() > 0

    suspend fun getDistinctGenres(): List<String> =
        movieDao.getDistinctGenres().flatMap { it.split(",") }.map { it.trim() }.filter { it.isNotBlank() }.distinct().sorted()

    suspend fun getDistinctDirectors(): List<String> =
        movieDao.getDistinctDirectors()

    suspend fun getYearRange(): Pair<Int, Int>? {
        val min = movieDao.getMinYear() ?: return null
        val max = movieDao.getMaxYear() ?: return null
        return min to max
    }
}
