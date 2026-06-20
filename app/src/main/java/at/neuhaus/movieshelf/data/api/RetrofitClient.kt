package at.neuhaus.movieshelf.data.api

import android.content.Context
import android.util.Log
import at.neuhaus.movieshelf.BuildConfig
import at.neuhaus.movieshelf.data.SessionManager
import com.google.gson.GsonBuilder
import okhttp3.Cache
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit

object RetrofitClient {
    var baseUrl: String = "http://10.0.2.2:8000/"
        private set

    private val authInterceptor = Interceptor { chain ->
        val original = chain.request()
        val requestBuilder = original.newBuilder()

        val path = original.url.encodedPath
        val isApiRequest = path.contains("/api/")
        val hadToken = SessionManager.token != null

        if (isApiRequest) {
            requestBuilder.header("Accept", "application/json")
            requestBuilder.header("Content-Type", "application/json")

            SessionManager.token?.let {
                requestBuilder.header("Authorization", "Bearer $it")
            }
        }

        val response = chain.proceed(requestBuilder.build())

        // Abgelaufenes/ungültiges Token erkennen und Session beenden.
        // Login-/OAuth-Endpunkte ausnehmen (dort ist 401 = falsche Credentials),
        // ebenso den lokalen Demo-Modus.
        val isAuthEndpoint = path.contains("/login") || path.contains("/oauth")
        if (isApiRequest && hadToken && !isAuthEndpoint && !SessionManager.isDemo && response.code == 401) {
            SessionManager.invalidateSession()
        }

        response
    }

    private val logging = HttpLoggingInterceptor { message ->
        Log.d("API-Log", message)
    }.apply {
        // Nur im Debug-Build vollständig loggen; im Release keinerlei Body-/Header-Ausgabe.
        level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.NONE
        redactHeader("Authorization")
        redactHeader("Cookie")
    }

    private fun newClientBuilder(context: Context?): OkHttpClient.Builder {
        val builder = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(logging)
            .followRedirects(true)
            // Explizite Timeouts, damit ein nicht erreichbarer Server nicht ewig hängt.
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)

        // Cache hinzufügen, wenn Context vorhanden ist
        context?.let {
            val cacheSize = 10L * 1024L * 1024L // 10 MB
            val cacheDir = File(it.cacheDir, "http_cache")
            builder.cache(Cache(cacheDir, cacheSize))
        }
        return builder
    }

    private var _httpClient: OkHttpClient? = null
    val httpClient: OkHttpClient
        get() = _httpClient ?: newClientBuilder(null).build().also { _httpClient = it }

    private var _api: MovieShelfApi? = null

    val api: MovieShelfApi
        get() = _api ?: throw IllegalStateException("RetrofitClient not initialized")

    fun initialize(url: String, context: Context? = null): Boolean {
        try {
            val finalUrl = if (url.endsWith("/")) url else "$url/"
            finalUrl.toHttpUrlOrNull() ?: return false

            val gson = GsonBuilder()
                .setLenient()
                .create()

            _httpClient = newClientBuilder(context).build()
            baseUrl = finalUrl
            
            _api = Retrofit.Builder()
                .baseUrl(baseUrl)
                .addConverterFactory(GsonConverterFactory.create(gson))
                .client(_httpClient!!)
                .build()
                .create(MovieShelfApi::class.java)
            return true
        } catch (e: Exception) {
            Log.e("RetrofitClient", "Init failed", e)
            return false
        }
    }
}
