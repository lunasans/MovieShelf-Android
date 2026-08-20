package info.movieshelf.ui.dashboard

import info.movieshelf.data.model.Movie
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Sortieren und Filtern der Sammlung.
 *
 * Die Sorte Fehler, die hier lauert, meldet sich nie: die Liste hat immer
 * *irgendeine* Reihenfolge. Geprüft wird deshalb gerade das, was man beim
 * Draufschauen nicht bemerkt — Bewertungen mit Komma, fehlende Werte, und ob
 * bei gleichem Wert die Reihenfolge stabil bleibt.
 */
class MovieSortingTest {

    @Test
    fun `Bewertungen mit Komma zaehlen als Zahl`() {
        // Die Shelf liefert je nach Herkunft "8.2" oder "8,2". Ohne die
        // Umwandlung sortierte der zweite Fall als "keine Bewertung".
        assertEquals(8.2, movie(rating = "8,2").ratingValue()!!, 0.001)
        assertEquals(8.2, movie(rating = "8.2").ratingValue()!!, 0.001)
        assertEquals(10.0, movie(rating = " 10 ").ratingValue()!!, 0.001)
        assertNull(movie(rating = "keine").ratingValue())
        assertNull(movie(rating = null).ratingValue())
    }

    @Test
    fun `nach Titel wird gross- und kleinschreibungsunabhaengig sortiert`() {
        val sorted = sortMovies(
            listOf(movie(title = "zulu"), movie(title = "Alien"), movie(title = "arrival")),
            SortKey.TITLE,
            ascending = true
        )
        assertEquals(listOf("Alien", "arrival", "zulu"), sorted.map { it.title })
    }

    @Test
    fun `die Richtung dreht die Reihenfolge um`() {
        val sorted = sortMovies(
            listOf(movie(title = "Alien"), movie(title = "Zulu")),
            SortKey.TITLE,
            ascending = false
        )
        assertEquals(listOf("Zulu", "Alien"), sorted.map { it.title })
    }

    @Test
    fun `fehlende Werte stehen in beiden Richtungen hinten`() {
        val movies = listOf(
            movie(title = "Ohne Jahr", year = null),
            movie(title = "Alt", year = 1980),
            movie(title = "Neu", year = 2020)
        )

        // Genau der Fall, den ein umgedrehter Vergleich falsch macht: dreht man
        // die fertige Liste um, wandern die leeren Werte nach vorn.
        assertEquals(
            listOf("Alt", "Neu", "Ohne Jahr"),
            sortMovies(movies, SortKey.YEAR, ascending = true).map { it.title }
        )
        assertEquals(
            listOf("Neu", "Alt", "Ohne Jahr"),
            sortMovies(movies, SortKey.YEAR, ascending = false).map { it.title }
        )
    }

    @Test
    fun `bei gleichem Wert entscheidet der Titel`() {
        val movies = listOf(
            movie(title = "Zulu", year = 1999),
            movie(title = "Arrival", year = 1999),
            movie(title = "Matrix", year = 1999)
        )
        // Ohne diesen zweiten Schlüssel wechselte die Reihenfolge gleichwertiger
        // Titel bei jedem Aufbau der Liste.
        assertEquals(
            listOf("Arrival", "Matrix", "Zulu"),
            sortMovies(movies, SortKey.YEAR, ascending = true).map { it.title }
        )
    }

    @Test
    fun `nach Bewertung wird numerisch sortiert, nicht als Text`() {
        val movies = listOf(
            movie(title = "Neun", rating = "9.0"),
            movie(title = "Zehn", rating = "10.0"),
            movie(title = "Acht", rating = "8,5")
        )
        // Als Text sortiert stünde "10.0" vor "8,5" — der häufigste Fehler bei
        // Bewertungen, die als Zeichenkette vorliegen.
        assertEquals(
            listOf("Acht", "Neun", "Zehn"),
            sortMovies(movies, SortKey.RATING, ascending = true).map { it.title }
        )
    }

    @Test
    fun `der Genre-Filter vergleicht die Einzelteile`() {
        val movies = listOf(
            movie(title = "Mehrfach", genre = "Action, Drama"),
            movie(title = "Nur Drama", genre = "Drama"),
            movie(title = "Aehnlich", genre = "Dramödie"),
            movie(title = "Ohne", genre = null)
        )

        val drama = filterByGenre(movies, "Drama").map { it.title }
        assertEquals(listOf("Mehrfach", "Nur Drama"), drama)

        // "Action" steht an erster Stelle einer Liste — ein einfacher
        // Textvergleich der ganzen Spalte fände ihn nicht.
        assertEquals(listOf("Mehrfach"), filterByGenre(movies, "Action").map { it.title })
        assertEquals(4, filterByGenre(movies, null).size)
    }

    @Test
    fun `die Genre-Auswahl entsteht aus der Sammlung`() {
        val movies = listOf(
            movie(genre = "Action, Drama"),
            movie(genre = "drama"),
            movie(genre = "  Sci-Fi  "),
            movie(genre = null),
            movie(genre = "")
        )
        // Getrimmt, ohne Leere, alphabetisch — und "Drama"/"drama" ist ein
        // Eintrag, nicht zwei: sonst teilte eine unterschiedliche Schreibweise
        // aus zwei Quellen die Sammlung in zwei Hälften.
        assertEquals(listOf("Action", "Drama", "Sci-Fi"), genresOf(movies))
    }

    @Test
    fun `eine Sammelaktion fasst nur an, was noch nicht stimmt`() {
        val movies = listOf(
            movie(title = "Schon gesehen").copy(isWatched = true),
            movie(title = "Ungesehen").copy(isWatched = false),
            movie(title = "Unbekannt").copy(isWatched = null)
        )

        // Gesetzt, nicht umgeschaltet: sonst liefe bei gemischter Auswahl die
        // Haelfte in die falsche Richtung. Und jeder ueberfluessige Aufruf
        // ginge einzeln an die Shelf.
        assertEquals(
            listOf("Ungesehen", "Unbekannt"),
            moviesNeedingWatchedChange(movies, target = true).map { it.title }
        )
        assertEquals(
            listOf("Schon gesehen"),
            moviesNeedingWatchedChange(movies, target = false).map { it.title }
        )
    }

    private fun movie(
        title: String = "Titel",
        year: Int? = 2000,
        rating: String? = null,
        genre: String? = null,
        runtime: Int? = null
    ) = Movie(
        id = title.hashCode(),
        title = title,
        year = year,
        rating = rating,
        genre = genre,
        runtime = runtime
    )
}
