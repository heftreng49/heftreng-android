package com.heftreng.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
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

    private val _followersCount = MutableStateFlow(0)
    val followersCount = _followersCount.asStateFlow()

    private val _followingCount = MutableStateFlow(0)
    val followingCount = _followingCount.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading = _loading.asStateFlow()

    val myUid get() = auth.currentUser?.uid ?: ""

    fun load(uid: String) {
        val targetUid = if (uid == "me") myUid else uid
        viewModelScope.launch {
            _loading.value = true
            try {
                // Kullanıcı belgesi
                val userDoc = firestore.collection("users").document(targetUid).get().await()
                val d = userDoc.data ?: return@launch
                _user.value = User(
                    uid        = d["uid"] as? String ?: targetUid,
                    displayName= d["displayName"] as? String ?: d["name"] as? String ?: "",
                    name       = d["name"] as? String ?: "",
                    username   = d["username"] as? String ?: (d["email"] as? String)?.substringBefore("@") ?: "",
                    email      = d["email"] as? String ?: "",
                    photoURL   = d["photoURL"] as? String ?: "",
                    coverPhoto = d["coverPhoto"] as? String ?: "",
                    bio        = d["bio"] as? String ?: "",
                    website    = d["website"] as? String ?: "",
                    level      = (d["level"] as? Long)?.toInt() ?: 1,
                    xp         = (d["xp"] as? Long)?.toInt() ?: 0,
                    streak     = (d["streak"] as? Long)?.toInt() ?: 0,
                )

                // Takipçi sayıları — follows koleksiyonundan say
                val followersSnap = firestore.collection("follows")
                    .whereEqualTo("targetUid", targetUid).get().await()
                val followingSnap = firestore.collection("follows")
                    .whereEqualTo("fromUid", targetUid).get().await()
                _followersCount.value = followersSnap.size()
                _followingCount.value = followingSnap.size()

                // Gönderiler
                // orderBy kaldırıldı — composite index gerektirmez
                // XML temasıyla aynı: where + limit, sonra client-side sort
                val snap = firestore.collection("feed")
                    .whereEqualTo("uid", targetUid)
                    .limit(30).get().await()
                _posts.value = snap.documents.mapNotNull { doc ->
                    val fd = doc.data ?: return@mapNotNull null
                    Post(
                        id            = doc.id,
                        uid           = fd["uid"] as? String ?: "",
                        displayName   = fd["displayName"] as? String ?: "",
                        username      = fd["username"] as? String ?: "",
                        photoURL      = fd["photoURL"] as? String ?: "",
                        text          = fd["text"] as? String ?: "",
                        imageURL      = fd["imageURL"] as? String ?: "",
                        likesCount    = (fd["likes"] as? Long)?.toInt() ?: 0,
                        commentsCount = (fd["cmtCount"] as? Long)?.toInt() ?: 0,
                        repostsCount  = (fd["reposts"] as? Long)?.toInt() ?: 0,
                        ts            = fd["ts"] as? Timestamp,
                    )
                }.sortedByDescending { it.ts?.seconds ?: 0L }

                // Takip durumu
                if (targetUid != myUid) {
                    val followDoc = firestore.collection("follows")
                        .document("${myUid}_$targetUid").get().await()
                    _isFollowing.value = followDoc.exists()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _loading.value = false
            }
        }
    }

    fun toggleFollow(targetUid: String) {
        viewModelScope.launch {
            try {
                val followDoc = firestore.collection("follows").document("${myUid}_$targetUid")
                if (_isFollowing.value) {
                    followDoc.delete().await()
                    _isFollowing.value = false
                    _followersCount.value = (_followersCount.value - 1).coerceAtLeast(0)
                } else {
                    followDoc.set(mapOf("fromUid" to myUid, "targetUid" to targetUid)).await()
                    _isFollowing.value = true
                    _followersCount.value += 1
                    // Bildirim
                    val myDoc     = firestore.collection("users").document(myUid).get().await()
                    val fromName  = myDoc.getString("displayName") ?: myDoc.getString("name") ?: ""
                    val fromPhoto = myDoc.getString("photoURL") ?: ""
                    firestore.collection("userNotifs").document(targetUid).collection("msgs").add(mapOf(
                        "fromUid"   to myUid,
                        "fromName"  to fromName,
                        "fromPhoto" to fromPhoto,
                        "type"      to "follow",
                        "message"   to "$fromName seni takip etmeye başladı",
                        "url"       to "",
                        "read"      to false,
                        "ts"        to Timestamp.now(),
                    )).await()
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun updateProfile(displayName: String, bio: String, website: String) {
        viewModelScope.launch {
            try {
                firestore.collection("users").document(myUid).update(mapOf(
                    "displayName" to displayName,
                    "name"        to displayName,
                    "bio"         to bio,
                    "website"     to website,
                )).await()
                _user.value = _user.value?.copy(displayName = displayName, bio = bio, website = website)
            } catch (e: Exception) { e.printStackTrace() }
        }
    }
}
