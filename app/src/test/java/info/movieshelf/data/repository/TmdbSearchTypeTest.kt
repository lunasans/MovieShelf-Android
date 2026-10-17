package info.movieshelf.data.repository

import info.movieshelf.data.api.MovieShelfApi
import info.movieshelf.data.api.TmdbApi
import info.movieshelf.data.local.ImageDownloader
import info.movieshelf.data.local.MediaStore
import info.movieshelf.data.sync.FakeMovieDao
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.lang.reflect.Proxy

/**
 * Die Shelf sucht ohne `type` nur Filme. Fehlt der Typ in der Anfrage, findet
 * die App im Shelf-Betrieb keine einzige Serie — während Desktop und Web, die
 * ihn mitschicken, dieselbe Serie sofort finden.
 */
class TmdbSearchTypeTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val requests = mutableListOf<okhttp3.HttpUrl>()

    private val shelfApi: MovieShelfApi = Retrofit.Builder()
        .baseUrl("https://shelf.example.com/")
        .client(
            OkHttpClient.Builder().addInterceptor(Interceptor { chain ->
                requests += chain.request().url
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200).message("OK")
                    .body("""{"results":[]}""".toResponseBody("application/json".toMediaType()))
                    .build()
            }).build()
        )
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(MovieShelfApi::class.java)

    private fun repository() = TmdbRepository(
        movieRepository = MovieRepository(
            movieDao = FakeMovieDao(),
            actorDao = unused(),
            seriesDao = unused(),
            pendingUploadDao = unused(),
            mediaStore = MediaStore(folder.root),
            imageDownloader = ImageDownloader(shelfClientProvider = {
                throw AssertionError("Die Suche laedt keine Bilder")
            }),
            shelfUrlProvider = { "https://shelf.example.com" },
            isShelfMode = { true },
            apiProvider = { shelfApi }
        ),
        tmdbApi = unused<TmdbApi>(),
        shelfApiProvider = { shelfApi },
        isShelfMode = { true },
        apiKeyProvider = { null }
    )

    @Test
    fun `Seriensuche fragt die Shelf nach type=tv`() = runBlocking {
        repository().search("Dark", series = true)
        assertEquals("tv", requests.single().queryParameter("type"))
    }

    @Test
    fun `Filmsuche fragt die Shelf nach type=movie`() = runBlocking {
        repository().search("Dark", series = false)
        assertEquals("movie", requests.single().queryParameter("type"))
    }

    private inline fun <reified T> unused(): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, _ ->
            throw AssertionError("Die Suche braucht ${T::class.simpleName}.${method.name} nicht")
        } as T
}
