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
                    fontSize = s.size?.sp ?: androidx.compose.ui.unit.TextUnit.Unspecified,
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
    // FIX: remember(value) her onChange'de cursor pozisyonunu sıfırlıyordu.
    // Şimdi sadece ilk açılışta (rememberSaveable boş) ya da dışarıdan gerçek
    // bir reset geldiğinde (örn. "yeni bölüm aç") TFV güncelleniyor.
    val stripped = remember(value) { htmlStrip(value) }
    var tfv by remember { mutableStateOf(TextFieldValue(text = stripped)) }

    // Dışarıdan farklı bir metin gelirse (örn. düzenle butonuna basıldı) sync et
    LaunchedEffect(stripped) {
        if (tfv.text != stripped) {
            tfv      = TextFieldValue(text = stripped)
            spans    = emptyList()          // önceki span listesini temizle
            boldOn   = false                // format bayraklarını sıfırla
            italicOn = false
            underOn  = false
            strikeOn = false
            fontSize  = null
            textColor = null
        }
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

    // Tema renkleri
    val surface    = HeftSurface
    val surfaceVar = SurfaceVar
    val onBg       = OnBackground
    val muted      = Muted
    val divider    = Divider

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
        spans = spans.filter { it.start >= sel.end || it.end <= sel.start } + newSpan
        onChange(spansToHtml(tfv.text, spans))
    }

    Column(modifier = modifier.fillMaxHeight()) {

        // ── Araç çubuğu ──────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                .background(surfaceVar),   // FIX: hardcoded siyah yerine tema rengi
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
                FmtBtn(Icons.Default.FormatBold,          "Kalın",        boldOn,   onBg) {
                    boldOn = !boldOn; applyToSelection(bold = boldOn)
                }
                FmtBtn(Icons.Default.FormatItalic,        "İtalik",       italicOn, onBg) {
                    italicOn = !italicOn; applyToSelection(italic = italicOn)
                }
                FmtBtn(Icons.Default.FormatUnderlined,    "Altı çizili",  underOn,  onBg) {
                    underOn = !underOn; applyToSelection(under = underOn)
                }
                FmtBtn(Icons.Default.FormatStrikethrough, "Üstü çizili",  strikeOn, onBg) {
                    strikeOn = !strikeOn; applyToSelection(strike = strikeOn)
                }

                ThinDivider(divider)

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
                        tint = if (showSize) Amber else onBg,   // FIX: tema rengi
                        modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        if (fontSize != null) "${fontSize}px" else "Boyut",
                        color    = if (showSize) Amber else onBg,   // FIX: tema rengi
                        fontSize = 12.sp,
                    )
                }

                ThinDivider(divider)

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

                ThinDivider(divider)

                FmtBtn(Icons.Default.FormatQuote, "Alıntı", false, onBg) {
                    applyToSelection()
                }
                FmtBtn(Icons.Default.FormatClear, "Temizle", false, onBg) {
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
                        .background(surface)
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
                                color    = if (sel) Amber else onBg,   // FIX: tema rengi
                                fontSize = (s * 0.55f).sp,
                                fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                            )
                        }
                    }
                }
            }

            // Renk seçici
            // FIX: "Beyaz" seçeneği kaldırıldı — açık temada beyaz metin görünmez.
            // Tema-güvenli renkler kullanılıyor (koyu ve açık temada kontrast sağlar).
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
                        null                  to "Varsayılan",
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
                                    else {
                                        // Varsayılan seçilince seçim aralığındaki renk span'larını sil
                                        val sel = tfv.selection
                                        if (!sel.collapsed) {
                                            spans = spans.mapNotNull { s ->
                                                when {
                                                    s.start >= sel.end || s.end <= sel.start -> s
                                                    else -> s.copy(color = null).takeIf {
                                                        it.bold || it.italic || it.under || it.strike || it.size != null
                                                    }
                                                }
                                            }
                                            onChange(spansToHtml(tfv.text, spans))
                                        }
                                    }
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
        val annotated = buildAnnotated(tfv.text, spans)
        BasicTextField(
            value         = tfv.copy(annotatedString = annotated),
            onValueChange = { new ->
                val oldText = tfv.text
                val newText = new.text
                val diff    = newText.length - oldText.length
                // FIX: annotatedString'i temizleyerek cursor pozisyonunu koru
                tfv = new.copy(annotatedString = AnnotatedString(new.text))

                if (diff > 0 && (boldOn || italicOn || underOn || strikeOn || fontSize != null || textColor != null)) {
                    // Yeni karakter yazıldı ve aktif format var — o karaktere span uygula
                    val cursor = new.selection.start
                    val charStart = cursor - diff
                    val charEnd   = cursor
                    if (charStart >= 0 && charEnd <= newText.length && charStart < charEnd) {
                        val newSpan = RichSpan(
                            start  = charStart, end = charEnd,
                            bold   = boldOn, italic = italicOn,
                            under  = underOn, strike = strikeOn,
                            size   = fontSize, color = textColor,
                        )
                        spans = spans + newSpan
                    }
                }

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
                    }.filter { it.start < it.end && it.end <= newText.length }
                }
                onChange(spansToHtml(newText, spans))
            },
            cursorBrush   = SolidColor(Amber),
            textStyle     = LocalTextStyle.current.copy(
                color         = onBg,
                fontSize      = 15.sp,
                lineHeight    = 24.sp,
                textDirection = TextDirection.Ltr,  // Kurmancî Latin — her zaman LTR
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

// ── Yardımcı composable'lar ───────────────────────────────────────────────────

@Composable
private fun FmtBtn(
    icon    : ImageVector,
    tooltip : String,
    active  : Boolean,
    onBg    : Color,     // FIX: tema rengi parametre olarak alınıyor
    onClick : () -> Unit,
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
            tint               = if (active) Amber else onBg,   // FIX: tema rengi
            modifier           = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun ThinDivider(color: Color) {   // FIX: tema rengi parametre olarak alınıyor
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(22.dp)
            .background(color),
    )
}
