package com.heftreng.app.viewmodel

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.heftreng.app.data.model.BlockedUser
import androidx.compose.ui.graphics.Color
import com.heftreng.app.ui.theme.HeftrangThemeVariant
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

    // Tema varyantı — 6 görsel tema arasından seçim
    private val _themeVariant = MutableStateFlow(
        runCatching {
            HeftrangThemeVariant.valueOf(
                prefs.getString("hf_theme_variant", HeftrangThemeVariant.CHARCOAL_INK.name) ?: ""
            )
        }.getOrDefault(HeftrangThemeVariant.CHARCOAL_INK)
    )
    val themeVariant = _themeVariant.asStateFlow()

    // Yazı rengi override — mod başına ayrı saklanır (dark / light)
    // Böylece koyu modda seçilen renk açık modda geçersiz kalmaz.
    private val _textColorDark  = MutableStateFlow(loadTextColor(isDark = true))
    private val _textColorLight = MutableStateFlow(loadTextColor(isDark = false))

    // Dışarıya tek bir flow sunuyoruz; mevcut themeMode + sistem durumuna göre
    // hangisinin aktif olduğu UI katmanında (NavHost) hesaplanıp
    // textColorForMode() ile çekiliyor.
    val textColorDark  = _textColorDark.asStateFlow()
    val textColorLight = _textColorLight.asStateFlow()

    /** UI katmanı mevcut isDark değerini geçirir; doğru override döner. */
    fun textColorForMode(isDark: Boolean): Color? =
        if (isDark) _textColorDark.value else _textColorLight.value

    fun setTextColorOverride(color: Color?, isDark: Boolean) {
        val key = if (isDark) "hf_text_color_dark" else "hf_text_color_light"
        if (color == null) {
            prefs.edit().remove(key).apply()
        } else {
            prefs.edit().putLong(key, color.value.toLong()).apply()
        }
        if (isDark) _textColorDark.value  = color
        else        _textColorLight.value = color
    }

    // Geriye dönük uyumluluk — tek parametreli çağrılar için (null sıfırlama)
    fun setTextColorOverride(color: Color?) {
        setTextColorOverride(color, isDark = true)
        setTextColorOverride(color, isDark = false)
    }

    private fun loadTextColor(isDark: Boolean): Color? {
        val key = if (isDark) "hf_text_color_dark" else "hf_text_color_light"
        // Eski tek-key'den migrate et (varsa)
        if (!prefs.contains(key) && prefs.contains("hf_text_color")) {
            val legacy = Color(prefs.getLong("hf_text_color", 0xFFF4F4FAL).toULong())
            // Eski renk koyu mod için saklanmıştı, açık moda geçirme
            if (isDark) return legacy
            return null
        }
        if (!prefs.contains(key)) return null
        return Color(prefs.getLong(key, 0xFFF4F4FAL).toULong())
    }

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

    // Görsel tema varyantı değiştir
    fun setThemeVariant(variant: HeftrangThemeVariant) {
        _themeVariant.value = variant
        prefs.edit().putString("hf_theme_variant", variant.name).apply()
    }

    // ÇÖZÜLDÜ: Yeni 3-seviyeli tema seçimi — "light" | "dark" | "system".
    fun setThemeMode(mode: String) {
        require(mode == "light" || mode == "dark" || mode == "system") { "Geçersiz tema modu: $mode" }
        _themeMode.value = mode
        prefs.edit().putString("hf_theme_mode", mode).apply()
        // Koyu↔açık geçişinde yazı rengi override'ı sıfırla
        // (koyu temadaki açık renk, açık temada okunaksız olur)
        setTextColorOverride(null)
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
