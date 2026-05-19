package com.heftreng.app.ui.screens.notifications

import com.heftreng.app.ui.i18n.Strings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.heftreng.app.data.model.Notification
import com.heftreng.app.navigation.Screen
import com.heftreng.app.ui.theme.*
import com.heftreng.app.viewmodel.NotificationsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    navController: NavController,
    vm: NotificationsViewModel = hiltViewModel(),
    settingsVm: com.heftreng.app.viewmodel.SettingsViewModel = hiltViewModel(),
) {
    val language      by settingsVm.language.collectAsState()
    val notifications by vm.notifications.collectAsState()
    val loading       by vm.loading.collectAsState()
    val unreadCount   = notifications.count { !it.read }

    Scaffold(
        containerColor = Background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Agahdarî", fontWeight = FontWeight.SemiBold, color = OnBackground)
                        if (unreadCount > 0)
                            Text("$unreadCount okunmamış", color = Muted, fontSize = 11.sp)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri", tint = OnBackground)
                    }
                },
                actions = {
                    if (unreadCount > 0) {
                        TextButton(onClick = { vm.markAllRead() }) {
                            Text("Tümünü oku", color = Amber, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Background),
            )
        }
    ) { padding ->
        when {
            loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Amber)
                }
            }
            notifications.isEmpty() -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Notifications, contentDescription = null, tint = Muted, modifier = Modifier.size(52.dp))
                        Spacer(Modifier.height(12.dp))
                        Text(Strings.noNotif(language), color = Muted, fontSize = 15.sp)
                        Text("Agahdarî tune", color = Muted, fontSize = 12.sp)
                    }
                }
            }
            else -> {
                LazyColumn(
                    modifier       = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(bottom = 80.dp),
                ) {
                    items(notifications, key = { it.id }) { notif ->
                        NotifItem(
                            notif   = notif,
                            onClick = {
                                vm.markRead(notif.id)
                                // Bildirim tipine göre yönlendir
                                when (notif.type) {
                                    "follow" -> navController.navigate(Screen.Profile.go(notif.fromUid))
                                    "like", "cmt", "comment", "repost",
                                    "chapter", "post_approved", "post_rejected" -> {
                                        val pid = notif.postId
                                        when {
                                            // postId direkt varsa
                                            !pid.isNullOrBlank() ->
                                                navController.navigate(Screen.PostDetail.go(pid))
                                            // url'den postId çıkar: /post/ABC123 veya ?pid=ABC123
                                            notif.url.isNotBlank() -> {
                                                // DÜZELTME: Regex ifadeleri Raw String (""") içine alındı
                                                val fromUrl = Regex("""post/([\w-]+)""").find(notif.url)?.groupValues?.get(1)
                                                    ?: Regex("""[?&]pid=([\w-]+)""").find(notif.url)?.groupValues?.get(1)
                                                    ?: Regex("""[?&]feedId=([\w-]+)""").find(notif.url)?.groupValues?.get(1)
                                                
                                                if (!fromUrl.isNullOrBlank())
                                                    navController.navigate(Screen.PostDetail.go(fromUrl))
                                            }
                                        }
                                    }
                                    // Seri bildirimi
                                    "serial" -> notif.url.isNotBlank().let {
                                        // DÜZELTME: Regex ifadesi Raw String (""") içine alındı
                                        val sid = Regex("""serial/([\w-]+)""").find(notif.url)?.groupValues?.get(1)
                                        if (!sid.isNullOrBlank()) navController.navigate("serial/$sid")
                                    }
                                    // Bilinmeyen tür ama fromUid varsa profile git
                                    else -> if (notif.fromUid.isNotBlank())
                                        navController.navigate(Screen.Profile.go(notif.fromUid))
                                }
                            },
                        )
                        HorizontalDivider(color = Divider, thickness = 0.5.dp)
                    }
                }
            }
        }
    }
}

@Composable
fun NotifItem(notif: Notification, language: String = "tr", onClick: () -> Unit = {}) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (!notif.read) SurfaceVar else Background)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Avatar + badge
        Box {
            AsyncImage(
                model              = notif.fromPhoto.ifEmpty { null },
                contentDescription = notif.fromName,
                modifier           = Modifier.size(50.dp).clip(CircleShape).background(SurfaceVar),
                contentScale       = ContentScale.Crop,
            )
            Box(
                modifier         = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(notifIconColor(notif.type))
                    .align(Alignment.BottomEnd),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    notifIcon(notif.type),
                    contentDescription = null,
                    tint     = androidx.compose.ui.graphics.Color.White,
                    modifier = Modifier.size(11.dp),
                )
            }
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            if (notif.fromName.isNotBlank()) {
                Text(notif.fromName, fontWeight = FontWeight.SemiBold, color = OnBackground, fontSize = 14.sp)
            }
            Text(
                notif.message.ifBlank { notifDefaultMessage(notif.type, language) },
                color = OnSurface, fontSize = 13.sp, lineHeight = 18.sp,
            )
        }

        // Okunmamış nokta
        if (!notif.read) {
            Spacer(Modifier.width(8.dp))
            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Amber))
        }
    }
}

fun notifIcon(type: String): ImageVector = when (type) {
    "like"    -> Icons.Default.Favorite
    "comment" -> Icons.Default.ChatBubble
    "follow"  -> Icons.Default.PersonAdd
    "repost"  -> Icons.Default.Repeat
    else      -> Icons.Default.Notifications
}

fun notifIconColor(type: String) = when (type) {
    "like"    -> androidx.compose.ui.graphics.Color(0xFFEF4444)
    "comment" -> androidx.compose.ui.graphics.Color(0xFF3B82F6)
    "follow"  -> androidx.compose.ui.graphics.Color(0xFF10B981)
    else      -> androidx.compose.ui.graphics.Color(0xFFF59E0B)
}

fun notifDefaultMessage(type: String, language: String = "tr") = when (type) {
    "like"    -> if (language == "ku") "nivîsa we hez kir"       else "gönderinizi beğendi"
    "comment" -> if (language == "ku") "li nivîsa we şîrove kir" else "gönderinize yorum yaptı"
    "follow"  -> if (language == "ku") "dest bi şopîna we kir"   else "sizi takip etmeye başladı"
    "repost"  -> if (language == "ku") "nivîsa we parve kir"     else "gönderinizi paylaştı"
    else      -> if (language == "ku") "agahiyeke nû"             else "yeni bir bildirim"
}
