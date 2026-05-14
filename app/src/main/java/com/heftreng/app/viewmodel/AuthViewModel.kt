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
import com.heftreng.app.utils.HeftrangMessagingService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
) : ViewModel() {

    private val _currentUser = MutableStateFlow<FirebaseUser?>(auth.currentUser)
    val currentUser = _currentUser.asStateFlow()

    val currentEmail: String get() = auth.currentUser?.email ?: ""

    private val _error   = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading = _loading.asStateFlow()

    init { auth.addAuthStateListener { _currentUser.value = it.currentUser } }

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
                        "lastSeen" to com.google.firebase.Timestamp.now(),
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
}
