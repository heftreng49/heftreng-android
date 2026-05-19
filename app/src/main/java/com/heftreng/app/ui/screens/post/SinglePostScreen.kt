package com.heftreng.app.ui.screens.post

// SinglePostScreen — Tek gönderi detay ekranı
// Tam özellikler: yorum beğeni, beğenenler listesi, profil tıklama

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
import androidx.compose.material.icons.filled.Favorite
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
import com.heftreng.app.ui.screens.feed.PostCard
import com.heftreng.app.ui.screens.social.LikerListSheet
import com.heftreng.app.ui.theme.*
import com.heftreng.app.viewmodel.FeedViewModel
import com.heftreng.app.viewmodel.SocialViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SinglePostScreen(
    postId       : String,
    navController: NavController,
    vm           : FeedViewModel  = hiltViewModel(),
    socialVm     : SocialViewModel = hiltViewModel(),
    language     : String = "tr",
) {
    val ku = language == "ku"
    val posts         by vm.posts.collectAsState()
    val comments      by vm.comments.collectAsState()
    val likers        by socialVm.likers.collectAsState()
    val socialLoading by socialVm.loading.collectAsState()

    val post  = posts.find { it.id == postId }
    val myUid = FirebaseAuth.getInstance().currentUser?.uid ?: ""

    var commentText   by remember { mutableStateOf("") }
    var showLikers    by remember { mutableStateOf(false) }
    var cmtLikersId   by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(postId) {
        vm.ensurePost(postId)   // Feed'de yoksa Firestore'dan çek
        vm.loadComments(postId)
    }

    Scaffold(
        containerColor = Background,
        topBar = {
            TopAppBar(
                title = { Text(if (ku) "Nivîs" else "Gönderi", fontWeight = FontWeight.SemiBold, color = OnBackground) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, if (ku) "Vegere" else "Geri", tint = OnBackground)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Background),
            )
        },
    ) { padding ->
        if (post == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Amber)
            }
            return@Scaffold
        }

        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(modifier = Modifier.weight(1f), contentPadding = PaddingValues(bottom = 8.dp)) {
                // Gönderi kartı
                item {
                    PostCard(
                        post         = post,
                        onLike       = { vm.toggleLike(post) },
                        onSave       = { vm.toggleSave(post) },
                        onProfile    = { navController.navigate(Screen.Profile.go(post.uid)) },
                        onComment    = {},
                        onShare      = { vm.repost(post) },
                        onDelete     = { vm.deletePost(post.id); navController.popBackStack() },
                        onEdit       = { newText -> vm.editPost(post.id, newText) },
                        onTap        = null,
                        onShowLikers = {
                            socialVm.loadPostLikers(post.id)
                            showLikers = true
                        },
                        onTapAuthor = { author ->
                            navController.navigate("author_quotes/${java.net.URLEncoder.encode(author, "UTF-8")}")
                        },
                        language = language,
                        onTapBook = { book ->
                            navController.navigate("book_quotes/${java.net.URLEncoder.encode(book, "UTF-8")}")
                        },
                    )
                    HorizontalDivider(color = SurfaceVar, thickness = 6.dp)
                }

                // Beğeni satırı — tıklanabilir
                if (post.likesCount > 0) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    socialVm.loadPostLikers(post.id)
                                    showLikers = true
                                }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Filled.Favorite, null, tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("${post.likesCount} ${if (ku) "xweşandin" else "beğeni"}", color = OnBackground, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        }
                        HorizontalDivider(color = Divider)
                    }
                }

                // Yorumlar başlığı
                item {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(if (ku) "Şîrove" else "Yorumlar", color = Muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        if (post.commentsCount > 0) {
                            Spacer(Modifier.width(6.dp))
                            Box(Modifier.clip(RoundedCornerShape(10.dp)).background(SurfaceVar).padding(horizontal = 8.dp, vertical = 2.dp)) {
                                Text("${post.commentsCount}", color = Muted, fontSize = 11.sp)
                            }
                        }
                    }
                }

                // Yorumlar
                if (comments.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            Text(if (ku) "Hîn şîrove tune" else "Henüz yorum yok", color = Muted, fontSize = 14.sp)
                        }
                    }
                } else {
                    items(comments, key = { it.id }) { cmt ->
                        CommentItem(
                            comment      = cmt,
                            myUid        = myUid,
                            onLike       = { vm.toggleCommentLike(postId, cmt) },
                            onProfile    = { navController.navigate("profile/${cmt.uid}") },
                            onShowLikers = {
                                socialVm.loadCommentLikers(cmt.id)
                                cmtLikersId = cmt.id
                            },
                        )
                        HorizontalDivider(color = Divider, thickness = 0.5.dp)
                    }
                }
            }

            // Yorum giriş alanı
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
                    placeholder   = { Text(if (ku) "Şîrove binivîse..." else "Yorum yaz...", color = Muted, fontSize = 14.sp) },
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
                            vm.addComment(post, commentText.trim())
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

    // Gönderi beğenenler
    if (showLikers) {
        LikerListSheet(
            likers    = likers,
            loading   = socialLoading,
            onDismiss = { showLikers = false; socialVm.clearLikers() },
            onProfile = { uid -> showLikers = false; navController.navigate("profile/$uid") },
        )
    }

    // Yorum beğenenler
    if (cmtLikersId != null) {
        LikerListSheet(
            title     = "Yorum Beğenenler",
            likers    = likers,
            loading   = socialLoading,
            onDismiss = { cmtLikersId = null; socialVm.clearLikers() },
            onProfile = { uid -> cmtLikersId = null; navController.navigate("profile/$uid") },
        )
    }
}

// ── Yorum bileşeni ────────────────────────────────────────────────────────────
@Composable
private fun CommentItem(
    comment      : Comment,
    myUid        : String,
    onLike       : () -> Unit,
    onProfile    : () -> Unit,
    onShowLikers : () -> Unit,
) {
    Row(
        modifier          = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier.size(34.dp).clip(CircleShape).background(SurfaceVar).clickable { onProfile() },
        ) {
            if (comment.photoURL.isNotBlank()) {
                AsyncImage(
                    model = comment.photoURL, contentDescription = null,
                    modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
                )
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(comment.displayName.firstOrNull()?.uppercase() ?: "?", color = OnBackground, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(comment.displayName, fontWeight = FontWeight.SemiBold, color = OnBackground, fontSize = 13.sp, modifier = Modifier.clickable { onProfile() })
            Spacer(Modifier.height(2.dp))
            Text(comment.text, color = OnSurface, fontSize = 14.sp, lineHeight = 20.sp)
        }
        Spacer(Modifier.width(8.dp))
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
        }
    }
}
