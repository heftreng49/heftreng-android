package com.heftreng.app.ui.screens.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import com.heftreng.app.ads.RemoteConfigManager
import com.heftreng.app.ui.component.AdSlotView
import kotlinx.coroutines.flow.debounce
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import java.util.concurrent.TimeUnit

// ── Zaman yardımcıları ─────────────────────────────────────────────────────────
private fun relativeTime(ts: com.google.firebase.Timestamp?, language: String): String {
    val ku = language == "ku"
    if (ts == null) return ""
    val diffMs  = System.currentTimeMillis() - ts.seconds * 1000
    val minutes = TimeUnit.MILLISECONDS.toMinutes(diffMs)
    val hours   = TimeUnit.MILLISECONDS.toHours(diffMs)
    val days    = TimeUnit.MILLISECONDS.toDays(diffMs)
    return when {
        minutes < 1  -> if (ku) "Niha"       else "Şimdi"
        minutes < 60 -> if (ku) "${minutes}d" else "${minutes}d"
        hours < 24   -> if (ku) "${hours}s"   else "${hours}sa"
        days < 7     -> if (ku) "${days}r"    else "${days}g"
        else         -> {
            val sdf = java.text.SimpleDateFormat("dd MMM", java.util.Locale.getDefault())
            sdf.format(java.util.Date(ts.seconds * 1000))
        }
    }
}

private enum class NotifGroup { TODAY, WEEK, OLDER }

private fun notifGroup(ts: com.google.firebase.Timestamp?): NotifGroup {
    if (ts == null) return NotifGroup.OLDER
    val diffMs = System.currentTimeMillis() - ts.seconds * 1000
    val days   = TimeUnit.MILLISECONDS.toDays(diffMs)
    return when {
        days < 1 -> NotifGroup.TODAY
        days < 7 -> NotifGroup.WEEK
        else     -> NotifGroup.OLDER
    }
}

// ── Bildirim tipi meta ─────────────────────────────────────────────────────────
fun notifIcon(type: String): ImageVector = when (type) {
    "like"                    -> Icons.Default.Favorite
    "cmt", "comment"          -> Icons.Default.ChatBubble
    "mention"                 -> Icons.Default.AlternateEmail
    "follow"                  -> Icons.Default.PersonAdd
    "follow_request"          -> Icons.Default.PersonAdd
    "follow_request_accepted" -> Icons.Default.HowToReg
    "repost"                  -> Icons.Default.Repeat
    "bm", "bookmark"          -> Icons.Default.Bookmark
    "verified"                -> Icons.Default.VerifiedUser
    "moderation"              -> Icons.Default.Shield
    "serial", "chapter",
    "book_chapter"            -> Icons.Default.MenuBook
    "library_quote",
    "library_review"          -> Icons.Default.FormatQuote
    "sys", "default", "admin"  -> Icons.Default.Campaign
    "appeal_result"            -> Icons.Default.Gavel
    "message"                 -> Icons.Default.Message
    else                      -> Icons.Default.Notifications
}

fun notifIconColor(type: String): Color = when (type) {
    "like"                    -> Color(0xFFEF4444)
    "cmt", "comment"          -> Color(0xFF3B82F6)
    "mention"                 -> Color(0xFFFFA726)
    "follow"                  -> Color(0xFF10B981)
    "follow_request"          -> Color(0xFFF59E0B)
    "follow_request_accepted" -> Color(0xFF10B981)
    "repost"                  -> Color(0xFF8B5CF6)
    "bm", "bookmark"          -> Color(0xFF06B6D4)
    "verified"                -> Color(0xFF22C55E)
    "moderation"              -> Color(0xFFEF4444)
    "serial", "chapter",
    "book_chapter"            -> Color(0xFF6366F1)
    "library_quote",
    "library_review"          -> Color(0xFF0EA5E9)
    "sys", "default", "admin" -> Color(0xFFF59E0B)
    "appeal_result"           -> Color(0xFF8B5CF6)
    "message"                 -> Color(0xFF14B8A6)
    else                      -> Color(0xFFF59E0B)
}

