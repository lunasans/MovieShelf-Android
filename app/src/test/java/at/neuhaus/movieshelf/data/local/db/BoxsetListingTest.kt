package at.neuhaus.movieshelf.data.local.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import at.neuhaus.movieshelf.data.local.LocalStats
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Boxsets in Listen und in Kennzahlen — zwei entgegengesetzte Regeln, die
 * schon einmal vertauscht waren: die Liste zeigte die Teile statt der Huelle.
 *
 * Massgeblich ist die Web-Oberflaeche: die Filmliste filtert
 * `whereNull('boxset_parent')`, die Statistik `whereDoesntHave('boxsetChildren')`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BoxsetListingTest {

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
    fun `die Liste zeigt das Boxset, nicht seine Teile`() = runTest {
        val dao = db.movieDao()
        val boxset = dao.insert(row(title = "Alien Anthology").copy(remoteId = 1, isBoxset = true))
        dao.insert(row(title = "Alien").copy(remoteId = 2, boxsetParentLocalId = boxset))
        dao.insert(row(title = "Aliens").copy(remoteId = 3, boxsetParentLocalId = boxset))
        dao.insert(row(title = "Arrival").copy(remoteId = 4))

        assertEquals(
            listOf("Alien Anthology", "Arrival"),
            dao.getAllMovies().map { it.title }.sorted()
        )
    }

    @Test
    fun `ein Teil ohne aufgeloeste Eltern-ID bleibt trotzdem draussen`() = runTest {
        val dao = db.movieDao()
        // Der Pull hat das Elternteil noch nicht zugeordnet — nur die Server-ID
        // steht da. Ohne diese Pruefung erschiene der Film doppelt.
        dao.insert(row(title = "Alien").copy(remoteId = 2, boxsetParentRemoteId = 1))
        dao.insert(row(title = "Arrival").copy(remoteId = 4))

        assertEquals(listOf("Arrival"), dao.getAllMovies().map { it.title })
    }

    @Test
    fun `die Suche findet auch einen Film im Boxset`() = runTest {
        val dao = db.movieDao()
        val boxset = dao.insert(row(title = "Alien Anthology").copy(remoteId = 1, isBoxset = true))
        dao.insert(row(title = "Alien").copy(remoteId = 2, boxsetParentLocalId = boxset))

        assertEquals(
            listOf("Alien", "Alien Anthology"),
            dao.searchMovies("Alien").map { it.title }.sorted()
        )
    }

    @Test
    fun `im Boxset stehen seine Teile - nach Jahr, dann Titel`() = runTest {
        val dao = db.movieDao()
        val boxset = dao.insert(row(title = "Alien Anthology").copy(remoteId = 1, isBoxset = true))
        dao.insert(row(title = "Aliens").copy(remoteId = 2, year = 1986, boxsetParentLocalId = boxset))
        dao.insert(row(title = "Alien").copy(remoteId = 3, year = 1979, boxsetParentLocalId = boxset))
        // Nicht Teil des Boxsets und darf dort nicht auftauchen.
        dao.insert(row(title = "Arrival").copy(remoteId = 4, year = 2016))

        assertEquals(
            listOf("Alien", "Aliens"),
            dao.getBoxsetChildren(boxset).map { it.title }
        )
    }

    @Test
    fun `ein aussortierter Teil steht nicht mehr im Boxset`() = runTest {
        val dao = db.movieDao()
        val boxset = dao.insert(row(title = "Alien Anthology").copy(remoteId = 1, isBoxset = true))
        dao.insert(row(title = "Alien").copy(remoteId = 2, boxsetParentLocalId = boxset))
        dao.insert(
            row(title = "Alien 3").copy(remoteId = 3, boxsetParentLocalId = boxset, inCollection = false)
        )

        assertEquals(listOf("Alien"), dao.getBoxsetChildren(boxset).map { it.title })
    }

    @Test
    fun `die Statistik zaehlt umgekehrt - die Teile, nicht die Huelle`() = runTest {
        val dao = db.movieDao()
        val boxset = dao.insert(row(title = "Alien Anthology").copy(remoteId = 1, isBoxset = true))
        dao.insert(row(title = "Alien").copy(remoteId = 2, boxsetParentLocalId = boxset))
        dao.insert(row(title = "Aliens").copy(remoteId = 3, boxsetParentLocalId = boxset))
        dao.insert(row(title = "Arrival").copy(remoteId = 4))

        // Zwei Teile plus ein Einzelfilm — die Huelle ist kein besessener Film.
        assertEquals(3, LocalStats.from(dao.getAllForStats()).totalFilms)
    }

    @Test
    fun `der Zaehler meint die Filme, nicht die Zeilen der Liste`() = runTest {
        val dao = db.movieDao()
        val boxset = dao.insert(row(title = "Alien Anthology").copy(remoteId = 1, isBoxset = true))
        dao.insert(row(title = "Alien").copy(remoteId = 2, boxsetParentLocalId = boxset))
        dao.insert(row(title = "Aliens").copy(remoteId = 3, boxsetParentLocalId = boxset))
        dao.insert(row(title = "Arrival").copy(remoteId = 4))
        dao.insert(row(title = "Fargo").copy(remoteId = 5, collectionType = "Serie"))

        // Die Liste zeigt zwei Eintraege: das Boxset und "Arrival".
        assertEquals(2, dao.getAllMovies().count { it.collectionType != "Serie" })
        // Gezaehlt werden aber drei Filme — die beiden Teile und "Arrival".
        assertEquals(3, dao.countFilmsInCollection())
        assertEquals(1, dao.countSeriesInCollection())
        // Und der Zaehler stimmt mit der Statistik ueberein.
        assertEquals(
            LocalStats.from(dao.getAllForStats()).totalFilms,
            dao.countFilmsInCollection() + dao.countSeriesInCollection()
        )
    }

    private fun row(title: String) = MovieEntity(
        remoteId = null,
        title = title,
        year = 2016,
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
        syncedWatched = false,
        actorsJson = null,
        boxsetChildrenJson = null
    )
}
