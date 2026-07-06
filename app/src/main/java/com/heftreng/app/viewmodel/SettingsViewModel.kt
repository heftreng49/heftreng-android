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

    // ÇÖZÜLDÜ: Eskiden sadece "hf_theme_dark" (Boolean) tutuluyordu — bu,
    // uygulama içi manuel toggle DIŞINDA hiçbir zaman telefonun sistem
    // karanlık/aydınlık mod ayarına bağlı değildi; kullanıcı telefonun
    // temasını değiştirse bile uygulama hep aynı (varsayılan koyu) kalıyordu.
    // Artık 3 seviyeli bir mod var: "light" | "dark" | "system". "system"
    // seçiliyken gerçek koyu/açık durumu MainActivity'de
    // isSystemInDarkTheme() ile canlı okunup HeftrangTheme'e geçiliyor.
    private val _themeMode = MutableStateFlow(prefs.getString("hf_theme_mode", "system") ?: "system")
    val themeMode = _themeMode.asStateFlow()

    // Geriye dönük uyumluluk: eski "hf_theme_dark" boolean'ını okuyan
    // yerler (varsa) için sabit bir Boolean değeri hâlâ sunuyoruz, ama
    // gerçek tema kararı artık UI katmanında themeMode + sistem durumuna
    // göre hesaplanıyor (bkz. MainActivity.safeSetContent).
    private val _darkMode       = MutableStateFlow(prefs.getBoolean("hf_theme_dark", true))
    val darkMode = _darkMode.asStateFlow()

    private val _language       = MutableStateFlow(prefs.getString("hf_lang", "tr") ?: "tr")
    val language = _language.asStateFlow()

    private val _pushEnabled    = MutableStateFlow(prefs.getBoolean("hf_push", true))
    val pushEnabled = _pushEnabled.asStateFlow()

    private val _privateAccount = MutableStateFlow(false)
    val privateAccount = _privateAccount.asStateFlow()
    // "everyone" | "followers" | "nobody"
    private val _messagePermission = MutableStateFlow("everyone")
    val messagePermission = _messagePermission.asStateFlow()

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
                _messagePermission.value = doc.getString("messagePermission") ?: "everyone"
            } catch (_: Exception) {}
        }
    }

    // ── Engellenen kullanıcıları yükle ──────────────────────────────────────
    fun loadBlockedUsers() {
        // Zaten yüklendiyse tekrar Firestore'a gitme
        if (_blockedUsers.value.isNotEmpty()) return
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

    // ÇÖZÜLDÜ: Yeni 3-seviyeli tema seçimi — "light" | "dark" | "system".
    fun setThemeMode(mode: String) {
        require(mode == "light" || mode == "dark" || mode == "system") { "Geçersiz tema modu: $mode" }
        _themeMode.value = mode
        prefs.edit().putString("hf_theme_mode", mode).apply()
    }

    // Geriye dönük uyumluluk: eski UI kodu hâlâ toggleDarkMode() çağırıyorsa
    // (light↔dark arası switch), bu artık "system" dışına çıkarıp doğrudan
    // light/dark arasında geçiş yapıyor.
    fun toggleDarkMode() {
        val next = if (_themeMode.value == "dark") "light" else "dark"
        setThemeMode(next)
        _darkMode.value = (next == "dark")
        prefs.edit().putBoolean("hf_theme_dark", next == "dark").apply()
    }

    fun setLanguage(lang: String) {
        _language.value = lang
        prefs.edit().putString("hf_lang", lang).apply()
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

    // "everyone" | "followers" | "nobody"
    fun setMessagePermission(perm: String) {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            _messagePermission.value = perm
            try {
                firestore.collection("users").document(uid)
                    .update("messagePermission", perm).await()
            } catch (_: Exception) {}
        }
    }
}
