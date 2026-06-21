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
import com.heftreng.app.viewmodel.AdsViewModel
import com.heftreng.app.viewmodel.AppConfigViewModel
import com.heftreng.app.viewmodel.CmsViewModel
import com.heftreng.app.viewmodel.YazarViewModel
import com.heftreng.app.viewmodel.PendingPost
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
    adsVm    : AdsViewModel       = hiltViewModel(),
    configVm : AppConfigViewModel = hiltViewModel(),
    yazarVm  : YazarViewModel     = hiltViewModel(),
) {
    val pages         by vm.pages.collectAsState()
    val banners       by vm.banners.collectAsState()
    val announcements by vm.announcements.collectAsState()
    val categories    by vm.categories.collectAsState()
    val loading       by vm.loading.collectAsState()
    val result        by vm.result.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }
    val pendingPosts  by yazarVm.pendingPosts.collectAsState()
    val pendingStats  by yazarVm.pendingStats.collectAsState()
    val pendingLoad   by yazarVm.pendingLoading.collectAsState()
    val appConfig by configVm.config.collectAsState()
    val tabs = listOf("Sayfalar", "Bannerlar", "Duyurular", "Kategoriler", "Reklamlar", "Özellikler", "Yazılar", "Kütüphane", "Admin", "Kurdî Admin")

    LaunchedEffect(Unit) {
        vm.loadPages()
        vm.loadBanners()
        vm.loadAnnouncements()
        vm.loadCategories()
        adsVm.loadAdConfigs()
        configVm.load()
        yazarVm.loadAllPendingPosts()
    }

    // Erişim kontrolü
    if (!vm.isAdmin) {
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
                4 -> AdsTab(adsVm)
                5 -> FeaturesTab(appConfig, configVm)
                6 -> PendingPostsTab(pendingPosts, pendingStats, pendingLoad, yazarVm)
                7 -> LibraryTab()
                8 -> { LaunchedEffect(Unit) { navController.navigate("admin") } }
                9 -> { LaunchedEffect(Unit) { navController.navigate("kurdi_admin") } }
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
    var title  by remember { mutableStateOf(initial.title) }
    var body   by remember { mutableStateOf(initial.body) }
    var type   by remember { mutableStateOf(initial.type) }
    var active by remember { mutableStateOf(initial.active) }

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
                        onSave(initial.copy(title = title.trim(), body = body.trim(), type = type, active = active))
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
                FeatureToggle("AI Ders",           current.kurdiShowAiLesson)   { current = current.copy(kurdiShowAiLesson = it) }
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
private fun CmsCard(content: @Composable ColumnScope.() -> Unit) {
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

// ── 5. Reklamlar ──────────────────────────────────────────────────────────────
@Composable
private fun AdsTab(adsVm: AdsViewModel) {
    val bannerConfig       by adsVm.bannerConfig.collectAsState()
    val interstitialConfig by adsVm.interstitialConfig.collectAsState()
    val rewardedConfig     by adsVm.rewardedConfig.collectAsState()
    val nativeFeedConfig   by adsVm.nativeFeedConfig.collectAsState()
    val nativeBlogConfig   by adsVm.nativeBlogConfig.collectAsState()
    val nativeLibraryConfig by adsVm.nativeLibraryConfig.collectAsState()
    val nativeKurdiConfig   by adsVm.nativeKurdiConfig.collectAsState()
    val adsEnabled         by adsVm.adsEnabled.collectAsState()
    val allAdConfigs       by adsVm.allAdConfigs.collectAsState()
    val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
    val scope     = androidx.compose.runtime.rememberCoroutineScope()

    var showAddSlotDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier       = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // ── Global Kill Switch ─────────────────────────────────────────────
        item {
            CmsCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Reklamlar", color = OnBackground, fontWeight = FontWeight.Bold)
                        Text(
                            if (adsEnabled) "Tüm reklamlar aktif" else "Tüm reklamlar kapalı",
                            color = if (adsEnabled) Success else Muted, fontSize = 12.sp,
                        )
                    }
                    Switch(
                        checked = adsEnabled,
                        onCheckedChange = { enabled ->
                            scope.launch {
                                try {
                                    firestore.collection("cms_ads").document("global")
                                        .set(mapOf("enabled" to enabled)).await()
                                    adsVm.loadAdConfigs()
                                } catch (e: Exception) { e.printStackTrace() }
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor   = Success,
                            checkedTrackColor   = Success.copy(alpha = 0.3f),
                            uncheckedThumbColor = Muted,
                            uncheckedTrackColor = Muted.copy(alpha = 0.2f),
                        ),
                    )
                }
            }
        }

        // ── Yeni Slot Ekle Butonu ──────────────────────────────────────────
        item {
            OutlinedButton(
                onClick  = { showAddSlotDialog = true },
                modifier = Modifier.fillMaxWidth(),
                border   = androidx.compose.foundation.BorderStroke(1.dp, Amber.copy(alpha = 0.5f)),
                shape    = RoundedCornerShape(10.dp),
            ) {
                Icon(Icons.Default.Add, null, tint = Amber, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Yeni Reklam Slotu Ekle", color = Amber)
            }
        }

        // ── Banner Feed ────────────────────────────────────────────────────
        item {
            AdConfigCard(
                title      = "Banner — Feed",
                docId      = "banner_feed",
                config     = bannerConfig,
                onSaved    = { adsVm.loadAdConfigs() },
                onDeleted  = null, // varsayılan slot — silinemez
            )
        }

        // ── Banner Kütüphane ───────────────────────────────────────────────
        item {
            AdConfigCard(
                title     = "Banner — Kütüphane",
                docId     = "banner_library",
                config    = adsVm.bannerLibraryConfig.collectAsState().value,
                onSaved   = { adsVm.loadAdConfigs() },
                onDeleted = null,
            )
        }

        // ── Banner Kürtçe ──────────────────────────────────────────────────
        item {
            AdConfigCard(
                title     = "Banner — Kürtçe Dersler",
                docId     = "banner_kurdi",
                config    = adsVm.bannerKurdiConfig.collectAsState().value,
                onSaved   = { adsVm.loadAdConfigs() },
                onDeleted = null,
            )
        }

        // ── Banner Blog ────────────────────────────────────────────────────
        item {
            AdConfigCard(
                title     = "Banner — Blog",
                docId     = "banner_blog",
                config    = adsVm.bannerBlogConfig.collectAsState().value,
                onSaved   = { adsVm.loadAdConfigs() },
                onDeleted = null,
            )
        }

        // ── Native Ad — Feed ────────────────────────────────────────────────
        item {
            AdConfigCard(
                title      = "Native Ad — Feed",
                docId      = "native_feed",
                config     = nativeFeedConfig,
                onSaved    = { adsVm.loadAdConfigs() },
                onDeleted  = null,
            )
        }

        // ── Native Ad — Blog ────────────────────────────────────────────────
        item {
            AdConfigCard(
                title      = "Native Ad — Blog",
                docId      = "native_blog",
                config     = nativeBlogConfig,
                onSaved    = { adsVm.loadAdConfigs() },
                onDeleted  = null,
            )
        }

        // ── Native Ad — Kütüphane (ÖNCEDEN HİÇ YOKTU — eksik ekranlardan biri) ──
        item {
            AdConfigCard(
                title      = "Native Ad — Kütüphane",
                docId      = "native_library",
                config     = nativeLibraryConfig,
                onSaved    = { adsVm.loadAdConfigs() },
                onDeleted  = null,
            )
        }

        // ── Native Ad — Kürtçe Dersler (ÖNCEDEN HİÇ YOKTU — eksik ekranlardan biri) ──
        item {
            AdConfigCard(
                title      = "Native Ad — Kürtçe Dersler",
                docId      = "native_kurdi",
                config     = nativeKurdiConfig,
                onSaved    = { adsVm.loadAdConfigs() },
                onDeleted  = null,
            )
        }

        // ── Dinamik Slotlar (CMS'den admin tarafından sonradan eklenenler) ────
        // Sabit slotlar (banner_feed, native_feed, vb.) yukarıda zaten ayrı
        // kartlarla render ediliyor — allAdConfigs TÜM dokümanları içerir,
        // bu yüzden burada onları HARİÇ TUTMAK gerekir, aksi halde aynı
        // slot ekranda iki kez görünür (biri sabit kart, biri burada).
        val FIXED_SLOT_IDS = setOf(
            "banner_feed", "banner_library", "banner_lib", "banner_kurdi", "banner_blog",
            "native_feed", "native_blog", "native_library", "native_kurdi",
            "interstitial_serial", "rewarded_xp", "global",
        )
        items(
            allAdConfigs.entries.filter { it.key !in FIXED_SLOT_IDS }.toList(),
            key = { it.key },
        ) { (docId, config) ->
            AdConfigCard(
                title     = config.label.ifBlank { docId },
                docId     = docId,
                config    = config,
                onSaved   = { adsVm.loadAdConfigs() },
                onDeleted = {
                    scope.launch {
                        try {
                            firestore.collection("cms_ads").document(docId).delete().await()
                            adsVm.loadAdConfigs()
                        } catch (e: Exception) { e.printStackTrace() }
                    }
                },
            )
        }

        // ── Interstitial Serial ────────────────────────────────────────────
        item {
            AdConfigCard(
                title     = "Interstitial — Seri Okuma",
                docId     = "interstitial_serial",
                config    = interstitialConfig,
                onSaved   = { adsVm.loadAdConfigs() },
                onDeleted = null,
            )
        }

        // ── Rewarded ──────────────────────────────────────────────────────
        item {
            RewardedConfigCard(
                config    = rewardedConfig,
                firestore = firestore,
                scope     = scope,
                onSaved   = { adsVm.loadAdConfigs() },
            )
        }
    }

    // ── Yeni Slot Dialog ──────────────────────────────────────────────────────
    if (showAddSlotDialog) {
        AddAdSlotDialog(
            onDismiss = { showAddSlotDialog = false },
            onSave    = { docId, config ->
                scope.launch {
                    try {
                        firestore.collection("cms_ads").document(docId).set(
                            mapOf(
                                "unitId"       to config.unitId,
                                "enabled"      to config.enabled,
                                "testMode"     to config.testMode,
                                "adType"       to config.adType,
                                "bannerSize"   to config.bannerSize,
                                "placement"    to config.placement,
                                "screens"      to config.screens,
                                "position"     to config.position,
                                "frequency"    to config.frequency,
                                "label"        to config.label,
                                "bgColor"      to config.bgColor,
                                "cornerRadius" to config.cornerRadius,
                                "paddingTop"   to config.paddingTop,
                                "paddingBottom" to config.paddingBottom,
                            )
                        ).await()
                        adsVm.loadAdConfigs()
                        showAddSlotDialog = false
                    } catch (e: Exception) { e.printStackTrace() }
                }
            },
        )
    }
}

