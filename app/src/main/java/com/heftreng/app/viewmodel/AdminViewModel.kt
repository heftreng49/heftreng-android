package com.heftreng.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.heftreng.app.data.model.User
import com.heftreng.app.data.model.FollowRow
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

// ══════════════════════════════════════════════════════════════════════
//  ÖZELLEŞTIRILEBILIR İZİN SİSTEMİ
//
//  Firestore: admins/{uid} → {
//    title       : "Kürtçe İçerik Editörü"   (görev adı, admin yazar)
//    permissions : ["pending","library","kurdi"]  (hangi sekmelere erişir)
//    addedAt     : timestamp
//    addedBy     : uid
//  }
//
//  İzin anahtarları:
//    push        → Push bildirimi
//    notif       → Sistem bildirimi
//    users       → Kullanıcılar (görüntüleme + ban)
//    pending     → Bekleyen gönderiler
//    reports     → Şikayetler
//    appeals     → İtirazlar
//    stats       → İstatistik
//    edit        → Düzenle (feed moderasyon)
//    library     → Kütüphane
//    kurdi       → Kürtçe admin ekranı
//    staff       → Yardımcılar (sadece admin)
// ══════════════════════════════════════════════════════════════════════
private const val ADMIN_EMAIL = "siirgibi49@gmail.com"

// Tüm izin anahtarları ve kullanıcıya gösterilen adları
val ALL_PERMISSIONS = linkedMapOf(
    "push"     to "Push Bildirimi",
    "notif"    to "Sistem Bildirimi",
    "users"    to "Kullanıcılar",
    "pending"  to "Bekleyen Gönderiler",
    "reports"  to "Şikayetler",
    "appeals"  to "İtirazlar",
    "stats"    to "İstatistik",
    "edit"     to "Düzenle",
    "library"  to "Kütüphane",
    "kurdi"    to "Kürtçe Admin",
    "staff"    to "Yardımcılar",
)

// Firestore role → permissions dönüşümü (Rules v10 ile senkronize)
fun roleToPermissions(role: String): Set<String> = when (role) {
    "admin"       -> ALL_PERMISSIONS.keys.toSet()
    "moderator"   -> setOf("users", "pending", "reports", "appeals", "edit")
    "editor"      -> setOf("push", "notif", "pending", "stats", "edit")
    "kutuphaneci" -> setOf("library", "kurdi")
    else          -> emptySet()
}

data class StaffPermissions(
    val uid         : String       = "",
    val title       : String       = "",          // görev adı
    val permissions : Set<String>  = emptySet(),  // izin anahtarları
) {
    fun can(key: String) = permissions.contains(key)
    fun isStaff()        = permissions.isNotEmpty()
}

