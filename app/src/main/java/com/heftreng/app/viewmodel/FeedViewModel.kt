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
import com.heftreng.app.data.repository.LibraryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

// Tema (heftreng-optimized-4-3.xml) ile tam senkron:
// feedLikes  → feedId alan adı  (postId değil)
// feedSaves  → feedId alan adı  (postId değil)
// feed/comments → name alan adı (displayName ek olarak yazılır)
// userNotifs → feedId alan adı  (postId değil)
//
// Adım 1.2 — Post.name ve Post.imgUrl kaldırıldı; displayName ve imageURL kullanılır.
// Adım 2.1 — ensureAuthorAndBook LibraryRepository'ye taşındı.
// Adım 2.2 — Yeni postlara artık nested "quote" objesi YAZILMAZ; yalnızca flat alanlar.
//            Okuma sırasında eski nested formatı hâlâ desteklenir (legacy veri uyumu).

@HiltViewModel
class FeedViewModel @Inject constructor(
    private val auth     : FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val library  : LibraryRepository,       // Adım 2.1
) : ViewModel() {

    private val _posts    = MutableStateFlow<List<Post>>(emptyList())
    val posts = _posts.asStateFlow()

    // Kütüphane — alıntı postları (bookName dolu feed postları)
    private val _libraryQuotes = MutableStateFlow<List<Post>>(emptyList())
    val libraryQuotes = _libraryQuotes.asStateFlow()

    private val _comments = MutableStateFlow<List<Comment>>(emptyList())
    val comments = _comments.asStateFlow()

    private val _loading  = MutableStateFlow(false)
    val loading = _loading.asStateFlow()

    private val _hasMore  = MutableStateFlow(true)
    val hasMore = _hasMore.asStateFlow()

    private val _loadingMore   = MutableStateFlow(false)
    private val _postNotFound  = MutableStateFlow<String?>(null)
    val postNotFound = _postNotFound.asStateFlow()
    val loadingMore = _loadingMore.asStateFlow()

    private val _uploading = MutableStateFlow(false)
    val uploading = _uploading.asStateFlow()

    private val _commentError = MutableStateFlow<String?>(null)
    val commentError = _commentError.asStateFlow()

    val uid get() = auth.currentUser?.uid ?: ""

    private var likedIds    = emptySet<String>()
    private var savedIds    = emptySet<String>()
    private var myRepostMap = emptyMap<String, String>()
    private var lastDoc: com.google.firebase.firestore.DocumentSnapshot? = null
    private val PAGE_SIZE = 30L

    init {
        observeFeed()
        viewModelScope.launch {
            var prevUid = ""
            while (true) {
                val currentUid = auth.currentUser?.uid ?: ""
                if (currentUid.isNotEmpty() && currentUid != prevUid) {
                    loadInteractions()
                    _posts.value = _posts.value.map { post ->
                        post.copy(
                            isLikedByMe    = post.id in likedIds,
                            isSavedByMe    = post.id in savedIds,
                            isRepostedByMe = post.id in myRepostMap,
                            myRepostId     = myRepostMap[post.id] ?: "",
                        )
                    }
                    prevUid = currentUid
                }
                kotlinx.coroutines.delay(1000)
            }
        }
    }

    // ── Feed dinleyici ─────────────────────────────────────────────────────────
    private fun observeFeed() {
        _loading.value = true
        firestore.collection("feed")
            .orderBy("ts", Query.Direction.DESCENDING)
            .limit(PAGE_SIZE)
            .addSnapshotListener { snap, err ->
                if (err != null || snap == null) { _loading.value = false; return@addSnapshotListener }
                viewModelScope.launch {
                    if (snap.documents.isNotEmpty()) lastDoc = snap.documents.last()
                    _hasMore.value = snap.documents.size >= PAGE_SIZE.toInt()
                    val rawPosts = snap.documents.mapNotNull { doc -> doc.toPost() }
                    _posts.value = enrichPostsWithUserData(rawPosts)
                    _loading.value = false
                }
            }
    }

    // ── Firestore doc → Post dönüştürücü ──────────────────────────────────────
    // Adım 1.2 / 2.2 — name→displayName, imgUrl→imageURL normalize edilir.
    // Eski nested "quote" objesi okunur ama artık yazılmaz.
    internal fun com.google.firebase.firestore.DocumentSnapshot.toPost(): Post? {
        val d = data ?: return null
        // Adım 1.2 — displayName/name normalizasyonu
        val displayName = (d["displayName"] as? String)?.takeIf { it.isNotBlank() }
            ?: d["name"] as? String ?: ""
        // Adım 2.2 — Eski nested quote objesi + flat alanlar okunur
        val quoteObj    = d["quote"] as? Map<*, *>
        val quoteText   = (quoteObj?.get("text") as? String)?.takeIf { it.isNotBlank() }
            ?: d["quoteText"] as? String ?: ""
        val bookName    = (quoteObj?.get("book") as? String)?.takeIf { it.isNotBlank() }
            ?: d["bookName"] as? String ?: ""
        val authorName  = (quoteObj?.get("author") as? String)?.takeIf { it.isNotBlank() }
            ?: d["authorName"] as? String ?: ""
        // Adım 1.2 — imageURL/imgUrl normalizasyonu
        val imageURL    = (d["imageURL"] as? String)?.takeIf { it.isNotBlank() }
            ?: d["imgUrl"] as? String ?: ""
        @Suppress("UNCHECKED_CAST")
        val badges = (d["badges"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
        return Post(
            id            = id,
            uid           = d["uid"]      as? String ?: "",
            displayName   = displayName,
            name          = displayName,   // legacy uyumu
            username      = d["username"] as? String ?: "",
            photoURL      = d["photoURL"] as? String ?: "",
            text          = d["text"]     as? String ?: "",
            imageURL      = imageURL,
            imgUrl        = imageURL,      // legacy uyumu
            ytVid         = d["ytVid"]       as? String ?: "",
            badges        = badges,
            repostTitle   = d["repostTitle"] as? String ?: "",
            repostUrl     = d["repostUrl"]   as? String ?: "",
            repostImg     = d["repostImg"]   as? String ?: "",
            repostType    = d["repostType"]  as? String ?: "",
            repostId      = d["repostId"]    as? String ?: "",
            repostText        = d["repostText"]        as? String ?: "",
            repostAuthor      = d["repostAuthor"]      as? String ?: "",
            repostAuthorPhoto = d["repostAuthorPhoto"] as? String ?: "",
            repostAuthorUid   = d["repostAuthorUid"]   as? String ?: "",
            serialTitle       = d["serialTitle"]       as? String ?: "",
            serialCover       = d["serialCover"]       as? String ?: d["serialBg"] as? String ?: "",
            chapterTitle      = d["chapterTitle"]      as? String ?: "",
            chapterOrder      = (d["chapterOrder"]     as? Long)?.toInt() ?: 0,
            likesCount    = (d["likes"]    as? Long)?.toInt() ?: 0,
            commentsCount = (d["cmtCount"] as? Long)?.toInt() ?: 0,
            repostsCount  = (d["reposts"]  as? Long)?.toInt() ?: 0,
            quoteText     = quoteText,
            bookName      = bookName,
            authorName    = authorName,
            ts            = d["ts"] as? Timestamp,
            isLikedByMe        = id in likedIds,
            isSavedByMe        = id in savedIds,
            isRepostedByMe     = id in myRepostMap,
            myRepostId         = myRepostMap[id] ?: "",
            repostSerialId         = d["repostSerialId"]         as? String ?: "",
            repostSerialTitle      = d["repostSerialTitle"]      as? String ?: "",
            repostSerialDesc       = d["repostSerialDesc"]       as? String ?: "",
            repostSerialCover      = d["repostSerialCover"]      as? String ?: "",
            repostSerialAuthorName = d["repostSerialAuthorName"] as? String ?: "",
            repostSerialAuthorUid  = d["repostSerialAuthorUid"]  as? String ?: "",
            repostSerialBg         = d["repostSerialBg"]         as? String ?: "",
            repostSerialChCount    = (d["repostSerialChCount"]   as? Long)?.toInt() ?: 0,
            serialId               = d["serialId"]               as? String ?: "",
            chapterId              = d["chapterId"]              as? String ?: "",
            libraryBookId          = d["libraryBookId"]          as? String ?: "",
            libraryAuthorId        = d["libraryAuthorId"]        as? String ?: "",
            type                   = d["type"]                   as? String ?: "",
        )
    }

    // ── Kullanıcı verileriyle zenginleştir ────────────────────────────────────
    private suspend fun enrichPostsWithUserData(posts: List<Post>): List<Post> {
        if (posts.isEmpty()) return posts
        val uids = posts.map { it.uid }.filter { it.isNotBlank() }.distinct()
        val userMap = mutableMapOf<String, Pair<String, String>>()
        uids.chunked(10).forEach { chunk ->
            try {
                val snap = firestore.collection("users")
                    .whereIn(com.google.firebase.firestore.FieldPath.documentId(), chunk)
                    .get().await()
                snap.documents.forEach { doc ->
                    val name  = ((doc.getString("displayName") ?: doc.getString("name")) ?: "").takeIf { it.isNotBlank() }
                    val photo = doc.getString("photoURL")?.takeIf { it.isNotBlank() }
                    if (name != null || photo != null) userMap[doc.id] = Pair(name ?: "", photo ?: "")
                }
            } catch (_: Exception) {}
        }
        return posts.map { post ->
            val (freshName, freshPhoto) = userMap[post.uid] ?: return@map post
            post.copy(
                displayName = freshName.ifBlank { post.displayName },
                name        = freshName.ifBlank { post.name },
                photoURL    = freshPhoto.ifBlank { post.photoURL },
            )
        }
    }

    private suspend fun loadInteractions() {
        if (uid.isEmpty()) return
        try {
            likedIds = firestore.collection("feedLikes").whereEqualTo("uid", uid)
                .get().await().documents.mapNotNull {
                    it.getString("feedId") ?: it.getString("postId")
                }.toSet()
            savedIds = firestore.collection("feedSaves").whereEqualTo("uid", uid)
                .get().await().documents.mapNotNull {
                    it.getString("feedId") ?: it.getString("postId")
                }.toSet()
            myRepostMap = firestore.collection("feed")
                .whereEqualTo("uid", uid)
                .whereEqualTo("repostType", "feed")
                .get().await().documents.mapNotNull { doc ->
                    val originalId = doc.getString("repostId") ?: return@mapNotNull null
                    originalId to doc.id
                }.toMap()
            if (_posts.value.isNotEmpty()) {
                _posts.value = _posts.value.map { post ->
                    post.copy(
                        isLikedByMe    = post.id in likedIds,
                        isSavedByMe    = post.id in savedIds,
                        isRepostedByMe = post.id in myRepostMap,
                        myRepostId     = myRepostMap[post.id] ?: "",
                    )
                }
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    // ── Beğeni ────────────────────────────────────────────────────────────────
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
                    likeRef.set(mapOf(
                        "uid"      to uid,
                        "feedId"   to post.id,
                        "name"     to myName,
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

    fun toggleCommentLike(postId: String, comment: Comment) {
        if (uid.isEmpty()) return
        val nowLiked = !comment.isLikedByMe
        _comments.value = _comments.value.map {
            if (it.id == comment.id) it.copy(
                isLikedByMe = nowLiked,
                likesCount  = it.likesCount + if (nowLiked) 1 else -1,
            ) else it
        }
        viewModelScope.launch {
            try {
                val likeRef = firestore.collection("feed").document(postId)
                    .collection("comments").document(comment.id).collection("likes").document(uid)
                val cmtRef  = firestore.collection("feed").document(postId)
                    .collection("comments").document(comment.id)
                if (nowLiked) {
                    likeRef.set(mapOf("uid" to uid, "ts" to Timestamp.now())).await()
                    cmtRef.update("likes", FieldValue.increment(1)).await()
                } else {
                    likeRef.delete().await()
                    cmtRef.update("likes", FieldValue.increment(-1)).await()
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    // ── Kaydet ────────────────────────────────────────────────────────────────
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
                    saveRef.set(mapOf("uid" to uid, "feedId" to post.id, "ts" to Timestamp.now())).await()
                    firestore.collection("feed").document(post.id).update("saves", FieldValue.increment(1)).await()
                } else {
                    saveRef.delete().await()
                    firestore.collection("feed").document(post.id).update("saves", FieldValue.increment(-1)).await()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                savedIds = if (nowSaved) savedIds - post.id else savedIds + post.id
                _posts.value = _posts.value.map {
                    if (it.id == post.id) it.copy(isSavedByMe = !nowSaved) else it
                }
            }
        }
    }

    // ── Yorumlar ──────────────────────────────────────────────────────────────
    fun loadComments(postId: String) {
        viewModelScope.launch {
            try {
                val snap = firestore.collection("feed").document(postId)
                    .collection("comments").orderBy("ts", Query.Direction.ASCENDING).get().await()
                _comments.value = snap.documents.mapNotNull { doc ->
                    val d = doc.data ?: return@mapNotNull null
                    val commentUid = (d["uid"] as? String)?.takeIf { it.isNotBlank() }
                        ?: (d["userId"] as? String)?.takeIf { it.isNotBlank() }
                        ?: (d["authorId"] as? String)?.takeIf { it.isNotBlank() }
                        ?: ""
                    Comment(
                        id          = doc.id,
                        postId      = postId,
                        uid         = commentUid,
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
                firestore.collection("feed").document(post.id).collection("comments").add(mapOf(
                    "uid"          to uid,
                    "name"         to myName,
                    "displayName"  to myName,
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

    fun deleteComment(postId: String, commentId: String) {
        if (uid.isEmpty()) return
        viewModelScope.launch {
            try {
                val cmtDoc  = firestore.collection("feed").document(postId)
                    .collection("comments").document(commentId).get().await()
                val postDoc = firestore.collection("feed").document(postId).get().await()
                val isCommentOwner = cmtDoc.getString("uid") == uid
                val isPostOwner    = postDoc.getString("uid") == uid
                if (!isCommentOwner && !isPostOwner) return@launch
                firestore.collection("feed").document(postId)
                    .collection("comments").document(commentId).delete().await()
                firestore.collection("feed").document(postId)
                    .update("cmtCount", FieldValue.increment(-1)).await()
                _comments.value = _comments.value.filter { it.id != commentId }
                _posts.value = _posts.value.map {
                    if (it.id == postId) it.copy(commentsCount = maxOf(0, it.commentsCount - 1)) else it
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _commentError.value = if (e.message?.contains("PERMISSION_DENIED") == true)
                    "Bu yorumu silme yetkiniz yok." else "Yorum silinemedi: ${e.message}"
            }
        }
    }

    fun editComment(postId: String, commentId: String, newText: String) {
        if (uid.isEmpty() || newText.isBlank()) return
        viewModelScope.launch {
            try {
                val cmtDoc = firestore.collection("feed").document(postId)
                    .collection("comments").document(commentId).get().await()
                if (cmtDoc.getString("uid") != uid) return@launch
                firestore.collection("feed").document(postId)
                    .collection("comments").document(commentId)
                    .update("text", newText.trim()).await()
                _comments.value = _comments.value.map {
                    if (it.id == commentId) it.copy(text = newText.trim()) else it
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun clearCommentError() { _commentError.value = null }

    // ── Repost ────────────────────────────────────────────────────────────────
    fun repost(post: Post) {
        if (uid.isEmpty()) return
        if (post.isRepostedByMe || post.id in myRepostMap) return
        viewModelScope.launch {
            try {
                val userDoc = firestore.collection("users").document(uid).get().await()
                val myName  = userDoc.getString("displayName") ?: userDoc.getString("name") ?: ""
                val myPhoto = userDoc.getString("photoURL") ?: ""
                val newRef = firestore.collection("feed").add(mapOf(
                    "uid"               to uid,
                    "name"              to myName,
                    "displayName"       to myName,
                    "username"          to (userDoc.getString("username") ?: ""),
                    "photoURL"          to myPhoto,
                    "text"              to "",
                    "imageURL"          to "",
                    "imgUrl"            to "",
                    "repostType"        to "feed",
                    "repostId"          to post.id,
                    "repostUid"         to post.uid,
                    "repostText"        to post.text.take(200),
                    "repostAuthor"      to post.displayName,
                    "repostAuthorPhoto" to post.photoURL,
                    "repostAuthorUid"   to post.uid,
                    "repostImg"         to post.imageURL,
                    "likes" to 0, "saves" to 0, "cmtCount" to 0, "reposts" to 0,
                    "ts"    to Timestamp.now(),
                )).await()
                firestore.collection("feed").document(post.id)
                    .update("reposts", FieldValue.increment(1)).await()
                myRepostMap = myRepostMap + (post.id to newRef.id)
                _posts.value = _posts.value.map {
                    if (it.id == post.id) it.copy(
                        repostsCount   = it.repostsCount + 1,
                        isRepostedByMe = true,
                        myRepostId     = newRef.id,
                    ) else it
                }
                if (post.uid != uid) sendNotif(post.uid, "repost", "$myName gönderini paylaştı", post.text.take(60), post.id)
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun unrepost(post: Post) {
        if (uid.isEmpty()) return
        val repostDocId = post.myRepostId.ifBlank { myRepostMap[post.id] } ?: return
        viewModelScope.launch {
            try {
                firestore.collection("feed").document(repostDocId).delete().await()
                firestore.collection("feed").document(post.id)
                    .update("reposts", FieldValue.increment(-1)).await()
                myRepostMap = myRepostMap - post.id
                _posts.value = _posts.value.map {
                    if (it.id == post.id) it.copy(
                        repostsCount   = maxOf(0, it.repostsCount - 1),
                        isRepostedByMe = false,
                        myRepostId     = "",
                    ) else it
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    // ── Draft ────────────────────────────────────────────────────────────────
    private var _draftPrefs: android.content.SharedPreferences? = null
    fun initDraftPrefs(context: android.content.Context) {
        _draftPrefs = context.getSharedPreferences("heft_drafts", android.content.Context.MODE_PRIVATE)
    }
    fun saveDraft(text: String) { _draftPrefs?.edit()?.putString("feed_draft", text)?.apply() }
    fun loadDraft(): String = _draftPrefs?.getString("feed_draft", "") ?: ""
    fun clearDraft() { _draftPrefs?.edit()?.remove("feed_draft")?.apply() }

    // ── Resim yükle → post oluştur ────────────────────────────────────────────
    fun uploadImageAndCreatePost(
        imageUri   : android.net.Uri,
        text       : String,
        quoteText  : String = "",
        authorName : String = "",
        bookName   : String = "",
        context    : android.content.Context,
    ) {
        if (uid.isEmpty()) return
        viewModelScope.launch {
            try {
                _uploading.value = true
                val storage = com.google.firebase.storage.FirebaseStorage.getInstance()
                val ref = storage.reference.child("posts/$uid/${System.currentTimeMillis()}.jpg")
                ref.putFile(imageUri).await()
                val url = ref.downloadUrl.await().toString()
                createPost(text = text, imageURL = url, quoteText = quoteText, authorName = authorName, bookName = bookName, type = if (quoteText.isNotBlank()) "library_quote" else "")
            } catch (e: Exception) { e.printStackTrace() }
            finally { _uploading.value = false }
        }
    }

    // ── Post oluştur ─────────────────────────────────────────────────────────
    // Adım 4.3 — Post oluşturma hatası UI'ya iletilir
    private val _createPostError = MutableStateFlow<String?>(null)
    val createPostError = _createPostError.asStateFlow()
    fun clearCreatePostError() { _createPostError.value = null }

    private val _createPostLoading = MutableStateFlow(false)
    val createPostLoading = _createPostLoading.asStateFlow()

    // Adım 2.1 — ensureAuthorAndBook → library.ensureAuthorAndBook
    // Adım 2.2 — nested "quote" objesi artık YAZILMAZ; yalnızca flat alanlar yazılır.
    // Adım 4.3 — ensureAuthorAndBook başarısız olursa post YINE oluşturulur (libraryBookId boş kalır)
    //            ama kullanıcıya bilgi verilir. addQuoteToLibrary başarısız olursa feedPostId
    //            üzerinden sonradan migrateLegacyFeedQuotes() ile kurtarılabilir.
    fun createPost(text: String, imageURL: String = "", quoteText: String = "", authorName: String = "", bookName: String = "", type: String = "") {
        if (uid.isEmpty()) return
        viewModelScope.launch {
            _createPostLoading.value = true
            try {
                val userDoc = firestore.collection("users").document(uid).get().await()
                val myName  = userDoc.getString("displayName") ?: userDoc.getString("name") ?: auth.currentUser?.displayName ?: ""
                val myPhoto = userDoc.getString("photoURL") ?: auth.currentUser?.photoUrl?.toString() ?: ""
                val myUser  = userDoc.getString("username") ?: ""
                val myEmail = userDoc.getString("email") ?: auth.currentUser?.email ?: ""

                // Adım 4.3 — ensureAuthorAndBook hatası post oluşturmayı engellemez;
                // libraryBookId boş kalır, post feed'e yazar ama kütüphane bağlantısı kurulmaz.
                var libraryAuthorId = ""
                var libraryBookId   = ""
                var libraryLinkFailed = false
                if (quoteText.isNotBlank() && (authorName.isNotBlank() || bookName.isNotBlank())) {
                    try {
                        val (aid, bid) = library.ensureAuthorAndBook(authorName, bookName)
                        libraryAuthorId = aid
                        libraryBookId   = bid
                    } catch (e: Exception) {
                        libraryLinkFailed = true
                        e.printStackTrace()
                    }
                }

                val feedRef = firestore.collection("feed").add(mapOf(
                    "uid"             to uid,
                    "name"            to myName,
                    "displayName"     to myName,
                    "username"        to myUser,
                    "photoURL"        to myPhoto,
                    "authorEmail"     to myEmail,
                    "text"            to text,
                    "imgUrl"          to imageURL,
                    "imageURL"        to imageURL,
                    "quoteText"       to quoteText,
                    "authorName"      to authorName,
                    "bookName"        to bookName,
                    "libraryAuthorId" to libraryAuthorId,
                    "libraryBookId"   to libraryBookId,
                    // type: alıntılı post ise "library_quote", değilse boş string
                    "type"            to if (quoteText.isNotBlank() && type.isBlank()) "library_quote" else type,
                    "likes"           to 0, "saves" to 0, "cmtCount" to 0, "reposts" to 0,
                    "ts"              to Timestamp.now(),
                )).await()

                // Adım 4.3 — Kütüphane yazma hatası feed yazmasını geri almaz;
                // migrateLegacyFeedQuotes() ile sonradan kurtarılabilir.
                if (quoteText.isNotBlank() && libraryBookId.isNotBlank()) {
                    try {
                        library.addQuoteToLibrary(
                            libraryBookId   = libraryBookId,
                            libraryAuthorId = libraryAuthorId,
                            bookName        = bookName,
                            authorName      = authorName,
                            quoteText       = quoteText,
                            uid             = uid,
                            userDisplayName = myName,
                            userPhotoURL    = myPhoto,
                            feedPostId      = feedRef.id,
                        )
                    } catch (e: Exception) {
                        libraryLinkFailed = true
                        e.printStackTrace()
                    }
                }

                // Adım 4.3 — Kütüphane bağlantısı kurulamazsa UI'ya soft uyarı ver
                if (libraryLinkFailed) {
                    _createPostError.value = "Gönderi paylaşıldı, ancak kütüphane bağlantısı kurulamadı."
                }

            } catch (e: Exception) {
                e.printStackTrace()
                _createPostError.value = "Gönderi paylaşılamadı: ${e.message}"
            } finally {
                _createPostLoading.value = false
            }
        }
    }

    // ── Post sil / düzenle ────────────────────────────────────────────────────
    fun deletePost(postId: String) {
        if (uid.isEmpty()) return
        viewModelScope.launch {
            try {
                val postDoc = firestore.collection("feed").document(postId).get().await()
                if (postDoc.getString("uid") != uid) return@launch
                firestore.collection("feed").document(postId).delete().await()
                _posts.value = _posts.value.filter { it.id != postId }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun editPost(postId: String, newText: String) {
        if (uid.isEmpty() || newText.isBlank()) return
        viewModelScope.launch {
            try {
                val postDoc = firestore.collection("feed").document(postId).get().await()
                if (postDoc.getString("uid") != uid) return@launch
                firestore.collection("feed").document(postId).update("text", newText.trim()).await()
                _posts.value = _posts.value.map { if (it.id == postId) it.copy(text = newText.trim()) else it }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    // ── Pagination ────────────────────────────────────────────────────────────
    fun refresh() {
        lastDoc = null
        _hasMore.value = true
        _postNotFound.value = null
        _loading.value = true
        viewModelScope.launch {
            try {
                val snap = firestore.collection("feed")
                    .orderBy("ts", Query.Direction.DESCENDING)
                    .limit(PAGE_SIZE)
                    .get().await()
                if (snap.documents.isNotEmpty()) lastDoc = snap.documents.last()
                _hasMore.value = snap.documents.size >= PAGE_SIZE.toInt()
                val rawPosts = snap.documents.mapNotNull { it.toPost() }
                _posts.value = enrichPostsWithUserData(rawPosts)
                loadInteractions()
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _loading.value = false
            }
        }
    }

    fun loadMore() {
        val last = lastDoc ?: return
        if (_loadingMore.value || !_hasMore.value) return
        viewModelScope.launch {
            _loadingMore.value = true
            try {
                val snap = firestore.collection("feed")
                    .orderBy("ts", Query.Direction.DESCENDING)
                    .startAfter(last)
                    .limit(PAGE_SIZE)
                    .get().await()
                if (snap.documents.isNotEmpty()) lastDoc = snap.documents.last()
                _hasMore.value = snap.documents.size >= PAGE_SIZE.toInt()
                val rawMore = snap.documents.mapNotNull { it.toPost() }
                val newPosts = enrichPostsWithUserData(rawMore)
                _posts.value = _posts.value + newPosts
            } catch (e: Exception) { e.printStackTrace() }
            finally { _loadingMore.value = false }
        }
    }

    fun getPostById(postId: String): Post? = _posts.value.find { it.id == postId }

    private val _fetchingPostIds = mutableSetOf<String>()

    fun ensurePost(postId: String) {
        if (_posts.value.any { it.id == postId }) return
        if (_fetchingPostIds.contains(postId)) return
        _fetchingPostIds.add(postId)
        if (_postNotFound.value == postId) _postNotFound.value = null
        viewModelScope.launch {
            try {
                val doc = firestore.collection("feed").document(postId).get().await()
                val post = doc.toPost()
                if (post == null) {
                    _postNotFound.value = postId
                } else {
                    val enriched = enrichPostsWithUserData(listOf(post))
                    _posts.value = _posts.value + enriched
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _postNotFound.value = postId
            } finally {
                _fetchingPostIds.remove(postId)
            }
        }
    }

    // ── Kitap bölümü repost ───────────────────────────────────────────────────
    fun repostBookChapter(
        bookId       : String,
        chapterId    : String,
        bookTitle    : String,
        chapterTitle : String,
        chapterOrder : Int,
        chapterBody  : String,
        bookCoverImg : String = "",
        bookAuthorUid: String = "",
        bookAuthorName: String = "",
    ) {
        if (uid.isEmpty()) return
        viewModelScope.launch {
            try {
                val userDoc = firestore.collection("users").document(uid).get().await()
                val myName  = userDoc.getString("displayName") ?: userDoc.getString("name") ?: ""
                val myPhoto = userDoc.getString("photoURL") ?: ""
                val preview = chapterBody.replace(Regex("<[^>]+>"), "").trim().take(200)
                firestore.collection("feed").add(mapOf(
                    "uid"          to uid,
                    "name"         to myName,
                    "displayName"  to myName,
                    "username"     to (userDoc.getString("username") ?: ""),
                    "photoURL"     to myPhoto,
                    "text"         to "",
                    "imageURL"     to "",
                    "imgUrl"       to "",
                    "repostType"   to "book_chapter",
                    "repostId"     to chapterId,
                    "repostTitle"  to bookTitle,
                    "repostText"   to preview,
                    "repostAuthor" to bookAuthorName,
                    "repostAuthorPhoto" to "",
                    "repostAuthorUid"   to bookAuthorUid,
                    "repostImg"    to bookCoverImg,
                    "serialTitle"  to bookTitle,
                    "serialCover"  to bookCoverImg,
                    "serialId"     to bookId,
                    "chapterId"    to chapterId,
                    "chapterTitle" to chapterTitle,
                    "chapterOrder" to chapterOrder,
                    "likes"  to 0, "saves" to 0, "cmtCount" to 0, "reposts" to 0,
                    "ts"     to Timestamp.now(),
                )).await()
                if (bookAuthorUid.isNotBlank() && bookAuthorUid != uid) {
                    sendNotif(bookAuthorUid, "repost", "$myName kitap bölümünü paylaştı", chapterTitle, chapterId)
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    // ── Bildirim gönder ───────────────────────────────────────────────────────
    private suspend fun sendNotif(toUid: String, type: String, title: String, sub: String = "", feedId: String = "") {
        try {
            val userDoc   = firestore.collection("users").document(uid).get().await()
            val fromName  = userDoc.getString("displayName") ?: userDoc.getString("name") ?: auth.currentUser?.displayName ?: "Kullanıcı"
            val fromPhoto = userDoc.getString("photoURL") ?: ""
            val ico = when (type) { "like" -> "favorite"; "cmt" -> "chat_bubble"; "follow" -> "person_add"; "repost" -> "repeat"; else -> "notifications" }
            firestore.collection("userNotifs").document(toUid).collection("msgs").add(mapOf(
                "fromUid"   to uid,
                "fromName"  to fromName,
                "fromPhoto" to fromPhoto,
                "type"      to type,
                "feedId"    to feedId,
                "postId"    to feedId,
                "title"     to title,
                "sub"       to sub,
                "ico"       to ico,
                "message"   to title,
                "url"       to "",
                "read"      to false,
                "ts"        to Timestamp.now(),
            )).await()
        } catch (e: Exception) { e.printStackTrace() }
    }

    // ── Kütüphane alıntı postları ─────────────────────────────────────────
    // Feed'den bookName dolu postları çeker; LibraryScreen PostCard ile gösterir.
    fun loadLibraryQuotes() {
        viewModelScope.launch {
            try {
                // bookName dolu AND quoteText dolu postları çek
                // "whereNotEqualTo" index gerektirir — tüm feed'i çekip bellekte filtrele
                val snap = firestore.collection("feed")
                    .orderBy("ts", com.google.firebase.firestore.Query.Direction.DESCENDING)
                    .limit(300).get().await()
                val result = snap.documents.mapNotNull { doc ->
                    val d = doc.data ?: return@mapNotNull null
                    val qObj      = d["quote"] as? Map<*, *>
                    val bookName  = (qObj?.get("book") as? String)?.takeIf { it.isNotBlank() }
                        ?: (d["bookName"] as? String)?.takeIf { it.isNotBlank() }
                        ?: return@mapNotNull null   // bookName yoksa atla
                    val quoteText = (qObj?.get("text") as? String)?.takeIf { it.isNotBlank() }
                        ?: (d["quoteText"] as? String)?.takeIf { it.isNotBlank() }
                        ?: return@mapNotNull null   // quoteText yoksa atla
                    doc.toPost()
                }
                _libraryQuotes.value = result
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
