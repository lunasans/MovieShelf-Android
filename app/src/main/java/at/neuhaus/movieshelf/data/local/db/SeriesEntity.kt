package at.neuhaus.movieshelf.data.local.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Staffel einer Serie. */
@Entity(
    tableName = "seasons",
    indices = [
        Index("remoteId", unique = true),
        Index(value = ["movieLocalId", "seasonNumber"], unique = true)
    ],
    foreignKeys = [
        ForeignKey(
            entity = MovieEntity::class,
            parentColumns = ["localId"],
            childColumns = ["movieLocalId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class SeasonEntity(
    @PrimaryKey(autoGenerate = true) val localId: Long = 0,
    val remoteId: Int? = null,
    val movieLocalId: Long,
    val seasonNumber: Int,
    val title: String? = null,
    val overview: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val syncedAt: String? = null
)

/**
 * Episode einer Staffel.
 *
 * Der eindeutige Index über Staffel und Episodennummer steht bewusst von Anfang
 * an: am Desktop mussten Duplikate nachträglich aufgeräumt werden, weil ein
 * wiederholter Serien-Import dieselbe Episode ein zweites Mal angelegt hat.
 */
@Entity(
    tableName = "episodes",
    indices = [
        Index("remoteId", unique = true),
        Index(value = ["seasonLocalId", "episodeNumber"], unique = true)
    ],
    foreignKeys = [
        ForeignKey(
            entity = SeasonEntity::class,
            parentColumns = ["localId"],
            childColumns = ["seasonLocalId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class EpisodeEntity(
    @PrimaryKey(autoGenerate = true) val localId: Long = 0,
    val remoteId: Int? = null,
    val seasonLocalId: Long,
    val episodeNumber: Int,
    val title: String? = null,
    val overview: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val syncedAt: String? = null
)
