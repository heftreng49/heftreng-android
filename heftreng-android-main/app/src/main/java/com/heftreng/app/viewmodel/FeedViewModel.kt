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

    private val _loadingMore   = MutableStateFlow(false)
    private val _postNotFound  = MutableStateFlow<String?>(null)
    val postNotFound = _postNotFound.asStateFlow()
    val loadingMore = _loadingMore.asStateFlow()

    val uid get() = auth.currentUser?.uid ?: ""

    private var likedIds    = emptySet<String>()
    private var savedIds    = emptySet<String>()
    private var myRepostMap = emptyMap<String, String>() // originalPostId → myRepostDocId
    private var lastDoc: com.google.firebase.firestore.DocumentSnapshot? = null
    private val PAGE_SIZE = 20L

    init {
        observeFeed()
        // Kullanıcı geç giriş yaparsa veya değişirse interactions'ı yenile
        viewModelScope.launch {
            var prevUid = ""
            while (true) {
                val currentUid = auth.currentUser?.uid ?: ""
                if (currentUid.isNotEmpty() && currentUid != prevUid) {
                    loadInteractions()
                    // Mevcut post listesinin isSavedByMe / isLikedByMe değerlerini güncelle
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

    // İlk sayfa — realtime listener (son 20 gönderi)
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
                    val rawPosts = snap.documents.mapNotNull { doc ->
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
                        val ytVid       = d["ytVid"]       as? String ?: ""
                        val repostTitle = d["repostTitle"] as? String ?: ""
                        val repostUrl   = d["repostUrl"]   as? String ?: ""
                        val repostImg   = d["repostImg"]   as? String ?: ""
                        val repostType  = d["repostType"]  as? String ?: ""
                        val repostId    = d["repostId"]    as? String ?: ""
                        val repostText        = d["repostText"]        as? String ?: ""
                        val repostAuthor      = d["repostAuthor"]      as? String ?: ""
                        val repostAuthorPhoto = d["repostAuthorPhoto"] as? String ?: ""
                        val repostAuthorUid   = d["repostAuthorUid"]   as? String ?: ""
                        val serialTitle       = d["serialTitle"]       as? String ?: ""
                        val serialCover       = d["serialCover"]       as? String ?: d["serialBg"] as? String ?: ""
                        val chapterTitle      = d["chapterTitle"]      as? String ?: ""
                        val chapterOrder      = (d["chapterOrder"]     as? Long)?.toInt() ?: 0
                        @Suppress("UNCHECKED_CAST")
                        val badges      = (d["badges"] as? List<*>)?.filterIsInstance<String>() ?: emptyList<String>()
                        Post(
                            id            = doc.id,
                            uid           = d["uid"]      as? String ?: "",
                            displayName   = displayName,
                            username      = d["username"] as? String ?: "",
                            photoURL      = d["photoURL"] as? String ?: "",
                            text          = d["text"]     as? String ?: "",
                            name          = d["name"]      as? String ?: displayName,
                            imgUrl        = d["imgUrl"]    as? String ?: "",
                            imageURL      = imageURL,
                            ytVid         = ytVid,
                            badges        = badges,
                            repostTitle   = repostTitle,
                            repostUrl     = repostUrl,
                            repostImg     = repostImg,
                            repostType    = repostType,
                            repostId      = repostId,
                            repostText        = repostText,
                            repostAuthor      = repostAuthor,
                            repostAuthorPhoto = repostAuthorPhoto,
                            repostAuthorUid   = repostAuthorUid,
                            serialTitle       = serialTitle,
                            serialCover       = serialCover,
                            chapterTitle      = chapterTitle,
                            chapterOrder      = chapterOrder,
                            likesCount    = (d["likes"]    as? Long)?.toInt() ?: 0,
                            commentsCount = (d["cmtCount"] as? Long)?.toInt() ?: 0,
                            repostsCount  = (d["reposts"]  as? Long)?.toInt() ?: 0,
                            quoteText     = quoteText,
                            bookName      = bookName,
                            authorName    = authorName,
                            ts            = d["ts"] as? Timestamp,
                            isLikedByMe        = doc.id in likedIds,
                            isSavedByMe        = doc.id in savedIds,
                            isRepostedByMe     = doc.id in myRepostMap,
                            myRepostId         = myRepostMap[doc.id] ?: "",
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
                        )
                    }
                    _posts.value = enrichPostsWithUserData(rawPosts)
                    _loading.value = false
                }
            }
    }

    // ── Post listesini users koleksiyonundan güncel avatar/isim ile zenginleştir ──
    // feed dokümanındaki photoURL/displayName eski kalabilir; her zaman users'tan çek
    private suspend fun enrichPostsWithUserData(posts: List<Post>): List<Post> {
        if (posts.isEmpty()) return posts
        val uids = posts.map { it.uid }.filter { it.isNotBlank() }.distinct()
        val userMap = mutableMapOf<String, Pair<String, String>>() // uid → (name, photoURL)
        uids.chunked(10).forEach { chunk ->
            try {
                val snap = firestore.collection("users")
                    .whereIn(com.google.firebase.firestore.FieldPath.documentId(), chunk)
                    .get().await()
                snap.documents.forEach { doc ->
                    val name = (doc.getString("displayName") ?: doc.getString("name") ?: "").takeIf { it.isNotBlank() }
                    val photo = doc.getString("photoURL")?.takeIf { it.isNotBlank() }
                    if (name != null || photo != null) {
                        userMap[doc.id] = Pair(name ?: "", photo ?: "")
                    }
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

    // Tema: feedLikes query → "feedId" alan adı
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
            // Kullanıcının kendi repostları: originalPostId → repostDocId
            myRepostMap = firestore.collection("feed")
                .whereEqualTo("uid", uid)
                .whereEqualTo("repostType", "feed")
                .get().await().documents.mapNotNull { doc ->
                    val originalId = doc.getString("repostId") ?: return@mapNotNull null
                    originalId to doc.id
                }.toMap()
            // Mevcut post listesindeki isSavedByMe / isLikedByMe değerlerini yenile
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
                val likeRef = firestore
                    .collection("feed").document(postId)
                    .collection("comments").document(comment.id)
                    .collection("likes").document(uid)
                val cmtRef  = firestore
                    .collection("feed").document(postId)
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

    fun toggleSave(post: Post) {
        if (uid.isEmpty()) return
        val nowSaved = !post.isSavedByMe
        // Optimistic update
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
            } catch (e: Exception) {
                e.printStackTrace()
                // Hata durumunda rollback
                savedIds = if (nowSaved) savedIds - post.id else savedIds + post.id
                _posts.value = _posts.value.map {
                    if (it.id == post.id) it.copy(isSavedByMe = !nowSaved) else it
                }
            }
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
                    // uid alanı farklı isimlerle kaydedilmiş olabilir
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
        // Zaten repost ettiyse engelle — tema: rpCache ile aynı mantık
        if (post.isRepostedByMe || post.id in myRepostMap) {
            return
        }
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
                    "imgUrl"            to "",
                    "imageURL"          to "",
                    "repostType"        to "feed",
                    "repostId"          to post.id,
                    "repostUid"         to post.uid,
                    "repostText"        to post.text.take(200),
                    "repostAuthor"      to (post.displayName.ifBlank { post.name }),
                    "repostAuthorPhoto" to post.photoURL,
                    "repostAuthorUid"   to post.uid,
                    "repostImg"         to (post.imageURL.takeIf { it.isNotBlank() } ?: post.imgUrl),
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

    private val _commentError = MutableStateFlow<String?>(null)
    val commentError = _commentError.asStateFlow()

    fun clearCommentError() { _commentError.value = null }

    // ── Yorum Sil ─────────────────────────────────────────────────────────────
    fun deleteComment(postId: String, commentId: String) {
        if (uid.isEmpty()) return
        viewModelScope.launch {
            try {
                // Güvenlik: yorumun sahibi veya post sahibi mi kontrol et
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
                // PERMISSION_DENIED — kullanıcıya hata göster
                _commentError.value = if (e.message?.contains("PERMISSION_DENIED") == true)
                    "Bu yorumu silme yetkiniz yok." else "Yorum silinemedi: ${e.message}"
            }
        }
    }

    fun editComment(postId: String, commentId: String, newText: String) {
        if (uid.isEmpty() || newText.isBlank()) return
        viewModelScope.launch {
            try {
                // Güvenlik: yorumun sahibi mi kontrol et
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

    // ── Draft kaydet/yükle ────────────────────────────────────────────────────
    private var _draftPrefs: android.content.SharedPreferences? = null

    fun initDraftPrefs(context: android.content.Context) {
        _draftPrefs = context.getSharedPreferences("heft_drafts", android.content.Context.MODE_PRIVATE)
    }

    fun saveDraft(text: String) {
        _draftPrefs?.edit()?.putString("feed_draft", text)?.apply()
    }

    fun loadDraft(): String = _draftPrefs?.getString("feed_draft", "") ?: ""

    fun clearDraft() {
        _draftPrefs?.edit()?.remove("feed_draft")?.apply()
    }

    // ── Firebase Storage'a resim yükle, sonra gönderi oluştur ──────────────────
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
                val ref = storage.reference
                    .child("posts/$uid/${System.currentTimeMillis()}.jpg")
                ref.putFile(imageUri).await()
                val url = ref.downloadUrl.await().toString()
                createPost(text = text, imageURL = url, quoteText = quoteText, authorName = authorName, bookName = bookName)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _uploading.value = false
            }
        }
    }

    private val _uploading = MutableStateFlow(false)
    val uploading = _uploading.asStateFlow()

    fun createPost(text: String, imageURL: String = "", quoteText: String = "", authorName: String = "", bookName: String = "") {
        if (uid.isEmpty()) return
        viewModelScope.launch {
            try {
                val userDoc = firestore.collection("users").document(uid).get().await()
                val myName  = userDoc.getString("displayName") ?: userDoc.getString("name") ?: auth.currentUser?.displayName ?: ""
                val myPhoto = userDoc.getString("photoURL") ?: auth.currentUser?.photoUrl?.toString() ?: ""
                val myUser  = userDoc.getString("username") ?: ""
                val myEmail = userDoc.getString("email") ?: auth.currentUser?.email ?: ""

                // ── Alıntı varsa yazar ve kitap sayfalarını otomatik oluştur ──────
                var libraryAuthorId = ""
                var libraryBookId   = ""
                if (quoteText.isNotBlank() && (authorName.isNotBlank() || bookName.isNotBlank())) {
                    try {
                        val (aid, bid) = ensureAuthorAndBook(authorName, bookName)
                        libraryAuthorId = aid
                        libraryBookId   = bid
                    } catch (_: Exception) {}
                }

                val feedRef = firestore.collection("feed").add(mapOf(
                    "uid"              to uid,
                    "name"             to myName,
                    "displayName"      to myName,
                    "username"         to myUser,
                    "photoURL"         to myPhoto,
                    "authorEmail"      to myEmail,
                    "text"             to text,
                    "imgUrl"           to imageURL,
                    "imageURL"         to imageURL,
                    "quote"            to if (quoteText.isNotBlank()) mapOf(
                        "text" to quoteText, "book" to bookName, "author" to authorName,
                    ) else null,
                    "quoteText"        to quoteText,
                    "authorName"       to authorName,
                    "bookName"         to bookName,
                    "libraryAuthorId"  to libraryAuthorId,
                    "libraryBookId"    to libraryBookId,
                    "likes"            to 0, "saves" to 0, "cmtCount" to 0, "reposts" to 0,
                    "ts"               to Timestamp.now(),
                )).await()

                // ── Kütüphane alıntı alt koleksiyonuna da ekle ────────────────
                if (quoteText.isNotBlank() && libraryBookId.isNotBlank()) {
                    try {
                        val quoteData = hashMapOf(
                            "bookId"          to libraryBookId,
                            "authorId"        to libraryAuthorId,
                            "bookTitle"       to bookName,
                            "authorName"      to authorName,
                            "text"            to quoteText,
                            "uid"             to uid,
                            "userDisplayName" to myName,
                            "userPhotoURL"    to myPhoto,
                            "feedPostId"      to feedRef.id,
                            "likesCount"      to 0,
                            "ts"              to Timestamp.now(),
                        )
                        firestore.collection("library_books").document(libraryBookId)
                            .collection("quotes").add(quoteData).await()
                        // Sayaç güncelle
                        firestore.collection("library_books").document(libraryBookId)
                            .update("quoteCount", com.google.firebase.firestore.FieldValue.increment(1))
                        if (libraryAuthorId.isNotBlank()) {
                            firestore.collection("authors").document(libraryAuthorId)
                                .update("quoteCount", com.google.firebase.firestore.FieldValue.increment(1))
                        }
                    } catch (_: Exception) {}
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    // ── Yazar ve kitap otomatik oluşturma ─────────────────────────────────
    private suspend fun ensureAuthorAndBook(authorName: String, bookName: String): Pair<String, String> {
        val authorId = if (authorName.isNotBlank()) {
            try {
                val snap = firestore.collection("authors")
                    .whereEqualTo("name", authorName.trim()).limit(1).get().await()
                if (!snap.isEmpty) snap.documents[0].id
                else {
                    firestore.collection("authors").add(hashMapOf(
                        "name"          to authorName.trim(),
                        "nameLower"     to authorName.trim().lowercase(),
                        "bio"           to "", "photoURL" to "", "birthYear" to 0,
                        "nationality"   to "", "bookCount" to 0, "quoteCount" to 0,
                        "reviewCount"   to 0, "followerCount" to 0,
                        "autoCreated"   to true,
                        "ts"            to Timestamp.now(),
                    )).await().id
                }
            } catch (_: Exception) { "" }
        } else ""

        val bookId = if (bookName.isNotBlank()) {
            try {
                val snap = firestore.collection("library_books")
                    .whereEqualTo("title", bookName.trim()).limit(1).get().await()
                if (!snap.isEmpty) snap.documents[0].id
                else {
                    val bid = firestore.collection("library_books").add(hashMapOf(
                        "title"       to bookName.trim(),
                        "titleLower"  to bookName.trim().lowercase(),
                        "authorId"    to authorId,
                        "authorName"  to authorName,
                        "coverImg"    to "", "genre" to "", "publishYear" to 0,
                        "synopsis"    to "", "pageCount" to 0,
                        "quoteCount"  to 0, "reviewCount" to 0, "avgRating" to 0f,
                        "autoCreated" to true,
                        "ts"          to Timestamp.now(),
                    )).await().id
                    if (authorId.isNotBlank()) {
                        try {
                            firestore.collection("authors").document(authorId)
                                .update("bookCount", com.google.firebase.firestore.FieldValue.increment(1))
                        } catch (_: Exception) {}
                    }
                    bid
                }
            } catch (_: Exception) { "" }
        } else ""

        return Pair(authorId, bookId)
    }

    fun deletePost(postId: String) {
        if (uid.isEmpty()) return
        viewModelScope.launch {
            try {
                // Güvenlik: post sahibi mi kontrol et
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
                // Güvenlik: post sahibi mi kontrol et
                val postDoc = firestore.collection("feed").document(postId).get().await()
                if (postDoc.getString("uid") != uid) return@launch
                firestore.collection("feed").document(postId).update("text", newText.trim()).await()
                  _posts.value = _posts.value.map { if (it.id == postId) it.copy(text = newText.trim()) else it }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    // ── Daha Fazla Yükle (pagination) ─────────────────────────────────────────
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
                val rawMore = snap.documents.mapNotNull { doc ->
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
                        isLikedByMe    = doc.id in likedIds,
                        isSavedByMe    = doc.id in savedIds,
                        isRepostedByMe = doc.id in myRepostMap,
                        myRepostId     = myRepostMap[doc.id] ?: "",
                    )
                }
                val newPosts = enrichPostsWithUserData(rawMore)
                _posts.value = _posts.value + newPosts
            } catch (e: Exception) { e.printStackTrace() }
            finally { _loadingMore.value = false }
        }
    }

    fun getPostById(postId: String): Post? = _posts.value.find { it.id == postId }

    // Tekil gönderi — feed'de yoksa Firestore'dan çek
    fun ensurePost(postId: String) {
        if (_posts.value.any { it.id == postId }) return
        viewModelScope.launch {
            try {
                val doc = firestore.collection("feed").document(postId).get().await()
                val d   = doc.data
                if (d == null) {
                    // Döküman yok — _notFound state'ini set et
                    _postNotFound.value = postId
                    return@launch
                }
                val quoteObj   = d["quote"] as? Map<*, *>
                val quoteText  = (quoteObj?.get("text") as? String)?.takeIf { it.isNotBlank() }
                                 ?: d["quoteText"] as? String ?: ""
                val bookName   = (quoteObj?.get("book") as? String)?.takeIf { it.isNotBlank() }
                                 ?: d["bookName"] as? String ?: ""
                val authorName = (quoteObj?.get("author") as? String)?.takeIf { it.isNotBlank() }
                                 ?: d["authorName"] as? String ?: ""
                val displayName = (d["displayName"] as? String)?.takeIf { it.isNotBlank() }
                                  ?: d["name"] as? String ?: ""
                val post = Post(
                    id            = doc.id,
                    uid           = d["uid"]           as? String ?: "",
                    displayName   = displayName,
                    name          = displayName,
                    username      = d["username"]      as? String ?: "",
                    photoURL      = d["photoURL"]      as? String ?: "",
                    text          = d["text"]          as? String ?: "",
                    imgUrl        = d["imgUrl"]        as? String ?: d["imageURL"] as? String ?: "",
                    imageURL      = d["imageURL"]      as? String ?: d["imgUrl"]  as? String ?: "",
                    likesCount    = (d["likes"]        as? Long)?.toInt() ?: 0,
                    commentsCount = (d["cmtCount"]     as? Long)?.toInt()
                                   ?: (d["commentsCount"] as? Long)?.toInt() ?: 0,
                    repostsCount  = (d["reposts"]      as? Long)?.toInt() ?: 0,
                    ts            = d["ts"]            as? com.google.firebase.Timestamp,
                    quoteText     = quoteText,
                    bookName      = bookName,
                    authorName    = authorName,
                    repostType    = d["repostType"]         as? String ?: "",
                    repostId      = d["repostId"]           as? String ?: "",
                    repostTitle   = d["repostTitle"]        as? String ?: "",
                    repostText    = d["repostText"]         as? String ?: "",
                    repostAuthor  = d["repostAuthor"]       as? String ?: "",
                    repostAuthorPhoto = d["repostAuthorPhoto"] as? String ?: "",
                    repostAuthorUid   = d["repostAuthorUid"]   as? String ?: "",
                    serialTitle   = d["serialTitle"]        as? String ?: "",
                    serialCover   = d["serialCover"]        as? String ?: "",
                    serialId      = d["serialId"]           as? String ?: "",
                    chapterId     = d["chapterId"]          as? String ?: "",
                    chapterTitle  = d["chapterTitle"]       as? String ?: "",
                    chapterOrder  = (d["chapterOrder"]      as? Long)?.toInt() ?: 0,
                    repostSerialId         = d["repostSerialId"]         as? String ?: "",
                    repostSerialTitle      = d["repostSerialTitle"]      as? String ?: "",
                    repostSerialDesc       = d["repostSerialDesc"]       as? String ?: "",
                    repostSerialCover      = d["repostSerialCover"]      as? String ?: "",
                    repostSerialAuthorName = d["repostSerialAuthorName"] as? String ?: "",
                    repostSerialAuthorUid  = d["repostSerialAuthorUid"]  as? String ?: "",
                    repostSerialBg         = d["repostSerialBg"]         as? String ?: "",
                    repostSerialChCount    = (d["repostSerialChCount"]   as? Long)?.toInt() ?: 0,
                    isLikedByMe    = doc.id in likedIds,
                    isSavedByMe    = doc.id in savedIds,
                    isRepostedByMe = doc.id in myRepostMap,
                    myRepostId     = myRepostMap[doc.id] ?: "",
                )
                _posts.value = _posts.value + post
            } catch (e: Exception) {
                e.printStackTrace()
                _postNotFound.value = postId
            }
        }
    }

    // ── Kitap Bölümü Repost ────────────────────────────────────────────────────
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
                val preview = chapterBody
                    .replace(Regex("<[^>]+>"), "") // HTML tag temizle
                    .trim().take(200)
                firestore.collection("feed").add(mapOf(
                    "uid"          to uid,
                    "name"         to myName,
                    "displayName"  to myName,
                    "username"     to (userDoc.getString("username") ?: ""),
                    "photoURL"     to myPhoto,
                    "text"         to "",
                    "imgUrl"       to "",
                    "imageURL"     to "",
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

    // Tema: userNotifs/{uid}/msgs, alan: feedId, title, sub, ico
    private suspend fun sendNotif(toUid: String, type: String, title: String, sub: String = "", feedId: String = "") {
        try {
            val userDoc  = firestore.collection("users").document(uid).get().await()
            val fromName = userDoc.getString("displayName") ?: userDoc.getString("name")
                ?: auth.currentUser?.displayName ?: "Kullanıcı"
            val fromPhoto= userDoc.getString("photoURL") ?: ""
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
            // FCM onNewNotif trigger tarafından gönderiliyor — sendPush kaldırıldı (çift bildirim yapıyordu)
        } catch (e: Exception) { e.printStackTrace() }
    }
}
