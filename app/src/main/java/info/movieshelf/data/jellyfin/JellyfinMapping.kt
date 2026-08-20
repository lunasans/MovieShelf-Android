package info.movieshelf.data.jellyfin

import info.movieshelf.data.api.TmdbApi
import info.movieshelf.data.api.TmdbMovieDetails
import info.movieshelf.data.local.db.MovieEntity
import info.movieshelf.data.repository.LocalCastMember
import kotlin.math.roundToInt

/**
 * Übersetzung Jellyfin → Sammlung. Reine Funktionen, ohne Netz und ohne
 * Datenbank — genau deshalb liegen sie hier und nicht im Importer: so sind die
 * heiklen Stellen (Laufzeit, Freigabe-Alter, Duplikaterkennung) prüfbar, ohne
 * einen Jellyfin-Server zu brauchen.
 *
 * Vorlage ist `electron/handlers/jellyfin.ts` der Desktop-App; die Regeln
 * stimmen absichtlich überein, damit dieselbe Bibliothek auf beiden Wegen
 * dieselbe Sammlung ergibt.
 */

/** Jellyfin misst Laufzeiten in "Ticks" — 100-Nanosekunden-Einheiten. */
private const val TICKS_PER_MINUTE = 600_000_000.0

/**
 * Jellyfin verwaltet Dateien, keine Discs. Importierte Titel bekommen deshalb
 * dasselbe Medium wie andere dateibasierte Quellen der Shelf.
 */
const val JELLYFIN_IMPORT_TAG = "Streaming"

/** Wie viele Schauspieler je Titel übernommen werden. */
private const val MAX_CAST = 10

fun ticksToMinutes(ticks: Long?): Int? {
    if (ticks == null || ticks <= 0) return null
    return (ticks / TICKS_PER_MINUTE).roundToInt().takeIf { it > 0 }
}

/**
 * Freigabe-Alter aus dem `OfficialRating`.
 *
 * Deutsche Angaben (`FSK 16`, `DE-16`) tragen die Zahl direkt; für die
 * gängigen US-Kürzel gibt es eine Näherung. Alles Unbekannte bleibt leer,
 * statt zu raten — ein falsches Alter wäre schlimmer als keines.
 */
fun parseRatingAge(rating: String?): Int? {
    val value = rating?.trim()?.uppercase()?.takeIf { it.isNotEmpty() } ?: return null

    Regex("(\\d{1,2})").find(value)?.groupValues?.get(1)?.toIntOrNull()?.let { age ->
        return age.takeIf { it in 0..21 }
    }

    return when (value) {
        "G", "TV-G", "TV-Y" -> 0
        "PG", "TV-PG" -> 6
        "TV-14" -> 14
        "R" -> 16
        "NC-17", "TV-MA" -> 18
        else -> null
    }
}

/** TMDb-ID aus den ProviderIds. Die Schreibweise des Schlüssels variiert je Plugin. */
fun tmdbIdOf(item: JellyfinItem): Int? {
    val ids = item.providerIds ?: return null
    val key = ids.keys.firstOrNull { it.equals("tmdb", ignoreCase = true) } ?: return null
    return ids[key]?.toIntOrNull()?.takeIf { it > 0 }
}

/**
 * Trailer aus Jellyfins RemoteTrailers.
 *
 * Die App spielt YouTube ab; andere Anbieter werden übergangen, statt als
 * toter Link in der Sammlung zu landen.
 */
fun jellyfinTrailerUrl(item: JellyfinItem): String? =
    item.remoteTrailers.orEmpty()
        .mapNotNull { it.url }
        .firstOrNull { Regex("^https?://(www\\.)?(youtube\\.com|youtu\\.be)/", RegexOption.IGNORE_CASE).containsMatchIn(it) }

/** Titel für den Duplikat-Vergleich vereinheitlichen. */
fun normalizeTitle(title: String?): String =
    title.orEmpty().trim().lowercase().replace(Regex("\\s+"), " ")

/**
 * Ein Jellyfin-Item als Zeile der Sammlung.
 *
 * Ohne `localId` und Zeitstempel-Feinheiten: die setzt der Importer, weil er
 * weiß, ob die Zeile neu entsteht oder eine vorhandene ersetzt.
 */