fun notifDefaultMessage(type: String, ku: Boolean = false): String {
    val l = if (ku) "ku" else "tr"
    return when (type) {
        "like"                    -> Strings.notifLike(l)
        "cmt", "comment"          -> Strings.notifComment(l)
        "mention"                 -> Strings.notifMention(l)
        "follow"                  -> Strings.notifFollow(l)
        "follow_request"          -> Strings.notifFollowRequest(l)
        "follow_request_accepted" -> if (ku) "Daxwaza şopînê qebûl kir" else "Takip isteğini kabul etti"
        "repost"                  -> Strings.notifRepost(l)
        "bm", "bookmark"          -> if (ku) "Nivîsa te tomar kir" else "Gönderini kaydetti"
        "verified"                -> if (ku) "Hesabê te hate verastkirin ✓" else "Hesabın doğrulandı ✓"
        "moderation"              -> if (ku) "Nîşedariyek li ser naveroka te" else "İçeriğin hakkında bildirim"
        "serial", "chapter",
        "book_chapter"            -> if (ku) "Beşek nû hate weşandin" else "Yeni bölüm yayınlandı"
        "library_quote"           -> if (ku) "Jêgirtek nû hat zêdekirin" else "Yeni alıntı eklendi"
        "library_review"          -> if (ku) "Nêrînek nû hat nivîsîn" else "Yeni inceleme yazıldı"
        "sys", "default"          -> if (ku) "Agahdarî" else "Duyuru"
        "message"                 -> if (ku) "Peyamek nû" else "Yeni mesaj"
        else                      -> Strings.notifNew(l)
    }
}

