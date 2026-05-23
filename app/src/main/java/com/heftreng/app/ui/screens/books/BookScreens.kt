package com.heftreng.app.ui.screens.books

// ═══════════════════════════════════════════════════════════════════════════
//  BookScreens — Birleşik kitap/seri listesi, detay, bölüm okuma
//
//  Firestore koleksiyonları değişmez:
//    books/{id}/chapters/{id}   → type=="book"
//    serials/{id}/chapters/{id} → type=="serial"
//
//  UI katmanı sadece Book / BookChapter modelini görür.
//  Tür farkı yalnızca küçük bir rozet ile gösterilir.
// ═══════════════════════════════════════════════════════════════════════════

import android.text.Html
import android.text.Spanned
import android.widget.TextView
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.heftreng.app.data.model.Book
import com.heftreng.app.data.model.BookChapter
import com.heftreng.app.ui.component.RichTextEditor
import com.heftreng.app.ui.i18n.Strings
import com.heftreng.app.ui.screens.social.LikerListSheet
import com.heftreng.app.ui.screens.social.UserAvatar
import com.heftreng.app.ui.theme.*
import com.heftreng.app.viewmodel.BookViewModel
import com.heftreng.app.viewmodel.FeedViewModel
import com.heftreng.app.viewmodel.SettingsViewModel
import com.heftreng.app.viewmodel.SocialViewModel
import kotlinx.coroutines.tasks.await

// ── Birleşik Kitap Listesi ────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BooksScreen(
    navController: NavController,
    language     : String = "tr",
    vm           : BookViewModel = hiltViewModel(),
) {
    val books   by vm.books.collectAsState()
    val loading by vm.loading.collectAsState()
    var showCreate by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.loadBooks() }

    Scaffold(
        modifier = Modifier.imePadding(),
        containerColor = Background,
        topBar = {
            TopAppBar(
                title = { Text(Strings.booksTitle(language), fontWeight = FontWeight.Bold, color = OnBackground) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Background),
                actions = {
                    IconButton(onClick = { showCreate = true }) {
                        Icon(Icons.Default.Add, null, tint = Amber)
                    }
                }
            )
        }
    ) { padding ->
        if (loading && books.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Amber)
            }
        } else if (books.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Outlined.MenuBook, null, tint = Divider, modifier = Modifier.size(56.dp))
                    Text(Strings.booksEmpty(language), color = Muted)
                    TextButton(onClick = { showCreate = true }) {
                        Text("+ ${Strings.bookAddBtn(language)}", color = Amber)
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(books, key = { it.id }) { book ->
                    BookCard(
                        book      = book,
                        language  = language,
                        onClick   = { navController.navigate("book/${book.id}?type=${book.type}") },
                        onLike    = { vm.toggleLikeBook(book) },
                        onProfile = { navController.navigate("profile/${book.uid}") },
                    )
                }
            }
        }
    }

    if (showCreate) {
        CreateBookDialog(
            language  = language,
            onDismiss = { showCreate = false },
            onCreate  = { title, desc, genre, type ->
                vm.createBook(title, desc, genre, type = type)
                showCreate = false
            },
        )
    }
}

