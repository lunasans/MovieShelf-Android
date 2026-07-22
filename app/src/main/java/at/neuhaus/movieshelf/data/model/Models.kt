package at.neuhaus.movieshelf.data.model

import com.google.gson.annotations.SerializedName

data class MovieResponse(
    val data: List<Movie>?
)

data class SingleMovieResponse(
    val data: Movie?,
    @SerializedName("is_updated") val isUpdated: Boolean? = false
)

data class ActorResponse(
    val data: List<Actor>?
)

data class SingleActorResponse(
    val data: Actor?
)

data class StatsResponse(
    val data: Stats?
)

data class Movie(
    val id: Int,
    val title: String? = null,
    val year: Int? = null,
    val rating: String? = null,
    val genre: String? = null,
    val overview: String? = null,
    val runtime: Int? = null,
    val director: String? = null,
    @SerializedName("cover_url") val coverUrl: String? = null,
    @SerializedName("backdrop_url") val backdropUrl: String? = null,
    @SerializedName("trailer_url") val trailerUrl: String? = null,
    @SerializedName("view_count") val viewCount: Int? = null,
    @SerializedName("is_watched") val isWatched: Boolean? = null,
    @SerializedName("actors", alternate = ["cast", "credits"]) val actors: List<Actor>? = null,
    @SerializedName("tmdb_id") val tmdbId: String? = null,
    @SerializedName("rating_age") val ratingAge: Int? = null,
    val tag: String? = null,
    @SerializedName("collection_type") val collectionType: String? = null,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("is_wishlisted") val isWishlisted: Boolean? = null,
    @SerializedName("in_collection") val inCollection: Boolean? = true,
    // Felder für Boxsets
    @SerializedName("is_boxset") val isBoxset: Boolean? = false,
    @SerializedName("boxset_parent_id") val boxsetParentId: Int? = null,
    @SerializedName("boxset_children", alternate = ["movies"]) val boxsetChildren: List<Movie>? = null,
    // Staffeln (nur bei Serien, von GET /api/movies/{id})
    val seasons: List<ApiSeason>? = null,
    // Nur in Listen-Items gesetzt ("movie" oder "external"), um beim Speichern
    // einer Liste den richtigen Item-Typ zurückzusenden (GET/PUT /api/lists/{id}).
    @SerializedName("item_type") val itemType: String? = null
)

data class ApiSeason(
    val id: Int,
    @SerializedName("season_number") val seasonNumber: Int,
    val title: String? = null,
    val overview: String? = null,
    val episodes: List<ApiEpisode>? = null
)

data class ApiEpisode(
    val id: Int,
    @SerializedName("episode_number") val episodeNumber: Int,
    val title: String? = null,
    val overview: String? = null,
    val runtime: Int? = null
)

// --- Staffeln nachladen (TMDb über die Shelf) ---
data class TmdbTvDetails(
    val seasons: List<TmdbSeasonOption>? = null
)

data class TmdbSeasonOption(
    @SerializedName("season_number") val seasonNumber: Int,
    val name: String? = null,
    @SerializedName("episode_count") val episodeCount: Int? = null,
    @SerializedName("poster_path") val posterPath: String? = null
)

data class SeasonImportRequest(
    @SerializedName("movie_id") val movieId: Int,
    val seasons: List<Int>
)

data class SeasonImportResponse(
    val success: Boolean? = null,
    val imported: Int? = null,
    val error: String? = null
)

data class TagItem(
    val tag: String,
    val count: Int
)

data class TagResponse(
    val data: List<TagItem>
)

// --- Listen / Wunschliste ---
// Antwort von POST /api/movies/{id}/wishlist
data class WishlistToggleResponse(
    val wishlisted: Boolean? = null
)

// Antwort der Cover/Backdrop-Upload-Endpunkte
data class ImageUploadResponse(
    val message: String? = null,
    @SerializedName("cover_url") val coverUrl: String? = null,
    @SerializedName("backdrop_url") val backdropUrl: String? = null
)

// Ein Listen-Eintrag laut Server: entweder ein Film aus der eigenen Sammlung
// ("movie") oder ein extern importierter TMDb-Titel ohne eigene Sammlung ("external").
data class ListItemRef(
    val type: String,
    val id: Int
)

data class MovieListSummary(
    val id: Int,
    val name: String? = null,
    val items: List<ListItemRef>? = null,
    @SerializedName("updated_at") val updatedAt: String? = null
) {
    val movieCount: Int get() = items?.size ?: 0
}

data class ListsResponse(
    val lists: List<MovieListSummary>? = null
)

data class ListDetailResponse(
    val id: Int,
    val name: String? = null,
    val items: List<Movie>? = null
)

data class ListMutationRequest(
    val name: String,
    val items: List<ListItemRef> = emptyList()
)

data class ListMutationResponse(
    val id: Int? = null,
    val name: String? = null
)

// Trailer von TMDb holen
data class FetchTrailerResponse(
    @SerializedName("trailer_url") val trailerUrl: String? = null,
    val found: Boolean? = null
)

// 2FA
data class TwoFactorEnableResponse(
    val secret: String? = null,
    @SerializedName("otpauth_url") val otpauthUrl: String? = null
)

data class TwoFactorConfirmResponse(
    val confirmed: Boolean? = null,
    @SerializedName("recovery_codes") val recoveryCodes: List<String>? = null
)

