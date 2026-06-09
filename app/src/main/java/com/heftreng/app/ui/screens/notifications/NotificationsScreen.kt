package com.heftreng.app.ui.screens.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.heftreng.app.ui.i18n.Strings
import com.heftreng.app.ui.theme.*
import com.heftreng.app.viewmodel.NotificationsViewModel
import com.heftreng.app.viewmodel.ProfileViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    navController: NavController,
    vm: NotificationsViewModel = hiltViewModel(),
    profileVm: ProfileViewModel = hiltViewModel(),
    language: String = "tr",
) {
    val ku = language == "ku"
    val notifications by vm.notifications.collectAsState()
    val loading       by vm.loading.collectAsState()
    val unreadCount   = notifications.count { !it.read }

    Scaffold(
        containerColor = Background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            Strings.navNotifs(language),
                            fontWeight = FontWeight.SemiBold, color = OnBackground
                        )
                        if (unreadCount > 0)
                            Text(
                                if (ku) "$unreadCount nexwendî" else "$unreadCount okunmamış",
                                color = Muted, fontSize = 11.sp
                            )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = Strings.back(language), tint = OnBackground)
                    }
                },
                actions = {
                    if (unreadCount > 0) {
                        TextButton(onClick = { vm.markAllRead() }) {
                            Text(
                                if (ku) "Hemû bixwîne" else "Tümünü oku",
                                color = Amber, fontSize = 13.sp, fontWeight = FontWeight.SemiBold
                            )
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
                        Text(
                            Strings.noNotif(language),
                            color = Muted, fontSize = 15.sp
                        )
                        Text(
                            if (ku) "Agahdariyên nû dê li vir xuya bikin" else "Yeni bildirimler burada görünecek",
                            color = Muted, fontSize = 12.sp
                        )
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
                            notif    = notif,
                            language = language,
                            onAcceptFollowRequest = if (notif.type == "follow_request") {
                                { profileVm.acceptFollowRequest(notif.fromUid, notif.id) }
                            } else null,
                            onDeclineFollowRequest = if (notif.type == "follow_request") {
                                { profileVm.declineFollowRequest(notif.fromUid, notif.id) }
                            } else null,
                            onClick  = {
                                vm.markRead(notif.id)
                                when (notif.type) {
                                    "follow",
                                    "follow_request_accepted" -> navController.navigate(Screen.Profile.go(notif.fromUid))
                                    "follow_request" -> navController.navigate(Screen.Profile.go(notif.fromUid))
                                    "like", "cmt", "comment", "repost",
                                    "chapter", "post_approved", "post_rejected" -> {
                                        val pid = notif.postId
                                        when {
                                            !pid.isNullOrBlank() ->
                                                navController.navigate(Screen.PostDetail.go(pid))
                                            notif.url.isNotBlank() -> {
                                                val fromUrl = Regex("""post/([\w-]+)""").find(notif.url)?.groupValues?.get(1)
                                                    ?: Regex("""[?&]pid=([\w-]+)""").find(notif.url)?.groupValues?.get(1)
                                                    ?: Regex("""[?&]feedId=([\w-]+)""").find(notif.url)?.groupValues?.get(1)
                                                if (!fromUrl.isNullOrBlank())
                                                    navController.navigate(Screen.PostDetail.go(fromUrl))
                                            }
                                        }
                                    }
                                    "serial" -> notif.url.isNotBlank().let {
                                        val sid = Regex("""serial/([\w-]+)""").find(notif.url)?.groupValues?.get(1)
                                        if (!sid.isNullOrBlank()) navController.navigate("serial/$sid")
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun NotifItem(
    notif: Notification,
    onClick: () -> Unit = {},
    language: String = "tr",
    onAcceptFollowRequest: (() -> Unit)? = null,
    onDeclineFollowRequest: (() -> Unit)? = null,
) {
    val ku = language == "ku"
    // follow_request için onay/red yapıldıktan sonra butonları gizle
    var requestHandled by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (!notif.read) SurfaceVar else Background)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
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
                notif.message.ifBlank { notifDefaultMessage(notif.type, ku) },
                color = OnSurface, fontSize = 13.sp, lineHeight = 18.sp,
            )
            // Takip isteği — onay/red butonları
            if (notif.type == "follow_request" && !requestHandled &&
                onAcceptFollowRequest != null && onDeclineFollowRequest != null
            ) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            onAcceptFollowRequest()
                            requestHandled = true
                        },
                        shape  = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = androidx.compose.ui.graphics.Color.Black),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                    ) {
                        Text(Strings.followRequestAccept(language), fontSize = 12.sp)
                    }
                    OutlinedButton(
                        onClick = {
                            onDeclineFollowRequest()
                            requestHandled = true
                        },
                        shape  = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                    ) {
                        Text(Strings.followRequestDecline(language), fontSize = 12.sp, color = OnBackground)
                    }
                }
            }
        }

        if (!notif.read) {
            Spacer(Modifier.width(8.dp))
            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Amber))
        }
    }
}

fun notifIcon(type: String): ImageVector = when (type) {
    "like"                     -> Icons.Default.Favorite
    "comment"                  -> Icons.Default.ChatBubble
    "follow"                   -> Icons.Default.PersonAdd
    "follow_request"           -> Icons.Default.PersonAdd
    "follow_request_accepted"  -> Icons.Default.PersonAdd
    "repost"                   -> Icons.Default.Repeat
    else                       -> Icons.Default.Notifications
}

fun notifIconColor(type: String) = when (type) {
    "like"                    -> androidx.compose.ui.graphics.Color(0xFFEF4444)
    "comment"                 -> androidx.compose.ui.graphics.Color(0xFF3B82F6)
    "follow"                  -> androidx.compose.ui.graphics.Color(0xFF10B981)
    "follow_request"          -> androidx.compose.ui.graphics.Color(0xFFF59E0B)
    "follow_request_accepted" -> androidx.compose.ui.graphics.Color(0xFF10B981)
    else                      -> androidx.compose.ui.graphics.Color(0xFFF59E0B)
}

fun notifDefaultMessage(type: String, ku: Boolean = false): String {
    val l = if (ku) "ku" else "tr"
    return when (type) {
        "like"                    -> Strings.notifLike(l)
        "comment"                 -> Strings.notifComment(l)
        "follow"                  -> Strings.notifFollow(l)
        "follow_request"          -> Strings.notifFollowRequest(l)
        "follow_request_accepted" -> if (ku) "Daxwaza şopînê qebûl kir" else "Takip isteğini kabul etti"
        "repost"                  -> Strings.notifRepost(l)
        else                      -> Strings.notifNew(l)
    }
}
