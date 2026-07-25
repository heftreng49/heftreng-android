package com.heftreng.app.ui.screens.yazar

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.heftreng.app.ui.component.WysiwygEditor
import com.heftreng.app.ui.i18n.Strings
import com.heftreng.app.ui.theme.*
import com.heftreng.app.viewmodel.PendingPost
import com.heftreng.app.viewmodel.SettingsViewModel
import com.heftreng.app.viewmodel.YazarViewModel
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.heightIn

// ═══════════════════════════════════════════════════════════════════════════════
// YAZAR PANELİ — Blog yazısı gönderme
// Sekmeler: Yaz | Yazılarım
// ═══════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalMaterialApi::class)
@Composable
fun YazarScreen(
    navController: NavController,
    vm: YazarViewModel = hiltViewModel(),
    settingsVm: SettingsViewModel = hiltViewModel(),
) {
    val loading      by vm.loading.collectAsState()
    val myPosts      by vm.myPosts.collectAsState()
    val submitResult by vm.submitResult.collectAsState()
    val updateResult by vm.updateResult.collectAsState()
    var selectedTab  by remember { mutableIntStateOf(0) }
    var editingPost  by remember { mutableStateOf<PendingPost?>(null) }

    val language by settingsVm.language.collectAsState()

    // Giriş kontrolü
    if (!vm.isLoggedIn) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Default.Lock, null, tint = Muted, modifier = Modifier.size(48.dp))
                Text(Strings.loginToWrite(language), color = Muted)
                Button(
                    onClick = { navController.popBackStack() },
                    colors  = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = Color.Black),
                    shape   = RoundedCornerShape(10.dp),
                ) { Text(Strings.back(language)) }
            }
        }
        return
    }

    // Submit sonuç snackbar
    val snackState = remember { SnackbarHostState() }
    LaunchedEffect(submitResult) {
        submitResult?.let { result ->
            when (result) {
                is YazarViewModel.SubmitResult.Success -> {
                    snackState.showSnackbar(Strings.submitSuccess(language))
                    selectedTab = 1
                }
                is YazarViewModel.SubmitResult.Error ->
                    snackState.showSnackbar("✗ ${result.message}")
            }
            vm.clearResult()
        }
    }

    LaunchedEffect(updateResult) {
        updateResult?.let { result ->
            when (result) {
                is YazarViewModel.SubmitResult.Success -> {
                    snackState.showSnackbar("✓ Yazı güncellendi, tekrar incelemeye gönderildi")
                    editingPost = null
                }
                is YazarViewModel.SubmitResult.Error ->
                    snackState.showSnackbar("✗ ${result.message}")
            }
            vm.clearUpdateResult()
        }
    }

    LaunchedEffect(Unit) { vm.loadMyPosts() }

    // ── Yazı düzenleme diyaloğu ───────────────────────────────────────────────
    editingPost?.let { post ->
        EditPostDialog(
            post     = post,
            vm       = vm,
            loading  = loading,
            language = language,
            onDismiss = { editingPost = null },
        )
    }



    var isRefreshing by remember { mutableStateOf(false) }
    val pullRefreshState = rememberPullRefreshState(
        refreshing = isRefreshing,
        onRefresh  = {
            isRefreshing = true
            vm.loadMyPosts()
        }
    )
    LaunchedEffect(isRefreshing) { if (isRefreshing) isRefreshing = false }
    Scaffold(
        containerColor = Background,
        snackbarHost = {
            SnackbarHost(snackState) { data ->
                Snackbar(
                    snackbarData   = data,
                    containerColor = if (data.visuals.message.startsWith("✓")) Color(0xFF22C55E) else Color(0xFFEF4444),
                    contentColor   = Color.White,
                    shape          = RoundedCornerShape(12.dp),
                )
            }
        },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Nivîskar", fontWeight = FontWeight.ExtraBold, color = Amber, fontSize = 18.sp)
                        Text("Yazı Gönder", color = Muted, fontSize = 11.sp)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = OnBackground)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Background),
            )
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().pullRefresh(pullRefreshState)) {
        Column(Modifier.fillMaxSize().padding(pad)) {

            // ── Sekmeler ───────────────────────────────────────────────────────
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor   = Background,
                contentColor     = Amber,
                indicator        = { tabPositions ->
                    Box(
                        Modifier
                            .tabIndicatorOffset(tabPositions[selectedTab])
                            .height(2.dp)
                            .background(Amber)
                    )
                },
                divider = { HorizontalDivider(color = Divider, thickness = 0.5.dp) },
            ) {
                listOf("✍️  " + Strings.yazarWrite(language), "📄  " + Strings.yazarMyPosts(language)).forEachIndexed { i, title ->
                    Tab(
                        selected               = selectedTab == i,
                        onClick                = {
                            selectedTab = i
                            if (i == 1) vm.loadMyPosts()
                        },
                        text                   = { Text(title, fontSize = 13.sp) },
                        selectedContentColor   = Amber,
                        unselectedContentColor = Muted,
                    )
                }
            }

            when (selectedTab) {
                0 -> WriteTab(vm = vm, loading = loading, language = language)
                1 -> MyPostsTab(posts = myPosts, loading = loading, vm = vm, language = language, onEdit = { editingPost = it })
            }
        }
    
        PullRefreshIndicator(
            refreshing = isRefreshing,
            state      = pullRefreshState,
            modifier   = Modifier.align(Alignment.TopCenter),
        )
        } // pullRefresh Box
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// SEKME 1 — YAZ
// ═══════════════════════════════════════════════════════════════════════════════
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WriteTab(vm: YazarViewModel, loading: Boolean, language: String) {
    var title    by remember { mutableStateOf("") }
    var content  by remember { mutableStateOf("") }
    var summary  by remember { mutableStateOf("") }
    var cover    by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }
    var lang     by remember { mutableStateOf("tr") }
    var tags     by remember { mutableStateOf(listOf<String>()) }
    var tagInput by remember { mutableStateOf("") }
    var showCatDropdown by remember { mutableStateOf(false) }

    val filteredCats = remember(tagInput, category) {
        if (category.isBlank()) vm.categories
        else vm.categories.filter { it.contains(category, ignoreCase = true) }
    }

    LazyColumn(
        modifier            = Modifier.fillMaxSize(),
        contentPadding      = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Kurallar
        item {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Amber.copy(alpha = 0.08f),
                border = BorderStroke(1.dp, Amber.copy(alpha = 0.3f)),
            ) {
                Row(
                    Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(Icons.Default.Info, null, tint = Amber, modifier = Modifier.size(18.dp))
                    Text(
                        "Özgün ve Türkçe/Kürtçe içerik paylaşın. Admin onayı 1-48 saat sürebilir. Onaylanan yazı blogda sizin adınıza eklenir.",
                        color = Amber,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                    )
                }
            }
        }

        // Başlık
        item {
            YazarField(
                value    = title,
                onChange = { if (it.length <= 150) title = it },
                label    = Strings.titleLabel(language) + " *",
                counter  = "${title.length}/150",
            )
        }

        // Kategori + Dil
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                // Kategori dropdown
                Box(Modifier.weight(1f)) {
                    YazarField(
                        value    = category,
                        onChange = { category = it; showCatDropdown = it.isNotEmpty() },
                        label    = "Kategori *",
                        trailingIcon = {
                            IconButton(onClick = { showCatDropdown = !showCatDropdown }) {
                                Icon(
                                    if (showCatDropdown) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    null, tint = Muted, modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    )
                    DropdownMenu(
                        expanded         = showCatDropdown && filteredCats.isNotEmpty(),
                        onDismissRequest = { showCatDropdown = false },
                        modifier         = Modifier.background(HeftSurface),
                    ) {
                        filteredCats.take(8).forEach { cat ->
                            DropdownMenuItem(
                                text    = { Text(cat, color = OnBackground, fontSize = 13.sp) },
                                onClick = { category = cat; showCatDropdown = false },
                            )
                        }
                    }
                }

                // Dil seçimi
                Column(Modifier.weight(0.8f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Dil", color = Muted, fontSize = 11.sp)
                    listOf("tr" to "Türkçe", "ku" to "Kurmancî", "both" to Strings.contentLangBoth(language)).forEach { (k, label) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier          = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { lang = k }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        ) {
                            RadioButton(
                                selected = lang == k,
                                onClick  = { lang = k },
                                colors   = RadioButtonDefaults.colors(selectedColor = Amber),
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(label, color = OnBackground, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // İçerik
        item {
            Text(Strings.contentLabel(language) + " *", color = Muted, fontSize = 12.sp)
            Spacer(Modifier.height(4.dp))
            WysiwygEditor(
                value    = content,
                onChange = { content = it },
                modifier = Modifier.fillMaxWidth(),
                minHeightDp = 300,
            )
            Text(
                "${content.length} karakter",
                color    = if (content.length < 100) Color(0xFFEF4444) else Muted,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 3.dp),
            )
        }

        // Özet
        item {
            YazarField(
                value    = summary,
                onChange = { if (it.length <= 280) summary = it },
                label    = Strings.summaryLabel(language),
                minLines = 2,
                counter  = "${summary.length}/280",
                hint     = "Okuyucuyu çekecek kısa bir açıklama...",
            )
        }

        // Kapak görseli
        item {
            YazarField(
                value    = cover,
                onChange = { cover = it },
                label    = "Kapak Görseli URL (opsiyonel)",
                hint     = "https://...",
                keyboardType = KeyboardType.Uri,
            )
        }

        // Etiketler
        item {
            Text("Etiketler (maks. 8)", color = Muted, fontSize = 12.sp)
            Spacer(Modifier.height(4.dp))
            OutlinedTextField(
                value         = tagInput,
                onValueChange = { tagInput = it },
                modifier      = Modifier.fillMaxWidth(),
                placeholder   = { Text("Etiket yaz, Enter ile ekle...", color = Muted, fontSize = 13.sp) },
                shape         = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                colors        = yazarFieldColors(),
                trailingIcon  = {
                    if (tagInput.isNotBlank() && tags.size < 8) {
                        IconButton(onClick = {
                            val v = tagInput.trim()
                            if (v.isNotBlank() && !tags.contains(v)) tags = tags + v
                            tagInput = ""
                        }) {
                            Icon(Icons.Default.Add, null, tint = Amber)
                        }
                    }
                },
            )
            if (tags.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    tags.forEach { tag ->
                        InputChip(
                            selected  = false,
                            onClick   = {},
                            label     = { Text(tag, fontSize = 12.sp) },
                            trailingIcon = {
                                Icon(
                                    Icons.Default.Close, null,
                                    tint     = Muted,
                                    modifier = Modifier.size(14.dp).clickable { tags = tags - tag },
                                )
                            },
                            colors = InputChipDefaults.inputChipColors(
                                containerColor = Amber.copy(alpha = 0.15f),
                                labelColor     = Amber,
                            ),
                            border = InputChipDefaults.inputChipBorder(
                                enabled          = true,
                                selected         = false,
                                borderColor      = Amber.copy(alpha = 0.3f),
                                selectedBorderColor = Amber,
                            ),
                        )
                    }
                }
            }
        }

        // Gönder butonu
        item {
            Spacer(Modifier.height(4.dp))
            Button(
                onClick  = {
                    vm.submitPost(title, content, summary, cover, category, lang, tags)
                },
                enabled  = !loading && title.isNotBlank() && content.length >= 100 && category.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(12.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = Color.Black),
                contentPadding = PaddingValues(vertical = 14.dp),
            ) {
                if (loading) {
                    CircularProgressIndicator(
                        color       = Color.Black,
                        modifier    = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(Strings.sending(language), fontWeight = FontWeight.Bold)
                } else {
                    Icon(Icons.Default.Send, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(Strings.yazarSubmit(language), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// SEKME 2 — YAZILARIM
// ═══════════════════════════════════════════════════════════════════════════════
@Composable
private fun MyPostsTab(
    posts   : List<PendingPost>,
    loading : Boolean,
    vm      : YazarViewModel,
    language: String,
    onEdit  : (PendingPost) -> Unit,
) {
    when {
        loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Amber)
        }
        posts.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Default.EditNote, null, tint = Muted, modifier = Modifier.size(48.dp))
                Text(Strings.noSubmissions(language), color = Muted)
            }
        }
        else -> LazyColumn(
            modifier            = Modifier.fillMaxSize(),
            contentPadding      = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(posts, key = { it.id }) { post ->
                PendingPostCard(post = post, onWithdraw = { vm.withdrawPost(post.id) }, onEdit = onEdit, language = language)
            }
        }
    }
}

@Composable
private fun PendingPostCard(
    post      : PendingPost,
    onWithdraw: () -> Unit,
    onEdit    : (PendingPost) -> Unit,
    language  : String,
) {
    val (statusColor, statusLabel, statusIcon) = when (post.status) {
        "approved" -> Triple(Color(0xFF22C55E), Strings.yazarApproved(language), Icons.Default.CheckCircle)
        "rejected" -> Triple(Color(0xFFEF4444), Strings.yazarRejected(language), Icons.Default.Cancel)
        else       -> Triple(Color(0xFFFBBF24), Strings.yazarPending(language), Icons.Default.HourglassBottom)
    }

    Surface(
        shape    = RoundedCornerShape(14.dp),
        color    = HeftSurface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp)) {
            // Durum + kategori
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier              = Modifier.fillMaxWidth(),
            ) {
                Surface(
                    shape = RoundedCornerShape(99.dp),
                    color = statusColor.copy(alpha = 0.15f),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier          = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(statusIcon, null, tint = statusColor, modifier = Modifier.size(12.dp))
                        Text(statusLabel, color = statusColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
                if (post.category.isNotBlank()) {
                    Text(post.category, color = Muted, fontSize = 11.sp)
                }
            }

            Spacer(Modifier.height(10.dp))

            // Başlık
            Text(
                post.title,
                color      = OnBackground,
                fontWeight = FontWeight.Bold,
                fontSize   = 15.sp,
                maxLines   = 2,
                overflow   = TextOverflow.Ellipsis,
            )

            // Özet
            if (post.summary.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    post.summary,
                    color    = Muted,
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 17.sp,
                )
            }

            // Admin notu (red/onay)
            if (post.adminNote.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = statusColor.copy(alpha = 0.08f),
                    border = BorderStroke(0.5.dp, statusColor.copy(alpha = 0.3f)),
                ) {
                    Row(
                        Modifier.padding(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(Icons.Default.AdminPanelSettings, null, tint = statusColor, modifier = Modifier.size(14.dp))
                        Text(
                            "Admin: ${post.adminNote}",
                            color    = statusColor,
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                        )
                    }
                }
            }

            // Blogger linki (onaylandıysa)
            if (post.status == "approved" && post.bloggerPostUrl.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "🔗 ${post.bloggerPostUrl}",
                    color    = Primary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // Etiketler
            if (post.tags.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    post.tags.take(4).forEach { tag ->
                        Surface(shape = RoundedCornerShape(99.dp), color = SurfaceVar) {
                            Text(
                                "#$tag",
                                color    = Muted,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
            }

            // Düzenle + Geri çek butonları (sadece pending/rejected iken)
            if (post.status == "pending" || post.status == "rejected") {
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Düzenle butonu
                    OutlinedButton(
                        onClick  = { onEdit(post) },
                        shape    = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(vertical = 6.dp),
                    ) {
                        Icon(Icons.Default.Edit, null, tint = Amber, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Düzenle", color = Amber, fontSize = 12.sp)
                    }
                    // Geri çek butonu (sadece pending iken)
                    if (post.status == "pending") {
                        OutlinedButton(
                            onClick  = onWithdraw,
                            shape    = RoundedCornerShape(8.dp),
                            border   = BorderStroke(1.dp, Error),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(vertical = 6.dp),
                        ) {
                            Icon(Icons.Default.Undo, null, tint = Error, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(Strings.yazarWithdraw(language), color = Error, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

// ── Yardımcı bileşenler ───────────────────────────────────────────────────────
@Composable
private fun EditPostDialog(
    post     : PendingPost,
    vm       : YazarViewModel,
    loading  : Boolean,
    language : String,
    onDismiss: () -> Unit,
) {
    var title    by remember(post.id) { mutableStateOf(post.title) }
    var content  by remember(post.id) { mutableStateOf(post.content) }
    var summary  by remember(post.id) { mutableStateOf(post.summary) }
    var cover    by remember(post.id) { mutableStateOf(post.cover) }
    var category by remember(post.id) { mutableStateOf(post.category) }
    var tags     by remember(post.id) { mutableStateOf(post.tags) }
    var tagInput by remember { mutableStateOf("") }
    var lang     by remember(post.id) { mutableStateOf(post.lang) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = HeftSurface,
        title = {
            Text(
                "Yazıyı Düzenle",
                fontWeight = FontWeight.Bold,
                color      = OnBackground,
                fontSize   = 16.sp,
            )
        },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                item {
                    OutlinedTextField(
                        value         = title,
                        onValueChange = { if (it.length <= 150) title = it },
                        label         = { Text("Başlık *") },
                        modifier      = Modifier.fillMaxWidth(),
                        shape         = RoundedCornerShape(10.dp),
                        colors        = yazarFieldColors(),
                        singleLine    = true,
                    )
                }
                item {
                    Text("İçerik *", color = Muted, fontSize = 12.sp)
                    Spacer(Modifier.height(4.dp))
                    WysiwygEditor(
                        value    = content,
                        onChange = { content = it },
                        modifier = Modifier.fillMaxWidth(),
                        minHeightDp = 250,
                    )
                    Text(
                        "${content.length} karakter",
                        color    = if (content.length < 100) Color(0xFFEF4444) else Muted,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                item {
                    OutlinedTextField(
                        value         = summary,
                        onValueChange = { if (it.length <= 280) summary = it },
                        label         = { Text("Özet") },
                        modifier      = Modifier.fillMaxWidth(),
                        shape         = RoundedCornerShape(10.dp),
                        colors        = yazarFieldColors(),
                        minLines      = 2,
                    )
                }
                item {
                    OutlinedTextField(
                        value         = cover,
                        onValueChange = { cover = it },
                        label         = { Text("Kapak Görseli URL") },
                        modifier      = Modifier.fillMaxWidth(),
                        shape         = RoundedCornerShape(10.dp),
                        colors        = yazarFieldColors(),
                        singleLine    = true,
                    )
                }
                item {
                    OutlinedTextField(
                        value         = category,
                        onValueChange = { category = it },
                        label         = { Text("Kategori *") },
                        modifier      = Modifier.fillMaxWidth(),
                        shape         = RoundedCornerShape(10.dp),
                        colors        = yazarFieldColors(),
                        singleLine    = true,
                    )
                }
                item {
                    Text("Dil", color = Muted, fontSize = 11.sp)
                    listOf("tr" to "Türkçe", "ku" to "Kurmancî", "both" to "Her ikisi").forEach { (k, label) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier          = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { lang = k }
                                .padding(horizontal = 4.dp, vertical = 2.dp),
                        ) {
                            RadioButton(
                                selected = lang == k,
                                onClick  = { lang = k },
                                colors   = RadioButtonDefaults.colors(selectedColor = Amber),
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(label, color = OnBackground, fontSize = 12.sp)
                        }
                    }
                }
                item {
                    OutlinedTextField(
                        value         = tagInput,
                        onValueChange = { tagInput = it },
                        label         = { Text("Etiket ekle") },
                        modifier      = Modifier.fillMaxWidth(),
                        shape         = RoundedCornerShape(10.dp),
                        colors        = yazarFieldColors(),
                        singleLine    = true,
                        trailingIcon  = {
                            if (tagInput.isNotBlank() && tags.size < 8) {
                                IconButton(onClick = {
                                    val v = tagInput.trim()
                                    if (v.isNotBlank() && !tags.contains(v)) tags = tags + v
                                    tagInput = ""
                                }) { Icon(Icons.Default.Add, null, tint = Amber) }
                            }
                        },
                    )
                    if (tags.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            tags.forEach { tag ->
                                Surface(shape = RoundedCornerShape(99.dp), color = Amber.copy(alpha = 0.15f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(start = 8.dp, end = 4.dp, top = 3.dp, bottom = 3.dp),
                                    ) {
                                        Text("#$tag", color = Amber, fontSize = 11.sp)
                                        Spacer(Modifier.width(4.dp))
                                        Icon(
                                            Icons.Default.Close, null,
                                            tint     = Muted,
                                            modifier = Modifier.size(12.dp).clickable { tags = tags - tag },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                item {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Amber.copy(alpha = 0.07f),
                        border = BorderStroke(0.5.dp, Amber.copy(alpha = 0.25f)),
                    ) {
                        Text(
                            "ℹ️  Düzenleme sonrası yazı tekrar admin incelemesine gönderilir.",
                            color    = Amber,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(10.dp),
                            lineHeight = 16.sp,
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick  = {
                    vm.updateMyPost(post.id, title, content, summary, cover, category, lang, tags)
                },
                enabled  = !loading && title.isNotBlank() && content.length >= 100 && category.isNotBlank(),
                colors   = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = Color.Black),
                shape    = RoundedCornerShape(8.dp),
            ) {
                if (loading) {
                    CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(6.dp))
                }
                Text("Güncelle", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("İptal", color = Muted)
            }
        },
    )
}

// ── Yardımcı bileşenler ───────────────────────────────────────────────────────
@Composable
private fun YazarField(
    value       : String,
    onChange    : (String) -> Unit,
    label       : String,
    hint        : String        = "",
    counter     : String?       = null,
    minLines    : Int           = 1,
    keyboardType: KeyboardType  = KeyboardType.Text,
    trailingIcon: @Composable (() -> Unit)? = null,
) {
    Column {
        OutlinedTextField(
            value         = value,
            onValueChange = onChange,
            label         = { Text(label) },
            placeholder   = if (hint.isNotBlank()) {{ Text(hint, color = Muted, fontSize = 13.sp) }} else null,
            modifier      = Modifier.fillMaxWidth(),
            shape         = RoundedCornerShape(12.dp),
            minLines      = minLines,
            trailingIcon  = trailingIcon,
            keyboardOptions = KeyboardOptions(
                keyboardType = keyboardType,
                imeAction    = if (minLines > 1) ImeAction.Default else ImeAction.Next,
            ),
            colors = yazarFieldColors(),
        )
        if (counter != null) {
            Text(counter, color = Muted, fontSize = 10.sp, modifier = Modifier.padding(top = 2.dp, start = 4.dp))
        }
    }
}

@Composable
private fun yazarFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor      = Amber,
    unfocusedBorderColor    = Divider,
    focusedTextColor        = OnBackground,
    unfocusedTextColor      = OnBackground,
    unfocusedContainerColor = SurfaceVar,
    focusedContainerColor   = SurfaceVar,
    focusedLabelColor       = Amber,
    unfocusedLabelColor     = Muted,
    cursorColor             = Amber,
)
