package info.movieshelf.ui.profile

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import info.movieshelf.data.SessionManager
import info.movieshelf.data.api.RetrofitClient
import info.movieshelf.data.model.User
import kotlinx.coroutines.launch
import info.movieshelf.R
import info.movieshelf.ui.util.UiText

class ProfileViewModel : ViewModel() {
    var user by mutableStateOf<User?>(null)
    var name by mutableStateOf("")
    var email by mutableStateOf("")
    var twoFactorEnabled by mutableStateOf(false)
    
    var isLoading by mutableStateOf(false)
    var isSaving by mutableStateOf(false)
    var error by mutableStateOf<UiText?>(null)
    var successMessage by mutableStateOf<UiText?>(null)

    init {
        // Zuerst Cache nutzen für sofortige Anzeige
        SessionManager.user?.let { cachedUser ->
            user = cachedUser
            name = cachedUser.name ?: ""
            email = cachedUser.email ?: ""
            twoFactorEnabled = cachedUser.twoFactorEnabled == true || cachedUser.twoFactorConfirmedAt != null
        }
        // Bewusst kein Laden hier: im eigenstaendigen Betrieb gibt es keinen
        // Server, den man nach einem Profil fragen koennte. Wer den Modus
        // kennt, ist der Bildschirm — er stoesst das Laden an.
    }

    fun loadProfile() {
        viewModelScope.launch {
            if (user == null) isLoading = true
            error = null
            try {
                    val updatedUser = RetrofitClient.api.getUser()
                    user = updatedUser
                    name = updatedUser.name ?: ""
                    email = updatedUser.email ?: ""
                    twoFactorEnabled = updatedUser.twoFactorEnabled == true || updatedUser.twoFactorConfirmedAt != null
                    SessionManager.user = updatedUser
                Log.d("ProfileViewModel", "Profile successfully loaded")
            } catch (e: Exception) {
                Log.e("ProfileViewModel", "Failed to load profile", e)
                if (user == null) {
                    error = UiText.of(R.string.error_profile_load, e.message ?: "")
                }
            } finally {
                isLoading = false
            }
        }
    }

    fun updateProfile() {
        val currentUser = user ?: return
        viewModelScope.launch {
            isSaving = true
            error = null
            successMessage = null
            try {
                val updatedUser = currentUser.copy(
                    name = name,
                    email = email,
                    twoFactorEnabled = twoFactorEnabled
                )
                val response = RetrofitClient.api.updateUser(updatedUser)
                
                response.user?.let {
                    user = it
                    SessionManager.user = it
                    twoFactorEnabled = it.twoFactorEnabled == true || it.twoFactorConfirmedAt != null
                    successMessage = UiText.of(R.string.message_profile_updated)
                }
            } catch (e: Exception) {
                error = UiText.of(R.string.error_save_failed, e.message ?: "")
            } finally {
                isSaving = false
            }
        }
    }
}
