package com.heftreng.app.ui.screens.quotes

// ═══════════════════════════════════════════════════════════════
//  AuthorQuotesScreen + BookQuotesScreen
//
//  Site: /p/yazar.html?q=YazarAdı  → feed.where("quote.author","==",q)
//  Site: /p/kitap.html?q=KitapAdı  → feed.where("quote.book","==",q)
// ═══════════════════════════════════════════════════════════════

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.heftreng.app.data.model.Post
import com.heftreng.app.ui.theme.*
import kotlinx.coroutines.tasks.await

// ── Yazar Alıntıları ─────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthorQuotesScreen(
    authorName : String,
    onBack     : () -> Unit,
) {
    QuoteListPage(
        title    = authorName,
        subtitle = "Yazar Alıntıları",
        icon     = Icons.Default.Person,
        field    = "quote.author",
        flatField= "authorName",
        value    = authorName,
        onBack   = onBack,
    )
}

// ── Kitap Alıntıları ─────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookQuotesScreen(
    bookName : String,
    onBack   : () -> Unit,
) {
    QuoteListPage(
        title    = bookName,
        subtitle = "Kitap Alıntıları",
        icon     = Icons.Default.AutoStories,
        field    = "quote.book",
        flatField= "bookName",
        value    = bookName,
        onBack   = onBack,
    )
}

// ── Ortak liste sayfası ──────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuoteListPage(
    title    : String,
    subtitle : String,
    icon     : androidx.compose.ui.graphics.vector.ImageVector,
    field    : String,      // "quote.author" veya "quote.book"
    flatField: String,      // "authorName" veya "bookName"
    value    : String,
    onBack   : () -> Unit,
) {
    val db      = remember { FirebaseFirestore.getInstance() }
    var posts   by remember { mutableStateOf<List<Post>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    // Hem nested (quote.author) hem flat (authorName) alanlardan yükle
    LaunchedEffect(value) {
        loading = true
        try {
            val snap1 = db.collection("feed")
                .whereEqualTo(field, value)
                .limit(50).get().await()

            val snap2 = db.collection("feed")
                .whereEqualTo(flatField, value)
                .limit(50).get().await()

            val all = (snap1.documents + snap2.documents)
                .distinctBy { it.id }
                .sortedByDescending {
                    (it.data?.get("ts") as? com.google.firebase.Timestamp)?.seconds ?: 0L
                }

            posts = all.mapNotNull { doc ->
                val d = doc.data ?: return@mapNotNull null
                val quoteObj   = d["quote"] as? Map<*, *>
                val quoteText  = (quoteObj?.get("text") as? String)?.takeIf { it.isNotBlank() }
                                 ?: d["quoteText"] as? String ?: ""
                if (quoteText.isBlank()) return@mapNotNull null
                Post(
                    id          = doc.id,
                    uid         = d["uid"]          as? String ?: "",
                    displayName = (d["name"]        as? String)?.takeIf { it.isNotBlank() }
                                 ?: d["displayName"] as? String ?: "",
                    photoURL    = d["photoURL"]     as? String ?: "",
                    quoteText   = quoteText,
                    bookName    = (quoteObj?.get("book") as? String)?.takeIf { it.isNotBlank() }
                                 ?: d["bookName"] as? String ?: "",
                    authorName  = (quoteObj?.get("author") as? String)?.takeIf { it.isNotBlank() }
                                 ?: d["authorName"] as? String ?: "",
                    ts          = d["ts"] as? com.google.firebase.Timestamp,
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            loading = false
        }
    }

    Scaffold(
        containerColor = Background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(title, fontWeight = FontWeight.Bold, color = OnBackground, fontSize = 16.sp)
                        Text(subtitle, color = Primary, fontSize = 11.sp)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = OnBackground)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Background),
            )
        }
    ) { padding ->
        when {
            loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Primary)
            }
            posts.isEmpty() -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Default.FormatQuote, null, tint = Divider, modifier = Modifier.size(56.dp))
                    Text("Henüz alıntı yok", color = Muted, fontSize = 14.sp)
                }
            }
            else -> LazyColumn(
                modifier       = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(posts, key = { it.id }) { post ->
                    QuoteItemCard(post = post)
                }
            }
        }
    }
}

// ── Alıntı kart bileşeni — site: .quote-card yapısıyla uyumlu ────────────────
@Composable
private fun QuoteItemCard(post: Post) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(14.dp),
        color    = HeftSurface,
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Sol şerit + alıntı metni
            Row {
                Box(
                    Modifier
                        .width(3.dp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(2.dp))
                        .background(Primary)
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "❝ ${post.quoteText}",
                        color      = OnSurface,
                        fontSize   = 14.sp,
                        fontStyle  = FontStyle.Italic,
                        lineHeight = 22.sp,
                    )
                }
            }

            // Kitap/Yazar meta
            if (post.bookName.isNotBlank() || post.authorName.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AutoStories, null, tint = Amber, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(4.dp))
                    if (post.bookName.isNotBlank()) {
                        Text(post.bookName, color = Amber, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                    if (post.bookName.isNotBlank() && post.authorName.isNotBlank()) {
                        Text(" — ", color = Muted, fontSize = 11.sp)
                    }
                    if (post.authorName.isNotBlank()) {
                        Text(post.authorName, color = Muted, fontSize = 11.sp)
                    }
                }
            }

            HorizontalDivider(color = Divider, modifier = Modifier.padding(vertical = 10.dp))

            // Paylaşan kullanıcı
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(SurfaceVar),
                    contentAlignment = Alignment.Center,
                ) {
                    if (post.photoURL.isNotBlank()) {
                        AsyncImage(
                            model        = post.photoURL,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier     = Modifier.fillMaxSize(),
                        )
                    } else {
                        Text(
                            post.displayName.firstOrNull()?.uppercase() ?: "?",
                            color      = OnBackground,
                            fontSize   = 11.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    post.displayName.ifBlank { "Kullanıcı" },
                    color      = Muted,
                    fontSize   = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}
