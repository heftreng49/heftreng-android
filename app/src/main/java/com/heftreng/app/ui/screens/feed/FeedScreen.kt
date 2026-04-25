package com.heftreng.app.ui.screens.feed

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.draw.shadow
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.heftreng.app.data.model.Post
import com.heftreng.app.navigation.Screen
import com.heftreng.app.ui.theme.*
import com.heftreng.app.viewmodel.FeedViewModel
import java.text.SimpleDateFormat
import java.util.*

// ── Feed Ekrani ───────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    navController: NavController,
    onOpenDrawer : () -> Unit = {},
    vm           : FeedViewModel = hiltViewModel(),
) {
    val posts       by vm.posts.collectAsState()
    val loading     by vm.loading.collectAsState()
    var showNewPost by remember { mutableStateOf(false) }
    var commentPost by remember { mutableStateOf<Post?>(null) }
    val listState   = rememberLazyListState()

    // FAB sadece yukari kaydirildiginda gizlenir
    val showFab by remember {
        derivedStateOf { listState.firstVisibleItemIndex == 0 || !listState.isScrollInProgress }
    }

    Scaffold(
        containerColor = Background,
        topBar = { FeedTopBar(onOpenDrawer) },
        floatingActionButton = {
            AnimatedVisibility(
                visible = showFab,
                enter   = scaleIn() + fadeIn(),
                exit    = scaleOut() + fadeOut(),
            ) {
                ExtendedFloatingActionButton(
                    onClick        = { showNewPost = true },
                    containerColor = Amber,
                    contentColor   = Color.Black,
                    shape          = RoundedCornerShape(16.dp),
                    icon           = { Icon(Icons.Default.Edit, contentDescription = null) },
                    text           = { Text("Bine\u0301vse", fontWeight = FontWeight.Bold) },
                    modifier       = Modifier.shadow(8.dp, RoundedCornerShape(16.dp)),
                )
            }
        }
    ) { padding ->
        when {
            loading && posts.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Amber, strokeWidth = 3.dp)
                        Spacer(Modifier.height(12.dp))
                        Text("Barkirin...", color = Muted, fontSize = 13.sp)
                    }
                }
            }
            posts.isEmpty() -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Outlined.DynamicFeed, null, tint = Muted, modifier = Modifier.size(56.dp))
                        Spacer(Modifier.height(14.dp))
                        Text("Henuz gonderi yok", color = OnBackground, fontWeight = FontWeight.SemiBold)
                        Text("Ilk gonderiyi sen paylasabilirsin!", color = Muted, fontSize = 13.sp)
                    }
                }
            }
            else -> {
                LazyColumn(
                    state          = listState,
                    modifier       = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(bottom = 100.dp),
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
    }

    // Yeni gonderi modal
    if (showNewPost) {
        NewPostSheet(onDismiss = { showNewPost = false }, vm = vm)
    }

    // Yorum modal
    commentPost?.let { post ->
        CommentSheet(post = post, onDismiss = { commentPost = null }, vm = vm)
    }
}

// ── Header ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedTopBar(onOpenDrawer: () -> Unit) {
    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Heftreng logosu - gradient text efekti
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(
                            brush      = Brush.horizontalGradient(listOf(Amber, Color(0xFFFF6B00))),
                            fontWeight = FontWeight.Black,
                            fontSize   = 24.sp,
                        )) { append("heft") }
                        withStyle(SpanStyle(
                            color      = OnBackground,
                            fontWeight = FontWeight.Black,
                            fontSize   = 24.sp,
                        )) { append("reng") }
                    }
                )
            }
        },
        navigationIcon = {
            IconButton(onClick = onOpenDrawer) {
                Icon(Icons.Default.Menu, contentDescription = "Menu", tint = OnBackground)
            }
        },
        actions = {
            IconButton(onClick = {}) {
                Icon(Icons.Outlined.Search, contentDescription = "Ara", tint = OnBackground)
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Background),
    )
}

