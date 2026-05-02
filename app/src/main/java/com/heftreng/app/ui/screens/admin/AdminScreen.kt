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
import androidx.navigation.NavController
import com.heftreng.app.ui.theme.*
import com.heftreng.app.viewmodel.AdminViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen(
    navController: NavController,
    vm           : AdminViewModel = hiltViewModel(),
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

    val tabs = listOf("Push", "Bildirim", "Kullanıcılar", "Bekleyenler", "İstatistik")

    LaunchedEffect(Unit) {
        vm.checkAdmin()
        vm.loadUsers()
        vm.loadPendingPosts()
        vm.loadStats()
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
        containerColor = Background,
        topBar = {
            TopAppBar(
                title = { Text("Admin Paneli", fontWeight = FontWeight.Bold, color = OnBackground) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = OnBackground)
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

                // ── İstatistikler ──────────────────────────────────────────────
                4 -> LazyColumn(
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
            }
        }
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
