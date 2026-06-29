package com.heftreng.app.ui.screens.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.heftreng.app.navigation.Screen
import com.heftreng.app.ui.screens.social.LikerListSheet
import com.heftreng.app.ui.i18n.Strings
import com.heftreng.app.ui.theme.*
import com.heftreng.app.viewmodel.FeedViewModel
import com.heftreng.app.viewmodel.SocialViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.net.URLEncoder

private data class DetailComment(
    val id           : String     = "",
    val uid          : String     = "",
    val name         : String     = "",
    val photoURL     : String     = "",
    val text         : String     = "",
    val replyTo      : String     = "",
    val replyToCmtId : String     = "",
    val likes        : Int        = 0,
    val edited       : Boolean    = false,
    val ts           : Timestamp? = null,
)

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun PostDetailScreen(
    navController   : NavController,
    viewModel       : FeedViewModel,
    postId          : String,
    autoOpenKeyboard: Boolean = false,
    socialVm        : SocialViewModel = hiltViewModel(),
    language        : String = "tr",
) {
    val ku = language == "ku"
    val posts         by viewModel.posts.collectAsState()
    val likers        by socialVm.likers.collectAsState()
    val socialLoading by socialVm.loading.collectAsState()
    val postNotFound  by viewModel.postNotFound.collectAsState()
    val post          = posts.find { it.id == postId }

    val db    = FirebaseFirestore.getInstance()
    val auth  = FirebaseAuth.getInstance()
    val scope = rememberCoroutineScope()

    var myUid   by remember { mutableStateOf(auth.currentUser?.uid ?: "") }
    var myName  by remember { mutableStateOf(auth.currentUser?.displayName ?: "") }
    var myPhoto by remember { mutableStateOf(auth.currentUser?.photoUrl?.toString() ?: "") }
    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { a ->
            myUid   = a.currentUser?.uid ?: ""
            myName  = a.currentUser?.displayName ?: ""
            myPhoto = a.currentUser?.photoUrl?.toString() ?: ""
        }
        auth.addAuthStateListener(listener)
        onDispose { auth.removeAuthStateListener(listener) }
    }

    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester     = remember { FocusRequester() }
    var openKeyboard       by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (autoOpenKeyboard) {
            kotlinx.coroutines.delay(400)
            openKeyboard = true
        }
    }
    LaunchedEffect(openKeyboard) {
        if (openKeyboard) {
            focusRequester.requestFocus()
            keyboardController?.show()
            openKeyboard = false
        }
    }

    var comments     by remember { mutableStateOf<List<DetailComment>>(emptyList()) }
    var cmtLoading   by remember { mutableStateOf(true) }
    var inputText    by remember { mutableStateOf("") }
    var replyTo      by remember { mutableStateOf<DetailComment?>(null) }
    var editTarget   by remember { mutableStateOf<DetailComment?>(null) }
    var deleteTarget by remember { mutableStateOf<DetailComment?>(null) }
    var menuTarget   by remember { mutableStateOf<DetailComment?>(null) }
    var errorMsg     by remember { mutableStateOf("") }
    var showLikers   by remember { mutableStateOf(false) }
    val listState    = rememberLazyListState()

    // Düzenleme moduna girilince inputText'i doldur ve klavyeyi aç
    LaunchedEffect(editTarget) {
        editTarget?.let {
            inputText = it.text
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    var myFirestoreName  by remember { mutableStateOf("") }
    var myFirestorePhoto by remember { mutableStateOf("") }
    LaunchedEffect(myUid) {
        if (myUid.isBlank()) return@LaunchedEffect
        try {
            val doc = db.collection("users").document(myUid).get().await()
            myFirestoreName  = doc.getString("displayName") ?: doc.getString("name") ?: ""
            myFirestorePhoto = doc.getString("photoURL") ?: myPhoto
        } catch (_: Exception) {}
    }

    DisposableEffect(postId) {
        var reg: ListenerRegistration? = null
        reg = db.collection("feed").document(postId)
            .collection("comments")
            .orderBy("ts", Query.Direction.ASCENDING)
            .addSnapshotListener { snap, err ->
                if (err != null || snap == null) { cmtLoading = false; return@addSnapshotListener }
                comments = snap.documents.mapNotNull { doc ->
                    val d = doc.data ?: return@mapNotNull null
                    val cmtUid = (d["uid"] as? String)?.takeIf { it.isNotBlank() }
                        ?: (d["userId"] as? String)?.takeIf { it.isNotBlank() }
                        ?: (d["authorId"] as? String)?.takeIf { it.isNotBlank() }
                        ?: ""
                    DetailComment(
                        id           = doc.id,
                        uid          = cmtUid,
                        name         = (d["displayName"] as? String)?.ifBlank { null }
                                       ?: d["name"] as? String ?: "?",
                        photoURL     = d["photoURL"]     as? String ?: "",
                        text         = d["text"]         as? String ?: "",
                        replyTo      = d["replyTo"]      as? String ?: "",
                        replyToCmtId = d["replyToCmtId"] as? String ?: "",
                        likes        = (d["likes"]       as? Long)?.toInt() ?: 0,
                        edited       = d["edited"]       as? Boolean ?: false,
                        ts           = d["ts"]           as? Timestamp,
                    )
                }
                cmtLoading = false
            }
        onDispose { reg?.remove() }
    }

    fun submitComment() {
        val text = inputText.trim()
        if (text.isBlank() || myUid.isBlank()) return

        val editing = editTarget
        inputText  = ""
        editTarget = null

        if (editing != null) {
            scope.launch {
                try {
                    db.collection("feed").document(postId)
                        .collection("comments").document(editing.id)
                        .update(mapOf("text" to text, "edited" to true))
                        .await()
                } catch (e: Exception) { errorMsg = e.message ?: "Hata" }
            }
            return
        }

        val replyRef = replyTo
        replyTo = null
        scope.launch {
            try {
                val name  = myFirestoreName.ifBlank { myName.ifBlank { auth.currentUser?.displayName ?: "?" } }
                val photo = myFirestorePhoto.ifBlank { myPhoto }
                db.collection("feed").document(postId).collection("comments").add(
                    mapOf(
                        "uid"          to myUid,
                        "displayName"  to name,
                        "name"         to name,
                        "photoURL"     to photo,
                        "text"         to text,
                        "replyTo"      to (replyRef?.name ?: ""),
                        "replyToCmtId" to (replyRef?.id   ?: ""),
                        "likes"        to 0,
                        "edited"       to false,
                        "ts"           to Timestamp.now(),
                    )
                ).await()
                db.collection("feed").document(postId)
                    .update("cmtCount", FieldValue.increment(1)).await()
            } catch (e: Exception) {
                errorMsg = e.message ?: "Hata"
            }
        }
    }

    fun deleteComment(cmt: DetailComment) {
        scope.launch {
            try {
                db.collection("feed").document(postId)
                    .collection("comments").document(cmt.id).delete().await()
                db.collection("feed").document(postId)
                    .update("cmtCount", FieldValue.increment(-1)).await()
            } catch (e: Exception) {
                errorMsg = "Silinemedi: ${e.message}"
            }
        }
    }

    var postAuthorUid by remember { mutableStateOf("") }
    LaunchedEffect(postId, post?.uid) {
        postAuthorUid = post?.uid?.takeIf { it.isNotBlank() } ?: try {
            db.collection("feed").document(postId).get().await().getString("uid") ?: ""
        } catch (_: Exception) { "" }
    }

    LaunchedEffect(postId) {
        viewModel.ensurePost(postId)
    }

    val loadFailed = postNotFound == postId

    // ── Scaffold: imePadding Scaffold dışında — en dış Box'ta ─────────────────
    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            contentWindowInsets = WindowInsets(0),
            containerColor = Background,
            topBar = {
                TopAppBar(
                    title = { Text(Strings.post(language), color = OnBackground, fontSize = 17.sp, fontWeight = FontWeight.SemiBold) },
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
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    if (loadFailed) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("😕", fontSize = 40.sp)
                            Text(
                                if (ku) "Nivîs nehate dîtin" else "Gönderi bulunamadı",
                                color = Muted, fontSize = 15.sp,
                            )
                            TextButton(onClick = { navController.popBackStack() }) {
                                Text(if (ku) "Vegere" else "Geri dön", color = Amber)
                            }
                        }
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            CircularProgressIndicator(color = Amber, modifier = Modifier.size(32.dp))
                            Text(
                                if (ku) "Bar dike..." else "Yükleniyor...",
                                color = Muted, fontSize = 13.sp,
                            )
                        }
                    }
                }
                return@Scaffold
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                // ── Yorumlar listesi ──────────────────────────────────────────
                LazyColumn(
                    state          = listState,
                    modifier       = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 8.dp),
                ) {
                    item {
                        PostCard(
                            post         = post,
                            onLike       = { viewModel.toggleLike(post) },
                            onSave       = { viewModel.toggleSave(post) },
                            onProfile    = { navController.navigate(Screen.Profile.go(post.uid)) },
                            onComment    = { openKeyboard = true },
                            onShare      = { viewModel.repost(post) },
                            onShowLikers = { socialVm.loadPostLikers(post.id); showLikers = true },
                            onTapBook    = { _ ->
                                if (post.libraryBookId.isNotBlank())
                                    navController.navigate("library_book_detail/${post.libraryBookId}")
                                else if (post.bookName.isNotBlank())
                                    navController.navigate("book_quotes/${URLEncoder.encode(post.bookName, "UTF-8")}")
                            },
                            onTapAuthor  = { _ ->
                                if (post.libraryAuthorId.isNotBlank())
                                    navController.navigate("author_detail/${post.libraryAuthorId}")
                                else if (post.authorName.isNotBlank())
                                    navController.navigate("author_quotes/${URLEncoder.encode(post.authorName, "UTF-8")}")
                            },
                            onTapRepost  = { repostId, repostType ->
                                when (repostType) {
                                    "feed"    -> navController.navigate(Screen.PostDetail.go(repostId))
                                    "serial"  -> navController.navigate("serial/$repostId")
                                    "chapter" -> navController.navigate("chapter/$repostId")
                                    "blog"    -> navController.navigate("blog/$repostId")
                                    else      -> navController.navigate(Screen.PostDetail.go(repostId))
                                }
                            },
                            language = language,
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
                                Text("${post.likesCount} ${Strings.likes(language)}", color = OnBackground, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            }
                            HorizontalDivider(color = Divider)
                        }
                    }

                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(Strings.comments(language), color = OnBackground, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            if (comments.isNotEmpty()) Text("${comments.size}", color = Muted, fontSize = 13.sp)
                        }
                        HorizontalDivider(color = Divider)
                    }

                    if (cmtLoading) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = Amber, modifier = Modifier.size(24.dp))
                            }
                        }
                    }

                    if (!cmtLoading && comments.isEmpty()) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("💬", fontSize = 32.sp)
                                    Spacer(Modifier.height(8.dp))
                                    Text(Strings.noComments(language), color = Muted, fontSize = 14.sp)
                                }
                            }
                        }
                    }

                    items(comments, key = { it.id }) { cmt ->
                        val isOwner   = myUid.isNotBlank() && cmt.uid.isNotBlank() && cmt.uid == myUid
                        val canDelete = isOwner || (myUid.isNotBlank() && myUid == postAuthorUid)
                        DetailCommentRow(
                            cmt         = cmt,
                            canEdit     = isOwner,
                            canDelete   = canDelete,
                            onLongPress = { menuTarget = cmt },
                            onDelete    = { deleteTarget = cmt },
                            onReply     = { replyTo = cmt; editTarget = null; openKeyboard = true },
                            onEdit      = { editTarget = cmt; replyTo = null },
                            language    = language,
                        )
                        HorizontalDivider(
                            color     = Divider.copy(alpha = 0.4f),
                            thickness = 0.5.dp,
                            modifier  = Modifier.padding(start = 56.dp),
                        )
                    }
                }

                // ── Düzenleme / Yanıt göstergesi ─────────────────────────────
                val indicator = editTarget ?: replyTo
                if (indicator != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(SurfaceVar)
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            if (editTarget != null) {
                                Icon(Icons.Default.Edit, null, tint = Amber, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(Strings.editCommentTitle(language), color = Amber, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            } else {
                                Icon(Icons.AutoMirrored.Filled.Reply, null, tint = Amber, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("@${replyTo!!.name} ${Strings.replyingToSuffix(language)}", color = Amber, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                        IconButton(
                            onClick  = { editTarget = null; replyTo = null; inputText = "" },
                            modifier = Modifier.size(28.dp),
                        ) {
                            Icon(Icons.Default.Close, null, tint = Muted, modifier = Modifier.size(16.dp))
                        }
                    }
                }

                // ── Giriş kutusu ─────────────────────────────────────────────
                HorizontalDivider(color = Divider)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Background)
                        .imePadding()
                        .navigationBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value         = inputText,
                        onValueChange = { inputText = it },
                        placeholder   = {
                            Text(
                                when {
                                    editTarget != null -> Strings.editCommentHint(language)
                                    replyTo    != null -> "@${replyTo!!.name} ${Strings.reply(language)}..."
                                    else               -> Strings.commentHint(language)
                                },
                                color = Muted, fontSize = 14.sp,
                            )
                        },
                        modifier = Modifier.weight(1f).focusRequester(focusRequester),
                        shape    = RoundedCornerShape(24.dp),
                        colors   = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor      = Amber,
                            unfocusedBorderColor    = Divider,
                            cursorColor             = Amber,
                            focusedTextColor        = OnBackground,
                            unfocusedTextColor      = OnBackground,
                            focusedContainerColor   = SurfaceVar,
                            unfocusedContainerColor = SurfaceVar,
                        ),
                        maxLines        = 4,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { submitComment() }),
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(
                        onClick  = { submitComment() },
                        enabled  = inputText.isNotBlank(),
                        modifier = Modifier
                            .size(44.dp)
                            .background(
                                if (inputText.isNotBlank()) Amber else Muted.copy(alpha = 0.15f),
                                CircleShape,
                            ),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = Strings.send(language),
                            tint     = if (inputText.isNotBlank()) Color.Black else Muted,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
    }

    // ── Long-press menü ───────────────────────────────────────────────────────
    menuTarget?.let { cmt ->
        val isOwner   = myUid.isNotBlank() && cmt.uid.isNotBlank() && cmt.uid == myUid
        val canDelete = isOwner || (myUid.isNotBlank() && myUid == postAuthorUid)
        AlertDialog(
            onDismissRequest = { menuTarget = null },
            containerColor   = HeftSurface,
            title = { Text(cmt.name, color = OnBackground, fontWeight = FontWeight.SemiBold, fontSize = 15.sp) },
            text  = { Text(cmt.text.take(100) + if (cmt.text.length > 100) "…" else "", color = Muted, fontSize = 13.sp) },
            confirmButton = {
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(
                        onClick  = { replyTo = cmt; editTarget = null; menuTarget = null; openKeyboard = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Reply, null, tint = Amber, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(Strings.replyAction(language), color = Amber)
                    }
                    if (isOwner) {
                        TextButton(
                            onClick  = { editTarget = cmt; replyTo = null; menuTarget = null },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Default.Edit, null, tint = OnBackground, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(Strings.editAction(language), color = OnBackground)
                        }
                    }
                    if (canDelete) {
                        TextButton(
                            onClick  = { deleteTarget = cmt; menuTarget = null },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Default.Delete, null, tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(Strings.deleteAction(language), color = Color(0xFFEF4444))
                        }
                    }
                    TextButton(onClick = { menuTarget = null }, modifier = Modifier.fillMaxWidth()) {
                        Text(Strings.cancelAction(language), color = Muted)
                    }
                }
            },
        )
    }

    // ── Silme onay ────────────────────────────────────────────────────────────
    deleteTarget?.let { cmt ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            containerColor   = HeftSurface,
            title  = { Text(Strings.deleteCommentTitle(language), color = OnBackground, fontWeight = FontWeight.SemiBold) },
            text   = { Text(cmt.text.take(80), color = Muted, fontSize = 13.sp) },
            confirmButton = {
                TextButton(onClick = { deleteComment(cmt); deleteTarget = null }) {
                    Text(Strings.deleteAction(language), color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text(Strings.cancelAction(language), color = Muted) }
            },
        )
    }

    if (errorMsg.isNotBlank()) {
        AlertDialog(
            onDismissRequest = { errorMsg = "" },
            containerColor   = HeftSurface,
            title  = { Text(Strings.error(language), color = Color(0xFFEF4444), fontWeight = FontWeight.SemiBold) },
            text   = { Text(errorMsg, color = Muted, fontSize = 13.sp) },
            confirmButton = { TextButton(onClick = { errorMsg = "" }) { Text(Strings.confirm(language), color = Amber) } },
        )
    }

    if (showLikers) {
        LikerListSheet(
            likers    = likers,
            loading   = socialLoading,
            onDismiss = { showLikers = false },
            onProfile = { uid -> showLikers = false; navController.navigate("profile/$uid") },
        )
    }
}