// ── Kitap/Seri Detay ──────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookDetailScreen(
    bookId       : String,
    type         : String = "book",
    navController: NavController,
    language     : String = "tr",
    vm           : BookViewModel  = hiltViewModel(),
    socialVm     : SocialViewModel = hiltViewModel(),
) {
    val book     by vm.selectedBook.collectAsState()
    val chapters by vm.chapters.collectAsState()
    val loading  by vm.loading.collectAsState()
    val likers   by socialVm.likers.collectAsState()
    val socialLoading by socialVm.loading.collectAsState()
    val myUid = FirebaseAuth.getInstance().currentUser?.uid ?: ""

    var showLikers     by remember { mutableStateOf(false) }
    var showAddChapter by remember { mutableStateOf(false) }
    var showDeleteBook by remember { mutableStateOf(false) }
    var chapterToEdit   by remember { mutableStateOf<BookChapter?>(null) }
    var chapterToDelete by remember { mutableStateOf<BookChapter?>(null) }
    var addTitle by remember { mutableStateOf("") }
    var addBody  by remember { mutableStateOf("") }
    var editTitle by remember { mutableStateOf("") }
    var editBody  by remember { mutableStateOf("") }

    LaunchedEffect(bookId) { vm.loadBook(bookId, type) }

    Scaffold(
        modifier = Modifier.imePadding(),
        containerColor = Background,
        topBar = {
            TopAppBar(
                title = {
                    Text(book?.title ?: "", color = OnBackground, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = OnBackground)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Background),
                actions = {
                    if (myUid.isNotBlank() && myUid == book?.uid) {
                        IconButton(onClick = { showDeleteBook = true }) {
                            Icon(Icons.Default.Delete, null, tint = Color(0xFFEF4444))
                        }
                        IconButton(onClick = { showAddChapter = true }) {
                            Icon(Icons.Default.Add, null, tint = Amber)
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (loading && book == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Amber)
            }
            return@Scaffold
        }

        val b = book ?: return@Scaffold

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 80.dp),
        ) {
            item {
                BookDetailHeader(
                    book         = b,
                    language     = language,
                    onProfile    = { navController.navigate("profile/${b.uid}") },
                    onLike       = { vm.toggleLikeBook(b) },
                    onShowLikers = {
                        if (type == "serial") socialVm.loadSerialLikers(b.id)
                        else socialVm.loadPostLikers(b.id)
                        showLikers = true
                    },
                )
            }

            if (chapters.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.FormatListNumbered, null, tint = Amber, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            Strings.bookChaptersTitle(language, chapters.size),
                            color = OnBackground, fontWeight = FontWeight.SemiBold, fontSize = 15.sp,
                        )
                    }
                }
                items(chapters, key = { it.id }) { chapter ->
                    UnifiedChapterRow(
                        chapter  = chapter,
                        canEdit  = myUid.isNotBlank() && myUid == b.uid,
                        language = language,
                        onClick  = { navController.navigate("book_chapter/${b.id}/${chapter.id}?type=$type") },
                        onEdit   = { chapterToEdit = chapter; editTitle = chapter.title; editBody = chapter.body },
                        onDelete = { chapterToDelete = chapter },
                    )
                    HorizontalDivider(color = Divider, thickness = 0.5.dp, modifier = Modifier.padding(start = 56.dp))
                }
            } else if (!loading) {
                item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text(Strings.bookChaptersEmpty(language), color = Muted, fontSize = 13.sp)
                    }
                }
            }
        }
    }

    // Kitap/Seri silme dialog
    if (showDeleteBook) {
        val label = if (type == "serial") "seriyi" else "kitabı"
        AlertDialog(
            onDismissRequest = { showDeleteBook = false },
            containerColor   = HeftSurface,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Delete, null, tint = Color(0xFFEF4444), modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("${if (type == "serial") "Seriyi" else "Kitabı"} Sil", color = OnBackground, fontWeight = FontWeight.SemiBold)
                }
            },
            text = {
                Text(
                    "\"${book?.title}\" adlı $label ve tüm bölümlerini kalıcı olarak silmek istediğine emin misin?",
                    color = Muted,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteBook(bookId, type)
                    showDeleteBook = false
                    navController.popBackStack()
                }) {
                    Text("Evet, Sil", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteBook = false }) {
                    Text(Strings.cancel(language), color = Muted)
                }
            },
        )
    }
        LikerListSheet(
            title     = Strings.likedBy(language),
            likers    = likers,
            loading   = socialLoading,
            onDismiss = { showLikers = false; socialVm.clearLikers() },
            onProfile = { uid -> showLikers = false; navController.navigate("profile/$uid") },
        )
    }

    if (showAddChapter) {
        ChapterEditorOverlay(
            title         = addTitle,
            body          = addBody,
            onTitleChange = { addTitle = it },
            onBodyChange  = { addBody = it },
            heading       = Strings.newChapter(language),
            saveLabel     = Strings.save(language),
            canSave       = addTitle.isNotBlank() && addBody.isNotBlank(),
            language      = language,
            onDismiss     = { showAddChapter = false; addTitle = ""; addBody = "" },
            onSave        = {
                vm.addChapter(bookId, addTitle, addBody, type)
                showAddChapter = false; addTitle = ""; addBody = ""
            },
        )
    }

    chapterToEdit?.let { ch ->
        ChapterEditorOverlay(
            title         = editTitle,
            body          = editBody,
            onTitleChange = { editTitle = it },
            onBodyChange  = { editBody = it },
            heading       = Strings.editChapter(language),
            saveLabel     = Strings.save(language),
            canSave       = editTitle.isNotBlank() && editBody.isNotBlank(),
            language      = language,
            onDismiss     = { chapterToEdit = null },
            onSave        = {
                vm.updateChapter(bookId, ch.id, editTitle, editBody, type)
                chapterToEdit = null
            },
        )
    }

    chapterToDelete?.let { ch ->
        AlertDialog(
            onDismissRequest = { chapterToDelete = null },
            containerColor   = HeftSurface,
            title = { Text(Strings.deleteChapter(language), color = OnBackground, fontWeight = FontWeight.SemiBold) },
            text  = { Text("\"${ch.title}\" bölümünü silmek istediğine emin misin?", color = Muted) },
            confirmButton = {
                TextButton(onClick = { vm.deleteChapter(bookId, ch.id, type); chapterToDelete = null }) {
                    Text("Sil", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { chapterToDelete = null }) { Text(Strings.cancel(language), color = Muted) }
            },
        )
    }
}

