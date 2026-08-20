package info.movieshelf.data.local.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Erkennung offener eigener Bewertungen gegen eine echte Datenbank.
 *
 * Dieselbe Falle wie bei den "gesehen"-Markierungen, nur schärfer: hier ist
 * `NULL` ein gültiger Wert mit eigener Bedeutung — "noch nicht bewertet" ist
 * etwas anderes als "null Sterne". Ein schlichtes `userRating != syncedUserRating`
 * fiele bei jedem Vergleich mit NULL durch, und ausgerechnet die erste
 * Bewertung eines Films (NULL → 4) bliebe unbemerkt liegen.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UserRatingSyncTest {

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
    fun `die erste Bewertung eines Films steht zur Uebertragung an`() = runTest {
        val dao = db.movieDao()
        val localId = dao.insert(row(remoteId = 1, title = "Arrival"))

        assertEquals("Unbewertet ist nichts zu tun", 0, dao.getPendingUserRatings().size)

        dao.updateUserRating(localId, 4, "2026-08-20T10:00:00Z")

        assertEquals(listOf("Arrival"), dao.getPendingUserRatings().map { it.title })
    }

    @Test
    fun `nach der Bestaetigung des Servers steht nichts mehr an`() = runTest {
        val dao = db.movieDao()
        val localId = dao.insert(row(remoteId = 1, title = "Arrival"))
        dao.updateUserRating(localId, 4, "2026-08-20T10:00:00Z")

        dao.markUserRatingSynced(localId, 4)

        assertEquals(0, dao.getPendingUserRatings().size)
    }

    @Test
    fun `das Entfernen einer Bewertung zaehlt genauso`() = runTest {
        val dao = db.movieDao()
        val localId = dao.insert(
            row(remoteId = 1, title = "Arrival").copy(userRating = 4, syncedUserRating = 4)
        )

        // Zurueck auf "noch nicht bewertet" — der Weg, den der Server mit
        // rating=0 abbildet. Ginge er hier verloren, bliebe die Bewertung auf
        // der Shelf fuer immer stehen.
        dao.updateUserRating(localId, null, "2026-08-20T10:00:00Z")

        assertEquals(listOf("Arrival"), dao.getPendingUserRatings().map { it.title })
        assertNull(dao.getByLocalId(localId)?.userRating)
    }

    @Test
    fun `eine geaenderte Bewertung steht an`() = runTest {
        val dao = db.movieDao()
        val localId = dao.insert(
            row(remoteId = 1, title = "Arrival").copy(userRating = 3, syncedUserRating = 3)
        )

        dao.updateUserRating(localId, 5, "2026-08-20T10:00:00Z")

        assertEquals(1, dao.getPendingUserRatings().size)
    }

    @Test
    fun `ohne Server-ID gibt es nichts zu melden`() = runTest {
        val dao = db.movieDao()
        val localId = dao.insert(row(remoteId = null, title = "Nur hier"))
        dao.updateUserRating(localId, 5, "2026-08-20T10:00:00Z")

        // Der Film muss erst selbst auf der Shelf ankommen; seine Bewertung
        // geht im Abgleich danach raus.
        assertEquals(0, dao.getPendingUserRatings().size)
    }

    @Test
    fun `ein geloeschter Film meldet seine Bewertung nicht mehr`() = runTest {
        val dao = db.movieDao()
        val localId = dao.insert(row(remoteId = 1, title = "Arrival"))
        dao.updateUserRating(localId, 4, "2026-08-20T10:00:00Z")
        dao.markDeleted(localId, "2026-08-20T11:00:00Z")

        assertEquals(0, dao.getPendingUserRatings().size)
    }

    @Test
    fun `der Serverstand kommt als bestaetigt herein`() = runTest {
        val dao = db.movieDao()
        dao.upsertFromServer(
            listOf(
                MovieEntity.fromServerMovie(
                    info.movieshelf.data.model.Movie(id = 7, title = "Arrival", userRating = 5),
                    syncedAt = "2026-08-20T10:00:00Z"
                )
            )
        )

        // Was gerade vom Server kam, darf nicht sofort wieder als offene
        // Bewertung gelten — sonst schickte der naechste Lauf sie zurueck.
        assertEquals(0, dao.getPendingUserRatings().size)
        assertEquals(5, dao.getByRemoteId(7)?.userRating)
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
