package info.movieshelf.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Schlüssel-Wert-Ablage für Zustand, der zur Datenbank gehört statt zum Gerät —
 * allen voran das Wasserzeichen des letzten Abgleichs. Er liegt bewusst nicht im
 * DataStore: wird die Datenbank verworfen, muss das Wasserzeichen mit ihr
 * verschwinden, sonst hielte der nächste Abgleich einen Vollstand für ein Delta
 * und die Sammlung bliebe halb leer.
 */
@Entity(tableName = "settings")
data class SettingEntity(
    @PrimaryKey val key: String,
    val value: String?
)

object SettingKeys {
    /** Server-Zeitstempel des letzten erfolgreichen Pulls (`exported_at`). */
    const val LAST_SYNC_AT = "last_sync_at"

    /** Betriebsmodus, siehe [AppMode]. */
    const val MODE = "mode"

}

/**
 * Wie die App betrieben wird.
 *
 * Der Modus liegt in der Datenbank, nicht in den Geraete-Einstellungen: er
 * beschreibt, woher der Bestand stammt. Wird die Datenbank verworfen, ist auch
 * die Frage nach dem Modus wieder offen.
 */
enum class AppMode(val key: String) {
    /** Eigener Bestand, keine Shelf, kein Konto. */
    STANDALONE("standalone"),

    /** An eine Shelf gebunden, mit Anmeldung und Abgleich. */
    SHELF("shelf");

    companion object {
        fun from(value: String?): AppMode? = entries.firstOrNull { it.key == value }
    }
}
