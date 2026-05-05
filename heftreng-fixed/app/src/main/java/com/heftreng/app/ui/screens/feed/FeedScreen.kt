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
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
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
import com.heftreng.app.data.model.Post
import com.heftreng.app.navigation.Screen
import com.heftreng.app.ui.component.QuoteCard
import com.heftreng.app.ui.component.QuoteDialog
import com.heftreng.app.ui.component.QuoteInputSection
import com.heftreng.app.ui.component.QuotePayload
import com.heftreng.app.ui.theme.*
import com.heftreng.app.ui.screens.social.LikerListSheet
import com.heftreng.app.viewmodel.FeedViewModel
import com.heftreng.app.viewmodel.SocialViewModel
import kotlinx.coroutines.tasks.await

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    navController: NavController,
    language     : String = "tr",
    vm           : FeedViewModel  = hiltViewModel(),
    socialVm     : SocialViewModel = hiltViewModel(),
) {
    val posts       by vm.posts.collectAsState()
    val loading     by vm.loading.collectAsState()
    val hasMore     by vm.hasMore.collectAsState()
    val loadingMore by vm.loadingMore.collectAsState()

    var likersPostId     by remember { mutableStateOf<String?>(null) }
    val likers           by socialVm.likers.collectAsState()
    val socialLoading    by socialVm.loading.collectAsState()
    var commentPost      by remember { mutableStateOf<Post?>(null) }
    var inlineText       by remember { mutableStateOf("") }
    var inlineQuote      by remember { mutableStateOf<QuotePayload?>(null) }
    var showInlineQuote  by remember { mutableStateOf(false) }

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
            title     = "Beğenenler",
            likers    = likers,
            loading   = socialLoading,
            onDismiss = { likersPostId = null; socialVm.clearLikers() },
            onProfile = { uid -> likersPostId = null; navController.navigate("profile/$uid") },
        )
    }

    Box(modifier = Modifier.fillMaxSize().background(Background).imePadding()) {
        if (loading && posts.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Primary)
            }
        } else {
            LazyColumn(
                modifier       = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 100.dp),
            ) {
                // ── Inline compose — tema .compose ────────────────────────
                item {
                    InlineComposeBox(
                        text          = inlineText,
                        onTextChange  = { inlineText = it },
                        quote         = inlineQuote,
                        onQuoteAdd    = { showInlineQuote = true },
                        onQuoteRemove = { inlineQuote = null },
                        onSend        = {
                            if (inlineText.isNotBlank() || inlineQuote != null) {
                                vm.createPost(
                                    text       = inlineText.trim(),
                                    quoteText  = inlineQuote?.text ?: "",
                                    authorName = inlineQuote?.authorName ?: "",
                                    bookName   = inlineQuote?.bookName ?: "",
                                )
                                inlineText  = ""
                                inlineQuote = null
                            }
                        },
                        photoURL = myPhotoURL,
                        language = language,
                    )
                }
                // ── Gönderi listesi ───────────────────────────────────────
                items(posts, key = { it.id }) { post ->
                    PostCard(
                        post      = post,
                        onLike    = { vm.toggleLike(post) },
                        onSave    = { vm.toggleSave(post) },
                        onProfile = { navController.navigate(Screen.Profile.go(post.uid)) },
                        onComment = { commentPost = post },
                        onShare   = { vm.repost(post) },
                        onDelete  = { vm.deletePost(post.id) },
                        onEdit    = { newText -> vm.editPost(post.id, newText) },
                        onTap        = { navController.navigate(Screen.PostDetail.go(post.id)) },
                        onShowLikers = {
                            socialVm.loadPostLikers(post.id)
                            likersPostId = post.id
                        },
                        onTapAuthor = { author ->
                            navController.navigate("author_quotes/${URLEncoder.encode(author, "UTF-8")}")
                        },
                        onTapBook = { book ->
                            navController.navigate("book_quotes/${URLEncoder.encode(book, "UTF-8")}")
                        },
                    )
                    HorizontalDivider(color = Divider, thickness = 0.5.dp)
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
                                        if (language == "ku") "Zêdetir Nîşan Bide" else "Daha Fazla Göster",
                                        color = Muted, fontSize = 13.sp,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

    }

    commentPost?.let { post ->
        CommentSheet(post = post, onDismiss = { commentPost = null }, vm = vm)
    }
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
                            if (language == "ku") "Tu çi difikire?" else "Ne düşünüyorsun?",
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
                    if (language == "ku") "Alıntî" else "Alıntı ekle",
                    color    = if (quote != null) Primary else Muted,
                    fontSize = 11.sp,
                    modifier = Modifier.clickable { onQuoteAdd() },
                )
                Spacer(Modifier.weight(1f))
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
                        if (language == "ku") "Parve bike" else "Paylaş",
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
                    Text(if (language == "ku") "Betal bike" else "İptal", color = Muted)
                }
                Text(
                    if (language == "ku") "Nivîsek Nû" else "Yeni Gönderi",
                    fontWeight = FontWeight.SemiBold,
                    color      = OnBackground,
                    fontSize   = 15.sp,
                )
                TextButton(
                    onClick  = { if (text.isNotBlank() || quotePayload != null) onPost(text.trim(), quotePayload) },
                    enabled  = text.isNotBlank() || quotePayload != null,
                ) {
                    Text(
                        if (language == "ku") "Parve bike" else "Paylaş",
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
                        Text(if (language == "ku") "Tu çi difikire?" else "Ne düşünüyorsun?", color = Muted)
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
                    if (language == "ku") "Alıntî" else "Alıntı ekle",
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
) {
    val myUid            = FirebaseAuth.getInstance().currentUser?.uid ?: ""
    val isOwn            = post.uid == myUid
    var menuExpanded     by remember { mutableStateOf(false) }
    var showEditDialog   by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Background)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
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
                Column {
                    Text(post.displayName.ifBlank { "Bênas" }, fontWeight = FontWeight.SemiBold, color = OnBackground, fontSize = 14.sp)
                    Text(
                        if (post.username.isNotBlank()) "@${post.username}" else "—",
                        color = Muted, fontSize = 12.sp,
                    )
                }
            }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Seçenekler", tint = Muted)
                }
                DropdownMenu(
                    expanded         = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    containerColor   = HeftSurface,
                ) {
                    if (isOwn) {
                        DropdownMenuItem(
                            text        = { Text("Düzenle", color = OnBackground) },
                            leadingIcon = { Icon(Icons.Default.Create, null, tint = Muted) },
                            onClick     = { menuExpanded = false; showEditDialog = true },
                        )
                        DropdownMenuItem(
                            text        = { Text("Sil", color = Color(0xFFEF4444)) },
                            leadingIcon = { Icon(Icons.Default.Delete, null, tint = Color(0xFFEF4444)) },
                            onClick     = { menuExpanded = false; showDeleteDialog = true },
                        )
                    } else {
                        DropdownMenuItem(
                            text        = { Text("Paylaş", color = OnBackground) },
                            leadingIcon = { Icon(Icons.Default.Repeat, null, tint = Muted) },
                            onClick     = { menuExpanded = false; onShare() },
                        )
                        if (onStoryShare != null) {
                            DropdownMenuItem(
                                text        = { Text("Hikaye Olarak Paylaş") },
                                leadingIcon = { Icon(Icons.Outlined.Wallpaper, null) },
                                onClick     = { menuExpanded = false; onStoryShare() },
                            )
                        }
                        DropdownMenuItem(
                            text        = { Text("Şikayet et", color = Color(0xFFEF4444)) },
                            leadingIcon = { Icon(Icons.Default.Flag, null, tint = Color(0xFFEF4444)) },
                            onClick     = { menuExpanded = false },
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
                Text(post.text, color = OnBackground, fontSize = 15.sp, lineHeight = 22.sp)
                Spacer(Modifier.height(8.dp))
            }
                        if (post.repostType.isNotBlank() && post.repostType != "feed") {
                Surface(shape = RoundedCornerShape(6.dp), color = Primary.copy(alpha = 0.12f),
                    modifier = Modifier.padding(bottom = 6.dp)) {
                    Text(when(post.repostType){"serial"->"📖 Serial";"chapter"->"📄 Bölüm";else->post.repostType},
                        color = Primary, fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                }
            }
            // Repost kartı — DÜZELTİLDİ: orijinal gönderi embed olarak gösterilir
            if (post.repostType.isNotBlank()) {
                Surface(
                    shape    = RoundedCornerShape(10.dp),
                    color    = SurfaceVar,
                    border   = androidx.compose.foundation.BorderStroke(0.5.dp, Divider),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        // Tip etiketi
                        Text(
                            when (post.repostType) { "serial" -> "📖 Serial"; "blog" -> "📄 Blog"; "feed" -> "🔁 Gönderi"; else -> "🔁" },
                            color = Primary, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        )
                        // Orijinal yazarın bilgisi (feed reposti için)
                        if (post.repostType == "feed" && post.repostAuthor.isNotBlank()) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (post.repostAuthorPhoto.isNotBlank()) {
                                    AsyncImage(
                                        model = post.repostAuthorPhoto, contentDescription = null,
                                        modifier = Modifier.size(22.dp).clip(CircleShape),
                                        contentScale = ContentScale.Crop,
                                    )
                                }
                                Text(post.repostAuthor, color = OnBackground, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        // Orijinal içerik
                        if (post.repostTitle.isNotBlank()) Text(post.repostTitle, color = OnBackground, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
                        if (post.serialTitle.isNotBlank()) Text(post.serialTitle, color = OnBackground, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
                        if (post.chapterTitle.isNotBlank()) Text("Bölüm ${post.chapterOrder}: ${post.chapterTitle}", color = Muted, fontSize = 12.sp)
                        if (post.repostText.isNotBlank()) Text(post.repostText, color = OnSurface, fontSize = 13.sp, maxLines = 4, lineHeight = 19.sp)
                        val rImg = listOf(post.repostImg, post.serialCover).firstOrNull { it.isNotBlank() } ?: ""
                        if (rImg.isNotBlank()) {
                            AsyncImage(
                                model = rImg, contentDescription = null,
                                modifier = Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop,
                            )
                        }
                    }
                }
            }
            val displayImg = post.imgUrl.ifBlank { post.imageURL }
            if (displayImg.isNotBlank()) {
                AsyncImage(
                    model              = displayImg,
                    contentDescription = null,
                    modifier           = Modifier.fillMaxWidth().heightIn(max = 300.dp).clip(RoundedCornerShape(12.dp)),
                    contentScale       = ContentScale.Crop,
                )
                Spacer(Modifier.height(8.dp))
            }
        }

        // Aksiyonlar
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onLike) {
                Icon(
                    if (post.isLikedByMe) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = "Beğen",
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
                Icon(Icons.Default.Repeat, null, tint = Muted, modifier = Modifier.size(20.dp))
            }
            if (post.repostsCount > 0) Text(post.repostsCount.toString(), color = Muted, fontSize = 13.sp)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onSave) {
                Icon(
                    if (post.isSavedByMe) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                    contentDescription = "Kaydet",
                    tint               = if (post.isSavedByMe) Amber else Muted,
                    modifier           = Modifier.size(20.dp),
                )
            }
        }
    }

    // Düzenleme dialog
    if (showEditDialog) {
        EditPostDialog(
            currentText = post.text,
            onDismiss   = { showEditDialog = false },
            onSave      = { newText -> onEdit?.invoke(newText); showEditDialog = false },
        )
    }

    // Silme onay dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            containerColor   = HeftSurface,
            title   = { Text("Gönderiyi sil?", color = OnBackground, fontWeight = FontWeight.SemiBold) },
            text    = { Text("Bu gönderi kalıcı olarak silinecek.", color = Muted, fontSize = 14.sp) },
            confirmButton = {
                TextButton(onClick = { onDelete?.invoke(); showDeleteDialog = false }) {
                    Text("Sil", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("İptal", color = Muted)
                }
            },
        )
    }
}

