package com.heftreng.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.heftreng.app.data.model.Post
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@HiltViewModel
class FeedViewModel @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
) : ViewModel() {

    private val _posts = MutableStateFlow<List<Post>>(emptyList())
    val posts = _posts.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading = _loading.asStateFlow()

    val uid get() = auth.currentUser?.uid ?: ""

    init { loadFeed() }

    fun loadFeed() {
        viewModelScope.launch {
            _loading.value = true
            try {
                val snap = firestore.collection("feed")
                    .orderBy("ts", Query.Direction.DESCENDING)
                    .limit(30)
                    .get().await()

                val postList = snap.documents.mapNotNull { doc ->
                    val d = doc.data ?: return@mapNotNull null
                    Post(
                        id           = doc.id,
                        uid          = d["uid"] as? String ?: "",
                        displayName  = d["displayName"] as? String ?: "",
                        username     = d["username"] as? String ?: "",
                        photoURL     = d["photoURL"] as? String ?: "",
                        text         = d["text"] as? String ?: "",
                        imageURL     = d["imageURL"] as? String ?: "",
                        likesCount   = (d["likes"] as? Long)?.toInt() ?: 0,
                        commentsCount= (d["cmtCount"] as? Long)?.toInt() ?: 0,
                        quoteText    = d["quoteText"] as? String ?: "",
                        bookName     = d["bookName"] as? String ?: "",
                        authorName   = d["authorName"] as? String ?: "",
                        ts           = d["ts"] as? Timestamp,
                    )
                }

                // feedLikes / feedSaves kontrol
                if (uid.isNotEmpty()) {
                    val likeSnaps = postList.map {
                        firestore.collection("feedLikes").document("${it.id}_$uid").get().await()
                    }
                    val saveSnaps = postList.map {
                        firestore.collection("feedSaves").document("${it.id}_$uid").get().await()
                    }
                    _posts.value = postList.mapIndexed { i, p ->
                        p.copy(
                            isLikedByMe = likeSnaps[i].exists(),
                            isSavedByMe = saveSnaps[i].exists(),
                        )
                    }
                } else {
                    _posts.value = postList
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _loading.value = false
            }
        }
    }

    fun toggleLike(post: Post) {
        viewModelScope.launch {
            val likeDoc = firestore.collection("feedLikes").document("${post.id}_$uid")
            val postRef = firestore.collection("feed").document(post.id)

            if (post.isLikedByMe) {
                likeDoc.delete().await()
                postRef.update("likes", FieldValue.increment(-1)).await()
            } else {
                likeDoc.set(mapOf("uid" to uid, "postId" to post.id, "ts" to Timestamp.now())).await()
                postRef.update("likes", FieldValue.increment(1)).await()
                if (post.uid != uid) sendNotification(post.uid, "like", "gönderini beğendi")
            }

            _posts.value = _posts.value.map {
                if (it.id == post.id) it.copy(
                    isLikedByMe = !post.isLikedByMe,
                    likesCount  = it.likesCount + (if (post.isLikedByMe) -1 else 1),
                ) else it
            }
        }
    }

    fun toggleSave(post: Post) {
        viewModelScope.launch {
            val saveDoc = firestore.collection("feedSaves").document("${post.id}_$uid")
            if (post.isSavedByMe) {
                saveDoc.delete().await()
            } else {
                saveDoc.set(mapOf("uid" to uid, "postId" to post.id, "ts" to Timestamp.now())).await()
            }
            _posts.value = _posts.value.map {
                if (it.id == post.id) it.copy(isSavedByMe = !post.isSavedByMe) else it
            }
        }
    }

    fun createPost(text: String) {
        viewModelScope.launch {
            val user = auth.currentUser ?: return@launch
            val userDoc = firestore.collection("users").document(uid).get().await()
            firestore.collection("feed").add(
                mapOf(
                    "uid"         to uid,
                    "displayName" to (userDoc.getString("displayName") ?: user.displayName ?: ""),
                    "username"    to (userDoc.getString("username") ?: ""),
                    "photoURL"    to (userDoc.getString("photoURL") ?: user.photoUrl?.toString() ?: ""),
                    "text"        to text,
                    "imageURL"    to "",
                    "likes"       to 0,       // rules: likes == 0
                    "saves"       to 0,       // rules: saves == 0
                    "cmtCount"    to 0,       // rules: cmtCount == 0
                    "ts"          to Timestamp.now(),
                )
            ).await()
            loadFeed()
        }
    }

    private suspend fun sendNotification(toUid: String, type: String, action: String) {
        val user = auth.currentUser ?: return
        val userDoc = firestore.collection("users").document(uid).get().await()
        val fromName  = userDoc.getString("displayName") ?: user.displayName ?: "Kullanıcı"
        val fromPhoto = userDoc.getString("photoURL") ?: ""

        // userNotifs/{toUid}/msgs/{auto} — rules yapısıyla uyumlu
        firestore.collection("userNotifs").document(toUid)
            .collection("msgs").add(
                mapOf(
                    "fromUid"   to uid,
                    "fromName"  to fromName,
                    "fromPhoto" to fromPhoto,
                    "type"      to type,
                    "message"   to "$fromName $action",
                    "url"       to "",
                    "read"      to false,
                    "ts"        to Timestamp.now(),
                )
            ).await()

        firestore.collection("pushQueue").add(
            mapOf(
                "targetUid" to toUid,
                "title"     to "Heftreng",
                "body"      to "$fromName $action",
                "url"       to "",
                "ts"        to Timestamp.now(),
            )
        ).await()
    }
}