// ── Ana Ekran ──────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
@Composable
fun NotificationsScreen(
    navController: NavController,
    vm: NotificationsViewModel = hiltViewModel(),
    adsVm: com.heftreng.app.viewmodel.AdsViewModel = hiltViewModel(),
    language: String = "tr",
) {
    val ku            = language == "ku"
    val notifications by vm.notifications.collectAsState()
    val loading       by vm.loading.collectAsState()
    val unreadCount   = notifications.count { !it.read }

    // ── Reklam alt yapısı — bilinçli olarak KAPALI başlıyor ────────────────
    // Bu ekran metin ağırlıklı, düz bir liste olduğu için native yerine
    // banner tercih edildi (Adım 5 kriteri: metin ağırlıklı listeler → banner).
    // Remote Config'te enabled:false olduğu sürece hiçbir şey görünmez —
    // gerçek unitId Firebase Console'dan girilene kadar bu ekranda reklam
    // yüklenmeyecek (bkz. REKLAM-DENETIM-PLANI.md Adım 6).
    val adConfigs by adsVm.allConfigs.collectAsState()
    val adPlan = remember(notifications.size, adConfigs) {
        adsVm.planFor(
            screenKey = "notifications",
            itemCount = notifications.size,
            bannerKey = RemoteConfigManager.KEY_BANNER_NOTIFICATIONS,
        )
    }
    val notifListState = rememberLazyListState()

    LaunchedEffect(notifListState, adPlan) {
        adsVm.warmVisiblePositions(adPlan, firstVisibleIndex = 0, maxInitialAds = 3)
        snapshotFlow { notifListState.firstVisibleItemIndex }
            .debounce(300L)
            .collect { firstVisible ->
                adsVm.warmVisiblePositions(adPlan, firstVisibleIndex = firstVisible)
            }
    }

    DisposableEffect(Unit) {
        onDispose { adsVm.releaseBanners("notifications_banner_") }
    }

    val refreshing by vm.refreshing.collectAsState()
    val pullRefreshState = rememberPullRefreshState(
        refreshing = refreshing,
        onRefresh  = { vm.refresh() }
    )

    // Ekran açılınca listener'ı başlat (zaten kuruluysa no-op)
    LaunchedEffect(Unit) {
        vm.load()
    }

    Scaffold(
        containerColor = Background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            Strings.navNotifs(language),
                            fontWeight = FontWeight.Bold,
                            color      = OnBackground,
                            fontSize   = 18.sp,
                        )
                        if (unreadCount > 0)
                            Text(
                                if (ku) "$unreadCount nexwendî" else "$unreadCount okunmamış",
                                color    = Amber,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                            )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = OnBackground)
                    }
                },
                actions = {
                    if (unreadCount > 0) {
                        TextButton(onClick = { vm.markAllRead() }) {
                            Text(
                                if (ku) "Hemû bixwîne" else "Tümünü oku",
                                color      = Amber,
                                fontSize   = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Background),
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().pullRefresh(pullRefreshState)) {
            when {
                loading && notifications.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Amber)
                    }
                }
                notifications.isEmpty() -> {
                    Box(
                        Modifier.fillMaxSize().padding(padding),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(80.dp)
                                    .clip(CircleShape)
                                    .background(SurfaceVar),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Default.Notifications,
                                    null,
                                    tint     = Muted,
                                    modifier = Modifier.size(40.dp),
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                Strings.noNotif(language),
                                color      = OnBackground,
                                fontSize   = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                if (ku) "Agahdariyên nû dê li vir xuya bikin"
                                else    "Yeni bildirimler burada görünecek",
                                color    = Muted,
                                fontSize = 13.sp,
                            )
                        }
                    }
                }
                else -> {
                    // Gruplama: Bugün / Bu Hafta / Daha Önce
                    val grouped = notifications.groupBy { notifGroup(it.ts) }
                    val todayList  = grouped[NotifGroup.TODAY]  ?: emptyList()
                    val weekList   = grouped[NotifGroup.WEEK]   ?: emptyList()
                    val olderList  = grouped[NotifGroup.OLDER]  ?: emptyList()

                    LazyColumn(
                        state          = notifListState,
                        modifier       = Modifier.fillMaxSize().padding(padding),
                        contentPadding = PaddingValues(bottom = 80.dp),
                    ) {
                        if (todayList.isNotEmpty()) {
                            item {
                                NotifGroupHeader(Strings.notifGroupToday(language))
                            }
                            itemsIndexed(todayList, key = { _, n -> n.id }) { idx, notif ->
                                NotifItem(
                                    notif    = notif,
                                    language = language,
                                    onAcceptFollowRequest  = if (notif.type == "follow_request") {{ vm.acceptFollowRequest(notif.fromUid, notif.id) }} else null,
                                    onDeclineFollowRequest = if (notif.type == "follow_request") {{ vm.declineFollowRequest(notif.fromUid, notif.id) }} else null,
                                    onClick  = { handleNotifClick(notif, navController, vm) },
                                )
                                // Global index = grup içi index (offset 0, ilk grup)
                                adPlan[idx]?.let { placement ->
                                    AdSlotView(placement = placement, adsVm = adsVm, modifier = Modifier.padding(vertical = 4.dp))
                                }
                            }
                        }
                        if (weekList.isNotEmpty()) {
                            item { NotifGroupHeader(Strings.notifGroupWeek(language)) }
                            itemsIndexed(weekList, key = { _, n -> n.id }) { idx, notif ->
                                NotifItem(
                                    notif    = notif,
                                    language = language,
                                    onAcceptFollowRequest  = if (notif.type == "follow_request") {{ vm.acceptFollowRequest(notif.fromUid, notif.id) }} else null,
                                    onDeclineFollowRequest = if (notif.type == "follow_request") {{ vm.declineFollowRequest(notif.fromUid, notif.id) }} else null,
                                    onClick  = { handleNotifClick(notif, navController, vm) },
                                )
                                // Global index = todayList'in boyutu + grup içi index
                                adPlan[todayList.size + idx]?.let { placement ->
                                    AdSlotView(placement = placement, adsVm = adsVm, modifier = Modifier.padding(vertical = 4.dp))
                                }
                            }
                        }
                        if (olderList.isNotEmpty()) {
                            item { NotifGroupHeader(Strings.notifGroupOlder(language)) }
                            itemsIndexed(olderList, key = { _, n -> n.id }) { idx, notif ->
                                NotifItem(
                                    notif    = notif,
                                    language = language,
                                    onAcceptFollowRequest  = if (notif.type == "follow_request") {{ vm.acceptFollowRequest(notif.fromUid, notif.id) }} else null,
                                    onDeclineFollowRequest = if (notif.type == "follow_request") {{ vm.declineFollowRequest(notif.fromUid, notif.id) }} else null,
                                    onClick  = { handleNotifClick(notif, navController, vm) },
                                )
                                // Global index = (todayList + weekList) boyutu + grup içi index
                                adPlan[todayList.size + weekList.size + idx]?.let { placement ->
                                    AdSlotView(placement = placement, adsVm = adsVm, modifier = Modifier.padding(vertical = 4.dp))
                                }
                            }
                        }
                    }
                }
            }

            PullRefreshIndicator(
                refreshing = refreshing,
                state      = pullRefreshState,
                modifier   = Modifier.align(Alignment.TopCenter),
                contentColor = Amber,
            )
        }
    }
}

