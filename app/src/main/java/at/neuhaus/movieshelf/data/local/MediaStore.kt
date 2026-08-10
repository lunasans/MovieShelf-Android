package at.neuhaus.movieshelf.data.local

import java.io.File

/**
 * Bildablage der App, mit zwei getrennten Zwecken.
 *
 * **Vorgemerkte Uploads** warten darauf, zur Shelf zu kommen. **Artwork**
 * gehört Filmen, die es sonst nirgends gibt — eigenständig angelegte Titel,
 * deren Cover von TMDb stammt.
 *
 * Beides liegt im privaten Speicher, nicht im Cache-Verzeichnis: Cache darf das
 * System jederzeit löschen. Bei vorgemerkten Uploads wäre die Datei dann weg,
 * bevor sie hochgeladen wurde; beim Artwork stünde die Sammlung ohne Bilder da,
 * sobald das Netz fehlt — und genau dafür ist der eigenständige Betrieb da.
 */
class MediaStore(private val baseDir: File) {

    private val pendingDir: File
        get() = File(baseDir, "pending_uploads").apply { mkdirs() }

    /**
     * Bild ablegen. Pro Film und Art gibt es genau eine Datei — eine erneute
     * Auswahl überschreibt die vorige, damit sich nichts ansammelt.
     */
    fun savePending(movieLocalId: Long, kind: String, bytes: ByteArray, mimeType: String): File {
        val file = File(pendingDir, "${movieLocalId}_$kind.${extensionFor(mimeType)}")
        // Ältere Datei mit anderer Endung entfernen, sonst bliebe sie liegen.
        pendingDir.listFiles()
            ?.filter { it.name.startsWith("${movieLocalId}_$kind.") && it.name != file.name }
            ?.forEach { it.delete() }
        file.writeBytes(bytes)
        return file
    }

    /** Wie in der Desktop-App: ein Verzeichnis `covers` für alle Bilder. */
    private val coversDir: File
        get() = File(baseDir, "covers").apply { mkdirs() }

    /**
     * Dateiname eines Bildes — die Namensgebung stammt aus der Desktop-App.
     *
     * Negative IDs werden abgewiesen: der Name wird zu einem Pfad, und eine
     * manipulierte ID darf nicht aus dem Verzeichnis herausführen.
     */
    fun fileNameFor(localId: Long, kind: String, extension: String = "jpg"): String? {
        if (localId < 0) return null
        return when (kind) {
            ArtworkKind.BACKDROP -> "${localId}_backdrop.$extension"
            ArtworkKind.ACTOR -> "actor_$localId.$extension"
            else -> "$localId.$extension"
        }
    }

    /**
     * Bild dauerhaft ablegen. Pro Eintrag und Art genau eine Datei; ein neues
     * Bild ersetzt das vorherige.
     */
    fun saveArtwork(localId: Long, kind: String, bytes: ByteArray, mimeType: String): File? {
        val name = fileNameFor(localId, kind, extensionFor(mimeType)) ?: return null
        val file = File(coversDir, name)
        val stem = name.substringBeforeLast('.')
        // Ältere Datei mit anderer Endung entfernen, sonst bliebe sie liegen.
        coversDir.listFiles()
            ?.filter { it.name.substringBeforeLast('.') == stem && it.name != name }
            ?.forEach { it.delete() }

        // Erst vollständig schreiben, dann an den Platz schieben: ein
        // abgebrochener Schreibvorgang darf kein halbes Bild hinterlassen.
        val temp = File(coversDir, "$name.part")
        return runCatching {
            temp.writeBytes(bytes)
            if (file.exists()) file.delete()
            if (!temp.renameTo(file)) throw IllegalStateException("Umbenennen fehlgeschlagen")
            file
        }.onFailure { temp.delete() }.getOrNull()
    }

    fun artworkFile(localId: Long, kind: String): File? {
        val stem = fileNameFor(localId, kind)?.substringBeforeLast('.') ?: return null
        return coversDir.listFiles()
            ?.firstOrNull { it.name.substringBeforeLast('.') == stem && !it.name.endsWith(".part") }
    }

    /** Alle Bilder eines Films entfernen — beim endgültigen Löschen. */
    fun deleteArtworkOf(movieLocalId: Long) {
        listOf(ArtworkKind.COVER, ArtworkKind.BACKDROP).forEach { kind ->
            artworkFile(movieLocalId, kind)?.delete()
        }
    }

    /** Ob ein Pfad in unsere Ablage zeigt — sonst ist es eine fremde Adresse. */
    fun isLocalPath(path: String?): Boolean =
        path != null && path.startsWith(baseDir.absolutePath)

    fun delete(path: String) {
        runCatching { File(path).delete() }
    }

    private fun extensionFor(mimeType: String): String = when {
        mimeType.contains("png") -> "png"
        mimeType.contains("webp") -> "webp"
        else -> "jpg"
    }
}

/**
 * Bildarten in der Ablage. Entspricht `type` in der Desktop-App
 * (`cover` | `backdrop` | `actor`).
 */
object ArtworkKind {
    const val COVER = "cover"
    const val BACKDROP = "backdrop"
    const val ACTOR = "actor"
}
