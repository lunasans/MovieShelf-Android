package info.movieshelf.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Übernommen aus `media.test.ts` der Desktop-App, damit die Freigabeliste hier
 * dieselben Fälle abdeckt.
 */
class MediaHostsTest {

    @Test
    fun `registrierbare Domain sind die letzten zwei Label`() {
        assertEquals("example.com", MediaHosts.baseDomain("example.com"))
        assertEquals("example.com", MediaHosts.baseDomain("medien.example.com"))
        assertEquals("example.com", MediaHosts.baseDomain("a.b.example.com"))
    }

    @Test
    fun `oeffentliche Second-Level-TLDs zaehlen drei Label`() {
        assertEquals("example.co.uk", MediaHosts.baseDomain("shelf.example.co.uk"))
        assertEquals("example.co.uk", MediaHosts.baseDomain("example.co.uk"))
        assertEquals("example.com.au", MediaHosts.baseDomain("shelf.example.com.au"))
    }

    @Test
    fun `IPv4 bleibt unveraendert`() {
        assertEquals("192.168.1.10", MediaHosts.baseDomain("192.168.1.10"))
    }

    @Test
    fun `erlaubt denselben Host und Subdomains derselben Domain`() {
        val shelf = "https://shelf.example.com"
        assertTrue(MediaHosts.isAllowed("https://shelf.example.com/img.jpg", shelf))
        // Shelf-Installationen liefern Bilder oft von einer eigenen Medien-Domain.
        assertTrue(MediaHosts.isAllowed("https://medien.example.com/img.jpg", shelf))
    }

    @Test
    fun `blockiert fremde Hosts und Protokollwechsel`() {
        val shelf = "https://shelf.example.com"
        assertFalse(MediaHosts.isAllowed("https://boese.example.org/img.jpg", shelf))
        assertFalse(MediaHosts.isAllowed("http://medien.example.com/img.jpg", shelf))
    }

    @Test
    fun `erlaubt bei co uk-Shelf keine fremden co uk-Domains`() {
        val shelf = "https://meineshelf.co.uk"
        assertFalse(MediaHosts.isAllowed("https://evil-fremd.co.uk/img.jpg", shelf))
        assertTrue(MediaHosts.isAllowed("https://medien.meineshelf.co.uk/img.jpg", shelf))
    }

    @Test
    fun `TMDb ist als Bezugsquelle erlaubt`() {
        // Einmaliger Download beim Import. Angezeigt wird danach die Datei -
        // die Adresse steht nicht mehr in der Zeile und kann keinen weiteren
        // Verkehr ausloesen.
        assertTrue(MediaHosts.isAllowed("https://image.tmdb.org/t/p/w500/abc.jpg", null))
        assertTrue(MediaHosts.isAllowed("https://image.tmdb.org/t/p/w500/abc.jpg", "https://shelf.example.com"))
    }

    @Test
    fun `ohne Shelf bleibt alles andere gesperrt`() {
        assertFalse(MediaHosts.isAllowed("https://irgendwo.example.com/img.jpg", null))
    }

    @Test
    fun `nur die Shelf selbst bekommt den Token`() {
        val shelf = "https://shelf.example.com"
        assertTrue(MediaHosts.needsShelfAuth("https://shelf.example.com/media/img.jpg", shelf))

        // Die Medien-Domain ist Objektspeicher und liefert ohne Anmeldung aus.
        // Ein Token dorthin waere ein Leck, obwohl der Host einem selbst gehoert.
        assertFalse(MediaHosts.needsShelfAuth("https://medien.example.com/img.jpg", shelf))
        assertFalse(MediaHosts.needsShelfAuth("https://image.tmdb.org/t/p/w500/abc.jpg", shelf))
        assertFalse(MediaHosts.needsShelfAuth("https://medien.example.com/img.jpg", null))
    }

    @Test
    fun `die Medien-Domain bleibt zum Laden erlaubt`() {
        // Erlaubt heisst nicht angemeldet: geladen wird von dort, nur ohne Token.
        val shelf = "https://shelf.example.com"
        assertTrue(MediaHosts.isAllowed("https://medien.example.com/img.jpg", shelf))
        assertFalse(MediaHosts.needsShelfAuth("https://medien.example.com/img.jpg", shelf))
    }
}
