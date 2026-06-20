package at.neuhaus.movieshelf.data.local.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import at.neuhaus.movieshelf.data.model.Actor
import at.neuhaus.movieshelf.data.model.Movie
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.lang.reflect.Type

@Entity(
    tableName = "movies",
    indices = [
        Index("boxsetParentId"),
        Index("inCollection"),
        Index("genre"),
        Index("director"),
        Index("year")
    ]
)
data class MovieEntity(
    @PrimaryKey val id: Int,
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
    val viewCount: Int?,
    val isWatched: Boolean?,
    val tmdbId: String?,
    val ratingAge: Int?,
    val tag: String?,
    val isBoxset: Boolean?,
    val boxsetParentId: Int?,
    val inCollection: Boolean?,
    val collectionType: String?,
    val createdAt: String?,
    val actorsJson: String?,
    val boxsetChildrenJson: String?,
    val cachedAt: Long = System.currentTimeMillis()
) {
    fun toMovie(): Movie {
        return Movie(
            id = id,
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
            viewCount = viewCount,
            isWatched = isWatched,
            tmdbId = tmdbId,
            ratingAge = ratingAge,
            tag = tag,
            isBoxset = isBoxset,
            boxsetParentId = boxsetParentId,
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

        fun fromMovie(movie: Movie): MovieEntity {
            return MovieEntity(
                id = movie.id,
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
                viewCount = movie.viewCount,
                isWatched = movie.isWatched,
                tmdbId = movie.tmdbId,
                ratingAge = movie.ratingAge,
                tag = movie.tag,
                isBoxset = movie.isBoxset,
                boxsetParentId = movie.boxsetParentId,
                inCollection = movie.inCollection,
                collectionType = movie.collectionType,
                createdAt = movie.createdAt,
                actorsJson = if (!movie.actors.isNullOrEmpty()) gson.toJson(movie.actors) else null,
                boxsetChildrenJson = if (!movie.boxsetChildren.isNullOrEmpty()) gson.toJson(movie.boxsetChildren) else null
            )
        }
    }
}
