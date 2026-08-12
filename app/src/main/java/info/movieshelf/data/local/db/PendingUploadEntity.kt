package info.movieshelf.data.local.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

/**
 * Ein Bild, das noch zum Server soll.
 *
 * Bilder passen nicht in das Dirty-Muster der übrigen Felder: sie stehen nicht
 * in der Zeile, sondern als Datei daneben. Ohne Vormerkung wäre ein Cover, das
 * man ohne Netz auswählt, nach dem Verlassen des Formulars verloren.
 *
 * Der eindeutige Index über Film und Art sorgt dafür, dass eine erneute Auswahl
 * die vorherige ersetzt — hochgeladen wird am Ende nur das zuletzt gewählte Bild.
 */
@Entity(
    tableName = "pending_uploads",
    indices = [Index(value = ["movieLocalId", "kind"], unique = true)],
    foreignKeys = [
        ForeignKey(
            entity = MovieEntity::class,
            parentColumns = ["localId"],
            childColumns = ["movieLocalId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class PendingUploadEntity(
    @PrimaryKey(autoGenerate = true) val localId: Long = 0,
    val movieLocalId: Long,
    /** [UploadKind.COVER] oder [UploadKind.BACKDROP]. */
    val kind: String,
    /** Absoluter Pfad der zwischengespeicherten Datei. */
    val filePath: String,
    val mimeType: String,
    val createdAt: String
)

object UploadKind {
    const val COVER = "cover"
    const val BACKDROP = "backdrop"
}

@Dao
interface PendingUploadDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(upload: PendingUploadEntity)

    @Query("SELECT * FROM pending_uploads ORDER BY createdAt")
    suspend fun getAll(): List<PendingUploadEntity>

    @Query("SELECT * FROM pending_uploads WHERE movieLocalId = :movieLocalId")
    suspend fun getFor(movieLocalId: Long): List<PendingUploadEntity>

    @Query("DELETE FROM pending_uploads WHERE movieLocalId = :movieLocalId AND kind = :kind")
    suspend fun remove(movieLocalId: Long, kind: String)
}
