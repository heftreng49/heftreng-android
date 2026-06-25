package com.heftreng.app.ui.screens.social

// ═══════════════════════════════════════════════════════════════════════════
//  PeopleHubScreen — "Tümünü Gör" hedefi: Takip Edilenler / Takipçiler / Önerilenler
//
//  ÖNCEDEN: Bu üçü birbirinden tamamen kopuk üç ayrı sistemdi:
//   1) FeedViewModel'in feed içindeki sayfalı (10'luk) öneri kartı
//   2) SearchViewModel'in arama ekranındaki ayrı öneri listesi
//   3) UserListSheet'in tek-seferde (sadece takipçi VEYA takip) bottom sheet'i
//  Artık tek bir ekranda, sekmeli olarak birleştirildi.
// ═══════════════════════════════════════════════════════════════════════════

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.heftreng.app.data.model.FollowEntry
import com.heftreng.app.navigation.Screen
import com.heftreng.app.ui.i18n.Strings
import com.heftreng.app.ui.theme.*
import com.heftreng.app.viewmodel.FeedViewModel
import com.heftreng.app.viewmodel.SocialViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeopleHubScreen(
    navController: NavController,
    language     : String = "tr",
    initialTab   : Int = 2,
    socialVm     : SocialViewModel = hiltViewModel(),
    feedVm       : FeedViewModel   = hiltViewModel(),
) {
    val ku = language == "ku"
    var selectedTab by remember { mutableIntStateOf(initialTab.coerceIn(0, 2)) }

    val myUid = socialVm.uid

    LaunchedEffect(Unit) {
        socialVm.loadFollowing(myUid)
        socialVm.loadFollowers(myUid)
        feedVm.loadSuggestedUsers()
    }

    val following        by socialVm.following.collectAsState()
    val followers         by socialVm.followers.collectAsState()
    val followingLoading by socialVm.followingLoading.collectAsState()
    val followersLoading by socialVm.followersLoading.collectAsState()
    val hasMoreFollowing by socialVm.hasMoreFollowing.collectAsState()
    val hasMoreFollowers by socialVm.hasMoreFollowers.collectAsState()

    val suggested        by feedVm.suggestedUsers.collectAsState()
    val suggestPage      by feedVm.suggestCurrentPage.collectAsState()
    val hasMoreSuggested by feedVm.hasMoreSuggestions.collectAsState()

    val tabs = listOf(
        Strings.peopleHubFollowing(language),
        Strings.peopleHubFollowers(language),
        Strings.peopleHubSuggested(language),
    )

    Scaffold(
        containerColor = Background,
        topBar = {
            TopAppBar(
                title = { Text(Strings.seeAll(language), fontWeight = FontWeight.Bold, color = OnBackground) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = OnBackground)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Background),
            )
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor   = Background,
                contentColor     = Primary,
            ) {
                tabs.forEachIndexed { i, title ->
                    Tab(
                        selected = selectedTab == i,
                        onClick  = { selectedTab = i },
                        text     = { Text(title, fontSize = 13.sp, fontWeight = if (selectedTab == i) FontWeight.Bold else FontWeight.Normal) },
                    )
                }
            }
            HorizontalDivider(color = Divider, thickness = 0.5.dp)

            when (selectedTab) {
                0 -> FollowEntryList(
                    entries   = following,
                    loading   = followingLoading,
                    hasMore   = hasMoreFollowing,
                    onLoadMore = { socialVm.loadMoreFollowing() },
                    emptyText = if (ku) "Tu kesî tu li dû nakî" else "Henüz kimseyi takip etmiyorsun",
                    onProfile = { navController.navigate(Screen.Profile.go(it)) },
                )
                1 -> FollowEntryList(
                    entries   = followers,
                    loading   = followersLoading,
                    hasMore   = hasMoreFollowers,
                    onLoadMore = { socialVm.loadMoreFollowers() },
                    emptyText = if (ku) "Tu şopîner tune" else "Henüz takipçin yok",
                    onProfile = { navController.navigate(Screen.Profile.go(it)) },
                )
                2 -> SuggestedList(
                    users      = suggested,
                    page       = suggestPage,
                    hasMore    = hasMoreSuggested,
                    language   = language,
                    onPrevPage = { feedVm.loadPrevSuggestedUsersPage() },
                    onNextPage = { feedVm.loadNextSuggestedUsersPage() },
                    onFollow   = { feedVm.followSuggestedUser(it) },
                    onProfile  = { navController.navigate(Screen.Profile.go(it)) },
                )
            }
        }
    }
}

