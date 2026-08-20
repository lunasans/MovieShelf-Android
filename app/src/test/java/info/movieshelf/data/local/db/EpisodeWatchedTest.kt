package info.movieshelf.data.local.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Gesehen-Markierungen einzelner Folgen.
 *
 * Dieselbe Bauart wie bei Filmen und Bewertungen: benutzergebunden, eigener
 * Endpunkt, also eigener bestätigter Stand. Der heikle Teil ist der Pull —
 * `upsertSeries` schreibt die Folgen neu, und eine noch nicht übertragene
 * Markierung darf dabei nicht verlorengehen.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EpisodeWatchedTest {

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
    fun `eine frisch markierte Folge steht zur Uebertragung an`() = runTest {
        val series = db.seriesDao()
        val movieLocalId = db.movieDao().insert(serie())
        series.upsertSeries(movieLocalId, listOf(staffelMitFolge(remoteId = 100)))

        val folge = series.getEpisodes(series.getSeasons(movieLocalId).single().localId).single()
        assertEquals(0, series.getPendingEpisodeWatched().size)

        series.updateEpisodeWatched(folge.localId, true, "2026-08-20T10:00:00Z")

        assertEquals(1, series.getPendingEpisodeWatched().size)
    }

    @Test
    fun `nach der Bestaetigung steht nichts mehr an`() = runTest {
        val series = db.seriesDao()
        val movieLocalId = db.movieDao().insert(serie())
        series.upsertSeries(movieLocalId, listOf(staffelMitFolge(remoteId = 100)))
        val folge = series.getEpisodes(series.getSeasons(movieLocalId).single().localId).single()

        series.updateEpisodeWatched(folge.localId, true, "2026-08-20T10:00:00Z")
        series.markEpisodeWatchedSynced(folge.localId, true)

        assertEquals(0, series.getPendingEpisodeWatched().size)
    }

    @Test
    fun `eine offene Markierung ueberlebt den naechsten Pull`() = runTest {
        val series = db.seriesDao()
        val movieLocalId = db.movieDao().insert(serie())
        series.upsertSeries(movieLocalId, listOf(staffelMitFolge(remoteId = 100)))
        val seasonId = series.getSeasons(movieLocalId).single().localId
        val folge = series.getEpisodes(seasonId).single()

        series.updateEpisodeWatched(folge.localId, true, "2026-08-20T10:00:00Z")

        // Der Server weiss noch nichts davon und liefert "ungesehen".
        series.upsertSeries(movieLocalId, listOf(staffelMitFolge(remoteId = 100, watched = false)))

        val danach = series.getEpisodes(seasonId).single()
        assertTrue("Die Markierung darf nicht verlorengehen", danach.isWatched)
        assertEquals(1, series.getPendingEpisodeWatched().size)
    }

    @Test
    fun `ohne offene Markierung gewinnt der Serverstand`() = runTest {
        val series = db.seriesDao()
        val movieLocalId = db.movieDao().insert(serie())
        series.upsertSeries(movieLocalId, listOf(staffelMitFolge(remoteId = 100)))
        val seasonId = series.getSeasons(movieLocalId).single().localId

        // Auf einem anderen Geraet gesehen markiert.
        series.upsertSeries(movieLocalId, listOf(staffelMitFolge(remoteId = 100, watched = true)))

        val danach = series.getEpisodes(seasonId).single()
        assertTrue(danach.isWatched)
        assertEquals("Vom Server heisst bestaetigt", 0, series.getPendingEpisodeWatched().size)
    }

    private fun staffelMitFolge(remoteId: Int, watched: Boolean = false) = SeasonWithEpisodes(
        season = SeasonEntity(remoteId = 10, movieLocalId = 0, seasonNumber = 1, title = "Staffel 1"),
        episodes = listOf(
            EpisodeEntity(
                remoteId = remoteId,
                seasonLocalId = 0,
                episodeNumber = 1,
                title = "Folge 1",
                isWatched = watched,
                syncedWatched = watched
            )
        )
    )

    private fun serie() = MovieEntity(
        remoteId = 1,
        title = "Dark",
        year = 2017,
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
        collectionType = "Serie",
        createdAt = "2026-01-01T00:00:00Z",
        updatedAt = "2026-01-01T00:00:00Z",
        syncedAt = "2026-01-01T00:00:00Z",
        actorsJson = null,
        boxsetChildrenJson = null
    )
}