private fun handleNotifClick(
    notif        : Notification,
    navController: NavController,
    vm           : NotificationsViewModel,
) {
    vm.markRead(notif.id)
    when (notif.type) {
        "follow",
        "follow_request",
        "follow_request_accepted" -> navController.navigate(Screen.Profile.go(notif.fromUid))
        "message"                 -> navController.navigate("messages/${notif.fromUid}")
        "like", "cmt", "comment", "mention",
        "repost", "bm", "bookmark",
        "chapter", "book_chapter",
        "post_approved", "post_rejected" -> {
            val pid = notif.postId
            when {
                !pid.isNullOrBlank() -> navController.navigate(Screen.PostDetail.go(pid))
                notif.url.isNotBlank() -> {
                    val fromUrl =
                        Regex("""post/([\w-]+)""").find(notif.url)?.groupValues?.get(1)
                        ?: Regex("""[?&]pid=([\w-]+)""").find(notif.url)?.groupValues?.get(1)
                        ?: Regex("""[?&]feedId=([\w-]+)""").find(notif.url)?.groupValues?.get(1)
                    if (!fromUrl.isNullOrBlank()) navController.navigate(Screen.PostDetail.go(fromUrl))
                }
            }
        }
        "serial" -> {
            val sid = Regex("""serial/([\w-]+)""").find(notif.url)?.groupValues?.get(1)
            if (!sid.isNullOrBlank()) navController.navigate("serial/$sid")
        }
        "admin", "moderation", "appeal_result" -> {
            val pid = notif.postId
            if (!pid.isNullOrBlank()) {
                navController.navigate(Screen.PostDetail.go(pid))
            }
            // url varsa ve harici linkse açma — sadece in-app navigasyon
        }
        "daily_quote", "daily_word" -> {
            // Orijinal alıntı/kelime paylaşımına git — postId varsa
            val pid = notif.postId
            when {
                !pid.isNullOrBlank() -> navController.navigate(Screen.PostDetail.go(pid))
                notif.url.isNotBlank() -> {
                    val fromUrl =
                        Regex("""post/([\w-]+)""").find(notif.url)?.groupValues?.get(1)
                        ?: Regex("""[?&]postId=([\w-]+)""").find(notif.url)?.groupValues?.get(1)
                    if (!fromUrl.isNullOrBlank()) navController.navigate(Screen.PostDetail.go(fromUrl))
                    // postId/url yoksa zaten bildirim ekranındayız, ek navigasyon gerekmiyor
                }
            }
        }
    }
}

// ── Grup Başlığı ───────────────────────────────────────────────────────────────
@Composable
private fun NotifGroupHeader(label: String) {
    Text(
        label,
        color      = Muted,
        fontSize   = 11.sp,
        fontWeight = FontWeight.SemiBold,
        modifier   = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        letterSpacing = 0.8.sp,
    )
}

// ── Günün Alıntısı / Kelimesi özel kart ───────────────────────────────────────
@Composable
fun DailyNotifCard(
    notif    : Notification,
    onClick  : () -> Unit = {},
    language : String     = "tr",
) {
    val ku         = language == "ku"
    val isQuote    = notif.type == "daily_quote"
    val accentColor = if (isQuote) Color(0xFF8B5CF6) else Color(0xFF0EA5E9)
    val timeText    = relativeTime(notif.ts, language)
    val isUnread    = !notif.read

    Surface(
        onClick   = onClick,
        modifier  = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        shape     = RoundedCornerShape(16.dp),
        color     = HeftCard,
        border    = androidx.compose.foundation.BorderStroke(
            width = if (isUnread) 1.5.dp else 0.75.dp,
            color = accentColor.copy(alpha = if (isUnread) 0.35f else 0.15f),
        ),
        tonalElevation = if (isUnread) 1.dp else 0.dp,
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Üst satır: ikon + başlık + zaman
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (isQuote) Icons.Default.FormatQuote else Icons.Default.Translate,
                        null,
                        tint     = accentColor,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        notif.message.ifBlank {
                            if (isQuote)
                                if (ku) "Jêgirta Rojê" else "Günün Alıntısı"
                            else
                                if (ku) "Peyvа Rojê" else "Günün Kelimesi"
                        },
                        fontWeight = FontWeight.Bold,
                        color      = accentColor,
                        fontSize   = 13.sp,
                        maxLines   = 1,
                        overflow   = TextOverflow.Ellipsis,
                    )
                    if (timeText.isNotBlank()) {
                        Text(timeText, color = Muted, fontSize = 11.sp)
                    }
                }
                if (isUnread) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(accentColor),
                    )
                }
            }

            // Alıntı / Kelime içeriği — tüm metin gösterilir, kesilmez
            if (notif.sub.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = accentColor.copy(alpha = 0.06f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        if (isQuote) {
                            Icon(
                                Icons.Default.FormatQuote,
                                null,
                                tint     = accentColor.copy(alpha = 0.5f),
                                modifier = Modifier.size(18.dp).padding(top = 2.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                        }
                        Text(
                            notif.sub,
                            color      = OnBackground,
                            fontSize   = 14.sp,
                            lineHeight = 21.sp,
                            fontStyle  = if (isQuote) androidx.compose.ui.text.font.FontStyle.Italic
                                         else         androidx.compose.ui.text.font.FontStyle.Normal,
                        )
                    }
                }
            }
        }
    }
}

