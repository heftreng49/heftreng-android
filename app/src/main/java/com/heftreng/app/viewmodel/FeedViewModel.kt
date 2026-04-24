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
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
) : ViewModel() {

    private val _posts = MutableStateFlow<List<Post>>(emptyList())
    val posts = _posts.asStateFlow()

    private val _comments = MutableStateFlow<List<Comment>>(emptyList())
    val comments = _comments.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading = _loading.asStateFlow()

    val uid get() = auth.currentUser?.uid ?: ""

    // Kullanıcının beğeni ve kaydetme setleri — tek seferinde çekilir
    private val _likedIds = MutableStateFlow<Set<String>>(emptySet())
    private val _savedIds = MutableStateFlow<Set<String>>(emptySet())

    init {
        viewModelScope.launch {
            loadUserInteractions()
            observeFeed()
        }
    }

    // Kullanıcının TÜM beğeni ve kaydetmelerini tek sorguda çek
    private suspend fun loadUserInteractions() {
        if (uid.isEmpty()) return
        try {
            val likes = firestore.collection("feedLikes")
                .whereEqualTo("uid", uid).get().await()
            _likedIds.value = likes.documents.mapNotNull { it.getString("postId") }.toSet()

            val saves = firestore.collection("feedSaves")
                .whereEqualTo("uid", uid).get().await()
            _savedIds.value = saves.documents.mapNotNull { it.getString("postId") }.toSet()
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun observeFeed() {
        firestore.collection("feed")
            .orderBy("ts", Query.Direction.DESCENDING)
            .limit(50)
            .addSnapshotListener { snap, err ->
                if (err != null || snap == null) {
                    _loading.value = false
                    return@addSnapshotListener
                }
                viewModelScope.launch {
                    val postList = snap.documents.mapNotNull { doc ->
                        val d = doc.data ?: return@mapNotNull null
                        Post(
                            id            = doc.id,
                            uid           = d["uid"] as? String ?: "",
                            displayName   = d["displayName"] as? String ?: "",
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
                            isLikedByMe   = doc.id in _likedIds.value,
                            isSavedByMe   = doc.id in _savedIds.value,
                        )
                    }
                    _posts.value = postList
                    _loading.value = false
                }
            }
    }

    // Anlık UI güncellemesi + Firestore yazma
    fun toggleLike(post: Post) {
        if (uid.isEmpty()) return
        // Anında UI güncelle
        val nowLiked = !post.isLikedByMe
        _likedIds.value = if (nowLiked)
            _likedIds.value + post.id
        else
            _likedIds.value - post.id

        _posts.value = _posts.value.map {
            if (it.id == post.id) it.copy(
                isLikedByMe = nowLiked,
                likesCount  = it.likesCount + if (nowLiked) 1 else -1
            ) else it
        }

        viewModelScope.launch {
            try {
                val likeDoc = firestore.collection("feedLikes").document("${post.id}_$uid")
                val postRef = firestore.collection("feed").document(post.id)
                if (nowLiked) {
                    likeDoc.set(mapOf("uid" to uid, "postId" to post.id, "ts" to Timestamp.now())).await()
                    postRef.update("likes", FieldValue.increment(1)).await()
                    if (post.uid != uid) sendNotif(post.uid, "like", "gönderini beğendi")
                } else {
                    likeDoc.delete().await()
                    postRef.update("likes", FieldValue.increment(-1)).await()
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun toggleSave(post: Post) {
        if (uid.isEmpty()) return
        val nowSaved = !post.isSavedByMe
        _savedIds.value = if (nowSaved)
            _savedIds.value + post.id
        else
            _savedIds.value - post.id

        _posts.value = _posts.value.map {
            if (it.id == post.id) it.copy(isSavedByMe = nowSaved) else it
        }

        viewModelScope.launch {
            try {
                val saveDoc = firestore.collection("feedSaves").document("${post.id}_$uid")
                if (nowSaved) {
                    saveDoc.set(mapOf("uid" to uid, "postId" to post.id, "ts" to Timestamp.now())).await()
                } else {
                    saveDoc.delete().await()
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun addComment(post: Post, text: String) {
        if (uid.isEmpty()) return
        viewModelScope.launch {
            try {
                val userDoc = firestore.collection("users").document(uid).get().await()
                val displayName = userDoc.getString("displayName") ?: "Bikarhêner"

                firestore.collection("feed").document(post.id)
                    .collection("comments").add(mapOf(
                        "uid"         to uid,
                        "displayName" to displayName,
                        "photoURL"    to (userDoc.getString("photoURL") ?: ""),
                        "text"        to text,
                        "likes"       to 0,
                        "ts"          to Timestamp.now(),
                    )).await()

                firestore.collection("feed").document(post.id)
                    .update("cmtCount", FieldValue.increment(1)).await()

                // Anlık commentsCount güncelle
                _posts.value = _posts.value.map {
                    if (it.id == post.id) it.copy(commentsCount = it.commentsCount + 1) else it
                }

                if (post.uid != uid) sendNotif(post.uid, "comment", "gönderini yorumladı")
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
                    "likes"       to 0,
                    "cmtCount"    to 0,
                    "reposts"     to 0,
                    "repostOf"    to post.id,
                    "ts"          to Timestamp.now(),
                )).await()

                firestore.collection("feed").document(post.id)
                    .update("reposts", FieldValue.increment(1)).await()

                _posts.value = _posts.value.map {
                    if (it.id == post.id) it.copy(repostsCount = it.repostsCount + 1) else it
                }

                if (post.uid != uid) sendNotif(post.uid, "repost", "gönderini paylaştı")
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun createPost(text: String) {
        if (uid.isEmpty()) return
        viewModelScope.launch {
            try {
                val userDoc = firestore.collection("users").document(uid).get().await()
                firestore.collection("feed").add(mapOf(
                    "uid"         to uid,
                    "displayName" to (userDoc.getString("displayName") ?: ""),
                    "username"    to (userDoc.getString("username") ?: ""),
                    "photoURL"    to (userDoc.getString("photoURL") ?: ""),
                    "text"        to text,
                    "likes"       to 0,
                    "cmtCount"    to 0,
                    "reposts"     to 0,
                    "ts"          to Timestamp.now(),
                )).await()
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    private suspend fun sendNotif(toUid: String, type: String, action: String) {
        try {
            val userDoc  = firestore.collection("users").document(uid).get().await()
            val fromName = userDoc.getString("displayName") ?: "Kullanıcı"
            firestore.collection("userNotifs").document(toUid).collection("msgs").add(mapOf(
                "fromUid"  to uid,
                "fromName" to fromName,
                "type"     to type,
                "message"  to "$fromName $action",
                "read"     to false,
                "ts"       to Timestamp.now(),
            )).await()
            firestore.collection("pushQueue").add(mapOf(
                "targetUid" to toUid,
                "title"     to "Heftreng",
                "body"      to "$fromName $action",
                "ts"        to Timestamp.now(),
            )).await()
        } catch (e: Exception) { e.printStackTrace() }
    }

    fun loadComments(postId: String) {
        viewModelScope.launch {
            try {
                val snap = firestore.collection("feed").document(postId)
                    .collection("comments")
                    .orderBy("ts", Query.Direction.ASCENDING).get().await()
                _comments.value = snap.documents.mapNotNull {
                    it.toObject(Comment::class.java)?.copy(id = it.id)
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }
}
