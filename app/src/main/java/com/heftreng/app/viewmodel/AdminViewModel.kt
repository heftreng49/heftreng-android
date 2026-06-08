package com.heftreng.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.heftreng.app.data.model.User
import dagger.hilt.android.lifecycle.HiltViewModel
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
                    // Hem role hem eski permissions array desteklenir
                    @Suppress("UNCHECKED_CAST")
                    val legacy = doc.get("permissions") as? List<String>
                    val perms  = if (!legacy.isNullOrEmpty()) legacy.toSet()
                                 else roleToPermissions(role)
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
            try {
                val snap = firestore.collection("admins").limit(50).get().await()
                _staffList.value = snap.documents.mapNotNull { doc ->
                    val uid     = doc.id
                    @Suppress("UNCHECKED_CAST")
                    val permList = doc.get("permissions") as? List<String> ?: emptyList()
                    val addedAt  = doc.getLong("addedAt") ?: 0L
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
            } catch (e: Exception) { e.printStackTrace() }
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
                firestore.collection("admins").document(uid.trim()).set(mapOf(
                    "role"        to inferredRole,
                    "title"       to title.ifBlank { "Yardımcı" },
                    "permissions" to permissions.toList(),
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
        viewModelScope.launch {
            try {
                firestore.collection("admins").document(uid).update(mapOf(
                    "title"       to title.ifBlank { "Yardımcı" },
                    "permissions" to permissions.toList(),
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
                val snap = firestore.collection("users")
                    .orderBy("displayName")
                    .limit(100).get().await()
                _users.value = snap.documents.mapNotNull { doc ->
                    val d = doc.data ?: return@mapNotNull null
                    User(
                        uid         = doc.id,
                        displayName = d["displayName"] as? String ?: d["name"] as? String ?: "",
                        email       = d["email"]    as? String ?: "",
                        photoURL    = d["photoURL"] as? String ?: "",
                        banned      = d["banned"]   as? Boolean ?: false,
                    )
                }
            } catch (e: Exception) {
                // orderBy index yoksa sıralamasız çek
                try {
                    val snap = firestore.collection("users").limit(100).get().await()
                    _users.value = snap.documents.mapNotNull { doc ->
                        val d = doc.data ?: return@mapNotNull null
                        User(
                            uid         = doc.id,
                            displayName = d["displayName"] as? String ?: d["name"] as? String ?: "",
                            email       = d["email"]    as? String ?: "",
                            photoURL    = d["photoURL"] as? String ?: "",
                            banned      = d["banned"]   as? Boolean ?: false,
                        )
                    }
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
                        uid         = doc.id,
                        displayName = d["displayName"] as? String ?: d["name"] as? String ?: "",
                        email       = d["email"]    as? String ?: "",
                        photoURL    = d["photoURL"] as? String ?: "",
                        banned      = d["banned"]   as? Boolean ?: false,
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
                firestore.collection("users").document(uid).update("banned", ban).await()
                _users.value = _users.value.map { if (it.uid == uid) it.copy(banned = ban) else it }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    // ── Push bildirimi ────────────────────────────────────────────────────────
    fun sendPush(title: String, body: String, url: String = "", targetUid: String = "") {
        if (_perms.value?.can("push") != true) return
        viewModelScope.launch {
            try {
                _pushResult.value = "Gönderiliyor…"
                val data = hashMapOf(
                    "targetUid" to targetUid.ifBlank { auth.currentUser?.uid ?: "" },
                    "title" to title, "body" to body,
                    "url"   to url.ifBlank { "https://heft-reng.blogspot.com/" },
                    "type"  to "default",
                )
                com.google.firebase.functions.FirebaseFunctions.getInstance("europe-west1")
                    .getHttpsCallable("sendPush").call(data).await()
                _pushResult.value = "✓ Push gönderildi"
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
                // 4. follows (fromUid)
                try {
                    firestore.collection("follows").whereEqualTo("fromUid", uid)
                        .limit(200).get().await().documents.forEach { it.reference.delete() }
                } catch (_: Exception) {}
                // 5. follows (targetUid)
                try {
                    firestore.collection("follows").whereEqualTo("targetUid", uid)
                        .limit(200).get().await().documents.forEach { it.reference.delete() }
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
                firestore.collection("feed").document(postId).delete().await()
                _feedPosts.value = _feedPosts.value.filter { it["id"] != postId }
                _editResult.value = "✓ Gönderi silindi"
            } catch (e: Exception) { _editResult.value = "✗ ${e.message}" }
        }
    }

    fun deleteComment(postId: String, commentId: String) {
        if (_perms.value?.can("edit") != true) return
        viewModelScope.launch {
            try {
                firestore.collection("feed").document(postId)
                    .collection("comments").document(commentId).delete().await()
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
            val newUsersToday  = try { firestore.collection("users").whereGreaterThanOrEqualTo("createdAt", todayTs).limit(100).get().await().size() } catch (_:Exception) { 0 }
            val bannedUsers    = try { firestore.collection("users").whereEqualTo("banned", true).limit(100).get().await().size() } catch (_:Exception) { 0 }
            val onlineNow      = try { firestore.collection("presence").whereEqualTo("online", true).limit(100).get().await().documents.count { (it.getTimestamp("lastSeen")?.seconds ?: 0L) >= twoMinAgo } } catch (_:Exception) { 0 }
            val newPostsToday  = try { firestore.collection("feed").whereGreaterThanOrEqualTo("ts", todayTs).whereEqualTo("moderationStatus","active").limit(100).get().await().size() } catch (_:Exception) { 0 }
            val pendingPosts   = try { firestore.collection("pendingPosts").limit(100).get().await().size() } catch (_:Exception) { 0 }
            val pendingReports = try { firestore.collection("reports").whereEqualTo("status","pending").limit(100).get().await().size() } catch (_:Exception) { 0 }
            val map = mapOf(
                "totalUsers" to ei("totalUsers"), "androidUsers" to ei("androidUsers"), "webUsers" to ei("webUsers"),
                "onlineNow" to onlineNow, "newUsersToday" to newUsersToday,
                "totalPosts" to ei("totalPosts"), "newPostsToday" to newPostsToday,
                "totalQuotes" to ei("totalQuotes"), "totalReviews" to ei("totalReviews"),
                "totalComments" to ei("totalComments"), "totalSerials" to ei("totalSerials"), "totalBooks" to ei("totalBooks"),
                "pendingPosts" to pendingPosts, "pendingReports" to pendingReports,
                "bannedUsers" to bannedUsers, "lastUpdated" to System.currentTimeMillis(),
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
                val notifTitle = when (status) { "restricted" -> "Gönderiniz kısıtlandı"; "suspended" -> "Gönderiniz askıya alındı"; "removed" -> "Gönderiniz kaldırıldı"; else -> "Gönderi durumu güncellendi" }
                val notifBody  = reason.ifBlank { when (status) { "restricted" -> "Gönderiniz yalnızca giriş yapmış kullanıcılara gösterilecek."; "suspended" -> "Gönderiniz inceleniyor."; "removed" -> "Gönderiniz platform kurallarına aykırı bulundu."; else -> "" } }
                if (targetUid.isNotBlank()) {
                    firestore.collection("userNotifs").document(targetUid).collection("notifs").add(mapOf(
                        "type" to "moderation", "title" to notifTitle, "body" to notifBody,
                        "postId" to postId, "status" to status, "read" to false, "ts" to com.google.firebase.Timestamp.now(),
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
                if (targetUid.isNotBlank()) {
                    firestore.collection("userNotifs").document(targetUid).collection("notifs").add(mapOf(
                        "type" to "moderation", "title" to "Gönderiniz yeniden aktif edildi",
                        "body" to "Gönderiniz tekrar herkese açık hale getirildi.",
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
                firestore.collection("userNotifs").document(appeal.postOwnerUid).collection("notifs").add(mapOf(
                    "type" to "appeal_result", "title" to "İtirazınız kabul edildi",
                    "body" to "Gönderiniz yeniden aktif edildi. ${adminNote.ifBlank { "" }}",
                    "postId" to appeal.postId, "read" to false, "ts" to com.google.firebase.Timestamp.now(),
                )).await()
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
                firestore.collection("userNotifs").document(appeal.postOwnerUid).collection("notifs").add(mapOf(
                    "type" to "appeal_result", "title" to "İtirazınız reddedildi",
                    "body" to "Kararımız geçerliliğini korumaktadır. ${adminNote.ifBlank { "" }}",
                    "postId" to appeal.postId, "read" to false, "ts" to com.google.firebase.Timestamp.now(),
                )).await()
                _appeals.value = _appeals.value.filter { it.id != appeal.id }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    override fun onCleared() {
        super.onCleared()
        statsRefreshJob?.cancel()
    }
}
