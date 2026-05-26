package com.heftreng.app.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import com.heftreng.app.R
import android.content.SharedPreferences
import javax.inject.Named
import com.heftreng.app.utils.HeftrangMessagingService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val auth     : FirebaseAuth,
    private val firestore: FirebaseFirestore,
    @Named("auth_prefs") private val prefs: SharedPreferences,
) : ViewModel() {

    // ── Kayıtlı Hesaplar ─────────────────────────────────────────────────────
    // Format: "email1::pass1|||email2::pass2"  (Google hesapları pass="__google__")
    // Güvenlik notu: şifre cihaz-local SharedPreferences'ta şifrelenmemiş tutulur.
    // Production için EncryptedSharedPreferences kullanılabilir.

    private val _savedAccounts = MutableStateFlow<List<SavedAccount>>(emptyList())
    val savedAccounts = _savedAccounts.asStateFlow()

    data class SavedAccount(
        val email      : String,
        val displayName: String,
        val photoURL   : String,
        val password   : String,   // Google için "__google__"
        val uid        : String,
    )

    init {
        auth.addAuthStateListener { _currentUser.value = it.currentUser }
        loadSavedAccounts()
    }

    private fun loadSavedAccounts() {
        val raw = prefs.getString("saved_accounts", "") ?: ""
        _savedAccounts.value = raw.split("|||")
            .filter { it.contains("::") }
            .mapNotNull { entry ->
                val parts = entry.split("::")
                if (parts.size < 5) return@mapNotNull null
                SavedAccount(
                    email       = parts[0],
                    displayName = parts[1],
                    photoURL    = parts[2],
                    password    = parts[3],
                    uid         = parts[4],
                )
            }
    }

    private fun saveAccount(email: String, displayName: String, photoURL: String, password: String, uid: String) {
        val accounts = _savedAccounts.value.toMutableList()
        accounts.removeAll { it.email == email }   // güncelleme için önce sil
        accounts.add(0, SavedAccount(email, displayName, photoURL, password, uid))
        val raw = accounts.take(5).joinToString("|||") {
            "${it.email}::${it.displayName}::${it.photoURL}::${it.password}::${it.uid}"
        }
        prefs.edit().putString("saved_accounts", raw).apply()
        _savedAccounts.value = accounts.take(5)
    }

    fun removeAccount(email: String) {
        val accounts = _savedAccounts.value.filter { it.email != email }
        val raw = accounts.joinToString("|||") {
            "${it.email}::${it.displayName}::${it.photoURL}::${it.password}::${it.uid}"
        }
        prefs.edit().putString("saved_accounts", raw).apply()
        _savedAccounts.value = accounts
    }

    // Kaydedilmiş hesaba tek dokunuşla geçiş
    fun switchAccount(account: SavedAccount, context: Context) {
        viewModelScope.launch {
            _loading.value = true
            try {
                if (account.password == "__google__") {
                    // Google hesabı: mevcut oturumu kapat, Google picker aç
                    // Çağıran taraf googleLauncher'ı başlatmalı — sadece signOut yapıyoruz
                    auth.signOut()
                    _currentUser.value = null
                    _switchToGoogle.value = true
                } else {
                    auth.signOut()
                    val result = auth.signInWithEmailAndPassword(account.email, account.password).await()
                    val user = result.user ?: return@launch
                    syncFcmToken(user.uid)
                    _currentUser.value = user
                }
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _loading.value = false
            }
        }
    }

    private val _switchToGoogle = MutableStateFlow(false)
    val switchToGoogle = _switchToGoogle.asStateFlow()
    fun clearSwitchToGoogle() { _switchToGoogle.value = false }

    private val _currentUser = MutableStateFlow<FirebaseUser?>(auth.currentUser)
    val currentUser = _currentUser.asStateFlow()

    val currentEmail: String get() = auth.currentUser?.email ?: ""

    private val _error   = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading = _loading.asStateFlow()


    fun getGoogleSignInClient(context: Context) = GoogleSignIn.getClient(
        context,
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(context.getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
    )

    fun signInWithGoogle(idToken: String) {
        viewModelScope.launch {
            _loading.value = true
            try {
                val credential = GoogleAuthProvider.getCredential(idToken, null)
                val result     = auth.signInWithCredential(credential).await()
                val user       = result.user ?: return@launch
                if (result.additionalUserInfo?.isNewUser == true) {
                    // Yeni kullanıcı: Google profilini Firestore'a yaz
                    createUserDoc(user)
                } else {
                    // Eski kullanıcı: sadece lastSeen güncelle
                    // displayName/photoURL'e dokunma — kullanıcı uygulama içinde değiştirmiş olabilir
                    // Sadece Firestore'da hiç isim yoksa Google'dan doldur
                    val doc = firestore.collection("users").document(user.uid).get().await()
                    val existingName = doc.getString("displayName")?.takeIf { it.isNotBlank() && it != "Kullanıcı" }
                    val updates = mutableMapOf<String, Any>(
                        "lastSeen"   to com.google.firebase.Timestamp.now(),
                        "appVersion" to "",
                        "platform"   to "android",
                    )
                    if (existingName == null) {
                        // İsim hiç yok veya "Kullanıcı" yazıyor — Google'dan doldur
                        user.displayName?.takeIf { it.isNotBlank() }?.let {
                            updates["displayName"] = it
                            updates["name"]        = it
                        }
                        user.photoUrl?.toString()?.takeIf { it.isNotBlank() }?.let {
                            updates["photoURL"] = it
                        }
                    }
                    firestore.collection("users").document(user.uid).update(updates)
                }
                _currentUser.value = user
                syncFcmToken(user.uid)
                // Hesabı kaydet
                saveAccount(
                    email       = user.email ?: "",
                    displayName = user.displayName ?: user.email?.substringBefore("@") ?: "",
                    photoURL    = user.photoUrl?.toString() ?: "",
                    password    = "__google__",
                    uid         = user.uid,
                )
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _loading.value = false
            }
        }
    }

    fun signInWithEmail(email: String, password: String) {
        viewModelScope.launch {
            _loading.value = true
            try {
                val result = auth.signInWithEmailAndPassword(email, password).await()
                val user   = result.user ?: return@launch
                // Kullanıcı adı boşsa Firestore'dan al ve Auth'a yaz
                if (user.displayName.isNullOrBlank()) {
                    try {
                        val doc  = firestore.collection("users").document(user.uid).get().await()
                        val name = doc.getString("displayName")?.takeIf { it.isNotBlank() }
                                   ?: doc.getString("name")?.takeIf { it.isNotBlank() }
                                   ?: email.substringBefore("@")
                        val profileUpdates = com.google.firebase.auth.userProfileChangeRequest {
                            displayName = name
                        }
                        user.updateProfile(profileUpdates).await()
                        // Firestore'da da name/displayName alanlarını güncelle
                        firestore.collection("users").document(user.uid).update(
                            mapOf("displayName" to name, "name" to name)
                        ).await()
                    } catch (e: Exception) { e.printStackTrace() }
                }
                syncFcmToken(user.uid)
                // Hesabı kaydet
                val nameForSave = try {
                    firestore.collection("users").document(user.uid).get().await()
                        .getString("displayName")?.ifBlank { null }
                } catch (_: Exception) { null } ?: user.displayName ?: ""
                saveAccount(
                    email       = user.email ?: "",
                    displayName = nameForSave,
                    photoURL    = user.photoUrl?.toString() ?: "",
                    password    = password,
                    uid         = user.uid,
                )
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _loading.value = false
            }
        }
    }

    fun registerWithEmail(email: String, password: String, displayName: String) {
        viewModelScope.launch {
            _loading.value = true
            try {
                val result = auth.createUserWithEmailAndPassword(email, password).await()
                val user   = result.user ?: return@launch
                createUserDoc(user, displayName)
                syncFcmToken(user.uid)
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _loading.value = false
            }
        }
    }

    // ── FCM token senkronizasyonu ─────────────────────────────────────────────
    // 1. Önce SharedPreferences'taki pending token'a bak (uygulama ilk açılışta
    //    kullanıcı giriş yapmadan önce token yenilenmişse buraya yazılmış olur)
    // 2. Yoksa Firebase'den taze token al
    // Her iki durumda da Firestore'a yaz
    private fun syncFcmToken(uid: String) {
        viewModelScope.launch {
            try {
                // Taze token her zaman al — pending olanı da üzerine yazar
                val token = FirebaseMessaging.getInstance().token.await()
                if (token.isNotEmpty()) {
                    firestore.collection("users").document(uid)
                        .update(mapOf(
                            "fcmToken"     to token,
                            "fcmUpdatedAt" to FieldValue.serverTimestamp(),
                        ))
                }
            } catch (_: Exception) {}
        }
    }

    // Context gerektiren varyant — MainActivity'den çağrılabilir
    fun syncFcmTokenWithContext(context: Context, uid: String) {
        // Önce pending token'ı dene (daha hızlı)
        val pending = HeftrangMessagingService.consumePendingToken(context)
        if (pending != null) {
            firestore.collection("users").document(uid)
                .update(mapOf(
                    "fcmToken"     to pending,
                    "fcmUpdatedAt" to FieldValue.serverTimestamp(),
                ))
        }
        // Ardından taze token ile güncelle
        syncFcmToken(uid)
    }

    private suspend fun createUserDoc(user: FirebaseUser, overrideName: String? = null) {
        val name     = overrideName?.takeIf { it.isNotBlank() }
                       ?: user.displayName?.takeIf { it.isNotBlank() }
                       ?: user.email?.substringBefore("@")?.takeIf { it.isNotBlank() }
                       ?: "Kullanıcı"
        val photoURL = user.photoUrl?.toString() ?: ""

        // 1. Firebase Auth profilini güncelle — displayName boş kalmasın
        try {
            val profileUpdates = com.google.firebase.auth.userProfileChangeRequest {
                displayName = name
            }
            user.updateProfile(profileUpdates).await()
        } catch (e: Exception) { e.printStackTrace() }

        // 2. Firestore users koleksiyonuna yaz
        val username = generateUniqueUsername(name)
        firestore.collection("users").document(user.uid).set(mapOf(
            "uid"         to user.uid,
            "displayName" to name,
            "name"        to name,
            "username"    to username,
            "email"       to (user.email ?: ""),
            "photoURL"    to photoURL,
            "coverPhoto"  to "",
            "bio"         to "",
            "website"     to "",
            "xp"          to 0,  "kf_xp"     to 0,
            "level"       to 1,
            "streak"      to 0,  "kf_streak" to 0,
            "banned"      to false,
            "createdAt"   to com.google.firebase.Timestamp.now(),
            "lastSeen"    to com.google.firebase.Timestamp.now(),
            "appVersion"  to "",
            "platform"    to "android",
        ), com.google.firebase.firestore.SetOptions.merge()).await()

        // 3. username benzersizlik kaydı
        firestore.collection("usernames").document(username).set(
            mapOf("uid" to user.uid)
        ).await()
    }

    private suspend fun generateUniqueUsername(displayName: String): String {
        val existing = try {
            firestore.collection("users")
                .whereEqualTo("email", auth.currentUser?.email ?: "")
                .limit(1).get().await()
                .documents.firstOrNull()?.getString("username")
        } catch (e: Exception) { null }
        if (!existing.isNullOrBlank()) return existing

        var handle = displayName.lowercase()
            .replace(Regex("[^a-z0-9_]"), "")
            .take(20)
            .ifBlank { "user" }
        var attempt = 0
        while (attempt < 5) {
            val taken = firestore.collection("usernames").document(handle).get().await().exists()
            if (!taken) break
            handle = handle.take(16) + (1000..9999).random()
            attempt++
        }
        return handle
    }

    fun signOut() {
        auth.signOut()
        _currentUser.value = null
    }

    fun sendPasswordReset(
        email    : String,
        onSuccess: () -> Unit,
        onError  : (String) -> Unit,
    ) {
        if (email.isBlank()) { onError("E-posta adresini girin"); return }
        viewModelScope.launch {
            try {
                auth.sendPasswordResetEmail(email.trim()).await()
                onSuccess()
            } catch (e: Exception) {
                onError(e.localizedMessage ?: "Hata oluştu")
            }
        }
    }

    fun clearError() { _error.value = null }
    // ══════════════════════════════════════════════════════════════════════
    //  KULLANIM KOŞULLARI KABULU
    //  users/{uid}.termsAcceptedAt → hukuki ispat için timestamp
    // ══════════════════════════════════════════════════════════════════════
    fun acceptTerms() {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                firestore.collection("users").document(uid).update(
                    mapOf(
                        "termsAcceptedAt"      to com.google.firebase.Timestamp.now(),
                        "termsAcceptedVersion" to "1.0",
                    )
                ).await()
            } catch (_: Exception) {}
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  HESAP SİLME  (Play Store Politikası — Kasım 2023 zorunluluğu)
    //  1. Firestore kullanıcı belgesi silinir
    //  2. Firebase Auth hesabı silinir
    //  Büyük veriler (gönderiler, yorumlar) Cloud Function ile cascade silinir
    // ══════════════════════════════════════════════════════════════════════
    fun deleteAccount(
        onSuccess : () -> Unit,
        onError   : (String) -> Unit,
    ) {
        viewModelScope.launch {
            try {
                val user = auth.currentUser ?: run { onError("Oturum bulunamadı"); return@launch }
                val uid  = user.uid

                // 1. Firestore kullanıcı belgesi
                try { firestore.collection("users").document(uid).delete().await() }
                catch (_: Exception) {}

                // 2. Kullanıcının bildirim belgesi
                try { firestore.collection("userNotifs").document(uid).delete().await() }
                catch (_: Exception) {}

                // 3. Kayıtlı hesabı SharedPreferences'tan kaldır
                try {
                    val saved = prefs.getString("saved_accounts", "") ?: ""
                    val remaining = saved.split("|").filter { !it.contains("::$uid") }.joinToString("|")
                    prefs.edit().putString("saved_accounts", remaining).apply()
                } catch (_: Exception) {}

                // 4. Firebase Auth hesabını sil (son adım — sonrası auth dinleyicisi tetiklenir)
                user.delete().await()

                onSuccess()
            } catch (e: com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException) {
                // Google hesapları yeniden giriş gerektirebilir
                onError("Hesabı silmek için lütfen tekrar giriş yapın.")
            } catch (e: Exception) {
                onError(e.message ?: "Hesap silinirken hata oluştu")
            }
        }
    }
}
