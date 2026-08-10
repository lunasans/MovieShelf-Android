package at.neuhaus.movieshelf.data.local

import android.util.Log
import at.neuhaus.movieshelf.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit

/**
 * Lädt Bilder herunter, damit sie der Sammlung dauerhaft gehören.
 *
 * Zwei Clients, weil die Bilder aus zwei Welten kommen: Cover der Shelf liegen
 * hinter deren Anmeldung und brauchen den Token, TMDb-Bilder sind öffentlich.
 * Den Shelf-Token an einen fremden Host zu schicken wäre ein Leck, deshalb
 * entscheidet [MediaHosts.needsShelfAuth] anhand der Adresse.
 *
 * Die Größenbegrenzung stammt aus der Desktop-App (`MAX_IMAGE_BYTES`) und
 * schützt davor, dass ein einzelnes Bild den Speicher volllaufen lässt.
 */
class ImageDownloader(
    /** Client der Shelf — bringt Token und Zertifikatseinstellungen mit. */
    private val shelfClientProvider: () -> OkHttpClient,
    private val publicClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        // Ohne diese Zeile sind Bild-Downloads im Protokoll unsichtbar: der
        // Shelf-Client bringt sein eigenes Logging mit, dieser nicht - und
        // seit Bilder vom Medien-Speicher ohne Token geholt werden, laufen
        // sie alle hierueber. BASIC statt BODY, sonst landen Bilddaten im Log.
        .addInterceptor(
            HttpLoggingInterceptor { message -> Log.d("Media-Log", message) }.apply {
                level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC
                else HttpLoggingInterceptor.Level.NONE
            }
        )
        .build()
) {
    /**
     * @param authenticated ob die Adresse zur Shelf gehört.
     * @return Bytes und MIME-Typ, oder `null` wenn der Abruf scheitert. Ein
     *   fehlgeschlagener Download bricht nichts ab — der Film ist auch ohne
     *   Bild angelegt, und der nächste Anlauf holt es nach.
     */
    suspend fun download(url: String, authenticated: Boolean): Pair<ByteArray, String>? =
        withContext(Dispatchers.IO) {
            val client = if (authenticated) shelfClientProvider() else publicClient
            runCatching {
                client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    val body = response.body ?: return@use null

                    val declaredLength = body.contentLength()
                    if (declaredLength > MAX_IMAGE_BYTES) return@use null

                    val mimeType = response.header("Content-Type")?.substringBefore(';')?.trim()
                        ?: "image/jpeg"
                    val bytes = body.bytes()

                    when {
                        bytes.isEmpty() -> null
                        bytes.size > MAX_IMAGE_BYTES -> null
                        // Fehlerseiten kommen gelegentlich mit HTTP 200 zurück.
                        // Ein HTML-Dokument als Cover abzulegen wäre schlimmer
                        // als gar kein Cover.
                        !mimeType.startsWith("image/") -> null
                        else -> bytes to mimeType
                    }
                }
            }.getOrNull()
        }

    companion object {
        /** Wie in der Desktop-App: 15 MB je Bild. */
        const val MAX_IMAGE_BYTES = 15L * 1024 * 1024
    }
}
