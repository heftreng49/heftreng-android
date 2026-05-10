package com.heftreng.app.ui.screens.cms

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.heftreng.app.ui.theme.*
import com.heftreng.app.viewmodel.CmsSettings
import com.heftreng.app.viewmodel.CmsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CmsScreen(
    onBack: () -> Unit,
    vm: CmsViewModel = hiltViewModel(),
) {
    val settings by vm.settings.collectAsState()
    val loading  by vm.loading.collectAsState()
    val saved    by vm.saved.collectAsState()

    val tabs = listOf("Duyurular", "Kategoriler", "Reklamlar", "Özellikler")
    var selectedTab by remember { mutableIntStateOf(3) } // Özellikler default

    if (saved) {
        LaunchedEffect(Unit) {
            kotlinx.coroutines.delay(1500)
            vm.resetSaved()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("CMS Yönetimi", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Background,
                    titleContentColor = OnBackground,
                ),
            )
        },
        containerColor = Background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // Tab row
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                containerColor   = Background,
                contentColor     = Primary,
                edgePadding      = 0.dp,
            ) {
                tabs.forEachIndexed { i, title ->
                    Tab(
                        selected = selectedTab == i,
                        onClick  = { selectedTab = i },
                        text = {
                            Text(
                                title,
                                fontSize   = 13.sp,
                                fontWeight = if (selectedTab == i) FontWeight.Bold else FontWeight.Normal,
                                color      = if (selectedTab == i) Amber else Muted,
                            )
                        },
                    )
                }
            }

            if (loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Primary)
                }
                return@Scaffold
            }

            when (selectedTab) {
                0 -> AnnouncementsTab(settings, vm)
                1 -> CategoriesTab()
                2 -> AdsTab()
                3 -> FeaturesTab(settings, vm)
            }
        }
    }

    // Kayıt bildirimi
    if (saved) {
        Box(
            Modifier.fillMaxSize().padding(bottom = 32.dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF10B981),
            ) {
                Row(
                    Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Default.CheckCircle, null, tint = Color.White)
                    Text("Kaydedildi", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

// ── Özellikler Tab ─────────────────────────────────────────────────────────────
@Composable
private fun FeaturesTab(settings: CmsSettings, vm: CmsViewModel) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Ekranlar
        CmsSection("Ekranlar") {
            CmsToggleRow("Akış (Feed)",    settings.screenFeed)          { vm.toggle("screenFeed") }
            CmsToggleRow("Mesajlar",       settings.screenMessages)      { vm.toggle("screenMessages") }
            CmsToggleRow("Seriler",        settings.screenSerials)       { vm.toggle("screenSerials") }
            CmsToggleRow("Kitaplar",       settings.screenBooks)         { vm.toggle("screenBooks") }
            CmsToggleRow("Kurdî Fêrbibe", settings.screenKurdi)         { vm.toggle("screenKurdi") }
            CmsToggleRow("Bildirimler",    settings.screenNotifications) { vm.toggle("screenNotifications") }
            CmsToggleRow("Arama",          settings.screenSearch)        { vm.toggle("screenSearch") }
            CmsToggleRow("Hikayeler",      settings.screenStories)       { vm.toggle("screenStories") }
        }

        // Feed özellikleri
        CmsSection("Feed Özellikleri") {
            CmsToggleRow("Resim göster",   settings.feedShowImages)  { vm.toggle("feedShowImages") }
            CmsToggleRow("Alıntı göster",  settings.feedShowQuotes)  { vm.toggle("feedShowQuotes") }
            CmsToggleRow("Repost göster",  settings.feedShowReposts) { vm.toggle("feedShowReposts") }
        }

        // Sistem
        CmsSection("Sistem") {
            CmsToggleRow(
                label   = "Bakım Modu",
                checked = settings.maintenanceMode,
                tint    = Color(0xFFEF4444),
            ) { vm.toggle("maintenanceMode") }
        }

        // Kaydet butonu
        Button(
            onClick = { vm.save() },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Primary),
            shape  = RoundedCornerShape(12.dp),
        ) {
            Icon(Icons.Default.Save, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Kaydet", fontWeight = FontWeight.Bold)
        }
    }
}

// ── Duyurular Tab ──────────────────────────────────────────────────────────────
@Composable
private fun AnnouncementsTab(settings: CmsSettings, vm: CmsViewModel) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        CmsSection("Duyuru Ayarları") {
            CmsToggleRow("Duyuru Aktif", settings.announcementActive) {
                vm.toggle("announcementActive")
            }
            Spacer(Modifier.height(4.dp))
            OutlinedTextField(
                value         = settings.announcementText,
                onValueChange = { vm.update(settings.copy(announcementText = it)) },
                label         = { Text("Duyuru Metni") },
                modifier      = Modifier.fillMaxWidth(),
                minLines      = 2,
                colors        = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = Primary,
                    unfocusedBorderColor = Divider,
                    focusedLabelColor    = Primary,
                ),
            )
            OutlinedTextField(
                value         = settings.announcementUrl,
                onValueChange = { vm.update(settings.copy(announcementUrl = it)) },
                label         = { Text("Bağlantı URL (opsiyonel)") },
                modifier      = Modifier.fillMaxWidth(),
                colors        = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = Primary,
                    unfocusedBorderColor = Divider,
                    focusedLabelColor    = Primary,
                ),
            )
        }
        Button(
            onClick = { vm.save() },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Primary),
            shape  = RoundedCornerShape(12.dp),
        ) {
            Text("Kaydet", fontWeight = FontWeight.Bold)
        }
    }
}

// ── Kategoriler Tab (placeholder) ─────────────────────────────────────────────
@Composable
private fun CategoriesTab() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Category, null, tint = Muted, modifier = Modifier.size(48.dp))
            Spacer(Modifier.height(8.dp))
            Text("Kategoriler yakında", color = Muted)
        }
    }
}

// ── Reklamlar Tab (placeholder) ───────────────────────────────────────────────
@Composable
private fun AdsTab() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Campaign, null, tint = Muted, modifier = Modifier.size(48.dp))
            Spacer(Modifier.height(8.dp))
            Text("Reklam yönetimi yakında", color = Muted)
        }
    }
}

// ── Yardımcı Composable'lar ───────────────────────────────────────────────────
@Composable
private fun CmsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
        Text(
            title,
            color      = Amber,
            fontWeight = FontWeight.Bold,
            fontSize   = 13.sp,
            modifier   = Modifier.padding(bottom = 8.dp),
        )
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = HeftSurface,
        ) {
            Column(
                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                content = content,
            )
        }
    }
}

@Composable
private fun CmsToggleRow(
    label   : String,
    checked : Boolean,
    tint    : Color = Color.Unspecified,
    onToggle: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            label,
            fontSize = 15.sp,
            color    = if (tint != Color.Unspecified) tint else OnBackground,
        )
        Switch(
            checked         = checked,
            onCheckedChange = { onToggle() },
            colors          = SwitchDefaults.colors(
                checkedThumbColor      = Color.White,
                checkedTrackColor      = if (tint != Color.Unspecified) tint else Primary,
                uncheckedThumbColor    = Muted,
                uncheckedTrackColor    = HeftSurface,
                uncheckedBorderColor   = Divider,
            ),
        )
    }
}