// ── Bildirim Öğesi ─────────────────────────────────────────────────────────────
@Composable
fun NotifItem(
    notif                 : Notification,
    onClick               : () -> Unit = {},
    language              : String     = "tr",
    onAcceptFollowRequest : (() -> Unit)? = null,
    onDeclineFollowRequest: (() -> Unit)? = null,
) {
    // Günün Alıntısı ve Günün Kelimesi → özel kart
    if (notif.type == "daily_quote" || notif.type == "daily_word") {
        DailyNotifCard(notif = notif, onClick = onClick, language = language)
        return
    }

    val ku = language == "ku"
    val iconColor  = notifIconColor(notif.type)
    val mainText   = notif.message.ifBlank { notifDefaultMessage(notif.type, ku) }
    val subText    = notif.sub.ifBlank { null }
    val timeText   = relativeTime(notif.ts, language)
    val isUnread   = !notif.read

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isUnread) iconColor.copy(alpha = 0.05f) else Background)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        // Avatar + tip ikonu
        Box(modifier = Modifier.padding(top = 2.dp)) {
            AsyncImage(
                model              = notif.fromPhoto.ifEmpty { null },
                contentDescription = notif.fromName,
                modifier           = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(SurfaceVar),
                contentScale = ContentScale.Crop,
            )
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(iconColor)
                    .align(Alignment.BottomEnd),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    notifIcon(notif.type),
                    contentDescription = null,
                    tint     = Color.White,
                    modifier = Modifier.size(10.dp),
                )
            }
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            // Başlık satırı: isim + zaman
            Row(
                modifier          = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // İsim (bold) + eylem metni
                val nameText = notif.fromName.ifBlank { null }
                if (nameText != null) {
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            nameText,
                            fontWeight = FontWeight.Bold,
                            color      = OnBackground,
                            fontSize   = 13.sp,
                            maxLines   = 1,
                            overflow   = TextOverflow.Ellipsis,
                            modifier   = Modifier.weight(1f, fill = false),
                        )
                    }
                }
                // Zaman
                if (timeText.isNotBlank()) {
                    Text(
                        timeText,
                        color    = Muted,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }

            Spacer(Modifier.height(2.dp))

            // Eylem açıklaması
            Text(
                mainText,
                color      = OnSurface,
                fontSize   = 13.sp,
                lineHeight = 18.sp,
                maxLines   = 2,
                overflow   = TextOverflow.Ellipsis,
            )

            // İçerik önizlemesi (post metni, yorum vb.)
            if (subText != null) {
                Spacer(Modifier.height(4.dp))
                Surface(
                    shape  = RoundedCornerShape(8.dp),
                    color  = SurfaceVar,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        subText,
                        color    = Muted,
                        fontSize = 12.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        lineHeight = 17.sp,
                        fontStyle  = androidx.compose.ui.text.font.FontStyle.Italic,
                    )
                }
            }

            // Post görseli küçük thumbnail
            if (notif.imageUrl.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                AsyncImage(
                    model              = notif.imageUrl,
                    contentDescription = null,
                    modifier           = Modifier
                        .size(width = 80.dp, height = 56.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(SurfaceVar),
                    contentScale = ContentScale.Crop,
                )
            }

            // Takip isteği onay/red
            if (notif.type == "follow_request" &&
                onAcceptFollowRequest != null && onDeclineFollowRequest != null
            ) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { onAcceptFollowRequest() },
                        shape  = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = Color.Black),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                    ) {
                        Text(Strings.followRequestAccept(language), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                    OutlinedButton(
                        onClick = { onDeclineFollowRequest() },
                        shape  = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Divider),
                    ) {
                        Text(Strings.followRequestDecline(language), fontSize = 12.sp, color = OnBackground)
                    }
                }
            }
        }

        // Okunmamış nokta
        if (isUnread) {
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .padding(top = 6.dp)
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(iconColor),
            )
        }
    }

    HorizontalDivider(
        color     = Divider,
        thickness = 0.4.dp,
        modifier  = Modifier.padding(start = 74.dp),
    )
}
