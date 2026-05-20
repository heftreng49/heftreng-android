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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
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
    vm            : SettingsViewModel = hiltViewModel(),
    authVm        : AuthViewModel     = hiltViewModel(),
) {
    val isDark         by vm.darkMode.collectAsState()
    val language       by vm.language.collectAsState()
    val pushEnabled    by vm.pushEnabled.collectAsState()
    val privateAccount by vm.privateAccount.collectAsState()
    val blockedUsers   by vm.blockedUsers.collectAsState()
    val blockedLoading by vm.blockedLoading.collectAsState()

    // Dialog state'leri
    var showPasswordDialog by remember { mutableStateOf(false) }
    var showEmailDialog    by remember { mutableStateOf(false) }
    var showBlockedDialog  by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.loadBlockedUsers() }

    Scaffold(
        containerColor = Background,
        topBar = {
            TopAppBar(
                title = { Text(if (language == "ku") "Mîheng" else "Ayarlar", fontWeight = FontWeight.SemiBold, color = OnBackground) },
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
                SettingsSection(title = if (language == "ku") "Xuyangeh" else "Görünüm") {
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
                            Text(
                                if (isDark) (if (language == "ku") "Moda Tarî" else "Karanlık Mod")
                                else (if (language == "ku") "Moda Ronahî" else "Aydınlık Mod"),
                                color = OnBackground, fontWeight = FontWeight.Medium,
                            )
                            Text(
                                if (isDark) "Rêya Tarî" else "Rêya Ronahî",
                                color = Muted, fontSize = 12.sp,
                            )
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

                    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Translate, null, tint = Amber, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(14.dp))
                            Column {
                                Text(if (language == "ku") "Ziman" else "Dil", color = OnBackground, fontWeight = FontWeight.Medium)
                                Text(if (language == "ku") "Zimanê serîlêdanê hilbijêre" else "Uygulama dilini seç", color = Muted, fontSize = 12.sp)
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
                SettingsSection(title = if (language == "ku") "Hesab" else "Hesap") {
                    SettingsRow(
                        Icons.Outlined.Person,
                        if (language == "ku") "Profîlê Biguherîne" else "Profili Düzenle",
                        if (language == "ku") "Profîla xwe nûve bike" else "Profil bilgilerini düzenle",
                    ) {
                        navController.navigate("edit_profile")
                    }
                    HorizontalDivider(color = Divider, modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsRow(
                        Icons.Outlined.Lock,
                        if (language == "ku") "Şîreya Biguherîne" else "Şifre Değiştir",
                        if (language == "ku") "Şîreya nû destnîşan bike" else "Yeni şifre belirle",
                    ) {
                        showPasswordDialog = true
                    }
                    HorizontalDivider(color = Divider, modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsRow(
                        Icons.Outlined.Email,
                        if (language == "ku") "E-Postayê Biguherîne" else "E-posta Değiştir",
                        vm.currentEmail.ifBlank { if (language == "ku") "Email biguherîne" else "E-posta adresi ekle" },
                    ) {
                        showEmailDialog = true
                    }
                }
            }

            // ── Bildirimler ──────────────────────────────────────────────
            item {
                SettingsSection(title = if (language == "ku") "Agahdarî" else "Bildirimler") {
                    SettingsSwitchRow(
                        icon    = Icons.Outlined.Notifications,
                        label   = if (language == "ku") "Agahdariyên Push" else "Push Bildirimleri",
                        sub     = if (language == "ku") "Agahdariyên push veke/bigire" else "Anlık bildirimleri aç/kapat",
                        checked = pushEnabled,
                        onCheck = { vm.togglePush() },
                    )
                }
            }

            // ── Gizlilik ─────────────────────────────────────────────────
            item {
                SettingsSection(title = if (language == "ku") "Nepenî" else "Gizlilik") {
                    SettingsSwitchRow(
                        icon    = Icons.Outlined.Lock,
                        label   = if (language == "ku") "Hesabê Veşartî" else "Gizli Hesap",
                        sub     = if (language == "ku") "Tenê şopîner dikarin bibînin" else "Sadece takipçiler görebilir",
                        checked = privateAccount,
                        onCheck = { vm.togglePrivate() },
                    )
                    HorizontalDivider(color = Divider, modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsRow(
                        Icons.Outlined.Block,
                        if (language == "ku") "Bikarhênerên Astengkirî" else "Engellenen Kullanıcılar",
                        if (language == "ku") "Bikarhênerên astengkirî birêve bibe" else "Engellenen hesapları yönet",
                    ) { showBlockedDialog = true }
                }
            }

            // ── Diğer ────────────────────────────────────────────────────
            item {
                SettingsSection(title = if (language == "ku") "Yên Din" else "Diğer") {
                    SettingsRow(
                        Icons.Outlined.Info,
                        if (language == "ku") "Derbarê Heftreng" else "Heftreng Hakkında",
                        if (language == "ku") "Serîlêdanê nas bike" else "Uygulama hakkında bilgi",
                    ) {
                        navController.navigate(Screen.CmsPage.go("hakkinda"))
                    }
                    HorizontalDivider(color = Divider, modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsRow(
                        Icons.Outlined.Description,
                        if (language == "ku") "Şert û Mercên Bikarhanînê" else "Kullanım Koşulları",
                        if (language == "ku") "Peymanname bixwîne" else "Kullanım şartlarını görüntüle",
                    ) {
                        navController.navigate(Screen.CmsPage.go("kullanim-kosullari"))
                    }
                    HorizontalDivider(color = Divider, modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsRow(
                        Icons.Outlined.Shield,
                        if (language == "ku") "Siyaseta Nepeniyê" else "Gizlilik Politikası",
                        if (language == "ku") "Siyaseta nepeniyê bixwîne" else "Gizlilik politikasını görüntüle",
                    ) {
                        navController.navigate(Screen.CmsPage.go("gizlilik-politikasi"))
                    }
                }
            }

            // ── Admin ────────────────────────────────────────────────────
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
                            Text(if (language == "ku") "Panela Admin" else "Admin Paneli", color = Error, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
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
                            .clickable { authVm.signOut() }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Logout, null, tint = Color(0xFFEF4444), modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(14.dp))
                        Text(if (language == "ku") "Derketin" else "Çıkış Yap", color = Color(0xFFEF4444), fontWeight = FontWeight.Medium)
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    // ── Şifre Değiştir Dialog ─────────────────────────────────────────────────
    if (showPasswordDialog) {
        ChangePasswordDialog(
            onDismiss = { showPasswordDialog = false },
            onConfirm = { current, newPw ->
                vm.changePassword(
                    currentPassword = current,
                    newPassword     = newPw,
                    onSuccess       = { showPasswordDialog = false },
                    onError         = { /* dialog içinde hata gösteriliyor */ },
                )
            },
            vm       = vm,
            language = language,
        )
    }

    // ── E-posta Değiştir Dialog ───────────────────────────────────────────────
    if (showEmailDialog) {
        ChangeEmailDialog(
            currentEmail = vm.currentEmail,
            onDismiss    = { showEmailDialog = false },
            onConfirm    = { password, newEmail ->
                vm.changeEmail(
                    currentPassword = password,
                    newEmail        = newEmail,
                    onSuccess       = { showEmailDialog = false },
                    onError         = {},
                )
            },
            vm       = vm,
            language = language,
        )
    }

    // ── Engellenen Kullanıcılar Dialog ───────────────────────────────────────
    if (showBlockedDialog) {
        BlockedUsersDialog(
            language      = language,
            blockedUsers  = blockedUsers,
            loading       = blockedLoading,
            onUnblock     = { uid -> vm.unblockUser(uid) },
            onDismiss     = { showBlockedDialog = false },
        )
    }
}

// ── Şifre Değiştir Dialog ─────────────────────────────────────────────────────
@Composable
private fun ChangePasswordDialog(
    onDismiss : () -> Unit,
    onConfirm : (String, String) -> Unit,
    vm        : SettingsViewModel,
    language  : String = "tr",
) {
    val ku = language == "ku"
    var currentPw  by remember { mutableStateOf("") }
    var newPw      by remember { mutableStateOf("") }
    var newPwAgain by remember { mutableStateOf("") }
    var error      by remember { mutableStateOf<String?>(null) }
    var loading    by remember { mutableStateOf(false) }
    var showCurrent by remember { mutableStateOf(false) }
    var showNew     by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!loading) onDismiss() },
        containerColor   = HeftSurface,
        title = { Text(if (ku) "Şîreya Biguherîne" else "Şifre Değiştir", color = OnBackground, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value         = currentPw,
                    onValueChange = { currentPw = it; error = null },
                    label         = { Text(if (ku) "Şîreya Niha" else "Mevcut Şifre") },
                    singleLine    = true,
                    modifier      = Modifier.fillMaxWidth(),
                    visualTransformation = if (showCurrent) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showCurrent = !showCurrent }) {
                            Icon(if (showCurrent) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, null, tint = Muted)
                        }
                    },
                    colors = settingsTextFieldColors(),
                )
                OutlinedTextField(
                    value         = newPw,
                    onValueChange = { newPw = it; error = null },
                    label         = { Text(if (ku) "Şîreya Nû" else "Yeni Şifre") },
                    singleLine    = true,
                    modifier      = Modifier.fillMaxWidth(),
                    visualTransformation = if (showNew) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showNew = !showNew }) {
                            Icon(if (showNew) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, null, tint = Muted)
                        }
                    },
                    colors = settingsTextFieldColors(),
                )
                OutlinedTextField(
                    value         = newPwAgain,
                    onValueChange = { newPwAgain = it; error = null },
                    label         = { Text(if (ku) "Şîreya Nû (Dubare)" else "Yeni Şifre (Tekrar)") },
                    singleLine    = true,
                    modifier      = Modifier.fillMaxWidth(),
                    visualTransformation = PasswordVisualTransformation(),
                    isError       = newPwAgain.isNotBlank() && newPw != newPwAgain,
                    colors = settingsTextFieldColors(),
                )
                if (error != null) {
                    Text(error!!, color = Error, fontSize = 12.sp)
                }
                if (newPwAgain.isNotBlank() && newPw != newPwAgain) {
                    Text(if (ku) "Şîre li hev nayên" else "Şifreler eşleşmiyor", color = Error, fontSize = 12.sp)
                }
                var showForgotFromSettings by remember { mutableStateOf(false) }
                TextButton(
                    onClick        = { showForgotFromSettings = true },
                    contentPadding = PaddingValues(0.dp),
                ) {
                    Text(
                        if (ku) "Şîreya xwe ji bîr kir? Bi maîlê sifir bike →"
                        else "Şifreni mi unuttun? Mail ile sıfırla →",
                        color = Amber, fontSize = 12.sp,
                    )
                }
                if (showForgotFromSettings) {
                    val authVm2 : AuthViewModel = hiltViewModel()
                    ForgotPasswordFromSettings(
                        prefillEmail = authVm2.currentEmail,
                        onDismiss    = { showForgotFromSettings = false },
                        authVm       = authVm2,
                        language     = language,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    when {
                        currentPw.isBlank() -> error = if (ku) "Şîreya niha binivîse" else "Mevcut şifreyi girin"
                        newPw.length < 6    -> error = if (ku) "Şîreya nû divê herî kêm 6 tîp be" else "Yeni şifre en az 6 karakter olmalı"
                        newPw != newPwAgain -> error = if (ku) "Şîre li hev nayên" else "Şifreler eşleşmiyor"
                        else -> {
                            loading = true
                            vm.changePassword(
                                currentPassword = currentPw,
                                newPassword     = newPw,
                                onSuccess       = { loading = false; onDismiss() },
                                onError         = { msg -> loading = false; error = msg },
                            )
                        }
                    }
                },
                enabled = !loading,
            ) {
                if (loading) CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Amber, strokeWidth = 2.dp)
                else Text(if (ku) "Tomarkirin" else "Kaydet", color = Amber, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = { if (!loading) onDismiss() }) { Text(if (ku) "Betal bike" else "İptal", color = Muted) }
        },
    )
}

