package com.heftreng.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.heftreng.app.data.model.Chapter
import com.heftreng.app.data.model.Serial
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@HiltViewModel
class SerialsViewModel @Inject constructor(
    private val auth     : FirebaseAuth,
    private val firestore: FirebaseFirestore,
) : ViewModel() {

    private val _serials  = MutableStateFlow<List<Serial>>(emptyList())
    val serials = _serials.asStateFlow()

    private val _mySerials = MutableStateFlow<List<Serial>>(emptyList())
    val mySerials = _mySerials.asStateFlow()

    private val _chapters = MutableStateFlow<List<Chapter>>(emptyList())
    val chapters = _chapters.asStateFlow()

    private val _selectedSerial = MutableStateFlow<Serial?>(null)
    val selectedSerial = _selectedSerial.asStateFlow()

    private val _selectedChapter = MutableStateFlow<Chapter?>(null)
    val selectedChapter = _selectedChapter.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading = _loading.asStateFlow()

    val uid get() = auth.currentUser?.uid ?: ""

    private var likedSerialIds = emptySet<String>()

    // ── Tüm seriler (keşif) ─────────────────────────────
    fun loadSerials() {
        viewModelScope.launch {
            _loading.value = true
            try {
                if (uid.isNotEmpty()) loadLikedSerials()
                val snap = firestore.collection("serials")
                    .orderBy("updatedAt", Query.Direction.DESCENDING)
                    .limit(30).get().await()
                _serials.value = snap.documents.mapNotNull { it.toSerial(likedSerialIds) }
            } catch (e: Exception) { e.printStackTrace() }
            finally { _loading.value = false }
        }
    }

    // ── Kendi serileri (profil) ─────────────────────────
    fun loadMySerials(targetUid: String = uid) {
        viewModelScope.launch {
            try {
                val snap = firestore.collection("serials")
                    .whereEqualTo("uid", targetUid)
                    .orderBy("updatedAt", Query.Direction.DESCENDING)
                    .limit(30).get().await()
                _mySerials.value = snap.documents.mapNotNull { it.toSerial(likedSerialIds) }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    // ── Seri detayı + bölüm listesi ────────────────────
    fun loadSerial(serialId: String) {
        viewModelScope.launch {
            _loading.value = true
            try {
                val doc = firestore.collection("serials").document(serialId).get().await()
                _selectedSerial.value = doc.toSerial(likedSerialIds)

                val chapSnap = firestore.collection("serials").document(serialId)
                    .collection("chapters")
                    .orderBy("order", Query.Direction.ASCENDING)
                    .limit(100).get().await()
                _chapters.value = chapSnap.documents.mapNotNull { it.toChapter() }
            } catch (e: Exception) { e.printStackTrace() }
            finally { _loading.value = false }
        }
    }

    // ── Bölüm içeriği ──────────────────────────────────
    fun loadChapter(serialId: String, chapterId: String) {
        viewModelScope.launch {
            try {
                val doc = firestore.collection("serials").document(serialId)
                    .collection("chapters").document(chapterId).get().await()
                _selectedChapter.value = doc.toChapter()
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    // ── Yeni seri oluştur ───────────────────────────────
    fun createSerial(title: String, desc: String, genre: String, coverImg: String = "") {
        if (uid.isEmpty()) return
        viewModelScope.launch {
            try {
                val userDoc = firestore.collection("users").document(uid).get().await()
                val name    = userDoc.getString("displayName") ?: auth.currentUser?.displayName ?: ""
                val photo   = userDoc.getString("photoURL")    ?: auth.currentUser?.photoUrl?.toString() ?: ""
                firestore.collection("serials").add(mapOf(
                    "uid"          to uid,
                    "name"         to name,
                    "photoURL"     to photo,
                    "title"        to title,
                    "desc"         to desc,
                    "genre"        to genre,
                    "coverImg"     to coverImg,
                    "chapterCount" to 0,
                    "likes"        to 0,
                    "ts"           to FieldValue.serverTimestamp(),
                    "updatedAt"    to FieldValue.serverTimestamp(),
                )).await()
                loadMySerials()
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    // ── Yeni bölüm ekle ────────────────────────────────
    fun addChapter(serialId: String, title: String, body: String) {
        if (uid.isEmpty()) return
        viewModelScope.launch {
            try {
                val order = (_chapters.value.maxOfOrNull { it.order } ?: 0) + 1
                val wc    = body.trim().split("\\s+".toRegex()).size
                firestore.collection("serials").document(serialId)
                    .collection("chapters").add(mapOf(
                        "serialId"  to serialId,
                        "title"     to title,
                        "body"      to body,
                        "order"     to order,
                        "wordCount" to wc,
                        "uid"       to uid,
                        "ts"        to FieldValue.serverTimestamp(),
                    )).await()
                firestore.collection("serials").document(serialId).update(
                    mapOf(
                        "chapterCount" to FieldValue.increment(1),
                        "updatedAt"    to FieldValue.serverTimestamp(),
                    )
                ).await()
                // Feed'e paylaş
                val userDoc = firestore.collection("users").document(uid).get().await()
                val serial  = _selectedSerial.value
                val myName = userDoc.getString("displayName") ?: userDoc.getString("name") ?: ""
                firestore.collection("feed").add(mapOf(
                    "uid"         to uid,
                    "name"        to myName,          // tema: name alanı
                    "displayName" to myName,           // Android uyumu
                    "username"    to (userDoc.getString("username") ?: ""),
                    "photoURL"    to (userDoc.getString("photoURL") ?: ""),
                    "text"        to "📖 ${serial?.title ?: ""} — Bölüm $order: $title",
                    "imgUrl"      to (serial?.coverImg ?: ""),   // tema: imgUrl
                    "imageURL"    to (serial?.coverImg ?: ""),   // Android uyumu
                    "bookName"    to (serial?.title ?: ""),
                    "authorName"  to myName,
                    "repostType"  to "serial",         // tema: repostType
                    "serialId"    to serialId,
                    "chapterId"   to "",
                    "likes"       to 0, "saves" to 0, "cmtCount" to 0, "reposts" to 0,
                    "ts"          to FieldValue.serverTimestamp(),
                )).await()
                loadSerial(serialId)
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    // ── Seri beğen ──────────────────────────────────────
    fun toggleLikeSerial(serial: Serial) {
        if (uid.isEmpty()) return
        val nowLiked = !serial.isLikedByMe
        likedSerialIds = if (nowLiked) likedSerialIds + serial.id else likedSerialIds - serial.id
        _serials.value = _serials.value.map {
            if (it.id == serial.id) it.copy(isLikedByMe = nowLiked, likes = it.likes + if (nowLiked) 1 else -1) else it
        }
        _selectedSerial.value?.let { s ->
            if (s.id == serial.id) _selectedSerial.value = s.copy(isLikedByMe = nowLiked, likes = s.likes + if (nowLiked) 1 else -1)
        }
        viewModelScope.launch {
            try {
                val ref = firestore.collection("serialLikes").document("${serial.id}_$uid")
                val ser = firestore.collection("serials").document(serial.id)
                if (nowLiked) {
                    ref.set(mapOf("uid" to uid, "serialId" to serial.id, "ts" to Timestamp.now())).await()
                    ser.update("likes", FieldValue.increment(1)).await()
                } else {
                    ref.delete().await()
                    ser.update("likes", FieldValue.increment(-1)).await()
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    // ── Bölüm beğeni toggle ────────────────────────────────────────────────────
    // Tema: chapterLikes/{chId}_{uid} — {chapterId, serialId, uid, ts}
    fun toggleLikeChapter(serialId: String, chapterId: String) {
        if (uid.isEmpty()) return
        viewModelScope.launch {
            try {
                val docId = "${chapterId}_$uid"
                val ref   = firestore.collection("chapterLikes").document(docId)
                val chRef = firestore.collection("serials").document(serialId)
                    .collection("chapters").document(chapterId)
                val exists = ref.get().await().exists()
                if (exists) {
                    ref.delete().await()
                    chRef.update("likes", com.google.firebase.firestore.FieldValue.increment(-1)).await()
                } else {
                    ref.set(mapOf(
                        "chapterId" to chapterId,
                        "serialId"  to serialId,
                        "uid"       to uid,
                        "ts"        to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                    )).await()
                    chRef.update("likes", com.google.firebase.firestore.FieldValue.increment(1)).await()
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    // Bölüm beğeni durumu sorgula
    fun isChapterLiked(chapterId: String, callback: (Boolean) -> Unit) {
        if (uid.isEmpty()) { callback(false); return }
        viewModelScope.launch {
            try {
                val exists = firestore.collection("chapterLikes")
                    .document("${chapterId}_$uid").get().await().exists()
                callback(exists)
            } catch (e: Exception) { callback(false) }
        }
    }

    // ── Bölüm okuma ilerlemesi ──────────────────────────────────────────────
    // Tema: localStorage["hf_rp_"+sid+"_"+chId] = Math.round(pct*1000)
    // Android: SharedPreferences aynı mantıkla
    private var _readPrefs: android.content.SharedPreferences? = null

    fun initReadPrefs(context: android.content.Context) {
        _readPrefs = context.getSharedPreferences("heft_read_progress", android.content.Context.MODE_PRIVATE)
    }

    fun saveReadProgress(serialId: String, chapterId: String, scrollPct: Float) {
        val pct = (scrollPct * 1000).toInt().coerceIn(0, 1000)
        _readPrefs?.edit()?.putInt("rp_${serialId}_$chapterId", pct)?.apply()
    }

    fun loadReadProgress(serialId: String, chapterId: String): Float {
        val raw = _readPrefs?.getInt("rp_${serialId}_$chapterId", 0) ?: 0
        return raw / 1000f
    }

    fun clearReadProgress(serialId: String, chapterId: String) {
        _readPrefs?.edit()?.remove("rp_${serialId}_$chapterId")?.apply()
    }

    private suspend fun loadLikedSerials() {
        try {
            likedSerialIds = firestore.collection("serialLikes").whereEqualTo("uid", uid)
                .get().await().documents.mapNotNull { it.getString("serialId") }.toSet()
        } catch (e: Exception) { e.printStackTrace() }
    }

    // ── Extension mapper'lar ────────────────────────────
    private fun com.google.firebase.firestore.DocumentSnapshot.toSerial(liked: Set<String>): Serial? {
        val d = data ?: return null
        return Serial(
            id           = id,
            uid          = d["uid"]          as? String ?: "",
            name         = d["name"]         as? String ?: "",
            photoURL     = d["photoURL"]     as? String ?: "",
            title        = d["title"]        as? String ?: "",
            desc         = d["desc"]         as? String ?: "",
            genre        = d["genre"]        as? String ?: "",
            coverImg     = d["coverImg"]     as? String ?: "",
            chapterCount = (d["chapterCount"] as? Long)?.toInt() ?: 0,
            likes        = (d["likes"]        as? Long)?.toInt() ?: 0,
            ts           = d["ts"]           as? Timestamp,
            updatedAt    = d["updatedAt"]    as? Timestamp,
            isLikedByMe  = id in liked,
        )
    }

    // ── Bölüm Düzenle ────────────────────────────────────────────────────────
    fun updateChapter(serialId: String, chapterId: String, newTitle: String, newBody: String) {
        viewModelScope.launch {
            try {
                val wc = newBody.trim().split("\\s+".toRegex()).count { it.isNotBlank() }
                firestore.collection("serials").document(serialId)
                    .collection("chapters").document(chapterId)
                    .update(mapOf("title" to newTitle.trim(), "body" to newBody.trim(), "wordCount" to wc)).await()
                _chapters.value = _chapters.value.map {
                    if (it.id == chapterId) it.copy(title = newTitle.trim(), body = newBody.trim(), wordCount = wc) else it
                }
                _selectedChapter.value = _selectedChapter.value?.let {
                    if (it.id == chapterId) it.copy(title = newTitle.trim(), body = newBody.trim(), wordCount = wc) else it
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    // ── Bölüm Sil ─────────────────────────────────────────────────────────────
    fun deleteChapter(serialId: String, chapterId: String) {
        viewModelScope.launch {
            try {
                firestore.collection("serials").document(serialId)
                    .collection("chapters").document(chapterId).delete().await()
                _chapters.value = _chapters.value.filter { it.id != chapterId }
                firestore.collection("serials").document(serialId)
                    .update("chapterCount", _chapters.value.size).await()
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    private fun com.google.firebase.firestore.DocumentSnapshot.toChapter(): Chapter? {
        val d = data ?: return null
        return Chapter(
            id        = id,
            serialId  = d["serialId"]  as? String ?: "",
            title     = d["title"]     as? String ?: "",
            body      = d["body"]      as? String ?: "",
            order     = (d["order"]    as? Long)?.toInt() ?: 0,
            wordCount = (d["wordCount"] as? Long)?.toInt() ?: 0,
            uid       = d["uid"]       as? String ?: "",
            ts        = d["ts"]        as? Timestamp,
        )
    }
}
