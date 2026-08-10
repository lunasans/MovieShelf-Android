package at.neuhaus.movieshelf.data.local

import java.io.File

/**
 * Ablage für Bilder, die noch nicht beim Server sind.
 *
 * Liegt im privaten Speicher der App statt im Cache-Verzeichnis: Cache darf das
 * System jederzeit löschen, und ein vorgemerkter Upload wäre dann weg, bevor er
 * je hochgeladen wurde.
 */
class MediaStore(private val baseDir: File) {

    private val pendingDir: File
        get() = File(baseDir, "pending_uploads").apply { mkdirs() }

    /**
     * Bild ablegen. Pro Film und Art gibt es genau eine Datei — eine erneute
     * Auswahl überschreibt die vorige, damit sich nichts ansammelt.
     */
    fun savePending(movieLocalId: Long, kind: String, bytes: ByteArray, mimeType: String): File {
        val extension = when {
            mimeType.contains("png") -> "png"
            mimeType.contains("webp") -> "webp"
            else -> "jpg"
        }
        val file = File(pendingDir, "${movieLocalId}_$kind.$extension")
        // Ältere Datei mit anderer Endung entfernen, sonst bliebe sie liegen.
        pendingDir.listFiles()
            ?.filter { it.name.startsWith("${movieLocalId}_$kind.") && it.name != file.name }
            ?.forEach { it.delete() }
        file.writeBytes(bytes)
        return file
    }

    fun delete(path: String) {
        runCatching { File(path).delete() }
    }
}
