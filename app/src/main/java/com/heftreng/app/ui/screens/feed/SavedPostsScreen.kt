package com.heftreng.app.ui.screens.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import kotlinx.coroutines.flow.debounce
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.heftreng.app.data.model.Post
import com.heftreng.app.navigation.Screen
import com.heftreng.app.ads.RemoteConfigManager
import com.heftreng.app.ui.component.AdSlotView
import com.heftreng.app.ui.component.ConnectedPostCard
import com.heftreng.app.ui.i18n.Strings
import com.heftreng.app.ui.theme.*
import com.heftreng.app.viewmodel.AdsViewModel
import com.heftreng.app.viewmodel.FeedViewModel
import com.heftreng.app.viewmodel.SocialViewModel
import com.heftreng.app.viewmodel.SettingsViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.tasks.await
import java.net.URLEncoder
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
@Composable
fun SavedPostsScreen(
    navController : NavController,
    feedVm        : FeedViewModel    = hiltViewModel(),
    socialVm      : SocialViewModel  = hiltViewModel(),
    settingsVm    : SettingsViewModel = hiltViewModel(),
    adsVm         : AdsViewModel     = hiltViewModel(),
) {
    val language by settingsVm.language.collectAsState()
    val ku = language == "ku"
    val auth = FirebaseAuth.getInstance()
    val db   = FirebaseFirestore.getInstance()
    val uid  = auth.currentUser?.uid ?: ""

    var savedPosts  by remember { mutableStateOf<List<Post>>(emptyList()) }
    var loading     by remember { mutableStateOf(true) }
    var errorMsg    by remember { mutableStateOf("") }
    val listState   = rememberLazyListState()

    // ── Reklam altyapısı (enabled:false — Firebase Console'dan unitId girilene kadar kapalı) ──
    val adConfigs by adsVm.allConfigs.collectAsState()
    val savedPostsAdPlan = remember(savedPosts.size, adConfigs) {
        adsVm.planFor(
            screenKey = "savedposts",
            itemCount = savedPosts.size,
            nativeKey = RemoteConfigManager.KEY_NATIVE_SAVEDPOSTS,
        )
    }
    LaunchedEffect(listState, savedPostsAdPlan) {
        adsVm.warmVisiblePositions(savedPostsAdPlan, firstVisibleIndex = 0, maxInitialAds = 3)
        snapshotFlow { listState.firstVisibleItemIndex }
            .debounce(300L)
            .collect { firstVisible ->
                adsVm.warmVisiblePositions(savedPostsAdPlan, firstVisibleIndex = firstVisible)
            }
    }
    DisposableEffect(Unit) {
        onDispose { adsVm.releaseAllNatives("savedposts_native_") }
    }

    // feedSaves koleksiyonundan uid'ye göre çek, sonra feed dokümanlarını getir
    LaunchedEffect(uid) {
        if (uid.isBlank()) { loading = false; return@LaunchedEffect }
        try {
            // feed_saves — Supabase'den oku
            val supabaseClient = io.github.jan.supabase.createSupabaseClient(
                supabaseUrl  = com.heftreng.app.BuildConfig.SUPABASE_URL,
                supabaseKey  = com.heftreng.app.BuildConfig.SUPABASE_ANON_KEY,
            ) { install(io.github.jan.supabase.postgrest.Postgrest) }

            val postIds = try {
                supabaseClient.postgrest["feed_saves"].select {
                    filter { eq("uid", uid) }
                    limit(50)
                    order("created_at", io.github.jan.supabase.postgrest.query.Order.DESCENDING)
                }.decodeList<com.heftreng.app.data.model.FeedSaveRow>()
                    .mapNotNull { it.postId.takeIf { id -> id.isNotBlank() } }.distinct()
            } catch (_: Exception) {
                // Fallback: Firestore
                val saveSnap = db.collection("feedSaves")
                    .whereEqualTo("uid", uid).limit(50).get().await()
                saveSnap.documents.sortedByDescending { it.getTimestamp("ts")?.seconds ?: 0L }
                    .mapNotNull { it.getString("feedId") ?: it.getString("postId") }.distinct()
            }

            if (postIds.isEmpty()) { loading = false; return@LaunchedEffect }

            // Firestore'da `in` query max 30, parçalara böl
            val posts = mutableListOf<Post>()
            postIds.chunked(30).forEach { chunk ->
                val snap = db.collection("feed")
                    .whereIn("__name__", chunk)
                    .get().await()
                snap.documents.forEach { doc ->
                    val d = doc.data ?: return@forEach
                    val displayName = (d["displayName"] as? String)?.takeIf { it.isNotBlank() }
                                      ?: d["name"] as? String ?: ""
                    val quoteObj   = d["quote"] as? Map<*, *>
                    posts.add(Post(
                        id            = doc.id,
                        uid           = d["uid"]         as? String ?: "",
                        displayName   = displayName,
                        name          = displayName,
                        username      = d["username"]    as? String ?: "",
                        photoURL      = d["photoURL"]    as? String ?: "",
                        text          = d["text"]        as? String ?: "",
                        title         = d["title"]       as? String ?: "",
                        category      = d["category"]    as? String ?: "",
                        imageURL      = d["imageURL"]    as? String ?: d["imgUrl"]  as? String ?: "",
                        likesCount    = (d["likes"]      as? Long)?.toInt() ?: 0,
                        commentsCount = (d["cmtCount"]   as? Long)?.toInt() ?: 0,
                        repostsCount  = (d["reposts"]    as? Long)?.toInt() ?: 0,
                        ts            = d["ts"]          as? com.google.firebase.Timestamp,
                        quoteText     = (quoteObj?.get("text") as? String)?.takeIf { it.isNotBlank() }
                                        ?: d["quoteText"] as? String ?: "",
                        bookName      = (quoteObj?.get("book") as? String)?.takeIf { it.isNotBlank() }
                                        ?: d["bookName"] as? String ?: "",
                        authorName    = (quoteObj?.get("author") as? String)?.takeIf { it.isNotBlank() }
                                        ?: d["authorName"] as? String ?: "",
                        repostType    = d["repostType"]       as? String ?: "",
                        repostId      = d["repostId"]         as? String ?: "",
                        repostTitle   = d["repostTitle"]      as? String ?: "",
                        repostText    = d["repostText"]       as? String ?: "",
                        repostAuthor  = d["repostAuthor"]     as? String ?: "",
                        repostAuthorPhoto = d["repostAuthorPhoto"] as? String ?: "",
                        serialTitle   = d["serialTitle"]      as? String ?: "",
                        serialCover   = d["serialCover"]      as? String ?: "",
                        serialId      = d["serialId"]         as? String ?: "",
                        chapterId     = d["chapterId"]        as? String ?: "",
                        chapterTitle  = d["chapterTitle"]     as? String ?: "",
                        chapterOrder  = (d["chapterOrder"]    as? Long)?.toInt() ?: 0,
                        isSavedByMe   = true,
                    ))
                }
            }

            // Orijinal kayıt sırasını koru
            val ordered = postIds.mapNotNull { id -> posts.find { it.id == id } }
            savedPosts = ordered
        } catch (e: Exception) {
            errorMsg = e.message ?: "Hata"
        } finally {
            loading = false
        }
    }


    val scope = rememberCoroutineScope()
    var isRefreshing by remember { mutableStateOf(false) }
    val pullRefreshState = rememberPullRefreshState(
        refreshing = isRefreshing || loading,
        onRefresh  = {
            scope.launch {
                isRefreshing = true
                savedPosts = emptyList()
                loading = true
            }
        }
    )
    LaunchedEffect(isRefreshing) { /* trigger above */ }
    Scaffold(
        containerColor = Background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (ku) "Tomarkirî" else "Kaydedilenler",
                        color = OnBackground, fontWeight = FontWeight.SemiBold, fontSize = 17.sp,
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
    ) { padding ->
        Box(Modifier.fillMaxSize().pullRefresh(pullRefreshState)) {
        when {
            loading -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Amber)
                }
            }
            savedPosts.isEmpty() -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            Icons.Outlined.BookmarkBorder, null,
                            tint = Muted, modifier = Modifier.size(56.dp),
                        )
                        Text(
                            if (ku) "Tu tiştekî hatî tomarkirin tune" else "Henüz kaydedilen gönderi yok",
                            color = Muted, fontSize = 15.sp,
                        )
                        Text(
                            if (ku) "Gava ku tu nivîsekê tomarbikî, li vir xuya dibe"
                            else "Bir gönderiyi kaydettiğinde burada görünür",
                            color = Muted.copy(alpha = 0.6f), fontSize = 13.sp,
                        )
                    }
                }
            }
            else -> {
                LazyColumn(
                    state          = listState,
                    modifier       = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(bottom = 80.dp),
                ) {
                    itemsIndexed(savedPosts, key = { _, p -> p.id }) { index, post ->
                        ConnectedPostCard(
                            post           = post,
                            navController  = navController,
                            feedVm         = feedVm,
                            socialVm       = socialVm,
                            language       = language,
                            onSaveOverride = {
                                feedVm.toggleSave(post)
                                savedPosts = savedPosts.filter { it.id != post.id }
                            },
                        )
                        HorizontalDivider(color = Divider, thickness = 0.5.dp)
                        savedPostsAdPlan[index]?.let { placement ->
                            AdSlotView(placement = placement, adsVm = adsVm, modifier = Modifier.padding(vertical = 4.dp))
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
