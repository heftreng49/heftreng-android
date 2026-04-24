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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.heftreng.app.data.model.Post
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
    var showNewPost  by remember { mutableStateOf(false) }
    var newPostText  by remember { mutableStateOf("") }
    var commentPost  by remember { mutableStateOf<Post?>(null) }

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
        } else if (posts.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.DynamicFeed, contentDescription = null, tint = Muted, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("Henüz gönderi yok", color = Muted)
                }
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
                    )
                    HorizontalDivider(color = Divider, thickness = 0.5.dp)
                }
            }
        }
    }

    if (showNewPost) {
        ModalBottomSheet(
            onDismissRequest = { showNewPost = false },
            containerColor   = Surface,
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
                    Text("Yeni Gönderi", fontWeight = FontWeight.SemiBold, color = OnBackground)
                    TextButton(onClick = {
                        if (newPostText.isNotBlank()) {
                            vm.createPost(newPostText.trim())
                            newPostText  = ""
                            showNewPost  = false
                        }
                    }) {
                        Text("Paylaş", color = Amber, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value           = newPostText,
                    onValueChange   = { newPostText = it },
                    placeholder     = { Text("Ne düşünüyorsun?", color = Muted) },
                    modifier        = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                    colors          = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor    = Amber,
                        unfocusedBorderColor  = Divider,
                        focusedTextColor      = OnBackground,
                        unfocusedTextColor    = OnBackground,
                        unfocusedContainerColor = Surface,
                        focusedContainerColor   = Surface,
                    ),
                    shape = RoundedCornerShape(12.dp),
                )
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    commentPost?.let { post ->
        CommentSheet(post = post, onDismiss = { commentPost = null }, vm = vm)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentSheet(post: Post, onDismiss: () -> Unit, vm: FeedViewModel) {
    val comments by vm.comments.collectAsState()
    var commentText by remember { mutableStateOf("") }
    LaunchedEffect(post.id) { vm.loadComments(post.id) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor   = Surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.75f)
                .padding(horizontal = 16.dp)
                .navigationBarsPadding(),
        ) {
            Text("Yorumlar", fontWeight = FontWeight.SemiBold, color = OnBackground, modifier = Modifier.padding(vertical = 8.dp))
            HorizontalDivider(color = Divider)
            LazyColumn(
                modifier       = Modifier.weight(1f),
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(comments, key = { it.id }) { cmt ->
                    Row(verticalAlignment = Alignment.Top) {
                        AsyncImage(
                            model              = cmt.photoURL.ifEmpty { null },
                            contentDescription = null,
                            modifier           = Modifier.size(32.dp).clip(CircleShape).background(SurfaceVar),
                            contentScale       = ContentScale.Crop,
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
            Row(
                modifier          = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value         = commentText,
                    onValueChange = { commentText = it },
                    placeholder   = { Text("Yorum yaz...", color = Muted) },
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
                    onClick  = {
                        if (commentText.isNotBlank()) {
                            vm.addComment(post, commentText.trim())
                            commentText = ""
                        }
                    },
                    modifier = Modifier.size(40.dp).clip(CircleShape).background(Amber),
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Gönder", tint = Color.Black, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
fun PostCard(
    post      : Post,
    onLike    : () -> Unit,
    onSave    : () -> Unit,
    onProfile : () -> Unit,
    onComment : () -> Unit,
    onShare   : () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Background)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        // Header: avatar + name + username
        Row(
            modifier          = Modifier.fillMaxWidth().clickable { onProfile() },
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
                // DÜZELTME: İsim varsa ismi, yoksa kullanıcı adını, o da yoksa "Heft Reng Kullanıcısı" yazdırıyoruz.
                val nameToDisplay = when {
                    post.displayName.isNotBlank() -> post.displayName
                    post.username.isNotBlank()    -> post.username
                    else                          -> "Bikarhênerê Heftreng" 
                }

                Text(
                    text       = nameToDisplay,
                    fontWeight = FontWeight.SemiBold,
                    color      = OnBackground,
                    fontSize   = 14.sp,
                )
                
                // Kullanıcı adı varsa ve görünen isimden farklıysa @handle olarak göster
                if (post.username.isNotBlank() && post.username != nameToDisplay) {
                    Text(
                        text     = "@${post.username}",
                        color    = Muted,
                        fontSize = 12.sp,
                    )
                } else if (post.username.isBlank() && post.displayName.isBlank()) {
                    // Her ikisi de boşsa en azından bir ayraç koy
                    Text("—", color = Muted, fontSize = 12.sp)
                }
            }
        }


        Spacer(Modifier.height(10.dp))

        // Quote block
        if (post.quoteText.isNotBlank()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(10.dp),
                color    = SurfaceVar,
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text("\"${post.quoteText}\"", color = OnSurface, fontSize = 14.sp, lineHeight = 20.sp)
                    if (post.bookName.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text("— ${post.authorName}, ${post.bookName}", color = Muted, fontSize = 12.sp)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        // Post text
        if (post.text.isNotBlank()) {
            Text(post.text, color = OnBackground, fontSize = 15.sp, lineHeight = 22.sp)
            Spacer(Modifier.height(8.dp))
        }

        // Image
        if (post.imageURL.isNotBlank()) {
            AsyncImage(
                model              = post.imageURL,
                contentDescription = null,
                modifier           = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 300.dp)
                    .clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop,
            )
            Spacer(Modifier.height(8.dp))
        }

        // Actions row
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            // Like
            IconButton(onClick = onLike) {
                Icon(
                    if (post.isLikedByMe) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = "Beğen",
                    tint               = if (post.isLikedByMe) Color(0xFFEF4444) else Muted,
                    modifier           = Modifier.size(20.dp),
                )
            }
            if (post.likesCount > 0)
                Text(post.likesCount.toString(), color = Muted, fontSize = 13.sp)

            Spacer(Modifier.width(4.dp))

            // Comment
            IconButton(onClick = onComment) {
                Icon(Icons.Outlined.ChatBubbleOutline, contentDescription = "Yorum", tint = Muted, modifier = Modifier.size(20.dp))
            }
            if (post.commentsCount > 0)
                Text(post.commentsCount.toString(), color = Muted, fontSize = 13.sp)

            Spacer(Modifier.width(4.dp))

            // Repost
            IconButton(onClick = onShare) {
                Icon(Icons.Default.Repeat, contentDescription = "Paylaş", tint = Muted, modifier = Modifier.size(20.dp))
            }
            if (post.repostsCount > 0)
                Text(post.repostsCount.toString(), color = Muted, fontSize = 13.sp)

            Spacer(Modifier.weight(1f))

            // Save
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
}
