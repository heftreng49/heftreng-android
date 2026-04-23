package com.heftreng.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.heftreng.app.data.model.Post
import com.heftreng.app.data.model.User
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
) : ViewModel() {

    private val _user = MutableStateFlow<User?>(null)
    val user = _user.asStateFlow()

    private val _posts = MutableStateFlow<List<Post>>(emptyList())
    val posts = _posts.asStateFlow()

    private val _isFollowing = MutableStateFlow(false)
    val isFollowing = _isFollowing.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading = _loading.asStateFlow()

    val myUid get() = auth.currentUser?.uid ?: ""

    fun load(uid: String) {
        val targetUid = if (uid == "me") myUid else uid
        viewModelScope.launch {
            _loading.value = true
            try {
                // Kullanıcı bilgisi
                val userDoc = firestore.collection("users").document(targetUid).get().await()
                _user.value = userDoc.toObject(User::class.java)

                // Gönderiler
                val snap = firestore.collection("feed")
                    .whereEqualTo("uid", targetUid)
                    .orderBy("ts", Query.Direction.DESCENDING)
                    .limit(20)
                    .get().await()
                _posts.value = snap.documents.mapNotNull { doc ->
                    doc.toObject(Post::class.java)?.copy(id = doc.id)
                }

                // Takip kontrolü
                if (targetUid != myUid) {
                    val followDoc = firestore.collection("follows")
                        .document("${myUid}_$targetUid").get().await()
                    _isFollowing.value = followDoc.exists()
                }
            } catch (_: Exception) {}
            finally { _loading.value = false }
        }
    }

    fun toggleFollow(targetUid: String) {
        viewModelScope.launch {
            val followDoc = firestore.collection("follows").document("${myUid}_$targetUid")
            val targetRef = firestore.collection("users").document(targetUid)
            val myRef     = firestore.collection("users").document(myUid)

            if (_isFollowing.value) {
                followDoc.delete().await()
                targetRef.update("followersCount", com.google.firebase.firestore.FieldValue.increment(-1)).await()
                myRef.update("followingCount", com.google.firebase.firestore.FieldValue.increment(-1)).await()
                _isFollowing.value = false
                _user.value = _user.value?.copy(followersCount = (_user.value?.followersCount ?: 1) - 1)
            } else {
                followDoc.set(mapOf("fromUid" to myUid, "toUid" to targetUid)).await()
                targetRef.update("followersCount", com.google.firebase.firestore.FieldValue.increment(1)).await()
                myRef.update("followingCount", com.google.firebase.firestore.FieldValue.increment(1)).await()
                _isFollowing.value = true
                _user.value = _user.value?.copy(followersCount = (_user.value?.followersCount ?: 0) + 1)
                // Bildirim
                val myDoc = firestore.collection("users").document(myUid).get().await()
                val fromName = myDoc.getString("displayName") ?: ""
                firestore.collection("userNotifs").add(mapOf(
                    "userId"    to targetUid,
                    "fromUid"   to myUid,
                    "fromName"  to fromName,
                    "fromPhoto" to (myDoc.getString("photoURL") ?: ""),
                    "type"      to "follow",
                    "message"   to "$fromName seni takip etmeye başladı",
                    "url"       to "",
                    "read"      to false,
                    "ts"        to com.google.firebase.Timestamp.now(),
                )).await()
            }
        }
    }

    fun updateProfile(displayName: String, bio: String) {
        viewModelScope.launch {
            try {
                firestore.collection("users").document(myUid).update(
                    mapOf("displayName" to displayName, "bio" to bio)
                ).await()
                _user.value = _user.value?.copy(displayName = displayName, bio = bio)
            } catch (_: Exception) {}
        }
    }
}
