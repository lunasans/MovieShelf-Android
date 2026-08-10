package at.neuhaus.movieshelf.data.repository

import at.neuhaus.movieshelf.data.local.ImageDownloader
import at.neuhaus.movieshelf.data.local.MediaStore
import at.neuhaus.movieshelf.data.local.db.ActorDao
import at.neuhaus.movieshelf.data.local.db.ActorEntity
import at.neuhaus.movieshelf.data.local.db.FilmActorCrossRef
import at.neuhaus.movieshelf.data.local.db.MovieEntity
import at.neuhaus.movieshelf.data.local.db.PendingUploadDao
import at.neuhaus.movieshelf.data.local.db.PendingUploadEntity
import at.neuhaus.movieshelf.data.sync.FakeMovieDao
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Ein heruntergeladenes Bild darf die Zeile nicht als lokal geändert
 * markieren.
 *
 * Der Fehler ist einmal passiert und hätte teuer werden können: weil das
 * Eintragen des Dateipfads `updatedAt` mitsetzte, galten nach dem ersten
 * Vollstand alle 618 Filme als abweichend — der nächste Lauf hätte die
 * gesamte Sammlung zur Shelf zurückgeschoben.
 */
class ArtworkDirtyTest {

    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun `heruntergeladenes Cover macht die Zeile nicht abweichend`() = runBlocking {
        val dao = RecordingMovieDao()
        dao.rows += serverRow()

        val repository = MovieRepository(
            movieDao = dao,
            actorDao = EmptyActorDao(),
            pendingUploadDao = EmptyPendingUploadDao(),
            mediaStore = MediaStore(folder.root),
            imageDownloader = AlwaysSucceedingDownloader(),
            shelfUrlProvider = { "https://shelf.example.com" },
            isShelfMode = { true },
            apiProvider = { throw AssertionError("Der Bild-Download braucht die API nicht") }
        )

        val stored = repository.downloadMissingArtwork()

        assertEquals(1, stored)
        // Der Pfad steht in der Zeile...
        val row = dao.rows.single()
        assertEquals(true, row.coverUrl?.endsWith("1.jpg"))
        // ...aber die Zeile gilt weiterhin als übertragen.
        assertFalse("Sonst schoebe der naechste Lauf alles zurueck", row.isDirty)
    }

    private fun serverRow() = MovieEntity(
        localId = 1,
        remoteId = 10,
        title = "Vom Server",
        year = 2020,
        rating = null, genre = null, overview = null, runtime = null, director = null,
        coverUrl = "https://medien.example.com/cover.jpg",
        backdropUrl = null,
        trailerUrl = null, edition = null, regionCode = null, discLocation = null,
        purchaseDate = null, purchasePrice = null, condition = null,
        viewCount = 0, isWatched = false, tmdbId = null, ratingAge = null, tag = null,
        isBoxset = false, inCollection = true, collectionType = "Film",
        createdAt = "2026-08-01T00:00:00Z",
        updatedAt = "2026-08-01T00:00:00Z",
        syncedAt = "2026-08-01T00:00:00Z",
        actorsJson = null, boxsetChildrenJson = null
    )
}

/** Merkt sich Pfadänderungen und lässt die übrigen Wege absichtlich auflaufen. */
private class RecordingMovieDao : FakeMovieDao() {

    override suspend fun getMoviesMissingArtwork(): List<MovieEntity> =
        rows.filter { it.coverUrl?.startsWith("http") == true }

    override suspend fun setCoverPath(localId: Long, path: String) {
        val index = rows.indexOfFirst { it.localId == localId }
        if (index >= 0) rows[index] = rows[index].copy(coverUrl = path)
    }

    override suspend fun setBackdropPath(localId: Long, path: String) {
        val index = rows.indexOfFirst { it.localId == localId }
        if (index >= 0) rows[index] = rows[index].copy(backdropUrl = path)
    }

    // Genau der Weg, der den Fehler verursacht hat: er setzt updatedAt mit.
    override suspend fun updateCoverUrl(localId: Long, url: String?, now: String) =
        throw AssertionError("Der Bild-Download darf updatedAt nicht anfassen")

    override suspend fun updateBackdropUrl(localId: Long, url: String?, now: String) =
        throw AssertionError("Der Bild-Download darf updatedAt nicht anfassen")
}

private class AlwaysSucceedingDownloader : ImageDownloader(shelfClientProvider = {
    throw AssertionError("Im Test wird kein Client gebraucht")
}) {
    override suspend fun download(url: String, authenticated: Boolean): Pair<ByteArray, String> =
        byteArrayOf(1, 2, 3) to "image/jpeg"
}

private class EmptyActorDao : ActorDao {
    override suspend fun getAll(): List<ActorEntity> = emptyList()
    override suspend fun getByLocalId(localId: Long): ActorEntity? = null
    override suspend fun findLocalIdByRemoteId(remoteId: Int): Long? = null
    override suspend fun findLocalIdByName(name: String): Long? = null
    override suspend fun insert(actor: ActorEntity): Long = 0
    override suspend fun getDirty(): List<ActorEntity> = emptyList()
    override suspend fun getAllLocalIds(): List<Long> = emptyList()
    override suspend fun getActorsMissingArtwork(): List<ActorEntity> = emptyList()
    override suspend fun updateImagePath(localId: Long, path: String?) = Unit
    override suspend fun setCast(refs: List<FilmActorCrossRef>) = Unit
    override suspend fun clearCast(movieLocalId: Long) = Unit
    override suspend fun getCastOf(movieLocalId: Long): List<ActorEntity> = emptyList()
}

private class EmptyPendingUploadDao : PendingUploadDao {
    override suspend fun put(upload: PendingUploadEntity) = Unit
    override suspend fun getAll(): List<PendingUploadEntity> = emptyList()
    override suspend fun getFor(movieLocalId: Long): List<PendingUploadEntity> = emptyList()
    override suspend fun remove(movieLocalId: Long, kind: String) = Unit
}
