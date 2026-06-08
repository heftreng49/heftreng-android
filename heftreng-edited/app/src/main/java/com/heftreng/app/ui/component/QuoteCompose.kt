package com.heftreng.app.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
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
    val postId    : String = "",
)

// ── Öneri modeli ──────────────────────────────────────────────────────────────
data class QuoteSuggestion(
    val bookName  : String = "",
    val authorName: String = "",
    val count     : Int    = 0,
)

// ── Gönderi kartında alıntı gösterimi ─────────────────────────────────────────
@Composable
fun QuoteCard(
    quoteText   : String,
    bookName    : String = "",
    authorName  : String = "",
    onTapBook   : ((String) -> Unit)? = null,
    onTapAuthor : ((String) -> Unit)? = null,
    modifier    : Modifier = Modifier,
) {
    if (quoteText.isBlank()) return
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(SurfaceVar)
            .border(1.dp, Amber.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
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
            if (bookName.isNotBlank() || authorName.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AutoStories, null, tint = Amber, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(4.dp))
                    if (bookName.isNotBlank()) {
                        Text(
                            bookName,
                            color      = Amber,
                            fontSize   = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier   = if (onTapBook != null)
                                Modifier.clickable { onTapBook(bookName) }
                            else Modifier,
                        )
                    }
                    if (bookName.isNotBlank() && authorName.isNotBlank()) {
                        Text(" — ", color = Muted, fontSize = 10.sp)
                    }
                    if (authorName.isNotBlank()) {
                        Text(
                            authorName,
                            color    = Muted,
                            fontSize = 10.sp,
                            modifier = if (onTapAuthor != null)
                                Modifier.clickable { onTapAuthor(authorName) }
                            else Modifier,
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
            Text(
                "❝ ${quoteText.take(300)}${if (quoteText.length > 300) "…" else ""}",
                color     = OnSurface,
                fontSize  = 13.sp,
                fontStyle = FontStyle.Italic,
                lineHeight = 20.sp,
            )
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

// ── Alıntı oluşturma dialog — autocomplete destekli ──────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuoteDialog(
    initialText  : String = "",
    initialBook  : String = "",
    initialAuthor: String = "",
    onDismiss    : () -> Unit,
    onConfirm    : (QuotePayload) -> Unit,
) {
    var text   by remember { mutableStateOf(initialText) }
    var book   by remember { mutableStateOf(initialBook) }
    var author by remember { mutableStateOf(initialAuthor) }

    // Firestore'dan önceki alıntı kitap+yazar geçmişi
    val db  = remember { FirebaseFirestore.getInstance() }
    val uid = remember { FirebaseAuth.getInstance().currentUser?.uid ?: "" }

    var bookSuggestions     by remember { mutableStateOf<List<QuoteSuggestion>>(emptyList()) }
    var authorSuggestions   by remember { mutableStateOf<List<QuoteSuggestion>>(emptyList()) }
    var showBookDrop        by remember { mutableStateOf(false) }
    var showAuthorDrop      by remember { mutableStateOf(false) }

    // Feed'deki tüm alıntıları çek — eski nested ve yeni flat format her ikisi desteklenir
    LaunchedEffect(Unit) {
        try {
            // 1. Yeni format: type == "library_quote", quoteText dolu, bookName/authorName flat
            val newSnap = db.collection("feed")
                .whereEqualTo("type", "library_quote")
                .limit(50).get().await()

            // 2. Eski format: type == "quote" veya nested quote map var
            val oldSnap = db.collection("feed")
                .whereEqualTo("type", "quote")
                .limit(50).get().await()

            // 3. library_books/{id}/quotes collectionGroup — en zengin kaynak
            val cgSnap = try {
                db.collectionGroup("quotes").limit(50).get().await()
            } catch (_: Exception) { null }

            val bookMap   = mutableMapOf<String, QuoteSuggestion>()
            val authorMap = mutableMapOf<String, QuoteSuggestion>()

            fun processEntry(bName: String, aName: String) {
                if (bName.isBlank()) return
                val bKey = bName.lowercase().trim()
                val aKey = aName.lowercase().trim()
                val cur  = bookMap[bKey]
                bookMap[bKey] = QuoteSuggestion(
                    bookName   = bName.trim(),
                    authorName = cur?.authorName?.ifBlank { aName.trim() } ?: aName.trim(),
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

            // Yeni flat format
            newSnap.documents.forEach { doc ->
                val bName = doc.getString("bookName")?.trim() ?: ""
                val aName = doc.getString("authorName")?.trim() ?: ""
                processEntry(bName, aName)
            }

            // Eski nested format
            oldSnap.documents.forEach { doc ->
                val qObj  = doc.get("quote") as? Map<*, *>
                val bName = (qObj?.get("book")   as? String)?.trim()
                    ?: doc.getString("bookName")?.trim() ?: ""
                val aName = (qObj?.get("author") as? String)?.trim()
                    ?: doc.getString("authorName")?.trim() ?: ""
                processEntry(bName, aName)
            }

            // CollectionGroup (library_books alıntıları)
            cgSnap?.documents?.forEach { doc ->
                val bName = (doc.getString("bookTitle") ?: doc.getString("bookName") ?: "").trim()
                val aName = (doc.getString("authorName") ?: "").trim()
                processEntry(bName, aName)
            }

            bookSuggestions   = bookMap.values.sortedByDescending { it.count }
            authorSuggestions = authorMap.values.sortedByDescending { it.count }
        } catch (_: Exception) {}
    }

    // Kitap yazınca — kitap + yazarı getir
    LaunchedEffect(book) {
        if (book.isBlank()) { showBookDrop = false; return@LaunchedEffect }
        val match = bookSuggestions.filter {
            it.bookName.contains(book, ignoreCase = true)
        }
        showBookDrop = match.isNotEmpty()
    }

    // Yazar yazınca — yazar + kitabı getir
    LaunchedEffect(author) {
        if (author.isBlank()) { showAuthorDrop = false; return@LaunchedEffect }
        val match = authorSuggestions.filter {
            it.authorName.contains(author, ignoreCase = true)
        }
        showAuthorDrop = match.isNotEmpty()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = HeftSurface,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.FormatQuote, null, tint = Primary, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text("Alıntı Ekle", color = OnBackground, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Alıntı metni
                OutlinedTextField(
                    value         = text,
                    onValueChange = { text = it },
                    label         = { Text("ALINTI METNİ *") },
                    minLines      = 3,
                    modifier      = Modifier.fillMaxWidth(),
                    colors        = quoteTextFieldColors(),
                )

                // Kitap adı + autocomplete dropdown
                Box {
                    OutlinedTextField(
                        value         = book,
                        onValueChange = {
                            book = it
                            showBookDrop = it.isNotBlank() &&
                                bookSuggestions.any { s -> s.bookName.contains(it, ignoreCase = true) }
                        },
                        label         = { Text("KİTAP ADI") },
                        singleLine    = true,
                        modifier      = Modifier.fillMaxWidth(),
                        colors        = quoteTextFieldColors(),
                        leadingIcon   = {
                            Icon(Icons.Default.AutoStories, null, tint = Muted, modifier = Modifier.size(18.dp))
                        },
                        trailingIcon  = if (book.isNotBlank()) {{
                            IconButton(onClick = { book = ""; showBookDrop = false }) {
                                Icon(Icons.Default.Close, null, tint = Muted, modifier = Modifier.size(16.dp))
                            }
                        }} else null,
                    )
                    // Kitap öneri dropdown
                    if (showBookDrop) {
                        Surface(
                            shape  = RoundedCornerShape(10.dp),
                            color  = HeftSurface,
                            border = androidx.compose.foundation.BorderStroke(1.dp, Divider),
                            tonalElevation = 4.dp,
                        ) {
                            LazyColumn(modifier = Modifier.heightIn(max = 160.dp)) {
                                val filtered = bookSuggestions.filter {
                                    it.bookName.contains(book, ignoreCase = true)
                                }
                                items(filtered) { s ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                book   = s.bookName
                                                if (s.authorName.isNotBlank()) author = s.authorName
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

                // Yazar + autocomplete
                Box {
                    OutlinedTextField(
                        value         = author,
                        onValueChange = {
                            author = it
                            showAuthorDrop = it.isNotBlank() &&
                                authorSuggestions.any { s -> s.authorName.contains(it, ignoreCase = true) }
                        },
                        label         = { Text("YAZAR") },
                        singleLine    = true,
                        modifier      = Modifier.fillMaxWidth(),
                        colors        = quoteTextFieldColors(),
                        leadingIcon   = {
                            Icon(Icons.Default.Person, null, tint = Muted, modifier = Modifier.size(18.dp))
                        },
                        trailingIcon  = if (author.isNotBlank()) {{
                            IconButton(onClick = { author = ""; showAuthorDrop = false }) {
                                Icon(Icons.Default.Close, null, tint = Muted, modifier = Modifier.size(16.dp))
                            }
                        }} else null,
                    )
                    // Yazar öneri dropdown — yazar adı + kitap adı + alıntı sayısı
                    if (showAuthorDrop) {
                        val filtered = authorSuggestions.filter {
                            it.authorName.contains(author, ignoreCase = true)
                        }.take(8)
                        if (filtered.isNotEmpty()) {
                            Surface(
                                shape          = RoundedCornerShape(10.dp),
                                color          = HeftSurface,
                                border         = androidx.compose.foundation.BorderStroke(1.dp, Divider),
                                tonalElevation = 4.dp,
                            ) {
                                LazyColumn(modifier = Modifier.heightIn(max = 160.dp)) {
                                    items(filtered) { s ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    author         = s.authorName
                                                    // Kitap boşsa yazara ait kitabı da doldur
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

                // Önizleme
                if (text.isNotBlank() || book.isNotBlank() || author.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    HorizontalDivider(color = Divider, thickness = 0.5.dp)
                    Spacer(Modifier.height(4.dp))
                    Text("Önizleme", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    QuoteCard(
                        quoteText  = text.ifBlank { "…" },
                        bookName   = book,
                        authorName = author,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (text.isNotBlank() || book.isNotBlank()) {
                        onConfirm(QuotePayload(text = text, bookName = book, authorName = author))
                    }
                },
                enabled = text.isNotBlank() || book.isNotBlank(),
            ) { Text("Ekle", color = Primary, fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("İptal", color = Muted) }
        },
    )
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
