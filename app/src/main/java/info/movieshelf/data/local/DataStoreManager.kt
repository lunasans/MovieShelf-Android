package info.movieshelf.data.local

import androidx.annotation.StringRes
import info.movieshelf.R
import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** Vom Nutzer wählbare Theme-Einstellung. */
enum class ThemeMode(@StringRes val labelRes: Int) {
    DARK(R.string.theme_dark),
    LIGHT(R.string.theme_light),
    SYSTEM(R.string.theme_system)
}

class DataStoreManager(private val context: Context) {

    companion object {
        val SERVER_URL_KEY = stringPreferencesKey("server_url")
        val DYNAMIC_COLOR_KEY = booleanPreferencesKey("dynamic_color")
        val THEME_MODE_KEY = stringPreferencesKey("theme_mode")

        private const val SECURE_PREFS_NAME  = "secure_auth"
        private const val KEY_AUTH_TOKEN      = "auth_token"
        private const val KEY_OAUTH_STATE     = "oauth_state"
        private const val KEY_OAUTH_VERIFIER  = "oauth_verifier"
        private const val KEY_TMDB_API        = "tmdb_api_key"
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

    init {
        // EncryptedSharedPreferences-Init (Keystore-I/O) im Hintergrund vorwärmen,
        // damit der erste Token-Zugriff den Main-Thread nicht blockiert.
        CoroutineScope(Dispatchers.IO).launch { runCatching { _authToken.value } }
    }

    // --- Server-URL (nicht sicherheitskritisch -> normaler DataStore) ---
    val serverUrl: Flow<String?> = context.dataStore.data.map { it[SERVER_URL_KEY] }

    suspend fun saveServerUrl(url: String) {
        context.dataStore.edit { it[SERVER_URL_KEY] = url }
    }

    // --- Material You / Dynamic Color (nicht sicherheitskritisch) ---
    val dynamicColor: Flow<Boolean> = context.dataStore.data.map { it[DYNAMIC_COLOR_KEY] ?: false }

    suspend fun saveDynamicColor(enabled: Boolean) {
        context.dataStore.edit { it[DYNAMIC_COLOR_KEY] = enabled }
    }

    // --- Hell/Dunkel (nicht sicherheitskritisch) ---
    // Standard ist DARK, weil die Web-Oberfläche ausschließlich dunkel ist und
    // der "Shelf"-Look darauf ausgelegt ist.
    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { prefs ->
        ThemeMode.entries.firstOrNull { it.name == prefs[THEME_MODE_KEY] } ?: ThemeMode.DARK
    }

    suspend fun saveThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[THEME_MODE_KEY] = mode.name }
    }

    // --- TMDb-Schlüssel (verschlüsselt) ---
    // Der Nutzer hinterlegt seinen eigenen, wie in der Desktop-App. Ein im APK
    // mitgelieferter Schlüssel waere extrahierbar und haenge an einem fremden
    // Kontingent; deshalb liegt er hier neben dem Auth-Token im Keystore und
    // nicht im normalen DataStore.
    private val _tmdbApiKey by lazy { MutableStateFlow(securePrefs.getString(KEY_TMDB_API, null)) }
    val tmdbApiKey: Flow<String?> get() = _tmdbApiKey.asStateFlow()

    fun currentTmdbApiKey(): String? = _tmdbApiKey.value

    fun saveTmdbApiKey(key: String?) {
        val trimmed = key?.trim()?.takeIf { it.isNotBlank() }
        securePrefs.edit().apply {
            if (trimmed == null) remove(KEY_TMDB_API) else putString(KEY_TMDB_API, trimmed)
        }.apply()
        _tmdbApiKey.value = trimmed
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
