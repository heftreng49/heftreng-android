package com.heftreng.app.ui.screens.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.FavoriteBorder
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.heftreng.app.data.model.Comment
import com.heftreng.app.navigation.Screen
import com.heftreng.app.ui.screens.social.LikerListSheet
import com.heftreng.app.ui.theme.*
import com.heftreng.app.viewmodel.FeedViewModel
import com.heftreng.app.viewmodel.SocialViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostDetailScreen(
    navController: NavController,
    viewModel    : FeedViewModel,
    postId       : String,
    socialVm     : SocialViewModel = hiltViewModel(),
) {
    val posts    by viewModel.posts.collectAsState()
    val comments by viewModel.comments.collectAsState()
    val likers   by socialVm.likers.collectAsState()
    val socialLoading by socialVm.loading.collectAsState()

    val post  = posts.find { it.id == postId }
    val myUid = remember { viewModel.uid }

    var commentText    by remember { mutableStateOf("") }
    var showLikers     by remember { mutableStateOf(false) }
    var cmtToDelete    by remember { mutableStateOf<Comment?>(null) }
    var showCmtLikers  by remember { mutableStateOf<String?>(null) }
    var editComment    by remember { mutableStateOf<Comment?>(null) }
    var editText       by remember { mutableStateOf("") }

    LaunchedEffect(postId) { viewModel.loadComments(postId) }

    Scaffold(
        modifier       = Modifier.imePadding(),
        containerColor = Background,
        topBar = {
            TopAppBar(
                title = { Text("Gönderi", color = OnBackground, fontSize = 17.sp, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = OnBackground)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Background),
            )
        },
    ) { padding ->
        if (post == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Amber)
            }
            return@Scaffold
        }

        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 8.dp),
            ) {
                item {
                    PostCard(
                        post         = post,
                        onLike       = { viewModel.toggleLike(post) },
                        onSave       = { viewModel.toggleSave(post) },
                        onProfile    = { navController.navigate(Screen.Profile.go(post.uid)) },
                        onComment    = {},
                        onShare      = { viewModel.repost(post) },
                        onShowLikers = { socialVm.loadPostLikers(post.id); showLikers = true },
                    )
                    HorizontalDivider(color = SurfaceVar, thickness = 6.dp)
                }

                if (post.likesCount > 0) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { socialVm.loadPostLikers(post.id); showLikers = true }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Filled.Favorite, null, tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("${post.likesCount} beğeni", color = OnBackground, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        }
                        HorizontalDivider(color = Divider)
                    }
                }

                item {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Yorumlar", color = Muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        if (post.commentsCount > 0) {
                            Spacer(Modifier.width(6.dp))
                            Box(Modifier.clip(RoundedCornerShape(10.dp)).background(SurfaceVar).padding(horizontal = 8.dp, vertical = 2.dp)) {
                                Text("${post.commentsCount}", color = Muted, fontSize = 11.sp)
                            }
                        }
                    }
                }

                if (comments.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            Text("Henüz yorum yok", color = Muted, fontSize = 14.sp)
                        }
                    }
                } else {
                    items(comments, key = { it.id }) { cmt ->
                        // ⋮ butonu her yorumda görünür — silme/düzenleme yetkisi
                        // deleteComment/editComment içinde Firestore'da kontrol edilir
                        val isOwner   = myUid.isNotEmpty() && myUid == cmt.uid
                        val canManage = myUid.isNotEmpty() && (myUid == cmt.uid || myUid == post.uid)
                        CommentRow(
                            comment      = cmt,
                            canManage    = canManage,
                            isOwner      = isOwner,
                            onLike       = { viewModel.toggleCommentLike(postId, cmt) },
                            onProfile    = { navController.navigate("profile/${cmt.uid}") },
                            onShowLikers = { socialVm.loadCommentLikers(cmt.id); showCmtLikers = cmt.id },
                            onDelete     = { cmtToDelete = cmt },
                            onEdit       = { editText = cmt.text; editComment = cmt },
                        )
                        HorizontalDivider(color = Divider, thickness = 0.5.dp)
                    }
                }
            }

            // Yorum yazma alanı
            HorizontalDivider(color = Divider)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(HeftSurface)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .navigationBarsPadding(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value         = commentText,
                    onValueChange = { commentText = it },
                    placeholder   = { Text("Yorum yaz...", color = Muted, fontSize = 14.sp) },
                    modifier      = Modifier.weight(1f),
                    shape         = RoundedCornerShape(24.dp),
                    singleLine    = true,
                    colors        = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor      = Amber,
                        unfocusedBorderColor    = Divider,
                        focusedTextColor        = OnBackground,
                        unfocusedTextColor      = OnBackground,
                        unfocusedContainerColor = SurfaceVar,
                        focusedContainerColor   = SurfaceVar,
                    ),
                )
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        if (commentText.isNotBlank()) {
                            viewModel.addComment(post, commentText.trim())
                            commentText = ""
                        }
                    },
                    enabled = commentText.isNotBlank(),
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, null, tint = if (commentText.isNotBlank()) Amber else Muted)
                }
            }
        }
    }

    // ── Silme onay dialogu ────────────────────────────────────────────────────
    cmtToDelete?.let { cmt ->
        AlertDialog(
            onDismissRequest = { cmtToDelete = null },
            containerColor   = HeftSurface,
            title  = { Text("Yorumu Sil", color = OnBackground, fontWeight = FontWeight.SemiBold) },
            text   = { Text("Bu yorumu silmek istediğine emin misin?", color = Muted) },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteComment(postId, cmt.id); cmtToDelete = null }) {
                    Text("Sil", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { cmtToDelete = null }) { Text("İptal", color = Muted) }
            },
        )
    }

    // ── Düzenleme dialogu ─────────────────────────────────────────────────────
    editComment?.let { cmt ->
        AlertDialog(
            onDismissRequest = { editComment = null },
            containerColor   = HeftSurface,
            title  = { Text("Yorumu Düzenle", color = OnBackground, fontWeight = FontWeight.SemiBold) },
            text   = {
                OutlinedTextField(
                    value         = editText,
                    onValueChange = { editText = it },
                    modifier      = Modifier.fillMaxWidth(),
                    colors        = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = Amber,
                        unfocusedBorderColor = Divider,
                        focusedTextColor     = OnBackground,
                        unfocusedTextColor   = OnBackground,
                        unfocusedContainerColor = SurfaceVar,
                        focusedContainerColor   = SurfaceVar,
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.editComment(postId, cmt.id, editText)
                        editComment = null
                    },
                    enabled = editText.isNotBlank() && editText != cmt.text,
                ) {
                    Text("Kaydet", color = Amber, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { editComment = null }) { Text("İptal", color = Muted) }
            },
        )
    }

    if (showLikers) {
        LikerListSheet(
            likers    = likers,
            loading   = socialLoading,
            onDismiss = { showLikers = false; socialVm.clearLikers() },
            onProfile = { uid -> showLikers = false; navController.navigate("profile/$uid") },
        )
    }

    if (showCmtLikers != null) {
        LikerListSheet(
            title     = "Yorum Beğenenler",
            likers    = likers,
            loading   = socialLoading,
            onDismiss = { showCmtLikers = null; socialVm.clearLikers() },
            onProfile = { uid -> showCmtLikers = null; navController.navigate("profile/$uid") },
        )
    }
}

