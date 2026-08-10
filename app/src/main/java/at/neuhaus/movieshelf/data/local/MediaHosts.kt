package at.neuhaus.movieshelf.data.local

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Von welchen Adressen Bilder geladen werden dürfen.
 *
 * Portiert aus `electron/handlers/media.ts` der Desktop-App. Der Gedanke dort:
 * ein Download-Aufruf mit frei wählbarer Adresse ist ein Einfallstor, deshalb
 * werden nur bekannte Hosts akzeptiert — die Shelf selbst und Subdomains
 * derselben registrierbaren Domain, weil Shelf-Installationen ihre Bilder oft
 * von einer eigenen Medien-Domain ausliefern.
 *
 * **Abweichung zur Desktop-App:** dort ist TMDb ausdrücklich blockiert, und
 * eigenständig übernommene Filme behalten die TMDb-Adresse. Auf dem Telefon ist
 * das keine Option — ohne Netz stünde die Sammlung dann ohne Cover da, und
 * genau dafür ist der eigenständige Betrieb gedacht. `image.tmdb.org` ist
 * deshalb zusätzlich erlaubt: ein fest eingebauter, bekannter Host, keine frei
 * wählbare Adresse.
 */
object MediaHosts {

    const val TMDB_IMAGE_HOST = "image.tmdb.org"

    // Übliche Second-Level-Labels unter Länder-TLDs (co.uk, com.au …). Dort sind
    // zwei Labels keine registrierbare Domain, sondern öffentlicher Namensraum.
    private val PUBLIC_SECOND_LEVEL = setOf("co", "com", "net", "org", "gov", "edu", "ac")

    private val IPV4 = Regex("""^\d{1,3}(\.\d{1,3}){3}$""")

    /**
     * Registrierbare Domain — vereinfachte Näherung: die letzten zwei Labels,
     * bei öffentlichen Second-Level-TLDs wie `co.uk` die letzten drei.
     * IPv4-Adressen bleiben unverändert.
     */
    fun baseDomain(host: String): String {
        if (IPV4.matches(host)) return host
        val labels = host.split(".")
        if (labels.size <= 2) return host
        val tld = labels.last()
        val second = labels[labels.size - 2]
        val take = if (tld.length == 2 && second in PUBLIC_SECOND_LEVEL) 3 else 2
        return labels.takeLast(take).joinToString(".")
    }

    /** Ob von dieser Adresse geladen werden darf. */
    fun isAllowed(url: HttpUrl, shelfUrl: HttpUrl?): Boolean {
        if (url.host == TMDB_IMAGE_HOST) return true
        if (shelfUrl == null) return false
        if (url.scheme != shelfUrl.scheme) return false
        if (url.host == shelfUrl.host && url.port == shelfUrl.port) return true
        return baseDomain(url.host) == baseDomain(shelfUrl.host)
    }

    fun isAllowed(url: String, shelfUrl: String?): Boolean {
        val parsed = url.toHttpUrlOrNull() ?: return false
        return isAllowed(parsed, shelfUrl?.toHttpUrlOrNull())
    }

    /** Ob die Adresse zur Shelf gehört und damit den Anmelde-Token braucht. */
    fun needsShelfAuth(url: String, shelfUrl: String?): Boolean {
        val parsed = url.toHttpUrlOrNull() ?: return false
        if (parsed.host == TMDB_IMAGE_HOST) return false
        val shelf = shelfUrl?.toHttpUrlOrNull() ?: return false
        return baseDomain(parsed.host) == baseDomain(shelf.host)
    }
}
