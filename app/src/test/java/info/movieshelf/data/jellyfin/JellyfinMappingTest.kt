package info.movieshelf.data.jellyfin

import info.movieshelf.data.api.TmdbCastMember
import info.movieshelf.data.api.TmdbCredits
import info.movieshelf.data.api.TmdbGenre
import info.movieshelf.data.api.TmdbMovieDetails
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Die Übersetzung Jellyfin → Sammlung, ohne Server.
 *
 * Geprüft wird vor allem, wo geraten werden könnte: Laufzeit in "Ticks",
 * Freigabe-Alter aus Freitext und die Frage, ob ein Suchtreffer derselbe Film
 * ist. An diesen drei Stellen wäre ein Fehler still — der Import liefe durch
 * und die Sammlung wäre falsch.
 */
class JellyfinMappingTest {

    @Test
    fun `Ticks werden zu Minuten`() {
        // 90 Minuten in 100-Nanosekunden-Einheiten
        assertEquals(90, ticksToMinutes(54_000_000_000))
        assertEquals(1, ticksToMinutes(600_000_000))
    }

    @Test
    fun `unbrauchbare Laufzeiten bleiben leer statt null Minuten zu ergeben`() {
        assertNull(ticksToMinutes(null))
        assertNull(ticksToMinutes(0))
        assertNull(ticksToMinutes(-5))
        // Weniger als eine halbe Minute rundet auf 0 — das ist keine Laufzeit.
        assertNull(ticksToMinutes(1000))
    }

    @Test
    fun `deutsche Freigaben tragen die Zahl direkt`() {
        assertEquals(16, parseRatingAge("FSK 16"))
        assertEquals(12, parseRatingAge("DE-12"))
        assertEquals(0, parseRatingAge("FSK 0"))
        assertEquals(18, parseRatingAge("18"))
    }

    @Test
    fun `US-Kuerzel werden genaehert, Unbekanntes bleibt leer`() {
        assertEquals(0, parseRatingAge("G"))
        assertEquals(6, parseRatingAge("PG"))
        assertEquals(18, parseRatingAge("TV-MA"))
        // Lieber keine Angabe als eine erfundene.
        assertNull(parseRatingAge("Unrated"))
        assertNull(parseRatingAge(""))
        assertNull(parseRatingAge(null))
    }

    @Test
    fun `PG-13 liefert 13 statt der Naeherung fuer PG`() {
        // Die Zahl im Text gewinnt — sonst waere "PG-13" faelschlich ab 6.
        assertEquals(13, parseRatingAge("PG-13"))
    }

    @Test
    fun `TMDb-ID wird unabhaengig von der Schreibweise gefunden`() {
        assertEquals(603, tmdbIdOf(item(providerIds = mapOf("Tmdb" to "603"))))
        assertEquals(603, tmdbIdOf(item(providerIds = mapOf("TMDB" to "603"))))
        assertNull(tmdbIdOf(item(providerIds = mapOf("Imdb" to "tt0133093"))))
        assertNull(tmdbIdOf(item(providerIds = mapOf("Tmdb" to "keine Zahl"))))
        assertNull(tmdbIdOf(item(providerIds = null)))
    }

    @Test
    fun `nur YouTube-Trailer werden uebernommen`() {
        val youtube = item(
            trailers = listOf(
                JellyfinTrailer(url = "https://vimeo.com/123"),
                JellyfinTrailer(url = "https://www.youtube.com/watch?v=abc")
            )
        )
        assertEquals("https://www.youtube.com/watch?v=abc", jellyfinTrailerUrl(youtube))

        // Ein Anbieter, den die App nicht abspielt, waere ein toter Link.
        assertNull(jellyfinTrailerUrl(item(trailers = listOf(JellyfinTrailer(url = "https://vimeo.com/123")))))
        assertNull(jellyfinTrailerUrl(item()))
    }

    @Test
    fun `ein Film wird vollstaendig uebersetzt`() {
        val entity = mapJellyfinItem(
            item(
                name = "Matrix",
                type = "Movie",
                year = 1999,
                genres = listOf("Action", "Science Fiction"),
                runTimeTicks = 81_600_000_000,
                officialRating = "FSK 16",
                communityRating = 8.2,
                providerIds = mapOf("Tmdb" to "603"),
                people = listOf(
                    JellyfinPerson(name = "Lana Wachowski", type = "Director"),
                    JellyfinPerson(name = "Keanu Reeves", type = "Actor", role = "Neo")
                ),
                userData = JellyfinUserData(played = true, playCount = 3)
            ),
            now = "2026-08-20T10:00:00Z"
        )

        assertEquals("Matrix", entity.title)
        assertEquals(1999, entity.year)
        assertEquals("Action, Science Fiction", entity.genre)
        assertEquals(136, entity.runtime)
        assertEquals(16, entity.ratingAge)
        assertEquals("8.2", entity.rating)
        assertEquals("603", entity.tmdbId)
        assertEquals("Lana Wachowski", entity.director)
        assertEquals("Film", entity.collectionType)
        assertEquals(JELLYFIN_IMPORT_TAG, entity.tag)
        assertEquals(true, entity.isWatched)
        assertEquals(3, entity.viewCount)
        // Ohne syncedAt gilt die Zeile als abweichend und geht beim naechsten
        // Abgleich zur Shelf — daran haengt der ganze Shelf-Modus.
        assertNull(entity.syncedAt)
    }

