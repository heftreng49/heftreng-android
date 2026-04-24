package com.heftreng.app.ui.screens.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
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
import com.heftreng.app.ui.theme.*
import com.heftreng.app.viewmodel.NotificationsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    navController: NavController,
    vm: NotificationsViewModel = hiltViewModel(),
) {
    val notifications by vm.notifications.collectAsState()
    val loading       by vm.loading.collectAsState()

    Scaffold(
        containerColor = Background,
        topBar = {
            TopAppBar(
                title  = { Text("Agahdarî", fontWeight = FontWeight.SemiBold, color = OnBackground) },
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
                        Icon(Icons.Default.Notifications, contentDescription = null, tint = Muted, modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(12.dp))
                        Text("Agahdarî tune / Henüz bildirim yok", color = Muted)
                    }
                }
            }
            else -> {
                LazyColumn(
                    modifier       = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(bottom = 80.dp),
                ) {
                    items(notifications, key = { it.id }) { notif ->
                        NotifItem(notif)
                        HorizontalDivider(color = Divider, thickness = 0.5.dp)
                    }
                }
            }
        }
    }
}

@Composable
fun NotifItem(notif: Notification) {
    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .background(if (!notif.read) SurfaceVar else Background)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Avatar + icon badge
        Box {
            AsyncImage(
                model              = notif.fromPhoto.ifEmpty { null },
                contentDescription = notif.fromName,
                modifier           = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(SurfaceVar),
                contentScale = ContentScale.Crop,
            )
            Box(
                modifier         = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(notifIconColor(notif.type))
                    .align(Alignment.BottomEnd),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    notifIcon(notif.type),
                    contentDescription = null,
                    tint               = androidx.compose.ui.graphics.Color.White,
                    modifier           = Modifier.size(10.dp),
                )
            }
        }

        Spacer(Modifier.width(12.dp))

        // Text content
        Column(modifier = Modifier.weight(1f)) {
            // Sender name
            if (notif.fromName.isNotBlank()) {
                Text(
                    notif.fromName,
                    fontWeight = FontWeight.SemiBold,
                    color      = OnBackground,
                    fontSize   = 14.sp,
                )
            }
            // Notification message
            val msg = notif.message.ifBlank { notifDefaultMessage(notif.type) }
            Text(
                msg,
                color      = OnSurface,
                fontSize   = 13.sp,
                lineHeight = 18.sp,
            )
        }

        // Unread dot
        if (!notif.read) {
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(Amber),
            )
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

fun notifDefaultMessage(type: String) = when (type) {
    "like"    -> "gönderinizi beğendi"
    "comment" -> "gönderinize yorum yaptı"
    "follow"  -> "sizi takip etmeye başladı"
    "repost"  -> "gönderinizi paylaştı"
    else      -> "yeni bir bildirim"
}