// ── Yorum satırı ─────────────────────────────────────────────────────────────
@Composable
private fun CommentRow(
    comment      : Comment,
    canManage    : Boolean,
    isOwner      : Boolean,
    onLike       : () -> Unit,
    onProfile    : () -> Unit,
    onShowLikers : () -> Unit,
    onDelete     : () -> Unit,
    onEdit       : () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier          = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        // Avatar
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(SurfaceVar)
                .clickable { onProfile() },
        ) {
            if (comment.photoURL.isNotBlank()) {
                AsyncImage(
                    model = comment.photoURL, contentDescription = null,
                    modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
                )
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        comment.displayName.firstOrNull()?.uppercase() ?: "?",
                        color = OnBackground, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(comment.displayName, fontWeight = FontWeight.SemiBold, color = OnBackground, fontSize = 13.sp, modifier = Modifier.clickable { onProfile() })
            Spacer(Modifier.height(2.dp))
            Text(comment.text, color = OnSurface, fontSize = 14.sp, lineHeight = 20.sp)
        }
        Spacer(Modifier.width(4.dp))
        // Sağ taraf — beğeni + menü
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            IconButton(onClick = onLike, modifier = Modifier.size(32.dp)) {
                Icon(
                    if (comment.isLikedByMe) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    null,
                    tint     = if (comment.isLikedByMe) Color(0xFFEF4444) else Muted,
                    modifier = Modifier.size(16.dp),
                )
            }
            if (comment.likesCount > 0) {
                Text("${comment.likesCount}", color = Muted, fontSize = 11.sp, modifier = Modifier.clickable { onShowLikers() })
            }
            if (canManage) {
                Box {
                    IconButton(
                        onClick  = { menuExpanded = true },
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Seçenekler", tint = Muted, modifier = Modifier.size(18.dp))
                    }
                    DropdownMenu(
                        expanded         = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                        containerColor   = HeftSurface,
                    ) {
                        if (isOwner) {
                            DropdownMenuItem(
                                text        = { Text("Düzenle", color = OnBackground) },
                                leadingIcon = { Icon(Icons.Default.Edit, null, tint = Amber) },
                                onClick     = { menuExpanded = false; onEdit() },
                            )
                        }
                        DropdownMenuItem(
                            text        = { Text("Sil", color = Color(0xFFEF4444)) },
                            leadingIcon = { Icon(Icons.Default.Delete, null, tint = Color(0xFFEF4444)) },
                            onClick     = { menuExpanded = false; onDelete() },
                        )
                    }
                }
            }
        }
    }
}
