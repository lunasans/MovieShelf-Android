package at.neuhaus.movieshelf.data.api

import at.neuhaus.movieshelf.data.model.*
import retrofit2.http.*

interface MovieShelfApi {
    @POST("api/login")
    suspend fun login(@Body request: Map<String, String>): LoginResponse

    @POST("api/login/2fa")
    suspend fun verify2fa(@Body request: Map<String, String>): LoginResponse

    @GET("api/movies")
    suspend fun getMovies(
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 20,
        @Query("tag") tag: String? = null
    ): MovieResponse

    @GET("api/movies/{id}")
    suspend fun getMovie(@Path("id") id: Int): SingleMovieResponse

    // Film bearbeiten (nur Admins – serverseitig durch admin-Middleware geschützt)
    @PUT("api/admin/movies/{id}")
    suspend fun updateMovie(
        @Path("id") id: Int,
        @Body request: MovieUpdateRequest
    ): SingleMovieResponse

    // Film löschen (nur Admins)
    @DELETE("api/admin/movies/{id}")
    suspend fun deleteMovie(@Path("id") id: Int): Map<String, Any>

    @GET("api/search")
    suspend fun searchMovies(
        @Query("q") query: String,
        @Query("tag") tag: String? = null
    ): MovieResponse

    @GET("api/tags")
    suspend fun getTags(): TagResponse

    @POST("api/movies/{id}/watched")
    suspend fun toggleWatched(@Path("id") id: Int): Map<String, Any>

    @GET("api/info")
    suspend fun getServerInfo(): ServerInfo

    // User Profile
    @GET("api/user")
    suspend fun getUser(): User

    @PUT("api/user")
    suspend fun updateUser(@Body user: User): UserUpdateResponse

    // Actors
    @GET("api/actors")
    suspend fun getActors(
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 20
    ): ActorResponse

    @GET("api/actors/{id}")
    suspend fun getActor(@Path("id") id: Int): SingleActorResponse

    @GET("api/actors/search")
    suspend fun searchActors(@Query("q") query: String): ActorResponse

    // TMDb Integration
    @GET("api/tmdb/search")
    suspend fun searchTmdb(
        @Query("query") query: String
    ): TmdbSearchResponse

    @GET("api/tmdb/details")
    suspend fun getTmdbDetails(
        @Query("tmdb_id") tmdbId: Int
    ): Map<String, Any>

    @POST("api/tmdb/import")
    suspend fun importFromTmdb(
        @Body request: TmdbImportRequest
    ): SingleMovieResponse

    // Stats - Rückgabe ist direkt das Stats Objekt
    @GET("api/stats")
    suspend fun getStats(): Stats

    // OAuth
    @POST("api/oauth/token")
    suspend fun exchangeOAuthCode(@Body request: Map<String, String>): OAuthTokenResponse

    @GET("api/oauth/userinfo")
    suspend fun getOAuthUserInfo(@Header("Authorization") bearer: String): OAuthUserInfo
}