@Composable
private fun FollowEntryList(
    entries   : List<FollowEntry>,
    loading   : Boolean,
    hasMore   : Boolean,
    onLoadMore: () -> Unit,
    emptyText : String,
    onProfile : (String) -> Unit,
) {
    when {
        loading && entries.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Primary, modifier = Modifier.size(28.dp))
        }
        entries.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Person, null, tint = Divider, modifier = Modifier.size(44.dp))
                Text(emptyText, color = Muted, fontSize = 13.sp)
            }
        }
        else -> LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            items(entries, key = { it.uid }) { entry ->
                FollowEntryRow(entry = entry, onClick = { onProfile(entry.uid) })
                HorizontalDivider(color = Divider, thickness = 0.5.dp, modifier = Modifier.padding(start = 64.dp))
            }
            if (hasMore) {
                item(key = "load_more") {
                    Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                        if (loading) {
                            CircularProgressIndicator(color = Primary, modifier = Modifier.size(20.dp))
                        } else {
                            TextButton(onClick = onLoadMore) { Text("Daha Fazla Yükle", color = Primary, fontSize = 13.sp) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SuggestedList(
    users     : List<FeedViewModel.SuggestedUser>,
    page      : Int,
    hasMore   : Boolean,
    language  : String,
    onPrevPage: () -> Unit,
    onNextPage: () -> Unit,
    onFollow  : (String) -> Unit,
    onProfile : (String) -> Unit,
) {
    val ku = language == "ku"
    if (users.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(if (ku) "Niha pêşniyar tune" else "Şu an önerilecek kimse yok", color = Muted, fontSize = 13.sp)
        }
        return
    }
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        items(users, key = { it.uid }) { user ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onProfile(user.uid) }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (user.photoURL.isNotBlank()) {
                    AsyncImage(model = user.photoURL, contentDescription = null, contentScale = ContentScale.Crop,
                        modifier = Modifier.size(44.dp).clip(CircleShape))
                } else {
                    Box(Modifier.size(44.dp).clip(CircleShape).background(SurfaceVar), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Person, null, tint = Muted, modifier = Modifier.size(22.dp))
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(user.name, color = OnBackground, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (user.bio.isNotBlank()) {
                        Text(user.bio, color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick        = { onFollow(user.uid) },
                    shape          = RoundedCornerShape(20.dp),
                    colors         = ButtonDefaults.buttonColors(containerColor = Primary),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                    modifier       = Modifier.height(34.dp),
                ) {
                    Text(if (ku) "Bişopîne" else "Takip Et", color = androidx.compose.ui.graphics.Color.White,
                        fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            HorizontalDivider(color = Divider, thickness = 0.5.dp, modifier = Modifier.padding(start = 64.dp))
        }
        item(key = "suggest_paging") {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onPrevPage, enabled = page > 0) {
                    Text(if (ku) "Berê" else "Önceki", color = if (page > 0) Primary else Muted, fontSize = 13.sp)
                }
                Text((if (ku) "Rûpel " else "Sayfa ") + "${page + 1}", color = Muted, fontSize = 12.sp)
                TextButton(onClick = onNextPage, enabled = hasMore) {
                    Text(if (ku) "Pêş" else "Sonraki", color = if (hasMore) Primary else Muted, fontSize = 13.sp)
                }
            }
        }
    }
}