// ── E-posta Değiştir Dialog ───────────────────────────────────────────────────
@Composable
private fun ChangeEmailDialog(
    currentEmail : String,
    onDismiss    : () -> Unit,
    onConfirm    : (String, String) -> Unit,
    vm           : SettingsViewModel,
    language     : String = "tr",
) {
    val ku = language == "ku"
    var password  by remember { mutableStateOf("") }
    var newEmail  by remember { mutableStateOf("") }
    var error     by remember { mutableStateOf<String?>(null) }
    var loading   by remember { mutableStateOf(false) }
    var showPw    by remember { mutableStateOf(false) }
    var success   by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!loading) onDismiss() },
        containerColor   = HeftSurface,
        title = { Text(if (ku) "E-Postayê Biguherîne" else "E-posta Değiştir", color = OnBackground, fontWeight = FontWeight.Bold) },
        text = {
            if (success) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF22C55E), modifier = Modifier.size(36.dp))
                    Text(
                        if (ku) "E-posta piştrastkirinê hate şandin. Navnîşana nû bipejirîne."
                        else "Doğrulama e-postası gönderildi. Yeni adresinizi onaylayın.",
                        color = OnBackground, fontSize = 14.sp,
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("${if (ku) "Heyî" else "Mevcut"}: $currentEmail", color = Muted, fontSize = 12.sp)
                    OutlinedTextField(
                        value         = newEmail,
                        onValueChange = { newEmail = it; error = null },
                        label         = { Text(if (ku) "E-Postaya Nû" else "Yeni E-Posta") },
                        singleLine    = true,
                        modifier      = Modifier.fillMaxWidth(),
                        colors        = settingsTextFieldColors(),
                    )
                    OutlinedTextField(
                        value         = password,
                        onValueChange = { password = it; error = null },
                        label         = { Text(if (ku) "Şîreya Niha" else "Mevcut Şifre") },
                        singleLine    = true,
                        modifier      = Modifier.fillMaxWidth(),
                        visualTransformation = if (showPw) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showPw = !showPw }) {
                                Icon(if (showPw) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, null, tint = Muted)
                            }
                        },
                        colors = settingsTextFieldColors(),
                    )
                    if (error != null) Text(error!!, color = Error, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            if (!success) {
                TextButton(
                    onClick = {
                        when {
                            newEmail.isBlank() || !android.util.Patterns.EMAIL_ADDRESS.matcher(newEmail).matches() ->
                                error = if (ku) "E-postayek derbasdar binivîse" else "Geçerli bir e-posta girin"
                            password.isBlank() -> error = if (ku) "Şîreya xwe binivîse" else "Şifrenizi girin"
                            else -> {
                                loading = true
                                vm.changeEmail(
                                    currentPassword = password,
                                    newEmail        = newEmail,
                                    onSuccess       = { loading = false; success = true },
                                    onError         = { msg -> loading = false; error = msg },
                                )
                            }
                        }
                    },
                    enabled = !loading,
                ) {
                    if (loading) CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Amber, strokeWidth = 2.dp)
                    else Text(if (ku) "Piştrastkirinê Bişîne" else "Doğrulama Gönder", color = Amber, fontWeight = FontWeight.Bold)
                }
            } else {
                TextButton(onClick = onDismiss) { Text(if (ku) "Temam" else "Tamam", color = Amber, fontWeight = FontWeight.Bold) }
            }
        },
        dismissButton = {
            if (!success) TextButton(onClick = { if (!loading) onDismiss() }) { Text(if (ku) "Betal bike" else "İptal", color = Muted) }
        },
    )
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

