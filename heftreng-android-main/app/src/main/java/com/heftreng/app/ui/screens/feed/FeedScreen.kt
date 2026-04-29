package com.heftreng.app.ui.screens.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.heftreng.app.data.model.Post
import com.heftreng.app.navigation.Screen
import com.heftreng.app.ui.component.QuoteButton
import com.heftreng.app.ui.component.QuoteCard
import com.heftreng.app.ui.component.QuoteDialog
import com.heftreng.app.ui.component.QuoteInputSection
import com.heftreng.app.ui.component.QuotePayload
import com.heftreng.app.ui.theme.*
import com.heftreng.app.viewmodel.FeedViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    navController: NavController,
    onOpenDrawer : () -> Unit = {},
    language     : String = "tr",
    vm           : FeedViewModel = hiltViewModel(),
) {
    val posts   by vm.posts.collectAsState()
    val loading by vm.loading.collectAsState()
    var showNewPost by remember { mutableStateOf(false) }
    var newPostText by remember { mutableStateOf("") }
    var commentPost by remember { mutableStateOf<Post?>(null) }
    var quotePayload by remember { mutableStateOf<QuotePayload?>(null) }
    var showQuoteDialog by remember { mutableStateOf(false) }
    var showQuoteInput by remember { mutableStateOf(false) }

    // ── Alıntı oluşturma dialog ──────────────────────────────────────────────
    if (showQuoteDialog) {
        QuoteDialog(
            initialText   = quotePayload?.text ?: "",
            initialBook   = quotePayload?.bookName ?: "",
            initialAuthor = quotePayload?.authorName ?: "",
            onDismiss     = { showQuoteDialog = false },
            onConfirm     = { payload ->
                quotePayload = payload
                showQuoteDialog = false
            },
        )
    }

    Scaffold(
        containerColor = Background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "heftreng",
                        fontWeight    = FontWeight.Bold,
                        color         = Amber,
                        fontSize      = 22.sp,
                        letterSpacing = (-0.5).sp,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Default.Menu, contentDescription = "Menü", tint = OnBackground)
                    }
                },
                actions = {
                    IconButton(onClick = { navController.navigate(Screen.Notifications.route) }) {
                        Icon(Icons.Outlined.NotificationsNone, contentDescription = "Bildirimler", tint = OnBackground)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Background),
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick        = { showNewPost = true },
                containerColor = Amber,
                contentColor   = Color.Black,
                shape          = RoundedCornerShape(16.dp),
            ) {
                Icon(Icons.Default.Add, contentDescription = "Yeni gönderi")
            }
        }
    ) { padding ->
        if (loading && posts.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Amber)
            }
        } else {
            LazyColumn(
                modifier       = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(bottom = 80.dp),
            ) {
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
                        onTap     = { navController.navigate(Screen.PostDetail.go(post.id)) },
                    )
                    HorizontalDivider(color = Divider, thickness = 0.5.dp)
                }
            }
        }
    }

    if (showNewPost) {
        ModalBottomSheet(
            onDismissRequest = { showNewPost = false },
            containerColor   = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .navigationBarsPadding(),
            ) {
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = { showNewPost = false }) {
                        Text("İptal", color = Muted)
                    }
                    Text(if (language == "ku") "Nivîsek Nû" else "Yeni Gönderi", fontWeight = FontWeight.SemiBold, color = OnBackground)
                    TextButton(onClick = {
                        if (newPostText.isNotBlank()) {
                            vm.createPost(
                                text       = newPostText.trim(),
                                quoteText  = quotePayload?.text ?: "",
                                authorName = quotePayload?.authorName ?: "",
                                bookName   = quotePayload?.bookName ?: "",
                            )
                            newPostText = ""
                            showNewPost = false
                        }
                    }) {
                        Text(if (language == "ku") "Parve bike" else "Paylaş", color = Amber, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(8.dp))

                // Seçili alıntı varsa göster
                QuoteInputSection(
                    quote    = quotePayload,
                    onRemove = { quotePayload = null },
                    modifier = Modifier.padding(bottom = 8.dp),
                )

                OutlinedTextField(
                    value           = newPostText,
                    onValueChange   = { newPostText = it },
                    placeholder     = { Text("Ne düşünüyorsun?", color = Muted) },
                    modifier        = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                    colors          = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor      = Amber,
                        unfocusedBorderColor    = Divider,
                        focusedTextColor        = OnBackground,
                        unfocusedTextColor      = OnBackground,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedContainerColor   = MaterialTheme.colorScheme.surface,
                    ),
                    shape = RoundedCornerShape(12.dp),
                )
                // Alt araç çubuğu — alıntı ve görsel ekleme
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Alıntı butonu
                    IconButton(onClick = { showQuoteDialog = true }) {
                        Icon(
                            Icons.Default.FormatQuote,
                            contentDescription = "Alıntı Ekle",
                            tint     = if (quotePayload != null) Amber else Muted,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    Text(
                        "Alıntı ekle",
                        color    = if (quotePayload != null) Amber else Muted,
                        fontSize = 12.sp,
                        modifier = Modifier.clickable { showQuoteDialog = true },
                    )
                    Spacer(Modifier.weight(1f))
                    // Karakter sayacı
                    Text(
                        "${newPostText.length}/1000",
                        color    = if (newPostText.length > 900) Error else Muted,
                        fontSize = 11.sp,
                    )
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    commentPost?.let { post ->
        CommentSheet(post = post, onDismiss = { commentPost = null }, vm = vm)
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
                AsyncImage(
                    model              = post.photoURL.ifEmpty { null },
                    contentDescription = post.displayName,
                    modifier           = Modifier.size(40.dp).clip(CircleShape).background(SurfaceVar),
                    contentScale       = ContentScale.Crop,
                )
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
                    containerColor   = MaterialTheme.colorScheme.surface,
                ) {
                    if (isOwn) {
                        DropdownMenuItem(
                            text        = { Text("Düzenle", color = OnBackground) },
                            leadingIcon = { Icon(Icons.Default.Edit, null, tint = Muted) },
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
                    quoteText  = post.quoteText,
                    bookName   = post.bookName,
                    authorName = post.authorName,
                    modifier   = Modifier.padding(bottom = 8.dp),
                )
            }
            if (post.text.isNotBlank()) {
                Text(post.text, color = OnBackground, fontSize = 15.sp, lineHeight = 22.sp)
                Spacer(Modifier.height(8.dp))
            }
            if (post.imageURL.isNotBlank()) {
                AsyncImage(
                    model              = post.imageURL,
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
            if (post.likesCount > 0) Text(post.likesCount.toString(), color = Muted, fontSize = 13.sp)
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
            containerColor   = MaterialTheme.colorScheme.surface,
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
        Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth()) {
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

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
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