// ── Yeni Slot Dialog ─────────────────────────────────────────────────────────
@Composable
private fun AddAdSlotDialog(
    onDismiss: () -> Unit,
    onSave   : (docId: String, config: com.heftreng.app.data.model.CmsAdConfig) -> Unit,
) {
    var label        by remember { mutableStateOf("") }
    var unitId       by remember { mutableStateOf("") }
    var adType       by remember { mutableStateOf("banner") }
    var bannerSize   by remember { mutableStateOf("adaptive") }
    var placement    by remember { mutableStateOf("in_list") }
    var position     by remember { mutableStateOf("5") }
    var frequency    by remember { mutableStateOf("3") }
    var bgColor      by remember { mutableStateOf("") }
    var cornerRadius by remember { mutableStateOf("0") }
    var paddingTop   by remember { mutableStateOf("0") }
    var paddingBottom by remember { mutableStateOf("0") }
    var testMode     by remember { mutableStateOf(true) }
    var enabled      by remember { mutableStateOf(true) }

    // Ekran seçimi
    val screenOptions = listOf("feed","profile","library","library_book","author_detail","kurdi","blog","search","messages")
    val selectedScreens = remember { mutableStateListOf("feed") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = HeftSurface,
        title = { Text("Yeni Reklam Slotu", color = OnBackground, fontWeight = FontWeight.Bold) },
        text  = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 500.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item { cmsField(label, { label = it }, "Slot Adı (örn: Banner — Blog)") }
                item { cmsField(unitId, { unitId = it }, "AdMob Unit ID") }

                // Reklam tipi
                item {
                    Text("Reklam Tipi", color = Muted, fontSize = 12.sp)
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("banner" to "Banner", "interstitial" to "Tam Ekran").forEach { (v, l) ->
                            FilterChip(
                                selected = adType == v,
                                onClick  = { adType = v },
                                label    = { Text(l, fontSize = 12.sp) },
                                colors   = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Amber.copy(alpha = 0.2f),
                                    selectedLabelColor     = Amber,
                                ),
                            )
                        }
                    }
                }

                // Banner boyutu (sadece banner tipinde)
                if (adType == "banner") {
                    item {
                        Text("Banner Boyutu", color = Muted, fontSize = 12.sp)
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf(
                                "adaptive" to "Adaptif",
                                "banner" to "Küçük",
                                "medium_rectangle" to "Orta",
                                "large_banner" to "Büyük",
                            ).forEach { (v, l) ->
                                FilterChip(
                                    selected = bannerSize == v,
                                    onClick  = { bannerSize = v },
                                    label    = { Text(l, fontSize = 11.sp) },
                                    colors   = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Primary.copy(alpha = 0.2f),
                                        selectedLabelColor     = Primary,
                                    ),
                                )
                            }
                        }
                    }
                }

                // Yerleşim tipi
                item {
                    Text("Yerleşim", color = Muted, fontSize = 12.sp)
                    Spacer(Modifier.height(4.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf("in_list" to "Liste İçi", "top" to "Üst", "bottom" to "Alt").forEach { (v, l) ->
                                FilterChip(
                                    selected = placement == v, onClick = { placement = v },
                                    label = { Text(l, fontSize = 11.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Primary.copy(alpha = 0.2f),
                                        selectedLabelColor = Primary,
                                    ),
                                )
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf("overlay" to "Overlay", "between_sections" to "Bölümler Arası").forEach { (v, l) ->
                                FilterChip(
                                    selected = placement == v, onClick = { placement = v },
                                    label = { Text(l, fontSize = 11.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Primary.copy(alpha = 0.2f),
                                        selectedLabelColor = Primary,
                                    ),
                                )
                            }
                        }
                    }
                }

                // Ekran seçimi
                item {
                    Text("Gösterileceği Ekranlar", color = Muted, fontSize = 12.sp)
                    Spacer(Modifier.height(4.dp))
                    val screenLabels = mapOf(
                        "feed" to "Feed", "profile" to "Profil", "library" to "Kütüphane",
                        "library_book" to "Kitap", "author_detail" to "Yazar",
                        "kurdi" to "Kürtçe", "blog" to "Blog",
                        "search" to "Arama", "messages" to "Mesajlar",
                    )
                    androidx.compose.foundation.layout.FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        screenOptions.forEach { screen ->
                            FilterChip(
                                selected = selectedScreens.contains(screen),
                                onClick  = {
                                    if (selectedScreens.contains(screen)) selectedScreens.remove(screen)
                                    else selectedScreens.add(screen)
                                },
                                label = { Text(screenLabels[screen] ?: screen, fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Amber.copy(alpha = 0.2f),
                                    selectedLabelColor     = Amber,
                                ),
                            )
                        }
                    }
                }

                // Pozisyon / Sıklık
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(Modifier.weight(1f)) {
                            cmsField(position, { position = it.filter(Char::isDigit) }, "Pozisyon (kaçıncı item)",
                                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                        }
                        Column(Modifier.weight(1f)) {
                            cmsField(frequency, { frequency = it.filter(Char::isDigit) }, "Sıklık",
                                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                        }
                    }
                }

                // Tasarım
                item {
                    Text("Tasarım (opsiyonel)", color = Muted, fontSize = 12.sp)
                    Spacer(Modifier.height(4.dp))
                    cmsField(bgColor, { bgColor = it }, "Arka plan rengi (#09090b)")
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(Modifier.weight(1f)) {
                            cmsField(cornerRadius, { cornerRadius = it.filter(Char::isDigit) }, "Köşe (dp)",
                                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                        }
                        Column(Modifier.weight(1f)) {
                            cmsField(paddingTop, { paddingTop = it.filter(Char::isDigit) }, "Üst boşluk",
                                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                        }
                        Column(Modifier.weight(1f)) {
                            cmsField(paddingBottom, { paddingBottom = it.filter(Char::isDigit) }, "Alt boşluk",
                                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                        }
                    }
                }

                // Aktif / Test
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Text("Aktif", color = OnBackground, fontSize = 13.sp, modifier = Modifier.weight(1f))
                            Switch(checked = enabled, onCheckedChange = { enabled = it },
                                colors = SwitchDefaults.colors(checkedThumbColor = Success, checkedTrackColor = Success.copy(alpha = 0.3f)))
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Text("Test", color = OnBackground, fontSize = 13.sp, modifier = Modifier.weight(1f))
                            Switch(checked = testMode, onCheckedChange = { testMode = it },
                                colors = SwitchDefaults.colors(checkedThumbColor = Amber, checkedTrackColor = Amber.copy(alpha = 0.3f)))
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val docId = "custom_${label.lowercase().replace(" ", "_")}_${System.currentTimeMillis() / 1000}"
                    val config = com.heftreng.app.data.model.CmsAdConfig(
                        unitId        = unitId.trim(),
                        enabled       = enabled,
                        testMode      = testMode,
                        adType        = adType,
                        bannerSize    = bannerSize,
                        placement     = placement,
                        screens       = selectedScreens.joinToString(","),
                        position      = position.toIntOrNull() ?: 5,
                        frequency     = frequency.toIntOrNull() ?: 3,
                        label         = label.trim(),
                        bgColor       = bgColor.trim(),
                        cornerRadius  = cornerRadius.toIntOrNull() ?: 0,
                        paddingTop    = paddingTop.toIntOrNull() ?: 0,
                        paddingBottom = paddingBottom.toIntOrNull() ?: 0,
                    )
                    onSave(docId, config)
                },
                enabled = label.isNotBlank() && selectedScreens.isNotEmpty(),
                colors  = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = Color.Black),
            ) { Text("Kaydet", fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("İptal", color = Muted) }
        },
    )
}

