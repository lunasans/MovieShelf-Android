package at.neuhaus.movieshelf.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Die Bewertung wird immer mit genau einer Nachkommastelle gezeigt — wie im
 * Web (`number_format($movie->rating, 1)`). Der Rohwert taugt dafuer nicht:
 * je nach Herkunft stehen dort drei Stellen.
 */
class FormatRatingTest {

    @Test
    fun `ein TMDb-Stimmenschnitt wird auf eine Stelle gekuerzt`() {
        assertEquals("6.9", formatRating("6.874"))
    }

    @Test
    fun `eine Stelle bleibt, wie sie ist`() {
        assertEquals("7.5", formatRating("7.5"))
    }

    @Test
    fun `eine ganze Zahl bekommt ihre Stelle`() {
        assertEquals("8.0", formatRating("8"))
    }

    @Test
    fun `ein Komma als Trennzeichen wird verstanden`() {
        assertEquals("7.5", formatRating("7,5"))
    }

    @Test
    fun `unlesbares bleibt unveraendert stehen - lieber roh als gar nichts`() {
        assertEquals("k. A.", formatRating("k. A."))
    }
}
