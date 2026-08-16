package com.heftreng.app.ui.screens.feed

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import androidx.compose.runtime.*
import java.net.URLEncoder
import com.heftreng.app.util.LinkPreviewUtil
import com.heftreng.app.ui.component.LinkPreviewCard
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.heftreng.app.data.model.Comment
import com.heftreng.app.data.model.Post
import com.heftreng.app.navigation.Screen
import com.heftreng.app.ads.RemoteConfigManager
import com.heftreng.app.ui.component.AdSlotView
import com.heftreng.app.ui.i18n.Strings
import com.heftreng.app.viewmodel.AdsViewModel
import com.heftreng.app.viewmodel.AdminViewModel
import com.heftreng.app.viewmodel.BlogViewModel
import com.heftreng.app.ui.component.QuoteCard
import com.heftreng.app.ui.component.MentionSuggestionBar
import com.heftreng.app.util.MentionHelper
import com.heftreng.app.ui.component.LinkifyText
import com.heftreng.app.ui.component.FullScreenImageViewer
import com.heftreng.app.ui.component.QuoteDialog
import com.heftreng.app.ui.component.QuoteInputSection
import com.heftreng.app.ui.component.QuotePayload
import com.heftreng.app.ui.component.QuoteSuggestion
import com.heftreng.app.ui.component.SharePreviewDialog
import com.heftreng.app.utils.ShareTarget
import com.heftreng.app.ui.theme.*
import com.heftreng.app.ui.screens.social.LikerListSheet
import com.heftreng.app.viewmodel.CmsViewModel
import com.heftreng.app.viewmodel.FeedViewModel
import com.heftreng.app.viewmodel.SocialViewModel
import com.heftreng.app.viewmodel.SettingsViewModel
import kotlinx.coroutines.tasks.await
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import android.net.Uri
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import com.heftreng.app.data.model.AppConfig
import androidx.core.content.ContextCompat
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.graphics.graphicsLayer

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
@Composable
fun FeedScreen(
    navController    : NavController,
    language         : String = "tr",
    appConfig        : AppConfig = AppConfig(),
    initialSharedText: String? = null,
    initialSharedUri : android.net.Uri? = null,
    vm               : FeedViewModel  = hiltViewModel(),
    socialVm         : SocialViewModel = hiltViewModel(),
    adsVm            : AdsViewModel    = hiltViewModel(),
    settingsVm   : SettingsViewModel = hiltViewModel(),
    blogVm       : BlogViewModel    = hiltViewModel(),
    adminVm      : AdminViewModel   = hiltViewModel(),
    cmsVm        : CmsViewModel     = hiltViewModel(),
) {
    // FAZ 1 devamı: Moderatör/editör artık feed'i gezerken bir gönderiyi
    // doğrudan karttan kaldırabiliyor — önceden bunun için Admin
    // Paneli'ndeki "Bekleyenler" sekmesine gidip gönderiyi aramak
    // gerekiyordu. `perms.can("edit")` zaten moderatePost/restorePost'un
    // kendi içindeki kontrolle birebir aynı (bkz. AdminViewModel.kt).
    val adminPerms by adminVm.perms.collectAsState()
    LaunchedEffect(Unit) { adminVm.checkAdmin() }
    val canModeratePosts = adminPerms?.can("edit") == true
    val feedAnnouncement by cmsVm.activeFeedAnnouncement.collectAsState()
    LaunchedEffect(Unit) {
        if (cmsVm.activeFeedAnnouncement.value == null) {
            cmsVm.loadActiveFeedAnnouncement()
        }
    }
    val posts       by vm.posts.collectAsState()
    val repostError by vm.repostError.collectAsState()
    val suggestedUsers by vm.suggestedUsers.collectAsState()
    val loading     by vm.loading.collectAsState()
    val hasMore     by vm.hasMore.collectAsState()
    val loadingMore by vm.loadingMore.collectAsState()
    // Reklam planı: banner+native aynı çağrıda hesaplanır, çakışma yapısal
    // olarak imkansızdır (bkz. AdPlanner.kt). Ekran artık kendi index
    // formülünü yazmaz.
    val adConfigs by adsVm.allConfigs.collectAsState()
    // Bildirim ekranıyla aynı pattern: enabled + adConfigs key olarak kullanılır.
    // adConfigs Map referansı değişmeyebilir — enabled ayrı primitive key olarak eklendi.
    val adPlan = remember(
        posts.size,
        adConfigs[RemoteConfigManager.KEY_NATIVE_FEED]?.enabled,
        adConfigs[RemoteConfigManager.KEY_NATIVE_FEED]?.unitId,
        adConfigs[RemoteConfigManager.KEY_BANNER_FEED]?.enabled,
        adConfigs[RemoteConfigManager.KEY_BANNER_FEED]?.unitId,
        adConfigs,
    ) {
        adsVm.planFor(
            screenKey = "feed",
            itemCount = posts.size,
            nativeKey = RemoteConfigManager.KEY_NATIVE_FEED,
            bannerKey = RemoteConfigManager.KEY_BANNER_FEED,
        )
    }
    val blockedUsers by settingsVm.blockedUsers.collectAsState()

    val serverRefreshing by vm.serverRefreshing.collectAsState()
    val pendingNewPosts by vm.pendingNewPosts.collectAsState()
    var isRefreshing by remember { mutableStateOf(false) }
    val pullRefreshState = rememberPullRefreshState(
        refreshing = isRefreshing || serverRefreshing,
        onRefresh  = {
            isRefreshing = true
            vm.refresh(forceRefresh = true)
            // ÖNCEKİ HATA: Aşağı çekip yenileme sadece feed gönderilerini
            // tazeliyordu — "Önerilen Kullanıcılar" listesi 1 saatlik cache'i
            // yüzünden hiç yenilenmiyordu. Artık o da birlikte tazeleniyor.
            vm.loadSuggestedUsers(forceReload = true)
        }
    )
    // isRefreshing'i server refresh bitince kapat
    LaunchedEffect(serverRefreshing) { if (!serverRefreshing) isRefreshing = false }

    val currentUserUid = FirebaseAuth.getInstance().currentUser?.uid ?: ""
    LaunchedEffect(Unit) {
        settingsVm.loadBlockedUsers()
        // vm.refresh() — ViewModel init{} zaten çağırıyor; burada çift server read olurdu.
        // Pull-to-refresh veya manual tetik için vm.refresh(forceRefresh=true) kullanılır.
        vm.loadLibraryQuotes()
        vm.loadSuggestedUsers()
        vm.loadFollowingUids(currentUserUid)
        vm.loadFriendsReading()
        blogVm.loadPosts()
    }

    // DÜZELTME: Ekran kompozisyondan çıkınca pozisyon bazlı banner ve native
    // AdView'ları serbest bırak. Bunu yapmamak; her geri dönüşte hafızada
    // erişilmez durumda yüzlerce stale AdView bırakıyordu (bellek sızıntısı).
    DisposableEffect(Unit) {
        onDispose {
            adsVm.releaseBanners("feed_banner_")
            adsVm.releaseAllNatives("feed_native_")
        }
    }

    // ── Feed sekme (Herkes / Takip edilenler) ────────────────────────────────
    val ku = language == "ku"
    val feedTabs = listOf(
        Strings.filterAll(language),
        Strings.filterFollowing(language),
    )
    var selectedFeedTab by remember { mutableIntStateOf(0) }

    // Takip edilen UIDs — ViewModel'den (get() ile, listener yok)
    val followingUids by vm.followingUids.collectAsState()
    val friendsReading by vm.friendsReading.collectAsState()
    val blogState by blogVm.state.collectAsState()
    val displayedPosts = remember(posts, selectedFeedTab, followingUids, blockedUsers) {
        val blockedUids = blockedUsers.map { it.uid }.toSet()
        val filtered = posts.filter { it.uid !in blockedUids }
        if (selectedFeedTab == 1) filtered.filter { it.uid in followingUids }
        else filtered
    }

    // ── Şikayet dialog ──────────────────────────────────────────────────────
    var reportPostId     by remember { mutableStateOf<String?>(null) }
    var reportTargetUid  by remember { mutableStateOf("") }
    var reportTargetName by remember { mutableStateOf("") }

    // ── Engelleme confirm dialog ─────────────────────────────────────────────
    var blockTargetUid   by remember { mutableStateOf("") }
    var blockTargetName  by remember { mutableStateOf("") }
    var blockTargetPhoto by remember { mutableStateOf("") }
    var showBlockDialog  by remember { mutableStateOf(false) }

    var likersPostId     by remember { mutableStateOf<String?>(null) }
    val likers           by socialVm.likers.collectAsState()
    val socialLoading    by socialVm.loading.collectAsState()

    var inlineText       by remember { mutableStateOf("") }
    var inlineTitle      by remember { mutableStateOf("") }
    var inlineTopic      by remember { mutableStateOf("") }
    var inlineQuote      by remember { mutableStateOf<QuotePayload?>(null) }
    val mentionSuggestions by vm.mentionSuggestions.collectAsState()
    var inlineMentionedUids by remember { mutableStateOf(listOf<String>()) }
    LaunchedEffect(inlineText) {
        val atIndex = inlineText.lastIndexOf('@')
        if (atIndex < 0) { vm.clearMentionSuggestions(); return@LaunchedEffect }
        val afterAt = inlineText.substring(atIndex + 1)
        if (afterAt.contains(' ') || afterAt.isEmpty()) { vm.clearMentionSuggestions(); return@LaunchedEffect }
        vm.searchMentionUsers(afterAt)
    }
    fun onInlineMentionSelected(user: MentionHelper.MentionUser) {
        val atIndex = inlineText.lastIndexOf('@')
        if (atIndex >= 0) {
            val before = inlineText.substring(0, atIndex)
            inlineText = "$before@${user.username} "
            inlineMentionedUids = inlineMentionedUids + user.uid
        }
        vm.clearMentionSuggestions()
    }
    var showInlineQuote   by remember { mutableStateOf(false) }
    var inlineImageUri    by remember { mutableStateOf<Uri?>(null) }
    var inlineLinkPreview by remember { mutableStateOf<com.heftreng.app.util.LinkPreview?>(null) }
    var inlineLinkLoading by remember { mutableStateOf(false) }
    // Inline metin değişince link tespit et
    // Link tespiti — mention LaunchedEffect ile çakışmaması için ayrı key kullan
    LaunchedEffect(inlineText.length) {
        kotlinx.coroutines.delay(500) // debounce
        val url = com.heftreng.app.util.LinkPreviewUtil.extractUrl(inlineText)
        if (url != null && url != inlineLinkPreview?.url) {
            inlineLinkLoading = true
            inlineLinkPreview = com.heftreng.app.util.LinkPreviewUtil.fetchPreview(url)
            inlineLinkLoading = false
        } else if (url == null) {
            inlineLinkPreview = null
        }
    }
    // FAB menü state'leri
    var showFabMenu      by remember { mutableStateOf(false) }
    var showComposeDialog by remember { mutableStateOf(false) }
    var sharedText       by remember { mutableStateOf(initialSharedText ?: "") }
    var sharedImageUri   by remember { mutableStateOf(initialSharedUri) }

    // Paylaşım intent'i ile açıldıysa compose dialog'u otomatik aç
    LaunchedEffect(initialSharedText, initialSharedUri) {
        if (!initialSharedText.isNullOrBlank() || initialSharedUri != null) {
            showComposeDialog = true
        }
    }
    val uploading        by vm.uploading.collectAsState()
    val context          = LocalContext.current

    // Photo Picker — izin gerektirmez (Android 13+ politikası)
    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> inlineImageUri = uri }

    val currentUser = FirebaseAuth.getInstance().currentUser
    val myUid       = currentUser?.uid ?: ""

    // Firestore'dan güncel photoURL — Auth'daki eski kalabilir
    var myPhotoURL by remember { mutableStateOf(currentUser?.photoUrl?.toString() ?: "") }
    LaunchedEffect(myUid) {
        if (myUid.isNotEmpty()) {
            try {
                val doc = FirebaseFirestore.getInstance().collection("users").document(myUid).get().await()
                myPhotoURL = doc.getString("photoURL") ?: currentUser?.photoUrl?.toString() ?: ""
            } catch (_: Exception) {}
        }
    }

    // ── FAB Menü — bottom sheet ───────────────────────────────────────────────
    if (showFabMenu) {
        androidx.compose.material3.ModalBottomSheet(
            onDismissRequest   = { showFabMenu = false },
            containerColor     = HeftSurface,
            dragHandle         = {
                Box(
                    Modifier
                        .padding(top = 12.dp, bottom = 8.dp)
                        .width(40.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Muted.copy(alpha = 0.4f)),
                )
            },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    if (language == "ku") "Çi dixwazî parve bikî?" else "Ne paylaşmak istersin?",
                    color      = Muted,
                    fontSize   = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier   = Modifier.padding(bottom = 8.dp, top = 4.dp),
                )
                // ── Gönderi Yaz ──────────────────────────────────────────
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape    = RoundedCornerShape(14.dp),
                    color    = Background,
                    onClick  = { showFabMenu = false; showComposeDialog = true },
                ) {
                    Row(
                        modifier          = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .background(Primary.copy(alpha = 0.12f), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Default.Edit, null, tint = Primary, modifier = Modifier.size(20.dp))
                        }
                        Column {
                            Text(
                                if (language == "ku") "Nivîs Binivîse" else "Gönderi Yaz",
                                color      = OnBackground,
                                fontWeight = FontWeight.Bold,
                                fontSize   = 15.sp,
                            )
                            Text(
                                if (language == "ku") "Ramanên xwe parve bike" else "Düşüncelerini paylaş",
                                color    = Muted,
                                fontSize = 12.sp,
                            )
                        }
                    }
                }
                // ── Alıntı Paylaş ────────────────────────────────────────
                if (appConfig.feedAllowQuotes) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape    = RoundedCornerShape(14.dp),
                        color    = Background,
                        onClick  = { showFabMenu = false; showInlineQuote = true },
                    ) {
                        Row(
                            modifier          = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .background(Amber.copy(alpha = 0.12f), CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Default.FormatQuote, null, tint = Amber, modifier = Modifier.size(22.dp))
                            }
                            Column {
                                Text(
                                    if (language == "ku") "Jêgirt Parve Bike" else "Alıntı Paylaş",
                                    color      = OnBackground,
                                    fontWeight = FontWeight.Bold,
                                    fontSize   = 15.sp,
                                )
                                Text(
                                    if (language == "ku") "Jêgirtê pirtûkan zêde bike" else "Kitaptan bir alıntı ekle",
                                    color    = Muted,
                                    fontSize = 12.sp,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // ── Tam ekran compose dialog ──────────────────────────────────────────────
    if (showComposeDialog) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showComposeDialog = false },
            properties = androidx.compose.ui.window.DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows  = false,
            ),
        ) {
            Surface(modifier = Modifier.fillMaxSize(), color = Background) {
                Scaffold(
                    containerColor = Background,
                    topBar = {
                        TopAppBar(
                            navigationIcon = {
                                IconButton(onClick = { showComposeDialog = false }) {
                                    Icon(Icons.Default.Close, null, tint = OnBackground)
                                }
                            },
                            title = {
                                AsyncImage(
                                    model              = myPhotoURL.ifEmpty { null },
                                    contentDescription = null,
                                    modifier           = Modifier.size(30.dp).clip(CircleShape).background(SurfaceVar),
                                    contentScale       = ContentScale.Crop,
                                )
                            },
                            actions = {
                                val canSend = inlineText.isNotBlank() || inlineQuote != null || inlineImageUri != null
                                Button(
                                    onClick = {
                                        if (canSend) {
                                            val uri = inlineImageUri
                                            if (uri != null) {
                                                vm.uploadImageAndCreatePost(
                                                    imageUri   = uri,
                                                    text       = inlineText.trim(),
                                                    title      = inlineTitle.trim(),
                                                    category   = inlineTopic,
                                                    quoteText  = inlineQuote?.text ?: "",
                                                    authorName = inlineQuote?.authorName ?: "",
                                                    bookName   = inlineQuote?.bookName ?: "",
                                                    coverImg   = inlineQuote?.coverImg ?: "",
                                                    context    = context,
                                                )
                                            } else {
                                                vm.createPost(
                                                    text       = inlineText.trim(),
                                                    title      = inlineTitle.trim(),
                                                    category   = inlineTopic,
                                                    quoteText  = inlineQuote?.text ?: "",
                                                    authorName = inlineQuote?.authorName ?: "",
                                                    bookName   = inlineQuote?.bookName ?: "",
                                                    coverImg   = inlineQuote?.coverImg ?: "",
                                                    mentions   = inlineMentionedUids,
                                                    linkUrl    = inlineLinkPreview?.url ?: "",
                                                    linkTitle  = inlineLinkPreview?.title ?: "",
                                                    linkDesc   = inlineLinkPreview?.desc ?: "",
                                                    linkImage  = inlineLinkPreview?.image ?: "",
                                                    linkType   = inlineLinkPreview?.type ?: "",
                                                )
                                            }
                                            inlineText          = ""
                                            inlineTitle         = ""
                                            inlineTopic         = ""
                                            inlineQuote         = null
                                            inlineImageUri      = null
                                            inlineMentionedUids = emptyList()
                                            inlineLinkPreview   = null
                                            vm.clearMentionSuggestions()
                                            showComposeDialog = false
                                        }
                                    },
                                    enabled = canSend,
                                    shape   = RoundedCornerShape(20.dp),
                                    colors  = ButtonDefaults.buttonColors(
                                        containerColor         = Primary,
                                        disabledContainerColor = Primary.copy(alpha = 0.3f),
                                    ),
                                    modifier = Modifier.height(36.dp).padding(end = 8.dp),
                                ) {
                                    if (uploading) {
                                        CircularProgressIndicator(
                                            modifier    = Modifier.size(16.dp),
                                            color       = androidx.compose.ui.graphics.Color.White,
                                            strokeWidth = 2.dp,
                                        )
                                    } else {
                                        Text(
                                            if (language == "ku") "Parve Bike" else "Paylaş",
                                            fontWeight = FontWeight.Bold,
                                            fontSize   = 14.sp,
                                        )
                                    }
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(containerColor = HeftSurface),
                        )
                    },
                ) { pad ->
                    androidx.compose.foundation.lazy.LazyColumn(
                        modifier       = Modifier.fillMaxSize().padding(pad),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        item {
                            // ── Başlık alanı ───────────────────────────
                            BasicTextField(
                                value           = inlineTitle,
                                onValueChange   = { if (it.length <= 120) inlineTitle = it },
                                modifier        = Modifier.fillMaxWidth(),
                                textStyle       = androidx.compose.ui.text.TextStyle(
                                    color      = OnBackground,
                                    fontSize   = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                ),
                                cursorBrush     = androidx.compose.ui.graphics.SolidColor(Primary),
                                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                                singleLine      = true,
                                decorationBox   = { inner ->
                                    if (inlineTitle.isEmpty()) {
                                        Text(
                                            Strings.postTitleHint(language),
                                            color = Muted, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                                        )
                                    }
                                    inner()
                                },
                            )
                        }
                        item {
                            // ── Gövde alanı ────────────────────────────
                            BasicTextField(
                                value           = inlineText,
                                onValueChange   = { inlineText = it },
                                modifier        = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                                textStyle       = androidx.compose.ui.text.TextStyle(
                                    color    = OnBackground,
                                    fontSize = 16.sp,
                                    lineHeight = androidx.compose.ui.unit.TextUnit(24f, androidx.compose.ui.unit.TextUnitType.Sp),
                                ),
                                cursorBrush     = androidx.compose.ui.graphics.SolidColor(Primary),
                                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                                decorationBox   = { inner ->
                                    if (inlineText.isEmpty()) {
                                        Text(Strings.whatsOnMind(language), color = Muted, fontSize = 16.sp)
                                    }
                                    inner()
                                },
                            )
                        }
                        // ── Mention öneri barı ──────────────────────────
                        if (mentionSuggestions.isNotEmpty()) {
                            item {
                                MentionSuggestionBar(
                                    suggestions = mentionSuggestions,
                                    onSelect    = { onInlineMentionSelected(it) },
                                    modifier    = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                        // ── Alıntı önizleme ─────────────────────────────
                        if (inlineQuote != null) {
                            item {
                                QuoteInputSection(quote = inlineQuote, onRemove = { inlineQuote = null }, language = language)
                            }
                        }
                        if (inlineLinkPreview != null && inlineLinkPreview!!.url.isNotBlank()) {
                            item {
                                LinkPreviewCard(
                                    url       = inlineLinkPreview!!.url,
                                    title     = inlineLinkPreview!!.title,
                                    desc      = inlineLinkPreview!!.desc,
                                    image     = inlineLinkPreview!!.image,
                                    type      = inlineLinkPreview!!.type,
                                    youtubeId = inlineLinkPreview!!.youtubeId,
                                    modifier  = Modifier.padding(horizontal = 4.dp),
                                )
                            }
                        }
                        // ── Görsel önizleme ─────────────────────────────
                        if (inlineImageUri != null) {
                            item {
                                Box(modifier = Modifier.fillMaxWidth()) {
                                    AsyncImage(
                                        model              = inlineImageUri,
                                        contentDescription = null,
                                        modifier           = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp)),
                                        contentScale       = ContentScale.Crop,
                                    )
                                    IconButton(
                                        onClick  = { inlineImageUri = null },
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(4.dp)
                                            .size(28.dp)
                                            .background(Color.Black.copy(alpha = 0.5f), CircleShape),
                                    ) {
                                        Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(14.dp))
                                    }
                                }
                            }
                        }
                        // ── Konu seçici ──────────────────────────────────
                        item {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(Strings.postTopics) { key ->
                                    val selected = inlineTopic == key
                                    FilterChip(
                                        selected = selected,
                                        onClick  = { inlineTopic = if (selected) "" else key },
                                        label    = { Text(Strings.topicLabel(language, key), fontSize = 12.sp) },
                                        colors   = FilterChipDefaults.filterChipColors(
                                            containerColor         = SurfaceVar,
                                            labelColor             = Muted,
                                            selectedContainerColor = Primary.copy(alpha = 0.16f),
                                            selectedLabelColor     = Primary,
                                        ),
                                        border   = FilterChipDefaults.filterChipBorder(
                                            enabled = true, selected = selected,
                                            borderColor         = Divider,
                                            selectedBorderColor = Primary,
                                            borderWidth          = 1.dp,
                                            selectedBorderWidth  = 1.dp,
                                        ),
                                        modifier = Modifier.height(30.dp),
                                    )
                                }
                            }
                        }
                        // ── Sayaç + araç çubuğu ──────────────────────────
                        item {
                            HorizontalDivider(color = Divider, thickness = 0.5.dp)
                            Row(
                                modifier          = Modifier.fillMaxWidth().padding(top = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                // Alıntı ekle
                                if (appConfig.feedAllowQuotes) {
                                    IconButton(onClick = { showInlineQuote = true }, modifier = Modifier.size(36.dp)) {
                                        Icon(
                                            Icons.Default.FormatQuote, null,
                                            tint     = if (inlineQuote != null) Amber else Muted,
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }
                                }
                                // Görsel ekle
                                if (appConfig.feedShowImages) {
                                    IconButton(
                                        onClick  = {
                                            // Photo Picker — sistem galerisi açılır, izin gerekmez
                                            imagePicker.launch(
                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                            )
                                        },
                                        modifier = Modifier.size(36.dp),
                                    ) {
                                        Icon(
                                            Icons.Default.Image, null,
                                            tint     = if (inlineImageUri != null) Primary else Muted,
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }
                                }

                                Spacer(Modifier.weight(1f))
                                Text(
                                    "${inlineText.length}/1000",
                                    color    = if (inlineText.length > 900) Error else Muted,
                                    fontSize = 11.sp,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    
    if (showInlineQuote) {
        QuoteDialog(
            initialText   = inlineQuote?.text ?: "",
            initialTitle  = "",
            initialBook   = inlineQuote?.bookName ?: "",
            initialAuthor = inlineQuote?.authorName ?: "",
            language      = language,
            onDismiss     = { showInlineQuote = false },
            onConfirm     = { p ->
                vm.createPost(
                    text       = "",
                    title      = p.title,
                    quoteText  = p.text,
                    authorName = p.authorName,
                    bookName   = p.bookName,
                    coverImg   = p.coverImg,
                    type       = "library_quote",
                )
                showInlineQuote = false
            },
            onLookupCover   = { title -> vm.findCoverImgByTitle(title) },
            onSearchBooks   = { q -> vm.searchBooksForQuote(q) },
            onSearchAuthors = { q -> vm.searchAuthorsForQuote(q) },
        )
    }

    // Beğenenler sheet
    if (likersPostId != null) {
        LikerListSheet(
            title     = Strings.likedBy(language),
            likers    = likers,
            loading   = socialLoading,
            onDismiss = { likersPostId = null; socialVm.clearLikers() },
            onProfile = { uid -> likersPostId = null; navController.navigate("profile/$uid") },
        )
    }

    // ── Şikayet gönder ──────────────────────────────────────────────────────
    if (reportPostId != null) {
        ReportDialog(
            language     = language,
            targetName   = reportTargetName,
            onDismiss    = { reportPostId = null },
            onConfirm    = { reason ->
                val ruid = FirebaseAuth.getInstance().currentUser?.uid ?: ""
                val rname = FirebaseAuth.getInstance().currentUser?.displayName ?: ""
                FirebaseFirestore.getInstance().collection("reports").add(
                    hashMapOf(
                        "reporterUid"  to ruid,
                        "reporterName" to rname,
                        "targetUid"    to reportTargetUid,
                        "targetName"   to reportTargetName,
                        "targetPostId" to (reportPostId ?: ""),
                        "reason"       to reason,
                        "status"       to "pending",
                        "ts"           to com.google.firebase.Timestamp.now(),
                    )
                )
                reportPostId = null
            },
        )
    }

    // ── Engelleme onay dialog ────────────────────────────────────────────────
    if (showBlockDialog) {
        AlertDialog(
            onDismissRequest = { showBlockDialog = false },
            containerColor   = HeftSurface,
            title = { Text(Strings.blockUser(language), color = OnBackground, fontWeight = FontWeight.SemiBold) },
            text  = { Text(Strings.blockUserConfirm(language), color = Muted) },
            confirmButton = {
                TextButton(onClick = {
                    settingsVm.blockUser(blockTargetUid, blockTargetName, blockTargetPhoto)
                    showBlockDialog = false
                }) {
                    Text(Strings.blockUser(language), color = Color(0xFFEF4444))
                }
            },
            dismissButton = {
                TextButton(onClick = { showBlockDialog = false }) {
                    Text(Strings.cancel(language), color = Muted)
                }
            },
        )
    }

    // ÇÖZÜLDÜ (Play Console: "Uçtan uca ekran tüm kullanıcılara gösterilmeyebilir"):
    // targetSdk 35 ile Android 15+'ta edge-to-edge varsayılan hale geliyor.
    // Bu ekran statusBarsPadding() olmadan doğrudan en üstten başlıyordu —
    // üstteki TabRow (sekme satırı) durum çubuğunun (saat, batarya, sinyal
    // simgeleri) ARKASINDA/ALTINDA kalıp tıklanamaz veya görünmez oluyordu.
    // navigationBarsPadding() zaten aşağıda mevcut yerlerde vardı, üstte de
    // aynı mantıkla statusBarsPadding() ekleniyor.
    Box(modifier = Modifier.fillMaxSize().background(Background).imePadding()) {
        Column(Modifier.fillMaxSize()) {
            // ── Sekme satırı ────────────────────────────────────────────────
            TabRow(
                selectedTabIndex = selectedFeedTab,
                containerColor   = Background,
                contentColor     = Amber,
                divider          = { HorizontalDivider(color = Divider, thickness = 0.5.dp) },
                indicator        = { tabPositions ->
                    Box(
                        Modifier
                            .tabIndicatorOffset(tabPositions[selectedFeedTab])
                            .padding(horizontal = 28.dp)
                            .height(2.5.dp)
                            .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                            .background(
                                brush = androidx.compose.ui.graphics.Brush.linearGradient(
                                    listOf(Color(0xFF6C8EFF), Color(0xFF38BDF8))
                                )
                            )
                    )
                },
            ) {
                feedTabs.forEachIndexed { i, title ->
                    Tab(
                        selected               = selectedFeedTab == i,
                        onClick                = { selectedFeedTab = i },
                        text                   = {
                            Text(
                                title,
                                fontSize      = 13.sp,
                                fontWeight    = if (selectedFeedTab == i) FontWeight.Bold else FontWeight.Normal,
                                letterSpacing = 0.2.sp,
                            )
                        },
                        selectedContentColor   = OnBackground,
                        unselectedContentColor = Muted,
                    )
                }
            }

        if (loading && displayedPosts.isEmpty()) {
            androidx.compose.foundation.lazy.LazyColumn(
                Modifier.fillMaxSize(), userScrollEnabled = false,
            ) {
                items(5) { com.heftreng.app.ui.component.PostCardSkeleton() }
            }
        } else if (!loading && displayedPosts.isEmpty()) {
            // İlk kurulumda cache boşken server isteği zaman aşımına uğradıysa
            // (bkz. FeedViewModel.refresh() — withTimeoutOrNull(15_000L)) kullanıcı
            // artık sonsuz skeleton değil, net bir "tekrar dene" ekranı görüyor.
            Column(
                modifier             = Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment  = Alignment.CenterHorizontally,
                verticalArrangement  = Arrangement.Center,
            ) {
                Text(
                    com.heftreng.app.ui.i18n.Strings.noPosts(language),
                    fontSize   = 15.sp,
                    color      = Muted,
                    textAlign  = androidx.compose.ui.text.style.TextAlign.Center,
                )
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = { vm.refresh(forceRefresh = true) }) {
                    Text(com.heftreng.app.ui.i18n.Strings.tryAgain(language), color = Primary)
                }
            }
        } else {
            Box(Modifier.fillMaxSize().pullRefresh(pullRefreshState)) {
            val feedListState = rememberLazyListState()
            val feedScope = rememberCoroutineScope()

            // Pill tıklanıp pending commit edilince otomatik olarak en başa scroll et
            LaunchedEffect(pendingNewPosts.size) {
                if (pendingNewPosts.isEmpty() && feedListState.firstVisibleItemIndex > 0) {
                    feedListState.animateScrollToItem(0)
                }
            }

            // ── Reklam önden-ısıtma: TEK çağrı, motor kararı veriyor ─────────
            // Plan zaten hesaplanmış (adPlan) — hangi index'te ne var biliniyor.
            // warmVisiblePositions viewport'a göre dinamik pencere kullanır
            // (sabit "3 ileri" değil), bu yüzden hızlı scroll'da da reklamın
            // 1-3sn yükleme süresini karşılayacak kadar erken tetiklenir.
            // Ekran artık kendi index formülünü YAZMAZ, banner/native ayrı ayrı
            // ısıtılmaz — ikisi de aynı plan'dan, aynı çağrıdan gelir.
            LaunchedEffect(feedListState, adPlan, adPlan.isEmpty()) {
                if (adPlan.isEmpty()) return@LaunchedEffect
                adsVm.warmVisiblePositions(adPlan, firstVisibleIndex = 0, maxInitialAds = 3)
                snapshotFlow { feedListState.firstVisibleItemIndex }
                    .debounce(300L) // hızlı scroll'da her kart için istek atılmasın
                    .collect { firstVisible ->
                        adsVm.warmVisiblePositions(adPlan, firstVisibleIndex = firstVisible)
                    }
            }

            LazyColumn(
                state          = feedListState,
                modifier       = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 6.dp, bottom = 100.dp),
            ) {
                // ── Gönderi listesi ───────────────────────────────────

                // ── Blog Yazıları — heft-reng.blogspot.com içeriği ─────────
                if (selectedFeedTab == 0 && blogState.posts.isNotEmpty()) {
                    item(key = "blog_posts") {
                        BlogPostsStrip(
                            posts    = blogState.posts.take(8),
                            language = language,
                            onPostClick = { post -> navController.navigate("blog_post/${post.id}") },
                            onSeeAll    = { navController.navigate(Screen.Blog.route) },
                        )
                    }
                }

                // ── Duyuru Kutusu ───────────────────────────────────────────
                if (selectedFeedTab == 0) {
                    feedAnnouncement?.let { ann ->
                        item(key = "feed_announcement") {
                            FeedAnnouncementBanner(
                                announcement = ann,
                                onDismiss    = { cmsVm.dismissFeedAnnouncement() },
                            )
                        }
                    }
                }

                itemsIndexed(displayedPosts, key = { _, p -> p.id }) { postIndex, post ->
                    val context = LocalContext.current
                    var heartBurst by remember { mutableStateOf(false) }
                    var visible    by remember { mutableStateOf(false) }
                    var showUnrepostConfirm by remember(post.id) { mutableStateOf(false) }
                    LaunchedEffect(post.id) { visible = true }

                    val enterAlpha by animateFloatAsState(
                        targetValue   = if (visible) 1f else 0f,
                        animationSpec = tween(200, (postIndex * 28).coerceAtMost(160)),
                        label         = "enterAlpha",
                    )
                    val enterTranslation by animateFloatAsState(
                        targetValue   = if (visible) 0f else 24f,
                        animationSpec = androidx.compose.animation.core.spring(
                            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                            stiffness    = androidx.compose.animation.core.Spring.StiffnessMediumLow,
                        ),
                        label = "enterY",
                    )

                    Box(Modifier.graphicsLayer { alpha = enterAlpha; translationY = enterTranslation }) {
                    Box {
                    PostCard(
                        post      = post,
                        onLike    = {
                            com.heftreng.app.ui.component.triggerHaptic(context, com.heftreng.app.ui.component.HapticType.LIGHT)
                            vm.toggleLike(post)
                        },
                        onSave    = {
                            com.heftreng.app.ui.component.triggerHaptic(context, com.heftreng.app.ui.component.HapticType.MEDIUM)
                            vm.toggleSave(post)
                        },
                        onProfile = { navController.navigate(Screen.Profile.go(post.uid)) },
                        onComment = { navController.navigate(Screen.PostDetail.go(post.id)) },
                        onShare   = {
                            if (post.isRepostedByMe) showUnrepostConfirm = true
                            else vm.repost(post)
                        },
                        onDelete  = { vm.deletePost(post.id) },
                        onEdit    = { newTitle, newText -> vm.editPost(post.id, newTitle, newText) },
                        onEditQuote = { newQ, newB, newA -> vm.editQuote(post.id, newQ, newB, newA) },
                        canModerate = canModeratePosts,
                        isRemoved   = post.moderationStatus == "removed",
                        onModerate  = { status ->
                            if (status == "active") adminVm.restorePost(post.id, post.uid)
                            else adminVm.moderatePost(post.id, post.uid, post.displayName, status, "", "")
                        },
                        onTap        = { navController.navigate(Screen.PostDetail.go(post.id)) },
                        onDoubleTap  = {
                            if (!post.isLikedByMe) vm.toggleLike(post)
                            heartBurst = true
                            com.heftreng.app.ui.component.triggerHaptic(context, com.heftreng.app.ui.component.HapticType.DOUBLE)
                        },
                        onShowLikers = {
                            socialVm.loadPostLikers(post.id)
                            likersPostId = post.id
                        },
                        onTapAuthor = { _ ->
                            if (post.libraryAuthorId.isNotBlank())
                                navController.navigate("author_detail/${post.libraryAuthorId}")
                            else
                                navController.navigate("author_quotes/${URLEncoder.encode(post.authorName, "UTF-8")}")
                        },
                        onTapBook = { _ ->
                            if (post.libraryBookId.isNotBlank())
                                navController.navigate("library_book_detail/${post.libraryBookId}")
                            else
                                navController.navigate("book_quotes/${URLEncoder.encode(post.bookName, "UTF-8")}")
                        },
                        onTapRepost = { repostId, repostType ->
                            when (repostType) {
                                "feed"         -> navController.navigate(Screen.PostDetail.go(repostId))
                                "serial"       -> navController.navigate("serial/$repostId")
                                "chapter"      -> {
                                    // repostId = chapterId, serialId post'tan alınır
                                    val sid = post.serialId.ifBlank { "" }
                                    val cid = post.chapterId.ifBlank { repostId }
                                    if (sid.isNotBlank()) {
                                        navController.navigate("chapter/$sid/$cid")
                                    } else {
                                        navController.navigate("serial/${post.repostId}")
                                    }
                                }
                                "book_chapter" -> {
                                    val bid = post.serialId.ifBlank { "" }
                                    val cid = post.chapterId.ifBlank { repostId }
                                    if (bid.isNotBlank()) {
                                        navController.navigate("book_chapter/$bid/$cid")
                                    }
                                }
                                "blog"         -> navController.navigate("blog/$repostId")
                                "kf_lesson"    -> navController.navigate(Screen.Kurdi.openLesson(repostId))
                                "grammar"      -> navController.navigate(Screen.Kurdi.openGrammar(repostId))
                                "kf_achievement" -> navController.navigate(Screen.Kurdi.base())
                                else           -> navController.navigate(Screen.PostDetail.go(repostId))
                            }
                        },
                        onTapHashtag = { taggedPostId -> navController.navigate(Screen.PostDetail.go(taggedPostId)) },
                        onTapMention = { mentionedUid -> navController.navigate("profile/$mentionedUid") },
                        onReport  = {
                            reportPostId     = post.id
                            reportTargetUid  = post.uid
                            reportTargetName = post.displayName.ifBlank { post.name }
                        },
                        onBlock = {
                            blockTargetUid   = post.uid
                            blockTargetName  = post.displayName.ifBlank { post.name }
                            blockTargetPhoto = post.photoURL
                            showBlockDialog  = true
                        },
                        language = language,
                        appConfig = appConfig,
                    )
                    com.heftreng.app.ui.component.HeartBurstOverlay(
                        visible = heartBurst,
                        onEnd   = { heartBurst = false },
                    )
                    } // inner Box (heart burst)
                    } // outer Box (enter animation)

                    if (showUnrepostConfirm) {
                        AlertDialog(
                            onDismissRequest = { showUnrepostConfirm = false },
                            shape   = RoundedCornerShape(16.dp),
                            title   = { Text(Strings.undoRepostTitle(language)) },
                            text    = { Text(Strings.undoRepostBody(language)) },
                            confirmButton = {
                                TextButton(onClick = {
                                    vm.unrepost(post)
                                    showUnrepostConfirm = false
                                }) { Text(Strings.undoRepostConfirm(language), color = Error) }
                            },
                            dismissButton = {
                                TextButton(onClick = { showUnrepostConfirm = false }) { Text(Strings.cancel(language)) }
                            },
                        )
                    }

                    // Reklam yerleşimi tamamen adPlan'dan gelir — banner/native
                    // çakışması yapısal olarak imkansız çünkü plan tek geçişte,
                    // tek index havuzundan hesaplanmıştı (bkz. AdPlanner.kt).
                    // Ekran burada hiçbir index formülü YAZMAZ.
                    adPlan[postIndex]?.let { placement ->
                        AdSlotView(placement = placement, adsVm = adsVm, modifier = Modifier.fillMaxWidth())
                    }

                    HorizontalDivider(color = Divider, thickness = 0.5.dp)
                    // ── Arkadaşlar ne okuyor? — gönderi kartlarının arasına ────
                    if (selectedFeedTab == 0 && postIndex == 2 && friendsReading.isNotEmpty()) {
                        FriendsReadingStrip(
                            items    = friendsReading,
                            language = language,
                            onClick  = { item ->
                                if (item.source == "library") {
                                    navController.navigate("library_book_detail/${item.bookId}")
                                } else {
                                    navController.navigate("serial/${item.bookId}")
                                }
                            },
                            onAvatarClick = { uid -> navController.navigate(Screen.Profile.go(uid)) },
                        )
                    }

                    // ── Önerilen Kişiler — 6. postun hemen altına ────────────
                    if (selectedFeedTab == 0 && postIndex == 5 && suggestedUsers.isNotEmpty()) {
                        SuggestedUsersCarousel(
                            users      = suggestedUsers,
                            onFollow   = { uid -> vm.followSuggestedUser(uid) },
                            onNavigate = { uid -> navController.navigate(Screen.Profile.go(uid)) },
                            onSeeAll   = { navController.navigate(Screen.PeopleHub.go(2)) },
                            language   = language,
                        )
                    }
                }
                // ── Daha Fazla Göster ─────────────────────────────────────
                if (hasMore) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            if (loadingMore) {
                                com.heftreng.app.ui.component.PostCardSkeleton()
                            } else {
                                OutlinedButton(
                                    onClick = { vm.loadMore() },
                                    shape   = RoundedCornerShape(20.dp),
                                    border  = androidx.compose.foundation.BorderStroke(1.dp, Divider),
                                ) {
                                    Icon(Icons.Default.ExpandMore, null, tint = Muted, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        Strings.showMore(language),
                                        color = Muted, fontSize = 13.sp,
                                    )
                                }
                            }
                        }
                    }
                }

                // ── Feed Sonu ──────────────────────────────────────────────────
            }
                PullRefreshIndicator(
                    refreshing = isRefreshing,
                    state      = pullRefreshState,
                    modifier   = Modifier.align(Alignment.TopCenter),
                    contentColor = Primary,
                )

                // ── Twitter tarzı "yeni gönderi" pill ────────────────────────
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 12.dp),
                ) {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = pendingNewPosts.isNotEmpty() && !isRefreshing,
                        enter   = fadeIn() + slideInVertically(initialOffsetY = { -it }),
                        exit    = fadeOut() + slideOutVertically(targetOffsetY = { -it }),
                    ) {
                        Surface(
                            onClick = {
                                vm.commitPendingNewPosts()
                                feedScope.launch { feedListState.animateScrollToItem(0) }
                            },
                            shape           = RoundedCornerShape(50),
                            color           = Primary,
                            shadowElevation = 6.dp,
                            modifier        = Modifier.padding(horizontal = 16.dp),
                        ) {
                            Row(
                                modifier              = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
                                verticalAlignment     = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Icon(
                                    Icons.Default.KeyboardArrowUp,
                                    contentDescription = null,
                                    tint     = Color.White,
                                    modifier = Modifier.size(18.dp),
                                )
                                Text(
                                    text = if (language == "ku")
                                        "${pendingNewPosts.size} nivîsên nû"
                                    else
                                        "${pendingNewPosts.size} yeni gönderi",
                                    color      = Color.White,
                                    fontSize   = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                }

                // ── Compose FAB ───────────────────────────────────────────
                if (selectedFeedTab == 0) {
                    androidx.compose.material3.FloatingActionButton(
                        onClick          = { showFabMenu = true },
                        modifier         = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 20.dp, bottom = 24.dp),
                        containerColor   = Primary,
                        contentColor     = androidx.compose.ui.graphics.Color.White,
                        shape            = CircleShape,
                        elevation        = androidx.compose.material3.FloatingActionButtonDefaults.elevation(6.dp),
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = if (language == "ku") "Binivîse" else "Yaz",
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            } // pullRefresh Box
        }
        } // Column
    } // Box

    // GEÇİCİ DEBUG: repost hatasının gerçek mesajını göster
    if (repostError != null) {
        AlertDialog(
            onDismissRequest = { vm.clearRepostError() },
            title   = { Text("Repost Hatası (debug)") },
            text    = { Text(repostError ?: "") },
            confirmButton = {
                TextButton(onClick = { vm.clearRepostError() }) { Text("Tamam") }
            },
        )
    }
}

// ── ReportDialog ──────────────────────────────────────────────────────────────
@Composable
private fun ReportDialog(
    language   : String,
    targetName : String,
    onDismiss  : () -> Unit,
    onConfirm  : (String) -> Unit,
) {
    val ku = language == "ku"
    val reasons = if (ku) listOf(
        "Naveroka neguncan",
        "Spamê",
        "Hatefsozî",
        "Derew",
        "Yê din",
    ) else listOf(
        "Uygunsuz içerik",
        "Spam",
        "Nefret söylemi",
        "Yanlış bilgi",
        "Diğer",
    )
    var selected by remember { mutableStateOf(reasons.first()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = HeftSurface,
        title = {
            Text(
                Strings.reportPost(language),
                color = OnBackground, fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "${if (ku) "Hesab" else "Hesap"}: $targetName",
                    color = Muted, fontSize = 12.sp,
                )
                Spacer(Modifier.height(4.dp))
                reasons.forEach { reason ->
                    Row(
                        modifier          = Modifier.fillMaxWidth().clickable { selected = reason }.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = selected == reason,
                            onClick  = { selected = reason },
                            colors   = RadioButtonDefaults.colors(selectedColor = Amber),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(reason, color = OnBackground, fontSize = 14.sp)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selected) }) {
                Text(Strings.send(language), color = Amber, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(Strings.cancel(language), color = Muted)
            }
        },
    )
}

// ── InlineComposeBox — feed üstündeki hızlı paylaşım kutusu ──────────────────
@Composable
private fun InlineComposeBox(
    text          : String,
    onTextChange  : (String) -> Unit,
    title         : String        = "",
    onTitleChange : (String) -> Unit = {},
    topic         : String        = "",
    onTopicChange : (String) -> Unit = {},
    quote         : QuotePayload?,
    onQuoteAdd    : () -> Unit,
    onQuoteRemove : () -> Unit,
    onSend        : () -> Unit,
    photoURL      : String,
    language      : String,
    imageUri           : Uri?         = null,
    uploading          : Boolean      = false,
    onImagePick        : () -> Unit   = {},
    onImageClear       : () -> Unit   = {},
    mentionSuggestions : List<MentionHelper.MentionUser> = emptyList(),
    onMentionSelected  : (MentionHelper.MentionUser) -> Unit = {},
    // ÇÖZÜLDÜ: Klavye açılınca bu kutu LazyColumn'ın ilk item'ı olduğu için
    // görünür alanın dışına kayabiliyor, altındaki "Blog Yazıları" şeridi
    // klavyenin üstünde asılı/boş bir alanda render edilebiliyordu. listState
    // verilirse, metin alanlarından birine odaklanınca bu item'a (index 0)
    // otomatik scroll edilir — kutu her zaman klavyenin hemen üstünde, tam
    // görünür kalır.
    listState     : androidx.compose.foundation.lazy.LazyListState? = null,
) {
    Surface(
        modifier       = Modifier.fillMaxWidth().padding(12.dp),
        shape          = RoundedCornerShape(14.dp),
        color          = HeftSurface,
        border         = androidx.compose.foundation.BorderStroke(1.dp, Divider),
        tonalElevation = 0.dp,
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                verticalAlignment     = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AsyncImage(
                    model              = photoURL.ifEmpty { null },
                    contentDescription = null,
                    modifier           = Modifier.size(36.dp).clip(CircleShape).background(SurfaceVar),
                    contentScale       = ContentScale.Crop,
                )
                // ── Başlık + gövde tek, kutusuz bir yazım alanında birleşik ──────────
                Column(modifier = Modifier.weight(1f)) {
                    // ÇÖZÜLDÜ: metin alanlarından birine odaklanınca, bu kutu
                    // (LazyColumn'ın ilk item'ı) her zaman görünür kalsın diye
                    // ilk pozisyona scroll ediliyor — klavye açıkken altındaki
                    // Blog Yazıları şeridinin görünmez/boş bir alanda asılı
                    // kalması engellenir.
                    val focusScope = androidx.compose.runtime.rememberCoroutineScope()
                    BasicTextField(
                        value           = title,
                        onValueChange   = { if (it.length <= 120) onTitleChange(it) },
                        modifier        = Modifier.fillMaxWidth()
                            .onFocusEvent { state ->
                                if (state.isFocused) {
                                    focusScope.launch { listState?.animateScrollToItem(0) }
                                }
                            },
                        textStyle       = androidx.compose.ui.text.TextStyle(
                            color      = OnBackground,
                            fontSize   = 16.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                        cursorBrush     = androidx.compose.ui.graphics.SolidColor(Primary),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        singleLine      = true,
                        decorationBox   = { inner ->
                            if (title.isEmpty()) {
                                Text(Strings.postTitleHint(language), color = Muted, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                            inner()
                        },
                    )
                    BasicTextField(
                        value           = text,
                        onValueChange   = onTextChange,
                        modifier        = Modifier.fillMaxWidth().heightIn(min = 56.dp, max = 200.dp).padding(top = 4.dp)
                            .onFocusEvent { state ->
                                if (state.isFocused) {
                                    focusScope.launch { listState?.animateScrollToItem(0) }
                                }
                            },
                        textStyle       = androidx.compose.ui.text.TextStyle(color = OnBackground, fontSize = 15.sp),
                        cursorBrush     = androidx.compose.ui.graphics.SolidColor(Primary),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        decorationBox   = { inner ->
                            if (text.isEmpty()) {
                                Text(Strings.whatsOnMind(language), color = Muted, fontSize = 15.sp)
                            }
                            inner()
                        },
                    )
                }
            }
            if (quote != null) {
                Spacer(Modifier.height(8.dp))
                QuoteInputSection(quote = quote, onRemove = onQuoteRemove, language = language)
            }
            if (imageUri != null) {
                Spacer(Modifier.height(8.dp))
                Box(modifier = Modifier.fillMaxWidth()) {
                    AsyncImage(
                        model            = imageUri,
                        contentDescription = null,
                        modifier         = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp)
                            .clip(RoundedCornerShape(10.dp)),
                        contentScale     = ContentScale.Crop,
                    )
                    IconButton(
                        onClick  = onImageClear,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(28.dp)
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape),
                    ) {
                        Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(14.dp))
                    }
                }
            }
            // ── Mention öneri barı ─────────────────────────────────────────────────
            MentionSuggestionBar(
                suggestions = mentionSuggestions,
                onSelect    = onMentionSelected,
                modifier    = Modifier.fillMaxWidth(),
            )
            // ── Konu seçici — opsiyonel chip listesi ──────────────────────────────
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(Strings.postTopics) { key ->
                    val selected = topic == key
                    FilterChip(
                        selected = selected,
                        onClick  = { onTopicChange(if (selected) "" else key) },
                        label    = { Text(Strings.topicLabel(language, key), fontSize = 12.sp) },
                        colors   = FilterChipDefaults.filterChipColors(
                            containerColor          = SurfaceVar,
                            labelColor              = Muted,
                            selectedContainerColor  = Primary.copy(alpha = 0.16f),
                            selectedLabelColor      = Primary,
                        ),
                        border   = FilterChipDefaults.filterChipBorder(
                            enabled = true, selected = selected,
                            borderColor         = Divider,
                            selectedBorderColor = Primary,
                            borderWidth          = 1.dp,
                            selectedBorderWidth  = 1.dp,
                        ),
                        modifier = Modifier.height(30.dp),
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(
                modifier          = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onQuoteAdd, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.FormatQuote, null,
                        tint     = if (quote != null) Primary else Muted,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Text(
                    Strings.addQuote(language),
                    color    = if (quote != null) Primary else Muted,
                    fontSize = 11.sp,
                    modifier = Modifier.clickable { onQuoteAdd() },
                )
                Spacer(Modifier.width(12.dp))
                IconButton(onClick = onImagePick, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.Image, null,
                        tint     = if (imageUri != null) Primary else Muted,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Spacer(Modifier.weight(1f))
                if (uploading) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Primary, strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    "${text.length}/1000",
                    color    = if (text.length > 900) Error else Muted,
                    fontSize = 11.sp,
                )
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick        = onSend,
                    enabled        = text.isNotBlank() || quote != null,
                    shape          = RoundedCornerShape(99.dp),
                    colors         = ButtonDefaults.buttonColors(
                        containerColor         = Primary,
                        contentColor           = Color.White,
                        disabledContainerColor = Divider,
                        disabledContentColor   = Muted,
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 7.dp),
                    modifier       = Modifier.height(34.dp),
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(5.dp))
                    Text(
                        Strings.share(language),
                        fontSize   = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

// ── ComposeBottomSheet — tam compose modal ────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ComposeBottomSheet(
    language        : String,
    currentUser     : FirebaseUser?,
    onDismiss       : () -> Unit,
    onPost          : (String, QuotePayload?, com.heftreng.app.util.LinkPreview?) -> Unit,
    onSearchBooks   : (suspend (String) -> List<QuoteSuggestion>)? = null,
    onSearchAuthors : (suspend (String) -> List<QuoteSuggestion>)? = null,
) {
    var text         by remember { mutableStateOf("") }
    var quotePayload by remember { mutableStateOf<QuotePayload?>(null) }
    var showQuote    by remember { mutableStateOf(false) }
    var linkPreview  by remember { mutableStateOf<com.heftreng.app.util.LinkPreview?>(null) }
    var linkLoading  by remember { mutableStateOf(false) }

    // Metinde URL tespit edilince önizleme çek
    LaunchedEffect(text) {
        val url = LinkPreviewUtil.extractUrl(text)
        if (url != null && url != linkPreview?.url) {
            linkLoading = true
            linkPreview = LinkPreviewUtil.fetchPreview(url)
            linkLoading = false
        } else if (url == null) {
            linkPreview = null
        }
    }

    if (showQuote) {
        QuoteDialog(
            initialText     = quotePayload?.text ?: "",
            initialBook     = quotePayload?.bookName ?: "",
            initialAuthor   = quotePayload?.authorName ?: "",
            language        = language,
            onDismiss       = { showQuote = false },
            onConfirm       = { p -> quotePayload = p; showQuote = false },
            onSearchBooks   = onSearchBooks,
            onSearchAuthors = onSearchAuthors,
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor   = HeftSurface,
        dragHandle       = { BottomSheetDefaults.DragHandle(color = Divider) },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding(),
        ) {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismiss) {
                    Text(Strings.cancel(language), color = Muted)
                }
                Text(
                    Strings.newPost(language),
                    fontWeight = FontWeight.SemiBold,
                    color      = OnBackground,
                    fontSize   = 15.sp,
                )
                TextButton(
                    onClick  = { if (text.isNotBlank() || quotePayload != null) onPost(text.trim(), quotePayload, linkPreview) },
                    enabled  = text.isNotBlank() || quotePayload != null,
                ) {
                    Text(
                        Strings.share(language),
                        color      = if (text.isNotBlank() || quotePayload != null) Primary else Muted,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier              = Modifier.fillMaxWidth(),
                verticalAlignment     = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AsyncImage(
                    model              = currentUser?.photoUrl,
                    contentDescription = null,
                    modifier           = Modifier.size(40.dp).clip(CircleShape).background(SurfaceVar),
                    contentScale       = ContentScale.Crop,
                )
                OutlinedTextField(
                    value           = text,
                    onValueChange   = { text = it },
                    placeholder     = {
                        Text(Strings.whatsOnMind(language), color = Muted)
                    },
                    modifier        = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                    colors          = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor      = Primary,
                        unfocusedBorderColor    = Divider,
                        focusedTextColor        = OnBackground,
                        unfocusedTextColor      = OnBackground,
                        unfocusedContainerColor = HeftSurface,
                        focusedContainerColor   = HeftSurface,
                        cursorColor             = Primary,
                    ),
                    shape           = RoundedCornerShape(12.dp),
                    maxLines        = 12,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )
            }
            if (quotePayload != null) {
                Spacer(Modifier.height(10.dp))
                QuoteInputSection(quote = quotePayload, onRemove = { quotePayload = null }, language = language)
            }
            if (linkPreview != null && linkPreview!!.url.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                LinkPreviewCard(
                    url       = linkPreview!!.url,
                    title     = linkPreview!!.title,
                    desc      = linkPreview!!.desc,
                    image     = linkPreview!!.image,
                    type      = linkPreview!!.type,
                    youtubeId = linkPreview!!.youtubeId,
                )
            }
            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = Divider, thickness = 0.5.dp)
            Spacer(Modifier.height(8.dp))
            Row(
                modifier          = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { showQuote = true }) {
                    Icon(
                        Icons.Default.FormatQuote, null,
                        tint     = if (quotePayload != null) Primary else Muted,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Text(
                    Strings.addQuote(language),
                    color    = if (quotePayload != null) Primary else Muted,
                    fontSize = 12.sp,
                    modifier = Modifier.clickable { showQuote = true },
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "${text.length}/1000",
                    color    = if (text.length > 900) Error else Muted,
                    fontSize = 11.sp,
                )
            }
        }
    }
}

// ── PostCard ──────────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PostCard(
    post      : Post,
    onLike    : () -> Unit,
    onSave    : () -> Unit,
    onProfile : () -> Unit,
    onComment : () -> Unit,
    onShare      : () -> Unit,
    onDelete     : (() -> Unit)? = null,
    onEdit       : ((title: String, text: String) -> Unit)? = null,
    onEditQuote  : ((quoteText: String, bookName: String, authorName: String) -> Unit)? = null,
    onTap        : (() -> Unit)? = null,
    onDoubleTap  : (() -> Unit)? = null,
    onQuote      : (() -> Unit)? = null,
    onStoryShare : (() -> Unit)? = null,
    onShowLikers : (() -> Unit)? = null,
    onTapAuthor  : ((String) -> Unit)? = null,
    onTapBook    : ((String) -> Unit)? = null,
    onTapRepost  : ((postId: String, type: String) -> Unit)? = null,
    onTapHashtag : ((postId: String) -> Unit)? = null,
    onTapMention : ((uid: String) -> Unit)? = null,
    onReport     : (() -> Unit)? = null,
    onBlock      : (() -> Unit)? = null,
    // FAZ 1 devamı: moderatör/editör hızlı işlem menüsü — bkz. FeedScreen
    // çağrı noktasındaki açıklama.
    canModerate    : Boolean = false,
    isRemoved      : Boolean = false,
    onModerate     : ((status: String) -> Unit)? = null,
    language       : String = "tr",
    isDetailScreen : Boolean = false,
    appConfig      : AppConfig = AppConfig(),
) {
    val ku = language == "ku"
    val myUid            = FirebaseAuth.getInstance().currentUser?.uid ?: ""
    val isOwn            = post.uid == myUid
    var menuExpanded     by remember { mutableStateOf(false) }
    var showEditDialog      by remember { mutableStateOf(false) }
    var showEditQuoteDialog by remember { mutableStateOf(false) }
    val isQuotePost = post.quoteText.isNotBlank()
    var showDeleteDialog by remember { mutableStateOf(false) }
    var shareTarget      by remember { mutableStateOf<ShareTarget?>(null) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 5.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(HeftCard)
            .border(0.7.dp, Divider, RoundedCornerShape(18.dp))
            .then(
                if (onDoubleTap != null)
                    Modifier.combinedClickable(
                        onClick       = { onTap?.invoke() },
                        onDoubleClick = { onDoubleTap() },
                    )
                else Modifier
            )
            .padding(horizontal = 15.dp, vertical = 13.dp),
    ) {
        // Header
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(
                modifier          = Modifier.weight(1f).clickable { onProfile() },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(brush = androidx.compose.ui.graphics.Brush.linearGradient(listOf(GradientStart, GradientEnd)))
                        .padding(1.5.dp)
                        .clip(CircleShape)
                        .background(SurfaceVar),
                    contentAlignment = Alignment.Center,
                ) {
                    if (post.photoURL.isNotBlank()) {
                        AsyncImage(
                            model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                                .data(post.photoURL)
                                .crossfade(true)
                                .build(),
                            contentDescription = post.displayName,
                            modifier           = Modifier.fillMaxSize(),
                            contentScale       = ContentScale.Crop,
                        )
                    }
                    // Fotoğraf yoksa / yüklenemezse baş harf
                    if (post.photoURL.isBlank()) {
                        Text(
                            post.displayName.firstOrNull()?.uppercase() ?: "?",
                            color      = OnBackground,
                            fontWeight = FontWeight.Bold,
                            fontSize   = 15.sp,
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(post.displayName.ifBlank { Strings.anonymous(language) }, fontWeight = FontWeight.Bold, color = OnBackground, fontSize = 14.sp)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            if (post.username.isNotBlank()) "@${post.username}" else "—",
                            color = Muted, fontSize = 12.sp,
                        )
                        if (post.ts != null) {
                            Text("·", color = Muted, fontSize = 12.sp)
                            Text(postTimeAgo(post.ts.seconds, ku), color = Muted, fontSize = 12.sp)
                        }
                    }
                }
            }
            Box {
                val clipboard = LocalClipboardManager.current
                val ctxForCopy = LocalContext.current
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = if (ku) "Vebijêrk" else "Seçenekler", tint = Muted)
                }
                DropdownMenu(
                    expanded         = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    containerColor   = HeftSurface,
                ) {
                    if (isOwn) {
                        DropdownMenuItem(
                            text        = { Text(Strings.edit(language), color = OnBackground) },
                            leadingIcon = { Icon(Icons.Default.Create, null, tint = Muted) },
                            onClick     = {
                                menuExpanded = false
                                if (isQuotePost) showEditQuoteDialog = true
                                else showEditDialog = true
                            },
                        )
                        DropdownMenuItem(
                            text        = { Text(Strings.delete(language), color = Color(0xFFEF4444)) },
                            leadingIcon = { Icon(Icons.Default.Delete, null, tint = Color(0xFFEF4444)) },
                            onClick     = { menuExpanded = false; showDeleteDialog = true },
                        )
                        HorizontalDivider(color = Divider, thickness = 0.5.dp)
                    } else {
                        DropdownMenuItem(
                            text        = { Text(Strings.repost(language), color = OnBackground) },
                            leadingIcon = { Icon(Icons.Default.Repeat, null, tint = Muted) },
                            onClick     = { menuExpanded = false; onShare() },
                        )
                        HorizontalDivider(color = Divider, thickness = 0.5.dp)
                    }
                    // Dış paylaşım
                    DropdownMenuItem(
                        text        = { Text(if (ku) "Di WhatsApp'ê de Parve Bike" else "WhatsApp'ta Paylaş", color = OnBackground) },
                        leadingIcon = {
                            Icon(Icons.Default.Share, null,
                                tint = Color(0xFF25D366), modifier = Modifier.size(20.dp))
                        },
                        onClick     = { menuExpanded = false; shareTarget = ShareTarget.WHATSAPP },
                    )
                    DropdownMenuItem(
                        text        = { Text(if (ku) "Di Instagram'ê de Parve Bike" else "Instagram'da Paylaş", color = OnBackground) },
                        leadingIcon = {
                            Icon(Icons.Default.Share, null,
                                tint = Color(0xFFE1306C), modifier = Modifier.size(20.dp))
                        },
                        onClick     = { menuExpanded = false; shareTarget = ShareTarget.INSTAGRAM },
                    )
                    DropdownMenuItem(
                        text        = { Text(if (ku) "Sepanên Din" else "Diğer Uygulamalar", color = OnBackground) },
                        leadingIcon = { Icon(Icons.Default.IosShare, null, tint = Muted) },
                        onClick     = { menuExpanded = false; shareTarget = ShareTarget.ANY },
                    )
                    HorizontalDivider(color = Divider, thickness = 0.5.dp)
                    DropdownMenuItem(
                        text        = { Text(Strings.copyPostLink(language), color = OnBackground) },
                        leadingIcon = { Icon(Icons.Default.ContentCopy, null, tint = Muted) },
                        onClick     = {
                            menuExpanded = false
                            val link = "https://heftreng.onrender.com/post/${post.id}"
                            clipboard.setText(AnnotatedString(link))
                            android.widget.Toast.makeText(
                                ctxForCopy,
                                Strings.linkCopied(language),
                                android.widget.Toast.LENGTH_SHORT,
                            ).show()
                        },
                    )
                    if (!isOwn) {
                        HorizontalDivider(color = Divider, thickness = 0.5.dp)
                        DropdownMenuItem(
                            text        = { Text(Strings.reportPost(language), color = Color(0xFFEF4444)) },
                            leadingIcon = { Icon(Icons.Default.Flag, null, tint = Color(0xFFEF4444)) },
                            onClick     = {
                                menuExpanded = false
                                onReport?.invoke()
                            },
                        )
                        DropdownMenuItem(
                            text        = { Text(Strings.blockUser(language), color = Color(0xFFEF4444)) },
                            leadingIcon = { Icon(Icons.Default.Block, null, tint = Color(0xFFEF4444)) },
                            onClick     = {
                                menuExpanded = false
                                onBlock?.invoke()
                            },
                        )
                        // FAZ 1 devamı: moderatör/editör için hızlı kaldırma —
                        // önceden bu işlem sadece Admin Paneli'nden mümkündü,
                        // moderatör feed'i gezerken karttan doğrudan işlem
                        // yapamıyordu.
                        if (canModerate && onModerate != null) {
                            HorizontalDivider(color = Divider, thickness = 0.5.dp)
                            if (isRemoved) {
                                DropdownMenuItem(
                                    text        = { Text(if (ku) "Ji Nû Ve Çalak Bike" else "Gönderiyi Geri Getir", color = Color(0xFF10B981)) },
                                    leadingIcon = { Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF10B981)) },
                                    onClick     = {
                                        menuExpanded = false
                                        onModerate("active")
                                    },
                                )
                            } else {
                                DropdownMenuItem(
                                    text        = { Text(if (ku) "Post Rake (Moderator)" else "Gönderiyi Kaldır (Moderatör)", color = Color(0xFFEF4444)) },
                                    leadingIcon = { Icon(Icons.Default.Gavel, null, tint = Color(0xFFEF4444)) },
                                    onClick     = {
                                        menuExpanded = false
                                        onModerate("removed")
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // İçerik — tıklanınca tekil gönderi ekranına
        Column(
            modifier = if (onTap != null)
                Modifier.fillMaxWidth().clickable { onTap() }
            else
                Modifier.fillMaxWidth()
        ) {
            if (post.quoteText.isNotBlank()) {
                QuoteCard(
                    quoteText       = post.quoteText,
                    bookName        = post.bookName,
                    authorName      = post.authorName,
                    coverImg        = post.coverImg,
                    language        = language,
                    onTapBook       = onTapBook,
                    onTapAuthor     = onTapAuthor,
                    expandByDefault = isDetailScreen,
                    modifier        = Modifier.padding(bottom = 8.dp),
                )
            }
            if (post.category.isNotBlank()) {
                Surface(
                    color    = Primary.copy(alpha = 0.12f),
                    shape    = RoundedCornerShape(99.dp),
                    modifier = Modifier.padding(bottom = 4.dp),
                ) {
                    Text(
                        Strings.topicLabel(language, post.category),
                        color      = Primary,
                        fontSize   = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier   = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }
            if (post.title.isNotBlank()) {
                Text(
                    post.title,
                    color      = OnBackground,
                    fontSize   = 16.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 21.sp,
                    modifier   = Modifier.padding(bottom = 4.dp),
                )
            }
            if (post.text.isNotBlank()) {
                LinkifyText(
                    post.text, fontSize = 15.sp, lineHeight = 22.sp,
                    expandable     = !isDetailScreen,
                    language       = language,
                    onHashtagClick = onTapHashtag,
                    mentionUids    = post.mentions,
                    onMentionClick = onTapMention,
                )
                Spacer(Modifier.height(8.dp))
            }
                        if (post.repostType.isNotBlank() && post.repostType != "feed" && post.repostType != "kf_achievement") {
                Surface(shape = RoundedCornerShape(6.dp), color = Primary.copy(alpha = 0.12f),
                    modifier = Modifier.padding(bottom = 6.dp)) {
                    Text(when(post.repostType){
                        "serial"->if(ku)"📖 Pirtûk" else "📖 Kitap"
                        "chapter"->if(ku)"📄 Beş" else "📄 Bölüm"
                        "book_chapter"->if(ku)"📄 Beşa Pirtûkê" else "📄 Kitap Bölümü"
                        "blog"->if(ku)"📝 Gotar" else "📝 Blog"
                        "kf_lesson"->if(ku)"🇰🇺 Dersa Kurdî" else "🇰🇺 Kurdî Ders"
                        "grammar"->if(ku)"📚 Rêziman" else "📚 Dilbilgisi"
                        "kf_achievement"->if(ku)"🏆 Serkeftin" else "🏆 Başarı"
                        else->post.repostType
                    },
                        color = Primary, fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                }
            }
            // Kurdî başarı kartı (seviye/XP/streak) — özel görsel tasarım, tamamen dile göre üretilir
            if (post.repostType == "kf_achievement") {
                Surface(
                    shape    = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .then(
                            if (onTapRepost != null)
                                Modifier.clickable { onTapRepost(post.repostId, post.repostType) }
                            else Modifier
                        ),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFFF5A623), Color(0xFFE8871E), Color(0xFFD9691B)),
                                ),
                            )
                            .padding(18.dp),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("🏆", fontSize = 28.sp)
                                Text(
                                    Strings.achievementLevelLabel(language, post.repostLevel),
                                    color      = Color.White,
                                    fontSize   = 18.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                                Column {
                                    Text("${post.repostXp}", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                    Text(Strings.xpLabel(language), color = Color.White.copy(alpha = 0.85f), fontSize = 11.sp)
                                }
                                Column {
                                    Text("${post.repostStreak}", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                    Text(Strings.streakDaysLabel(language), color = Color.White.copy(alpha = 0.85f), fontSize = 11.sp)
                                }
                            }
                            Text(
                                Strings.achievementCaption(language),
                                color      = Color.White.copy(alpha = 0.9f),
                                fontSize   = 11.5.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
            }
            // Repost embed kartı — tema buildRpEmbed() ile senkron
            if (appConfig.feedShowReposts && post.repostType.isNotBlank() && post.repostType != "kf_achievement") {
                Surface(
                    shape    = RoundedCornerShape(13.dp),
                    color    = SurfaceVar,
                    border   = androidx.compose.foundation.BorderStroke(0.5.dp, Divider),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .then(
                            if (post.repostId.isNotBlank() && onTapRepost != null)
                                Modifier.clickable { onTapRepost(post.repostId, post.repostType) }
                            else Modifier
                        ),
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        // Tip etiketi — tema: rp-embed-lbl
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(
                                when (post.repostType) {
                                    "serial"       -> Icons.Outlined.MenuBook
                                    "book_chapter" -> Icons.Outlined.MenuBook
                                    "blog"         -> Icons.Outlined.Article
                                    "kf_lesson"    -> Icons.Outlined.School
                                    "grammar"      -> Icons.Outlined.MenuBook
                                    "kf_achievement" -> Icons.Outlined.EmojiEvents
                                    else           -> Icons.Default.Repeat
                                },
                                contentDescription = null, tint = Primary, modifier = Modifier.size(11.dp),
                            )
                            Text(
                                when (post.repostType) {
                                    "serial"       -> if (ku) "Pirtûk" else "Kitap"
                                    "book_chapter" -> if (ku) "Beşa Pirtûkê" else "Kitap Bölümü"
                                    "blog"         -> if (ku) "Gotara Blogê" else "Blog Yazısı"
                                    "feed"         -> if (ku) "Dîsa Parvekirî" else "Paylaşım"
                                    "kf_lesson"    -> if (ku) "Dersa Kurdî" else "Kurdî Dersi"
                                    "grammar"      -> if (ku) "Rêziman" else "Dilbilgisi"
                                    "kf_achievement" -> if (ku) "Serkeftin" else "Başarı"
                                    else           -> if (ku) "Parvekirî" else "Paylaşım"
                                },
                                color = Primary, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp,
                            )
                        }
                        // Feed repostu: orijinal yazar avatarı + adı — tema: rp-meta
                        if (post.repostType == "feed" && post.repostAuthor.isNotBlank()) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                Box(
                                    modifier = Modifier.size(16.dp).clip(CircleShape).background(SurfaceVar),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (post.repostAuthorPhoto.isNotBlank()) {
                                        AsyncImage(model = post.repostAuthorPhoto, contentDescription = null,
                                            modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                    } else {
                                        Text(post.repostAuthor.firstOrNull()?.uppercase() ?: "?",
                                            color = OnBackground, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                                Text(post.repostAuthor, color = OnSurface, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        // İçerik
                        if (post.repostTitle.isNotBlank())  Text(post.repostTitle,  color = OnBackground, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
                        if (post.serialTitle.isNotBlank())  Text(post.serialTitle,  color = OnBackground, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
                        if (post.chapterTitle.isNotBlank()) Text("${Strings.chapter(language)} ${post.chapterOrder}: ${post.chapterTitle}", color = Muted, fontSize = 12.sp)
                        if (post.repostText.isNotBlank())   Text(post.repostText,   color = OnSurface,    fontSize = 13.sp, maxLines = if (isDetailScreen) Int.MAX_VALUE else 4, lineHeight = 19.sp)
                        val rImg = listOf(post.repostImg, post.serialCover).firstOrNull { it.isNotBlank() } ?: ""
                        if (rImg.isNotBlank()) {
                            AsyncImage(model = rImg, contentDescription = null,
                                modifier = Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop)
                        }
                    }
                }
            }
            val displayImg = post.imgUrl.ifBlank { post.imageURL }
            if (displayImg.isNotBlank()) {
                var showImg by remember { mutableStateOf(false) }
                AsyncImage(
                    model              = displayImg,
                    contentDescription = null,
                    modifier           = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { showImg = true },
                    contentScale       = ContentScale.Crop,
                )
                if (showImg) FullScreenImageViewer(url = displayImg) { showImg = false }
                Spacer(Modifier.height(8.dp))
            }

            // Link önizleme — görsel yoksa ve link varsa göster
            if (displayImg.isBlank() && post.linkUrl.isNotBlank()) {
                val ytId = if (post.linkType == "youtube")
                    com.heftreng.app.util.LinkPreviewUtil.extractYoutubeId(post.linkUrl)
                else ""
                LinkPreviewCard(
                    url       = post.linkUrl,
                    title     = post.linkTitle,
                    desc      = post.linkDesc,
                    image     = post.linkImage,
                    type      = post.linkType,
                    youtubeId = ytId,
                    modifier  = Modifier.padding(bottom = 8.dp),
                )
            }
        }

        // Aksiyonlar
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            val likeScale by animateFloatAsState(
                targetValue   = if (post.isLikedByMe) 1.35f else 1f,
                animationSpec = spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMediumLow),
                label         = "likeScale",
            )
            val likeColor by animateColorAsState(
                targetValue   = if (post.isLikedByMe) Color(0xFFFF3A5C) else Muted,
                animationSpec = tween(180),
                label         = "likeColor",
            )
            val saveScale by animateFloatAsState(
                targetValue   = if (post.isSavedByMe) 1.25f else 1f,
                animationSpec = spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMedium),
                label         = "saveScale",
            )
            val saveColor by animateColorAsState(
                targetValue   = if (post.isSavedByMe) Amber else Muted,
                animationSpec = tween(180),
                label         = "saveColor",
            )
            IconButton(onClick = onLike) {
                Icon(
                    if (post.isLikedByMe) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = Strings.likeAction(language),
                    tint               = likeColor,
                    modifier           = Modifier.size(22.dp).graphicsLayer { scaleX = likeScale; scaleY = likeScale },
                )
            }
            if (post.likesCount > 0) {
                Text(
                    post.likesCount.toString(), color = Muted, fontSize = 13.sp,
                    modifier = if (onShowLikers != null) Modifier.clickable { onShowLikers() } else Modifier,
                )
            }
            Spacer(Modifier.width(4.dp))
            IconButton(onClick = onComment) {
                Icon(Icons.Outlined.ChatBubbleOutline, null, tint = Muted, modifier = Modifier.size(20.dp))
            }
            if (post.commentsCount > 0) Text(post.commentsCount.toString(), color = Muted, fontSize = 13.sp)
            Spacer(Modifier.width(4.dp))
            IconButton(onClick = onShare) {
                Icon(Icons.Default.Repeat, null,
                    tint     = if (post.isRepostedByMe) Amber else Muted,
                    modifier = Modifier.size(20.dp))
            }
            if (post.repostsCount > 0) Text(post.repostsCount.toString(), color = Muted, fontSize = 13.sp)
            Spacer(Modifier.width(4.dp))
            // Dış paylaşım butonu
            Box {
                var shareMenuExpanded by remember { mutableStateOf(false) }
                IconButton(onClick = { shareMenuExpanded = true }) {
                    Icon(Icons.Default.IosShare, null, tint = Muted, modifier = Modifier.size(20.dp))
                }
                DropdownMenu(
                    expanded         = shareMenuExpanded,
                    onDismissRequest = { shareMenuExpanded = false },
                    containerColor   = HeftSurface,
                ) {
                    DropdownMenuItem(
                        text        = { Text("WhatsApp", color = OnBackground, fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Default.Share, null, tint = Color(0xFF25D366), modifier = Modifier.size(18.dp)) },
                        onClick     = { shareMenuExpanded = false; shareTarget = ShareTarget.WHATSAPP },
                    )
                    DropdownMenuItem(
                        text        = { Text("Instagram", color = OnBackground, fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Default.Share, null, tint = Color(0xFFE1306C), modifier = Modifier.size(18.dp)) },
                        onClick     = { shareMenuExpanded = false; shareTarget = ShareTarget.INSTAGRAM },
                    )
                    DropdownMenuItem(
                        text        = { Text(if (ku) "Yên Din" else "Diğer", color = OnBackground, fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Default.IosShare, null, tint = Muted, modifier = Modifier.size(18.dp)) },
                        onClick     = { shareMenuExpanded = false; shareTarget = ShareTarget.ANY },
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onSave) {
                Icon(
                    if (post.isSavedByMe) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                    contentDescription = Strings.save(language),
                    tint               = saveColor,
                    modifier           = Modifier.size(22.dp).graphicsLayer { scaleX = saveScale; scaleY = saveScale },
                )
            }
        }
    }

    // Paylaşım önizleme dialogu — hedef seçilince açılır, Coil yüklenince paylaş butonuna basılır
    if (shareTarget != null) {
        SharePreviewDialog(
            post      = post,
            target    = shareTarget!!,
            onDismiss = { shareTarget = null },
            language  = language,
        )
    }

    // Düzenleme dialog — normal post
    if (showEditDialog) {
        EditPostDialog(
            currentText  = post.text,
            currentTitle = post.title,
            language     = language,
            onDismiss    = { showEditDialog = false },
            onSave       = { newTitle, newText -> onEdit?.invoke(newTitle, newText); showEditDialog = false },
        )
    }

    // Düzenleme dialog — alıntı postu
    if (showEditQuoteDialog) {
        EditQuoteDialog(
            currentQuoteText  = post.quoteText,
            currentBookName   = post.bookName,
            currentAuthorName = post.authorName,
            language          = language,
            onDismiss         = { showEditQuoteDialog = false },
            onSave            = { newQuote, newBook, newAuthor ->
                onEditQuote?.invoke(newQuote, newBook, newAuthor)
                showEditQuoteDialog = false
            },
        )
    }

    // Silme onay dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            containerColor   = HeftSurface,
            title   = { Text(Strings.deletePost(language), color = OnBackground, fontWeight = FontWeight.SemiBold) },
            text    = { Text(Strings.deletePostConfirm(language), color = Muted, fontSize = 14.sp) },
            confirmButton = {
                TextButton(onClick = { onDelete?.invoke(); showDeleteDialog = false }) {
                    Text(Strings.delete(language), color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(Strings.cancel(language), color = Muted)
                }
            },
        )
    }
}

// ── EditPostDialog ────────────────────────────────────────────────────────────

@Composable
fun EditPostDialog(currentText: String, currentTitle: String = "", onDismiss: () -> Unit, onSave: (title: String, text: String) -> Unit, language: String = "tr") {
    val ku = language == "ku"
    var title by remember { mutableStateOf(currentTitle) }
    var text by remember { mutableStateOf(currentText) }
    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = HeftSurface), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(Strings.edit(language), fontWeight = FontWeight.SemiBold, color = OnBackground, fontSize = 16.sp)
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value         = title,
                    onValueChange = { if (it.length <= 120) title = it },
                    placeholder   = { Text(Strings.postTitleHint(language), color = Muted) },
                    modifier      = Modifier.fillMaxWidth(),
                    singleLine    = true,
                    colors        = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor      = Amber,
                        unfocusedBorderColor    = Divider,
                        focusedTextColor        = OnBackground,
                        unfocusedTextColor      = OnBackground,
                        unfocusedContainerColor = SurfaceVar,
                        focusedContainerColor   = SurfaceVar,
                    ),
                    shape = RoundedCornerShape(12.dp),
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value         = text,
                    onValueChange = { text = it },
                    modifier      = Modifier.fillMaxWidth().heightIn(min = 100.dp),
                    colors        = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor      = Amber,
                        unfocusedBorderColor    = Divider,
                        focusedTextColor        = OnBackground,
                        unfocusedTextColor      = OnBackground,
                        unfocusedContainerColor = SurfaceVar,
                        focusedContainerColor   = SurfaceVar,
                    ),
                    shape = RoundedCornerShape(12.dp),
                )
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onDismiss) { Text(Strings.cancel(language), color = Muted) }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { if (text.isNotBlank()) onSave(title.trim(), text) },
                        colors  = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = Color.Black),
                        shape   = RoundedCornerShape(10.dp),
                    ) { Text(Strings.save(language), fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

@Composable
fun EditQuoteDialog(
    currentQuoteText  : String,
    currentBookName   : String,
    currentAuthorName : String,
    onDismiss         : () -> Unit,
    onSave            : (quoteText: String, bookName: String, authorName: String) -> Unit,
    language          : String = "tr",
) {
    val ku = language == "ku"
    var quoteText  by remember { mutableStateOf(currentQuoteText) }
    var bookName   by remember { mutableStateOf(currentBookName) }
    var authorName by remember { mutableStateOf(currentAuthorName) }
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape  = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = HeftSurface),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    if (ku) "Jêgirtê Biguherîne" else "Alıntıyı Düzenle",
                    fontWeight = FontWeight.SemiBold, color = OnBackground, fontSize = 16.sp,
                )
                // Alıntı metni
                OutlinedTextField(
                    value         = quoteText,
                    onValueChange = { quoteText = it },
                    modifier      = Modifier.fillMaxWidth().heightIn(min = 100.dp),
                    label         = { Text(if (ku) "Nivîsa Jêgirtê" else "Alıntı metni", color = Muted, fontSize = 12.sp) },
                    colors        = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor      = Primary,
                        unfocusedBorderColor    = Divider,
                        focusedTextColor        = OnBackground,
                        unfocusedTextColor      = OnBackground,
                        unfocusedContainerColor = SurfaceVar,
                        focusedContainerColor   = SurfaceVar,
                    ),
                    shape = RoundedCornerShape(12.dp),
                )
                // Kitap adı
                OutlinedTextField(
                    value         = bookName,
                    onValueChange = { bookName = it },
                    modifier      = Modifier.fillMaxWidth(),
                    label         = { Text(if (ku) "Pirtûk" else "Kitap adı", color = Muted, fontSize = 12.sp) },
                    colors        = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor      = Amber,
                        unfocusedBorderColor    = Divider,
                        focusedTextColor        = OnBackground,
                        unfocusedTextColor      = OnBackground,
                        unfocusedContainerColor = SurfaceVar,
                        focusedContainerColor   = SurfaceVar,
                    ),
                    shape = RoundedCornerShape(12.dp),
                )
                // Yazar adı
                OutlinedTextField(
                    value         = authorName,
                    onValueChange = { authorName = it },
                    modifier      = Modifier.fillMaxWidth(),
                    label         = { Text(if (ku) "Nivîskar" else "Yazar adı", color = Muted, fontSize = 12.sp) },
                    colors        = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor      = Amber,
                        unfocusedBorderColor    = Divider,
                        focusedTextColor        = OnBackground,
                        unfocusedTextColor      = OnBackground,
                        unfocusedContainerColor = SurfaceVar,
                        focusedContainerColor   = SurfaceVar,
                    ),
                    shape = RoundedCornerShape(12.dp),
                )
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onDismiss) { Text(Strings.cancel(language), color = Muted) }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick  = { if (quoteText.isNotBlank()) onSave(quoteText, bookName, authorName) },
                        enabled  = quoteText.isNotBlank(),
                        colors   = ButtonDefaults.buttonColors(containerColor = Primary, contentColor = Color.White),
                        shape    = RoundedCornerShape(10.dp),
                    ) { Text(Strings.save(language), fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

// ── CommentSheet ──────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentSheet(post: Post, onDismiss: () -> Unit, vm: FeedViewModel, language: String = "tr") {
    val ku = language == "ku"
    val comments    by vm.comments.collectAsState()
    var commentText by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<Comment?>(null) }
    val myUid = vm.uid
    LaunchedEffect(post.id) { vm.loadComments(post.id) }

    val mentionSuggestions by vm.mentionSuggestions.collectAsState()
    var mentionedUids by remember { mutableStateOf(listOf<String>()) }
    LaunchedEffect(commentText) {
        val atIndex = commentText.lastIndexOf('@')
        if (atIndex < 0) { vm.clearMentionSuggestions(); return@LaunchedEffect }
        val afterAt = commentText.substring(atIndex + 1)
        if (afterAt.contains(' ') || afterAt.isEmpty()) { vm.clearMentionSuggestions(); return@LaunchedEffect }
        vm.searchMentionUsers(afterAt)
    }
    fun onMentionSelected(user: MentionHelper.MentionUser) {
        val atIndex = commentText.lastIndexOf('@')
        if (atIndex >= 0) {
            val before = commentText.substring(0, atIndex)
            commentText = "$before@${user.username} "
            mentionedUids = mentionedUids + user.uid
        }
        vm.clearMentionSuggestions()
    }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = HeftSurface) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.75f)
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
                .imePadding(),
        ) {
            Text(Strings.comments(language), fontWeight = FontWeight.SemiBold, color = OnBackground, modifier = Modifier.padding(vertical = 8.dp))
            HorizontalDivider(color = Divider)
            LazyColumn(modifier = Modifier.weight(1f), contentPadding = PaddingValues(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(comments, key = { it.id }) { cmt ->
                    val canDelete = myUid.isNotBlank() &&
                        (cmt.uid == myUid || post.uid == myUid)
                    Row(verticalAlignment = Alignment.Top) {
                        AsyncImage(
                            model = cmt.photoURL.ifEmpty { null }, contentDescription = null,
                            modifier = Modifier.size(32.dp).clip(CircleShape).background(SurfaceVar),
                            contentScale = ContentScale.Crop,
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(cmt.displayName, fontWeight = FontWeight.SemiBold, color = OnBackground, fontSize = 13.sp)
                            Text(cmt.text, color = OnSurface, fontSize = 14.sp, lineHeight = 20.sp)
                        }
                        if (canDelete) {
                            IconButton(
                                onClick  = { deleteTarget = cmt },
                                modifier = Modifier.size(32.dp),
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = Strings.delete(language),
                                    tint     = Color(0xFFEF4444).copy(alpha = 0.7f),
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                }
            }
            HorizontalDivider(color = Divider)
            MentionSuggestionBar(
                suggestions = mentionSuggestions,
                onSelect    = { onMentionSelected(it) },
                modifier    = Modifier.fillMaxWidth(),
            )
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = commentText, onValueChange = { commentText = it },
                    placeholder = { Text(Strings.commentHint(language), color = Muted) },
                    modifier = Modifier.weight(1f), shape = RoundedCornerShape(24.dp), singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Amber, unfocusedBorderColor = Divider,
                        focusedTextColor = OnBackground, unfocusedTextColor = OnBackground,
                        unfocusedContainerColor = SurfaceVar, focusedContainerColor = SurfaceVar,
                    ),
                )
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick  = {
                        if (commentText.isNotBlank()) {
                            vm.addComment(post, commentText.trim(), mentions = mentionedUids)
                            commentText   = ""
                            mentionedUids = emptyList()
                            vm.clearMentionSuggestions()
                        }
                    },
                    modifier = Modifier.size(40.dp).clip(CircleShape).background(Amber),
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, null, tint = Color.Black, modifier = Modifier.size(18.dp))
                }
            }
        }
    }

    deleteTarget?.let { cmt ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            containerColor   = HeftSurface,
            title  = { Text(if (ku) "Şîrove Jê Bibe" else "Yorumu Sil", color = OnBackground, fontWeight = FontWeight.SemiBold) },
            text   = { Text(cmt.text.take(80), color = Muted, fontSize = 13.sp) },
            confirmButton = {
                TextButton(onClick = { vm.deleteComment(post.id, cmt.id); deleteTarget = null }) {
                    Text(Strings.delete(language), color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(Strings.cancel(language), color = Muted)
                }
            },
        )
    }
}

// ── Tarih helper ─────────────────────────────────────────────────────────────
fun postTimeAgo(seconds: Long, ku: Boolean = false): String {
    val now  = System.currentTimeMillis() / 1000L
    val diff = now - seconds
    return when {
        diff < 60          -> if (ku) "niha"                     else "az önce"
        diff < 3600        -> if (ku) "${diff / 60}xv"           else "${diff / 60}dk"
        diff < 86400       -> if (ku) "${diff / 3600}sa"         else "${diff / 3600}sa"
        diff < 86400 * 7   -> if (ku) "${diff / 86400}rj"        else "${diff / 86400}g"
        diff < 86400 * 30  -> if (ku) "${diff / 86400 / 7}hf"   else "${diff / 86400 / 7}hf"
        diff < 86400 * 365 -> if (ku) "${diff / 86400 / 30}mh"  else "${diff / 86400 / 30}ay"
        else               -> if (ku) "${diff / 86400 / 365}sal" else "${diff / 86400 / 365}y"
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Önerilen Kişiler — Instagram tarzı yatay kaydırmalı şerit
// ÖNCEDEN: dikey, sayfalı (Önceki/Sonraki butonlu) bir kart kullanılıyordu.
// Artık yatay kaydırmalı, kompakt kartlar + "Tümünü Gör" → PeopleHubScreen.
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun SuggestedUsersCarousel(
    users     : List<FeedViewModel.SuggestedUser>,
    onFollow  : (uid: String) -> Unit,
    onNavigate: (uid: String) -> Unit,
    onSeeAll  : () -> Unit,
    language  : String = "tr",
) {
    val ku = language == "ku"
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color    = HeftSurface,
    ) {
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.PersonAdd, null, tint = Primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    Strings.suggestedPeople(language),
                    color      = OnBackground,
                    fontWeight = FontWeight.SemiBold,
                    fontSize   = 15.sp,
                    modifier   = Modifier.weight(1f),
                )
                Text(
                    Strings.seeAll(language),
                    color      = Primary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize   = 13.sp,
                    modifier   = Modifier.clickable { onSeeAll() },
                )
            }

            LazyRow(
                contentPadding        = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(users, key = { it.uid }) { user ->
                    SuggestedUserCarouselCard(
                        user       = user,
                        onFollow   = { onFollow(user.uid) },
                        onNavigate = { onNavigate(user.uid) },
                        language   = language,
                    )
                }
                item(key = "see_all_card") {
                    SeeAllCarouselCard(language = language, onClick = onSeeAll)
                }
            }

            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun SuggestedUserCarouselCard(
    user      : FeedViewModel.SuggestedUser,
    onFollow  : () -> Unit,
    onNavigate: () -> Unit,
    language  : String = "tr",
) {
    val ku = language == "ku"
    Column(
        modifier = Modifier
            .width(96.dp)
            .clickable { onNavigate() },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (user.photoURL.isNotBlank()) {
            AsyncImage(
                model              = user.photoURL,
                contentDescription = null,
                contentScale       = ContentScale.Crop,
                modifier           = Modifier.size(64.dp).clip(CircleShape),
            )
        } else {
            Box(
                Modifier.size(64.dp).clip(CircleShape).background(SurfaceVar),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Person, null, tint = Muted, modifier = Modifier.size(28.dp))
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            user.name.ifBlank { "?" },
            color      = OnBackground,
            fontWeight = FontWeight.SemiBold,
            fontSize   = 12.sp,
            maxLines   = 1,
            overflow   = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            textAlign  = androidx.compose.ui.text.style.TextAlign.Center,
            modifier   = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(4.dp))
        Button(
            onClick        = onFollow,
            shape          = RoundedCornerShape(16.dp),
            colors         = ButtonDefaults.buttonColors(containerColor = Primary),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
            modifier       = Modifier.height(28.dp).fillMaxWidth(),
        ) {
            Text(
                if (ku) "Bişopîne" else "Takip Et",
                color      = Color.White,
                fontSize   = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines   = 1,
            )
        }
    }
}

@Composable
private fun SeeAllCarouselCard(language: String, onClick: () -> Unit) {
    val ku = language == "ku"
    Column(
        modifier = Modifier
            .width(96.dp)
            .clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(64.dp).clip(CircleShape).background(SurfaceVar),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = Primary, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(
            Strings.seeAll(language),
            color      = Primary,
            fontWeight = FontWeight.SemiBold,
            fontSize   = 12.sp,
            maxLines   = 2,
            textAlign  = androidx.compose.ui.text.style.TextAlign.Center,
            modifier   = Modifier.fillMaxWidth(),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  FriendsReadingStrip — "Arkadaşların ne okuyor?" yatay şerit (Feed üstü)
//  reading_status (status='okuyorum') + follows isim/foto eşleşmesi
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun FriendsReadingStrip(
    items        : List<com.heftreng.app.data.model.FriendReadingItem>,
    language     : String = "tr",
    onClick      : (com.heftreng.app.data.model.FriendReadingItem) -> Unit,
    onAvatarClick: (String) -> Unit = {},
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color    = HeftSurface,
    ) {
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Outlined.AutoStories,
                    null,
                    tint     = Primary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    Strings.friendsReadingTitle(language),
                    color      = OnBackground,
                    fontWeight = FontWeight.SemiBold,
                    fontSize   = 15.sp,
                )
            }

            LazyRow(
                contentPadding      = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(items, key = { it.uid + it.bookId }) { item ->
                    FriendReadingCard(
                        item          = item,
                        language      = language,
                        onClick       = { onClick(item) },
                        onAvatarClick = { onAvatarClick(item.uid) },
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = Divider, thickness = 0.5.dp)
        }
    }
}

@Composable
private fun FriendReadingCard(
    item         : com.heftreng.app.data.model.FriendReadingItem,
    language     : String,
    onClick      : () -> Unit,
    onAvatarClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(110.dp)
            .clickable { onClick() },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(10.dp))
                .background(SurfaceVar),
            contentAlignment = Alignment.Center,
        ) {
            if (item.coverImg.isNotBlank()) {
                AsyncImage(
                    model              = item.coverImg,
                    contentDescription = item.title,
                    contentScale       = ContentScale.Crop,
                    modifier           = Modifier.fillMaxSize(),
                )
            } else {
                Icon(Icons.Outlined.AutoStories, null, tint = Muted, modifier = Modifier.size(28.dp))
            }

            // Arkadaşın avatarı — sol üst köşe
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(SurfaceVar)
                    .clickable { onAvatarClick() },
                contentAlignment = Alignment.Center,
            ) {
                if (item.photoURL.isNotBlank()) {
                    AsyncImage(
                        model              = item.photoURL,
                        contentDescription = item.name,
                        contentScale       = ContentScale.Crop,
                        modifier           = Modifier.fillMaxSize().clip(CircleShape),
                    )
                } else {
                    Icon(Icons.Outlined.Person, null, tint = Muted, modifier = Modifier.size(14.dp))
                }
            }

            // Sayfa rozeti
            if (item.currentPage > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.55f))
                        .padding(horizontal = 5.dp, vertical = 2.dp),
                ) {
                    Text(
                        Strings.friendsReadingPage(language, item.currentPage),
                        color    = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            item.title,
            color      = OnBackground,
            fontSize   = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines   = 2,
            overflow   = TextOverflow.Ellipsis,
            lineHeight = 14.sp,
        )
        if (item.name.isNotBlank()) {
            Text(
                item.name,
                color    = Muted,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  BlogPostsStrip — "Blog Yazıları" yatay şerit (Feed üstü)
//  heft-reng.blogspot.com (Blogger API) — Blog sekmesi kaldırıldı, içerik
//  Kültür/Feed'e entegre edildi. "Tümünü Gör" → mevcut Blog ekranı (rota duruyor).
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun BlogPostsStrip(
    posts      : List<com.heftreng.app.viewmodel.BlogPost>,
    language   : String = "tr",
    onPostClick: (com.heftreng.app.viewmodel.BlogPost) -> Unit,
    onSeeAll   : () -> Unit,
) {
    val ku = language == "ku"
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color    = HeftSurface,
    ) {
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Outlined.Article,
                    null,
                    tint     = Primary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (ku) "Gotarên Blogê" else "Blog Yazıları",
                    color      = OnBackground,
                    fontWeight = FontWeight.SemiBold,
                    fontSize   = 15.sp,
                    modifier   = Modifier.weight(1f),
                )
                Text(
                    if (ku) "Hemî" else "Tümünü Gör",
                    color    = Primary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable { onSeeAll() },
                )
            }

            LazyRow(
                contentPadding        = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(posts, key = { it.id }) { post ->
                    BlogPostCard(post = post, onClick = { onPostClick(post) })
                }
            }

            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = Divider, thickness = 0.5.dp)
        }
    }
}

