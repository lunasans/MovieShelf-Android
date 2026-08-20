package info.movieshelf.data.model

import com.google.gson.Gson
import info.movieshelf.data.local.db.MovieEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class ExportRatingParseTest {
    @Test
    fun `user_rating aus dem Export landet in der Zeile`() {
        val json = """
            {"exported_at":"2026-08-20T10:00:00Z","movies":[
              {"id":7,"title":"Arrival","year":2016,"user_rating":4,"is_watched":true}
            ]}
        """.trimIndent()

        val response = Gson().fromJson(json, ExportResponse::class.java)
        val movie = response.movies!!.single()
        assertEquals(4, movie.userRating)

        val entity = MovieEntity.fromServerMovie(movie, syncedAt = "2026-08-20T10:00:00Z")
        assertEquals(4, entity.userRating)
        assertEquals("Der Serverstand gilt als bestaetigt", 4, entity.syncedUserRating)
    }

    @Test
    fun `fehlt user_rating in der Antwort, ist es keine Bewertung`() {
        // Der Einzelfilm-Endpunkt der Shelf laedt die Relation nicht mit, das
        // Feld fehlt dort also ganz. Gson macht daraus null — deshalb darf eine
        // solche Antwort keine vorhandene Bewertung ueberschreiben, siehe
        // MovieRepository.getMovie().
        val movie = Gson().fromJson("""{"id":7,"title":"Arrival"}""", Movie::class.java)
        assertEquals(null, movie.userRating)
    }
}
