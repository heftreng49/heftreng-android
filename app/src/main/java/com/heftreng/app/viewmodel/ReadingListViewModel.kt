package com.heftreng.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.heftreng.app.data.model.ReadingListEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

// Okuma listesi durumları — XML temasıyla birebir aynı
enum class RlStatus(val key: String, val labelTr: String, val labelKu: String, val color: Long) {
    READING   ("okuyorum",           "Okuyorum",            "Dixwînim",          0xFF2563EB),
    WANT      ("okumak_istiyorum",   "Okumak İstiyorum",    "Dixwazim Bixwînim", 0xFF7C3AED),
    READ      ("okudum",             "Okudum",              "Xwendiye",          0xFF059669),
    DROPPED   ("biraktim",           "Bıraktım",            "Berda",             0xFFDC2626),
}

@HiltViewModel
class ReadingListViewModel @Inject constructor(
    private val auth     : FirebaseAuth,
    private val firestore: FirebaseFirestore,
) : ViewModel() {

    // status → liste
    private val _entries = MutableStateFlow<Map<String, List<ReadingListEntry>>>(emptyMap())
    val entries = _entries.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading = _loading.asStateFlow()

    val uid get() = auth.currentUser?.uid ?: ""

    // ── Okuma listesini yükle ───────────────────────────
    // Firestore: readingLists/{uid}/books — status'a göre grupla
    fun load(targetUid: String = uid) {
        if (targetUid.isEmpty()) return
        viewModelScope.launch {
            _loading.value = true
            try {
                val snap = firestore.collection("readingLists")
                    .document(targetUid)
                    .collection("books")
                    .orderBy("updatedAt", Query.Direction.DESCENDING)
                    .limit(50).get().await()

                val map = mutableMapOf<String, MutableList<ReadingListEntry>>()
                RlStatus.values().forEach { map[it.key] = mutableListOf() }

                snap.documents.forEach { doc ->
                    val d   = doc.data ?: return@forEach
                    val ent = ReadingListEntry(
                        sid        = d["sid"]        as? String ?: doc.id,
                        title      = d["title"]      as? String ?: "",
                        coverImg   = d["coverImg"]   as? String ?: "",
                        bg         = d["bg"]         as? String ?: "",
                        status     = d["status"]     as? String ?: "",
                        updatedAt  = d["updatedAt"]  as? com.google.firebase.Timestamp,
                        source     = d["source"]     as? String ?: "serial",
                        authorName = d["authorName"] as? String ?: "",
                    )
                    map.getOrPut(ent.status) { mutableListOf() }.add(ent)
                }
                _entries.value = map
            } catch (e: Exception) { e.printStackTrace() }
            finally { _loading.value = false }
        }
    }

    // ── Durumu güncelle / ekle ──────────────────────────
    // Firestore: readingLists/{uid}/books/{sid}
    // Alanlar: sid, title, coverImg, bg, status, updatedAt
    fun setStatus(sid: String, title: String, coverImg: String, bg: String, status: RlStatus?) {
        if (uid.isEmpty()) return
        viewModelScope.launch {
            try {
                val ref = firestore.collection("readingLists")
                    .document(uid).collection("books").document(sid)
                val prevStatus = getStatus(sid)
                if (status == null) {
                    ref.delete().await()
                } else {
                    ref.set(mapOf(
                        "sid"       to sid,
                        "title"     to title,
                        "coverImg"  to coverImg,
                        "bg"        to bg,
                        "status"    to status.key,
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )).await()
                }
                // booksRead sayacı güncelle
                val userRef = firestore.collection("users").document(uid)
                when {
                    status == RlStatus.READ && prevStatus != RlStatus.READ ->
                        userRef.update("booksRead", FieldValue.increment(1)).await()
                    status != RlStatus.READ && prevStatus == RlStatus.READ ->
                        userRef.update("booksRead", FieldValue.increment(-1)).await()
                    status == null && prevStatus == RlStatus.READ ->
                        userRef.update("booksRead", FieldValue.increment(-1)).await()
                }
                load()
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    // ── Mevcut durumu sorgula ───────────────────────────
    fun getStatus(sid: String): RlStatus? {
        _entries.value.forEach { (statusKey, list) ->
            if (list.any { it.sid == sid }) {
                return RlStatus.values().find { it.key == statusKey }
            }
        }
        return null
    }

    // ── Sil ────────────────────────────────────────────
    fun remove(sid: String) = setStatus(sid, "", "", "", null)

    // ══════════════════════════════════════════════════════════════════════
    //  KÜTÜPHANEKİTABI — library_books koleksiyonu için ayrı fonksiyonlar
    //  source = "library" olarak kaydeder, profil navigate ayrımı buradan
    // ══════════════════════════════════════════════════════════════════════

    fun setLibraryBookStatus(
        bookId    : String,
        title     : String,
        coverImg  : String,
        authorName: String,
        status    : RlStatus?,
    ) {
        if (uid.isEmpty()) return
        viewModelScope.launch {
            try {
                val ref = firestore.collection("readingLists")
                    .document(uid).collection("books").document(bookId)
                val prevStatus = getStatus(bookId)
                if (status == null) {
                    ref.delete().await()
                } else {
                    ref.set(mapOf(
                        "sid"        to bookId,
                        "title"      to title,
                        "coverImg"   to coverImg,
                        "bg"         to "",
                        "authorName" to authorName,
                        "source"     to "library",
                        "status"     to status.key,
                        "updatedAt"  to FieldValue.serverTimestamp(),
                    )).await()
                }
                // booksRead sayacı güncelle
                val userRef = firestore.collection("users").document(uid)
                when {
                    status == RlStatus.READ && prevStatus != RlStatus.READ ->
                        userRef.update("booksRead", FieldValue.increment(1)).await()
                    status != RlStatus.READ && prevStatus == RlStatus.READ ->
                        userRef.update("booksRead", FieldValue.increment(-1)).await()
                    status == null && prevStatus == RlStatus.READ ->
                        userRef.update("booksRead", FieldValue.increment(-1)).await()
                }
                load()
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun getLibraryBookStatus(bookId: String): RlStatus? = getStatus(bookId)

    fun removeLibraryBook(bookId: String) = setLibraryBookStatus(bookId, "", "", "", null)
}
