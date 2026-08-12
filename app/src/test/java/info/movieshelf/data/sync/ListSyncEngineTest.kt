package info.movieshelf.data.sync

import info.movieshelf.data.local.db.ExternalMovieDao
import info.movieshelf.data.local.db.ExternalMovieEntity
import info.movieshelf.data.local.db.ListDao
import info.movieshelf.data.local.db.ListEntity
import info.movieshelf.data.local.db.ListItemEntity
import info.movieshelf.data.local.db.ListItemTombstoneEntity
import info.movieshelf.data.local.db.ListItemType
import info.movieshelf.data.model.ListDetailResponse
import info.movieshelf.data.model.ListItemRef
import info.movieshelf.data.model.Movie
import info.movieshelf.data.model.MovieListSummary
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Der Listen-Abgleich vereinigt, statt zu spiegeln. Die Tombstones sind dabei
 * der heikle Teil: ohne sie sieht ein lokal entfernter Eintrag exakt aus wie
 * ein serverseitig neu hinzugekommener.
 */
class ListSyncEngineTest {

    @Test
    fun `vereinigt Eintraege beider Seiten`() = runBlocking {
        val lists = FakeListDao()
        val movies = FakeMovieDao()
        val listLocalId = lists.seedList(remoteId = 1, name = "Watchlist")
        // Serverseitig bekannt: Film 10. Lokal hinzugefuegt: Film 11.
        movies.seed(localId = 1, remoteId = 10)
        movies.seed(localId = 2, remoteId = 11)
        lists.items += ListItemEntity(listLocalId, ListItemType.MOVIE, 2)

        val api = FakeListSyncApi(
            lists = listOf(MovieListSummary(id = 1, name = "Watchlist")),
            detail = ListDetailResponse(id = 1, name = "Watchlist", items = listOf(listMovie(10)))
        )

        val result = ListSyncEngine(lists, movies, FakeExternalMovieDao()) { api }.sync()

        assertEquals(1, result.itemsAdded)
        // Lokal stehen jetzt beide.
        assertEquals(setOf(1L, 2L), lists.items.map { it.itemLocalId }.toSet())
        // Und der Server hat den lokalen Zugang bekommen.
        assertEquals(setOf(10, 11), api.pushedItems.map { it.id }.toSet())
    }

    @Test
    fun `entfernter Eintrag kommt nicht zurueck`() = runBlocking {
        val lists = FakeListDao()
        val movies = FakeMovieDao()
        val listLocalId = lists.seedList(remoteId = 1, name = "Watchlist")
        movies.seed(localId = 1, remoteId = 10)
        // Lokal entfernt, Merker gesetzt — der Server kennt den Eintrag noch.
        lists.tombstones += ListItemTombstoneEntity(listLocalId, ListItemType.MOVIE, 10)

        val api = FakeListSyncApi(
            lists = listOf(MovieListSummary(id = 1, name = "Watchlist")),
            detail = ListDetailResponse(id = 1, name = "Watchlist", items = listOf(listMovie(10)))
        )

        val result = ListSyncEngine(lists, movies, FakeExternalMovieDao()) { api }.sync()

        assertEquals(0, result.itemsAdded)
        assertTrue("Der entfernte Eintrag darf nicht zurueckkehren", lists.items.isEmpty())
        assertEquals(1, result.itemsRemoved)
        assertTrue("Der Server muss die Entfernung uebernehmen", api.pushedItems.isEmpty())
    }

    @Test
    fun `Merker wird erst nach erfolgreichem Push verworfen`() = runBlocking {
        val lists = FakeListDao()
        val movies = FakeMovieDao()
        val listLocalId = lists.seedList(remoteId = 1, name = "Watchlist")
        movies.seed(localId = 1, remoteId = 10)
        lists.tombstones += ListItemTombstoneEntity(listLocalId, ListItemType.MOVIE, 10)

        val api = object : FakeListSyncApi(
            lists = listOf(MovieListSummary(id = 1, name = "Watchlist")),
            detail = ListDetailResponse(id = 1, name = "Watchlist", items = listOf(listMovie(10)))
        ) {
            override suspend fun setItems(listId: Int, name: String, items: List<ListItemRef>) {
                throw IllegalStateException("Server nicht erreichbar")
            }
        }

        val result = ListSyncEngine(lists, movies, FakeExternalMovieDao()) { api }.sync()

        assertEquals(1, result.errors.size)
        // Sonst wuesste nach dem Fehlschlag niemand mehr, dass etwas entfernt wurde.
        assertEquals(1, lists.tombstones.size)
    }

    @Test
    fun `externer Titel wird lokal angelegt`() = runBlocking {
        val lists = FakeListDao()
        val externals = FakeExternalMovieDao()
        lists.seedList(remoteId = 1, name = "Wunschzettel")

        val api = FakeListSyncApi(
            lists = listOf(MovieListSummary(id = 1, name = "Wunschzettel")),
            detail = ListDetailResponse(
                id = 1,
                name = "Wunschzettel",
                items = listOf(listMovie(77).copy(title = "Dune 3", itemType = ListItemType.EXTERNAL))
            )
        )

        val result = ListSyncEngine(lists, FakeMovieDao(), externals) { api }.sync()

        assertEquals(1, result.itemsAdded)
        assertEquals("Dune 3", externals.rows.single().title)
    }

