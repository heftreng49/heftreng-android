package com.heftreng.app.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.heftreng.app.ui.theme.*

// ─────────────────────────────────────────────────────────────────────────────
// Compat shims — eski kod bu fonksiyonları kullanıyor, MD ile saklıyoruz
// ─────────────────────────────────────────────────────────────────────────────

fun spansToHtml(text: String, @Suppress("UNUSED_PARAMETER") spans: Any?): String = text
fun htmlToSpans(html: String): HtmlParseResult = HtmlParseResult(html, emptyList())
fun htmlStrip(html: String): String = html
    .replace(Regex("<br/?>", RegexOption.IGNORE_CASE), "\n")
    .replace(Regex("<[^>]+>"), "")
    .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
    .trimEnd()

data class HtmlParseResult(val text: String, val spans: List<Any>)

// ─────────────────────────────────────────────────────────────────────────────
// Markdown araç çubuğu kısayolları
// ─────────────────────────────────────────────────────────────────────────────

private data class MdAction(val icon: ImageVector, val label: String, val syntax: String, val wrap: Boolean = true)

private val MD_ACTIONS = listOf(
    MdAction(Icons.Filled.Title,            "Başlık H2",     "## ",        wrap = false),
    MdAction(Icons.Filled.TextFields,       "Başlık H3",     "### ",       wrap = false),
    MdAction(Icons.Filled.FormatBold,       "Kalın",         "**"),
    MdAction(Icons.Filled.FormatItalic,     "İtalik",        "_"),
    MdAction(Icons.Filled.FormatStrikethrough, "Üstü Çizili","~~"),
    MdAction(Icons.Filled.Code,             "Kod",           "`"),
    MdAction(Icons.Filled.FormatListBulleted, "Liste",       "- ",         wrap = false),
    MdAction(Icons.Filled.FormatQuote,      "Alıntı",        "> ",         wrap = false),
    MdAction(Icons.Filled.TableChart,       "Tablo",         TABLE_SNIPPET, wrap = false),
)

private const val TABLE_SNIPPET = "| Başlık 1 | Başlık 2 |\n|----------|----------|\n| Hücre 1  | Hücre 2  |\n"

// ─────────────────────────────────────────────────────────────────────────────
// Ana RichTextEditor — artık Markdown editörü
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun RichTextEditor(
    value      : String,
    onChange   : (String) -> Unit,
    modifier   : Modifier = Modifier,
    placeholder: String   = "Markdown ile yazın...",
) {
    var tfv       by remember(value) { mutableStateOf(TextFieldValue(value)) }
    var isFocused by remember { mutableStateOf(false) }
    var preview   by remember { mutableStateOf(false) }

    val surface    = HeftSurface
    val surfaceVar = SurfaceVar
    val onBg       = OnBackground
    val muted      = Muted
    val divider    = Divider

    fun insert(action: MdAction) {
        val sel  = tfv.selection
        val text = tfv.text
        val selected = if (!sel.collapsed) text.substring(sel.min, sel.max) else ""

        val newText: String
        val newCursor: Int

        if (!action.wrap) {
            // Satır başına prefix ekle
            val lineStart = text.lastIndexOf('\n', sel.start - 1).let { if (it < 0) 0 else it + 1 }
            val prefix = action.syntax
            if (action.syntax == TABLE_SNIPPET) {
                val ins = "\n$TABLE_SNIPPET"
                newText   = text.substring(0, sel.start) + ins + text.substring(sel.start)
                newCursor = sel.start + ins.length
            } else {
                newText   = text.substring(0, lineStart) + prefix + text.substring(lineStart)
                newCursor = sel.start + prefix.length
            }
        } else {
            // Seçimi sar
            val s = action.syntax
            newText   = text.substring(0, sel.min) + s + selected + s + text.substring(sel.max)
            newCursor = if (selected.isNotEmpty()) sel.max + s.length * 2 else sel.min + s.length
        }

        tfv = TextFieldValue(newText, TextRange(newCursor))
        onChange(newText)
    }

    Column(modifier = modifier) {
        // ── Tab: Düzenle / Önizle ─────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(surface, RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            listOf("Düzenle" to false, "Önizle" to true).forEach { (label, isPreview) ->
                val active = preview == isPreview
                Text(
                    text     = label,
                    color    = if (active) Amber else muted,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (active) Amber.copy(.1f) else Color.Transparent)
                        .clickable { preview = isPreview }
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }

        if (!preview) {
            // ── Araç çubuğu ───────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(surface)
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                MD_ACTIONS.forEach { action ->
                    IconButton(
                        onClick  = { insert(action) },
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(6.dp)),
                    ) {
                        Icon(action.icon, action.label, tint = onBg, modifier = Modifier.size(17.dp))
                    }
                }
            }

            // ── Yazı alanı ────────────────────────────────────────────────
            BasicTextField(
                value         = tfv,
                onValueChange = { tfv = it; onChange(it.text) },
                cursorBrush   = SolidColor(Amber),
                textStyle     = LocalTextStyle.current.copy(
                    color      = onBg,
                    fontSize   = 14.sp,
                    lineHeight = 22.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 180.dp)
                    .background(surfaceVar, RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp))
                    .border(1.dp, if (isFocused) Amber else divider, RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp))
                    .padding(12.dp)
                    .onFocusChanged { isFocused = it.isFocused },
                decorationBox = { inner ->
                    Box {
                        if (tfv.text.isEmpty()) Text(placeholder, color = muted, fontSize = 14.sp)
                        inner()
                    }
                },
            )

            // Kelime sayısı
            val wordCount = tfv.text.trim().split(Regex("\\s+")).count { it.isNotBlank() }
            Text("$wordCount kelime", color = muted, fontSize = 11.sp,
                modifier = Modifier.padding(top = 4.dp, start = 2.dp))
        } else {
            // ── Önizleme ──────────────────────────────────────────────────
            val md = tfv.text
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 180.dp)
                    .background(surfaceVar, RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp))
                    .border(1.dp, divider, RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp))
                    .padding(12.dp),
            ) {
                if (md.isBlank()) {
                    Text("Önizlenecek içerik yok.", color = muted, fontSize = 13.sp)
                } else {
                    MarkdownView(md)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Basit Markdown önizleme (Markwon AndroidView)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun MarkdownView(markdown: String, modifier: Modifier = Modifier) {
    val context      = androidx.compose.ui.platform.LocalContext.current
    val textColorInt = OnSurface.let { c ->
        android.graphics.Color.argb(
            (c.alpha * 255).toInt(), (c.red * 255).toInt(),
            (c.green * 255).toInt(), (c.blue * 255).toInt(),
        )
    }
    val markwon = remember(context) {
        io.noties.markwon.Markwon.builder(context)
            .usePlugin(io.noties.markwon.ext.strikethrough.StrikethroughPlugin.create())
            .usePlugin(io.noties.markwon.ext.tables.TablePlugin.create(context))
            .usePlugin(io.noties.markwon.linkify.LinkifyPlugin.create())
            .build()
    }
    androidx.compose.ui.viewinterop.AndroidView(
        modifier = modifier,
        factory  = { ctx ->
            android.widget.TextView(ctx).apply {
                setTextColor(textColorInt)
                textSize     = 15f
                setLineSpacing(0f, 1.4f)
                movementMethod = android.text.method.LinkMovementMethod.getInstance()
                setPadding(0, 0, 0, 0)
            }
        },
        update = { tv ->
            markwon.setMarkdown(tv, markdown)
            tv.setTextColor(textColorInt)
        },
    )
}
