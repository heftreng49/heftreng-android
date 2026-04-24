package com.heftreng.app.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.heftreng.app.R
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
                    // lastSeen güncelle
                    firestore.collection("users").document(user.uid)
                        .update("lastSeen", com.google.firebase.Timestamp.now())
                }
                _currentUser.value = user
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
                auth.signInWithEmailAndPassword(email, password).await()
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
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _loading.value = false
            }
        }
    }

    private suspend fun createUserDoc(user: FirebaseUser, overrideName: String? = null) {
        val name = overrideName ?: user.displayName ?: user.email?.substringBefore("@") ?: "Kullanıcı"
        firestore.collection("users").document(user.uid).set(mapOf(
            "uid"         to user.uid,
            "displayName" to name,
            "name"        to name,
            "email"       to (user.email ?: ""),
            "photoURL"    to (user.photoUrl?.toString() ?: ""),
            "coverPhoto"  to "",
            "bio"         to "",
            "website"     to "",
            "xp"          to 0,
            "level"       to 1,
            "streak"      to 0,
            "createdAt"   to com.google.firebase.Timestamp.now(),
            "lastSeen"    to com.google.firebase.Timestamp.now(),
        )).await()
    }

    fun signOut() {
        auth.signOut()
        _currentUser.value = null
    }

    fun clearError() { _error.value = null }
}
