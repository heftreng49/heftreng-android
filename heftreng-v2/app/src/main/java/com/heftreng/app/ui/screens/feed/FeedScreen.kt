package com.heftreng.app.ui.screens.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.heftreng.app.navigation.Screen
import com.heftreng.app.ui.component.PostCard
import com.heftreng.app.ui.screens.auth.heftrangFieldColors
import com.heftreng.app.ui.theme.*
import com.heftreng.app.viewmodel.FeedViewModel

// ════════════════════════════════════════════════════════════
//  FEED SCREEN
// ════════════════════════════════════════════════════════════
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
        containerColor = bg(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "heftreng",
                        fontWeight    = FontWeight.Bold,
                        color         = accent(),
                        fontSize      = 22.sp,
                        letterSpacing = (-0.5).sp,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Default.Menu, "Menû", tint = onBg())
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = bg()),
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick        = { showNewPost = true },
                containerColor = accent(),
                contentColor   = Color.Black,
                shape          = RoundedCornerShape(16.dp),
            ) { Icon(Icons.Default.Add, "Nivîsa nû") }
        }
    ) { padding ->
        when {
            loading && posts.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = accent())
                }
            }
            posts.isEmpty() -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Outlined.DynamicFeed, null, tint = muted(), modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(12.dp))
                        Text("Nivîs tune / Henüz gönderi yok", color = muted())
                    }
                }
            }
            else -> {
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
                        )
                        HorizontalDivider(color = divider(), thickness = 0.5.dp)
                    }
                }
            }
        }
    }

    if (showNewPost) {
        NewPostSheet(
            onDismiss = { showNewPost = false },
            onSubmit  = { text, quote, book, author ->
                vm.createPost(text, quote, book, author)
                showNewPost = false
            },
        )
    }
}

