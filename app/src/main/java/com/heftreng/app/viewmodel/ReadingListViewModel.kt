package com.heftreng.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.heftreng.app.data.model.ReadingListEntry
import com.heftreng.app.data.repository.LibraryRepository
import com.heftreng.app.data.repository.ReadingStatusRow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

// Okuma listesi durumları — XML temasıyla birebir aynı
enum class RlStatus(val key: String, val labelTr: String, val labelKu: String, val color: Long) {
    READING   ("okuyorum",           "Okuyorum",            "Dixwînim",          0xFF2563EB),
    WANT      ("okumak_istiyorum",   "Okumak İstiyorum",    "Dixwazim Bixwînim", 0xFF7C3AED),
    READ      ("okudum",             "Okudum",              "Xwendiye",          0xFF059669),
    DROPPED   ("biraktim",           "Bıraktım",            "Berda",             0xFFDC2626),
}

// ═══════════════════════════════════════════════════════════════════════
//  ReadingListViewModel — Supabase tabanlı (v2)
//
//  Okuma + Yazma: Supabase reading_status tablosu (Firestore readingLists/
//  taşındı). uid+book_id birincil anahtar — hem library_books hem
//  serials/books içerikleri için aynı tablo kullanılır (source alanı ile
//  ayrılır).
// ═══════════════════════════════════════════════════════════════════════

private fun ReadingStatusRow.toDomain() = ReadingListEntry(
    sid         = bookId,
    title       = title,
    coverImg    = coverImg,
    bg          = bg,
    status      = status,
    updatedAt   = null,
    source      = source,
    authorName  = authorName,
    currentPage = currentPage,
)

@HiltViewModel
class ReadingListViewModel @Inject constructor(
    private val auth   : FirebaseAuth,
    private val library: LibraryRepository,
) : ViewModel() {

    // status → liste
    private val _entries = MutableStateFlow<Map<String, List<ReadingListEntry>>>(emptyMap())
    val entries = _entries.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading = _loading.asStateFlow()

    val uid get() = auth.currentUser?.uid ?: ""

    // ── Okuma listesini yükle ───────────────────────────
    // Supabase: reading_status — uid'e göre çek, status'a göre grupla
    fun load(targetUid: String = uid) {
        if (targetUid.isEmpty()) return
        viewModelScope.launch {
            _loading.value = true
            try {
                val rows = library.getReadingStatus(targetUid, limit = 50)

                val map = mutableMapOf<String, MutableList<ReadingListEntry>>()
                RlStatus.values().forEach { map[it.key] = mutableListOf() }

                rows.forEach { row ->
                    val ent = row.toDomain()
                    map.getOrPut(ent.status) { mutableListOf() }.add(ent)
                }
                _entries.value = map
            } catch (e: Exception) { e.printStackTrace() }
            finally { _loading.value = false }
        }
    }

    // ── Durumu güncelle / ekle ──────────────────────────
    // Supabase: reading_status (uid, book_id) — upsert / delete
    fun setStatus(sid: String, title: String, coverImg: String, bg: String, status: RlStatus?) {
        if (uid.isEmpty()) return
        viewModelScope.launch {
            try {
                if (status == null) {
                    library.deleteReadingStatus(uid, sid)
                } else {
                    library.upsertReadingStatus(
                        ReadingStatusRow(
                            uid      = uid,
                            bookId   = sid,
                            status   = status.key,
                            title    = title,
                            coverImg = coverImg,
                            bg       = bg,
                            source   = "serial",
                        )
                    )
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
    //  KÜTÜPHANE KİTABI — library_books için ayrı fonksiyonlar
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
                if (status == null) {
                    library.deleteReadingStatus(uid, bookId)
                } else {
                    library.upsertReadingStatus(
                        ReadingStatusRow(
                            uid        = uid,
                            bookId     = bookId,
                            status     = status.key,
                            title      = title,
                            coverImg   = coverImg,
                            bg         = "",
                            authorName = authorName,
                            source     = "library",
                        )
                    )
                }
                load()
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun getLibraryBookStatus(bookId: String): RlStatus? = getStatus(bookId)

    fun removeLibraryBook(bookId: String) = setLibraryBookStatus(bookId, "", "", "", null)

    // ══════════════════════════════════════════════════════════════════════
    //  Okuma ilerlemesi — "X. sayfadayım" paylaşımı (Goodreads benzeri)
    //  Mevcut durumu korur, sadece current_page'i günceller.
    // ══════════════════════════════════════════════════════════════════════

    fun updateCurrentPage(sid: String, currentPage: Int) {
        if (uid.isEmpty()) return
        viewModelScope.launch {
            try {
                val existing = _entries.value.values.flatten().find { it.sid == sid } ?: return@launch
                library.upsertReadingStatus(
                    ReadingStatusRow(
                        uid         = uid,
                        bookId      = sid,
                        status      = existing.status,
                        title       = existing.title,
                        coverImg    = existing.coverImg,
                        bg          = existing.bg,
                        authorName  = existing.authorName,
                        source      = existing.source,
                        currentPage = currentPage,
                    )
                )
                load()
            } catch (e: Exception) { e.printStackTrace() }
        }
    }
}
