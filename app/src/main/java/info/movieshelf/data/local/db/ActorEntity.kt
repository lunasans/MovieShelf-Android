package info.movieshelf.data.local.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Darsteller. Wie Filme mit lokaler und Server-Identität, siehe [MovieEntity]. */
@Entity(
    tableName = "actors",
    indices = [Index("remoteId", unique = true), Index("name")]
)
data class ActorEntity(
    @PrimaryKey(autoGenerate = true) val localId: Long = 0,
    val remoteId: Int? = null,
    val name: String,
    val bio: String? = null,
    val birthday: String? = null,
    val placeOfBirth: String? = null,
    val imagePath: String? = null,
    val tmdbId: Int? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val syncedAt: String? = null
)

/**
 * Besetzung: welcher Darsteller spielt in welchem Film welche Rolle.
 * Beide Seiten kaskadieren, damit gelöschte Filme keine verwaisten Zeilen lassen.
 */
@Entity(
    tableName = "film_actor",
    primaryKeys = ["movieLocalId", "actorLocalId"],
    indices = [Index("actorLocalId")],
    foreignKeys = [
        ForeignKey(
            entity = MovieEntity::class,
            parentColumns = ["localId"],
            childColumns = ["movieLocalId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ActorEntity::class,
            parentColumns = ["localId"],
            childColumns = ["actorLocalId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class FilmActorCrossRef(
    val movieLocalId: Long,
    val actorLocalId: Long,
    val role: String? = null,
    val isMainRole: Boolean = false,
    val sortOrder: Int = 0
)
