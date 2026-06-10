package com.heftreng.app.ui.screens.auth

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.ui.res.painterResource
import com.heftreng.app.R
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import com.heftreng.app.ui.i18n.Strings
import com.heftreng.app.ui.theme.*
import com.heftreng.app.viewmodel.AuthViewModel
import com.heftreng.app.viewmodel.SettingsViewModel
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi

@Composable
fun AuthScreen(
    onAuthSuccess: () -> Unit,
    vm           : AuthViewModel     = hiltViewModel(),
    settingsVm   : SettingsViewModel = hiltViewModel(),
) {
    val context     = LocalContext.current
    val currentUser      by vm.currentUser.collectAsState()
    val verificationSent by vm.verificationSent.collectAsState()
    val loading     by vm.loading.collectAsState()
    val error       by vm.error.collectAsState()
    val language    by settingsVm.language.collectAsState()
    val ku = language == "ku"

    var isRegister      by remember { mutableStateOf(false) }
    var email           by remember { mutableStateOf("") }
    var password        by remember { mutableStateOf("") }
    var displayName     by remember { mutableStateOf("") }
    var showPassword    by remember { mutableStateOf(false) }
    var showForgotDialog by remember { mutableStateOf(false) }
    var termsAccepted    by remember { mutableStateOf(false) }

    LaunchedEffect(currentUser) {
        if (currentUser != null) onAuthSuccess()
    }

    val googleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)
                account.idToken?.let { vm.signInWithGoogle(it) }
            } catch (_: ApiException) {}
        }
    }

    // ── Email Doğrulama Bekleme Ekranı ─────────────────────────────────────
    if (verificationSent) {
        var notYetError by remember { mutableStateOf(false) }
        Box(Modifier.fillMaxSize().background(Background), contentAlignment = Alignment.Center) {
            Column(
                modifier            = Modifier.fillMaxWidth().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text("📧", fontSize = 56.sp)
                Text("Email Doğrulama", color = OnBackground,
                    fontWeight = FontWeight.Bold, fontSize = 22.sp)
                Text(
                    "Email adresinize bir doğrulama linki gönderdik. Lütfen gelen kutunuzu kontrol edin ve linke tıklayın.",
                    color = Muted, fontSize = 14.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    lineHeight = 22.sp,
                )
                // Spam uyarısı
                Surface(
                    shape  = RoundedCornerShape(10.dp),
                    color  = Amber.copy(alpha = 0.12f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text("📁", fontSize = 20.sp)
                        Text(
                            if (ku)
                                "Ger name hat, qutiya spamê jî kontrol bike."
                            else
                                "E-posta gelmedi mi? Spam / Önemsiz klasörünüzü de kontrol edin.",
                            color    = Amber,
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                        )
                    }
                }
                if (notYetError) {
                    Text("Henüz doğrulanmamış. Lütfen email'inizdeki linke tıklayın.",
                        color = Color(0xFFEF4444), fontSize = 13.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
                Button(
                    onClick = {
                        notYetError = false
                        vm.reloadAndCheckVerification(
                            onVerified = { vm.clearVerificationSent() },
                            onNotYet   = { notYetError = true },
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors   = ButtonDefaults.buttonColors(containerColor = Amber),
                ) {
                    if (loading)
                        CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                    else
                        Text("✅ Email'i Doğruladım", color = Color.White, fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick  = { vm.resendVerificationEmail() },
                    modifier = Modifier.fillMaxWidth(),
                    border   = androidx.compose.foundation.BorderStroke(1.dp, Amber.copy(alpha = 0.5f)),
                ) { Text("Tekrar Gönder", color = Amber) }
                TextButton(onClick = { vm.clearVerificationSent(); vm.signOut() }) {
                    Text("Geri Dön", color = Muted)
                }
            }
        }
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .imePadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Logo
            Text(
                text         = "heftreng",
                fontSize     = 40.sp,
                fontWeight   = FontWeight.Bold,
                color        = Amber,
                letterSpacing = (-1).sp,
            )
            Text(
                text     = if (isRegister)
                    Strings.register(language)
                else
                    Strings.welcome(language),
                fontSize = 14.sp,
                color    = Muted,
            )

            Spacer(Modifier.height(8.dp))

            // Dil seçimi — giriş ekranında da görünür
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("tr" to "Türkçe", "ku" to "Kurdî").forEach { (code, label) ->
                    val selected = language == code
                    OutlinedButton(
                        onClick  = { settingsVm.setLanguage(code) },
                        modifier = Modifier.weight(1f),
                        shape    = RoundedCornerShape(10.dp),
                        colors   = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (selected) Amber else Color.Transparent,
                            contentColor   = if (selected) Color.Black else Muted,
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (selected) Amber else Divider,
                        ),
                    ) { Text(label, fontSize = 13.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal) }
                }
            }

            // ── Google ile Giriş — Önerilen Yöntem ───────────────────────────
            // Önerilen rozeti
            Row(
                modifier              = Modifier.fillMaxWidth(),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFF22C55E).copy(alpha = 0.15f),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text("✓", color = Color(0xFF22C55E), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text(
                            Strings.googleRecommended(language),
                            color = Color(0xFF22C55E),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }

            // Google butonu — logo + metin
            Button(
                onClick = {
                    val client = vm.getGoogleSignInClient(context)
                    googleLauncher.launch(client.signInIntent)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape  = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor   = Color(0xFF1F1F1F),
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
            ) {
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        painter            = painterResource(R.drawable.ic_google),
                        contentDescription = "Google",
                        modifier           = Modifier.size(22.dp),
                        tint               = Color.Unspecified,
                    )
                    Text(
                        Strings.continueWithGoogle(language),
                        fontWeight = FontWeight.SemiBold,
                        fontSize   = 15.sp,
                        color      = Color(0xFF1F1F1F),
                    )
                }
            }

            // E-posta ile devam etmek hakkında uyarı
            Surface(
                shape    = RoundedCornerShape(10.dp),
                color    = Amber.copy(alpha = 0.10f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("⚠️", fontSize = 16.sp)
                    Text(
                        Strings.googleWarning(language),
                        color      = Amber,
                        fontSize   = 12.sp,
                        lineHeight = 18.sp,
                    )
                }
            }

            Row(
                modifier          = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HorizontalDivider(modifier = Modifier.weight(1f), color = Divider)
                Text(
                    "  ${Strings.orDivider(language)}  ",
                    color = Muted, fontSize = 12.sp,
                )
                HorizontalDivider(modifier = Modifier.weight(1f), color = Divider)
            }

            // Ad alanı (sadece kayıt modunda)
            AnimatedVisibility(visible = isRegister) {
                OutlinedTextField(
                    value         = displayName,
                    onValueChange = { displayName = it },
                    label         = { Text(Strings.yourName(language)) },
                    modifier      = Modifier.fillMaxWidth(),
                    shape         = RoundedCornerShape(12.dp),
                    colors        = heftrangTextFieldColors(),
                    singleLine    = true,
                )
            }

            OutlinedTextField(
                value           = email,
                onValueChange   = { email = it },
                label           = { Text("E-posta") },
                modifier        = Modifier.fillMaxWidth(),
                shape           = RoundedCornerShape(12.dp),
                colors          = heftrangTextFieldColors(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                singleLine      = true,
            )

            OutlinedTextField(
                value         = password,
                onValueChange = { password = it },
                label         = { Text(Strings.password(language)) },
                modifier      = Modifier.fillMaxWidth(),
                shape         = RoundedCornerShape(12.dp),
                colors        = heftrangTextFieldColors(),
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon  = {
                    IconButton(onClick = { showPassword = !showPassword }) {
                        Icon(
                            if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = null,
                            tint = Muted,
                        )
                    }
                },
                singleLine = true,
            )

            // Şifremi unuttum
            if (!isRegister) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                    TextButton(
                        onClick        = { showForgotDialog = true },
                        contentPadding = PaddingValues(0.dp),
                    ) {
                        Text(
                            Strings.forgotPass(language),
                            color = Amber, fontSize = 12.sp,
                        )
                    }
                }
            }

            error?.let { errCode ->
                if (errCode == "EMAIL_NOT_VERIFIED") {
                    Surface(
                        shape  = RoundedCornerShape(12.dp),
                        color  = MaterialTheme.colorScheme.error.copy(alpha = 0.10f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                Strings.emailNotVerifiedTitle(language),
                                color      = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Bold,
                                fontSize   = 13.sp,
                            )
                            Text(
                                Strings.emailNotVerifiedBody(language),
                                color      = MaterialTheme.colorScheme.error,
                                fontSize   = 12.sp,
                                lineHeight = 17.sp,
                            )
                            HorizontalDivider(
                                color     = MaterialTheme.colorScheme.error.copy(alpha = 0.20f),
                                thickness = 0.5.dp,
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Icon(
                                    painter            = painterResource(R.drawable.ic_google),
                                    contentDescription = null,
                                    modifier           = Modifier.size(14.dp),
                                    tint               = Color.Unspecified,
                                )
                                Text(
                                    Strings.emailNotVerifiedGoogle(language),
                                    color      = Muted,
                                    fontSize   = 11.sp,
                                    lineHeight = 16.sp,
                                )
                            }
                        }
                    }
                } else {
                    Text(errCode, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                }
                LaunchedEffect(errCode) { vm.clearError() }
            }

            Button(
                onClick = {
                    if (isRegister) vm.registerWithEmail(email, password, displayName)
                    else vm.signInWithEmail(email, password)
                },
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(12.dp),
                colors   = ButtonDefaults.buttonColors(
                    containerColor = if (isRegister && !termsAccepted) Muted else Amber,
                    contentColor   = Color.Black,
                ),
                enabled = !loading && (!isRegister || termsAccepted),
            ) {
                if (loading) {
                    CircularProgressIndicator(
                        modifier    = Modifier.size(20.dp),
                        color       = Color.Black,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(
                        if (isRegister) Strings.register(language) else Strings.login(language),
                        fontWeight = FontWeight.SemiBold,
                        modifier   = Modifier.padding(vertical = 4.dp),
                    )
                }
            }

            // ── Kullanım Koşulları & Gizlilik Politikası ─────────────────────────
            val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
            val termsUrl   = "https://heft-reng.blogspot.com/p/kullanim-kosullari.html"
            val privacyUrl = "https://heft-reng.blogspot.com/p/gizlilik-politikasi.html"

            // Kayıt modunda: onay checkbox'ı (zorunlu)
            // Giriş modunda: bilgilendirici metin (pasif)
            if (isRegister) {
                Row(
                    modifier          = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Checkbox(
                        checked         = termsAccepted,
                        onCheckedChange = { termsAccepted = it },
                        colors          = CheckboxDefaults.colors(
                            checkedColor        = Primary,
                            uncheckedColor      = Muted,
                            checkmarkColor      = androidx.compose.ui.graphics.Color.White,
                        ),
                        modifier = Modifier.size(20.dp),
                    )
                    @OptIn(ExperimentalLayoutApi::class)
                    androidx.compose.foundation.layout.FlowRow {
                        Text(
                            if (ku) "Ez " else "Okudum, ",
                            color = Muted, fontSize = 11.sp,
                        )
                        Text(
                            if (ku) "Mercên Bikaranînê" else "Kullanım Koşullarını",
                            color    = Primary,
                            fontSize = 11.sp,
                            modifier = Modifier.clickable { uriHandler.openUri(termsUrl) },
                        )
                        Text(if (ku) " û " else " ve ", color = Muted, fontSize = 11.sp)
                        Text(
                            if (ku) "Siyaseta Nepeniyê" else "Gizlilik Politikasını",
                            color    = Primary,
                            fontSize = 11.sp,
                            modifier = Modifier.clickable { uriHandler.openUri(privacyUrl) },
                        )
                        Text(
                            if (ku) " xwendim û qebûl dikim." else " kabul ediyorum.",
                            color = Muted, fontSize = 11.sp,
                        )
                    }
                }
            } else {
                // Giriş modunda bilgilendirici metin
                @OptIn(ExperimentalLayoutApi::class)
                androidx.compose.foundation.layout.FlowRow(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text(if (ku) "Bi têketinê " else "Giriş yaparak ", color = Muted, fontSize = 11.sp)
                    Text(
                        if (ku) "Mercên Bikaranînê" else "Kullanım Koşullarını",
                        color    = Primary, fontSize = 11.sp,
                        modifier = Modifier.clickable { uriHandler.openUri(termsUrl) },
                    )
                    Text(if (ku) " û " else " ve ", color = Muted, fontSize = 11.sp)
                    Text(
                        if (ku) "Siyaseta Nepeniyê" else "Gizlilik Politikasını",
                        color    = Primary, fontSize = 11.sp,
                        modifier = Modifier.clickable { uriHandler.openUri(privacyUrl) },
                    )
                    Text(
                        if (ku) " qebûl dikî." else " kabul etmiş olursunuz.",
                        color = Muted, fontSize = 11.sp,
                    )
                }
            }

            TextButton(onClick = { isRegister = !isRegister; termsAccepted = false }) {
                Text(
                    if (isRegister)
                        Strings.hasAccount(language)
                    else
                        Strings.noAccount(language),
                    color    = Amber,
                    fontSize = 13.sp,
                )
            }
        }
    }

    if (showForgotDialog) {
        ForgotPasswordDialog(
            prefillEmail = email,
            onDismiss    = { showForgotDialog = false },
            vm           = vm,
            ku           = ku,
            language     = language,
        )
    }
}

@Composable
private fun ForgotPasswordDialog(
    prefillEmail: String,
    onDismiss   : () -> Unit,
    vm          : AuthViewModel,
    ku          : Boolean = false,
    language    : String  = "tr",
) {
    var resetEmail by remember { mutableStateOf(prefillEmail) }
    var error      by remember { mutableStateOf<String?>(null) }
    var loading    by remember { mutableStateOf(false) }
    var success    by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!loading) onDismiss() },
        containerColor   = HeftSurface,
        title = {
            Text(
                Strings.forgotPass(language),
                color      = OnBackground,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            if (success) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("✅", fontSize = 32.sp)
                    Text(
                        Strings.resetLinkSent(language),
                        color    = OnBackground,
                        fontSize = 14.sp,
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        Strings.resetLinkDesc(language),
                        color    = Muted,
                        fontSize = 13.sp,
                    )
                    OutlinedTextField(
                        value         = resetEmail,
                        onValueChange = { resetEmail = it; error = null },
                        label         = { Text("E-posta") },
                        singleLine    = true,
                        modifier      = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor      = Amber,
                            unfocusedBorderColor    = Divider,
                            focusedTextColor        = OnBackground,
                            unfocusedTextColor      = OnBackground,
                            unfocusedContainerColor = SurfaceVar,
                            focusedContainerColor   = SurfaceVar,
                            focusedLabelColor       = Amber,
                            unfocusedLabelColor     = Muted,
                            cursorColor             = Amber,
                        ),
                    )
                    if (error != null) {
                        Text(error!!, color = Error, fontSize = 12.sp)
                    }
                }
            }
        },
        confirmButton = {
            if (!success) {
                TextButton(
                    onClick = {
                        loading = true
                        vm.sendPasswordReset(
                            email     = resetEmail,
                            onSuccess = { loading = false; success = true },
                            onError   = { msg -> loading = false; error = msg },
                        )
                    },
                    enabled = !loading,
                ) {
                    if (loading) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Amber, strokeWidth = 2.dp)
                    } else {
                        Text(Strings.send(language), color = Amber, fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                TextButton(onClick = onDismiss) {
                    Text(Strings.confirm(language), color = Amber, fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            if (!success) {
                TextButton(onClick = { if (!loading) onDismiss() }) {
                    Text(Strings.cancel(language), color = Muted)
                }
            }
        },
    )
}

@Composable
fun heftrangTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor      = Amber,
    unfocusedBorderColor    = Divider,
    focusedLabelColor       = Amber,
    unfocusedLabelColor     = Muted,
    cursorColor             = Amber,
    focusedTextColor        = OnBackground,
    unfocusedTextColor      = OnBackground,
    unfocusedContainerColor = HeftSurface,
    focusedContainerColor   = HeftSurface,
)