@Composable
private fun settingsTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor      = Amber,
    unfocusedBorderColor    = Divider,
    focusedTextColor        = OnBackground,
    unfocusedTextColor      = OnBackground,
    unfocusedContainerColor = SurfaceVar,
    focusedContainerColor   = SurfaceVar,
    focusedLabelColor       = Amber,
    unfocusedLabelColor     = Muted,
    cursorColor             = Amber,
)

@Composable
internal fun ForgotPasswordFromSettings(
    prefillEmail: String,
    onDismiss   : () -> Unit,
    authVm      : AuthViewModel,
    language    : String = "tr",
) {
    val ku = language == "ku"
    var resetEmail by remember { mutableStateOf(prefillEmail) }
    var error      by remember { mutableStateOf<String?>(null) }
    var loading    by remember { mutableStateOf(false) }
    var success    by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!loading) onDismiss() },
        containerColor   = HeftSurface,
        title = { Text(if (ku) "Şîreya Xwe Sifir Bike" else "Şifreni Sıfırla", color = OnBackground, fontWeight = FontWeight.Bold) },
        text = {
            if (success) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("✅", fontSize = 32.sp)
                    Text(
                        if (ku) "Lînka sifirkirinê hate şandin. E-postaya xwe kontrol bike."
                        else "Şifre sıfırlama bağlantısı gönderildi. E-posta kutunuzu kontrol edin.",
                        color = OnBackground, fontSize = 14.sp,
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        if (ku) "Em ê lînka sifirkirinê ji bo e-postaya qeydkirî bişînin."
                        else "Kayıtlı e-posta adresinize şifre sıfırlama bağlantısı göndereceğiz.",
                        color = Muted, fontSize = 13.sp,
                    )
                    OutlinedTextField(
                        value         = resetEmail,
                        onValueChange = { resetEmail = it; error = null },
                        label         = { Text("E-posta") },
                        singleLine    = true,
                        modifier      = Modifier.fillMaxWidth(),
                        colors        = settingsTextFieldColors(),
                    )
                    if (error != null) Text(error!!, color = Error, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            if (!success) {
                TextButton(
                    onClick = {
                        loading = true
                        authVm.sendPasswordReset(
                            email     = resetEmail,
                            onSuccess = { loading = false; success = true },
                            onError   = { msg -> loading = false; error = msg },
                        )
                    },
                    enabled = !loading,
                ) {
                    if (loading) CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Amber, strokeWidth = 2.dp)
                    else Text(if (ku) "Bişîne" else "Gönder", color = Amber, fontWeight = FontWeight.Bold)
                }
            } else {
                TextButton(onClick = onDismiss) { Text(if (ku) "Temam" else "Tamam", color = Amber, fontWeight = FontWeight.Bold) }
            }
        },
        dismissButton = {
            if (!success) TextButton(onClick = { if (!loading) onDismiss() }) { Text(if (ku) "Betal bike" else "İptal", color = Muted) }
        },
    )
}

