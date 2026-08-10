package at.neuhaus.movieshelf.data.local.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import at.neuhaus.movieshelf.data.model.Actor
import at.neuhaus.movieshelf.data.model.Movie
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.lang.reflect.Type

/**
 * Die Sammlung als führende lokale Tabelle (nicht mehr als Cache).
 *
 * Zwei Identitäten pro Zeile, bewusst getrennt:
 * - [localId] vergibt die App. Sie bleibt über Abgleiche hinweg stabil und ist
 *   die einzige ID, mit der die Oberfläche navigieren darf.
 * - [remoteId] ist die ID der Shelf. Sie ist `null`, solange eine Zeile nur
 *   lokal existiert, und wird beim ersten erfolgreichen Push nachgetragen.
 *
 * Der Abgleich erkennt an [syncedAt] gegenüber [updatedAt], ob eine Zeile lokal
 * verändert und noch nicht übertragen wurde. Fehlt [syncedAt] ganz, war die
 * Zeile noch nie auf dem Server.
 */
@Entity(
    tableName = "movies",
    indices = [
        Index("remoteId", unique = true),
        Index("boxsetParentLocalId"),
        Index("inCollection"),
        Index("genre"),
        Index("director"),
        Index("year")
    ]
)
data class MovieEntity(
    @PrimaryKey(autoGenerate = true) val localId: Long = 0,
    val remoteId: Int? = null,
    val title: String?,
    val year: Int?,
    val rating: String?,
    val genre: String?,
    val overview: String?,
    val runtime: Int?,
    val director: String?,
    val coverUrl: String?,
    val backdropUrl: String?,
    val trailerUrl: String?,
    val edition: String?,
    val regionCode: String?,
    val discLocation: String?,
    val purchaseDate: String?,
    val purchasePrice: Double?,
    val condition: String?,
    val viewCount: Int?,
    val isWatched: Boolean?,
    val tmdbId: String?,
    val ratingAge: Int?,
    val tag: String?,
    val isBoxset: Boolean?,
    /** Aufgelöste lokale ID des Boxsets. Wird erst im zweiten Pull-Durchgang gesetzt. */
    val boxsetParentLocalId: Long? = null,
    /**
     * Die Shelf liefert in `boxset_parent_id` ihre eigene ID. Sie wird beim Pull
     * hier roh abgelegt und erst danach in [boxsetParentLocalId] übersetzt — in
     * einer einzigen Spalte wäre eine bereits aufgelöste lokale ID von einer
     * Server-ID nicht zu unterscheiden.
     */
    val boxsetParentRemoteId: Int? = null,
    val inCollection: Boolean?,
    val collectionType: String?,
    val collectionNo: Int? = null,
    val createdAt: String?,
    val updatedAt: String? = null,
    val syncedAt: String? = null,
    val isDeleted: Boolean = false,
    val actorsJson: String?,
    val boxsetChildrenJson: String?,
    val cachedAt: Long = System.currentTimeMillis()
) {
    /** Lokal verändert und noch nicht übertragen. */
    val isDirty: Boolean
        get() = syncedAt == null || (updatedAt != null && updatedAt > syncedAt)

    /**
     * In das Modell der Oberfläche übersetzen. [Movie.id] bleibt die Server-ID
     * für Netzaufrufe, [Movie.localId] trägt die lokale Identität — darüber
     * navigiert die Oberfläche.
     */
    fun toMovie(): Movie {
        return Movie(
            id = remoteId ?: 0,
            localId = localId,
            title = title,
            year = year,
            rating = rating,
            genre = genre,
            overview = overview,
            runtime = runtime,
            director = director,
            coverUrl = coverUrl,
            backdropUrl = backdropUrl,
            trailerUrl = trailerUrl,
            edition = edition,
            regionCode = regionCode,
            discLocation = discLocation,
            purchaseDate = purchaseDate,
            purchasePrice = purchasePrice,
            condition = condition,
            viewCount = viewCount,
            isWatched = isWatched,
            tmdbId = tmdbId,
            ratingAge = ratingAge,
            tag = tag,
            isBoxset = isBoxset,
            boxsetParentId = boxsetParentRemoteId,
            inCollection = inCollection,
            collectionType = collectionType,
            createdAt = createdAt,
            actors = if (actorsJson != null) gson.fromJson(actorsJson, actorListType) else null,
            boxsetChildren = if (boxsetChildrenJson != null) gson.fromJson(boxsetChildrenJson, movieListType) else null
        )
    }

    companion object {
        // Geteilte, threadsichere Instanzen statt pro Mapping-Aufruf neu zu erzeugen.
        // getParameterized statt anonymer TypeToken-Subklassen: so kann R8 die
        // generische Signatur nicht wegoptimieren (sonst Crash im Release-Build).
        private val gson = Gson()
        private val actorListType: Type = TypeToken.getParameterized(List::class.java, Actor::class.java).type
        private val movieListType: Type = TypeToken.getParameterized(List::class.java, Movie::class.java).type

        /**
         * Server-Antwort in eine lokale Zeile übersetzen. [syncedAt] wird gesetzt,
         * weil die Daten per Definition gerade vom Server kamen; [localId] bleibt
         * 0 und wird vom DAO auf eine bestehende Zeile gehoben, falls die
         * [Movie.id] hier schon bekannt ist.
         */
        fun fromServerMovie(movie: Movie, syncedAt: String? = null): MovieEntity {
            return MovieEntity(
                remoteId = movie.id,
                title = movie.title,
                year = movie.year,
                rating = movie.rating,
                genre = movie.genre,
                overview = movie.overview,
                runtime = movie.runtime,
                director = movie.director,
                coverUrl = movie.coverUrl,
                backdropUrl = movie.backdropUrl,
                trailerUrl = movie.trailerUrl,
                edition = movie.edition,
                regionCode = movie.regionCode,
                discLocation = movie.discLocation,
                purchaseDate = movie.purchaseDate,
                purchasePrice = movie.purchasePrice,
                condition = movie.condition,
                viewCount = movie.viewCount,
                isWatched = movie.isWatched,
                tmdbId = movie.tmdbId,
                ratingAge = movie.ratingAge,
                tag = movie.tag,
                isBoxset = movie.isBoxset,
                boxsetParentRemoteId = movie.boxsetParentId,
                inCollection = movie.inCollection,
                collectionType = movie.collectionType,
                createdAt = movie.createdAt,
                updatedAt = movie.createdAt,
                syncedAt = syncedAt ?: movie.createdAt,
                actorsJson = if (!movie.actors.isNullOrEmpty()) gson.toJson(movie.actors) else null,
                boxsetChildrenJson = if (!movie.boxsetChildren.isNullOrEmpty()) gson.toJson(movie.boxsetChildren) else null
            )
        }
    }
}
