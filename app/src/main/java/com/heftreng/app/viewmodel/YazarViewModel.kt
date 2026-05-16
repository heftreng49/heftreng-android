package com.heftreng.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

// ── Veri modeli ───────────────────────────────────────────────────────────────
data class PendingPost(
    val id            : String = "",
    val title         : String = "",
    val content       : String = "",
    val summary       : String = "",
    val cover         : String = "",
    val category      : String = "",
    val lang          : String = "tr",
    val tags          : List<String> = emptyList(),
    val authorId      : String = "",
    val authorName    : String = "",
    val authorEmail   : String = "",
    val status        : String = "pending",   // pending | approved | rejected
    val adminNote     : String = "",
    val bloggerPostId : String = "",
    val bloggerPostUrl: String = "",
)

// ── ViewModel ─────────────────────────────────────────────────────────────────
@HiltViewModel
class YazarViewModel @Inject constructor(
    private val auth     : FirebaseAuth,
    private val firestore: FirebaseFirestore,
) : ViewModel() {

    val isLoggedIn get() = auth.currentUser != null
    val currentUser get() = auth.currentUser

    private val _myPosts  = MutableStateFlow<List<PendingPost>>(emptyList())
    val myPosts = _myPosts.asStateFlow()

    private val _loading  = MutableStateFlow(false)
    val loading = _loading.asStateFlow()

    private val _submitResult = MutableStateFlow<SubmitResult?>(null)
    val submitResult = _submitResult.asStateFlow()

    sealed class SubmitResult {
        object Success : SubmitResult()
        data class Error(val message: String) : SubmitResult()
    }

    // Kategoriler — web temasındaki datalist ile aynı
    val categories = listOf(
        "Yaşam", "Unutulmayanlar", "Tourette Sendromu", "Şiir",
        "Suzan Suzi", "Sizden Gelenler", "Şairden Şiirler", "Sevgi",
        "Kurdî", "Korku", "Tüm Yazılar", "Özgürlük", "Umuda Dair",
        "Kürtçe", "Kitap İncelemesi", "Kısa Yazılar", "İnsan",
        "İnanç", "Hikaye", "Hayata Dair", "İhtimal", "Hakikat",
        "Düşünce", "Aşka Dair", "Anılar", "Aile", "Aşk",
        "Edebiyat", "Acı",
    )

    // ── Kendi yazılarımı yükle ────────────────────────────────────────────────
    fun loadMyPosts() {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            _loading.value = true
            try {
                val snap = firestore.collection("pendingPosts")
                    .whereEqualTo("authorId", uid)
                    .orderBy("createdAt", Query.Direction.DESCENDING)
                    .limit(50)
                    .get().await()
                _myPosts.value = snap.documents.mapNotNull { doc ->
                    val d = doc.data ?: return@mapNotNull null
                    @Suppress("UNCHECKED_CAST")
                    PendingPost(
                        id             = doc.id,
                        title          = d["title"]          as? String ?: "",
                        content        = d["content"]        as? String ?: "",
                        summary        = d["summary"]        as? String ?: "",
                        cover          = d["cover"]          as? String ?: "",
                        category       = d["category"]       as? String ?: "",
                        lang           = d["lang"]           as? String ?: "tr",
                        tags           = (d["tags"]          as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                        authorId       = d["authorId"]       as? String ?: "",
                        authorName     = d["authorName"]     as? String ?: "",
                        authorEmail    = d["authorEmail"]    as? String ?: "",
                        status         = d["status"]         as? String ?: "pending",
                        adminNote      = d["adminNote"]      as? String ?: "",
                        bloggerPostId  = d["bloggerPostId"]  as? String ?: "",
                        bloggerPostUrl = d["bloggerPostUrl"] as? String ?: "",
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _loading.value = false
            }
        }
    }

    // ── Yazı gönder ───────────────────────────────────────────────────────────
    fun submitPost(
        title   : String,
        content : String,
        summary : String,
        cover   : String,
        category: String,
        lang    : String,
        tags    : List<String>,
    ) {
        val user = auth.currentUser ?: run {
            _submitResult.value = SubmitResult.Error("Giriş yapman gerekiyor")
            return
        }
        if (title.isBlank()) {
            _submitResult.value = SubmitResult.Error("Başlık zorunlu")
            return
        }
        if (content.length < 100) {
            _submitResult.value = SubmitResult.Error("İçerik en az 100 karakter olmalı")
            return
        }
        if (category.isBlank()) {
            _submitResult.value = SubmitResult.Error("Kategori seçmelisin")
            return
        }

        viewModelScope.launch {
            _loading.value = true
            try {
                val userName = user.displayName ?: user.email?.substringBefore("@") ?: "?"
                firestore.collection("pendingPosts").add(
                    mapOf(
                        "title"         to title.trim(),
                        "content"       to content.trim(),
                        "summary"       to summary.ifBlank { title.take(120) },
                        "cover"         to cover.trim(),
                        "category"      to category.trim(),
                        "lang"          to lang,
                        "tags"          to tags,
                        "authorId"      to user.uid,
                        "authorEmail"   to (user.email ?: ""),
                        "authorName"    to userName,
                        "status"        to "pending",
                        "adminNote"     to "",
                        "bloggerPostId" to "",
                        "bloggerPostUrl" to "",
                        "createdAt"     to FieldValue.serverTimestamp(),
                        "updatedAt"     to FieldValue.serverTimestamp(),
                    )
                ).await()
                _submitResult.value = SubmitResult.Success
                loadMyPosts()
            } catch (e: Exception) {
                _submitResult.value = SubmitResult.Error(e.message ?: "Hata oluştu")
            } finally {
                _loading.value = false
            }
        }
    }

    // ── Yazıyı geri çek (pending iken) ───────────────────────────────────────
    fun withdrawPost(postId: String) {
        viewModelScope.launch {
            try {
                firestore.collection("pendingPosts").document(postId).delete().await()
                _myPosts.value = _myPosts.value.filter { it.id != postId }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun clearResult() { _submitResult.value = null }

    // ════════════════════════════════════════════════════════════
    // ADMİN FONKSİYONLARI
    // ════════════════════════════════════════════════════════════

    private val _pendingPosts = MutableStateFlow<List<PendingPost>>(emptyList())
    val pendingPosts = _pendingPosts.asStateFlow()

    private val _pendingLoading = MutableStateFlow(false)
    val pendingLoading = _pendingLoading.asStateFlow()

    private val _pendingStats = MutableStateFlow(PendingStats())
    val pendingStats = _pendingStats.asStateFlow()

    data class PendingStats(
        val pending  : Int = 0,
        val approved : Int = 0,
        val rejected : Int = 0,
    )

    // Tüm pending yazıları yükle (admin)
    fun loadAllPendingPosts(filter: String = "all") {
        viewModelScope.launch {
            _pendingLoading.value = true
            try {
                var q = firestore.collection("pendingPosts")
                    .orderBy("createdAt", Query.Direction.DESCENDING)
                    .limit(100)
                val snap = q.get().await()
                val all = snap.documents.mapNotNull { doc ->
                    val d = doc.data ?: return@mapNotNull null
                    @Suppress("UNCHECKED_CAST")
                    PendingPost(
                        id             = doc.id,
                        title          = d["title"]          as? String ?: "",
                        content        = d["content"]        as? String ?: "",
                        summary        = d["summary"]        as? String ?: "",
                        cover          = d["cover"]          as? String ?: "",
                        category       = d["category"]       as? String ?: "",
                        lang           = d["lang"]           as? String ?: "tr",
                        tags           = (d["tags"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                        authorId       = d["authorId"]       as? String ?: "",
                        authorName     = d["authorName"]     as? String ?: "",
                        authorEmail    = d["authorEmail"]    as? String ?: "",
                        status         = d["status"]         as? String ?: "pending",
                        adminNote      = d["adminNote"]      as? String ?: "",
                        bloggerPostId  = d["bloggerPostId"]  as? String ?: "",
                        bloggerPostUrl = d["bloggerPostUrl"] as? String ?: "",
                    )
                }
                // İstatistik
                _pendingStats.value = PendingStats(
                    pending  = all.count { it.status == "pending" },
                    approved = all.count { it.status == "approved" },
                    rejected = all.count { it.status == "rejected" },
                )
                // Filtre
                _pendingPosts.value = if (filter == "all") all else all.filter { it.status == filter }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _pendingLoading.value = false
            }
        }
    }

    // Onayla — status → approved, adminNote güncelle
    fun approvePost(postId: String, note: String = "") {
        viewModelScope.launch {
            try {
                firestore.collection("pendingPosts").document(postId).update(
                    mapOf(
                        "status"    to "approved",
                        "adminNote" to note,
                        "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                    )
                ).await()
                // Local güncelle
                _pendingPosts.value = _pendingPosts.value.map {
                    if (it.id == postId) it.copy(status = "approved", adminNote = note) else it
                }
                recalcStats()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Reddet — status → rejected, adminNote güncelle
    fun rejectPost(postId: String, note: String = "") {
        viewModelScope.launch {
            try {
                firestore.collection("pendingPosts").document(postId).update(
                    mapOf(
                        "status"    to "rejected",
                        "adminNote" to note,
                        "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                    )
                ).await()
                _pendingPosts.value = _pendingPosts.value.map {
                    if (it.id == postId) it.copy(status = "rejected", adminNote = note) else it
                }
                recalcStats()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Status'u herhangi bir değere güncelle
    fun updatePostStatus(postId: String, status: String, note: String = "") {
        viewModelScope.launch {
            try {
                firestore.collection("pendingPosts").document(postId).update(
                    mapOf(
                        "status"    to status,
                        "adminNote" to note,
                        "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                    )
                ).await()
                _pendingPosts.value = _pendingPosts.value.map {
                    if (it.id == postId) it.copy(status = status, adminNote = note) else it
                }
                recalcStats()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun recalcStats() {
        val all = _pendingPosts.value
        _pendingStats.value = PendingStats(
            pending  = all.count { it.status == "pending" },
            approved = all.count { it.status == "approved" },
            rejected = all.count { it.status == "rejected" },
        )
    }

}
