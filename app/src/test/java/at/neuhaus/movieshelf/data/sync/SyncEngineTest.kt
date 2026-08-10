package at.neuhaus.movieshelf.data.sync

import at.neuhaus.movieshelf.data.local.db.MovieEntity
import at.neuhaus.movieshelf.data.local.db.SettingKeys
import at.neuhaus.movieshelf.data.model.ExportResponse
import at.neuhaus.movieshelf.data.model.Movie
import at.neuhaus.movieshelf.data.model.MovieUpdateRequest
import at.neuhaus.movieshelf.data.model.SingleMovieResponse
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Prüft die Regeln, die in der Desktop-App teuer erkauft wurden. Sie sind
 * deshalb hier festgeschrieben: ein Verstoß verliert Daten still, ohne dass
 * irgendwo ein Fehler auftaucht.
 */
class SyncEngineTest {

    // ── Der Pull darf lokale Änderungen nicht überschreiben ──────────────────

    @Test
    fun `pull laesst abweichende Zeilen unangetastet`() = runBlocking {
        val dao = FakeMovieDao()
        val local = movie(localId = 1, remoteId = 10, title = "Lokal geaendert")
            .copy(updatedAt = "2026-08-10T12:00:00Z", syncedAt = "2026-08-10T10:00:00Z")
        dao.rows += local

        val engine = engine(
            dao,
            FakeSettingDao(),
            FakeSyncApi(export = ExportResponse(exportedAt = "2026-08-10T13:00:00Z", movies = listOf(
                serverMovie(id = 10, title = "Serverstand")
            )))
        )

        val result = engine.pull()

        assertEquals(1, result.skipped)
        assertEquals(0, result.applied)
        assertEquals("Lokal geaendert", dao.rows.single().title)
    }

    @Test
    fun `pull uebernimmt saubere Zeilen`() = runBlocking {
        val dao = FakeMovieDao()
        dao.rows += movie(localId = 1, remoteId = 10, title = "Alt")
            .copy(updatedAt = "2026-08-10T10:00:00Z", syncedAt = "2026-08-10T10:00:00Z")

        val engine = engine(
            dao,
            FakeSettingDao(),
            FakeSyncApi(export = ExportResponse(exportedAt = "2026-08-10T13:00:00Z", movies = listOf(
                serverMovie(id = 10, title = "Neu")
            )))
        )

        val result = engine.pull()

        assertEquals(1, result.applied)
        assertEquals("Neu", dao.rows.single().title)
        // Die lokale ID muss die Aktualisierung überleben, sonst laufen
        // gemerkte Navigationsziele ins Leere.
        assertEquals(1L, dao.rows.single().localId)
    }

    // ── Das Wasserzeichen ────────────────────────────────────────────────────

    @Test
    fun `pull merkt sich den Server-Zeitstempel`() = runBlocking {
        val settings = FakeSettingDao()
        val engine = engine(
            FakeMovieDao(),
            settings,
            FakeSyncApi(export = ExportResponse(exportedAt = "2026-08-10T13:00:00Z", movies = emptyList()))
        )

        engine.pull()

        assertEquals("2026-08-10T13:00:00Z", settings.values[SettingKeys.LAST_SYNC_AT])
    }

    @Test
    fun `pull schreibt das Wasserzeichen nicht fort, wenn Zeilen fehlschlagen`() = runBlocking {
        val settings = FakeSettingDao()
        val dao = object : FakeMovieDao() {
            override suspend fun upsertFromServer(movies: List<MovieEntity>) {
                throw IllegalStateException("Schreibfehler")
            }
        }
        val engine = engine(
            dao,
            settings,
            FakeSyncApi(export = ExportResponse(exportedAt = "2026-08-10T13:00:00Z", movies = listOf(
                serverMovie(id = 10, title = "Kaputt")
            )))
        )

        val result = engine.pull()

        assertEquals(1, result.errors.size)
        // Sonst gaelten die fehlgeschlagenen Zeilen beim naechsten Mal als erledigt.
        assertNull(settings.values[SettingKeys.LAST_SYNC_AT])
    }

    @Test
    fun `pull ohne full nutzt das gemerkte Wasserzeichen`() = runBlocking {
        val settings = FakeSettingDao()
        settings.values[SettingKeys.LAST_SYNC_AT] = "2026-08-09T00:00:00Z"
        val api = FakeSyncApi(export = ExportResponse(exportedAt = "x", movies = emptyList()))

        engine(FakeMovieDao(), settings, api).pull()
        assertEquals("2026-08-09T00:00:00Z", api.lastSince)

        engine(FakeMovieDao(), settings, api).pull(full = true)
        assertNull(api.lastSince)
    }

    // ── Löschungen ───────────────────────────────────────────────────────────

    @Test
    fun `pull entfernt serverseitig geloeschte Zeilen`() = runBlocking {
        val dao = FakeMovieDao()
        dao.rows += movie(localId = 1, remoteId = 10, title = "Weg")

        val engine = engine(
            dao,
            FakeSettingDao(),
            FakeSyncApi(export = ExportResponse(exportedAt = "t", movies = listOf(
                serverMovie(id = 10, title = "Weg").copy(isDeleted = true)
            )))
        )

        val result = engine.pull()

        assertEquals(1, result.deleted)
        assertTrue(dao.rows.isEmpty())
    }

    // ── Push ─────────────────────────────────────────────────────────────────

    @Test
    fun `push traegt die neue Server-ID nach`() = runBlocking {
        val dao = FakeMovieDao()
        dao.rows += movie(localId = 1, remoteId = null, title = "Nur lokal")
            .copy(updatedAt = "2026-08-10T12:00:00Z", syncedAt = null)

        val api = FakeSyncApi(created = serverMovie(id = 99, title = "Nur lokal"))
        val result = engine(dao, FakeSettingDao(), api).push()

        assertEquals(1, result.created)
        assertEquals(99, dao.rows.single().remoteId)
        assertNotNull(dao.rows.single().syncedAt)
    }

