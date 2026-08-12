package info.movieshelf.data.local.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Prüft die Fremdschlüssel gegen eine echte SQLite-Datenbank.
 *
 * Diese Tests gibt es, weil ein Darsteller beim Einspielen aus allen Filmen
 * bis auf den zuletzt verarbeiteten herausfiel: bestehende Zeilen wurden mit
 * `INSERT OR REPLACE` geschrieben, was in SQLite ein Löschen mit
 * anschließendem Einfügen ist — und auf den Fremdschlüsseln liegt
 * `ON DELETE CASCADE`. Handgeschriebene Test-Doubles bilden das nicht ab; nur
 * eine echte Datenbank zeigt es.
 */
@RunWith(RobolectricTestRunner::class)
// Robolectric kennt das compileSdk der App (36) noch nicht. Fuer SQLite und
// Room ist die Version ohne Belang, deshalb die hoechste unterstuetzte.
@Config(sdk = [34])
class CascadeTest {

    private lateinit var db: MovieShelfDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MovieShelfDatabase::class.java
        ).build()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `ein Darsteller bleibt in allen seinen Filmen`() = runTest {
        val movieDao = db.movieDao()
        val actorDao = db.actorDao()

        val ersterFilm = movieDao.insert(serverMovie(remoteId = 1, title = "Sing Street"))
        val zweiterFilm = movieDao.insert(serverMovie(remoteId = 2, title = "Midsommar"))

        // Dieselbe Person, aus zwei Filmen eingespielt.
        actorDao.upsertFromServer(listOf(reynor()))
        val reynorId = actorDao.findLocalIdByRemoteId(REYNOR_REMOTE_ID)!!
        actorDao.setCast(listOf(crossRef(ersterFilm, reynorId)))

        actorDao.upsertFromServer(listOf(reynor()))
        actorDao.setCast(listOf(crossRef(zweiterFilm, reynorId)))

        assertEquals(
            "Das zweite Einspielen darf die Verknüpfung zum ersten Film nicht löschen",
            listOf("Jack Reynor"),
            actorDao.getCastOf(ersterFilm).map { it.name }
        )
        assertEquals(listOf("Jack Reynor"), actorDao.getCastOf(zweiterFilm).map { it.name })
        assertEquals("Die Person darf nur einmal angelegt werden", 1, actorDao.getAll().size)
    }

    @Test
    fun `ein erneut eingespielter Film behaelt seine Besetzung`() = runTest {
        val movieDao = db.movieDao()
        val actorDao = db.actorDao()

        movieDao.upsertFromServer(listOf(serverMovie(remoteId = 1, title = "Sing Street")))
        val filmId = movieDao.getByRemoteId(1)!!.localId
        actorDao.upsertFromServer(listOf(reynor()))
        val reynorId = actorDao.findLocalIdByRemoteId(REYNOR_REMOTE_ID)!!
        actorDao.setCast(listOf(crossRef(filmId, reynorId)))

        // Derselbe Film kommt beim naechsten Abgleich erneut herein.
        movieDao.upsertFromServer(listOf(serverMovie(remoteId = 1, title = "Sing Street")))

        assertEquals(
            "Die localId muss stabil bleiben, sonst zeigen Verknuepfungen ins Leere",
            filmId,
            movieDao.getByRemoteId(1)!!.localId
        )
        assertEquals(listOf("Jack Reynor"), actorDao.getCastOf(filmId).map { it.name })
    }

    @Test
    fun `eine erneut eingespielte Liste behaelt ihre Eintraege`() = runTest {
        val listDao = db.listDao()

        listDao.upsertFromServer(listOf(ListEntity(remoteId = 7, name = "Wunschliste")))
        val listId = listDao.findLocalIdByRemoteId(7)!!
        listDao.addItem(
            ListItemEntity(listLocalId = listId, itemLocalId = 1, itemType = ListItemType.MOVIE)
        )

        listDao.upsertFromServer(listOf(ListEntity(remoteId = 7, name = "Merkliste")))

        assertEquals("Merkliste", listDao.getAll().single().name)
        assertEquals(1, listDao.getItems(listId).size)
    }

    private companion object {
        const val REYNOR_REMOTE_ID = 7012

        fun reynor() = ActorEntity(
            remoteId = REYNOR_REMOTE_ID,
            name = "Jack Reynor",
            createdAt = "2026-01-01T00:00:00Z",
            updatedAt = "2026-01-01T00:00:00Z"
        )

        fun serverMovie(remoteId: Int, title: String) = MovieEntity(
            remoteId = remoteId,
            title = title,
            year = 2020,
            rating = null,
            genre = null,
            overview = null,
            runtime = null,
            director = null,
            coverUrl = null,
            backdropUrl = null,
            trailerUrl = null,
            edition = null,
            regionCode = null,
            discLocation = null,
            purchaseDate = null,
            purchasePrice = null,
            condition = null,
            viewCount = 0,
            isWatched = false,
            tmdbId = null,
            ratingAge = null,
            tag = null,
            isBoxset = false,
            inCollection = true,
            collectionType = "Film",
            createdAt = "2026-01-01T00:00:00Z",
            updatedAt = "2026-01-01T00:00:00Z",
            syncedAt = "2026-01-01T00:00:00Z",
            actorsJson = null,
            boxsetChildrenJson = null
        )

        fun crossRef(movieLocalId: Long, actorLocalId: Long) = FilmActorCrossRef(
            movieLocalId = movieLocalId,
            actorLocalId = actorLocalId,
            role = null,
            isMainRole = true,
            sortOrder = 0
        )
    }
}