// ── Yorum satırı ─────────────────────────────────────────────────────────────
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun DetailCommentRow(
    cmt         : DetailComment,
    canEdit     : Boolean,
    canDelete   : Boolean,
    onLongPress : () -> Unit,
    onDelete    : () -> Unit,
    onReply     : () -> Unit,
    onEdit      : () -> Unit,
    language    : String = "tr",
) {
    val ku = language == "ku"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = {}, onLongClick = onLongPress)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier         = Modifier.size(36.dp).clip(CircleShape).background(SurfaceVar),
            contentAlignment = Alignment.Center,
        ) {
            if (cmt.photoURL.isNotBlank()) {
                AsyncImage(model = cmt.photoURL, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Text(cmt.name.firstOrNull()?.uppercase() ?: "?", color = OnBackground, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(cmt.name, color = OnBackground, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                if (cmt.edited) {
                    Spacer(Modifier.width(4.dp))
                    Text("· ${Strings.editedLabel(language)}", color = Muted, fontSize = 10.sp)
                }
            }
            if (cmt.replyTo.isNotBlank()) {
                Text("@${cmt.replyTo}", color = Amber, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.height(2.dp))
            Text(cmt.text, color = OnSurface, fontSize = 14.sp, lineHeight = 20.sp)
            Row(
                modifier              = Modifier.padding(top = 4.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (cmt.likes > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Favorite, null, tint = Color(0xFFEF4444), modifier = Modifier.size(11.dp))
                        Spacer(Modifier.width(2.dp))
                        Text("${cmt.likes}", color = Muted, fontSize = 11.sp)
                    }
                }
                Text(Strings.replyAction(language), color = Muted, fontSize = 11.sp, modifier = Modifier.clickable { onReply() })
                if (canEdit) {
                    Text(Strings.editAction(language), color = Muted, fontSize = 11.sp, modifier = Modifier.clickable { onEdit() })
                }
                if (canDelete) {
                    Text(Strings.deleteAction(language), color = Color(0xFFEF4444).copy(alpha = 0.7f), fontSize = 11.sp, modifier = Modifier.clickable { onDelete() })
                }
            }
        }
    }
}