    @Test
    fun `push loescht eine nur lokal angelegte Zeile ohne Serveraufruf`() = runBlocking {
        val dao = FakeMovieDao()
        dao.rows += movie(localId = 1, remoteId = null, title = "Sofort wieder weg")
            .copy(isDeleted = true, updatedAt = "2026-08-10T12:00:00Z", syncedAt = null)

        val api = FakeSyncApi()
        val result = engine(dao, FakeSettingDao(), api).push()

        assertEquals(1, result.deleted)
        assertTrue(dao.rows.isEmpty())
        // Der Server hat von dieser Zeile nie erfahren.
        assertTrue(api.deletedIds.isEmpty())
    }

    @Test
    fun `push laesst eine abgelehnte Zeile die uebrigen nicht blockieren`() = runBlocking {
        val dao = FakeMovieDao()
        dao.rows += movie(localId = 1, remoteId = 10, title = "Wird abgelehnt")
            .copy(updatedAt = "2026-08-10T12:00:00Z", syncedAt = "2026-08-10T10:00:00Z")
        dao.rows += movie(localId = 2, remoteId = 11, title = "Geht durch")
            .copy(updatedAt = "2026-08-10T12:00:00Z", syncedAt = "2026-08-10T10:00:00Z")

        val api = object : FakeSyncApi() {
            override suspend fun updateMovie(id: Int, request: MovieUpdateRequest): SingleMovieResponse {
                if (id == 10) throw IllegalStateException("422")
                return super.updateMovie(id, request)
            }
        }

        val result = engine(dao, FakeSettingDao(), api).push()

        assertEquals(1, result.updated)
        assertEquals(1, result.errors.size)
        // Die abgelehnte Zeile bleibt abweichend und wird erneut versucht.
        assertTrue(dao.rows.first { it.localId == 1L }.isDirty)
    }

    // ── Wechsel vom eigenstaendigen Betrieb zur Shelf ────────────────────────

    @Test
    fun `erster Abgleich nach dem Wechsel laedt den lokalen Bestand hoch`() = runBlocking {
        val dao = FakeMovieDao()
        // Zwei ohne Shelf angelegte Filme: keine Server-ID, nie uebertragen.
        dao.rows += movie(localId = 1, remoteId = null, title = "Ohne Shelf angelegt")
            .copy(updatedAt = "2026-08-10T12:00:00Z", syncedAt = null)
        dao.rows += movie(localId = 2, remoteId = null, title = "Auch ohne Shelf")
            .copy(updatedAt = "2026-08-10T12:00:00Z", syncedAt = null)

        // Die Shelf kennt noch nichts davon.
        val api = FakeSyncApi(
            export = ExportResponse(exportedAt = "2026-08-10T13:00:00Z", movies = emptyList()),
            created = serverMovie(id = 50, title = "Ohne Shelf angelegt")
        )

        val result = engine(dao, FakeSettingDao(), api).runFullSync()

        assertEquals(2, result.push.created)
        // Entscheidend: der leere Serverstand darf den lokalen Bestand nicht
        // ausloeschen. Er wird hochgeladen, nicht ueberschrieben.
        assertEquals(2, dao.rows.size)
        assertEquals(0, result.pull.deleted)
    }

    @Test
    fun `Pull entfernt keine Zeilen, die der Server nie gesehen hat`() = runBlocking {
        val dao = FakeMovieDao()
        dao.rows += movie(localId = 1, remoteId = null, title = "Nur lokal")
            .copy(updatedAt = "2026-08-10T12:00:00Z", syncedAt = null)

        val engine = engine(
            dao,
            FakeSettingDao(),
            FakeSyncApi(export = ExportResponse(exportedAt = "t", movies = listOf(
                serverMovie(id = 10, title = "Vom Server")
            )))
        )

        engine.pull()

        assertEquals(2, dao.rows.size)
        assertTrue(dao.rows.any { it.title == "Nur lokal" })
    }

    // ── Reihenfolge ──────────────────────────────────────────────────────────

    @Test
    fun `voller Abgleich laedt erst hoch, dann herunter`() = runBlocking {
        val order = mutableListOf<String>()
        val dao = object : FakeMovieDao() {
            override suspend fun getDirtyMovies(): List<MovieEntity> {
                order += "push"
                return emptyList()
            }
        }
        val api = object : FakeSyncApi(export = ExportResponse(exportedAt = "t", movies = emptyList())) {
            override suspend fun exportMovies(since: String?): ExportResponse {
                order += "pull"
                return super.exportMovies(since)
            }
        }

        engine(dao, FakeSettingDao(), api).runFullSync()

        // Andersherum ueberschriebe der Pull genau die Zeilen, die gleich
        // haetten hochgeladen werden sollen.
        assertEquals(listOf("push", "pull"), order)
    }

    // ── Hilfen ───────────────────────────────────────────────────────────────

    private fun engine(dao: FakeMovieDao, settings: FakeSettingDao, api: FakeSyncApi) =
        SyncEngine(dao, settings, { api })

    private fun movie(localId: Long, remoteId: Int?, title: String) = MovieEntity(
        localId = localId,
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
        createdAt = "2026-08-01T00:00:00Z",
        actorsJson = null,
        boxsetChildrenJson = null
    )

    private fun serverMovie(id: Int, title: String) = Movie(
        id = id,
        title = title,
        year = 2020,
        collectionType = "Film",
        createdAt = "2026-08-01T00:00:00Z",
        updatedAt = "2026-08-10T11:00:00Z"
    )
}
