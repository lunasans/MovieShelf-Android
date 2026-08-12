package at.neuhaus.movieshelf.data.repository

import at.neuhaus.movieshelf.data.api.MovieShelfApi
import at.neuhaus.movieshelf.data.api.TmdbApi
import at.neuhaus.movieshelf.data.api.TmdbCredits
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
    suspend fun import(tmdbId: Int, inCollection: Boolean, series: Boolean = false): Long? {
        if (isShelfMode()) {
            movieRepository.importFromTmdb(tmdbId, inCollection)
            return null
        }

        val key = apiKeyProvider() ?: throw MissingTmdbKeyException()
        return if (series) importSeries(tmdbId, key, inCollection)
        else importMovie(tmdbId, key, inCollection)
    }

    private suspend fun importMovie(tmdbId: Int, key: String, inCollection: Boolean): Long? {
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
        ) ?: return null

        finish(localId, details.posterPath, details.backdropPath, details.credits)
        return localId
    }

    private suspend fun importSeries(tmdbId: Int, key: String, inCollection: Boolean): Long? {
        val details = tmdbApi.getSeries(tmdbId, key)
        val localId = movieRepository.createMovie(
            MovieUpdateRequest(
                title = details.name ?: "Ohne Titel",
                year = details.firstAirDate?.take(4)?.toIntOrNull() ?: 0,
                collectionType = "Serie",
                genre = details.genres?.mapNotNull { it.name }?.joinToString(", ")?.takeIf { it.isNotBlank() },
                // Serien haben keine einzelne Laufzeit; TMDb liefert eine Liste
                // ueblicher Episodenlaengen.
                runtime = details.episodeRunTime?.firstOrNull(),
                rating = details.voteAverage,
                overview = details.overview,
                inCollection = inCollection
            )
        ) ?: return null

        finish(localId, details.posterPath, details.backdropPath, details.credits)
        return localId
    }

    /**
     * Bilder und Besetzung nachtragen.
     *
     * Die TMDb-Adresse wird eingetragen und das Bild sofort geholt; danach
     * steht in der Spalte der Dateipfad und die Adresse ist weg. So entsteht
     * genau ein Abruf bei TMDb und nicht bei jedem Anzeigen einer.
     */
    private suspend fun finish(
        localId: Long,
        posterPath: String?,
        backdropPath: String?,
        credits: TmdbCredits?
    ) {
        movieRepository.setImageUrls(
            localId = localId,
            coverUrl = TmdbApi.imageUrl(posterPath),
            backdropUrl = TmdbApi.imageUrl(backdropPath)
        )

        val cast = credits?.cast.orEmpty()
            .sortedBy { it.order ?: Int.MAX_VALUE }
            // Nur die vordere Besetzung: TMDb liefert bei grossen Produktionen
            // hunderte Eintraege bis zur Statisterie.
            .take(15)
            .mapNotNull { member ->
                member.name?.let {
                    LocalCastMember(
                        name = it,
                        role = member.character,
                        imageUrl = TmdbApi.imageUrl(member.profilePath),
                        tmdbId = member.id
                    )
                }
            }
        if (cast.isNotEmpty()) movieRepository.setCast(localId, cast)
    }
}

/** Ohne hinterlegten Schluessel geht im eigenstaendigen Betrieb nichts. */
class MissingTmdbKeyException : IllegalStateException(
    "Kein TMDb-Schlüssel hinterlegt. Du kannst ihn im Profil eintragen."
)
