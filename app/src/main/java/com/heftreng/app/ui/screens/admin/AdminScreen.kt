package com.heftreng.app.ui.screens.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
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
    val loading     by vm.loading.collectAsState()
    val pushResult  by vm.pushResult.collectAsState()

    var pushTitle   by remember { mutableStateOf("") }
    var pushBody    by remember { mutableStateOf("") }
    var banUid      by remember { mutableStateOf("") }
    var selectedTab by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) { vm.checkAdmin(); vm.loadUsers() }

    if (!isAdmin) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Erişim Yok", color = Error, fontWeight = FontWeight.Bold, fontSize = 18.sp)
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
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor   = Background,
                contentColor     = Amber,
            ) {
                Tab(selectedTab == 0, { selectedTab = 0 }, text = { Text("Push", fontSize = 12.sp) }, selectedContentColor = Amber, unselectedContentColor = Muted)
                Tab(selectedTab == 1, { selectedTab = 1 }, text = { Text("Kullanıcılar", fontSize = 12.sp) }, selectedContentColor = Amber, unselectedContentColor = Muted)
                Tab(selectedTab == 2, { selectedTab = 2 }, text = { Text("Ban", fontSize = 12.sp) }, selectedContentColor = Amber, unselectedContentColor = Muted)
            }

            when (selectedTab) {
                // ─── Push Bildirimi ────────────────────────────────────────
                0 -> Column(
                    modifier            = Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("Toplu Push Bildirimi", fontWeight = FontWeight.SemiBold, color = Amber)
                    OutlinedTextField(
                        value = pushTitle, onValueChange = { pushTitle = it },
                        label = { Text("Başlık") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = adminTextFieldColors(),
                    )
                    OutlinedTextField(
                        value = pushBody, onValueChange = { pushBody = it },
                        label = { Text("Mesaj") }, minLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                        colors = adminTextFieldColors(),
                    )
                    Button(
                        onClick  = { vm.sendPushToAll(pushTitle, pushBody) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled  = pushTitle.isNotBlank() && pushBody.isNotBlank(),
                        shape    = RoundedCornerShape(10.dp),
                        colors   = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = Color.Black),
                    ) {
                        Icon(Icons.Default.Notifications, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Gönder", fontWeight = FontWeight.Bold)
                    }
                    if (pushResult.isNotBlank()) {
                        Text(pushResult, color = if (pushResult.startsWith("✓")) Success else Error, fontSize = 13.sp)
                    }
                }

                // ─── Kullanıcı Listesi ─────────────────────────────────────
                1 -> {
                    if (loading) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Amber)
                        }
                    } else {
                        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(8.dp)) {
                            items(users, key = { it.uid }) { user ->
                                Row(
                                    modifier          = Modifier.fillMaxWidth().padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(user.displayName.ifBlank { "—" }, color = OnBackground, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                        Text(user.email, color = Muted, fontSize = 11.sp)
                                    }
                                    if (user.banned) {
                                        Text("BANLANDI", color = Error, fontSize = 10.sp, fontWeight = FontWeight.Bold)
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

                // ─── Ban / Unban ────────────────────────────────────────────
                2 -> Column(
                    modifier            = Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("UID ile Ban/Unban", fontWeight = FontWeight.SemiBold, color = Amber)
                    OutlinedTextField(
                        value = banUid, onValueChange = { banUid = it },
                        label = { Text("Kullanıcı UID") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = adminTextFieldColors(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick  = { vm.toggleBan(banUid, true) },
                            enabled  = banUid.isNotBlank(),
                            shape    = RoundedCornerShape(10.dp),
                            colors   = ButtonDefaults.buttonColors(containerColor = Error, contentColor = Color.White),
                            modifier = Modifier.weight(1f),
                        ) { Text("Banla") }
                        Button(
                            onClick  = { vm.toggleBan(banUid, false) },
                            enabled  = banUid.isNotBlank(),
                            shape    = RoundedCornerShape(10.dp),
                            colors   = ButtonDefaults.buttonColors(containerColor = Success, contentColor = Color.Black),
                            modifier = Modifier.weight(1f),
                        ) { Text("Ban Kaldır") }
                    }
                }
            }
        }
    }
}

@Composable
private fun adminTextFieldColors() = OutlinedTextFieldDefaults.colors(
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
