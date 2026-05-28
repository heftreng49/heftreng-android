package com.heftreng.app.viewmodel

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.heftreng.app.data.model.BlockedUser
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val auth     : FirebaseAuth,
    private val firestore: FirebaseFirestore,
) : ViewModel() {

    val currentEmail: String
        get() = auth.currentUser?.email ?: ""

    private val prefs: SharedPreferences =
        context.getSharedPreferences("hf_settings", Context.MODE_PRIVATE)

    private val _darkMode       = MutableStateFlow(prefs.getBoolean("hf_theme_dark", true))
    val darkMode = _darkMode.asStateFlow()

    private val _language       = MutableStateFlow(prefs.getString("hf_lang", "tr") ?: "tr")
    val language = _language.asStateFlow()

    private val _pushEnabled    = MutableStateFlow(prefs.getBoolean("hf_push", true))
    val pushEnabled = _pushEnabled.asStateFlow()

    private val _privateAccount = MutableStateFlow(false)
    val privateAccount = _privateAccount.asStateFlow()

    // ── Engellenen kullanıcılar ──────────────────────────────────────────────
    private val _blockedUsers   = MutableStateFlow<List<BlockedUser>>(emptyList())
    val blockedUsers = _blockedUsers.asStateFlow()

    private val _blockedLoading = MutableStateFlow(false)
    val blockedLoading = _blockedLoading.asStateFlow()

    init {
        loadPrivacySettings()
    }

    // ── Gizli profil — Firebase'den oku ─────────────────────────────────────
    private fun loadPrivacySettings() {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                val doc = firestore.collection("users").document(uid).get().await()
                _privateAccount.value = doc.getBoolean("private") ?: false
            } catch (_: Exception) {}
        }
    }

    // ── Engellenen kullanıcıları yükle ──────────────────────────────────────
    fun loadBlockedUsers() {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            _blockedLoading.value = true
            try {
                val snap = firestore.collection("users").document(uid)
                    .collection("blocked").get().await()
                _blockedUsers.value = snap.documents.mapNotNull { doc ->
                    val d = doc.data ?: return@mapNotNull null
                    BlockedUser(
                        uid         = doc.id,
                        displayName = d["displayName"] as? String ?: d["name"] as? String ?: "",
                        photoURL    = d["photoURL"] as? String ?: "",
                    )
                }
            } catch (_: Exception) {}
            finally { _blockedLoading.value = false }
        }
    }

    // ── Kullanıcıyı engelle ──────────────────────────────────────────────────
    fun blockUser(targetUid: String, targetName: String, targetPhoto: String) {
        val myUid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                firestore.collection("users").document(myUid)
                    .collection("blocked").document(targetUid).set(
                        mapOf(
                            "displayName" to targetName,
                            "photoURL"    to targetPhoto,
                            "blockedAt"   to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                        )
                    ).await()
                // Local state'i anında güncelle
                val updated = BlockedUser(uid = targetUid, displayName = targetName, photoURL = targetPhoto)
                if (_blockedUsers.value.none { it.uid == targetUid }) {
                    _blockedUsers.value = _blockedUsers.value + updated
                }
            } catch (_: Exception) {}
        }
    }

    // ── Engeli kaldır ────────────────────────────────────────────────────────
    fun unblockUser(blockedUid: String) {
        val myUid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                firestore.collection("users").document(myUid)
                    .collection("blocked").document(blockedUid).delete().await()
                _blockedUsers.value = _blockedUsers.value.filter { it.uid != blockedUid }
            } catch (_: Exception) {}
        }
    }

    // ── Şifre değiştir ───────────────────────────────────────────────────────
    fun changePassword(
        currentPassword: String,
        newPassword: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
    ) {
        val user  = auth.currentUser ?: return onError("Oturum bulunamadı")
        val email = user.email       ?: return onError("E-posta bulunamadı")
        viewModelScope.launch {
            try {
                val cred = EmailAuthProvider.getCredential(email, currentPassword)
                user.reauthenticate(cred).await()
                user.updatePassword(newPassword).await()
                onSuccess()
            } catch (e: Exception) {
                onError(e.localizedMessage ?: "Hata oluştu")
            }
        }
    }

    // ── E-posta değiştir ─────────────────────────────────────────────────────
    fun changeEmail(
        currentPassword: String,
        newEmail: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
    ) {
        val user  = auth.currentUser ?: return onError("Oturum bulunamadı")
        val email = user.email       ?: return onError("E-posta bulunamadı")
        viewModelScope.launch {
            try {
                val cred = EmailAuthProvider.getCredential(email, currentPassword)
                user.reauthenticate(cred).await()
                user.verifyBeforeUpdateEmail(newEmail).await()
                onSuccess()
            } catch (e: Exception) {
                onError(e.localizedMessage ?: "Hata oluştu")
            }
        }
    }

    fun toggleDarkMode() {
        viewModelScope.launch {
            val next = !_darkMode.value
            _darkMode.value = next
            prefs.edit().putBoolean("hf_theme_dark", next).apply()
        }
    }

    fun setLanguage(lang: String) {
        viewModelScope.launch {
            _language.value = lang
            prefs.edit().putString("hf_lang", lang).apply()
        }
    }

    fun togglePush() {
        viewModelScope.launch {
            val next = !_pushEnabled.value
            _pushEnabled.value = next
            prefs.edit().putBoolean("hf_push", next).apply()
        }
    }

    fun togglePrivate() {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            val next = !_privateAccount.value
            _privateAccount.value = next
            try {
                firestore.collection("users").document(uid)
                    .update("private", next).await()
            } catch (_: Exception) {}
        }
    }
}
