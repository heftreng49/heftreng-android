package com.heftreng.app.ui.screens.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
    val tabs = listOf("Sayfalar", "Bannerlar", "Duyurular", "Kategoriler", "Reklamlar", "Özellikler", "Yazılar")

    LaunchedEffect(Unit) {
        vm.loadPages()
        vm.loadBanners()
        vm.loadAnnouncements()
        vm.loadCategories()
        adsVm.loadAdConfigs()
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
    val adsEnabled         by adsVm.adsEnabled.collectAsState()
    val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
    val scope     = androidx.compose.runtime.rememberCoroutineScope()

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
                            color    = if (adsEnabled) Success else Muted,
                            fontSize = 12.sp,
                        )
                    }
                    Switch(
                        checked         = adsEnabled,
                        onCheckedChange = { enabled ->
                            scope.launch {
                                try {
                                    firestore.collection("cms_ads").document("global")
                                        .set(mapOf("enabled" to enabled))
                                        .await()
                                    adsVm.loadAdConfigs()
                                } catch (e: Exception) { e.printStackTrace() }
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor  = Success,
                            checkedTrackColor  = Success.copy(alpha = 0.3f),
                            uncheckedThumbColor = Muted,
                            uncheckedTrackColor = Muted.copy(alpha = 0.2f),
                        ),
                    )
                }
            }
        }

        // ── Banner Feed ────────────────────────────────────────────────────
        item {
            AdConfigCard(
                title      = "Banner — Feed",
                docId      = "banner_feed",
                config     = bannerConfig,
                extraLabel = "Pozisyon (kaçıncı kartta)",
                extraKey   = "position",
                onSaved    = { adsVm.loadAdConfigs() },
            )
        }

        // ── Interstitial Serial ────────────────────────────────────────────
        item {
            AdConfigCard(
                title      = "Interstitial — Seri Okuma",
                docId      = "interstitial_serial",
                config     = interstitialConfig,
                extraLabel = "Sıklık (kaç chapter'da bir)",
                extraKey   = "frequency",
                onSaved    = { adsVm.loadAdConfigs() },
            )
        }

        // ── Rewarded XP ────────────────────────────────────────────────────
        item {
            AdConfigCard(
                title      = "Rewarded — XP Ödülü",
                docId      = "rewarded_xp",
                config     = rewardedConfig,
                extraLabel = "XP ödülü",
                extraKey   = "xpReward",
                onSaved    = { adsVm.loadAdConfigs() },
            )
        }
    }
}

@Composable
private fun AdConfigCard(
    title      : String,
    docId      : String,
    config     : com.heftreng.app.data.model.CmsAdConfig?,
    extraLabel : String,
    extraKey   : String,
    onSaved    : () -> Unit,
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

    CmsCard {
        Text(title, color = Amber, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Spacer(Modifier.height(8.dp))

        cmsField(unitId, { unitId = it }, "Unit ID")
        Spacer(Modifier.height(6.dp))
        cmsField(extra, { extra = it.filter(Char::isDigit) }, extraLabel,
            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
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
                val data = mutableMapOf<String, Any>(
                    "unitId"   to unitId.trim(),
                    "enabled"  to enabled,
                    "testMode" to testMode,
                    extraKey   to extraInt,
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
