package com.heftreng.app.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.heftreng.app.util.ConsentHelper
import com.heftreng.app.viewmodel.AdminViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.heftreng.app.navigation.Screen
import com.heftreng.app.ui.i18n.Strings

import com.heftreng.app.ui.theme.*
import com.heftreng.app.ui.component.ThemeSelector
import com.heftreng.app.viewmodel.AuthViewModel
import android.app.Activity
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import com.heftreng.app.viewmodel.SettingsViewModel

private fun android.content.Context.findActivity(): android.app.Activity? {
    var ctx = this
    while (ctx is android.content.ContextWrapper) {
        if (ctx is android.app.Activity) return ctx
        ctx = ctx.baseContext
    }
    return ctx as? android.app.Activity
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController : NavController,
    vm            : SettingsViewModel = hiltViewModel(),
    authVm        : AuthViewModel     = hiltViewModel(),
    adminVm       : AdminViewModel    = hiltViewModel(),
) {
    val themeMode          by vm.themeMode.collectAsState()
    val themeVariant       by vm.themeVariant.collectAsState()
    val isDarkNow          = themeMode == "dark" || (themeMode == "system" && androidx.compose.foundation.isSystemInDarkTheme())
    val adminPerms     by adminVm.perms.collectAsState()
    val isAdmin        = adminPerms?.isStaff() == true
    val language       by vm.language.collectAsState()
    val pushEnabled    by vm.pushEnabled.collectAsState()
    val privateAccount    by vm.privateAccount.collectAsState()
    val messagePermission by vm.messagePermission.collectAsState()
    val blockedUsers      by vm.blockedUsers.collectAsState()
    val blockedLoading by vm.blockedLoading.collectAsState()

    val savedAccounts  by authVm.savedAccounts.collectAsState()
    val switchToGoogle by authVm.switchToGoogle.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

    var showPasswordDialog   by remember { mutableStateOf(false) }
    var showAccountsSheet    by remember { mutableStateOf(false) }

    // Google ile hesap geçişi için launcher
    val switchGoogleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)
                account.idToken?.let { authVm.signInWithGoogle(it) }
            } catch (_: ApiException) {}
        }
    }
    // FAZ: Admin Paneli/CMS ayrımı devamı — bu ekran `adminVm.perms`'i
    // "Admin" menü öğesini göstermek için okuyor, ama checkAdmin() hiç
    // tetiklenmiyordu. Sonuç: perms sonsuza kadar null kalıyor, isAdmin
    // hep false oluyor, süper admin dahil HERKES "Admin" menü öğesini
    // hiç göremiyordu — Admin Paneli'ne tek bağımsız giriş noktası burada.
    LaunchedEffect(Unit) { adminVm.checkAdmin() }

    LaunchedEffect(switchToGoogle) {
        if (switchToGoogle) {
            val client = authVm.getGoogleSignInClient(context)
            client.signOut().addOnCompleteListener {
                switchGoogleLauncher.launch(client.signInIntent)
            }
            authVm.clearSwitchToGoogle()
        }
    }
    var showEmailDialog    by remember { mutableStateOf(false) }
    var showBlockedDialog  by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.loadBlockedUsers() }

    Scaffold(
        containerColor = Background,
        topBar = {
            TopAppBar(
                title = { Text(Strings.settingsTitle(language), fontWeight = FontWeight.SemiBold, color = OnBackground) },
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
            contentPadding      = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {

            // ── Görünüm ──────────────────────────────────────────────────
            // ÇÖZÜLDÜ: Eskiden tek bir Switch ile sadece açık/koyu arası
            // geçiş yapılıyordu, telefonun sistem temasını takip eden bir
            // seçenek hiç yoktu. Artık 3 seçenekli bir buton grubu var:
            // Açık / Koyu / Sistemi Takip Et (varsayılan).
            item {
                SettingsSection(title = Strings.appearance(language)) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                when (themeMode) {
                                    "dark"  -> Icons.Filled.DarkMode
                                    "light" -> Icons.Outlined.LightMode
                                    else    -> Icons.Filled.BrightnessAuto
                                },
                                null, tint = Amber, modifier = Modifier.size(22.dp),
                            )
                            Spacer(Modifier.width(14.dp))
                            Text(
                                Strings.appearance(language),
                                color = OnBackground, fontWeight = FontWeight.Medium,
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier              = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            listOf(
                                "light"  to Strings.lightMode(language),
                                "dark"   to Strings.darkMode(language),
                                "system" to Strings.systemMode(language),
                            ).forEach { (mode, label) ->
                                val selected = themeMode == mode
                                FilterChip(
                                    selected = selected,
                                    onClick  = { vm.setThemeMode(mode) },
                                    label    = { Text(label, fontSize = 12.sp) },
                                    modifier = Modifier.weight(1f),
                                    colors   = FilterChipDefaults.filterChipColors(
                                        containerColor         = SurfaceVar,
                                        labelColor              = Muted,
                                        selectedContainerColor  = Amber.copy(alpha = 0.16f),
                                        selectedLabelColor      = Amber,
                                    ),
                                    border   = FilterChipDefaults.filterChipBorder(
                                        enabled = true, selected = selected,
                                        borderColor         = Divider,
                                        selectedBorderColor = Amber,
                                        borderWidth          = 1.dp,
                                        selectedBorderWidth  = 1.dp,
                                    ),
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = Divider, modifier = Modifier.padding(horizontal = 16.dp))

                    // Görsel tema seçimi
                    ThemeSelector(
                        selectedVariant  = themeVariant,
                        language         = language,
                        isDarkMode       = themeMode == "dark" || (themeMode == "system" && androidx.compose.foundation.isSystemInDarkTheme()),
                        onVariantChange  = { vm.setThemeVariant(it) },
                        onDarkModeChange = { /* dark/light toggle butonları yukarıda yönetiliyor */ },
                        modifier         = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    )

                    HorizontalDivider(color = Divider, modifier = Modifier.padding(horizontal = 16.dp))

                    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Translate, null, tint = Amber, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(14.dp))
                            Column {
                                Text(Strings.appLanguage(language), color = OnBackground, fontWeight = FontWeight.Medium)
                                Text(Strings.selectLang(language), color = Muted, fontSize = 12.sp)
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        // 4 dil — 2 satır x 2 sütun
                        val langList = listOf("tr" to "Türkçe", "ku" to "Kurmancî", "zza" to "Zazakî", "ckb" to "Soranî")
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            langList.chunked(2).forEach { row ->
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    row.forEach { (code, label) ->
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
                                    // Tek elemanlı satırda boşluk doldur
                                    if (row.size == 1) Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }

            // ── Hesap ────────────────────────────────────────────────────
            item {
                SettingsSection(title = Strings.account(language)) {
                    SettingsRow(
                        Icons.Outlined.Person,
                        Strings.editProfile(language),
                        Strings.settingsEditSub(language),
                    ) { navController.navigate("edit_profile") }
                    HorizontalDivider(color = Divider, modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsRow(
                        Icons.Outlined.Lock,
                        Strings.changePassword(language),
                        Strings.settingsPasswordSub(language),
                    ) { showPasswordDialog = true }
                    HorizontalDivider(color = Divider, modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsRow(
                        Icons.Outlined.Email,
                        Strings.changeEmail(language),
                        vm.currentEmail.ifBlank { Strings.settingsEmailAdd(language) },
                    ) { showEmailDialog = true }
                }
            }

            // ── Bildirimler ──────────────────────────────────────────────
            item {
                SettingsSection(title = Strings.navNotifs(language)) {
                    SettingsSwitchRow(
                        icon    = Icons.Outlined.Notifications,
                        label   = Strings.pushNotifs(language),
                        sub     = Strings.settingsPushSub(language),
                        checked = pushEnabled,
                        onCheck = { vm.togglePush() },
                    )
                }
            }

            // ── Gizlilik ─────────────────────────────────────────────────
            item {
                SettingsSection(title = Strings.privacy(language)) {
                    SettingsSwitchRow(
                        icon    = Icons.Outlined.Lock,
                        label   = Strings.privateAccount(language),
                        sub     = Strings.settingsPrivateSub(language),
                        checked = privateAccount,
                        onCheck = { vm.togglePrivate() },
                    )
                    HorizontalDivider(color = Divider, modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsRow(
                        Icons.Outlined.Block,
                        Strings.blockedUsers(language),
                        Strings.settingsBlockedSub(language),
                    ) { showBlockedDialog = true }
                    HorizontalDivider(color = Divider, modifier = Modifier.padding(horizontal = 16.dp))
                    // ── Mesaj İzni ───────────────────────────────────────
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text(
                            if (language == "ku") "Kî dikare peyamê bişîne?" else "Kim mesaj gönderebilir?",
                            color = OnBackground, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                        )
                        Spacer(Modifier.height(8.dp))
                        listOf(
                            "everyone"  to (if (language == "ku") "Hemû kes" else "Herkes"),
                            "followers" to (if (language == "ku") "Tenê şopînerên min" else "Sadece takipçilerim"),
                            "nobody"    to (if (language == "ku") "Tu kes" else "Hiç kimse"),
                        ).forEach { (key, label) ->
                            Row(
                                modifier = Modifier.fillMaxWidth()
                                    .clickable { vm.setMessagePermission(key) }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(label, color = OnBackground, fontSize = 13.sp)
                                RadioButton(
                                    selected = messagePermission == key,
                                    onClick  = { vm.setMessagePermission(key) },
                                    colors   = RadioButtonDefaults.colors(selectedColor = Primary),
                                )
                            }
                        }
                    }
                }
            }

            // ── Diğer ────────────────────────────────────────────────────
            item {
                val context = LocalContext.current
                SettingsSection(title = Strings.settingsOther(language)) {
                    // Gizlilik Seçenekleri — yalnızca GDPR/CCPA bölgelerinde göster
                    if (ConsentHelper.isPrivacyOptionsRequired(context)) {
                        SettingsRow(
                            Icons.Outlined.PrivacyTip,
                            if (language == "ku") "Bijartinên Nepeniyê" else "Gizlilik Seçenekleri",
                            if (language == "ku") "Reklam tercihlerini güncelle" else "Reklam tercihlerini güncelle",
                        ) {
                            context.findActivity()?.let { ConsentHelper.showPrivacyOptionsForm(it) }
                        }
                        HorizontalDivider(color = Divider, thickness = 0.5.dp)
                    }

                    SettingsRow(
                        Icons.Outlined.StarRate,
                        Strings.rateApp(language),
                        Strings.rateAppSub(language),
                    ) {
                        // Her zaman Play Store'a aç + in-app review bonus olarak dene
                        val activity = context.findActivity()
                        if (activity != null) {
                            com.heftreng.app.util.InAppReviewHelper.requestReviewNow(activity)
                        } else {
                            // Activity bulunamazsa direkt Play Store
                            val pkg = context.packageName
                            try {
                                context.startActivity(
                                    android.content.Intent(
                                        android.content.Intent.ACTION_VIEW,
                                        android.net.Uri.parse("market://details?id=$pkg")
                                    ).apply { flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK }
                                )
                            } catch (_: android.content.ActivityNotFoundException) {
                                context.startActivity(
                                    android.content.Intent(
                                        android.content.Intent.ACTION_VIEW,
                                        android.net.Uri.parse("https://play.google.com/store/apps/details?id=$pkg")
                                    ).apply { flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK }
                                )
                            }
                        }
                    }
                    HorizontalDivider(color = Divider, modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsRow(
                        icon  = Icons.Outlined.Share,
                        label = Strings.shareApp(language),
                        sub   = Strings.shareAppSub(language),
                    ) {
                        context.startActivity(
                            android.content.Intent.createChooser(
                                android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(android.content.Intent.EXTRA_TEXT, Strings.shareAppText(language))
                                },
                                Strings.shareAppChooser(language)
                            ).apply { flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK }
                        )
                    }
                    HorizontalDivider(color = Divider, modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsRow(
                        Icons.Outlined.Info,
                        Strings.settingsAbout(language),
                        Strings.settingsAboutSub(language),
                    ) { navController.navigate(Screen.CmsPage.go("hakkinda")) }
                    HorizontalDivider(color = Divider, modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsRow(
                        Icons.Outlined.Description,
                        Strings.termsOfUse(language),
                        Strings.settingsTermsSub(language),
                    ) { navController.navigate(Screen.CmsPage.go("kullanim-kosullari")) }
                    HorizontalDivider(color = Divider, modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsRow(
                        Icons.Outlined.Shield,
                        Strings.privacyPolicy(language),
                        Strings.settingsPrivacySub(language),
                    ) { navController.navigate(Screen.CmsPage.go("gizlilik-politikasi")) }
                }
            }

            // ── Admin ────────────────────────────────────────────────────
            if (isAdmin) {
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
                            Text(Strings.settingsAdminPanel(language), color = Error, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            Icon(Icons.Default.ChevronRight, null, tint = Error, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            // ── Hesap Ekle ──────────────────────────────────────────────
            item {
                SettingsSection {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { authVm.signOut() }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.PersonAdd, null, tint = Primary, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(14.dp))
                        Text(
                            if (language == "ku") "Hesabek din lê zêde bike" else "Hesap ekle",
                            color = OnBackground, fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }

            // ── Hesap Değiştir ───────────────────────────────────────────
            if (savedAccounts.size > 1) {
                item {
                    SettingsSection {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showAccountsSheet = true }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Default.SwitchAccount, null, tint = Primary, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    if (language == "ku") "Hesabê biguhere" else "Hesap Değiştir",
                                    color = OnBackground, fontWeight = FontWeight.Medium,
                                )
                                Text(
                                    "${savedAccounts.size} hesap kayıtlı",
                                    color = Muted, fontSize = 12.sp,
                                )
                            }
                            Icon(Icons.Default.ChevronRight, null, tint = Muted, modifier = Modifier.size(18.dp))
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
                        Text(Strings.logout(language), color = Color(0xFFEF4444), fontWeight = FontWeight.Medium)
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            // ── Hesabı Sil (Play Store Zorunluluğu) ───────────────────────
            item {
                var showDeleteDialog by remember { mutableStateOf(false) }
                var deleteError      by remember { mutableStateOf("") }
                var deleting         by remember { mutableStateOf(false) }

                if (showDeleteDialog) {
                    androidx.compose.material3.AlertDialog(
                        onDismissRequest = { if (!deleting) showDeleteDialog = false },
                        icon  = { Icon(Icons.Outlined.DeleteForever, null, tint = Color(0xFFEF4444), modifier = Modifier.size(32.dp)) },
                        title = { Text("Hesabı Kalıcı Olarak Sil", fontWeight = FontWeight.Bold, color = Color(0xFFEF4444)) },
                        text  = {
                            androidx.compose.foundation.layout.Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    "Hesabınız ve profil bilgileriniz kalıcı olarak silinecek. Bu işlem geri alınamaz.",
                                    color = OnBackground, fontSize = 14.sp,
                                )
                                Text(
                                    "Gönderileriniz platformda anonim olarak kalabilir.",
                                    color = Muted, fontSize = 12.sp,
                                )
                                if (deleteError.isNotBlank()) {
                                    Text(deleteError, color = Color(0xFFEF4444), fontSize = 12.sp)
                                }
                            }
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    deleting = true
                                    deleteError = ""
                                    authVm.deleteAccount(
                                        onSuccess = {
                                            showDeleteDialog = false
                                            navController.navigate("auth") {
                                                popUpTo(0) { inclusive = true }
                                            }
                                        },
                                        onError = { msg ->
                                            deleteError = msg
                                            deleting = false
                                        }
                                    )
                                },
                                enabled = !deleting,
                                colors  = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                            ) {
                                if (deleting) {
                                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                } else {
                                    Text("Hesabı Sil", color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                        },
                        dismissButton = {
                            OutlinedButton(onClick = { showDeleteDialog = false }, enabled = !deleting) {
                                Text("İptal")
                            }
                        },
                    )
                }

                SettingsSection {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showDeleteDialog = true }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.DeleteForever, null, tint = Color(0xFFEF4444).copy(alpha = 0.7f), modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(14.dp))
                        androidx.compose.foundation.layout.Column {
                            Text("Hesabı Kalıcı Olarak Sil", color = Color(0xFFEF4444).copy(alpha = 0.7f), fontWeight = FontWeight.Medium)
                            Text("Tüm verileriniz silinir, geri alınamaz", color = Muted, fontSize = 11.sp)
                        }
                    }
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }

    // ── Şifre Değiştir Dialog ────────────────────────────────────────────────
    // ── Hesap Değiştirme Sheet ───────────────────────────────────────────
    if (showAccountsSheet) {
        com.heftreng.app.navigation.InstagramAccountSwitcherDialog(
            accounts     = savedAccounts,
            currentEmail = authVm.currentEmail,
            language     = language,
            onSelect     = { account ->
                showAccountsSheet = false
                authVm.switchAccount(account, context)
            },
            onRemove     = { email -> authVm.removeAccount(email) },
            onAddAccount = {
                showAccountsSheet = false
                authVm.signOut()
            },
            onDismiss    = { showAccountsSheet = false },
        )
    }

    if (showPasswordDialog) {
        ChangePasswordDialog(
            onDismiss = { showPasswordDialog = false },
            onConfirm = { current, newPw ->
                vm.changePassword(
                    currentPassword = current,
                    newPassword     = newPw,
                    onSuccess       = { showPasswordDialog = false },
                    onError         = {},
                )
            },
            vm       = vm,
            language = language,
        )
    }

    // ── E-posta Değiştir Dialog ──────────────────────────────────────────────
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
            language     = language,
            blockedUsers = blockedUsers,
            loading      = blockedLoading,
            onUnblock    = { uid -> vm.unblockUser(uid) },
            onDismiss    = { showBlockedDialog = false },
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
        title = { Text(Strings.changePassword(language), color = OnBackground, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value         = currentPw,
                    onValueChange = { currentPw = it; error = null },
                    label         = { Text(Strings.currentPassword(language)) },
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
                    label         = { Text(Strings.newPassword(language)) },
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
                    label         = { Text(Strings.pwRepeat(language)) },
                    singleLine    = true,
                    modifier      = Modifier.fillMaxWidth(),
                    visualTransformation = PasswordVisualTransformation(),
                    isError       = newPwAgain.isNotBlank() && newPw != newPwAgain,
                    colors = settingsTextFieldColors(),
                )
                if (error != null) Text(error!!, color = Error, fontSize = 12.sp)
                if (newPwAgain.isNotBlank() && newPw != newPwAgain) {
                    Text(Strings.passwordMismatch(language), color = Error, fontSize = 12.sp)
                }
                var showForgot by remember { mutableStateOf(false) }
                TextButton(onClick = { showForgot = true }, contentPadding = PaddingValues(0.dp)) {
                    Text(
                        Strings.forgotPassPrompt(language),
                        color = Amber, fontSize = 12.sp,
                    )
                }
                if (showForgot) {
                    val authVm2: AuthViewModel = hiltViewModel()
                    ForgotPasswordFromSettings(
                        prefillEmail = authVm2.currentEmail,
                        onDismiss    = { showForgot = false },
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
                        currentPw.isBlank() -> error = Strings.errPwBlank(language)
                        newPw.length < 6    -> error = Strings.errPwShort(language)
                        newPw != newPwAgain -> error = Strings.passwordMismatch(language)
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
                else Text(Strings.save(language), color = Amber, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = { if (!loading) onDismiss() }) {
                Text(Strings.cancel(language), color = Muted)
            }
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
    var password by remember { mutableStateOf("") }
    var newEmail by remember { mutableStateOf("") }
    var error    by remember { mutableStateOf<String?>(null) }
    var loading  by remember { mutableStateOf(false) }
    var showPw   by remember { mutableStateOf(false) }
    var success  by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!loading) onDismiss() },
        containerColor   = HeftSurface,
        title = { Text(Strings.changeEmail(language), color = OnBackground, fontWeight = FontWeight.Bold) },
        text = {
            if (success) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF22C55E), modifier = Modifier.size(36.dp))
                    Text(
                        Strings.emailConfirmSent(language),
                        color = OnBackground, fontSize = 14.sp,
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("${Strings.currentLabel(language)}: $currentEmail", color = Muted, fontSize = 12.sp)
                    OutlinedTextField(
                        value         = newEmail,
                        onValueChange = { newEmail = it; error = null },
                        label         = { Text(Strings.newEmailLabel(language)) },
                        singleLine    = true,
                        modifier      = Modifier.fillMaxWidth(),
                        colors        = settingsTextFieldColors(),
                    )
                    OutlinedTextField(
                        value         = password,
                        onValueChange = { password = it; error = null },
                        label         = { Text(Strings.currentPassword(language)) },
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
                                error = Strings.errInvalidEmail(language)
                            password.isBlank() -> error = Strings.errEnterPw(language)
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
                    else Text(Strings.sendVerification(language), color = Amber, fontWeight = FontWeight.Bold)
                }
            } else {
                TextButton(onClick = onDismiss) {
                    Text(Strings.confirm(language), color = Amber, fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            if (!success) TextButton(onClick = { if (!loading) onDismiss() }) {
                Text(Strings.cancel(language), color = Muted)
            }
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
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = HeftSurface,
        title = {
            Text(
                Strings.blockedUsers(language),
                color = OnBackground, fontWeight = FontWeight.Bold,
            )
        },
        text = {
            when {
                loading -> Box(
                    Modifier.fillMaxWidth().padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator(color = Amber, modifier = Modifier.size(28.dp)) }
                blockedUsers.isEmpty() -> Text(
                    Strings.settingsNoBlocked(language),
                    color = Muted, fontSize = 14.sp,
                )
                else -> Column(
                    modifier            = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    blockedUsers.forEach { user ->
                        Row(
                            modifier          = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AsyncImage(
                                model              = user.photoURL.ifEmpty { null },
                                contentDescription = null,
                                modifier           = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(SurfaceVar),
                                contentScale       = ContentScale.Crop,
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                user.displayName.ifBlank { Strings.settingsAnonymous(language) },
                                color    = OnBackground,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = { onUnblock(user.uid) }) {
                                Text(
                                    Strings.unblock(language),
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
                Text(Strings.close(language), color = Amber, fontWeight = FontWeight.Bold)
            }
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
    var resetEmail by remember { mutableStateOf(prefillEmail) }
    var error      by remember { mutableStateOf<String?>(null) }
    var loading    by remember { mutableStateOf(false) }
    var success    by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!loading) onDismiss() },
        containerColor   = HeftSurface,
        title = { Text(Strings.forgotPass(language), color = OnBackground, fontWeight = FontWeight.Bold) },
        text = {
            if (success) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("✅", fontSize = 32.sp)
                    Text(
                        Strings.resetLinkSent(language),
                        color = OnBackground, fontSize = 14.sp,
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        Strings.resetLinkDesc(language),
                        color = Muted, fontSize = 13.sp,
                    )
                    OutlinedTextField(
                        value         = resetEmail,
                        onValueChange = { resetEmail = it; error = null },
                        label         = { Text(Strings.email(language)) },
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
                    else Text(Strings.send(language), color = Amber, fontWeight = FontWeight.Bold)
                }
            } else {
                TextButton(onClick = onDismiss) {
                    Text(Strings.confirm(language), color = Amber, fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            if (!success) TextButton(onClick = { if (!loading) onDismiss() }) {
                Text(Strings.cancel(language), color = Muted)
            }
        },
    )
}

// ─────────────────────────────────────────────────────────────────────────────
//  Hesap Değiştirme Bottom Sheet
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountSwitcherSheet(
    accounts    : List<AuthViewModel.SavedAccount>,
    currentEmail: String,
    language    : String,
    onSelect    : (AuthViewModel.SavedAccount) -> Unit,
    onRemove    : (String) -> Unit,
    onDismiss   : () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor   = HeftSurface,
        dragHandle       = { BottomSheetDefaults.DragHandle(color = Muted) },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp),
        ) {
            Text(
                if (language == "ku") "Hesabê hilbijêre" else "Hesap Seç",
                modifier       = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                fontWeight     = FontWeight.Bold,
                color          = OnBackground,
                fontSize       = 17.sp,
            )
            HorizontalDivider(color = Divider)

            accounts.forEach { account ->
                val isCurrent = account.email == currentEmail
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !isCurrent) { onSelect(account) }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Avatar
                    Box(
                        modifier         = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Primary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (account.photoURL.isNotBlank()) {
                            AsyncImage(
                                model              = account.photoURL,
                                contentDescription = account.displayName,
                                modifier           = Modifier.fillMaxSize(),
                                contentScale       = ContentScale.Crop,
                            )
                        } else {
                            Text(
                                account.displayName.firstOrNull()?.uppercase() ?: "?",
                                color      = Primary,
                                fontWeight = FontWeight.Bold,
                                fontSize   = 18.sp,
                            )
                        }
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                account.displayName.ifBlank { account.email },
                                color      = OnBackground,
                                fontWeight = FontWeight.SemiBold,
                                fontSize   = 15.sp,
                                maxLines   = 1,
                            )
                            if (isCurrent) {
                                Spacer(Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = Primary.copy(alpha = 0.15f),
                                ) {
                                    Text(
                                        "aktif",
                                        color    = Primary,
                                        fontSize = 10.sp,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    )
                                }
                            }
                        }
                        Text(
                            account.email,
                            color    = Muted,
                            fontSize = 12.sp,
                            maxLines = 1,
                        )
                    }
                    if (!isCurrent) {
                        IconButton(
                            onClick  = { onRemove(account.email) },
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Kaldır",
                                tint     = Muted,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
                HorizontalDivider(color = Divider, modifier = Modifier.padding(horizontal = 16.dp))
            }
        }
    }
}