    private fun listMovie(id: Int) = Movie(id = id, itemType = ListItemType.MOVIE)
}

private fun FakeMovieDao.seed(localId: Long, remoteId: Int) {
    rows += info.movieshelf.data.local.db.MovieEntity(
        localId = localId, remoteId = remoteId, title = "Film $remoteId", year = 2020,
        rating = null, genre = null, overview = null, runtime = null, director = null,
        coverUrl = null, backdropUrl = null, trailerUrl = null, edition = null,
        regionCode = null, discLocation = null, purchaseDate = null, purchasePrice = null,
        condition = null, viewCount = 0, isWatched = false, tmdbId = null, ratingAge = null,
        tag = null, isBoxset = false, inCollection = true, collectionType = "Film",
        createdAt = null, actorsJson = null, boxsetChildrenJson = null
    )
}

open class FakeListDao : ListDao {
    val lists = mutableListOf<ListEntity>()
    val items = mutableListOf<ListItemEntity>()
    val tombstones = mutableListOf<ListItemTombstoneEntity>()

    fun seedList(remoteId: Int, name: String): Long {
        val localId = (lists.maxOfOrNull { it.localId } ?: 0L) + 1
        lists += ListEntity(localId = localId, remoteId = remoteId, name = name)
        return localId
    }

    override suspend fun findLocalIdByRemoteId(remoteId: Int): Long? =
        lists.firstOrNull { it.remoteId == remoteId }?.localId

    override suspend fun update(list: ListEntity) {
        lists.removeAll { it.localId == list.localId }
        lists += list
    }

    override suspend fun insert(list: ListEntity): Long {
        // Wie in SQLite: ein Einfuegen mit bekannter ID ersetzt die Zeile und
        // raeumt per CASCADE ihre Eintraege ab. Bestehendes gehoert in update().
        if (list.localId != 0L) {
            throw AssertionError(
                "Bestehende Listen muessen ueber update() laufen, sonst " +
                    "loescht CASCADE ihre Eintraege"
            )
        }
        val id = (lists.maxOfOrNull { it.localId } ?: 0L) + 1
        lists += list.copy(localId = id)
        return id
    }

    override suspend fun addItem(item: ListItemEntity) {
        if (items.none { it.listLocalId == item.listLocalId && it.itemType == item.itemType && it.itemLocalId == item.itemLocalId }) {
            items += item
        }
    }

    override suspend fun getItems(listLocalId: Long): List<ListItemEntity> =
        items.filter { it.listLocalId == listLocalId }

    override suspend fun getTombstones(listLocalId: Long): List<ListItemTombstoneEntity> =
        tombstones.filter { it.listLocalId == listLocalId }

    override suspend fun clearTombstones(listLocalId: Long) {
        tombstones.removeAll { it.listLocalId == listLocalId }
    }

    override suspend fun getAll(): List<ListEntity> = lists
    override suspend fun getByLocalId(localId: Long): ListEntity? = lists.firstOrNull { it.localId == localId }
    override suspend fun getDirty(): List<ListEntity> = throw AssertionError("nicht benutzt")
    override suspend fun delete(localId: Long) = throw AssertionError("nicht benutzt")
    override suspend fun removeItem(listLocalId: Long, itemType: String, itemLocalId: Long) =
        throw AssertionError("nicht benutzt")
    override suspend fun addTombstone(tombstone: ListItemTombstoneEntity) {
        tombstones += tombstone
    }
}

open class FakeExternalMovieDao : ExternalMovieDao {
    val rows = mutableListOf<ExternalMovieEntity>()

    override suspend fun getByLocalId(localId: Long): ExternalMovieEntity? =
        rows.firstOrNull { it.localId == localId }

    override suspend fun findLocalIdByRemoteId(remoteId: Int): Long? =
        rows.firstOrNull { it.remoteId == remoteId }?.localId

    override suspend fun insert(movie: ExternalMovieEntity): Long {
        val id = (rows.maxOfOrNull { it.localId } ?: 0L) + 1
        rows += movie.copy(localId = id)
        return id
    }

    override suspend fun getDirty(): List<ExternalMovieEntity> = throw AssertionError("nicht benutzt")
    override suspend fun delete(localId: Long) = throw AssertionError("nicht benutzt")
}

open class FakeListSyncApi(
    private val lists: List<MovieListSummary> = emptyList(),
    private val detail: ListDetailResponse = ListDetailResponse(id = 0)
) : ListSyncApi {

    var pushedItems: List<ListItemRef> = emptyList()
        private set

    override suspend fun getLists(): List<MovieListSummary> = lists
    override suspend fun getList(listId: Int): ListDetailResponse = detail
    override suspend fun setItems(listId: Int, name: String, items: List<ListItemRef>) {
        pushedItems = items
    }
}
