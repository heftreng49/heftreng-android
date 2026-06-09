package com.heftreng.app.ui.screens.readinglist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.heftreng.app.data.model.ReadingListEntry
import com.heftreng.app.ui.i18n.Strings
import com.heftreng.app.ui.theme.*
import com.heftreng.app.viewmodel.ReadingListViewModel
import com.heftreng.app.viewmodel.RlStatus
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState

// ── Okuma Listesi ekranı ───────────────────────────────────────────────────────
// Firestore: readingLists/{uid}/books — 4 sekme: okuyorum | okumak_istiyorum | okudum | biraktim
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadingListScreen(
    uid           : String,
    navController : NavController,
    language      : String = "tr",
    vm            : ReadingListViewModel = hiltViewModel(),
) {
    val ku = language == "ku"
    val entries  by vm.entries.collectAsState()
    val loading  by vm.loading.collectAsState()
    var selectedStatus by remember { mutableStateOf(RlStatus.READING) }

    LaunchedEffect(uid) { vm.load(uid) }


    var isRefreshing by remember { mutableStateOf(false) }
    val pullRefreshState = rememberPullRefreshState(
        refreshing = isRefreshing,
        onRefresh  = {
            isRefreshing = true
            vm.load()
        }
    )
    LaunchedEffect(isRefreshing) { if (isRefreshing) isRefreshing = false }
    Scaffold(
        containerColor = Background,
        topBar = {
            TopAppBar(
                title = { Text(Strings.readingList(language), fontWeight = FontWeight.Bold, color = OnBackground) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, null, tint = OnBackground)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Background),
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().pullRefresh(pullRefreshState)) {
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {

            // Durum sekmeleri — XML'deki 4 durum
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(RlStatus.values()) { status ->
                    val selected = selectedStatus == status
                    val bg = Color(status.color)
                    FilterChip(
                        selected = selected,
                        onClick  = { selectedStatus = status },
                        label    = { Text(if (ku) status.labelKu else status.labelTr, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                        colors   = FilterChipDefaults.filterChipColors(
                            selectedContainerColor   = bg.copy(alpha = 0.2f),
                            selectedLabelColor       = bg,
                            containerColor           = SurfaceVar,
                            labelColor               = Muted,
                        ),
                        border   = FilterChipDefaults.filterChipBorder(
                            enabled         = true,
                            selected        = selected,
                            selectedBorderColor = bg,
                            borderColor     = Divider,
                            borderWidth     = 1.dp,
                            selectedBorderWidth = 1.5.dp,
                        ),
                    )
                }
            }

            if (loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Amber)
                }
            } else {
                val list = entries[selectedStatus.key] ?: emptyList()
                if (list.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.LibraryBooks, null, tint = Muted, modifier = Modifier.size(48.dp))
                            Spacer(Modifier.height(12.dp))
                            Text(Strings.readingListEmpty(language), color = Muted, fontSize = 14.sp)
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns        = GridCells.Fixed(3),
                        contentPadding = PaddingValues(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement   = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(list) { entry ->
                            ReadingListBookCard(
                                entry    = entry,
                                status   = selectedStatus,
                                language = language,
                                onClick  = {
                            if (entry.sid.startsWith("book_")) {
                                navController.navigate("book/${entry.sid.removePrefix("book_")}")
                            } else {
                                navController.navigate("serial/${entry.sid}")
                            }
                        },
                                onRemove = { vm.remove(entry.sid) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReadingListBookCard(
    entry    : ReadingListEntry,
    status   : RlStatus,
    language : String = "tr",
    onClick  : () -> Unit,
    onRemove : () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }
    val ku = language == "ku"
    val bg = Color(status.color)

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(HeftSurface)
            .clickable { onClick() }
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            if (entry.coverImg.isNotEmpty()) {
                AsyncImage(
                    model        = entry.coverImg,
                    contentDescription = entry.title,
                    contentScale = ContentScale.Crop,
                    modifier     = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .clip(RoundedCornerShape(7.dp)),
                )
            } else {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(SurfaceVar),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.AutoStories, null, tint = Muted, modifier = Modifier.size(28.dp))
                }
            }
            // Durum rozeti
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(bg.copy(alpha = 0.85f))
                    .padding(horizontal = 4.dp, vertical = 2.dp),
            ) {
                Text(if (ku) status.labelKu else status.labelTr, color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
            }
            // Menü butonu
            Box(Modifier.align(Alignment.TopStart)) {
                IconButton(
                    onClick  = { showMenu = true },
                    modifier = Modifier.size(24.dp),
                ) {
                    Icon(Icons.Default.MoreVert, null, tint = Color.White, modifier = Modifier.size(14.dp))
                }
                DropdownMenu(
                    expanded         = showMenu,
                    onDismissRequest = { showMenu = false },
                    containerColor   = HeftSurface,
                ) {
                    DropdownMenuItem(
                        text    = { Text(Strings.removeFromList(language), color = Color(0xFFEF4444)) },
                        onClick = { showMenu = false; onRemove() },
                    )
                }
            }
        }

        Spacer(Modifier.height(6.dp))
        Text(
            entry.title,
            color      = OnBackground,
            fontSize   = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines   = 2,
            overflow   = TextOverflow.Ellipsis,
        )
    }
}

// ── Seri/Kitap detay sayfasından okuma listesi durumu seçme bottom sheet ──────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadingListStatusSheet(
    serialId  : String,
    title     : String,
    coverImg  : String,
    bg        : String,
    current   : RlStatus?,
    language  : String = "tr",
    onDismiss : () -> Unit,
    onSelect  : (RlStatus?) -> Unit,
) {
    val ku = language == "ku"
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor   = HeftSurface,
    ) {
        Column(modifier = Modifier.padding(bottom = 32.dp)) {
            Text(
                Strings.addToReadingList(language),
                fontWeight = FontWeight.Bold,
                color      = OnBackground,
                fontSize   = 16.sp,
                modifier   = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
            HorizontalDivider(color = Divider)
            RlStatus.values().forEach { status ->
                val selected = current == status
                val col = Color(status.color)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(if (selected) null else status) }
                        .background(if (selected) col.copy(alpha = 0.08f) else Color.Transparent)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(10.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(col)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(if (ku) status.labelKu else status.labelTr, color = if (selected) col else OnBackground, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                    Spacer(Modifier.width(6.dp))
                    
                    if (selected) {
                        Spacer(Modifier.weight(1f))
                        Icon(Icons.Default.Check, null, tint = col, modifier = Modifier.size(18.dp))
                    }
                }
            }
            if (current != null) {
                HorizontalDivider(color = Divider, modifier = Modifier.padding(vertical = 4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(null) }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Delete, null, tint = Color(0xFFEF4444), modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(Strings.removeFromList(language), color = Color(0xFFEF4444))
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
}}
