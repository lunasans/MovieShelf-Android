package info.movieshelf.data.repository

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Einen Zielzustand ueber einen Umschalter durchsetzen.
 *
 * Der Fall, der die Uebertragung von "gesehen" lange unbrauchbar machte:
 * stand die Shelf schon auf dem Wert, den die App setzen wollte, drehte der
 * Aufruf davon weg — quittiert mit 200 und `Movie marked as unwatched`, von
 * einem Erfolg also nicht zu unterscheiden.
 */
class WatchedStateTest {

    /** Eine Shelf, die nur umschalten kann — wie das Original. */
    private class Shelf(var state: Boolean) {
        var calls = 0

        fun toggle(): Boolean {
            calls++
            state = !state
            return state
        }
    }

    @Test
    fun `steht die Shelf schon richtig, wird zurueckgedreht`() = runBlocking {
        val shelf = Shelf(state = true)

        val result = applyWatchedState(desired = true) { shelf.toggle() }

        assertEquals(true, result)
        assertEquals("Der Zielzustand muss am Ende gelten", true, shelf.state)
        assertEquals("Einmal daneben, einmal zurueck", 2, shelf.calls)
    }

    @Test
    fun `steht die Shelf entgegengesetzt, genuegt ein Aufruf`() = runBlocking {
        val shelf = Shelf(state = false)

        val result = applyWatchedState(desired = true) { shelf.toggle() }

        assertEquals(true, result)
        assertEquals(true, shelf.state)
        assertEquals("Kein Aufruf zu viel", 1, shelf.calls)
    }

    @Test
    fun `das Zuruecknehmen laeuft genauso`() = runBlocking {
        val shelf = Shelf(state = false)

        val result = applyWatchedState(desired = false) { shelf.toggle() }

        assertEquals(false, result)
        assertEquals(false, shelf.state)
        assertEquals(2, shelf.calls)
    }

    @Test
    fun `mehr als zwei Aufrufe gibt es nie`() = runBlocking {
        // Eine Shelf, die sich nichts merkt: der Zielzustand ist nicht
        // erreichbar. Dann gilt ihr Stand - aber der Versuch endet trotzdem.
        val stur = object {
            var calls = 0
            fun toggle(): Boolean {
                calls++
                return false
            }
        }

        val result = applyWatchedState(desired = true) { stur.toggle() }

        assertEquals("Die Shelf behaelt recht", false, result)
        assertEquals(2, stur.calls)
    }
}