// ── Yeni Gönderi Sheet ────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewPostSheet(
    onDismiss: () -> Unit,
    onSubmit : (text: String, quote: String, book: String, author: String) -> Unit,
) {
    var text       by remember { mutableStateOf("") }
    var quoteText  by remember { mutableStateOf("") }
    var bookName   by remember { mutableStateOf("") }
    var authorName by remember { mutableStateOf("") }
    var showQuote  by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = surf()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp).navigationBarsPadding(),
        ) {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismiss) { Text("Betal bike", color = muted()) }
                Text("Nivîsa Nû", fontWeight = FontWeight.SemiBold, color = onBg())
                TextButton(onClick = {
                    if (text.isNotBlank() || quoteText.isNotBlank())
                        onSubmit(text.trim(), quoteText.trim(), bookName.trim(), authorName.trim())
                }) {
                    Text("Belav bike", color = accent(), fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = text, onValueChange = { text = it },
                placeholder = { Text("Tu çi difikiri?", color = muted()) },
                modifier    = Modifier.fillMaxWidth().heightIn(min = 100.dp),
                colors      = heftrangFieldColors(),
                shape       = RoundedCornerShape(12.dp),
            )

            Spacer(Modifier.height(8.dp))

            TextButton(onClick = { showQuote = !showQuote }) {
                Text(
                    if (showQuote) "− Alintiyê rake" else "+ Alintî zêde bike",
                    color = accent(), fontSize = 13.sp,
                )
            }

            if (showQuote) {
                OutlinedTextField(
                    value = quoteText, onValueChange = { quoteText = it },
                    placeholder = { Text("Alintiya xwe binivîse...", color = muted()) },
                    modifier    = Modifier.fillMaxWidth().heightIn(min = 80.dp),
                    colors      = heftrangFieldColors(),
                    shape       = RoundedCornerShape(12.dp),
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = authorName, onValueChange = { authorName = it },
                        placeholder = { Text("Nivîskar", color = muted()) },
                        modifier    = Modifier.weight(1f),
                        colors      = heftrangFieldColors(),
                        shape       = RoundedCornerShape(12.dp),
                        singleLine  = true,
                    )
                    OutlinedTextField(
                        value = bookName, onValueChange = { bookName = it },
                        placeholder = { Text("Kitêb", color = muted()) },
                        modifier    = Modifier.weight(1f),
                        colors      = heftrangFieldColors(),
                        shape       = RoundedCornerShape(12.dp),
                        singleLine  = true,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

// ════════════════════════════════════════════════════════════
//  POST DETAIL SCREEN
// ════════════════════════════════════════════════════════════
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostDetailScreen(
    navController: NavController,
    viewModel    : FeedViewModel,
    postId       : String,
) {
    val posts    by viewModel.posts.collectAsState()
    val comments by viewModel.comments.collectAsState()
    val post     = posts.find { it.id == postId }
    var commentText by remember { mutableStateOf("") }

    LaunchedEffect(postId) { viewModel.loadComments(postId) }

    Scaffold(
        containerColor = bg(),
        topBar = {
            TopAppBar(
                title          = { Text("Mijar", color = onBg(), fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Vegere", tint = onBg())
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = bg()),
            )
        },
    ) { padding ->
        if (post == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = accent())
            }
            return@Scaffold
        }

        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                modifier       = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 8.dp),
            ) {
                item {
                    PostCard(
                        post       = post,
                        onLike     = { viewModel.toggleLike(post) },
                        onSave     = { viewModel.toggleSave(post) },
                        onProfile  = { navController.navigate(Screen.Profile.go(post.uid)) },
                        onComment  = {},
                        onShare    = { viewModel.repost(post) },
                        onCardClick= {},
                    )
                    HorizontalDivider(color = surfVar(), thickness = 6.dp)
                }

                if (post.likesCount > 0) {
                    item {
                        Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                            Text(
                                "${post.likesCount}",
                                fontWeight = FontWeight.Bold, color = onBg(), fontSize = 14.sp,
                            )
                            Text(" Hezkirî / Beğeni", color = muted(), fontSize = 14.sp)
                        }
                        HorizontalDivider(color = divider())
                    }
                }

                item {
                    Text(
                        "Şîrove (${comments.size})",
                        color    = muted(),
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    )
                }

                if (comments.isEmpty()) {
                    item {
                        Box(
                            modifier         = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Outlined.ChatBubbleOutline, null, tint = muted(), modifier = Modifier.size(36.dp))
                                Spacer(Modifier.height(8.dp))
                                Text("Şîrove tune / Henüz yorum yok", color = muted(), fontSize = 13.sp)
                            }
                        }
                    }
                } else {
                    items(comments, key = { it.id }) { cmt ->
                        Row(
                            modifier          = Modifier
                                .fillMaxWidth()
                                .background(bg())
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Box(
                                modifier         = Modifier.size(34.dp).clip(CircleShape).background(surfVar()),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (cmt.photoURL.isNotBlank()) {
                                    AsyncImage(
                                        model = cmt.photoURL, contentDescription = null,
                                        modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
                                    )
                                } else {
                                    Text(
                                        cmt.displayName.firstOrNull()?.uppercase() ?: "H",
                                        color = accent(), fontWeight = FontWeight.Bold, fontSize = 13.sp,
                                    )
                                }
                            }
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(
                                    cmt.displayName.ifBlank { "Bikarhêner" },
                                    fontWeight = FontWeight.SemiBold, color = onBg(), fontSize = 13.sp,
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    cmt.text,
                                    color      = onSurf(),
                                    fontSize   = AppFontSize.sp,
                                    lineHeight = (AppFontSize + 6).sp,
                                )
                            }
                        }
                        HorizontalDivider(color = divider(), thickness = 0.5.dp)
                    }
                }
            }

            // Yorum yazma alanı
            HorizontalDivider(color = divider())
            Row(
                modifier          = Modifier
                    .fillMaxWidth()
                    .background(surf())
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .navigationBarsPadding(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value         = commentText,
                    onValueChange = { commentText = it },
                    placeholder   = { Text("Şîroveya xwe binivîse...", color = muted()) },
                    modifier      = Modifier.weight(1f),
                    shape         = RoundedCornerShape(24.dp),
                    singleLine    = true,
                    colors        = heftrangFieldColors(),
                )
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick  = {
                        if (commentText.isNotBlank()) {
                            viewModel.addComment(post, commentText.trim())
                            commentText = ""
                        }
                    },
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(if (commentText.isNotBlank()) accent() else surfVar()),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send, "Bişîne",
                        tint     = if (commentText.isNotBlank()) Color.Black else muted(),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}