@Composable
private fun AdConfigCard(
    title      : String,
    docId      : String,
    config     : com.heftreng.app.data.model.CmsAdConfig?,
    onSaved    : () -> Unit,
    onDeleted  : (() -> Unit)? = null,
    extraLabel : String = "Pozisyon (kaçıncı kartta)",
    extraKey   : String = "position",
) {
    val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()

    var unitId   by remember(config) { mutableStateOf(config?.unitId   ?: "") }
    var enabled  by remember(config) { mutableStateOf(config?.enabled  ?: false) }
    var testMode by remember(config) { mutableStateOf(config?.testMode ?: true) }
    var extra    by remember(config) {
        mutableStateOf(when (extraKey) {
            "position"  -> (config?.position  ?: 5).toString()
            "frequency" -> (config?.frequency ?: 3).toString()
            "xpReward"  -> (config?.xpReward  ?: 50).toString()
            else        -> "5"
        })
    }
    var saving by remember { mutableStateOf(false) }

    // Boyut seçici state
    var bannerSize by remember(config) { mutableStateOf(config?.bannerSize ?: "adaptive") }

    CmsCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = Amber, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.weight(1f))
            if (onDeleted != null) {
                IconButton(onClick = onDeleted, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.DeleteOutline, null, tint = Error, modifier = Modifier.size(18.dp))
                }
            }
        }
        Spacer(Modifier.height(8.dp))

        cmsField(unitId, { unitId = it }, "Unit ID")
        Spacer(Modifier.height(6.dp))
        cmsField(extra, { extra = it.filter(Char::isDigit) }, extraLabel,
            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
        Spacer(Modifier.height(6.dp))

        // Banner boyutu seçici
        if (config?.adType != "interstitial" && config?.adType != "rewarded") {
            Text("Banner Boyutu", color = Muted, fontSize = 11.sp)
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf("adaptive" to "Adaptif", "banner" to "Küçük",
                       "medium_rectangle" to "Orta", "large_banner" to "Büyük").forEach { (v, l) ->
                    FilterChip(
                        selected = bannerSize == v,
                        onClick  = { bannerSize = v },
                        label    = { Text(l, fontSize = 10.sp) },
                        colors   = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Primary.copy(alpha = 0.2f),
                            selectedLabelColor     = Primary,
                        ),
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
        }
        Spacer(Modifier.height(6.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment     = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Text("Aktif", color = OnBackground, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Switch(
                    checked = enabled, onCheckedChange = { enabled = it },
                    colors  = SwitchDefaults.colors(
                        checkedThumbColor = Success, checkedTrackColor = Success.copy(alpha = 0.3f),
                    ),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Text("Test", color = OnBackground, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Switch(
                    checked = testMode, onCheckedChange = { testMode = it },
                    colors  = SwitchDefaults.colors(
                        checkedThumbColor = Amber, checkedTrackColor = Amber.copy(alpha = 0.3f),
                    ),
                )
            }
        }

        if (testMode) {
            Text(
                "Test ID kullanılıyor — gerçek gelir sayılmaz",
                color    = Amber,
                fontSize = 11.sp,
            )
        }

        Spacer(Modifier.height(8.dp))
        Button(
            onClick = {
                saving = true
                val extraInt = extra.toIntOrNull() ?: 5
                // ÖNEMLİ: set() merge olmadan tüm dokümanın üzerine yazar.
                // adType/screens gibi bu kartta düzenlenmeyen alanları korumak için
                // mevcut config'ten devralıyoruz — aksi halde her "Kaydet" basışında
                // bu alanlar sessizce silinir ve doküman default değerlere döner.
                val data = mutableMapOf<String, Any>(
                    "unitId"     to unitId.trim(),
                    "enabled"    to enabled,
                    "testMode"   to testMode,
                    "bannerSize" to bannerSize,
                    "adType"     to (config?.adType ?: "banner"),
                    "screens"    to (config?.screens ?: "feed"),
                    "placement"  to (config?.placement ?: "in_list"),
                    "label"      to (config?.label ?: ""),
                    extraKey      to extraInt,
                )
                firestore.collection("cms_ads").document(docId)
                    .set(data)
                    .addOnSuccessListener { saving = false; onSaved() }
                    .addOnFailureListener { saving = false }
            },
            enabled  = !saving,
            modifier = Modifier.fillMaxWidth(),
            shape    = RoundedCornerShape(10.dp),
            colors   = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = Color.Black),
        ) {
            if (saving) {
                CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
            }
            Text("Kaydet", fontWeight = FontWeight.Bold)
        }
    }
}

// ── 7. Bekleyen Yazılar ───────────────────────────────────────────────────────
@Composable
private fun PendingPostsTab(
    posts   : List<PendingPost>,
    stats   : YazarViewModel.PendingStats,
    loading : Boolean,
    vm      : YazarViewModel,
) {
    var filter      by remember { mutableStateOf("all") }
    var expandedId  by remember { mutableStateOf<String?>(null) }
    var noteInput   by remember { mutableStateOf("") }
    var actionPostId by remember { mutableStateOf<String?>(null) }
    var actionType   by remember { mutableStateOf("") } // "approve" | "reject"

    val filtered = when (filter) {
        "pending"  -> posts.filter { it.status == "pending" }
        "approved" -> posts.filter { it.status == "approved" }
        "rejected" -> posts.filter { it.status == "rejected" }
        else       -> posts
    }

    LazyColumn(
        modifier       = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // İstatistik satırı
        item {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatChip("⏳ ${stats.pending}",  "Bekliyor",  filter == "pending",  Amber)   { filter = if (filter == "pending")  "all" else "pending" }
                StatChip("✅ ${stats.approved}", "Onaylanan", filter == "approved", Success) { filter = if (filter == "approved") "all" else "approved" }
                StatChip("❌ ${stats.rejected}", "Reddedilen",filter == "rejected", Error)   { filter = if (filter == "rejected") "all" else "rejected" }
            }
        }

        if (loading) {
            item {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Amber, modifier = Modifier.size(28.dp))
                }
            }
        }

        if (!loading && filtered.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text("Yazı yok", color = Muted, fontSize = 14.sp)
                }
            }
        }

        items(filtered, key = { it.id }) { post ->
            val isExpanded = expandedId == post.id
            val statusColor = when (post.status) {
                "approved" -> Success
                "rejected" -> Error
                else       -> Amber
            }
            val statusLabel = when (post.status) {
                "approved" -> "✅ Onaylandı"
                "rejected" -> "❌ Reddedildi"
                else       -> "⏳ Bekliyor"
            }

            CmsCard {
                // Başlık + status
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expandedId = if (isExpanded) null else post.id },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            post.title,
                            color      = OnBackground,
                            fontWeight = FontWeight.SemiBold,
                            fontSize   = 14.sp,
                            maxLines   = 2,
                            overflow   = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(2.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(post.authorName, color = Muted, fontSize = 11.sp)
                            Text("·", color = Muted, fontSize = 11.sp)
                            Text(post.category, color = Muted, fontSize = 11.sp)
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Box(
                        Modifier
                            .background(statusColor.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(statusLabel, color = statusColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                    Icon(
                        if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        null, tint = Muted, modifier = Modifier.size(20.dp),
                    )
                }

                // Genişletilmiş içerik
                if (isExpanded) {
                    Spacer(Modifier.height(10.dp))
                    HorizontalDivider(color = Divider)
                    Spacer(Modifier.height(10.dp))

                    // Özet / İçerik
                    if (post.summary.isNotBlank()) {
                        Text("Özet", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                        Text(post.summary, color = OnSurface, fontSize = 13.sp, lineHeight = 19.sp)
                        Spacer(Modifier.height(8.dp))
                    }
                    if (post.content.isNotBlank()) {
                        Text("İçerik (ilk 400 karakter)", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                        Text(
                            post.content.take(400) + if (post.content.length > 400) "…" else "",
                            color    = OnSurface, fontSize = 13.sp, lineHeight = 19.sp,
                        )
                        Spacer(Modifier.height(8.dp))
                    }

                    // Admin notu
                    if (post.adminNote.isNotBlank()) {
                        Surface(
                            color  = SurfaceVar,
                            shape  = RoundedCornerShape(8.dp),
                        ) {
                            Text(
                                "Not: ${post.adminNote}",
                                color    = Muted,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(8.dp),
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                    }

                    // Not girişi
                    OutlinedTextField(
                        value         = if (actionPostId == post.id) noteInput else "",
                        onValueChange = { noteInput = it; actionPostId = post.id },
                        placeholder   = { Text("Admin notu (opsiyonel)", color = Muted, fontSize = 12.sp) },
                        modifier      = Modifier.fillMaxWidth(),
                        shape         = RoundedCornerShape(8.dp),
                        colors        = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor   = Amber, unfocusedBorderColor = Divider,
                            focusedTextColor     = OnBackground, unfocusedTextColor = OnBackground,
                            focusedContainerColor = SurfaceVar, unfocusedContainerColor = SurfaceVar,
                        ),
                        singleLine = true,
                    )
                    Spacer(Modifier.height(10.dp))

                    // Onayla / Reddet butonları
                    if (post.status != "approved") {
                        Button(
                            onClick = {
                                vm.approvePost(post.id, if (actionPostId == post.id) noteInput else "")
                                noteInput = ""; actionPostId = null; expandedId = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape    = RoundedCornerShape(8.dp),
                            colors   = ButtonDefaults.buttonColors(containerColor = Success, contentColor = Color.Black),
                        ) {
                            Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Onayla", fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                    if (post.status != "rejected") {
                        OutlinedButton(
                            onClick = {
                                vm.rejectPost(post.id, if (actionPostId == post.id) noteInput else "")
                                noteInput = ""; actionPostId = null; expandedId = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape    = RoundedCornerShape(8.dp),
                            border   = androidx.compose.foundation.BorderStroke(1.dp, Error),
                        ) {
                            Icon(Icons.Default.Close, null, tint = Error, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Reddet", color = Error, fontWeight = FontWeight.Bold)
                        }
                    }
                    if (post.status != "pending") {
                        Spacer(Modifier.height(6.dp))
                        TextButton(
                            onClick = {
                                vm.updatePostStatus(post.id, "pending", "")
                                expandedId = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Tekrar Bekleyene Al", color = Muted, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatChip(
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
@Composable
private fun RewardedConfigCard(
    config    : com.heftreng.app.data.model.CmsAdConfig?,
    firestore : com.google.firebase.firestore.FirebaseFirestore,
    scope     : kotlinx.coroutines.CoroutineScope,
    onSaved   : () -> Unit,
) {
    val cfg = config
    var unitId      by remember(cfg) { mutableStateOf(cfg?.unitId   ?: "") }
    var enabled     by remember(cfg) { mutableStateOf(cfg?.enabled  ?: false) }
    var testMode    by remember(cfg) { mutableStateOf(cfg?.testMode ?: true) }
    var xpReward    by remember(cfg) { mutableStateOf((cfg?.xpReward   ?: 50).toString()) }
    var dailyLimit  by remember(cfg) { mutableStateOf((cfg?.dailyLimit ?: 3).toString()) }
    var scnDoubleXp by remember(cfg) { mutableStateOf(cfg?.scenarioDoubleXp     ?: true) }
    var scnUnlock   by remember(cfg) { mutableStateOf(cfg?.scenarioUnlockLesson ?: true) }
    var scnStreak   by remember(cfg) { mutableStateOf(cfg?.scenarioSaveStreak   ?: true) }
    var saving      by remember { mutableStateOf(false) }

    CmsCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Rewarded — Ödüllü Reklam", color = Amber, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(if (enabled) "Aktif" else "Kapalı", color = if (enabled) Success else Muted, fontSize = 11.sp)
            }
            Switch(checked = enabled, onCheckedChange = { enabled = it },
                colors = SwitchDefaults.colors(checkedThumbColor = Success, checkedTrackColor = Success.copy(alpha = 0.3f)))
        }
        Spacer(Modifier.height(10.dp))
        cmsField(unitId, { unitId = it }, "Unit ID")
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Test Modu", color = OnBackground, fontSize = 13.sp, modifier = Modifier.weight(1f))
            Switch(checked = testMode, onCheckedChange = { testMode = it },
                colors = SwitchDefaults.colors(checkedThumbColor = Amber, checkedTrackColor = Amber.copy(alpha = 0.3f)))
        }
        if (testMode) Text("Test ID kullanılıyor", color = Amber, fontSize = 11.sp)
        Spacer(Modifier.height(10.dp))
        HorizontalDivider(color = com.heftreng.app.ui.theme.Divider, thickness = 0.5.dp)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                cmsField(xpReward, { xpReward = it.filter(Char::isDigit) }, "XP Ödülü",
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
            }
            Column(Modifier.weight(1f)) {
                cmsField(dailyLimit, { dailyLimit = it.filter(Char::isDigit) }, "Günlük Limit",
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
            }
        }
        Spacer(Modifier.height(10.dp))
        HorizontalDivider(color = com.heftreng.app.ui.theme.Divider, thickness = 0.5.dp)
        Spacer(Modifier.height(10.dp))
        Text("Senaryolar", color = OnBackground, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        Spacer(Modifier.height(6.dp))
        listOf(
            Triple("⚡ Çift XP", "Ders bittikten sonra XP'yi 2 katla", scnDoubleXp) to { v: Boolean -> scnDoubleXp = v },
            Triple("🔓 Kilitli Ders Aç", "Kilitli derse tıklayınca reklam izleyerek aç", scnUnlock) to { v: Boolean -> scnUnlock = v },
            Triple("🔥 Streak Kurtarma", "Seri bozulunca reklam izleyerek kurtar", scnStreak) to { v: Boolean -> scnStreak = v },
        ).forEach { (triple, setter) ->
            val (label, desc, checked) = triple
            Row(verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                    .background(if (checked) Success.copy(alpha = 0.08f) else SurfaceVar)
                    .padding(12.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(label, color = OnBackground, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Text(desc, color = Muted, fontSize = 11.sp)
                }
                Switch(checked = checked, onCheckedChange = setter,
                    colors = SwitchDefaults.colors(checkedThumbColor = Success, checkedTrackColor = Success.copy(alpha = 0.3f)))
            }
            Spacer(Modifier.height(6.dp))
        }
        Spacer(Modifier.height(6.dp))
        Button(
            onClick = {
                saving = true
                scope.launch {
                    try {
                        firestore.collection("cms_ads").document("rewarded_xp").set(mapOf(
                            "unitId" to unitId.trim(), "enabled" to enabled, "testMode" to testMode,
                            "xpReward" to (xpReward.toIntOrNull() ?: 50),
                            "dailyLimit" to (dailyLimit.toIntOrNull() ?: 3),
                            "scenarioDoubleXp" to scnDoubleXp,
                            "scenarioUnlockLesson" to scnUnlock,
                            "scenarioSaveStreak" to scnStreak,
                        )).await()
                        onSaved()
                    } catch (e: Exception) { e.printStackTrace() }
                    finally { saving = false }
                }
            },
            enabled = !saving, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp),
            colors  = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = Color.Black),
        ) {
            if (saving) { CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(14.dp), strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)) }
            Text("Kaydet", fontWeight = FontWeight.Bold)
        }
    }
}

