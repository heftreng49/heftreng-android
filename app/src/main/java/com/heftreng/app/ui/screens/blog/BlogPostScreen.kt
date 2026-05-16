package com.heftreng.app.ui.screens.blog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.heftreng.app.ui.theme.*
import com.heftreng.app.viewmodel.BlogViewModel

// ═══════════════════════════════════════════════════════════════════════════════
// BLOG YAZI DETAY — TAM NATIVE (WebView yok)
// ═══════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlogPostScreen(
    postId        : String,
    navController : NavController,
    vm            : BlogViewModel = hiltViewModel(),
) {
    val post    by vm.detail.collectAsState()
    val loading by vm.detailLoading.collectAsState()

    LaunchedEffect(postId) { vm.loadPostDetail(postId) }

    Scaffold(
        containerColor = Background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        post?.title ?: "Blog",
                        maxLines   = 1,
                        fontWeight = FontWeight.Bold,
                        color      = OnBackground,
                        fontSize   = 16.sp,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = OnBackground)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Background),
            )
        },
    ) { pad ->
        when {
            loading -> Box(
                modifier         = Modifier.fillMaxSize().padding(pad),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator(color = Amber) }

            post != null -> {
                val p = post!!
                Column(
                    modifier = Modifier
                        .padding(pad)
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                ) {
                    // Kapak görseli
                    if (p.thumbnail.isNotBlank()) {
                        AsyncImage(
                            model              = p.thumbnail,
                            contentDescription = null,
                            modifier           = Modifier.fillMaxWidth().height(220.dp),
                            contentScale       = ContentScale.Crop,
                        )
                    }

                    Column(modifier = Modifier.padding(16.dp)) {
                        // Etiketler
                        if (p.labels.isNotEmpty()) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                p.labels.take(4).forEach { label ->
                                    Surface(
                                        shape = RoundedCornerShape(99.dp),
                                        color = Amber.copy(alpha = 0.15f),
                                    ) {
                                        Text(
                                            label, color = Amber, fontSize = 11.sp,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                        }

                        // Başlık
                        Text(
                            p.title, color = OnBackground,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 20.sp, lineHeight = 28.sp,
                        )

                        Spacer(Modifier.height(12.dp))

                        // Yazar + tarih
                        Row(
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            if (p.authorPhoto.isNotBlank()) {
                                AsyncImage(
                                    model = p.authorPhoto, contentDescription = null,
                                    modifier = Modifier.size(28.dp).clip(CircleShape).background(SurfaceVar),
                                    contentScale = ContentScale.Crop,
                                )
                            }
                            Column {
                                if (p.authorName.isNotBlank())
                                    Text(p.authorName, color = OnBackground, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                Text(formatBlogDate(p.published), color = Muted, fontSize = 11.sp)
                            }
                        }

                        Spacer(Modifier.height(16.dp))
                        HorizontalDivider(color = Divider, thickness = 0.5.dp)
                        Spacer(Modifier.height(16.dp))

                        // İçerik — tamamen native
                        HtmlContent(html = p.content)
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// HTML PARSER + NATIVE RENDERER
// ═══════════════════════════════════════════════════════════════════════════════

// HTML node türleri
sealed class HtmlNode {
    data class Heading(val level: Int, val text: String)              : HtmlNode()
    data class Paragraph(val spans: List<HtmlSpan>)                   : HtmlNode()
    data class BlockQuote(val text: String)                           : HtmlNode()
    data class BulletList(val items: List<String>)                    : HtmlNode()
    data class OrderedList(val items: List<String>)                   : HtmlNode()
    data class ImageNode(val src: String, val alt: String)            : HtmlNode()
    data class HRule(val dummy: Unit = Unit)                          : HtmlNode()
    data class CodeBlock(val code: String)                            : HtmlNode()
    object Spacer                                                      : HtmlNode()
}

data class HtmlSpan(
    val text   : String,
    val bold   : Boolean = false,
    val italic : Boolean = false,
    val link   : String? = null,
    val code   : Boolean = false,
)

// ── Parser ────────────────────────────────────────────────────────────────────
fun parseHtml(html: String): List<HtmlNode> {
    val nodes = mutableListOf<HtmlNode>()

    // 1. Style/script temizle
    val noStyle = html
        .replace(Regex("<style[^>]*>.*?</style>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)), "")
        .replace(Regex("<script[^>]*>.*?</script>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)), "")
        .replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), "")

    // 2. Normalize
    val clean = noStyle
        .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("</(div|p|section|article|li)>", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("<div[^>]*>", RegexOption.IGNORE_CASE), "")
        .replace(Regex("<span[^>]*>", RegexOption.IGNORE_CASE), "")
        .replace("</span>", "")

    val blockPattern = Regex(
        """<(h[1-6]|p|blockquote|ul|ol|pre|hr|img)[^>]*>(.*?)</\1>|<(hr|img)([^>]*)/?>""",
        setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE),
    )

    var lastEnd = 0
    blockPattern.findAll(clean).forEach { match ->
        val before = clean.substring(lastEnd, match.range.first).trim()
        if (before.isNotBlank()) {
            before.split("\n").map { it.trim() }.filter { it.isNotBlank() }.forEach { line ->
                nodes += HtmlNode.Paragraph(parseInline(line))
                nodes += HtmlNode.Spacer
            }
        }
        lastEnd = match.range.last + 1

        val tag   = (match.groupValues[1].ifBlank { match.groupValues[3] }).lowercase()
        val inner = match.groupValues[2]
        val attrs = match.groupValues[4]

        when {
            tag.matches(Regex("h[1-6]")) -> {
                val text = stripTags(inner).trim()
                if (text.isNotBlank()) {
                    nodes += HtmlNode.Heading(tag[1].digitToInt(), text)
                    nodes += HtmlNode.Spacer
                }
            }
            tag == "p" -> {
                val imgMatch = Regex("""<img[^>]+src=["']([^"']+)["'][^>]*alt=["']([^"']*)["']""", RegexOption.IGNORE_CASE).find(inner)
                    ?: Regex("""<img[^>]+src=["']([^"']+)["']""", RegexOption.IGNORE_CASE).find(inner)
                if (imgMatch != null) {
                    val src = imgMatch.groupValues[1]
                    val alt = imgMatch.groupValues.getOrElse(2) { "" }
                    nodes += HtmlNode.ImageNode(src, alt)
                } else {
                    val spans = parseInline(inner)
                    if (spans.any { it.text.isNotBlank() }) nodes += HtmlNode.Paragraph(spans)
                }
                nodes += HtmlNode.Spacer
            }
            tag == "blockquote" -> {
                val text = stripTags(inner).trim()
                if (text.isNotBlank()) { nodes += HtmlNode.BlockQuote(text); nodes += HtmlNode.Spacer }
            }
            tag == "ul" -> {
                val items = Regex("<li[^>]*>(.*?)</li>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
                    .findAll(inner).map { stripTags(it.groupValues[1]).trim() }.filter { it.isNotBlank() }.toList()
                if (items.isNotEmpty()) { nodes += HtmlNode.BulletList(items); nodes += HtmlNode.Spacer }
            }
            tag == "ol" -> {
                val items = Regex("<li[^>]*>(.*?)</li>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
                    .findAll(inner).map { stripTags(it.groupValues[1]).trim() }.filter { it.isNotBlank() }.toList()
                if (items.isNotEmpty()) { nodes += HtmlNode.OrderedList(items); nodes += HtmlNode.Spacer }
            }
            tag == "pre" -> {
                val text = stripTags(inner).trim()
                if (text.isNotBlank()) { nodes += HtmlNode.CodeBlock(text); nodes += HtmlNode.Spacer }
            }
            tag == "hr" -> { nodes += HtmlNode.HRule(); nodes += HtmlNode.Spacer }
            tag == "img" -> {
                val src = Regex("""src=["']([^"']+)["']""", RegexOption.IGNORE_CASE).find(attrs)?.groupValues?.get(1) ?: ""
                val alt = Regex("""alt=["']([^"']*)["']""", RegexOption.IGNORE_CASE).find(attrs)?.groupValues?.get(1) ?: ""
                if (src.isNotBlank()) { nodes += HtmlNode.ImageNode(src, alt); nodes += HtmlNode.Spacer }
            }
        }
    }

    // Kalan metin
    val tail = clean.substring(lastEnd).trim()
    if (tail.isNotBlank()) {
        tail.split("\n").map { it.trim() }.filter { it.isNotBlank() }.forEach { line ->
            nodes += HtmlNode.Paragraph(parseInline(line))
            nodes += HtmlNode.Spacer
        }
    }

    // Hiç node bulunamadıysa ham metni göster
    return nodes.ifEmpty {
        val plain = html
            .replace(Regex("<style[^>]*>.*?</style>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)), "")
            .replace(Regex("<[^>]*>"), " ")
            .replace(Regex("&nbsp;"), " ")
            .replace(Regex("&[a-z]+;"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (plain.isNotBlank()) listOf(HtmlNode.Paragraph(listOf(HtmlSpan(plain))))
        else emptyList()
    }
}

// Satır içi span parse
fun parseInline(html: String): List<HtmlSpan> {
    val spans = mutableListOf<HtmlSpan>()
    val clean = html.replace(Regex("<img[^>]+>", RegexOption.IGNORE_CASE), "")

    val inlinePattern = Regex(
        """<(strong|b|em|i|code|a)([^>]*)>(.*?)</\1>""",
        setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE),
    )

    var last = 0
    inlinePattern.findAll(clean).forEach { match ->
        val before = clean.substring(last, match.range.first)
        if (before.isNotBlank()) spans += HtmlSpan(decodeEntities(stripTags(before)))
        last = match.range.last + 1

        val tag   = match.groupValues[1].lowercase()
        val attrs = match.groupValues[2]
        val text  = decodeEntities(stripTags(match.groupValues[3]))
        val href  = Regex("""href=["']([^"']+)["']""", RegexOption.IGNORE_CASE).find(attrs)?.groupValues?.get(1)

        spans += HtmlSpan(
            text   = text,
            bold   = tag in listOf("strong", "b"),
            italic = tag in listOf("em", "i"),
            code   = tag == "code",
            link   = href,
        )
    }

    val tail = clean.substring(last)
    if (tail.isNotBlank()) spans += HtmlSpan(decodeEntities(stripTags(tail)))

    return spans.filter { it.text.isNotBlank() }
}

private fun stripTags(html: String) = html.replace(Regex("<[^>]*>"), "").replace(Regex("\\s+"), " ").trim()

private fun decodeEntities(s: String) = s
    .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
    .replace("&quot;", "\"").replace("&#39;", "'").replace("&nbsp;", " ")
    .replace("&rsquo;", "'").replace("&lsquo;", "'")
    .replace("&rdquo;", "\"").replace("&ldquo;", "\"")
    .replace("&mdash;", "—").replace("&ndash;", "–")

// ── Compose renderer ──────────────────────────────────────────────────────────
@Composable
fun HtmlContent(html: String) {
    val context = LocalContext.current
    val textColor = OnBackground
    val linkColor = Amber
    val codeColor = SurfaceVar

    AndroidView(
        factory = { ctx ->
            android.widget.TextView(ctx).apply {
                setTextColor(android.graphics.Color.parseColor("#E2E8F0"))
                setLinkTextColor(android.graphics.Color.parseColor("#F59E0B"))
                textSize = 15f
                linksClickable = true
                autoLinkMask = 0
                movementMethod = android.text.method.LinkMovementMethod.getInstance()
                setPadding(0, 0, 0, 0)
            }
        },
        update = { tv ->
            val cleaned = html
                .replace(Regex("<style[^>]*>.*?</style>", setOf(kotlin.text.RegexOption.DOT_MATCHES_ALL, kotlin.text.RegexOption.IGNORE_CASE)), "")
                .replace(Regex("<script[^>]*>.*?</script>", setOf(kotlin.text.RegexOption.DOT_MATCHES_ALL, kotlin.text.RegexOption.IGNORE_CASE)), "")
            @Suppress("DEPRECATION")
            tv.text = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N)
                android.text.Html.fromHtml(cleaned, android.text.Html.FROM_HTML_MODE_LEGACY)
            else
                android.text.Html.fromHtml(cleaned)
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp),
    )
}

private fun formatBlogDate(iso: String): String {
    return try {
        val sdf  = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", java.util.Locale.getDefault())
        val date = sdf.parse(iso) ?: return iso.take(10)
        java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault()).format(date)
    } catch (_: Exception) { iso.take(10) }
}