fun mapJellyfinItem(item: JellyfinItem, now: String): MovieEntity {
    val people = item.people.orEmpty()
    val directors = people.filter { it.type == "Director" }.mapNotNull { it.name }
    val isSeries = item.type == "Series"

    return MovieEntity(
        remoteId = null,
        title = item.name.orEmpty(),
        year = item.productionYear,
        // Die Bewertung liegt als Text vor, weil die Shelf sie so ausliefert.
        rating = item.communityRating?.let { String.format(java.util.Locale.US, "%.1f", it) },
        genre = item.genres?.takeIf { it.isNotEmpty() }?.joinToString(", "),
        overview = item.overview?.takeIf { it.isNotBlank() },
        runtime = ticksToMinutes(item.runTimeTicks),
        director = directors.takeIf { it.isNotEmpty() }?.joinToString(", "),
        coverUrl = null,
        backdropUrl = null,
        trailerUrl = jellyfinTrailerUrl(item),
        edition = null,
        regionCode = null,
        discLocation = null,
        purchaseDate = null,
        purchasePrice = null,
        condition = null,
        viewCount = item.userData?.playCount ?: 0,
        isWatched = item.userData?.played == true,
        tmdbId = tmdbIdOf(item)?.toString(),
        ratingAge = parseRatingAge(item.officialRating),
        tag = JELLYFIN_IMPORT_TAG,
        isBoxset = false,
        inCollection = true,
        collectionType = if (isSeries) "Serie" else "Film",
        createdAt = now,
        updatedAt = now,
        // Noch nie beim Server gewesen — genau das macht die Zeile abweichend
        // und sorgt dafür, dass der nächste Abgleich sie hochschiebt.
        syncedAt = null,
        actorsJson = null,
        boxsetChildrenJson = null
    )
}

/**
 * TMDb-Details über die Jellyfin-Daten legen.
 *
 * TMDb gewinnt, wo es etwas liefert; leere Felder lassen den Jellyfin-Wert
 * stehen. Der Gesehen-Status und alles Sammlungsbezogene bleibt unangetastet —
 * das weiß nur Jellyfin beziehungsweise die App selbst.
 */
fun mergeTmdbDetails(mapped: MovieEntity, details: TmdbMovieDetails): MovieEntity = mapped.copy(
    tmdbId = details.id?.toString() ?: mapped.tmdbId,
    title = details.title?.takeIf { it.isNotBlank() } ?: mapped.title,
    overview = details.overview?.takeIf { it.isNotBlank() } ?: mapped.overview,
    genre = details.genreNames ?: mapped.genre,
    runtime = details.runtime?.takeIf { it > 0 } ?: mapped.runtime,
    rating = details.voteAverage?.let { String.format(java.util.Locale.US, "%.1f", it) } ?: mapped.rating,
    year = details.year ?: mapped.year,
    director = details.director?.takeIf { it.isNotBlank() } ?: mapped.director,
    trailerUrl = details.trailerUrl ?: mapped.trailerUrl
)

/**
 * Passender Treffer aus einer TMDb-Suche.
 *
 * Nur bei identischem Titel und — sofern bekannt — identischem Jahr. Lieber
 * kein Treffer als der falsche Film: ein falsch zugeordneter Titel zöge
 * Besetzung, Bilder und Beschreibung eines fremden Films nach sich.
 */
fun <T> pickTmdbMatch(
    results: List<T>,
    title: String?,
    year: Int?,
    titleOf: (T) -> String?,
    yearOf: (T) -> Int?
): T? {
    val wanted = normalizeTitle(title)
    if (wanted.isEmpty()) return null

    return results.firstOrNull { candidate ->
        normalizeTitle(titleOf(candidate)) == wanted && (year == null || yearOf(candidate) == year)
    }
}

/** Besetzung aus den TMDb-Credits — mit Rollennamen und Profilbildern. */
fun castFromTmdb(details: TmdbMovieDetails?): List<LocalCastMember> =
    details?.credits?.cast.orEmpty()
        .filter { !it.name.isNullOrBlank() }
        .take(MAX_CAST)
        .map { member ->
            LocalCastMember(
                name = member.name!!,
                role = member.character?.takeIf { it.isNotBlank() },
                imageUrl = TmdbApi.imageUrl(member.profilePath),
                tmdbId = member.id
            )
        }

/**
 * Besetzung aus Jellyfins People — der Rückfall ohne TMDb-Treffer.
 *
 * Das Bild bleibt hier leer: Jellyfin liefert Portraits nur über die Item-ID
 * hinter der Anmeldung, das lädt der Importer selbst nach.
 */
fun castFromJellyfin(item: JellyfinItem): List<LocalCastMember> =
    item.people.orEmpty()
        .filter { it.type == "Actor" && !it.name.isNullOrBlank() }
        .take(MAX_CAST)
        .map { person ->
            LocalCastMember(
                name = person.name!!,
                role = person.role?.takeIf { it.isNotBlank() },
                imageUrl = null,
                tmdbId = null
            )
        }

/** Basis-Adresse vereinheitlichen: ohne abschließenden Schrägstrich. */
fun normalizeBaseUrl(url: String?): String = url.orEmpty().trim().trimEnd('/')

/**
 * Gehört die Bild-Adresse zum konfigurierten Jellyfin-Server?
 *
 * Der Token darf nur dorthin. Ohne diese Prüfung könnte eine manipulierte
 * Antwort ihn an einen fremden Host schicken.
 */
fun isSameOrigin(url: String, baseUrl: String): Boolean = runCatching {
    val a = java.net.URI(url)
    val b = java.net.URI(baseUrl)
    a.scheme.equals(b.scheme, ignoreCase = true) &&
        a.host.equals(b.host, ignoreCase = true) &&
        a.port == b.port
}.getOrDefault(false)
