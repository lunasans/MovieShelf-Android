package at.neuhaus.movieshelf.data.repository

import at.neuhaus.movieshelf.data.api.MovieShelfApi
import at.neuhaus.movieshelf.data.model.ListItemRef
import at.neuhaus.movieshelf.data.model.ListMutationRequest
import at.neuhaus.movieshelf.data.model.Movie
import at.neuhaus.movieshelf.data.model.MovieListSummary

/**
 * Eigene Listen.
 *
 * Noch ohne lokale Tabellen — die stehen bereits (`lists`, `list_items`,
 * `list_item_tombstones`), werden aber erst mit dem Listen-Abgleich in Phase 4
 * gefüllt. Bis dahin bündelt diese Klasse die Netzaufrufe, damit die
 * ViewModels schon jetzt nur noch das Repository kennen.
 */
class ListRepository(
    // Provider statt fester Instanz: nach einem Server-Wechsel
    // (RetrofitClient.initialize) muss die aktuelle API benutzt werden.
    private val apiProvider: () -> MovieShelfApi
) {
    private val api: MovieShelfApi get() = apiProvider()

    suspend fun getLists(): List<MovieListSummary> = api.getLists().lists ?: emptyList()

    suspend fun getList(listId: Int) = api.getList(listId)

    suspend fun createList(name: String) {
        api.createList(ListMutationRequest(name))
    }

    suspend fun renameList(listId: Int, name: String, items: List<ListItemRef>) {
        api.updateList(listId, ListMutationRequest(name, items))
    }

    suspend fun setItems(listId: Int, name: String, items: List<ListItemRef>) {
        api.updateList(listId, ListMutationRequest(name, items))
    }

    suspend fun deleteList(listId: Int) {
        api.deleteList(listId)
    }

    /**
     * Film einer Liste hinzufügen. Die Shelf erwartet beim Speichern die
     * vollständige Mitgliederliste, nicht das einzelne neue Element — deshalb
     * wird der bestehende Inhalt ergänzt statt ersetzt. Doppelte Einträge
     * werden abgefangen, sonst stünde der Film zweimal in der Liste.
     */
    suspend fun addMovieToList(list: MovieListSummary, movie: Movie) {
        val items = ((list.items ?: emptyList()) + ListItemRef(movie.itemType ?: "movie", movie.id))
            .distinctBy { it.type to it.id }
        setItems(list.id, list.name ?: "Liste", items)
    }
}