// ── Post Karti ────────────────────────────────────────────────────────────────

@Composable
fun PostCard(
    post     : Post,
    onLike   : () -> Unit,
    onSave   : () -> Unit,
    onProfile: () -> Unit,
    onComment: () -> Unit,
    onShare  : () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Background)
            .padding(top = 14.dp),
    ) {
        // ── Avatar + isim satiri ─────────────────────────────────────────
        Row(
            modifier          = Modifier
                .fillMaxWidth()
                .clickable { onProfile() }
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Avatar
            Box {
                AsyncImage(
                    model              = post.photoURL.ifEmpty { null },
                    contentDescription = post.displayName,
                    modifier           = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(SurfaceVar),
                    contentScale = ContentScale.Crop,
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    post.displayName.ifBlank { "Benas" },
                    fontWeight = FontWeight.Bold,
                    color      = OnBackground,
                    fontSize   = 14.sp,
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (post.username.isNotBlank()) {
                        Text("@${post.username}", color = Muted, fontSize = 12.sp)
                        Text("*", color = Muted, fontSize = 8.sp)
                    }
                    // Zaman
                    val timeStr = remember(post.ts) {
                        post.ts?.toDate()?.let {
                            val diff = (System.currentTimeMillis() - it.time) / 1000
                            when {
                                diff < 60    -> "${diff}s"
                                diff < 3600  -> "${diff/60}d"
                                diff < 86400 -> "${diff/3600}sa"
                                else -> SimpleDateFormat("dd MMM", Locale.getDefault()).format(it)
                            }
                        } ?: ""
                    }
                    if (timeStr.isNotEmpty()) Text(timeStr, color = Muted, fontSize = 11.sp)
                }
            }
            IconButton(onClick = {}, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.MoreHoriz, contentDescription = null, tint = Muted, modifier = Modifier.size(18.dp))
            }
        }

        Spacer(Modifier.height(10.dp))

        // ── Alinti blogu ─────────────────────────────────────────────────
        if (post.quoteText.isNotBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(SurfaceVar),
            ) {
                // Sol renkli cizgi
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .fillMaxHeight()
                        .background(
                            Brush.verticalGradient(listOf(Amber, Color(0xFFFF6B00))),
                            RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp),
                        )
                        .align(Alignment.CenterStart)
                )
                Column(modifier = Modifier.padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp)) {
                    // Tirnak ikonu
                    Icon(
                        Icons.Default.FormatQuote,
                        contentDescription = null,
                        tint               = Amber.copy(alpha = 0.6f),
                        modifier           = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        post.quoteText,
                        color      = OnSurface,
                        fontSize   = 14.sp,
                        lineHeight = 21.sp,
                        fontStyle  = FontStyle.Italic,
                    )
                    if (post.authorName.isNotBlank() || post.bookName.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        HorizontalDivider(color = Divider.copy(alpha = 0.5f), thickness = 0.5.dp)
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Default.AutoStories, null, tint = Amber, modifier = Modifier.size(12.dp))
                            Text(
                                buildString {
                                    if (post.authorName.isNotBlank()) append(post.authorName)
                                    if (post.authorName.isNotBlank() && post.bookName.isNotBlank()) append(" — ")
                                    if (post.bookName.isNotBlank()) append(post.bookName)
                                },
                                color      = Amber,
                                fontSize   = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        // ── Post metni ───────────────────────────────────────────────────
        if (post.text.isNotBlank()) {
            Text(
                post.text,
                color    = OnBackground,
                fontSize = 15.sp,
                lineHeight = 23.sp,
                modifier = Modifier.padding(horizontal = 14.dp),
            )
            Spacer(Modifier.height(10.dp))
        }

        // ── Gorsel - tam genislik ─────────────────────────────────────────
        if (post.imageURL.isNotBlank()) {
            AsyncImage(
                model              = post.imageURL,
                contentDescription = null,
                modifier           = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 180.dp, max = 380.dp),
                contentScale = ContentScale.Crop,
            )
            Spacer(Modifier.height(10.dp))
        }

        // ── Aksiyon satiri ───────────────────────────────────────────────
        Row(
            modifier          = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Begeni
            ActionBtn(
                icon      = if (post.isLikedByMe) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                count     = post.likesCount,
                tint      = if (post.isLikedByMe) Color(0xFFEF4444) else Muted,
                onClick   = onLike,
            )

            // Yorum
            ActionBtn(
                icon    = Icons.Outlined.ChatBubbleOutline,
                count   = post.commentsCount,
                tint    = Muted,
                onClick = onComment,
            )

            // Repost
            ActionBtn(
                icon    = Icons.Default.Repeat,
                count   = post.repostsCount,
                tint    = Muted,
                onClick = onShare,
            )

            Spacer(Modifier.weight(1f))

            // Kaydet
            IconButton(onClick = onSave, modifier = Modifier.size(36.dp)) {
                Icon(
                    if (post.isSavedByMe) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                    contentDescription = "Kaydet",
                    tint               = if (post.isSavedByMe) Amber else Muted,
                    modifier           = Modifier.size(20.dp),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
fun ActionBtn(
    icon   : androidx.compose.ui.graphics.vector.ImageVector,
    count  : Int,
    tint   : Color,
    onClick: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onClick, modifier = Modifier.size(36.dp)) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        }
        if (count > 0) {
            Text(
                if (count >= 1000) "${count/1000}B" else count.toString(),
                color    = Muted,
                fontSize = 12.sp,
                modifier = Modifier.padding(end = 8.dp),
            )
        }
    }
}

// ── Yeni Gonderi Modal ────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewPostSheet(onDismiss: () -> Unit, vm: FeedViewModel) {
    var text         by remember { mutableStateOf("") }
    var showQuote    by remember { mutableStateOf(false) }
    var quoteText    by remember { mutableStateOf("") }
    var authorName   by remember { mutableStateOf("") }
    var bookName     by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor   = Surface,
        dragHandle       = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .background(Divider, RoundedCornerShape(2.dp)),
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp),
        ) {
            // Baslik satiri
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Berde / Iptal", color = Muted, fontSize = 13.sp)
                }
                Text("Nivisa Nwe", fontWeight = FontWeight.Bold, color = OnBackground)
                Button(
                    onClick  = {
                        if (text.isNotBlank() || quoteText.isNotBlank()) {
                            vm.createPost(
                                text       = text.trim(),
                                quoteText  = quoteText.trim(),
                                authorName = authorName.trim(),
                                bookName   = bookName.trim(),
                            )
                            onDismiss()
                        }
                    },
                    enabled  = text.isNotBlank() || quoteText.isNotBlank(),
                    shape    = RoundedCornerShape(10.dp),
                    colors   = ButtonDefaults.buttonColors(
                        containerColor         = Amber,
                        contentColor           = Color.Black,
                        disabledContainerColor = SurfaceVar,
                        disabledContentColor   = Muted,
                    ),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Text("Biweyne / Paylas", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }

            Spacer(Modifier.height(12.dp))

            // Metin alani
            OutlinedTextField(
                value           = text,
                onValueChange   = { text = it },
                placeholder     = { Text("Ne dusunuyorsun? / Tu cawa difikiri?", color = Muted, fontSize = 14.sp) },
                modifier        = Modifier.fillMaxWidth().heightIn(min = 100.dp),
                colors          = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor      = Amber,
                    unfocusedBorderColor    = Color.Transparent,
                    focusedTextColor        = OnBackground,
                    unfocusedTextColor      = OnBackground,
                    unfocusedContainerColor = SurfaceVar,
                    focusedContainerColor   = SurfaceVar,
                ),
                shape            = RoundedCornerShape(12.dp),
                keyboardOptions  = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )

            Spacer(Modifier.height(8.dp))

            // Alinti ekle butonu
            TextButton(
                onClick = { showQuote = !showQuote },
                contentPadding = PaddingValues(0.dp),
            ) {
                Icon(
                    if (showQuote) Icons.Default.RemoveCircleOutline else Icons.Default.FormatQuote,
                    contentDescription = null,
                    tint     = Amber,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    if (showQuote) "Alinti kaldir" else "Alinti ekle",
                    color    = Amber,
                    fontSize = 13.sp,
                )
            }

            // Alinti alanlari
            AnimatedVisibility(visible = showQuote) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value         = quoteText,
                        onValueChange = { quoteText = it },
                        placeholder   = { Text("Alinti metni...", color = Muted) },
                        modifier      = Modifier.fillMaxWidth().heightIn(min = 80.dp),
                        shape         = RoundedCornerShape(12.dp),
                        colors        = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor      = Amber,
                            unfocusedBorderColor    = Divider,
                            focusedTextColor        = OnBackground,
                            unfocusedTextColor      = OnBackground,
                            unfocusedContainerColor = SurfaceVar,
                            focusedContainerColor   = SurfaceVar,
                        ),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value         = authorName,
                            onValueChange = { authorName = it },
                            placeholder   = { Text("Yazar", color = Muted) },
                            modifier      = Modifier.weight(1f),
                            shape         = RoundedCornerShape(12.dp),
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
                        OutlinedTextField(
                            value         = bookName,
                            onValueChange = { bookName = it },
                            placeholder   = { Text("Kitap", color = Muted) },
                            modifier      = Modifier.weight(1f),
                            shape         = RoundedCornerShape(12.dp),
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
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

// ── Yorum Modal ───────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentSheet(post: Post, onDismiss: () -> Unit, vm: FeedViewModel) {
    val comments by vm.comments.collectAsState()
    var commentText by remember { mutableStateOf("") }
    LaunchedEffect(post.id) { vm.loadComments(post.id) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor   = Surface,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .background(Divider, RoundedCornerShape(2.dp)),
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.8f)
                .navigationBarsPadding(),
        ) {
            // Baslik
            Row(
                modifier          = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("${post.commentsCount} Bersiv", fontWeight = FontWeight.Bold, color = OnBackground)
                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, null, tint = Muted, modifier = Modifier.size(18.dp))
                }
            }
            HorizontalDivider(color = Divider)

            // Yorumlar listesi
            LazyColumn(
                modifier            = Modifier.weight(1f),
                contentPadding      = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (comments.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                            Text("Henuz yorum yok", color = Muted)
                        }
                    }
                }
                items(comments, key = { it.id }) { cmt ->
                    Row(verticalAlignment = Alignment.Top) {
                        AsyncImage(
                            model              = cmt.photoURL.ifEmpty { null },
                            contentDescription = null,
                            modifier           = Modifier.size(34.dp).clip(CircleShape).background(SurfaceVar),
                            contentScale       = ContentScale.Crop,
                        )
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(cmt.displayName, fontWeight = FontWeight.SemiBold, color = OnBackground, fontSize = 13.sp)
                                Text("*", color = Muted, fontSize = 8.sp)
                                Text("az once", color = Muted, fontSize = 11.sp)
                            }
                            Spacer(Modifier.height(2.dp))
                            Surface(shape = RoundedCornerShape(topStart = 2.dp, topEnd = 12.dp, bottomStart = 12.dp, bottomEnd = 12.dp), color = SurfaceVar) {
                                Text(cmt.text, color = OnBackground, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp))
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = Divider)

            // Giri satiri
            Row(
                modifier          = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value         = commentText,
                    onValueChange = { commentText = it },
                    placeholder   = { Text("Bersiva xwe binivise...", color = Muted, fontSize = 13.sp) },
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
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(if (commentText.isNotBlank()) Amber else SurfaceVar),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Gonder",
                        tint               = if (commentText.isNotBlank()) Color.Black else Muted,
                        modifier           = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}