data class Actor(
    val id: Int? = null,
    @SerializedName("name", alternate = ["full_name", "actor_name", "display_name"]) val name: String? = null,
    @SerializedName("image_url", alternate = ["profile_path", "profile_url", "photo_url"]) val imageUrl: String? = null,
    @SerializedName("role", alternate = ["character"]) val role: String? = null,
    @SerializedName("is_main_role") val isMainRole: Boolean? = null,
    @SerializedName("movies") val movies: List<Movie>? = null,
    @SerializedName("bio", alternate = ["biography"]) val biography: String? = null,
    @SerializedName("birth_date", alternate = ["birthday"]) val birthDate: String? = null,
    @SerializedName("place_of_birth") val placeOfBirth: String? = null
)

data class Stats(
    @SerializedName("total_films") val totalFilms: Int,
    @SerializedName("total_runtime_minutes") val totalRuntimeMinutes: Long,
    @SerializedName("total_runtime_hours") val totalRuntimeHours: Double,
    @SerializedName("total_runtime_days") val totalRuntimeDays: Double,
    @SerializedName("avg_runtime") val avgRuntime: Double,
    val watched: WatchedStats?,
    val years: YearStats?,
    val collections: List<CollectionStats>?,
    val ratings: List<RatingStats>?,
    val genres: List<GenreStats>?,
    @SerializedName("year_distribution") val yearDistribution: Map<String, Int>?,
    val decades: List<DecadeStats>?
)

data class WatchedStats(
    val count: Int,
    val percentage: Double
)

data class YearStats(
    @SerializedName("avg_year") val avgYear: Double,
    @SerializedName("oldest_year") val oldestYear: Int,
    @SerializedName("newest_year") val newestYear: Int
)

data class CollectionStats(
    @SerializedName("collection_type") val collectionType: String,
    val count: Int,
    val percentage: Double
)

data class RatingStats(
    @SerializedName("rating_age") val ratingAge: Int,
    val count: Int
)

data class GenreStats(
    val genre: String,
    val count: Int
)

data class DecadeStats(
    val decade: Int,
    val count: Int,
    @SerializedName("avg_runtime") val avgRuntime: Double
)

data class TmdbImportRequest(
    @SerializedName("tmdb_id") val tmdbId: Int,
    val type: String = "movie",
    @SerializedName("in_collection") val inCollection: Boolean = true
)

/**
 * Antwort von GET /api/tmdb/search.
 *
 * Der Server (TmdbController::search -> TmdbService::searchMovie/searchTv)
 * reicht die TMDb-Rohantwort (/search/movie bzw. /search/tv) unverändert
 * durch: { page, results, total_pages, total_results }.
 * Quelle: app/Http/Controllers/Api/TmdbController.php,
 *         app/Services/TmdbService.php
 */
data class TmdbSearchResponse(
    val page: Int? = null,
    val results: List<TmdbSearchItem>? = null,
    @SerializedName("total_pages") val totalPages: Int? = null,
    @SerializedName("total_results") val totalResults: Int? = null
)

/**
 * Einzelnes TMDb-Suchergebnis. Movie-Felder (title, release_date) und
 * TV-Felder (name, first_air_date) werden über `alternate` zusammengeführt,
 * passend zu dem, was AddMovieScreen/TmdbMovieItem aus den Map-Einträgen liest.
 */
data class TmdbSearchItem(
    val id: Int? = null,
    @SerializedName("title", alternate = ["name"]) val title: String? = null,
    @SerializedName("release_date", alternate = ["first_air_date"]) val releaseDate: String? = null,
    @SerializedName("poster_path") val posterPath: String? = null,
    val overview: String? = null
)

/**
 * Request-Body für PUT /api/admin/movies/{id}.
 * Pflichtfelder: title, year, collection_type. Null-Felder werden von Gson
 * weggelassen und bleiben serverseitig unverändert erhalten.
 */
data class MovieUpdateRequest(
    val title: String,
    val year: Int,
    @SerializedName("collection_type") val collectionType: String,
    val genre: String? = null,
    val director: String? = null,
    val runtime: Int? = null,
    val rating: Double? = null,
    val overview: String? = null,
    val tag: String? = null,
    @SerializedName("trailer_url") val trailerUrl: String? = null,
    @SerializedName("in_collection") val inCollection: Boolean? = null
)

data class LoginResponse(
    val token: String?,
    val user: User?,
    @SerializedName("requires_2fa") val requires2fa: Boolean? = false,
    @SerializedName("user_id") val userId: Int? = null,
    @SerializedName("device_name") val deviceName: String? = null
)

data class User(
    val id: Int,
    val name: String? = null,
    val email: String? = null,
    @SerializedName("is_admin") val isAdmin: Boolean? = false,
    @SerializedName("two_factor_enabled") val twoFactorEnabled: Boolean? = false,
    @SerializedName("two_factor_confirmed_at") val twoFactorConfirmedAt: String? = null
)

data class UserUpdateResponse(
    val user: User?
)

data class ServerInfo(
    @SerializedName("app_name") val appName: String? = null,
    val version: String? = null
)

data class OAuthTokenResponse(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("token_type")   val tokenType: String = "Bearer"
)

data class OAuthUserInfo(
    val id: Int,
    val name: String,
    val username: String?,
    val email: String
)
