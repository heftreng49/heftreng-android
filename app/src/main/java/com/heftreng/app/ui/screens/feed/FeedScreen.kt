package com.heftreng.app.ui.screens.feed

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.heftreng.app.data.model.Post
import com.heftreng.app.navigation.AppPrefs
import com.heftreng.app.navigation.Screen
import com.heftreng.app.ui.theme.*
import com.heftreng.app.viewmodel.FeedViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    navController: NavController,
    onOpenDrawer : () -> Unit = {},
    vm           : FeedViewModel = hiltViewModel(),
) {
    val posts   by vm.posts.collectAsState()
    val loading by vm.loading.collectAsState()
    var showNewPost by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = Background,
        topBar = {
            TopAppBar(
                title = {
                    Text(buildAnnotatedString {
                        withStyle(SpanStyle(brush = Brush.horizontalGradient(listOf(AppPrefs.accentColor, Color(0xFFFF6B00))), fontWeight = FontWeight.Black, fontSize = 24.sp)) { append("heft") }
                        withStyle(SpanStyle(color = OnBackground, fontWeight = FontWeight.Black, fontSize = 24.sp)) { append("reng") }
                    })
                },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Default.Menu, "Menü", tint = OnBackground)
                    }
                },
                actions = {
                    IconButton(onClick = { navController.navigate(Screen.Notifications.route) }) {
                        Icon(Icons.Outlined.NotificationsNone, "Bildirimler", tint = OnBackground)
                    }
                    IconButton(onClick = {}) {
                        Icon(Icons.Outlined.Search, "Ara", tint = OnBackground)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Background),
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showNewPost = true },
                containerColor = AppPrefs.accentColor, contentColor = Color.Black,
                shape = RoundedCornerShape(16.dp),
            ) { Icon(Icons.Default.Add, "Yeni gönderi") }
        }
    ) { padding ->
        if (loading && posts.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AppPrefs.accentColor)
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
                        onComment = { navController.navigate(Screen.PostDetail.go(post.id)) },
                        onShare   = { vm.repost(post) },
                        onDelete  = { vm.deletePost(post.id) },
                        onEdit    = { newText -> vm.editPost(post.id, newText) },
                    )
                    HorizontalDivider(color = Divider, thickness = 0.5.dp)
                }
            }
        }
    }

    // Yeni Gönderi Sheet
    if (showNewPost) {
        var text       by remember { mutableStateOf("") }
        var showQuote  by remember { mutableStateOf(false) }
        var quoteText  by remember { mutableStateOf("") }
        var authorName by remember { mutableStateOf("") }
        var bookName   by remember { mutableStateOf("") }

        ModalBottomSheet(
            onDismissRequest = { showNewPost = false },
            containerColor   = Surface,
            dragHandle = { Box(Modifier.padding(vertical = 10.dp).size(width = 36.dp, height = 4.dp).background(Divider, RoundedCornerShape(2.dp))) }
        ) {
            Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { showNewPost = false }) { Text("İptal", color = Muted, fontSize = 13.sp) }
                    Text("Yeni Gönderi", fontWeight = FontWeight.Bold, color = OnBackground)
                    Button(
                        onClick = {
                            if (text.isNotBlank() || quoteText.isNotBlank()) {
                                vm.createPost(text.trim(), quoteText.trim(), authorName.trim(), bookName.trim())
                                showNewPost = false
                            }
                        },
                        enabled = text.isNotBlank() || quoteText.isNotBlank(),
                        shape   = RoundedCornerShape(10.dp),
                        colors  = ButtonDefaults.buttonColors(containerColor = AppPrefs.accentColor, contentColor = Color.Black, disabledContainerColor = SurfaceVar, disabledContentColor = Muted),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    ) { Text("Paylaş", fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = text, onValueChange = { text = it },
                    placeholder = { Text("Ne düşünüyorsun?", color = Muted, fontSize = 14.sp) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AppPrefs.accentColor, unfocusedBorderColor = Color.Transparent, focusedTextColor = OnBackground, unfocusedTextColor = OnBackground, unfocusedContainerColor = SurfaceVar, focusedContainerColor = SurfaceVar),
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { showQuote = !showQuote }, contentPadding = PaddingValues(0.dp)) {
                    Icon(if (showQuote) Icons.Default.RemoveCircleOutline else Icons.Default.FormatQuote, null, tint = AppPrefs.accentColor, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (showQuote) "Alıntıyı kaldır" else "Alıntı ekle", color = AppPrefs.accentColor, fontSize = 13.sp)
                }
                AnimatedVisibility(visible = showQuote) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = quoteText, onValueChange = { quoteText = it }, placeholder = { Text("Alıntı metni...", color = Muted) }, modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp), shape = RoundedCornerShape(12.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AppPrefs.accentColor, unfocusedBorderColor = Divider, focusedTextColor = OnBackground, unfocusedTextColor = OnBackground, unfocusedContainerColor = SurfaceVar, focusedContainerColor = SurfaceVar))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(value = authorName, onValueChange = { authorName = it }, placeholder = { Text("Yazar", color = Muted) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp), singleLine = true, colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AppPrefs.accentColor, unfocusedBorderColor = Divider, focusedTextColor = OnBackground, unfocusedTextColor = OnBackground, unfocusedContainerColor = SurfaceVar, focusedContainerColor = SurfaceVar))
                            OutlinedTextField(value = bookName, onValueChange = { bookName = it }, placeholder = { Text("Kitap", color = Muted) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp), singleLine = true, colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AppPrefs.accentColor, unfocusedBorderColor = Divider, focusedTextColor = OnBackground, unfocusedTextColor = OnBackground, unfocusedContainerColor = SurfaceVar, focusedContainerColor = SurfaceVar))
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

