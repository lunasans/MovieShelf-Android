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
 * Die Wunschliste.
 *
 * Sie hängt am **Benutzer** (`user_wishlist` auf der Shelf, `isWishlisted`
 * lokal) — nicht an `inCollection`, das am Film hängt und sagt, ob er zur
 * Sammlung gehört. Die erste Fassung dieser Ansicht las die falsche Spalte und
 * zeigte damit alles, was *nicht* zur Sammlung gehört. Diese Tests halten die
 * Unterscheidung fest.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WishlistTest {

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
    fun `nur vorgemerkte Titel stehen auf der Wunschliste`() = runTest {
        val dao = db.movieDao()
        dao.insert(row("Nur gesammelt").copy(isWishlisted = false))
        dao.insert(row("Vorgemerkt").copy(isWishlisted = true))
        dao.insert(row("Geloescht").copy(isWishlisted = true, isDeleted = true))

        assertEquals(listOf("Vorgemerkt"), dao.getWishlist().map { it.title })
    }

    @Test
    fun `ein Titel kann vorgemerkt und zugleich in der Sammlung sein`() = runTest {
        val dao = db.movieDao()
        // Genau der Fall, den die erste Fassung falsch machte: sie las
        // inCollection und haette diesen Titel nie gezeigt — obwohl er
        // vorgemerkt ist.
        dao.insert(row("Beides").copy(isWishlisted = true, inCollection = true))
        dao.insert(row("Weder noch").copy(isWishlisted = false, inCollection = false))

        assertEquals(listOf("Beides"), dao.getWishlist().map { it.title })
    }

    @Test
    fun `eine frisch gesetzte Vormerkung steht zur Uebertragung an`() = runTest {
        val dao = db.movieDao()
        val localId = dao.insert(row("Arrival").copy(remoteId = 1))

        assertEquals(0, dao.getPendingWishlist().size)

        dao.updateWishlisted(localId, true, "2026-08-20T10:00:00Z")
        assertEquals(listOf("Arrival"), dao.getPendingWishlist().map { it.title })

        dao.markWishlistSynced(localId, true)
        assertEquals(0, dao.getPendingWishlist().size)
    }

    @Test
    fun `die Wunschliste ist alphabetisch`() = runTest {
        val dao = db.movieDao()
        listOf("Zulu", "arrival", "Matrix").forEach {
            dao.insert(row(it).copy(isWishlisted = true))
        }

        assertEquals(listOf("arrival", "Matrix", "Zulu"), dao.getWishlist().map { it.title })
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
        actorsJson = null,
        boxsetChildrenJson = null
    )
}
