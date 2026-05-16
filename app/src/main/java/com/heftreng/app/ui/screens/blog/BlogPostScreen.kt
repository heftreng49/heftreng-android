package com.heftreng.app.ui.screens.blog

import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.heftreng.app.ui.theme.*
import com.heftreng.app.viewmodel.BlogViewModel

// ═══════════════════════════════════════════════════════════════════════════════
// BLOG YAZI DETAY EKRANI
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
    val isDark  = LocalHeftrangColors.current.isDark

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
            ) {
                CircularProgressIndicator(color = Amber)
            }

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
                            modifier           = Modifier
                                .fillMaxWidth()
                                .height(220.dp),
                            contentScale       = ContentScale.Crop,
                        )
                    }

                    Column(modifier = Modifier.padding(16.dp)) {
                        // Etiketler
                        if (p.labels.isNotEmpty()) {
                            Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)) {
                                p.labels.take(4).forEach { label ->
                                    Surface(
                                        shape = RoundedCornerShape(99.dp),
                                        color = Amber.copy(alpha = 0.15f),
                                    ) {
                                        Text(
                                            label,
                                            color    = Amber,
                                            fontSize = 11.sp,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                        }

                        // Başlık
                        Text(
                            p.title,
                            color      = OnBackground,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize   = 20.sp,
                            lineHeight = 28.sp,
                        )

                        Spacer(Modifier.height(12.dp))

                        // Yazar + tarih
                        Row(
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                        ) {
                            if (p.authorPhoto.isNotBlank()) {
                                AsyncImage(
                                    model              = p.authorPhoto,
                                    contentDescription = null,
                                    modifier           = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(SurfaceVar),
                                    contentScale       = ContentScale.Crop,
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

                        // İçerik — WebView ile HTML render
                        val bgHex  = if (isDark) "#0D0D1A" else "#F8F7FF"
                        val txtHex = if (isDark) "#E0E0F0" else "#1A1040"
                        val htmlContent = buildStyledHtml(p.content, bgHex, txtHex)

                        var webHeight by remember { mutableStateOf(800) }

                        AndroidView(
                            factory = { ctx ->
                                WebView(ctx).apply {
                                    settings.apply {
                                        javaScriptEnabled = false
                                        loadWithOverviewMode = true
                                        useWideViewPort = true
                                        setSupportZoom(false)
                                        builtInZoomControls = false
                                        displayZoomControls = false
                                        cacheMode = WebSettings.LOAD_NO_CACHE
                                    }
                                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                                    isScrollContainer = false
                                    webViewClient = WebViewClient()
                                    webChromeClient = object : WebChromeClient() {}
                                    loadDataWithBaseURL(null, htmlContent, "text/html", "UTF-8", null)
                                }
                            },
                            update = { wv ->
                                wv.loadDataWithBaseURL(null, htmlContent, "text/html", "UTF-8", null)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 200.dp, max = 8000.dp),
                        )
                    }
                }
            }
        }
    }
}

private fun buildStyledHtml(content: String, bg: String, text: String): String = """
<!DOCTYPE html>
<html>
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0">
<style>
  * { box-sizing: border-box; margin: 0; padding: 0; }
  body {
    background: $bg;
    color: $text;
    font-family: -apple-system, 'Segoe UI', sans-serif;
    font-size: 15px;
    line-height: 1.7;
    padding: 4px 0;
    word-break: break-word;
  }
  img {
    max-width: 100%;
    height: auto;
    border-radius: 10px;
    margin: 8px 0;
    display: block;
  }
  a { color: #FFB300; text-decoration: none; }
  h1, h2, h3 { margin: 16px 0 8px; line-height: 1.3; }
  p { margin-bottom: 12px; }
  blockquote {
    border-left: 3px solid #FFB300;
    padding-left: 12px;
    margin: 12px 0;
    color: #888899;
    font-style: italic;
  }
  pre, code {
    background: rgba(255,255,255,0.07);
    border-radius: 6px;
    padding: 2px 6px;
    font-size: 13px;
  }
</style>
</head>
<body>
$content
</body>
</html>
""".trimIndent()

private fun formatBlogDate(iso: String): String {
    return try {
        val sdf  = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", java.util.Locale.getDefault())
        val date = sdf.parse(iso) ?: return iso.take(10)
        java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault()).format(date)
    } catch (_: Exception) { iso.take(10) }
}
