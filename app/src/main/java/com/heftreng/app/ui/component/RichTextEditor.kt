package com.heftreng.app.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.heftreng.app.ui.theme.*

// ─────────────────────────────────────────────────────────────────────────────
// Veri modeli
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Tek bir format aralığı. [start, end) — end dahil değil.
 *
 * [heading] → null = normal paragraf, 2 = <h2>, 3 = <h3>
 * Başlık satırları otomatik bold + büyük font alır; üstü çizili OLMAZ.
 */
data class RichSpan(
    val start   : Int,
    val end     : Int,
    val bold    : Boolean = false,
    val italic  : Boolean = false,
    val under   : Boolean = false,
    val strike  : Boolean = false,
    val size    : Int?    = null,    // sp; null = varsayılan
    val color   : Color?  = null,
    val heading : Int?    = null,    // 2 veya 3; diğer alanları override eder
)

// ─────────────────────────────────────────────────────────────────────────────
// HTML ↔ Span / Tablo dönüşümleri  (public)
// ─────────────────────────────────────────────────────────────────────────────

/** Span listesi + düz metin → HTML.
 *  Başlıklar <h2>/<h3> olarak, tablolar <table> bloğu olarak çıkar. */
fun spansToHtml(text: String, spans: List<RichSpan>): String {
    if (text.isBlank() && spans.isEmpty()) return ""

    // Tablo bloklarını önce ayıkla — bunlar span ile değil, kendi tag'leriyle saklanır.
    // Tablo metni "\u0000TABLE:<html>\u0000" sentinel formatındadır.
    val tableRegex = Regex("\u0000TABLE:(.*?)\u0000", RegexOption.DOT_MATCHES_ALL)

    return buildString {
        var cursor = 0
        for (tableMatch in tableRegex.findAll(text)) {
            // Tablo öncesindeki normal metin
            val before = text.substring(cursor, tableMatch.range.first)
            if (before.isNotEmpty()) {
                append(segmentToHtml(before, cursor, spans))
            }
            // Tablo HTML'sini doğrudan ekle (zaten <table>…</table>)
            append(tableMatch.groupValues[1])
            cursor = tableMatch.range.last + 1
        }
        // Kalan metin
        val tail = text.substring(cursor)
        if (tail.isNotEmpty()) append(segmentToHtml(tail, cursor, spans))
    }
}

/** Normal metin segmentini (tablo dışı) HTML'ye çevirir. */
private fun segmentToHtml(segment: String, offset: Int, spans: List<RichSpan>): String {
    if (segment.isEmpty()) return ""
    if (spans.isEmpty()) return "<p>${segment.replace("\n", "<br/>")}</p>"

    return buildString {
        var i = 0
        while (i < segment.length) {
            val absI   = i + offset
            val ch     = segment[i]
            val active = spans.filter { absI >= it.start && absI < it.end }

            val headingSpan = active.firstOrNull { it.heading != null }
            if (headingSpan != null) {
                // Başlık satırı — sadece tag, strikethrough/underline yok
                val tag = "h${headingSpan.heading}"
                append("<$tag>")
                append(if (ch == '\n') "<br/>" else ch.htmlEscape())
                append("</$tag>")
                i++
                continue
            }

            val bold   = active.any { it.bold }
            val italic = active.any { it.italic }
            val under  = active.any { it.under }
            val strike = active.any { it.strike }
            val size   = active.mapNotNull { it.size }.maxOrNull()
            val color  = active.mapNotNull { it.color }.lastOrNull()

            val styleAttr = buildString {
                if (size  != null) append("font-size:${size}px;")
                if (color != null) append("color:${color.toHtmlHex()};")
            }

            if (styleAttr.isNotBlank()) append("<span style=\"$styleAttr\">")
            if (bold)   append("<b>")
            if (italic) append("<i>")
            if (under)  append("<u>")
            if (strike) append("<s>")

            append(if (ch == '\n') "<br/>" else ch.htmlEscape())

            if (strike) append("</s>")
            if (under)  append("</u>")
            if (italic) append("</i>")
            if (bold)   append("</b>")
            if (styleAttr.isNotBlank()) append("</span>")

            i++
        }
    }
}

