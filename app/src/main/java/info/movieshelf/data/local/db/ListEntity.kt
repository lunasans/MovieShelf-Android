package info.movieshelf.data.local.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Eigene Liste ("Watchlist", "Weihnachten", …). */
@Entity(
    tableName = "lists",
    indices = [Index("remoteId", unique = true)]
)
data class ListEntity(
    @PrimaryKey(autoGenerate = true) val localId: Long = 0,
    val remoteId: Int? = null,
    val name: String,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val syncedAt: String? = null
)

/** Item-Arten in [ListItemEntity]. */
object ListItemType {
    /** Film aus der eigenen Sammlung ([MovieEntity]). */
    const val MOVIE = "movie"

    /** Film, der nur in einer Liste steht ([ExternalMovieEntity]). */
    const val EXTERNAL = "external"
}

/**
 * Polymorpher Listeninhalt: eine Liste kann Sammlungsfilme und externe Filme
 * mischen. [itemType] entscheidet, auf welche Tabelle [itemLocalId] zeigt —
 * deshalb gibt es hier bewusst keinen Fremdschlüssel auf die Item-Zeile.
 */
@Entity(
    tableName = "list_items",
    primaryKeys = ["listLocalId", "itemType", "itemLocalId"],
    foreignKeys = [
        ForeignKey(
            entity = ListEntity::class,
            parentColumns = ["localId"],
            childColumns = ["listLocalId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class ListItemEntity(
    val listLocalId: Long,
    val itemType: String,
    val itemLocalId: Long,
    val addedAt: String? = null
)

/**
 * Merker für lokal aus einer Liste entfernte Items.
 *
 * Der Listen-Abgleich arbeitet als Vereinigung, damit keine Seite Einträge der
 * anderen verliert. Ohne diese Merker ließe sich "lokal entfernt" nicht von
 * "serverseitig neu hinzugekommen" unterscheiden — der Eintrag käme beim
 * nächsten Pull einfach zurück. Nach erfolgreichem Push werden sie gelöscht.
 * Referenziert wird die Server-ID, weil nur synchronisierte Items betroffen sind.
 */
@Entity(
    tableName = "list_item_tombstones",
    primaryKeys = ["listLocalId", "itemType", "remoteId"],
    foreignKeys = [
        ForeignKey(
            entity = ListEntity::class,
            parentColumns = ["localId"],
            childColumns = ["listLocalId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class ListItemTombstoneEntity(
    val listLocalId: Long,
    val itemType: String,
    val remoteId: Int,
    val removedAt: String? = null
)
