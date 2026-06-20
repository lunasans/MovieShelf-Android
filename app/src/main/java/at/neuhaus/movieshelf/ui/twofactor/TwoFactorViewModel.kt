package at.neuhaus.movieshelf.ui.twofactor

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.neuhaus.movieshelf.data.SessionManager
import at.neuhaus.movieshelf.data.api.RetrofitClient
import kotlinx.coroutines.launch
import retrofit2.HttpException

class TwoFactorViewModel : ViewModel() {

    // Initialer Status aus der aktuellen Session ableiten.
    private val initialUser = SessionManager.user
    var isEnabled by mutableStateOf(
        initialUser?.twoFactorEnabled == true || initialUser?.twoFactorConfirmedAt != null
    )
        private set

    var secret by mutableStateOf<String?>(null)
        private set
    var otpauthUrl by mutableStateOf<String?>(null)
        private set

    var code by mutableStateOf("")

    var recoveryCodes by mutableStateOf<List<String>?>(null)
        private set
    var justConfirmed by mutableStateOf(false)
        private set

    var isLoading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)

    fun clearError() {
        error = null
    }

    /** Initiiert die 2FA-Einrichtung: holt Secret + otpauth-URL vom Server. */
    fun startEnable() {
        viewModelScope.launch {
            isLoading = true
            error = null
            try {
                val response = RetrofitClient.api.enable2fa()
                secret = response.secret
                otpauthUrl = response.otpauthUrl
                code = ""
                if (secret == null) {
                    error = "Server-Fehler: Kein Schlüssel erhalten."
                }
            } catch (e: HttpException) {
                handleHttpError(e)
            } catch (e: Exception) {
                Log.e("MovieShelf_2FA", "Fehler bei enable2fa", e)
                error = "Verbindungsfehler: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    /** Bestätigt die Einrichtung mit dem eingegebenen Code. */
    fun confirm() {
        val trimmed = code.trim()
        if (trimmed.isBlank()) {
            error = "Bitte gib den Code ein."
            return
        }

        viewModelScope.launch {
            isLoading = true
            error = null
            try {
                val response = RetrofitClient.api.confirm2fa(mapOf("code" to trimmed))
                if (response.confirmed == true) {
                    recoveryCodes = response.recoveryCodes
                    justConfirmed = true
                    isEnabled = true
                    secret = null
                    otpauthUrl = null
                    code = ""
                    // Aktuelles Profil nachladen, damit der Status konsistent ist.
                    try {
                        SessionManager.user = RetrofitClient.api.getUser()
                    } catch (e: Exception) {
                        Log.w("MovieShelf_2FA", "Profil-Refresh fehlgeschlagen", e)
                    }
                } else {
                    error = "Ungültiger Code."
                }
            } catch (e: HttpException) {
                if (e.code() == 422) {
                    error = "Ungültiger Code."
                } else {
                    handleHttpError(e)
                }
            } catch (e: Exception) {
                Log.e("MovieShelf_2FA", "Fehler bei confirm2fa", e)
                error = "Verbindungsfehler: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    /** Deaktiviert 2FA für den aktuellen Benutzer. */
    fun disable() {
        viewModelScope.launch {
            isLoading = true
            error = null
            try {
                RetrofitClient.api.disable2fa()
                isEnabled = false
                secret = null
                otpauthUrl = null
                recoveryCodes = null
                justConfirmed = false
                code = ""
                try {
                    SessionManager.user = RetrofitClient.api.getUser()
                } catch (e: Exception) {
                    Log.w("MovieShelf_2FA", "Profil-Refresh fehlgeschlagen", e)
                }
            } catch (e: HttpException) {
                handleHttpError(e)
            } catch (e: Exception) {
                Log.e("MovieShelf_2FA", "Fehler bei disable2fa", e)
                error = "Verbindungsfehler: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    private fun handleHttpError(e: HttpException) {
        val errorBody = e.response()?.errorBody()?.string()
        error = when (e.code()) {
            422 -> parseServerMessage(errorBody) ?: "Ungültiger Code."
            else -> "Serverfehler: ${e.code()}"
        }
    }

    /** Liest das "message"-Feld aus einer JSON-Fehlerantwort robust aus. */
    private fun parseServerMessage(body: String?): String? {
        if (body.isNullOrBlank()) return null
        return try {
            com.google.gson.JsonParser.parseString(body)
                .asJsonObject
                .get("message")
                ?.asString
                ?.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            null
        }
    }
}