// ── Bölüm Okuma ──────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun BookChapterReadScreen(
    parentId     : String,
    chapterId    : String,
    type         : String = "book",
    navController: NavController,
    language     : String = "tr",
    vm           : BookViewModel = hiltViewModel(),
    feedVm       : FeedViewModel  = hiltViewModel(),
) {
    val chapter  by vm.selectedChapter.collectAsState()
    val chapters by vm.chapters.collectAsState()
    val book     by vm.selectedBook.collectAsState()
    val auth     = FirebaseAuth.getInstance()
    val db       = FirebaseFirestore.getInstance()
    val myUid    = auth.currentUser?.uid ?: ""
    val scope    = rememberCoroutineScope()

    var repostDone by remember { mutableStateOf(false) }

    LaunchedEffect(parentId, chapterId) {
        vm.loadBook(parentId, type)
        vm.loadChapter(parentId, chapterId, type)
    }

    val currentIndex = chapters.indexOfFirst { it.id == chapterId }
    val prevChapter  = if (currentIndex > 0) chapters[currentIndex - 1] else null
    val nextChapter  = if (currentIndex in 0 until chapters.size - 1) chapters[currentIndex + 1] else null

    // Yorum state
    data class ChCmt(
        val id: String = "", val uid: String = "", val name: String = "",
        val photoURL: String = "", val text: String = "", val replyTo: String = "",
        val replyToCmtId: String = "", val likes: Int = 0, val edited: Boolean = false,
        val ts: com.google.firebase.Timestamp? = null,
    )

    var comments     by remember { mutableStateOf<List<ChCmt>>(emptyList()) }
    var cmtLoading   by remember { mutableStateOf(true) }
    var inputText    by remember { mutableStateOf("") }
    var replyTo      by remember { mutableStateOf<ChCmt?>(null) }
    var editTarget   by remember { mutableStateOf<ChCmt?>(null) }
    var deleteTarget by remember { mutableStateOf<ChCmt?>(null) }
    var menuTarget   by remember { mutableStateOf<ChCmt?>(null) }
    val focusRequester = remember { FocusRequester() }
    val keyboardCtrl   = LocalSoftwareKeyboardController.current
    val listState      = rememberLazyListState()

    var myName  by remember { mutableStateOf("") }
    var myPhoto by remember { mutableStateOf("") }
    LaunchedEffect(myUid) {
        if (myUid.isBlank()) return@LaunchedEffect
        try {
            val doc = db.collection("users").document(myUid).get().await()
            myName  = doc.getString("displayName") ?: doc.getString("name") ?: ""
            myPhoto = doc.getString("photoURL") ?: ""
        } catch (_: Exception) {}
    }

    LaunchedEffect(editTarget) {
        editTarget?.let { inputText = it.text; focusRequester.requestFocus(); keyboardCtrl?.show() }
    }

    // Realtime yorum listener — type'a göre doğru koleksiyon
    val cmtCol = if (type == "serial") "serials" else "books"
    DisposableEffect(chapterId) {
        val reg = db.collection(cmtCol).document(parentId)
            .collection("chapters").document(chapterId)
            .collection("comments")
            .orderBy("ts", Query.Direction.ASCENDING)
            .addSnapshotListener { snap, _ ->
                if (snap == null) { cmtLoading = false; return@addSnapshotListener }
                comments = snap.documents.mapNotNull { doc ->
                    val d = doc.data ?: return@mapNotNull null
                    ChCmt(
                        id           = doc.id,
                        uid          = d["uid"]          as? String ?: "",
                        name         = (d["displayName"] as? String)?.ifBlank { null } ?: d["name"] as? String ?: "?",
                        photoURL     = d["photoURL"]     as? String ?: "",
                        text         = d["text"]         as? String ?: "",
                        replyTo      = d["replyTo"]      as? String ?: "",
                        replyToCmtId = d["replyToCmtId"] as? String ?: "",
                        likes        = (d["likes"]       as? Long)?.toInt() ?: 0,
                        edited       = d["edited"]       as? Boolean ?: false,
                        ts           = d["ts"]           as? com.google.firebase.Timestamp,
                    )
                }
                cmtLoading = false
            }
        onDispose { reg.remove() }
    }

    fun submitComment() {
        val text    = inputText.trim()
        if (text.isBlank() || myUid.isBlank()) return
        val editing = editTarget
        inputText   = ""
        editTarget  = null
        if (editing != null) {
            vm.editChapterComment(parentId, chapterId, editing.id, text, type)
            return
        }
        val rTo = replyTo
        replyTo = null
        vm.addChapterComment(parentId, chapterId, text, rTo?.name ?: "", rTo?.id ?: "", type)
    }

    Box(modifier = Modifier.fillMaxSize().imePadding()) {
        Scaffold(
            containerColor = Background,
            topBar = {
                TopAppBar(
                    title = {
                        Text(chapter?.title ?: "", color = OnBackground, fontWeight = FontWeight.SemiBold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = OnBackground)
                        }
                    },
                    actions = {
                        val ch = chapter; val b = book
                        if (ch != null && type == "serial") {
                            IconButton(onClick = {
                                if (!repostDone) {
                                    feedVm.repostBookChapter(
                                        bookId         = parentId,
                                        chapterId      = ch.id,
                                        bookTitle      = b?.title ?: "",
                                        chapterTitle   = ch.title,
                                        chapterOrder   = ch.order,
                                        chapterBody    = ch.body,
                                        bookCoverImg   = b?.coverImg ?: "",
                                        bookAuthorUid  = b?.uid ?: ch.uid,
                                        bookAuthorName = b?.name ?: "",
                                    )
                                    repostDone = true
                                }
                            }) {
                                Icon(Icons.Default.Repeat, null,
                                    tint = if (repostDone) Amber else Muted)
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Background),
                )
            },
            bottomBar = {
                Row(
                    modifier = Modifier.fillMaxWidth().background(HeftSurface).padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    if (prevChapter != null) {
                        TextButton(onClick = {
                            navController.navigate("book_chapter/$parentId/${prevChapter.id}?type=$type") {
                                popUpTo("book_chapter/$parentId/$chapterId") { inclusive = true }
                            }
                        }) {
                            Icon(Icons.Default.ChevronLeft, null, tint = Primary)
                            Text(Strings.prevChapter(language), color = Primary, fontSize = 13.sp)
                        }
                    } else Spacer(Modifier.width(1.dp))
                    if (nextChapter != null) {
                        TextButton(onClick = {
                            navController.navigate("book_chapter/$parentId/${nextChapter.id}?type=$type") {
                                popUpTo("book_chapter/$parentId/$chapterId") { inclusive = true }
                            }
                        }) {
                            Text(Strings.nextChapter(language), color = Amber, fontSize = 13.sp)
                            Icon(Icons.Default.ChevronRight, null, tint = Amber)
                        }
                    } else Spacer(Modifier.width(1.dp))
                }
            }
        ) { padding ->
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                LazyColumn(
                    state          = listState,
                    modifier       = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 16.dp),
                ) {
                    // Bölüm içeriği
                    item {
                        chapter?.let { ch ->
                            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                                Text(
                                    "${Strings.chapter(language)} ${ch.order} · ${Strings.wordCount(language, ch.wordCount)}",
                                    color = Muted, fontSize = 12.sp,
                                )
                                Spacer(Modifier.height(16.dp))
                                if (ch.body.contains("<") && ch.body.contains(">")) {
                                    AndroidView(
                                        modifier = Modifier.fillMaxWidth(),
                                        factory  = { ctx ->
                                            TextView(ctx).apply {
                                                setTextColor(android.graphics.Color.parseColor("#E5E5EA"))
                                                textSize = 16f; setLineSpacing(0f, 1.6f)
                                            }
                                        },
                                        update = { tv ->
                                            @Suppress("DEPRECATION")
                                            val spanned: Spanned = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N)
                                                Html.fromHtml(ch.body, Html.FROM_HTML_MODE_COMPACT)
                                            else Html.fromHtml(ch.body)
                                            tv.text = spanned
                                        }
                                    )
                                } else {
                                    Text(ch.body, color = OnBackground, fontSize = 16.sp, lineHeight = 26.sp)
                                }
                                Spacer(Modifier.height(24.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.clickable {
                                            vm.toggleLikeChapter(parentId, chapterId, ch.isLikedByMe, type)
                                        },
                                    ) {
                                        Icon(
                                            if (ch.isLikedByMe) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                            null, tint = if (ch.isLikedByMe) Color(0xFFEF4444) else Muted,
                                            modifier = Modifier.size(20.dp),
                                        )
                                        Spacer(Modifier.width(5.dp))
                                        Text("${ch.likes}", color = Muted, fontSize = 13.sp)
                                    }
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.clickable { focusRequester.requestFocus(); keyboardCtrl?.show() },
                                    ) {
                                        Icon(Icons.Outlined.ChatBubbleOutline, null, tint = Muted, modifier = Modifier.size(20.dp))
                                        Spacer(Modifier.width(5.dp))
                                        Text("${ch.cmtCount}", color = Muted, fontSize = 13.sp)
                                    }
                                }
                            }
                        } ?: Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Amber)
                        }
                        HorizontalDivider(color = SurfaceVar, thickness = 6.dp)
                    }

                    // Yorumlar
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
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
                        val isOwner   = myUid.isNotBlank() && cmt.uid == myUid
                        val canDelete = isOwner || chapter?.uid == myUid
                        ChapterCommentRow(
                            id          = cmt.id,
                            uid         = cmt.uid,
                            name        = cmt.name,
                            photoURL    = cmt.photoURL,
                            text        = cmt.text,
                            replyTo     = cmt.replyTo,
                            likes       = cmt.likes,
                            edited      = cmt.edited,
                            canEdit     = isOwner,
                            canDelete   = canDelete,
                            language    = language,
                            onLongPress = { menuTarget = cmt },
                            onReply     = { replyTo = cmt; editTarget = null; focusRequester.requestFocus(); keyboardCtrl?.show() },
                            onEdit      = { editTarget = cmt; replyTo = null },
                            onDelete    = { deleteTarget = cmt },
                        )
                        HorizontalDivider(color = Divider.copy(alpha = 0.4f), thickness = 0.5.dp, modifier = Modifier.padding(start = 56.dp))
                    }
                }

                // Düzenleme / Yanıt göstergesi
                val indicator = editTarget ?: replyTo
                if (indicator != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth().background(SurfaceVar).padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            if (editTarget != null) {
                                Icon(Icons.Default.Edit, null, tint = Amber, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(Strings.editCommentTitle(language), color = Amber, fontSize = 12.sp)
                            } else {
                                Icon(Icons.AutoMirrored.Filled.Reply, null, tint = Amber, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("@${replyTo!!.name} ${Strings.replyingToSuffix(language)}", color = Amber, fontSize = 12.sp)
                            }
                        }
                        IconButton(onClick = { editTarget = null; replyTo = null; inputText = "" }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Close, null, tint = Muted, modifier = Modifier.size(16.dp))
                        }
                    }
                }

                // Giriş kutusu
                HorizontalDivider(color = Divider)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Background)
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
                        modifier        = Modifier.weight(1f).focusRequester(focusRequester),
                        shape           = RoundedCornerShape(24.dp),
                        colors          = hfTextFieldColors(),
                        maxLines        = 4,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { submitComment() }),
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(
                        onClick  = { submitComment() },
                        enabled  = inputText.isNotBlank(),
                        modifier = Modifier.size(44.dp).background(
                            if (inputText.isNotBlank()) Amber else Muted.copy(alpha = 0.15f), CircleShape,
                        ),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, null,
                            tint = if (inputText.isNotBlank()) Color.Black else Muted,
                            modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }

    // Long-press menü
    menuTarget?.let { cmt ->
        val isOwner   = myUid.isNotBlank() && cmt.uid == myUid
        val canDelete = isOwner || chapter?.uid == myUid
        AlertDialog(
            onDismissRequest = { menuTarget = null },
            containerColor   = HeftSurface,
            title = { Text(cmt.name, color = OnBackground, fontWeight = FontWeight.SemiBold, fontSize = 15.sp) },
            text  = { Text(cmt.text.take(100) + if (cmt.text.length > 100) "…" else "", color = Muted, fontSize = 13.sp) },
            confirmButton = {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { replyTo = cmt; editTarget = null; menuTarget = null; focusRequester.requestFocus(); keyboardCtrl?.show() }, Modifier.fillMaxWidth()) {
                        Icon(Icons.AutoMirrored.Filled.Reply, null, tint = Amber, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(8.dp))
                        Text(Strings.replyAction(language), color = Amber)
                    }
                    if (isOwner) TextButton(onClick = { editTarget = cmt; replyTo = null; menuTarget = null }, Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Edit, null, tint = OnBackground, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(8.dp))
                        Text(Strings.editAction(language), color = OnBackground)
                    }
                    if (canDelete) TextButton(onClick = { deleteTarget = cmt; menuTarget = null }, Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Delete, null, tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp)); Spacer(Modifier.width(8.dp))
                        Text(Strings.deleteAction(language), color = Color(0xFFEF4444))
                    }
                    TextButton(onClick = { menuTarget = null }, Modifier.fillMaxWidth()) {
                        Text(Strings.cancelAction(language), color = Muted)
                    }
                }
            },
        )
    }

    deleteTarget?.let { cmt ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            containerColor   = HeftSurface,
            title  = { Text(Strings.deleteCommentTitle(language), color = OnBackground, fontWeight = FontWeight.SemiBold) },
            text   = { Text(cmt.text.take(80), color = Muted, fontSize = 13.sp) },
            confirmButton  = {
                TextButton(onClick = { vm.deleteChapterComment(parentId, cmt.id, chapterId, type); deleteTarget = null }) {
                    Text(Strings.deleteAction(language), color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton  = { TextButton(onClick = { deleteTarget = null }) { Text(Strings.cancelAction(language), color = Muted) } },
        )
    }
}

// ── Alt bileşenler ────────────────────────────────────────────────────────────

@Composable
fun BookCard(
    book      : Book,
    language  : String = "tr",
    onClick   : () -> Unit,
    onLike    : () -> Unit,
    onProfile : () -> Unit,
) {
    val isSerial = book.type == "serial"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(HeftSurface)
            .border(1.dp, Divider, RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier.size(64.dp, 88.dp).clip(RoundedCornerShape(8.dp)).background(SurfaceVar),
            contentAlignment = Alignment.Center,
        ) {
            if (book.coverImg.isNotBlank()) {
                AsyncImage(model = book.coverImg, contentDescription = null,
                    contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Icon(
                    if (isSerial) Icons.Default.AutoStories else Icons.Default.MenuBook,
                    null, tint = Muted, modifier = Modifier.size(28.dp),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            // Tür rozeti
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    Modifier.clip(RoundedCornerShape(4.dp))
                        .background(if (isSerial) Color(0xFF7C3AED).copy(alpha = 0.15f) else Amber.copy(alpha = 0.15f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        if (isSerial) (if (language == "ku") "Rêze" else "Seri")
                        else          (if (language == "ku") "Pirtûk" else "Kitap"),
                        color = if (isSerial) Color(0xFF7C3AED) else Amber,
                        fontSize = 9.sp, fontWeight = FontWeight.Bold,
                    )
                }
                if (book.genre.isNotBlank()) {
                    Box(Modifier.clip(RoundedCornerShape(4.dp)).background(SurfaceVar).padding(horizontal = 6.dp, vertical = 2.dp)) {
                        Text(book.genre, color = Primary, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(book.title, color = OnBackground, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Row(modifier = Modifier.clickable { onProfile() }, verticalAlignment = Alignment.CenterVertically) {
                UserAvatar(name = book.name, photoURL = book.photoURL, size = 18)
                Spacer(Modifier.width(5.dp))
                Text(book.name, color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (book.desc.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(book.desc, color = OnSurface, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 18.sp)
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.clickable { onLike() }, verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (book.isLikedByMe) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                        null, tint = if (book.isLikedByMe) Color(0xFFEF4444) else Muted,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("${book.likes}", color = Muted, fontSize = 11.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.FormatListNumbered, null, tint = Muted, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("${book.chapterCount} ${Strings.chapter(language)}", color = Muted, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
fun BookDetailHeader(
    book        : Book,
    language    : String = "tr",
    onProfile   : () -> Unit,
    onLike      : () -> Unit,
    onShowLikers: () -> Unit,
) {
    val isSerial = book.type == "serial"
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            Modifier.fillMaxWidth().height(180.dp)
                .background(
                    if (book.bg.isNotBlank())
                        try { Color(android.graphics.Color.parseColor(book.bg)) } catch (_: Exception) { SurfaceVar }
                    else SurfaceVar
                )
        ) {
            if (book.coverImg.isNotBlank()) {
                AsyncImage(model = book.coverImg, contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().graphicsLayer { alpha = 0.5f })
                AsyncImage(model = book.coverImg, contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.align(Alignment.Center).size(100.dp, 140.dp)
                        .clip(RoundedCornerShape(8.dp)))
            } else {
                Box(
                    Modifier.align(Alignment.Center).size(100.dp, 140.dp)
                        .clip(RoundedCornerShape(8.dp)).background(SurfaceVar),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (isSerial) Icons.Default.AutoStories else Icons.Default.MenuBook,
                        null, tint = Muted, modifier = Modifier.size(40.dp),
                    )
                }
            }
            // Tür rozeti — sağ üst
            Box(
                Modifier.align(Alignment.TopEnd).padding(10.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (isSerial) Color(0xFF7C3AED) else Amber)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    if (isSerial) (if (language == "ku") "Rêze" else "Seri")
                    else          (if (language == "ku") "Pirtûk" else "Kitap"),
                    color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                )
            }
        }

        Column(Modifier.padding(16.dp)) {
            Text(book.title, color = OnBackground, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
            Spacer(Modifier.height(6.dp))
            Row(modifier = Modifier.clickable { onProfile() }, verticalAlignment = Alignment.CenterVertically) {
                UserAvatar(name = book.name, photoURL = book.photoURL, size = 22)
                Spacer(Modifier.width(6.dp))
                Text(book.name, color = Muted, fontSize = 13.sp)
            }
            if (book.genre.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Box(Modifier.clip(RoundedCornerShape(8.dp)).background(SurfaceVar).padding(horizontal = 10.dp, vertical = 4.dp)) {
                    Text(book.genre, color = Primary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            if (book.desc.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(book.desc, color = OnSurface, fontSize = 14.sp, lineHeight = 22.sp)
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(
                    modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(SurfaceVar)
                        .clickable { onLike() }.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        if (book.isLikedByMe) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                        null, tint = if (book.isLikedByMe) Color(0xFFEF4444) else Muted,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("${book.likes}", color = OnBackground, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
                TextButton(onClick = onShowLikers) {
                    Text(Strings.likedBy(language), color = Muted, fontSize = 12.sp)
                }
            }
        }
        HorizontalDivider(color = Divider)
    }
}

@Composable
fun UnifiedChapterRow(
    chapter  : BookChapter,
    canEdit  : Boolean,
    language : String = "tr",
    onClick  : () -> Unit,
    onEdit   : () -> Unit,
    onDelete : () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(HeftSurface)
            .clickable { onClick() }
            .padding(start = 14.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("${chapter.order}", color = Amber, fontWeight = FontWeight.Bold, fontSize = 13.sp,
            modifier = Modifier.width(28.dp))
        Column(Modifier.weight(1f)) {
            Text(chapter.title, color = OnBackground, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(Strings.wordCount(language, chapter.wordCount), color = Muted, fontSize = 11.sp)
        }
        if (canEdit) {
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Default.MoreVert, null, tint = Muted, modifier = Modifier.size(18.dp))
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }, containerColor = HeftSurface) {
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.Edit, null, tint = Amber, modifier = Modifier.size(16.dp))
                                Text(Strings.editAction(language), color = OnBackground, fontSize = 14.sp)
                            }
                        },
                        onClick = { menuExpanded = false; onEdit() },
                    )
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.Delete, null, tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                                Text(Strings.deleteAction(language), color = Color(0xFFEF4444), fontSize = 14.sp)
                            }
                        },
                        onClick = { menuExpanded = false; onDelete() },
                    )
                }
            }
        } else {
            Icon(Icons.Default.ChevronRight, null, tint = Muted, modifier = Modifier.size(18.dp))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChapterCommentRow(
    id: String, uid: String, name: String, photoURL: String, text: String,
    replyTo: String, likes: Int, edited: Boolean,
    canEdit: Boolean, canDelete: Boolean, language: String,
    onLongPress: () -> Unit, onReply: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .combinedClickable(onClick = {}, onLongClick = onLongPress)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(SurfaceVar), contentAlignment = Alignment.Center) {
            if (photoURL.isNotBlank()) {
                AsyncImage(model = photoURL, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Text(name.firstOrNull()?.uppercase() ?: "?", color = OnBackground, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, color = OnBackground, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                if (edited) { Spacer(Modifier.width(4.dp)); Text("· ${Strings.editedLabel(language)}", color = Muted, fontSize = 10.sp) }
            }
            if (replyTo.isNotBlank()) Text("@$replyTo", color = Amber, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(2.dp))
            Text(text, color = OnSurface, fontSize = 14.sp, lineHeight = 20.sp)
            Row(modifier = Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (likes > 0) Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Favorite, null, tint = Color(0xFFEF4444), modifier = Modifier.size(11.dp))
                    Spacer(Modifier.width(2.dp)); Text("$likes", color = Muted, fontSize = 11.sp)
                }
                Text(Strings.replyAction(language), color = Muted, fontSize = 11.sp, modifier = Modifier.clickable { onReply() })
                if (canEdit) Text(Strings.editAction(language), color = Muted, fontSize = 11.sp, modifier = Modifier.clickable { onEdit() })
                if (canDelete) Text(Strings.deleteAction(language), color = Color(0xFFEF4444).copy(alpha = 0.7f), fontSize = 11.sp, modifier = Modifier.clickable { onDelete() })
            }
        }
    }
}

// ── Bölüm yazma overlay ───────────────────────────────────────────────────────
@Composable
fun ChapterEditorOverlay(
    title        : String,
    body         : String,
    onTitleChange: (String) -> Unit,
    onBodyChange : (String) -> Unit,
    heading      : String,
    saveLabel    : String,
    canSave      : Boolean,
    language     : String,
    onDismiss    : () -> Unit,
    onSave       : () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize().background(HeftSurface).imePadding()) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            Row(
                modifier = Modifier.fillMaxWidth().background(HeftSurface).padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, null, tint = Muted) }
                Text(heading, color = OnBackground, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                TextButton(onClick = onSave, enabled = canSave) {
                    Text(saveLabel, color = if (canSave) Amber else Muted, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
            HorizontalDivider(color = Divider)
            OutlinedTextField(
                value         = title,
                onValueChange = onTitleChange,
                placeholder   = { Text("${Strings.chapterTitle(language)} *", color = Muted) },
                singleLine    = true,
                modifier      = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                colors        = hfTextFieldColors(),
            )
            HorizontalDivider(color = Divider)
            Box(modifier = Modifier.weight(1f).fillMaxWidth().navigationBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp)) {
                RichTextEditor(value = body, onChange = onBodyChange, placeholder = "$heading...", modifier = Modifier.fillMaxSize())
            }
        }
    }
}

// ── Kitap/Seri oluşturma dialog ───────────────────────────────────────────────
@Composable
fun CreateBookDialog(
    language  : String,
    onDismiss : () -> Unit,
    onCreate  : (String, String, String, String) -> Unit,  // title, desc, genre, type
) {
    var title    by remember { mutableStateOf("") }
    var desc     by remember { mutableStateOf("") }
    var genre    by remember { mutableStateOf("") }
    var typeSerial by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = HeftSurface,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.MenuBook, null, tint = Amber, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text(Strings.bookNewTitle(language), color = OnBackground, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Tür seçimi — toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(SurfaceVar)
                        .padding(4.dp),
                ) {
                    listOf(false to (if (language == "ku") "Pirtûk" else "Kitap"),
                           true  to (if (language == "ku") "Rêze"   else "Seri")).forEach { (isSerial, label) ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (typeSerial == isSerial) (if (isSerial) Color(0xFF7C3AED) else Amber) else Color.Transparent)
                                .clickable { typeSerial = isSerial }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(label, color = if (typeSerial == isSerial) Color.White else Muted,
                                fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        }
                    }
                }
                OutlinedTextField(
                    value = title, onValueChange = { title = it },
                    label = { Text(Strings.bookNameLabel(language)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth(), colors = hfTextFieldColors(),
                )
                OutlinedTextField(
                    value = desc, onValueChange = { desc = it },
                    label = { Text(Strings.bookDescLabel(language)) },
                    minLines = 2, modifier = Modifier.fillMaxWidth(), colors = hfTextFieldColors(),
                )
                OutlinedTextField(
                    value = genre, onValueChange = { genre = it },
                    label = { Text(Strings.bookGenreLabel(language)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth(), colors = hfTextFieldColors(),
                    leadingIcon = { Icon(Icons.Default.Category, null, tint = Muted, modifier = Modifier.size(18.dp)) },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (title.isNotBlank()) onCreate(title.trim(), desc.trim(), genre.trim(), if (typeSerial) "serial" else "book") },
                enabled = title.isNotBlank(),
            ) { Text(Strings.bookCreateBtn(language), color = Amber, fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(Strings.cancelAction(language), color = Muted) }
        },
    )
}

// ── TextField renkleri ────────────────────────────────────────────────────────
@Composable
fun hfTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor      = Amber,
    unfocusedBorderColor    = Divider,
    focusedTextColor        = OnBackground,
    unfocusedTextColor      = OnBackground,
    unfocusedContainerColor = SurfaceVar,
    focusedContainerColor   = SurfaceVar,
    focusedLabelColor       = Amber,
    unfocusedLabelColor     = Muted,
    cursorColor             = Amber,
)
