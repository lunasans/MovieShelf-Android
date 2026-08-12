package at.neuhaus.movieshelf.data.local.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import at.neuhaus.movieshelf.data.model.Actor
import at.neuhaus.movieshelf.data.model.Movie
import at.neuhaus.movieshelf.data.model.MovieUpdateRequest
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.lang.reflect.Type

/**
 * Die Sammlung als führende lokale Tabelle (nicht mehr als Cache).
 *
 * Zwei Identitäten pro Zeile, bewusst getrennt:
 * - [localId] vergibt die App. Sie bleibt über Abgleiche hinweg stabil und ist
 *   die einzige ID, mit der die Oberfläche navigieren darf.
 * - [remoteId] ist die ID der Shelf. Sie ist `null`, solange eine Zeile nur
 *   lokal existiert, und wird beim ersten erfolgreichen Push nachgetragen.
 *
 * Der Abgleich erkennt an [syncedAt] gegenüber [updatedAt], ob eine Zeile lokal
 * verändert und noch nicht übertragen wurde. Fehlt [syncedAt] ganz, war die
 * Zeile noch nie auf dem Server.
 */
@Entity(
    tableName = "movies",
    indices = [
        Index("remoteId", unique = true),
        Index("boxsetParentLocalId"),
        Index("inCollection"),
        Index("genre"),
        Index("director"),
        Index("year")
    ]
)
data class MovieEntity(
    @PrimaryKey(autoGenerate = true) val localId: Long = 0,
    val remoteId: Int? = null,
    val title: String?,
    val year: Int?,
    val rating: String?,
    val genre: String?,
    val overview: String?,
    val runtime: Int?,
    val director: String?,
    /**
     * Bildquelle — wie `cover_path` in der Desktop-App **eine** Spalte.
     *
     * Zunaechst die Adresse, von der das Bild zu holen ist (Shelf oder TMDb).
     * Nach erfolgreichem Download steht hier der Pfad der lokalen Datei und die
     * Adresse ist weg. Damit kann sie nicht zum Rueckfall werden und beim
     * Anzeigen erneut Verkehr verursachen.
     */
    val coverUrl: String?,
    val backdropUrl: String?,
    val trailerUrl: String?,
    val edition: String?,
    val regionCode: String?,
    val discLocation: String?,
    val purchaseDate: String?,
    val purchasePrice: Double?,
    val condition: String?,
    val viewCount: Int?,
    val isWatched: Boolean?,
    /**
     * Der "gesehen"-Stand, den der Server zuletzt bestaetigt hat.
     *
     * "Gesehen" haengt nicht am Film, sondern am Benutzer, und wird deshalb
     * ueber einen eigenen Endpunkt umgeschaltet — `MovieUpdateRequest` traegt
     * es gar nicht. Ohne diesen Vergleichswert waere beim Abgleich nicht zu
     * erkennen, ob die Markierung noch hochzuschicken ist.
     */
    val syncedWatched: Boolean? = null,
    val tmdbId: String?,
    val ratingAge: Int?,
    val tag: String?,
    val isBoxset: Boolean?,
    /** Aufgelöste lokale ID des Boxsets. Wird erst im zweiten Pull-Durchgang gesetzt. */
    val boxsetParentLocalId: Long? = null,
    /**
     * Die Shelf liefert in `boxset_parent_id` ihre eigene ID. Sie wird beim Pull
     * hier roh abgelegt und erst danach in [boxsetParentLocalId] übersetzt — in
     * einer einzigen Spalte wäre eine bereits aufgelöste lokale ID von einer
     * Server-ID nicht zu unterscheiden.
     */
    val boxsetParentRemoteId: Int? = null,
    val inCollection: Boolean?,
    val collectionType: String?,
    val collectionNo: Int? = null,
    val createdAt: String?,
    val updatedAt: String? = null,
    val syncedAt: String? = null,
    val isDeleted: Boolean = false,
    val actorsJson: String?,
    val boxsetChildrenJson: String?,
    val cachedAt: Long = System.currentTimeMillis()
) {
    /** Lokal verändert und noch nicht übertragen. */
    val isDirty: Boolean
        get() = syncedAt == null || (updatedAt != null && updatedAt > syncedAt)

    /**
     * "Gesehen" ist lokal gesetzt, der Server weiß noch nichts davon.
     *
     * Eigenes Merkmal neben [isDirty], weil die Markierung einen eigenen
     * Endpunkt hat: der Film-Push setzt [syncedAt] und macht die Zeile damit
     * sauber, während die Markierung noch aussteht. Ohne diese Unterscheidung
     * überschriebe der nächste Pull genau die Markierung, die noch hochsollte.
     */
    val hasPendingWatched: Boolean
        get() = (isWatched == true) != (syncedWatched == true)

    /**
     * In das Modell der Oberfläche übersetzen. [Movie.id] bleibt die Server-ID
     * für Netzaufrufe, [Movie.localId] trägt die lokale Identität — darüber
     * navigiert die Oberfläche.
     */
    fun toMovie(): Movie {
        return Movie(
            id = remoteId ?: 0,
            localId = localId,
            title = title,
            year = year,
            rating = rating,
            genre = genre,
            overview = overview,
            runtime = runtime,
            director = director,
            coverUrl = coverUrl,
            backdropUrl = backdropUrl,
            trailerUrl = trailerUrl,
            edition = edition,
            regionCode = regionCode,
            discLocation = discLocation,
            purchaseDate = purchaseDate,
            purchasePrice = purchasePrice,
            condition = condition,
            viewCount = viewCount,
            isWatched = isWatched,
            tmdbId = tmdbId,
            ratingAge = ratingAge,
            tag = tag,
            isBoxset = isBoxset,
            boxsetParentId = boxsetParentRemoteId,
            inCollection = inCollection,
            collectionType = collectionType,
            createdAt = createdAt,
            actors = if (actorsJson != null) gson.fromJson(actorsJson, actorListType) else null,
            boxsetChildren = if (boxsetChildrenJson != null) gson.fromJson(boxsetChildrenJson, movieListType) else null
        )
    }

    /**
     * Die Zeile als Anfrage an den Server. Gegenstueck zu [withRequest] und
     * vom Push benutzt, um lokale Aenderungen hochzuladen.
     */
    fun toUpdateRequest(): MovieUpdateRequest = MovieUpdateRequest(
        title = title.orEmpty(),
        year = year ?: 0,
        collectionType = collectionType ?: "Film",
        genre = genre,
        director = director,
        runtime = runtime,
        rating = rating?.toDoubleOrNull(),
        overview = overview,
        tag = tag,
        trailerUrl = trailerUrl,
        edition = edition,
        regionCode = regionCode,
        discLocation = discLocation,
        purchaseDate = purchaseDate,
        purchasePrice = purchasePrice,
        condition = condition,
        inCollection = inCollection
    )

    /**
     * Formulareingaben auf die Zeile anwenden.
     *
     * [syncedAt] bleibt absichtlich unangetastet: die Zeile gilt damit als
     * lokal verändert und wartet auf ihren Push. Erst dessen Erfolg stempelt
     * sie wieder als übertragen.
     */
    fun withRequest(request: MovieUpdateRequest, now: String): MovieEntity = copy(
        title = request.title,
        year = request.year,
        collectionType = request.collectionType,
        genre = request.genre,
        director = request.director,
        runtime = request.runtime,
        // Die Bewertung kommt als Zahl herein, liegt aber als Text vor, weil die
        // Shelf sie so ausliefert (etwa "8.8").
        rating = request.rating?.toString(),
        overview = request.overview,
        tag = request.tag,
        trailerUrl = request.trailerUrl,
        edition = request.edition,
        regionCode = request.regionCode,
        discLocation = request.discLocation,
        purchaseDate = request.purchaseDate,
        purchasePrice = request.purchasePrice,
        condition = request.condition,
        inCollection = request.inCollection ?: inCollection,
        updatedAt = now
    )

    companion object {

        /** Neue, nur lokal existierende Zeile aus den Formulareingaben. */
        fun fromRequest(request: MovieUpdateRequest, now: String): MovieEntity = MovieEntity(
            remoteId = null,
            title = request.title,
            year = request.year,
            rating = request.rating?.toString(),
            genre = request.genre,
            overview = request.overview,
            runtime = request.runtime,
            director = request.director,
            coverUrl = null,
            backdropUrl = null,
            trailerUrl = request.trailerUrl,
            edition = request.edition,
            regionCode = request.regionCode,
            discLocation = request.discLocation,
            purchaseDate = request.purchaseDate,
            purchasePrice = request.purchasePrice,
            condition = request.condition,
            viewCount = 0,
            isWatched = false,
            tmdbId = null,
            ratingAge = null,
            tag = request.tag,
            isBoxset = false,
            inCollection = request.inCollection ?: true,
            collectionType = request.collectionType,
            createdAt = now,
            updatedAt = now,
            // Noch nie beim Server gewesen — genau das macht die Zeile abweichend.
            syncedAt = null,
            actorsJson = null,
            boxsetChildrenJson = null
        )
        // Geteilte, threadsichere Instanzen statt pro Mapping-Aufruf neu zu erzeugen.
        // getParameterized statt anonymer TypeToken-Subklassen: so kann R8 die
        // generische Signatur nicht wegoptimieren (sonst Crash im Release-Build).
        private val gson = Gson()
        private val actorListType: Type = TypeToken.getParameterized(List::class.java, Actor::class.java).type
        private val movieListType: Type = TypeToken.getParameterized(List::class.java, Movie::class.java).type

        /**
         * Server-Antwort in eine lokale Zeile übersetzen. [syncedAt] wird gesetzt,
         * weil die Daten per Definition gerade vom Server kamen; [localId] bleibt
         * 0 und wird vom DAO auf eine bestehende Zeile gehoben, falls die
         * [Movie.id] hier schon bekannt ist.
         */
        fun fromServerMovie(movie: Movie, syncedAt: String? = null): MovieEntity {
            return MovieEntity(
                remoteId = movie.id,
                title = movie.title,
                year = movie.year,
                rating = movie.rating,
                genre = movie.genre,
                overview = movie.overview,
                runtime = movie.runtime,
                director = movie.director,
                coverUrl = movie.coverUrl,
                backdropUrl = movie.backdropUrl,
                trailerUrl = movie.trailerUrl,
                edition = movie.edition,
                regionCode = movie.regionCode,
                discLocation = movie.discLocation,
                purchaseDate = movie.purchaseDate,
                purchasePrice = movie.purchasePrice,
                condition = movie.condition,
                viewCount = movie.viewCount,
                isWatched = movie.isWatched,
                // Kommt der Stand vom Server, ist er dort per Definition bekannt.
                syncedWatched = movie.isWatched,
                tmdbId = movie.tmdbId,
                ratingAge = movie.ratingAge,
                tag = movie.tag,
                isBoxset = movie.isBoxset,
                boxsetParentRemoteId = movie.boxsetParentId,
                inCollection = movie.inCollection,
                collectionType = movie.collectionType,
                createdAt = movie.createdAt,
                // Der Server-Zeitstempel entscheidet, ob eine Zeile neuer ist
                // als der lokale Stand — nie die Geraeteuhr.
                updatedAt = movie.updatedAt ?: movie.createdAt,
                syncedAt = syncedAt ?: movie.updatedAt ?: movie.createdAt,
                isDeleted = movie.isDeleted == true,
                actorsJson = if (!movie.actors.isNullOrEmpty()) gson.toJson(movie.actors) else null,
                boxsetChildrenJson = if (!movie.boxsetChildren.isNullOrEmpty()) gson.toJson(movie.boxsetChildren) else null
            )
        }
    }
}
