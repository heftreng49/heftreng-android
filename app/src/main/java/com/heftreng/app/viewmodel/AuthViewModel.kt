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
                    createUserDoc(user)
                } else {
                    firestore.collection("users").document(user.uid)
                        .update("lastSeen", com.google.firebase.Timestamp.now())
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
                result.user?.let { syncFcmToken(it.uid) }
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
        val name     = overrideName ?: user.displayName ?: user.email?.substringBefore("@") ?: "Kullanıcı"
        val username = generateUniqueUsername(name)
        firestore.collection("users").document(user.uid).set(mapOf(
            "uid"         to user.uid,
            "displayName" to name,
            "name"        to name,
            "username"    to username,
            "email"       to (user.email ?: ""),
            "photoURL"    to (user.photoUrl?.toString() ?: ""),
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