data class HtmlParseResult(val text: String, val spans: List<RichSpan>)

/** HTML → düz metin + span listesi.
 *  <table> blokları sentinel formatına çevrilir, diğer tag'ler span'a dönüşür. */
fun htmlToSpans(html: String): HtmlParseResult {
    if (html.isBlank()) return HtmlParseResult("", emptyList())

    // Tablo bloklarını önce sentinel'e çevir
    val tableRegex = Regex(
        "<table[^>]*>.*?</table>",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )
    var preprocessed = html
    for (m in tableRegex.findAll(html)) {
        val sentinel = "\u0000TABLE:${m.value}\u0000"
        preprocessed = preprocessed.replace(m.value, sentinel)
    }

    // Başlıkları işle: <h2>…</h2> ve <h3>…</h3>
    val headingRegex = Regex(
        "<(h[23])[^>]*>(.*?)</\\1>",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )
    preprocessed = headingRegex.replace(preprocessed) { mr ->
        val level   = mr.groupValues[1].removePrefix("h")
        val content = mr.groupValues[2]
            .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("<[^>]+>"), "")
        "\u0001H${level}:${content}\u0001"
    }

    // <br> / <p> → newline; entity decode
    val normalized = preprocessed
        .replace(Regex("<br\\s*/?>",  RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("<p[^>]*>",    RegexOption.IGNORE_CASE), "")
        .replace(Regex("</p>",        RegexOption.IGNORE_CASE), "\n")
        .replace("&amp;",  "&")
        .replace("&lt;",   "<")
        .replace("&gt;",   ">")
        .replace("&nbsp;", " ")

    val tagRegex = Regex(
        "<(/?)(b|strong|i|em|u|s|strike|span)([^>]*)>",
        RegexOption.IGNORE_CASE,
    )

    data class OpenTag(val tag: String, val startIndex: Int, val proto: RichSpan)

    val plain  = StringBuilder()
    val spans  = mutableListOf<RichSpan>()
    val stack  = mutableListOf<OpenTag>()
    var cursor = 0

    // Başlık sentinel'lerini bul ve işle
    val headingSentinelRegex = Regex("\u0001H([23]):(.*?)\u0001", RegexOption.DOT_MATCHES_ALL)

    // Tüm özel blokları (heading sentinel) ve tag'leri birlikte işle
    val allMatches: List<Pair<String, MatchResult>> = (
        tagRegex.findAll(normalized).map { "tag" to it as MatchResult }.toList() +
        headingSentinelRegex.findAll(normalized).map { "heading" to it as MatchResult }.toList()
    ).sortedBy { it.second.range.first }

    for ((type, match) in allMatches) {
        if (match.range.first < cursor) continue
        // Araya giren düz metin
        if (match.range.first > cursor) {
            plain.append(normalized.substring(cursor, match.range.first))
        }
        cursor = match.range.last + 1

        when (type) {
            "heading" -> {
                val level   = match.groupValues[1].toIntOrNull() ?: 2
                val content = match.groupValues[2]
                val start   = plain.length
                plain.append(content)
                val end = plain.length
                if (end > start) {
                    spans.add(RichSpan(start = start, end = end, heading = level, bold = true))
                }
            }
            "tag" -> {
                val closing = match.groupValues[1] == "/"
                val tag     = match.groupValues[2].lowercase()
                val attrs   = match.groupValues[3]

                if (!closing) {
                    val proto = RichSpan(
                        start  = plain.length,
                        end    = plain.length,
                        bold   = tag == "b" || tag == "strong",
                        italic = tag == "i" || tag == "em",
                        under  = tag == "u",
                        strike = tag == "s" || tag == "strike",
                        color  = if (tag == "span") parseHtmlColor(attrs) else null,
                        size   = if (tag == "span") parseHtmlFontSize(attrs) else null,
                    )
                    stack.add(OpenTag(tag, plain.length, proto))
                } else {
                    val idx = stack.indexOfLast { open ->
                        when (tag) {
                            "b", "strong" -> open.tag == "b" || open.tag == "strong"
                            "i", "em"     -> open.tag == "i" || open.tag == "em"
                            "s", "strike" -> open.tag == "s" || open.tag == "strike"
                            else          -> open.tag == tag
                        }
                    }
                    if (idx >= 0) {
                        val open = stack.removeAt(idx)
                        val end  = plain.length
                        if (end > open.startIndex) {
                            spans.add(open.proto.copy(start = open.startIndex, end = end))
                        }
                    }
                }
            }
        }
    }

    if (cursor < normalized.length) plain.append(normalized.substring(cursor))

    stack.forEach { open ->
        val end = plain.length
        if (end > open.startIndex) spans.add(open.proto.copy(start = open.startIndex, end = end))
    }

    // Bilinmeyen tag kalıntılarını temizle; sentinel'leri koru
    val cleanText = plain.toString()
        .replace(Regex("<[^>]+>"), "")
        .trimEnd()

    val clampedSpans = spans
        .map { s ->
            s.copy(
                start = s.start.coerceIn(0, cleanText.length),
                end   = s.end.coerceIn(0, cleanText.length),
            )
        }
        .filter { it.start < it.end }

    return HtmlParseResult(cleanText, clampedSpans)
}

