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
 * Erkennung offener "gesehen"-Markierungen gegen eine echte Datenbank.
 *
 * Der Vergleich ist heikler, als er aussieht: in SQL faellt jeder Vergleich mit
 * NULL durch, und beide Spalten duerfen NULL sein. Mit `!=` bliebe eine frisch
 * gesetzte Markierung unbemerkt liegen — genau der Fall, in dem sie verloren
 * ging.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WatchedSyncTest {

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
    fun `frisch markierter Film steht zur Uebertragung an`() = runTest {
        val dao = db.movieDao()
        val localId = dao.insert(row(remoteId = 1, title = "Arrival"))

        assertEquals("Unveraendert ist nichts zu tun", 0, dao.getPendingWatched().size)

        dao.updateWatched(localId, true, "2026-08-10T10:00:00Z")

        assertEquals(listOf("Arrival"), dao.getPendingWatched().map { it.title })
    }

    @Test
    fun `nach der Bestaetigung des Servers steht nichts mehr an`() = runTest {
        val dao = db.movieDao()
        val localId = dao.insert(row(remoteId = 1, title = "Arrival"))
        dao.updateWatched(localId, true, "2026-08-10T10:00:00Z")

        dao.markWatchedSynced(localId, true)

        assertEquals(0, dao.getPendingWatched().size)
    }

    @Test
    fun `ohne Server-ID gibt es nichts zu melden`() = runTest {
        val dao = db.movieDao()
        val localId = dao.insert(row(remoteId = null, title = "Nur hier"))
        dao.updateWatched(localId, true, "2026-08-10T10:00:00Z")

        assertEquals(0, dao.getPendingWatched().size)
    }

    @Test
    fun `das Zuruecknehmen zaehlt genauso`() = runTest {
        val dao = db.movieDao()
        val localId = dao.insert(
            row(remoteId = 1, title = "Arrival").copy(isWatched = true, syncedWatched = true)
        )

        dao.updateWatched(localId, false, "2026-08-10T10:00:00Z")

        assertEquals(listOf("Arrival"), dao.getPendingWatched().map { it.title })
    }

    private fun row(remoteId: Int?, title: String) = MovieEntity(
        remoteId = remoteId,
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
