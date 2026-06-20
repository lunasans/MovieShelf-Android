package at.neuhaus.movieshelf.ui.login

import android.os.Build
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.neuhaus.movieshelf.data.SessionManager
import at.neuhaus.movieshelf.data.api.RetrofitClient
import at.neuhaus.movieshelf.data.local.DataStoreManager
import at.neuhaus.movieshelf.data.model.LoginResponse
import at.neuhaus.movieshelf.data.model.User
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import retrofit2.HttpException

class LoginViewModel : ViewModel() {
    var email by mutableStateOf("")
    var password by mutableStateOf("")
    var code2fa by mutableStateOf("")
    
    var isLoading by mutableStateOf(false)
    var is2faRequired by mutableStateOf(false)
    
    // Daten für Schritt 2
    private var userIdFor2fa: Int? = null
    private var deviceNameFor2fa: String? = null
    
    var error by mutableStateOf<String?>(null)
    var loginSuccess by mutableStateOf(false)

    fun onLoginClick(dataStoreManager: DataStoreManager) {
        if (email.isBlank() || password.isBlank()) {
            error = "Email und Passwort dürfen nicht leer sein."
            return
        }

        // LOKALER DEMO MODUS für Play Store Review
        val demoEmail = "demo@movieshelf.info"
        if (email.lowercase() == demoEmail && password == "playstore") {
            viewModelScope.launch {
                isLoading = true
                delay(1000) // Simulation Netzwerkverzögerung
                
                val demoUser = User(
                    id = 999,
                    name = "Demo User",
                    email = demoEmail
                )
                val demoResponse = LoginResponse(
                    token = "demo_token_123456789",
                    user = demoUser,
                    requires2fa = false
                )
                
                completeLogin(demoResponse, dataStoreManager)
                isLoading = false
            }
            return
        }

        viewModelScope.launch {
            isLoading = true
            error = null
            try {
                val deviceName = "${Build.MANUFACTURER} ${Build.MODEL}"
                Log.i("MovieShelf_Login", "Schritt 1: Login-Anfrage wird gesendet")
                
                val response = RetrofitClient.api.login(mapOf(
                    "email" to email,
                    "password" to password,
                    "device_name" to deviceName
                ))
                
                if (response.requires2fa == true) {
                    Log.i("MovieShelf_Login", "2FA erforderlich. UserID: ${response.userId}")
                    userIdFor2fa = response.userId
                    deviceNameFor2fa = response.deviceName ?: deviceName
                    is2faRequired = true
                } else {
                    Log.i("MovieShelf_Login", "Direkter Login erfolgreich.")
                    completeLogin(response, dataStoreManager)
                }
            } catch (e: HttpException) {
                handleHttpError(e)
            } catch (e: Exception) {
                Log.e("MovieShelf_Login", "Fehler in Schritt 1", e)
                error = "Verbindungsfehler: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    fun onVerify2faClick(dataStoreManager: DataStoreManager) {
        if (code2fa.isBlank() || userIdFor2fa == null) {
            error = "Bitte gib den Code ein."
            return
        }

        viewModelScope.launch {
            isLoading = true
            error = null
            try {
                Log.i("MovieShelf_Login", "Schritt 2: Sende Code für UserID $userIdFor2fa")
                val response = RetrofitClient.api.verify2fa(mapOf(
                    "user_id" to userIdFor2fa.toString(),
                    "device_name" to (deviceNameFor2fa ?: "Android Device"),
                    "code" to code2fa
                ))
                
                Log.i("MovieShelf_Login", "Schritt 2 erfolgreich!")
                completeLogin(response, dataStoreManager)
            } catch (e: HttpException) {
                Log.e("MovieShelf_Login", "Fehler in Schritt 2: ${e.code()}")
                if (e.code() == 422 || e.code() == 401) {
                    error = "Ungültiger 2FA-Code."
                } else {
                    handleHttpError(e)
                }
            } catch (e: Exception) {
                Log.e("MovieShelf_Login", "Verbindungsfehler in Schritt 2", e)
                error = "Verbindungsfehler: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    private suspend fun completeLogin(response: LoginResponse, dataStoreManager: DataStoreManager) {
        val token = response.token
        if (token.isNullOrBlank()) {
            error = "Server-Fehler: Kein Token erhalten."
            return
        }
        
        SessionManager.token = token
        SessionManager.user = response.user
        dataStoreManager.saveAuthToken(token)
        // Vollständiges Profil inkl. is_admin nachladen (Login-Antwort enthält es nicht)
        try { SessionManager.user = RetrofitClient.api.getUser() } catch (_: Exception) {}
        loginSuccess = true
    }

    private fun handleHttpError(e: HttpException) {
        val errorBody = e.response()?.errorBody()?.string()
        error = when (e.code()) {
            422 -> parseServerMessage(errorBody) ?: "Anmeldedaten ungültig."
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
