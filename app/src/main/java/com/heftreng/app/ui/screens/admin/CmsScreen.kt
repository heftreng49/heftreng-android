package com.heftreng.app.ui.screens.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.text.KeyboardOptions
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.heftreng.app.data.model.*
import com.heftreng.app.ui.theme.*
import com.heftreng.app.data.model.AppConfig
import com.heftreng.app.viewmodel.AppConfigViewModel
import com.heftreng.app.viewmodel.CmsViewModel
import com.heftreng.app.data.model.Author
import com.heftreng.app.data.model.LibraryBook
import com.heftreng.app.data.model.BookQuote

// ─────────────────────────────────────────────────────────────────────────────
// CMS Ana Ekranı
// Sekmeler: Sayfalar | Bannerlar | Duyurular | Kategoriler
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CmsScreen(
    navController: NavController,
    vm       : CmsViewModel       = hiltViewModel(),
    configVm : AppConfigViewModel = hiltViewModel(),
) {
    val pages         by vm.pages.collectAsState()
    val banners       by vm.banners.collectAsState()
    val announcements by vm.announcements.collectAsState()
    val categories    by vm.categories.collectAsState()
    val loading       by vm.loading.collectAsState()
    val result        by vm.result.collectAsState()
    // FAZ -1 DÜZELTME: `vm.isAdmin` bir Compose state'i DEĞİL (val get()),
    // yani perms Firestore'dan asenkron dolduğunda recompose tetiklemiyordu.
    // Ayrıca "henüz yükleniyor" (null) durumu "yetkisiz" ile aynı
    // muameleyi görüyordu — bu yüzden gerçek admin hesabı bile Firestore
    // cevabı gelmeden (veya gecikirse hiç) "Erişim Yok" ekranına takılıyordu.
    // Çözüm: perms'i doğrudan collectAsState ile izle, null/false/true
    // durumlarını ayrı ele al.
    // FAZ -1 devamı: CMS artık gerçekten sadece CMS içeriğiyle (Sayfalar,
    // Bannerlar, Duyurular, Kategoriler, Özellikler) ilgili. "Yazılar",
    // "Kütüphane", "Admin", "Kurdî Admin" sekmeleri CMS'nin altına gömülü
    // değil — Admin Paneli (AdminScreen) kendi başına, "staff"/"library"/
    // "kurdi"/"pending" izinlerine göre kendi sekmelerini gösteriyor.
    // Kişi CMS'ye ("edit" izni) girmeden de, CMS'ye hiç bakmadan da
    // Admin Paneli'ne erişebilir/erişemez — ikisi birbirinden bağımsız.
    val cmsPerms      by vm.perms.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }
    val appConfig by configVm.config.collectAsState()
    val tabs = listOf("Sayfalar", "Bannerlar", "Duyurular", "Kategoriler", "Özellikler")

    LaunchedEffect(Unit) {
        vm.loadPages()
        vm.loadBanners()
        vm.loadAnnouncements()
        vm.loadCategories()
        configVm.load()
    }

    // Erişim kontrolü
    // cmsPerms == null  → henüz yükleniyor, bekleme göstergesi
    // cmsPerms.can("edit") == false → gerçekten yetkisiz, "Erişim Yok"
    // cmsPerms.can("edit") == true  → ekran açılır
    if (cmsPerms == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Amber)
        }
        return
    }
    if (cmsPerms?.can("edit") != true) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Block, null, tint = Error, modifier = Modifier.size(48.dp))
                Spacer(Modifier.height(12.dp))
                Text("Erişim Yok", color = Error, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        }
        return
    }

    // Sonuç snackbar
    val snackState = remember { SnackbarHostState() }
    LaunchedEffect(result) {
        if (result.isNotBlank()) {
            snackState.showSnackbar(result)
            vm.clearResult()
        }
    }

    Scaffold(
        modifier = Modifier.imePadding(),
        containerColor = Background,
        snackbarHost = {
            SnackbarHost(snackState) { data ->
                Snackbar(
                    snackbarData  = data,
                    containerColor = if (data.visuals.message.startsWith("✓")) Success else Error,
                    contentColor  = Color.Black,
                    shape         = RoundedCornerShape(12.dp),
                )
            }
        },
        topBar = {
            TopAppBar(
                title = {
                    Text("CMS Yönetimi", fontWeight = FontWeight.Bold, color = OnBackground)
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = OnBackground)
                    }
                },

                colors = TopAppBarDefaults.topAppBarColors(containerColor = Background),
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {

            // ── Tab satırı ─────────────────────────────────────────────────────
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                containerColor   = Background,
                contentColor     = Amber,
                edgePadding      = 0.dp,
                indicator        = { tabPositions ->
                    Box(
                        Modifier
                            .tabIndicatorOffset(tabPositions[selectedTab])
                            .height(2.dp)
                            .background(Amber)
                    )
                },
            ) {
                tabs.forEachIndexed { i, title ->
                    Tab(
                        selected               = selectedTab == i,
                        onClick                = { selectedTab = i },
                        text                   = { Text(title, fontSize = 12.sp) },
                        selectedContentColor   = Amber,
                        unselectedContentColor = Muted,
                    )
                }
            }

            if (loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Amber)
                }
                return@Column
            }

            when (selectedTab) {
                0 -> PagesTab(pages, vm)
                1 -> BannersTab(banners, vm)
                2 -> AnnouncementsTab(announcements, vm)
                3 -> CategoriesTab(categories, vm)
                4 -> FeaturesTab(appConfig, configVm)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SEKMELER
// ─────────────────────────────────────────────────────────────────────────────

// ── 1. Sayfalar ───────────────────────────────────────────────────────────────
@Composable
private fun PagesTab(pages: List<CmsPage>, vm: CmsViewModel) {
    var editing by remember { mutableStateOf<CmsPage?>(null) }

    if (editing != null) {
        PageEditor(
            initial = editing!!,
            onSave  = { vm.savePage(it); editing = null },
            onCancel = { editing = null },
        )
        return
    }

    LazyColumn(
        modifier       = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Button(
                onClick  = { editing = CmsPage() },
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(10.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = Color.Black),
            ) {
                Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("Yeni Sayfa", fontWeight = FontWeight.Bold)
            }
        }

        items(pages, key = { it.id }) { page ->
            CmsCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(page.title.ifBlank { "(başlıksız)" }, color = OnBackground, fontWeight = FontWeight.SemiBold)
                        Text("/${page.slug}  •  ${page.lang.uppercase()}", color = Muted, fontSize = 12.sp)
                    }
                    // Yayın toggle
                    Switch(
                        checked        = page.published,
                        onCheckedChange = { vm.togglePagePublished(page.id, it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor  = Success,
                            checkedTrackColor  = Success.copy(alpha = 0.3f),
                            uncheckedThumbColor = Muted,
                            uncheckedTrackColor = Muted.copy(alpha = 0.2f),
                        ),
                    )
                    Spacer(Modifier.width(4.dp))
                    IconButton(onClick = { editing = page }) {
                        Icon(Icons.Default.Edit, null, tint = Amber, modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = { vm.deletePage(page.id) }) {
                        Icon(Icons.Default.Delete, null, tint = Error, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun PageEditor(
    initial : CmsPage,
    onSave  : (CmsPage) -> Unit,
    onCancel: () -> Unit,
) {
    var slug      by remember { mutableStateOf(initial.slug) }
    var title     by remember { mutableStateOf(initial.title) }
    var body      by remember { mutableStateOf(initial.body) }
    var lang      by remember { mutableStateOf(initial.lang) }
    var published by remember { mutableStateOf(initial.published) }
    var order     by remember { mutableStateOf(initial.order.toString()) }

    LazyColumn(
        modifier       = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text(
                if (initial.id.isBlank()) "Yeni Sayfa" else "Sayfa Düzenle",
                color = Amber, fontWeight = FontWeight.Bold, fontSize = 15.sp,
            )
            Spacer(Modifier.height(4.dp))
            cmsField(slug, { slug = it }, "Slug (ör. about, rules)")
            Spacer(Modifier.height(6.dp))
            cmsField(title, { title = it }, "Başlık *")
            Spacer(Modifier.height(6.dp))
            cmsField(body, { body = it }, "İçerik (Markdown destekli)", minLines = 8)
            Spacer(Modifier.height(6.dp))
            // Dil seçimi
            Text("Dil", color = Muted, fontSize = 12.sp)
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("tr" to "Türkçe", "ku" to "Kürtçe", "both" to "İkisi").forEach { (k, label) ->
                    FilterChip(
                        selected = lang == k,
                        onClick  = { lang = k },
                        label    = { Text(label, fontSize = 11.sp) },
                        colors   = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Amber.copy(alpha = 0.2f),
                            selectedLabelColor     = Amber,
                        ),
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            cmsField(order, { order = it.filter(Char::isDigit) }, "Sıra (sayı)", keyboardType = KeyboardType.Number)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Yayında", color = OnBackground, modifier = Modifier.weight(1f))
                Switch(
                    checked = published,
                    onCheckedChange = { published = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor  = Success,
                        checkedTrackColor  = Success.copy(alpha = 0.3f),
                    ),
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                ) { Text("İptal") }
                Button(
                    onClick = {
                        onSave(initial.copy(
                            slug      = slug.trim(),
                            title     = title.trim(),
                            body      = body,
                            lang      = lang,
                            published = published,
                            order     = order.toIntOrNull() ?: 0,
                        ))
                    },
                    enabled  = title.isNotBlank(),
                    modifier = Modifier.weight(1f),
                    shape    = RoundedCornerShape(10.dp),
                    colors   = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = Color.Black),
                ) { Text("Kaydet", fontWeight = FontWeight.Bold) }
            }
        }
    }
}

// ── 2. Bannerlar ──────────────────────────────────────────────────────────────
@Composable
private fun BannersTab(banners: List<CmsBanner>, vm: CmsViewModel) {
    var editing by remember { mutableStateOf<CmsBanner?>(null) }

    if (editing != null) {
        BannerEditor(
            initial  = editing!!,
            onSave   = { vm.saveBanner(it); editing = null },
            onCancel = { editing = null },
        )
        return
    }

    LazyColumn(
        modifier       = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Button(
                onClick  = { editing = CmsBanner() },
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(10.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = Color.Black),
            ) {
                Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("Yeni Banner", fontWeight = FontWeight.Bold)
            }
        }

        items(banners, key = { it.id }) { banner ->
            CmsCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(banner.title.ifBlank { "(başlıksız)" }, color = OnBackground, fontWeight = FontWeight.SemiBold)
                        if (banner.subtitle.isNotBlank())
                            Text(banner.subtitle, color = Muted, fontSize = 12.sp, maxLines = 1)
                        if (banner.linkUrl.isNotBlank())
                            Text(banner.linkUrl, color = Primary, fontSize = 11.sp, maxLines = 1)
                    }
                    Switch(
                        checked         = banner.active,
                        onCheckedChange = { vm.toggleBanner(banner.id, it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor  = Success,
                            checkedTrackColor  = Success.copy(alpha = 0.3f),
                            uncheckedThumbColor = Muted,
                            uncheckedTrackColor = Muted.copy(alpha = 0.2f),
                        ),
                    )
                    Spacer(Modifier.width(4.dp))
                    IconButton(onClick = { editing = banner }) {
                        Icon(Icons.Default.Edit, null, tint = Amber, modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = { vm.deleteBanner(banner.id) }) {
                        Icon(Icons.Default.Delete, null, tint = Error, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun BannerEditor(
    initial : CmsBanner,
    onSave  : (CmsBanner) -> Unit,
    onCancel: () -> Unit,
) {
    var title    by remember { mutableStateOf(initial.title) }
    var subtitle by remember { mutableStateOf(initial.subtitle) }
    var imageUrl by remember { mutableStateOf(initial.imageUrl) }
    var linkUrl  by remember { mutableStateOf(initial.linkUrl) }
    var active   by remember { mutableStateOf(initial.active) }
    var order    by remember { mutableStateOf(initial.order.toString()) }

    LazyColumn(
        modifier       = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text(
                if (initial.id.isBlank()) "Yeni Banner" else "Banner Düzenle",
                color = Amber, fontWeight = FontWeight.Bold, fontSize = 15.sp,
            )
            Spacer(Modifier.height(4.dp))
            cmsField(title, { title = it }, "Başlık *")
            Spacer(Modifier.height(6.dp))
            cmsField(subtitle, { subtitle = it }, "Alt başlık")
            Spacer(Modifier.height(6.dp))
            cmsField(imageUrl, { imageUrl = it }, "Görsel URL")
            Spacer(Modifier.height(6.dp))
            cmsField(linkUrl, { linkUrl = it }, "Link URL")
            Spacer(Modifier.height(6.dp))
            cmsField(order, { order = it.filter(Char::isDigit) }, "Sıra", keyboardType = KeyboardType.Number)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Aktif", color = OnBackground, modifier = Modifier.weight(1f))
                Switch(
                    checked = active, onCheckedChange = { active = it },
                    colors = SwitchDefaults.colors(checkedThumbColor = Success, checkedTrackColor = Success.copy(alpha = 0.3f)),
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp)) {
                    Text("İptal")
                }
                Button(
                    onClick = {
                        onSave(initial.copy(
                            title    = title.trim(),
                            subtitle = subtitle.trim(),
                            imageUrl = imageUrl.trim(),
                            linkUrl  = linkUrl.trim(),
                            active   = active,
                            order    = order.toIntOrNull() ?: 0,
                        ))
                    },
                    enabled  = title.isNotBlank(),
                    modifier = Modifier.weight(1f),
                    shape    = RoundedCornerShape(10.dp),
                    colors   = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = Color.Black),
                ) { Text("Kaydet", fontWeight = FontWeight.Bold) }
            }
        }
    }
}

// ── 3. Duyurular ──────────────────────────────────────────────────────────────
@Composable
private fun AnnouncementsTab(announcements: List<CmsAnnouncement>, vm: CmsViewModel) {
    var editing by remember { mutableStateOf<CmsAnnouncement?>(null) }

    if (editing != null) {
        AnnouncementEditor(
            initial  = editing!!,
            onSave   = { vm.saveAnnouncement(it); editing = null },
            onCancel = { editing = null },
        )
        return
    }

    LazyColumn(
        modifier       = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Button(
                onClick  = { editing = CmsAnnouncement() },
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(10.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = Color.Black),
            ) {
                Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("Yeni Duyuru", fontWeight = FontWeight.Bold)
            }
        }

        items(announcements, key = { it.id }) { ann ->
            val typeColor = when (ann.type) {
                "warning" -> Color(0xFFFBBF24)
                "success" -> Success
                else      -> Primary
            }
            CmsCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(typeColor, RoundedCornerShape(50)),
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(ann.title, color = OnBackground, fontWeight = FontWeight.SemiBold)
                        if (ann.body.isNotBlank())
                            Text(ann.body, color = Muted, fontSize = 12.sp, maxLines = 2)
                    }
                    Switch(
                        checked         = ann.active,
                        onCheckedChange = { vm.toggleAnnouncement(ann.id, it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor  = Success,
                            checkedTrackColor  = Success.copy(alpha = 0.3f),
                            uncheckedThumbColor = Muted,
                            uncheckedTrackColor = Muted.copy(alpha = 0.2f),
                        ),
                    )
                    IconButton(onClick = { editing = ann }) {
                        Icon(Icons.Default.Edit, null, tint = Amber, modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = { vm.deleteAnnouncement(ann.id) }) {
                        Icon(Icons.Default.Delete, null, tint = Error, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun AnnouncementEditor(
    initial : CmsAnnouncement,
    onSave  : (CmsAnnouncement) -> Unit,
    onCancel: () -> Unit,
) {
    var title   by remember { mutableStateOf(initial.title) }
    var body    by remember { mutableStateOf(initial.body) }
    var type    by remember { mutableStateOf(initial.type) }
    var active  by remember { mutableStateOf(initial.active) }
    var linkUrl by remember { mutableStateOf(initial.linkUrl) }

    LazyColumn(
        modifier       = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text(
                if (initial.id.isBlank()) "Yeni Duyuru" else "Duyuru Düzenle",
                color = Amber, fontWeight = FontWeight.Bold, fontSize = 15.sp,
            )
            Spacer(Modifier.height(4.dp))
            cmsField(title, { title = it }, "Başlık *")
            Spacer(Modifier.height(6.dp))
            cmsField(body, { body = it }, "İçerik", minLines = 4)
            Spacer(Modifier.height(6.dp))
            cmsField(linkUrl, { linkUrl = it }, "Bağlantı URL'si (opsiyonel)")
            Spacer(Modifier.height(6.dp))
            Text("Tür", color = Muted, fontSize = 12.sp)
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("info" to "Bilgi", "warning" to "Uyarı", "success" to "Başarı").forEach { (k, label) ->
                    FilterChip(
                        selected = type == k,
                        onClick  = { type = k },
                        label    = { Text(label, fontSize = 11.sp) },
                        colors   = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Amber.copy(alpha = 0.2f),
                            selectedLabelColor     = Amber,
                        ),
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Aktif", color = OnBackground, modifier = Modifier.weight(1f))
                Switch(
                    checked = active, onCheckedChange = { active = it },
                    colors = SwitchDefaults.colors(checkedThumbColor = Success, checkedTrackColor = Success.copy(alpha = 0.3f)),
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp)) {
                    Text("İptal")
                }
                Button(
                    onClick = {
                        onSave(initial.copy(title = title.trim(), body = body.trim(), type = type, active = active, linkUrl = linkUrl.trim()))
                    },
                    enabled  = title.isNotBlank(),
                    modifier = Modifier.weight(1f),
                    shape    = RoundedCornerShape(10.dp),
                    colors   = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = Color.Black),
                ) { Text("Kaydet", fontWeight = FontWeight.Bold) }
            }
        }
    }
}

// ── 4. Kategoriler ────────────────────────────────────────────────────────────
@Composable
private fun CategoriesTab(categories: List<CmsCategory>, vm: CmsViewModel) {
    var editing by remember { mutableStateOf<CmsCategory?>(null) }

    if (editing != null) {
        CategoryEditor(
            initial  = editing!!,
            onSave   = { vm.saveCategory(it); editing = null },
            onCancel = { editing = null },
        )
        return
    }

    LazyColumn(
        modifier       = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Button(
                onClick  = { editing = CmsCategory() },
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(10.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = Color.Black),
            ) {
                Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("Yeni Kategori", fontWeight = FontWeight.Bold)
            }
        }

        items(categories, key = { it.id }) { cat ->
            CmsCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(cat.name.ifBlank { "(isimsiz)" }, color = OnBackground, fontWeight = FontWeight.SemiBold)
                        if (cat.nameKu.isNotBlank())
                            Text("Kürtçe: ${cat.nameKu}", color = Muted, fontSize = 12.sp)
                        Text("/${cat.slug}", color = Primary, fontSize = 11.sp)
                    }
                    IconButton(onClick = { editing = cat }) {
                        Icon(Icons.Default.Edit, null, tint = Amber, modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = { vm.deleteCategory(cat.id) }) {
                        Icon(Icons.Default.Delete, null, tint = Error, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryEditor(
    initial : CmsCategory,
    onSave  : (CmsCategory) -> Unit,
    onCancel: () -> Unit,
) {
    var name   by remember { mutableStateOf(initial.name) }
    var nameKu by remember { mutableStateOf(initial.nameKu) }
    var slug   by remember { mutableStateOf(initial.slug) }
    var order  by remember { mutableStateOf(initial.order.toString()) }

    LazyColumn(
        modifier       = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text(
                if (initial.id.isBlank()) "Yeni Kategori" else "Kategori Düzenle",
                color = Amber, fontWeight = FontWeight.Bold, fontSize = 15.sp,
            )
            Spacer(Modifier.height(4.dp))
            cmsField(name, { name = it }, "İsim (Türkçe) *")
            Spacer(Modifier.height(6.dp))
            cmsField(nameKu, { nameKu = it }, "İsim (Kürtçe)")
            Spacer(Modifier.height(6.dp))
            cmsField(slug, { slug = it }, "Slug (ör. roman, siir)")
            Spacer(Modifier.height(6.dp))
            cmsField(order, { order = it.filter(Char::isDigit) }, "Sıra", keyboardType = KeyboardType.Number)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp)) {
                    Text("İptal")
                }
                Button(
                    onClick = {
                        onSave(initial.copy(
                            name   = name.trim(),
                            nameKu = nameKu.trim(),
                            slug   = slug.trim(),
                            order  = order.toIntOrNull() ?: 0,
                        ))
                    },
                    enabled  = name.isNotBlank(),
                    modifier = Modifier.weight(1f),
                    shape    = RoundedCornerShape(10.dp),
                    colors   = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = Color.Black),
                ) { Text("Kaydet", fontWeight = FontWeight.Bold) }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Yardımcı bileşenler
// ─────────────────────────────────────────────────────────────────────────────

// ── 6. Özellikler ─────────────────────────────────────────────────────────────
@Composable
private fun FeaturesTab(config: AppConfig, vm: AppConfigViewModel) {
    var current  by remember(config) { mutableStateOf(config) }
    var saveResult by remember { mutableStateOf("") }
    var saving   by remember { mutableStateOf(false) }

    LazyColumn(
        modifier       = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // ── Ekranlar ───────────────────────────────────────────────────────
        item {
            FeatureSection("Ekranlar") {
                FeatureToggle("Akış (Feed)",       current.feedEnabled)          { current = current.copy(feedEnabled = it) }
                FeatureToggle("Mesajlar",          current.messagesEnabled)      { current = current.copy(messagesEnabled = it) }
                FeatureToggle("Seriler",           current.serialsEnabled)       { current = current.copy(serialsEnabled = it) }
                FeatureToggle("Kitaplar",          current.booksEnabled)         { current = current.copy(booksEnabled = it) }
                FeatureToggle("Kurdî Fêrbibe",     current.kurdiEnabled)         { current = current.copy(kurdiEnabled = it) }
                FeatureToggle("Bildirimler",       current.notificationsEnabled) { current = current.copy(notificationsEnabled = it) }
                FeatureToggle("Arama",             current.searchEnabled)        { current = current.copy(searchEnabled = it) }
                FeatureToggle("Hikayeler",         current.storiesEnabled)       { current = current.copy(storiesEnabled = it) }
            }
        }

        // ── Feed Özellikleri ───────────────────────────────────────────────
        item {
            FeatureSection("Feed Özellikleri") {
                FeatureToggle("Resim göster",      current.feedShowImages)  { current = current.copy(feedShowImages = it) }
                FeatureToggle("Repost göster",     current.feedShowReposts) { current = current.copy(feedShowReposts = it) }
                FeatureToggle("Alıntı izni ver",   current.feedAllowQuotes) { current = current.copy(feedAllowQuotes = it) }
                Spacer(Modifier.height(6.dp))
                cmsField(
                    current.feedMaxTextLength.toString(),
                    { current = current.copy(feedMaxTextLength = it.filter(Char::isDigit).toIntOrNull() ?: 1000) },
                    "Maks. metin uzunluğu",
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                )
                Spacer(Modifier.height(4.dp))
                cmsField(current.feedTitle, { current = current.copy(feedTitle = it) }, "Feed başlığı (boşsa varsayılan)")
            }
        }

        // ── Mesaj Özellikleri ──────────────────────────────────────────────
        item {
            FeatureSection("Mesaj Özellikleri") {
                FeatureToggle("Resim gönderme",    current.messagesAllowImages) { current = current.copy(messagesAllowImages = it) }
                FeatureToggle("Sesli mesaj",       current.messagesAllowVoice)  { current = current.copy(messagesAllowVoice = it) }
                Spacer(Modifier.height(4.dp))
                cmsField(current.messagesTitle, { current = current.copy(messagesTitle = it) }, "Mesajlar başlığı")
            }
        }

        // ── Profil Özellikleri ─────────────────────────────────────────────
        item {
            FeatureSection("Profil Özellikleri") {
                FeatureToggle("XP göster",         current.profileShowXp)       { current = current.copy(profileShowXp = it) }
                FeatureToggle("Seri göster",       current.profileShowStreak)   { current = current.copy(profileShowStreak = it) }
                FeatureToggle("Rozetler",          current.profileShowBadges)   { current = current.copy(profileShowBadges = it) }
                FeatureToggle("Okuma listesi",     current.profileShowReadList) { current = current.copy(profileShowReadList = it) }
            }
        }

        // ── Kurdî Fêrbibe ─────────────────────────────────────────────────
        item {
            FeatureSection("Kurdî Fêrbibe") {
                FeatureToggle("Günün Kelimesi",    current.kurdiShowWordOfDay)  { current = current.copy(kurdiShowWordOfDay = it) }
                Spacer(Modifier.height(4.dp))
                cmsField(current.kurdiTitle, { current = current.copy(kurdiTitle = it) }, "Kurdî sayfası başlığı")
            }
        }

        // ── Bakım Modu ────────────────────────────────────────────────────
        item {
            FeatureSection("Bakım Modu", sectionColor = if (current.maintenanceMode) Color(0xFFEF4444) else Muted) {
                FeatureToggle(
                    "Bakım Modu",
                    current.maintenanceMode,
                    activeColor = Color(0xFFEF4444),
                ) { current = current.copy(maintenanceMode = it) }
                Spacer(Modifier.height(6.dp))
                cmsField(current.maintenanceMessage, { current = current.copy(maintenanceMessage = it) }, "Bakım mesajı", minLines = 2)
                Spacer(Modifier.height(4.dp))
                cmsField(
                    current.minVersion.toString(),
                    { current = current.copy(minVersion = it.filter(Char::isDigit).toIntOrNull() ?: 1) },
                    "Min. uygulama versiyonu",
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                )
            }
        }

        // ── Kaydet ────────────────────────────────────────────────────────
        item {
            if (saveResult.isNotBlank()) {
                Text(
                    saveResult,
                    color    = if (saveResult.startsWith("✓")) Color(0xFF22C55E) else Color(0xFFEF4444),
                    fontSize = 13.sp,
                )
                Spacer(Modifier.height(6.dp))
            }
            Button(
                onClick = {
                    saving = true
                    saveResult = ""
                    vm.save(current) { success ->
                        saving     = false
                        saveResult = if (success) "✓ Kaydedildi" else "✗ Hata oluştu"
                    }
                },
                enabled  = !saving,
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(10.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = Color.Black),
            ) {
                if (saving) {
                    CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                } else {
                    Icon(Icons.Default.Save, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                }
                Text("Kaydet", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun FeatureSection(
    title       : String,
    sectionColor: Color = Amber,
    content     : @Composable ColumnScope.() -> Unit,
) {
    Text(title, color = sectionColor, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
    Spacer(Modifier.height(6.dp))
    Surface(shape = RoundedCornerShape(14.dp), color = HeftSurface, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(4.dp), content = content)
    }
}

@Composable
private fun FeatureToggle(
    label      : String,
    value      : Boolean,
    activeColor: Color = Color(0xFF22C55E),
    onChange   : (Boolean) -> Unit,
) {
    Row(
        modifier          = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = OnBackground, modifier = Modifier.weight(1f), fontSize = 14.sp)
        Switch(
            checked         = value,
            onCheckedChange = onChange,
            colors          = SwitchDefaults.colors(
                checkedThumbColor   = Color.White,
                checkedTrackColor   = activeColor,
                uncheckedThumbColor = Muted,
                uncheckedTrackColor = Muted.copy(alpha = 0.2f),
            ),
        )
    }
}

@Composable
internal fun CmsCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape    = RoundedCornerShape(12.dp),
        color    = HeftSurface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), content = content)
    }
}

@Composable
private fun cmsField(
    value       : String,
    onChange    : (String) -> Unit,
    label       : String,
    minLines    : Int          = 1,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    OutlinedTextField(
        value         = value,
        onValueChange = onChange,
        label         = { Text(label) },
        minLines      = minLines,
        modifier      = Modifier.fillMaxWidth(),
        shape         = RoundedCornerShape(10.dp),
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType,
            imeAction    = if (minLines > 1) ImeAction.Default else ImeAction.Next,
        ),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor      = Amber,
            unfocusedBorderColor    = Divider,
            focusedTextColor        = OnBackground,
            unfocusedTextColor      = OnBackground,
            unfocusedContainerColor = SurfaceVar,
            focusedContainerColor   = SurfaceVar,
            focusedLabelColor       = Amber,
            unfocusedLabelColor     = Muted,
            cursorColor             = Amber,
        ),
    )
}

@Composable
internal fun StatChip(
    label   : String,
    sub     : String,
    selected: Boolean,
    color   : androidx.compose.ui.graphics.Color,
    onClick : () -> Unit,
) {
    Surface(
        modifier  = Modifier.clickable { onClick() },
        shape     = RoundedCornerShape(10.dp),
        color     = if (selected) color.copy(alpha = 0.2f) else HeftSurface,
        border    = if (selected) androidx.compose.foundation.BorderStroke(1.dp, color) else null,
    ) {
        Column(
            modifier            = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(label, color = color, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Text(sub,   color = Muted, fontSize   = 10.sp)
        }
    }
}


// ─────────────────────────────────────────────────────────────────────────────
// KÜTÜPHANE SEKMESİ — Yazar ↔ Kitap bağlama + Alıntı yönetimi
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun LibraryTab() {
    val db = remember { com.google.firebase.firestore.FirebaseFirestore.getInstance() }
    val scope = rememberCoroutineScope()

    // ── State ─────────────────────────────────────────────────────────────────
    var authors      by remember { mutableStateOf<List<Author>>(emptyList()) }
    var books        by remember { mutableStateOf<List<LibraryBook>>(emptyList()) }
    var quotes       by remember { mutableStateOf<List<BookQuote>>(emptyList()) }
    var loading      by remember { mutableStateOf(true) }
    var snackMsg     by remember { mutableStateOf("") }
    val snackState   = remember { SnackbarHostState() }

    // seçili yazar (kitap bağlama için)
    var selectedAuthorId   by remember { mutableStateOf("") }
    var selectedAuthorName by remember { mutableStateOf("") }
    // seçili kitap (alıntı bağlama için)
    var selectedBookId     by remember { mutableStateOf("") }
    var selectedBookTitle  by remember { mutableStateOf("") }

    // Arama filtreleri
    var authorSearch by remember { mutableStateOf("") }
    var bookSearch   by remember { mutableStateOf("") }
    var quoteSearch  by remember { mutableStateOf("") }

    // İç tab: 0=Yazarlar, 1=Kitaplar, 2=Alıntılar
    var innerTab by remember { mutableIntStateOf(0) }

    // Yeni kitap formu
    var showAddBook     by remember { mutableStateOf(false) }
    var newBookTitle    by remember { mutableStateOf("") }
    var newBookGenre    by remember { mutableStateOf("") }
    var newBookYear     by remember { mutableStateOf("") }

    // Alıntı kitap bağlama
    var linkQuoteId    by remember { mutableStateOf("") }
    var linkBookExpand by remember { mutableStateOf(false) }

    LaunchedEffect(snackMsg) {
        if (snackMsg.isNotBlank()) {
            snackState.showSnackbar(snackMsg)
            snackMsg = ""
        }
    }

    // Veri yükleme
    fun reload() {
        scope.launch {
            loading = true
            try {
                authors = db.collection("authors").get().await()
                    .documents.mapNotNull { d ->
                        d.toObject(Author::class.java)?.copy(id = d.id)
                    }.sortedBy { it.name }

                books = db.collection("library_books").get().await()
                    .documents.mapNotNull { d ->
                        d.toObject(LibraryBook::class.java)?.copy(id = d.id)
                    }.sortedBy { it.title }
            } catch (e: Exception) {
                snackMsg = "❌ ${e.message}"
            } finally {
                loading = false
            }
        }
    }

    fun loadQuotesForBook(bookId: String) {
        scope.launch {
            try {
                quotes = db.collection("library_books").document(bookId)
                    .collection("quotes").get().await()
                    .documents.mapNotNull { d ->
                        d.toObject(BookQuote::class.java)?.copy(id = d.id)
                    }
            } catch (e: Exception) {
                snackMsg = "❌ ${e.message}"
            }
        }
    }

    LaunchedEffect(Unit) { reload() }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {

            // ── İç sekmeler ───────────────────────────────────────────────────
            TabRow(
                selectedTabIndex = innerTab,
                containerColor   = Background,
                contentColor     = Primary,
            ) {
                listOf("Yazarlar", "Kitaplar", "Alıntılar").forEachIndexed { i, title ->
                    Tab(
                        selected = innerTab == i,
                        onClick  = { innerTab = i },
                        text     = { Text(title, fontSize = 12.sp) },
                        selectedContentColor   = Primary,
                        unselectedContentColor = Muted,
                    )
                }
            }

            if (loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Primary)
                }
                return@Column
            }

            when (innerTab) {

                // ══════════════════════════════════════════════════════════════
                // 0 — YAZARLAR
                // ══════════════════════════════════════════════════════════════
                0 -> {
                    val filtered = authors.filter {
                        authorSearch.isBlank() || it.name.contains(authorSearch, ignoreCase = true)
                    }

                    Column(Modifier.fillMaxSize()) {
                        OutlinedTextField(
                            value         = authorSearch,
                            onValueChange = { authorSearch = it },
                            placeholder   = { Text("Yazar ara…", color = Muted, fontSize = 13.sp) },
                            modifier      = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                            singleLine    = true,
                            leadingIcon   = { Icon(Icons.Default.Search, null, tint = Muted) },
                            colors        = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor   = Primary,
                                unfocusedBorderColor = Divider,
                                focusedTextColor     = OnBackground,
                                unfocusedTextColor   = OnBackground,
                            ),
                            shape = RoundedCornerShape(10.dp),
                        )

                        Text(
                            "${filtered.size} yazar",
                            color    = Muted,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                        )

                        LazyColumn(
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            items(filtered, key = { it.id }) { author ->
                                val authorBooks = books.filter { it.authorId == author.id }
                                AuthorAdminCard(
                                    author      = author,
                                    books       = authorBooks,
                                    allBooks    = books,
                                    onLinkBook  = { bookId, bookTitle ->
                                        scope.launch {
                                            try {
                                                db.collection("library_books").document(bookId)
                                                    .update(mapOf(
                                                        "authorId"   to author.id,
                                                        "authorName" to author.name,
                                                    )).await()
                                                db.collection("authors").document(author.id)
                                                    .update("bookCount", com.google.firebase.firestore.FieldValue.increment(1)).await()
                                                snackMsg = "✓ '$bookTitle' → '${author.name}' bağlandı"
                                                reload()
                                            } catch (e: Exception) { snackMsg = "❌ ${e.message}" }
                                        }
                                    },
                                    onUnlinkBook = { bookId, bookTitle ->
                                        scope.launch {
                                            try {
                                                db.collection("library_books").document(bookId)
                                                    .update(mapOf("authorId" to "", "authorName" to "")).await()
                                                db.collection("authors").document(author.id)
                                                    .update("bookCount", com.google.firebase.firestore.FieldValue.increment(-1)).await()
                                                snackMsg = "✓ '$bookTitle' bağlantısı kaldırıldı"
                                                reload()
                                            } catch (e: Exception) { snackMsg = "❌ ${e.message}" }
                                        }
                                    },
                                    onAddBook = { title, genre, year ->
                                        scope.launch {
                                            try {
                                                db.collection("library_books").add(mapOf(
                                                    "title"      to title,
                                                    "authorId"   to author.id,
                                                    "authorName" to author.name,
                                                    "genre"      to genre,
                                                    "publishYear" to (year.toIntOrNull() ?: 0),
                                                    "quoteCount" to 0,
                                                    "reviewCount" to 0,
                                                    "avgRating"  to 0f,
                                                )).await()
                                                db.collection("authors").document(author.id)
                                                    .update("bookCount", com.google.firebase.firestore.FieldValue.increment(1)).await()
                                                snackMsg = "✓ '$title' kitabı eklendi"
                                                reload()
                                            } catch (e: Exception) { snackMsg = "❌ ${e.message}" }
                                        }
                                    },
                                )
                            }
                        }
                    }
                }

                // ══════════════════════════════════════════════════════════════
                // 1 — KİTAPLAR
                // ══════════════════════════════════════════════════════════════
                1 -> {
                    val filtered = books.filter {
                        bookSearch.isBlank() ||
                        it.title.contains(bookSearch, ignoreCase = true) ||
                        it.authorName.contains(bookSearch, ignoreCase = true)
                    }

                    Column(Modifier.fillMaxSize()) {
                        OutlinedTextField(
                            value         = bookSearch,
                            onValueChange = { bookSearch = it },
                            placeholder   = { Text("Kitap veya yazar ara…", color = Muted, fontSize = 13.sp) },
                            modifier      = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                            singleLine    = true,
                            leadingIcon   = { Icon(Icons.Default.Search, null, tint = Muted) },
                            colors        = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor   = Primary,
                                unfocusedBorderColor = Divider,
                                focusedTextColor     = OnBackground,
                                unfocusedTextColor   = OnBackground,
                            ),
                            shape = RoundedCornerShape(10.dp),
                        )
                        Text(
                            "${filtered.size} kitap",
                            color    = Muted,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                        )
                        LazyColumn(
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            items(filtered, key = { it.id }) { book ->
                                BookAdminCard(
                                    book    = book,
                                    authors = authors,
                                    onChangeAuthor = { newAuthorId, newAuthorName ->
                                        scope.launch {
                                            try {
                                                // eski yazarın bookCount'unu düşür
                                                if (book.authorId.isNotBlank()) {
                                                    db.collection("authors").document(book.authorId)
                                                        .update("bookCount", com.google.firebase.firestore.FieldValue.increment(-1)).await()
                                                }
                                                db.collection("library_books").document(book.id)
                                                    .update(mapOf("authorId" to newAuthorId, "authorName" to newAuthorName)).await()
                                                db.collection("authors").document(newAuthorId)
                                                    .update("bookCount", com.google.firebase.firestore.FieldValue.increment(1)).await()
                                                snackMsg = "✓ '${book.title}' → '$newAuthorName' bağlandı"
                                                reload()
                                            } catch (e: Exception) { snackMsg = "❌ ${e.message}" }
                                        }
                                    },
                                    onViewQuotes = { bookId, bookTitle ->
                                        selectedBookId    = bookId
                                        selectedBookTitle = bookTitle
                                        loadQuotesForBook(bookId)
                                        innerTab = 2
                                    },
                                )
                            }
                        }
                    }
                }

                // ══════════════════════════════════════════════════════════════
                // 2 — ALINTILAR
                // ══════════════════════════════════════════════════════════════
                2 -> {
                    Column(Modifier.fillMaxSize()) {

                        // Hangi kitabın alıntıları gösteriliyor
                        if (selectedBookId.isNotBlank()) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .background(SurfaceVar)
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Default.MenuBook, null, tint = Primary, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    selectedBookTitle,
                                    color      = Primary,
                                    fontSize   = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier   = Modifier.weight(1f),
                                )
                                TextButton(onClick = {
                                    selectedBookId = ""
                                    selectedBookTitle = ""
                                    quotes = emptyList()
                                }) {
                                    Text("Tümü", color = Muted, fontSize = 11.sp)
                                }
                            }
                        } else {
                            // Tüm alıntıları yükle
                            LaunchedEffect(Unit) {
                                scope.launch {
                                    try {
                                        val result = mutableListOf<BookQuote>()
                                        books.take(30).forEach { book ->
                                            db.collection("library_books").document(book.id)
                                                .collection("quotes").limit(20).get().await()
                                                .documents.mapNotNull { d ->
                                                    d.toObject(BookQuote::class.java)?.copy(id = d.id)
                                                }.let { result.addAll(it) }
                                        }
                                        quotes = result.sortedByDescending { it.ts }
                                    } catch (_: Exception) {}
                                }
                            }
                        }

                        OutlinedTextField(
                            value         = quoteSearch,
                            onValueChange = { quoteSearch = it },
                            placeholder   = { Text("Alıntı ara…", color = Muted, fontSize = 13.sp) },
                            modifier      = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                            singleLine    = true,
                            leadingIcon   = { Icon(Icons.Default.Search, null, tint = Muted) },
                            colors        = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor   = Primary,
                                unfocusedBorderColor = Divider,
                                focusedTextColor     = OnBackground,
                                unfocusedTextColor   = OnBackground,
                            ),
                            shape = RoundedCornerShape(10.dp),
                        )

                        val filteredQ = quotes.filter {
                            quoteSearch.isBlank() ||
                            it.text.contains(quoteSearch, ignoreCase = true) ||
                            it.bookTitle.contains(quoteSearch, ignoreCase = true) ||
                            it.authorName.contains(quoteSearch, ignoreCase = true)
                        }

                        Text(
                            "${filteredQ.size} alıntı",
                            color    = Muted,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                        )

                        LazyColumn(
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            items(filteredQ, key = { it.id }) { quote ->
                                QuoteAdminCard(
                                    quote   = quote,
                                    books   = books,
                                    authors = authors,
                                    onDelete = {
                                        scope.launch {
                                            try {
                                                if (quote.bookId.isNotBlank()) {
                                                    db.collection("library_books").document(quote.bookId)
                                                        .collection("quotes").document(quote.id).delete().await()
                                                    db.collection("library_books").document(quote.bookId)
                                                        .update("quoteCount", com.google.firebase.firestore.FieldValue.increment(-1)).await()
                                                }
                                                if (quote.authorId.isNotBlank()) {
                                                    db.collection("authors").document(quote.authorId)
                                                        .update("quoteCount", com.google.firebase.firestore.FieldValue.increment(-1)).await()
                                                }
                                                snackMsg = "✓ Alıntı silindi"
                                                if (selectedBookId.isNotBlank()) loadQuotesForBook(selectedBookId)
                                                else quotes = quotes.filter { it.id != quote.id }
                                            } catch (e: Exception) { snackMsg = "❌ ${e.message}" }
                                        }
                                    },
                                    onRelink = { newBookId, newBookTitle, newAuthorId, newAuthorName ->
                                        scope.launch {
                                            try {
                                                // Eski kitaptan sil
                                                if (quote.bookId.isNotBlank()) {
                                                    db.collection("library_books").document(quote.bookId)
                                                        .collection("quotes").document(quote.id).delete().await()
                                                    db.collection("library_books").document(quote.bookId)
                                                        .update("quoteCount", com.google.firebase.firestore.FieldValue.increment(-1)).await()
                                                }
                                                // Yeni kitaba ekle
                                                db.collection("library_books").document(newBookId)
                                                    .collection("quotes").document(quote.id).set(
                                                        mapOf(
                                                            "bookId"      to newBookId,
                                                            "authorId"    to newAuthorId,
                                                            "bookTitle"   to newBookTitle,
                                                            "authorName"  to newAuthorName,
                                                            "text"        to quote.text,
                                                            "uid"         to quote.uid,
                                                            "userDisplayName" to quote.userDisplayName,
                                                            "userPhotoURL"    to quote.userPhotoURL,
                                                            "feedPostId"  to quote.feedPostId,
                                                            "ts"          to quote.ts,
                                                        )
                                                    ).await()
                                                db.collection("library_books").document(newBookId)
                                                    .update("quoteCount", com.google.firebase.firestore.FieldValue.increment(1)).await()
                                                // feed postunu da güncelle
                                                if (quote.feedPostId.isNotBlank()) {
                                                    db.collection("posts").document(quote.feedPostId)
                                                        .update(mapOf(
                                                            "libraryBookId"   to newBookId,
                                                            "libraryAuthorId" to newAuthorId,
                                                        )).await()
                                                }
                                                snackMsg = "✓ Alıntı '$newBookTitle' kitabına bağlandı"
                                                if (selectedBookId.isNotBlank()) loadQuotesForBook(selectedBookId)
                                                else quotes = quotes.filter { it.id != quote.id }
                                            } catch (e: Exception) { snackMsg = "❌ ${e.message}" }
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackState,
            modifier  = Modifier.align(Alignment.BottomCenter).padding(16.dp),
        ) { data ->
            Snackbar(
                snackbarData   = data,
                containerColor = if (data.visuals.message.startsWith("✓")) Success else Error,
                contentColor   = Color.Black,
                shape          = RoundedCornerShape(12.dp),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// AuthorAdminCard — yazarı genişletilebilir kart
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun AuthorAdminCard(
    author       : Author,
    books        : List<LibraryBook>,         // bu yazarın mevcut kitapları
    allBooks     : List<LibraryBook>,         // bağlanabilecek tüm kitaplar
    onLinkBook   : (bookId: String, bookTitle: String) -> Unit,
    onUnlinkBook : (bookId: String, bookTitle: String) -> Unit,
    onAddBook    : (title: String, genre: String, year: String) -> Unit,
) {
    var expanded     by remember { mutableStateOf(false) }
    var showLinkDrop by remember { mutableStateOf(false) }
    var showAddForm  by remember { mutableStateOf(false) }
    var newTitle     by remember { mutableStateOf("") }
    var newGenre     by remember { mutableStateOf("") }
    var newYear      by remember { mutableStateOf("") }

    // Bağlanabilecek kitaplar (henüz bu yazara ait olmayan)
    val linkable = allBooks.filter { it.authorId != author.id }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(12.dp),
        colors   = CardDefaults.cardColors(containerColor = HeftSurface),
        elevation = CardDefaults.cardElevation(1.dp),
    ) {
        Column(Modifier.padding(12.dp)) {
            // ── Başlık satırı ─────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Default.Person,
                    null,
                    tint     = Primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(author.name, color = OnBackground, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text(
                        "${books.size} kitap · ${author.quoteCount} alıntı",
                        color    = Muted,
                        fontSize = 11.sp,
                    )
                }
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    null,
                    tint     = Muted,
                    modifier = Modifier.size(18.dp),
                )
            }

            if (expanded) {
                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = Divider)
                Spacer(Modifier.height(8.dp))

                // ── Mevcut kitaplar ───────────────────────────────────────
                if (books.isEmpty()) {
                    Text("Bağlı kitap yok", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 6.dp))
                } else {
                    books.forEach { book ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Default.MenuBook, null, tint = Muted, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(book.title, color = OnBackground, fontSize = 13.sp, modifier = Modifier.weight(1f))
                            IconButton(
                                onClick  = { onUnlinkBook(book.id, book.title) },
                                modifier = Modifier.size(28.dp),
                            ) {
                                Icon(Icons.Default.LinkOff, null, tint = Error, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }

                Spacer(Modifier.height(6.dp))

                // ── Mevcut kitabı bağla ───────────────────────────────────
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { showLinkDrop = !showLinkDrop; showAddForm = false },
                        shape   = RoundedCornerShape(8.dp),
                        border  = androidx.compose.foundation.BorderStroke(1.dp, Primary),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(vertical = 6.dp),
                    ) {
                        Icon(Icons.Default.Link, null, tint = Primary, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Kitap Bağla", color = Primary, fontSize = 12.sp)
                    }
                    OutlinedButton(
                        onClick = { showAddForm = !showAddForm; showLinkDrop = false },
                        shape   = RoundedCornerShape(8.dp),
                        border  = androidx.compose.foundation.BorderStroke(1.dp, Success),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(vertical = 6.dp),
                    ) {
                        Icon(Icons.Default.Add, null, tint = Success, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Yeni Kitap", color = Success, fontSize = 12.sp)
                    }
                }

                // Dropdown — mevcut kitap seç
                if (showLinkDrop) {
                    Spacer(Modifier.height(6.dp))
                    if (linkable.isEmpty()) {
                        Text("Bağlanabilecek kitap yok", color = Muted, fontSize = 12.sp)
                    } else {
                        Card(
                            shape  = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(containerColor = SurfaceVar),
                        ) {
                            LazyColumn(Modifier.heightIn(max = 200.dp)) {
                                items(linkable) { book ->
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                onLinkBook(book.id, book.title)
                                                showLinkDrop = false
                                            }
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(Icons.Default.MenuBook, null, tint = Muted, modifier = Modifier.size(14.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Column {
                                            Text(book.title, color = OnBackground, fontSize = 13.sp)
                                            if (book.authorName.isNotBlank())
                                                Text(book.authorName, color = Muted, fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Form — yeni kitap ekle
                if (showAddForm) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value         = newTitle,
                        onValueChange = { newTitle = it },
                        label         = { Text("Kitap Adı *", fontSize = 12.sp) },
                        modifier      = Modifier.fillMaxWidth(),
                        singleLine    = true,
                        colors        = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor   = Primary,
                            unfocusedBorderColor = Divider,
                            focusedTextColor     = OnBackground,
                            unfocusedTextColor   = OnBackground,
                        ),
                        shape = RoundedCornerShape(8.dp),
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value         = newGenre,
                            onValueChange = { newGenre = it },
                            label         = { Text("Tür", fontSize = 12.sp) },
                            modifier      = Modifier.weight(1f),
                            singleLine    = true,
                            colors        = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor   = Primary,
                                unfocusedBorderColor = Divider,
                                focusedTextColor     = OnBackground,
                                unfocusedTextColor   = OnBackground,
                            ),
                            shape = RoundedCornerShape(8.dp),
                        )
                        OutlinedTextField(
                            value         = newYear,
                            onValueChange = { newYear = it },
                            label         = { Text("Yıl", fontSize = 12.sp) },
                            modifier      = Modifier.weight(1f),
                            singleLine    = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors        = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor   = Primary,
                                unfocusedBorderColor = Divider,
                                focusedTextColor     = OnBackground,
                                unfocusedTextColor   = OnBackground,
                            ),
                            shape = RoundedCornerShape(8.dp),
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Button(
                        onClick  = {
                            if (newTitle.isNotBlank()) {
                                onAddBook(newTitle, newGenre, newYear)
                                newTitle = ""; newGenre = ""; newYear = ""
                                showAddForm = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape    = RoundedCornerShape(8.dp),
                        colors   = ButtonDefaults.buttonColors(containerColor = Primary),
                    ) {
                        Text("Ekle", color = Color.White, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// BookAdminCard — kitabı yazar değiştir / alıntıları gör
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun BookAdminCard(
    book          : LibraryBook,
    authors       : List<Author>,
    onChangeAuthor: (authorId: String, authorName: String) -> Unit,
    onViewQuotes  : (bookId: String, bookTitle: String) -> Unit,
) {
    var showAuthorDrop by remember { mutableStateOf(false) }
    var authorSearch   by remember { mutableStateOf("") }

    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(12.dp),
        colors    = CardDefaults.cardColors(containerColor = HeftSurface),
        elevation = CardDefaults.cardElevation(1.dp),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.MenuBook, null, tint = Primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(book.title, color = OnBackground, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text(
                        (book.authorName.ifBlank { "Yazar yok" }) + " · ${book.quoteCount} alıntı",
                        color    = Muted,
                        fontSize = 11.sp,
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick        = { showAuthorDrop = !showAuthorDrop },
                    shape          = RoundedCornerShape(8.dp),
                    border         = androidx.compose.foundation.BorderStroke(1.dp, Amber),
                    modifier       = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 6.dp),
                ) {
                    Icon(Icons.Default.Person, null, tint = Amber, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Yazarı Değiştir", color = Amber, fontSize = 12.sp)
                }
                OutlinedButton(
                    onClick        = { onViewQuotes(book.id, book.title) },
                    shape          = RoundedCornerShape(8.dp),
                    border         = androidx.compose.foundation.BorderStroke(1.dp, Primary),
                    modifier       = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 6.dp),
                ) {
                    Icon(Icons.Default.FormatQuote, null, tint = Primary, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Alıntılar (${book.quoteCount})", color = Primary, fontSize = 12.sp)
                }
            }

            if (showAuthorDrop) {
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value         = authorSearch,
                    onValueChange = { authorSearch = it },
                    placeholder   = { Text("Yazar ara…", color = Muted, fontSize = 12.sp) },
                    modifier      = Modifier.fillMaxWidth(),
                    singleLine    = true,
                    colors        = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = Primary,
                        unfocusedBorderColor = Divider,
                        focusedTextColor     = OnBackground,
                        unfocusedTextColor   = OnBackground,
                    ),
                    shape = RoundedCornerShape(8.dp),
                )
                Spacer(Modifier.height(4.dp))
                val filtered = authors.filter {
                    authorSearch.isBlank() || it.name.contains(authorSearch, ignoreCase = true)
                }
                Card(
                    shape  = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceVar),
                ) {
                    LazyColumn(Modifier.heightIn(max = 180.dp)) {
                        items(filtered) { a ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onChangeAuthor(a.id, a.name)
                                        showAuthorDrop = false
                                        authorSearch   = ""
                                    }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Default.Person, null, tint = Muted, modifier = Modifier.size(13.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(a.name, color = OnBackground, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// QuoteAdminCard — alıntıyı sil / kitaba yeniden bağla
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun QuoteAdminCard(
    quote    : BookQuote,
    books    : List<LibraryBook>,
    authors  : List<Author>,
    onDelete : () -> Unit,
    onRelink : (bookId: String, bookTitle: String, authorId: String, authorName: String) -> Unit,
) {
    var showRelinkDrop by remember { mutableStateOf(false) }
    var showDeleteConf by remember { mutableStateOf(false) }
    var bookSearch     by remember { mutableStateOf("") }

    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(12.dp),
        colors    = CardDefaults.cardColors(containerColor = HeftSurface),
        elevation = CardDefaults.cardElevation(1.dp),
    ) {
        Column(Modifier.padding(12.dp)) {
            // Kitap / yazar bilgisi
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.FormatQuote, null, tint = Amber, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Column(Modifier.weight(1f)) {
                    if (quote.bookTitle.isNotBlank())
                        Text(quote.bookTitle, color = Primary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    if (quote.authorName.isNotBlank())
                        Text(quote.authorName, color = Muted, fontSize = 11.sp)
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                quote.text,
                color    = OnBackground,
                fontSize = 13.sp,
                maxLines = 4,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            if (quote.userDisplayName.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text("@${quote.userDisplayName}", color = Muted, fontSize = 11.sp)
            }

            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick        = { showRelinkDrop = !showRelinkDrop; showDeleteConf = false },
                    shape          = RoundedCornerShape(8.dp),
                    border         = androidx.compose.foundation.BorderStroke(1.dp, Primary),
                    modifier       = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 6.dp),
                ) {
                    Icon(Icons.Default.Link, null, tint = Primary, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Kitap Bağla", color = Primary, fontSize = 12.sp)
                }
                OutlinedButton(
                    onClick        = { showDeleteConf = !showDeleteConf; showRelinkDrop = false },
                    shape          = RoundedCornerShape(8.dp),
                    border         = androidx.compose.foundation.BorderStroke(1.dp, Error),
                    modifier       = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 6.dp),
                ) {
                    Icon(Icons.Default.Delete, null, tint = Error, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Sil", color = Error, fontSize = 12.sp)
                }
            }

            // Silme onayı
            if (showDeleteConf) {
                Spacer(Modifier.height(6.dp))
                Card(
                    shape  = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = Error.copy(alpha = 0.1f)),
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Silinsin mi?", color = Error, fontSize = 12.sp, modifier = Modifier.weight(1f))
                        TextButton(onClick = { showDeleteConf = false }) {
                            Text("Hayır", color = Muted, fontSize = 12.sp)
                        }
                        TextButton(onClick = { onDelete(); showDeleteConf = false }) {
                            Text("Evet, Sil", color = Error, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Kitap bağlama dropdown
            if (showRelinkDrop) {
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value         = bookSearch,
                    onValueChange = { bookSearch = it },
                    placeholder   = { Text("Kitap ara…", color = Muted, fontSize = 12.sp) },
                    modifier      = Modifier.fillMaxWidth(),
                    singleLine    = true,
                    colors        = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = Primary,
                        unfocusedBorderColor = Divider,
                        focusedTextColor     = OnBackground,
                        unfocusedTextColor   = OnBackground,
                    ),
                    shape = RoundedCornerShape(8.dp),
                )
                Spacer(Modifier.height(4.dp))
                val filteredB = books.filter {
                    bookSearch.isBlank() || it.title.contains(bookSearch, ignoreCase = true)
                }
                Card(
                    shape  = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceVar),
                ) {
                    LazyColumn(Modifier.heightIn(max = 200.dp)) {
                        items(filteredB) { b ->
                            val bAuthor = authors.find { it.id == b.authorId }
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onRelink(b.id, b.title, b.authorId, b.authorName)
                                        showRelinkDrop = false
                                        bookSearch     = ""
                                    }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Default.MenuBook, null, tint = Muted, modifier = Modifier.size(13.dp))
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text(b.title, color = OnBackground, fontSize = 13.sp)
                                    if (b.authorName.isNotBlank())
                                        Text(b.authorName, color = Muted, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
