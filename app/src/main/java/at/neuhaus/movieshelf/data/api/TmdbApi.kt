package at.neuhaus.movieshelf.data.api

import at.neuhaus.movieshelf.data.model.TmdbSearchResponse
import at.neuhaus.movieshelf.data.model.TmdbTvDetails
import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

/**
 * TMDb direkt, ohne Umweg über die Shelf.
 *
 * Wird nur im eigenständigen Betrieb benutzt; mit Shelf läuft die Suche
 * weiterhin über deren Endpunkte, damit dort der serverseitige Import
 * greift. Genauso macht es die Desktop-App.
 *
 * Der Schlüssel wird pro Aufruf mitgegeben statt über einen Interceptor: er
 * kann sich jederzeit ändern, und ein Interceptor müsste ihn dann bei jedem
 * Aufruf neu aus dem Keystore lesen.
 */
interface TmdbApi {

    @GET("search/movie")
    suspend fun searchMovies(
        @Query("api_key") apiKey: String,
        @Query("query") query: String,
        @Query("language") language: String = "de-DE"
    ): TmdbSearchResponse

    @GET("search/tv")
    suspend fun searchSeries(
        @Query("api_key") apiKey: String,
        @Query("query") query: String,
        @Query("language") language: String = "de-DE"
    ): TmdbSearchResponse

    @GET("movie/{id}")
    suspend fun getMovie(
        @Path("id") id: Int,
        @Query("api_key") apiKey: String,
        @Query("language") language: String = "de-DE",
        @Query("append_to_response") append: String = "credits,videos,release_dates"
    ): TmdbMovieDetails

    @GET("tv/{id}")
    suspend fun getSeries(
        @Path("id") id: Int,
        @Query("api_key") apiKey: String,
        @Query("language") language: String = "de-DE",
        @Query("append_to_response") append: String = "credits,videos,content_ratings"
    ): TmdbTvDetails

    companion object {
        const val BASE_URL = "https://api.themoviedb.org/3/"
        const val IMAGE_BASE_URL = "https://image.tmdb.org/t/p/w500"

        fun create(): TmdbApi {
            val client = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .build()

            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(TmdbApi::class.java)
        }

        /** Vollständige Bild-URL aus einem TMDb-Pfad wie `/abc123.jpg`. */
        fun imageUrl(path: String?): String? =
            path?.takeIf { it.isNotBlank() }?.let { IMAGE_BASE_URL + it }
    }
}

/**
 * Filmdetails von TMDb. Bewusst nur die Felder, die beim Anlegen gebraucht
 * werden — TMDb liefert ein Vielfaches davon.
 */
data class TmdbMovieDetails(
    val id: Int? = null,
    val title: String? = null,
    val overview: String? = null,
    val runtime: Int? = null,
    @SerializedName("release_date") val releaseDate: String? = null,
    @SerializedName("poster_path") val posterPath: String? = null,
    @SerializedName("backdrop_path") val backdropPath: String? = null,
    @SerializedName("vote_average") val voteAverage: Double? = null,
    val genres: List<TmdbGenre>? = null,
    val credits: TmdbCredits? = null,
    val videos: TmdbVideos? = null
) {
    val year: Int? get() = releaseDate?.take(4)?.toIntOrNull()

    val genreNames: String? get() = genres?.mapNotNull { it.name }?.joinToString(", ")?.takeIf { it.isNotBlank() }

    /** Erster Regisseur aus der Crew — TMDb liefert gelegentlich mehrere. */
    val director: String? get() = credits?.crew?.firstOrNull { it.job == "Director" }?.name

    /** Erstes YouTube-Video, bevorzugt ein Trailer. */
    val trailerUrl: String?
        get() {
            val results = videos?.results.orEmpty().filter { it.site == "YouTube" && it.key != null }
            val best = results.firstOrNull { it.type == "Trailer" } ?: results.firstOrNull()
            return best?.key?.let { "https://www.youtube.com/watch?v=$it" }
        }
}

data class TmdbGenre(val id: Int? = null, val name: String? = null)

data class TmdbCredits(
    val cast: List<TmdbCastMember>? = null,
    val crew: List<TmdbCrewMember>? = null
)

data class TmdbCastMember(
    val id: Int? = null,
    val name: String? = null,
    val character: String? = null,
    @SerializedName("profile_path") val profilePath: String? = null,
    val order: Int? = null
)

data class TmdbCrewMember(
    val id: Int? = null,
    val name: String? = null,
    val job: String? = null
)

data class TmdbVideos(val results: List<TmdbVideo>? = null)

data class TmdbVideo(
    val key: String? = null,
    val site: String? = null,
    val type: String? = null
)
