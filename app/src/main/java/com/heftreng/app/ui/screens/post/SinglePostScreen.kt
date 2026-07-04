package com.heftreng.app.ui.screens.post

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
import com.heftreng.app.data.model.Comment
import com.heftreng.app.data.model.Post
import com.heftreng.app.navigation.Screen
import com.heftreng.app.ui.component.ConnectedPostCard
import com.heftreng.app.ui.component.MentionSuggestionBar
import com.heftreng.app.ui.component.MentionText
import com.heftreng.app.ui.i18n.Strings
import com.heftreng.app.ui.screens.social.LikerListSheet
import com.heftreng.app.ui.theme.*
import com.heftreng.app.viewmodel.FeedViewModel
import com.heftreng.app.viewmodel.SocialViewModel

// ─────────────────────────────────────────────────────────────────────────────
//  SinglePostScreen — Supabase feed_comments tabanlı tek detay ekranı
//  PostDetailScreen ve CommentsSheet kaldırıldı, tüm yorum CRUD buradan yönetilir.
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun SinglePostScreen(
    postId       : String,
    navController: NavController,
    vm           : FeedViewModel   = hiltViewModel(),
    socialVm     : SocialViewModel = hiltViewModel(),
    language     : String = "tr",
) {
    val ku = language == "ku"

    val posts         by vm.posts.collectAsState()
    val comments      by vm.comments.collectAsState()
    val cmtError      by vm.commentError.collectAsState()
    val likers        by socialVm.likers.collectAsState()
    val socialLoading by socialVm.loading.collectAsState()
    val postNotFound  by vm.postNotFound.collectAsState()

    val post        = posts.find { it.id == postId }
    val loadFailed  = postNotFound == postId

    val myUid = vm.uid

    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester     = remember { FocusRequester() }

    var cmtLoading   by remember { mutableStateOf(true) }
    var inputText    by remember { mutableStateOf("") }
    var replyTo      by remember { mutableStateOf<Comment?>(null) }
    var editTarget   by remember { mutableStateOf<Comment?>(null) }
    var deleteTarget by remember { mutableStateOf<Comment?>(null) }
    var menuTarget   by remember { mutableStateOf<Comment?>(null) }
    var showLikers   by remember { mutableStateOf(false) }
    var cmtLikersId  by remember { mutableStateOf<String?>(null) }
    val listState    = rememberLazyListState()

    // ── Yorum ağaçlaştırma — Instagram tarzı: üst yorum + altında girintili yanıtlar ──
    // replyTo.commentId üst yorumun id'siyle eşleşen her yorum, o üst yorumun "yanıtları"
    // listesine girer. Parent yorum silinmiş/bulunamıyorsa yanıt üst seviyede kalır
    // (kaybolmasın diye) — Instagram da silinen yorumun yanıtlarını böyle gösterir.
    val commentThreads = remember(comments) {
        val allIds = comments.map { it.id }.toSet()
        val topLevel = comments.filter { it.replyTo == null || it.replyTo.commentId !in allIds }
        val repliesByParent = comments
            .filter { it.replyTo != null && it.replyTo.commentId in allIds }
            .groupBy { it.replyTo!!.commentId }
        topLevel.map { parent -> CommentThread(parent, repliesByParent[parent.id] ?: emptyList()) }
    }
    // Açık olan thread'lerin parent id'leri — varsayılan kapalı, tıklanınca açılır
    var expandedThreads by remember { mutableStateOf(setOf<String>()) }

    // ── Mention (@kullanıcı) — girilen metindeki sırayla eklenen uid listesi ────
    val mentionSuggestions by vm.mentionSuggestions.collectAsState()
    // key: metinde eklendiği andaki "@DisplayName " öneki, value: uid — sıraya göre gönderilirken kullanılır
    var mentionedUids by remember { mutableStateOf(listOf<String>()) }

    // inputText her değiştiğinde: sondaki "@query" tetikleyicisini bul, aksi halde öneriyi temizle
    LaunchedEffect(inputText) {
        val atIndex = inputText.lastIndexOf('@')
        if (atIndex == -1) {
            vm.clearMentionSuggestions()
        } else {
            val afterAt = inputText.substring(atIndex + 1)
            // @ sonrası boşluk varsa artık mention yazımı bitmiştir
            if (afterAt.contains(' ') || afterAt.contains('\n')) {
                vm.clearMentionSuggestions()
            } else if (afterAt.isNotEmpty()) {
                vm.searchMentionUsers(afterAt)
            } else {
                vm.clearMentionSuggestions()
            }
        }
    }

    fun onMentionSelected(user: com.heftreng.app.util.MentionHelper.MentionUser) {
        val atIndex = inputText.lastIndexOf('@')
        if (atIndex != -1) {
            val before = inputText.substring(0, atIndex)
            inputText = "$before@${user.username} "
            mentionedUids = mentionedUids + user.uid
        }
        vm.clearMentionSuggestions()
    }

    // Düzenleme moduna girilince inputText doldur + klavye aç
    LaunchedEffect(editTarget) {
        editTarget?.let {
            inputText = it.text
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    // Post ve yorumları yükle
    LaunchedEffect(postId) {
        cmtLoading = true
        vm.ensurePost(postId)
        vm.loadComments(postId)
        cmtLoading = false
    }

    // Feed arka planda yenilenip bu post listeden düşerse (feed sadece son N postu tutar),
    // sonsuza kadar "Yükleniyor..." ekranında kalmaması için post kaybolduğunda tekrar çek.
    LaunchedEffect(post, loadFailed) {
        if (post == null && !loadFailed) {
            vm.ensurePost(postId)
        }
    }

    // cmtError dialog
    if (cmtError != null) {
        AlertDialog(
            onDismissRequest = { vm.clearCommentError() },
            containerColor   = HeftSurface,
            title  = { Text(Strings.error(language), color = Color(0xFFEF4444), fontWeight = FontWeight.SemiBold) },
            text   = { Text(cmtError!!, color = Muted, fontSize = 13.sp) },
            confirmButton = { TextButton(onClick = { vm.clearCommentError() }) { Text(Strings.confirm(language), color = Amber) } },
        )
    }

    // ── UI ────────────────────────────────────────────────────────────────────
    Box(modifier = Modifier.fillMaxSize().imePadding()) {
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
                            Text(if (ku) "Bar dike..." else "Yükleniyor...", color = Muted, fontSize = 13.sp)
                        }
                    }
                }
                return@Scaffold
            }

            Column(modifier = Modifier.fillMaxSize().padding(padding)) {

                // ── Liste ─────────────────────────────────────────────────────
                LazyColumn(
                    state          = listState,
                    modifier       = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 8.dp),
                ) {
                    // Gönderi kartı
                    item {
                        ConnectedPostCard(
                            post             = post,
                            navController    = navController,
                            feedVm           = vm,
                            socialVm         = socialVm,
                            language         = language,
                            isDetailScreen   = true,
                            onDeleteOverride = { vm.deletePost(post.id); navController.popBackStack() },
                            onEditOverride   = { newTitle, newText -> vm.editPost(post.id, newTitle, newText) },
                        )
                        HorizontalDivider(color = SurfaceVar, thickness = 6.dp)
                    }

                    // Beğeni sayısı
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
                                Text(
                                    "${post.likesCount} ${if (ku) "xweşandin" else "beğeni"}",
                                    color = OnBackground, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                                )
                            }
                            HorizontalDivider(color = Divider)
                        }
                    }

                    // Yorum başlığı
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                if (ku) "Şîrove" else "Yorumlar",
                                color = OnBackground, fontWeight = FontWeight.Bold, fontSize = 16.sp,
                            )
                            if (comments.isNotEmpty()) Text("${comments.size}", color = Muted, fontSize = 13.sp)
                        }
                        HorizontalDivider(color = Divider)
                    }

                    // Yükleniyor
                    if (cmtLoading) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = Amber, modifier = Modifier.size(24.dp))
                            }
                        }
                    }

                    // Boş durum
                    if (!cmtLoading && comments.isEmpty()) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("💬", fontSize = 32.sp)
                                    Spacer(Modifier.height(8.dp))
                                    Text(if (ku) "Hîn şîrove tune" else "Henüz yorum yok", color = Muted, fontSize = 14.sp)
                                }
                            }
                        }
                    }

                    // Yorum listesi — üst yorum + altında girintili, aç/kapa'lı yanıtlar
                    items(commentThreads, key = { it.parent.id }) { thread ->
                        val cmt = thread.parent
                        val isOwner   = myUid.isNotBlank() && cmt.uid.isNotBlank() && cmt.uid == myUid
                        val isPostOwner = myUid.isNotBlank() && myUid == post.uid
                        val canDelete = isOwner || isPostOwner
                        val isExpanded = thread.parent.id in expandedThreads

                        Column {
                            SingleCommentRow(
                                cmt         = cmt,
                                myUid       = myUid,
                                canEdit     = isOwner,
                                canDelete   = canDelete,
                                language    = language,
                                onLikeClick = { vm.toggleCommentLike(postId, cmt) },
                                onLikersClick = {
                                    if (cmt.likesCount > 0) {
                                        cmtLikersId = cmt.id
                                        socialVm.loadCommentLikers(cmt.id)
                                    }
                                },
                                onReply     = {
                                    replyTo = cmt
                                    editTarget = null
                                    focusRequester.requestFocus()
                                    keyboardController?.show()
                                },
                                onEdit      = { editTarget = cmt; replyTo = null },
                                onDelete    = { deleteTarget = cmt },
                                onLongPress = { menuTarget = cmt },
                                onMentionClick = { uid -> navController.navigate("profile/$uid") },
                                onHashtagClick = { taggedPostId -> navController.navigate(Screen.PostDetail.go(taggedPostId)) },
                            )

                            // "N yanıtı gör" tıklacı — girintili, üst yorumun altında
                            if (thread.replies.isNotEmpty()) {
                                Row(
                                    modifier = Modifier
                                        .padding(start = 56.dp, top = 2.dp, bottom = 4.dp)
                                        .clickable {
                                            expandedThreads = if (isExpanded)
                                                expandedThreads - thread.parent.id
                                            else
                                                expandedThreads + thread.parent.id
                                        },
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    HorizontalDivider(
                                        color    = Muted.copy(alpha = 0.5f),
                                        modifier = Modifier.width(24.dp),
                                        thickness = 1.dp,
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        if (isExpanded)
                                            (if (ku) "Veşêre" else "Gizle")
                                        else {
                                            val n = thread.replies.size
                                            if (ku) "$n bersiv bibîne" else "$n yanıtı gör"
                                        },
                                        color      = Muted,
                                        fontSize   = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                            }

                            // Yanıtlar — girintili, hafif arka planlı ayrı kutular
                            if (isExpanded) {
                                thread.replies.forEach { reply ->
                                    val replyIsOwner   = myUid.isNotBlank() && reply.uid.isNotBlank() && reply.uid == myUid
                                    val replyCanDelete = replyIsOwner || isPostOwner
                                    Box(
                                        modifier = Modifier
                                            .padding(start = 40.dp, end = 8.dp, bottom = 2.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(SurfaceVar.copy(alpha = 0.5f)),
                                    ) {
                                        SingleCommentRow(
                                            cmt         = reply,
                                            myUid       = myUid,
                                            canEdit     = replyIsOwner,
                                            canDelete   = replyCanDelete,
                                            language    = language,
                                            onLikeClick = { vm.toggleCommentLike(postId, reply) },
                                            onLikersClick = {
                                                if (reply.likesCount > 0) {
                                                    cmtLikersId = reply.id
                                                    socialVm.loadCommentLikers(reply.id)
                                                }
                                            },
                                            onReply     = {
                                                replyTo = cmt // yanıta yanıt → yine üst yorumun thread'ine eklenir
                                                editTarget = null
                                                focusRequester.requestFocus()
                                                keyboardController?.show()
                                            },
                                            onEdit      = { editTarget = reply; replyTo = null },
                                            onDelete    = { deleteTarget = reply },
                                            onLongPress = { menuTarget = reply },
                                            onMentionClick = { uid -> navController.navigate("profile/$uid") },
                                            onHashtagClick = { taggedPostId -> navController.navigate(Screen.PostDetail.go(taggedPostId)) },
                                        )
                                    }
                                }
                            }
                        }
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
                                Text("@${replyTo!!.displayName} ${Strings.replyingToSuffix(language)}", color = Amber, fontSize = 12.sp, fontWeight = FontWeight.Medium)
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

                // ── Mention öneri barı ────────────────────────────────────────
                MentionSuggestionBar(
                    suggestions = mentionSuggestions,
                    onSelect    = { onMentionSelected(it) },
                )

                // ── Giriş kutusu ─────────────────────────────────────────────
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
                                    replyTo    != null -> "@${replyTo!!.displayName} ${Strings.reply(language)}..."
                                    else               -> if (ku) "Şîrove binivîse..." else "Yorum yaz..."
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
                        keyboardActions = KeyboardActions(onSend = {
                            submitComment(
                                vm          = vm,
                                post        = post,
                                inputText   = inputText,
                                editTarget  = editTarget,
                                replyTo     = replyTo,
                                mentionUids = mentionedUids,
                                onDone      = { inputText = ""; editTarget = null; replyTo = null; mentionedUids = emptyList() },
                            )
                        }),
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(
                        onClick  = {
                            submitComment(
                                vm          = vm,
                                post        = post,
                                inputText   = inputText,
                                editTarget  = editTarget,
                                replyTo     = replyTo,
                                mentionUids = mentionedUids,
                                onDone      = { inputText = ""; editTarget = null; replyTo = null; mentionedUids = emptyList() },
                            )
                        },
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
                            contentDescription = if (ku) "Bişîne" else "Gönder",
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
        val isPostOwner = myUid.isNotBlank() && myUid == post?.uid
        val canDelete = isOwner || isPostOwner
        AlertDialog(
            onDismissRequest = { menuTarget = null },
            containerColor   = HeftSurface,
            title = { Text(cmt.displayName, color = OnBackground, fontWeight = FontWeight.SemiBold, fontSize = 15.sp) },
            text  = { Text(cmt.text.take(100) + if (cmt.text.length > 100) "…" else "", color = Muted, fontSize = 13.sp) },
            confirmButton = {
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(
                        onClick  = {
                            replyTo = cmt; editTarget = null; menuTarget = null
                            focusRequester.requestFocus(); keyboardController?.show()
                        },
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

    // ── Silme onayı ──────────────────────────────────────────────────────────
    deleteTarget?.let { cmt ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            containerColor   = HeftSurface,
            title  = { Text(if (ku) "Şîrove Jê Bibe" else "Yorumu Sil", color = OnBackground, fontWeight = FontWeight.SemiBold) },
            text   = { Text(cmt.text.take(80), color = Muted, fontSize = 13.sp) },
            confirmButton = {
                TextButton(onClick = { vm.deleteComment(postId, cmt.id); deleteTarget = null }) {
                    Text(if (ku) "Jê bibe" else "Sil", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(if (ku) "Betal bike" else "İptal", color = Muted)
                }
            },
        )
    }

    // ── Beğeni listesi (gönderi) ──────────────────────────────────────────────
    if (showLikers) {
        LikerListSheet(
            likers    = likers,
            loading   = socialLoading,
            onDismiss = { showLikers = false; socialVm.clearLikers() },
            onProfile = { uid -> showLikers = false; navController.navigate("profile/$uid") },
        )
    }

    // ── Beğeni listesi (yorum) ────────────────────────────────────────────────
    if (cmtLikersId != null) {
        LikerListSheet(
            title     = if (ku) "Xweşandina Şîroveyê" else "Yorum Beğenenler",
            likers    = likers,
            loading   = socialLoading,
            onDismiss = { cmtLikersId = null; socialVm.clearLikers() },
            onProfile = { uid -> cmtLikersId = null; navController.navigate("profile/$uid") },
        )
    }
}

// ── Yorum gönderme yardımcısı ─────────────────────────────────────────────────
private fun submitComment(
    vm         : FeedViewModel,
    post       : Post,
    inputText  : String,
    editTarget : Comment?,
    replyTo    : Comment?,
    mentionUids: List<String> = emptyList(),
    onDone     : () -> Unit,
) {
    val text = inputText.trim()
    if (text.isBlank()) return
    if (editTarget != null) {
        vm.editComment(post.id, editTarget.id, text)
    } else {
        vm.addComment(post, text, replyTo, mentionUids)
    }
    onDone()
}

// ── Yorum + yanıtları grubu (Instagram tarzı thread gösterimi için) ────────────
private data class CommentThread(
    val parent  : Comment,
    val replies : List<Comment>,
)

// ── Yorum satırı ─────────────────────────────────────────────────────────────
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun SingleCommentRow(
    cmt            : Comment,
    myUid          : String,
    canEdit        : Boolean,
    canDelete      : Boolean,
    language       : String,
    onLikeClick    : () -> Unit,
    onLikersClick  : () -> Unit,
    onReply        : () -> Unit,
    onEdit         : () -> Unit,
    onDelete       : () -> Unit,
    onLongPress    : () -> Unit,
    onMentionClick : (uid: String) -> Unit,
    onHashtagClick : (postId: String) -> Unit = {},
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
                AsyncImage(
                    model            = cmt.photoURL,
                    contentDescription = null,
                    modifier         = Modifier.fillMaxSize(),
                    contentScale     = ContentScale.Crop,
                )
            } else {
                Text(
                    cmt.displayName.firstOrNull()?.uppercase() ?: "?",
                    color      = OnBackground,
                    fontSize   = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(cmt.displayName, color = OnBackground, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)

            // Reply göstergesi
            if (cmt.replyTo != null && cmt.replyTo.displayName.isNotBlank()) {
                Text("@${cmt.replyTo.displayName}", color = Amber, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.height(2.dp))

            // Yorum metni
            MentionText(
                text        = cmt.text,
                mentionUids = cmt.mentions,
                fontSize    = 14.sp,
                lineHeight  = 20.sp,
                color       = OnSurface,
                onMentionClick = onMentionClick,
                onHashtagClick = onHashtagClick,
            )

            // Aksiyon satırı
            Row(
                modifier              = Modifier.padding(top = 4.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Beğeni
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier          = Modifier.clickable { onLikeClick() },
                ) {
                    Icon(
                        if (cmt.isLikedByMe) Icons.Filled.Favorite else Icons.Filled.Favorite,
                        null,
                        tint     = if (cmt.isLikedByMe) Color(0xFFEF4444) else Muted,
                        modifier = Modifier.size(12.dp),
                    )
                    if (cmt.likesCount > 0) {
                        Spacer(Modifier.width(2.dp))
                        Text(
                            "${cmt.likesCount}",
                            color    = Muted,
                            fontSize = 11.sp,
                            modifier = Modifier.clickable { onLikersClick() },
                        )
                    }
                }

                Text(
                    Strings.replyAction(language),
                    color    = Muted,
                    fontSize = 11.sp,
                    modifier = Modifier.clickable { onReply() },
                )
                if (canEdit) {
                    Text(
                        Strings.editAction(language),
                        color    = Muted,
                        fontSize = 11.sp,
                        modifier = Modifier.clickable { onEdit() },
                    )
                }
                if (canDelete) {
                    Text(
                        Strings.deleteAction(language),
                        color    = Color(0xFFEF4444).copy(alpha = 0.7f),
                        fontSize = 11.sp,
                        modifier = Modifier.clickable { onDelete() },
                    )
                }
            }
        }
    }
}
