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
}
