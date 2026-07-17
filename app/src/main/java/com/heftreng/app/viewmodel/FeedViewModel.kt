package com.heftreng.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import com.heftreng.app.data.model.Comment
import com.heftreng.app.data.model.ReplyTo
import com.heftreng.app.data.model.Post
import com.heftreng.app.data.repository.LibraryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import com.heftreng.app.data.model.FeedLikeRow
import com.heftreng.app.data.model.FeedSaveRow
import com.heftreng.app.data.model.FeedCommentRow
import com.heftreng.app.data.model.FeedCommentInsert
import com.heftreng.app.data.model.CommentLikeRow
import com.heftreng.app.data.model.FollowRow
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import com.heftreng.app.util.CacheEntry
import javax.inject.Inject

@HiltViewModel
class FeedViewModel @Inject constructor(
    private val auth     : FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val library  : LibraryRepository,
    private val supabase : SupabaseClient,
    private val quoteDao : com.heftreng.app.data.local.QuoteDao,
) : ViewModel() {

    private val _posts    = MutableStateFlow<List<Post>>(emptyList())
    val posts = _posts.asStateFlow()

    private val _suggestedUsers = MutableStateFlow<List<SuggestedUser>>(emptyList())
    val suggestedUsers = _suggestedUsers.asStateFlow()

    data class SuggestedUser(
        val uid        : String,
        val name       : String,
        val photoURL   : String,
        val bio        : String = "",
        val isFollowing: Boolean = false,
    )

    // ── Mention (@kullanıcı) önerileri ──────────────────────────────────────
    // Not: arama mantığı ve tip tanımı ortak MentionHelper'a taşındı (kod tekrarını
    // önlemek için) — MessagesViewModel de aynı MentionHelper.MentionUser tipini kullanır.
    private val _mentionSuggestions = MutableStateFlow<List<com.heftreng.app.util.MentionHelper.MentionUser>>(emptyList())
    val mentionSuggestions = _mentionSuggestions.asStateFlow()

    private var mentionSearchJob: kotlinx.coroutines.Job? = null

    /** @sonrası yazılan metne göre Firestore users koleksiyonundan displayName prefix araması yapar. */
    fun searchMentionUsers(query: String) {
        mentionSearchJob?.cancel()
        if (query.isBlank()) {
            _mentionSuggestions.value = emptyList()
            return
        }
        mentionSearchJob = viewModelScope.launch {
            kotlinx.coroutines.delay(200) // basit debounce
            _mentionSuggestions.value = com.heftreng.app.util.MentionHelper.searchUsers(firestore, query)
        }
    }

    fun clearMentionSuggestions() {
        mentionSearchJob?.cancel()
        _mentionSuggestions.value = emptyList()
    }

    private val _libraryQuotes = MutableStateFlow<List<Post>>(emptyList())
    val libraryQuotes = _libraryQuotes.asStateFlow()

    // Öncelik 4: Offline cache — Room'dan gösteriliyorsa true (ağ hatası/çevrimdışı)
    private val _libraryQuotesOffline = MutableStateFlow(false)
    val libraryQuotesOffline = _libraryQuotesOffline.asStateFlow()

    // Kütüphane alıntıları sayfalama
    private val LIBRARY_PAGE_SIZE = 20
    private val _libraryHasMore = MutableStateFlow(false)
    val libraryHasMore = _libraryHasMore.asStateFlow()
    private val _libraryLoadingMore = MutableStateFlow(false)
    val libraryLoadingMore = _libraryLoadingMore.asStateFlow()

    // ── Arkadaşlar ne okuyor? — Feed üst şeridi ──────────────────────────────
    private val _friendsReading = MutableStateFlow<List<com.heftreng.app.data.model.FriendReadingItem>>(emptyList())
    val friendsReading = _friendsReading.asStateFlow()

    private val _comments = MutableStateFlow<List<Comment>>(emptyList())
    val comments = _comments.asStateFlow()

    private val _loading  = MutableStateFlow(false)
    val loading = _loading.asStateFlow()

    private val _followingUids = MutableStateFlow<Set<String>>(emptySet())
    val followingUids = _followingUids.asStateFlow()

    // Gizli hesap koruması — client-side son katman.
    // Firestore Rules "list" sorgusunda tek tek belge bazlı filtre yapamadığı için
    // (allow list: if true), feed çekildikten sonra burada filtreliyoruz:
    // visibility == "friends" olan bir post sadece sahibi veya takipçisi tarafından görülebilir.
    private fun filterVisible(posts: List<Post>): List<Post> {
        val myUid = uid
        return posts.filter { p ->
            when (p.visibility) {
                "only_me" -> p.uid == myUid
                "friends" -> p.uid == myUid || _followingUids.value.contains(p.uid)
                else      -> true
            }
        }
    }


    // Swipe-to-refresh için ayrı state — server fetch bitince false olur
    private val _serverRefreshing = MutableStateFlow(false)
    val serverRefreshing = _serverRefreshing.asStateFlow()

    private val _hasMore  = MutableStateFlow(true)
    val hasMore = _hasMore.asStateFlow()

    private val _loadingMore   = MutableStateFlow(false)
    val loadingMore = _loadingMore.asStateFlow()

    private val _postNotFound  = MutableStateFlow<String?>(null)
    val postNotFound = _postNotFound.asStateFlow()

    private val _uploading = MutableStateFlow(false)
    val uploading = _uploading.asStateFlow()

    private val _commentError = MutableStateFlow<String?>(null)
    val commentError = _commentError.asStateFlow()

    val uid get() = auth.currentUser?.uid ?: ""

    private var likedIds    = emptySet<String>()
    private var savedIds    = emptySet<String>()
    private var myRepostMap = emptyMap<String, String>()
    private val interactionCache = CacheEntry<Unit>(ttlMs = 3 * 60_000L)
    private var lastDoc: com.google.firebase.firestore.DocumentSnapshot? = null
    private val PAGE_SIZE = 20L
    private var lastServerFetchMs: Long = 0L
    private val AUTO_REFRESH_INTERVAL_MS: Long = 5L * 60L * 1000L // 5 dk — otomatik yenileme aralığı
    
    private val userCache = mutableMapOf<String, Pair<String, String>>()

    // user dökümanı bellek cache — aynı uid'yi tekrar Firestore'dan çekmez
    private val _userDocCache = mutableMapOf<String, Map<String, Any>>()
    private suspend fun cachedUserDoc(targetUid: String): Map<String, Any>? {
        _userDocCache[targetUid]?.let { return it }
        return try {
            val d = firestore.collection("users").document(targetUid).get().await().data
            if (d != null) _userDocCache[targetUid] = d
            d
        } catch (_: Exception) { null }
    }

    // Kendi profilimiz için auth'tan oku — Firestore okuması yapmaz
    private fun myUserData(): Map<String, Any> = mapOf(
        "displayName" to (auth.currentUser?.displayName ?: ""),
        "photoURL"    to (auth.currentUser?.photoUrl?.toString() ?: ""),
        "uid"         to uid,
    )

    // Session'da bir kez çalıştır — migration'dan kaçan kullanıcıları Supabase'e yazar.
    // Cloud Function (onUserCreated) yapması gereken işi yapar, sadece güvenlik ağı olarak.
    private var _supabaseUserEnsured = false
    private fun ensureUserInSupabase() {
        if (_supabaseUserEnsured || uid.isEmpty()) return
        _supabaseUserEnsured = true
        viewModelScope.launch {
            try {
                val d           = cachedUserDoc(uid)
                val fireUser    = auth.currentUser
                // displayName boş olsa bile kaydı yaz — en azından uid ile tabloya gir.
                // Eski kod displayName boşsa return ediyordu, yeni kayıtlar hiç yazılmıyordu.
                val displayName = (d?.get("displayName") as? String
                    ?: d?.get("name") as? String
                    ?: fireUser?.displayName
                    ?: fireUser?.email?.substringBefore("@")
                    ?: "").trim()
                supabase.postgrest["users"].upsert(
                    mapOf(
                        "uid"          to uid,
                        "display_name" to displayName,
                        "photo_url"    to (d?.get("photoURL") as? String ?: fireUser?.photoUrl?.toString() ?: ""),
                        "bio"          to (d?.get("bio") as? String ?: ""),
                        "banned"       to (d?.get("banned") as? Boolean ?: false),
                    )
                )
                android.util.Log.d("FeedVM", "ensureUserInSupabase: OK (uid=$uid, name='$displayName')")
            } catch (e: Exception) {
                android.util.Log.w("FeedVM", "ensureUserInSupabase: ${e.message}")
            }
        }
    }

    // liked/saved/repost → get() ile yükleniyor, listener yok

    init {
        auth.addAuthStateListener { firebaseAuth ->
            val currentUid = firebaseAuth.currentUser?.uid ?: ""
            if (currentUid.isNotEmpty()) {
                startLiveInteractions(currentUid)
                ensureUserInSupabase()
            }
        }
        refresh()
        loadLibraryQuotes()
        recordDailyActivityAndStreak()
    }

    // ── Genel okuma/etkileşim streak'i — daily_activity (Supabase) ──────────
    // Feed her açıldığında bugünün aktivitesini kaydeder, ardından ardışık
    // gün sayısını hesaplayıp users/{uid}.streak alanına yazar (profil hero'su
    // ve Kurdî streak kartı dışındaki genel streak için).
    private fun recordDailyActivityAndStreak() {
        val myUid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                library.recordDailyActivity(myUid)
                val streak = library.computeStreak(myUid)
                firestore.collection("users").document(myUid)
                    .update("streak", streak)

                // ── Rozetler — Öncelik 4 ────────────────────────────────────
                val booksRead    = library.getBooksReadCount(myUid)
                val quotesShared = library.getQuotesSharedCount(myUid)
                library.checkAndAwardBadges(myUid, booksRead, quotesShared, streak)
            } catch (e: Exception) {
                android.util.Log.w("FeedVM", "recordDailyActivityAndStreak: ${e.message}")
            }
        }
    }

    fun loadFollowingUids(uid: String) {
        if (uid.isBlank()) return
        // loadSuggestedUsers zaten _followingUids'i cursor-based çekiyor.
        // O çalıştıysa tekrar sorgu atmıyoruz — fatura tasarrufu.
        if (_followingUids.value.isNotEmpty()) return
        viewModelScope.launch {
            try {
                // FATURA OPTİMİZASYONU: 1000 → 300.
                // Feed filtreleme için 300 yeterli; çok fazla takip varsa
                // loadSuggestedUsers'ın cursor-based versiyonu devreye girer.
                val rows = supabase.postgrest["follows"]
                    .select {
                        filter { eq("from_uid", uid) }
                        limit(PAGE_SIZE)
                    }
                    .decodeList<FollowRow>()
                _followingUids.value = rows.map { it.targetUid }.toSet()
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    // Takip/bırak işleminden sonra yerel güncelleme — ekstra Firestore okuması yok
    fun onFollowChanged(targetUid: String, isFollowing: Boolean) {
        _followingUids.value = if (isFollowing)
            _followingUids.value + targetUid
        else
            _followingUids.value - targetUid
    }

    private fun startLiveInteractions(currentUid: String) {
        // Cache geçerliyse ağ isteği yok
        if (interactionCache.isValid()) { syncInteractionsToState(); return }
        viewModelScope.launch {
            try {
                val postIds = _posts.value.map { it.id }.filter { it.isNotBlank() }
                if (postIds.isNotEmpty()) {
                    // Supabase: tek sorguda tüm postların like/save durumu — N sorgu → 2 sorgu
                    val likedPostIds = supabase.postgrest["feed_likes"]
                        .select {
                            filter {
                                eq("uid", currentUid)
                                isIn("post_id", postIds)
                            }
                        }
                        .decodeList<FeedLikeRow>()
                        .map { it.postId }.toSet()

                    val savedPostIds = supabase.postgrest["feed_saves"]
                        .select {
                            filter {
                                eq("uid", currentUid)
                                isIn("post_id", postIds)
                            }
                        }
                        .decodeList<FeedSaveRow>()
                        .map { it.postId }.toSet()

                    likedIds = likedIds + likedPostIds
                    savedIds = savedIds + savedPostIds
                }

                // Repost map — sadece ilk yüklemede çek, session boyunca cache'de kalır
                if (myRepostMap.isEmpty()) {
                    val repostSnap = firestore.collection("feed")
                        .whereEqualTo("uid", currentUid)
                        .whereEqualTo("repostType", "feed").limit(100).get().await()
                    myRepostMap = repostSnap.documents.mapNotNull { doc ->
                        val orig = doc.getString("repostId") ?: return@mapNotNull null
                        orig to doc.id
                    }.toMap()
                }

                interactionCache.set(Unit)
                syncInteractionsToState()
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    // Yeni sayfa yüklendiğinde o sayfanın beğeni/kayıt durumlarını kontrol et
    fun refreshInteractionsForPage(posts: List<Post>) {
        val currentUid = auth.currentUser?.uid ?: return
        if (posts.isEmpty()) return
        viewModelScope.launch {
            try {
                val postIds = posts.map { it.id }.filter { it.isNotBlank() }
                if (postIds.isEmpty()) return@launch

                val newLikedIds = supabase.postgrest["feed_likes"]
                    .select { filter { eq("uid", currentUid); isIn("post_id", postIds) } }
                    .decodeList<FeedLikeRow>().map { it.postId }.toSet()

                val newSavedIds = supabase.postgrest["feed_saves"]
                    .select { filter { eq("uid", currentUid); isIn("post_id", postIds) } }
                    .decodeList<FeedSaveRow>().map { it.postId }.toSet()

                likedIds = likedIds + newLikedIds
                savedIds = savedIds + newSavedIds
                syncInteractionsToState()
            } catch (_: Exception) {}
        }
    }

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
        // Listener yok — get() kullanılıyor
    }

    // ── OPTİMİZE EDİLDİ: İki Aşamalı Hibrit Hızlı Akış (Cache -> Server) ──────
    fun refresh(forceRefresh: Boolean = false) {
        lastDoc = null
        _hasMore.value = true
        _postNotFound.value = null

        if (forceRefresh) {
            _serverRefreshing.value = true // Swipe spinner — server bitince kapanır
        } else if (_posts.value.isEmpty()) {
            _loading.value = true          // İlk yüklemede normal spinner
        }

        viewModelScope.launch {
            val query = firestore.collection("feed")
                .orderBy("ts", Query.Direction.DESCENDING)
                .limit(PAGE_SIZE)

            // 1. AŞAMA: Önce cihaz hafızasındaki (Cache) verileri jet hızıyla çek ve ekrana bas
            try {
                val cacheSnap = query.get(Source.CACHE).await()
                if (!cacheSnap.isEmpty) {
                    if (cacheSnap.documents.isNotEmpty()) lastDoc = cacheSnap.documents.last()
                    _hasMore.value = cacheSnap.documents.size >= PAGE_SIZE.toInt()
                    
                    val rawPosts = cacheSnap.documents.mapNotNull { it.toPost() }
                    val filtered = filterVisible(rawPosts.filter { it.moderationStatus != "removed" })
                    
                    _posts.value = mapInteractions(filtered)
                    _loading.value = false // Önbellekten veri geldiği an yükleme çemberi biter!
                    enrichPostsInBackground(filtered)
                    refreshInteractionsForPage(filtered)
                }
            } catch (e: Exception) {
                // Önbellek boşsa veya ilk yüklemeyse burası sessizce pas geçilir
            }

            // 2. AŞAMA: Server — kullanıcı swipe yaptıysa HER ZAMAN, yoksa 5 dk geçmişse
            val now = System.currentTimeMillis()
            val shouldHitServer = forceRefresh
                || _posts.value.isEmpty()
                || (now - lastServerFetchMs) > AUTO_REFRESH_INTERVAL_MS
            if (shouldHitServer) {
                try {
                    // ÖNEMLİ: withTimeoutOrNull olmadan bu çağrı, zayıf/sorunsuz
                    // bağlantılarda (özellikle cache de boşsa, yani ilk kurulumda)
                    // dakikalarca askıda kalabiliyordu — kullanıcı süresiz skeleton
                    // görüyordu, "ilk açılışta içerik boş kalıyor" şikayetinin sebebi buydu.
                    // 15sn sonra pes edip _loading'i kapatıyoruz; postNotFound/hata UI'ı
                    // yerine boş feed + pull-to-refresh ile tekrar deneme imkânı kalıyor.
                    val serverSnap = withTimeoutOrNull(15_000L) { query.get(Source.SERVER).await() }
                    if (serverSnap != null && !serverSnap.isEmpty) {
                        lastServerFetchMs = now
                        if (serverSnap.documents.isNotEmpty()) lastDoc = serverSnap.documents.last()
                        _hasMore.value = serverSnap.documents.size >= PAGE_SIZE.toInt()
                        val rawPosts = serverSnap.documents.mapNotNull { it.toPost() }
                        val filtered = filterVisible(rawPosts.filter { it.moderationStatus != "removed" })
                        _posts.value = mapInteractions(filtered)
                        enrichPostsInBackground(filtered)
                        refreshInteractionsForPage(filtered)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    _loading.value = false
                    _serverRefreshing.value = false // Server bitti → swipe spinner kapat
                }
            } else {
                _loading.value = false
                _serverRefreshing.value = false
            }
        }
    }

    fun loadMore() {
        val last = lastDoc ?: return
        if (_loadingMore.value || !_hasMore.value) return
        viewModelScope.launch {
            _loadingMore.value = true
            try {
                // Sayfalandırma (Pagination) işlemlerini doğrudan sunucudan çekmeye devam ediyoruz
                val snap = firestore.collection("feed")
                    .orderBy("ts", Query.Direction.DESCENDING)
                    .startAfter(last)
                    .limit(PAGE_SIZE)
                    .get(Source.SERVER).await()
                
                if (snap.documents.isNotEmpty()) lastDoc = snap.documents.last()
                _hasMore.value = snap.documents.size >= PAGE_SIZE.toInt()
                
                val rawMore = snap.documents.mapNotNull { it.toPost() }
                val filtered = filterVisible(rawMore.filter { it.moderationStatus != "removed" })
                
                _posts.value = _posts.value + mapInteractions(filtered)
                enrichPostsInBackground(filtered)
                // Yeni sayfanın beğeni/kayıt durumlarını kontrol et
                refreshInteractionsForPage(filtered)
            } catch (e: Exception) { 
                e.printStackTrace() 
            } finally { 
                _loadingMore.value = false 
            }
        }
    }

    private fun mapInteractions(posts: List<Post>): List<Post> = posts.map { post ->
        post.copy(
            isLikedByMe    = post.id in likedIds,
            isSavedByMe    = post.id in savedIds,
            isRepostedByMe = post.id in myRepostMap,
            myRepostId     = myRepostMap[post.id] ?: "",
        )
    }

    /** coverImg boş alıntı postlarını Supabase library_books'tan tamamlar */
    internal fun enrichMissingCovers(posts: List<Post>) {
        val needsCover = posts.filter {
            it.coverImg.isBlank() && (it.libraryBookId.isNotBlank() || it.bookName.isNotBlank())
        }
        if (needsCover.isEmpty()) return

        viewModelScope.launch {
            // bookId -> coverImg: önce ID ile direkt çek (daha güvenilir), yoksa isimle ara
            val coverByIdMap   = mutableMapOf<String, String>() // libraryBookId -> coverImg
            val coverByNameMap = mutableMapOf<String, String>() // bookName -> coverImg

            needsCover.filter { it.libraryBookId.isNotBlank() }
                .map { it.libraryBookId }.distinct()
                .forEach { bookId ->
                    try {
                        val url = library.getBook(bookId)?.coverImg ?: ""
                        if (url.isNotBlank()) coverByIdMap[bookId] = url
                    } catch (_: Exception) {}
                }

            // ID'si olmayanlar için fallback: isimle arama
            needsCover.filter { it.libraryBookId.isBlank() && it.bookName.isNotBlank() }
                .map { it.bookName }.distinct()
                .forEach { title ->
                    try {
                        val url = library.searchBooks(title)
                            .firstOrNull { it.title.equals(title.trim(), ignoreCase = true) }
                            ?.coverImg ?: ""
                        if (url.isNotBlank()) coverByNameMap[title] = url
                    } catch (_: Exception) {}
                }

            if (coverByIdMap.isEmpty() && coverByNameMap.isEmpty()) return@launch

            // in-memory güncelleme
            val updatedPosts = _posts.value.map { post ->
                if (post.coverImg.isBlank()) {
                    val url = coverByIdMap[post.libraryBookId] ?: coverByNameMap[post.bookName] ?: return@map post
                    post.copy(coverImg = url)
                } else post
            }
            _posts.value = updatedPosts

            val updatedQuotes = _libraryQuotes.value.map { post ->
                if (post.coverImg.isBlank()) {
                    val url = coverByIdMap[post.libraryBookId] ?: coverByNameMap[post.bookName] ?: return@map post
                    post.copy(coverImg = url)
                } else post
            }
            _libraryQuotes.value = updatedQuotes

            // Sorun 4 düzeltmesi: Firestore'daki coverImg alanını da güncelle.
            // Böylece sonraki açılışlarda aynı Supabase sorgusu tekrar atılmaz.
            val postsToWrite = (updatedPosts + updatedQuotes)
                .filter { it.coverImg.isNotBlank() && it.id.isNotBlank() }
                .distinctBy { it.id }
            if (postsToWrite.isNotEmpty()) {
                try {
                    val batch = firestore.batch()
                    postsToWrite.forEach { post ->
                        batch.update(
                            firestore.collection("feed").document(post.id),
                            "coverImg", post.coverImg
                        )
                    }
                    batch.commit().await()
                } catch (_: Exception) {}
            }
        }
    }

    private fun enrichPostsInBackground(posts: List<Post>) {
        if (posts.isEmpty()) return
        val missingUids = posts.map { it.uid }
            .filter { it.isNotBlank() && it !in userCache }
            .distinct()

        // Sayaç senkronizasyonu her zaman çalışır — kullanıcı cache'i eksik olsun olmasın
        syncPostCounts(posts.map { it.id })

        // coverImg boş olan alıntı postları için Supabase'den kapak URL'ini çek
        enrichMissingCovers(posts)

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

    /** Beğeni / yorum sayılarını Supabase'den çek — feed.likesCount/commentsCount artık
     *  Firestore'da güncellenmiyor (RLS/quota gerektirmemesi için Supabase tek kaynak). */
    private fun syncPostCounts(postIds: List<String>) {
        val ids = postIds.filter { it.isNotBlank() }.distinct()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            try {
                val likeRows = supabase.postgrest["feed_likes"]
                    .select { filter { isIn("post_id", ids) } }
                    .decodeList<FeedLikeRow>()
                val commentRows = supabase.postgrest["feed_comments"]
                    .select { filter { isIn("post_id", ids) } }
                    .decodeList<FeedCommentRow>()

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

    // ── Supabase book_quotes → Post (Keşfet/Alıntılar) ───────────────────────
    private fun com.heftreng.app.data.repository.BookQuoteRow.toPost() = Post(
        id              = feedPostId,
        uid             = uid,
        displayName     = userDisplayName,
        name            = userDisplayName,
        photoURL        = userPhotoUrl,
        quoteText       = text,
        bookName        = bookTitle,
        authorName      = authorName,
        coverImg        = coverImg,
        likesCount      = likesCount,
        libraryBookId   = bookId,
        libraryAuthorId = authorId ?: "",
        ts              = parseSupabaseTimestamp(createdAt),
    )

    // (parseSupabaseTimestamp zaten daha aşağıda tanımlı — bkz. nullable String? versiyonu)

    // ── Öncelik 4: Offline cache (Room) — Post <-> CachedQuote dönüştürücüler ──
    private fun Post.toCachedQuote() = com.heftreng.app.data.local.CachedQuote(
        id              = id,
        uid             = uid,
        displayName     = displayName.ifBlank { name },
        photoURL        = photoURL,
        quoteText       = quoteText,
        bookName        = bookName,
        authorName      = authorName,
        coverImg        = coverImg,
        likesCount      = likesCount,
        commentsCount   = commentsCount,
        repostsCount    = repostsCount,
        libraryBookId   = libraryBookId,
        libraryAuthorId = libraryAuthorId,
        feedPostId      = id,
        tsMillis        = ts?.toDate()?.time ?: 0L,
    )

    private fun com.heftreng.app.data.local.CachedQuote.toPost() = Post(
        id              = feedPostId,
        uid             = uid,
        displayName     = displayName,
        name            = displayName,
        photoURL        = photoURL,
        quoteText       = quoteText,
        bookName        = bookName,
        authorName      = authorName,
        coverImg        = coverImg,
        likesCount      = likesCount,
        commentsCount   = commentsCount,
        repostsCount    = repostsCount,
        libraryBookId   = libraryBookId,
        libraryAuthorId = libraryAuthorId,
        ts              = if (tsMillis > 0) com.google.firebase.Timestamp(java.util.Date(tsMillis)) else null,
    )

    internal fun com.google.firebase.firestore.DocumentSnapshot.toPost(): Post? {
        val d = data ?: return null
        val displayName = (d["displayName"] as? String)?.takeIf { it.isNotBlank() }
            ?: d["name"] as? String ?: ""
        val quoteObj    = d["quote"] as? Map<*, *>
        val quoteText   = (quoteObj?.get("text") as? String)?.takeIf { it.isNotBlank() }
            ?: d["quoteText"] as? String ?: ""
        val bookName    = (quoteObj?.get("book") as? String)?.takeIf { it.isNotBlank() }
            ?: d["bookName"] as? String ?: ""
        val authorName  = (quoteObj?.get("author") as? String)?.takeIf { it.isNotBlank() }
            ?: d["authorName"] as? String ?: ""
        val imageURL    = (d["imageURL"] as? String)?.takeIf { it.isNotBlank() }
            ?: d["imgUrl"] as? String ?: ""
        @Suppress("UNCHECKED_CAST")
        val badges = (d["badges"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
        return Post(
            id            = id,
            uid           = d["uid"]      as? String ?: "",
            displayName   = displayName,
            name          = displayName,
            username      = d["username"] as? String ?: "",
            photoURL      = d["photoURL"] as? String ?: "",
            text          = d["text"]     as? String ?: "",
            title         = d["title"]    as? String ?: "",
            category      = d["category"] as? String ?: "",
            imageURL      = imageURL,
            imgUrl        = imageURL,
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
            repostLevel       = (d["repostLevel"]      as? Long)?.toInt() ?: 0,
            repostXp          = (d["repostXp"]         as? Long)?.toInt() ?: 0,
            repostStreak      = (d["repostStreak"]     as? Long)?.toInt() ?: 0,
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
            coverImg               = d["coverImg"]               as? String ?: "",
            type                   = d["type"]                   as? String ?: "",
            visibility             = d["visibility"]             as? String ?: "public",
            mentions               = (d["mentions"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
            moderationStatus       = d["moderationStatus"]       as? String ?: "active",
            moderationReason       = d["moderationReason"]       as? String ?: "",
        )
    }

    fun setPostVisibility(postId: String, visibility: String) {
        viewModelScope.launch {
            try {
                firestore.collection("feed").document(postId)
                    .update("visibility", visibility).await()
                _posts.value = _posts.value.map {
                    if (it.id == postId) it.copy(visibility = visibility) else it
                }
                _libraryQuotes.value = _libraryQuotes.value.map {
                    if (it.id == postId) it.copy(visibility = visibility) else it
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun toggleLike(post: Post) {
        if (uid.isEmpty()) return
        val nowLiked = !post.isLikedByMe
        likedIds = if (nowLiked) likedIds + post.id else likedIds - post.id

        // Optimistic UI — Supabase tek kaynak, Firestore'a sayaç yazılmaz
        _posts.value = _posts.value.map {
            if (it.id == post.id) it.copy(
                isLikedByMe = nowLiked,
                likesCount  = maxOf(0, it.likesCount + if (nowLiked) 1 else -1),
            ) else it
        }
        viewModelScope.launch {
            try {
                if (nowLiked) {
                    ensureMyProfileCached()
                    val myName  = _cachedMyName.ifBlank { auth.currentUser?.displayName ?: "" }
                    val myPhoto = _cachedMyPhoto.ifBlank { auth.currentUser?.photoUrl?.toString() ?: "" }
                    // Duplicate kayıt engelle
                    val existing = try {
                        supabase.postgrest["feed_likes"]
                            .select { filter { eq("post_id", post.id); eq("uid", uid) }; limit(1) }
                            .decodeList<FeedLikeRow>()
                    } catch (_: Exception) { emptyList() }
                    if (existing.isEmpty()) {
                        supabase.postgrest["feed_likes"].insert(
                            mapOf(
                                "id"        to "${post.id}_$uid",
                                "post_id"   to post.id,
                                "uid"       to uid,
                                "name"      to myName,
                                "photo_url" to myPhoto,
                            )
                        )
                        if (post.uid != uid) sendNotif(post.uid, "like", "$myName gönderini beğendi", post.text.take(60), post.id)
                    }
                } else {
                    // uid + post_id ile sil — id UUID olsa da çalışır
                    supabase.postgrest["feed_likes"].delete {
                        filter { eq("post_id", post.id); eq("uid", uid) }
                    }
                }
                // Gerçek sayıyı arka planda doğrula (yarış durumlarını düzeltir)
                syncPostCounts(listOf(post.id))
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun toggleCommentLike(postId: String, comment: Comment) {
        if (uid.isEmpty()) return
        val nowLiked = !comment.isLikedByMe
        _comments.value = _comments.value.map {
            if (it.id == comment.id) it.copy(
                isLikedByMe = nowLiked,
                likesCount  = maxOf(0, it.likesCount + if (nowLiked) 1 else -1),
            ) else it
        }
        viewModelScope.launch {
            try {
                if (nowLiked) {
                    ensureMyProfileCached()
                    val myName  = _cachedMyName.ifBlank { auth.currentUser?.displayName ?: "" }
                    val myPhoto = _cachedMyPhoto
                    val existing = try {
                        supabase.postgrest["comment_likes"]
                            .select { filter { eq("comment_id", comment.id); eq("uid", uid) }; limit(1) }
                            .decodeList<CommentLikeRow>()
                    } catch (_: Exception) { emptyList() }
                    if (existing.isEmpty()) {
                        supabase.postgrest["comment_likes"].insert(
                            mapOf(
                                "id"         to "${comment.id}_$uid",
                                "comment_id" to comment.id,
                                "uid"        to uid,
                                "name"       to myName,
                                "photo_url"  to myPhoto,
                            )
                        )
                    }
                } else {
                    supabase.postgrest["comment_likes"].delete {
                        filter { eq("comment_id", comment.id); eq("uid", uid) }
                    }
                }
                // Gerçek beğeni sayısını comment_likes'tan say ve feed_comments.likes_count'u güncelle
                val realCount = try {
                    supabase.postgrest["comment_likes"]
                        .select { filter { eq("comment_id", comment.id) } }
                        .decodeList<CommentLikeRow>().size
                } catch (_: Exception) { -1 }
                if (realCount >= 0) {
                    try {
                        supabase.postgrest["feed_comments"]
                            .update(mapOf("likes_count" to realCount)) {
                                filter { eq("id", comment.id) }
                            }
                    } catch (_: Exception) {}
                    _comments.value = _comments.value.map {
                        if (it.id == comment.id) it.copy(likesCount = realCount) else it
                    }
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
                val postRef = firestore.collection("feed").document(post.id)
                if (nowSaved) {
                    supabase.postgrest["feed_saves"].upsert(
                        FeedSaveRow(
                            id     = "${post.id}_$uid",
                            postId = post.id,
                            uid    = uid,
                        )
                    )
                    postRef.update("saves", FieldValue.increment(1)).await()
                } else {
                    supabase.postgrest["feed_saves"].delete {
                        filter { eq("id", "${post.id}_$uid") }
                    }
                    postRef.update("saves", FieldValue.increment(-1)).await()
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

    fun loadComments(postId: String) {
        viewModelScope.launch {
            try {
                _comments.value = emptyList() // önceki post yorumlarını temizle
                val rows = supabase.postgrest["feed_comments"]
                    .select { filter { eq("post_id", postId) }; order("created_at", Order.ASCENDING) }
                    .decodeList<FeedCommentRow>()

                val myLikedCmtIds = if (uid.isNotEmpty() && rows.isNotEmpty()) {
                    try {
                        supabase.postgrest["comment_likes"]
                            .select { filter { eq("uid", uid); isIn("comment_id", rows.map { it.id }) } }
                            .decodeList<CommentLikeRow>()
                            .map { it.commentId }.toSet()
                    } catch (_: Exception) { emptySet() }
                } else emptySet()

                // replyToCmtId -> ReplyTo: displayName için yorumlar arasında eşleşme yap
                val cmtMap = rows.associateBy { it.id }
                _comments.value = rows.map { r ->
                    val replyToCmt = r.replyToCmtId?.let { cmtMap[it] }
                    Comment(
                        id          = r.id,
                        postId      = r.postId,
                        uid         = r.uid,
                        displayName = r.name ?: "",
                        photoURL    = r.photoUrl ?: "",
                        text        = r.text,
                        likesCount  = r.likesCount,
                        mentions    = r.mentions ?: emptyList(),
                        replyTo     = replyToCmt?.let { ReplyTo(commentId = it.id, uid = it.uid, displayName = it.name ?: "") },
                        ts          = parseSupabaseTimestamp(r.createdAt),
                        isLikedByMe = r.id in myLikedCmtIds,
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _commentError.value = "Yorumlar yüklenemedi: ${e.message}"
            }
        }
    }

    /** Supabase ISO 8601 created_at -> Firestore Timestamp (UI bunu kullanıyor) */
    private fun parseSupabaseTimestamp(iso: String?): Timestamp? {
        if (iso.isNullOrBlank()) return null
        return try {
            val instant = java.time.Instant.parse(iso)
            Timestamp(instant.epochSecond, instant.nano)
        } catch (_: Exception) { null }
    }

    fun addComment(post: Post, text: String, replyTo: Comment? = null, mentions: List<String> = emptyList()) {
        if (uid.isEmpty() || text.isBlank()) return
        viewModelScope.launch {
            try {
                ensureMyProfileCached()
                val d       = cachedUserDoc(uid) ?: myUserData()
                val myName  = d["displayName"] as? String
                    ?: d["name"] as? String
                    ?: _cachedMyName.ifBlank { auth.currentUser?.displayName } ?: "Bikarhêner"
                val myPhoto = d["photoURL"] as? String
                    ?: _cachedMyPhoto.ifBlank { auth.currentUser?.photoUrl?.toString() } ?: ""

                val insertRow = FeedCommentInsert(
                    postId       = post.id,
                    uid          = uid,
                    name         = myName,
                    photoUrl     = myPhoto,
                    text         = text.trim(),
                    replyToCmtId = replyTo?.id,
                    mentions     = mentions.ifEmpty { null },
                )

                supabase.postgrest["feed_comments"].insert(insertRow)

                _posts.value = _posts.value.map {
                    if (it.id == post.id) it.copy(commentsCount = it.commentsCount + 1) else it
                }
                if (post.uid != uid) sendNotif(post.uid, "cmt", "$myName gönderine yorum yaptı", text.take(60), post.id)
                // Mention bildirimi — gönderi sahibine zaten "cmt" bildirimi gitti, tekrar mention göndermeyelim
                mentions.distinct().forEach { mentionedUid ->
                    if (mentionedUid != uid && mentionedUid != post.uid) {
                        sendNotif(mentionedUid, "mention", "$myName seni bir yorumda etiketledi", text.take(60), post.id)
                    }
                }
                loadComments(post.id)
            } catch (e: Exception) {
                e.printStackTrace()
                _commentError.value = "Yorum gönderilemedi: ${e.message}"
            }
        }
    }

    fun deleteComment(postId: String, commentId: String) {
        if (uid.isEmpty()) return
        viewModelScope.launch {
            try {
                val comment = _comments.value.find { it.id == commentId }
                val post    = _posts.value.find { it.id == postId }
                val isCommentOwner = comment?.uid == uid
                val isPostOwner    = post?.uid == uid
                if (!isCommentOwner && !isPostOwner) {
                    _commentError.value = "Bu yorumu silme yetkiniz yok."
                    return@launch
                }
                supabase.postgrest["feed_comments"].delete {
                    filter { eq("id", commentId) }
                }
                try {
                    supabase.postgrest["comment_likes"].delete {
                        filter { eq("comment_id", commentId) }
                    }
                } catch (_: Exception) {}

                _comments.value = _comments.value.filter { it.id != commentId }
                _posts.value = _posts.value.map {
                    if (it.id == postId) it.copy(commentsCount = maxOf(0, it.commentsCount - 1)) else it
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _commentError.value = "Yorum silinemedi: ${e.message}"
            }
        }
    }

    fun editComment(postId: String, commentId: String, newText: String) {
        if (uid.isEmpty() || newText.isBlank()) return
        viewModelScope.launch {
            try {
                val comment = _comments.value.find { it.id == commentId }
                if (comment?.uid != uid) return@launch
                supabase.postgrest["feed_comments"]
                    .update(mapOf("text" to newText.trim())) {
                        filter { eq("id", commentId) }
                    }
                _comments.value = _comments.value.map {
                    if (it.id == commentId) it.copy(text = newText.trim()) else it
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun clearCommentError() { _commentError.value = null }

    fun repost(post: Post) {
        if (uid.isEmpty()) return
        if (post.isRepostedByMe || post.id in myRepostMap) return
        // Optimistic — çift tıklamayı önler, coroutine başlamadan map'e ekle
        myRepostMap = myRepostMap + (post.id to "__pending__")
        _posts.value = _posts.value.map {
            if (it.id == post.id) it.copy(isRepostedByMe = true, repostsCount = it.repostsCount + 1) else it
        }
        // Repostlanan gönderi kendisi bir repost ise (örn. zincirleme repost) veya
        // sadece alıntı/quote içeriyorsa (düz metin boş) — orijinal içerik kaybolmasın diye
        // sıralı fallback uygula: düz metin → alıntı metni → (zincirde) önceki repost metni.
        val chainedRepost  = post.repostType == "feed" && post.repostId.isNotBlank()
        val previewText    = post.text.ifBlank { post.quoteText }.ifBlank { post.repostText }
        val previewImg     = post.imageURL.ifBlank { post.repostImg }
        val origId         = if (chainedRepost) post.repostId else post.id
        val origAuthorName = if (chainedRepost) post.repostAuthor.ifBlank { post.displayName } else post.displayName
        val origAuthorPhoto = if (chainedRepost) post.repostAuthorPhoto.ifBlank { post.photoURL } else post.photoURL
        val origAuthorUid  = if (chainedRepost) post.repostAuthorUid.ifBlank { post.uid } else post.uid
        // ÖNEMLİ: Deterministik doküman ID'si kullanılıyor (repost_<uid>_<origId>).
        // Profil, Kütüphane, Gönderi Detayı gibi farklı ekranlar kendi FeedViewModel
        // örneğine ve myRepostMap'e sahip olabildiği için (henüz senkron olmamış olabilir),
        // eskiden .add() ile rastgele ID üretilince aynı içerik birden fazla kez
        // repostlanabiliyordu. Artık aynı kullanıcı + aynı orijinal içerik için her zaman
        // AYNI doküman ID'sine yazılıyor — sunucu tarafında ikinci bir kayıt asla oluşmaz.
        val repostDocId = "repost_${uid}_$origId"
        viewModelScope.launch {
            try {
                val ref = firestore.collection("feed").document(repostDocId)
                val existing = try {
                    ref.get().await()
                } catch (offlineErr: Exception) {
                    // Firestore SDK bazen network varken bile ilk istekte "client is offline"
                    // hatası fırlatabilir (cache senkronizasyon gecikmesi) — bir kez yeniden dene.
                    kotlinx.coroutines.delay(700)
                    ref.get().await()
                }
                if (existing.exists()) {
                    // Sunucuda zaten var (başka bir ekrandan/oturumdan önceden repostlanmış) —
                    // yinelenen kayıt oluşturma, sadece yerel state'i gerçek duruma senkronla.
                    myRepostMap = myRepostMap + (post.id to repostDocId)
                    _posts.value = _posts.value.map {
                        if (it.id == post.id) it.copy(isRepostedByMe = true, myRepostId = repostDocId) else it
                    }
                    return@launch
                }

                val d       = cachedUserDoc(uid) ?: myUserData()
                val myName  = d["displayName"] as? String ?: d["name"] as? String ?: ""
                val myPhoto = d["photoURL"] as? String ?: ""
                ref.set(mapOf(
                    "uid"               to uid,
                    "name"              to myName,
                    "displayName"       to myName,
                    "username"          to (d["username"] as? String ?: ""),
                    "photoURL"          to myPhoto,
                    "text"              to "",
                    "imageURL"          to "",
                    "imgUrl"            to "",
                    "repostType"        to "feed",
                    "repostId"          to origId,
                    "repostUid"         to origAuthorUid,
                    "repostText"        to previewText.take(200),
                    "repostAuthor"      to origAuthorName,
                    "repostAuthorPhoto" to origAuthorPhoto,
                    "repostAuthorUid"   to origAuthorUid,
                    "repostImg"         to previewImg,
                    "likesCount" to 0, "saves" to 0, "commentsCount" to 0, "reposts" to 0,
                    "ts"    to Timestamp.now(),
                )).await()

                firestore.collection("feed").document(post.id)
                    .update("reposts", FieldValue.increment(1)).await()

                myRepostMap = myRepostMap + (post.id to repostDocId)
                _posts.value = _posts.value.map {
                    if (it.id == post.id) it.copy(myRepostId = repostDocId) else it
                }
                if (post.uid != uid) sendNotif(post.uid, "repost", "$myName gönderini paylaştı", previewText.take(60), post.id)
            } catch (e: Exception) {
                android.util.Log.e("HeftrengRepost", "repost() hata: ${e.javaClass.simpleName}: ${e.message}", e)
                _repostError.value = "${e.javaClass.simpleName}: ${e.message}"
                // Hata → optimistic güncellemeyi geri al
                myRepostMap = myRepostMap - post.id
                _posts.value = _posts.value.map {
                    if (it.id == post.id) it.copy(isRepostedByMe = false, repostsCount = maxOf(0, it.repostsCount - 1), myRepostId = "") else it
                }
            }
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

    private var _draftPrefs: android.content.SharedPreferences? = null
    fun initDraftPrefs(context: android.content.Context) {
        _draftPrefs = context.getSharedPreferences("heft_drafts", android.content.Context.MODE_PRIVATE)
    }
    fun saveDraft(text: String) { _draftPrefs?.edit()?.putString("feed_draft", text)?.apply() }
    fun loadDraft(): String = _draftPrefs?.getString("feed_draft", "") ?: ""
    fun clearDraft() { _draftPrefs?.edit()?.remove("feed_draft")?.apply() }

    fun uploadImageAndCreatePost(
        imageUri   : android.net.Uri,
        text       : String,
        title      : String = "",
        category   : String = "",
        quoteText  : String = "",
        authorName : String = "",
        bookName   : String = "",
        coverImg   : String = "",
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
                createPost(text = text, title = title, category = category, imageURL = url, quoteText = quoteText, authorName = authorName, bookName = bookName, coverImg = coverImg, type = if (quoteText.isNotBlank()) "library_quote" else "")
            } catch (e: Exception) { e.printStackTrace() }
            finally { _uploading.value = false }
        }
    }

    private val _createPostError = MutableStateFlow<String?>(null)
    val createPostError = _createPostError.asStateFlow()
    fun clearCreatePostError() { _createPostError.value = null }

    private val _repostError = MutableStateFlow<String?>(null)
    val repostError = _repostError.asStateFlow()
    fun clearRepostError() { _repostError.value = null }

    private val _createPostLoading = MutableStateFlow(false)
    val createPostLoading = _createPostLoading.asStateFlow()

    fun createPost(text: String, title: String = "", category: String = "", imageURL: String = "", quoteText: String = "", authorName: String = "", bookName: String = "", coverImg: String = "", type: String = "", libraryAuthorId: String = "", libraryBookId: String = "", mentions: List<String> = emptyList()) {
        if (uid.isEmpty()) return
        viewModelScope.launch {
            _createPostLoading.value = true
            try {
                val d       = cachedUserDoc(uid) ?: myUserData()
                val myName  = d["displayName"] as? String ?: d["name"] as? String ?: auth.currentUser?.displayName ?: ""
                val myPhoto = d["photoURL"] as? String ?: auth.currentUser?.photoUrl?.toString() ?: ""
                val myUser  = d["username"] as? String ?: ""
                val myEmail = d["email"] as? String ?: auth.currentUser?.email ?: ""

                var resolvedAuthorId = libraryAuthorId
                var resolvedBookId   = libraryBookId
                var resolvedCoverImg = ""
                var libraryLinkFailed = false
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
                // Kitap kapak resmini çek — paylaşım kartında göstermek için
                if (resolvedBookId.isNotBlank() && resolvedCoverImg.isBlank()) {
                    try {
                        resolvedCoverImg = library.getBook(resolvedBookId)?.coverImg ?: ""
                    } catch (_: Exception) {}
                }

                val myIsPrivate = d["isPrivate"] as? Boolean ?: false
                val resolvedVisibility = if (myIsPrivate) "friends" else "public"

                val feedRef = firestore.collection("feed").add(mapOf(
                    "uid"             to uid,
                    "name"            to myName,
                    "displayName"     to myName,
                    "username"        to myUser,
                    "photoURL"        to myPhoto,
                    "authorEmail"     to myEmail,
                    "text"            to text,
                    "title"           to title,
                    "category"        to category,
                    "imgUrl"          to imageURL,
                    "imageURL"        to imageURL,
                    "quoteText"       to quoteText,
                    "authorName"      to authorName,
                    "bookName"        to bookName,
                    "coverImg"        to resolvedCoverImg,
                    "libraryAuthorId" to resolvedAuthorId,
                    "libraryBookId"   to resolvedBookId,
                    "type"            to if (quoteText.isNotBlank() && type.isBlank()) "library_quote" else type,
                    "visibility"      to resolvedVisibility,
                    "mentions"        to mentions.distinct(),
                    "likes"           to 0, "saves" to 0, "cmtCount" to 0, "reposts" to 0,
                    "ts"              to Timestamp.now(),
                )).await()

                // Mention edilen kullanıcılara bildirim gönder (yorumlardaki mention mantığıyla aynı desen)
                mentions.distinct().forEach { mentionedUid ->
                    if (mentionedUid.isNotBlank() && mentionedUid != uid) {
                        sendNotif(mentionedUid, "mention", "$myName seni bir gönderide etiketledi", text.take(60), feedRef.id)
                    }
                }

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


    /** Kitap adına göre Supabase'den kapak resmini döndürür — QuoteDialog için */
    suspend fun findCoverImgByTitle(title: String): String {
        if (title.isBlank()) return ""
        return try {
            library.searchBooks(title)
                .firstOrNull { it.title.equals(title.trim(), ignoreCase = true) }
                ?.coverImg
                ?: library.searchBooks(title).firstOrNull()?.coverImg
                ?: ""
        } catch (_: Exception) { "" }
    }

    /** QuoteDialog için: kitap adı aratıp öneri döner — Supabase'den, canlı arama. */
    suspend fun searchBooksForQuote(query: String) = library.searchBooksForQuote(query)

    /** QuoteDialog için: yazar adı aratıp öneri döner — Supabase'den, canlı arama. */
    suspend fun searchAuthorsForQuote(query: String) = library.searchAuthorsForQuote(query)

    /** LegacyQuoteListPage için: kitap adına göre alıntıları Supabase'den çeker, Post'a çevirir. */
    suspend fun getQuotesAsPostsByBookName(bookName: String): List<Post> {
        return try {
            library.getActiveQuotesByBookName(bookName).map { q ->
                Post(
                    id          = q.feedPostId.ifBlank { q.id },
                    uid         = q.uid,
                    displayName = q.userDisplayName,
                    photoURL    = q.userPhotoUrl,
                    quoteText   = q.text,
                    bookName    = q.bookTitle,
                    authorName  = q.authorName,
                    coverImg    = q.coverImg,
                    ts          = parseSupabaseTimestamp(q.createdAt),
                )
            }
        } catch (_: Exception) { emptyList() }
    }

    /** LegacyQuoteListPage için: yazar adına göre alıntıları Supabase'den çeker, Post'a çevirir. */
    suspend fun getQuotesAsPostsByAuthorName(authorName: String): List<Post> {
        return try {
            library.getActiveQuotesByAuthorName(authorName).map { q ->
                Post(
                    id          = q.feedPostId.ifBlank { q.id },
                    uid         = q.uid,
                    displayName = q.userDisplayName,
                    photoURL    = q.userPhotoUrl,
                    quoteText   = q.text,
                    bookName    = q.bookTitle,
                    authorName  = q.authorName,
                    coverImg    = q.coverImg,
                    ts          = parseSupabaseTimestamp(q.createdAt),
                )
            }
        } catch (_: Exception) { emptyList() }
    }

    fun deletePost(postId: String) {
        if (uid.isEmpty()) return
        viewModelScope.launch {
            try {
                val postDoc = firestore.collection("feed").document(postId).get().await()
                if (postDoc.getString("uid") != uid) return@launch
                firestore.collection("feed").document(postId).delete().await()
                _posts.value = _posts.value.filter { it.id != postId }
                _libraryQuotes.value = _libraryQuotes.value.filter { it.id != postId }
                // FAZ 1 devamı: gönderi bir kitap alıntısı/incelemesiyse,
                // Supabase'deki karşılığı da silinsin — aksi halde alıntı
                // feed'den kaybolduğu halde Kütüphane ekranında görünmeye
                // devam ediyordu. Hangi tablo olduğunu bilmediğimiz için
                // ikisini de deniyoruz; feed_post_id eşleşmeyen tabloda
                // fonksiyon sessizce hiçbir şey yapmaz.
                library.deleteQuoteByFeedPostId(postId)
                library.deleteReviewByFeedPostId(postId)
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun editPost(postId: String, newTitle: String = "", newText: String) {
        if (uid.isEmpty() || newText.isBlank()) return
        viewModelScope.launch {
            try {
                val postDoc = firestore.collection("feed").document(postId).get().await()
                if (postDoc.getString("uid") != uid) return@launch
                firestore.collection("feed").document(postId)
                    .update(mapOf("text" to newText.trim(), "title" to newTitle.trim())).await()
                _posts.value = _posts.value.map { if (it.id == postId) it.copy(text = newText.trim(), title = newTitle.trim()) else it }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

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
                // Bağlı kitap alıntısı varsa (book_quotes) onu da güncelle — Supabase
                val feedPostSnap = firestore.collection("feed").document(postId).get().await()
                val libBookId = feedPostSnap.getString("libraryBookId")
                if (!libBookId.isNullOrBlank()) {
                    try {
                        library.updateQuoteTextByFeedPostId(postId, newQuoteText.trim())
                    } catch (_: Exception) {}
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    // ── Kullanıcı önerileri — sayfalama destekli ─────────────────────────────
    // SORUN 1: Eski kod postsCount'a göre sıralayıp ilk 100'ü çekiyordu.
    //          Bu yüzden yeni / az içerikli kullanıcılar hiç çıkmıyordu.
    // SORUN 2: followingUids sayfalanmıyordu; 500 takip üzerinde eksik kalıyordu.
    //
    // ÇÖZÜM:
    //   - Takip listesi sayfalanarak tam olarak çekilir (cursor-based)
    //   - Kullanıcılar followersCount + postsCount karması ile çekilir
    //   - Daha geniş havuz (200 doc) — daha fazla çeşitlilik
    //   - Yeni kullanıcıların da görünmesi için karışık sıra (shuffled)
    //   - banned == false filtresi client-side (Rules'da zaten kısıtlı)

    private var _suggestLastLoadMs : Long = 0L
    private val _hasMoreSuggestions = MutableStateFlow(false)
    val hasMoreSuggestions = _hasMoreSuggestions.asStateFlow()

    // Sayfalama: her sayfada en fazla 10 kullanıcı (OFFSET tabanlı, Supabase)
    private val _suggestCurrentPage = MutableStateFlow(0)
    val suggestCurrentPage = _suggestCurrentPage.asStateFlow()
    val SUGGEST_PAGE_SIZE = 10

    private var suggestionsLoaded = false

    fun loadSuggestedUsers(forceReload: Boolean = false) {
        val myUid = auth.currentUser?.uid ?: return
        val now = System.currentTimeMillis()
        val cacheValid = suggestionsLoaded
            && _suggestedUsers.value.isNotEmpty()
            && (now - _suggestLastLoadMs) < 600_000L   // 10 dk (önceden 1 saat — yeni üyeler geç görünüyordu)
        if (cacheValid && !forceReload) return
        _suggestLastLoadMs = now
        _suggestCurrentPage.value = 0
        viewModelScope.launch {
            try {
                // Takip listesini Supabase'den çek — _followingUids'i güncelle
                // limit(PAGE_SIZE) vardı (30) — 30+ kişi takip edince geri kalanlar
                // filtrelenmiyordu. Tüm takipleri çekmek için limit kaldırıldı.
                val followingUids = mutableSetOf<String>()
                followingUids.add(myUid)
                val followRows = supabase.postgrest["follows"]
                    .select { filter { eq("from_uid", myUid) }; limit(1000) }
                    .decodeList<FollowRow>()
                followRows.forEach { followingUids.add(it.targetUid) }
                _followingUids.value = followingUids

                fetchSuggestedUsersPage(page = 0)
                suggestionsLoaded = true
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun loadSuggestedUsersPage(page: Int) {
        if (page < 0) return
        viewModelScope.launch {
            try { fetchSuggestedUsersPage(page) }
            catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun loadNextSuggestedUsersPage() {
        val next = _suggestCurrentPage.value + 1
        if (_hasMoreSuggestions.value) loadSuggestedUsersPage(next)
    }

    fun loadPrevSuggestedUsersPage() {
        val prev = _suggestCurrentPage.value - 1
        if (prev >= 0) loadSuggestedUsersPage(prev)
    }

    // Supabase users tablosundan sayfalı sorgu.
    // Takip edilenler client-side filtreleniyor — postgrest-kt'de NOT IN
    // güvenilir çalışmadığı için, daha geniş bir set çekip filtreliyoruz.
    private suspend fun fetchSuggestedUsersPage(page: Int) {
        val myUid = auth.currentUser?.uid ?: return
        val excludeUids = (_followingUids.value + myUid).toSet()

        val pageUsers: List<SuggestedUser>
        val hasMore: Boolean

        if (page == 0) {
            // Havuz büyütüldü (x20): yeni üyelerin dahil olma şansı arttı.
            // eq("banned", false) → neq("banned", true): banned=null olan yeni
            // kayıtlar artık dışlanmıyor.
            val poolSize = (SUGGEST_PAGE_SIZE * 20 + excludeUids.size).coerceAtLeast(200).toLong()
            val rows = supabase.postgrest["users"].select {
                filter { neq("banned", true) }
                order("created_at", io.github.jan.supabase.postgrest.query.Order.DESCENDING)
                range(0, poolSize - 1)
            }.decodeList<com.heftreng.app.data.model.UserRow>()

            // displayName.isNotBlank() → uid.isNotBlank(): profil doldurmamış
            // yeni üyeler de önerilere girebilsin.
            val candidates = rows.filter { it.uid !in excludeUids && it.uid.isNotBlank() }
            pageUsers = candidates.shuffled().take(SUGGEST_PAGE_SIZE).map { row ->
                SuggestedUser(
                    uid         = row.uid,
                    name        = row.displayName.ifBlank { row.uid.take(8) },
                    photoURL    = row.photoUrl,
                    bio         = row.bio,
                    isFollowing = false,
                )
            }
            hasMore = candidates.size > SUGGEST_PAGE_SIZE
        } else {
            val offset = (page * SUGGEST_PAGE_SIZE).toLong()
            val fetchSize = (SUGGEST_PAGE_SIZE + excludeUids.size).coerceAtLeast(SUGGEST_PAGE_SIZE * 3).toLong()

            val rows = supabase.postgrest["users"].select {
                filter { neq("banned", true) }
                order("created_at", io.github.jan.supabase.postgrest.query.Order.DESCENDING)
                range(offset, offset + fetchSize - 1)
            }.decodeList<com.heftreng.app.data.model.UserRow>()

            pageUsers = rows
                .filter { it.uid !in excludeUids && it.uid.isNotBlank() }
                .take(SUGGEST_PAGE_SIZE)
                .map { row ->
                    SuggestedUser(
                        uid         = row.uid,
                        name        = row.displayName.ifBlank { row.uid.take(8) },
                        photoURL    = row.photoUrl,
                        bio         = row.bio,
                        isFollowing = false,
                    )
                }
            hasMore = rows.size >= fetchSize
        }

        _hasMoreSuggestions.value = hasMore
        _suggestedUsers.value = pageUsers
        _suggestCurrentPage.value = page
    }

    fun followSuggestedUser(targetUid: String) {
        val myUid = auth.currentUser?.uid ?: return

        // Optimistic update — kullanıcıyı listeden hemen çıkar (takip / istek her iki durumda da "halloldu")
        _suggestedUsers.value = _suggestedUsers.value.filter { it.uid != targetUid }
        _followingUids.value  = _followingUids.value + targetUid

        viewModelScope.launch {
            try {
                ensureMyProfileCached()
                val myName  = _cachedMyName
                val myPhoto = _cachedMyPhoto
                val tDoc      = firestore.collection("users").document(targetUid).get().await()
                val tName     = tDoc.getString("displayName") ?: tDoc.getString("name") ?: ""
                val tPhoto    = tDoc.getString("photoURL") ?: ""
                val tIsPrivate = tDoc.getBoolean("private") ?: false

                // ÖNCEKİ HATA: Bu fonksiyon hesap gizli olsun olmasın HER ZAMAN
                // doğrudan "follows" tablosuna yazıyordu — yani Önerilerden takip
                // edince gizli hesaplar onay beklemeden anında takip ediliyordu
                // (Profil ekranındaki "İstek Gönder" akışı tamamen bypass ediliyordu).
                // Artık Profil ekranıyla aynı mantık: gizliyse istek gönder.
                if (tIsPrivate) {
                    firestore.collection("followRequests").document(targetUid)
                        .collection("pending").document(myUid).set(mapOf(
                            "fromUid"   to myUid,
                            "fromName"  to myName,
                            "fromPhoto" to myPhoto,
                            "targetUid" to targetUid,
                            "ts"        to com.google.firebase.Timestamp.now(),
                        )).await()
                    sendNotif(targetUid, "follow_request", "$myName seni takip etmek istiyor", "", "")
                } else {
                    supabase.postgrest["follows"].upsert(
                        FollowRow(
                            id          = "${myUid}_${targetUid}",
                            fromUid     = myUid,
                            fromName    = myName,
                            fromPhoto   = myPhoto,
                            targetUid   = targetUid,
                            targetName  = tName,
                            targetPhoto = tPhoto,
                        )
                    )
                    firestore.collection("users").document(myUid)
                        .update("followingCount", com.google.firebase.firestore.FieldValue.increment(1))
                    firestore.collection("users").document(targetUid)
                        .update("followersCount", com.google.firebase.firestore.FieldValue.increment(1))
                    sendNotif(targetUid, "follow", "$myName sizi takip etmeye başladı", "", "")
                }
                // Cache'i geçersiz kıl — bir sonraki loadSuggestedUsers güncel listeyi çeker
                suggestionsLoaded = false
            } catch (e: Exception) {
                // Hata: geri al — kişiyi listeye tekrar ekle
                _followingUids.value  = _followingUids.value - targetUid
                loadSuggestedUsers(forceReload = true)
                e.printStackTrace()
            }
        }
    }

    fun getPostById(postId: String): Post? = _posts.value.find { it.id == postId }

    private val _fetchingPostIds = mutableSetOf<String>()

    fun ensurePost(postId: String) {
        // Post listede varsa cover eksikse enrich et, sonra dön
        val existing = _posts.value.find { it.id == postId }
        if (existing != null) {
            if (existing.coverImg.isBlank() && existing.bookName.isNotBlank()) {
                enrichMissingCovers(listOf(existing))
            }
            return
        }
        if (_fetchingPostIds.contains(postId)) return
        _fetchingPostIds.add(postId)
        if (_postNotFound.value == postId) _postNotFound.value = null
        viewModelScope.launch {
            try {
                // Ağ yavaş/yoksa sonsuza kadar "Yükleniyor..." ekranında kalmasın diye
                // önce cache'i dene, ardından timeout'lu şekilde server'ı dene.
                var doc = try {
                    firestore.collection("feed").document(postId).get(Source.CACHE).await()
                } catch (_: Exception) { null }

                var post = doc?.toPost()

                if (post == null) {
                    doc = withTimeoutOrNull(12_000L) {
                        firestore.collection("feed").document(postId).get(Source.SERVER).await()
                    }
                    post = doc?.toPost()
                }

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
                val d       = cachedUserDoc(uid) ?: emptyMap()
                val myName  = d["displayName"] as? String ?: d["name"] as? String
                    ?: auth.currentUser?.displayName ?: ""
                val myPhoto = d["photoURL"] as? String
                    ?: auth.currentUser?.photoUrl?.toString() ?: ""
                val preview = chapterBody.replace(Regex("<[^>]+>"), "").trim().take(200)
                firestore.collection("feed").add(mapOf(
                    "uid"          to uid,
                    "name"         to myName,
                    "displayName"  to myName,
                    "username"     to (d["username"] as? String ?: ""),
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

    // Kurdî dersini feed'de paylaş — repostBookChapter ile aynı desen (repostType: "kf_lesson")
    fun repostKfLesson(
        lessonId    : String,
        lessonTitle : String,   // nameTr veya nameKu (aktif dile göre çağıran taraf seçer)
        lessonTip   : String = "",
        unitTitle   : String = "",
        emoji       : String = "📖",
        onResult    : (Boolean) -> Unit = {},
    ) {
        if (uid.isEmpty()) { onResult(false); return }
        viewModelScope.launch {
            try {
                val d       = cachedUserDoc(uid) ?: emptyMap()
                val myName  = d["displayName"] as? String ?: d["name"] as? String
                    ?: auth.currentUser?.displayName ?: ""
                val myPhoto = d["photoURL"] as? String
                    ?: auth.currentUser?.photoUrl?.toString() ?: ""
                firestore.collection("feed").add(mapOf(
                    "uid"          to uid,
                    "name"         to myName,
                    "displayName"  to myName,
                    "username"     to (d["username"] as? String ?: ""),
                    "photoURL"     to myPhoto,
                    "text"         to "",
                    "imageURL"     to "",
                    "imgUrl"       to "",
                    "repostType"   to "kf_lesson",
                    "repostId"     to lessonId,
                    "repostTitle"  to "$emoji $lessonTitle",
                    "repostText"   to lessonTip,
                    "serialTitle"  to unitTitle,
                    "likes"  to 0, "saves" to 0, "cmtCount" to 0, "reposts" to 0,
                    "ts"     to Timestamp.now(),
                )).await()
                onResult(true)
            } catch (e: Exception) { e.printStackTrace(); onResult(false) }
        }
    }

    // Kurdî gramer kuralını feed'de paylaş — aynı desen (repostType: "grammar")
    fun repostGrammarRule(
        ruleId      : String,
        ruleTitle   : String,   // title veya titleTr (aktif dile göre çağıran taraf seçer)
        rulePreview : String = "",
        onResult    : (Boolean) -> Unit = {},
    ) {
        if (uid.isEmpty()) { onResult(false); return }
        viewModelScope.launch {
            try {
                val d       = cachedUserDoc(uid) ?: emptyMap()
                val myName  = d["displayName"] as? String ?: d["name"] as? String
                    ?: auth.currentUser?.displayName ?: ""
                val myPhoto = d["photoURL"] as? String
                    ?: auth.currentUser?.photoUrl?.toString() ?: ""
                val preview = rulePreview.replace(Regex("<[^>]+>"), "").trim().take(200)
                firestore.collection("feed").add(mapOf(
                    "uid"          to uid,
                    "name"         to myName,
                    "displayName"  to myName,
                    "username"     to (d["username"] as? String ?: ""),
                    "photoURL"     to myPhoto,
                    "text"         to "",
                    "imageURL"     to "",
                    "imgUrl"       to "",
                    "repostType"   to "grammar",
                    "repostId"     to ruleId,
                    "repostTitle"  to "📚 $ruleTitle",
                    "repostText"   to preview,
                    "likes"  to 0, "saves" to 0, "cmtCount" to 0, "reposts" to 0,
                    "ts"     to Timestamp.now(),
                )).await()
                onResult(true)
            } catch (e: Exception) { e.printStackTrace(); onResult(false) }
        }
    }

    // Kurdî öğrenme başarısını (seviye/XP/streak) feed'de paylaş — repostType: "kf_achievement"
    // Not: metin burada değil, görüntüleyenin diline göre FeedScreen'de üretilir (repostLevel/Xp/Streak sayısal alanlar).
    fun repostKfAchievement(
        level  : Int,
        xp     : Int,
        streak : Int,
        onResult : (Boolean) -> Unit = {},
    ) {
        if (uid.isEmpty()) { onResult(false); return }
        viewModelScope.launch {
            try {
                val d       = cachedUserDoc(uid) ?: emptyMap()
                val myName  = d["displayName"] as? String ?: d["name"] as? String
                    ?: auth.currentUser?.displayName ?: ""
                val myPhoto = d["photoURL"] as? String
                    ?: auth.currentUser?.photoUrl?.toString() ?: ""
                firestore.collection("feed").add(mapOf(
                    "uid"          to uid,
                    "name"         to myName,
                    "displayName"  to myName,
                    "username"     to (d["username"] as? String ?: ""),
                    "photoURL"     to myPhoto,
                    "text"         to "",
                    "imageURL"     to "",
                    "imgUrl"       to "",
                    "repostType"   to "kf_achievement",
                    "repostId"     to uid,
                    "repostLevel"  to level,
                    "repostXp"     to xp,
                    "repostStreak" to streak,
                    "likes"  to 0, "saves" to 0, "cmtCount" to 0, "reposts" to 0,
                    "ts"     to Timestamp.now(),
                )).await()
                onResult(true)
            } catch (e: Exception) { e.printStackTrace(); onResult(false) }
        }
    }

    // Kendi profilimizi cache'le — sendNotif her çağrıda Firestore okumaz
    private var _cachedMyName  : String = ""
    private var _cachedMyPhoto : String = ""
    private var _cachedMyUid   : String = ""

    private suspend fun ensureMyProfileCached() {
        val currentUid = auth.currentUser?.uid ?: return
        if (_cachedMyUid == currentUid && _cachedMyName.isNotBlank()) return
        val userDoc = firestore.collection("users").document(currentUid).get().await()
        val d       = userDoc.data ?: emptyMap<String, Any>()
        _cachedMyName  = d["displayName"] as? String ?: d["name"] as? String ?: auth.currentUser?.displayName ?: "Kullanıcı"
        _cachedMyPhoto = d["photoURL"] as? String ?: ""
        _cachedMyUid   = currentUid
    }

    private suspend fun sendNotif(toUid: String, type: String, title: String, sub: String = "", feedId: String = "") {
        try {
            ensureMyProfileCached()
            val fromName  = _cachedMyName.ifBlank { auth.currentUser?.displayName ?: "Kullanıcı" }
            val fromPhoto = _cachedMyPhoto
            // title içindeki isim boşsa _cachedMyName ile yeniden oluştur
            val resolvedTitle = if (title.startsWith(" ")) {
                when (type) {
                    "like"             -> "$fromName gönderini beğendi"
                    "cmt", "comment"   -> "$fromName gönderine yorum yaptı"
                    "repost"           -> "$fromName gönderini paylaştı"
                    "follow"           -> "$fromName sizi takip etmeye başladı"
                    else                -> title.trim()
                }
            } else title
            val ico = when (type) { "like" -> "favorite"; "cmt" -> "chat_bubble"; "follow" -> "person_add"; "repost" -> "repeat"; else -> "notifications" }
            // Tek yazma — onNewNotif Cloud Function trigger'ı bu eklemeyi yakalayıp
            // otomatik FCM push gönderir. Burada manuel sendPush ÇAĞIRMA — çift
            // bildirime ve gecikmeye sebep olur.
            firestore.collection("userNotifs").document(toUid).collection("msgs").add(mapOf(
                "fromUid"   to uid,
                "fromName"  to fromName,
                "fromPhoto" to fromPhoto,
                "type"      to type,
                "feedId"    to feedId,
                "postId"    to feedId,
                "title"     to resolvedTitle,
                "sub"       to sub,
                "ico"       to ico,
                "message"   to resolvedTitle,
                "url"       to "",
                "read"      to false,
                "ts"        to Timestamp.now(),
            )).await()
        } catch (e: Exception) { e.printStackTrace() }
    }

    // Keşfet → Alıntılar şeridi.
    // ÖNCEKİ (2 Firestore okuması/açılış): feed type='library_quote' (sınırsız) +
    //   feed son 300 belge taraması (eski format alıntılar için).
    // ŞİMDİ: Supabase book_quotes (RLS public read, 1 sorgu) — addBookQuote() artık
    //   her alıntıyı buraya da yazıyor, kapak/yazar/kitap bilgisiyle katalog bağlantılı.
    //   Not: book_quotes'a taşınmadan önceki çok eski, kataloğa bağlı olmayan
    //   alıntılar bu agregasyonda görünmez (yine de kullanıcı profillerinde durur).
    suspend fun loadLibraryQuotesAsync() {
        val result = mutableListOf<Post>()

        try {
            // Sayfa boyutundan 1 fazla çek — daha fazlası var mı bileceğiz
            val rows = library.getRecentQuotes(LIBRARY_PAGE_SIZE + 1)
            rows.take(LIBRARY_PAGE_SIZE).forEach { row ->
                if (row.feedPostId.isBlank() || row.text.isBlank()) return@forEach
                result.add(row.toPost())
            }
            _libraryHasMore.value = rows.size > LIBRARY_PAGE_SIZE
        } catch (e: Exception) {
            android.util.Log.w("FeedVM", "loadLibraryQuotes book_quotes: ${e.message}")
            _libraryHasMore.value = false
        }

        val sorted = result

        // ── Öncelik 4: Offline cache (Room) ──────────────────────────────────
        if (sorted.isNotEmpty()) {
            // Önce ham veriyi göster (likesCount = book_quotes.likes_count, isLikedByMe = false)
            _libraryQuotes.value = sorted
            _libraryQuotesOffline.value = false
            try {
                quoteDao.replaceAll(sorted.take(LIBRARY_PAGE_SIZE).map { it.toCachedQuote() })
            } catch (e: Exception) {
                android.util.Log.w("FeedVM", "quoteDao.replaceAll: ${e.message}")
            }
            // Gerçek beğeni sayısını ve kullanıcının beğenip beğenmediğini
            // feed_likes tablosundan çekip state'e yansıt.
            // book_quotes.likes_count artık yazılmıyor (feed_likes tek kaynak).
            syncLibraryQuoteLikeStates(sorted)
            // Eski kayıtlarda cover_img boş olabilir — Supabase library_books'tan tamamla.
            enrichMissingCovers(sorted)
        } else {
            // Ağ sonucu boş — internet yoksa son önbelleğe düş
            try {
                val cached = quoteDao.getCachedQuotes(LIBRARY_PAGE_SIZE)
                if (cached.isNotEmpty()) {
                    _libraryQuotes.value = cached.map { it.toPost() }
                    _libraryQuotesOffline.value = true
                } else {
                    _libraryQuotes.value = emptyList()
                    _libraryQuotesOffline.value = false
                }
            } catch (e: Exception) {
                android.util.Log.w("FeedVM", "quoteDao.getCachedQuotes: ${e.message}")
                _libraryQuotes.value = emptyList()
            }
        }
    }

    fun loadLibraryQuotes() {
        viewModelScope.launch { loadLibraryQuotesAsync() }
    }

    /** Kütüphane alıntıları için "Daha Fazla Göster" — offset ile bir sonraki sayfa */
    fun loadMoreLibraryQuotes() {
        if (_libraryLoadingMore.value || !_libraryHasMore.value) return
        viewModelScope.launch {
            _libraryLoadingMore.value = true
            try {
                val currentSize = _libraryQuotes.value.size
                // Supabase offset: mevcut liste boyutu kadar atla, 1 fazla çek
                val rows = library.getRecentQuotes(LIBRARY_PAGE_SIZE + 1, offset = currentSize)
                val newPosts = rows.take(LIBRARY_PAGE_SIZE)
                    .filter { it.feedPostId.isNotBlank() && it.text.isNotBlank() }
                    .map { it.toPost() }
                _libraryHasMore.value = rows.size > LIBRARY_PAGE_SIZE
                if (newPosts.isNotEmpty()) {
                    val merged = _libraryQuotes.value + newPosts
                    _libraryQuotes.value = merged
                    syncLibraryQuoteLikeStates(newPosts)
                    enrichMissingCovers(newPosts)
                }
            } catch (e: Exception) {
                android.util.Log.w("FeedVM", "loadMoreLibraryQuotes: ${e.message}")
            } finally {
                _libraryLoadingMore.value = false
            }
        }
    }

    /**
     * feed_likes tablosundan _libraryQuotes için gerçek beğeni sayısını ve
     * kullanıcının beğenip beğenmediğini çekip state'e yansıtır.
     * book_quotes.likes_count artık güncellenmediğinden bu tek doğru kaynaktır.
     *
     * İki sorgu: tüm satırlar (sayım için) + kullanıcının beğendikleri.
     * groupBy Supabase Kotlin client'ta desteklenmediğinden client-side sayım yapılır.
     */
    private suspend fun syncLibraryQuoteLikeStates(quotes: List<Post>) {
        val currentUid = auth.currentUser?.uid ?: return
        val postIds = quotes.map { it.id }.filter { it.isNotBlank() }
        if (postIds.isEmpty()) return
        try {
            // Tüm beğenileri çek — hem sayım hem de isLikedByMe için
            val allLikes = supabase.postgrest["feed_likes"]
                .select { filter { isIn("post_id", postIds) } }
                .decodeList<FeedLikeRow>()

            // Client-side gruplama
            val countMap   = allLikes.groupingBy { it.postId }.eachCount()
            val likedByMe  = allLikes.filter { it.uid == currentUid }.map { it.postId }.toSet()

            // State'i güncelle
            _libraryQuotes.value = _libraryQuotes.value.map { post ->
                post.copy(
                    likesCount  = countMap[post.id] ?: post.likesCount,
                    isLikedByMe = post.id in likedByMe,
                )
            }
        } catch (e: Exception) {
            android.util.Log.w("FeedVM", "syncLibraryQuoteLikeStates: ${e.message}")
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Arkadaşlar ne okuyor? — reading_status (Supabase) + follows (isim/foto)
    //  Yalnızca 'okuyorum' durumundaki kayıtlar; current_page varsa gösterilir.
    // ══════════════════════════════════════════════════════════════════════

    fun loadFriendsReading() {
        val myUid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                val followRows = supabase.postgrest["follows"]
                    .select { filter { eq("from_uid", myUid) }; limit(PAGE_SIZE) }
                    .decodeList<FollowRow>()
                if (followRows.isEmpty()) { _friendsReading.value = emptyList(); return@launch }

                val nameByUid  = followRows.associate { it.targetUid to it.targetName }
                val photoByUid = followRows.associate { it.targetUid to it.targetPhoto }
                val uids       = followRows.map { it.targetUid }

                val rows = library.getReadingStatusForUids(uids, status = "okuyorum", limit = 20)
                _friendsReading.value = rows.map { row ->
                    com.heftreng.app.data.model.FriendReadingItem(
                        uid         = row.uid,
                        name        = nameByUid[row.uid] ?: "",
                        photoURL    = photoByUid[row.uid] ?: "",
                        bookId      = row.bookId,
                        title       = row.title,
                        coverImg    = row.coverImg,
                        authorName  = row.authorName,
                        source      = row.source,
                        currentPage = row.currentPage,
                    )
                }
            } catch (e: Exception) {
                android.util.Log.w("FeedVM", "loadFriendsReading: ${e.message}")
            }
        }
    }
}