/** HTML'yi düz metne indirger (bildirim özeti vb.). */
fun htmlStrip(html: String): String = html
    .replace(Regex("<br/?>",   RegexOption.IGNORE_CASE), "\n")
    .replace(Regex("<hr/?>",   RegexOption.IGNORE_CASE), "\n───────────────\n")
    .replace(Regex("<h[1-6][^>]*>", RegexOption.IGNORE_CASE), "")
    .replace(Regex("</h[1-6]>",     RegexOption.IGNORE_CASE), "\n")
    .replace(Regex("<p>",      RegexOption.IGNORE_CASE), "")
    .replace(Regex("</p>",     RegexOption.IGNORE_CASE), "\n")
    .replace(Regex("<td[^>]*>",RegexOption.IGNORE_CASE), " | ")
    .replace(Regex("<[^>]+>"), "")
    .replace("&amp;",  "&")
    .replace("&lt;",   "<")
    .replace("&gt;",   ">")
    .replace("&nbsp;", " ")
    .trimEnd()

// ─────────────────────────────────────────────────────────────────────────────
// AnnotatedString oluşturucu  (public)
// ─────────────────────────────────────────────────────────────────────────────

fun buildAnnotated(text: String, spans: List<RichSpan>): AnnotatedString =
    buildAnnotatedString {
        append(text)
        spans.forEach { s ->
            val start = s.start.coerceIn(0, text.length)
            val end   = s.end.coerceIn(0, text.length)
            if (start >= end) return@forEach

            if (s.heading != null) {
                // Başlık: büyük + bold, ASLA strikethrough/underline yok
                val headingSize = if (s.heading == 2) 22.sp else 18.sp
                addStyle(
                    SpanStyle(
                        fontWeight = FontWeight.Bold,
                        fontSize   = headingSize,
                        color      = s.color ?: Color.Unspecified,
                        // textDecoration = null → varsayılan, üstü çizili kesinlikle yok
                    ),
                    start, end,
                )
                return@forEach
            }

            addStyle(
                SpanStyle(
                    fontWeight     = if (s.bold)   FontWeight.Bold   else null,
                    fontStyle      = if (s.italic) FontStyle.Italic  else null,
                    textDecoration = when {
                        s.under && s.strike -> TextDecoration.combine(
                            listOf(TextDecoration.Underline, TextDecoration.LineThrough)
                        )
                        s.under  -> TextDecoration.Underline
                        s.strike -> TextDecoration.LineThrough
                        else     -> TextDecoration.None
                    },
                    fontSize = s.size?.sp ?: androidx.compose.ui.unit.TextUnit.Unspecified,
                    color    = s.color ?: Color.Unspecified,
                ),
                start, end,
            )
        }
    }

