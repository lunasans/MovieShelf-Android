package at.neuhaus.movieshelf.data.api

import at.neuhaus.movieshelf.data.model.*
import okhttp3.MultipartBody
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

    // Cover/Backdrop hochladen (nur Admins, Multipart)
    @Multipart
    @POST("api/admin/movies/{id}/cover")
    suspend fun uploadCover(
        @Path("id") id: Int,
        @Part cover: MultipartBody.Part
    ): ImageUploadResponse

    @Multipart
    @POST("api/admin/movies/{id}/backdrop")
    suspend fun uploadBackdrop(
        @Path("id") id: Int,
        @Part backdrop: MultipartBody.Part
    ): ImageUploadResponse

    @GET("api/search")
    suspend fun searchMovies(
        @Query("q") query: String,
        @Query("tag") tag: String? = null
    ): MovieResponse

    @GET("api/tags")
    suspend fun getTags(): TagResponse

    // Listen / Wunschliste
    @GET("api/lists")
    suspend fun getLists(): ListsResponse

    @GET("api/lists/{id}")
    suspend fun getList(@Path("id") id: Int): ListDetailResponse

    @POST("api/lists")
    suspend fun createList(@Body request: ListMutationRequest): ListMutationResponse

    @PUT("api/lists/{id}")
    suspend fun updateList(@Path("id") id: Int, @Body request: ListMutationRequest): ListMutationResponse

    @DELETE("api/lists/{id}")
    suspend fun deleteList(@Path("id") id: Int): Map<String, Any>

    // Film manuell anlegen (Admin) – gleiche Felder wie update
    @POST("api/admin/movies")
    suspend fun createMovie(@Body request: MovieUpdateRequest): SingleMovieResponse

    // Trailer von TMDb holen & speichern (Admin)
    @POST("api/admin/movies/{id}/fetch-trailer")
    suspend fun fetchTrailer(@Path("id") id: Int): FetchTrailerResponse

    // 2FA-Verwaltung
    @POST("api/user/2fa/enable")
    suspend fun enable2fa(): TwoFactorEnableResponse

    @POST("api/user/2fa/confirm")
    suspend fun confirm2fa(@Body request: Map<String, String>): TwoFactorConfirmResponse

    @POST("api/user/2fa/disable")
    suspend fun disable2fa(): Map<String, Any>

    @POST("api/movies/{id}/watched")
    suspend fun toggleWatched(@Path("id") id: Int): Map<String, Any>

    @POST("api/movies/{id}/wishlist")
    suspend fun toggleWishlist(@Path("id") id: Int): WishlistToggleResponse

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

    // Staffel-Liste einer Serie (für das Nachladen)
    @GET("api/tmdb/details")
    suspend fun getTmdbTvDetails(
        @Query("tmdb_id") tmdbId: Int,
        @Query("type") type: String = "tv"
    ): TmdbTvDetails

    // Staffeln für eine bestehende Serie nachladen (vorhandene überspringt der Server)
    @POST("api/tmdb/import-seasons")
    suspend fun importSeasons(
        @Body request: SeasonImportRequest
    ): SeasonImportResponse

    // Staffeln einer bestehenden Serie entfernen (Episoden cascaden auf dem Server)
    @POST("api/tmdb/remove-seasons")
    suspend fun removeSeasons(
        @Body request: SeasonImportRequest
    ): SeasonImportResponse

    // Stats - Rückgabe ist direkt das Stats Objekt
    @GET("api/stats")
    suspend fun getStats(): Stats

    // OAuth
    @POST("api/oauth/token")
    suspend fun exchangeOAuthCode(@Body request: Map<String, String>): OAuthTokenResponse

    @GET("api/oauth/userinfo")
    suspend fun getOAuthUserInfo(@Header("Authorization") bearer: String): OAuthUserInfo
}
