package com.heftreng.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.heftreng.app.data.model.Comment
import com.heftreng.app.data.model.Post
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@HiltViewModel
class FeedViewModel @Inject constructor(
    private val auth     : FirebaseAuth,
    private val firestore: FirebaseFirestore,
) : ViewModel() {

    private val _posts    = MutableStateFlow<List<Post>>(emptyList())
    val posts = _posts.asStateFlow()

    private val _comments = MutableStateFlow<List<Comment>>(emptyList())
    val comments = _comments.asStateFlow()

    private val _loading  = MutableStateFlow(false)
    val loading = _loading.asStateFlow()

    val uid get() = auth.currentUser?.uid ?: ""

    // Kullanıcının etkileşimleri — 2 sabit sorgu (N*2 değil)
    private var likedIds = emptySet<String>()
    private var savedIds = emptySet<String>()

    init { observeFeed() }

    private fun observeFeed() {
        _loading.value = true
        firestore.collection("feed")
            .orderBy("ts", Query.Direction.DESCENDING)
            .limit(40)
            .addSnapshotListener { snap, err ->
                if (err != null || snap == null) { _loading.value = false; return@addSnapshotListener }
                viewModelScope.launch {
                    if (uid.isNotEmpty() && likedIds.isEmpty() && savedIds.isEmpty()) {
                        loadInteractions()
                    }
                    _posts.value = snap.documents.mapNotNull { doc ->
                        val d = doc.data ?: return@mapNotNull null
                        Post(
                            id            = doc.id,
                            uid           = d["uid"] as? String ?: "",
                            displayName   = ((d["displayName"] as? String)?.takeIf { it.isNotBlank() } ?: (d["name"] as? String)?.takeIf { it.isNotBlank() } ?: ""),
                            username      = d["username"] as? String ?: "",
                            photoURL      = d["photoURL"] as? String ?: "",
                            text          = d["text"] as? String ?: "",
                            imageURL      = d["imageURL"] as? String ?: "",
                            likesCount    = (d["likes"] as? Long)?.toInt() ?: 0,
                            commentsCount = (d["cmtCount"] as? Long)?.toInt() ?: 0,
                            repostsCount  = (d["reposts"] as? Long)?.toInt() ?: 0,
                            quoteText     = d["quoteText"] as? String ?: "",
                            bookName      = d["bookName"] as? String ?: "",
                            authorName    = d["authorName"] as? String ?: "",
                            ts            = d["ts"] as? Timestamp,
                            isLikedByMe   = doc.id in likedIds,
                            isSavedByMe   = doc.id in savedIds,
                        )
                    }
                    _loading.value = false
                }
            }
    }

    private suspend fun loadInteractions() {
        if (uid.isEmpty()) return
        try {
            likedIds = firestore.collection("feedLikes").whereEqualTo("uid", uid)
                .get().await().documents.mapNotNull { it.getString("postId") }.toSet()
            savedIds = firestore.collection("feedSaves").whereEqualTo("uid", uid)
                .get().await().documents.mapNotNull { it.getString("postId") }.toSet()
        } catch (e: Exception) { e.printStackTrace() }
    }

    fun toggleLike(post: Post) {
        if (uid.isEmpty()) return
        val nowLiked = !post.isLikedByMe
        likedIds = if (nowLiked) likedIds + post.id else likedIds - post.id
        _posts.value = _posts.value.map {
            if (it.id == post.id) it.copy(
                isLikedByMe = nowLiked,
                likesCount  = it.likesCount + if (nowLiked) 1 else -1,
            ) else it
        }
        viewModelScope.launch {
            try {
                val likeRef = firestore.collection("feedLikes").document("${post.id}_$uid")
                val postRef = firestore.collection("feed").document(post.id)
                if (nowLiked) {
                    likeRef.set(mapOf("uid" to uid, "postId" to post.id, "ts" to Timestamp.now())).await()
                    postRef.update("likes", FieldValue.increment(1)).await()
                    if (post.uid != uid) sendNotif(post.uid, "like", "gönderini beğendi", post.id)
                } else {
                    likeRef.delete().await()
                    postRef.update("likes", FieldValue.increment(-1)).await()
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun toggleSave(post: Post) {
        if (uid.isEmpty()) return
        val nowSaved = !post.isSavedByMe
        savedIds = if (nowSaved) savedIds + post.id else savedIds - post.id
        _posts.value = _posts.value.map {
            if (it.id == post.id) it.copy(isSavedByMe = nowSaved) else it
        }
        viewModelScope.launch {
            try {
                val saveRef = firestore.collection("feedSaves").document("${post.id}_$uid")
                if (nowSaved) {
                    saveRef.set(mapOf("uid" to uid, "postId" to post.id, "ts" to Timestamp.now())).await()
                } else {
                    saveRef.delete().await()
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun loadComments(postId: String) {
        viewModelScope.launch {
            try {
                val snap = firestore.collection("feed").document(postId)
                    .collection("comments").orderBy("ts", Query.Direction.ASCENDING).get().await()
                _comments.value = snap.documents.mapNotNull { doc ->
                    val d = doc.data ?: return@mapNotNull null
                    Comment(
                        id          = doc.id,
                        postId      = postId,
                        uid         = d["uid"] as? String ?: "",
                        displayName = d["displayName"] as? String ?: "",
                        photoURL    = d["photoURL"] as? String ?: "",
                        text        = d["text"] as? String ?: "",
                        likesCount  = (d["likes"] as? Long)?.toInt() ?: 0,
                        ts          = d["ts"] as? Timestamp,
                    )
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun addComment(post: Post, text: String) {
        if (uid.isEmpty()) return
        viewModelScope.launch {
            try {
                val userDoc = firestore.collection("users").document(uid).get().await()
                val name    = userDoc.getString("displayName") ?: auth.currentUser?.displayName ?: "Bikarhêner"
                firestore.collection("feed").document(post.id).collection("comments").add(mapOf(
                    "uid"         to uid,
                    "displayName" to name,
                    "photoURL"    to (userDoc.getString("photoURL") ?: ""),
                    "text"        to text,
                    "likes"       to 0,
                    "ts"          to Timestamp.now(),
                )).await()
                firestore.collection("feed").document(post.id)
                    .update("cmtCount", FieldValue.increment(1)).await()
                _posts.value = _posts.value.map {
                    if (it.id == post.id) it.copy(commentsCount = it.commentsCount + 1) else it
                }
                if (post.uid != uid) sendNotif(post.uid, "comment", "gönderini yorumladı", post.id)
                loadComments(post.id)
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun repost(post: Post) {
        if (uid.isEmpty()) return
        viewModelScope.launch {
            try {
                val userDoc = firestore.collection("users").document(uid).get().await()
                firestore.collection("feed").add(mapOf(
                    "uid"         to uid,
                    "displayName" to (userDoc.getString("displayName") ?: ""),
                    "username"    to (userDoc.getString("username") ?: ""),
                    "photoURL"    to (userDoc.getString("photoURL") ?: ""),
                    "text"        to post.text,
                    "imageURL"    to post.imageURL,
                    "quoteText"   to post.quoteText,
                    "bookName"    to post.bookName,
                    "authorName"  to post.authorName,
                    "likes"       to 0, "saves" to 0, "cmtCount" to 0, "reposts" to 0,
                    "repostOf"    to post.id, "repostUid" to post.uid,
                    "ts"          to Timestamp.now(),
                )).await()
                firestore.collection("feed").document(post.id)
                    .update("reposts", FieldValue.increment(1)).await()
                if (post.uid != uid) sendNotif(post.uid, "repost", "gönderini paylaştı", post.id)
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun createPost(text: String, imageURL: String = "", quoteText: String = "",
                   authorName: String = "", bookName: String = "") {
        if (uid.isEmpty()) return
        viewModelScope.launch {
            try {
                val userDoc = firestore.collection("users").document(uid).get().await()
                firestore.collection("feed").add(mapOf(
                    "uid"         to uid,
                    "displayName" to (userDoc.getString("displayName") ?: auth.currentUser?.displayName ?: ""),
                    "username"    to (userDoc.getString("username") ?: ""),
                    "photoURL"    to (userDoc.getString("photoURL") ?: auth.currentUser?.photoUrl?.toString() ?: ""),
                    "text"        to text,
                    "imageURL"    to imageURL,
                    "quoteText"   to quoteText,
                    "authorName"  to authorName,
                    "bookName"    to bookName,
                    "likes"       to 0, "saves" to 0, "cmtCount" to 0, "reposts" to 0,
                    "ts"          to Timestamp.now(),
                )).await()
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun deletePost(postId: String) {
        viewModelScope.launch {
            try {
                firestore.collection("feed").document(postId).delete().await()
                _posts.value = _posts.value.filter { it.id != postId }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun editPost(postId: String, newText: String) {
        viewModelScope.launch {
            try {
                firestore.collection("feed").document(postId).update("text", newText.trim()).await()
                _posts.value = _posts.value.map {
                    if (it.id == postId) it.copy(text = newText.trim()) else it
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun getPostById(postId: String): Post? = _posts.value.find { it.id == postId }

    private suspend fun sendNotif(toUid: String, type: String, action: String, postId: String = "") {
        try {
            val userDoc  = firestore.collection("users").document(uid).get().await()
            val fromName = userDoc.getString("displayName") ?: auth.currentUser?.displayName ?: "Kullanıcı"
            val fromPhoto= userDoc.getString("photoURL") ?: ""
            firestore.collection("userNotifs").document(toUid).collection("msgs").add(mapOf(
                "fromUid"   to uid,
                "fromName"  to fromName,
                "fromPhoto" to fromPhoto,
                "type"      to type,
                "postId"    to postId,
                "message"   to "$fromName $action",
                "url"       to "",
                "read"      to false,
                "ts"        to Timestamp.now(),
            )).await()
            firestore.collection("pushQueue").add(mapOf(
                "targetUid" to toUid,
                "title"     to "Heftreng",
                "body"      to "$fromName $action",
                "postId"    to postId,
                "ts"        to Timestamp.now(),
            )).await()
        } catch (e: Exception) { e.printStackTrace() }
    }
}
