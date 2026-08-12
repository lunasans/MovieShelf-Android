package info.movieshelf.data.repository

import info.movieshelf.data.api.MovieShelfApi
import info.movieshelf.data.model.Actor

/**
 * Darsteller.
 *
 * Noch ohne lokale Tabellen — `actors` und `film_actor` stehen bereits, werden
 * aber erst mit dem Abgleich in Phase 4 gefüllt. Bis dahin bündelt diese Klasse
 * die Netzaufrufe, damit die ViewModels nur noch das Repository kennen.
 */
class ActorRepository(
    private val apiProvider: () -> MovieShelfApi
) {
    private val api: MovieShelfApi get() = apiProvider()

    suspend fun getActors(page: Int = 1, perPage: Int = 100): List<Actor> =
        api.getActors(page = page, perPage = perPage).data ?: emptyList()

    suspend fun getActor(actorId: Int): Actor? = api.getActor(actorId).data

    suspend fun searchActors(query: String): List<Actor> =
        api.searchActors(query).data ?: emptyList()
}
