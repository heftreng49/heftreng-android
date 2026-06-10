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

    private val authStateListener = FirebaseAuth.AuthStateListener {
        _currentUser.value = it.currentUser
    }

    init {
        auth.addAuthStateListener(authStateListener)
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
                    if (!user.isEmailVerified) {
                        try { user.sendEmailVerification().await() } catch (_: Exception) {}
                        auth.signOut()
                        _error.value = "EMAIL_NOT_VERIFIED"
                        return@launch
                    }
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

    private val _verificationSent = MutableStateFlow(false)
    val verificationSent = _verificationSent.asStateFlow()
    private val _verificationPending = MutableStateFlow(false)
    val verificationPending = _verificationPending.asStateFlow()
    fun clearVerificationPending() { _verificationPending.value = false }
    fun clearVerificationSent()    { _verificationSent.value    = false }
    /** Giriş yapıldı ama e-posta henüz doğrulanmamış — UI'dan tetiklenir. */
    fun triggerVerificationPending() {
        if (!_verificationPending.value) {
            _verificationPending.value = true
            // Eğer daha önce mail gönderilmemişse gönder
            viewModelScope.launch {
                try {
                    auth.currentUser?.sendEmailVerification()?.await()
                } catch (_: Exception) {}
            }
        }
    }

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
                    acceptTerms(method = "google_register")
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
                    updates["signInMethod"] = "google"
                    firestore.collection("users").document(user.uid).update(updates)
                    acceptTerms(method = "google_login")
                }
                _currentUser.value = user
                syncFcmToken(user.uid)
                syncEmailVerified(user) // Google her zaman true — Firestore'u güncelle
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

                // Email doğrulanmamışsa içeri alma — doğrulama maili yeniden gönder
                if (!user.isEmailVerified) {
                    try { user.sendEmailVerification().await() } catch (_: Exception) {}
                    auth.signOut()
                    _verificationPending.value = true
                    _error.value = "EMAIL_NOT_VERIFIED" // AuthScreen bu kodu yakalar
                    return@launch
                }

                syncFcmToken(user.uid)
                syncEmailVerified(user) // Firestore'u güncelle
                acceptTerms(method = "email_login")
                // ── Tek Firestore çağrısı: displayName hem Auth'a hem saveAccount'a ──
                val userDoc  = try { firestore.collection("users").document(user.uid).get().await() } catch (_: Exception) { null }
                val nameFromDb = userDoc?.getString("displayName")?.takeIf { it.isNotBlank() }
                              ?: userDoc?.getString("name")?.takeIf { it.isNotBlank() }

                if (user.displayName.isNullOrBlank() && nameFromDb != null) {
                    try {
                        val profileUpdates = com.google.firebase.auth.userProfileChangeRequest { displayName = nameFromDb }
                        user.updateProfile(profileUpdates).await()
                        firestore.collection("users").document(user.uid)
                            .update(mapOf("displayName" to nameFromDb, "name" to nameFromDb))
                    } catch (_: Exception) {}
                }
                val nameForSave = nameFromDb ?: user.displayName ?: email.substringBefore("@")
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
                // ── KATMAN 1: Cloud Function güvenlik kontrolü ────────────────
                // Tek kullanımlık email, hız sınırı, isim doğrulama
                val verifyResult = com.google.firebase.functions.FirebaseFunctions
                    .getInstance()
                    .getHttpsCallable("verifyRegistration")
                    .call(mapOf("email" to email, "displayName" to displayName))
                    .await()
                @Suppress("UNCHECKED_CAST")
                val resultData = verifyResult.data as? Map<String, Any>
                val allowed    = resultData?.get("allowed") as? Boolean ?: true
                if (!allowed) {
                    _error.value = resultData?.get("reason") as? String ?: "Kayıt engeliendi"
                    return@launch
                }

                // ── KATMAN 2: Firebase Auth ile hesap oluştur ─────────────────
                val result = auth.createUserWithEmailAndPassword(email, password).await()
                val user   = result.user ?: return@launch
                createUserDoc(user, displayName)
                syncFcmToken(user.uid)
                acceptTerms(method = "email_register")
                try { user.sendEmailVerification().await() } catch (_: Exception) {}
                _verificationSent.value = true
                auth.signOut()
                _currentUser.value = null
            } catch (e: com.google.firebase.functions.FirebaseFunctionsException) {
                // Function çalışmazsa (offline, cold start) devam et — açık kalmasın
                android.util.Log.w("AuthVM", "verifyRegistration unavailable: ${e.message}")
                try {
                    val result = auth.createUserWithEmailAndPassword(email, password).await()
                    val user   = result.user ?: return@launch
                    createUserDoc(user, displayName)
                    syncFcmToken(user.uid)
                    acceptTerms(method = "email_register")
                    try { user.sendEmailVerification().await() } catch (_: Exception) {}
                    _verificationSent.value = true
                    auth.signOut()
                    _currentUser.value = null
                } catch (e2: Exception) {
                    _error.value = e2.message
                }
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

    // Firebase Auth'un isEmailVerified değerini Firestore'a yaz.
    // Böylece admin paneli Firestore'dan okuyabilir; admin'in elle onaylamasına gerek yok.
    // Google kullanıcıları her zaman true döndürür, email kullanıcıları linke tıklayınca true olur.
    private fun syncEmailVerified(user: com.google.firebase.auth.FirebaseUser) {
        viewModelScope.launch {
            try {
                user.reload().await() // Auth token'ını yenile — en güncel değeri al
                firestore.collection("users").document(user.uid)
                    .update("emailVerified", user.isEmailVerified)
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
            "username"      to username,
            "usernameLower" to username.lowercase(),
            "email"         to (user.email ?: ""),
            "photoURL"    to photoURL,
            "coverPhoto"  to "",
            "bio"         to "",
            "website"     to "",
            "xp"          to 0,  "kf_xp"     to 0,
            "level"       to 1,
            "streak"      to 0,  "kf_streak" to 0,
            "banned"        to false,
            "followersCount" to 0,
            "followingCount" to 0,
            "postsCount"     to 0,
            // Google ile giriş yapanların emaili Firebase tarafından zaten doğrulanmış olur.
            // Email/şifre ile kayıt olanlarda admin onayı gerektiği için false başlatıyoruz.
            "emailVerified" to user.providerData.any { it.providerId == "google.com" },
            "signInMethod"  to if (user.providerData.any { it.providerId == "google.com" }) "google" else "email",
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
            val uid = auth.currentUser?.uid ?: ""
            if (uid.isNotBlank())
                firestore.collection("users").document(uid).get().await().getString("username")
            else null
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
    //  KULLANIM KOŞULLARI KABULU — Yasal Audit Trail
    //
    //  Kaydedilen veriler:
    //    users/{uid}            → termsAcceptedAt, termsVersion (hızlı erişim)
    //    terms_acceptances/{id} → tam audit kaydı (denetim/mahkeme için)
    //
    //  Her kayıt şunları içerir:
    //    - uid, email
    //    - timestamp (sunucu saati)
    //    - termsVersion, privacyVersion
    //    - platform (android), appVersion
    //    - method: "email_register" | "google" | "email_login"
    //    - termsUrl, privacyUrl
    // ══════════════════════════════════════════════════════════════════════
    fun acceptTerms(method: String = "email_login", appVersion: String = "") {
        val user = auth.currentUser ?: return
        val uid  = user.uid
        val now  = com.google.firebase.Timestamp.now()

        viewModelScope.launch {
            try {
                val termsVersion   = "1.0"
                val privacyVersion = "1.0"
                val termsUrl       = "https://heft-reng.blogspot.com/p/kullanim-kosullari.html"
                val privacyUrl     = "https://heft-reng.blogspot.com/p/gizlilik-politikasi.html"

                // 1. users/{uid} — hızlı erişim için özet
                firestore.collection("users").document(uid).update(
                    mapOf(
                        "termsAcceptedAt"      to now,
                        "termsVersion"         to termsVersion,
                        "privacyVersion"       to privacyVersion,
                    )
                ).await()

                // 2. terms_acceptances — değiştirilemez audit trail
                //    Her kabul ayrı belge olarak saklanır (üzerine yazılmaz)
                firestore.collection("terms_acceptances").add(
                    mapOf(
                        "uid"            to uid,
                        "email"          to (user.email ?: ""),
                        "ts"             to now,
                        "termsVersion"   to termsVersion,
                        "privacyVersion" to privacyVersion,
                        "termsUrl"       to termsUrl,
                        "privacyUrl"     to privacyUrl,
                        "platform"       to "android",
                        "appVersion"     to appVersion,
                        "method"         to method,  // email_register | google | email_login
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


    fun reloadAndCheckVerification(onVerified: () -> Unit, onNotYet: () -> Unit) {
        viewModelScope.launch {
            _loading.value = true
            try {
                auth.currentUser?.reload()?.await()
                if (auth.currentUser?.isEmailVerified == true) {
                    _currentUser.value = auth.currentUser
                    onVerified()
                } else {
                    onNotYet()
                }
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _loading.value = false
            }
        }
    }

    fun resendVerificationEmail() {
        viewModelScope.launch {
            try {
                auth.currentUser?.sendEmailVerification()?.await()
                _verificationSent.value = true
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        auth.removeAuthStateListener(authStateListener)
    }
}
