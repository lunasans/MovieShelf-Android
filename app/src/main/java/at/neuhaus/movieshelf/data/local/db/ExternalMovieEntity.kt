package at.neuhaus.movieshelf.data.local.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Film, der nur in einer Liste steht und nicht zur Sammlung gehört — etwa ein
 * Wunschtitel, den man noch nicht besitzt.
 *
 * Bewusst eine eigene Tabelle statt eines Merkmals an [MovieEntity]: sonst
 * müsste jede Sammlungsabfrage, Statistik und Regalreihe das Merkmal
 * mitfiltern, und ein vergessener Filter ließe fremde Titel in der Sammlung
 * auftauchen. Cover bleiben hier Server- beziehungsweise TMDb-URLs; lokal
 * abgelegt werden nur Bilder der eigenen Sammlung.
 */
@Entity(
    tableName = "external_movies",
    indices = [Index("remoteId", unique = true)]
)
data class ExternalMovieEntity(
    @PrimaryKey(autoGenerate = true) val localId: Long = 0,
    val remoteId: Int? = null,
    val title: String,
    val year: Int? = null,
    val genre: String? = null,
    val director: String? = null,
    val runtime: Int? = null,
    val rating: String? = null,
    val ratingAge: Int? = null,
    val overview: String? = null,
    val collectionType: String? = null,
    val coverUrl: String? = null,
    val backdropUrl: String? = null,
    val trailerUrl: String? = null,
    val tmdbId: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val syncedAt: String? = null
)
