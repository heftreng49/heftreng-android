package com.heftreng.app.ui.component

import androidx.compose.foundation.BorderStroke
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

// ── Span verisi ─────────────────────────────────────────────────────────────
data class RichSpan(
    val start  : Int,
    val end    : Int,
    val bold   : Boolean = false,
    val italic : Boolean = false,
    val under  : Boolean = false,
    val strike : Boolean = false,
    val size   : Int?    = null,   // sp cinsinden; null = varsayılan
    val color  : Color?  = null,
)

// ── AnnotatedString oluşturucu ───────────────────────────────────────────────
fun buildAnnotated(text: String, spans: List<RichSpan>): AnnotatedString {
    return buildAnnotatedString {
        append(text)
        spans.forEach { s ->
            val start = s.start.coerceIn(0, text.length)
            val end   = s.end.coerceIn(0, text.length)
            if (start >= end) return@forEach
            addStyle(
                SpanStyle(
                    fontWeight     = if (s.bold)   FontWeight.Bold else null,
                    fontStyle      = if (s.italic) FontStyle.Italic else null,
                    textDecoration = when {
                        s.under  && s.strike -> TextDecoration.combine(
                            listOf(TextDecoration.Underline, TextDecoration.LineThrough)
                        )
                        s.under  -> TextDecoration.Underline
                        s.strike -> TextDecoration.LineThrough
                        else     -> null
                    },
                    fontSize = s.size?.sp ?: 15.sp,
                    color    = s.color ?: Color.Unspecified,
                ),
                start, end,
            )
        }
    }
}

