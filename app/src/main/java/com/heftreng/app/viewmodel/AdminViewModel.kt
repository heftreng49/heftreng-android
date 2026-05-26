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

// Admin kontrolü artık Firestore'daki admins/{uid} koleksiyonuna dayanır.
// Firebase Console'dan: admins/{your_uid} belgesi oluştur → { role: "super_admin" }
// Bu sayede email değişse bile admin yetkisi korunur, hesap ele geçirilse de
// tek başına email kontrolü yetmez.
private const val ADMIN_EMAIL = "siirgibi49@gmail.com" // fallback

@HiltViewModel
class AdminViewModel @Inject constructor(
    private val auth     : FirebaseAuth,
    private val firestore: FirebaseFirestore,
) : ViewModel() {

    private val _isAdmin      = MutableStateFlow(false)
    val isAdmin = _isAdmin.asStateFlow()

    private val _users        = MutableStateFlow<List<User>>(emptyList())
    val users = _users.asStateFlow()

    private val _pendingPosts = MutableStateFlow<List<Map<String, Any>>>(emptyList())
    val pendingPosts = _pendingPosts.asStateFlow()

    private val _loading      = MutableStateFlow(false)
    val loading = _loading.asStateFlow()

    private val _pushResult   = MutableStateFlow("")
    val pushResult = _pushResult.asStateFlow()

    data class PlatformStats(
        val totalUsers    : Int  = 0,
        val androidUsers  : Int  = 0,
        val webUsers      : Int  = 0,
        val onlineNow     : Int  = 0,
        val newUsersToday : Int  = 0,
        val totalPosts    : Int  = 0,
        val newPostsToday : Int  = 0,
        val totalQuotes   : Int  = 0,
        val totalReviews  : Int  = 0,
        val totalComments : Int  = 0,
        val totalSerials  : Int  = 0,
        val totalBooks    : Int  = 0,
        val pendingPosts  : Int  = 0,
        val pendingReports: Int  = 0,
        val bannedUsers   : Int  = 0,
        val lastUpdated   : Long = 0L,
    )
    private val _platformStats = MutableStateFlow(PlatformStats())
    val platformStats = _platformStats.asStateFlow()

    private val _stats        = MutableStateFlow<Map<String, Int>>(emptyMap())
    val stats = _stats.asStateFlow()

    private var statsListener: ListenerRegistration? = null

    // ── Detaylı istatistikler ─────────────────────────────────────────────────
    data class UserActivity(
        val uid         : String = "",
        val displayName : String = "",
        val photoURL    : String = "",
        val online      : Boolean = false,
        val lastSeenMs  : Long = 0L,
        val appVersion  : String = "",
        val platform    : String = "",
        val postsCount  : Int = 0,
        val level       : Int = 1,
    )
    private val _activeUsers = MutableStateFlow<List<UserActivity>>(emptyList())
    val activeUsers = _activeUsers.asStateFlow()

    private val _statsLoading = MutableStateFlow(false)
    val statsLoading = _statsLoading.asStateFlow()

    fun checkAdmin() {
        viewModelScope.launch {
            val currentUser = auth.currentUser ?: run { _isAdmin.value = false; return@launch }
            // Önce Firestore admins/{uid} koleksiyonunu kontrol et
            try {
                val adminDoc = firestore.collection("admins").document(currentUser.uid).get().await()
                if (adminDoc.exists()) {
                    _isAdmin.value = true
                    return@launch
                }
            } catch (_: Exception) {}
            // Fallback: email kontrolü (admins koleksiyonu henüz kurulmamışsa)
            _isAdmin.value = currentUser.email == ADMIN_EMAIL
        }
    }

    // ── Kullanıcıları listele ──────────────────────────────────────────────────
    fun loadUsers() {
        viewModelScope.launch {
            _loading.value = true
            try {
                val snap = firestore.collection("users").limit(100).get().await()
                _users.value = snap.documents.mapNotNull { doc ->
                    val d = doc.data ?: return@mapNotNull null
                    User(
                        uid         = doc.id,
                        displayName = d["displayName"] as? String ?: d["name"] as? String ?: "",
                        email       = d["email"] as? String ?: "",
                        photoURL    = d["photoURL"] as? String ?: "",
                        banned      = d["banned"] as? Boolean ?: false,
                    )
                }
            } catch (e: Exception) { e.printStackTrace() }
            finally { _loading.value = false }
        }
    }

    // ── Ban / Unban ────────────────────────────────────────────────────────────
    fun toggleBan(uid: String, ban: Boolean) {
        if (uid.isBlank() || !_isAdmin.value) return
        viewModelScope.launch {
            try {
                firestore.collection("users").document(uid).update("banned", ban).await()
                _users.value = _users.value.map { if (it.uid == uid) it.copy(banned = ban) else it }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    // ── Push bildirimi — Cloud Function sendPush çağırır ──────────────────────
    fun sendPush(title: String, body: String, url: String = "", targetUid: String = "") {
        if (!_isAdmin.value) return
        viewModelScope.launch {
            try {
                _pushResult.value = "Gönderiliyor…"
                val functions = com.google.firebase.functions.FirebaseFunctions
                    .getInstance("europe-west1")
                val data = hashMapOf(
                    "targetUid" to targetUid.ifBlank { auth.currentUser?.uid ?: "" },
                    "title"     to title,
                    "body"      to body,
                    "url"       to url.ifBlank { "https://heft-reng.blogspot.com/" },
                    "type"      to "default",
                )
                functions.getHttpsCallable("sendPush")
                    .call(data)
                    .await()
                _pushResult.value = "✓ Push gönderildi"
            } catch (e: Exception) {
                _pushResult.value = "✗ Hata: ${e.message}"
            }
        }
    }

    // ── Sistem bildirimi — XML: sendAdminNotifFromPanel ────────────────────────
    // notifications koleksiyonuna yazar (tüm kullanıcılara)
    fun sendSystemNotif(title: String, body: String, type: String = "sys") {
        if (!_isAdmin.value) return
        viewModelScope.launch {
            try {
                val icoMap = mapOf("sys" to "campaign", "cmt" to "chat_bubble", "like" to "favorite", "bm" to "bookmark")
                firestore.collection("notifications").add(
                    mapOf(
                        "type"   to type,
                        "title"  to title,
                        "sub"    to body,
                        "ico"    to (icoMap[type] ?: "campaign"),
                        "read"   to false,
                        "ts"     to FieldValue.serverTimestamp(),
                        "sentBy" to (auth.currentUser?.email ?: "admin"),
                    )
                ).await()
                _pushResult.value = "✓ Bildirim gönderildi"
            } catch (e: Exception) {
                _pushResult.value = "✗ Hata: ${e.message}"
            }
        }
    }

    // ── Bekleyen gönderiler — XML: pendingPosts ────────────────────────────────
    fun loadPendingPosts() {
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
        viewModelScope.launch {
            try {
                firestore.collection("pendingPosts").document(postId).delete().await()
                _pendingPosts.value = _pendingPosts.value.filter { it["id"] != postId }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }


    // ── Kullanıcı adı / fotoğraf düzelt ──────────────────────────────────────
    private val _editResult = MutableStateFlow("")
    val editResult = _editResult.asStateFlow()

    fun updateUserProfile(uid: String, displayName: String, photoURL: String) {
        if (uid.isBlank() || !_isAdmin.value) return
        viewModelScope.launch {
            try {
                val updates = mutableMapOf<String, Any>()
                if (displayName.isNotBlank()) {
                    updates["displayName"] = displayName
                    updates["name"]        = displayName
                }
                if (photoURL.isNotBlank()) updates["photoURL"] = photoURL
                if (updates.isEmpty()) { _editResult.value = "✗ Değişiklik yok"; return@launch }
                firestore.collection("users").document(uid).update(updates).await()
                _users.value = _users.value.map {
                    if (it.uid == uid) it.copy(
                        displayName = displayName.ifBlank { it.displayName },
                        photoURL    = photoURL.ifBlank    { it.photoURL },
                    ) else it
                }
                _editResult.value = "✓ Profil güncellendi"
            } catch (e: Exception) { _editResult.value = "✗ ${e.message}" }
        }
    }

    // ── Kullanıcıyı tamamen sil ───────────────────────────────────────────────
    fun deleteUser(uid: String) {
        if (uid.isBlank() || !_isAdmin.value) return
        viewModelScope.launch {
            try {
                // Firestore dökümanını sil
                firestore.collection("users").document(uid).delete().await()
                // Kullanıcının gönderilerini sil
                val posts = firestore.collection("feed")
                    .whereEqualTo("uid", uid).get().await()
                posts.documents.forEach { it.reference.delete() }
                _users.value = _users.value.filter { it.uid != uid }
                _editResult.value = "✓ Kullanıcı silindi"
            } catch (e: Exception) { _editResult.value = "✗ ${e.message}" }
        }
    }

    // ── Feed gönderisi sil ────────────────────────────────────────────────────
    private val _feedPosts = MutableStateFlow<List<Map<String, Any>>>(emptyList())
    val feedPosts = _feedPosts.asStateFlow()

    fun loadFeedPosts(query: String = "") {
        viewModelScope.launch {
            try {
                val snap = firestore.collection("feed")
                    .orderBy("ts", com.google.firebase.firestore.Query.Direction.DESCENDING)
                    .limit(50).get().await()
                _feedPosts.value = snap.documents.mapNotNull { doc ->
                    (doc.data ?: return@mapNotNull null)
                        .toMutableMap().also { it["id"] = doc.id }
                }.filter {
                    query.isBlank() ||
                    (it["text"] as? String ?: "").contains(query, ignoreCase = true) ||
                    (it["uid"]  as? String ?: "").contains(query, ignoreCase = true)
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun deletePost(postId: String) {
        if (postId.isBlank() || !_isAdmin.value) return
        viewModelScope.launch {
            try {
                firestore.collection("feed").document(postId).delete().await()
                _feedPosts.value = _feedPosts.value.filter { it["id"] != postId }
                _editResult.value = "✓ Gönderi silindi"
            } catch (e: Exception) { _editResult.value = "✗ ${e.message}" }
        }
    }

    // ── Yorum sil ─────────────────────────────────────────────────────────────
    fun deleteComment(postId: String, commentId: String) {
        if (!_isAdmin.value) return
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
        if (statsListener != null) return
        statsListener = firestore.collection("appConfig").document("stats")
            .addSnapshotListener { snap, _ ->
                val d = snap?.data ?: return@addSnapshotListener
                fun i(k: String) = (d[k] as? Long)?.toInt() ?: (d[k] as? Int) ?: 0
                val ps = PlatformStats(
                    totalUsers     = i("totalUsers"),
                    androidUsers   = i("androidUsers"),
                    webUsers       = i("webUsers"),
                    onlineNow      = i("onlineNow"),
                    newUsersToday  = i("newUsersToday"),
                    totalPosts     = i("totalPosts"),
                    newPostsToday  = i("newPostsToday"),
                    totalQuotes    = i("totalQuotes"),
                    totalReviews   = i("totalReviews"),
                    totalComments  = i("totalComments"),
                    totalSerials   = i("totalSerials"),
                    totalBooks     = i("totalBooks"),
                    pendingPosts   = i("pendingPosts"),
                    pendingReports = i("pendingReports"),
                    bannedUsers    = i("bannedUsers"),
                    lastUpdated    = (d["lastUpdated"] as? Long) ?: 0L,
                )
                _platformStats.value = ps
                _stats.value = mapOf(
                    "users"    to ps.totalUsers,  "online"   to ps.onlineNow,
                    "posts"    to ps.totalPosts,  "newUsers" to ps.newUsersToday,
                    "newPosts" to ps.newPostsToday, "serials" to ps.totalSerials,
                    "books"    to ps.totalBooks,  "pending"  to ps.pendingPosts,
                    "reports"  to ps.pendingReports, "banned" to ps.bannedUsers,
                )
            }
        viewModelScope.launch { loadActiveUsers() }
    }

    fun loadStats() {
        viewModelScope.launch {
            _statsLoading.value = true
            try {
                com.google.firebase.functions.FirebaseFunctions
                    .getInstance().getHttpsCallable("refreshStats").call().await()
                loadActiveUsers()
            } catch (_: Exception) {
                loadStatsFallback()
            } finally {
                _statsLoading.value = false
            }
        }
    }

    private suspend fun loadStatsFallback() {
        try {
            val todaySec = run {
                val c = java.util.Calendar.getInstance()
                c.set(java.util.Calendar.HOUR_OF_DAY, 0); c.set(java.util.Calendar.MINUTE, 0)
                c.set(java.util.Calendar.SECOND, 0); c.timeInMillis / 1000
            }
            val twoMinAgo = System.currentTimeMillis() / 1000 - 120

            val usersSnap    = firestore.collection("users").get().await()
            val totalUsers   = usersSnap.size()
            val androidUsers = usersSnap.documents.count { it.getString("platform") == "android" }
            val webUsers     = usersSnap.documents.count {
                val p = it.getString("platform") ?: ""; p == "web" || p.isBlank()
            }
            val newUsersToday = usersSnap.documents.count {
                (it.getTimestamp("createdAt") ?: it.getTimestamp("ts"))?.seconds?.let { s -> s >= todaySec } == true
            }
            val bannedUsers = usersSnap.documents.count { it.getBoolean("banned") == true }

            val onlineNow = try {
                firestore.collection("presence").whereEqualTo("online", true).get().await()
                    .documents.count { (it.getTimestamp("lastSeen")?.seconds ?: 0L) >= twoMinAgo }
            } catch (_: Exception) { 0 }

            val postsSnap     = firestore.collection("feed").get().await()
            val totalPosts    = postsSnap.documents.count { (it.getString("moderationStatus") ?: "active") == "active" }
            val newPostsToday = postsSnap.documents.count {
                (it.getString("moderationStatus") ?: "active") == "active" &&
                (it.getTimestamp("ts")?.seconds ?: 0L) >= todaySec
            }
            val pendingPosts  = postsSnap.documents.count { it.getString("moderationStatus") == "suspended" }

            val totalQuotes   = try { firestore.collectionGroup("quotes").get().await().size() } catch (_: Exception) { 0 }
            val totalReviews  = try { firestore.collectionGroup("reviews").get().await().size() } catch (_: Exception) { 0 }
            val totalSerials  = try { firestore.collection("serials").get().await().size() } catch (_: Exception) { 0 }
            val totalBooks    = try { firestore.collection("library_books").get().await().size() } catch (_: Exception) { 0 }
            val pendingReports = try {
                firestore.collection("reports").whereEqualTo("status", "pending").get().await().size()
            } catch (_: Exception) { 0 }

            val ps = PlatformStats(
                totalUsers = totalUsers, androidUsers = androidUsers, webUsers = webUsers,
                onlineNow = onlineNow, newUsersToday = newUsersToday,
                totalPosts = totalPosts, newPostsToday = newPostsToday,
                totalQuotes = totalQuotes, totalReviews = totalReviews,
                totalSerials = totalSerials, totalBooks = totalBooks,
                pendingPosts = pendingPosts, pendingReports = pendingReports,
                bannedUsers = bannedUsers, lastUpdated = System.currentTimeMillis(),
            )
            _platformStats.value = ps

            val map = mapOf(
                "totalUsers" to totalUsers, "androidUsers" to androidUsers, "webUsers" to webUsers,
                "onlineNow" to onlineNow, "newUsersToday" to newUsersToday,
                "totalPosts" to totalPosts, "newPostsToday" to newPostsToday,
                "totalQuotes" to totalQuotes, "totalReviews" to totalReviews,
                "totalComments" to 0, "totalSerials" to totalSerials, "totalBooks" to totalBooks,
                "pendingPosts" to pendingPosts, "pendingReports" to pendingReports,
                "bannedUsers" to bannedUsers, "lastUpdated" to System.currentTimeMillis(),
            )
            try { firestore.collection("appConfig").document("stats").set(map).await() } catch (_: Exception) {}
        } catch (e: Exception) { e.printStackTrace() }
    }
    private suspend fun loadActiveUsers() {
        try {
            val oneWeekAgo = com.google.firebase.Timestamp(
                System.currentTimeMillis() / 1000 - 7 * 86400, 0
            )
            // whereGreaterThan index gerektirir — tümünü çekip bellekte filtrele
            val presenceSnap = firestore.collection("presence")
                .limit(200).get().await()

            val result = mutableListOf<UserActivity>()
            val twoMinMs = 120_000L

            presenceSnap.documents.forEach { doc ->
                val d          = doc.data ?: return@forEach
                val uid        = doc.id
                // Bellekte son 7 gün filtresi
                val lastSeenCheck = (d["lastSeen"] as? com.google.firebase.Timestamp)?.seconds ?: 0L
                if (lastSeenCheck < oneWeekAgo.seconds) return@forEach
                val lastSeenTs = (d["lastSeen"] as? com.google.firebase.Timestamp)
                val lastSeenMs = lastSeenTs?.toDate()?.time ?: 0L
                val online     = (d["online"] as? Boolean == true) &&
                                 (System.currentTimeMillis() - lastSeenMs < twoMinMs)

                // User dökümanından ek bilgi al
                val userDoc = try {
                    firestore.collection("users").document(uid).get().await()
                } catch (_: Exception) { null }
                val ud = userDoc?.data

                result.add(UserActivity(
                    uid         = uid,
                    displayName = ud?.get("displayName") as? String
                                  ?: ud?.get("name") as? String ?: "—",
                    photoURL    = ud?.get("photoURL") as? String ?: "",
                    online      = online,
                    lastSeenMs  = lastSeenMs,
                    appVersion  = ud?.get("appVersion") as? String ?: "—",
                    platform    = ud?.get("platform")   as? String ?: "android",
                    postsCount  = (ud?.get("postsCount") as? Long)?.toInt() ?: 0,
                    level       = (ud?.get("level")      as? Long)?.toInt() ?: 1,
                ))
            }
            _activeUsers.value = result.sortedWith(
                compareByDescending<UserActivity> { it.online }
                    .thenByDescending { it.lastSeenMs }
            )
        } catch (e: Exception) { e.printStackTrace() }
    }

    // ── Şikayetler ────────────────────────────────────────────────────────────
    private val _reports = MutableStateFlow<List<Map<String, Any>>>(emptyList())
    private val _appeals = MutableStateFlow<List<com.heftreng.app.data.model.Appeal>>(emptyList())
    val appeals = _appeals.asStateFlow()
    val reports = _reports.asStateFlow()

    fun loadReports() {
        if (!_isAdmin.value) return
        viewModelScope.launch {
            _loading.value = true
            try {
                val snap = firestore.collection("reports")
                    .orderBy("ts", com.google.firebase.firestore.Query.Direction.DESCENDING)
                    .limit(100).get().await()
                _reports.value = snap.documents.mapNotNull { doc ->
                    val d = doc.data?.toMutableMap() ?: return@mapNotNull null
                    d["id"] = doc.id
                    d
                }
            } catch (e: Exception) { e.printStackTrace() }
            finally { _loading.value = false }
        }
    }

    fun updateReportStatus(reportId: String, status: String) {
        if (!_isAdmin.value) return
        viewModelScope.launch {
            try {
                firestore.collection("reports").document(reportId)
                    .update("status", status).await()
                _reports.value = _reports.value.map { r ->
                    if (r["id"] == reportId) r.toMutableMap().also { it["status"] = status } else r
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }
    // ══════════════════════════════════════════════════════════════════════
    //  GÖNDERI MODERASYON SİSTEMİ
    //  3 seviye: restricted | suspended | removed
    //  Her işlemde kullanıcıya bildirim + feed/appeals güncelleme
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Gönderiyi kısıtla.
     * restricted → sadece giriş yapanlar görür
     * suspended  → sadece gönderi sahibi görür
     * removed    → hiç kimse görmez, gönderi feed'den çıkar
     */
    fun moderatePost(
        postId      : String,
        targetUid   : String,
        targetName  : String,
        status      : String,   // "restricted" | "suspended" | "removed"
        reason      : String,   // kullanıcıya gösterilecek sebep
        adminNote   : String,   // sadece admin görür
    ) {
        if (!_isAdmin.value) return
        viewModelScope.launch {
            try {
                val myUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: ""

                // 1. Feed dokümanını güncelle
                firestore.collection("feed").document(postId).update(mapOf(
                    "moderationStatus" to status,
                    "moderationReason" to reason,
                    "moderationNote"   to adminNote,
                    "moderatedBy"      to myUid,
                    "moderatedAt"      to com.google.firebase.Timestamp.now(),
                )).await()

                // 2. Kullanıcıya bildirim gönder
                val notifTitle = when (status) {
                    "restricted" -> "Gönderiniz kısıtlandı"
                    "suspended"  -> "Gönderiniz askıya alındı"
                    "removed"    -> "Gönderiniz kaldırıldı"
                    else -> "Gönderi durumu güncellendi"
                }
                val notifBody = if (reason.isNotBlank()) reason else when (status) {
                    "restricted" -> "Gönderiniz yalnızca giriş yapmış kullanıcılara gösterilecek."
                    "suspended"  -> "Gönderiniz inceleniyor, şimdilik yalnızca siz görebilirsiniz."
                    "removed"    -> "Gönderiniz platform kurallarına aykırı bulundu ve kaldırıldı."
                    else -> ""
                }
                if (targetUid.isNotBlank()) {
                    firestore.collection("userNotifs").document(targetUid)
                        .collection("notifs").add(mapOf(
                            "type"      to "moderation",
                            "title"     to notifTitle,
                            "body"      to notifBody,
                            "postId"    to postId,
                            "status"    to status,
                            "read"      to false,
                            "ts"        to com.google.firebase.Timestamp.now(),
                        )).await()
                }

                // 3. Moderasyon kaydı tut (audit log)
                firestore.collection("moderationLogs").add(mapOf(
                    "postId"     to postId,
                    "targetUid"  to targetUid,
                    "targetName" to targetName,
                    "status"     to status,
                    "reason"     to reason,
                    "adminNote"  to adminNote,
                    "adminUid"   to myUid,
                    "ts"         to com.google.firebase.Timestamp.now(),
                )).await()

            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    /** Kısıtlamayı kaldır — gönderiyi aktif yap */
    fun restorePost(postId: String, targetUid: String) {
        if (!_isAdmin.value) return
        viewModelScope.launch {
            try {
                firestore.collection("feed").document(postId).update(mapOf(
                    "moderationStatus" to "active",
                    "moderationReason" to "",
                    "moderationNote"   to "",
                )).await()
                if (targetUid.isNotBlank()) {
                    firestore.collection("userNotifs").document(targetUid)
                        .collection("notifs").add(mapOf(
                            "type"   to "moderation",
                            "title"  to "Gönderiniz yeniden aktif edildi",
                            "body"   to "İnceleme sonucunda gönderiniz tekrar herkese açık hale getirildi.",
                            "read"   to false,
                            "ts"     to com.google.firebase.Timestamp.now(),
                        )).await()
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    // ── İTİRAZ YÖNETİMİ ──────────────────────────────────────────────────

    fun loadAppeals() {
        if (!_isAdmin.value) return
        viewModelScope.launch {
            try {
                val snap = firestore.collection("appeals")
                    .whereEqualTo("status", "pending")
                    .limit(50).get().await()
                _appeals.value = snap.documents.mapNotNull { doc ->
                    try {
                        doc.toObject(com.heftreng.app.data.model.Appeal::class.java)?.copy(id = doc.id)
                    } catch (_: Exception) { null }
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    /** İtirazı onayla — gönderiyi geri yükle */
    fun approveAppeal(appeal: com.heftreng.app.data.model.Appeal, adminNote: String = "") {
        if (!_isAdmin.value) return
        viewModelScope.launch {
            try {
                // İtirazı güncelle
                firestore.collection("appeals").document(appeal.id).update(mapOf(
                    "status"      to "approved",
                    "adminNote"   to adminNote,
                    "resolvedAt"  to com.google.firebase.Timestamp.now(),
                )).await()
                // Gönderiyi geri yükle
                restorePost(appeal.postId, appeal.postOwnerUid)
                // Kullanıcıya bildirim
                firestore.collection("userNotifs").document(appeal.postOwnerUid)
                    .collection("notifs").add(mapOf(
                        "type"   to "appeal_result",
                        "title"  to "İtirazınız kabul edildi",
                        "body"   to "Gönderiniz yeniden aktif edildi. ${if (adminNote.isNotBlank()) adminNote else ""}",
                        "postId" to appeal.postId,
                        "read"   to false,
                        "ts"     to com.google.firebase.Timestamp.now(),
                    )).await()
                _appeals.value = _appeals.value.filter { it.id != appeal.id }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    /** İtirazı reddet — kısıtlama devam eder */
    fun rejectAppeal(appeal: com.heftreng.app.data.model.Appeal, adminNote: String = "") {
        if (!_isAdmin.value) return
        viewModelScope.launch {
            try {
                firestore.collection("appeals").document(appeal.id).update(mapOf(
                    "status"     to "rejected",
                    "adminNote"  to adminNote,
                    "resolvedAt" to com.google.firebase.Timestamp.now(),
                )).await()
                // Kullanıcıya bildirim
                firestore.collection("userNotifs").document(appeal.postOwnerUid)
                    .collection("notifs").add(mapOf(
                        "type"   to "appeal_result",
                        "title"  to "İtirazınız reddedildi",
                        "body"   to "Gönderinizle ilgili kararımız geçerliliğini korumaktadır. ${if (adminNote.isNotBlank()) adminNote else ""}",
                        "postId" to appeal.postId,
                        "read"   to false,
                        "ts"     to com.google.firebase.Timestamp.now(),
                    )).await()
                _appeals.value = _appeals.value.filter { it.id != appeal.id }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }


    override fun onCleared() {
        super.onCleared()
        statsListener?.remove()
        statsListener = null
    }
}
