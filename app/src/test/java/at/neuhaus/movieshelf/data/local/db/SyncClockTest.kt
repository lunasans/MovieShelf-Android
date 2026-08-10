package at.neuhaus.movieshelf.data.local.db

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.TimeZone

class SyncClockTest {

    @Test
    fun `formatiert den Server-Zeitstempel in Ortszeit`() {
        val previous = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Europe/Vienna"))
            assertEquals("10.08.26 14:45", SyncClock.formatForDisplay("2026-08-10T14:45:19+02:00"))
            // Andere Zonenangabe, gleicher Zeitpunkt.
            assertEquals("10.08.26 14:45", SyncClock.formatForDisplay("2026-08-10T12:45:19+00:00"))
        } finally {
            TimeZone.setDefault(previous)
        }
    }

    @Test
    fun `versteht auch die eigenen Zeitstempel`() {
        val previous = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Europe/Vienna"))
            // now() schreibt UTC ohne Zonenangabe - ohne Sonderbehandlung
            // laege die Anzeige zwei Stunden daneben.
            assertEquals("10.08.26 14:45", SyncClock.formatForDisplay("2026-08-10T12:45:19Z"))
        } finally {
            TimeZone.setDefault(previous)
        }
    }

    @Test
    fun `zeigt Unbekanntes lieber roh als gar nicht`() {
        assertEquals("irgendwas", SyncClock.formatForDisplay("irgendwas"))
        assertNull(SyncClock.formatForDisplay(null))
        assertNull(SyncClock.formatForDisplay("  "))
    }
}
