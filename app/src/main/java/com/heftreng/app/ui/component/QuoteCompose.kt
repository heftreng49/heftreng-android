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

    var bookSuggestions    by remember { mutableStateOf<List<QuoteSuggestion>>(emptyList()) }
    var authorSuggestions  by remember { mutableStateOf<List<String>>(emptyList()) }
    var fsAuthorSuggestions by remember { mutableStateOf<List<String>>(emptyList()) }
    var showBookDrop       by remember { mutableStateOf(false) }
    var showAuthorDrop     by remember { mutableStateOf(false) }

    // Kullanıcının önceki alıntı kitaplarını yükle
    LaunchedEffect(uid) {
        if (uid.isBlank()) return@LaunchedEffect
        try {
            val snap = db.collection("feed")
                .whereEqualTo("uid", uid)
                .limit(50).get().await()
            val map = mutableMapOf<String, QuoteSuggestion>()
            snap.documents.forEach { doc ->
                val qObj   = doc.get("quote") as? Map<*, *> ?: return@forEach
                val bName  = (qObj["book"]   as? String)?.takeIf { it.isNotBlank() } ?: return@forEach
                val aName  = (qObj["author"] as? String) ?: ""
                val key    = bName.lowercase()
                val cur    = map[key]
                map[key]   = QuoteSuggestion(
                    bookName   = bName,
                    authorName = cur?.authorName?.ifBlank { aName } ?: aName,
                    count      = (cur?.count ?: 0) + 1,
                )
            }
            bookSuggestions = map.values.sortedByDescending { it.count }
        } catch (_: Exception) {}
    }

    // Kitap değişince yazar öner
    LaunchedEffect(book) {
        val match = bookSuggestions.filter {
            it.bookName.contains(book, ignoreCase = true) && book.isNotBlank()
        }
        authorSuggestions = match.map { it.authorName }.filter { it.isNotBlank() }.distinct()
        showBookDrop      = book.isNotBlank() && match.isNotEmpty()
        if (!showBookDrop) showAuthorDrop = false
    }

    // Yazar alanına yazınca Firestore'dan ara
    LaunchedEffect(author) {
        if (author.length < 2) {
            fsAuthorSuggestions = emptyList()
            showAuthorDrop = authorSuggestions.any { it.contains(author, ignoreCase = true) } && author.isNotBlank()
            return@LaunchedEffect
        }
        try {
            // authors koleksiyonundan ara
            val authorEnd = author + "\uf8ff"
            val snap = db.collection("authors")
                .orderBy("name")
                .startAt(author.lowercase())
                .endAt(authorEnd.lowercase())
                .limit(10).get().await()

            val results = snap.documents
                .mapNotNull { it.getString("name") ?: it.getString("displayName") }
                .filter { it.isNotBlank() }

            // Eğer authors koleksiyonu yoksa feed'den çek
            val finalResults = if (results.isEmpty()) {
                val feedSnap = db.collection("feed")
                    .limit(200).get().await()
                feedSnap.documents.mapNotNull { doc ->
                    val qObj = doc.get("quote") as? Map<*, *> ?: return@mapNotNull null
                    qObj["author"] as? String
                }
                .filter { it.contains(author, ignoreCase = true) && it.isNotBlank() }
                .distinct()
                .take(10)
            } else results

            fsAuthorSuggestions = finalResults
            showAuthorDrop = finalResults.isNotEmpty() || authorSuggestions.any { it.contains(author, ignoreCase = true) }
        } catch (_: Exception) {}
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
                            showAuthorDrop = it.isNotBlank() && (
                                authorSuggestions.any { a -> a.contains(it, ignoreCase = true) } ||
                                fsAuthorSuggestions.isNotEmpty()
                            )
                        },
                        label         = { Text("YAZAR") },
                        singleLine    = true,
                        modifier      = Modifier.fillMaxWidth(),
                        colors        = quoteTextFieldColors(),
                        leadingIcon   = {
                            Icon(Icons.Default.Person, null, tint = Muted, modifier = Modifier.size(18.dp))
                        },
                        trailingIcon  = if (author.isNotBlank()) {{
                            IconButton(onClick = { author = ""; showAuthorDrop = false; fsAuthorSuggestions = emptyList() }) {
                                Icon(Icons.Default.Close, null, tint = Muted, modifier = Modifier.size(16.dp))
                            }
                        }} else null,
                    )
                    // Yazar öneri dropdown — hem local hem Firestore sonuçları
                    val combinedAuthors = (
                        authorSuggestions.filter { it.contains(author, ignoreCase = true) } +
                        fsAuthorSuggestions.filter { a -> authorSuggestions.none { it.equals(a, ignoreCase = true) } }
                    ).distinct().take(8)

                    if (showAuthorDrop && combinedAuthors.isNotEmpty()) {
                        Surface(
                            shape  = RoundedCornerShape(10.dp),
                            color  = HeftSurface,
                            border = androidx.compose.foundation.BorderStroke(1.dp, Divider),
                            tonalElevation = 4.dp,
                        ) {
                            Column {
                                combinedAuthors.forEach { a ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { author = a; showAuthorDrop = false }
                                            .padding(horizontal = 14.dp, vertical = 10.dp),
                                        verticalAlignment     = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Icon(Icons.Default.Person, null, tint = Primary, modifier = Modifier.size(14.dp))
                                        Text(a, color = OnBackground, fontSize = 13.sp)
                                    }
                                    HorizontalDivider(color = Divider, thickness = 0.5.dp)
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
