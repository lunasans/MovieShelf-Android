package info.movieshelf.data.jellyfin

import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Zugriff auf einen Jellyfin-Server.
 *
 * Kein Retrofit: die Basis-Adresse steht erst zur Laufzeit fest und kann sich
 * mit jeder Anmeldung ändern — ein Retrofit-Interface müsste dafür bei jedem
 * Wechsel neu gebaut werden. Ein schlanker OkHttp-Client mit von Hand gebauten
 * Adressen ist hier der geradere Weg.
 *
 * Die Anmeldung läuft über den `MediaBrowser`-Kopfzeilenwert, den Jellyfin
 * erwartet; der Token wandert ausschließlich an den konfigurierten Server.
 */
class JellyfinClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build(),
    private val gson: Gson = Gson()
) {

    /** Die Felder, ohne die der Import nicht auskommt. */
    private val itemFields =
        "Genres,Overview,ProviderIds,People,RunTimeTicks,OfficialRating,RemoteTrailers"

    private fun authHeader(deviceId: String, token: String? = null): String {
        val parts = mutableListOf(
            "Client=\"MovieShelf Android\"",
            "Device=\"MovieShelf\"",
            "DeviceId=\"$deviceId\"",
            "Version=\"1.0.0\""
        )
        if (token != null) parts += "Token=\"$token\""
        return "MediaBrowser " + parts.joinToString(", ")
    }

    /**
     * Am Server anmelden.
     *
     * @throws JellyfinError bei ungültiger Adresse, falschen Zugangsdaten oder
     *   unerreichbarem Server — die Oberfläche unterscheidet die Fälle.
     */
    suspend fun authenticate(
        baseUrl: String,
        username: String,
        password: String,
        deviceId: String
    ): JellyfinSession = withContext(Dispatchers.IO) {
        val base = normalizeBaseUrl(baseUrl)
        if (!base.startsWith("http://", true) && !base.startsWith("https://", true)) {
            throw JellyfinError.InvalidUrl
        }
        val url = "$base/Users/AuthenticateByName".toHttpUrlOrNull() ?: throw JellyfinError.InvalidUrl

        val body = gson.toJson(mapOf("Username" to username, "Pw" to password))
            .toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url(url)
            .post(body)
            .header("Authorization", authHeader(deviceId))
            .header("Accept", "application/json")
            .build()

        val response = try {
            client.newCall(request).execute()
        } catch (e: Exception) {
            throw JellyfinError.Unreachable(e.message)
        }

        response.use {
            if (it.code == 401) throw JellyfinError.BadCredentials
            if (!it.isSuccessful) throw JellyfinError.Unreachable("HTTP ${it.code}")

            val auth = gson.fromJson(it.body?.string().orEmpty(), JellyfinAuthResponse::class.java)
            val token = auth?.accessToken
            val userId = auth?.user?.id
            if (token.isNullOrBlank() || userId.isNullOrBlank()) throw JellyfinError.NoToken

            JellyfinSession(baseUrl = base, token = token, userId = userId)
        }
    }

    /** Auswählbare Bibliotheken: Filme und Serien, alles andere ist hier ohne Belang. */
    suspend fun libraries(session: JellyfinSession, deviceId: String): List<JellyfinLibrary> {
        val response = get<JellyfinViewsResponse>(
            session, deviceId, "/Users/${session.userId}/Views"
        )
        return response.items.orEmpty()
            .filter { it.collectionType == "movies" || it.collectionType == "tvshows" }
            .map { JellyfinLibrary(id = it.id, name = it.name.orEmpty(), type = it.collectionType.orEmpty()) }
    }

    /**
     * Alle Filme und Serien einer Bibliothek.
     *
     * Seitenweise, damit ein Server mit einigen tausend Titeln nicht in einer
     * einzigen Antwort geliefert werden muss.
     */
    suspend fun items(session: JellyfinSession, deviceId: String, libraryId: String): List<JellyfinItem> {
        val pageSize = 200
        val all = mutableListOf<JellyfinItem>()
        var start = 0

        while (true) {
            val page = get<JellyfinItemsResponse>(
                session, deviceId, "/Users/${session.userId}/Items",
                mapOf(
                    "parentId" to libraryId,
                    "recursive" to "true",
                    "includeItemTypes" to "Movie,Series",
                    "fields" to itemFields,
                    "startIndex" to start.toString(),
                    "limit" to pageSize.toString(),
                    "sortBy" to "SortName"
                )
            )
            val items = page.items.orEmpty()
            all += items
            if (items.size < pageSize) break
            start += pageSize
        }
        return all
    }

    suspend fun seasons(session: JellyfinSession, deviceId: String, seriesId: String): List<JellyfinItem> =
        get<JellyfinItemsResponse>(
            session, deviceId, "/Shows/$seriesId/Seasons", mapOf("userId" to session.userId)
        ).items.orEmpty()

    suspend fun episodes(session: JellyfinSession, deviceId: String, seriesId: String): List<JellyfinItem> =
        get<JellyfinItemsResponse>(
            session, deviceId, "/Shows/$seriesId/Episodes",
            mapOf("userId" to session.userId, "fields" to "Overview")
        ).items.orEmpty()

    /**
     * Ein Bild vom Server holen.
     *
     * @return Bytes und MIME-Typ, oder `null`. Ein fehlgeschlagener Download
     *   bricht nichts ab — der Titel ist auch ohne Bild angelegt.
     */
    suspend fun image(
        session: JellyfinSession,
        deviceId: String,
        itemId: String,
        type: String,
        maxWidth: Int
    ): Pair<ByteArray, String>? = withContext(Dispatchers.IO) {
        val url = buildUrl(
            session.baseUrl, "/Items/$itemId/Images/$type",
            mapOf("maxWidth" to maxWidth.toString(), "quality" to "90")
        ) ?: return@withContext null

        // Der Token gehört nur an den konfigurierten Server.
        if (!isSameOrigin(url.toString(), session.baseUrl)) return@withContext null

        runCatching {
            val request = Request.Builder()
                .url(url)
                .header("Authorization", authHeader(deviceId, session.token))
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val body = response.body ?: return@use null
                if (body.contentLength() > MAX_IMAGE_BYTES) return@use null
                val mime = response.header("Content-Type")?.substringBefore(';')?.trim() ?: "image/jpeg"
                val bytes = body.bytes()
                if (bytes.size > MAX_IMAGE_BYTES) null else bytes to mime
            }
        }.getOrNull()
    }

    private suspend inline fun <reified T> get(
        session: JellyfinSession,
        deviceId: String,
        path: String,
        params: Map<String, String> = emptyMap()
    ): T = withContext(Dispatchers.IO) {
        val url = buildUrl(session.baseUrl, path, params) ?: throw JellyfinError.InvalidUrl
        val request = Request.Builder()
            .url(url)
            .header("Authorization", authHeader(deviceId, session.token))
            .header("Accept", "application/json")
            .build()

        val response = try {
            client.newCall(request).execute()
        } catch (e: Exception) {
            throw JellyfinError.Unreachable(e.message)
        }

        response.use {
            if (it.code == 401) throw JellyfinError.NotAuthenticated
            if (!it.isSuccessful) throw JellyfinError.Unreachable("HTTP ${it.code}")
            gson.fromJson(it.body?.string().orEmpty(), T::class.java)
        }
    }

    private fun buildUrl(baseUrl: String, path: String, params: Map<String, String>): HttpUrl? {
        val builder = (normalizeBaseUrl(baseUrl) + path).toHttpUrlOrNull()?.newBuilder() ?: return null
        params.forEach { (key, value) -> builder.addQueryParameter(key, value) }
        return builder.build()
    }

    companion object {
        /** Wie in der Desktop-App und beim Shelf-Download: ein Bild sprengt nicht den Speicher. */
        const val MAX_IMAGE_BYTES = 10L * 1024 * 1024

        const val COVER_MAX_WIDTH = 600
        const val BACKDROP_MAX_WIDTH = 1280

        /** Portraits erscheinen nur als kleine Kreise — Coverbreite wäre Verschwendung. */
        const val PORTRAIT_MAX_WIDTH = 300
    }
}
