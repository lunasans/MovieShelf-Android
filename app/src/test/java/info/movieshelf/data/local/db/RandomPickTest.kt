package info.movieshelf.data.local.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Die Auslosung darf nur ziehen, was man auch ansehen kann.
 *
 * Ein Boxset ist eine Hülle — niemand schaut sie, gemeint sind die Filme
 * darin. Gelöschte und nicht gesammelte Zeilen zählen ebenso wenig. Ohne
 * diese Regeln schlüge die Auslosung regelmäßig etwas vor, das gar nicht da ist.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RandomPickTest {

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
    fun `Boxsets, geloeschte und nicht gesammelte Titel bleiben aussen vor`() = runTest {
        val dao = db.movieDao()
        dao.insert(row("Boxset").copy(isBoxset = true))
        dao.insert(row("Geloescht").copy(isDeleted = true))
        dao.insert(row("Wunschliste").copy(inCollection = false))
        dao.insert(row("Arrival"))

        // Mehrfach ziehen: bei einer Zufallsabfrage sagt ein einzelner Treffer
        // nichts darüber, ob die Bedingungen greifen.
        repeat(20) {
            assertEquals("Arrival", dao.randomMovie(null)?.title)
        }
    }

    @Test
    fun `die Kategorie schraenkt die Auslosung ein`() = runTest {
        val dao = db.movieDao()
        dao.insert(row("Ein Film").copy(collectionType = "Film"))
        dao.insert(row("Eine Serie").copy(collectionType = "Serie"))

        repeat(10) {
            assertEquals("Eine Serie", dao.randomMovie("Serie")?.title)
            assertEquals("Ein Film", dao.randomMovie("Film")?.title)
        }

        // Ohne Angabe kommt beides in Frage.
        val gezogen = (1..40).mapNotNull { dao.randomMovie(null)?.title }.toSet()
        assertTrue("Beide Arten müssen vorkommen", gezogen.size == 2)
    }

    @Test
    fun `eine leere Sammlung ergibt keinen Vorschlag`() = runTest {
        assertNull(db.movieDao().randomMovie(null))
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
