package info.movieshelf.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Das Aufräumen der Bildablage arbeitet auf Dateinamen. Ein Fehler darin
 * löscht Bilder, die noch gebraucht werden — deshalb hier festgeschrieben.
 */
class MediaStoreTest {

    @get:Rule
    val folder = TemporaryFolder()

    private fun store() = MediaStore(folder.root)

    private fun coversDir() = File(folder.root, "covers")

    private fun givenFile(name: String) {
        coversDir().mkdirs()
        File(coversDir(), name).writeBytes(byteArrayOf(1, 2, 3))
    }

    @Test
    fun `behaelt Dateien mit vorhandener Zeile`() {
        givenFile("7.jpg")
        givenFile("7_backdrop.jpg")
        givenFile("actor_3.jpg")

        val removed = store().removeOrphans(movieLocalIds = setOf(7L), actorLocalIds = setOf(3L))

        assertEquals(0, removed)
        assertEquals(3, coversDir().listFiles()!!.size)
    }

    @Test
    fun `entfernt Dateien ohne Zeile`() {
        givenFile("7.jpg")
        givenFile("99.jpg")
        givenFile("99_backdrop.jpg")
        givenFile("actor_42.jpg")

        val removed = store().removeOrphans(movieLocalIds = setOf(7L), actorLocalIds = emptySet())

        assertEquals(3, removed)
        assertEquals(listOf("7.jpg"), coversDir().listFiles()!!.map { it.name })
    }

    @Test
    fun `verwechselt Darsteller nicht mit Filmen`() {
        // actor_7 und 7 sind verschiedene Eintraege mit derselben Nummer.
        givenFile("7.jpg")
        givenFile("actor_7.jpg")

        store().removeOrphans(movieLocalIds = setOf(7L), actorLocalIds = emptySet())

        val names = coversDir().listFiles()!!.map { it.name }
        assertTrue("Der Film bleibt", names.contains("7.jpg"))
        assertFalse("Der Darsteller geht", names.contains("actor_7.jpg"))
    }

    @Test
    fun `raeumt abgebrochene Schreibvorgaenge weg`() {
        givenFile("7.jpg.part")

        val removed = store().removeOrphans(movieLocalIds = setOf(7L), actorLocalIds = emptySet())

        // Eine .part-Datei ist nie ein gueltiges Bild, auch wenn die Zeile existiert.
        assertEquals(1, removed)
        assertTrue(coversDir().listFiles()!!.isEmpty())
    }

    @Test
    fun `legt Bilder unter dem Namen der Desktop-App ab`() {
        val store = store()
        val cover = store.saveArtwork(42, ArtworkKind.COVER, byteArrayOf(1), "image/jpeg")
        val backdrop = store.saveArtwork(42, ArtworkKind.BACKDROP, byteArrayOf(1), "image/jpeg")
        val actor = store.saveArtwork(9, ArtworkKind.ACTOR, byteArrayOf(1), "image/jpeg")

        assertEquals("42.jpg", cover?.name)
        assertEquals("42_backdrop.jpg", backdrop?.name)
        assertEquals("actor_9.jpg", actor?.name)
    }

    @Test
    fun `weist negative IDs ab`() {
        // Der Name wird zu einem Pfad - eine manipulierte ID darf nicht
        // aus dem Verzeichnis herausfuehren.
        assertEquals(null, store().saveArtwork(-1, ArtworkKind.COVER, byteArrayOf(1), "image/jpeg"))
    }
}
