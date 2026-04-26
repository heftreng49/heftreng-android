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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.heftreng.app.ui.theme.*
import com.heftreng.app.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController : NavController,
    vm            : SettingsViewModel = hiltViewModel(),
) {
    val isDark   by vm.darkMode.collectAsState()
    val language by vm.language.collectAsState()

    Scaffold(
        containerColor = Background,
        topBar = {
            TopAppBar(
                title = { Text("Mîheng / Ayarlar", fontWeight = FontWeight.SemiBold, color = OnBackground) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri", tint = OnBackground)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Background),
            )
        }
    ) { padding ->
        LazyColumn(
            modifier       = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ── Görünüm ──────────────────────────────────────────────────
            item {
                SettingsSection(title = "Görünüm / Xuyangeh") {
                    // Karanlık / Aydınlık mod
                    Row(
                        modifier          = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            if (isDark) Icons.Filled.DarkMode else Icons.Outlined.LightMode,
                            contentDescription = null,
                            tint               = Amber,
                            modifier           = Modifier.size(22.dp),
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
                                checkedThumbColor  = Amber,
                                checkedTrackColor  = Amber.copy(alpha = 0.3f),
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
                            Column(Modifier.weight(1f)) {
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
                                        contentColor   = if (selected) androidx.compose.ui.graphics.Color.Black else Muted,
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
                    SettingsRow(icon = Icons.Outlined.Person, label = "Profili Düzenle", sub = "Profîlê biguherîne") {
                        navController.navigate("edit_profile")
                    }
                    HorizontalDivider(color = Divider, modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsRow(icon = Icons.Outlined.Lock, label = "Şifre Değiştir", sub = "Şîfreya nû") {}
                    HorizontalDivider(color = Divider, modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsRow(icon = Icons.Outlined.Email, label = "E-posta", sub = "Email biguherîne") {}
                }
            }

            // ── Bildirimler ──────────────────────────────────────────────
            item {
                SettingsSection(title = "Bildirimler / Agahdarî") {
                    var pushEnabled by remember { mutableStateOf(true) }
                    var emailEnabled by remember { mutableStateOf(false) }

                    Row(
                        modifier          = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.Notifications, null, tint = Amber, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Push Bildirimleri", color = OnBackground, fontWeight = FontWeight.Medium)
                            Text("Agahdariyên push", color = Muted, fontSize = 12.sp)
                        }
                        Switch(
                            checked         = pushEnabled,
                            onCheckedChange = { pushEnabled = it },
                            colors          = SwitchDefaults.colors(checkedThumbColor = Amber, checkedTrackColor = Amber.copy(alpha = 0.3f)),
                        )
                    }
                    HorizontalDivider(color = Divider, modifier = Modifier.padding(horizontal = 16.dp))
                    Row(
                        modifier          = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.Email, null, tint = Amber, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text("E-posta Bildirimleri", color = OnBackground, fontWeight = FontWeight.Medium)
                            Text("Agahdariyên email", color = Muted, fontSize = 12.sp)
                        }
                        Switch(
                            checked         = emailEnabled,
                            onCheckedChange = { emailEnabled = it },
                            colors          = SwitchDefaults.colors(checkedThumbColor = Amber, checkedTrackColor = Amber.copy(alpha = 0.3f)),
                        )
                    }
                }
            }

            // ── Gizlilik ─────────────────────────────────────────────────
            item {
                SettingsSection(title = "Gizlilik / Nepenî") {
                    var privateAccount by remember { mutableStateOf(false) }
                    Row(
                        modifier          = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.Lock, null, tint = Amber, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Gizli Hesap", color = OnBackground, fontWeight = FontWeight.Medium)
                            Text("Tenê şopîner dikarin bibînin", color = Muted, fontSize = 12.sp)
                        }
                        Switch(
                            checked         = privateAccount,
                            onCheckedChange = { privateAccount = it },
                            colors          = SwitchDefaults.colors(checkedThumbColor = Amber, checkedTrackColor = Amber.copy(alpha = 0.3f)),
                        )
                    }
                    HorizontalDivider(color = Divider, modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsRow(icon = Icons.Outlined.Block, label = "Engellenen Kullanıcılar", sub = "Bikarhênerên astengkirî") {}
                }
            }

            // ── Diğer ────────────────────────────────────────────────────
            item {
                SettingsSection(title = "Diğer / Yên Din") {
                    SettingsRow(icon = Icons.Outlined.Info, label = "Heftreng Hakkında", sub = "Derbarê heftreng") {}
                    HorizontalDivider(color = Divider, modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsRow(icon = Icons.Outlined.Description, label = "Kullanım Koşulları", sub = "Şert û mercên bikarhanînê") {}
                    HorizontalDivider(color = Divider, modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsRow(icon = Icons.Outlined.Shield, label = "Gizlilik Politikası", sub = "Siyaseta nepeniyê") {}
                }
            }

            // ── Çıkış ────────────────────────────────────────────────────
            item {
                SettingsSection {
                    Row(
                        modifier          = Modifier.fillMaxWidth().clickable { }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Logout, null, tint = androidx.compose.ui.graphics.Color(0xFFEF4444), modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(14.dp))
                        Text("Çıkış Yap / Derketin", color = androidx.compose.ui.graphics.Color(0xFFEF4444), fontWeight = FontWeight.Medium)
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun SettingsSection(title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column {
        if (title != null) {
            Text(
                title,
                color    = Muted,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
                letterSpacing = 0.5.sp,
            )
        }
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = HeftSurface,
        ) {
            Column(modifier = Modifier.fillMaxWidth(), content = content)
        }
    }
}

@Composable
private fun SettingsRow(icon: ImageVector, label: String, sub: String, onClick: () -> Unit) {
    Row(
        modifier          = Modifier.fillMaxWidth().clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
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
