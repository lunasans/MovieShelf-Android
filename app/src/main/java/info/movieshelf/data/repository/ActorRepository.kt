package info.movieshelf.data.repository

import info.movieshelf.data.api.MovieShelfApi
import info.movieshelf.data.local.db.ActorDao
import info.movieshelf.data.local.db.ActorEntity
import info.movieshelf.data.model.Actor
import info.movieshelf.data.local.db.SyncClock

/**
 * Darsteller — lokal zuerst, wie alles andere in dieser App.
 *
 * Die Tabelle `actors` traegt Biografie, Geburtstag und Bildpfad; der Abgleich
 * fuellt sie ueber `MovieRepository.saveServerCast`. Frueher fragte die
 * Detailansicht trotzdem nur den Server — war der nicht erreichbar, blieb sie
 * leer, obwohl alles Noetige danebenlag. Der Netzaufruf ist jetzt nur noch die
 * Auffrischung: er ergaenzt, was der Server zusaetzlich weiss, und schreibt es
 * fuer das naechste Mal in die Datenbank zurueck.
 */
class ActorRepository(
    private val actorDao: ActorDao,
    private val apiProvider: () -> MovieShelfApi
) {
    private val api: MovieShelfApi get() = apiProvider()

    /** Die Personen der eigenen Sammlung. */
    suspend fun getLocalActors(): List<Actor> =
        actorDao.getAllWithFilms().map { it.toActor() }

    suspend fun searchLocalActors(query: String): List<Actor> =
        actorDao.searchByName(query).map { it.toActor() }

    /**
     * Ein Profil aus der Datenbank, mit den Filmen der Person aus der eigenen
     * Sammlung. `null`, wenn die Person lokal nicht bekannt ist.
     */
    suspend fun getLocalActor(localId: Long): Actor? {
        val entity = actorDao.getByLocalId(localId) ?: return null
        return entity.toActor().copy(
            movies = actorDao.getMoviesOf(localId).map { it.toMovie() }
        )
    }

    /**
     * Vom Server nachladen und das Ergebnis lokal festhalten.
     *
     * Die Filmliste des Servers ersetzt die lokale nicht: sie umfasst dessen
     * gesamten Bestand, waehrend die Detailansicht die eigene Sammlung zeigen
     * soll. Ohne lokalen Treffer bleibt es bei dem, was der Server liefert.
     */
    suspend fun refreshActor(remoteId: Int, localId: Long): Actor? {
        val remote = api.getActor(remoteId).data ?: return null
        val resolvedLocalId = localId.takeIf { it != 0L }
            ?: actorDao.findLocalIdByRemoteId(remoteId)
            ?: remote.name?.let { actorDao.findLocalIdByName(it) }

        if (resolvedLocalId != null) {
            actorDao.getByLocalId(resolvedLocalId)?.let { existing ->
                actorDao.update(
                    existing.copy(
                        remoteId = existing.remoteId ?: remote.id,
                        bio = remote.biography ?: existing.bio,
                        birthday = remote.birthDate ?: existing.birthday,
                        placeOfBirth = remote.placeOfBirth ?: existing.placeOfBirth,
                        updatedAt = SyncClock.now(),
                        syncedAt = SyncClock.now()
                    )
                )
            }
            return getLocalActor(resolvedLocalId)
        }
        return remote
    }

    /**
     * Die Darstellerliste des Servers. Nur noch Rueckfallebene: gezeigt wird
     * die eigene Sammlung, solange sie etwas hergibt.
     */
    suspend fun getRemoteActors(page: Int = 1, perPage: Int = 100): List<Actor> =
        api.getActors(page = page, perPage = perPage).data ?: emptyList()

    suspend fun searchRemoteActors(query: String): List<Actor> =
        api.searchActors(query).data ?: emptyList()

    /** Nur fuer den Fall, dass eine Person lokal gar nicht vorkommt. */
    suspend fun findRemoteIdByName(name: String): Int? =
        api.searchActors(name).data?.firstOrNull()?.id

    suspend fun findLocalIdByName(name: String): Long? = actorDao.findLocalIdByName(name)

    private fun ActorEntity.toActor() = Actor(
        id = remoteId,
        localId = localId,
        name = name,
        imageUrl = imagePath,
        biography = bio,
        birthDate = birthday,
        placeOfBirth = placeOfBirth
    )
}