// ─────────────────────────────────────────────────────────────────────────────
// Tablo HTML yardımcısı
// ─────────────────────────────────────────────────────────────────────────────

/** [rows] × [cols] boyutunda boş tablo HTML'si üretir. */
fun buildTableHtml(rows: Int, cols: Int): String = buildString {
    append("<table border=\"1\" cellpadding=\"6\" cellspacing=\"0\" style=\"border-collapse:collapse;width:100%;\">")
    repeat(rows) { r ->
        append("<tr>")
        repeat(cols) {
            if (r == 0) append("<th style=\"background:#f0f0f0;\">Başlık</th>")
            else        append("<td>&nbsp;</td>")
        }
        append("</tr>")
    }
    append("</table>")
}

/** Tablo sentinel'i oluşturur (metne gömülür). */
fun tableBlock(rows: Int, cols: Int): String =
    "\u0000TABLE:${buildTableHtml(rows, cols)}\u0000"

// ─────────────────────────────────────────────────────────────────────────────
// Yardımcı fonksiyonlar
// ─────────────────────────────────────────────────────────────────────────────

private fun Char.htmlEscape(): String = when (this) {
    '<'  -> "&lt;"
    '>'  -> "&gt;"
    '&'  -> "&amp;"
    else -> toString()
}

private fun Color.toHtmlHex(): String {
    val r = (red   * 255).toInt()
    val g = (green * 255).toInt()
    val b = (blue  * 255).toInt()
    return "#%02x%02x%02x".format(r, g, b)
}

private fun parseHtmlColor(attrs: String): Color? {
    val hex = Regex("color\\s*:\\s*(#[0-9a-fA-F]{3,8})", RegexOption.IGNORE_CASE)
        .find(attrs)?.groupValues?.get(1) ?: return null
    return try { Color(android.graphics.Color.parseColor(hex)) } catch (_: Exception) { null }
}

private fun parseHtmlFontSize(attrs: String): Int? =
    Regex("font-size\\s*:\\s*(\\d+)px", RegexOption.IGNORE_CASE)
        .find(attrs)?.groupValues?.get(1)?.toIntOrNull()

// ─────────────────────────────────────────────────────────────────────────────
// Span kaydırma yardımcısı
// ─────────────────────────────────────────────────────────────────────────────

