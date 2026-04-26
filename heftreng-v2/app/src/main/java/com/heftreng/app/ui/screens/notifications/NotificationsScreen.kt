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
import androidx.compose.ui.graphics.Color
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
        containerColor = bg(),
        topBar = {
            TopAppBar(
                title  = { Text("Agahdarî / Bildirimler", fontWeight = FontWeight.SemiBold, color = onBg()) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = bg()),
            )
        }
    ) { padding ->
        when {
            loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = accent())
                }
            }
            notifications.isEmpty() -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Notifications, null, tint = muted(), modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(12.dp))
                        Text("Agahdarî tune / Bildirim yok", color = muted())
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
                        HorizontalDivider(color = divider(), thickness = 0.5.dp)
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
            .background(if (!notif.read) surfVar() else bg())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Avatar + tür rozeti
        Box {
            Box(
                modifier         = Modifier.size(48.dp).clip(CircleShape).background(surfVar()),
                contentAlignment = Alignment.Center,
            ) {
                if (notif.fromPhoto.isNotBlank()) {
                    AsyncImage(
                        model              = notif.fromPhoto,
                        contentDescription = notif.fromName,
                        modifier           = Modifier.fillMaxSize(),
                        contentScale       = ContentScale.Crop,
                    )
                } else {
                    Text(
                        notif.fromName.firstOrNull()?.uppercase() ?: "H",
                        color      = accent(),
                        fontWeight = FontWeight.Bold,
                        fontSize   = 16.sp,
                    )
                }
            }
            // Küçük ikon rozeti
            Box(
                modifier         = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(notifBadgeColor(notif.type))
                    .align(Alignment.BottomEnd),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    notifIcon(notif.type), null,
                    tint     = Color.White,
                    modifier = Modifier.size(10.dp),
                )
            }
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            if (notif.fromName.isNotBlank())
                Text(notif.fromName, fontWeight = FontWeight.SemiBold, color = onBg(), fontSize = 14.sp)
            Text(
                notif.message.ifBlank { notifDefaultMsg(notif.type) },
                color      = onSurf(),
                fontSize   = AppFontSize.sp,
                lineHeight = (AppFontSize + 4).sp,
            )
        }

        if (!notif.read) {
            Spacer(Modifier.width(8.dp))
            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(accent()))
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

fun notifBadgeColor(type: String): Color = when (type) {
    "like"    -> Color(0xFFEF4444)
    "comment" -> Color(0xFF3B82F6)
    "follow"  -> Color(0xFF10B981)
    "repost"  -> Color(0xFF8B5CF6)
    else      -> Color(0xFFF59E0B)
}

fun notifDefaultMsg(type: String) = when (type) {
    "like"    -> "nivîsa te hezkirî"
    "comment" -> "şîroveyê li nivîsa te kir"
    "follow"  -> "dest bi şopandina te kir"
    "repost"  -> "nivîsa te dubare kir"
    else      -> "agahdariya nû"
}
