package com.heftreng.app.ui.screens.admin


import androidx.compose.foundation.background

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.heftreng.app.viewmodel.ALL_PERMISSIONS
import com.heftreng.app.viewmodel.StaffPermissions
import com.heftreng.app.viewmodel.LibraryViewModel
import androidx.navigation.NavController
import com.heftreng.app.ui.theme.*
import com.heftreng.app.viewmodel.AdminViewModel
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen(
    navController: NavController,
    vm           : AdminViewModel = hiltViewModel(),
    libraryVm    : LibraryViewModel = hiltViewModel(),
) {
    val isAdmin     by vm.isAdmin.collectAsState()
    val perms       by vm.perms.collectAsState()  // null = yükleniyor
    val staffList   by vm.staffList.collectAsState()
    val users           by vm.users.collectAsState()
    val unverifiedUsers by vm.unverifiedUsers.collectAsState()
    val unverifiedLoading by vm.unverifiedLoading.collectAsState()
    val pendingPosts by vm.pendingPosts.collectAsState()
    val loading     by vm.loading.collectAsState()
    val pushResult  by vm.pushResult.collectAsState()
    val stats       by vm.stats.collectAsState()
    val activeUsers  by vm.activeUsers.collectAsState()
    val statsLoading by vm.statsLoading.collectAsState()

    var pushTitle      by remember { mutableStateOf("") }
    var pushBody       by remember { mutableStateOf("") }
    var pushUrl        by remember { mutableStateOf("") }
    var pushUid        by remember { mutableStateOf("") }
    var pushPostId     by remember { mutableStateOf("") }
    var pushPostSearch by remember { mutableStateOf("") }
    var pushTarget     by remember { mutableStateOf("all") } // "all" | "uid" | "topic"
    var pushTopic      by remember { mutableStateOf("all_users") }
    var pushEmoji      by remember { mutableStateOf("") }
    var pushImageUrl   by remember { mutableStateOf("") }
    var pushExpanded   by remember { mutableStateOf(false) }
    var notifTitle  by remember { mutableStateOf("") }
    var notifBody   by remember { mutableStateOf("") }
    var notifType   by remember { mutableStateOf("sys") }
    var userSearch  by remember { mutableStateOf("") }

    // Düzenle tab state
    val feedPosts   by vm.feedPosts.collectAsState()
    val editResult  by vm.editResult.collectAsState()
    var editUser    by remember { mutableStateOf<com.heftreng.app.data.model.User?>(null) }
    var editName    by remember { mutableStateOf("") }
    var editPhoto   by remember { mutableStateOf("") }
    var deleteUserConfirm by remember { mutableStateOf<String?>(null) }
    var deletePostConfirm by remember { mutableStateOf<String?>(null) }
    var postSearch  by remember { mutableStateOf("") }

    // Şikayetler
    val reports     by vm.reports.collectAsState()
    val appeals     by vm.appeals.collectAsState()

    data class AdminTab(val title: String, val key: String)
    val allTabs = listOf(
        AdminTab("Push",         "push"),
        AdminTab("Bildirim",     "notif"),
        AdminTab("Kullanıcılar", "users"),
        AdminTab("Bekleyenler",  "pending"),
        AdminTab("Şikayetler",   "reports"),
        AdminTab("İtirazlar",    "appeals"),
        AdminTab("İstatistik",   "stats"),
        AdminTab("Düzenle",      "edit"),
        AdminTab("Kütüphane",    "library"),
        AdminTab("Kürtçe Admin", "kurdi"),
        AdminTab("Yardımcılar",  "staff"),
    )
    val tabs = allTabs.filter { tab -> perms?.can(tab.key) == true }
    var selectedTabKey by remember { mutableStateOf(tabs.firstOrNull()?.key ?: "push") }

    val platformStats by vm.platformStats.collectAsState()

    // perms yüklenince veriyi çek — checkAdmin async olduğu için bekle
    LaunchedEffect(Unit) {
        vm.checkAdmin()
    }

    // perms yüklenince UI'ı güncelle (veri AdminViewModel.init'te otomatik yüklendi)
    LaunchedEffect(perms) {
        if (perms == null) return@LaunchedEffect
        // Veri zaten AdminViewModel init bloğunda yükleniyor
        // Burada sadece stats listener'ı başlat (UI state gerektirir)
        if (perms?.can("stats") == true) vm.startStatsListener()
    }

    // editResult bildirimi
    LaunchedEffect(editResult) {
        if (editResult.isNotBlank()) {
            kotlinx.coroutines.delay(3000)
            vm.clearEditResult()
        }
    }

    // perms null = Firebase henüz cevap vermedi
    if (perms == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Amber)
        }
        return
    }

    if (!isAdmin) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Block, null, tint = Error, modifier = Modifier.size(48.dp))
                Spacer(Modifier.height(12.dp))
                Text("Erişim Yok / Destûr Tune", color = Error, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        }
        return
    }

    Scaffold(
        modifier = Modifier.imePadding(),
        containerColor = Background,
        topBar = {
            TopAppBar(
                title = { androidx.compose.foundation.layout.Column {
                    Text("Admin Paneli", fontWeight = FontWeight.Bold, color = OnBackground, fontSize = 16.sp)
                    if (!perms?.title.isNullOrBlank()) Text(perms?.title ?: "", color = Amber, fontSize = 11.sp)
                } },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = OnBackground)
                    }
                },
                actions = {
                    IconButton(onClick = { navController.navigate("cms") }) {
                        Icon(Icons.Default.Tune, contentDescription = "CMS", tint = Amber)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Background),
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {

            ScrollableTabRow(
                selectedTabIndex = tabs.indexOfFirst { it.key == selectedTabKey }.coerceAtLeast(0),
                containerColor   = Background,
                contentColor     = Amber,
                edgePadding      = 0.dp,
                indicator        = { tabPositions ->
                    val idx = tabs.indexOfFirst { it.key == selectedTabKey }.coerceAtLeast(0)
                    Box(Modifier.tabIndicatorOffset(tabPositions[idx]).height(2.dp).background(Amber))
                },
            ) {
                tabs.forEachIndexed { _, tab ->
                    Tab(
                        selected               = selectedTabKey == tab.key,
                        onClick                = { selectedTabKey = tab.key },
                        text                   = { Text(tab.title, fontSize = 12.sp) },
                        selectedContentColor   = Amber,
                        unselectedContentColor = Muted,
                    )
                }
            }

            when (selectedTabKey) {

                "push" -> LazyColumn(
                    modifier       = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        // ── Başlık ────────────────────────────────────────────
                        Text("Push Bildirimi", color = Amber, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Spacer(Modifier.height(12.dp))

                        // ── Hedef seçimi ──────────────────────────────────────
                        Text("Hedef", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("all" to "Herkes", "uid" to "Kullanıcı", "topic" to "Konu").forEach { (k, v) ->
                                val selected = pushTarget == k
                                FilterChip(
                                    selected = selected,
                                    onClick  = { pushTarget = k },
                                    label    = { Text(v, fontSize = 12.sp) },
                                    colors   = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Amber,
                                        selectedLabelColor     = Color.Black,
                                    )
                                )
                            }
                        }
                        if (pushTarget == "uid") {
                            Spacer(Modifier.height(8.dp))
                            adminTextField(pushUid, { pushUid = it }, "Hedef UID *")
                        }
                        if (pushTarget == "topic") {
                            Spacer(Modifier.height(8.dp))
                            adminTextField(pushTopic, { pushTopic = it }, "Topic (örn: all_users)")
                        }

                        Spacer(Modifier.height(14.dp))
                        Divider(color = Color.White.copy(alpha = 0.08f))
                        Spacer(Modifier.height(14.dp))

                        // ── İçerik ────────────────────────────────────────────
                        Text("İçerik", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(6.dp))

                        // Emoji + Başlık yan yana
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            adminTextField(pushEmoji, { pushEmoji = it }, "📣", modifier = Modifier.width(64.dp))
                            adminTextField(
                                pushTitle, { pushTitle = it }, "Başlık *",
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        adminTextField(pushBody, { pushBody = it }, "Mesaj içeriği *", minLines = 3)

                        Spacer(Modifier.height(14.dp))
                        Divider(color = Color.White.copy(alpha = 0.08f))
                        Spacer(Modifier.height(14.dp))

                        // ── Gönderi ekle ──────────────────────────────────────
                        Text("Gönderi Ekle (opsiyonel)", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(6.dp))
                        adminTextField(pushPostId, { pushPostId = it }, "Post ID (Firestore doc id)")
                        Spacer(Modifier.height(6.dp))
                        if (pushPostId.isNotBlank()) {
                            Surface(
                                color  = Color.White.copy(alpha = 0.05f),
                                shape  = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Article, null, tint = Amber, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Post ID: $pushPostId", fontSize = 12.sp, color = Color.White.copy(alpha = 0.8f))
                                    Spacer(Modifier.weight(1f))
                                    IconButton(onClick = { pushPostId = "" }, modifier = Modifier.size(20.dp)) {
                                        Icon(Icons.Default.Close, null, tint = Error, modifier = Modifier.size(14.dp))
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(14.dp))
                        Divider(color = Color.White.copy(alpha = 0.08f))
                        Spacer(Modifier.height(14.dp))

                        // ── Ekstra (opsiyonel) ────────────────────────────────
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { pushExpanded = !pushExpanded }
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment      = Alignment.CenterVertically,
                        ) {
                            Text("Ekstra Ayarlar", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
                            Icon(
                                if (pushExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                null, tint = Color.White.copy(alpha = 0.4f), modifier = Modifier.size(18.dp)
                            )
                        }
                        if (pushExpanded) {
                            Spacer(Modifier.height(8.dp))
                            adminTextField(pushUrl, { pushUrl = it }, "Deep Link URL (opsiyonel)")
                            Spacer(Modifier.height(8.dp))
                            adminTextField(pushImageUrl, { pushImageUrl = it }, "Görsel URL (opsiyonel)")
                        }

                        Spacer(Modifier.height(16.dp))

                        // ── Önizleme kartı ────────────────────────────────────
                        if (pushTitle.isNotBlank() || pushBody.isNotBlank()) {
                            Text("Önizleme", color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
                            Spacer(Modifier.height(6.dp))
                            Surface(
                                color  = Color(0xFF1E1E2E),
                                shape  = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth(),
                                shadowElevation = 4.dp,
                            ) {
                                Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                    // Uygulama ikonu alanı
                                    Surface(
                                        color = Amber.copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.size(42.dp),
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(pushEmoji.ifBlank { "📣" }, fontSize = 22.sp)
                                        }
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            buildString {
                                                if (pushEmoji.isNotBlank()) append("$pushEmoji ")
                                                append(pushTitle.ifBlank { "Başlık" })
                                            },
                                            color      = Color.White,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize   = 13.sp,
                                            maxLines   = 1,
                                        )
                                        Spacer(Modifier.height(2.dp))
                                        Text(
                                            pushBody.ifBlank { "Mesaj içeriği" },
                                            color   = Color.White.copy(alpha = 0.65f),
                                            fontSize = 12.sp,
                                            maxLines = 2,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                        )
                                        if (pushPostId.isNotBlank()) {
                                            Spacer(Modifier.height(4.dp))
                                            Text(
                                                "📎 Gönderi eklendi",
                                                color    = Amber.copy(alpha = 0.8f),
                                                fontSize = 11.sp,
                                            )
                                        }
                                    }
                                    Text(
                                        "şimdi",
                                        color    = Color.White.copy(alpha = 0.35f),
                                        fontSize = 10.sp,
                                    )
                                }
                            }
                            Spacer(Modifier.height(16.dp))
                        }

                        // ── Gönder butonu ─────────────────────────────────────
                        Button(
                            onClick = {
                                val finalTitle = buildString {
                                    if (pushEmoji.isNotBlank()) append("$pushEmoji ")
                                    append(pushTitle)
                                }
                                val targetUid = when (pushTarget) {
                                    "uid"   -> pushUid
                                    "topic" -> ""
                                    else    -> ""
                                }
                                vm.sendPush(
                                    title    = finalTitle,
                                    body     = pushBody,
                                    url      = pushUrl,
                                    targetUid = targetUid,
                                    postId   = pushPostId,
                                    topic    = if (pushTarget == "topic") pushTopic else if (pushTarget == "all") "all_users" else "",
                                    imageUrl = pushImageUrl,
                                )
                            },
                            enabled  = pushTitle.isNotBlank() && pushBody.isNotBlank() &&
                                       (pushTarget != "uid" || pushUid.isNotBlank()),
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            shape    = RoundedCornerShape(12.dp),
                            colors   = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = Color.Black),
                        ) {
                            Icon(Icons.Default.Send, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Push Gönder", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }

                        if (pushResult.isNotBlank()) {
                            Spacer(Modifier.height(10.dp))
                            Surface(
                                color = if (pushResult.startsWith("✓")) Success.copy(alpha = 0.12f) else Error.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    pushResult,
                                    color    = if (pushResult.startsWith("✓")) Success else Error,
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(10.dp),
                                )
                            }
                        }
                    }
                }

                // ── Sistem Bildirimi ─────────────────────────────────────────────
                "notif" -> LazyColumn(
                    modifier       = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item {
                        Text("Sistem Bildirimi Gönder", color = Amber, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Spacer(Modifier.height(8.dp))
                        adminTextField(notifTitle, { notifTitle = it }, "Başlık *")
                        Spacer(Modifier.height(8.dp))
                        adminTextField(notifBody, { notifBody = it }, "İçerik", minLines = 2)
                        Spacer(Modifier.height(8.dp))

                        // Tür seçimi
                        Text("Tür", color = Muted, fontSize = 12.sp)
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("sys" to "Sistem", "like" to "Beğeni", "cmt" to "Yorum").forEach { (key, label) ->
                                FilterChip(
                                    selected = notifType == key,
                                    onClick  = { notifType = key },
                                    label    = { Text(label, fontSize = 11.sp) },
                                    colors   = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Amber.copy(alpha = 0.2f),
                                        selectedLabelColor     = Amber,
                                    ),
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick  = { vm.sendSystemNotif(notifTitle, notifBody, notifType) },
                            enabled  = notifTitle.isNotBlank(),
                            modifier = Modifier.fillMaxWidth(),
                            shape    = RoundedCornerShape(10.dp),
                            colors   = ButtonDefaults.buttonColors(containerColor = Primary, contentColor = Color.White),
                        ) {
                            Icon(Icons.Default.Notifications, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Bildirim Gönder", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // ── Kullanıcılar ─────────────────────────────────────────────────
                "users" -> {
                    var showUnverified by remember { mutableStateOf(false) }

                    Column(Modifier.fillMaxSize()) {
                        // Alt sekme: Tümü / Doğrulanmamış
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            listOf(false to "Tümü", true to "Doğrulanmamış").forEach { (isUnverified, label) ->
                                val selected = showUnverified == isUnverified
                                OutlinedButton(
                                    onClick = {
                                        showUnverified = isUnverified
                                        if (isUnverified) vm.loadUnverifiedUsers()
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape    = RoundedCornerShape(10.dp),
                                    colors   = ButtonDefaults.outlinedButtonColors(
                                        containerColor = if (selected) Amber else Color.Transparent,
                                        contentColor   = if (selected) Color.Black else Muted,
                                    ),
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp, if (selected) Amber else Divider,
                                    ),
                                ) {
                                    Text(
                                        if (isUnverified && unverifiedUsers.isNotEmpty())
                                            "$label (${unverifiedUsers.size})"
                                        else label,
                                        fontSize = 13.sp,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                    )
                                }
                            }
                        }

                        if (!showUnverified) {
                            // ── Tüm kullanıcılar ──────────────────────────────
                            OutlinedTextField(
                                value         = userSearch,
                                onValueChange = { userSearch = it },
                                placeholder   = { Text("Kullanıcı ara…", color = Muted) },
                                singleLine    = true,
                                modifier      = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                                shape         = RoundedCornerShape(12.dp),
                                colors        = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor      = Amber,
                                    unfocusedBorderColor    = Divider,
                                    focusedTextColor        = OnBackground,
                                    unfocusedTextColor      = OnBackground,
                                    unfocusedContainerColor = SurfaceVar,
                                    focusedContainerColor   = SurfaceVar,
                                ),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                leadingIcon = { Icon(Icons.Default.Search, null, tint = Muted) },
                            )
                            val filtered = users.filter {
                                userSearch.isBlank() ||
                                it.displayName.contains(userSearch, ignoreCase = true) ||
                                it.email.contains(userSearch, ignoreCase = true)
                            }
                            LazyColumn(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
                                items(filtered, key = { it.uid }) { user ->
                                    AdminUserRow(
                                        user        = user,
                                        onToggleBan = { vm.toggleBan(user.uid, !user.banned) },
                                        onVerify    = if (!user.emailVerified) {{ vm.verifyUser(user.uid) }} else null,
                                    )
                                    HorizontalDivider(color = Divider, thickness = 0.5.dp)
                                }
                            }
                        } else {
                            // ── Doğrulanmamış kullanıcılar ────────────────────
                            if (unverifiedLoading) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(color = Amber)
                                }
                            } else if (unverifiedUsers.isEmpty()) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF22C55E),
                                            modifier = Modifier.size(44.dp))
                                        Text("Tüm hesaplar doğrulanmış", color = Muted, fontSize = 14.sp)
                                    }
                                }
                            } else {
                                LazyColumn(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
                                    items(unverifiedUsers, key = { it.uid }) { user ->
                                        AdminUserRow(
                                            user        = user,
                                            onToggleBan = { vm.toggleBan(user.uid, !user.banned) },
                                            onVerify    = { vm.verifyUser(user.uid) },
                                        )
                                        HorizontalDivider(color = Divider, thickness = 0.5.dp)
                                    }
                                }
                            }
                        }
                    }
                }

                // ── Bekleyen Gönderiler ─────────────────────────────────────────
                "pending" -> {
                    if (loading) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Amber)
                        }
                    } else if (pendingPosts.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.CheckCircle, null, tint = Success, modifier = Modifier.size(44.dp))
                                Spacer(Modifier.height(8.dp))
                                Text("Bekleyen gönderi yok", color = Muted)
                            }
                        }
                    } else {
                        LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(pendingPosts, key = { it["id"] as? String ?: "" }) { post ->
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = HeftSurface,
                                ) {
                                    Column(Modifier.padding(12.dp)) {
                                        Text(post["text"] as? String ?: "", color = OnBackground, fontSize = 13.sp, maxLines = 3)
                                        Spacer(Modifier.height(4.dp))
                                        Text("Yazar: ${post["authorName"] as? String ?: "?"}", color = Muted, fontSize = 11.sp)
                                        Spacer(Modifier.height(8.dp))
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Button(
                                                onClick = { vm.approvePost(post["id"] as? String ?: "") },
                                                shape   = RoundedCornerShape(8.dp),
                                                colors  = ButtonDefaults.buttonColors(containerColor = Success, contentColor = Color.Black),
                                                modifier = Modifier.weight(1f),
                                                contentPadding = PaddingValues(vertical = 6.dp),
                                            ) { Text("Onayla", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                                            OutlinedButton(
                                                onClick = { vm.rejectPost(post["id"] as? String ?: "") },
                                                shape   = RoundedCornerShape(8.dp),
                                                border  = androidx.compose.foundation.BorderStroke(1.dp, Error),
                                                modifier = Modifier.weight(1f),
                                                contentPadding = PaddingValues(vertical = 6.dp),
                                            ) { Text("Reddet", fontSize = 12.sp, color = Error) }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // ── Şikayetler ─────────────────────────────────────────────────
                "reports" -> LazyColumn(
                    modifier       = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item {
                        Text("Şikayetler (${reports.size})", color = Amber, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                    if (reports.isEmpty()) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                Text("Bekleyen şikayet yok", color = Muted)
                            }
                        }
                    }
                    items(reports, key = { it["id"] as? String ?: "" }) { report ->
                        val reportId     = report["id"] as? String ?: ""
                        val targetName   = report["targetName"] as? String ?: ""
                        val reporterName = report["reporterName"] as? String ?: ""
                        val reason       = report["reason"] as? String ?: ""
                        val targetPostId = report["targetPostId"] as? String ?: ""
                        val status       = report["status"] as? String ?: "pending"
                        val statusColor  = when (status) {
                            "reviewed"  -> Color(0xFF22C55E)
                            "dismissed" -> Muted
                            else        -> Color(0xFFF59E0B)
                        }
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = HeftSurface,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Şikayet edilen: $targetName", color = OnBackground, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                    Surface(shape = RoundedCornerShape(6.dp), color = statusColor.copy(alpha = 0.15f)) {
                                        Text(
                                            when (status) { "reviewed" -> "İncelendi"; "dismissed" -> "Reddedildi"; else -> "Bekliyor" },
                                            color = statusColor, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                        )
                                    }
                                }
                                Text("Şikayetçi: $reporterName", color = Muted, fontSize = 12.sp)
                                Text("Sebep: $reason", color = OnBackground, fontSize = 12.sp)
                                if (targetPostId.isNotBlank()) {
                                    Text("Post ID: $targetPostId", color = Muted, fontSize = 11.sp)
                                }
                                // Moderasyon aksiyonları (gönderi şikayetiyse)
                                if (targetPostId.isNotBlank()) {
                                    var showModDialog by remember { mutableStateOf(false) }
                                    var modReason by remember { mutableStateOf("") }
                                    var modNote by remember { mutableStateOf("") }
                                    var selectedModStatus by remember { mutableStateOf("restricted") }

                                    if (showModDialog) {
                                        androidx.compose.material3.AlertDialog(
                                            onDismissRequest = { showModDialog = false },
                                            title = { Text("Gönderiyi Kısıtla", fontWeight = FontWeight.Bold) },
                                            text = {
                                                androidx.compose.foundation.layout.Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    listOf(
                                                        "restricted" to "Kısıtlı (sadece giriş yapanlar)",
                                                        "suspended"  to "Askıya al (sadece sahibi görür)",
                                                        "removed"    to "Kaldır (hiç kimse görmez)",
                                                    ).forEach { (s, label) ->
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth().clickable { selectedModStatus = s }.padding(vertical = 4.dp),
                                                            verticalAlignment = Alignment.CenterVertically,
                                                        ) {
                                                            androidx.compose.material3.RadioButton(selected = selectedModStatus == s, onClick = { selectedModStatus = s })
                                                            Spacer(Modifier.width(6.dp))
                                                            Text(label, fontSize = 13.sp, color = OnBackground)
                                                        }
                                                    }
                                                    Spacer(Modifier.height(4.dp))
                                                    androidx.compose.material3.OutlinedTextField(
                                                        value = modReason, onValueChange = { modReason = it },
                                                        label = { Text("Kullanıcıya gösterilecek sebep") },
                                                        modifier = Modifier.fillMaxWidth(), maxLines = 3,
                                                    )
                                                    androidx.compose.material3.OutlinedTextField(
                                                        value = modNote, onValueChange = { modNote = it },
                                                        label = { Text("Admin notu (gizli)") },
                                                        modifier = Modifier.fillMaxWidth(), maxLines = 2,
                                                    )
                                                }
                                            },
                                            confirmButton = {
                                                Button(onClick = {
                                                    vm.moderatePost(
                                                        postId     = targetPostId,
                                                        targetUid  = report["targetUid"] as? String ?: "",
                                                        targetName = targetName,
                                                        status     = selectedModStatus,
                                                        reason     = modReason,
                                                        adminNote  = modNote,
                                                    )
                                                    vm.updateReportStatus(reportId, "reviewed")
                                                    showModDialog = false
                                                }) { Text("Uygula") }
                                            },
                                            dismissButton = {
                                                OutlinedButton(onClick = { showModDialog = false }) { Text("İptal") }
                                            },
                                        )
                                    }

                                    if (status == "pending") {
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Button(
                                                onClick = { showModDialog = true },
                                                shape  = RoundedCornerShape(8.dp),
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444), contentColor = Color.White),
                                                modifier = Modifier.weight(1f),
                                                contentPadding = PaddingValues(vertical = 6.dp),
                                            ) { Text("Kısıtla", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                                            Button(
                                                onClick = { vm.updateReportStatus(reportId, "reviewed") },
                                                shape  = RoundedCornerShape(8.dp),
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22C55E), contentColor = Color.White),
                                                modifier = Modifier.weight(1f),
                                                contentPadding = PaddingValues(vertical = 6.dp),
                                            ) { Text("Sorunsuz", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                                            OutlinedButton(
                                                onClick = { vm.updateReportStatus(reportId, "dismissed") },
                                                shape   = RoundedCornerShape(8.dp),
                                                modifier = Modifier.weight(1f),
                                                contentPadding = PaddingValues(vertical = 6.dp),
                                            ) { Text("Yoksay", fontSize = 12.sp, color = Muted) }
                                        }
                                    }
                                } else if (status == "pending") {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(
                                            onClick = { vm.updateReportStatus(reportId, "reviewed") },
                                            shape   = RoundedCornerShape(8.dp),
                                            colors  = ButtonDefaults.buttonColors(containerColor = Color(0xFF22C55E), contentColor = Color.White),
                                            modifier = Modifier.weight(1f),
                                            contentPadding = PaddingValues(vertical = 6.dp),
                                        ) { Text("İncelendi", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                                        OutlinedButton(
                                            onClick = { vm.updateReportStatus(reportId, "dismissed") },
                                            shape   = RoundedCornerShape(8.dp),
                                            modifier = Modifier.weight(1f),
                                            contentPadding = PaddingValues(vertical = 6.dp),
                                        ) { Text("Reddet", fontSize = 12.sp, color = Muted) }
                                    }
                                }
                            }
                        }
                    }
                }

                // ── İtirazlar ──────────────────────────────────────────────
                "appeals" -> LazyColumn(
                    modifier       = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("İtirazlar (${appeals.size})", color = Amber, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Spacer(Modifier.weight(1f))
                            if (appeals.isEmpty()) {
                                Text("Bekleyen itiraz yok", color = Muted, fontSize = 12.sp)
                            }
                        }
                    }
                    items(appeals, key = { it.id }) { appeal ->
                        var adminNote by remember { mutableStateOf("") }
                        Surface(shape = RoundedCornerShape(12.dp), color = HeftSurface) {
                            androidx.compose.foundation.layout.Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Flag, null, tint = Amber, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(appeal.postOwnerName, color = OnBackground, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                    Spacer(Modifier.weight(1f))
                                    Surface(color = Color(0xFFF59E0B).copy(alpha = 0.15f), shape = RoundedCornerShape(4.dp)) {
                                        Text("${appeal.moderationStatus}", color = Color(0xFFF59E0B),
                                            fontSize = 10.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                    }
                                }
                                Text("Post ID: ${appeal.postId}", color = Muted, fontSize = 11.sp)
                                if (appeal.text.isNotBlank()) {
                                    Text("İtiraz metni: ${appeal.text}", color = OnBackground, fontSize = 12.sp)
                                }
                                androidx.compose.material3.OutlinedTextField(
                                    value = adminNote, onValueChange = { adminNote = it },
                                    label = { Text("Admin notu") },
                                    modifier = Modifier.fillMaxWidth(), maxLines = 2,
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = { vm.approveAppeal(appeal, adminNote) },
                                        shape  = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22C55E), contentColor = Color.White),
                                        modifier = Modifier.weight(1f),
                                        contentPadding = PaddingValues(vertical = 6.dp),
                                    ) { Text("Onayla", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                                    OutlinedButton(
                                        onClick = { vm.rejectAppeal(appeal, adminNote) },
                                        shape   = RoundedCornerShape(8.dp),
                                        modifier = Modifier.weight(1f),
                                        contentPadding = PaddingValues(vertical = 6.dp),
                                    ) { Text("Reddet", fontSize = 12.sp, color = Muted) }
                                }
                            }
                        }
                    }
                }

                // ── İstatistikler ──────────────────────────────────────────────
                "stats" -> {
                    val backfillRunning  by vm.backfillRunning.collectAsState()
                    val backfillProgress by vm.backfillProgress.collectAsState()
                    LazyColumn(
                    modifier            = Modifier.fillMaxSize(),
                    contentPadding      = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    // ── Sayaç Düzeltme ────────────────────────────────────────
                    item {
                        Surface(
                            shape    = RoundedCornerShape(16.dp),
                            color    = SurfaceVar,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Row(
                                    verticalAlignment     = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Icon(Icons.Default.Sync, null, tint = Amber,
                                        modifier = Modifier.size(20.dp))
                                    Text("Takip Sayaçlarını Düzelt",
                                        color = OnBackground, fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp)
                                }
                                Text(
                                    "Mevcut kullanıcıların followersCount / followingCount " +
                                    "değerleri eksik olabilir. Bu işlem tüm kullanıcıları " +
                                    "tarayıp gerçek sayıları hesaplar ve günceller.",
                                    color = Muted, fontSize = 12.sp, lineHeight = 17.sp,
                                )
                                if (backfillProgress.isNotBlank()) {
                                    val isOk  = backfillProgress.startsWith("✅")
                                    val isErr = backfillProgress.startsWith("❌")
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = when {
                                            isOk  -> androidx.compose.ui.graphics.Color(0xFF22C55E).copy(alpha = 0.12f)
                                            isErr -> Error.copy(alpha = 0.12f)
                                            else  -> Amber.copy(alpha = 0.10f)
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Text(
                                            backfillProgress,
                                            color = when {
                                                isOk  -> androidx.compose.ui.graphics.Color(0xFF22C55E)
                                                isErr -> Error
                                                else  -> Amber
                                            },
                                            fontSize = 12.sp, fontWeight = FontWeight.Medium,
                                            modifier = Modifier.padding(10.dp),
                                        )
                                    }
                                }
                                Button(
                                    onClick  = { vm.backfillFollowerCounts() },
                                    enabled  = !backfillRunning,
                                    shape    = RoundedCornerShape(10.dp),
                                    colors   = ButtonDefaults.buttonColors(
                                        containerColor = Amber, contentColor = androidx.compose.ui.graphics.Color.Black),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    if (backfillRunning) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            color = androidx.compose.ui.graphics.Color.Black,
                                            strokeWidth = 2.dp,
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text("İşleniyor…", fontWeight = FontWeight.Bold)
                                    } else {
                                        Icon(Icons.Default.Sync, null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Sayaçları Yenile", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                    item {
                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                            Column {
                                Text("Platform İstatistikleri", color = Amber,
                                    fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                if (platformStats.lastUpdated > 0L)
                                    Text("Güncellendi: ${formatLastSeen(platformStats.lastUpdated)}",
                                        color = Muted, fontSize = 11.sp)
                            }
                            if (statsLoading)
                                CircularProgressIndicator(Modifier.size(20.dp), color = Amber, strokeWidth = 2.dp)
                            else
                                IconButton(onClick = { vm.loadStats() }, modifier = Modifier.size(36.dp)) {
                                    Icon(Icons.Default.Refresh, null, tint = Amber, modifier = Modifier.size(20.dp))
                                }
                        }
                    }

                    // Platform ayrımı
                    item {
                        Text("Kullanıcı Platformları", color = OnBackground,
                            fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PlatformCard(Modifier.weight(1f), "🤖", "Android",
                                platformStats.androidUsers, platformStats.totalUsers, Color(0xFF16A34A))
                            PlatformCard(Modifier.weight(1f), "🌐", "Web",
                                platformStats.webUsers, platformStats.totalUsers, Color(0xFF2563EB))
                        }
                    }

                    // Canlı durum
                    item {
                        Text("Canlı Durum", color = OnBackground,
                            fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MiniStatCard(Modifier.weight(1f), "🟢 Şu An Online",
                                "${platformStats.onlineNow}", Color(0xFF16A34A))
                            MiniStatCard(Modifier.weight(1f), "👤 Toplam Üye",
                                "${platformStats.totalUsers}", Color(0xFF7C3AED))
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MiniStatCard(Modifier.weight(1f), "🆕 Bugün Kayıt",
                                "${platformStats.newUsersToday}", Color(0xFFD97706))
                            MiniStatCard(Modifier.weight(1f), "🚫 Banlı",
                                "${platformStats.bannedUsers}", Color(0xFF6B7280))
                        }
                    }

                    // İçerik
                    item {
                        Text("İçerik", color = OnBackground,
                            fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Spacer(Modifier.height(8.dp))
                        androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                            columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(2),
                            modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            userScrollEnabled = false,
                        ) {
                            val cells = listOf(
                                Triple("📝 Gönderi",      "${platformStats.totalPosts}",    Color(0xFF2563EB)),
                                Triple("📬 Bugün",        "${platformStats.newPostsToday}", Color(0xFF0891B2)),
                                Triple("💬 Alıntı",       "${platformStats.totalQuotes}",   Color(0xFF9333EA)),
                                Triple("⭐ İnceleme",     "${platformStats.totalReviews}",  Color(0xFFB45309)),
                                Triple("📚 Seri",         "${platformStats.totalSerials}",  Color(0xFF7C3AED)),
                                Triple("📖 Kitap",        "${platformStats.totalBooks}",    Color(0xFFB45309)),
                            )
                            items(cells.size) { i ->
                                val (label, value, color) = cells[i]
                                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = HeftSurface)) {
                                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(label, color = Muted, fontSize = 11.sp)
                                        Text(value, color = color, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                                    }
                                }
                            }
                        }
                    }

                    // Moderasyon
                    item {
                        Text("Moderasyon", color = OnBackground,
                            fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MiniStatCard(Modifier.weight(1f), "⏳ Bekleyen",
                                "${platformStats.pendingPosts}", Color(0xFFEA580C))
                            MiniStatCard(Modifier.weight(1f), "🚩 Şikayet",
                                "${platformStats.pendingReports}", Color(0xFFDC2626))
                        }
                    }

                    // Son aktif kullanıcılar
                    item {
                        Spacer(Modifier.height(4.dp))
                        Text("Son Aktif Kullanıcılar", color = Amber,
                            fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text("Son 7 gün • presence verisi", color = Muted, fontSize = 11.sp)
                    }

                    if (activeUsers.isEmpty() && !statsLoading) {
                        item { Text("Henüz veri yok", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(vertical = 8.dp)) }
                    }

                    items(activeUsers.size) { i ->
                        val u = activeUsers[i]
                        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = HeftSurface)) {
                            Row(Modifier.fillMaxWidth().padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Box(contentAlignment = Alignment.BottomEnd) {
                                    Box(Modifier.size(38.dp).clip(CircleShape).background(SurfaceVar),
                                        contentAlignment = Alignment.Center) {
                                        if (u.photoURL.isNotBlank())
                                            coil.compose.AsyncImage(u.photoURL, null, Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop)
                                        else
                                            Text(u.displayName.firstOrNull()?.uppercase() ?: "?",
                                                color = OnBackground, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    }
                                    Box(Modifier.size(10.dp).clip(CircleShape)
                                        .background(if (u.online) Color(0xFF16A34A) else Color(0xFF6B7280)))
                                }
                                Column(Modifier.weight(1f)) {
                                    Text(u.displayName, color = OnBackground,
                                        fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1)
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            if (u.online) "🟢 Online" else "⏱ ${formatLastSeen(u.lastSeenMs)}",
                                            color = if (u.online) Color(0xFF16A34A) else Muted, fontSize = 11.sp)
                                        Text(if (u.platform == "android") "🤖" else "🌐", fontSize = 11.sp)
                                        if (u.appVersion.isNotBlank() && u.appVersion != "—")
                                            Text("v${u.appVersion}", color = Muted, fontSize = 10.sp)
                                    }
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("Lv.${u.level}", color = Amber, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    Text("${u.postsCount} gönderi", color = Muted, fontSize = 10.sp)
                                }
                            }
                        }
                    }
                }
                } // stats LazyColumn
                // ── Düzenle ───────────────────────────────────────────────────
                "edit" -> {
                    LazyColumn(
                        modifier       = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        // Sonuç bildirimi
                        if (editResult.isNotBlank()) {
                            item {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (editResult.startsWith("✓")) Success.copy(0.15f) else Error.copy(0.15f),
                                ) {
                                    Text(
                                        editResult,
                                        color    = if (editResult.startsWith("✓")) Success else Error,
                                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                            }
                        }

                        // ── Kullanıcı Düzenle / Sil ──────────────────────────
                        item {
                            val searchResults  by vm.userSearchResults.collectAsState()
                            val searchLoading  by vm.userSearchLoading.collectAsState()

                            Text("Kullanıcı Düzenle / Sil", color = Amber, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Spacer(Modifier.height(8.dp))

                            OutlinedTextField(
                                value         = userSearch,
                                onValueChange = {
                                    userSearch = it
                                    vm.searchUsersAdmin(it)
                                },
                                placeholder   = { Text("İsim, email veya UID ara…", color = Muted, fontSize = 13.sp) },
                                singleLine    = true,
                                modifier      = Modifier.fillMaxWidth(),
                                shape         = RoundedCornerShape(10.dp),
                                colors        = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor   = Amber, unfocusedBorderColor = Divider,
                                    focusedTextColor     = OnBackground, unfocusedTextColor = OnBackground,
                                    unfocusedContainerColor = SurfaceVar, focusedContainerColor = SurfaceVar,
                                ),
                                leadingIcon = { Icon(Icons.Default.Search, null, tint = Muted) },
                                trailingIcon = if (searchLoading) {{ CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Amber, strokeWidth = 2.dp) }} else null,
                            )
                            Spacer(Modifier.height(8.dp))

                            // Arama varsa Firestore sonuçları, yoksa yerel ilk 200
                            val displayUsers = if (userSearch.isBlank()) {
                                users.sortedBy { it.displayName }
                            } else {
                                searchResults
                            }

                            if (displayUsers.isEmpty() && userSearch.isNotBlank() && !searchLoading) {
                                Text("Sonuç bulunamadı", color = Muted, fontSize = 12.sp,
                                    modifier = Modifier.padding(vertical = 8.dp))
                            }

                            displayUsers.forEach { user ->
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = HeftSurface,
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                ) {
                                    Column(Modifier.padding(12.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Column(Modifier.weight(1f)) {
                                                Text(user.displayName.ifBlank { "—" }, color = OnBackground, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                                Text(user.email, color = Muted, fontSize = 11.sp)
                                                Text(user.uid, color = Muted, fontSize = 9.sp)
                                                if (user.banned) Text("BANLANDI", color = Error, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                            }
                                            // Düzenle butonu
                                            IconButton(onClick = {
                                                editUser  = user
                                                editName  = user.displayName
                                                editPhoto = user.photoURL
                                            }) {
                                                Icon(Icons.Default.Edit, null, tint = Amber, modifier = Modifier.size(18.dp))
                                            }
                                            // Ban/Unban
                                            IconButton(onClick = { vm.toggleBan(user.uid, !user.banned) }) {
                                                Icon(
                                                    if (user.banned) Icons.Default.LockOpen else Icons.Default.Block,
                                                    null,
                                                    tint = if (user.banned) Success else Error,
                                                    modifier = Modifier.size(18.dp),
                                                )
                                            }
                                            // Sil
                                            IconButton(onClick = { deleteUserConfirm = user.uid }) {
                                                Icon(Icons.Default.DeleteForever, null, tint = Error, modifier = Modifier.size(18.dp))
                                            }
                                        }

                                        // Düzenleme formu
                                        if (editUser?.uid == user.uid) {
                                            Spacer(Modifier.height(8.dp))
                                            HorizontalDivider(color = Divider)
                                            Spacer(Modifier.height(8.dp))
                                            adminTextField(editName,  { editName  = it }, "Yeni isim")
                                            Spacer(Modifier.height(6.dp))
                                            adminTextField(editPhoto, { editPhoto = it }, "Yeni fotoğraf URL")
                                            Spacer(Modifier.height(8.dp))
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Button(
                                                    onClick  = { vm.updateUserProfile(user.uid, editName, editPhoto); editUser = null },
                                                    shape    = RoundedCornerShape(8.dp),
                                                    colors   = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = Color.Black),
                                                    modifier = Modifier.weight(1f),
                                                ) { Text("Kaydet", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                                                OutlinedButton(
                                                    onClick  = { editUser = null },
                                                    shape    = RoundedCornerShape(8.dp),
                                                    modifier = Modifier.weight(1f),
                                                ) { Text("İptal", fontSize = 12.sp, color = Muted) }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // ── Gönderi Sil ───────────────────────────────────────
                        item {
                            HorizontalDivider(color = Divider)
                            Spacer(Modifier.height(4.dp))
                            Text("Gönderi Sil", color = Error, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Spacer(Modifier.height(8.dp))

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value         = postSearch,
                                    onValueChange = { postSearch = it },
                                    placeholder   = { Text("İçerik veya UID ara…", color = Muted, fontSize = 13.sp) },
                                    singleLine    = true,
                                    modifier      = Modifier.weight(1f),
                                    shape         = RoundedCornerShape(10.dp),
                                    colors        = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Amber, unfocusedBorderColor = Divider,
                                        focusedTextColor = OnBackground, unfocusedTextColor = OnBackground,
                                        unfocusedContainerColor = SurfaceVar, focusedContainerColor = SurfaceVar,
                                    ),
                                )
                                IconButton(
                                    onClick  = { vm.loadFeedPosts(postSearch) },
                                    modifier = Modifier.background(Amber, RoundedCornerShape(10.dp)),
                                ) {
                                    Icon(Icons.Default.Search, null, tint = Color.Black)
                                }
                            }
                            Spacer(Modifier.height(8.dp))

                            feedPosts.forEach { post ->
                                Surface(
                                    shape    = RoundedCornerShape(10.dp),
                                    color    = HeftSurface,
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                ) {
                                    Row(
                                        modifier          = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.Top,
                                    ) {
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                (post["text"] as? String ?: "").take(100),
                                                color    = OnBackground,
                                                fontSize = 12.sp,
                                                maxLines = 2,
                                            )
                                            Spacer(Modifier.height(2.dp))
                                            Text("UID: ${post["uid"] as? String ?: "?"}", color = Muted, fontSize = 10.sp)
                                        }
                                        IconButton(onClick = { deletePostConfirm = post["id"] as? String }) {
                                            Icon(Icons.Default.Delete, null, tint = Error, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                // ─────────────────────────────────────────────────────────
                //  TAB 8 — KÜTÜPHANE: Yazar & Kitap Yönetimi
                // ─────────────────────────────────────────────────────────
                "library" -> {
                    AdminLibraryTab(libraryVm = libraryVm)
                }
                "kurdi" -> {
                    // KurdiAdminScreen NavHost üzerinden açılır
                    val navCtrl = navController
                    LaunchedEffect(Unit) { navCtrl.navigate("kurdi_admin") }
                }
                "staff" -> perms?.let { p -> StaffTab(vm = vm, perms = p, staffList = staffList, users = users) }
            }
        }
    }

    // Kullanıcı silme onay dialogu
    deleteUserConfirm?.let { uid ->
        AlertDialog(
            onDismissRequest = { deleteUserConfirm = null },
            containerColor   = HeftSurface,
            title  = { Text("Kullanıcıyı Sil", color = Error, fontWeight = FontWeight.SemiBold) },
            text   = { Text("Bu işlem geri alınamaz. Kullanıcı ve tüm gönderileri silinir.", color = Muted) },
            confirmButton = {
                TextButton(onClick = { vm.deleteUser(uid); deleteUserConfirm = null }) {
                    Text("Sil", color = Error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteUserConfirm = null }) { Text("İptal", color = Muted) }
            },
        )
    }

    // Gönderi silme onay dialogu
    deletePostConfirm?.let { postId ->
        AlertDialog(
            onDismissRequest = { deletePostConfirm = null },
            containerColor   = HeftSurface,
            title  = { Text("Gönderiyi Sil", color = Error, fontWeight = FontWeight.SemiBold) },
            text   = { Text("Bu gönderi kalıcı olarak silinecek.", color = Muted) },
            confirmButton = {
                TextButton(onClick = { vm.deletePost(postId); deletePostConfirm = null }) {
                    Text("Sil", color = Error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { deletePostConfirm = null }) { Text("İptal", color = Muted) }
            },
        )
    }
}
@Composable
private fun adminTextField(
    value   : String,
    onChange: (String) -> Unit,
    label   : String,
    minLines: Int = 1,
    modifier: Modifier = Modifier.fillMaxWidth(),
) {
    OutlinedTextField(
        value         = value,
        onValueChange = onChange,
        label         = { Text(label) },
        minLines      = minLines,
        modifier      = modifier,
        shape         = RoundedCornerShape(10.dp),
        colors        = OutlinedTextFieldDefaults.colors(
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

// ═══════════════════════════════════════════════════════════════════════════
//  ADMIN KÜTÜPHANESİ — Yazar & Kitap Düzenleme Tabı
//  AdminScreen içinde "Kütüphane" tabı seçildiğinde gösterilir.
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun AdminLibraryTab(libraryVm: LibraryViewModel) {
    val authors  by libraryVm.authors.collectAsState()
    val loading  by libraryVm.loading.collectAsState()
    val error    by libraryVm.error.collectAsState()
    val scope    = rememberCoroutineScope()

    var showNewAuthor by remember { mutableStateOf(false) }
    var editAuthor    by remember { mutableStateOf<com.heftreng.app.data.model.Author?>(null) }
    var editBook      by remember { mutableStateOf<com.heftreng.app.data.model.LibraryBook?>(null) }

    var migrateStatus  by remember { mutableStateOf("") }
    var migrateRunning by remember { mutableStateOf(false) }
    var counterStatus  by remember { mutableStateOf("") }
    var counterRunning by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { libraryVm.loadAuthors() }

    LazyColumn(
        modifier       = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // ── Başlık ────────────────────────────────────────────────────────
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                Column {
                    Text("Kütüphane Yönetimi", color = OnBackground,
                        fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("Yazar & Kitap sayfalarını düzenle", color = Muted, fontSize = 12.sp)
                }
                Button(
                    onClick = { showNewAuthor = true },
                    colors  = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = Color(0xFF1A1040)),
                    shape   = RoundedCornerShape(10.dp),
                ) {
                    Icon(Icons.Default.PersonAdd, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Yazar Ekle", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // ── Eski Alıntı Migrasyonu ────────────────────────────────────────
        item {
            Surface(shape = RoundedCornerShape(12.dp), color = HeftSurface) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Sync, null, tint = Primary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Eski Alıntıları Eşleştir", color = OnBackground,
                            fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Feed'deki mevcut alıntıları kitap/yazar sayfalarına otomatik bağlar. " +
                        "Yeni oluşturulan yazar ve kitap sayfaları da dahil edilir.",
                        color = Muted, fontSize = 12.sp, lineHeight = 18.sp,
                    )
                    if (migrateStatus.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(migrateStatus, color = Primary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick  = {
                            if (!migrateRunning) {
                                migrateRunning = true
                                migrateStatus  = "Başlatılıyor…"
                                libraryVm.migrateLegacyFeedQuotes { done, total ->
                                    migrateStatus = if (done >= total) {
                                        libraryVm.loadAuthors() // tamamlanınca yazar listesini yenile
                                        "✓ Tamamlandı ($total alıntı işlendi)"
                                    } else "İşleniyor… $done / $total"
                                    if (done >= total) migrateRunning = false
                                }
                            }
                        },
                        enabled  = !migrateRunning,
                        colors   = ButtonDefaults.buttonColors(containerColor = Primary),
                        shape    = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (migrateRunning) {
                            CircularProgressIndicator(color = Color.White,
                                modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(if (migrateRunning) "Çalışıyor…" else "Eşleştirmeyi Başlat",
                            fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // ── Sayaç Düzeltme ────────────────────────────────────────────────
        item {
            Surface(shape = RoundedCornerShape(12.dp), color = HeftSurface) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Refresh, null, tint = Amber, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Sayaçları Düzelt", color = OnBackground,
                            fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Yazar ve kitap alıntı/kitap sayaçlarını Firestore'daki gerçek veriye göre yeniden hesaplar.",
                        color = Muted, fontSize = 12.sp, lineHeight = 18.sp,
                    )
                    if (counterStatus.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(counterStatus, color = Amber, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = {
                            if (!counterRunning) {
                                counterRunning = true
                                counterStatus  = "Hesaplanıyor…"
                                libraryVm.rebuildCounters { msg ->
                                    counterStatus  = msg
                                    counterRunning = false
                                    libraryVm.loadAuthors()
                                }
                            }
                        },
                        enabled  = !counterRunning,
                        colors   = ButtonDefaults.buttonColors(containerColor = Amber),
                        shape    = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (counterRunning) {
                            CircularProgressIndicator(color = Color.White,
                                modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(if (counterRunning) "Hesaplanıyor…" else "Sayaçları Düzelt",
                            fontWeight = FontWeight.Bold, color = Color.Black)
                    }
                }
            }
        }

        // ── Yazar Listesi ─────────────────────────────────────────────────
        item {
            Text("Yazarlar (${authors.size})", color = OnBackground,
                fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        }

        if (loading && authors.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Amber, strokeWidth = 2.dp,
                        modifier = Modifier.size(28.dp))
                }
            }
        }

        items(authors, key = { it.id }) { author ->
            AdminAuthorCard(
                author       = author,
                onEditAuthor = { editAuthor = author },
                
                libraryVm    = libraryVm,
                onEditBook   = { editBook = it },
            )
        }

        if (authors.isEmpty() && !loading) {
            item {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text("Henüz yazar yok", color = Muted, fontSize = 13.sp)
                }
            }
        }
    }

    // ── Hata Toast ────────────────────────────────────────────────────────
    error?.let { msg ->
        LaunchedEffect(msg) {
            kotlinx.coroutines.delay(3000)
            libraryVm.clearError()
        }
        Box(
            modifier = Modifier.fillMaxSize().padding(bottom = 16.dp, start = 16.dp, end = 16.dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Surface(shape = RoundedCornerShape(10.dp), color = Error) {
                Text(msg, color = Color.White, fontSize = 13.sp,
                    modifier = Modifier.padding(12.dp))
            }
        }
    }

    // ── Yeni Yazar Dialog ─────────────────────────────────────────────────
    if (showNewAuthor) {
        AdminCreateAuthorDialog(
            onDismiss = { showNewAuthor = false },
            onCreate  = { name, bio, photo, year, nat ->
                libraryVm.createAuthor(name, bio, photo, year, nat)
                showNewAuthor = false
            },
        )
    }

    // ── Yazar Düzenleme Dialog ────────────────────────────────────────────
    editAuthor?.let { au ->
        AdminEditAuthorDialog(
            author    = au,
            onDismiss = { editAuthor = null },
            onSave    = { name, bio, photo, year, nat ->
                libraryVm.updateAuthor(au.id, name, bio, photo, year, nat)
                editAuthor = null
            },
        )
    }

    // ── Kitap Düzenleme Dialog ────────────────────────────────────────────
    editBook?.let { bk ->
        AdminEditBookDialog(
            book      = bk,
            onDismiss = { editBook = null },
            onSave    = { title, synopsis, genre, year, pages, cover ->
                libraryVm.updateLibraryBook(bk.id, title, synopsis, genre, year, pages, cover)
                editBook = null
            },
        )
    }
}

// ─── Yazar Kartı (admin görünümü) ───────────────────────────────────────────
@Composable
private fun AdminAuthorCard(
    author      : com.heftreng.app.data.model.Author,
    onEditAuthor: () -> Unit,

    libraryVm   : LibraryViewModel,
    onEditBook  : (com.heftreng.app.data.model.LibraryBook) -> Unit,
) {
    // Her kart kendi kitap listesini tutar — global authorBooks state'i kullanmıyoruz
    // çünkü o state tüm kartlar arasında paylaşılıyor ve son yüklenen yazarın
    // kitaplarını gösteriyor.
    var localBooks  by remember { mutableStateOf<List<com.heftreng.app.data.model.LibraryBook>>(emptyList()) }
    var booksLoaded by remember { mutableStateOf(false) }
    var expanded    by remember { mutableStateOf(false) }
    val scope       = rememberCoroutineScope()

    Surface(shape = RoundedCornerShape(12.dp), color = HeftSurface) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Avatar
                Box(
                    modifier = Modifier.size(44.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(SurfaceVar),
                    contentAlignment = Alignment.Center,
                ) {
                    if (author.photoURL.isNotBlank()) {
                        AsyncImage(model = author.photoURL, contentDescription = null,
                            contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    } else {
                        Text(
                            author.name.firstOrNull()?.uppercase() ?: "?",
                            color = Amber, fontWeight = FontWeight.Bold, fontSize = 16.sp,
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(author.name, color = OnBackground, fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp)
                    val meta = buildList {
                        if (author.nationality.isNotBlank()) add(author.nationality)
                        if (author.birthYear > 0) add("${author.birthYear}")
                    }.joinToString(" · ")
                    if (meta.isNotBlank()) Text(meta, color = Muted, fontSize = 11.sp)
                    Text(
                        "${author.bookCount} kitap  ·  ${author.quoteCount} alıntı  ·  ${author.reviewCount} inceleme",
                        color = Muted, fontSize = 11.sp,
                    )
                }
                // Düzenle butonu
                IconButton(onClick = onEditAuthor, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Edit, null, tint = Amber, modifier = Modifier.size(18.dp))
                }
                // Kitapları aç — her kart kendi kitaplarını yükler
                IconButton(
                    onClick = {
                        expanded = !expanded
                        if (expanded && !booksLoaded) {
                            booksLoaded = true
                            scope.launch {
                                localBooks = libraryVm.fetchBooksForAuthor(author.id)
                            }
                        }
                    },
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        null, tint = Muted, modifier = Modifier.size(18.dp),
                    )
                }
            }

            // Biyografi özeti
            if (author.bio.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(author.bio, color = OnSurface, fontSize = 12.sp, lineHeight = 17.sp, maxLines = 2,
                    overflow = TextOverflow.Ellipsis)
            }

            // Genişletilmiş kitap listesi
            if (expanded) {
                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = Divider)
                Spacer(Modifier.height(8.dp))
                Text("Kitapları", color = Primary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                if (localBooks.isEmpty()) {
                    Text("Bu yazara ait kitap yok.", color = Muted, fontSize = 12.sp)
                }
                localBooks.forEach { book ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.AutoStories, null, tint = Muted,
                            modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(book.title, color = OnBackground, fontSize = 13.sp,
                                fontWeight = FontWeight.Medium)
                            val bMeta = buildList {
                                if (book.genre.isNotBlank()) add(book.genre)
                                if (book.publishYear > 0) add("${book.publishYear}")
                                if (book.pageCount > 0) add("${book.pageCount} s.")
                            }.joinToString(" · ")
                            if (bMeta.isNotBlank()) Text(bMeta, color = Muted, fontSize = 11.sp)
                        }
                        IconButton(onClick = { onEditBook(book) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Edit, null, tint = Amber, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}

// ─── Yeni Yazar Dialog ──────────────────────────────────────────────────────
@Composable
private fun AdminCreateAuthorDialog(
    onDismiss: () -> Unit,
    onCreate : (name: String, bio: String, photo: String, birthYear: Int, nationality: String) -> Unit,
) {
    var name    by remember { mutableStateOf("") }
    var bio     by remember { mutableStateOf("") }
    var photo   by remember { mutableStateOf("") }
    var year    by remember { mutableStateOf("") }
    var nat     by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = HeftSurface,
        title = { Text("Yeni Yazar", color = OnBackground, fontWeight = FontWeight.Bold) },
        text  = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                adminTextField(name,  { name  = it }, "Yazar Adı *")
                adminTextField(nat,   { nat   = it }, "Milliyet")
                adminTextField(year,  { year  = it }, "Doğum Yılı")
                adminTextField(photo, { photo = it }, "Fotoğraf URL")
                adminTextField(bio,   { bio   = it }, "Biyografi", minLines = 3)
            }
        },
        confirmButton = {
            TextButton(
                onClick  = {
                    if (name.isNotBlank())
                        onCreate(name.trim(), bio.trim(), photo.trim(), year.toIntOrNull() ?: 0, nat.trim())
                },
                enabled = name.isNotBlank(),
            ) { Text("Oluştur", color = Amber, fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("İptal", color = Muted) }
        },
    )
}

// ─── Yazar Düzenleme Dialog ─────────────────────────────────────────────────
@Composable
private fun AdminEditAuthorDialog(
    author   : com.heftreng.app.data.model.Author,
    onDismiss: () -> Unit,
    onSave   : (name: String, bio: String, photo: String, birthYear: Int, nationality: String) -> Unit,
) {
    var name  by remember { mutableStateOf(author.name) }
    var bio   by remember { mutableStateOf(author.bio) }
    var photo by remember { mutableStateOf(author.photoURL) }
    var year  by remember { mutableStateOf(if (author.birthYear > 0) author.birthYear.toString() else "") }
    var nat   by remember { mutableStateOf(author.nationality) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = HeftSurface,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Edit, null, tint = Amber, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Yazarı Düzenle", color = OnBackground, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                adminTextField(name,  { name  = it }, "Ad *")
                adminTextField(nat,   { nat   = it }, "Milliyet")
                adminTextField(year,  { year  = it }, "Doğum Yılı")
                adminTextField(photo, { photo = it }, "Fotoğraf URL")
                adminTextField(bio,   { bio   = it }, "Biyografi", minLines = 4)
            }
        },
        confirmButton = {
            TextButton(
                onClick  = {
                    if (name.isNotBlank())
                        onSave(name.trim(), bio.trim(), photo.trim(), year.toIntOrNull() ?: 0, nat.trim())
                },
                enabled = name.isNotBlank(),
            ) { Text("Kaydet", color = Amber, fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("İptal", color = Muted) }
        },
    )
}

// ─── Kitap Düzenleme Dialog ─────────────────────────────────────────────────
@Composable
private fun AdminEditBookDialog(
    book     : com.heftreng.app.data.model.LibraryBook,
    onDismiss: () -> Unit,
    onSave   : (title: String, synopsis: String, genre: String, publishYear: Int, pageCount: Int, coverImg: String) -> Unit,
) {
    var title    by remember { mutableStateOf(book.title) }
    var synopsis by remember { mutableStateOf(book.synopsis) }
    var genre    by remember { mutableStateOf(book.genre) }
    var year     by remember { mutableStateOf(if (book.publishYear > 0) book.publishYear.toString() else "") }
    var pages    by remember { mutableStateOf(if (book.pageCount > 0) book.pageCount.toString() else "") }
    var cover    by remember { mutableStateOf(book.coverImg) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = HeftSurface,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AutoStories, null, tint = Amber, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Kitabı Düzenle", color = OnBackground, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Yazar bilgisi (salt okunur)
                Surface(shape = RoundedCornerShape(8.dp), color = SurfaceVar) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.Person, null, tint = Muted, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(book.authorName, color = Muted, fontSize = 12.sp)
                    }
                }
                adminTextField(title,    { title    = it }, "Kitap Adı *")
                adminTextField(genre,    { genre    = it }, "Tür (Roman, Şiir…)")
                adminTextField(year,     { year     = it }, "Basım Yılı")
                adminTextField(pages,    { pages    = it }, "Sayfa Sayısı")
                adminTextField(cover,    { cover    = it }, "Kapak Resmi URL")
                adminTextField(synopsis, { synopsis = it }, "Hakkında / Özet", minLines = 4)
            }
        },
        confirmButton = {
            TextButton(
                onClick  = {
                    if (title.isNotBlank())
                        onSave(
                            title.trim(), synopsis.trim(), genre.trim(),
                            year.toIntOrNull() ?: 0, pages.toIntOrNull() ?: 0, cover.trim(),
                        )
                },
                enabled = title.isNotBlank(),
            ) { Text("Kaydet", color = Amber, fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("İptal", color = Muted) }
        },
    )
}

// ── Son görülme formatlayıcı ──────────────────────────────────────────────────
// Stat değeri: null=yüklenmedi, -1=hata, >=0=değer
private fun Int?.toStatStr(): String = when {
    this == null -> "—"
    this == -1   -> "!"
    else         -> this.toString()
}

private fun formatLastSeen(lastSeenMs: Long): String {
    if (lastSeenMs == 0L) return "Bilinmiyor"
    val diffMs  = System.currentTimeMillis() - lastSeenMs
    val diffMin = diffMs / 60_000
    val diffHr  = diffMs / 3_600_000
    val diffDay = diffMs / 86_400_000
    return when {
        diffMin < 2   -> "Az önce"
        diffMin < 60  -> "${diffMin}dk önce"
        diffHr  < 24  -> "${diffHr}sa önce"
        else          -> "${diffDay}g önce"
    }
}

// ─── PlatformCard ─────────────────────────────────────────────────────────────

@Composable
private fun PlatformCard(
    modifier : Modifier,
    icon     : String,
    label    : String,
    count    : Int,
    total    : Int,
    color    : Color,
) {
    val pct = if (total > 0) (count * 100f / total) else 0f
    Card(modifier, shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = HeftSurface)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(icon, fontSize = 16.sp)
                Text(label, color = OnBackground, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            }
            Text("$count", color = color, fontWeight = FontWeight.Bold, fontSize = 26.sp)
            // Yüzde bar
            Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp))
                .background(SurfaceVar)) {
                Box(Modifier.fillMaxWidth(pct / 100f).fillMaxHeight()
                    .clip(RoundedCornerShape(2.dp)).background(color))
            }
            Text("%.0f%%".format(pct), color = Muted, fontSize = 10.sp)
        }
    }
}

// ─── MiniStatCard ────────────────────────────────────────────────────────────

@Composable
private fun MiniStatCard(modifier: Modifier, label: String, value: String, color: Color) {
    Card(modifier, shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = HeftSurface)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, color = Muted, fontSize = 11.sp)
            Text(value, color = color, fontWeight = FontWeight.Bold, fontSize = 22.sp)
        }
    }
}

// ══════════════════════════════════════════════════════════════════════
//  YARDIMCILAR SEKMESİ — Özelleştirilebilir izin sistemi
// ══════════════════════════════════════════════════════════════════════
@Composable
private fun StaffTab(
    vm        : AdminViewModel,
    perms     : StaffPermissions,
    staffList : List<AdminViewModel.StaffMember>,
    users     : List<com.heftreng.app.data.model.User>,
) {
    var uidInput     by remember { mutableStateOf("") }
    var titleInput   by remember { mutableStateOf("") }
    var newPerms     by remember { mutableStateOf(setOf<String>()) }
    var msg          by remember { mutableStateOf("") }
    var editingUid   by remember { mutableStateOf<String?>(null) }
    var editPerms    by remember { mutableStateOf(setOf<String>()) }
    var editTitle    by remember { mutableStateOf("") }

    // Renk paleti
    val permColors = mapOf(
        "push"    to Color(0xFFF59E0B),
        "notif"   to Color(0xFF60A5FA),
        "users"   to Color(0xFFF87171),
        "pending" to Color(0xFF34D399),
        "reports" to Color(0xFFFC8181),
        "appeals" to Color(0xFFEF4444),
        "stats"   to Color(0xFFA78BFA),
        "edit"    to Color(0xFF38BDF8),
        "library" to Color(0xFF4ADE80),
        "kurdi"   to Color(0xFFFBBF24),
        "staff"   to Color(0xFFE879F9),
    )

    LazyColumn(
        modifier            = Modifier.fillMaxSize(),
        contentPadding      = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {

        // ── Mevcut yardımcılar ─────────────────────────────────────────────
        item {
            Text(
                "Yardımcılar (${staffList.size})",
                color = OnBackground, fontWeight = FontWeight.Bold, fontSize = 16.sp,
            )
            Spacer(Modifier.height(4.dp))
        }

        items(staffList, key = { it.uid }) { member ->
            val isEditing  = editingUid == member.uid
            val myUid      = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(14.dp),
                colors   = CardDefaults.cardColors(containerColor = Color(0xFF1A1730)),
                elevation = CardDefaults.cardElevation(2.dp),
            ) {
                Column(Modifier.padding(14.dp)) {

                    // Üst satır: isim + görev adı
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Avatar dairesi
                        Box(
                            Modifier.size(42.dp).clip(CircleShape).background(Primary.copy(.2f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (member.photoURL.isNotBlank()) {
                                coil.compose.AsyncImage(
                                    model              = member.photoURL,
                                    contentDescription = null,
                                    modifier           = Modifier.fillMaxSize().clip(CircleShape),
                                )
                            } else {
                                Text(
                                    member.displayName.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                                    color = Primary, fontWeight = FontWeight.Bold, fontSize = 18.sp,
                                )
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(member.displayName, color = OnBackground, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            if (member.title.isNotBlank())
                                Text(member.title, color = Amber, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            if (member.email.isNotBlank())
                                Text(member.email, color = Muted, fontSize = 11.sp)
                        }
                        // İzin sayısı badge
                        Box(
                            Modifier.clip(RoundedCornerShape(8.dp))
                                .background(Primary.copy(.15f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("${member.permissions.size} izin", color = Primary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    // İzin chip'leri (sadece göster)
                    if (!isEditing) {
                        @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
                        androidx.compose.foundation.layout.FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalArrangement   = Arrangement.spacedBy(4.dp),
                        ) {
                            member.permissions.forEach { key ->
                                val label = ALL_PERMISSIONS[key] ?: key
                                val color = permColors[key] ?: Primary
                                Box(
                                    Modifier.clip(RoundedCornerShape(6.dp))
                                        .background(color.copy(.15f))
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text(label, color = color, fontSize = 10.sp, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }

                    // Düzenle / Kaldır butonları (kendin olmadığın sürece)
                    if (member.uid != myUid) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    if (isEditing) {
                                        editingUid = null
                                    } else {
                                        editingUid  = member.uid
                                        editPerms   = member.permissions.toMutableSet()
                                        editTitle   = member.title
                                    }
                                },
                                shape   = RoundedCornerShape(8.dp),
                                border  = androidx.compose.foundation.BorderStroke(1.dp, if (isEditing) Amber else Muted),
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(vertical = 6.dp),
                            ) {
                                Icon(
                                    if (isEditing) Icons.Default.ExpandLess else Icons.Default.Edit,
                                    null, Modifier.size(14.dp),
                                    tint = if (isEditing) Amber else Muted,
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    if (isEditing) "Kapat" else "Düzenle",
                                    fontSize = 12.sp,
                                    color    = if (isEditing) Amber else Muted,
                                )
                            }
                            OutlinedButton(
                                onClick = { vm.removeStaff(member.uid) { _, m -> msg = m } },
                                shape   = RoundedCornerShape(8.dp),
                                border  = androidx.compose.foundation.BorderStroke(1.dp, Error),
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(vertical = 6.dp),
                            ) {
                                Icon(Icons.Default.PersonRemove, null, Modifier.size(14.dp), tint = Error)
                                Spacer(Modifier.width(4.dp))
                                Text("Kaldır", fontSize = 12.sp, color = Error)
                            }
                        }

                        // Satır içi düzenleme paneli
                        if (isEditing) {
                            Spacer(Modifier.height(10.dp))
                            // Görev adı
                            OutlinedTextField(
                                value         = editTitle,
                                onValueChange = { editTitle = it },
                                label         = { Text("Görev adı", color = Muted) },
                                modifier      = Modifier.fillMaxWidth(),
                                singleLine    = true,
                                colors        = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor   = Primary,
                                    unfocusedBorderColor = Muted.copy(.4f),
                                    focusedTextColor     = OnBackground,
                                    unfocusedTextColor   = OnBackground,
                                ),
                            )
                            Spacer(Modifier.height(8.dp))
                            // İzin toggle listesi
                            Text("İzinler", color = Muted, fontSize = 12.sp)
                            Spacer(Modifier.height(6.dp))
                            ALL_PERMISSIONS.forEach { (key, label) ->
                                if (key == "staff") return@forEach // staff iznini başkasına verme
                                val checked = key in editPerms
                                val color   = permColors[key] ?: Primary
                                Row(
                                    Modifier.fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (checked) color.copy(.08f) else Color.Transparent)
                                        .clickable {
                                            editPerms = if (checked) editPerms - key else editPerms + key
                                        }
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment     = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            Modifier.size(10.dp).clip(CircleShape)
                                                .background(if (checked) color else Muted.copy(.3f))
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(label, color = if (checked) color else Muted, fontSize = 13.sp)
                                    }
                                    Switch(
                                        checked         = checked,
                                        onCheckedChange = {
                                            editPerms = if (it) editPerms + key else editPerms - key
                                        },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor  = color,
                                            checkedTrackColor  = color.copy(.3f),
                                        ),
                                    )
                                }
                                Divider(color = Muted.copy(.08f), thickness = 0.5.dp)
                            }
                            Spacer(Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    vm.updateStaff(member.uid, editTitle, editPerms) { ok, m ->
                                        msg = m
                                        if (ok) editingUid = null
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape    = RoundedCornerShape(10.dp),
                                colors   = ButtonDefaults.buttonColors(containerColor = Primary),
                            ) {
                                Icon(Icons.Default.Save, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Kaydet", fontWeight = FontWeight.Bold)
                            }
                        }
                    } else {
                        Text("(Siz)", color = Muted, fontSize = 11.sp)
                    }
                }
            }
        }

        if (staffList.isEmpty()) {
            item {
                Box(
                    Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Henüz yardımcı eklenmemiş.", color = Muted, fontSize = 13.sp)
                }
            }
        }

        // ── Yeni yardımcı ekle ─────────────────────────────────────────────
        item {
            Spacer(Modifier.height(4.dp))
            Text("Yeni Yardımcı Ekle", color = OnBackground, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(Modifier.height(4.dp))
            Text(
                "Firebase Authentication → Users bölümünden kullanıcının UID'ini kopyalayın.",
                color = Muted, fontSize = 12.sp,
            )
            Spacer(Modifier.height(10.dp))

            Card(
                modifier  = Modifier.fillMaxWidth(),
                shape     = RoundedCornerShape(14.dp),
                colors    = CardDefaults.cardColors(containerColor = Color(0xFF1A1730)),
                elevation = CardDefaults.cardElevation(2.dp),
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {

                    // UID input
                    OutlinedTextField(
                        value         = uidInput,
                        onValueChange = { uidInput = it },
                        label         = { Text("Kullanıcı UID *", color = Muted) },
                        modifier      = Modifier.fillMaxWidth(),
                        singleLine    = true,
                        colors        = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor   = Primary,
                            unfocusedBorderColor = Muted.copy(.4f),
                            focusedTextColor     = OnBackground,
                            unfocusedTextColor   = OnBackground,
                        ),
                    )

                    // Görev adı
                    OutlinedTextField(
                        value         = titleInput,
                        onValueChange = { titleInput = it },
                        label         = { Text("Görev adı (örn: Kürtçe Editörü)", color = Muted) },
                        modifier      = Modifier.fillMaxWidth(),
                        singleLine    = true,
                        colors        = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor   = Primary,
                            unfocusedBorderColor = Muted.copy(.4f),
                            focusedTextColor     = OnBackground,
                            unfocusedTextColor   = OnBackground,
                        ),
                    )

                    // İzin listesi
                    Text("İzinler", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Medium)

                    ALL_PERMISSIONS.forEach { (key, label) ->
                        if (key == "staff") return@forEach
                        val checked = key in newPerms
                        val color   = permColors[key] ?: Primary
                        Row(
                            Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (checked) color.copy(.08f) else Color.Transparent)
                                .clickable { newPerms = if (checked) newPerms - key else newPerms + key }
                                .padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    Modifier.size(10.dp).clip(CircleShape)
                                        .background(if (checked) color else Muted.copy(.3f))
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(label, color = if (checked) color else Muted, fontSize = 13.sp)
                            }
                            Switch(
                                checked         = checked,
                                onCheckedChange = { newPerms = if (it) newPerms + key else newPerms - key },
                                colors          = SwitchDefaults.colors(
                                    checkedThumbColor = color,
                                    checkedTrackColor = color.copy(.3f),
                                ),
                            )
                        }
                        Divider(color = Muted.copy(.08f), thickness = 0.5.dp)
                    }

                    Spacer(Modifier.height(2.dp))

                    // Seçili izinler özeti
                    if (newPerms.isNotEmpty()) {
                        @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
                    androidx.compose.foundation.layout.FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalArrangement   = Arrangement.spacedBy(4.dp),
                        ) {
                            newPerms.forEach { key ->
                                val color = permColors[key] ?: Primary
                                Box(
                                    Modifier.clip(RoundedCornerShape(6.dp))
                                        .background(color.copy(.15f))
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text(ALL_PERMISSIONS[key] ?: key, color = color, fontSize = 10.sp, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }

                    // Ekle butonu
                    Button(
                        onClick = {
                            vm.addStaff(uidInput.trim(), titleInput.trim(), newPerms) { ok, m ->
                                msg = m
                                if (ok) { uidInput = ""; titleInput = ""; newPerms = emptySet() }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape    = RoundedCornerShape(10.dp),
                        colors   = ButtonDefaults.buttonColors(containerColor = Primary),
                        enabled  = uidInput.isNotBlank() && newPerms.isNotEmpty(),
                    ) {
                        Icon(Icons.Default.PersonAdd, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Yardımcı Ekle", fontWeight = FontWeight.Bold)
                    }

                    // Sonuç mesajı
                    if (msg.isNotBlank()) {
                        Text(
                            msg,
                            color          = if (msg.startsWith("✓")) Success else Error,
                            fontSize       = 13.sp,
                            fontWeight     = FontWeight.Medium,
                        )
                    }
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

// ── Admin Kullanıcı Satırı ────────────────────────────────────────────────────
@Composable
private fun AdminUserRow(
    user        : com.heftreng.app.data.model.User,
    onToggleBan : () -> Unit,
    onVerify    : (() -> Unit)? = null,
) {
    Row(
        modifier          = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(2.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    user.displayName.ifBlank { "—" },
                    color      = com.heftreng.app.ui.theme.OnBackground,
                    fontWeight = FontWeight.SemiBold,
                    fontSize   = 13.sp,
                )
                if (user.emailVerified) {
                    Surface(
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
                        color = Color(0xFF22C55E).copy(alpha = 0.15f),
                    ) {
                        Text(
                            "✓ Doğrulandı",
                            color    = Color(0xFF22C55E),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                        )
                    }
                } else {
                    Surface(
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
                        color = com.heftreng.app.ui.theme.Amber.copy(alpha = 0.15f),
                    ) {
                        Text(
                            "Doğrulanmamış",
                            color    = com.heftreng.app.ui.theme.Amber,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                        )
                    }
                }
            }
            Text(user.email, color = com.heftreng.app.ui.theme.Muted, fontSize = 11.sp)
            if (user.banned) {
                Text("● BANLANDI", color = com.heftreng.app.ui.theme.Error, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
        // Admin doğrulama butonu — sadece doğrulanmamış email kullanıcılarında göster
        if (!user.emailVerified && onVerify != null) {
            IconButton(onClick = onVerify) {
                Icon(
                    Icons.Default.VerifiedUser,
                    contentDescription = "Doğrula",
                    tint     = Color(0xFF22C55E),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        // Ban / Unban
        IconButton(onClick = onToggleBan) {
            Icon(
                if (user.banned) Icons.Default.LockOpen else Icons.Default.Block,
                null,
                tint = if (user.banned) com.heftreng.app.ui.theme.Success else com.heftreng.app.ui.theme.Error,
            )
        }
    }
}