@Composable
private fun BlogPostCard(
    post   : com.heftreng.app.viewmodel.BlogPost,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(200.dp)
            .clickable { onClick() },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(SurfaceVar),
            contentAlignment = Alignment.Center,
        ) {
            if (post.thumbnail.isNotBlank()) {
                AsyncImage(
                    model              = post.thumbnail,
                    contentDescription = post.title,
                    contentScale       = ContentScale.Crop,
                    modifier           = Modifier.fillMaxSize(),
                )
            } else {
                Icon(Icons.Outlined.Article, null, tint = Muted, modifier = Modifier.size(28.dp))
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            post.title,
            color      = OnBackground,
            fontSize   = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines   = 2,
            overflow   = TextOverflow.Ellipsis,
            lineHeight = 16.sp,
        )
    }
}

// ── Feed Duyuru Kutusu ────────────────────────────────────────────────────────
@Composable
private fun FeedAnnouncementBanner(
    announcement: com.heftreng.app.data.model.CmsAnnouncement,
    onDismiss   : () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val bgColor = when (announcement.type) {
        "warning" -> Color(0xFFFEF3C7) // sarı
        "success" -> Color(0xFFDCFCE7) // yeşil
        else      -> Color(0xFFDBEAFE) // mavi (info)
    }
    val accentColor = when (announcement.type) {
        "warning" -> Color(0xFFD97706)
        "success" -> Color(0xFF16A34A)
        else      -> Color(0xFF2563EB)
    }
    val iconColor = accentColor

    androidx.compose.animation.AnimatedVisibility(
        visible = true,
        enter   = androidx.compose.animation.fadeIn() +
                  androidx.compose.animation.expandVertically(),
        exit    = androidx.compose.animation.fadeOut() +
                  androidx.compose.animation.shrinkVertically(),
    ) {
        androidx.compose.material3.Surface(
            shape    = RoundedCornerShape(14.dp),
            color    = bgColor,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .then(
                    if (announcement.linkUrl.isNotBlank())
                        Modifier.clickable {
                            val intent = android.content.Intent(
                                android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse(announcement.linkUrl)
                            ).apply { flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK }
                            context.startActivity(intent)
                        }
                    else Modifier
                ),
        ) {
            Row(
                modifier          = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                // Sol renk çizgisi
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(40.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(accentColor)
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    if (announcement.title.isNotBlank()) {
                        androidx.compose.material3.Text(
                            text       = announcement.title,
                            color      = accentColor,
                            fontWeight = FontWeight.Bold,
                            fontSize   = 13.sp,
                            lineHeight = 18.sp,
                        )
                        if (announcement.body.isNotBlank()) Spacer(Modifier.height(3.dp))
                    }
                    if (announcement.body.isNotBlank()) {
                        androidx.compose.material3.Text(
                            text       = announcement.body,
                            color      = accentColor.copy(alpha = 0.85f),
                            fontSize   = 12.sp,
                            lineHeight = 17.sp,
                        )
                    }
                    if (announcement.linkUrl.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        androidx.compose.material3.Text(
                            text       = announcement.linkUrl,
                            color      = accentColor,
                            fontSize   = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines   = 1,
                            overflow   = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                androidx.compose.material3.IconButton(
                    onClick  = onDismiss,
                    modifier = Modifier.size(24.dp),
                ) {
                    androidx.compose.material3.Icon(
                        imageVector        = Icons.Default.Close,
                        contentDescription = "Kapat",
                        tint               = iconColor.copy(alpha = 0.6f),
                        modifier           = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

