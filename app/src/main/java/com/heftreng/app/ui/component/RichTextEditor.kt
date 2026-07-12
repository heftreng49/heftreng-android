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
 * Bu model değişmedi; Firestore/Supabase'de saklanan HTML ile uyumlu.
 */
data class RichSpan(
    val start  : Int,
    val end    : Int,
    val bold   : Boolean = false,
    val italic : Boolean = false,
    val under  : Boolean = false,
    val strike : Boolean = false,
    val size   : Int?    = null,   // sp; null = varsayılan
    val color  : Color?  = null,
)

// ─────────────────────────────────────────────────────────────────────────────
// HTML ↔ Span dönüşümleri  (public — KurdiScreen ve AdminScreen bunları kullanır)
// ─────────────────────────────────────────────────────────────────────────────

/** Span listesi + düz metin → tek <p> içinde HTML */
fun spansToHtml(text: String, spans: List<RichSpan>): String {
    if (text.isBlank() && spans.isEmpty()) return ""
    if (spans.isEmpty()) return "<p>${text.replace("\n", "<br/>")}</p>"

    // Her karakter için hangi span'ların aktif olduğunu bul; aynı bloğu birleştir.
    return buildString {
        append("<p>")
        var i = 0
        while (i < text.length) {
            val ch      = text[i]
            val active  = spans.filter { i >= it.start && i < it.end }
            val bold    = active.any { it.bold }
            val italic  = active.any { it.italic }
            val under   = active.any { it.under }
            val strike  = active.any { it.strike }
            val size    = active.mapNotNull { it.size }.maxOrNull()
            val color   = active.mapNotNull { it.color }.lastOrNull()

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
        append("</p>")
    }
}

data class HtmlParseResult(val text: String, val spans: List<RichSpan>)

/** HTML → düz metin + span listesi.
 *  Desteklenen tag'ler: b, strong, i, em, u, s, strike, span(style).
 */
fun htmlToSpans(html: String): HtmlParseResult {
    if (html.isBlank()) return HtmlParseResult("", emptyList())

    // <br> / <p> → newline; entity decode
    val normalized = html
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

    val plain   = StringBuilder()
    val spans   = mutableListOf<RichSpan>()
    val stack   = mutableListOf<OpenTag>()
    var cursor  = 0

    for (match in tagRegex.findAll(normalized)) {
        // Tag öncesi düz metin ekle
        if (match.range.first > cursor) {
            plain.append(normalized.substring(cursor, match.range.first))
        }
        cursor = match.range.last + 1

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
            // En yakın eşleşen açık tag'i bul (last match first)
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

    // Son kalan düz metin
    if (cursor < normalized.length) plain.append(normalized.substring(cursor))

    // Kapatılmamış tag'leri kapat
    stack.forEach { open ->
        val end = plain.length
        if (end > open.startIndex) spans.add(open.proto.copy(start = open.startIndex, end = end))
    }

    // Bilinmeyen tag kalıntılarını temizle
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

/** HTML'yi düz metne indirger (önizleme, bildirim özeti için). */
fun htmlStrip(html: String): String = html
    .replace(Regex("<br/?>",  RegexOption.IGNORE_CASE), "\n")
    .replace(Regex("<hr/?>",  RegexOption.IGNORE_CASE), "\n───────────────\n")
    .replace(Regex("<p>",     RegexOption.IGNORE_CASE), "")
    .replace(Regex("</p>",    RegexOption.IGNORE_CASE), "\n")
    .replace(Regex("<[^>]+>"), "")
    .replace("&amp;",  "&")
    .replace("&lt;",   "<")
    .replace("&gt;",   ">")
    .replace("&nbsp;", " ")
    .trimEnd()

// ─────────────────────────────────────────────────────────────────────────────
// AnnotatedString oluşturucu  (public — önizleme bileşenleri kullanır)
// ─────────────────────────────────────────────────────────────────────────────

fun buildAnnotated(text: String, spans: List<RichSpan>): AnnotatedString =
    buildAnnotatedString {
        append(text)
        spans.forEach { s ->
            val start = s.start.coerceIn(0, text.length)
            val end   = s.end.coerceIn(0, text.length)
            if (start >= end) return@forEach
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
                        else     -> null
                    },
                    fontSize = s.size?.sp ?: androidx.compose.ui.unit.TextUnit.Unspecified,
                    color    = s.color ?: Color.Unspecified,
                ),
                start, end,
            )
        }
    }

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
// Span güncelleme yardımcısı
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Metin değiştiğinde span listesini metin farkına göre güvenle kaydırır.
 *
 * [insertAt]  → yeni karakterlerin eklendiği konum  (diff > 0 ise)
 * [deleteAt]  → silinen karakterlerin başlangıç konumu (diff < 0 ise)
 * [diff]      → uzunluk farkı (pozitif = ekleme, negatif = silme)
 */
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
            // Ekleme: [insertAt] noktasından itibaren kaydır
            newStart = if (s.start >= insertAt) s.start + diff else s.start
            newEnd   = if (s.end   >  insertAt) s.end   + diff else s.end
        } else {
            // Silme: [insertAt .. insertAt - diff] aralığı silindi
            val delStart = insertAt + diff   // diff negatif, bu yüzden gerçek başlangıç
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
        val clampedStart = newStart.coerceIn(0, newLen)
        val clampedEnd   = newEnd.coerceIn(0, newLen)
        if (clampedStart < clampedEnd) s.copy(start = clampedStart, end = clampedEnd) else null
    }
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
    // ── İlk parse ─────────────────────────────────────────────────────────
    val initialParsed = remember(value) { htmlToSpans(value) }

    var tfv       by remember { mutableStateOf(TextFieldValue(text = initialParsed.text)) }
    var spans     by remember { mutableStateOf(initialParsed.spans) }
    var isFocused by remember { mutableStateOf(false) }

    // Aktif format bayrakları (imleç konumundaki format)
    var boldOn    by remember { mutableStateOf(false) }
    var italicOn  by remember { mutableStateOf(false) }
    var underOn   by remember { mutableStateOf(false) }
    var strikeOn  by remember { mutableStateOf(false) }
    var fontSize  by remember { mutableStateOf<Int?>(null) }
    var textColor by remember { mutableStateOf<Color?>(null) }
    var showSize  by remember { mutableStateOf(false) }
    var showColor by remember { mutableStateOf(false) }

    // Dışarıdan farklı bir value gelirse (örn. "Düzenle" butonu) yeniden senkronize et
    LaunchedEffect(initialParsed) {
        if (tfv.text != initialParsed.text) {
            tfv      = TextFieldValue(text = initialParsed.text)
            spans    = initialParsed.spans
            boldOn   = false; italicOn = false; underOn = false; strikeOn = false
            fontSize = null;  textColor = null
        }
    }

    // Tema renkleri
    val surface    = HeftSurface
    val surfaceVar = SurfaceVar
    val onBg       = OnBackground
    val muted      = Muted
    val divider    = Divider

    // ── Seçili aralığa format uygula ──────────────────────────────────────
    fun applyToSelection(
        bold   : Boolean? = null,
        italic : Boolean? = null,
        under  : Boolean? = null,
        strike : Boolean? = null,
        size   : Int?     = null,
        color  : Color?   = null,
    ) {
        val sel = tfv.selection
        if (sel.collapsed) return          // seçim yok → sadece flag değişir
        val start = sel.min
        val end   = sel.max

        // Seçimle çakışan eski span'ları böl / kırp / çıkar
        val updated = mutableListOf<RichSpan>()
        for (s in spans) {
            when {
                s.end <= start || s.start >= end -> updated.add(s)   // çakışmıyor
                else -> {
                    // Seçim öncesi kısım
                    if (s.start < start) updated.add(s.copy(end = start))
                    // Seçim sonrası kısım
                    if (s.end > end)     updated.add(s.copy(start = end))
                    // Seçim içindeki kısım — aynı türü güncelle, diğerini koru
                    val inside = s.copy(
                        start  = maxOf(s.start, start),
                        end    = minOf(s.end,   end),
                        bold   = bold   ?: s.bold,
                        italic = italic ?: s.italic,
                        under  = under  ?: s.under,
                        strike = strike ?: s.strike,
                        size   = size   ?: s.size,
                        color  = color  ?: s.color,
                    )
                    if (inside.bold || inside.italic || inside.under || inside.strike
                        || inside.size != null || inside.color != null
                    ) {
                        updated.add(inside)
                    }
                }
            }
        }

        // Eğer seçim aralığı hiç span'la kaplı değilse yeni span ekle
        val covered = updated.any { it.start <= start && it.end >= end }
        if (!covered && (bold == true || italic == true || under == true || strike == true
                || size != null || color != null)
        ) {
            updated.add(
                RichSpan(
                    start  = start, end = end,
                    bold   = bold   == true,
                    italic = italic == true,
                    under  = under  == true,
                    strike = strike == true,
                    size   = size,
                    color  = color,
                )
            )
        }

        spans = updated.filter { it.start < it.end }
        onChange(spansToHtml(tfv.text, spans))
    }

    // ── Toggle: seçimde format var mı? ────────────────────────────────────
    fun selectionHas(
        check: (RichSpan) -> Boolean,
    ): Boolean {
        val sel = tfv.selection
        if (sel.collapsed) return false
        return spans.any { s ->
            s.start < sel.max && s.end > sel.min && check(s)
        }
    }

    // ── Toolbar toggle: format varsa kaldır, yoksa uygula ─────────────────
    fun toggleFormat(
        flag     : Boolean,
        setFlag  : (Boolean) -> Unit,
        checkSpan: (RichSpan) -> Boolean,
        applySpan: (Boolean) -> Unit,
    ) {
        val sel = tfv.selection
        if (!sel.collapsed) {
            val hasFormat = selectionHas(checkSpan)
            applySpan(!hasFormat)
        } else {
            val newFlag = !flag
            setFlag(newFlag)
        }
    }

    // ─────────────────────────────────────────────────────────────────────
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
                    // Kalın
                    FmtBtn(Icons.Filled.FormatBold, "Kalın", boldOn, onBg) {
                        toggleFormat(boldOn, { boldOn = it }, { it.bold }) { v ->
                            boldOn = v
                            applyToSelection(bold = v)
                        }
                    }
                    // İtalik
                    FmtBtn(Icons.Filled.FormatItalic, "İtalik", italicOn, onBg) {
                        toggleFormat(italicOn, { italicOn = it }, { it.italic }) { v ->
                            italicOn = v
                            applyToSelection(italic = v)
                        }
                    }
                    // Altı çizili
                    FmtBtn(Icons.Filled.FormatUnderlined, "Altı Çizili", underOn, onBg) {
                        toggleFormat(underOn, { underOn = it }, { it.under }) { v ->
                            underOn = v
                            applyToSelection(under = v)
                        }
                    }
                    // Üstü çizili
                    FmtBtn(Icons.Filled.FormatStrikethrough, "Üstü Çizili", strikeOn, onBg) {
                        toggleFormat(strikeOn, { strikeOn = it }, { it.strike }) { v ->
                            strikeOn = v
                            applyToSelection(strike = v)
                        }
                    }

                    ThinDivider(divider)

                    // Yazı boyutu
                    FmtBtn(Icons.Filled.FormatSize, "Yazı Boyutu", showSize, onBg) {
                        showSize  = !showSize
                        showColor = false
                    }
                    // Renk
                    FmtBtn(Icons.Filled.Palette, "Renk", showColor, onBg) {
                        showColor = !showColor
                        showSize  = false
                    }

                    ThinDivider(divider)

                    // Tümünü temizle
                    FmtBtn(Icons.Filled.FormatClear, "Formatı Temizle", false, onBg) {
                        val sel = tfv.selection
                        if (!sel.collapsed) {
                            spans = spans.mapNotNull { s ->
                                when {
                                    s.end <= sel.min || s.start >= sel.max -> s
                                    else -> null          // seçim içindeki span'ları sil
                                }
                            }
                            onChange(spansToHtml(tfv.text, spans))
                        } else {
                            spans = emptyList()
                            boldOn = false; italicOn = false; underOn = false; strikeOn = false
                            fontSize = null; textColor = null
                            onChange(spansToHtml(tfv.text, emptyList()))
                        }
                        showSize  = false
                        showColor = false
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
                               20 to "Büyük", 24 to "Başlık").forEach { (s, label) ->
                            val isSel = fontSize == s
                            FilterChip(
                                selected = isSel,
                                onClick  = {
                                    fontSize = s
                                    showSize = false
                                    applyToSelection(size = s)
                                },
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
                            null                 to "Varsayılan",
                            Color(0xFFF59E0B)    to "Amber",
                            Color(0xFF3B82F6)    to "Mavi",
                            Color(0xFF22C55E)    to "Yeşil",
                            Color(0xFFEF4444)    to "Kırmızı",
                            Color(0xFFA855F7)    to "Mor",
                            Color(0xFFEC4899)    to "Pembe",
                            Color(0xFF6B7280)    to "Gri",
                            Color(0xFFFF6B35)    to "Turuncu",
                            Color(0xFF06B6D4)    to "Cam Göbeği",
                        ).forEach { (c, name) ->
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
                                        textColor = c
                                        showColor = false
                                        if (c != null) {
                                            applyToSelection(color = c)
                                        } else {
                                            // Varsayılan: seçim aralığından renk span'larını çıkar
                                            val sel = tfv.selection
                                            if (!sel.collapsed) {
                                                spans = spans.mapNotNull { s ->
                                                    when {
                                                        s.start >= sel.max || s.end <= sel.min -> s
                                                        else -> {
                                                            val updated = s.copy(color = null)
                                                            if (updated.bold || updated.italic || updated.under
                                                                || updated.strike || updated.size != null
                                                            ) updated else null
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
                val oldText  = tfv.text
                val newText  = new.text
                val lenDiff  = newText.length - oldText.length

                // annotatedString temizlenerek yazılır (imleç bozulmasın)
                tfv = new.copy(annotatedString = AnnotatedString(newText))

                if (lenDiff != 0) {
                    // Değişim noktasını bul
                    val changePos = new.selection.start   // yeni imleç konumu
                    val insertAt  = if (lenDiff > 0) changePos else changePos - lenDiff

                    spans = shiftSpans(spans, insertAt, lenDiff, newText.length)

                    // Ekleme: aktif format varsa yeni karakterlere uygula
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
                .background(
                    color = surfaceVar,
                    shape = RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp),
                )
                .border(
                    width = 1.dp,
                    color = if (isFocused) Amber else divider,
                    shape = RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp),
                )
                .padding(14.dp)
                .onFocusChanged { isFocused = it.isFocused },
            decorationBox = { inner ->
                Box {
                    if (tfv.text.isEmpty()) {
                        Text(placeholder, color = muted, fontSize = 15.sp)
                    }
                    inner()
                }
            },
        )

        // Kelime sayacı
        val wordCount = tfv.text.trim().split(Regex("\\s+")).count { it.isNotBlank() }
        Text(
            "$wordCount kelime",
            color    = muted,
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 4.dp, start = 2.dp),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Yardımcı Composable'lar
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun FmtBtn(
    icon     : ImageVector,
    tooltip  : String,
    active   : Boolean,
    onBg     : Color,
    onClick  : () -> Unit,
) {
    IconButton(
        onClick  = onClick,
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (active) Amber.copy(alpha = .2f) else Color.Transparent),
    ) {
        Icon(
            imageVector        = icon,
            contentDescription = tooltip,
            tint               = if (active) Amber else onBg,
            modifier           = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun ThinDivider(color: Color) {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(22.dp)
            .background(color),
    )
}
