package at.neuhaus.movieshelf.ui.login

import android.content.Context
import android.net.Uri
import android.util.Base64
import android.util.Log
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.neuhaus.movieshelf.data.SessionManager
import at.neuhaus.movieshelf.data.api.RetrofitClient
import at.neuhaus.movieshelf.data.local.DataStoreManager
import at.neuhaus.movieshelf.data.model.User
import kotlinx.coroutines.launch
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID

class OAuthViewModel : ViewModel() {

    companion object {
        const val CLIENT_ID    = "filmdb-desktop"
        const val REDIRECT_URI = "movieshelf://oauth/callback"
    }

    var isLoading    by mutableStateOf(false)
    var error        by mutableStateOf<String?>(null)
    var loginSuccess by mutableStateOf(false)

    private var pendingState:    String? = null
    private var pendingVerifier: String? = null

    fun startOAuth(context: Context, shelfUrl: String, dataStoreManager: DataStoreManager) {
        if (shelfUrl.isBlank()) {
            error = "Bitte zuerst die Server-URL eintragen."
            return
        }

        pendingState    = UUID.randomUUID().toString()
        pendingVerifier = generateCodeVerifier()
        val challenge   = generateCodeChallenge(pendingVerifier!!)

        // State und Verifier persistieren, damit sie Process-Death überleben
        viewModelScope.launch {
            dataStoreManager.saveOAuthState(pendingState!!, pendingVerifier!!)
        }

        val baseUrl = shelfUrl.trimEnd('/')
        val uri = Uri.parse("$baseUrl/oauth/authorize").buildUpon()
            .appendQueryParameter("response_type",         "code")
            .appendQueryParameter("client_id",             CLIENT_ID)
            .appendQueryParameter("redirect_uri",          REDIRECT_URI)
            .appendQueryParameter("state",                 pendingState)
            .appendQueryParameter("code_challenge",        challenge)
            .appendQueryParameter("code_challenge_method", "S256")
            .build()

        CustomTabsIntent.Builder()
            .setShowTitle(true)
            .build()
            .launchUrl(context, uri)

        isLoading = true
    }

    fun handleCallback(
        uri: Uri,
        shelfUrl: String,
        dataStoreManager: DataStoreManager
    ) {
        val code       = uri.getQueryParameter("code")
        val state      = uri.getQueryParameter("state")
        val oauthError = uri.getQueryParameter("error")

        isLoading = false

        if (oauthError != null) {
            error = "Zugriff verweigert."
            return
        }

        viewModelScope.launch {
            // Wenn in-memory Werte fehlen (nach Process-Death), aus DataStore laden
            if (pendingState == null || pendingVerifier == null) {
                val (savedState, savedVerifier) = dataStoreManager.loadOAuthState()
                pendingState    = savedState
                pendingVerifier = savedVerifier
            }

            if (code == null || state == null || state != pendingState) {
                error = "OAuth Sicherheitsfehler – bitte erneut versuchen."
                dataStoreManager.clearOAuthState()
                return@launch
            }

            val verifier = pendingVerifier
            if (verifier == null) {
                error = "OAuth Sicherheitsfehler – bitte erneut versuchen."
                dataStoreManager.clearOAuthState()
                return@launch
            }

            isLoading = true
            error     = null
            try {
                val tokenResponse = RetrofitClient.api.exchangeOAuthCode(
                    mapOf(
                        "grant_type"    to "authorization_code",
                        "code"          to code,
                        "redirect_uri"  to REDIRECT_URI,
                        "client_id"     to CLIENT_ID,
                        "code_verifier" to verifier,
                    )
                )

                val bearer   = "Bearer ${tokenResponse.accessToken}"
                val userInfo = RetrofitClient.api.getOAuthUserInfo(bearer)

                SessionManager.token = tokenResponse.accessToken
                SessionManager.user  = User(
                    id    = userInfo.id,
                    name  = userInfo.name,
                    email = userInfo.email
                )
                dataStoreManager.saveAuthToken(tokenResponse.accessToken)
                // Vollständiges Profil inkl. is_admin nachladen (userinfo enthält es nicht)
                try { SessionManager.user = RetrofitClient.api.getUser() } catch (_: Exception) {}
                loginSuccess = true

                Log.i("MovieShelf_OAuth", "OAuth-Login erfolgreich")
            } catch (e: Exception) {
                Log.e("MovieShelf_OAuth", "Token-Austausch fehlgeschlagen", e)
                error = "Anmeldung fehlgeschlagen. Bitte erneut versuchen."
            } finally {
                isLoading        = false
                pendingState     = null
                pendingVerifier  = null
                dataStoreManager.clearOAuthState()
            }
        }
    }

    private fun generateCodeVerifier(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    private fun generateCodeChallenge(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash   = digest.digest(verifier.toByteArray(Charsets.US_ASCII))
        return Base64.encodeToString(hash, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }
}
