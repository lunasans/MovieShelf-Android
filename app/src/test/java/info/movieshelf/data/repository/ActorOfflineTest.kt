package info.movieshelf.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import info.movieshelf.data.api.MovieShelfApi
import info.movieshelf.data.local.db.ActorEntity
import info.movieshelf.data.local.db.FilmActorCrossRef
import info.movieshelf.data.local.db.MovieEntity
import info.movieshelf.data.local.db.MovieShelfDatabase
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Ein Darstellerprofil muss ohne Server dastehen.
 *
 * Die Detailansicht fragte frueher ausschliesslich `GET /actors/{id}`. War die
 * Shelf nicht erreichbar — im gemeldeten Fall ein 521 —, blieb der Bildschirm
 * leer, obwohl Biografie, Geburtstag und Bild laengst in `actors` standen und
 * die Filme der Person ueber `film_actor` verknuepft waren. Ein stiller
 * Fehler: die Daten waren da, nur fragte sie niemand ab.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ActorOfflineTest {

    private lateinit var db: MovieShelfDatabase
    private lateinit var repository: ActorRepository

    /** Eine Shelf, die nicht antwortet — wie bei einem 521. */
    private val serverDown: () -> MovieShelfApi = {
        throw IllegalStateException("RetrofitClient not initialized")
    }

    @Before
    fun setUp() = runTest {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MovieShelfDatabase::class.java
        ).build()
        repository = ActorRepository(db.actorDao(), serverDown)

        val actorLocalId = db.actorDao().insert(
            ActorEntity(
                remoteId = 7,
                name = "Sigourney Weaver",
                bio = "US-amerikanische Schauspielerin.",
                birthday = "1949-10-08",
                placeOfBirth = "New York City",
                imagePath = "/data/actors/7.jpg"
            )
        )
        val movieLocalId = db.movieDao().insert(film(1, "Alien", 1979))
        db.actorDao().setCast(
            listOf(FilmActorCrossRef(movieLocalId = movieLocalId, actorLocalId = actorLocalId))
        )
    }

    @After
    fun tearDown() = db.close()

    private fun film(
        remoteId: Int,
        title: String,
        year: Int,
        isDeleted: Boolean = false,
        inCollection: Boolean? = true
    ) = MovieEntity(
        remoteId = remoteId,
        title = title,
        year = year,
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
        inCollection = inCollection,
        collectionType = "Film",
        createdAt = "2026-01-01T00:00:00Z",
        updatedAt = "2026-01-01T00:00:00Z",
        syncedAt = "2026-01-01T00:00:00Z",
        actorsJson = null,
        boxsetChildrenJson = null,
        isDeleted = isDeleted
    )

    @Test
    fun `Profil kommt aus der Datenbank, wenn der Server schweigt`() = runTest {
        val localId = db.actorDao().findLocalIdByRemoteId(7)!!

        val actor = repository.getLocalActor(localId)

        assertNotNull(actor)
        assertEquals("Sigourney Weaver", actor!!.name)
        assertEquals("1949-10-08", actor.birthDate)
        assertEquals("New York City", actor.placeOfBirth)
        assertEquals("US-amerikanische Schauspielerin.", actor.biography)
        // Der heruntergeladene Pfad, nicht die Adresse: offline waere eine
        // URL wertlos.
        assertEquals("/data/actors/7.jpg", actor.imageUrl)
    }

    @Test
    fun `die Filme der Person kommen aus der eigenen Sammlung`() = runTest {
        val localId = db.actorDao().findLocalIdByRemoteId(7)!!

        val filme = repository.getLocalActor(localId)!!.movies

        assertEquals(listOf("Alien"), filme?.map { it.title })
    }

    @Test
    fun `geloeschte und ausgelagerte Filme bleiben draussen, neueste zuerst`() = runTest {
        // Dieselbe Regel wie in der Desktop-App (getMoviesForActor): sonst
        // stuende in der Filmografie ein Titel, den die Sammlung nicht mehr
        // fuehrt — und die beiden Oberflaechen zeigten verschiedene Listen.
        val actorLocalId = db.actorDao().findLocalIdByRemoteId(7)!!
        listOf(
            film(2, "Aliens", 1986),
            film(3, "Geloescht", 1990, isDeleted = true),
            film(4, "Nicht in Sammlung", 1992, inCollection = false),
            film(5, "Alte Zeile ohne Angabe", 2000, inCollection = null)
        ).forEach { titel ->
            val id = db.movieDao().insert(titel)
            db.actorDao().setCast(
                listOf(FilmActorCrossRef(movieLocalId = id, actorLocalId = actorLocalId))
            )
        }

        val filme = repository.getLocalActor(actorLocalId)!!.movies!!.map { it.title }

        assertEquals(listOf("Alte Zeile ohne Angabe", "Aliens", "Alien"), filme)
    }

    @Test
    fun `die lokale ID bleibt am Profil haengen`() = runTest {
        val localId = db.actorDao().findLocalIdByRemoteId(7)!!

        // Ohne sie fuehrt kein Weg zurueck in die Datenbank — im
        // eigenstaendigen Betrieb gibt es gar keine Server-ID.
        assertEquals(localId, repository.getLocalActor(localId)!!.localId)
    }

    @Test
    fun `unbekannte Person liefert null statt eines leeren Profils`() = runTest {
        assertNull(repository.getLocalActor(999L))
    }

    @Test
    fun `die Liste zeigt nur Personen mit Filmen in der Sammlung`() = runTest {
        // Bleibt aus einem geloeschten Film uebrig und gehoert nicht in die
        // Liste: zu ihr fuehrt kein Film mehr.
        db.actorDao().insert(ActorEntity(remoteId = 8, name = "Niemand"))

        val namen = repository.getLocalActors().map { it.name }

        assertEquals(listOf("Sigourney Weaver"), namen)
    }

    @Test
    fun `die Namenssuche geht auf die lokale Tabelle`() = runTest {
        assertTrue(repository.searchLocalActors("Weaver").isNotEmpty())
        assertTrue(repository.searchLocalActors("Weav").isNotEmpty())
        assertTrue(repository.searchLocalActors("Ripley").isEmpty())
    }
}
