package info.movieshelf.data.repository

import info.movieshelf.data.api.MovieShelfApi
import info.movieshelf.data.model.AccessToken
import retrofit2.HttpException

/**
 * Zugänge zum eigenen Konto: auflisten und widerrufen.
 *
 * Braucht eine Shelf ab 2.43.0. Ältere Fassungen kennen die Endpunkte nicht
 * und antworten mit 404 — das wird hier zu [OutdatedShelfException], damit die
 * Oberfläche einen verständlichen Satz zeigen kann statt „HTTP 404".
 */
class AccessRepository(private val apiProvider: () -> MovieShelfApi) {

    private val api get() = apiProvider()

    /** Die Shelf ist zu alt für diese Funktion. */
    class OutdatedShelfException : Exception()

    suspend fun tokens(): List<AccessToken> = mapErrors {
        api.getAccessTokens().data.orEmpty()
    }

    suspend fun revoke(id: Int) = mapErrors { api.revokeAccessToken(id) }

    /**
     * Alle anderen abmelden. Der eigene Zugang bleibt — das entscheidet die
     * Shelf, nicht die App: nur sie weiß, mit welchem Token der Aufruf kam.
     */
    suspend fun revokeOthers() = mapErrors { api.revokeOtherAccessTokens() }

    private inline fun <T> mapErrors(block: () -> T): T = mapAccessErrors(block)
}

/**
 * 404 dieser Endpunkte heisst nicht "nicht gefunden", sondern "diese Shelf
 * kennt die Funktion noch nicht" — sie kam erst mit 2.43.0 dazu.
 *
 * Ausserhalb der Klasse, damit die Umsetzung ohne API-Doppel pruefbar ist:
 * genau hier entscheidet sich, ob der Nutzer einen verstaendlichen Satz sieht
 * oder "HTTP 404".
 */
inline fun <T> mapAccessErrors(block: () -> T): T = try {
    block()
} catch (e: HttpException) {
    if (e.code() == 404) throw AccessRepository.OutdatedShelfException() else throw e
}
