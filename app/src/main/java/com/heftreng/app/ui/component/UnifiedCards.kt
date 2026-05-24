package com.heftreng.app.ui.component

// ═══════════════════════════════════════════════════════════════════════════
//  UnifiedCards.kt  —  Evrensel Kart Sistemi
//
//  Tüm ekranlarda (Feed, Profil, Kütüphane, Yazar/Kitap Detay) aynı
//  BookQuoteCard ve BookReviewCard kullanılır. PostCard tasarım diliyle
//  tutarlıdır:
//   • Kullanıcı başlığı (avatar + isim + @kullanıcı + zaman)
//   • İçerik alanı
//   • Kitap/yazar referansı (tıklanabilir)
//   • Aksiyon çubuğu: beğeni · yorum (opsiyonel) · paylaş · kaydet (opsiyonel)
//   • 3-nokta menü: düzenle / sil (sahip/admin)
//
//  KULLANIM:
//    BookQuoteCard(quote = q, actions = BookCardActions(vm = vm, navController = nav))
//    BookReviewCard(review = r, actions = BookCardActions(vm = vm, navController = nav))
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
import com.heftreng.app.ui.theme.*
import com.heftreng.app.viewmodel.LibraryViewModel
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

// ─────────────────────────────────────────────────────────────────────────────
//  Aksiyon paketi — isteğe bağlı olarak ekranlardan geçirilir
// ─────────────────────────────────────────────────────────────────────────────

data class BookCardActions(
    val vm           : LibraryViewModel? = null,
    val navController: NavController?    = null,
    val onComment    : (() -> Unit)?      = null,   // şimdilik feed yorumları farklı
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
    val isLiked       = myUid in quote.likedBy

    var menuExpanded     by remember { mutableStateOf(false) }
    var showEditDialog   by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

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
            // Avatar
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
            // 3-nokta menü (sahip/admin için)
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
                // Kitap / Yazar referansı
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
                // Alıntı metni
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
            // Beğeni
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

            // Yorum (opsiyonel)
            actions.onComment?.let { onComment ->
                Spacer(Modifier.width(4.dp))
                IconButton(onClick = onComment, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Outlined.ChatBubbleOutline, null, tint = Muted, modifier = Modifier.size(20.dp))
                }
            }

            Spacer(Modifier.weight(1f))

            // Kitap sayfasına git (eğer bookId varsa)
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

    // ── Düzenle Dialog ─────────────────────────────────────────────────
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

    // ── Sil Dialog ─────────────────────────────────────────────────────
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
    val isLiked       = myUid in review.likedBy

    var menuExpanded     by remember { mutableStateOf(false) }
    var showEditDialog   by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Background)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        // ── Başlık ────────────────────────────────────────────────────────
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

        // ── Kitap referansı ───────────────────────────────────────────────
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

        // ── İnceleme metni ────────────────────────────────────────────────
        Text(
            review.text,
            color      = OnBackground,
            fontSize   = 14.sp,
            lineHeight = 22.sp,
        )

        Spacer(Modifier.height(8.dp))

        // ── Aksiyon çubuğu ────────────────────────────────────────────────
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

    // ── Düzenle Dialog ─────────────────────────────────────────────────
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

    // ── Sil Dialog ─────────────────────────────────────────────────────
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            containerColor   = HeftSurface,
            title = { Text(if (language == "ku") "Nirxandinê jê bibe" else "İncelemeyi Sil", color = OnBackground, fontWeight = FontWeight.Bold) },
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
    rating : Float,
    size   : androidx.compose.ui.unit.Dp = 14.dp,
    active : Color = Amber,
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
