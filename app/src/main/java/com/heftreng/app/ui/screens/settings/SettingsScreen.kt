package com.heftreng.app.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.heftreng.app.navigation.Screen
import com.heftreng.app.ui.theme.*
import com.heftreng.app.viewmodel.AuthViewModel
import com.heftreng.app.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController : NavController,
    // NavHost'taki aynı ViewModel instance'ını kullan — activity scope
    vm            : SettingsViewModel = hiltViewModel(),
    authVm        : AuthViewModel     = hiltViewModel(),
) {
    val isDark   by vm.darkMode.collectAsState()
    val language by vm.language.collectAsState()
    val pushEnabled   by vm.pushEnabled.collectAsState()
    val privateAccount by vm.privateAccount.collectAsState()

    Scaffold(
        containerColor = Background,
        topBar = {
            TopAppBar(
                title = { Text("Mîheng / Ayarlar", fontWeight = FontWeight.SemiBold, color = OnBackground) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = OnBackground)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Background),
            )
        }
    ) { padding ->
        LazyColumn(
            modifier            = Modifier.fillMaxSize().padding(padding),
            contentPadding      = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {

            // ── Görünüm ──────────────────────────────────────────────────
            item {
                SettingsSection(title = "Görünüm / Xuyangeh") {
                    Row(
                        modifier          = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            if (isDark) Icons.Filled.DarkMode else Icons.Outlined.LightMode,
                            null, tint = Amber, modifier = Modifier.size(22.dp),
                        )
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(if (isDark) "Karanlık Mod" else "Aydınlık Mod", color = OnBackground, fontWeight = FontWeight.Medium)
                            Text(if (isDark) "Rêya Tarî" else "Rêya Ronahî", color = Muted, fontSize = 12.sp)
                        }
                        Switch(
                            checked         = isDark,
                            onCheckedChange = { vm.toggleDarkMode() },
                            colors          = SwitchDefaults.colors(
                                checkedThumbColor   = Amber,
                                checkedTrackColor   = Amber.copy(alpha = 0.35f),
                                uncheckedThumbColor = Muted,
                                uncheckedTrackColor = Muted.copy(alpha = 0.2f),
                            ),
                        )
                    }

                    HorizontalDivider(color = Divider, modifier = Modifier.padding(horizontal = 16.dp))

                    // Dil seçimi
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Translate, null, tint = Amber, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(14.dp))
                            Column {
                                Text("Dil / Ziman", color = OnBackground, fontWeight = FontWeight.Medium)
                                Text("Uygulama dilini seç", color = Muted, fontSize = 12.sp)
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("tr" to "Türkçe", "ku" to "Kurdî").forEach { (code, label) ->
                                val selected = language == code
                                Button(
                                    onClick  = { vm.setLanguage(code) },
                                    modifier = Modifier.weight(1f),
                                    shape    = RoundedCornerShape(10.dp),
                                    colors   = ButtonDefaults.buttonColors(
                                        containerColor = if (selected) Amber else SurfaceVar,
                                        contentColor   = if (selected) Color.Black else Muted,
                                    ),
                                ) { Text(label, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal) }
                            }
                        }
                    }
                }
            }

            // ── Hesap ────────────────────────────────────────────────────
            item {
                SettingsSection(title = "Hesap / Hesab") {
                    SettingsRow(Icons.Outlined.Person, "Profili Düzenle", "Profîlê biguherîne") {
                        navController.navigate("edit_profile")
                    }
                    HorizontalDivider(color = Divider, modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsRow(Icons.Outlined.Lock, "Şifre Değiştir", "Şîfreya nû") {}
                    HorizontalDivider(color = Divider, modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsRow(Icons.Outlined.Email, "E-posta", "Email biguherîne") {}
                }
            }

            // ── Bildirimler ──────────────────────────────────────────────
            item {
                SettingsSection(title = "Bildirimler / Agahdarî") {
                    SettingsSwitchRow(
                        icon    = Icons.Outlined.Notifications,
                        label   = "Push Bildirimleri",
                        sub     = "Agahdariyên push",
                        checked = pushEnabled,
                        onCheck = { vm.togglePush() },
                    )
                }
            }

            // ── Gizlilik ─────────────────────────────────────────────────
            item {
                SettingsSection(title = "Gizlilik / Nepenî") {
                    SettingsSwitchRow(
                        icon    = Icons.Outlined.Lock,
                        label   = "Gizli Hesap",
                        sub     = "Tenê şopîner dikarin bibînin",
                        checked = privateAccount,
                        onCheck = { vm.togglePrivate() },
                    )
                    HorizontalDivider(color = Divider, modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsRow(Icons.Outlined.Block, "Engellenen Kullanıcılar", "Bikarhênerên astengkirî") {}
                }
            }

            // ── Diğer ────────────────────────────────────────────────────
            item {
                SettingsSection(title = "Diğer / Yên Din") {
                    SettingsRow(Icons.Outlined.Info, "Heftreng Hakkında", "Derbarê heftreng") {
                        navController.navigate(Screen.CmsPage.go("hakkinda"))
                    }
                    HorizontalDivider(color = Divider, modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsRow(Icons.Outlined.Description, "Kullanım Koşulları", "Şert û mercên bikarhanînê") {
                        navController.navigate(Screen.CmsPage.go("kullanim-kosullari"))
                    }
                    HorizontalDivider(color = Divider, modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsRow(Icons.Outlined.Shield, "Gizlilik Politikası", "Siyaseta nepeniyê") {
                        navController.navigate(Screen.CmsPage.go("gizlilik-politikasi"))
                    }
                }
            }

            // ── Admin (sadece admin email) ────────────────────────────────
            if (vm.isAdmin) {
                item {
                    SettingsSection {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { navController.navigate("admin") }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Default.AdminPanelSettings, null, tint = Error, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(14.dp))
                            Text("Admin Paneli", color = Error, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            Icon(Icons.Default.ChevronRight, null, tint = Error, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            // ── Çıkış ────────────────────────────────────────────────────
            item {
                SettingsSection {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                authVm.signOut()
                                // NavHost auth null olunca otomatik AuthScreen'e gider
                            }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Logout, null, tint = Color(0xFFEF4444), modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(14.dp))
                        Text("Çıkış Yap / Derketin", color = Color(0xFFEF4444), fontWeight = FontWeight.Medium)
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

// ── Bileşenler ────────────────────────────────────────────────────────────────

@Composable
private fun SettingsSection(title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column {
        if (title != null) {
            Text(
                title,
                color         = Muted,
                fontSize      = 11.sp,
                fontWeight    = FontWeight.SemiBold,
                modifier      = Modifier.padding(start = 4.dp, bottom = 6.dp),
                letterSpacing = 0.5.sp,
            )
        }
        Surface(shape = RoundedCornerShape(16.dp), color = HeftSurface) {
            Column(modifier = Modifier.fillMaxWidth(), content = content)
        }
    }
}

@Composable
private fun SettingsRow(icon: ImageVector, label: String, sub: String, onClick: () -> Unit) {
    Row(
        modifier          = Modifier.fillMaxWidth().clickable { onClick() }.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Amber, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(label, color = OnBackground, fontWeight = FontWeight.Medium)
            Text(sub, color = Muted, fontSize = 12.sp)
        }
        Icon(Icons.Default.ChevronRight, null, tint = Muted, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun SettingsSwitchRow(
    icon   : ImageVector,
    label  : String,
    sub    : String,
    checked: Boolean,
    onCheck: () -> Unit,
) {
    Row(
        modifier          = Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Amber, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(label, color = OnBackground, fontWeight = FontWeight.Medium)
            Text(sub, color = Muted, fontSize = 12.sp)
        }
        Switch(
            checked         = checked,
            onCheckedChange = { onCheck() },
            colors          = SwitchDefaults.colors(
                checkedThumbColor   = Amber,
                checkedTrackColor   = Amber.copy(alpha = 0.35f),
                uncheckedThumbColor = Muted,
                uncheckedTrackColor = Muted.copy(alpha = 0.2f),
            ),
        )
    }
}
