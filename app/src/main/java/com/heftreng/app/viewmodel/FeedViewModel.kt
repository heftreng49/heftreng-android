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

// Tema (heftreng-optimized-4-3.xml) ile tam senkron:
// feedLikes  → feedId alan adı  (postId değil)
// feedSaves  → feedId alan adı  (postId değil)
// feed/comments → name alan adı (displayName değil)
// userNotifs → feedId alan adı  (postId değil)
// feed gönderi → name + imgUrl alan adları (displayName/imageURL ek olarak)
// quote → nested {text,book,author} objesi + flat alanlar ikisi birden

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

    private val _hasMore  = MutableStateFlow(true)
    val hasMore = _hasMore.asStateFlow()

    private val _loadingMore = MutableStateFlow(false)
    val loadingMore = _loadingMore.asStateFlow()

    val uid get() = auth.currentUser?.uid ?: ""

    private var likedIds = emptySet<String>()
    private var savedIds = emptySet<String>()
    private var lastDoc: com.google.firebase.firestore.DocumentSnapshot? = null
    private val PAGE_SIZE = 20L

    init { observeFeed() }

    // İlk sayfa — realtime listener (son 20 gönderi)
    private fun observeFeed() {
        _loading.value = true
        firestore.collection("feed")
            .orderBy("ts", Query.Direction.DESCENDING)
            .limit(PAGE_SIZE)
            .addSnapshotListener { snap, err ->
                if (err != null || snap == null) { _loading.value = false; return@addSnapshotListener }
                viewModelScope.launch {
                    if (uid.isNotEmpty() && likedIds.isEmpty() && savedIds.isEmpty()) loadInteractions()
                    if (snap.documents.isNotEmpty()) lastDoc = snap.documents.last()
                    _hasMore.value = snap.documents.size >= PAGE_SIZE.toInt()
                    _posts.value = snap.documents.mapNotNull { doc ->
                        val d = doc.data ?: return@mapNotNull null
                        // Tema: "name", Android: "displayName" — ikisini destekle
                        val displayName = (d["displayName"] as? String)?.takeIf { it.isNotBlank() }
                            ?: d["name"] as? String ?: ""
                        // Tema: quote nested {text,book,author}, Android: flat alanlar
                        val quoteObj    = d["quote"] as? Map<*, *>
                        val quoteText   = (quoteObj?.get("text") as? String)?.takeIf { it.isNotBlank() }
                            ?: d["quoteText"] as? String ?: ""
                        val bookName    = (quoteObj?.get("book") as? String)?.takeIf { it.isNotBlank() }
                            ?: d["bookName"] as? String ?: ""
                        val authorName  = (quoteObj?.get("author") as? String)?.takeIf { it.isNotBlank() }
                            ?: d["authorName"] as? String ?: ""
                        // Tema: "imgUrl", Android: "imageURL"
                        val imageURL    = (d["imageURL"] as? String)?.takeIf { it.isNotBlank() }
                            ?: d["imgUrl"] as? String ?: ""
                        Post(
                            id            = doc.id,
                            uid           = d["uid"]      as? String ?: "",
                            displayName   = displayName,
                            username      = d["username"] as? String ?: "",
                            photoURL      = d["photoURL"] as? String ?: "",
                            text          = d["text"]     as? String ?: "",
                            imageURL      = imageURL,
                            likesCount    = (d["likes"]    as? Long)?.toInt() ?: 0,
                            commentsCount = (d["cmtCount"] as? Long)?.toInt() ?: 0,
                            repostsCount  = (d["reposts"]  as? Long)?.toInt() ?: 0,
                            quoteText     = quoteText,
                            bookName      = bookName,
                            authorName    = authorName,
                            ts            = d["ts"] as? Timestamp,
                            isLikedByMe   = doc.id in likedIds,
                            isSavedByMe   = doc.id in savedIds,
                        )
                    }
                    _loading.value = false
                }
            }
    }

    // Tema: feedLikes query → "feedId" alan adı
    private suspend fun loadInteractions() {
        if (uid.isEmpty()) return
        try {
            likedIds = firestore.collection("feedLikes").whereEqualTo("uid", uid)
                .get().await().documents.mapNotNull {
                    it.getString("feedId") ?: it.getString("postId") // geriye dönük uyum
                }.toSet()
            savedIds = firestore.collection("feedSaves").whereEqualTo("uid", uid)
                .get().await().documents.mapNotNull {
                    it.getString("feedId") ?: it.getString("postId")
                }.toSet()
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
                    val myName  = auth.currentUser?.displayName ?: ""
                    val myPhoto = auth.currentUser?.photoUrl?.toString() ?: ""
                    // Tema alan yapısı: uid, feedId, name, photoURL, ts
                    likeRef.set(mapOf(
                        "uid"      to uid,
                        "feedId"   to post.id,   // tema: feedId
                        "name"     to myName,     // tema: name
                        "photoURL" to myPhoto,
                        "ts"       to Timestamp.now(),
                    )).await()
                    postRef.update("likes", FieldValue.increment(1)).await()
                    if (post.uid != uid) sendNotif(post.uid, "like", "$myName gönderini beğendi", post.text.take(60), post.id)
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
                    // Tema alan yapısı: uid, feedId, ts
                    saveRef.set(mapOf("uid" to uid, "feedId" to post.id, "ts" to Timestamp.now())).await()
                    firestore.collection("feed").document(post.id).update("saves", FieldValue.increment(1)).await()
                } else {
                    saveRef.delete().await()
                    firestore.collection("feed").document(post.id).update("saves", FieldValue.increment(-1)).await()
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    // Tema: feed/{postId}/comments alt koleksiyonu, "name" alanı
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
                        uid         = d["uid"]      as? String ?: "",
                        displayName = (d["displayName"] as? String)?.takeIf { it.isNotBlank() } ?: d["name"] as? String ?: "",
                        photoURL    = d["photoURL"] as? String ?: "",
                        text        = d["text"]     as? String ?: "",
                        likesCount  = (d["likes"]   as? Long)?.toInt() ?: 0,
                        ts          = d["ts"]       as? Timestamp,
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
                val myName  = userDoc.getString("displayName") ?: userDoc.getString("name")
                    ?: auth.currentUser?.displayName ?: "Bikarhêner"
                val myPhoto = userDoc.getString("photoURL") ?: ""
                // Tema alan yapısı: uid, name, photoURL, text, likes, replyTo, replyToCmtId, ts
                firestore.collection("feed").document(post.id).collection("comments").add(mapOf(
                    "uid"          to uid,
                    "name"         to myName,    // tema: name
                    "displayName"  to myName,    // Android uyumu
                    "photoURL"     to myPhoto,
                    "text"         to text,
                    "likes"        to 0,
                    "replyTo"      to "",
                    "replyToCmtId" to "",
                    "ts"           to Timestamp.now(),
                )).await()
                firestore.collection("feed").document(post.id).update("cmtCount", FieldValue.increment(1)).await()
                _posts.value = _posts.value.map {
                    if (it.id == post.id) it.copy(commentsCount = it.commentsCount + 1) else it
                }
                if (post.uid != uid) sendNotif(post.uid, "cmt", "$myName gönderine yorum yaptı", text.take(60), post.id)
                loadComments(post.id)
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun repost(post: Post) {
        if (uid.isEmpty()) return
        viewModelScope.launch {
            try {
                val userDoc = firestore.collection("users").document(uid).get().await()
                val myName  = userDoc.getString("displayName") ?: userDoc.getString("name") ?: ""
                val myPhoto = userDoc.getString("photoURL") ?: ""
                // Tema + Android alan uyumu
                firestore.collection("feed").add(mapOf(
                    "uid"         to uid,
                    "name"        to myName,
                    "displayName" to myName,
                    "username"    to (userDoc.getString("username") ?: ""),
                    "photoURL"    to myPhoto,
                    "text"        to post.text,
                    "imgUrl"      to post.imageURL,
                    "imageURL"    to post.imageURL,
                    "quote"       to if (post.quoteText.isNotBlank()) mapOf(
                        "text" to post.quoteText, "book" to post.bookName, "author" to post.authorName,
                    ) else null,
                    "quoteText"   to post.quoteText,
                    "bookName"    to post.bookName,
                    "authorName"  to post.authorName,
                    "likes"       to 0, "saves" to 0, "cmtCount" to 0, "reposts" to 0,
                    "repostType"  to "feed",
                    "repostId"    to post.id,
                    "repostUid"   to post.uid,
                    "ts"          to Timestamp.now(),
                )).await()
                firestore.collection("feed").document(post.id).update("reposts", FieldValue.increment(1)).await()
                if (post.uid != uid) sendNotif(post.uid, "repost", "$myName gönderini paylaştı", post.text.take(60), post.id)
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun createPost(text: String, imageURL: String = "", quoteText: String = "", authorName: String = "", bookName: String = "") {
        if (uid.isEmpty()) return
        viewModelScope.launch {
            try {
                val userDoc = firestore.collection("users").document(uid).get().await()
                val myName  = userDoc.getString("displayName") ?: userDoc.getString("name") ?: auth.currentUser?.displayName ?: ""
                val myPhoto = userDoc.getString("photoURL") ?: auth.currentUser?.photoUrl?.toString() ?: ""
                val myUser  = userDoc.getString("username") ?: ""
                val myEmail = userDoc.getString("email") ?: auth.currentUser?.email ?: ""
                // Tema + Android uyumu — ikisini birden yaz
                firestore.collection("feed").add(mapOf(
                    "uid"          to uid,
                    "name"         to myName,
                    "displayName"  to myName,
                    "username"     to myUser,
                    "photoURL"     to myPhoto,
                    "authorEmail"  to myEmail,
                    "text"         to text,
                    "imgUrl"       to imageURL,
                    "imageURL"     to imageURL,
                    "quote"        to if (quoteText.isNotBlank()) mapOf(
                        "text" to quoteText, "book" to bookName, "author" to authorName,
                    ) else null,
                    "quoteText"    to quoteText,
                    "authorName"   to authorName,
                    "bookName"     to bookName,
                    "likes"        to 0, "saves" to 0, "cmtCount" to 0, "reposts" to 0,
                    "ts"           to Timestamp.now(),
                )).await()
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun deletePost(postId: String) {
        viewModelScope.launch {
            try { firestore.collection("feed").document(postId).delete().await()
                  _posts.value = _posts.value.filter { it.id != postId }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun editPost(postId: String, newText: String) {
        viewModelScope.launch {
            try { firestore.collection("feed").document(postId).update("text", newText.trim()).await()
                  _posts.value = _posts.value.map { if (it.id == postId) it.copy(text = newText.trim()) else it }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    // ── Daha Fazla Yükle (pagination) ────────────────────────────────────────────
    fun loadMore() {
        val last = lastDoc ?: return
        if (_loadingMore.value || !_hasMore.value) return
        viewModelScope.launch {
            _loadingMore.value = true
            try {
                val snap = firestore.collection("feed")
                    .orderBy("ts", com.google.firebase.firestore.Query.Direction.DESCENDING)
                    .startAfter(last)
                    .limit(PAGE_SIZE)
                    .get().await()
                if (snap.documents.isNotEmpty()) lastDoc = snap.documents.last()
                _hasMore.value = snap.documents.size >= PAGE_SIZE.toInt()
                val newPosts = snap.documents.mapNotNull { doc ->
                    val d = doc.data ?: return@mapNotNull null
                    val displayName = (d["displayName"] as? String)?.takeIf { it.isNotBlank() }
                        ?: d["name"] as? String ?: ""
                    val quoteObj   = d["quote"] as? Map<*, *>
                    val quoteText  = (quoteObj?.get("text") as? String)?.takeIf { it.isNotBlank() }
                        ?: d["quoteText"] as? String ?: ""
                    val bookName   = (quoteObj?.get("book") as? String)?.takeIf { it.isNotBlank() }
                        ?: d["bookName"] as? String ?: ""
                    val authorName = (quoteObj?.get("author") as? String)?.takeIf { it.isNotBlank() }
                        ?: d["authorName"] as? String ?: ""
                    val imageURL   = (d["imageURL"] as? String)?.takeIf { it.isNotBlank() }
                        ?: d["imgUrl"] as? String ?: ""
                    Post(
                        id            = doc.id,
                        uid           = d["uid"]      as? String ?: "",
                        displayName   = displayName,
                        username      = d["username"] as? String ?: "",
                        photoURL      = d["photoURL"] as? String ?: "",
                        text          = d["text"]     as? String ?: "",
                        imageURL      = imageURL,
                        likesCount    = (d["likes"]    as? Long)?.toInt() ?: 0,
                        commentsCount = (d["cmtCount"] as? Long)?.toInt() ?: 0,
                        repostsCount  = (d["reposts"]  as? Long)?.toInt() ?: 0,
                        quoteText     = quoteText,
                        bookName      = bookName,
                        authorName    = authorName,
                        ts            = d["ts"] as? com.google.firebase.Timestamp,
                        isLikedByMe   = doc.id in likedIds,
                        isSavedByMe   = doc.id in savedIds,
                    )
                }
                val existingIds = _posts.value.map { it.id }.toSet()
                _posts.value = _posts.value + newPosts.filter { it.id !in existingIds }
            } catch (e: Exception) { e.printStackTrace() }
            finally { _loadingMore.value = false }
        }
    }

    fun getPostById(postId: String): Post? = _posts.value.find { it.id == postId }

    // Tema: userNotifs/{uid}/msgs, alan: feedId, title, sub, ico
    private suspend fun sendNotif(toUid: String, type: String, title: String, sub: String = "", feedId: String = "") {
        try {
            val userDoc  = firestore.collection("users").document(uid).get().await()
            val fromName = userDoc.getString("displayName") ?: userDoc.getString("name")
                ?: auth.currentUser?.displayName ?: "Kullanıcı"
            val fromPhoto= userDoc.getString("photoURL") ?: ""
            val ico = when (type) { "like" -> "favorite"; "cmt" -> "chat_bubble"; "follow" -> "person_add"; "repost" -> "repeat"; else -> "notifications" }
            // Tema alan yapısı
            firestore.collection("userNotifs").document(toUid).collection("msgs").add(mapOf(
                "fromUid"   to uid,
                "fromName"  to fromName,
                "fromPhoto" to fromPhoto,
                "type"      to type,
                "feedId"    to feedId,   // tema: feedId
                "postId"    to feedId,   // Android uyumu (NotificationsScreen bunu okuyor)
                "title"     to title,
                "sub"       to sub,
                "ico"       to ico,
                "message"   to title,
                "url"       to "",
                "read"      to false,
                "ts"        to Timestamp.now(),
            )).await()
            firestore.collection("pushQueue").add(mapOf(
                "targetUid" to toUid, "title" to "Heftreng", "body" to title,
                "feedId"    to feedId, "ts"   to Timestamp.now(),
            )).await()
        } catch (e: Exception) { e.printStackTrace() }
    }
}
