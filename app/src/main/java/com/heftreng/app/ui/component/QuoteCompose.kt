package com.heftreng.app.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.heftreng.app.ui.theme.*

data class QuotePayload(
    val text      : String = "",
    val authorName: String = "",
    val bookName  : String = "",
    val postId    : String = "",
)

// ── Gönderi kartında alıntı gösterimi ────────────────────────────────────────
// XML: _quoteHtml(q) — book, author, text
@Composable
fun QuoteCard(
    quoteText  : String,
    bookName   : String = "",
    authorName : String = "",
    modifier   : Modifier = Modifier,
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
            // Kitap + yazar üst satır
            if (bookName.isNotBlank() || authorName.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AutoStories, null, tint = Amber, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(4.dp))
                    if (bookName.isNotBlank()) {
                        Text(bookName, color = Amber, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                    }
                    if (bookName.isNotBlank() && authorName.isNotBlank()) {
                        Text(" — ", color = Muted, fontSize = 10.sp)
                    }
                    if (authorName.isNotBlank()) {
                        Text(authorName, color = Muted, fontSize = 10.sp)
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
            // Alıntı metni
            Text(
                "❝ ${quoteText.take(300)}${if (quoteText.length > 300) "…" else ""}",
                color      = OnSurface,
                fontSize   = 13.sp,
                fontStyle  = FontStyle.Italic,
                lineHeight = 20.sp,
            )
        }
    }
}

// ── Compose alanında seçili alıntı gösterimi + kaldır butonu ─────────────────
@Composable
fun QuoteInputSection(
    quote   : QuotePayload?,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (quote == null) return
    Box(modifier = modifier) {
        QuoteCard(
            quoteText  = quote.text,
            bookName   = quote.bookName,
            authorName = quote.authorName,
        )
        IconButton(
            onClick  = onRemove,
            modifier = Modifier.align(Alignment.TopEnd).size(28.dp),
        ) {
            Icon(Icons.Default.Close, null, tint = Muted, modifier = Modifier.size(16.dp))
        }
    }
}

// ── Alıntı butonu (PostCard'da) ───────────────────────────────────────────────
@Composable
fun QuoteButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(onClick = onClick, modifier = modifier.size(36.dp)) {
        Icon(Icons.Default.FormatQuote, null, tint = Muted, modifier = Modifier.size(20.dp))
    }
}

// ── Alıntı oluşturma dialog ───────────────────────────────────────────────────
// XML: _applyQuote — kullanıcı metni, kitap, yazar girer
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

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = HeftSurface,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.FormatQuote, null, tint = Amber, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text("Alıntı Ekle", color = OnBackground, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value         = text,
                    onValueChange = { text = it },
                    label         = { Text("Alıntı metni *") },
                    minLines      = 3,
                    modifier      = Modifier.fillMaxWidth(),
                    colors        = quoteTextFieldColors(),
                )
                OutlinedTextField(
                    value         = book,
                    onValueChange = { book = it },
                    label         = { Text("Kitap adı") },
                    singleLine    = true,
                    modifier      = Modifier.fillMaxWidth(),
                    colors        = quoteTextFieldColors(),
                    leadingIcon   = { Icon(Icons.Default.AutoStories, null, tint = Muted, modifier = Modifier.size(18.dp)) },
                )
                OutlinedTextField(
                    value         = author,
                    onValueChange = { author = it },
                    label         = { Text("Yazar") },
                    singleLine    = true,
                    modifier      = Modifier.fillMaxWidth(),
                    colors        = quoteTextFieldColors(),
                )

                // Önizleme
                if (text.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text("Önizleme", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    QuoteCard(quoteText = text, bookName = book, authorName = author)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (text.isNotBlank()) {
                        onConfirm(QuotePayload(text = text, bookName = book, authorName = author))
                    }
                },
                enabled = text.isNotBlank(),
            ) {
                Text("Ekle", color = Amber, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("İptal", color = Muted) }
        },
    )
}

@Composable
private fun quoteTextFieldColors() = OutlinedTextFieldDefaults.colors(
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
