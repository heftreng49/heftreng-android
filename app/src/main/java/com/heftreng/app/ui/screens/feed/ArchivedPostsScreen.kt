package com.heftreng.app.ui.screens.feed

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.heftreng.app.ui.component.ConnectedPostCard
import com.heftreng.app.ui.i18n.Strings
import com.heftreng.app.ui.theme.*
import com.heftreng.app.viewmodel.FeedViewModel
import com.heftreng.app.viewmodel.ProfileViewModel
import com.heftreng.app.viewmodel.SocialViewModel
import com.heftreng.app.viewmodel.SettingsViewModel

/**
 * Kullanıcının kendi arşivlediği (silmek yerine kaldırdığı) gönderileri
 * listeler. Her satırda "Geri Yükle" (feed'e geri koyar) ve "Kalıcı Olarak
 * Sil" (gerçekten siler, geri dönüşü yok) aksiyonları var.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchivedPostsScreen(
    navController : NavController,
    profileVm     : ProfileViewModel = hiltViewModel(),
    feedVm        : FeedViewModel    = hiltViewModel(),
    socialVm      : SocialViewModel  = hiltViewModel(),
    settingsVm    : SettingsViewModel = hiltViewModel(),
) {
    val language by settingsVm.language.collectAsState()
    val archivedPosts by profileVm.archivedPosts.collectAsState()
    val loading by profileVm.archivedLoading.collectAsState()
    val listState = rememberLazyListState()

    var pendingDeleteId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { profileVm.loadArchivedPosts() }

    Scaffold(
        containerColor = Background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        Strings.archivedPosts(language),
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
        when {
            loading && archivedPosts.isEmpty() -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Amber)
                }
            }
            archivedPosts.isEmpty() -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            Icons.Outlined.Archive, null,
                            tint = Muted, modifier = Modifier.size(56.dp),
                        )
                        Text(
                            Strings.archivedPostsEmpty(language),
                            color = Muted, fontSize = 15.sp,
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
                    itemsIndexed(archivedPosts, key = { _, p -> p.id }) { _, post ->
                        Column {
                            ConnectedPostCard(
                                post          = post,
                                navController = navController,
                                feedVm        = feedVm,
                                socialVm      = socialVm,
                                language      = language,
                            )
                            // ── Arşiv aksiyonları ────────────────────────
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                OutlinedButton(
                                    onClick = { profileVm.restoreArchivedPost(post.id) },
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Icon(Icons.Filled.Restore, null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(Strings.restorePost(language), fontSize = 13.sp)
                                }
                                OutlinedButton(
                                    onClick = { pendingDeleteId = post.id },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = androidx.compose.ui.graphics.Color(0xFFEF4444),
                                    ),
                                ) {
                                    Icon(Icons.Filled.Delete, null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(Strings.deleteForever(language), fontSize = 13.sp)
                                }
                            }
                            HorizontalDivider(color = Divider, thickness = 0.5.dp)
                        }
                    }
                }
            }
        }
    }

    // Kalıcı silme onay diyaloğu
    pendingDeleteId?.let { postId ->
        AlertDialog(
            onDismissRequest = { pendingDeleteId = null },
            containerColor   = HeftSurface,
            title   = { Text(Strings.deleteForever(language), color = OnBackground, fontWeight = FontWeight.SemiBold) },
            text    = { Text(Strings.deleteForeverConfirm(language), color = Muted, fontSize = 14.sp) },
            confirmButton = {
                TextButton(onClick = {
                    profileVm.permanentlyDeleteArchivedPost(postId)
                    pendingDeleteId = null
                }) {
                    Text(Strings.deleteForever(language), color = androidx.compose.ui.graphics.Color(0xFFEF4444), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteId = null }) {
                    Text(Strings.cancel(language), color = Muted)
                }
            },
        )
    }
}
