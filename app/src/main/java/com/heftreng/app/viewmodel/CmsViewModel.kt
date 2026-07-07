package com.heftreng.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.heftreng.app.data.model.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@HiltViewModel
class CmsViewModel @Inject constructor(
    private val auth     : FirebaseAuth,
    private val firestore: FirebaseFirestore,
) : ViewModel() {

    // ── Yetki ─────────────────────────────────────────────────────────────────
    // FAZ -1: Eski sabit e-posta kontrolü kaldırıldı, yerine Firestore
    // admins/{uid} rol/izin sistemi kullanılıyor (AdminViewModel.perms ile
    // birebir aynı mantık — Firestore güvenlik kuralındaki
    // "isEditor() || hasPermission('edit')" ile hizalı).
    //
    // null = izinler henüz yüklenmedi (loading), StaffPermissions() = yüklendi ama yetkisiz.
    private val _perms = MutableStateFlow<StaffPermissions?>(null)
    val perms = _perms.asStateFlow()

    // CMS ekranı özelinde "edit" yetkisi var mı — null iken (yükleniyorken) false döner
    val isAdmin: Boolean get() = _perms.value?.can("edit") == true

    init { loadPerms() }

    fun loadPerms() {
        viewModelScope.launch {
            _perms.value = null
            val user = auth.currentUser ?: run { _perms.value = StaffPermissions(); return@launch }
            try {
                val doc = firestore.collection("admins").document(user.uid).get().await()
                if (doc.exists()) {
                    val role  = doc.getString("role") ?: "none"
                    val title = doc.getString("title") ?: role.replaceFirstChar { it.uppercase() }
                    @Suppress("UNCHECKED_CAST")
                    val legacy = doc.get("permissions") as? List<String>
                    val permSet = if (!legacy.isNullOrEmpty()) legacy.toSet() else roleToPermissions(role)
                    _perms.value = StaffPermissions(uid = user.uid, title = title, permissions = permSet)
                } else {
                    _perms.value = StaffPermissions()
                }
            } catch (e: Exception) {
                android.util.Log.w("CmsVM", "loadPerms: ${e.message}")
                _perms.value = StaffPermissions()
            }
        }
    }

    // ── Sayfalar ──────────────────────────────────────────────────────────────
    private val _pages   = MutableStateFlow<List<CmsPage>>(emptyList())
    val pages = _pages.asStateFlow()

    // ── Bannerlar ─────────────────────────────────────────────────────────────
    private val _banners = MutableStateFlow<List<CmsBanner>>(emptyList())
    val banners = _banners.asStateFlow()

    // ── Duyurular ─────────────────────────────────────────────────────────────
    private val _announcements = MutableStateFlow<List<CmsAnnouncement>>(emptyList())
    val announcements = _announcements.asStateFlow()

    // ── Kategoriler ───────────────────────────────────────────────────────────
    private val _categories = MutableStateFlow<List<CmsCategory>>(emptyList())
    val categories = _categories.asStateFlow()

    // ── UI durumu ─────────────────────────────────────────────────────────────
    private val _loading = MutableStateFlow(false)
    val loading = _loading.asStateFlow()

    private val _result  = MutableStateFlow("")
    val result = _result.asStateFlow()

    // ── Sayfalar CRUD ─────────────────────────────────────────────────────────

    fun loadPages() {
        if (!isAdmin) return
        viewModelScope.launch {
            _loading.value = true
            try {
                val snap = firestore.collection("cms_pages")
                    .orderBy("order")
                    .get().await()
                _pages.value = snap.documents.mapNotNull { doc ->
                    val d = doc.data ?: return@mapNotNull null
                    CmsPage(
                        id        = doc.id,
                        slug      = d["slug"]      as? String  ?: "",
                        title     = d["title"]     as? String  ?: "",
                        body      = d["body"]      as? String  ?: "",
                        lang      = d["lang"]      as? String  ?: "tr",
                        published = d["published"] as? Boolean ?: true,
                        order     = (d["order"]    as? Long)?.toInt() ?: 0,
                        updatedBy = d["updatedBy"] as? String  ?: "",
                    )
                }
            } catch (e: Exception) { e.printStackTrace() }
            finally { _loading.value = false }
        }
    }

    fun savePage(page: CmsPage) {
        if (!isAdmin) return
        viewModelScope.launch {
            try {
                _result.value = "Kaydediliyor…"
                val data = hashMapOf(
                    "slug"      to page.slug,
                    "title"     to page.title,
                    "body"      to page.body,
                    "lang"      to page.lang,
                    "published" to page.published,
                    "order"     to page.order,
                    "updatedAt" to FieldValue.serverTimestamp(),
                    "updatedBy" to (auth.currentUser?.email ?: "admin"),
                )
                if (page.id.isBlank()) {
                    val ref = firestore.collection("cms_pages").add(data).await()
                    _pages.value = _pages.value + page.copy(id = ref.id)
                } else {
                    firestore.collection("cms_pages").document(page.id).set(data).await()
                    _pages.value = _pages.value.map { if (it.id == page.id) page else it }
                }
                _result.value = "✓ Sayfa kaydedildi"
            } catch (e: Exception) {
                _result.value = "✗ Hata: ${e.message}"
            }
        }
    }

    fun deletePage(pageId: String) {
        if (!isAdmin) return
        viewModelScope.launch {
            try {
                firestore.collection("cms_pages").document(pageId).delete().await()
                _pages.value = _pages.value.filter { it.id != pageId }
                _result.value = "✓ Sayfa silindi"
            } catch (e: Exception) {
                _result.value = "✗ Hata: ${e.message}"
            }
        }
    }

    fun togglePagePublished(pageId: String, published: Boolean) {
        if (!isAdmin) return
        viewModelScope.launch {
            try {
                firestore.collection("cms_pages").document(pageId)
                    .update("published", published).await()
                _pages.value = _pages.value.map {
                    if (it.id == pageId) it.copy(published = published) else it
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    // ── Banner CRUD ───────────────────────────────────────────────────────────

    fun loadBanners() {
        if (!isAdmin) return
        viewModelScope.launch {
            _loading.value = true
            try {
                val snap = firestore.collection("cms_banners")
                    .orderBy("order").get().await()
                _banners.value = snap.documents.mapNotNull { doc ->
                    val d = doc.data ?: return@mapNotNull null
                    CmsBanner(
                        id       = doc.id,
                        title    = d["title"]    as? String  ?: "",
                        subtitle = d["subtitle"] as? String  ?: "",
                        imageUrl = d["imageUrl"] as? String  ?: "",
                        linkUrl  = d["linkUrl"]  as? String  ?: "",
                        active   = d["active"]   as? Boolean ?: true,
                        order    = (d["order"]   as? Long)?.toInt() ?: 0,
                    )
                }
            } catch (e: Exception) { e.printStackTrace() }
            finally { _loading.value = false }
        }
    }

    fun saveBanner(banner: CmsBanner) {
        if (!isAdmin) return
        viewModelScope.launch {
            try {
                _result.value = "Kaydediliyor…"
                val data = hashMapOf(
                    "title"     to banner.title,
                    "subtitle"  to banner.subtitle,
                    "imageUrl"  to banner.imageUrl,
                    "linkUrl"   to banner.linkUrl,
                    "active"    to banner.active,
                    "order"     to banner.order,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
                if (banner.id.isBlank()) {
                    val ref = firestore.collection("cms_banners").add(data).await()
                    _banners.value = _banners.value + banner.copy(id = ref.id)
                } else {
                    firestore.collection("cms_banners").document(banner.id).set(data).await()
                    _banners.value = _banners.value.map { if (it.id == banner.id) banner else it }
                }
                _result.value = "✓ Banner kaydedildi"
            } catch (e: Exception) {
                _result.value = "✗ Hata: ${e.message}"
            }
        }
    }

    fun deleteBanner(bannerId: String) {
        if (!isAdmin) return
        viewModelScope.launch {
            try {
                firestore.collection("cms_banners").document(bannerId).delete().await()
                _banners.value = _banners.value.filter { it.id != bannerId }
                _result.value = "✓ Banner silindi"
            } catch (e: Exception) {
                _result.value = "✗ Hata: ${e.message}"
            }
        }
    }

    fun toggleBanner(bannerId: String, active: Boolean) {
        if (!isAdmin) return
        viewModelScope.launch {
            try {
                firestore.collection("cms_banners").document(bannerId)
                    .update("active", active).await()
                _banners.value = _banners.value.map {
                    if (it.id == bannerId) it.copy(active = active) else it
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    // ── Duyuru CRUD ───────────────────────────────────────────────────────────

    fun loadAnnouncements() {
        if (!isAdmin) return
        viewModelScope.launch {
            _loading.value = true
            try {
                val snap = firestore.collection("cms_announcements")
                    .orderBy("ts", com.google.firebase.firestore.Query.Direction.DESCENDING)
                    .limit(30).get().await()
                _announcements.value = snap.documents.mapNotNull { doc ->
                    val d = doc.data ?: return@mapNotNull null
                    CmsAnnouncement(
                        id     = doc.id,
                        title  = d["title"]  as? String  ?: "",
                        body   = d["body"]   as? String  ?: "",
                        type   = d["type"]   as? String  ?: "info",
                        active = d["active"] as? Boolean ?: true,
                    )
                }
            } catch (e: Exception) { e.printStackTrace() }
            finally { _loading.value = false }
        }
    }

    fun saveAnnouncement(ann: CmsAnnouncement) {
        if (!isAdmin) return
        viewModelScope.launch {
            try {
                _result.value = "Kaydediliyor…"
                val data = hashMapOf(
                    "title"  to ann.title,
                    "body"   to ann.body,
                    "type"   to ann.type,
                    "active" to ann.active,
                    "ts"     to FieldValue.serverTimestamp(),
                )
                if (ann.id.isBlank()) {
                    val ref = firestore.collection("cms_announcements").add(data).await()
                    _announcements.value = listOf(ann.copy(id = ref.id)) + _announcements.value
                } else {
                    firestore.collection("cms_announcements").document(ann.id).set(data).await()
                    _announcements.value = _announcements.value.map { if (it.id == ann.id) ann else it }
                }
                _result.value = "✓ Duyuru kaydedildi"
            } catch (e: Exception) {
                _result.value = "✗ Hata: ${e.message}"
            }
        }
    }

    fun deleteAnnouncement(annId: String) {
        if (!isAdmin) return
        viewModelScope.launch {
            try {
                firestore.collection("cms_announcements").document(annId).delete().await()
                _announcements.value = _announcements.value.filter { it.id != annId }
                _result.value = "✓ Duyuru silindi"
            } catch (e: Exception) {
                _result.value = "✗ Hata: ${e.message}"
            }
        }
    }

    fun toggleAnnouncement(annId: String, active: Boolean) {
        if (!isAdmin) return
        viewModelScope.launch {
            try {
                firestore.collection("cms_announcements").document(annId)
                    .update("active", active).await()
                _announcements.value = _announcements.value.map {
                    if (it.id == annId) it.copy(active = active) else it
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    // ── Kategori CRUD ─────────────────────────────────────────────────────────

    fun loadCategories() {
        if (!isAdmin) return
        viewModelScope.launch {
            _loading.value = true
            try {
                val snap = firestore.collection("cms_categories")
                    .orderBy("order").get().await()
                _categories.value = snap.documents.mapNotNull { doc ->
                    val d = doc.data ?: return@mapNotNull null
                    CmsCategory(
                        id     = doc.id,
                        name   = d["name"]   as? String ?: "",
                        nameKu = d["nameKu"] as? String ?: "",
                        slug   = d["slug"]   as? String ?: "",
                        order  = (d["order"] as? Long)?.toInt() ?: 0,
                    )
                }
            } catch (e: Exception) { e.printStackTrace() }
            finally { _loading.value = false }
        }
    }

    fun saveCategory(cat: CmsCategory) {
        if (!isAdmin) return
        viewModelScope.launch {
            try {
                _result.value = "Kaydediliyor…"
                val data = hashMapOf(
                    "name"   to cat.name,
                    "nameKu" to cat.nameKu,
                    "slug"   to cat.slug,
                    "order"  to cat.order,
                )
                if (cat.id.isBlank()) {
                    val ref = firestore.collection("cms_categories").add(data).await()
                    _categories.value = _categories.value + cat.copy(id = ref.id)
                } else {
                    firestore.collection("cms_categories").document(cat.id).set(data).await()
                    _categories.value = _categories.value.map { if (it.id == cat.id) cat else it }
                }
                _result.value = "✓ Kategori kaydedildi"
            } catch (e: Exception) {
                _result.value = "✗ Hata: ${e.message}"
            }
        }
    }

    fun deleteCategory(catId: String) {
        if (!isAdmin) return
        viewModelScope.launch {
            try {
                firestore.collection("cms_categories").document(catId).delete().await()
                _categories.value = _categories.value.filter { it.id != catId }
                _result.value = "✓ Kategori silindi"
            } catch (e: Exception) {
                _result.value = "✗ Hata: ${e.message}"
            }
        }
    }

    fun clearResult() { _result.value = "" }
}