    @Test
    fun `eine Serie wird als Serie angelegt`() {
        val entity = mapJellyfinItem(item(name = "Dark", type = "Series"), now = "2026-08-20T10:00:00Z")
        assertEquals("Serie", entity.collectionType)
    }

    @Test
    fun `TMDb gewinnt, laesst aber leere Felder in Ruhe`() {
        val jellyfin = mapJellyfinItem(
            item(name = "The Matrix", year = 1999, genres = listOf("Action")),
            now = "2026-08-20T10:00:00Z"
        )
        val merged = mergeTmdbDetails(
            jellyfin,
            TmdbMovieDetails(
                id = 603,
                title = "Matrix",
                overview = "Neo erfaehrt die Wahrheit.",
                genres = listOf(TmdbGenre(name = "Action"), TmdbGenre(name = "Science Fiction")),
                runtime = 136,
                voteAverage = 8.2
            )
        )

        assertEquals("Matrix", merged.title)
        assertEquals("Action, Science Fiction", merged.genre)
        assertEquals(136, merged.runtime)
        assertEquals("603", merged.tmdbId)
        // TMDb liefert kein Jahr in diesem Datensatz: der Jellyfin-Wert bleibt.
        assertEquals(1999, merged.year)
        // Und der Gesehen-Status gehoert weiterhin Jellyfin.
        assertEquals(false, merged.isWatched)
    }

    @Test
    fun `ein Suchtreffer zaehlt nur bei gleichem Titel und Jahr`() {
        val results = listOf(
            Treffer("Matrix Reloaded", 2003),
            Treffer("Matrix", 1999),
            Treffer("Matrix", 2021)
        )

        val hit = pickTmdbMatch(results, "matrix", 1999, { it.titel }, { it.jahr })
        assertEquals(1999, hit?.jahr)

        // Falsches Jahr: lieber kein Treffer als der falsche Film — sonst zoege
        // er Besetzung und Bilder eines fremden Titels nach sich.
        assertNull(pickTmdbMatch(results, "Matrix", 1998, { it.titel }, { it.jahr }))
        assertNull(pickTmdbMatch(results, "", 1999, { it.titel }, { it.jahr }))
    }

    @Test
    fun `ohne bekanntes Jahr genuegt der Titel`() {
        val results = listOf(Treffer("Matrix", 1999))
        assertEquals(1999, pickTmdbMatch(results, "  MATRIX  ", null, { it.titel }, { it.jahr })?.jahr)
    }

    @Test
    fun `Besetzung kommt von TMDb, sonst von Jellyfin`() {
        val fromTmdb = castFromTmdb(
            TmdbMovieDetails(
                credits = TmdbCredits(
                    cast = listOf(TmdbCastMember(id = 6384, name = "Keanu Reeves", character = "Neo"))
                )
            )
        )
        assertEquals("Keanu Reeves", fromTmdb.single().name)
        assertEquals("Neo", fromTmdb.single().role)
        assertEquals(6384, fromTmdb.single().tmdbId)

        val fromJellyfin = castFromJellyfin(
            item(
                people = listOf(
                    JellyfinPerson(name = "Keanu Reeves", type = "Actor", role = "Neo"),
                    JellyfinPerson(name = "Lana Wachowski", type = "Director")
                )
            )
        )
        // Nur Schauspieler, keine Regie.
        assertEquals(1, fromJellyfin.size)
        assertEquals("Keanu Reeves", fromJellyfin.single().name)
        assertNull(fromJellyfin.single().tmdbId)
    }

    @Test
    fun `der Token geht nur an den eigenen Server`() {
        val base = "https://jellyfin.example.com"
        assertTrue(isSameOrigin("$base/Items/1/Images/Primary", base))
        assertTrue(!isSameOrigin("https://boese.example.com/Items/1/Images/Primary", base))
        // Anderer Port ist ein anderer Server.
        assertTrue(!isSameOrigin("https://jellyfin.example.com:9999/Items/1", base))
        assertTrue(!isSameOrigin("kein-url", base))
    }

    @Test
    fun `Basisadresse verliert den abschliessenden Schraegstrich`() {
        assertEquals("http://server:8096", normalizeBaseUrl("  http://server:8096/  "))
        assertEquals("http://server:8096", normalizeBaseUrl("http://server:8096"))
        assertEquals("", normalizeBaseUrl(null))
    }

    @Test
    fun `Titelvergleich ignoriert Schreibweise und Leerraum`() {
        assertEquals(normalizeTitle("  Der   Herr  der Ringe "), normalizeTitle("der herr der ringe"))
    }

    private data class Treffer(val titel: String?, val jahr: Int?)

    private fun item(
        name: String? = "Titel",
        type: String? = "Movie",
        year: Int? = null,
        genres: List<String>? = null,
        runTimeTicks: Long? = null,
        officialRating: String? = null,
        communityRating: Double? = null,
        providerIds: Map<String, String>? = null,
        people: List<JellyfinPerson>? = null,
        trailers: List<JellyfinTrailer>? = null,
        userData: JellyfinUserData? = null
    ) = JellyfinItem(
        id = "jf-1",
        name = name,
        type = type,
        productionYear = year,
        genres = genres,
        officialRating = officialRating,
        communityRating = communityRating,
        runTimeTicks = runTimeTicks,
        providerIds = providerIds,
        people = people,
        remoteTrailers = trailers,
        userData = userData
    )
}
