package info.movieshelf.ui.dashboard

import info.movieshelf.data.model.Movie

/**
 * Sortieren und Filtern der Sammlung — ohne Zustand, ohne Datenbank.
 *
 * Liegt bewusst neben dem ViewModel und nicht darin: die Regeln sind das
 * Einzige an der Sortierung, das still falsch sein kann. Eine Bewertung, die
 * als Text mit Komma vorliegt, oder ein fehlendes Jahr am falschen Ende fällt
 * niemandem auf, solange die Liste überhaupt eine Reihenfolge hat.
 */

/**
 * Die Bewertung als Zahl.
 *
 * Sie liegt als Text vor, weil die Shelf sie so ausliefert — und je nach
 * Herkunft mit Komma statt Punkt ("8,2"). Ohne diese Umwandlung sortierte
 * jeder solche Titel als "keine Bewertung".
 */
fun Movie.ratingValue(): Double? = rating?.trim()?.replace(',', '.')?.toDoubleOrNull()

/**
 * Nach Genre filtern.
 *
 * Die Shelf legt Genres als kommagetrennte Liste je Film ab ("Action, Drama").
 * Verglichen werden deshalb die Einzelteile und nicht der ganze Text: `contains`
 * fände "Drama" auch in "Dramödie" und "Action" nicht zuverlässig in
 * "Action, Drama".
 */
fun filterByGenre(movies: List<Movie>, genre: String?): List<Movie> {
    if (genre == null) return movies
    return movies.filter { movie ->
        movie.genre.orEmpty().split(",").any { it.trim().equals(genre, ignoreCase = true) }
    }
}

/**
 * Alle Genres einer Sammlung, alphabetisch und ohne Wiederholung.
 *
 * Es gibt keine Genre-Tabelle, aus der sich das lesen liesse; die Auswahl
 * entsteht aus dem, was tatsächlich in der Sammlung steht.
 */
fun genresOf(movies: List<Movie>): List<String> = movies
    .mapNotNull { it.genre }
    .flatMap { it.split(",") }
    .map { it.trim() }
    .filter { it.isNotBlank() }
    // Ohne Ruecksicht auf Gross-/Kleinschreibung zusammenfassen: kaeme
    // "Drama" von der Shelf und "drama" aus einem Jellyfin-Import, stuenden
    // beide in der Auswahl und teilten die Sammlung in zwei Haelften. Es
    // gewinnt die zuerst gesehene Schreibweise; der Filter vergleicht ohnehin
    // ohne Ruecksicht darauf.
    .distinctBy { it.lowercase() }
    .sortedWith(String.CASE_INSENSITIVE_ORDER)

/**
 * Nach dem gewählten Schlüssel sortieren.
 *
 * Fehlende Werte stehen **immer** am Ende, unabhängig von der Richtung: ein
 * Film ohne Jahr gehört nicht an die Spitze, nur weil er keines hat. Deshalb
 * wird die Richtung auf den Vergleich der Werte angewandt und nicht auf die
 * fertige Liste umgedreht.
 *
 * Bei Gleichstand entscheidet der Titel — sonst wechselte die Reihenfolge
 * gleichwertiger Titel bei jedem Aufbau der Liste.
 */
fun sortMovies(movies: List<Movie>, key: SortKey, ascending: Boolean): List<Movie> {
    val byTitle = compareBy(String.CASE_INSENSITIVE_ORDER) { movie: Movie -> movie.title.orEmpty() }

    val comparator: Comparator<Movie> = when (key) {
        SortKey.TITLE -> if (ascending) byTitle else byTitle.reversed()
        SortKey.YEAR -> nullsLastBy(ascending) { it.year }
        SortKey.RATING -> nullsLastBy(ascending) { it.ratingValue() }
        SortKey.RUNTIME -> nullsLastBy(ascending) { it.runtime }
        SortKey.ADDED -> nullsLastBy(ascending) { it.createdAt }
    }

    return movies.sortedWith(comparator.then(byTitle))
}

/**
 * Vergleich, bei dem `null` unabhängig von der Richtung hinten steht.
 *
 * `compareBy(nullsLast())` allein genügt nicht: kehrt man den fertigen
 * Vergleich um, wandern die leeren Werte nach vorn.
 */
private fun <T : Comparable<T>> nullsLastBy(
    ascending: Boolean,
    selector: (Movie) -> T?
): Comparator<Movie> = Comparator { a, b ->
    val left = selector(a)
    val right = selector(b)
    when {
        left == null && right == null -> 0
        left == null -> 1
        right == null -> -1
        ascending -> left.compareTo(right)
        else -> right.compareTo(left)
    }
}

/**
 * Welche Titel eine Sammelaktion tatsächlich anfassen muss.
 *
 * Gesetzt wird ein Zielzustand, nicht umgeschaltet: bei gemischter Auswahl
 * liefe ein Umschalter für die Hälfte in die falsche Richtung. Was schon
 * richtig steht, bleibt unberührt — jeder überflüssige Aufruf ginge einzeln an
 * die Shelf, die keinen Sammelaufruf kennt.
 */
fun moviesNeedingWatchedChange(movies: List<Movie>, target: Boolean): List<Movie> =
    movies.filter { (it.isWatched == true) != target }
