package com.heftreng.app.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
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
import androidx.navigation.NavController
import com.heftreng.app.navigation.AppPrefs
import com.heftreng.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavController) {
    var pushEnabled    by remember { mutableStateOf(true) }
    var emailEnabled   by remember { mutableStateOf(false) }
    var privateAccount by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = Background,
        topBar = {
            TopAppBar(
                title = { Text("Mîheng / Ayarlar", fontWeight = FontWeight.SemiBold, color = OnBackground) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri", tint = OnBackground)
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

            // ── Görünüm ──────────────────────────────────────────────────────
            item {
                SettingsSection("Görünüm / Xuyangeh") {
                    // Karanlık / Aydınlık mod
                    Row(
                        modifier          = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(if (AppPrefs.darkMode) Icons.Filled.DarkMode else Icons.Outlined.LightMode, null,
                            tint = AppPrefs.accentColor, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(if (AppPrefs.darkMode) "Karanlık Mod" else "Aydınlık Mod",
                                color = OnBackground, fontWeight = FontWeight.Medium)
                            Text(if (AppPrefs.darkMode) "Moda Tarî" else "Moda Ronahî", color = Muted, fontSize = 12.sp)
                        }
                        Switch(
                            checked         = AppPrefs.darkMode,
                            onCheckedChange = { AppPrefs.darkMode = it },
                            colors          = SwitchDefaults.colors(
                                checkedThumbColor   = Color.Black,
                                checkedTrackColor   = AppPrefs.accentColor,
                                uncheckedThumbColor = Muted,
                                uncheckedTrackColor = SurfaceVar,
                            ),
                        )
                    }

                    HorizontalDivider(color = Divider, modifier = Modifier.padding(horizontal = 16.dp))

                    // Yazı boyutu
                    Row(
                        modifier          = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.FormatSize, null, tint = AppPrefs.accentColor, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Yazı Boyutu / Mezinahiya Tîpan", color = OnBackground, fontWeight = FontWeight.Medium)
                            Text("Şu an: ${AppPrefs.fontSize}sp", color = Muted, fontSize = 12.sp)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { if (AppPrefs.fontSize > 12) AppPrefs.fontSize-- }, modifier = Modifier.size(32.dp)) {
                                Text("−", color = AppPrefs.accentColor, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            }
                            Text("${AppPrefs.fontSize}", color = OnBackground, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            IconButton(onClick = { if (AppPrefs.fontSize < 22) AppPrefs.fontSize++ }, modifier = Modifier.size(32.dp)) {
                                Text("+", color = AppPrefs.accentColor, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            }
                        }
                    }

                    HorizontalDivider(color = Divider, modifier = Modifier.padding(horizontal = 16.dp))

                    // Renk seçici
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Palette, null, tint = AppPrefs.accentColor, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(14.dp))
                            Text("Vurgu Rengi / Reng", color = OnBackground, fontWeight = FontWeight.Medium)
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            listOf(
                                Color(0xFFF59E0B) to "Amber",
                                Color(0xFF6366F1) to "Indigo",
                                Color(0xFFEF4444) to "Kırmızı",
                                Color(0xFF10B981) to "Yeşil",
                                Color(0xFF0EA5E9) to "Mavi",
                            ).forEach { (color, label) ->
                                val selected = AppPrefs.accentColor == color
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Box(
                                        modifier         = Modifier
                                            .size(38.dp).clip(CircleShape).background(color)
                                            .clickable { AppPrefs.accentColor = color },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        if (selected) Icon(Icons.Default.Check, null, tint = Color.Black, modifier = Modifier.size(18.dp))
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text(label, color = if (selected) AppPrefs.accentColor else Muted, fontSize = 10.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = Divider, modifier = Modifier.padding(horizontal = 16.dp))

                    // Dil
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Translate, null, tint = AppPrefs.accentColor, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(14.dp))
                            Text("Dil / Ziman", color = OnBackground, fontWeight = FontWeight.Medium)
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("tr" to "Türkçe", "ku" to "Kurdî").forEach { (code, label) ->
                                val sel = AppPrefs.language == code
                                Button(
                                    onClick  = { AppPrefs.language = code },
                                    modifier = Modifier.weight(1f),
                                    shape    = RoundedCornerShape(10.dp),
                                    colors   = ButtonDefaults.buttonColors(
                                        containerColor = if (sel) AppPrefs.accentColor else SurfaceVar,
                                        contentColor   = if (sel) Color.Black else Muted,
                                    ),
                                ) { Text(label, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal) }
                            }
                        }
                    }
                }
            }

            // ── Hesap ─────────────────────────────────────────────────────────
            item {
                SettingsSection("Hesap / Hesab") {
                    SettingsRow(Icons.Outlined.Person, "Profili Düzenle", "Profîlê biguherîne") { navController.navigate("edit_profile") }
                    HorizontalDivider(color = Divider, modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsRow(Icons.Outlined.Lock, "Şifre Değiştir", "Şîfreya nû") {}
                    HorizontalDivider(color = Divider, modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsRow(Icons.Outlined.Email, "E-posta Güncelle", "Email nû") {}
                }
            }

            // ── Bildirimler ───────────────────────────────────────────────────
            item {
                SettingsSection("Bildirimler / Agahdarî") {
                    SettingsSwitchRow(Icons.Outlined.Notifications, "Push Bildirimleri", "Agahdariyên push", pushEnabled) { pushEnabled = it }
                    HorizontalDivider(color = Divider, modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsSwitchRow(Icons.Outlined.Email, "E-posta Bildirimleri", "Agahdariyên email", emailEnabled) { emailEnabled = it }
                }
            }

            // ── Gizlilik ──────────────────────────────────────────────────────
            item {
                SettingsSection("Gizlilik / Nepenî") {
                    SettingsSwitchRow(Icons.Outlined.Lock, "Gizli Hesap", "Tenê şopîner dikarin bibînin", privateAccount) { privateAccount = it }
                    HorizontalDivider(color = Divider, modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsRow(Icons.Outlined.Block, "Engellenen Kullanıcılar", "Bikarhênerên astengkirî") {}
                }
            }

            // ── Diğer ─────────────────────────────────────────────────────────
            item {
                SettingsSection("Diğer / Yên Din") {
                    SettingsRow(Icons.Outlined.Info, "Heftreng Hakkında", "v4.0 — civaka nivîskar") {}
                    HorizontalDivider(color = Divider, modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsRow(Icons.Outlined.Description, "Kullanım Koşulları", "Şert û mercên bikarhanînê") {}
                    HorizontalDivider(color = Divider, modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsRow(Icons.Outlined.Shield, "Gizlilik Politikası", "Siyaseta nepeniyê") {}
                }
            }

            // ── Çıkış ─────────────────────────────────────────────────────────
            item {
                Surface(shape = RoundedCornerShape(16.dp), color = Surface) {
                    Row(
                        modifier          = Modifier.fillMaxWidth().clickable {}.padding(horizontal = 16.dp, vertical = 14.dp),
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

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(title, color = Muted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp), letterSpacing = 0.5.sp)
        Surface(shape = RoundedCornerShape(16.dp), color = Surface) {
            Column(modifier = Modifier.fillMaxWidth(), content = content)
        }
    }
}

@Composable
private fun SettingsRow(icon: ImageVector, label: String, sub: String, onClick: () -> Unit) {
    Row(
        modifier          = Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = AppPrefs.accentColor, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(label, color = OnBackground, fontWeight = FontWeight.Medium)
            Text(sub, color = Muted, fontSize = 12.sp)
        }
        Icon(Icons.Default.ChevronRight, null, tint = Muted, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun SettingsSwitchRow(icon: ImageVector, label: String, sub: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(
        modifier          = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = AppPrefs.accentColor, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(label, color = OnBackground, fontWeight = FontWeight.Medium)
            Text(sub, color = Muted, fontSize = 12.sp)
        }
        Switch(
            checked         = checked,
            onCheckedChange = onChecked,
            colors          = SwitchDefaults.colors(
                checkedThumbColor   = Color.Black,
                checkedTrackColor   = AppPrefs.accentColor,
                uncheckedThumbColor = Muted,
                uncheckedTrackColor = SurfaceVar,
            ),
        )
    }
}
