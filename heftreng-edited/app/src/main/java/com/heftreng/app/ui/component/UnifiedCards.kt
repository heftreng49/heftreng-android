package com.heftreng.app.ui.component

// ═══════════════════════════════════════════════════════════════════════════
//  UnifiedCards.kt  —  Evrensel Kart + Ortak Bileşen Sistemi [GÜNCELLENDİ]
// ═══════════════════════════════════════════════════════════════════════════

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.google.firebase.Timestamp
import com.heftreng.app.data.model.BookQuote
import com.heftreng.app.data.model.BookReview
import com.heftreng.app.data.model.LibraryBook
import com.heftreng.app.ui.theme.*
import com.heftreng.app.viewmodel.LibraryViewModel
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

// ─────────────────────────────────────────────────────────────────────────────
//  Aksiyon paketi — isteğe bağlı olarak ekranlardan geçirilir
// ─────────────────────────────────────────────────────────────────────────────

data class BookCardActions(
    val vm            : LibraryViewModel? = null,
    val feedVm        : com.heftreng.app.viewmodel.FeedViewModel? = null,
    val navController : NavController?    = null,
    val onComment     : (() -> Unit)?      = null,
)

// ─────────────────────────────────────────────────────────────────────────────
//  BookQuoteCard
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun BookQuoteCard(
    quote  : BookQuote,
    actions: BookCardActions = BookCardActions(),
    language: String = "tr",
) {
    val vm            = actions.vm
    val navController = actions.navController
    val myUid         = vm?.myUid ?: ""
    val isOwner       = myUid.isNotBlank() && myUid == quote.uid
    val isAdmin       = vm?.isAdmin ?: false
    val canEdit       = isOwner || isAdmin
    
    // ÇÖZÜLDÜ: 'likedBy' listesi yerine modeldeki boolean durum veya harici kontrol atandı
    val isLiked       = quote.isLikedByMe

    var menuExpanded     by remember { mutableStateOf(false) }
    var showEditDialog   by remember { mutableStateOf(false) }
    var showDeleteDialog   by remember { mutableStateOf(false) }
    var showRestrictDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Background)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        // ── Başlık: Avatar + İsim + Zaman + 3-nokta ──────────────────────
        Row(
            modifier          = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            UserAvatar(
                photoURL    = quote.userPhotoURL,
                displayName = quote.userDisplayName,
                size        = 38.dp,
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    quote.userDisplayName.ifBlank { "—" },
                    fontWeight = FontWeight.SemiBold,
                    color      = OnBackground,
                    fontSize   = 14.sp,
                )
                Text(
                    timeAgo(quote.ts),
                    color    = Muted,
                    fontSize = 12.sp,
                )
            }
            if (canEdit) {
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, null, tint = Muted)
                    }
                    DropdownMenu(
                        expanded         = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                        containerColor   = HeftSurface,
                    ) {
                        DropdownMenuItem(
                            text        = { Text(if (language == "ku") "Biguhere" else "Düzenle", color = OnBackground) },
                            leadingIcon = { Icon(Icons.Default.Edit, null, tint = Primary) },
                            onClick     = { menuExpanded = false; showEditDialog = true },
                        )
                        DropdownMenuItem(
                            text        = { Text(if (language == "ku") "Jê bibe" else "Sil", color = Color(0xFFEF4444)) },
                            leadingIcon = { Icon(Icons.Default.Delete, null, tint = Color(0xFFEF4444)) },
                            onClick     = { menuExpanded = false; showDeleteDialog = true },
                        )
                        if (isAdmin) {
                            DropdownMenuItem(
                                text        = { Text(if (language == "ku") "Sînordar bike" else "Kısıtla", color = Color(0xFFF59E0B)) },
                                leadingIcon = { Icon(Icons.Default.VisibilityOff, null, tint = Color(0xFFF59E0B)) },
                                onClick     = { menuExpanded = false; showRestrictDialog = true },
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // ── Alıntı içeriği ────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(SurfaceVar)
                .padding(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(2.dp))
                    .background(Amber)
            )
            Spacer(Modifier.width(10.dp))
            Column {
                if (quote.bookTitle.isNotBlank() || quote.authorName.isNotBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier          = Modifier.clickable(
                            enabled = quote.bookId.isNotBlank() || quote.authorId.isNotBlank()
                        ) {
                            if (quote.bookId.isNotBlank())
                                navController?.navigate("library_book_detail/${quote.bookId}")
                            else if (quote.authorId.isNotBlank())
                                navController?.navigate("author_detail/${quote.authorId}")
                        }
                    ) {
                        Icon(Icons.Default.AutoStories, null, tint = Amber, modifier = Modifier.size(12.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            buildString {
                                if (quote.bookTitle.isNotBlank()) append(quote.bookTitle)
                                if (quote.bookTitle.isNotBlank() && quote.authorName.isNotBlank()) append(" — ")
                                if (quote.authorName.isNotBlank()) append(quote.authorName)
                            },
                            color      = Amber,
                            fontSize   = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                }
                Text(
                    "❝ ${quote.text}",
                    color      = OnSurface,
                    fontSize   = 14.sp,
                    fontStyle  = FontStyle.Italic,
                    lineHeight = 21.sp,
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // ── Aksiyon çubuğu ────────────────────────────────────────────────
        Row(
            modifier          = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick  = { vm?.toggleLikeQuote(quote.bookId, quote.id) },
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    if (isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    null,
                    tint     = if (isLiked) Color(0xFFEF4444) else Muted,
                    modifier = Modifier.size(20.dp),
                )
            }
            if (quote.likesCount > 0)
                Text(quote.likesCount.toString(), color = Muted, fontSize = 13.sp)

            actions.onComment?.let { onComment ->
                Spacer(Modifier.width(4.dp))
                IconButton(onClick = onComment, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Outlined.ChatBubbleOutline, null, tint = Muted, modifier = Modifier.size(20.dp))
                }
            }

            Spacer(Modifier.weight(1f))

            if (quote.bookId.isNotBlank() && navController != null) {
                TextButton(
                    onClick      = { navController.navigate("library_book_detail/${quote.bookId}") },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                ) {
                    Icon(Icons.Outlined.MenuBook, null, tint = Primary, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        if (language == "ku") "Pirtûk" else "Kitap",
                        color    = Primary,
                        fontSize = 12.sp,
                    )
                }
            }
        }

        HorizontalDivider(color = Divider, thickness = 0.5.dp)
    }

    if (showEditDialog) {
        var editText by remember { mutableStateOf(quote.text) }
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            containerColor   = HeftSurface,
            title = {
                Text(
                    if (language == "ku") "Gotinê biguhere" else "Alıntıyı Düzenle",
                    color = OnBackground, fontWeight = FontWeight.Bold,
                )
            },
            text = {
                OutlinedTextField(
                    value         = editText,
                    onValueChange = { editText = it },
                    minLines      = 3,
                    modifier      = Modifier.fillMaxWidth(),
                    colors        = unifiedFieldColors(),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm?.editQuote(quote.bookId, quote.id, quote.uid, editText.trim())
                    showEditDialog = false
                }, enabled = editText.isNotBlank()) {
                    Text(if (language == "ku") "Tomar bike" else "Kaydet", color = Primary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) {
                    Text(if (language == "ku") "Betal bike" else "İptal", color = Muted)
                }
            },
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            containerColor   = HeftSurface,
            title = { Text(if (language == "ku") "Gotinê jê bibe" else "Alıntıyı Sil", color = OnBackground, fontWeight = FontWeight.Bold) },
            text  = { Text(if (language == "ku") "Tu dixwazî vê gotinê jê bibî?" else "Bu alıntıyı silmek istiyor musunuz?", color = Muted) },
            confirmButton = {
                TextButton(onClick = {
                    vm?.deleteQuote(quote.bookId, quote.id, quote.uid)
                    showDeleteDialog = false
                }) { Text(if (language == "ku") "Jê bibe" else "Sil", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(if (language == "ku") "Betal bike" else "İptal", color = Muted)
                }
            },
        )
    }

    if (showRestrictDialog && isAdmin) {
        VisibilityRestrictDialog(
            currentVisibility = quote.visibility,
            language          = language,
            onDismiss         = { showRestrictDialog = false },
            onApply           = { vis ->
                val postId = quote.feedPostId.ifBlank { quote.id }
                actions.feedVm?.setPostVisibility(postId, vis)
                showRestrictDialog = false
            },
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  BookReviewCard
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun BookReviewCard(
    review  : BookReview,
    actions : BookCardActions = BookCardActions(),
    language: String = "tr",
) {
    val vm            = actions.vm
    val navController = actions.navController
    val myUid         = vm?.myUid ?: ""
    val isOwner       = myUid.isNotBlank() && myUid == review.uid
    val isAdmin       = vm?.isAdmin ?: false
    val canEdit       = isOwner || isAdmin
    
    // ÇÖZÜLDÜ: 'likedBy' listesi yerine modeldeki boolean durum veya harici kontrol atandı
    val isLiked       = review.isLikedByMe

    var menuExpanded     by remember { mutableStateOf(false) }
    var showEditDialog   by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Background)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Row(
            modifier          = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            UserAvatar(
                photoURL    = review.userPhotoURL,
                displayName = review.userDisplayName,
                size        = 38.dp,
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    review.userDisplayName.ifBlank { "—" },
                    fontWeight = FontWeight.SemiBold,
                    color      = OnBackground,
                    fontSize   = 14.sp,
                )
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(timeAgo(review.ts), color = Muted, fontSize = 12.sp)
                    if (review.rating > 0) {
                        Text("·", color = Muted, fontSize = 12.sp)
                        StarRow(review.rating, size = 12.dp)
                    }
                }
            }
            if (canEdit) {
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, null, tint = Muted)
                    }
                    DropdownMenu(
                        expanded         = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                        containerColor   = HeftSurface,
                    ) {
                        DropdownMenuItem(
                            text        = { Text(if (language == "ku") "Biguhere" else "Düzenle", color = OnBackground) },
                            leadingIcon = { Icon(Icons.Default.Edit, null, tint = Primary) },
                            onClick     = { menuExpanded = false; showEditDialog = true },
                        )
                        DropdownMenuItem(
                            text        = { Text(if (language == "ku") "Jê bibe" else "Sil", color = Color(0xFFEF4444)) },
                            leadingIcon = { Icon(Icons.Default.Delete, null, tint = Color(0xFFEF4444)) },
                            onClick     = { menuExpanded = false; showDeleteDialog = true },
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        if (review.bookTitle.isNotBlank()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier          = Modifier
                    .clickable(enabled = review.bookId.isNotBlank()) {
                        navController?.navigate("library_book_detail/${review.bookId}")
                    }
                    .padding(bottom = 8.dp),
            ) {
                Icon(Icons.Outlined.AutoStories, null, tint = Primary, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(5.dp))
                Text(
                    review.bookTitle,
                    color      = Primary,
                    fontSize   = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines   = 1,
                    overflow   = TextOverflow.Ellipsis,
                )
                if (review.authorName.isNotBlank()) {
                    Text(" — ${review.authorName}", color = Muted, fontSize = 12.sp, maxLines = 1)
                }
            }
        }

        Text(
            review.text,
            color      = OnBackground,
            fontSize   = 14.sp,
            lineHeight = 22.sp,
        )

        Spacer(Modifier.height(8.dp))

        Row(
            modifier          = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick  = { vm?.toggleLikeReview(review.bookId, review.id) },
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    if (isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    null,
                    tint     = if (isLiked) Color(0xFFEF4444) else Muted,
                    modifier = Modifier.size(20.dp),
                )
            }
            if (review.likesCount > 0)
                Text(review.likesCount.toString(), color = Muted, fontSize = 13.sp)

            actions.onComment?.let { onComment ->
                Spacer(Modifier.width(4.dp))
                IconButton(onClick = onComment, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Outlined.ChatBubbleOutline, null, tint = Muted, modifier = Modifier.size(20.dp))
                }
            }

            Spacer(Modifier.weight(1f))

            if (review.bookId.isNotBlank() && navController != null) {
                TextButton(
                    onClick        = { navController.navigate("library_book_detail/${review.bookId}") },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                ) {
                    Icon(Icons.Outlined.MenuBook, null, tint = Primary, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        if (language == "ku") "Pirtûk" else "Kitap",
                        color    = Primary,
                        fontSize = 12.sp,
                    )
                }
            }
        }

        HorizontalDivider(color = Divider, thickness = 0.5.dp)
    }

    if (showEditDialog) {
        var editText   by remember { mutableStateOf(review.text) }
        var editRating by remember { mutableFloatStateOf(review.rating) }
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            containerColor   = HeftSurface,
            title = {
                Text(
                    if (language == "ku") "Nirxandinê biguhere" else "İncelemeyi Düzenle",
                    color = OnBackground, fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row {
                        repeat(5) { i ->
                            IconButton(onClick = { editRating = (i + 1).toFloat() }, modifier = Modifier.size(36.dp)) {
                                Icon(
                                    if (i < editRating.roundToInt()) Icons.Default.Star else Icons.Outlined.StarBorder,
                                    null, tint = Amber, modifier = Modifier.size(26.dp),
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value         = editText,
                        onValueChange = { editText = it },
                        minLines      = 3,
                        modifier      = Modifier.fillMaxWidth(),
                        colors        = unifiedFieldColors(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick  = {
                        vm?.editReview(review.bookId, review.id, review.uid, editText.trim(), editRating)
                        showEditDialog = false
                    },
                    enabled = editText.isNotBlank() && editRating > 0,
                ) {
                    Text(if (language == "ku") "Tomar bike" else "Kaydet", color = Primary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) {
                    Text(if (language == "ku") "Betal bike" else "İptal", color = Muted)
                }
            },
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            containerColor   = HeftSurface,
            title = { Text(if (language == "ku") "Nirxandinê jê bibe" else "Nirxandinê Sil", color = OnBackground, fontWeight = FontWeight.Bold) },
            text  = { Text(if (language == "ku") "Tu dixwazî vê nirxandinê jê bibî?" else "Bu incelemeyi silmek istiyor musunuz?", color = Muted) },
            confirmButton = {
                TextButton(onClick = {
                    vm?.deleteReview(review.bookId, review.id, review.uid)
                    showDeleteDialog = false
                }) { Text(if (language == "ku") "Jê bibe" else "Sil", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(if (language == "ku") "Betal bike" else "İptal", color = Muted)
                }
            },
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  EmptyState — tüm kütüphane ekranlarında ortak boş durum gösterimi
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun EmptyState(
    icon    : ImageVector,
    message : String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier         = modifier
            .fillMaxWidth()
            .padding(vertical = 56.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector        = icon,
                contentDescription = null,
                tint               = Muted,
                modifier           = Modifier.size(56.dp),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text      = message,
                color     = Muted,
                fontSize  = 15.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                lineHeight = 22.sp,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  LibraryBookCard — kitap listesi satırı
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun LibraryBookCard(
    book    : LibraryBook,
    onClick : () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier  = modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape     = RoundedCornerShape(14.dp),
        colors    = CardDefaults.cardColors(containerColor = HeftSurface),
        elevation = CardDefaults.cardElevation(1.dp),
    ) {
        Row(modifier = Modifier.padding(12.dp)) {
            Box(
                modifier = Modifier
                    .width(54.dp)
                    .height(76.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(SurfaceVar),
                contentAlignment = Alignment.Center,
            ) {
                if (book.coverImg.isNotBlank()) {
                    AsyncImage(
                        model             = book.coverImg,
                        contentDescription = book.title,
                        contentScale      = ContentScale.Crop,
                        modifier          = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(
                        Icons.Default.AutoStories,
                        contentDescription = null,
                        tint     = Muted,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    book.title,
                    color      = OnBackground,
                    fontSize   = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines   = 2,
                    overflow   = TextOverflow.Ellipsis,
                )
                if (book.authorName.isNotBlank()) {
                    Text(
                        book.authorName,
                        color    = Primary,
                        fontSize = 13.sp,
                        maxLines = 1,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (book.avgRating > 0f) StarRow(book.avgRating, size = 13.dp)
                    if (book.quoteCount > 0) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.FormatQuote, null, tint = Muted, modifier = Modifier.size(12.dp))
                            Spacer(Modifier.width(3.dp))
                            Text("${book.quoteCount}", color = Muted, fontSize = 11.sp)
                        }
                    }
                }
                if (book.genre.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(book.genre, color = Muted, fontSize = 11.sp)
                }
            }

            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint     = Muted,
                modifier = Modifier
                    .size(20.dp)
                    .align(Alignment.CenterVertically),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  AddReviewDialog — yıldız + metin ile inceleme ekleme
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun AddReviewDialog(
    bookTitle: String,
    language : String = "tr",
    onDismiss: () -> Unit,
    onSubmit : (text: String, rating: Float) -> Unit,
) {
    var text   by remember { mutableStateOf("") }
    var rating by remember { mutableFloatStateOf(0f) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = HeftSurface,
        title = {
            Text(
                if (language == "ku") "Nirxandinê binivîse" else "İnceleme Yaz",
                color = OnBackground, fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(bookTitle, color = Primary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    if (language == "ku") "Xalê bide:" else "Puan ver:",
                    color = Muted, fontSize = 12.sp,
                )
                Row {
                    repeat(5) { i ->
                        IconButton(
                            onClick  = { rating = (i + 1).toFloat() },
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(
                                if (i < rating.roundToInt()) Icons.Default.Star
                                else Icons.Outlined.StarBorder,
                                contentDescription = null,
                                tint     = Amber,
                                modifier = Modifier.size(28.dp),
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value         = text,
                    onValueChange = { text = it },
                    placeholder   = {
                        Text(
                            if (language == "ku") "Nirxandina xwe binivîse…" else "İncelemenizi yazın…",
                            color = Muted,
                        )
                    },
                    minLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                    colors   = unifiedFieldColors(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick  = { if (text.isNotBlank() && rating > 0) onSubmit(text.trim(), rating) },
                enabled  = text.isNotBlank() && rating > 0,
            ) {
                Text(
                    if (language == "ku") "Parve bike" else "Paylaş",
                    color = Primary, fontWeight = FontWeight.Bold,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    if (language == "ku") "Betal bike" else "İptal",
                    color = Muted,
                )
            }
        },
    )
}

// ─────────────────────────────────────────────────────────────────────────────
//  BookPickerDialog — inceleme FAB'ı için kitap seçici
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun BookPickerDialog(
    books    : List<LibraryBook>,
    language : String = "tr",
    onDismiss: () -> Unit,
    onSelect : (LibraryBook) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = HeftSurface,
        title = {
            Text(
                if (language == "ku") "Pirtûkê hilbijêre" else "Kitap Seçin",
                color = OnBackground, fontWeight = FontWeight.Bold,
            )
        },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                items(books, key = { it.id }) { book ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(book) }
                            .padding(vertical = 12.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Outlined.AutoStories,
                            contentDescription = null,
                            tint     = Primary,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                book.title,
                                color      = OnBackground,
                                fontSize   = 14.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines   = 1,
                                overflow   = TextOverflow.Ellipsis,
                            )
                            if (book.authorName.isNotBlank())
                                Text(book.authorName, color = Muted, fontSize = 12.sp)
                        }
                    }
                    HorizontalDivider(color = Divider, thickness = 0.5.dp)
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    if (language == "ku") "Betal bike" else "İptal",
                    color = Muted,
                )
            }
        },
    )
}

// ─────────────────────────────────────────────────────────────────────────────
//  Ortak yardımcı composable'lar
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun UserAvatar(
    photoURL    : String,
    displayName : String,
    size        : androidx.compose.ui.unit.Dp = 40.dp,
) {
    Box(
        modifier         = Modifier.size(size).clip(CircleShape).background(SurfaceVar),
        contentAlignment = Alignment.Center,
    ) {
        if (photoURL.isNotBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(photoURL).crossfade(true).build(),
                contentDescription = displayName,
                modifier           = Modifier.fillMaxSize(),
                contentScale       = ContentScale.Crop,
            )
        } else {
            Text(
                displayName.firstOrNull()?.uppercase() ?: "?",
                color      = OnBackground,
                fontWeight = FontWeight.Bold,
                fontSize   = (size.value * 0.35f).sp,
            )
        }
    }
}

@Composable
fun StarRow(
    rating  : Float,
    size    : androidx.compose.ui.unit.Dp = 14.dp,
    active  : Color = Amber,
    inactive: Color = Muted,
) {
    val full = rating.roundToInt().coerceIn(0, 5)
    Row {
        repeat(5) { i ->
            Icon(
                if (i < full) Icons.Filled.Star else Icons.Outlined.StarBorder,
                null,
                tint     = if (i < full) active else inactive,
                modifier = Modifier.size(size),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Zaman formatı
// ─────────────────────────────────────────────────────────────────────────────

fun timeAgo(ts: Timestamp?): String {
    ts ?: return ""
    val diffMs = System.currentTimeMillis() - ts.toDate().time
    val mins   = TimeUnit.MILLISECONDS.toMinutes(diffMs)
    val hours  = TimeUnit.MILLISECONDS.toHours(diffMs)
    val days   = TimeUnit.MILLISECONDS.toDays(diffMs)
    return when {
        mins  < 1   -> "şimdi"
        mins  < 60  -> "${mins}d"
        hours < 24  -> "${hours}s"
        days  < 7   -> "${days}g"
        days  < 30  -> "${days / 7}h"
        days  < 365 -> "${days / 30}ay"
        else        -> "${days / 365}y"
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  TextField renkleri (dialog içi)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun unifiedFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor   = Primary,
    unfocusedBorderColor = Divider,
    focusedTextColor     = OnBackground,
    unfocusedTextColor   = OnBackground,
    cursorColor          = Primary,
)

// ─────────────────────────────────────────────────────────────────────────────
//  Admin Görünürlük Kısıtlama Dialog'u
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun VisibilityRestrictDialog(
    currentVisibility: String = "public",
    language         : String = "tr",
    onDismiss        : () -> Unit,
    onApply          : (String) -> Unit,
) {
    var selected by remember { mutableStateOf(currentVisibility) }

    val options = listOf(
        Triple("public",   Icons.Default.Public,        if (language == "ku") "Herkesî" else "Herkese açık"),
        Triple("friends",  Icons.Default.Group,         if (language == "ku") "Tenê hevalên" else "Sadece takipçiler"),
        Triple("only_me",  Icons.Default.VisibilityOff, if (language == "ku") "Tenê ez" else "Sadece sahibi"),
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = HeftSurface,
        title = {
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Default.AdminPanelSettings, null, tint = Color(0xFFF59E0B), modifier = Modifier.size(20.dp))
                Text(
                    if (language == "ku") "Sînordarkirina Nîşandanê" else "Görünürlük Kısıtla",
                    color = OnBackground, fontWeight = FontWeight.Bold,
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    if (language == "ku") "Vê postê kî bibîne?" else "Bu gönderiyi kim görebilecek?",
                    color = Muted, fontSize = 13.sp,
                )
                Spacer(Modifier.height(4.dp))
                options.forEach { (value, icon, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (selected == value) Primary.copy(alpha = 0.1f) else Color.Transparent)
                            .clickable { selected = value }
                            .padding(12.dp),
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(icon, null, tint = if (selected == value) Primary else Muted, modifier = Modifier.size(20.dp))
                        Text(
                            label,
                            color      = OnBackground,
                            fontWeight = if (selected == value) FontWeight.SemiBold else FontWeight.Normal,
                            fontSize   = 14.sp,
                            modifier   = Modifier.weight(1f),
                        )
                        if (selected == value) {
                            Icon(Icons.Default.CheckCircle, null, tint = Primary, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onApply(selected) }) {
                Text(
                    if (language == "ku") "Bicîh bike" else "Uygula",
                    color = Color(0xFFF59E0B), fontWeight = FontWeight.Bold,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(if (language == "ku") "Betal bike" else "İptal", color = Muted)
            }
        },
    )
}