private fun shiftSpans(
    spans    : List<RichSpan>,
    insertAt : Int,
    diff     : Int,
    newLen   : Int,
): List<RichSpan> {
    if (diff == 0) return spans
    return spans.mapNotNull { s ->
        val newStart: Int
        val newEnd: Int
        if (diff > 0) {
            newStart = if (s.start >= insertAt) s.start + diff else s.start
            newEnd   = if (s.end   >  insertAt) s.end   + diff else s.end
        } else {
            val delStart = insertAt + diff
            val delEnd   = insertAt
            newStart = when {
                s.start <= delStart -> s.start
                s.start >= delEnd   -> s.start + diff
                else                -> delStart
            }
            newEnd = when {
                s.end <= delStart -> s.end
                s.end >= delEnd   -> s.end + diff
                else              -> delStart
            }
        }
        val cs = newStart.coerceIn(0, newLen)
        val ce = newEnd.coerceIn(0, newLen)
        if (cs < ce) s.copy(start = cs, end = ce) else null
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Tablo ekleme dialog'u
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun InsertTableDialog(
    onDismiss : () -> Unit,
    onInsert  : (rows: Int, cols: Int) -> Unit,
) {
    var rows by remember { mutableStateOf(3) }
    var cols by remember { mutableStateOf(3) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title   = { Text("Tablo Ekle") },
        text    = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Satır sayısı
                Text("Satır sayısı: $rows", fontSize = 13.sp)
                Slider(
                    value         = rows.toFloat(),
                    onValueChange = { rows = it.toInt() },
                    valueRange    = 1f..10f,
                    steps         = 8,
                    colors        = SliderDefaults.colors(thumbColor = Amber, activeTrackColor = Amber),
                )
                // Sütun sayısı
                Text("Sütun sayısı: $cols", fontSize = 13.sp)
                Slider(
                    value         = cols.toFloat(),
                    onValueChange = { cols = it.toInt() },
                    valueRange    = 1f..6f,
                    steps         = 4,
                    colors        = SliderDefaults.colors(thumbColor = Amber, activeTrackColor = Amber),
                )
                // Önizleme etiketi
                Text(
                    "${rows} satır × ${cols} sütun",
                    color    = Muted,
                    fontSize = 12.sp,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onInsert(rows, cols) }) {
                Text("Ekle", color = Amber)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("İptal")
            }
        },
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Ana RichTextEditor Composable
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun RichTextEditor(
    value      : String,
    onChange   : (String) -> Unit,
    modifier   : Modifier = Modifier,
    placeholder: String   = "İçeriğinizi buraya yazın...",
) {
    val initialParsed = remember(value) { htmlToSpans(value) }

    var tfv       by remember { mutableStateOf(TextFieldValue(text = initialParsed.text)) }
    var spans     by remember { mutableStateOf(initialParsed.spans) }
    var isFocused by remember { mutableStateOf(false) }

    // Format bayrakları
    var boldOn    by remember { mutableStateOf(false) }
    var italicOn  by remember { mutableStateOf(false) }
    var underOn   by remember { mutableStateOf(false) }
    var strikeOn  by remember { mutableStateOf(false) }
    var fontSize  by remember { mutableStateOf<Int?>(null) }
    var textColor by remember { mutableStateOf<Color?>(null) }
    var showSize  by remember { mutableStateOf(false) }
    var showColor by remember { mutableStateOf(false) }
    var showTableDialog by remember { mutableStateOf(false) }

    LaunchedEffect(initialParsed) {
        if (tfv.text != initialParsed.text) {
            tfv      = TextFieldValue(text = initialParsed.text)
            spans    = initialParsed.spans
            boldOn   = false; italicOn = false; underOn = false; strikeOn = false
            fontSize = null;  textColor = null
        }
    }

    val surface    = HeftSurface
    val surfaceVar = SurfaceVar
    val onBg       = OnBackground
    val muted      = Muted
    val divider    = Divider

    // ── Seçime format uygula ──────────────────────────────────────────────
    fun applyToSelection(
        bold    : Boolean? = null,
        italic  : Boolean? = null,
        under   : Boolean? = null,
        strike  : Boolean? = null,
        size    : Int?     = null,
        color   : Color?   = null,
        heading : Int?     = null,
    ) {
        val sel = tfv.selection
        if (sel.collapsed) return
        val start = sel.min
        val end   = sel.max

        val updated = mutableListOf<RichSpan>()
        for (s in spans) {
            when {
                s.end <= start || s.start >= end -> updated.add(s)
                else -> {
                    if (s.start < start) updated.add(s.copy(end = start))
                    if (s.end   > end)   updated.add(s.copy(start = end))
                    val inside = s.copy(
                        start   = maxOf(s.start, start),
                        end     = minOf(s.end,   end),
                        bold    = bold    ?: s.bold,
                        italic  = italic  ?: s.italic,
                        under   = under   ?: s.under,
                        // Başlık span'ına strike uygulanmaz
                        strike  = if (s.heading != null) false else (strike ?: s.strike),
                        size    = size    ?: s.size,
                        color   = color   ?: s.color,
                        heading = heading ?: s.heading,
                    )
                    if (inside.bold || inside.italic || inside.under || inside.strike
                        || inside.size != null || inside.color != null || inside.heading != null
                    ) updated.add(inside)
                }
            }
        }

        val covered = updated.any { it.start <= start && it.end >= end }
        if (!covered && (bold == true || italic == true || under == true || strike == true
                || size != null || color != null || heading != null)
        ) {
            updated.add(
                RichSpan(
                    start   = start, end = end,
                    bold    = bold   == true || heading != null,
                    italic  = italic == true,
                    under   = under  == true,
                    strike  = if (heading != null) false else (strike == true),
                    size    = size,
                    color   = color,
                    heading = heading,
                )
            )
        }

        spans = updated.filter { it.start < it.end }
        onChange(spansToHtml(tfv.text, spans))
    }

    fun selectionHas(check: (RichSpan) -> Boolean): Boolean {
        val sel = tfv.selection
        if (sel.collapsed) return false
        return spans.any { s -> s.start < sel.max && s.end > sel.min && check(s) }
    }

    fun toggleFormat(
        flag     : Boolean,
        setFlag  : (Boolean) -> Unit,
        checkSpan: (RichSpan) -> Boolean,
        applySpan: (Boolean) -> Unit,
    ) {
        if (!tfv.selection.collapsed) {
            applySpan(!selectionHas(checkSpan))
        } else {
            setFlag(!flag)
        }
    }

    // ── Başlık satırı ekle ────────────────────────────────────────────────
    fun insertHeading(level: Int) {
        val cursor  = tfv.selection.start
        val text    = tfv.text
        // İmleç satırının başını bul
        val lineStart = text.lastIndexOf('\n', cursor - 1).let { if (it < 0) 0 else it + 1 }
        val lineEnd   = text.indexOf('\n', cursor).let { if (it < 0) text.length else it }
        val lineText  = text.substring(lineStart, lineEnd)

        // Satıra başlık span'ı ekle/değiştir
        val updatedSpans = spans.mapNotNull { s ->
            when {
                s.end <= lineStart || s.start >= lineEnd -> s
                else -> null // mevcut span'ları temizle
            }
        } + RichSpan(start = lineStart, end = lineEnd, heading = level, bold = true)

        spans = updatedSpans.filter { it.start < it.end }
        onChange(spansToHtml(tfv.text, spans))
    }

    // ─────────────────────────────────────────────────────────────────────
    if (showTableDialog) {
        InsertTableDialog(
            onDismiss = { showTableDialog = false },
            onInsert  = { rows, cols ->
                showTableDialog = false
                val sentinel = tableBlock(rows, cols)
                val cursor   = tfv.selection.start
                val newText  = tfv.text.substring(0, cursor) + "\n" + sentinel + "\n" + tfv.text.substring(cursor)
                val newCursor = cursor + sentinel.length + 2
                spans = shiftSpans(spans, cursor, sentinel.length + 2, newText.length)
                tfv   = TextFieldValue(text = newText, selection = TextRange(newCursor))
                onChange(spansToHtml(newText, spans))
            }
        )
    }

    Column(modifier = modifier) {

        // ── Toolbar ───────────────────────────────────────────────────────
        Surface(
            tonalElevation = 1.dp,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)),
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(surface)
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                ) {
                    // H2
                    FmtBtn(Icons.Filled.Title, "Başlık H2", false, onBg) { insertHeading(2) }
                    // H3
                    FmtBtn(Icons.Filled.TextFields, "Başlık H3", false, onBg) { insertHeading(3) }

                    ThinDivider(divider)

                    FmtBtn(Icons.Filled.FormatBold, "Kalın", boldOn, onBg) {
                        toggleFormat(boldOn, { boldOn = it }, { it.bold && it.heading == null }) { v ->
                            boldOn = v; applyToSelection(bold = v)
                        }
                    }
                    FmtBtn(Icons.Filled.FormatItalic, "İtalik", italicOn, onBg) {
                        toggleFormat(italicOn, { italicOn = it }, { it.italic }) { v ->
                            italicOn = v; applyToSelection(italic = v)
                        }
                    }
                    FmtBtn(Icons.Filled.FormatUnderlined, "Altı Çizili", underOn, onBg) {
                        toggleFormat(underOn, { underOn = it }, { it.under }) { v ->
                            underOn = v; applyToSelection(under = v)
                        }
                    }
                    FmtBtn(Icons.Filled.FormatStrikethrough, "Üstü Çizili", strikeOn, onBg) {
                        toggleFormat(strikeOn, { strikeOn = it }, { it.strike }) { v ->
                            strikeOn = v; applyToSelection(strike = v)
                        }
                    }

                    ThinDivider(divider)

                    FmtBtn(Icons.Filled.FormatSize, "Yazı Boyutu", showSize, onBg) {
                        showSize = !showSize; showColor = false
                    }
                    FmtBtn(Icons.Filled.Palette, "Renk", showColor, onBg) {
                        showColor = !showColor; showSize = false
                    }
                    FmtBtn(Icons.Filled.TableChart, "Tablo Ekle", false, onBg) {
                        showTableDialog = true; showSize = false; showColor = false
                    }

                    ThinDivider(divider)

                    FmtBtn(Icons.Filled.FormatClear, "Formatı Temizle", false, onBg) {
                        val sel = tfv.selection
                        if (!sel.collapsed) {
                            spans = spans.filter { s -> s.end <= sel.min || s.start >= sel.max }
                        } else {
                            spans = emptyList()
                            boldOn = false; italicOn = false; underOn = false; strikeOn = false
                            fontSize = null; textColor = null
                        }
                        showSize = false; showColor = false
                        onChange(spansToHtml(tfv.text, spans))
                    }
                }

                // ── Boyut seçici ───────────────────────────────────────────
                if (showSize) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(surface)
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment     = Alignment.CenterVertically,
                    ) {
                        Text("Boyut:", color = muted, fontSize = 11.sp)
                        listOf(null to "Varsayılan", 12 to "Küçük", 16 to "Normal",
                               20 to "Büyük").forEach { (s, label) ->
                            val isSel = fontSize == s
                            FilterChip(
                                selected = isSel,
                                onClick  = { fontSize = s; showSize = false; applyToSelection(size = s) },
                                label    = { Text(label, fontSize = 12.sp) },
                                colors   = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Amber.copy(alpha = .2f),
                                    selectedLabelColor     = Amber,
                                ),
                            )
                        }
                    }
                }

                // ── Renk seçici ────────────────────────────────────────────
                if (showColor) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(surface)
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment     = Alignment.CenterVertically,
                    ) {
                        Text("Renk:", color = muted, fontSize = 11.sp)
                        listOf(
                            null              to "Varsayılan",
                            Color(0xFFF59E0B) to "Amber",
                            Color(0xFF3B82F6) to "Mavi",
                            Color(0xFF22C55E) to "Yeşil",
                            Color(0xFFEF4444) to "Kırmızı",
                            Color(0xFFA855F7) to "Mor",
                            Color(0xFFEC4899) to "Pembe",
                            Color(0xFF6B7280) to "Gri",
                            Color(0xFFFF6B35) to "Turuncu",
                            Color(0xFF06B6D4) to "Cam Göbeği",
                        ).forEach { (c, _) ->
                            val isSel = textColor == c
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (c == null) Brush.linearGradient(
                                            listOf(Color(0xFFFF6B6B), Color(0xFF4ECDC4))
                                        )
                                        else Brush.linearGradient(listOf(c, c))
                                    )
                                    .border(2.5.dp, if (isSel) Amber else Color.Transparent, CircleShape)
                                    .clickable(
                                        indication        = null,
                                        interactionSource = remember { MutableInteractionSource() },
                                    ) {
                                        textColor = c; showColor = false
                                        if (c != null) {
                                            applyToSelection(color = c)
                                        } else {
                                            val sel = tfv.selection
                                            if (!sel.collapsed) {
                                                spans = spans.mapNotNull { s ->
                                                    when {
                                                        s.start >= sel.max || s.end <= sel.min -> s
                                                        else -> {
                                                            val u = s.copy(color = null)
                                                            if (u.bold || u.italic || u.under || u.strike
                                                                || u.size != null || u.heading != null) u else null
                                                        }
                                                    }
                                                }
                                                onChange(spansToHtml(tfv.text, spans))
                                            }
                                        }
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                if (c == null) Text("A", color = Color.White, fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // ── Yazı alanı ────────────────────────────────────────────────────
        val annotated = remember(tfv.text, spans) { buildAnnotated(tfv.text, spans) }

        BasicTextField(
            value         = tfv.copy(annotatedString = annotated),
            onValueChange = { new ->
                val newText = new.text
                val lenDiff = newText.length - tfv.text.length
                tfv = new.copy(annotatedString = AnnotatedString(newText))

                if (lenDiff != 0) {
                    val changePos = new.selection.start
                    val insertAt  = if (lenDiff > 0) changePos else changePos - lenDiff
                    spans = shiftSpans(spans, insertAt, lenDiff, newText.length)

                    if (lenDiff > 0 &&
                        (boldOn || italicOn || underOn || strikeOn || fontSize != null || textColor != null)
                    ) {
                        val charStart = changePos - lenDiff
                        val charEnd   = changePos
                        if (charStart >= 0 && charEnd <= newText.length && charStart < charEnd) {
                            spans = spans + RichSpan(
                                start  = charStart, end = charEnd,
                                bold   = boldOn,    italic = italicOn,
                                under  = underOn,   strike = strikeOn,
                                size   = fontSize,  color  = textColor,
                            )
                        }
                    } else if (lenDiff > 0) {
                        // Format aktif değil ama imleç strike/under span'ının içine girebilir.
                        // Yeni karakteri sıfırlayan bir "temiz" span ekle — bulaşmayı önler.
                        val charStart = changePos - lenDiff
                        val charEnd   = changePos
                        val contaminated = spans.any { s ->
                            s.start < charEnd && s.end > charStart && (s.strike || s.under)
                        }
                        if (contaminated && charStart >= 0 && charEnd <= newText.length && charStart < charEnd) {
                            spans = spans + RichSpan(
                                start = charStart, end = charEnd,
                                bold = false, italic = false, under = false, strike = false,
                            )
                        }
                    }
                }
                onChange(spansToHtml(newText, spans))
            },
            cursorBrush   = SolidColor(Amber),
            textStyle     = LocalTextStyle.current.copy(
                color         = onBg,
                fontSize      = 15.sp,
                lineHeight    = 24.sp,
                textDirection = TextDirection.Ltr,
            ),
            modifier      = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .heightIn(min = 160.dp)
                .background(color = surfaceVar,
                    shape = RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp))
                .border(1.dp, if (isFocused) Amber else divider,
                    RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp))
                .padding(14.dp)
                .onFocusChanged { isFocused = it.isFocused },
            decorationBox = { inner ->
                Box {
                    if (tfv.text.isEmpty()) Text(placeholder, color = muted, fontSize = 15.sp)
                    inner()
                }
            },
        )

        val wordCount = tfv.text
            .replace(Regex("\u0000TABLE:.*?\u0000", RegexOption.DOT_MATCHES_ALL), "")
            .trim().split(Regex("\\s+")).count { it.isNotBlank() }
        Text("$wordCount kelime", color = muted, fontSize = 11.sp,
            modifier = Modifier.padding(top = 4.dp, start = 2.dp))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Yardımcı Composable'lar
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun FmtBtn(
    icon    : ImageVector,
    tooltip : String,
    active  : Boolean,
    onBg    : Color,
    onClick : () -> Unit,
) {
    IconButton(
        onClick  = onClick,
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (active) Amber.copy(alpha = .2f) else Color.Transparent),
    ) {
        Icon(imageVector = icon, contentDescription = tooltip,
            tint = if (active) Amber else onBg, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun ThinDivider(color: Color) {
    Box(Modifier.width(1.dp).height(22.dp).background(color))
}
