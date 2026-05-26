package com.heftreng.app.ui.screens.feed

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import kotlinx.coroutines.launch
import androidx.compose.runtime.*
import java.net.URLEncoder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import com.heftreng.app.ui.component.AdBannerView
import com.heftreng.app.ui.i18n.Strings
import com.heftreng.app.viewmodel.AdsViewModel
import com.heftreng.app.ui.component.QuoteCard
import com.heftreng.app.ui.component.LinkifyText
import com.heftreng.app.ui.component.FullScreenImageViewer
import com.heftreng.app.ui.component.QuoteDialog
import com.heftreng.app.ui.component.QuoteInputSection
import com.heftreng.app.ui.component.QuotePayload
import com.heftreng.app.ui.component.SharePreviewDialog
import com.heftreng.app.utils.ShareTarget
import com.heftreng.app.ui.theme.*
import com.heftreng.app.ui.screens.social.LikerListSheet
import com.heftreng.app.viewmodel.FeedViewModel
import com.heftreng.app.viewmodel.SocialViewModel
import kotlinx.coroutines.tasks.await
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import androidx.compose.ui.platform.LocalContext
import com.heftreng.app.data.model.AppConfig
import androidx.core.content.ContextCompat

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
@Composable
fun FeedScreen(
    navController: NavController,
    language     : String = "tr",
    appConfig    : AppConfig = AppConfig(),
    vm           : FeedViewModel  = hiltViewModel(),
    socialVm     : SocialViewModel = hiltViewModel(),
    adsVm        : AdsViewModel    = hiltViewModel(),
) {
    val posts       by vm.posts.collectAsState()
    val loading     by vm.loading.collectAsState()
    val hasMore     by vm.hasMore.collectAsState()
    val loadingMore by vm.loadingMore.collectAsState()
    val bannerUnitId by adsVm.bannerUnitId.collectAsState()
    val bannerPos    by adsVm.bannerPosition.collectAsState()

    var isRefreshing by remember { mutableStateOf(false) }
    val pullRefreshState = rememberPullRefreshState(
        refreshing = isRefreshing,
        onRefresh  = {
            isRefreshing = true
            vm.refresh()
        }
    )
    // isRefreshing'i loading bitince kapat
    LaunchedEffect(loading) { if (!loading) isRefreshing = false }

    LaunchedEffect(Unit) { adsVm.loadAdConfigs() }

    // ── Feed sekme (Herkes / Takip edilenler) ────────────────────────────────
    val ku = language == "ku"
    val feedTabs = listOf(
        Strings.filterAll(language),
        Strings.filterFollowing(language),
    )
    val feedPagerState  = rememberPagerState { feedTabs.size }
    val selectedFeedTab by derivedStateOf { feedPagerState.currentPage }
    val feedScope       = rememberCoroutineScope()

    // Takip edilen kullanıcıların UID listesi
    val currentUserUid = FirebaseAuth.getInstance().currentUser?.uid ?: ""
    var followingUids by remember { mutableStateOf<Set<String>>(emptySet()) }
    LaunchedEffect(currentUserUid) {
        if (currentUserUid.isNotEmpty()) {
            try {
                val snap = FirebaseFirestore.getInstance()
                    .collection("follows")
                    .whereEqualTo("fromUid", currentUserUid)
                    .get().await()
                followingUids = snap.documents.mapNotNull { it.getString("targetUid") }.toSet()
            } catch (_: Exception) {}
        }
    }

    val displayedPosts = remember(posts, selectedFeedTab, followingUids, currentUserUid) {
        val base = if (selectedFeedTab == 1) {
            val allowed = followingUids + setOf(currentUserUid)
            posts.filter { it.uid in allowed }
        } else posts
        // suspended postlar: sadece sahibi görsün
        // restricted postlar: giriş yapanlar görsün (currentUserUid boş değilse)
        base.filter { post ->
            when (post.moderationStatus) {
                "removed"    -> false  // hiç kimse
                "suspended"  -> post.uid == currentUserUid  // sadece sahibi
                "restricted" -> currentUserUid.isNotBlank() // giriş yapanlar
                else         -> true
            }
        }
    }

    // ── Şikayet dialog ──────────────────────────────────────────────────────
    var reportPostId     by remember { mutableStateOf<String?>(null) }
    var reportTargetUid  by remember { mutableStateOf("") }
    var reportTargetName by remember { mutableStateOf("") }

    var likersPostId     by remember { mutableStateOf<String?>(null) }
    val likers           by socialVm.likers.collectAsState()
    val socialLoading    by socialVm.loading.collectAsState()
    var inlineText       by remember { mutableStateOf("") }
    var inlineQuote      by remember { mutableStateOf<QuotePayload?>(null) }
    var showInlineQuote  by remember { mutableStateOf(false) }
    var inlineImageUri   by remember { mutableStateOf<Uri?>(null) }
    val uploading        by vm.uploading.collectAsState()
    val context          = LocalContext.current

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> inlineImageUri = uri }

    val imagePermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) imagePicker.launch("image/*") }

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

    if (showInlineQuote) {
        QuoteDialog(
            initialText   = inlineQuote?.text ?: "",
            initialBook   = inlineQuote?.bookName ?: "",
            initialAuthor = inlineQuote?.authorName ?: "",
            onDismiss     = { showInlineQuote = false },
            onConfirm     = { p -> inlineQuote = p; showInlineQuote = false },
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

    Box(modifier = Modifier.fillMaxSize().background(Background).imePadding()) {
        Column(Modifier.fillMaxSize()) {
            // ── Sekme satırı ────────────────────────────────────────────────
            TabRow(
                selectedTabIndex = selectedFeedTab,
                containerColor   = Background,
                contentColor     = Primary,
                indicator        = { tabPositions ->
                    Box(
                        Modifier
                            .tabIndicatorOffset(tabPositions[selectedFeedTab])
                            .height(2.dp)
                            .background(Primary)
                    )
                },
            ) {
                feedTabs.forEachIndexed { i, title ->
                    Tab(
                        selected               = selectedFeedTab == i,
                        onClick                = { feedScope.launch { feedPagerState.animateScrollToPage(i) } },
                        text                   = { Text(title, fontSize = 13.sp, fontWeight = if (selectedFeedTab == i) FontWeight.Bold else FontWeight.Normal) },
                        selectedContentColor   = Primary,
                        unselectedContentColor = Muted,
                    )
                }
            }

        if (loading && displayedPosts.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Primary)
            }
        } else {
            Box(Modifier.fillMaxSize().pullRefresh(pullRefreshState)) {
            LazyColumn(
                modifier       = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 100.dp),
            ) {
                // ── Inline compose — tema .compose ────────────────────────
                if (selectedFeedTab == 0) item {
                    InlineComposeBox(
                        text          = inlineText,
                        onTextChange  = { inlineText = it },
                        quote         = inlineQuote,
                        onQuoteAdd    = { if (appConfig.feedAllowQuotes) showInlineQuote = true },
                        onQuoteRemove = { inlineQuote = null },
                        onSend        = {
                            if (inlineText.isNotBlank() || inlineQuote != null || inlineImageUri != null) {
                                val uri = inlineImageUri
                                if (uri != null) {
                                    vm.uploadImageAndCreatePost(
                                        imageUri   = uri,
                                        text       = inlineText.trim(),
                                        quoteText  = inlineQuote?.text ?: "",
                                        authorName = inlineQuote?.authorName ?: "",
                                        bookName   = inlineQuote?.bookName ?: "",
                                        context    = context,
                                    )
                                } else {
                                    vm.createPost(
                                        text       = inlineText.trim(),
                                        quoteText  = inlineQuote?.text ?: "",
                                        authorName = inlineQuote?.authorName ?: "",
                                        bookName   = inlineQuote?.bookName ?: "",
                                    )
                                }
                                inlineText     = ""
                                inlineQuote    = null
                                inlineImageUri = null
                            }
                        },
                        photoURL     = myPhotoURL,
                        language     = language,
                        imageUri     = inlineImageUri,
                        uploading    = uploading,
                        onImagePick  = {
                            if (appConfig.feedShowImages) {
                                val perm = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU)
                                    android.Manifest.permission.READ_MEDIA_IMAGES
                                else android.Manifest.permission.READ_EXTERNAL_STORAGE
                                if (androidx.core.content.ContextCompat.checkSelfPermission(context, perm)
                                    == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                                    imagePicker.launch("image/*")
                                } else {
                                    imagePermLauncher.launch(perm)
                                }
                            }
                        },
                        onImageClear = { inlineImageUri = null },
                    )
                }
                // ── Gönderi listesi ───────────────────────────────────
                if (displayedPosts.isEmpty() && !loading && selectedFeedTab == 1) {
                    item {
                        Box(
                            Modifier.fillMaxWidth().padding(top = 80.dp),
                            contentAlignment = androidx.compose.ui.Alignment.Center,
                        ) {
                            androidx.compose.foundation.layout.Column(
                                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    Icons.Outlined.PersonAdd, null,
                                    tint = Muted,
                                    modifier = Modifier.size(48.dp),
                                )
                                Text(
                                    Strings.followSomeone(language),
                                    color = Muted, fontSize = 14.sp,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                )
                            }
                        }
                    }
                }
                items(displayedPosts, key = { it.id }) { post ->
                    PostCard(
                        post      = post,
                        onLike    = { vm.toggleLike(post) },
                        onSave    = { vm.toggleSave(post) },
                        onProfile = { navController.navigate(Screen.Profile.go(post.uid)) },
                        onComment = { navController.navigate(Screen.PostDetail.go(post.id)) },
                        onShare   = {
                            if (post.isRepostedByMe) vm.unrepost(post)
                            else vm.repost(post)
                        },
                        onDelete  = { vm.deletePost(post.id) },
                        onEdit    = { newText -> vm.editPost(post.id, newText) },
                        onTap        = { navController.navigate(Screen.PostDetail.go(post.id)) },
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
                                else           -> navController.navigate(Screen.PostDetail.go(repostId))
                            }
                        },
                        onReport  = {
                            reportPostId     = post.id
                            reportTargetUid  = post.uid
                            reportTargetName = post.displayName.ifBlank { post.name }
                        },
                        language = language,
                    )
                    HorizontalDivider(color = Divider, thickness = 0.5.dp)
                    // ── AdMob Banner — her bannerPos. kartta bir ─────────
                    val postIndex = displayedPosts.indexOf(post)
                    if (bannerUnitId != null &&
                        postIndex >= 0 &&
                        (postIndex + 1) % bannerPos == 0) {
                        AdBannerView(unitId = bannerUnitId)
                    }
                }
                // ── Daha Fazla Göster ─────────────────────────────────────
                if (hasMore) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            if (loadingMore) {
                                CircularProgressIndicator(color = Primary, modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
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
            }
                PullRefreshIndicator(
                    refreshing = isRefreshing,
                    state      = pullRefreshState,
                    modifier   = Modifier.align(Alignment.TopCenter),
                    contentColor = Primary,
                )
            } // pullRefresh Box
        }
        } // Column
    } // Box

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
    quote         : QuotePayload?,
    onQuoteAdd    : () -> Unit,
    onQuoteRemove : () -> Unit,
    onSend        : () -> Unit,
    photoURL      : String,
    language      : String,
    imageUri      : Uri?         = null,
    uploading     : Boolean      = false,
    onImagePick   : () -> Unit   = {},
    onImageClear  : () -> Unit   = {},
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
                OutlinedTextField(
                    value           = text,
                    onValueChange   = onTextChange,
                    placeholder     = {
                        Text(
                            Strings.whatsOnMind(language),
                            color = Muted, fontSize = 14.sp,
                        )
                    },
                    modifier        = Modifier.fillMaxWidth().heightIn(min = 60.dp, max = 200.dp),
                    colors          = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor      = Primary,
                        unfocusedBorderColor    = Color.Transparent,
                        focusedTextColor        = OnBackground,
                        unfocusedTextColor      = OnBackground,
                        unfocusedContainerColor = Color.Transparent,
                        focusedContainerColor   = Color.Transparent,
                        cursorColor             = Primary,
                    ),
                    shape           = RoundedCornerShape(8.dp),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    maxLines        = 8,
                )
            }
            if (quote != null) {
                Spacer(Modifier.height(8.dp))
                QuoteInputSection(quote = quote, onRemove = onQuoteRemove)
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
    language    : String,
    currentUser : FirebaseUser?,
    onDismiss   : () -> Unit,
    onPost      : (String, QuotePayload?) -> Unit,
) {
    var text         by remember { mutableStateOf("") }
    var quotePayload by remember { mutableStateOf<QuotePayload?>(null) }
    var showQuote    by remember { mutableStateOf(false) }

    if (showQuote) {
        QuoteDialog(
            initialText   = quotePayload?.text ?: "",
            initialBook   = quotePayload?.bookName ?: "",
            initialAuthor = quotePayload?.authorName ?: "",
            onDismiss     = { showQuote = false },
            onConfirm     = { p -> quotePayload = p; showQuote = false },
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
                    onClick  = { if (text.isNotBlank() || quotePayload != null) onPost(text.trim(), quotePayload) },
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
                QuoteInputSection(quote = quotePayload, onRemove = { quotePayload = null })
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

@Composable
fun PostCard(
    post      : Post,
    onLike    : () -> Unit,
    onSave    : () -> Unit,
    onProfile : () -> Unit,
    onComment : () -> Unit,
    onShare      : () -> Unit,
    onDelete     : (() -> Unit)? = null,
    onEdit       : ((String) -> Unit)? = null,
    onTap        : (() -> Unit)? = null,
    onQuote      : (() -> Unit)? = null,
    onStoryShare : (() -> Unit)? = null,
    onShowLikers : (() -> Unit)? = null,
    onTapAuthor  : ((String) -> Unit)? = null,
    onTapBook    : ((String) -> Unit)? = null,
    onTapRepost  : ((postId: String, type: String) -> Unit)? = null,
    onReport     : (() -> Unit)? = null,
    language     : String = "tr",
) {
    val ku = language == "ku"
    val myUid            = FirebaseAuth.getInstance().currentUser?.uid ?: ""
    val isOwn            = post.uid == myUid
    var menuExpanded     by remember { mutableStateOf(false) }
    var showEditDialog   by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var shareTarget      by remember { mutableStateOf<ShareTarget?>(null) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Background)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        // ── Moderasyon Banner — sadece gönderi sahibine göster ────────────
        if (isOwn && post.moderationStatus != "active") {
            val (bannerColor, bannerIcon, bannerText) = when (post.moderationStatus) {
                "restricted" -> Triple(
                    androidx.compose.ui.graphics.Color(0xFFF59E0B),
                    Icons.Outlined.VisibilityOff,
                    "Gönderiniz kısıtlandı — yalnızca giriş yapanlar görebilir"
                )
                "suspended"  -> Triple(
                    androidx.compose.ui.graphics.Color(0xFFEF4444),
                    Icons.Outlined.PauseCircle,
                    "Gönderiniz askıya alındı — şu an yalnızca siz görüyorsunuz"
                )
                else -> Triple(
                    androidx.compose.ui.graphics.Color(0xFF6B7280),
                    Icons.Outlined.Block,
                    "Gönderiniz kaldırıldı"
                )
            }
            Surface(
                color  = bannerColor.copy(alpha = 0.12f),
                shape  = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            ) {
                androidx.compose.foundation.layout.Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(bannerIcon, null, tint = bannerColor, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(bannerText, color = bannerColor, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                    if (post.moderationReason.isNotBlank()) {
                        Text("Sebep: ${post.moderationReason}", color = bannerColor.copy(alpha = 0.8f), fontSize = 11.sp)
                    }
                    // İtiraz butonu — removed hariç
                    if (post.moderationStatus != "removed") {
                        val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                        val appealScope = rememberCoroutineScope()
                        var appealSent by remember { mutableStateOf(false) }
                        var showAppealDialog by remember { mutableStateOf(false) }
                        var appealText by remember { mutableStateOf("") }

                        if (showAppealDialog) {
                            androidx.compose.material3.AlertDialog(
                                onDismissRequest = { showAppealDialog = false },
                                title = { Text("İtiraz Gönder", fontWeight = FontWeight.Bold) },
                                text = {
                                    androidx.compose.foundation.layout.Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text("Bu kararın hatalı olduğunu düşünüyorsanız itiraz edebilirsiniz.", color = Muted, fontSize = 13.sp)
                                        androidx.compose.material3.OutlinedTextField(
                                            value = appealText, onValueChange = { appealText = it },
                                            label = { Text("Açıklamanız") },
                                            modifier = Modifier.fillMaxWidth(), maxLines = 4,
                                        )
                                    }
                                },
                                confirmButton = {
                                    Button(onClick = {
                                        appealScope.launch {
                                            try {
                                                db.collection("appeals").add(mapOf(
                                                    "postId"          to post.id,
                                                    "postOwnerUid"    to post.uid,
                                                    "postOwnerName"   to post.displayName,
                                                    "moderationStatus"to post.moderationStatus,
                                                    "text"            to appealText,
                                                    "status"          to "pending",
                                                    "ts"              to com.google.firebase.Timestamp.now(),
                                                )).await()
                                            } catch (_: Exception) {}
                                        }
                                        appealSent = true
                                        showAppealDialog = false
                                    }, enabled = appealText.isNotBlank()) { Text("Gönder") }
                                },
                                dismissButton = {
                                    OutlinedButton(onClick = { showAppealDialog = false }) { Text("İptal") }
                                },
                            )
                        }

                        Spacer(Modifier.height(4.dp))
                        if (appealSent) {
                            Text("İtirazınız alındı, incelenecektir.", color = bannerColor, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                        } else {
                            Text(
                                "İtiraz et →",
                                color = bannerColor,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.clickable { showAppealDialog = true },
                            )
                        }
                    }
                }
            }
        }
        // Header
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(
                modifier          = Modifier.weight(1f).clickable { onProfile() },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier.size(40.dp).clip(CircleShape).background(SurfaceVar),
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
                    Text(post.displayName.ifBlank { Strings.anonymous(language) }, fontWeight = FontWeight.SemiBold, color = OnBackground, fontSize = 14.sp)
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
                            onClick     = { menuExpanded = false; showEditDialog = true },
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
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // İçerik — tıklanınca tekil gönderi ekranına
        Column(
            modifier = if (onTap != null)
                Modifier.fillMaxWidth().clickable { onTap() }
            else
                Modifier.fillMaxWidth()
        ) {
            if (post.quoteText.isNotBlank()) {
                QuoteCard(
                    quoteText   = post.quoteText,
                    bookName    = post.bookName,
                    authorName  = post.authorName,
                    onTapBook   = onTapBook,
                    onTapAuthor = onTapAuthor,
                    modifier    = Modifier.padding(bottom = 8.dp),
                )
            }
            if (post.text.isNotBlank()) {
                LinkifyText(post.text, fontSize = 15.sp, lineHeight = 22.sp, expandable = true, language = language)
                Spacer(Modifier.height(8.dp))
            }
                        if (post.repostType.isNotBlank() && post.repostType != "feed") {
                Surface(shape = RoundedCornerShape(6.dp), color = Primary.copy(alpha = 0.12f),
                    modifier = Modifier.padding(bottom = 6.dp)) {
                    Text(when(post.repostType){"serial"->"📖 Kitap";"chapter"->"📄 Bölüm";"book_chapter"->"📄 Kitap Bölümü";"blog"->"📝 Blog";else->post.repostType},
                        color = Primary, fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                }
            }
            // Repost embed kartı — tema buildRpEmbed() ile senkron
            if (post.repostType.isNotBlank()) {
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
                        if (post.repostText.isNotBlank())   Text(post.repostText,   color = OnSurface,    fontSize = 13.sp, maxLines = 4, lineHeight = 19.sp)
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
        }

        // Aksiyonlar
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onLike) {
                Icon(
                    if (post.isLikedByMe) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = Strings.likeAction(language),
                    tint               = if (post.isLikedByMe) Color(0xFFEF4444) else Muted,
                    modifier           = Modifier.size(20.dp),
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
                    tint               = if (post.isSavedByMe) Amber else Muted,
                    modifier           = Modifier.size(20.dp),
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
        )
    }

    // Düzenleme dialog
    if (showEditDialog) {
        EditPostDialog(
            currentText = post.text,
            language    = language,
            onDismiss   = { showEditDialog = false },
            onSave      = { newText -> onEdit?.invoke(newText); showEditDialog = false },
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
fun EditPostDialog(currentText: String, onDismiss: () -> Unit, onSave: (String) -> Unit, language: String = "tr") {
    val ku = language == "ku"
    var text by remember { mutableStateOf(currentText) }
    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = HeftSurface), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(Strings.edit(language), fontWeight = FontWeight.SemiBold, color = OnBackground, fontSize = 16.sp)
                Spacer(Modifier.height(14.dp))
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
                        onClick = { if (text.isNotBlank()) onSave(text) },
                        colors  = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = Color.Black),
                        shape   = RoundedCornerShape(10.dp),
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
                    onClick  = { if (commentText.isNotBlank()) { vm.addComment(post, commentText.trim()); commentText = "" } },
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
        diff < 60          -> if (ku) "niha" else "az önce"
        diff < 3600        -> if (ku) "${diff / 60}d"   else "${diff / 60}dk"
        diff < 86400       -> if (ku) "${diff / 3600}s" else "${diff / 3600}sa"
        diff < 86400 * 7   -> if (ku) "${diff / 86400}r" else "${diff / 86400}g"
        diff < 86400 * 30  -> if (ku) "${diff / 86400 / 7}hf" else "${diff / 86400 / 7}hf"
        diff < 86400 * 365 -> if (ku) "${diff / 86400 / 30}m" else "${diff / 86400 / 30}ay"
        else               -> if (ku) "${diff / 86400 / 365}s" else "${diff / 86400 / 365}y"
    }
}
