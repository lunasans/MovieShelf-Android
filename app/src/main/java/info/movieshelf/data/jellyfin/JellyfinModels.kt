package info.movieshelf.data.jellyfin

import com.google.gson.annotations.SerializedName

/**
 * Ein Eintrag der Jellyfin-Bibliothek — Film, Serie, Staffel oder Episode.
 *
 * Jellyfin liefert für alle vier denselben Typ, unterschieden über `Type`.
 * Bewusst nur die Felder, die der Import braucht; die Antwort trägt ein
 * Vielfaches davon.
 */
data class JellyfinItem(
    @SerializedName("Id") val id: String = "",
    @SerializedName("Name") val name: String? = null,
    @SerializedName("Type") val type: String? = null,
    @SerializedName("ProductionYear") val productionYear: Int? = null,
    @SerializedName("Genres") val genres: List<String>? = null,
    @SerializedName("Overview") val overview: String? = null,
    @SerializedName("OfficialRating") val officialRating: String? = null,
    @SerializedName("CommunityRating") val communityRating: Double? = null,
    @SerializedName("RunTimeTicks") val runTimeTicks: Long? = null,
    @SerializedName("ProviderIds") val providerIds: Map<String, String>? = null,
    @SerializedName("People") val people: List<JellyfinPerson>? = null,
    @SerializedName("RemoteTrailers") val remoteTrailers: List<JellyfinTrailer>? = null,
    @SerializedName("ImageTags") val imageTags: Map<String, String>? = null,
    @SerializedName("BackdropImageTags") val backdropImageTags: List<String>? = null,
    @SerializedName("UserData") val userData: JellyfinUserData? = null,
    @SerializedName("IndexNumber") val indexNumber: Int? = null,
    @SerializedName("ParentIndexNumber") val parentIndexNumber: Int? = null
)

data class JellyfinPerson(
    @SerializedName("Id") val id: String? = null,
    @SerializedName("Name") val name: String? = null,
    @SerializedName("Type") val type: String? = null,
    @SerializedName("Role") val role: String? = null,
    @SerializedName("PrimaryImageTag") val primaryImageTag: String? = null
)

data class JellyfinTrailer(
    @SerializedName("Url") val url: String? = null,
    @SerializedName("Name") val name: String? = null
)

data class JellyfinUserData(
    @SerializedName("Played") val played: Boolean? = null,
    @SerializedName("PlayCount") val playCount: Int? = null
)

data class JellyfinItemsResponse(
    @SerializedName("Items") val items: List<JellyfinItem>? = null,
    @SerializedName("TotalRecordCount") val totalRecordCount: Int? = null
)

data class JellyfinView(
    @SerializedName("Id") val id: String = "",
    @SerializedName("Name") val name: String? = null,
    @SerializedName("CollectionType") val collectionType: String? = null
)

data class JellyfinViewsResponse(
    @SerializedName("Items") val items: List<JellyfinView>? = null
)

data class JellyfinAuthResponse(
    @SerializedName("AccessToken") val accessToken: String? = null,
    @SerializedName("User") val user: JellyfinUser? = null
)

data class JellyfinUser(
    @SerializedName("Id") val id: String? = null,
    @SerializedName("Name") val name: String? = null
)

/** Eine auswählbare Bibliothek des Servers. */
data class JellyfinLibrary(
    val id: String,
    val name: String,
    /** `movies` oder `tvshows` — bestimmt nur das Symbol in der Auswahl. */
    val type: String
)

/** Angemeldete Sitzung. Ohne alle drei Werte ist kein Aufruf möglich. */
data class JellyfinSession(
    val baseUrl: String,
    val token: String,
    val userId: String
)

/**
 * Fortschritt eines Importlaufs.
 *
 * Die Oberfläche zeigt damit sowohl einen Balken als auch den gerade
 * bearbeiteten Titel — bei tausend Filmen dauert der Lauf lange genug, dass
 * ein bloßer Kreisel keine Auskunft mehr wäre.
 */
data class JellyfinProgress(
    val phase: Phase,
    val current: Int = 0,
    val total: Int = 0,
    val title: String = "",
    val imported: Int = 0,
    val skipped: Int = 0,
    val failed: Int = 0
) {
    enum class Phase { LIBRARIES, ITEMS, DONE }
}

/** Ergebnis eines Importlaufs. */
data class JellyfinImportResult(
    val imported: Int = 0,
    val skipped: Int = 0,
    val failed: Int = 0,
    /**
     * Was einzeln schiefging. Ein Film ohne Besetzung oder Cover gilt trotzdem
     * als importiert — hier steht, was dabei verloren ging.
     */
    val errors: List<String> = emptyList()
)

/** Fehlerarten, die die Oberfläche unterschiedlich beantwortet. */
sealed class JellyfinError : Exception() {
    /** Adresse ohne http:// oder https:// — der häufigste Tippfehler. */
    object InvalidUrl : JellyfinError()

    /** Benutzername oder Passwort falsch (HTTP 401). */
    object BadCredentials : JellyfinError()

    /** Server erreichbar, liefert aber kein Token. */
    object NoToken : JellyfinError()

    /** Netzwerk, Zeitüberschreitung, Zertifikat — mit Begründung des Clients. */
    data class Unreachable(val reason: String?) : JellyfinError()

    /** Token abgelaufen oder Server neu aufgesetzt. */
    object NotAuthenticated : JellyfinError()
}
