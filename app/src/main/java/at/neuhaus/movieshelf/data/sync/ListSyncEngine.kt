package at.neuhaus.movieshelf.data.sync

import at.neuhaus.movieshelf.data.local.db.ExternalMovieDao
import at.neuhaus.movieshelf.data.local.db.ExternalMovieEntity
import at.neuhaus.movieshelf.data.local.db.ListDao
import at.neuhaus.movieshelf.data.local.db.ListEntity
import at.neuhaus.movieshelf.data.local.db.ListItemEntity
import at.neuhaus.movieshelf.data.local.db.ListItemType
import at.neuhaus.movieshelf.data.local.db.MovieDao
import at.neuhaus.movieshelf.data.local.db.SyncClock
import at.neuhaus.movieshelf.data.model.ListDetailResponse
import at.neuhaus.movieshelf.data.model.ListItemRef
import at.neuhaus.movieshelf.data.model.MovieListSummary

/**
 * Abgleich der eigenen Listen.
 *
 * Anders als bei Filmen wird hier **vereinigt statt gespiegelt**: eine Liste
 * gehört beiden Seiten, und keine darf Einträge der anderen wegwerfen, nur weil
 * sie sie nicht kennt. Was ein Nutzer auf dem Telefon hinzufügt, soll neben dem
 * stehen, was er am Rechner hinzugefügt hat.
 *
 * Damit die Vereinigung nicht jede Entfernung rückgängig macht, merkt sich die
 * App entfernte Einträge als Tombstone. Ohne diesen Merker sähe ein lokal
 * entferntes Element exakt aus wie ein serverseitig neu hinzugekommenes — und
 * käme beim nächsten Abgleich zurück.
 */
