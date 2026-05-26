package com.heftreng.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.heftreng.app.data.model.User
import dagger.hilt.android.lifecycle.HiltViewModel
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

    private val _stats        = MutableStateFlow<Map<String, Int>>(emptyMap())
    val stats = _stats.asStateFlow()

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
    fun loadStats() {
        viewModelScope.launch {
            _statsLoading.value = true
            try {
                // Her sorgu bağımsız — biri hata verse diğerleri çalışmaya devam eder
                val users   = try { firestore.collection("users").get().await().size() } catch (_: Exception) { -1 }
                val posts   = try { firestore.collection("feed").limit(500).get().await().size() } catch (_: Exception) { -1 }
                val serials = try { firestore.collection("serials").get().await().size() } catch (_: Exception) { -1 }
                val books   = try { firestore.collection("library_books").get().await().size() } catch (_: Exception) { -1 }
                // pending: feed'de moderationStatus="suspended" olan postlar
                val pending = try {
                    firestore.collection("feed")
                        .whereEqualTo("moderationStatus", "suspended")
                        .get().await().size()
                } catch (_: Exception) { -1 }
                val reports = try { firestore.collection("reports").whereEqualTo("status", "pending").get().await().size() } catch (_: Exception) { -1 }
                val banned  = try { firestore.collection("users").whereEqualTo("banned", true).get().await().size() } catch (_: Exception) { -1 }

                // Son 24 saatte kayıt olan kullanıcılar
                // whereGreaterThan index gerektirebilir — client-side filtre kullan
                val oneDayAgo = com.google.firebase.Timestamp(
                    System.currentTimeMillis() / 1000 - 86400, 0
                )
                val newUsers = try {
                    firestore.collection("users")
                        .get().await().documents.count { doc ->
                            val ts = doc.getTimestamp("createdAt")
                                ?: doc.getTimestamp("ts")
                            ts != null && ts.seconds > oneDayAgo.seconds
                        }
                } catch (_: Exception) { -1 }

                // Son 24 saatte atılan gönderiler — client-side filtre
                val newPosts = try {
                    firestore.collection("feed")
                        .orderBy("ts", com.google.firebase.firestore.Query.Direction.DESCENDING)
                        .limit(500).get().await().documents.count { doc ->
                            val ts = doc.getTimestamp("ts")
                            ts != null && ts.seconds > oneDayAgo.seconds
                        }
                } catch (_: Exception) { -1 }

                // Online kullanıcılar — whereEqualTo + whereGreaterThan composite index gerektirebilir
                // Güvenli fallback: sadece online=true filtrele, lastSeen'i client-side filtrele
                val twoMinAgo = com.google.firebase.Timestamp(
                    System.currentTimeMillis() / 1000 - 120, 0
                )
                val onlineCount = try {
                    firestore.collection("presence")
                        .whereEqualTo("online", true)
                        .get().await()
                        .documents.count { doc ->
                            val ls = doc.getTimestamp("lastSeen")?.seconds ?: 0L
                            (System.currentTimeMillis() / 1000 - ls) < 120
                        }
                } catch (_: Exception) { -1 }

                _stats.value = mapOf(
                    "users"    to users,
                    "posts"    to posts,
                    "serials"  to serials,
                    "books"    to books,
                    "pending"  to pending,
                    "reports"  to reports,
                    "banned"   to banned,
                    "newUsers" to newUsers,
                    "newPosts" to newPosts,
                    "online"   to onlineCount,
                )

                // Aktif kullanıcı hareketleri — presence + users join
                loadActiveUsers()
            } catch (e: Exception) { e.printStackTrace() }
            finally { _statsLoading.value = false }
        }
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
}
