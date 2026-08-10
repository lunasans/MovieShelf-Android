package at.neuhaus.movieshelf.data.local.db

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Zeitstempel für lokale Änderungen.
 *
 * `java.time` steht nicht zur Verfügung (minSdk 24, kein Desugaring), deshalb
 * ein UTC-Formatierer. Die Werte werden nur zeichenweise verglichen — genau wie
 * in der Desktop-App —, weshalb das Format zwingend feste Feldbreiten braucht
 * und in UTC liegen muss. Ein lokal formatierter Zeitstempel würde bei
 * Zeitzonenwechsel plötzlich kleiner ausfallen und Änderungen verschlucken.
 *
 * Das Wasserzeichen des Abgleichs kommt dagegen immer vom Server; die Geräteuhr
 * darf darüber nicht entscheiden.
 */
object SyncClock {

    private val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    fun now(): String = synchronized(format) { format.format(Date()) }

    /**
     * Zeitstempel für die Anzeige: `10.08.26 14:45`, wie in der Desktop-App.
     *
     * Die gespeicherten Werte kommen aus zwei Quellen und sehen entsprechend
     * unterschiedlich aus — der Server liefert eine Zeitzonen-Angabe, unsere
     * eigenen enden auf `Z`. Beide werden versucht; passt keins, wird der
     * Rohwert gezeigt, statt gar nichts anzuzeigen.
     */
    fun formatForDisplay(raw: String?): String? {
        val value = raw?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val patterns = listOf(
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ss.SSSSSSXXX",
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
            "yyyy-MM-dd'T'HH:mm:ss'Z'"
        )
        for (pattern in patterns) {
            val parser = SimpleDateFormat(pattern, Locale.US)
            // Unsere eigenen Zeitstempel stehen in UTC, tragen aber keine
            // Zonenangabe - ohne diese Zeile laege die Anzeige daneben.
            if (pattern.endsWith("'Z'")) parser.timeZone = TimeZone.getTimeZone("UTC")
            val parsed = runCatching { parser.parse(value) }.getOrNull()
            if (parsed != null) return display.format(parsed)
        }
        return value
    }

    /** Ortszeit des Geräts — der Nutzer liest die Uhr, nicht den Server. */
    private val display = SimpleDateFormat("dd.MM.yy HH:mm", Locale.getDefault())
}
