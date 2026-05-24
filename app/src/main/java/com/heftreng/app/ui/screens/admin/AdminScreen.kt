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
    val users       by vm.users.collectAsState()
    val pendingPosts by vm.pendingPosts.collectAsState()
    val loading     by vm.loading.collectAsState()
    val pushResult  by vm.pushResult.collectAsState()
    val stats       by vm.stats.collectAsState()

    var pushTitle   by remember { mutableStateOf("") }
    var pushBody    by remember { mutableStateOf("") }
    var pushUrl     by remember { mutableStateOf("") }
    var pushUid     by remember { mutableStateOf("") }
    var notifTitle  by remember { mutableStateOf("") }
    var notifBody   by remember { mutableStateOf("") }
    var notifType   by remember { mutableStateOf("sys") }
    var userSearch  by remember { mutableStateOf("") }
    var selectedTab by remember { mutableIntStateOf(0) }

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

    val tabs = listOf("Push", "Bildirim", "Kullanıcılar", "Bekleyenler", "Şikayetler", "İstatistik", "Düzenle", "Kütüphane")

    LaunchedEffect(Unit) {
        vm.checkAdmin()
        vm.loadUsers()
        vm.loadPendingPosts()
        vm.loadStats()
        vm.loadFeedPosts()
        vm.loadReports()
    }

    // editResult bildirimi
    LaunchedEffect(editResult) {
        if (editResult.isNotBlank()) {
            kotlinx.coroutines.delay(3000)
            vm.clearEditResult()
        }
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
                title = { Text("Admin Paneli", fontWeight = FontWeight.Bold, color = OnBackground) },
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
                selectedTabIndex = selectedTab,
                containerColor   = Background,
                contentColor     = Amber,
                edgePadding      = 0.dp,
                indicator        = { tabPositions ->
                    Box(Modifier.tabIndicatorOffset(tabPositions[selectedTab]).height(2.dp).background(Amber))
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

            when (selectedTab) {

                // ── Push Bildirimi ──────────────────────────────────────────────
                0 -> LazyColumn(
                    modifier       = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item {
                        Text("Toplu Push Bildirimi", color = Amber, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Spacer(Modifier.height(8.dp))
                        adminTextField(pushTitle, { pushTitle = it }, "Başlık *")
                        Spacer(Modifier.height(8.dp))
                        adminTextField(pushBody, { pushBody = it }, "Mesaj *", minLines = 3)
                        Spacer(Modifier.height(8.dp))
                        adminTextField(pushUrl, { pushUrl = it }, "URL (opsiyonel)")
                        Spacer(Modifier.height(8.dp))
                        adminTextField(pushUid, { pushUid = it }, "Hedef UID (boşsa herkese)")
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = { vm.sendPush(pushTitle, pushBody, pushUrl, pushUid) },
                            enabled = pushTitle.isNotBlank() && pushBody.isNotBlank(),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = Color.Black),
                        ) {
                            Icon(Icons.Default.Send, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Push Gönder", fontWeight = FontWeight.Bold)
                        }
                        if (pushResult.isNotBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Text(pushResult, color = if (pushResult.startsWith("✓")) Success else Error, fontSize = 13.sp)
                        }
                    }
                }

                // ── Sistem Bildirimi ─────────────────────────────────────────────
                1 -> LazyColumn(
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
                2 -> {
                    Column(Modifier.fillMaxSize()) {
                        OutlinedTextField(
                            value         = userSearch,
                            onValueChange = { userSearch = it },
                            placeholder   = { Text("Kullanıcı ara…", color = Muted) },
                            singleLine    = true,
                            modifier      = Modifier.fillMaxWidth().padding(12.dp),
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
                        LazyColumn(contentPadding = PaddingValues(horizontal = 12.dp)) {
                            items(filtered, key = { it.uid }) { user ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(user.displayName.ifBlank { "—" }, color = OnBackground, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                        Text(user.email, color = Muted, fontSize = 11.sp)
                                        if (user.banned) Text("● BANLANDI", color = Error, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                    IconButton(onClick = { vm.toggleBan(user.uid, !user.banned) }) {
                                        Icon(
                                            if (user.banned) Icons.Default.LockOpen else Icons.Default.Block,
                                            null,
                                            tint = if (user.banned) Success else Error,
                                        )
                                    }
                                }
                                HorizontalDivider(color = Divider, thickness = 0.5.dp)
                            }
                        }
                    }
                }

                // ── Bekleyen Gönderiler ─────────────────────────────────────────
                3 -> {
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
                4 -> LazyColumn(
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
                                if (status == "pending") {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(
                                            onClick = { vm.updateReportStatus(reportId, "reviewed") },
                                            shape   = RoundedCornerShape(8.dp),
                                            colors  = ButtonDefaults.buttonColors(containerColor = Color(0xFF22C55E), contentColor = Color.White),
                                            modifier = Modifier.weight(1f),
                                            contentPadding = PaddingValues(vertical = 6.dp),
                                        ) {
                                            Text("İncelendi", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                        OutlinedButton(
                                            onClick = { vm.updateReportStatus(reportId, "dismissed") },
                                            shape   = RoundedCornerShape(8.dp),
                                            modifier = Modifier.weight(1f),
                                            contentPadding = PaddingValues(vertical = 6.dp),
                                        ) {
                                            Text("Reddet", fontSize = 12.sp, color = Muted)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // ── İstatistikler ──────────────────────────────────────────────
                5 -> LazyColumn(
                    modifier       = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        Text("Platform İstatistikleri", color = Amber, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Spacer(Modifier.height(8.dp))
                        listOf(
                            "Toplam Kullanıcı" to (stats["users"]?.toString() ?: "—"),
                            "Toplam Gönderi"   to (stats["posts"]?.toString() ?: "—"),
                            "Toplam Seri"      to (stats["serials"]?.toString() ?: "—"),
                            "Toplam Kitap"     to (stats["books"]?.toString() ?: "—"),
                            "Bekleyen"         to (stats["pending"]?.toString() ?: "—"),
                        ).forEach { (label, value) ->
                            Row(
                                modifier = Modifier.fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(label, color = OnBackground, modifier = Modifier.weight(1f))
                                Text(value, color = Amber, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            }
                            HorizontalDivider(color = Divider, thickness = 0.5.dp)
                        }
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick  = { vm.loadStats() },
                            modifier = Modifier.fillMaxWidth(),
                            shape    = RoundedCornerShape(10.dp),
                            colors   = ButtonDefaults.buttonColors(containerColor = SurfaceVar, contentColor = OnBackground),
                        ) {
                            Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Yenile")
                        }
                    }
                }


                // ── Düzenle ───────────────────────────────────────────────────
                6 -> {
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
                            Text("Kullanıcı Düzenle / Sil", color = Amber, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Spacer(Modifier.height(8.dp))

                            val filteredUsers = users.filter {
                                userSearch.isBlank() ||
                                it.displayName.contains(userSearch, ignoreCase = true) ||
                                it.email.contains(userSearch, ignoreCase = true) ||
                                it.uid.contains(userSearch, ignoreCase = true)
                            }

                            OutlinedTextField(
                                value         = userSearch,
                                onValueChange = { userSearch = it },
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
                            )
                            Spacer(Modifier.height(8.dp))

                            filteredUsers.take(20).forEach { user ->
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
                //  TAB 7 — KÜTÜPHANE: Yazar & Kitap Yönetimi
                // ─────────────────────────────────────────────────────────
                7 -> {
                    AdminLibraryTab(libraryVm = libraryVm)
                }
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
) {
    OutlinedTextField(
        value         = value,
        onValueChange = onChange,
        label         = { Text(label) },
        minLines      = minLines,
        modifier      = Modifier.fillMaxWidth(),
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

    var migrateStatus by remember { mutableStateOf("") }
    var migrateRunning by remember { mutableStateOf(false) }

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
