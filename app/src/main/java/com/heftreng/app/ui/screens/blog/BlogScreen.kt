package com.heftreng.app.ui.screens.blog

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.heftreng.app.ui.theme.*
import com.heftreng.app.ui.component.AdBannerView
import com.heftreng.app.ui.component.NativeAdViewCompose
import com.heftreng.app.ui.component.PositionedAdBannerView
import com.heftreng.app.ui.component.PositionedNativeAdView
import com.heftreng.app.viewmodel.AdsViewModel
import androidx.hilt.navigation.compose.hiltViewModel
import com.heftreng.app.viewmodel.BlogPost
import com.heftreng.app.viewmodel.BlogViewModel
import com.heftreng.app.navigation.Screen
import com.heftreng.app.ui.i18n.Strings
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState

// ═══════════════════════════════════════════════════════════════════════════════
// BLOG LİSTESİ EKRANI
// ═══════════════════════════════════════════════════════════════════════════════
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
@Composable
fun BlogScreen(
    navController : NavController,
    vm            : BlogViewModel,
    language      : String = "tr",
    adsVm         : AdsViewModel = hiltViewModel(),
) {
    val ku = language == "ku"
    val state    by vm.state.collectAsState()
    val bannerUnitId  by adsVm.bannerBlogUnitId.collectAsState()
    val bannerCfg     by adsVm.bannerBlogConfig.collectAsState()
    val blogBannerSize = bannerCfg?.bannerSize ?: "adaptive"
    val bannerPos      = bannerCfg?.position ?: 4
    // nativeBlogAd artık pozisyon bazlı yükleniyor (PositionedNativeAdView), tekil collect kaldırıldı.
    var selLabel by remember { mutableStateOf<String?>(null) }


    DisposableEffect(Unit) {
        onDispose {
            adsVm.releasePositionedBanners("blog_banner_")
            adsVm.releaseAllPositionedNatives("blog_native_")
        }
    }

    // Tüm etiketleri topla
    val allLabels = remember(state.posts) {
        state.posts.flatMap { it.labels }.distinct().sorted()
    }


    var isRefreshing by remember { mutableStateOf(false) }
    val pullRefreshState = rememberPullRefreshState(
        refreshing = isRefreshing,
        onRefresh  = {
            isRefreshing = true
            vm.loadPosts(refresh = true)
        }
    )
    LaunchedEffect(isRefreshing) { if (isRefreshing) isRefreshing = false }
    Scaffold(
        containerColor = Background,
        topBar = {
            TopAppBar(
                title = {
                    Text("Blog", fontWeight = FontWeight.ExtraBold, color = Primary, fontSize = 20.sp)
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Background),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { navController.navigate(Screen.Yazar.route) },
                containerColor = Amber,
                contentColor   = Color.Black,
                icon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                text = { Text(Strings.yazarTitle(language), fontWeight = FontWeight.Bold) },
            )
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().pullRefresh(pullRefreshState)) {
        Column(modifier = Modifier.padding(pad).fillMaxSize()) {

            // ── Etiket filtreleri ─────────────────────────────────────────────
            if (allLabels.isNotEmpty()) {
                LazyRow(
                    contentPadding        = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        FilterChip(
                            selected = selLabel == null,
                            onClick  = { selLabel = null; vm.filterByLabel(null) },
                            label    = { Text(if (ku) "Hemû" else "Tümü", fontSize = 12.sp) },
                            colors   = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Amber,
                                selectedLabelColor     = Color.Black,
                            ),
                        )
                    }
                    items(allLabels) { label ->
                        FilterChip(
                            selected = selLabel == label,
                            onClick  = { selLabel = label; vm.filterByLabel(label) },
                            label    = { Text(label, fontSize = 12.sp) },
                            colors   = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Amber,
                                selectedLabelColor     = Color.Black,
                            ),
                        )
                    }
                }
                HorizontalDivider(color = Divider, thickness = 0.5.dp)
            }

            // ── İçerik ───────────────────────────────────────────────────────
            when {
                state.loading && state.posts.isEmpty() -> BlogShimmerList()
                state.error != null && state.posts.isEmpty() -> BlogError(
                    message  = state.error ?: "",
                    onRetry  = { vm.loadPosts(refresh = true) },
                    language = language,
                )
                else -> {
                    LazyColumn(
                        contentPadding        = PaddingValues(12.dp),
                        verticalArrangement   = Arrangement.spacedBy(12.dp),
                        modifier              = Modifier.fillMaxSize(),
                    ) {
                        itemsIndexed(state.posts, key = { _, post -> post.id }) { index, post ->
                            BlogPostCard(
                                post     = post,
                                onClick  = { navController.navigate("blog_post/${post.id}") },
                            )
                            // Her bannerPos. yazıdan sonra reklam göster — her pozisyon kendi AdView'ını yükler
                            if (bannerUnitId != null && (index + 1) % bannerPos == 0) {
                                PositionedAdBannerView(
                                    positionKey  = "blog_banner_$index",
                                    unitId       = bannerUnitId,
                                    adsVm        = adsVm,
                                    bannerSize   = blogBannerSize,
                                )
                            }

                            // CMS'deki position/frequency alanlarına göre native ad yerleşimi.
                            // DÜZELTME: bkz. FeedScreen.kt aynı blok — eskiden sadece "position"
                            // okunup frekans gibi kullanılıyordu, "frequency" göz ardı ediliyordu.
                            val nativeBlogCfg by adsVm.nativeBlogConfig.collectAsState()
                            val nativeBlogStartPos = (nativeBlogCfg?.position ?: 5).coerceAtLeast(1)
                            val nativeBlogFreq     = (nativeBlogCfg?.frequency ?: 5).coerceAtLeast(1)
                            val showBlogNativeHere = index >= nativeBlogStartPos &&
                                (index - nativeBlogStartPos) % nativeBlogFreq == 0
                            if (showBlogNativeHere) {
                                // ÖNEMLİ: nativeBlogCfg CMS'den gelene kadar null'dur — eskiden bu
                                // yüzden unitId de null kalıyor, reklam hiç istenmiyordu. Artık
                                // adsVm.nativeBlogUnitId (anlık varsayılan prod ID'li) kullanılıyor.
                                val nativeUnitId by adsVm.nativeBlogUnitId.collectAsState()
                                PositionedNativeAdView(
                                    positionKey  = "blog_native_$index",
                                    unitId       = nativeUnitId,
                                    adsVm        = adsVm,
                                    modifier     = Modifier.fillMaxWidth(),
                                ) { ad ->
                                    NativeAdViewCompose(
                                        nativeAd = ad,
                                        modifier = Modifier.fillMaxWidth(),
                                        adSize   = when (nativeBlogCfg?.bannerSize?.lowercase()) { "medium" -> com.heftreng.app.ui.component.NativeAdSize.MEDIUM; "large" -> com.heftreng.app.ui.component.NativeAdSize.LARGE; else -> com.heftreng.app.ui.component.NativeAdSize.SMALL }
                                    )
                                }
                            }
                        }

                        // Sayfa sonu — daha fazla yükle
                        if (state.hasMore) {
                            item {
                                LaunchedEffect(Unit) { vm.loadMore() }
                                Box(
                                    modifier         = Modifier.fillMaxWidth().padding(16.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CircularProgressIndicator(
                                        color       = Amber,
                                        modifier    = Modifier.size(24.dp),
                                        strokeWidth = 2.dp,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    
        PullRefreshIndicator(
            refreshing = isRefreshing,
            state      = pullRefreshState,
            modifier   = Modifier.align(Alignment.TopCenter),
        )
        } // pullRefresh Box
    }
}

// ── Post kartı ────────────────────────────────────────────────────────────────
@Composable
fun BlogPostCard(post: BlogPost, onClick: () -> Unit) {
    Surface(
        shape         = RoundedCornerShape(16.dp),
        color         = HeftSurface,
        tonalElevation = 0.dp,
        modifier      = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Column {
            // Görsel
            if (post.thumbnail.isNotBlank()) {
                AsyncImage(
                    model              = post.thumbnail,
                    contentDescription = null,
                    modifier           = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)),
                    contentScale       = ContentScale.Crop,
                )
            }

            Column(modifier = Modifier.padding(14.dp)) {
                // Etiketler
                if (post.labels.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        post.labels.take(3).forEach { label ->
                            Surface(
                                shape = RoundedCornerShape(99.dp),
                                color = Amber.copy(alpha = 0.15f),
                            ) {
                                Text(
                                    label,
                                    color    = Amber,
                                    fontSize = 10.sp,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }

                // Başlık
                Text(
                    post.title,
                    color      = OnBackground,
                    fontWeight = FontWeight.Bold,
                    fontSize   = 15.sp,
                    maxLines   = 2,
                    overflow   = TextOverflow.Ellipsis,
                )

                Spacer(Modifier.height(6.dp))

                // Özet
                if (post.summary.isNotBlank()) {
                    Text(
                        post.summary,
                        color    = Muted,
                        fontSize = 13.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 19.sp,
                    )
                    Spacer(Modifier.height(10.dp))
                }

                // Meta — yazar + tarih
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (post.authorPhoto.isNotBlank()) {
                        AsyncImage(
                            model              = post.authorPhoto,
                            contentDescription = null,
                            modifier           = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(SurfaceVar),
                            contentScale       = ContentScale.Crop,
                        )
                    }
                    if (post.authorName.isNotBlank()) {
                        Text(post.authorName, color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                        Text("·", color = Muted, fontSize = 11.sp)
                    }
                    Text(formatBlogDate(post.published), color = Muted, fontSize = 11.sp)
                }
            }
        }
    }
}

// ── Hata ─────────────────────────────────────────────────────────────────────
@Composable
private fun BlogError(message: String, onRetry: () -> Unit, language: String = "tr") {
    Column(
        modifier              = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment   = Alignment.CenterHorizontally,
        verticalArrangement   = Arrangement.Center,
    ) {
        Icon(Icons.Default.CloudOff, null, tint = Muted, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(12.dp))
        Text(message.take(100), color = Muted, fontSize = 13.sp)
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onRetry,
            colors  = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = Color.Black),
        ) { Text(if (language == "ku") "Dîsa biceribîne" else "Tekrar Dene") }
    }
}

// ── Shimmer ──────────────────────────────────────────────────────────────────
@Composable
private fun BlogShimmerList() {
    LazyColumn(
        contentPadding      = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(5) { BlogShimmerCard() }
    }
}

@Composable
private fun BlogShimmerCard() {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val x by transition.animateFloat(
        initialValue   = -1f, targetValue = 2f,
        animationSpec  = infiniteRepeatable(tween(1200, easing = LinearEasing)),
        label          = "shimmer_x",
    )
    val shimmer = Brush.linearGradient(
        colors     = listOf(SurfaceVar, HeftSurface, SurfaceVar),
        start      = Offset(x * 400, 0f),
        end        = Offset((x + 1) * 400, 0f),
    )
    Surface(
        shape  = RoundedCornerShape(16.dp),
        color  = HeftSurface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            Box(modifier = Modifier.fillMaxWidth().height(180.dp).background(shimmer))
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(modifier = Modifier.fillMaxWidth(0.3f).height(12.dp).clip(RoundedCornerShape(4.dp)).background(shimmer))
                Box(modifier = Modifier.fillMaxWidth(0.9f).height(16.dp).clip(RoundedCornerShape(4.dp)).background(shimmer))
                Box(modifier = Modifier.fillMaxWidth(0.7f).height(16.dp).clip(RoundedCornerShape(4.dp)).background(shimmer))
                Box(modifier = Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(4.dp)).background(shimmer))
                Box(modifier = Modifier.fillMaxWidth(0.5f).height(12.dp).clip(RoundedCornerShape(4.dp)).background(shimmer))
            }
        }
    }
}

// ── Tarih formatı ─────────────────────────────────────────────────────────────
private fun formatBlogDate(iso: String): String {
    return try {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.getDefault())
        val date = sdf.parse(iso) ?: return iso.take(10)
        SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(date)
    } catch (_: Exception) { iso.take(10) }
}