// ── Span listesi → HTML ──────────────────────────────────────────────────────
fun spansToHtml(text: String, spans: List<RichSpan>): String {
    if (text.isBlank() && spans.isEmpty()) return ""
    if (spans.isEmpty()) return "<p>${text.replace("\n", "<br/>")}</p>"

    // Her karakter için style belirle, sonra komşuları birleştir
    val result = buildString {
        append("<p>")
        text.forEachIndexed { i, ch ->
            val active = spans.filter { i >= it.start && i < it.end }
            if (active.isEmpty()) {
                append(ch.htmlEscape())
                return@forEachIndexed
            }
            val bold   = active.any { it.bold }
            val italic = active.any { it.italic }
            val under  = active.any { it.under }
            val strike = active.any { it.strike }
            val size   = active.mapNotNull { it.size }.maxOrNull()
            val color  = active.mapNotNull { it.color }.lastOrNull()

            val styleAttr = buildString {
                if (size  != null) append("font-size:${size}px;")
                if (color != null) append("color:${color.toHex()};")
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
        }
        append("</p>")
    }
    return result
}

private fun Char.htmlEscape(): String = when (this) {
    '<'  -> "&lt;"
    '>'  -> "&gt;"
    '&'  -> "&amp;"
    else -> this.toString()
}

private fun Color.toHex(): String {
    val r = (red   * 255).toInt()
    val g = (green * 255).toInt()
    val b = (blue  * 255).toInt()
    return "#%02x%02x%02x".format(r, g, b)
}

fun htmlStrip(html: String): String = html
    .replace(Regex("<br/?>", RegexOption.IGNORE_CASE), "\n")
    .replace(Regex("<hr/?>", RegexOption.IGNORE_CASE), "\n───────────────\n")
    .replace(Regex("<p>",    RegexOption.IGNORE_CASE), "")
    .replace(Regex("</p>",   RegexOption.IGNORE_CASE), "\n")
    .replace(Regex("<[^>]+>"), "")
    .replace("&amp;",  "&")
    .replace("&lt;",   "<")
    .replace("&gt;",   ">")
    .replace("&nbsp;", " ")
    .trimEnd()

// ── Ana RichTextEditor ───────────────────────────────────────────────────────
@Composable
fun RichTextEditor(
    value      : String,
    onChange   : (String) -> Unit,
    modifier   : Modifier = Modifier,
    placeholder: String   = "İçeriğinizi buraya yazın...",
) {
    var tfv       by remember(value.hashCode()) {
        mutableStateOf(TextFieldValue(htmlStrip(value)))
    }
    var spans     by remember { mutableStateOf(listOf<RichSpan>()) }
    var isFocused by remember { mutableStateOf(false) }

    // Aktif format bayrakları
    var boldOn    by remember { mutableStateOf(false) }
    var italicOn  by remember { mutableStateOf(false) }
    var underOn   by remember { mutableStateOf(false) }
    var strikeOn  by remember { mutableStateOf(false) }
    var fontSize  by remember { mutableStateOf<Int?>(null) }
    var textColor by remember { mutableStateOf<Color?>(null) }
    var showSize  by remember { mutableStateOf(false) }
    var showColor by remember { mutableStateOf(false) }

    fun applyToSelection(
        bold: Boolean? = null, italic: Boolean? = null,
        under: Boolean? = null, strike: Boolean? = null,
        size: Int? = null, color: Color? = null,
    ) {
        val sel = tfv.selection
        if (sel.collapsed) return
        val newSpan = RichSpan(
            start  = sel.start,
            end    = sel.end,
            bold   = bold   ?: boldOn,
            italic = italic ?: italicOn,
            under  = under  ?: underOn,
            strike = strike ?: strikeOn,
            size   = size   ?: fontSize,
            color  = color  ?: textColor,
        )
        // Aynı aralıktaki eski span'ları temizle, yenisini ekle
        spans = spans.filter { it.start >= sel.end || it.end <= sel.start } + newSpan
        onChange(spansToHtml(tfv.text, spans))
    }

    Column(modifier = modifier.fillMaxHeight()) {

        // ── Araç çubuğu ──────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                .background(Color(0xFF1C1C1E)),
        ) {
            // Ana butonlar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                FmtBtn(Icons.Default.FormatBold,          "Kalın",        boldOn)   {
                    boldOn = !boldOn; applyToSelection(bold = boldOn)
                }
                FmtBtn(Icons.Default.FormatItalic,        "İtalik",       italicOn) {
                    italicOn = !italicOn; applyToSelection(italic = italicOn)
                }
                FmtBtn(Icons.Default.FormatUnderlined,    "Altı çizili",  underOn)  {
                    underOn = !underOn; applyToSelection(under = underOn)
                }
                FmtBtn(Icons.Default.FormatStrikethrough, "Üstü çizili",  strikeOn) {
                    strikeOn = !strikeOn; applyToSelection(strike = strikeOn)
                }

                ThinDivider()

                // Font boyutu butonu
                TextButton(
                    onClick        = { showSize = !showSize; showColor = false },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    modifier       = Modifier
                        .height(36.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (showSize) Amber.copy(.15f) else Color.Transparent),
                ) {
                    Icon(Icons.Default.FormatSize, null,
                        tint = if (showSize) Amber else Color.White,
                        modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        if (fontSize != null) "${fontSize}px" else "Boyut",
                        color    = if (showSize) Amber else Color.White,
                        fontSize = 12.sp,
                    )
                }

                ThinDivider()

                // Renk butonu
                val colorBrush = if (textColor != null)
                    Brush.linearGradient(listOf(textColor!!, textColor!!))
                else
                    Brush.linearGradient(listOf(Color(0xFFFF6B6B), Color(0xFF4ECDC4), Color(0xFF45B7D1)))
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(colorBrush)
                        .border(2.dp, if (showColor) Amber else Color.Transparent, CircleShape)
                        .clickable(
                            indication        = null,
                            interactionSource = remember { MutableInteractionSource() },
                        ) { showColor = !showColor; showSize = false },
                    contentAlignment = Alignment.Center,
                ) {
                    if (textColor == null) {
                        Text("A", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                ThinDivider()

                FmtBtn(Icons.Default.FormatQuote, "Alıntı", false) {
                    applyToSelection()
                }
                FmtBtn(Icons.Default.FormatClear, "Temizle", false) {
                    val sel = tfv.selection
                    if (!sel.collapsed) {
                        spans = spans.filter { it.start >= sel.end || it.end <= sel.start }
                        onChange(spansToHtml(tfv.text, spans))
                    }
                    boldOn = false; italicOn = false; underOn = false; strikeOn = false
                    fontSize = null; textColor = null
                }
            }

            // Font boyutu seçici
            if (showSize) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF2C2C2E))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                ) {
                    listOf(
                        12 to "Minik",
                        14 to "Küçük",
                        16 to "Normal",
                        20 to "Büyük",
                        24 to "Başlık",
                        30 to "Dev",
                    ).forEach { (s, label) ->
                        val sel = s == fontSize
                        TextButton(
                            onClick        = {
                                fontSize  = s
                                showSize  = false
                                applyToSelection(size = s)
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier       = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (sel) Amber.copy(.2f) else Color.Transparent),
                        ) {
                            Text(
                                label,
                                color    = if (sel) Amber else Color.White,
                                fontSize = (s * 0.55f).sp,
                                fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                            )
                        }
                    }
                }
            }

            // Renk seçici
            if (showColor) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF2C2C2E))
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                ) {
                    Text("Renk:", color = Color(0xFF888888), fontSize = 11.sp)
                    listOf(
                        null                  to "Varsayılan",
                        Color(0xFFFFFFFF)     to "Beyaz",
                        Color(0xFF111111)     to "Siyah",
                        Color(0xFFF59E0B)     to "Amber",
                        Color(0xFF3B82F6)     to "Mavi",
                        Color(0xFF22C55E)     to "Yeşil",
                        Color(0xFFEF4444)     to "Kırmızı",
                        Color(0xFFA855F7)     to "Mor",
                        Color(0xFFEC4899)     to "Pembe",
                        Color(0xFF6B7280)     to "Gri",
                        Color(0xFFFF6B35)     to "Turuncu",
                        Color(0xFF06B6D4)     to "Cam Göbeği",
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
                                    if (c != null) applyToSelection(color = c)
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (c == null) Text("A", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // ── Yazı alanı ────────────────────────────────────────────────────
        BasicTextField(
            value         = tfv.copy(annotatedString = buildAnnotated(tfv.text, spans)),
            onValueChange = { new ->
                val oldLen = tfv.text.length
                val newLen = new.text.length
                val diff   = newLen - oldLen
                tfv = new

                if (diff != 0) {
                    val cursor = new.selection.start
                    spans = spans.mapNotNull { s ->
                        when {
                            s.end <= (cursor - diff.coerceAtLeast(0)) -> s
                            s.start >= cursor                          -> s.copy(
                                start = (s.start + diff).coerceAtLeast(0),
                                end   = (s.end   + diff).coerceAtLeast(0),
                            )
                            else -> {
                                val ne = (s.end + diff).coerceAtLeast(s.start)
                                if (ne <= s.start) null else s.copy(end = ne)
                            }
                        }
                    }.filter { it.start < it.end && it.end <= new.text.length }
                }
                onChange(spansToHtml(new.text, spans))
            },
            cursorBrush   = SolidColor(Amber),
            textStyle     = LocalTextStyle.current.copy(
                color         = OnBackground,
                fontSize      = 15.sp,
                lineHeight    = 24.sp,
                textDirection = TextDirection.Ltr,
            ),
            modifier      = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .heightIn(min = 160.dp)
                .background(
                    color = SurfaceVar,
                    shape = RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp),
                )
                .border(
                    width = 1.dp,
                    color = if (isFocused) Amber else Divider,
                    shape = RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp),
                )
                .padding(14.dp)
                .onFocusChanged { isFocused = it.isFocused },
            decorationBox = { inner ->
                Box {
                    if (tfv.text.isEmpty()) {
                        Text(placeholder, color = Muted, fontSize = 15.sp)
                    }
                    inner()
                }
            },
        )

        // Kelime sayacı
        val wordCount = tfv.text.trim().split(Regex("\\s+")).count { it.isNotBlank() }
        Text(
            "$wordCount kelime",
            color    = Muted,
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 4.dp, start = 2.dp),
        )
    }
}

// ── Yardımcı composable'lar ───────────────────────────────────────────────────

@Composable
private fun FmtBtn(icon: ImageVector, tooltip: String, active: Boolean, onClick: () -> Unit) {
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
            tint               = if (active) Amber else Color.White,
            modifier           = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun ThinDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(22.dp)
            .background(Color(0xFF3A3A3C)),
    )
}
