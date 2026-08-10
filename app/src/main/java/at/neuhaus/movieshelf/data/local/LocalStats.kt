package at.neuhaus.movieshelf.data.local

import at.neuhaus.movieshelf.data.local.db.MovieEntity
import at.neuhaus.movieshelf.data.model.CollectionStats
import at.neuhaus.movieshelf.data.model.DecadeStats
import at.neuhaus.movieshelf.data.model.GenreStats
import at.neuhaus.movieshelf.data.model.RatingStats
import at.neuhaus.movieshelf.data.model.Stats
import at.neuhaus.movieshelf.data.model.WatchedStats
import at.neuhaus.movieshelf.data.model.YearStats

/**
 * Statistik aus der lokalen Sammlung.
 *
 * Ohne Shelf gibt es kein `api/stats`, und im Shelf-Betrieb wäre ein Netzaufruf
 * für Zahlen, die vollständig lokal vorliegen, ohnehin Verschwendung. Gerechnet
 * wird in Kotlin statt in SQL: die Sammlung passt auf ein Telefon in den
 * Speicher, und die Genre-Aufteilung braucht ein Zerlegen der Komma-Liste, das
 * SQLite nicht kann.
 */
object LocalStats {

    fun from(movies: List<MovieEntity>): Stats {
        // Gezählt wird wie in der Desktop-App (`is_deleted = 0 AND is_boxset = 0
        // AND in_collection = 1`): die Teile eines Boxsets zählen einzeln, das
        // Boxset selbst nicht. Sonst stünde die Hülle als eigener Titel in der
        // Summe und die Gesamtzahl läge über der tatsächlichen Sammlung.
        val relevant = movies.filter {
            !it.isDeleted && it.inCollection != false && !it.isBoxset
        }
        val total = relevant.size

        val runtimes = relevant.mapNotNull { it.runtime }.filter { it > 0 }
        val totalRuntime = runtimes.sumOf { it.toLong() }

        val years = relevant.mapNotNull { it.year }.filter { it > 0 }
        val watchedCount = relevant.count { it.isWatched == true }

        return Stats(
            totalFilms = total,
            totalRuntimeMinutes = totalRuntime,
            totalRuntimeHours = totalRuntime / 60.0,
            totalRuntimeDays = totalRuntime / 1440.0,
            avgRuntime = if (runtimes.isEmpty()) 0.0 else runtimes.average(),
            watched = WatchedStats(
                count = watchedCount,
                percentage = if (total == 0) 0.0 else watchedCount * 100.0 / total
            ),
            years = if (years.isEmpty()) null else YearStats(
                avgYear = years.average(),
                oldestYear = years.min(),
                newestYear = years.max()
            ),
            collections = relevant
                .groupingBy { it.collectionType ?: "Film" }
                .eachCount()
                .map { (type, count) ->
                    CollectionStats(type, count, if (total == 0) 0.0 else count * 100.0 / total)
                }
                .sortedByDescending { it.count },
            ratings = relevant
                .mapNotNull { it.ratingAge }
                .groupingBy { it }
                .eachCount()
                .map { (age, count) -> RatingStats(age, count) }
                .sortedBy { it.ratingAge },
            genres = relevant
                // Genres stehen als Komma-Liste in einem Feld — ein Film mit
                // "Action, Thriller" zählt in beiden.
                .flatMap { entity -> entity.genre.orEmpty().split(",").map { it.trim() } }
                .filter { it.isNotBlank() }
                .groupingBy { it }
                .eachCount()
                .map { (genre, count) -> GenreStats(genre, count) }
                .sortedByDescending { it.count },
            yearDistribution = years
                .groupingBy { it.toString() }
                .eachCount()
                .toSortedMap(),
            decades = relevant
                .mapNotNull { entity -> entity.year?.takeIf { it > 0 }?.let { it / 10 * 10 to entity.runtime } }
                .groupBy({ it.first }, { it.second })
                .map { (decade, entries) ->
                    val decadeRuntimes = entries.filterNotNull().filter { it > 0 }
                    DecadeStats(
                        decade = decade,
                        count = entries.size,
                        avgRuntime = if (decadeRuntimes.isEmpty()) 0.0 else decadeRuntimes.average()
                    )
                }
                .sortedBy { it.decade }
        )
    }
}
