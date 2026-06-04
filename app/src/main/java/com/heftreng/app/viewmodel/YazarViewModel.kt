package com.heftreng.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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
    val uid get() = auth.currentUser?.uid ?: ""

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
        if (uid.isEmpty()) return
        viewModelScope.launch {
            _loading.value = true
            try {
                val snap = firestore.collection("pendingPosts")
                    .whereEqualTo("authorId", uid)
                    .orderBy("createdAt", Query.Direction.DESCENDING)
                    .limit(50)
                    .get().await()
                
                _myPosts.value = snap.documents.mapNotNull { it.toPendingPost() }
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
        if (uid.isEmpty()) return
        viewModelScope.launch {
            try {
                // GÜVENLİK KALKANI: Önce dökümanın sahibini doğrula
                val docRef = firestore.collection("pendingPosts").document(postId)
                val docSnap = docRef.get().await()
                if (docSnap.getString("authorId") != uid) return@launch // Yetkisiz silme engellendi

                docRef.delete().await()
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
                // OPTİMİZASYON 1: Sorguyu Firestore tarafında filtrele (Maliyet ve veri tasarrufu)
                var baseQuery: Query = firestore.collection("pendingPosts")
                if (filter != "all") {
                    baseQuery = baseQuery.whereEqualTo("status", filter)
                }
                
                val snap = baseQuery.orderBy("createdAt", Query.Direction.DESCENDING).limit(100).get().await()
                _pendingPosts.value = snap.documents.mapNotNull { it.toPendingPost() }

                // OPTİMİZASYON 2: Doğru istatistikleri döküman indirmeden, yüksek performanslı sayım sorgusuyla çek
                updateRealtimeStats()

            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _pendingLoading.value = false
            }
        }
    }

    // Gerçek toplam sayıları döküman indirmeden çeken optimize metot
    private suspend fun updateRealtimeStats() = coroutineScope {
        try {
            val coll = firestore.collection("pendingPosts")
            
            // 3 sayım sorgusunu paralel fırlatıyoruz
            val pendingCountJob  = async { coll.whereEqualTo("status", "pending").count().get(com.google.firebase.firestore.AggregateSource.SERVER).await() }
            val approvedCountJob = async { coll.whereEqualTo("status", "approved").count().get(com.google.firebase.firestore.AggregateSource.SERVER).await() }
            val rejectedCountJob = async { coll.whereEqualTo("status", "rejected").count().get(com.google.firebase.firestore.AggregateSource.SERVER).await() }

            _pendingStats.value = PendingStats(
                pending  = pendingCountJob.await().count.toInt(),
                approved = approvedCountJob.await().count.toInt(),
                rejected = rejectedCountJob.await().count.toInt()
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Onayla
    fun approvePost(postId: String, note: String = "") {
        updatePostStatusInternal(postId, "approved", note)
    }

    // Reddet
    fun rejectPost(postId: String, note: String = "") {
        updatePostStatusInternal(postId, "rejected", note)
    }

    // Genel status güncelleme köprüsü
    fun updatePostStatus(postId: String, status: String, note: String = "") {
        updatePostStatusInternal(postId, status, note)
    }

    // Ortak yönetim fonksiyonu (Kod tekrarını engellemek için)
    private fun updatePostStatusInternal(postId: String, status: String, note: String) {
        viewModelScope.launch {
            try {
                firestore.collection("pendingPosts").document(postId).update(
                    mapOf(
                        "status"    to status,
                        "adminNote" to note,
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                ).await()
                
                // Lokal listeyi güncelle
                _pendingPosts.value = _pendingPosts.value.map {
                    if (it.id == postId) it.copy(status = status, adminNote = note) else it
                }
                
                // İstatistikleri sunucudan güvenli güncelle
                updateRealtimeStats()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // ════════════════════════════════════════════════════════════
    // PRIVATE EXTENSION HELPERS
    // ════════════════════════════════════════════════════════════

    // Dönüştürme işlemini tek bir merkezde topladık
    private fun DocumentSnapshot.toPendingPost(): PendingPost? {
        val d = data ?: return null
        return PendingPost(
            id             = id,
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
}
