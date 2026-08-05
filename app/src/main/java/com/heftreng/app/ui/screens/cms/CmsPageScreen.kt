package com.heftreng.app.ui.screens.cms

import android.content.Intent
import android.net.Uri
import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import com.google.firebase.firestore.FirebaseFirestore
import com.heftreng.app.ui.theme.*
import io.noties.markwon.Markwon
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.linkify.LinkifyPlugin
import kotlinx.coroutines.tasks.await

// Slug → Blogger sayfası URL eşlemesi
// Firestore'da içerik yoksa bu URL'lere fallback yapılır.
private val SLUG_FALLBACK_URLS = mapOf(
    "hakkinda"           to "https://heftreng.onrender.com",
    "gizlilik-politikasi" to "https://heftreng.onrender.com/privacy",
    "kullanim-kosullari"  to "https://heftreng.onrender.com/terms",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CmsPageScreen(
    navController: NavController,
    slug         : String,
) {
    val context = LocalContext.current
    var title   by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var notFound by remember { mutableStateOf(false) }

    LaunchedEffect(slug) {
        try {
            val db   = FirebaseFirestore.getInstance()
            val snap = db.collection("cms_pages")
                .whereEqualTo("slug", slug)
                .limit(1)
                .get().await()

            if (!snap.isEmpty) {
                val doc = snap.documents.first()
                title   = doc.getString("title")   ?: slug
                content = doc.getString("body") ?: ""
            } else {
                notFound = true
            }
        } catch (e: Exception) {
            notFound = true
        } finally {
            loading = false
        }
    }

    // Fallback URL varsa doğrudan tarayıcıya aç
    LaunchedEffect(notFound) {
        if (notFound) {
            val fallbackUrl = SLUG_FALLBACK_URLS[slug]
            if (fallbackUrl != null) {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(fallbackUrl)).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                )
                navController.popBackStack()
            }
        }
    }

    Scaffold(
        containerColor = Background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        title.ifBlank { slug },
                        color      = OnBackground,
                        fontSize   = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = OnBackground)
                    }
                },
                actions = {
                    // Tarayıcıda aç butonu — içerik varken de kullanışlı
                    val fallbackUrl = SLUG_FALLBACK_URLS[slug]
                    if (fallbackUrl != null && !loading && !notFound) {
                        IconButton(onClick = {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(fallbackUrl)).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                            )
                        }) {
                            Icon(Icons.Outlined.OpenInBrowser, null, tint = Muted)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Background),
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.TopStart,
        ) {
            when {
                loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Primary)
                    }
                }
                notFound && SLUG_FALLBACK_URLS[slug] == null -> {
                    // Fallback URL da yoksa hata göster
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Sayfa bulunamadı", color = Muted, fontSize = 15.sp)
                            Spacer(Modifier.height(8.dp))
                            Text(slug, color = Muted, fontSize = 12.sp)
                        }
                    }
                }
                !notFound -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                    ) {
                        if (title.isNotBlank()) {
                            Text(
                                title,
                                color      = OnBackground,
                                fontSize   = 20.sp,
                                fontWeight = FontWeight.Bold,
                                lineHeight = 28.sp,
                            )
                            Spacer(Modifier.height(12.dp))
                            HorizontalDivider(color = Divider)
                            Spacer(Modifier.height(16.dp))
                        }
                        MarkdownContent(
                            markdown = content,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

// ── Markdown Renderer ─────────────────────────────────────────────────────────
@Composable
private fun MarkdownContent(
    markdown : String,
    modifier : Modifier = Modifier,
) {
    val context = LocalContext.current

    // Markwon instance — remember ile her recompose'da yeniden oluşturulmaz
    val markwon = remember(context) {
        Markwon.builder(context)
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(TablePlugin.create(context))
            .usePlugin(LinkifyPlugin.create())
            .build()
    }

    // Tema renklerini al — hex'e çevir
    val textColorInt = OnSurface.copy(alpha = 1f)
        .let { c ->
            android.graphics.Color.argb(
                (c.alpha * 255).toInt(),
                (c.red   * 255).toInt(),
                (c.green * 255).toInt(),
                (c.blue  * 255).toInt(),
            )
        }

    AndroidView(
        modifier = modifier,
        factory  = { ctx ->
            TextView(ctx).apply {
                setTextColor(textColorInt)
                textSize        = 15f
                setLineSpacing(0f, 1.5f)
                movementMethod  = LinkMovementMethod.getInstance()
                setPadding(0, 0, 0, 0)
            }
        },
        update = { textView ->
            markwon.setMarkdown(textView, markdown)
            textView.setTextColor(textColorInt)
        }
    )
}
