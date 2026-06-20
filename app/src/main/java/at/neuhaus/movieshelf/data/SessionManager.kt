package at.neuhaus.movieshelf.data

import at.neuhaus.movieshelf.data.model.User
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object SessionManager {
    var token: String? = null
    var user: User? = null

    private val _sessionExpired = MutableStateFlow(false)
    /** Wird true, sobald der Server ein Token mit HTTP 401 ablehnt. */
    val sessionExpired: StateFlow<Boolean> = _sessionExpired.asStateFlow()

    val isDemo: Boolean
        get() = token == "demo_token_123456789"

    /** Vom Netzwerk-Layer aufgerufen, wenn ein Bearer-Token ungültig/abgelaufen ist. */
    fun invalidateSession() {
        token = null
        user = null
        _sessionExpired.value = true
    }

    /** Nach erfolgtem Logout/Navigation zurücksetzen. */
    fun resetExpiredFlag() {
        _sessionExpired.value = false
    }
}
