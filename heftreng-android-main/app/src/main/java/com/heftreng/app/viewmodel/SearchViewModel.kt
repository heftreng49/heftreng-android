package com.heftreng.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.heftreng.app.data.model.User
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val auth     : FirebaseAuth,
    private val firestore: FirebaseFirestore,
) : ViewModel() {

    private val _results  = MutableStateFlow<List<User>>(emptyList())
    val results = _results.asStateFlow()

    private val _suggestions = MutableStateFlow<List<User>>(emptyList())
    val suggestions = _suggestions.asStateFlow()

    private val _loading  = MutableStateFlow(false)
    val loading = _loading.asStateFlow()

    val uid get() = auth.currentUser?.uid ?: ""

    // ── Kullanıcı ara — users koleksiyonu displayName prefix ──────────────────
    fun search(query: String) {
        val q = query.trim()
        if (q.isEmpty()) { _results.value = emptyList(); return }
        viewModelScope.launch {
            _loading.value = true
            try {
                // displayName >= q ve displayName < q+'\uf8ff' — prefix arama
                val snap = firestore.collection("users")
                    .orderBy("displayName")
                    .startAt(q)
                    .endAt(q + "\uF8FF")
                    .limit(20).get().await()
                _results.value = snap.documents.mapNotNull { it.toUser() }
            } catch (e: Exception) { e.printStackTrace() }
            finally { _loading.value = false }
        }
    }

    // ── Takip önerileri — takip edilmeyenler ─────────────────────────────────
    // XML: _sugLoad — follows koleksiyonundan fromUid=me olanları çıkar,
    // kalan kullanıcılardan random öner
    fun loadSuggestions() {
        if (uid.isEmpty()) return
        viewModelScope.launch {
            try {
                // Takip ettiklerimi bul
                val followSnap = firestore.collection("follows")
                    .whereEqualTo("fromUid", uid)
                    .limit(100).get().await()
                val followedUids = followSnap.documents.mapNotNull { it.getString("targetUid") }.toSet() + uid

                // Son kaydolan kullanıcılar
                val usersSnap = firestore.collection("users")
                    .limit(30).get().await()

                _suggestions.value = usersSnap.documents
                    .mapNotNull { it.toUser() }
                    .filter { it.uid !in followedUids }
                    .shuffled()
                    .take(10)
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    // ── Takip et / bırak ─────────────────────────────────────────────────────
    fun toggleFollow(targetUid: String) {
        viewModelScope.launch {
            try {
                val ref = firestore.collection("follows").document("${uid}_$targetUid")
                if (ref.get().await().exists()) {
                    ref.delete().await()
                } else {
                    ref.set(mapOf("fromUid" to uid, "targetUid" to targetUid)).await()
                }
                loadSuggestions()
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    private fun com.google.firebase.firestore.DocumentSnapshot.toUser(): User? {
        val d = data ?: return null
        return User(
            uid         = id,
            displayName = d["displayName"] as? String ?: d["name"] as? String ?: "",
            username    = d["username"] as? String ?: "",
            photoURL    = d["photoURL"] as? String ?: "",
            bio         = d["bio"] as? String ?: "",
            followersCount = (d["followersCount"] as? Long)?.toInt() ?: 0,
        )
    }
}