// ── PostCard ─────────────────────────────────────────────────────────────────
@Composable
fun PostCard(
    post     : Post,
    onLike   : () -> Unit,
    onSave   : () -> Unit,
    onProfile: () -> Unit,
    onComment: () -> Unit,
    onShare  : () -> Unit,
    onDelete : (() -> Unit)? = null,
    onEdit   : ((String) -> Unit)? = null,
) {
    val myUid            = FirebaseAuth.getInstance().currentUser?.uid ?: ""
    val isOwn            = post.uid == myUid
    var menuExpanded     by remember { mutableStateOf(false) }
    var showEditDialog   by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth().background(Background).padding(horizontal = 16.dp, vertical = 12.dp)) {
        // Header
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(modifier = Modifier.weight(1f).clickable { onProfile() }, verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = post.photoURL.ifEmpty { null }, contentDescription = post.displayName,
                    modifier = Modifier.size(40.dp).clip(CircleShape).background(SurfaceVar),
                    contentScale = ContentScale.Crop,
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    val nameToShow = post.displayName.ifBlank { post.username.ifBlank { "Bikarhênerê Heftreng" } }
                    Text(nameToShow, fontWeight = FontWeight.SemiBold, color = OnBackground, fontSize = AppPrefs.fontSize.sp)
                    if (post.username.isNotBlank() && post.username != nameToShow)
                        Text("@${post.username}", color = Muted, fontSize = 12.sp)
                }
            }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Default.MoreVert, "Seçenekler", tint = Muted)
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }, containerColor = Surface) {
                    if (isOwn) {
                        DropdownMenuItem(
                            text = { Text("Düzenle", color = OnBackground) },
                            leadingIcon = { Icon(Icons.Default.Edit, null, tint = Muted) },
                            onClick = { menuExpanded = false; showEditDialog = true },
                        )
                        DropdownMenuItem(
                            text = { Text("Sil", color = Color(0xFFEF4444)) },
                            leadingIcon = { Icon(Icons.Default.Delete, null, tint = Color(0xFFEF4444)) },
                            onClick = { menuExpanded = false; showDeleteDialog = true },
                        )
                    } else {
                        DropdownMenuItem(
                            text = { Text("Paylaş", color = OnBackground) },
                            leadingIcon = { Icon(Icons.Default.Repeat, null, tint = Muted) },
                            onClick = { menuExpanded = false; onShare() },
                        )
                        DropdownMenuItem(
                            text = { Text("Şikayet et", color = Color(0xFFEF4444)) },
                            leadingIcon = { Icon(Icons.Default.Flag, null, tint = Color(0xFFEF4444)) },
                            onClick = { menuExpanded = false },
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // Quote
        if (post.quoteText.isNotBlank()) {
            Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(SurfaceVar)) {
                Box(modifier = Modifier.width(4.dp).fillMaxHeight().background(Brush.verticalGradient(listOf(AppPrefs.accentColor, Color(0xFFFF6B00))), RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp)).align(Alignment.CenterStart))
                Column(modifier = Modifier.padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp)) {
                    Icon(Icons.Default.FormatQuote, null, tint = AppPrefs.accentColor.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
                    Spacer(Modifier.height(4.dp))
                    Text(post.quoteText, color = OnSurface, fontSize = (AppPrefs.fontSize - 1).sp, lineHeight = 21.sp, fontStyle = FontStyle.Italic)
                    if (post.authorName.isNotBlank() || post.bookName.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        HorizontalDivider(color = Divider.copy(alpha = 0.4f), thickness = 0.5.dp)
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Default.AutoStories, null, tint = AppPrefs.accentColor, modifier = Modifier.size(11.dp))
                            Text(buildString {
                                if (post.authorName.isNotBlank()) append(post.authorName)
                                if (post.authorName.isNotBlank() && post.bookName.isNotBlank()) append(" — ")
                                if (post.bookName.isNotBlank()) append(post.bookName)
                            }, color = AppPrefs.accentColor, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        // Text — tıklanınca detay
        if (post.text.isNotBlank()) {
            Text(post.text, color = OnBackground, fontSize = AppPrefs.fontSize.sp, lineHeight = (AppPrefs.fontSize + 7).sp,
                modifier = Modifier.clickable { onComment() })
            Spacer(Modifier.height(8.dp))
        }

        // Image
        if (post.imageURL.isNotBlank()) {
            AsyncImage(
                model = post.imageURL, contentDescription = null,
                modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp, max = 380.dp).clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop,
            )
            Spacer(Modifier.height(8.dp))
        }

        // Actions
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onLike) {
                Icon(if (post.isLikedByMe) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder, "Beğen",
                    tint = if (post.isLikedByMe) Color(0xFFEF4444) else Muted, modifier = Modifier.size(20.dp))
            }
            if (post.likesCount > 0) Text(post.likesCount.toString(), color = Muted, fontSize = 13.sp)
            Spacer(Modifier.width(4.dp))
            IconButton(onClick = onComment) {
                Icon(Icons.Outlined.ChatBubbleOutline, "Yorumlar", tint = Muted, modifier = Modifier.size(20.dp))
            }
            if (post.commentsCount > 0) Text(post.commentsCount.toString(), color = Muted, fontSize = 13.sp)
            Spacer(Modifier.width(4.dp))
            IconButton(onClick = onShare) {
                Icon(Icons.Default.Repeat, "Paylaş", tint = Muted, modifier = Modifier.size(20.dp))
            }
            if (post.repostsCount > 0) Text(post.repostsCount.toString(), color = Muted, fontSize = 13.sp)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onSave) {
                Icon(if (post.isSavedByMe) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder, "Kaydet",
                    tint = if (post.isSavedByMe) AppPrefs.accentColor else Muted, modifier = Modifier.size(20.dp))
            }
        }
    }

    // Edit Dialog
    if (showEditDialog) {
        var editText by remember { mutableStateOf(post.text) }
        Dialog(onDismissRequest = { showEditDialog = false }) {
            Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Surface), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("Gönderiyi Düzenle", fontWeight = FontWeight.SemiBold, color = OnBackground, fontSize = 16.sp)
                    Spacer(Modifier.height(14.dp))
                    OutlinedTextField(
                        value = editText, onValueChange = { editText = it },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AppPrefs.accentColor, unfocusedBorderColor = Divider, focusedTextColor = OnBackground, unfocusedTextColor = OnBackground, unfocusedContainerColor = SurfaceVar, focusedContainerColor = SurfaceVar),
                        shape = RoundedCornerShape(12.dp),
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                        TextButton(onClick = { showEditDialog = false }) { Text("İptal", color = Muted) }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = { if (editText.isNotBlank()) { onEdit?.invoke(editText); showEditDialog = false } },
                            colors = ButtonDefaults.buttonColors(containerColor = AppPrefs.accentColor, contentColor = Color.Black),
                            shape = RoundedCornerShape(10.dp),
                        ) { Text("Kaydet", fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
    }

    // Delete Dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            containerColor   = Surface,
            title   = { Text("Gönderiyi sil?", color = OnBackground, fontWeight = FontWeight.SemiBold) },
            text    = { Text("Bu gönderi kalıcı olarak silinecek.", color = Muted, fontSize = 14.sp) },
            confirmButton = {
                TextButton(onClick = { onDelete?.invoke(); showDeleteDialog = false }) {
                    Text("Sil", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("İptal", color = Muted) }
            },
        )
    }
}
