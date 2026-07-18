package com.heftreng.app.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.heftreng.app.ui.theme.*

// ─────────────────────────────────────────────────────────────────────────────
// HTML içerik renderer  —  KurdiScreen / SinglePostScreen'de kullanılır
//
//  Desteklenen bloklar:
//   • <h2> / <h3>          → büyük/orta başlık (üstü çizili YOK)
//   • <p> / <br/>          → paragraf / satır sonu
//   • <b> <i> <u> <s>      → satır içi format
//   • <span style="...">   → renk + font-size
//   • <table>…</table>     → tablo (hücre düzenleme yok; sadece görüntüleme)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * HTML içeriği Compose ile render eder.
 *
 * Kullanım:
 * ```
 * HtmlContent(html = ders.aciklama)
 * ```
 */
@Composable
fun HtmlContent(
    html     : String,
    modifier : Modifier = Modifier,
    baseColor: Color    = OnBackground,
    baseFontSize: Float = 15f,
) {
    if (html.isBlank()) return

    val blocks = remember(html) { parseHtmlBlocks(html) }

    Column(
        modifier            = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        for (block in blocks) {
            when (block) {
                is HtmlBlock.Heading -> {
                    val size = if (block.level == 2) 20.sp else 17.sp
                    Text(
                        text  = block.text,
                        style = TextStyle(
                            fontWeight = FontWeight.Bold,
                            fontSize   = size,
                            color      = baseColor,
                            // textDecoration = null → üstü çizili kesinlikle yok
                        ),
                    )
                }

                is HtmlBlock.Paragraph -> {
                    MarkdownView(
                        markdown = block.html,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                is HtmlBlock.Table -> {
                    HtmlTable(
                        rows      = block.rows,
                        baseColor = baseColor,
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Tablo Composable
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun HtmlTable(
    rows     : List<List<TableCell>>,
    baseColor: Color,
) {
    if (rows.isEmpty()) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Divider, RoundedCornerShape(6.dp)),
    ) {
        rows.forEachIndexed { rowIdx, cells ->
            Row(modifier = Modifier.fillMaxWidth()) {
                cells.forEachIndexed { colIdx, cell ->
                    val isHeader = cell.isHeader
                    val weight   = 1f / cells.size.toFloat()
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(
                                if (isHeader) HeftSurface else SurfaceVar
                            )
                            .then(
                                if (colIdx < cells.size - 1)
                                    Modifier.border(width = 0.5.dp, color = Divider,
                                        shape = RoundedCornerShape(0.dp))
                                else Modifier
                            )
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                    ) {
                        Text(
                            text  = cell.text,
                            style = TextStyle(
                                fontSize   = 13.sp,
                                fontWeight = if (isHeader) FontWeight.SemiBold else FontWeight.Normal,
                                color      = baseColor,
                            ),
                        )
                    }
                }
            }
            // Satır ayraç çizgisi (son satır hariç)
            if (rowIdx < rows.size - 1) {
                Box(Modifier.fillMaxWidth().height(0.5.dp).background(Divider))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// HTML blok parser  (internal)
// ─────────────────────────────────────────────────────────────────────────────

private sealed class HtmlBlock {
    data class Heading(val level: Int, val text: String) : HtmlBlock()
    data class Paragraph(val html: String)               : HtmlBlock()
    data class Table(val rows: List<List<TableCell>>)    : HtmlBlock()
}

private data class TableCell(val text: String, val isHeader: Boolean)

private fun parseHtmlBlocks(html: String): List<HtmlBlock> {
    val blocks = mutableListOf<HtmlBlock>()

    // Tüm üst düzey blokları sırayla bul:
    // <h2>, <h3>, <table>, geri kalan her şey → Paragraph
    val blockRegex = Regex(
        "(<h([23])[^>]*>.*?</h\\2>)" +         // heading
        "|(<table[^>]*>.*?</table>)" +           // table
        "|((?:(?!<h[23]|<table).)+)",            // diğer (paragraf)
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )

    for (m in blockRegex.findAll(html)) {
        val full = m.value.trim()
        if (full.isBlank()) continue

        when {
            // Başlık
            m.groupValues[1].isNotEmpty() -> {
                val level = m.groupValues[2].toIntOrNull() ?: 2
                val inner = full
                    .replace(Regex("<h$level[^>]*>", RegexOption.IGNORE_CASE), "")
                    .replace(Regex("</h$level>",    RegexOption.IGNORE_CASE), "")
                    .replace(Regex("<br\\s*/?>",     RegexOption.IGNORE_CASE), "\n")
                    .replace(Regex("<[^>]+>"), "")
                    .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                    .trim()
                if (inner.isNotBlank()) blocks.add(HtmlBlock.Heading(level, inner))
            }

            // Tablo
            m.groupValues[3].isNotEmpty() -> {
                val tableRows = parseTableRows(full)
                if (tableRows.isNotEmpty()) blocks.add(HtmlBlock.Table(tableRows))
            }

            // Paragraf / düz metin
            else -> {
                if (full.isNotBlank()) blocks.add(HtmlBlock.Paragraph(full))
            }
        }
    }

    return blocks
}

private fun parseTableRows(tableHtml: String): List<List<TableCell>> {
    val rowRegex  = Regex("<tr[^>]*>(.*?)</tr>",  setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    val cellRegex = Regex("<(th|td)[^>]*>(.*?)</\\1>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))

    return rowRegex.findAll(tableHtml).map { rowMatch ->
        cellRegex.findAll(rowMatch.groupValues[1]).map { cellMatch ->
            val isHeader = cellMatch.groupValues[1].lowercase() == "th"
            val rawText  = cellMatch.groupValues[2]
                .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
                .replace(Regex("<[^>]+>"), "")
                .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&nbsp;", " ").trim()
            TableCell(rawText, isHeader)
        }.toList()
    }.filter { it.isNotEmpty() }.toList()
}