// ── EditPostDialog ────────────────────────────────────────────────────────────

@Composable
fun EditPostDialog(currentText: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(currentText) }
    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = HeftSurface), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Gönderiyi Düzenle", fontWeight = FontWeight.SemiBold, color = OnBackground, fontSize = 16.sp)
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
                    TextButton(onClick = onDismiss) { Text("İptal", color = Muted) }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { if (text.isNotBlank()) onSave(text) },
                        colors  = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = Color.Black),
                        shape   = RoundedCornerShape(10.dp),
                    ) { Text("Kaydet", fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

// ── CommentSheet ──────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentSheet(post: Post, onDismiss: () -> Unit, vm: FeedViewModel) {
    val comments    by vm.comments.collectAsState()
    var commentText by remember { mutableStateOf("") }
    LaunchedEffect(post.id) { vm.loadComments(post.id) }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = HeftSurface) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.75f)
                .padding(horizontal = 16.dp)
                .navigationBarsPadding(),
        ) {
            Text("Yorumlar", fontWeight = FontWeight.SemiBold, color = OnBackground, modifier = Modifier.padding(vertical = 8.dp))
            HorizontalDivider(color = Divider)
            LazyColumn(modifier = Modifier.weight(1f), contentPadding = PaddingValues(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(comments, key = { it.id }) { cmt ->
                    Row(verticalAlignment = Alignment.Top) {
                        AsyncImage(
                            model = cmt.photoURL.ifEmpty { null }, contentDescription = null,
                            modifier = Modifier.size(32.dp).clip(CircleShape).background(SurfaceVar),
                            contentScale = ContentScale.Crop,
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(cmt.displayName, fontWeight = FontWeight.SemiBold, color = OnBackground, fontSize = 13.sp)
                            Text(cmt.text, color = OnSurface, fontSize = 14.sp, lineHeight = 20.sp)
                        }
                    }
                }
            }
            HorizontalDivider(color = Divider)
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = commentText, onValueChange = { commentText = it },
                    placeholder = { Text("Yorum yaz...", color = Muted) },
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
}