class ListSyncEngine(
    private val listDao: ListDao,
    private val movieDao: MovieDao,
    private val externalMovieDao: ExternalMovieDao,
    private val apiProvider: () -> ListSyncApi
) {
    private val api: ListSyncApi get() = apiProvider()

    /**
     * Beide Richtungen. Erst laden, dann hochladen — so kommen
     * Server-Ergaenzungen in die Vereinigung, die anschliessend hochgeht.
     */
    suspend fun sync(): ListSyncResult {
        val pulled = pullLists()
        val pushed = pushLists()
        return ListSyncResult(
            listsApplied = maxOf(pulled.listsApplied, pushed.listsApplied),
            itemsAdded = pulled.itemsAdded,
            itemsRemoved = pushed.itemsRemoved,
            errors = pulled.errors + pushed.errors
        )
    }

    /**
     * Server-Listen nach lokal: fehlende Eintraege anlegen und verknuepfen.
     *
     * Aendert den Server nicht und entfernt lokal nichts. Was hier entfernt
     * wurde, traegt einen Merker und kommt deshalb nicht zurueck — die
     * Entfernung wird erst vom Push weitergegeben.
     */
    suspend fun pullLists(): ListSyncResult {
        var listsApplied = 0
        var itemsAdded = 0
        var itemsRemoved = 0
        val errors = mutableListOf<SyncError>()

        val summaries = try {
            api.getLists()
        } catch (e: Exception) {
            return ListSyncResult(errors = listOf(SyncError("Listen", e.message ?: "Nicht erreichbar")))
        }

        for (summary in summaries) {
            try {
                val listLocalId = upsertList(summary)
                val detail = api.getList(summary.id)

                val tombstoned = listDao.getTombstones(listLocalId)
                    .map { it.itemType to it.remoteId }
                    .toSet()
                val localRefs = localRefsOf(listLocalId)

                for ((type, remoteId) in serverRefsOf(detail) - localRefs - tombstoned) {
                    val itemLocalId = resolveOrCreateItem(type, remoteId, detail) ?: continue
                    listDao.addItem(ListItemEntity(listLocalId, type, itemLocalId, SyncClock.now()))
                    itemsAdded++
                }
                listsApplied++
            } catch (e: Exception) {
                errors += SyncError(summary.name ?: "Liste ${summary.id}", e.message ?: "Unbekannter Fehler")
            }
        }

        return ListSyncResult(listsApplied, itemsAdded, itemsRemoved, errors)
    }

    /**
     * Lokale Listen zum Server: die Mitgliedschaft als Vereinigung schreiben.
     *
     * Ueberschreibt nicht — was serverseitig hinzugekommen ist, bleibt. Nur
     * was hier ausdruecklich entfernt wurde, faellt heraus.
     */
    suspend fun pushLists(): ListSyncResult {
        var listsApplied = 0
        var itemsRemoved = 0
        val errors = mutableListOf<SyncError>()

        val summaries = try {
            api.getLists()
        } catch (e: Exception) {
            return ListSyncResult(errors = listOf(SyncError("Listen", e.message ?: "Nicht erreichbar")))
        }

        for (summary in summaries) {
            try {
                val listLocalId = upsertList(summary)
                val detail = api.getList(summary.id)

                val tombstoned = listDao.getTombstones(listLocalId)
                    .map { it.itemType to it.remoteId }
                    .toSet()
                val serverRefs = serverRefsOf(detail)
                val union = (serverRefs + localRefsOf(listLocalId)) - tombstoned

                if (union != serverRefs) {
                    api.setItems(
                        summary.id,
                        summary.name ?: "Liste",
                        union.map { ListItemRef(it.first, it.second) }
                    )
                    itemsRemoved += (serverRefs - union).size
                }

                // Erst nach erfolgreichem Push verwerfen: sonst wuesste nach
                // einem Fehlschlag niemand mehr, dass etwas entfernt wurde.
                listDao.clearTombstones(listLocalId)
                listsApplied++
            } catch (e: Exception) {
                errors += SyncError(summary.name ?: "Liste ${summary.id}", e.message ?: "Unbekannter Fehler")
            }
        }

        return ListSyncResult(listsApplied, 0, itemsRemoved, errors)
    }

    private fun serverRefsOf(detail: ListDetailResponse): Set<Pair<String, Int>> =
        detail.items.orEmpty().mapNotNull { item ->
            val type = item.itemType ?: ListItemType.MOVIE
            if (item.id == 0) null else type to item.id
        }.toSet()

    private suspend fun upsertList(summary: MovieListSummary): Long {
        val existing = listDao.findLocalIdByRemoteId(summary.id)
        val now = SyncClock.now()
        val entity = ListEntity(
            localId = existing ?: 0,
            remoteId = summary.id,
            name = summary.name ?: "Liste",
            updatedAt = summary.updatedAt ?: now,
            syncedAt = now
        )
        return if (existing != null) {
            listDao.insert(entity)
            existing
        } else {
            listDao.insert(entity)
        }
    }

    /** Lokale Einträge als (Typ, Server-ID). Nur lokal existierende Einträge
     *  haben noch keine Server-ID und bleiben deshalb außen vor — sie kommen
     *  mit, sobald ihr Film selbst hochgeladen wurde. */
    private suspend fun localRefsOf(listLocalId: Long): Set<Pair<String, Int>> =
        listDao.getItems(listLocalId).mapNotNull { item ->
            val remoteId = when (item.itemType) {
                ListItemType.MOVIE -> movieDao.getByLocalId(item.itemLocalId)?.remoteId
                else -> externalMovieDao.getByLocalId(item.itemLocalId)?.remoteId
            }
            remoteId?.let { item.itemType to it }
        }.toSet()

    /**
     * Lokale Zeile zu einem Server-Eintrag finden — und für externe Titel bei
     * Bedarf anlegen, weil die sonst nirgends herkämen.
     */
    private suspend fun resolveOrCreateItem(
        type: String,
        remoteId: Int,
        detail: ListDetailResponse
    ): Long? {
        if (type == ListItemType.MOVIE) return movieDao.findLocalIdByRemoteId(remoteId)

        externalMovieDao.findLocalIdByRemoteId(remoteId)?.let { return it }

        val source = detail.items.orEmpty().firstOrNull { it.id == remoteId } ?: return null
        val now = SyncClock.now()
        return externalMovieDao.insert(
            ExternalMovieEntity(
                remoteId = remoteId,
                title = source.title ?: "Ohne Titel",
                year = source.year,
                genre = source.genre,
                director = source.director,
                runtime = source.runtime,
                rating = source.rating,
                ratingAge = source.ratingAge,
                overview = source.overview,
                collectionType = source.collectionType,
                coverUrl = source.coverUrl,
                backdropUrl = source.backdropUrl,
                trailerUrl = source.trailerUrl,
                tmdbId = source.tmdbId,
                createdAt = source.createdAt ?: now,
                updatedAt = source.updatedAt ?: now,
                syncedAt = now
            )
        )
    }
}

/** Der Ausschnitt der Shelf-API, den der Listen-Abgleich braucht. */
interface ListSyncApi {
    suspend fun getLists(): List<MovieListSummary>
    suspend fun getList(listId: Int): ListDetailResponse
    suspend fun setItems(listId: Int, name: String, items: List<ListItemRef>)
}

data class ListSyncResult(
    val listsApplied: Int = 0,
    val itemsAdded: Int = 0,
    val itemsRemoved: Int = 0,
    val errors: List<SyncError> = emptyList()
)