@HiltViewModel
class AdminViewModel @Inject constructor(
    private val auth     : FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val supabase : io.github.jan.supabase.SupabaseClient,
    private val libraryRepository: com.heftreng.app.data.repository.LibraryRepository,
) : ViewModel() {

    // null = henüz yüklenmedi (loading), StaffPermissions() = yüklendi ama yetkisiz
    private val _perms = MutableStateFlow<StaffPermissions?>(null)
    val perms = _perms.asStateFlow()

    // Geriye dönük uyumluluk — null iken false döner
    val isAdmin: StateFlow<Boolean> = _perms.map { it?.isStaff() == true }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val isSuperAdmin: StateFlow<Boolean> = _perms.map { it?.can("staff") == true }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    init {
        // _perms null'dan bir değere geçince (checkAdmin tamamlandı) veriyi yükle
        viewModelScope.launch {
            _perms.filterNotNull().collect { p ->
                if (p.isStaff()) {
                    if (p.can("users"))   loadUsers()
                    if (p.can("pending")) loadPendingPosts()
                    if (p.can("stats"))   startStatsListener()
                    if (p.can("edit"))    loadFeedPosts()
                    if (p.can("reports")) loadReports()
                    if (p.can("appeals")) loadAppeals()
                    if (p.can("staff"))   loadStaff()
                }
            }
        }
    }

    private val _users        = MutableStateFlow<List<User>>(emptyList())
    val users = _users.asStateFlow()

    private val _pendingPosts = MutableStateFlow<List<Map<String, Any>>>(emptyList())
    val pendingPosts = _pendingPosts.asStateFlow()

    private val _loading      = MutableStateFlow(false)
    val loading = _loading.asStateFlow()

    private val _pushResult   = MutableStateFlow("")
    val pushResult = _pushResult.asStateFlow()

    // ══════════════════════════════════════════════════════════════════════
    //  Günlük bildirimler — "Günün Alıntısı" / "Günün Kelimesi"
    //  Firestore: daily_quote/{date}, daily_word/{date}  (date = yyyy-MM-dd)
    //  Admin içeriği düzenler ve "Bildirim Gönder" ile FCM tetikler.
    // ══════════════════════════════════════════════════════════════════════
    data class DailyQuoteContent(
        val textTr  : String = "",
        val textKu  : String = "",
        val author  : String = "",
        val book    : String = "",
        val feedPostId: String = "",
    )
    data class DailyWordContent(
        val word     : String = "",
        val meaningTr: String = "",
        val meaningKu: String = "",
        val exampleKu: String = "",
    )

    private val _dailyQuote = MutableStateFlow(DailyQuoteContent())
    val dailyQuote = _dailyQuote.asStateFlow()

    private val _dailyWord = MutableStateFlow(DailyWordContent())
    val dailyWord = _dailyWord.asStateFlow()

    private val _dailyResult = MutableStateFlow("")
    val dailyResult = _dailyResult.asStateFlow()

    private fun todayKey(): String {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        return sdf.format(java.util.Date())
    }

    /** Bugünün günün alıntısı / günün kelimesi içeriğini yükle (varsa) */
    fun loadDailyContent() {
        if (_perms.value?.can("notif") != true && _perms.value?.can("push") != true) return
        val date = todayKey()
        viewModelScope.launch {
            try {
                val qDoc = firestore.collection("daily_quote").document(date).get().await()
                if (qDoc.exists()) {
                    _dailyQuote.value = DailyQuoteContent(
                        textTr = qDoc.getString("text_tr") ?: "",
                        textKu = qDoc.getString("text_ku") ?: "",
                        author = qDoc.getString("author_name") ?: "",
                        book   = qDoc.getString("book_name") ?: "",
                        feedPostId = qDoc.getString("feed_post_id") ?: "",
                    )
                }
            } catch (e: Exception) { e.printStackTrace() }
            try {
                val wDoc = firestore.collection("daily_word").document(date).get().await()
                if (wDoc.exists()) {
                    _dailyWord.value = DailyWordContent(
                        word      = wDoc.getString("word") ?: "",
                        meaningTr = wDoc.getString("meaning_tr") ?: "",
                        meaningKu = wDoc.getString("meaning_ku") ?: "",
                        exampleKu = wDoc.getString("example_ku") ?: "",
                    )
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    /** Günün Alıntısı içeriğini kaydet (daily_quote/{date}) — admin düzenler */
    fun saveDailyQuote(content: DailyQuoteContent, date: String = todayKey()) {
        if (_perms.value?.can("notif") != true && _perms.value?.can("push") != true) return
        viewModelScope.launch {
            try {
                firestore.collection("daily_quote").document(date).set(mapOf(
                    "text_tr"     to content.textTr,
                    "text_ku"     to content.textKu,
                    "author_name" to content.author,
                    "book_name"   to content.book,
                    "feed_post_id" to content.feedPostId,
                    "updatedBy"   to (auth.currentUser?.email ?: "admin"),
                    "updatedAt"   to FieldValue.serverTimestamp(),
                )).await()
                _dailyQuote.value = content
                _dailyResult.value = "✓ Günün Alıntısı kaydedildi"
            } catch (e: Exception) { _dailyResult.value = "✗ Hata: ${e.message}" }
        }
    }

    /** Günün Kelimesi içeriğini kaydet (daily_word/{date}) — admin düzenler */
    fun saveDailyWord(content: DailyWordContent, date: String = todayKey()) {
        if (_perms.value?.can("notif") != true && _perms.value?.can("push") != true) return
        viewModelScope.launch {
            try {
                firestore.collection("daily_word").document(date).set(mapOf(
                    "word"       to content.word,
                    "meaning_tr" to content.meaningTr,
                    "meaning_ku" to content.meaningKu,
                    "example_ku" to content.exampleKu,
                    "updatedBy"  to (auth.currentUser?.email ?: "admin"),
                    "updatedAt"  to FieldValue.serverTimestamp(),
                )).await()
                _dailyWord.value = content
                _dailyResult.value = "✓ Günün Kelimesi kaydedildi"
            } catch (e: Exception) { _dailyResult.value = "✗ Hata: ${e.message}" }
        }
    }

    /** Günün Alıntısı'nı kaydet + herkese push gönder ("Tetikle") */
    fun saveDailyQuoteAndNotify(content: DailyQuoteContent, date: String = todayKey()) {
        saveDailyQuote(content, date)
        // Title: kısa — yazar ve kitap bilgisi
        val meta = listOfNotNull(
            content.author.takeIf { it.isNotBlank() },
            content.book.takeIf { it.isNotBlank() },
        ).joinToString(" · ")
        val title = buildString {
            append("📖 Günün Alıntısı")
            if (meta.isNotBlank()) append(" — $meta")
        }
        // Body: sadece alıntı metni — tam, kesilmeden. data payload'da limit yok.
        // TR + KU varsa ikisi de, yoksa sadece TR.
        val body = buildString {
            append("\u201C${content.textTr}\u201D")
            if (content.textKu.isNotBlank()) {
                append("\n\n\u201C${content.textKu}\u201D")
            }
        }
        val url = if (content.feedPostId.isNotBlank())
            "https://heft-reng.blogspot.com/p/akis_01024829108.html?postId=${content.feedPostId}"
        else
            "heftreng://daily_quote"
        sendPush(
            title  = title,
            body   = body,
            url    = url,
            postId = content.feedPostId,
            type   = "daily_quote",
            ico    = "format_quote",
        )
        // Bildirimler ekranında kart tıklanınca orijinal gönderiye gitsin.
        // sendBroadcastPush sadece FCM push atar, userNotifs yazmaz.
        // Genel notifications koleksiyonuna da yaz — NotificationsScreen buradan okur.
        if (content.feedPostId.isNotBlank()) {
            viewModelScope.launch {
                try {
                    firestore.collection("notifications").add(mapOf(
                        "type"      to "daily_quote",
                        "feedId"    to content.feedPostId,
                        "postId"    to content.feedPostId,
                        "title"     to title,
                        "sub"       to body,
                        "message"   to title,
                        "ico"       to "format_quote",
                        "url"       to url,
                        "read"      to false,
                        "ts"        to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                    )).await()
                } catch (e: Exception) { e.printStackTrace() }
            }
        }
    }

    /** Günün Kelimesi'ni kaydet + herkese push gönder ("Tetikle") */
    fun saveDailyWordAndNotify(content: DailyWordContent, date: String = todayKey()) {
        saveDailyWord(content, date)
        // Title: kelime + TR anlam (kısa)
        val title = "📝 ${content.word} — ${content.meaningTr}"
        // Body: KU anlam + örnek cümle tam olarak
        val body = buildString {
            if (content.meaningKu.isNotBlank()) append("Kurdî: ${content.meaningKu}")
            if (content.exampleKu.isNotBlank()) {
                if (isNotEmpty()) append("\n")
                append("\u201C${content.exampleKu}\u201D")
            }
        }.ifBlank { "${content.word} — ${content.meaningTr}" }
        sendPush(
            title = title,
            body  = body,
            url   = "heftreng://daily_word",
            type  = "daily_word",
            ico   = "translate",
        )
    }

    fun clearDailyResult() { _dailyResult.value = "" }

    // ── Supabase book_quotes'tan alıntı arama — Admin "Alıntılar'dan Seç" için ──
    private val _quoteSearchResults = MutableStateFlow<List<com.heftreng.app.data.model.BookQuote>>(emptyList())
    val quoteSearchResults = _quoteSearchResults.asStateFlow()
    private val _quoteSearchLoading = MutableStateFlow(false)
    val quoteSearchLoading = _quoteSearchLoading.asStateFlow()

    fun searchBookQuotes(query: String) {
        viewModelScope.launch {
            _quoteSearchLoading.value = true
            try {
                val rows = if (query.isBlank()) {
                    // Boş sorgu → en çok beğenilen 30 alıntı
                    supabase.postgrest["book_quotes"]
                        .select {
                            order("likes_count", io.github.jan.supabase.postgrest.query.Order.DESCENDING)
                            limit(30)
                        }.decodeList<com.heftreng.app.data.repository.BookQuoteRow>()
                } else {
                    // Arama: text, author_name veya book_title içinde geçiyorsa getir
                    // postgrest or() filter: "text.ilike.%q%,author_name.ilike.%q%,book_title.ilike.%q%"
                    // Supabase postgrest-kt v3: or() yerine ayrı ilike sorguları + client merge
                    val byText = supabase.postgrest["book_quotes"].select {
                        filter { ilike("text", "%$query%") }
                        order("likes_count", io.github.jan.supabase.postgrest.query.Order.DESCENDING)
                        limit(15)
                    }.decodeList<com.heftreng.app.data.repository.BookQuoteRow>()

                    val byAuthor = supabase.postgrest["book_quotes"].select {
                        filter { ilike("author_name", "%$query%") }
                        order("likes_count", io.github.jan.supabase.postgrest.query.Order.DESCENDING)
                        limit(15)
                    }.decodeList<com.heftreng.app.data.repository.BookQuoteRow>()

                    val byBook = supabase.postgrest["book_quotes"].select {
                        filter { ilike("book_title", "%$query%") }
                        order("likes_count", io.github.jan.supabase.postgrest.query.Order.DESCENDING)
                        limit(15)
                    }.decodeList<com.heftreng.app.data.repository.BookQuoteRow>()

                    (byText + byAuthor + byBook)
                        .distinctBy { it.id }
                        .sortedByDescending { it.likesCount }
                }
                _quoteSearchResults.value = rows.map { row ->
                    com.heftreng.app.data.model.BookQuote(
                        id         = row.id,
                        text       = row.text,
                        authorName = row.authorName,
                        bookTitle  = row.bookTitle,
                        likesCount = row.likesCount,
                        feedPostId = row.feedPostId,
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _quoteSearchResults.value = emptyList()
            } finally {
                _quoteSearchLoading.value = false
            }
        }
    }

    fun clearQuoteSearch() { _quoteSearchResults.value = emptyList() }

    data class PlatformStats(
        val totalUsers    : Int  = 0, val androidUsers  : Int  = 0, val webUsers      : Int  = 0,
        val onlineNow     : Int  = 0, val newUsersToday : Int  = 0, val totalPosts    : Int  = 0,
        val newPostsToday : Int  = 0, val totalQuotes   : Int  = 0, val totalReviews  : Int  = 0,
        val totalComments : Int  = 0, val totalSerials  : Int  = 0, val totalBooks    : Int  = 0,
        val pendingPosts  : Int  = 0, val pendingReports: Int  = 0, val bannedUsers   : Int  = 0,
        val lastUpdated   : Long = 0L,
    )
    private val _platformStats = MutableStateFlow(PlatformStats())
    val platformStats = _platformStats.asStateFlow()

    private val _stats        = MutableStateFlow<Map<String, Int>>(emptyMap())
    val stats = _stats.asStateFlow()

    private var statsRefreshJob: kotlinx.coroutines.Job? = null

    data class UserActivity(
        val uid         : String  = "", val displayName : String  = "", val photoURL    : String  = "",
        val online      : Boolean = false, val lastSeenMs  : Long    = 0L, val appVersion  : String  = "",
        val platform    : String  = "", val postsCount  : Int     = 0, val level       : Int     = 1,
    )
    private val _activeUsers  = MutableStateFlow<List<UserActivity>>(emptyList())
    val activeUsers = _activeUsers.asStateFlow()

    private val _statsLoading = MutableStateFlow(false)
    val statsLoading = _statsLoading.asStateFlow()

    // ── Yardımcı listesi ──────────────────────────────────────────────────────
    data class StaffMember(
        val uid         : String      = "",
        val displayName : String      = "",
        val photoURL    : String      = "",
        val email       : String      = "",
        val title       : String      = "",
        val permissions : Set<String> = emptySet(),
        val addedAt     : Long        = 0L,
    )
    private val _staffList = MutableStateFlow<List<StaffMember>>(emptyList())
    val staffList = _staffList.asStateFlow()

    // loadStaff() Firestore'dan okurken hata alırsa (ör. permission-denied)
    // burada tutulur, ekran "Henüz yardımcı eklenmemiş" yerine gerçek
    // hatayı gösterebilsin diye.
    private val _staffLoadError = MutableStateFlow<String?>(null)
    val staffLoadError = _staffLoadError.asStateFlow()

    // ── Oturum açan kullanıcının izinlerini yükle ─────────────────────────────
    fun checkAdmin() {
        viewModelScope.launch {
            _perms.value = null // yükleniyor
            val user = auth.currentUser ?: run {
                _perms.value = StaffPermissions()
                return@launch
            }
            try {
                val doc = firestore.collection("admins").document(user.uid).get().await()
                if (doc.exists()) {
                    val role  = doc.getString("role") ?: "none"
                    val title = doc.getString("title") ?: role.replaceFirstChar { it.uppercase() }
                    // Hem role hem eski permissions array desteklenir.
                    // Güvenlik: "admin" rolü hariç, ham permissions listesi role'ün
                    // gerçekten izin verdiği kümenin dışına taşamaz — eski/bozuk
                    // dökümanlarda (ör. moderator + "push") sızmış fazladan izinler
                    // burada otomatik olarak süzülür (Firestore'a geri yazılmaz,
                    // sadece bu oturumda uygulanır; kalıcı düzeltme için doküman
                    // Firestore konsolundan veya Yardımcılar ekranından güncellenmeli).
                    @Suppress("UNCHECKED_CAST")
                    val legacy = doc.get("permissions") as? List<String>
                    val rawPerms = if (!legacy.isNullOrEmpty()) legacy.toSet()
                                   else roleToPermissions(role)
                    // Güvenlik: "admin" hariç her role, o role'ün gerçekten izin
                    // verdiği kümeyle SIKI şekilde sınırlanır. Önceki mantıkta
                    // roleToPermissions(role) boş dönerse (ör. role alanı eksik/
                    // "none"/bozuk) rawPerms hiç kırpılmadan kabul ediliyordu —
                    // bu da eski/bozuk dökümanlarda push gibi izinlerin sızmasına
                    // yol açıyordu. Artık tanınmayan/eksik role için de izin
                    // kümesi boş kabul edilir (fail-closed).
                    val perms = if (role == "admin") rawPerms
                                else rawPerms.intersect(roleToPermissions(role))
                    _perms.value = StaffPermissions(uid = user.uid, title = title, permissions = perms)
                } else {
                    // Belge yok — email sahibiyse otomatik oluştur
                    if (user.email == ADMIN_EMAIL) {
                        try {
                            firestore.collection("admins").document(user.uid).set(mapOf(
                                "role"    to "admin",
                                "title"   to "Super Admin",
                                "addedAt" to com.google.firebase.Timestamp.now(),
                                "addedBy" to user.uid,
                            )).await()
                        } catch (_: Exception) {}
                        _perms.value = StaffPermissions(
                            uid = user.uid, title = "Super Admin",
                            permissions = ALL_PERMISSIONS.keys.toSet(),
                        )
                    } else {
                        _perms.value = StaffPermissions()
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("AdminVM", "checkAdmin: ${e.message}")
                // Firestore hatası — email fallback
                _perms.value = if (user.email == ADMIN_EMAIL)
                    StaffPermissions(uid = user.uid, title = "Admin", permissions = ALL_PERMISSIONS.keys.toSet())
                else StaffPermissions()
            }
        }
    }

    // ── Yardımcı yönetimi ─────────────────────────────────────────────────────
    fun loadStaff() {
        if (_perms.value?.can("staff") != true) return
        viewModelScope.launch {
            _staffLoadError.value = null
            try {
                val snap = firestore.collection("admins").limit(50).get().await()
                _staffList.value = snap.documents.mapNotNull { doc ->
                    val uid     = doc.id
                    @Suppress("UNCHECKED_CAST")
                    val permList = doc.get("permissions") as? List<String> ?: emptyList()
                    // addedAt Firestore'da Timestamp olarak yazılıyor (bkz. addStaff),
                    // getLong() ile okunursa tip uyuşmazlığından dolayı hep null/0
                    // dönüyordu ve sıralama bozuluyordu — getTimestamp() kullanılmalı.
                    val addedAt  = doc.getTimestamp("addedAt")?.toDate()?.time ?: 0L
                    val title    = doc.getString("title") ?: ""
                    val userDoc  = try { firestore.collection("users").document(uid).get().await() } catch (_: Exception) { null }
                    StaffMember(
                        uid         = uid,
                        displayName = userDoc?.getString("displayName") ?: userDoc?.getString("name") ?: uid.take(12),
                        photoURL    = userDoc?.getString("photoURL") ?: "",
                        email       = userDoc?.getString("email") ?: "",
                        title       = title,
                        permissions = permList.toSet(),
                        addedAt     = addedAt,
                    )
                }.sortedByDescending { it.addedAt }
            } catch (e: Exception) {
                // Artık sessizce yutulmuyor — ekran tarafı staffLoadError'ı
                // gözlemleyip kullanıcıya gösterebilir (ör. Firestore rules
                // reddi burada yakalanır, "Henüz yardımcı eklenmemiş" ile
                // karıştırılmaz).
                android.util.Log.w("AdminVM", "loadStaff failed: ${e.message}", e)
                _staffLoadError.value = e.message ?: "Yardımcılar yüklenemedi"
            }
        }
    }

    fun addStaff(uid: String, title: String, permissions: Set<String>, onResult: (Boolean, String) -> Unit) {
        if (_perms.value?.can("staff") != true)  { onResult(false, "Yetkisiz işlem"); return }
        if (uid.isBlank())               { onResult(false, "UID boş olamaz"); return }
        if (permissions.isEmpty())       { onResult(false, "En az bir izin seçmelisin"); return }
        viewModelScope.launch {
            try {
                // permissions'dan en uygun role'ü çıkar
                val inferredRole = when {
                    permissions.containsAll(ALL_PERMISSIONS.keys) -> "admin"
                    permissions.any { it in setOf("users","reports","appeals") } -> "moderator"
                    permissions.any { it in setOf("push","notif","stats") } -> "editor"
                    permissions.any { it in setOf("library","kurdi") } -> "kutuphaneci"
                    else -> "moderator"
                }
                // ── Güvenlik: izinler, çıkarılan role'ün izin verdiği kümenin
                // dışına çıkamaz (ör. "moderator" seçilip "push" işaretlenemez).
                // "admin" her izni alabilir; diğer roller kendi sabit kümesiyle sınırlı.
                val allowedForRole = if (inferredRole == "admin") ALL_PERMISSIONS.keys.toSet()
                                     else roleToPermissions(inferredRole)
                val clampedPermissions = permissions.intersect(allowedForRole)
                if (clampedPermissions.isEmpty()) {
                    onResult(false, "Seçilen izinler bu rol için geçersiz"); return@launch
                }
                firestore.collection("admins").document(uid.trim()).set(mapOf(
                    "role"        to inferredRole,
                    "title"       to title.ifBlank { "Yardımcı" },
                    "permissions" to clampedPermissions.toList(),
                    "addedBy"     to (auth.currentUser?.uid ?: ""),
                    "addedAt"     to com.google.firebase.Timestamp.now(),
                )).await()
                onResult(true, "✓ Yardımcı eklendi")
                loadStaff()
            } catch (e: Exception) { onResult(false, "✗ ${e.message}") }
        }
    }

    fun updateStaff(uid: String, title: String, permissions: Set<String>, onResult: (Boolean, String) -> Unit) {
        if (_perms.value?.can("staff") != true)    { onResult(false, "Yetkisiz işlem"); return }
        if (uid == auth.currentUser?.uid)  { onResult(false, "Kendi iznini değiştiremezsin"); return }
        if (permissions.isEmpty())         { onResult(false, "En az bir izin seçmelisin"); return }
        viewModelScope.launch {
            try {
                // Mevcut role'ü oku — güncellemede role değişmiyor, sadece
                // izinler o role'ün izin verdiği kümeyle sınırlanıyor (bkz. addStaff).
                val doc  = firestore.collection("admins").document(uid).get().await()
                val role = doc.getString("role") ?: "moderator"
                val allowedForRole = if (role == "admin") ALL_PERMISSIONS.keys.toSet()
                                     else roleToPermissions(role)
                val clampedPermissions = permissions.intersect(allowedForRole)
                if (clampedPermissions.isEmpty()) {
                    onResult(false, "Seçilen izinler bu rol için geçersiz"); return@launch
                }
                firestore.collection("admins").document(uid).update(mapOf(
                    "title"       to title.ifBlank { "Yardımcı" },
                    "permissions" to clampedPermissions.toList(),
                )).await()
                onResult(true, "✓ Güncellendi")
                loadStaff()
            } catch (e: Exception) { onResult(false, "✗ ${e.message}") }
        }
    }

    fun removeStaff(uid: String, onResult: (Boolean, String) -> Unit) {
        if (_perms.value?.can("staff") != true)   { onResult(false, "Yetkisiz işlem"); return }
        if (uid == auth.currentUser?.uid) { onResult(false, "Kendinizi silemezsiniz"); return }
        viewModelScope.launch {
            try {
                firestore.collection("admins").document(uid).delete().await()
                onResult(true, "✓ Kaldırıldı")
                loadStaff()
            } catch (e: Exception) { onResult(false, "✗ ${e.message}") }
        }
    }

    // ── Kullanıcıları listele ─────────────────────────────────────────────────
    fun loadUsers() {
        if (_perms.value?.can("users") != true) return
        viewModelScope.launch {
            _loading.value = true
            try {
                // ÖNCEKİ HATA: orderBy("displayName") kullanılıyordu — admin panelindeki
                // kullanıcı listesi alfabetik sıralanıyordu, en son kayıt olanlar listenin
                // ortasına/sonuna düşüyordu (hatta limit(100) yüzünden hiç görünmeyebiliyordu).
                // Artık createdAt'e göre AZALAN sırada çekiliyor — en son kayıt olan en üstte.
                val snap = firestore.collection("users")
                    .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
                    .limit(100).get().await()
                _users.value = snap.documents.mapNotNull { doc ->
                    val d = doc.data ?: return@mapNotNull null
                    User(
                        uid           = doc.id,
                        displayName   = d["displayName"] as? String ?: d["name"] as? String ?: "",
                        email         = d["email"]    as? String ?: "",
                        photoURL      = d["photoURL"] as? String ?: "",
                        banned        = d["banned"]   as? Boolean ?: false,
                        emailVerified = d["emailVerified"] as? Boolean ?: false,
                        createdAt     = (d["createdAt"] as? com.google.firebase.Timestamp)?.toDate()?.time ?: 0L,
                    )
                }
            } catch (e: Exception) {
                // orderBy index yoksa (veya createdAt alanı eski kayıtlarda yoksa) sıralamasız
                // çekip client-side'da createdAt'e göre sırala — yine de en yeni en üstte olsun.
                try {
                    val snap = firestore.collection("users").limit(100).get().await()
                    _users.value = snap.documents.mapNotNull { doc ->
                        val d = doc.data ?: return@mapNotNull null
                        User(
                            uid           = doc.id,
                            displayName   = d["displayName"] as? String ?: d["name"] as? String ?: "",
                            email         = d["email"]    as? String ?: "",
                            photoURL      = d["photoURL"] as? String ?: "",
                            banned        = d["banned"]   as? Boolean ?: false,
                            emailVerified = d["emailVerified"] as? Boolean ?: false,
                            createdAt     = (d["createdAt"] as? com.google.firebase.Timestamp)?.toDate()?.time ?: 0L,
                        )
                    }.sortedByDescending { it.createdAt }
                } catch (e2: Exception) { e2.printStackTrace() }
            } finally { _loading.value = false }
        }
    }

    // ── Admin kullanıcı araması — Firestore prefix query ──────────────────────
    private val _userSearchResults = MutableStateFlow<List<User>>(emptyList())
    val userSearchResults = _userSearchResults.asStateFlow()
    private val _userSearchLoading = MutableStateFlow(false)
    val userSearchLoading = _userSearchLoading.asStateFlow()

    fun searchUsersAdmin(query: String) {
        if (query.isBlank()) { _userSearchResults.value = emptyList(); return }
        viewModelScope.launch {
            _userSearchLoading.value = true
            try {
                val q = query.trim()
                val seenIds = mutableSetOf<String>()
                val results = mutableListOf<User>()

                fun mapUser(doc: com.google.firebase.firestore.DocumentSnapshot): User? {
                    val d = doc.data ?: return null
                    return User(
                        uid           = doc.id,
                        displayName   = d["displayName"] as? String ?: d["name"] as? String ?: "",
                        email         = d["email"]    as? String ?: "",
                        photoURL      = d["photoURL"] as? String ?: "",
                        banned        = d["banned"]   as? Boolean ?: false,
                        emailVerified = d["emailVerified"] as? Boolean ?: false,
                    )
                }

                // displayName prefix
                for (prefix in listOf(q, q.lowercase(), q.replaceFirstChar { it.uppercaseChar() }).distinct()) {
                    try {
                        val snap = firestore.collection("users")
                            .orderBy("displayName")
                            .startAt(prefix).endAt(prefix + "\uF8FF")
                            .limit(20).get().await()
                        snap.documents.forEach { if (seenIds.add(it.id)) mapUser(it)?.let { u -> results.add(u) } }
                    } catch (_: Exception) {}
                }

                // email prefix (tam eşleşme dene)
                if (q.contains("@")) {
                    try {
                        val snap = firestore.collection("users")
                            .whereEqualTo("email", q.lowercase()).limit(5).get().await()
                        snap.documents.forEach { if (seenIds.add(it.id)) mapUser(it)?.let { u -> results.add(u) } }
                    } catch (_: Exception) {}
                }

                // UID tam eşleşme
                if (q.length > 10) {
                    try {
                        val doc = firestore.collection("users").document(q).get().await()
                        if (doc.exists() && seenIds.add(doc.id)) mapUser(doc)?.let { results.add(it) }
                    } catch (_: Exception) {}
                }

                // name prefix
                for (prefix in listOf(q, q.lowercase(), q.replaceFirstChar { it.uppercaseChar() }).distinct()) {
                    try {
                        val snap = firestore.collection("users")
                            .orderBy("name")
                            .startAt(prefix).endAt(prefix + "\uF8FF")
                            .limit(10).get().await()
                        snap.documents.forEach { if (seenIds.add(it.id)) mapUser(it)?.let { u -> results.add(u) } }
                    } catch (_: Exception) {}
                }

                _userSearchResults.value = results
            } catch (e: Exception) { e.printStackTrace() }
            finally { _userSearchLoading.value = false }
        }
    }

    fun toggleBan(uid: String, ban: Boolean) {
        if (uid.isBlank() || _perms.value?.can("users") != true) return
        viewModelScope.launch {
            try {
                // 1. Firestore
                firestore.collection("users").document(uid).update("banned", ban).await()
                // 2. Supabase users — service_role gerektirir (anon key yazamaz).
                //    Cloud Function üzerinden yaparız; yoksa Firestore kaynak of truth.
                try {
                    com.google.firebase.functions.FirebaseFunctions
                        .getInstance("europe-west1")
                        .getHttpsCallable("adminSetBan")
                        .call(mapOf("uid" to uid, "banned" to ban))
                        .await()
                } catch (_: Exception) {
                    // Function deploy edilmemişse sessizce geç —
                    // Firestore kaydı yeterli, Supabase sonraki sync'te düzelir.
                }
                _users.value = _users.value.map { if (it.uid == uid) it.copy(banned = ban) else it }
                _unverifiedUsers.value = _unverifiedUsers.value.map { if (it.uid == uid) it.copy(banned = ban) else it }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    // ── Doğrulanmamış kullanıcılar ────────────────────────────────────────────
    private val _unverifiedUsers = MutableStateFlow<List<User>>(emptyList())
    val unverifiedUsers = _unverifiedUsers.asStateFlow()

    private val _unverifiedLoading = MutableStateFlow(false)
    val unverifiedLoading = _unverifiedLoading.asStateFlow()

    fun loadUnverifiedUsers() {
        if (_perms.value?.can("users") != true) return
        viewModelScope.launch {
            _unverifiedLoading.value = true
            try {
                // emailVerified == false olan kullanıcılar.
                // Bu değer AuthViewModel.syncEmailVerified() tarafından giriş anında
                // Firebase Auth'un user.isEmailVerified değerinden kopyalanır.
                // Google kullanıcıları her zaman true, email kullanıcıları linke tıklayınca true.
                // Admin elle onaylamaz — sadece izler.
                val snap = firestore.collection("users")
                    .whereEqualTo("emailVerified", false)
                    .limit(100).get().await()
                val results = snap.documents.mapNotNull { doc ->
                    val d = doc.data ?: return@mapNotNull null
                    val email = d["email"] as? String ?: ""
                    if (email.isBlank()) return@mapNotNull null
                    // Ek güvenlik: signInMethod google ise emailVerified zaten true olmalıydı,
                    // veri tutarsızlığı varsa yine de listeden çıkar.
                    val signInMethod = d["signInMethod"] as? String ?: ""
                    if (signInMethod == "google") return@mapNotNull null
                    User(
                        uid           = doc.id,
                        displayName   = d["displayName"] as? String ?: d["name"] as? String ?: "",
                        email         = email,
                        photoURL      = d["photoURL"] as? String ?: "",
                        banned        = d["banned"]   as? Boolean ?: false,
                        emailVerified = false,
                    )
                }.sortedBy { it.displayName }
                _unverifiedUsers.value = results
            } catch (e: Exception) { e.printStackTrace() }
            finally { _unverifiedLoading.value = false }
        }
    }

    // Kullanıcı email linkine tıklayamadıysa admin elle doğrulayabilir.
    // Google kullanıcıları bu listeye zaten düşmez (syncEmailVerified true yazar).
    fun verifyUser(uid: String) {
        if (uid.isBlank() || _perms.value?.can("users") != true) return
        viewModelScope.launch {
            try {
                firestore.collection("users").document(uid)
                    .update("emailVerified", true).await()
                _unverifiedUsers.value = _unverifiedUsers.value.filter { it.uid != uid }
                _users.value = _users.value.map {
                    if (it.uid == uid) it.copy(emailVerified = true) else it
                }
                firestore.collection("userNotifs").document(uid).collection("msgs").add(mapOf(
                    "fromUid"   to (auth.currentUser?.uid ?: ""),
                    "fromName"  to "Heftreng",
                    "fromPhoto" to "",
                    "type"      to "verified",
                    "feedId"    to "",
                    "postId"    to "",
                    "title"     to "Hesabın doğrulandı ✓",
                    "sub"       to "",
                    "ico"       to "verified_user",
                    "message"   to "Hesabın admin tarafından doğrulandı.",
                    "url"       to "",
                    "read"      to false,
                    "ts"        to com.google.firebase.Timestamp.now(),
                )).await()
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    // ── Push bildirimi ────────────────────────────────────────────────────────
    fun sendPush(
        title    : String,
        body     : String,
        url      : String = "",
        targetUid: String = "",
        postId   : String = "",
        topic    : String = "",
        imageUrl : String = "",
        type     : String = "admin",
        ico      : String = "campaign",
    ) {
        if (_perms.value?.can("push") != true) return
        viewModelScope.launch {
            try {
                _pushResult.value = "Gönderiliyor…"
                if (targetUid.isBlank()) {
                    // "Herkese gönder" veya "topic" — toplu push
                    val data = hashMapOf<String, Any>(
                        "title" to title, "body" to body,
                        "url"   to url.ifBlank { "https://heft-reng.blogspot.com/" },
                        "type"  to type,
                        "ico"   to ico,
                    )
                    if (postId.isNotBlank()) data["postId"] = postId
                    if (imageUrl.isNotBlank()) data["imageUrl"] = imageUrl
                    val result = com.google.firebase.functions.FirebaseFunctions.getInstance("europe-west1")
                        .getHttpsCallable("sendBroadcastPush").call(data).await()
                    @Suppress("UNCHECKED_CAST")
                    val resMap = result.data as? Map<String, Any?>
                    val count  = (resMap?.get("count") as? Number)?.toInt() ?: 0
                    _pushResult.value = "✓ $count kullanıcıya gönderildi"
                } else {
                    // Belirli bir kullanıcıya — userNotifs yazarak onNewNotif trigger'ını tetikle
                    firestore.collection("userNotifs").document(targetUid).collection("msgs").add(mapOf(
                        "fromUid"   to (auth.currentUser?.uid ?: ""),
                        "fromName"  to "Heftreng",
                        "fromPhoto" to "",
                        "type"      to type,
                        "feedId"    to postId,
                        "postId"    to postId,
                        "title"     to title,
                        "sub"       to body,
                        "ico"       to ico,
                        "message"   to title,
                        "url"       to url.ifBlank { "" },
                        "imageUrl"  to imageUrl,
                        "read"      to false,
                        "ts"        to com.google.firebase.Timestamp.now(),
                    )).await()
                    _pushResult.value = "✓ Push gönderildi"
                }
            } catch (e: Exception) { _pushResult.value = "✗ Hata: ${e.message}" }
        }
    }

    // ── Sistem bildirimi ──────────────────────────────────────────────────────
    fun sendSystemNotif(title: String, body: String, type: String = "sys") {
        if (_perms.value?.can("notif") != true) return
        viewModelScope.launch {
            try {
                val icoMap = mapOf("sys" to "campaign", "cmt" to "chat_bubble", "like" to "favorite", "bm" to "bookmark")
                firestore.collection("notifications").add(mapOf(
                    "type" to type, "title" to title, "sub" to body,
                    "ico"  to (icoMap[type] ?: "campaign"), "read" to false,
                    "ts"   to FieldValue.serverTimestamp(),
                    "sentBy" to (auth.currentUser?.email ?: "admin"),
                )).await()
                _pushResult.value = "✓ Bildirim gönderildi"
            } catch (e: Exception) { _pushResult.value = "✗ Hata: ${e.message}" }
        }
    }

    // ── Bekleyen gönderiler ───────────────────────────────────────────────────
    fun loadPendingPosts() {
        if (_perms.value?.can("pending") != true) return
        viewModelScope.launch {
            try {
                val snap = firestore.collection("pendingPosts").limit(50).get().await()
                _pendingPosts.value = snap.documents.mapNotNull { doc ->
                    (doc.data ?: return@mapNotNull null).toMutableMap().also { it["id"] = doc.id }
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun approvePost(postId: String) {
        if (_perms.value?.can("pending") != true) return
        viewModelScope.launch {
            try {
                val doc = firestore.collection("pendingPosts").document(postId).get().await()
                val d   = doc.data ?: return@launch
                firestore.collection("feed").add(d).await()
                firestore.collection("pendingPosts").document(postId).delete().await()
                _pendingPosts.value = _pendingPosts.value.filter { it["id"] != postId }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun rejectPost(postId: String) {
        if (_perms.value?.can("pending") != true) return
        viewModelScope.launch {
            try {
                firestore.collection("pendingPosts").document(postId).delete().await()
                _pendingPosts.value = _pendingPosts.value.filter { it["id"] != postId }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    // ── Kullanıcı düzenleme ───────────────────────────────────────────────────
    private val _editResult = MutableStateFlow("")
    val editResult = _editResult.asStateFlow()

    fun updateUserProfile(uid: String, displayName: String, photoURL: String) {
        if (uid.isBlank() || _perms.value?.can("edit") != true) return
        viewModelScope.launch {
            try {
                val updates = mutableMapOf<String, Any>()
                if (displayName.isNotBlank()) { updates["displayName"] = displayName; updates["name"] = displayName }
                if (photoURL.isNotBlank()) updates["photoURL"] = photoURL
                if (updates.isEmpty()) { _editResult.value = "✗ Değişiklik yok"; return@launch }
                firestore.collection("users").document(uid).update(updates).await()
                // Feed'deki displayName'i de güncelle (son 50 gönderi)
                if (displayName.isNotBlank()) {
                    try {
                        firestore.collection("feed").whereEqualTo("uid", uid)
                            .limit(50).get().await().documents.forEach {
                                it.reference.update("displayName", displayName, "name", displayName)
                            }
                    } catch (_: Exception) {}
                }
                _users.value = _users.value.map {
                    if (it.uid == uid) it.copy(
                        displayName = displayName.ifBlank { it.displayName },
                        photoURL    = photoURL.ifBlank { it.photoURL },
                    ) else it
                }
                _editResult.value = "✓ Profil güncellendi"
            } catch (e: Exception) { _editResult.value = "✗ ${e.message}" }
        }
    }

    fun deleteUser(uid: String) {
        if (uid.isBlank() || _perms.value?.can("staff") != true) return
        viewModelScope.launch {
            try {
                // 1. Firestore kullanıcı belgesi
                firestore.collection("users").document(uid).delete().await()
                // 2. Feed postları
                try {
                    firestore.collection("feed").whereEqualTo("uid", uid)
                        .limit(100).get().await().documents.forEach { it.reference.delete() }
                } catch (_: Exception) {}
                // 3. userNotifs
                try { firestore.collection("userNotifs").document(uid).delete().await() }
                catch (_: Exception) {}
                // 4+5. follows — Supabase'den sil (from_uid ve target_uid)
                try {
                    supabase.postgrest["follows"].delete {
                        filter { eq("from_uid", uid) }
                    }
                    supabase.postgrest["follows"].delete {
                        filter { eq("target_uid", uid) }
                    }
                } catch (_: Exception) {}
                // feed_likes ve feed_saves da temizle
                try {
                    supabase.postgrest["feed_likes"].delete { filter { eq("uid", uid) } }
                    supabase.postgrest["feed_saves"].delete { filter { eq("uid", uid) } }
                    supabase.postgrest["book_quotes"].delete { filter { eq("uid", uid) } }
                    supabase.postgrest["book_reviews"].delete { filter { eq("uid", uid) } }
                    supabase.postgrest["reading_status"].delete { filter { eq("uid", uid) } }
                    supabase.postgrest["user_badges"].delete { filter { eq("uid", uid) } }
                    supabase.postgrest["daily_activity"].delete { filter { eq("uid", uid) } }
                    // Supabase users kaydı — en son sil (referans bütünlüğü)
                    supabase.postgrest["users"].delete { filter { eq("uid", uid) } }
                } catch (_: Exception) {}
                // 6. Firebase Auth kullanıcısını sil (Cloud Function üzerinden)
                try {
                    com.google.firebase.functions.FirebaseFunctions
                        .getInstance("europe-west1")
                        .getHttpsCallable("deleteAuthUser")
                        .call(mapOf("uid" to uid))
                        .await()
                } catch (_: Exception) {}  // Function yoksa geç
                _users.value = _users.value.filter { it.uid != uid }
                _editResult.value = "✓ Kullanıcı silindi"
            } catch (e: Exception) { _editResult.value = "✗ ${e.message}" }
        }
    }

    // ── Feed moderasyon ───────────────────────────────────────────────────────
    private val _feedPosts = MutableStateFlow<List<Map<String, Any>>>(emptyList())
    val feedPosts = _feedPosts.asStateFlow()

    fun loadFeedPosts(query: String = "") {
        if (_perms.value?.can("edit") != true) return
        viewModelScope.launch {
            try {
                val snap = firestore.collection("feed")
                    .orderBy("ts", com.google.firebase.firestore.Query.Direction.DESCENDING)
                    .limit(50).get().await()
                _feedPosts.value = snap.documents.mapNotNull { doc ->
                    (doc.data ?: return@mapNotNull null).toMutableMap().also { it["id"] = doc.id }
                }.filter {
                    query.isBlank() ||
                    (it["text"] as? String ?: "").contains(query, ignoreCase = true) ||
                    (it["uid"]  as? String ?: "").contains(query, ignoreCase = true)
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun deletePost(postId: String) {
        if (postId.isBlank() || _perms.value?.can("edit") != true) return
        viewModelScope.launch {
            try {
                // 1. Firestore feed
                firestore.collection("feed").document(postId).delete().await()
                // 2. Supabase — book_quotes / book_reviews (feed_post_id eşleşmesi)
                // FAZ 1 devamı: book_reviews eksikti, sadece book_quotes
                // temizleniyordu — bir kitap incelemesi gönderisi admin
                // tarafından silinince Kütüphane'de görünmeye devam ediyordu.
                try {
                    supabase.postgrest["book_quotes"].delete {
                        filter { eq("feed_post_id", postId) }
                    }
                    supabase.postgrest["book_reviews"].delete {
                        filter { eq("feed_post_id", postId) }
                    }
                } catch (_: Exception) {}
                // 3. Supabase — feed_likes, feed_comments, feed_saves
                try {
                    supabase.postgrest["feed_likes"].delete { filter { eq("post_id", postId) } }
                    supabase.postgrest["feed_comments"].delete { filter { eq("post_id", postId) } }
                    supabase.postgrest["feed_saves"].delete { filter { eq("post_id", postId) } }
                } catch (_: Exception) {}
                _feedPosts.value = _feedPosts.value.filter { it["id"] != postId }
                _editResult.value = "✓ Gönderi silindi"
            } catch (e: Exception) { _editResult.value = "✗ ${e.message}" }
        }
    }

    fun deleteComment(postId: String, commentId: String) {
        if (_perms.value?.can("edit") != true) return
        viewModelScope.launch {
            try {
                // Yorumlar artık Supabase feed_comments'te tutuluyor (eski Firestore
                // feed/{id}/comments koleksiyonu kaldırıldı, bkz. COMMENT_SYSTEM_PLAN Adım 3/7).
                supabase.postgrest["feed_comments"].delete {
                    filter { eq("id", commentId) }
                }
                try {
                    supabase.postgrest["comment_likes"].delete {
                        filter { eq("comment_id", commentId) }
                    }
                } catch (_: Exception) {}
                firestore.collection("feed").document(postId)
                    .update("cmtCount", FieldValue.increment(-1)).await()
                _editResult.value = "✓ Yorum silindi"
            } catch (e: Exception) { _editResult.value = "✗ ${e.message}" }
        }
    }

    fun clearEditResult() { _editResult.value = "" }

    // ── İstatistikler ─────────────────────────────────────────────────────────
    fun startStatsListener() {
        if (statsRefreshJob?.isActive == true) return
        statsRefreshJob = viewModelScope.launch {
            while (true) {
                fetchStatsOnce()
                kotlinx.coroutines.delay(60_000L)
            }
        }
        viewModelScope.launch { loadActiveUsers() }
    }

    private suspend fun fetchStatsOnce() {
        try {
            val snap = firestore.collection("appConfig").document("stats").get().await()
            val d = snap.data ?: return
            fun i(k: String) = (d[k] as? Long)?.toInt() ?: (d[k] as? Int) ?: 0
            val ps = PlatformStats(
                totalUsers     = i("totalUsers"),    androidUsers   = i("androidUsers"),  webUsers       = i("webUsers"),
                onlineNow      = i("onlineNow"),     newUsersToday  = i("newUsersToday"),
                totalPosts     = i("totalPosts"),    newPostsToday  = i("newPostsToday"),
                totalQuotes    = i("totalQuotes"),   totalReviews   = i("totalReviews"),
                totalComments  = i("totalComments"), totalSerials   = i("totalSerials"),
                totalBooks     = i("totalBooks"),    pendingPosts   = i("pendingPosts"),
                pendingReports = i("pendingReports"),bannedUsers    = i("bannedUsers"),
                lastUpdated    = (d["lastUpdated"] as? Long) ?: 0L,
            )
            _platformStats.value = ps
            _stats.value = mapOf(
                "users"    to ps.totalUsers,   "online"   to ps.onlineNow,    "posts"    to ps.totalPosts,
                "newUsers" to ps.newUsersToday,"newPosts" to ps.newPostsToday,
                "serials"  to ps.totalSerials, "books"    to ps.totalBooks,
                "pending"  to ps.pendingPosts, "reports"  to ps.pendingReports,"banned"  to ps.bannedUsers,
            )
        } catch (e: Exception) { e.printStackTrace() }
    }

    // ── Sayaç Geri Doldurma ───────────────────────────────────────────────────
    // Mevcut kullanıcıların followersCount / followingCount değerlerini
    // follows koleksiyonundan hesaplayıp Firestore'a yazar.
    // Bir kez çalıştırılması yeterli.

    private val _backfillProgress = MutableStateFlow<String>("")
    val backfillProgress = _backfillProgress.asStateFlow()

    private val _backfillRunning = MutableStateFlow(false)
    val backfillRunning = _backfillRunning.asStateFlow()

    fun backfillFollowerCounts() {
        if (_backfillRunning.value) return
        if (_perms.value?.can("users") != true) return
        viewModelScope.launch {
            _backfillRunning.value = true
            _backfillProgress.value = "Kullanıcılar yükleniyor…"
            try {
                // Tüm kullanıcıları çek (sayfalı)
                var lastDoc: com.google.firebase.firestore.DocumentSnapshot? = null
                var totalProcessed = 0
                var totalUpdated   = 0

                while (true) {
                    var query = firestore.collection("users").limit(200)
                    if (lastDoc != null) query = query.startAfter(lastDoc)
                    val snap = query.get().await()
                    if (snap.isEmpty) break

                    val batch = firestore.batch()
                    var batchCount = 0

                    for (userDoc in snap.documents) {
                        val uid = userDoc.id

                        // Supabase'den gerçek sayıları al — tek sorgu, çok daha hızlı
                        // Supabase'den count — decodeList ile say
                        val realFollowers = try {
                            supabase.postgrest["follows"].select {
                                filter { eq("target_uid", uid) }
                            }.decodeList<FollowRow>().size
                        } catch (_: Exception) { 0 }

                        val realFollowing = try {
                            supabase.postgrest["follows"].select {
                                filter { eq("from_uid", uid) }
                            }.decodeList<FollowRow>().size
                        } catch (_: Exception) { 0 }

                        val storedFollowers = (userDoc.getLong("followersCount") ?: -1L).toInt()
                        val storedFollowing = (userDoc.getLong("followingCount") ?: -1L).toInt()

                        // Sadece yanlış olanları güncelle
                        if (storedFollowers != realFollowers || storedFollowing != realFollowing) {
                            batch.update(userDoc.reference, mapOf(
                                "followersCount" to realFollowers,
                                "followingCount" to realFollowing,
                            ))
                            batchCount++
                            totalUpdated++
                        }
                        totalProcessed++
                    }

                    if (batchCount > 0) batch.commit().await()

                    _backfillProgress.value =
                        "$totalProcessed kullanıcı işlendi, $totalUpdated güncellendi…"

                    lastDoc = snap.documents.last()
                    if (snap.documents.size < 200) break
                }

                _backfillProgress.value =
                    "✅ Tamamlandı — $totalProcessed kullanıcı, $totalUpdated güncelleme"
            } catch (e: Exception) {
                _backfillProgress.value = "❌ Hata: ${e.message}"
                e.printStackTrace()
            } finally {
                _backfillRunning.value = false
            }
        }
    }

    fun loadStats() {
        viewModelScope.launch {
            _statsLoading.value = true
            try { loadStatsFallback(); loadActiveUsers() }
            catch (_: Exception) { loadStatsFallback() }
            finally { _statsLoading.value = false }
        }
    }

    private suspend fun loadStatsFallback() {
        try {
            val todaySec  = run { val c = java.util.Calendar.getInstance(); c.set(java.util.Calendar.HOUR_OF_DAY,0); c.set(java.util.Calendar.MINUTE,0); c.set(java.util.Calendar.SECOND,0); c.timeInMillis/1000 }
            val twoMinAgo = System.currentTimeMillis()/1000 - 120
            val todayTs   = com.google.firebase.Timestamp(todaySec, 0)
            val existingStats = try { firestore.collection("appConfig").document("stats").get().await().data } catch (_:Exception) { null }
            fun ei(k: String) = (existingStats?.get(k) as? Long)?.toInt() ?: 0
            // FATURA OPTİMİZASYONU:
            // Statik sayaçlar (totalUsers, bannedUsers, pendingPosts vb.) appConfig/stats'ta
            // increment/decrement ile güncelleniyor — burada tekrar collection taranmıyor.
            // Sadece gerçek zamanlı veriler (online, bugünkü yeni kayıt/gönderi) sorgulanıyor.
            val newUsersToday  = try { firestore.collection("users").whereGreaterThanOrEqualTo("createdAt", todayTs).limit(100).get().await().size() } catch (_:Exception) { 0 }
            val onlineNow      = try { firestore.collection("presence").whereEqualTo("online", true).limit(100).get().await().documents.count { (it.getTimestamp("lastSeen")?.seconds ?: 0L) >= twoMinAgo } } catch (_:Exception) { 0 }
            val newPostsToday  = try { firestore.collection("feed").whereGreaterThanOrEqualTo("ts", todayTs).whereEqualTo("moderationStatus","active").limit(100).get().await().size() } catch (_:Exception) { 0 }
            val map = mapOf(
                "totalUsers"    to ei("totalUsers"),    "androidUsers"  to ei("androidUsers"),  "webUsers"      to ei("webUsers"),
                "onlineNow"     to onlineNow,           "newUsersToday" to newUsersToday,
                "totalPosts"    to ei("totalPosts"),    "newPostsToday" to newPostsToday,
                "totalQuotes"   to ei("totalQuotes"),   "totalReviews"  to ei("totalReviews"),
                "totalComments" to ei("totalComments"), "totalSerials"  to ei("totalSerials"),   "totalBooks"    to ei("totalBooks"),
                "pendingPosts"  to ei("pendingPosts"),  "pendingReports" to ei("pendingReports"),
                "bannedUsers"   to ei("bannedUsers"),   "lastUpdated"   to System.currentTimeMillis(),
            )
            try { firestore.collection("appConfig").document("stats").set(map, com.google.firebase.firestore.SetOptions.merge()).await() } catch (_:Exception) {}
        } catch (e: Exception) { e.printStackTrace() }
    }

    private suspend fun loadActiveUsers() {
        try {
            val oneWeekAgo   = com.google.firebase.Timestamp(System.currentTimeMillis()/1000 - 7*86400, 0)
            val presenceSnap = firestore.collection("presence").limit(100).get().await()
            val result = mutableListOf<UserActivity>()
            val twoMinMs = 120_000L
            presenceSnap.documents.forEach { doc ->
                val d   = doc.data ?: return@forEach
                val uid = doc.id
                if ((d["lastSeen"] as? com.google.firebase.Timestamp)?.seconds ?: 0L < oneWeekAgo.seconds) return@forEach
                val lastSeenMs = (d["lastSeen"] as? com.google.firebase.Timestamp)?.toDate()?.time ?: 0L
                val online     = (d["online"] as? Boolean == true) && (System.currentTimeMillis() - lastSeenMs < twoMinMs)
                val ud         = try { firestore.collection("users").document(uid).get().await().data } catch (_:Exception) { null }
                result.add(UserActivity(
                    uid = uid, displayName = ud?.get("displayName") as? String ?: ud?.get("name") as? String ?: "—",
                    photoURL = ud?.get("photoURL") as? String ?: "", online = online, lastSeenMs = lastSeenMs,
                    appVersion = ud?.get("appVersion") as? String ?: "—", platform = ud?.get("platform") as? String ?: "android",
                    postsCount = (ud?.get("postsCount") as? Long)?.toInt() ?: 0, level = (ud?.get("level") as? Long)?.toInt() ?: 1,
                ))
            }
            _activeUsers.value = result.sortedWith(compareByDescending<UserActivity> { it.online }.thenByDescending { it.lastSeenMs })
        } catch (e: Exception) { e.printStackTrace() }
    }

    // ── Şikayetler ────────────────────────────────────────────────────────────
    private val _reports = MutableStateFlow<List<Map<String, Any>>>(emptyList())
    private val _appeals = MutableStateFlow<List<com.heftreng.app.data.model.Appeal>>(emptyList())
    val reports = _reports.asStateFlow()
    val appeals = _appeals.asStateFlow()

    fun loadReports() {
        if (_perms.value?.can("reports") != true) return
        viewModelScope.launch {
            _loading.value = true
            try {
                val snap = firestore.collection("reports")
                    .orderBy("ts", com.google.firebase.firestore.Query.Direction.DESCENDING)
                    .limit(100).get().await()
                _reports.value = snap.documents.mapNotNull { doc ->
                    val d = doc.data?.toMutableMap() ?: return@mapNotNull null; d["id"] = doc.id; d
                }
            } catch (e: Exception) { e.printStackTrace() }
            finally { _loading.value = false }
        }
    }

    fun updateReportStatus(reportId: String, status: String) {
        if (_perms.value?.can("reports") != true) return
        viewModelScope.launch {
            try {
                firestore.collection("reports").document(reportId).update("status", status).await()
                _reports.value = _reports.value.map { r ->
                    if (r["id"] == reportId) r.toMutableMap().also { it["status"] = status } else r
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    // ── Gönderi moderasyonu ───────────────────────────────────────────────────
    fun moderatePost(postId: String, targetUid: String, targetName: String, status: String, reason: String, adminNote: String) {
        if (_perms.value?.can("edit") != true) return
        viewModelScope.launch {
            try {
                val myUid = auth.currentUser?.uid ?: ""
                firestore.collection("feed").document(postId).update(mapOf(
                    "moderationStatus" to status, "moderationReason" to reason,
                    "moderationNote" to adminNote, "moderatedBy" to myUid,
                    "moderatedAt" to com.google.firebase.Timestamp.now(),
                )).await()
                // FAZ 1: Firestore feed'deki moderasyon durumu Supabase
                // book_quotes/book_reviews'a da yansıtılıyor — aksi halde
                // moderatör bir alıntıyı/incelemeyi kaldırsa bile Kütüphane
                // ekranında görünmeye devam ediyordu (feed_post_id ile eşleşen
                // satır bulunamazsa fonksiyon sessizce hiçbir şey yapmaz).
                libraryRepository.setQuoteModerationStatusByFeedPostId(postId, status)
                libraryRepository.setReviewModerationStatusByFeedPostId(postId, status)
                val notifTitle = when (status) { "restricted" -> "Gönderiniz kısıtlandı"; "suspended" -> "Gönderiniz askıya alındı"; "removed" -> "Gönderiniz kaldırıldı"; else -> "Gönderi durumu güncellendi" }
                val notifBody  = reason.ifBlank { when (status) { "restricted" -> "Gönderiniz yalnızca giriş yapmış kullanıcılara gösterilecek."; "suspended" -> "Gönderiniz inceleniyor."; "removed" -> "Gönderiniz platform kurallarına aykırı bulundu."; else -> "" } }
                if (targetUid.isNotBlank()) {
                    firestore.collection("userNotifs").document(targetUid).collection("msgs").add(mapOf(
                        "fromUid" to "", "fromName" to "Heftreng", "fromPhoto" to "",
                        "type" to "moderation", "title" to notifTitle, "sub" to notifBody,
                        "message" to notifTitle, "feedId" to postId, "postId" to postId,
                        "ico" to "gavel", "status" to status, "url" to "",
                        "read" to false, "ts" to com.google.firebase.Timestamp.now(),
                    )).await()
                }
                firestore.collection("moderationLogs").add(mapOf(
                    "postId" to postId, "targetUid" to targetUid, "targetName" to targetName,
                    "status" to status, "reason" to reason, "adminNote" to adminNote,
                    "adminUid" to myUid, "ts" to com.google.firebase.Timestamp.now(),
                )).await()
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun restorePost(postId: String, targetUid: String) {
        if (_perms.value?.can("edit") != true) return
        viewModelScope.launch {
            try {
                firestore.collection("feed").document(postId).update(mapOf(
                    "moderationStatus" to "active", "moderationReason" to "", "moderationNote" to "",
                )).await()
                // FAZ 1: bkz. moderatePost'taki aynı senkronizasyon açıklaması.
                libraryRepository.setQuoteModerationStatusByFeedPostId(postId, "active")
                libraryRepository.setReviewModerationStatusByFeedPostId(postId, "active")
                if (targetUid.isNotBlank()) {
                    firestore.collection("userNotifs").document(targetUid).collection("msgs").add(mapOf(
                        "fromUid" to "", "fromName" to "Heftreng", "fromPhoto" to "",
                        "type" to "moderation", "title" to "Gönderiniz yeniden aktif edildi",
                        "sub" to "Gönderiniz tekrar herkese açık hale getirildi.",
                        "message" to "Gönderiniz yeniden aktif edildi", "feedId" to postId, "postId" to postId,
                        "ico" to "check_circle", "url" to "",
                        "read" to false, "ts" to com.google.firebase.Timestamp.now(),
                    )).await()
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    // ── İtiraz yönetimi ───────────────────────────────────────────────────────
    fun loadAppeals() {
        if (_perms.value?.can("appeals") != true) return
        viewModelScope.launch {
            try {
                val snap = firestore.collection("appeals").whereEqualTo("status","pending").limit(50).get().await()
                _appeals.value = snap.documents.mapNotNull { doc ->
                    try { doc.toObject(com.heftreng.app.data.model.Appeal::class.java)?.copy(id = doc.id) } catch (_:Exception) { null }
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun approveAppeal(appeal: com.heftreng.app.data.model.Appeal, adminNote: String = "") {
        if (_perms.value?.can("appeals") != true) return
        viewModelScope.launch {
            try {
                firestore.collection("appeals").document(appeal.id).update(mapOf(
                    "status" to "approved", "adminNote" to adminNote, "resolvedAt" to com.google.firebase.Timestamp.now(),
                )).await()
                restorePost(appeal.postId, appeal.postOwnerUid)
                run {
                    val appealBody = "Gönderiniz yeniden aktif edildi. ${adminNote.ifBlank { "" }}"
                    firestore.collection("userNotifs").document(appeal.postOwnerUid).collection("msgs").add(mapOf(
                        "fromUid" to "", "fromName" to "Heftreng", "fromPhoto" to "",
                        "type" to "appeal_result", "title" to "İtirazınız kabul edildi", "sub" to appealBody,
                        "message" to "İtirazınız kabul edildi", "feedId" to appeal.postId, "postId" to appeal.postId,
                        "ico" to "thumb_up", "url" to "",
                        "read" to false, "ts" to com.google.firebase.Timestamp.now(),
                    )).await()
                }
                _appeals.value = _appeals.value.filter { it.id != appeal.id }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun rejectAppeal(appeal: com.heftreng.app.data.model.Appeal, adminNote: String = "") {
        if (_perms.value?.can("appeals") != true) return
        viewModelScope.launch {
            try {
                firestore.collection("appeals").document(appeal.id).update(mapOf(
                    "status" to "rejected", "adminNote" to adminNote, "resolvedAt" to com.google.firebase.Timestamp.now(),
                )).await()
                run {
                    val appealBody = "Kararımız geçerliliğini korumaktadır. ${adminNote.ifBlank { "" }}"
                    firestore.collection("userNotifs").document(appeal.postOwnerUid).collection("msgs").add(mapOf(
                        "fromUid" to "", "fromName" to "Heftreng", "fromPhoto" to "",
                        "type" to "appeal_result", "title" to "İtirazınız reddedildi", "sub" to appealBody,
                        "message" to "İtirazınız reddedildi", "feedId" to appeal.postId, "postId" to appeal.postId,
                        "ico" to "thumb_down", "url" to "",
                        "read" to false, "ts" to com.google.firebase.Timestamp.now(),
                    )).await()
                }
                _appeals.value = _appeals.value.filter { it.id != appeal.id }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }


    // ── Tek seferlik migration: Firestore users → Supabase users ──────────────
    // Admin panelinden çalıştırılır. Tüm Firestore kullanıcılarını
    // Supabase users tablosuna toplu yazar (upsert — tekrar çalıştırılabilir).
    val migrationState = androidx.compose.runtime.mutableStateOf("idle") // idle | running | done | error

    fun migrateAllUsersToSupabase() {
        if (migrationState.value == "running") return
        migrationState.value = "running"
        viewModelScope.launch {
            try {
                // Firestore'dan tüm kullanıcıları çek (1000'er chunk)
                val allDocs = mutableListOf<com.google.firebase.firestore.DocumentSnapshot>()
                var lastDoc: com.google.firebase.firestore.DocumentSnapshot? = null
                while (true) {
                    var q = firestore.collection("users")
                        .orderBy("__name__")
                        .limit(1000)
                    if (lastDoc != null) q = q.startAfter(lastDoc)
                    val snap = q.get().await()
                    allDocs.addAll(snap.documents)
                    if (snap.documents.size < 1000) break
                    lastDoc = snap.documents.last()
                }

                // 50'şer chunk olarak Supabase'e upsert et
                var written = 0
                allDocs.chunked(50).forEach { chunk ->
                    val rows = chunk.mapNotNull { doc ->
                        val uid = doc.id.ifBlank { return@mapNotNull null }
                        com.heftreng.app.data.model.UserRow(
                            uid         = uid,
                            displayName = (doc.getString("displayName")
                                ?: doc.getString("name") ?: "").trim(),
                            photoUrl    = doc.getString("photoURL") ?: "",
                            bio         = doc.getString("bio") ?: "",
                            banned      = doc.getBoolean("banned") ?: false,
                        )
                    }
                    if (rows.isNotEmpty()) {
                        supabase.postgrest["users"].upsert(rows)
                        written += rows.size
                    }
                }
                android.util.Log.d("AdminVM", "Migration OK: $written kullanıcı yazıldı")
                migrationState.value = "done:$written"
            } catch (e: Exception) {
                android.util.Log.e("AdminVM", "Migration FAILED: ${e.message}")
                migrationState.value = "error:${e.message}"
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        statsRefreshJob?.cancel()
    }
}
