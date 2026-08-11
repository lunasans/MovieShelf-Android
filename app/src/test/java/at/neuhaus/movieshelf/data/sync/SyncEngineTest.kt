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

    @Test
    fun `pull loescht eine noch nicht uebertragene Gesehen-Markierung nicht`() = runBlocking {
        val dao = FakeMovieDao()
        // Sauber im Sinne von updatedAt/syncedAt — der Film-Push war schon da.
        // Offen ist nur noch die Markierung, die ueber ihren eigenen Endpunkt geht.
        dao.rows += movie(localId = 1, remoteId = 10, title = "Arrival").copy(
            isWatched = true,
            syncedWatched = false,
            updatedAt = "2026-08-01T00:00:00Z",
            syncedAt = "2026-08-10T12:00:00Z"
        )

        val engine = engine(
            dao,
            FakeSettingDao(),
            FakeSyncApi(export = ExportResponse(exportedAt = "2026-08-10T13:00:00Z", movies = listOf(
                serverMovie(id = 10, title = "Arrival").copy(isWatched = false)
            )))
        )

        engine.pull()

        val row = dao.rows.single()
        assertEquals(true, row.isWatched)
        // Und sie steht weiterhin zur Uebertragung an.
        assertEquals(true, row.hasPendingWatched)
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

    // ── Anlegen ueber TMDb statt roher Felder ────────────────────────────────

    @Test
    fun `push laesst einen TMDb-Film vom Server importieren`() = runBlocking {
        val dao = FakeMovieDao()
        dao.rows += movie(localId = 1, remoteId = null, title = "Dune")
            .copy(tmdbId = "438631", updatedAt = "2026-08-10T12:00:00Z", syncedAt = null)

        val api = FakeSyncApi(created = serverMovie(id = 77, title = "Dune"))
        val result = SyncEngine(dao, FakeSettingDao(), { api }).push()

        assertEquals(1, result.created)
        // Ueber /tmdb/import holt der Server Bilder, Besetzung und Metadaten
        // selbst; ueber /admin/movies staende der Film ohne all das da.
        assertEquals(listOf(Triple(438631, "movie", null)), api.tmdbImports)
        assertEquals(77, dao.rows.single().remoteId)
    }

    @Test
    fun `push schickt bei Serien nur die lokal vorhandenen Staffeln mit`() = runBlocking {
        val dao = FakeMovieDao()
        dao.rows += movie(localId = 1, remoteId = null, title = "Severance")
            .copy(tmdbId = "95396", collectionType = "Serie", updatedAt = "2026-08-10T12:00:00Z", syncedAt = null)

        val api = FakeSyncApi(created = serverMovie(id = 88, title = "Severance"))
        SyncEngine(
            dao, FakeSettingDao(), { api },
            localSeasonNumbers = { listOf(1, 2) }
        ).push()

        // Ohne diese Angabe importiert der Server alle bei TMDb bekannten
        // Staffeln - lokal liegen aber nur zwei.
        assertEquals(listOf(Triple(95396, "tv", listOf(1, 2))), api.tmdbImports)
    }

    @Test
    fun `push nimmt einen bereits geloeschten Film als erledigt hin`() = runBlocking {
        val dao = FakeMovieDao()
        dao.rows += movie(localId = 1, remoteId = 10, title = "Auf der Shelf schon weg")
            .copy(isDeleted = true, updatedAt = "2026-08-10T12:00:00Z", syncedAt = "2026-08-10T10:00:00Z")

        val api = object : FakeSyncApi() {
            override suspend fun deleteMovie(id: Int) {
                throw retrofit2.HttpException(
                    retrofit2.Response.error<Any>(404, okhttp3.ResponseBody.create(null, ""))
                )
            }
        }

        val result = SyncEngine(dao, FakeSettingDao(), { api }).push()

        // Das Ziel ist erreicht. Als Fehler zu werten hiesse, die Zeile bliebe
        // fuer immer abweichend und der Fehler wiederholte sich bei jedem Lauf.
        assertEquals(1, result.deleted)
        assertEquals(0, result.errors.size)
        assertTrue(dao.rows.isEmpty())
    }

    // ── Vorschau im Detail ───────────────────────────────────────────────────

    @Test
    fun `Vorschau benennt die geaenderten Felder`() = runBlocking {
        val dao = FakeMovieDao()
        dao.rows += movie(localId = 1, remoteId = 10, title = "Alt")
            .copy(year = 2020, updatedAt = "t", syncedAt = "t")

        val engine = engine(
            dao,
            FakeSettingDao(),
            FakeSyncApi(export = ExportResponse(exportedAt = "t2", movies = listOf(
                serverMovie(id = 10, title = "Neu").copy(year = 2021, director = "Nolan")
            )))
        )

        val preview = engine.preview()

        assertEquals(1, preview.incomingUpdated)
        val item = preview.items.single { it.direction == SyncDirection.PULL }
        assertEquals(SyncAction.UPDATED, item.action)
        // Nur Felder, die der Nutzer sieht - und alle drei geaenderten.
        assertEquals(listOf("Titel", "Jahr", "Regisseur"), item.changes)
    }

    @Test
    fun `Vorschau fuehrt beide Richtungen auf`() = runBlocking {
        val dao = FakeMovieDao()
        dao.rows += movie(localId = 1, remoteId = null, title = "Nur lokal")
            .copy(updatedAt = "t", syncedAt = null)

        val engine = engine(
            dao,
            FakeSettingDao(),
            FakeSyncApi(export = ExportResponse(exportedAt = "t2", movies = listOf(
                serverMovie(id = 10, title = "Nur auf dem Server")
            )))
        )

        val preview = engine.preview()

        assertEquals(
            listOf(SyncDirection.PULL, SyncDirection.PUSH),
            preview.items.map { it.direction }
        )
        assertEquals(0, preview.overflow)
    }

    // ── Einzelne Richtungen ──────────────────────────────────────────────────

    @Test
    fun `nur hochladen rueckt das Wasserzeichen nicht vor`() = runBlocking {
        val settings = FakeSettingDao()
        settings.values[SettingKeys.LAST_SYNC_AT] = "2026-08-09T00:00:00Z"
        val api = FakeSyncApi(export = ExportResponse(exportedAt = "2026-08-10T13:00:00Z", movies = emptyList()))

        engine(FakeMovieDao(), settings, api).runPushOnly()

        // Das Wasserzeichen steht fuer "bis hierhin ist der Serverstand
        // bekannt". Dieser Lauf hat ihn nicht geholt - es vorzuruecken wuerde
        // den naechsten Delta-Pull um alles bringen, was inzwischen passiert ist.
        assertEquals("2026-08-09T00:00:00Z", settings.values[SettingKeys.LAST_SYNC_AT])
        assertNull("Der Serverstand wurde gar nicht abgefragt", api.lastSince)
    }

    @Test
    fun `nur laden faesst den Server nicht an`() = runBlocking {
        val dao = FakeMovieDao()
        dao.rows += movie(localId = 1, remoteId = 10, title = "Lokal geaendert")
            .copy(updatedAt = "2026-08-10T12:00:00Z", syncedAt = "2026-08-10T10:00:00Z")

        val api = FakeSyncApi(export = ExportResponse(exportedAt = "t", movies = emptyList()))
        val result = engine(dao, FakeSettingDao(), api).runPullOnly()

        assertEquals(0, result.push.total)
        assertTrue(api.updatedIds.isEmpty())
        // Die abweichende Zeile bleibt liegen, bis jemand hochlaedt.
        assertTrue(dao.rows.single().isDirty)
    }

    // ── Staffeln: gerichtetes Spiegeln ───────────────────────────────────────

    @Test
    fun `push gleicht die Staffeln aller synchronisierten Serien ab`() = runBlocking {
        val dao = FakeMovieDao()
        // Nicht abweichend - trotzdem muss der Staffel-Abgleich laufen, sonst
        // wuerde eine Serie ohne Metadaten-Aenderung nie geprueft.
        dao.rows += movie(localId = 1, remoteId = 10, title = "Severance")
            .copy(collectionType = "Serie", updatedAt = "t", syncedAt = "t")

        val api = FakeSyncApi().apply { remoteSeasons = mapOf(10 to listOf(1, 3)) }
        val result = SyncEngine(
            dao, FakeSettingDao(), { api },
            localSeasonNumbers = { listOf(1, 2) }
        ).push()

        assertEquals(0, result.errors.size)
        // Lokal 1+2, Shelf 1+3: Staffel 2 fehlt dort, Staffel 3 ist ueberzaehlig.
        assertEquals(listOf(10 to listOf(2)), api.importedSeasons)
        assertEquals(listOf(10 to listOf(3)), api.removedSeasons)
    }

    @Test
    fun `pull schneidet lokale Staffeln auf den Serverstand zurueck`() = runBlocking {
        val dao = FakeMovieDao()
        dao.rows += movie(localId = 1, remoteId = 10, title = "Serie")
            .copy(collectionType = "Serie", updatedAt = "t", syncedAt = "t")

        val pruned = mutableListOf<Pair<Long, List<Int>>>()
        val engine = SyncEngine(
            dao,
            FakeSettingDao(),
            {
                FakeSyncApi(export = ExportResponse(exportedAt = "t2", movies = listOf(
                    serverMovie(id = 10, title = "Serie").copy(
                        seasons = listOf(at.neuhaus.movieshelf.data.model.ApiSeason(id = 5, seasonNumber = 1))
                    )
                )))
            },
            pruneSeasons = { localId, keep -> pruned += localId to keep }
        )

        engine.pull()

        // Die Shelf kennt nur Staffel 1 - alles andere muss lokal weg.
        assertEquals(listOf(1L to listOf(1)), pruned)
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

        val result = engine(dao, FakeSettingDao(), api).runSync()

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
    fun `Abgleich laedt erst herunter, dann hoch`() = runBlocking {
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

        engine(dao, FakeSettingDao(), api).runSync()

        // Reihenfolge wie in der Desktop-App. Sicher ist beides, weil der Pull
        // abweichende Zeilen nicht anfasst - aber so sieht man den Serverstand,
        // bevor man ihn ueberschreibt.
        assertEquals(listOf("pull", "push"), order)
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
