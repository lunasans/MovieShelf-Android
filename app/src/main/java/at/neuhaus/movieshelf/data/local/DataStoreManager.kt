package at.neuhaus.movieshelf.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class DataStoreManager(private val context: Context) {

    companion object {
        val SERVER_URL_KEY = stringPreferencesKey("server_url")

        private const val SECURE_PREFS_NAME  = "secure_auth"
        private const val KEY_AUTH_TOKEN      = "auth_token"
        private const val KEY_OAUTH_STATE     = "oauth_state"
        private const val KEY_OAUTH_VERIFIER  = "oauth_verifier"
    }

    /**
     * Verschlüsselter Speicher (Android Keystore / AES-256-GCM) für sicherheitskritische
     * Werte wie das Auth-Token und den transienten OAuth-State. So liegt das Token nicht
     * im Klartext auf dem Gerät und kann auch über Backups nicht ausgelesen werden.
     */
    private val securePrefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            SECURE_PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    // --- Server-URL (nicht sicherheitskritisch -> normaler DataStore) ---
    val serverUrl: Flow<String?> = context.dataStore.data.map { it[SERVER_URL_KEY] }

    suspend fun saveServerUrl(url: String) {
        context.dataStore.edit { it[SERVER_URL_KEY] = url }
    }

    // --- Auth-Token (verschlüsselt) ---
    // lazy, damit das Erzeugen des DataStoreManager (z.B. pro Recomposition)
    // nicht jedes Mal eine Entschlüsselung auslöst.
    private val _authToken by lazy { MutableStateFlow(securePrefs.getString(KEY_AUTH_TOKEN, null)) }
    val authToken: Flow<String?> get() = _authToken.asStateFlow()

    fun saveAuthToken(token: String?) {
        securePrefs.edit().apply {
            if (token == null) remove(KEY_AUTH_TOKEN) else putString(KEY_AUTH_TOKEN, token)
        }.apply()
        _authToken.value = token
    }

    // --- OAuth State/Verifier (verschlüsselt, transient) ---
    fun saveOAuthState(state: String, verifier: String) {
        securePrefs.edit()
            .putString(KEY_OAUTH_STATE, state)
            .putString(KEY_OAUTH_VERIFIER, verifier)
            .apply()
    }

    fun loadOAuthState(): Pair<String?, String?> =
        securePrefs.getString(KEY_OAUTH_STATE, null) to securePrefs.getString(KEY_OAUTH_VERIFIER, null)

    fun clearOAuthState() {
        securePrefs.edit()
            .remove(KEY_OAUTH_STATE)
            .remove(KEY_OAUTH_VERIFIER)
            .apply()
    }
}
