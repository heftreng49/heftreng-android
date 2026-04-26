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

    private val _loading  = MutableStateFlow(true)
    val loading = _loading.asStateFlow()

    val uid get() = auth.currentUser?.uid ?: ""

    private val _likedIds = MutableStateFlow<Set<String>>(emptySet())
    private val _savedIds = MutableStateFlow<Set<String>>(emptySet())

    init {
        viewModelScope.launch {
            loadUserInteractions()
            observeFeed()
        }
    }

    private suspend fun loadUserInteractions() {
        if (uid.isEmpty()) return
        try {
            val likes = firestore.collection("feedLikes").whereEqualTo("uid", uid).get().await()
            _likedIds.value = likes.documents.mapNotNull { it.getString("postId") }.toSet()
            val saves = firestore.collection("feedSaves").whereEqualTo("uid", uid).get().await()
            _savedIds.value = saves.documents.mapNotNull { it.getString("postId") }.toSet()
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun observeFeed() {
        firestore.collection("feed")
            .orderBy("ts", Query.Direction.DESCENDING)
            .limit(50)
            .addSnapshotListener { snap, err ->
                if (err != null || snap == null) { _loading.value = false; return@addSnapshotListener }
                viewModelScope.launch {
                    _posts.value = snap.documents.mapNotNull { doc ->
                        val d = doc.data ?: return@mapNotNull null
                        Post(
                            id            = doc.id,
                            uid           = d["uid"]         as? String ?: "",
                            displayName   = d["displayName"] as? String ?: "",
                            username      = d["username"]    as? String ?: "",
                            photoURL      = d["photoURL"]    as? String ?: "",
                            text          = d["text"]        as? String ?: "",
                            imageURL      = d["imageURL"]    as? String ?: "",
                            quoteText     = d["quoteText"]   as? String ?: "",
                            authorName    = d["authorName"]  as? String ?: "",
                            bookName      = d["bookName"]    as? String ?: "",
                            ts            = d["ts"]          as? Timestamp,
                            likesCount    = (d["likes"]    as? Long)?.toInt() ?: 0,
                            commentsCount = (d["cmtCount"] as? Long)?.toInt() ?: 0,
                            repostsCount  = (d["reposts"]  as? Long)?.toInt() ?: 0,
                            savesCount    = (d["saves"]    as? Long)?.toInt() ?: 0,
                            isLikedByMe   = doc.id in _likedIds.value,
                            isSavedByMe   = doc.id in _savedIds.value,
                        )
                    }
                    _loading.value = false
                }
            }
    }

    fun toggleLike(post: Post) {
        if (uid.isEmpty()) return
        val nowLiked = !post.isLikedByMe
        _likedIds.value = if (nowLiked) _likedIds.value + post.id else _likedIds.value - post.id
        _posts.value = _posts.value.map {
            if (it.id == post.id) it.copy(isLikedByMe = nowLiked, likesCount = it.likesCount + if (nowLiked) 1 else -1) else it
        }
        viewModelScope.launch {
            try {
                val likeDoc = firestore.collection("feedLikes").document("${post.id}_$uid")
                val postRef = firestore.collection("feed").document(post.id)
                if (nowLiked) {
                    likeDoc.set(mapOf("uid" to uid, "postId" to post.id, "ts" to Timestamp.now())).await()
                    postRef.update("likes", FieldValue.increment(1)).await()
                    if (post.uid != uid) sendNotif(post.uid, "like", "${myDisplayName()} nivîsa te hezkirî")
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
        _savedIds.value = if (nowSaved) _savedIds.value + post.id else _savedIds.value - post.id
        _posts.value = _posts.value.map {
            if (it.id == post.id) it.copy(isSavedByMe = nowSaved) else it
        }
        viewModelScope.launch {
            try {
                val saveDoc = firestore.collection("feedSaves").document("${post.id}_$uid")
                val postRef = firestore.collection("feed").document(post.id)
                if (nowSaved) {
                    saveDoc.set(mapOf("uid" to uid, "postId" to post.id, "ts" to Timestamp.now())).await()
                    postRef.update("saves", FieldValue.increment(1)).await()
                } else {
                    saveDoc.delete().await()
                    postRef.update("saves", FieldValue.increment(-1)).await()
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun repost(post: Post) {
        if (uid.isEmpty()) return
        viewModelScope.launch {
            try {
                val userDoc = firestore.collection("users").document(uid).get().await()
                firestore.collection("feed").add(mapOf(
                    "uid"        to uid,
                    "displayName"to (userDoc.getString("displayName") ?: ""),
                    "username"   to (userDoc.getString("username")    ?: ""),
                    "photoURL"   to (userDoc.getString("photoURL")    ?: ""),
                    "text"       to post.text,
                    "imageURL"   to post.imageURL,
                    "quoteText"  to post.quoteText,
                    "authorName" to post.authorName,
                    "bookName"   to post.bookName,
                    "likes"      to 0,
                    "cmtCount"   to 0,
                    "reposts"    to 0,
                    "saves"      to 0,
                    "repostOf"   to post.id,
                    "ts"         to Timestamp.now(),
                )).await()
                firestore.collection("feed").document(post.id)
                    .update("reposts", FieldValue.increment(1)).await()
                _posts.value = _posts.value.map {
                    if (it.id == post.id) it.copy(repostsCount = it.repostsCount + 1) else it
                }
                if (post.uid != uid) sendNotif(post.uid, "repost", "${myDisplayName()} nivîsa te dubare kir")
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun createPost(text: String, quoteText: String = "", bookName: String = "", authorName: String = "") {
        if (uid.isEmpty()) return
        viewModelScope.launch {
            try {
                val userDoc = firestore.collection("users").document(uid).get().await()
                firestore.collection("feed").add(mapOf(
                    "uid"        to uid,
                    "displayName"to (userDoc.getString("displayName") ?: ""),
                    "username"   to (userDoc.getString("username")    ?: ""),
                    "photoURL"   to (userDoc.getString("photoURL")    ?: ""),
                    "text"       to text,
                    "imageURL"   to "",
                    "quoteText"  to quoteText,
                    "authorName" to authorName,
                    "bookName"   to bookName,
                    "likes"      to 0,
                    "cmtCount"   to 0,
                    "reposts"    to 0,
                    "saves"      to 0,
                    "ts"         to Timestamp.now(),
                )).await()
                firestore.collection("users").document(uid)
                    .update("xp", FieldValue.increment(5)).await()
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun addComment(post: Post, text: String) {
        if (uid.isEmpty() || text.isBlank()) return
        viewModelScope.launch {
            try {
                val userDoc = firestore.collection("users").document(uid).get().await()
                firestore.collection("feed").document(post.id).collection("comments").add(mapOf(
                    "uid"        to uid,
                    "displayName"to (userDoc.getString("displayName") ?: "Bikarhêner"),
                    "photoURL"   to (userDoc.getString("photoURL")    ?: ""),
                    "text"       to text,
                    "likes"      to 0,
                    "ts"         to Timestamp.now(),
                )).await()
                firestore.collection("feed").document(post.id)
                    .update("cmtCount", FieldValue.increment(1)).await()
                _posts.value = _posts.value.map {
                    if (it.id == post.id) it.copy(commentsCount = it.commentsCount + 1) else it
                }
                if (post.uid != uid) sendNotif(post.uid, "comment", "${myDisplayName()} şîroveyê li nivîsa te kir")
                loadComments(post.id)
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun loadComments(postId: String) {
        viewModelScope.launch {
            try {
                val snap = firestore.collection("feed").document(postId)
                    .collection("comments")
                    .orderBy("ts", Query.Direction.ASCENDING).get().await()
                _comments.value = snap.documents.mapNotNull { doc ->
                    val d = doc.data ?: return@mapNotNull null
                    Comment(
                        id          = doc.id,
                        uid         = d["uid"]         as? String ?: "",
                        displayName = d["displayName"] as? String ?: "",
                        photoURL    = d["photoURL"]    as? String ?: "",
                        text        = d["text"]        as? String ?: "",
                        likesCount  = (d["likes"] as? Long)?.toInt() ?: 0,
                        ts          = d["ts"] as? Timestamp,
                    )
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    private suspend fun myDisplayName(): String {
        return try {
            firestore.collection("users").document(uid).get().await()
                .getString("displayName") ?: "Bikarhêner"
        } catch (_: Exception) { "Bikarhêner" }
    }

    private suspend fun sendNotif(toUid: String, type: String, message: String) {
        try {
            val userDoc = firestore.collection("users").document(uid).get().await()
            firestore.collection("userNotifs").document(toUid).collection("msgs").add(mapOf(
                "fromUid"  to uid,
                "fromName" to (userDoc.getString("displayName") ?: ""),
                "fromPhoto"to (userDoc.getString("photoURL")    ?: ""),
                "type"     to type,
                "message"  to message,
                "url"      to "",
                "read"     to false,
                "ts"       to Timestamp.now(),
            )).await()
        } catch (e: Exception) { e.printStackTrace() }
    }
}
