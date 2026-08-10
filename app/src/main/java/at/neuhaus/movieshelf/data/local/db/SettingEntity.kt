package at.neuhaus.movieshelf.data.local.db

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

    /** `standalone` oder `shelf` — ab Phase 6 relevant. */
    const val MODE = "mode"
}
