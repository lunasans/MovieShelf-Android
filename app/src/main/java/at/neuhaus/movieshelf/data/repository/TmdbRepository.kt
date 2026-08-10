package at.neuhaus.movieshelf.data.repository

import at.neuhaus.movieshelf.data.api.MovieShelfApi
import at.neuhaus.movieshelf.data.api.TmdbApi
import at.neuhaus.movieshelf.data.model.MovieUpdateRequest
import at.neuhaus.movieshelf.data.model.TmdbSearchResponse

/**
 * TMDb-Zugriff, je nach Betriebsart über zwei verschiedene Wege.
 *
 * **Mit Shelf** läuft alles über deren Endpunkte. Das ist kein Umweg, sondern
 * notwendig: der serverseitige Import legt den Film samt Darstellern und
 * Bildern in der Shelf an, und die App bekommt ihn beim nächsten Abgleich
 * ohnehin. Würde die App hier direkt importieren, entstünde der Film zweimal.
 *
 * **Ohne Shelf** spricht die App TMDb direkt an, mit dem Schlüssel des Nutzers.
 * Der angelegte Film ist dann eine rein lokale Zeile wie jede andere.
 */
class TmdbRepository(
    private val movieRepository: MovieRepository,
    private val tmdbApi: TmdbApi,
    private val shelfApiProvider: () -> MovieShelfApi,
    private val isShelfMode: suspend () -> Boolean,
    private val apiKeyProvider: () -> String?
) {
    /** Ohne Shelf und ohne Schluessel ist die Suche nicht benutzbar. */
    suspend fun isSearchAvailable(): Boolean = isShelfMode() || apiKeyProvider() != null

    suspend fun search(query: String, series: Boolean = false): TmdbSearchResponse {
        if (isShelfMode()) return shelfApiProvider().searchTmdb(query)

        val key = apiKeyProvider() ?: throw MissingTmdbKeyException()
        return if (series) tmdbApi.searchSeries(key, query) else tmdbApi.searchMovies(key, query)
    }

    /**
     * Film aus TMDb übernehmen.
     *
     * @return lokale ID des angelegten Films, oder `null` im Shelf-Betrieb —
     *   dort legt der Server ihn an und der Abgleich holt ihn.
     */
    suspend fun import(tmdbId: Int, inCollection: Boolean): Long? {
        if (isShelfMode()) {
            movieRepository.importFromTmdb(tmdbId, inCollection)
            return null
        }

        val key = apiKeyProvider() ?: throw MissingTmdbKeyException()
        val details = tmdbApi.getMovie(tmdbId, key)

        val localId = movieRepository.createMovie(
            MovieUpdateRequest(
                title = details.title ?: "Ohne Titel",
                year = details.year ?: 0,
                collectionType = "Film",
                genre = details.genreNames,
                director = details.director,
                runtime = details.runtime,
                rating = details.voteAverage,
                overview = details.overview,
                trailerUrl = details.trailerUrl,
                inCollection = inCollection
            )
        )

        // Bilder liegen bei TMDb unter einem Pfad, nicht unter einer vollen URL.
        // Sie werden als URL vermerkt statt heruntergeladen: Coil hält sie
        // ohnehin im Cache, und ein eigener Speicher müsste selbst aufgeräumt
        // werden.
        if (localId != null) {
            movieRepository.setImageUrls(
                localId = localId,
                coverUrl = TmdbApi.imageUrl(details.posterPath),
                backdropUrl = TmdbApi.imageUrl(details.backdropPath)
            )
        }
        return localId
    }
}

/** Ohne hinterlegten Schluessel geht im eigenstaendigen Betrieb nichts. */
class MissingTmdbKeyException : IllegalStateException(
    "Kein TMDb-Schlüssel hinterlegt. Du kannst ihn im Profil eintragen."
)
