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
 * Die Wunschliste ist dieselbe Tabelle wie die Sammlung, nur ohne Besitz
 * (`in_collection = 0`). Genau deshalb ist die Abgrenzung heikel: ein zu
 * weiter Filter zeigte die halbe Sammlung als "vorgemerkt".
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
        dao.insert(row("In der Sammlung").copy(inCollection = true))
        dao.insert(row("Vorgemerkt").copy(inCollection = false))
        dao.insert(row("Geloescht").copy(inCollection = false, isDeleted = true))

        assertEquals(listOf("Vorgemerkt"), dao.getWishlist().map { it.title })
    }

    @Test
    fun `eine Zeile ohne Angabe gilt als Sammlung, nicht als Wunsch`() = runTest {
        val dao = db.movieDao()
        // Alte Zeilen und lokal angelegte Filme koennen inCollection = null
        // tragen. Sie hier zu zeigen hiesse, die halbe Sammlung als vorgemerkt
        // auszugeben.
        dao.insert(row("Ohne Angabe").copy(inCollection = null))

        assertEquals(0, dao.getWishlist().size)
    }

    @Test
    fun `die Wunschliste ist alphabetisch`() = runTest {
        val dao = db.movieDao()
        listOf("Zulu", "arrival", "Matrix").forEach {
            dao.insert(row(it).copy(inCollection = false))
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
