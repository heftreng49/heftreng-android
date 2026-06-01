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

    // ── Takip Önerileri ──────────────────────────────────────────────────────
    private val _suggestedUsers = MutableStateFlow<List<SuggestedUser>>(emptyList())
    val suggestedUsers = _suggestedUsers.asStateFlow()

    data class SuggestedUser(
        val uid        : String,
        val name       : String,
        val photoURL   : String,
        val bio        : String = "",
        val isFollowing: Boolean = false,
    )

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
    // Kullanıcı bilgilerini ViewModel ömrü boyunca cache'le — her snapshot'ta yeniden çekmeyi önler
    private val userCache = mutableMapOf<String, Pair<String, String>>()

    // Canlı dinleyici referansları — onCleared'da kapatılır
    private var feedListener    : com.google.firebase.firestore.ListenerRegistration? = null
    private var likedListener   : com.google.firebase.firestore.ListenerRegistration? = null
    private var savedListener   : com.google.firebase.firestore.ListenerRegistration? = null
    private var repostListener  : com.google.firebase.firestore.ListenerRegistration? = null
    private var libQuoteListener: com.google.firebase.firestore.ListenerRegistration? = null

    init {
        auth.addAuthStateListener { firebaseAuth ->
            val currentUid = firebaseAuth.currentUser?.uid ?: ""
            if (currentUid.isNotEmpty()) {
                startLiveInteractions(currentUid)
            }
        }
        observeFeed()
        observeLibraryQuotes()
    }

    // ── Etkileşimleri canlı dinle (liked / saved / repost) ───────────────────
    // .get().await() yerine addSnapshotListener — kalp kaybolma sorunu çözülür
    private fun startLiveInteractions(currentUid: String) {
        // Beğeniler
        likedListener?.remove()
        likedListener = firestore.collection("feedLikes")
            .whereEqualTo("uid", currentUid)
            .addSnapshotListener { snap, _ ->
                likedIds = snap?.documents
                    ?.mapNotNull { it.getString("feedId") ?: it.getString("postId") }
                    ?.toSet() ?: emptySet()
                syncInteractionsToState()
            }

        // Kaydedilenler
        savedListener?.remove()
        savedListener = firestore.collection("feedSaves")
            .whereEqualTo("uid", currentUid)
            .addSnapshotListener { snap, _ ->
                savedIds = snap?.documents
                    ?.mapNotNull { it.getString("feedId") ?: it.getString("postId") }
                    ?.toSet() ?: emptySet()
                syncInteractionsToState()
            }

        // Repostlar
        repostListener?.remove()
        repostListener = firestore.collection("feed")
            .whereEqualTo("uid", currentUid)
            .whereEqualTo("repostType", "feed")
            .addSnapshotListener { snap, _ ->
                myRepostMap = snap?.documents
                    ?.mapNotNull { doc ->
                        val orig = doc.getString("repostId") ?: return@mapNotNull null
                        orig to doc.id
                    }?.toMap() ?: emptyMap()
                syncInteractionsToState()
            }
    }

    // Tüm postların etkileşim bayraklarını güncel setlerle senkronize eder
    private fun syncInteractionsToState() {
        if (_posts.value.isEmpty()) return
        _posts.value = _posts.value.map { post ->
            post.copy(
                isLikedByMe    = post.id in likedIds,
                isSavedByMe    = post.id in savedIds,
                isRepostedByMe = post.id in myRepostMap,
                myRepostId     = myRepostMap[post.id] ?: "",
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        feedListener?.remove()
        likedListener?.remove()
        savedListener?.remove()
        repostListener?.remove()
        libQuoteListener?.remove()
    }

    // ── Kütüphane alıntı dinleyici — gerçek zamanlı ───────────────────────────
    private fun observeLibraryQuotes() {
        libQuoteListener?.remove()
        libQuoteListener = firestore.collection("feed")
            .whereEqualTo("type", "library_quote")
            .addSnapshotListener { snap, err ->
                if (err != null || snap == null) return@addSnapshotListener
                viewModelScope.launch {
                    val seenIds = mutableSetOf<String>()
                    val result  = mutableListOf<Post>()
                    snap.documents.forEach { doc ->
                        if (seenIds.add(doc.id)) {
                            doc.toPost()?.let { result.add(it) }
                        }
                    }
                    val existing   = _libraryQuotes.value.filter { it.id.startsWith("cg_") }
                    val existingIds = result.map { it.id }.toSet()
                    val merged = result + existing.filter { it.id !in existingIds }
                    _libraryQuotes.value = merged.sortedByDescending { it.ts?.seconds ?: 0L }
                }
            }
    }

    // ── Feed dinleyici — Anında göster, arka planda zenginleştir ─────────────
    private fun observeFeed() {
        // Zaten post varsa yükleme çarkını gösterme — sayfa önceden açılmışsa akıcılığı koru
        _loading.value = _posts.value.isEmpty()
        feedListener?.remove()
        feedListener = firestore.collection("feed")
            .orderBy("ts", Query.Direction.DESCENDING)
            .limit(PAGE_SIZE)
            .addSnapshotListener { snap, err ->
                if (err != null || snap == null) { _loading.value = false; return@addSnapshotListener }
                viewModelScope.launch {
                    // lastDoc yalnızca liste boşken set edilir — sayfalama kaymasını önler
                    if (lastDoc == null && snap.documents.isNotEmpty()) {
                        lastDoc = snap.documents.last()
                    }
                    _hasMore.value = snap.documents.size >= PAGE_SIZE.toInt()

                    val rawPosts = snap.documents.mapNotNull { doc -> doc.toPost() }
                    val filtered = rawPosts.filter { it.moderationStatus != "removed" }

                    // ── Adım 1: Anında ekrana bas (post içindeki gömülü verilerle)
                    // Kullanıcı yükleme çarkı görmeden postaları okumaya başlar
                    _posts.value = mapInteractions(filtered)
                    _loading.value = false

                    // ── Adım 2: Arka planda profil bilgilerini zenginleştir
                    // await() burada ana akışı bloklamaz — ayrı launch içinde
                    enrichPostsInBackground(filtered)
                }
            }
    }

    // Etkileşim bayraklarını güncel setlerle uygular
    private fun mapInteractions(posts: List<Post>): List<Post> = posts.map { post ->
        post.copy(
            isLikedByMe    = post.id in likedIds,
            isSavedByMe    = post.id in savedIds,
            isRepostedByMe = post.id in myRepostMap,
            myRepostId     = myRepostMap[post.id] ?: "",
        )
    }

    // Cache'de olmayan kullanıcı bilgilerini arka planda çeker, varsa state'i sessizce günceller
    private fun enrichPostsInBackground(posts: List<Post>) {
        if (posts.isEmpty()) return
        val missingUids = posts.map { it.uid }
            .filter { it.isNotBlank() && it !in userCache }
            .distinct()
        if (missingUids.isEmpty()) return

        viewModelScope.launch {
            var cacheUpdated = false
            missingUids.chunked(10).forEach { chunk ->
                try {
                    val snap = firestore.collection("users")
                        .whereIn(com.google.firebase.firestore.FieldPath.documentId(), chunk)
                        .get().await()
                    snap.documents.forEach { doc ->
                        val name  = (doc.getString("displayName") ?: doc.getString("name"))
                            ?.takeIf { it.isNotBlank() }
                        val photo = doc.getString("photoURL")?.takeIf { it.isNotBlank() }
                        if (name != null || photo != null) {
                            userCache[doc.id] = Pair(name ?: "", photo ?: "")
                            cacheUpdated = true
                        }
                    }
                } catch (_: Exception) {}
            }
            // Yeni veri geldiyse postları sessizce güncelle (profil resimleri/adlar)
            if (cacheUpdated) {
                _posts.value = _posts.value.map { post ->
                    val (freshName, freshPhoto) = userCache[post.uid] ?: return@map post
                    post.copy(
                        displayName = freshName.ifBlank { post.displayName },
                        name        = freshName.ifBlank { post.name },
                        photoURL    = freshPhoto.ifBlank { post.photoURL },
                    )
                }
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
            visibility             = d["visibility"]             as? String ?: "public",
            moderationStatus       = d["moderationStatus"]       as? String ?: "active",
            moderationReason       = d["moderationReason"]       as? String ?: "",
        )
    }

    // ── Beğeni ────────────────────────────────────────────────────────────────
    // ── Admin: Gönderi Görünürlük Kısıtlaması ────────────────────────────────
    // "public"  → herkese açık
    // "friends" → sadece takipçiler
    // "only_me" → sadece sahibi
    fun setPostVisibility(postId: String, visibility: String) {
        viewModelScope.launch {
            try {
                firestore.collection("feed").document(postId)
                    .update("visibility", visibility).await()
                // Lokal state güncelle
                _posts.value = _posts.value.map {
                    if (it.id == postId) it.copy(visibility = visibility) else it
                }
                _libraryQuotes.value = _libraryQuotes.value.map {
                    if (it.id == postId) it.copy(visibility = visibility) else it
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
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
                    val uDoc    = try { firestore.collection("users").document(uid).get().await() } catch (_: Exception) { null }
                    val myName  = (uDoc?.getString("displayName") ?: uDoc?.getString("name"))
                        ?.takeIf { it.isNotBlank() } ?: auth.currentUser?.displayName ?: ""
                    val myPhoto = uDoc?.getString("photoURL") ?: auth.currentUser?.photoUrl?.toString() ?: ""
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
    fun createPost(text: String, imageURL: String = "", quoteText: String = "", authorName: String = "", bookName: String = "", type: String = "", libraryAuthorId: String = "", libraryBookId: String = "") {
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
                var resolvedAuthorId = libraryAuthorId
                var resolvedBookId   = libraryBookId
                var libraryLinkFailed = false
                // Dışarıdan geçirilmemişse otomatik eşleştir
                if (resolvedAuthorId.isBlank() && resolvedBookId.isBlank() &&
                    quoteText.isNotBlank() && (authorName.isNotBlank() || bookName.isNotBlank())) {
                    try {
                        val (aid, bid) = library.ensureAuthorAndBook(authorName, bookName)
                        resolvedAuthorId = aid
                        resolvedBookId   = bid
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
                    "libraryAuthorId" to resolvedAuthorId,
                    "libraryBookId"   to resolvedBookId,
                    // type: alıntılı post ise "library_quote", değilse boş string
                    "type"            to if (quoteText.isNotBlank() && type.isBlank()) "library_quote" else type,
                    "likes"           to 0, "saves" to 0, "cmtCount" to 0, "reposts" to 0,
                    "ts"              to Timestamp.now(),
                )).await()

                // Adım 4.3 — Kütüphane yazma hatası feed yazmasını geri almaz;
                // migrateLegacyFeedQuotes() ile sonradan kurtarılabilir.
                if (quoteText.isNotBlank() && resolvedBookId.isNotBlank()) {
                    try {
                        library.addQuoteToLibrary(
                            libraryBookId   = resolvedBookId,
                            libraryAuthorId = resolvedAuthorId,
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

    // Alıntı metni düzenleme — quoteText + isteğe bağlı bookName/authorName günceller
    fun editQuote(postId: String, newQuoteText: String, newBookName: String = "", newAuthorName: String = "") {
        if (uid.isEmpty() || newQuoteText.isBlank()) return
        viewModelScope.launch {
            try {
                val postDoc = firestore.collection("feed").document(postId).get().await()
                if (postDoc.getString("uid") != uid) return@launch
                val updates = mutableMapOf<String, Any>(
                    "quoteText" to newQuoteText.trim(),
                )
                if (newBookName.isNotBlank())   updates["bookName"]   = newBookName.trim()
                if (newAuthorName.isNotBlank()) updates["authorName"] = newAuthorName.trim()
                firestore.collection("feed").document(postId).update(updates).await()
                // Local state güncelle
                _posts.value = _posts.value.map {
                    if (it.id == postId) it.copy(
                        quoteText  = newQuoteText.trim(),
                        bookName   = newBookName.ifBlank { it.bookName },
                        authorName = newAuthorName.ifBlank { it.authorName },
                    ) else it
                }
                _libraryQuotes.value = _libraryQuotes.value.map {
                    if (it.id == postId) it.copy(
                        quoteText  = newQuoteText.trim(),
                        bookName   = newBookName.ifBlank { it.bookName },
                        authorName = newAuthorName.ifBlank { it.authorName },
                    ) else it
                }
                // library_books/{id}/quotes alt koleksiyonunu da güncelle
                val feedPostSnap = firestore.collection("feed").document(postId).get().await()
                val libBookId = feedPostSnap.getString("libraryBookId")
                if (!libBookId.isNullOrBlank()) {
                    try {
                        val quoteSnap = firestore.collection("library_books").document(libBookId)
                            .collection("quotes")
                            .whereEqualTo("feedPostId", postId)
                            .get().await()
                        quoteSnap.documents.forEach { doc ->
                            doc.reference.update("text", newQuoteText.trim())
                        }
                    } catch (_: Exception) {}
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    // ── Pagination ────────────────────────────────────────────────────────────
    fun loadSuggestedUsers() {
        val myUid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                // Takip ettiklerimi al
                // fromUid/targetUid — web teması ve diğer tüm ViewModel'larla tutarlı
                val followingSnap = firestore.collection("follows")
                    .whereEqualTo("fromUid", myUid)
                    .limit(500).get().await()
                val followingUids = followingSnap.documents
                    .mapNotNull { it.getString("targetUid") }
                    .toSet()

                // orderBy("postsCount") index gerektirir — limitsiz çekip client-side filtrele
                val usersSnap = firestore.collection("users")
                    .limit(50)
                    .get().await()

                val suggestions = usersSnap.documents
                    .mapNotNull { doc ->
                        val sUid = doc.getString("uid") ?: doc.id
                        if (sUid == myUid || sUid in followingUids) return@mapNotNull null
                        SuggestedUser(
                            uid      = sUid,
                            name     = doc.getString("displayName") ?: doc.getString("name") ?: "",
                            photoURL = doc.getString("photoURL") ?: "",
                            bio      = doc.getString("bio") ?: "",
                        )
                    }
                    .filter { it.name.isNotBlank() }
                    .shuffled()
                    .take(20)

                _suggestedUsers.value = suggestions
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun followSuggestedUser(targetUid: String) {
        val myUid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                val myDoc   = firestore.collection("users").document(myUid).get().await()
                val myName  = myDoc.getString("displayName") ?: myDoc.getString("name") ?: ""
                val myPhoto = myDoc.getString("photoURL") ?: ""
                // Web teması şeması: fromUid / targetUid / fromName / fromPhoto / targetUid / ts
                val tDoc    = firestore.collection("users").document(targetUid).get().await()
                val tName   = tDoc.getString("displayName") ?: tDoc.getString("name") ?: ""
                val tPhoto  = tDoc.getString("photoURL") ?: ""
                firestore.collection("follows").document("${myUid}_${targetUid}").set(mapOf(
                    "fromUid"     to myUid,
                    "fromName"    to myName,
                    "fromPhoto"   to myPhoto,
                    "targetUid"   to targetUid,
                    "targetName"  to tName,
                    "targetPhoto" to tPhoto,
                    "ts"          to Timestamp.now(),
                )).await()
                firestore.collection("users").document(myUid)
                    .update("followingCount", com.google.firebase.firestore.FieldValue.increment(1))
                firestore.collection("users").document(targetUid)
                    .update("followerCount", com.google.firebase.firestore.FieldValue.increment(1))
                // Bildirim gönder
                sendNotif(targetUid, "follow", "$myName sizi takip etmeye başladı", "", "")
                // State güncelle
                _suggestedUsers.value = _suggestedUsers.value.map {
                    if (it.uid == targetUid) it.copy(isFollowing = true) else it
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

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
                val filtered = rawPosts.filter { it.moderationStatus != "removed" }
                _posts.value = mapInteractions(filtered)
                enrichPostsInBackground(filtered)
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
                val filtered = rawMore.filter { it.moderationStatus != "removed" }
                _posts.value = _posts.value + mapInteractions(filtered)
                enrichPostsInBackground(filtered)
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
                    val mapped = mapInteractions(listOf(post))
                    _posts.value = _posts.value + mapped
                    enrichPostsInBackground(mapped)
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
            // 1. Firestore bildirim kaydı
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
            // 2. FCM push bildirimi — alıcının fcmToken'ını al ve push gönder
            try {
                val toUserDoc = firestore.collection("users").document(toUid).get().await()
                val fcmToken  = toUserDoc.getString("fcmToken") ?: ""
                if (fcmToken.isNotBlank()) {
                    com.google.firebase.functions.FirebaseFunctions.getInstance()
                        .getHttpsCallable("sendPush")
                        .call(mapOf(
                            "token"  to fcmToken,
                            "title"  to fromName,
                            "body"   to title,
                            "url"    to if (feedId.isNotBlank()) "post/$feedId" else "",
                            "toUid"  to toUid,
                        )).await()
                }
            } catch (_: Exception) {} // push başarısız olsa da Firestore kaydı yapıldı
        } catch (e: Exception) { e.printStackTrace() }
    }

    // ── Kütüphane alıntı postları ─────────────────────────────────────────
    // Feed'den bookName dolu postları çeker; LibraryScreen PostCard ile gösterir.
    // suspend versiyonu — LibraryScreen LaunchedEffect join() ile bekleyebilir
    suspend fun loadLibraryQuotesAsync() {
        val myUid   = auth.currentUser?.uid ?: ""
        val isAdmin = auth.currentUser?.email == "siirgibi49@gmail.com"
        val seenIds = mutableSetOf<String>()
        val result  = mutableListOf<Post>()

        // ── feed.whereEqualTo("type","library_quote")
        //    allow list: if isAuth() — rules güncellendi, query artık çalışır
        try {
            val snap = firestore.collection("feed")
                .whereEqualTo("type", "library_quote")
                .get().await()

            snap.documents.forEach { doc ->
                val d   = doc.data ?: return@forEach
                val vis = d["visibility"] as? String ?: "public"

                // visibility filtresi client-side
                val canSee = when (vis) {
                    "only_me"  -> d["uid"] == myUid || isAdmin
                    "friends"  -> {
                        val ownerUid = d["uid"] as? String ?: ""
                        ownerUid == myUid || isAdmin ||
                        runCatching {
                            firestore.collection("follows")
                                .document("${myUid}_${ownerUid}").get().await().exists()
                        }.getOrDefault(false)
                    }
                    else -> true  // public
                }

                if (canSee && seenIds.add(doc.id)) {
                    doc.toPost()?.let { result.add(it) }
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("FeedVM", "loadLibraryQuotes feed: ${e.message}")
        }

        // ── library_books/quotes collectionGroup (fallback + ek kayıtlar)
        try {
            val cgSnap = firestore.collectionGroup("quotes").get().await()
            cgSnap.documents.forEach { doc ->
                val d    = doc.data ?: return@forEach
                val text = (d["text"] as? String)?.takeIf { it.isNotBlank() } ?: return@forEach
                val feedPostId = d["feedPostId"] as? String
                if (feedPostId != null && seenIds.contains(feedPostId)) return@forEach
                if (seenIds.add("cg_${doc.id}")) {
                    result.add(Post(
                        id            = doc.id,
                        uid           = d["uid"] as? String ?: "",
                        displayName   = d["userDisplayName"] as? String ?: d["displayName"] as? String ?: "",
                        name          = d["userDisplayName"] as? String ?: "",
                        photoURL      = d["userPhotoURL"] as? String ?: "",
                        quoteText     = text,
                        bookName      = d["bookTitle"] as? String ?: d["bookName"] as? String ?: "",
                        authorName    = d["authorName"] as? String ?: "",
                        libraryBookId = d["bookId"] as? String ?: "",
                        ts            = d["ts"] as? com.google.firebase.Timestamp,
                        type          = "library_quote",
                        visibility    = d["visibility"] as? String ?: "public",
                    ))
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("FeedVM", "collectionGroup: ${e.message}")
        }

        // ── Eski postlar fallback — type alanı yok ama bookName dolu ──
        // Firestore'da type="library_quote" olmayan eski paylaşımları da yakala
        try {
            val oldSnap = firestore.collection("feed")
                .orderBy("ts", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(300).get().await()
            oldSnap.documents.forEach { doc ->
                if (seenIds.contains(doc.id)) return@forEach
                val d = doc.data ?: return@forEach
                val bookName = (d["bookName"] as? String)?.takeIf { it.isNotBlank() }
                    ?: ((d["quote"] as? Map<*,*>)?.get("book") as? String)?.takeIf { it.isNotBlank() }
                    ?: return@forEach
                val quoteText = (d["quoteText"] as? String)?.takeIf { it.isNotBlank() }
                    ?: ((d["quote"] as? Map<*,*>)?.get("text") as? String)?.takeIf { it.isNotBlank() }
                    ?: return@forEach
                if (seenIds.add(doc.id)) {
                    doc.toPost()?.let { result.add(it) }
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("FeedVM", "loadLibraryQuotes oldPosts: ${e.message}")
        }

        _libraryQuotes.value = result.sortedByDescending { it.ts?.seconds ?: 0L }
    }

    fun loadLibraryQuotes() {
        viewModelScope.launch { loadLibraryQuotesAsync() }
    }
}
