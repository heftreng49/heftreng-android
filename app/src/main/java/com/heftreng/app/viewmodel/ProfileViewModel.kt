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
import com.heftreng.app.data.model.FollowRow
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import com.heftreng.app.util.CacheEntry
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val auth     : FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val supabase : SupabaseClient,
    private val library  : com.heftreng.app.data.repository.LibraryRepository,
) : ViewModel() {

    private val _user = MutableStateFlow<User?>(null)
    val user = _user.asStateFlow()

    // Rozetler — Öncelik 4 (Supabase user_badges, katalog: BadgeCatalog)
    private val _badgeIds = MutableStateFlow<Set<String>>(emptySet())
    val badgeIds = _badgeIds.asStateFlow()

    private val _posts = MutableStateFlow<List<Post>>(emptyList())
    val posts = _posts.asStateFlow()

    private val _isFollowing = MutableStateFlow(false)
    val isFollowing = _isFollowing.asStateFlow()

    // "none" | "pending" | "accepted"
    private val _followRequestStatus = MutableStateFlow("none")
    val followRequestStatus = _followRequestStatus.asStateFlow()

    private val _userNotFound = MutableStateFlow(false)
    val userNotFound = _userNotFound.asStateFlow()

    private val _followersCount = MutableStateFlow(0)
    val followersCount = _followersCount.asStateFlow()

    private val _followingCount = MutableStateFlow(0)
    val followingCount = _followingCount.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading = _loading.asStateFlow()
    // Profil cache: 3 dk TTL (aynı profili tekrar açınca beklemeden göster)
    private val profileCache = mutableMapOf<String, CacheEntry<Unit>>()

    private val _hasMorePosts = MutableStateFlow(false)
    val hasMorePosts = _hasMorePosts.asStateFlow()

    // ── İlerleme kartı detayları: "X alıntı" tıklanınca açılan liste ─────────
    private val _userQuotes = MutableStateFlow<List<com.heftreng.app.data.repository.BookQuoteRow>>(emptyList())
    val userQuotes = _userQuotes.asStateFlow()

    private val _userQuotesLoading = MutableStateFlow(false)
    val userQuotesLoading = _userQuotesLoading.asStateFlow()

    fun loadUserQuotes(targetUid: String) {
        viewModelScope.launch {
            _userQuotesLoading.value = true
            try {
                _userQuotes.value = library.getQuotesByUser(targetUid)
            } catch (e: Exception) { e.printStackTrace() }
            finally { _userQuotesLoading.value = false }
        }
    }

    private val _loadingMore = MutableStateFlow(false)
    val loadingMorePosts = _loadingMore.asStateFlow()

    private var lastPostDoc  : com.google.firebase.firestore.DocumentSnapshot? = null
    private var lastLoadedUid: String = ""
    private val POST_PAGE    = 20L

    private val _savedPosts = MutableStateFlow<List<Post>>(emptyList())
    val savedPosts = _savedPosts.asStateFlow()

    private val _savedLoading = MutableStateFlow(false)
    val savedLoading = _savedLoading.asStateFlow()

    val myUid get() = auth.currentUser?.uid ?: ""

    fun load(uid: String, preloadedUser: User? = null, forceRefresh: Boolean = false) {
        val targetUid = if (uid == "me") myUid else uid

        // ── 1. Eğer önceki ekrandan User verisi geldiyse anında göster ──────
        if (preloadedUser != null && _user.value == null) {
            _user.value = preloadedUser
        }

        // ── TTL Cache: 3 dk içinde aynı profili tekrar yükleme ──────────────
        if (!forceRefresh) {
            val entry = profileCache[targetUid]
            if (entry?.isValid() == true && _user.value != null) return
        } else {
            profileCache[targetUid]?.invalidate()
        }

        viewModelScope.launch {
            // Loading'i sadece liste boşsa göster — ikinci açılışta çark gözükmez
            _loading.value = _posts.value.isEmpty()
            try {
                // ── 2. Paralel fetch: user + follow durumu aynı anda ──────────
                val userDocDeferred = viewModelScope.async {
                    firestore.collection("users").document(targetUid).get().await()
                }
                val followDocDeferred = viewModelScope.async {
                    if (targetUid != myUid && myUid.isNotEmpty()) {
                        try {
                            val rows = supabase.postgrest["follows"].select {
                                filter { eq("id", "${myUid}_$targetUid") }
                                limit(1)
                            }.decodeList<FollowRow>()
                            rows.isNotEmpty()
                        } catch (_: Exception) { false }
                    } else false
                }
                val followRequestDeferred = viewModelScope.async {
                    if (targetUid != myUid && myUid.isNotEmpty())
                        firestore.collection("followRequests").document(targetUid)
                            .collection("pending").document(myUid).get().await()
                    else null
                }
                val userDoc           = userDocDeferred.await()
                val isFollowingResult = followDocDeferred.await()
                val followRequestDoc  = followRequestDeferred.await()

                val d = userDoc.data ?: run {
                    _userNotFound.value = true
                    _loading.value = false
                    return@launch
                }
                _user.value = User(
                    uid         = d["uid"] as? String ?: targetUid,
                    displayName = d["displayName"] as? String ?: d["name"] as? String ?: "",
                    name        = d["name"] as? String ?: "",
                    username    = d["username"] as? String ?: (d["email"] as? String)?.substringBefore("@") ?: "",
                    email       = d["email"] as? String ?: "",
                    photoURL    = d["photoURL"] as? String ?: "",
                    coverPhoto  = d["coverPhoto"] as? String ?: "",
                    bio         = d["bio"] as? String ?: "",
                    website     = d["website"] as? String ?: "",
                    level          = (d["level"] as? Long)?.toInt() ?: 1,
                    xp             = (d["xp"] as? Long)?.toInt() ?: 0,
                    streak         = (d["streak"] as? Long)?.toInt() ?: 0,
                    followersCount = (d["followersCount"] as? Long)?.toInt() ?: 0,
                    followingCount = (d["followingCount"] as? Long)?.toInt() ?: 0,
                    isPrivate          = d["private"]           as? Boolean ?: false,
                    messagePermission  = d["messagePermission"] as? String  ?: "everyone",
                )

                _isFollowing.value = isFollowingResult

                // Takip isteği durumu
                _followRequestStatus.value = when {
                    _isFollowing.value                       -> "accepted"
                    followRequestDoc?.exists() == true       -> "pending"
                    else                                     -> "none"
                }

                // followersCount / followingCount — Supabase follows tablosundan gerçek sayı çek.
                // Firestore'daki followersCount alanı stale olabilir (eski takip sisteminden kalma).
                viewModelScope.launch {
                    try {
                        val followersRows = supabase.postgrest["follows"].select {
                            filter { eq("target_uid", targetUid) }
                        }.decodeList<FollowRow>()
                        _followersCount.value = followersRows.size

                        val followingRows = supabase.postgrest["follows"].select {
                            filter { eq("from_uid", targetUid) }
                        }.decodeList<FollowRow>()
                        _followingCount.value = followingRows.size

                        // Firestore'u da güncelle — bir sonraki açılışta stale olmasın
                        if (_followersCount.value != (_user.value?.followersCount ?: -1) ||
                            _followingCount.value != (_user.value?.followingCount ?: -1)) {
                            firestore.collection("users").document(targetUid).update(
                                mapOf(
                                    "followersCount" to _followersCount.value,
                                    "followingCount" to _followingCount.value,
                                )
                            ).await()
                        }
                    } catch (e: Exception) {
                        // Supabase başarısız → Firestore değerlerini kullan
                        _followersCount.value = _user.value?.followersCount ?: 0
                        _followingCount.value = _user.value?.followingCount ?: 0
                    }
                }

                val isOwnProfile  = (targetUid == myUid)
                val isPrivate     = _user.value?.isPrivate ?: false
                val canSeeContent = isOwnProfile || !isPrivate || _isFollowing.value

                if (!canSeeContent) { _posts.value = emptyList(); return@launch }

                // ── 3. Gönderileri çek — her post için ayrı likeDoc await() YOK ──
                if (lastLoadedUid != targetUid) {
                    lastPostDoc   = null
                    lastLoadedUid = targetUid
                    _posts.value  = emptyList()
                }
                val snap = firestore.collection("feed")
                    .whereEqualTo("uid", targetUid)
                    .orderBy("ts", com.google.firebase.firestore.Query.Direction.DESCENDING)
                    .limit(POST_PAGE).get().await()
                if (snap.documents.isNotEmpty()) lastPostDoc = snap.documents.last()
                _hasMorePosts.value = snap.documents.size >= POST_PAGE.toInt()

                // Beğenilen ID'leri tek toplu sorguyla al
                val postIds = snap.documents.map { it.id }
                val likedIds = if (myUid.isNotEmpty() && postIds.isNotEmpty()) {
                    try {
                        supabase.postgrest["feed_likes"].select {
                            filter { eq("uid", myUid); isIn("post_id", postIds) }
                        }.decodeList<com.heftreng.app.data.model.FeedLikeRow>()
                            .mapNotNull { it.postId.takeIf { id -> id.isNotBlank() } }.toSet()
                    } catch (_: Exception) { emptySet() }
                } else emptySet()

                val rawPosts = snap.documents.mapNotNull { doc ->
                    val fd = doc.data ?: return@mapNotNull null
                    val postText = fd["text"] as? String ?: ""
                    val imageURL = fd["imageURL"] as? String ?: fd["imgUrl"] as? String ?: ""
                    val quoteObj = fd["quote"] as? Map<*, *>
                    val quoteText  = (quoteObj?.get("text")   as? String)?.takeIf { it.isNotBlank() } ?: fd["quoteText"]  as? String ?: ""
                    val bookName   = (quoteObj?.get("book")   as? String)?.takeIf { it.isNotBlank() } ?: fd["bookName"]   as? String ?: ""
                    val authorName = (quoteObj?.get("author") as? String)?.takeIf { it.isNotBlank() } ?: fd["authorName"] as? String ?: ""
                    val repostOf   = fd["repostOf"] as? String ?: fd["repostType"] as? String ?: ""
                    if (postText.isBlank() && imageURL.isBlank() && quoteText.isBlank() && repostOf.isBlank()) return@mapNotNull null

                    @Suppress("UNCHECKED_CAST")
                    val badges = (fd["badges"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                    Post(
                        id            = doc.id,
                        uid           = fd["uid"]         as? String ?: "",
                        displayName   = (fd["displayName"] as? String)?.takeIf { it.isNotBlank() } ?: fd["name"] as? String ?: "",
                        name          = fd["name"]        as? String ?: "",
                        username      = fd["username"]    as? String ?: "",
                        photoURL      = fd["photoURL"]    as? String ?: "",
                        text          = postText,
                        imageURL      = imageURL,
                        ytVid         = fd["ytVid"]        as? String ?: "",
                        badges        = badges,
                        quoteText     = quoteText,
                        bookName      = bookName,
                        authorName    = authorName,
                        repostOf            = repostOf,
                        repostUid           = fd["repostUid"]   as? String ?: "",
                        repostType          = fd["repostType"]  as? String ?: "",
                        repostId            = fd["repostId"]    as? String ?: "",
                        repostTitle         = fd["repostTitle"] as? String ?: "",
                        repostUrl           = fd["repostUrl"]   as? String ?: "",
                        repostImg           = fd["repostImg"]   as? String ?: "",
                        repostText          = fd["repostText"]        as? String ?: "",
                        repostAuthor        = fd["repostAuthor"]      as? String ?: "",
                        repostAuthorPhoto   = fd["repostAuthorPhoto"] as? String ?: "",
                        repostAuthorUid     = fd["repostAuthorUid"]   as? String ?: "",
                        repostSerialId         = fd["repostSerialId"]         as? String ?: "",
                        repostSerialTitle      = fd["repostSerialTitle"]      as? String ?: "",
                        repostSerialDesc       = fd["repostSerialDesc"]       as? String ?: "",
                        repostSerialCover      = fd["repostSerialCover"]      as? String ?: "",
                        repostSerialAuthorName = fd["repostSerialAuthorName"] as? String ?: "",
                        repostSerialAuthorUid  = fd["repostSerialAuthorUid"]  as? String ?: "",
                        repostSerialBg         = fd["repostSerialBg"]         as? String ?: "",
                        repostSerialChCount    = (fd["repostSerialChCount"]   as? Long)?.toInt() ?: 0,
                        serialId      = fd["serialId"]      as? String ?: "",
                        serialTitle   = fd["serialTitle"]   as? String ?: "",
                        serialCover   = fd["serialCover"]   as? String ?: fd["serialBg"] as? String ?: "",
                        chapterId     = fd["chapterId"]     as? String ?: "",
                        chapterTitle  = fd["chapterTitle"]  as? String ?: "",
                        chapterOrder  = (fd["chapterOrder"] as? Long)?.toInt() ?: 0,
                        likesCount      = (fd["likesCount"]    as? Long)?.toInt() ?: 0,
                        commentsCount   = (fd["commentsCount"] as? Long)?.toInt() ?: 0,
                        repostsCount    = (fd["reposts"]  as? Long)?.toInt() ?: 0,
                        isLikedByMe     = doc.id in likedIds,
                        ts              = fd["ts"] as? Timestamp,
                        coverImg        = fd["coverImg"]        as? String ?: "",
                        libraryBookId   = fd["libraryBookId"]   as? String ?: "",
                        libraryAuthorId = fd["libraryAuthorId"] as? String ?: "",
                        type            = fd["type"]            as? String ?: "",
                    )
                }.sortedByDescending { it.ts?.seconds ?: 0L }

                // ── 4. Denormalize veriyle anında ekrana bas ──────────────────
                _posts.value = rawPosts
                _loading.value = false
                syncProfilePostCounts(rawPosts.map { it.id })
                syncReadingSummary(targetUid)

                // ── 5. Arka planda güncel avatar/isim ile sessizce güncelle ──
                enrichPostsInBackground(rawPosts)

                // Cache'e yaz
                profileCache.getOrPut(targetUid) {
                    CacheEntry(ttlMs = 3 * 60_000L)
                }.set(Unit)

            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _loading.value = false
            }
        }
    }

    private fun enrichPostsInBackground(
        posts: List<Post>,
        target: MutableStateFlow<List<Post>> = _posts,
    ) {
        if (posts.isEmpty()) return
        viewModelScope.launch {
            val uids = posts.map { it.uid }.filter { it.isNotBlank() }.distinct()
            val userMap = mutableMapOf<String, Pair<String, String>>()
            uids.chunked(10).forEach { chunk ->
                try {
                    val snap = firestore.collection("users")
                        .whereIn(com.google.firebase.firestore.FieldPath.documentId(), chunk)
                        .get().await()
                    snap.documents.forEach { doc ->
                        val name  = (doc.getString("displayName") ?: doc.getString("name") ?: "").takeIf { it.isNotBlank() } ?: ""
                        val photo = doc.getString("photoURL") ?: ""
                        userMap[doc.id] = name to photo
                    }
                } catch (_: Exception) {}
            }
            if (userMap.isNotEmpty()) {
                target.value = target.value.map { post ->
                    val (freshName, freshPhoto) = userMap[post.uid] ?: return@map post
                    post.copy(
                        displayName = freshName.ifBlank { post.displayName },
                        photoURL    = freshPhoto.ifBlank { post.photoURL },
                    )
                }
            }
            // coverImg boş alıntı postları için Supabase'den kapak URL'ini çek
            val needsCover = posts.filter { it.coverImg.isBlank() && it.bookName.isNotBlank() }
            if (needsCover.isNotEmpty()) {
                val coverMap = mutableMapOf<String, String>()
                needsCover.map { it.bookName }.distinct().forEach { title ->
                    try {
                        val url = library.searchBooks(title)
                            .firstOrNull { it.title.equals(title.trim(), ignoreCase = true) }
                            ?.coverImg
                            ?: library.searchBooks(title).firstOrNull()?.coverImg
                            ?: ""
                        if (url.isNotBlank()) coverMap[title] = url
                    } catch (_: Exception) {}
                }
                if (coverMap.isNotEmpty()) {
                    target.value = target.value.map { post ->
                        if (post.coverImg.isBlank() && post.bookName.isNotBlank()) {
                            val url = coverMap[post.bookName] ?: return@map post
                            post.copy(coverImg = url)
                        } else post
                    }
                }
            }
        }
    }

    // ── Ana takip butonu — gizli hesap kontrolü yapar ────────────────────────
    fun toggleFollow(targetUid: String) {
        val isPrivate = _user.value?.isPrivate ?: false
        when {
            _isFollowing.value                      -> unfollowUser(targetUid)
            _followRequestStatus.value == "pending" -> cancelFollowRequest(targetUid)
            isPrivate                               -> sendFollowRequest(targetUid)
            else                                    -> followUserDirectly(targetUid)
        }
    }

    // ── Takipten çık ─────────────────────────────────────────────────────────
    private fun unfollowUser(targetUid: String) {
        viewModelScope.launch {
            try {
                supabase.postgrest["follows"].delete {
                    filter { eq("id", "${myUid}_$targetUid") }
                }
                _isFollowing.value = false
                _followRequestStatus.value = "none"
                _followersCount.value = (_followersCount.value - 1).coerceAtLeast(0)
                firestore.collection("users").document(targetUid)
                    .update("followersCount", FieldValue.increment(-1))
                firestore.collection("users").document(myUid)
                    .update("followingCount", FieldValue.increment(-1))
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    // ── Gizli hesaba takip isteği gönder ─────────────────────────────────────
    private fun sendFollowRequest(targetUid: String) {
        viewModelScope.launch {
            try {
                val myDoc     = firestore.collection("users").document(myUid).get().await()
                val fromName  = myDoc.getString("displayName") ?: myDoc.getString("name") ?: ""
                val fromPhoto = myDoc.getString("photoURL") ?: ""

                // followRequests/{targetUid}/pending/{fromUid}
                firestore.collection("followRequests").document(targetUid)
                    .collection("pending").document(myUid).set(mapOf(
                        "fromUid"   to myUid,
                        "fromName"  to fromName,
                        "fromPhoto" to fromPhoto,
                        "targetUid" to targetUid,
                        "ts"        to Timestamp.now(),
                    )).await()

                _followRequestStatus.value = "pending"

                // Bildirim
                firestore.collection("userNotifs").document(targetUid).collection("msgs").add(mapOf(
                    "fromUid"   to myUid,
                    "fromName"  to fromName,
                    "fromPhoto" to fromPhoto,
                    "type"      to "follow_request",
                    "feedId"    to "",
                    "postId"    to "",
                    "title"     to "$fromName seni takip etmek istiyor",
                    "sub"       to "",
                    "ico"       to "person_add",
                    "message"   to "$fromName seni takip etmek istiyor",
                    "url"       to "",
                    "read"      to false,
                    "ts"        to Timestamp.now(),
                )).await()
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    // ── Bekleyen isteği iptal et ──────────────────────────────────────────────
    private fun cancelFollowRequest(targetUid: String) {
        viewModelScope.launch {
            try {
                firestore.collection("followRequests").document(targetUid)
                    .collection("pending").document(myUid).delete().await()
                _followRequestStatus.value = "none"
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    // ── Açık hesaba direkt takip ──────────────────────────────────────────────
    private fun followUserDirectly(targetUid: String) {
        viewModelScope.launch {
            try {
                val myDoc       = firestore.collection("users").document(myUid).get().await()
                val fromName    = myDoc.getString("displayName") ?: myDoc.getString("name") ?: ""
                val fromPhoto   = myDoc.getString("photoURL") ?: ""
                val targetDoc   = firestore.collection("users").document(targetUid).get().await()
                val targetName  = targetDoc.getString("displayName") ?: targetDoc.getString("name") ?: ""
                val targetPhoto = targetDoc.getString("photoURL") ?: ""

                supabase.postgrest["follows"].upsert(
                    FollowRow(
                        id          = "${myUid}_$targetUid",
                        fromUid     = myUid,
                        fromName    = fromName,
                        fromPhoto   = fromPhoto,
                        targetUid   = targetUid,
                        targetName  = targetName,
                        targetPhoto = targetPhoto,
                    )
                )

                _isFollowing.value = true
                _followRequestStatus.value = "accepted"
                _followersCount.value += 1
                firestore.collection("users").document(targetUid)
                    .update("followersCount", FieldValue.increment(1))
                firestore.collection("users").document(myUid)
                    .update("followingCount", FieldValue.increment(1))

                // Bildirim
                firestore.collection("userNotifs").document(targetUid).collection("msgs").add(mapOf(
                    "fromUid"   to myUid,
                    "fromName"  to fromName,
                    "fromPhoto" to fromPhoto,
                    "type"      to "follow",
                    "feedId"    to "",
                    "postId"    to "",
                    "title"     to "$fromName seni takip etmeye başladı",
                    "sub"       to "",
                    "ico"       to "person_add",
                    "message"   to "$fromName seni takip etmeye başladı",
                    "url"       to "",
                    "read"      to false,
                    "ts"        to Timestamp.now(),
                )).await()
            } catch (e: Exception) { e.printStackTrace() }
        }
    }


    fun loadMorePosts(targetUid: String) {
        val last = lastPostDoc ?: return
        if (_loadingMore.value || !_hasMorePosts.value) return
        viewModelScope.launch {
            _loadingMore.value = true
            try {
                val snap = firestore.collection("feed")
                    .whereEqualTo("uid", targetUid)
                    .orderBy("ts", com.google.firebase.firestore.Query.Direction.DESCENDING)
                    .startAfter(last)
                    .limit(POST_PAGE).get().await()
                if (snap.documents.isNotEmpty()) lastPostDoc = snap.documents.last()
                _hasMorePosts.value = snap.documents.size >= POST_PAGE.toInt()
                val morePostIds = snap.documents.map { it.id }
                val moreLikedIds = if (myUid.isNotEmpty() && morePostIds.isNotEmpty()) {
                    try {
                        supabase.postgrest["feed_likes"].select {
                            filter { eq("uid", myUid); isIn("post_id", morePostIds) }
                        }.decodeList<com.heftreng.app.data.model.FeedLikeRow>()
                            .mapNotNull { it.postId.takeIf { id -> id.isNotBlank() } }.toSet()
                    } catch (_: Exception) { emptySet() }
                } else emptySet()
                // Yeni postları mevcut listeye ekle (tam parse için mevcut rawPost mantığını tekrar ederiz)
                val newPosts = snap.documents.mapNotNull { doc ->
                    val fd = doc.data ?: return@mapNotNull null
                    val quoteObj   = fd["quote"] as? Map<*, *>
                    val quoteText  = (quoteObj?.get("text")   as? String)?.takeIf { it.isNotBlank() } ?: fd["quoteText"]  as? String ?: ""
                    val bookName   = (quoteObj?.get("book")   as? String)?.takeIf { it.isNotBlank() } ?: fd["bookName"]   as? String ?: ""
                    val authorName = (quoteObj?.get("author") as? String)?.takeIf { it.isNotBlank() } ?: fd["authorName"] as? String ?: ""
                    val repostOf   = fd["repostOf"] as? String ?: fd["repostType"] as? String ?: ""
                    @Suppress("UNCHECKED_CAST")
                    val badges = (fd["badges"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                    com.heftreng.app.data.model.Post(
                        id          = doc.id,
                        uid         = fd["uid"]         as? String ?: "",
                        displayName = fd["displayName"] as? String ?: fd["name"] as? String ?: "",
                        name        = fd["name"]        as? String ?: "",
                        username    = fd["username"]    as? String ?: "",
                        photoURL    = fd["photoURL"]    as? String ?: "",
                        text        = fd["text"]        as? String ?: "",
                        title       = fd["title"]        as? String ?: "",
                        imageURL    = fd["imageURL"]    as? String ?: fd["imgUrl"] as? String ?: "",
                        ytVid       = fd["ytVid"]        as? String ?: "",
                        badges      = badges,
                        quoteText   = quoteText,
                        bookName    = bookName,
                        authorName  = authorName,
                        repostOf            = repostOf,
                        repostUid           = fd["repostUid"]   as? String ?: "",
                        repostType          = fd["repostType"]  as? String ?: "",
                        repostId            = fd["repostId"]    as? String ?: "",
                        repostTitle         = fd["repostTitle"] as? String ?: "",
                        repostUrl           = fd["repostUrl"]   as? String ?: "",
                        repostImg           = fd["repostImg"]   as? String ?: "",
                        repostText          = fd["repostText"]        as? String ?: "",
                        repostAuthor        = fd["repostAuthor"]      as? String ?: "",
                        repostAuthorPhoto   = fd["repostAuthorPhoto"] as? String ?: "",
                        repostAuthorUid     = fd["repostAuthorUid"]   as? String ?: "",
                        repostSerialId         = fd["repostSerialId"]         as? String ?: "",
                        repostSerialTitle      = fd["repostSerialTitle"]      as? String ?: "",
                        repostSerialDesc       = fd["repostSerialDesc"]       as? String ?: "",
                        repostSerialCover      = fd["repostSerialCover"]      as? String ?: "",
                        repostSerialAuthorName = fd["repostSerialAuthorName"] as? String ?: "",
                        repostSerialAuthorUid  = fd["repostSerialAuthorUid"]  as? String ?: "",
                        repostSerialBg         = fd["repostSerialBg"]         as? String ?: "",
                        repostSerialChCount    = (fd["repostSerialChCount"]   as? Long)?.toInt() ?: 0,
                        serialId      = fd["serialId"]      as? String ?: "",
                        serialTitle   = fd["serialTitle"]   as? String ?: "",
                        serialCover   = fd["serialCover"]   as? String ?: fd["serialBg"] as? String ?: "",
                        chapterId     = fd["chapterId"]     as? String ?: "",
                        chapterTitle  = fd["chapterTitle"]  as? String ?: "",
                        chapterOrder  = (fd["chapterOrder"] as? Long)?.toInt() ?: 0,
                        likesCount    = (fd["likesCount"]    as? Long)?.toInt() ?: 0,
                        commentsCount = (fd["commentsCount"] as? Long)?.toInt() ?: 0,
                        repostsCount  = (fd["reposts"]  as? Long)?.toInt() ?: 0,
                        isLikedByMe   = doc.id in moreLikedIds,
                        ts          = fd["ts"]          as? com.google.firebase.Timestamp,
                    )
                }
                _posts.value = _posts.value + newPosts
                syncProfilePostCounts(newPosts.map { it.id })
            } catch (e: Exception) { e.printStackTrace() }
            finally { _loadingMore.value = false }
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

                // Supabase users tablosunu da güncelle — öneri listesi buradan besleniyor.
                try {
                    supabase.postgrest["users"].upsert(
                        mapOf(
                            "uid"          to myUid,
                            "display_name" to displayName,
                            "bio"          to bio,
                        )
                    )
                } catch (e: Exception) {
                    android.util.Log.w("ProfileVM", "Supabase users sync failed: ${e.message}")
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    /** Profil "okuma özet kartı" — booksRead (Supabase reading_status, status='okudum')
     *  ve quotesShared (Supabase book_quotes uid sayısı) gerçek verilerle doldurulur. */
    private fun syncReadingSummary(targetUid: String) {
        viewModelScope.launch {
            try {
                val completedBooks = try {
                    supabase.postgrest["reading_status"]
                        .select { filter { eq("uid", targetUid); eq("status", "okudum") } }
                        .decodeList<com.heftreng.app.data.repository.ReadingStatusRow>().size
                } catch (e: Exception) { 0 }

                val quotesCount = try {
                    supabase.postgrest["book_quotes"]
                        .select { filter { eq("uid", targetUid) } }
                        .decodeList<com.heftreng.app.data.repository.BookQuoteRow>().size
                } catch (e: Exception) { 0 }

                _user.value = _user.value?.copy(
                    booksRead    = completedBooks,
                    quotesShared = quotesCount,
                )

                _badgeIds.value = library.getUserBadgeIds(targetUid)
            } catch (e: Exception) { e.printStackTrace() }
        }
    }
    /** Beğeni / yorum sayılarını Supabase'den çek (feed.likesCount/commentsCount artık
     *  Firestore'da güncellenmiyor — Supabase tek kaynak). */
    private fun syncProfilePostCounts(postIds: List<String>) {
        val ids = postIds.filter { it.isNotBlank() }.distinct()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            try {
                val likeRows = supabase.postgrest["feed_likes"]
                    .select { filter { isIn("post_id", ids) } }
                    .decodeList<com.heftreng.app.data.model.FeedLikeRow>()
                val commentRows = supabase.postgrest["feed_comments"]
                    .select { filter { isIn("post_id", ids) } }
                    .decodeList<com.heftreng.app.data.model.FeedCommentRow>()

                val likeCounts    = likeRows.groupingBy { it.postId }.eachCount()
                val commentCounts = commentRows.groupingBy { it.postId }.eachCount()
                if (likeCounts.isEmpty() && commentCounts.isEmpty()) return@launch

                _posts.value = _posts.value.map { post ->
                    if (post.id !in ids) return@map post
                    post.copy(
                        likesCount    = likeCounts[post.id]    ?: post.likesCount,
                        commentsCount = commentCounts[post.id] ?: post.commentsCount,
                    )
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    // ── Profil gönderilerinde beğeni / silme / düzenleme ─────────────────────
    private var _cachedMyName2  : String = ""
    private var _cachedMyPhoto2 : String = ""

    fun toggleLikePost(post: com.heftreng.app.data.model.Post) {
        if (myUid.isEmpty()) return
        val nowLiked = !post.isLikedByMe
        _posts.value = _posts.value.map {
            if (it.id == post.id) it.copy(
                isLikedByMe = nowLiked,
                likesCount  = maxOf(0, it.likesCount + if (nowLiked) 1 else -1)
            ) else it
        }
        viewModelScope.launch {
            try {
                if (nowLiked) {
                    if (_cachedMyName2.isBlank()) {
                        val d = firestore.collection("users").document(myUid).get().await().data ?: emptyMap()
                        _cachedMyName2  = d["displayName"] as? String ?: d["name"] as? String
                            ?: auth.currentUser?.displayName ?: "Kullanıcı"
                        _cachedMyPhoto2 = d["photoURL"] as? String ?: ""
                    }
                    val myName  = _cachedMyName2
                    val myPhoto = _cachedMyPhoto2

                    val existing = try {
                        supabase.postgrest["feed_likes"]
                            .select { filter { eq("post_id", post.id); eq("uid", myUid) }; limit(1) }
                            .decodeList<com.heftreng.app.data.model.FeedLikeRow>()
                    } catch (_: Exception) { emptyList() }

                    if (existing.isEmpty()) {
                        supabase.postgrest["feed_likes"].insert(
                            mapOf(
                                "id"        to "${post.id}_$myUid",
                                "post_id"   to post.id,
                                "uid"       to myUid,
                                "name"      to myName,
                                "photo_url" to myPhoto,
                            )
                        )
                        if (post.uid.isNotEmpty() && post.uid != myUid) {
                            firestore.collection("userNotifs").document(post.uid).collection("msgs").add(mapOf(
                                "fromUid" to myUid, "fromName" to myName, "fromPhoto" to myPhoto,
                                "type" to "like", "feedId" to post.id, "postId" to post.id,
                                "title" to "$myName gönderini beğendi", "sub" to post.text.take(60),
                                "ico" to "favorite",
                                "ts" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                            )).await()
                        }
                    }
                } else {
                    supabase.postgrest["feed_likes"].delete {
                        filter { eq("post_id", post.id); eq("uid", myUid) }
                    }
                }
                // Gerçek sayıyı arka planda doğrula
                val realCount = try {
                    supabase.postgrest["feed_likes"]
                        .select { filter { eq("post_id", post.id) } }
                        .decodeList<com.heftreng.app.data.model.FeedLikeRow>().size
                } catch (_: Exception) { -1 }
                if (realCount >= 0) {
                    _posts.value = _posts.value.map {
                        if (it.id == post.id) it.copy(likesCount = realCount) else it
                    }
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun deleteOwnPost(postId: String) {
        if (myUid.isEmpty()) return
        _posts.value = _posts.value.filter { it.id != postId }
        viewModelScope.launch {
            try {
                // Güvenlik: post sahibi mi kontrol et
                val postDoc = firestore.collection("feed").document(postId).get().await()
                if (postDoc.getString("uid") != myUid) return@launch
                firestore.collection("feed").document(postId).delete().await()
            }
            catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun editOwnPost(postId: String, newTitle: String = "", newText: String) {
        if (myUid.isEmpty() || newText.isBlank()) return
        viewModelScope.launch {
            try {
                // Güvenlik: post sahibi mi kontrol et
                val postDoc = firestore.collection("feed").document(postId).get().await()
                if (postDoc.getString("uid") != myUid) return@launch
                firestore.collection("feed").document(postId)
                    .update(mapOf("text" to newText, "title" to newTitle.trim())).await()
                _posts.value = _posts.value.map { if (it.id == postId) it.copy(text = newText, title = newTitle.trim()) else it }
            }
            catch (e: Exception) { e.printStackTrace() }
        }
    }

    // ── Kaydedilen gönderiler (Beğendikleri tab) ─────────────────────────────
    fun loadSavedPosts() {
        val uid = myUid
        if (uid.isEmpty()) return
        viewModelScope.launch {
            _savedLoading.value = true
            try {
                // feed_saves Supabase'den oku
                val postIds = try {
                    supabase.postgrest["feed_saves"].select {
                        filter { eq("uid", uid) }
                        limit(30)
                    }.decodeList<com.heftreng.app.data.model.FeedSaveRow>()
                        .mapNotNull { it.postId.takeIf { id -> id.isNotBlank() } }.distinct()
                } catch (_: Exception) { emptyList() }

                if (postIds.isEmpty()) { _savedPosts.value = emptyList(); return@launch }

                // Batch: 10'ar 10'ar (Firestore in-query limiti)
                val posts = mutableListOf<Post>()
                postIds.chunked(10).forEach { chunk ->
                    val snap = firestore.collection("feed")
                        .whereIn(com.google.firebase.firestore.FieldPath.documentId(), chunk)
                        .get().await()
                    snap.documents.forEach { doc ->
                        val fd = doc.data ?: return@forEach
                        val quoteObj   = fd["quote"] as? Map<*, *>
                        val repostOf   = fd["repostOf"] as? String ?: fd["repostType"] as? String ?: ""
                        @Suppress("UNCHECKED_CAST")
                        val badges = (fd["badges"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                        posts.add(Post(
                            id            = doc.id,
                            uid           = fd["uid"]      as? String ?: "",
                            displayName   = (fd["name"]    as? String)?.takeIf { it.isNotBlank() } ?: "",
                            name          = fd["name"]     as? String ?: "",
                            username      = fd["username"] as? String ?: "",
                            photoURL      = fd["photoURL"] as? String ?: "",
                            text          = fd["text"]     as? String ?: "",
                            title         = fd["title"]    as? String ?: "",
                            imageURL      = fd["imgUrl"]   as? String ?: fd["imageURL"] as? String ?: "",
                            ytVid         = fd["ytVid"]     as? String ?: "",
                            badges        = badges,
                            quoteText     = (quoteObj?.get("text")   as? String) ?: fd["quoteText"]  as? String ?: "",
                            bookName      = (quoteObj?.get("book")   as? String) ?: fd["bookName"]   as? String ?: "",
                            authorName    = (quoteObj?.get("author") as? String) ?: fd["authorName"] as? String ?: "",
                            repostOf            = repostOf,
                            repostUid           = fd["repostUid"]   as? String ?: "",
                            repostType          = fd["repostType"]  as? String ?: "",
                            repostId            = fd["repostId"]    as? String ?: "",
                            repostTitle         = fd["repostTitle"] as? String ?: "",
                            repostUrl           = fd["repostUrl"]   as? String ?: "",
                            repostImg           = fd["repostImg"]   as? String ?: "",
                            repostText          = fd["repostText"]        as? String ?: "",
                            repostAuthor        = fd["repostAuthor"]      as? String ?: "",
                            repostAuthorPhoto   = fd["repostAuthorPhoto"] as? String ?: "",
                            repostAuthorUid     = fd["repostAuthorUid"]   as? String ?: "",
                            repostSerialId         = fd["repostSerialId"]         as? String ?: "",
                            repostSerialTitle      = fd["repostSerialTitle"]      as? String ?: "",
                            repostSerialDesc       = fd["repostSerialDesc"]       as? String ?: "",
                            repostSerialCover      = fd["repostSerialCover"]      as? String ?: "",
                            repostSerialAuthorName = fd["repostSerialAuthorName"] as? String ?: "",
                            repostSerialAuthorUid  = fd["repostSerialAuthorUid"]  as? String ?: "",
                            repostSerialBg         = fd["repostSerialBg"]         as? String ?: "",
                            repostSerialChCount    = (fd["repostSerialChCount"]   as? Long)?.toInt() ?: 0,
                            serialId      = fd["serialId"]      as? String ?: "",
                            serialTitle   = fd["serialTitle"]   as? String ?: "",
                            serialCover   = fd["serialCover"]   as? String ?: fd["serialBg"] as? String ?: "",
                            chapterId     = fd["chapterId"]     as? String ?: "",
                            chapterTitle  = fd["chapterTitle"]  as? String ?: "",
                            chapterOrder  = (fd["chapterOrder"] as? Long)?.toInt() ?: 0,
                            likesCount    = (fd["likes"]    as? Long)?.toInt() ?: 0,
                            commentsCount = (fd["cmtCount"] as? Long)?.toInt() ?: 0,
                            repostsCount  = (fd["reposts"]  as? Long)?.toInt() ?: 0,
                            ts            = fd["ts"] as? com.google.firebase.Timestamp,
                        ))
                    }
                }
                val sorted = posts.sortedByDescending { it.ts?.seconds ?: 0L }
                _savedPosts.value = sorted
                enrichPostsInBackground(sorted, _savedPosts)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _savedLoading.value = false
            }
        }
    }

    // ── Profil / Kapak fotoğrafı güncelle ────────────────────────────────────
    fun updateProfilePhoto(
        imageUri: android.net.Uri,
        storage : com.google.firebase.storage.FirebaseStorage,
        onDone  : (String) -> Unit = {},
        onError : (String) -> Unit = {},
    ) {
        if (myUid.isEmpty()) { onError("Kullanıcı bulunamadı"); return }
        _loading.value = true
        viewModelScope.launch {
            try {
                val ref = storage.reference.child("profile_photos/${myUid}.jpg")
                ref.putFile(imageUri).await()
                val url = ref.downloadUrl.await().toString()
                firestore.collection("users").document(myUid).update("photoURL", url).await()
                _user.value = _user.value?.copy(photoURL = url)
                onDone(url)
            } catch (e: Exception) {
                e.printStackTrace()
                onError(e.localizedMessage ?: "Fotoğraf yüklenemedi")
            } finally {
                _loading.value = false
            }
        }
    }

    fun updateCoverPhoto(
        imageUri: android.net.Uri,
        storage : com.google.firebase.storage.FirebaseStorage,
        onDone  : (String) -> Unit = {},
        onError : (String) -> Unit = {},
    ) {
        if (myUid.isEmpty()) { onError("Kullanıcı bulunamadı"); return }
        _loading.value = true
        viewModelScope.launch {
            try {
                val ref = storage.reference.child("cover_photos/${myUid}.jpg")
                ref.putFile(imageUri).await()
                val url = ref.downloadUrl.await().toString()
                firestore.collection("users").document(myUid).update(mapOf(
                    "coverPhoto" to url,
                    "coverURL"   to url,
                )).await()
                _user.value = _user.value?.copy(coverPhoto = url)
                onDone(url)
            } catch (e: Exception) {
                e.printStackTrace()
                onError(e.localizedMessage ?: "Kapak fotoğrafı yüklenemedi")
            } finally {
                _loading.value = false
            }
        }
    }

    // ── Username güncelle ─────────────────────────────────────────────────────
    fun updateUsername(newUsername: String, onSuccess: () -> Unit = {}, onError: (String) -> Unit = {}) {
        if (myUid.isEmpty() || newUsername.isBlank()) return
        val handle = newUsername.lowercase().trim()
            .filter { it.isLetterOrDigit() || it == '_' }
            .take(20)
        if (handle.isBlank()) { onError("Geçersiz kullanıcı adı"); return }
        viewModelScope.launch {
            try {
                // Alınmış mı kontrolü
                val takenDoc = firestore.collection("usernames").document(handle).get().await()
                if (takenDoc.exists()) {
                    val ownerUid = takenDoc.getString("uid") ?: ""
                    if (ownerUid != myUid) { onError("Bu kullanıcı adı alınmış"); return@launch }
                }
                val oldHandle = _user.value?.username?.lowercase()?.trim() ?: ""
                // Eski handle sil
                if (oldHandle.isNotBlank() && oldHandle != handle) {
                    try { firestore.collection("usernames").document(oldHandle).delete().await() }
                    catch (_: Exception) {}
                }
                // Yeni handle kaydet
                firestore.collection("usernames").document(handle).set(
                    mapOf("uid" to myUid, "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp())
                ).await()
                // users dokümanını güncelle
                firestore.collection("users").document(myUid).update(mapOf(
                    "username"      to handle,
                    "usernameLower" to handle.lowercase(),
                )).await()
                _user.value = _user.value?.copy(username = handle)
                onSuccess()
            } catch (e: Exception) { onError(e.message ?: "Hata") }
        }
    }


}