// ── Engellenen Kullanıcılar Dialog ───────────────────────────────────────────
@Composable
private fun BlockedUsersDialog(
    language     : String,
    blockedUsers : List<com.heftreng.app.data.model.BlockedUser>,
    loading      : Boolean,
    onUnblock    : (String) -> Unit,
    onDismiss    : () -> Unit,
) {
    val ku = language == "ku"
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = HeftSurface,
        title = {
            Text(
                if (ku) "Bikarhênerên Astengkirî" else "Engellenen Kullanıcılar",
                color = OnBackground, fontWeight = FontWeight.Bold,
            )
        },
        text = {
            if (loading) {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Amber, modifier = Modifier.size(28.dp))
                }
            } else if (blockedUsers.isEmpty()) {
                Text(
                    if (ku) "Bikarhênerên astengkirî tune ne." else "Engellenmiş kullanıcı yok.",
                    color = Muted, fontSize = 14.sp,
                )
            } else {
                androidx.compose.foundation.lazy.LazyColumn(
                    modifier           = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    androidx.compose.foundation.lazy.items(blockedUsers) { user ->
                        Row(
                            modifier          = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            coil.compose.AsyncImage(
                                model              = user.photoURL.ifEmpty { null },
                                contentDescription = null,
                                modifier           = Modifier
                                    .size(38.dp)
                                    .clip(androidx.compose.foundation.shape.CircleShape)
                                    .background(SurfaceVar),
                                contentScale       = androidx.compose.ui.layout.ContentScale.Crop,
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                user.displayName.ifBlank { "Kullanıcı" },
                                color = OnBackground, fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = { onUnblock(user.uid) }) {
                                Text(
                                    if (ku) "Astengiyê Berde" else "Engeli Kaldır",
                                    color = Amber, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(if (ku) "Bigire" else "Kapat", color = Amber, fontWeight = FontWeight.Bold)
            }
        },
    )
}
