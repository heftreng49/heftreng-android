package com.heftreng.app.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.ui.unit.offset
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.offset
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.heftreng.app.ui.theme.*
import kotlinx.coroutines.tasks.await

data class QuotePayload(
    val text      : String = "",
    val authorName: String = "",
    val bookName  : String = "",
    val coverImg  : String = "",
    val postId    : String = "",
)

// ── Öneri modeli ──────────────────────────────────────────────────────────────
data class QuoteSuggestion(
    val bookName  : String = "",
    val authorName: String = "",
    val coverImg  : String = "",
    val count     : Int    = 0,
)

// ── Gönderi kartında alıntı gösterimi ─────────────────────────────────────────
@Composable
fun QuoteCard(
    quoteText   : String,
    bookName    : String = "",
    authorName  : String = "",
    coverImg    : String = "",
    onTapBook   : ((String) -> Unit)? = null,
    onTapAuthor : ((String) -> Unit)? = null,
    modifier    : Modifier = Modifier,
) {
    if (quoteText.isBlank()) return
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(
                brush = androidx.compose.ui.graphics.Brush.linearGradient(
                    listOf(
                        Amber.copy(alpha = 0.08f),
                        androidx.compose.ui.graphics.Color(0xFF9B72F5).copy(alpha = 0.06f),
                    )
                )
            )
            .border(
                width = 1.dp,
                brush = androidx.compose.ui.graphics.Brush.linearGradient(
                    listOf(Amber.copy(alpha = 0.4f), androidx.compose.ui.graphics.Color(0xFF9B72F5).copy(alpha = 0.3f))
                ),
                shape = RoundedCornerShape(14.dp),
            )
            .padding(14.dp),
    ) {
        Text(
            "\u201c",
            fontSize = 56.sp,
            color    = Amber.copy(alpha = 0.12f),
            fontWeight = FontWeight.Black,
            modifier = Modifier.align(Alignment.TopStart).offset(x = (-4).dp, y = (-10).dp),
        )
        Column(modifier = Modifier.padding(start = 8.dp)) {
            Text(
                "${quoteText.take(300)}${if (quoteText.length > 300) "…" else ""}",
                color      = OnSurface,
                fontSize   = 14.sp,
                fontStyle  = FontStyle.Italic,
                lineHeight = 22.sp,
                fontWeight = FontWeight.Medium,
            )
            if (bookName.isNotBlank() || authorName.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .width(28.dp)
                            .height(42.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(Amber.copy(alpha = 0.10f))
                            .then(
                                if (onTapBook != null && bookName.isNotBlank())
                                    Modifier.clickable { onTapBook(bookName) }
                                else Modifier
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (coverImg.isNotBlank()) {
                            coil.compose.AsyncImage(
                                model              = coverImg,
                                contentDescription = bookName,
                                contentScale       = androidx.compose.ui.layout.ContentScale.Crop,
                                modifier           = Modifier.fillMaxSize(),
                            )
                        } else {
                            Icon(Icons.Default.AutoStories, null, tint = Amber, modifier = Modifier.size(14.dp))
                        }
                    }
                    Column {
                        if (bookName.isNotBlank()) {
                            Text(
                                bookName,
                                color      = Amber,
                                fontSize   = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines   = 1,
                                overflow   = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                modifier   = if (onTapBook != null) Modifier.clickable { onTapBook(bookName) } else Modifier,
                            )
                        }
                        if (authorName.isNotBlank()) {
                            Text(
                                authorName,
                                color    = Muted,
                                fontSize = 10.sp,
                                maxLines = 1,
                                modifier = if (onTapAuthor != null) Modifier.clickable { onTapAuthor(authorName) } else Modifier,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Compose alanında seçili alıntı + kaldır ───────────────────────────────────
@Composable
fun QuoteInputSection(quote: QuotePayload?, onRemove: () -> Unit, modifier: Modifier = Modifier) {
    if (quote == null) return
    Box(modifier = modifier) {
        QuoteCard(quoteText = quote.text, bookName = quote.bookName, authorName = quote.authorName)
        IconButton(onClick = onRemove, modifier = Modifier.align(Alignment.TopEnd).size(28.dp)) {
            Icon(Icons.Default.Close, null, tint = Muted, modifier = Modifier.size(16.dp))
        }
    }
}

// ── Alıntı butonu ─────────────────────────────────────────────────────────────
@Composable
fun QuoteButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(onClick = onClick, modifier = modifier.size(36.dp)) {
        Icon(Icons.Default.FormatQuote, null, tint = Muted, modifier = Modifier.size(20.dp))
    }
}

// ── Tam ekran alıntı oluşturma ekranı ────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuoteDialog(
    initialText    : String = "",
    initialBook    : String = "",
    initialAuthor  : String = "",
    onDismiss      : () -> Unit,
    onConfirm      : (QuotePayload) -> Unit,
    onLookupCover  : (suspend (String) -> String)? = null,
) {
    var text     by remember { mutableStateOf(initialText) }
    var book     by remember { mutableStateOf(initialBook) }
    var author   by remember { mutableStateOf(initialAuthor) }
    var coverImg by remember { mutableStateOf("") }

    val db  = remember { FirebaseFirestore.getInstance() }
    val uid = remember { FirebaseAuth.getInstance().currentUser?.uid ?: "" }

    var bookSuggestions   by remember { mutableStateOf<List<QuoteSuggestion>>(emptyList()) }
    var authorSuggestions by remember { mutableStateOf<List<QuoteSuggestion>>(emptyList()) }
    var showBookDrop      by remember { mutableStateOf(false) }
    var showAuthorDrop    by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        try {
            val newSnap = db.collection("feed")
                .whereEqualTo("type", "library_quote")
                .limit(50).get().await()
            val oldSnap = db.collection("feed")
                .whereEqualTo("type", "quote")
                .limit(50).get().await()
            val cgSnap = try {
                db.collectionGroup("quotes").limit(50).get().await()
            } catch (_: Exception) { null }

            val bookMap   = mutableMapOf<String, QuoteSuggestion>()
            val authorMap = mutableMapOf<String, QuoteSuggestion>()

            fun processEntry(bName: String, aName: String, cover: String = "") {
                if (bName.isBlank()) return
                val bKey = bName.lowercase().trim()
                val aKey = aName.lowercase().trim()
                val cur  = bookMap[bKey]
                bookMap[bKey] = QuoteSuggestion(
                    bookName   = bName.trim(),
                    authorName = cur?.authorName?.ifBlank { aName.trim() } ?: aName.trim(),
                    coverImg   = cur?.coverImg?.ifBlank { cover } ?: cover,
                    count      = (cur?.count ?: 0) + 1,
                )
                if (aName.isNotBlank()) {
                    val curA = authorMap[aKey]
                    authorMap[aKey] = QuoteSuggestion(
                        bookName   = curA?.bookName?.ifBlank { bName.trim() } ?: bName.trim(),
                        authorName = aName.trim(),
                        count      = (curA?.count ?: 0) + 1,
                    )
                }
            }

            newSnap.documents.forEach { doc ->
                processEntry(
                    doc.getString("bookName")?.trim() ?: "",
                    doc.getString("authorName")?.trim() ?: "",
                )
            }
            oldSnap.documents.forEach { doc ->
                val qObj  = doc.get("quote") as? Map<*, *>
                processEntry(
                    (qObj?.get("book") as? String)?.trim() ?: doc.getString("bookName")?.trim() ?: "",
                    (qObj?.get("author") as? String)?.trim() ?: doc.getString("authorName")?.trim() ?: "",
                )
            }
            cgSnap?.documents?.forEach { doc ->
                processEntry(
                    (doc.getString("bookTitle") ?: doc.getString("bookName") ?: "").trim(),
                    (doc.getString("authorName") ?: "").trim(),
                    (doc.getString("coverImg") ?: doc.getString("cover_img") ?: "").trim(),
                )
            }

            bookSuggestions   = bookMap.values.sortedByDescending { it.count }
            authorSuggestions = authorMap.values.sortedByDescending { it.count }
        } catch (_: Exception) {}
    }

    LaunchedEffect(book) {
        if (book.isBlank()) { showBookDrop = false; return@LaunchedEffect }
        showBookDrop = bookSuggestions.any { it.bookName.contains(book, ignoreCase = true) }
    }

    LaunchedEffect(book, coverImg) {
        if (book.isBlank() || coverImg.isNotBlank() || onLookupCover == null) return@LaunchedEffect
        if (showBookDrop) return@LaunchedEffect
        val fetched = onLookupCover(book)
        if (fetched.isNotBlank()) coverImg = fetched
    }

    LaunchedEffect(author) {
        if (author.isBlank()) { showAuthorDrop = false; return@LaunchedEffect }
        showAuthorDrop = authorSuggestions.any { it.authorName.contains(author, ignoreCase = true) }
    }

    val canConfirm = text.isNotBlank() || book.isNotBlank()

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows  = false,
        ),
    ) {
    Scaffold(
        containerColor = Background,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = OnBackground)
                    }
                },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.FormatQuote, null, tint = Primary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Alıntı Ekle", color = OnBackground, fontWeight = FontWeight.Bold)
                    }
                },
                actions = {
                    TextButton(
                        onClick  = {
                            if (canConfirm) onConfirm(QuotePayload(text = text, bookName = book, authorName = author, coverImg = coverImg))
                        },
                        enabled  = canConfirm,
                    ) {
                        Text(
                            "Paylaş",
                            color      = if (canConfirm) Primary else Muted,
                            fontWeight = FontWeight.Bold,
                            fontSize   = 15.sp,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Background),
            )
        }
    ) { padding ->
        LazyColumn(
            modifier       = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp),
        ) {

            // ── Alıntı metni ──────────────────────────────────────────────
            item {
                OutlinedTextField(
                    value         = text,
                    onValueChange = { text = it },
                    label         = { Text("ALINTI METNİ *") },
                    minLines      = 5,
                    modifier      = Modifier.fillMaxWidth(),
                    colors        = quoteTextFieldColors(),
                )
            }

            // ── Kitap adı + autocomplete ───────────────────────────────────
            item {
                Box {
                    OutlinedTextField(
                        value         = book,
                        onValueChange = {
                            book = it
                            showBookDrop = it.isNotBlank() &&
                                bookSuggestions.any { s -> s.bookName.contains(it, ignoreCase = true) }
                        },
                        label      = { Text("KİTAP ADI") },
                        singleLine = true,
                        modifier   = Modifier.fillMaxWidth(),
                        colors     = quoteTextFieldColors(),
                        leadingIcon = {
                            Icon(Icons.Default.AutoStories, null, tint = Muted, modifier = Modifier.size(18.dp))
                        },
                        trailingIcon = if (book.isNotBlank()) {{
                            IconButton(onClick = { book = ""; showBookDrop = false }) {
                                Icon(Icons.Default.Close, null, tint = Muted, modifier = Modifier.size(16.dp))
                            }
                        }} else null,
                    )
                    if (showBookDrop) {
                        Surface(
                            modifier       = Modifier.fillMaxWidth(),
                            shape          = RoundedCornerShape(10.dp),
                            color          = HeftSurface,
                            border         = androidx.compose.foundation.BorderStroke(1.dp, Divider),
                            tonalElevation = 4.dp,
                        ) {
                            LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
                                val filtered = bookSuggestions.filter {
                                    it.bookName.contains(book, ignoreCase = true)
                                }
                                items(filtered) { s ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                book           = s.bookName
                                                if (s.authorName.isNotBlank()) author = s.authorName
                                                coverImg       = s.coverImg
                                                showBookDrop   = false
                                                showAuthorDrop = false
                                            }
                                            .padding(horizontal = 14.dp, vertical = 10.dp),
                                        verticalAlignment     = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Icon(Icons.Default.AutoStories, null, tint = Amber, modifier = Modifier.size(14.dp))
                                        Column {
                                            Text(s.bookName, color = OnBackground, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                            if (s.authorName.isNotBlank())
                                                Text(s.authorName, color = Muted, fontSize = 11.sp)
                                        }
                                        Spacer(Modifier.weight(1f))
                                        Text("${s.count} alıntı", color = Muted, fontSize = 10.sp)
                                    }
                                    HorizontalDivider(color = Divider, thickness = 0.5.dp)
                                }
                            }
                        }
                    }
                }
            }

            // ── Yazar + autocomplete ───────────────────────────────────────
            item {
                Box {
                    OutlinedTextField(
                        value         = author,
                        onValueChange = {
                            author = it
                            showAuthorDrop = it.isNotBlank() &&
                                authorSuggestions.any { s -> s.authorName.contains(it, ignoreCase = true) }
                        },
                        label      = { Text("YAZAR") },
                        singleLine = true,
                        modifier   = Modifier.fillMaxWidth(),
                        colors     = quoteTextFieldColors(),
                        leadingIcon = {
                            Icon(Icons.Default.Person, null, tint = Muted, modifier = Modifier.size(18.dp))
                        },
                        trailingIcon = if (author.isNotBlank()) {{
                            IconButton(onClick = { author = ""; showAuthorDrop = false }) {
                                Icon(Icons.Default.Close, null, tint = Muted, modifier = Modifier.size(16.dp))
                            }
                        }} else null,
                    )
                    if (showAuthorDrop) {
                        val filtered = authorSuggestions.filter {
                            it.authorName.contains(author, ignoreCase = true)
                        }.take(8)
                        if (filtered.isNotEmpty()) {
                            Surface(
                                modifier       = Modifier.fillMaxWidth(),
                                shape          = RoundedCornerShape(10.dp),
                                color          = HeftSurface,
                                border         = androidx.compose.foundation.BorderStroke(1.dp, Divider),
                                tonalElevation = 4.dp,
                            ) {
                                LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
                                    items(filtered) { s ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    author = s.authorName
                                                    if (book.isBlank() && s.bookName.isNotBlank())
                                                        book = s.bookName
                                                    showAuthorDrop = false
                                                    showBookDrop   = false
                                                }
                                                .padding(horizontal = 14.dp, vertical = 10.dp),
                                            verticalAlignment     = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            Icon(Icons.Default.Person, null, tint = Primary, modifier = Modifier.size(14.dp))
                                            Column(Modifier.weight(1f)) {
                                                Text(s.authorName, color = OnBackground, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                                if (s.bookName.isNotBlank())
                                                    Text(s.bookName, color = Muted, fontSize = 11.sp)
                                            }
                                            Text("${s.count} alıntı", color = Muted, fontSize = 10.sp)
                                        }
                                        HorizontalDivider(color = Divider, thickness = 0.5.dp)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ── Önizleme ──────────────────────────────────────────────────
            if (text.isNotBlank() || book.isNotBlank() || author.isNotBlank()) {
                item {
                    HorizontalDivider(color = Divider, thickness = 0.5.dp)
                    Spacer(Modifier.height(4.dp))
                    Text("Önizleme", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    QuoteCard(
                        quoteText  = text.ifBlank { "…" },
                        bookName   = book,
                        authorName = author,
                    )
                }
            }
        }
    }
    } // Dialog
}

@Composable
private fun quoteTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor      = Primary,
    unfocusedBorderColor    = Divider,
    focusedTextColor        = OnBackground,
    unfocusedTextColor      = OnBackground,
    unfocusedContainerColor = SurfaceVar,
    focusedContainerColor   = SurfaceVar,
    focusedLabelColor       = Primary,
    unfocusedLabelColor     = Muted,
    cursorColor             = Primary,
)
