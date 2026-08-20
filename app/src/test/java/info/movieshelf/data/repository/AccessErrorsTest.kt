package info.movieshelf.data.repository

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

/**
 * Ein 404 dieser Endpunkte bedeutet etwas anderes als sonst: nicht "nicht
 * gefunden", sondern "diese Shelf kennt die Funktion noch nicht". Ohne diese
 * Unterscheidung stünde vor dem Nutzer "HTTP 404" statt eines Satzes, mit dem
 * er etwas anfangen kann.
 */
class AccessErrorsTest {

    private fun httpError(code: Int) = HttpException(
        Response.error<Any>(code, "".toResponseBody("application/json".toMediaType()))
    )

    @Test
    fun `404 wird zur Meldung ueber eine zu alte Shelf`() {
        assertThrows(AccessRepository.OutdatedShelfException::class.java) {
            mapAccessErrors { throw httpError(404) }
        }
    }

    @Test
    fun `andere Fehler bleiben, was sie sind`() {
        // 401 heisst "abgemeldet" und 500 "Serverfehler" — beides braucht eine
        // andere Antwort als "zu alt".
        listOf(401, 403, 500).forEach { code ->
            val fehler = assertThrows(HttpException::class.java) {
                mapAccessErrors { throw httpError(code) }
            }
            assertEquals(code, fehler.code())
        }
    }

    @Test
    fun `ohne Fehler kommt das Ergebnis durch`() {
        assertEquals(42, mapAccessErrors { 42 })
    }
}
