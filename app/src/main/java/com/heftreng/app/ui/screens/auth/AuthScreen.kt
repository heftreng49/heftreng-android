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
import com.heftreng.app.ui.theme.*
import com.heftreng.app.viewmodel.AuthViewModel
import com.heftreng.app.viewmodel.SettingsViewModel

@Composable
fun AuthScreen(
    onAuthSuccess: () -> Unit,
    vm           : AuthViewModel     = hiltViewModel(),
    settingsVm   : SettingsViewModel = hiltViewModel(),
) {
    val context     = LocalContext.current
    val currentUser by vm.currentUser.collectAsState()
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
                    (if (ku) "Hesabek nû çêke" else "Hesap oluştur")
                else
                    (if (ku) "Xêr hatî" else "Hoş geldin"),
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

            // Google ile giriş
            OutlinedButton(
                onClick = {
                    val client = vm.getGoogleSignInClient(context)
                    googleLauncher.launch(client.signInIntent)
                },
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(12.dp),
                colors   = ButtonDefaults.outlinedButtonColors(contentColor = OnBackground),
                border   = androidx.compose.foundation.BorderStroke(1.dp, Divider),
            ) {
                Text(
                    if (ku) "Bi Google re berdewam bike" else "Google ile devam et",
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }

            Row(
                modifier          = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HorizontalDivider(modifier = Modifier.weight(1f), color = Divider)
                Text(if (ku) "  an jî  " else "  ya da  ", color = Muted, fontSize = 12.sp)
                HorizontalDivider(modifier = Modifier.weight(1f), color = Divider)
            }

            // Ad alanı (sadece kayıt modunda)
            AnimatedVisibility(visible = isRegister) {
                OutlinedTextField(
                    value         = displayName,
                    onValueChange = { displayName = it },
                    label         = { Text(if (ku) "Navê te" else "Adın") },
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
                label         = { Text(if (ku) "Şîfre" else "Şifre") },
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
                            if (ku) "Şîfreya xwe ji bîr kir" else "Şifremi unuttum",
                            color = Amber, fontSize = 12.sp,
                        )
                    }
                }
            }

            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                LaunchedEffect(it) { vm.clearError() }
            }

            Button(
                onClick = {
                    if (isRegister) vm.registerWithEmail(email, password, displayName)
                    else vm.signInWithEmail(email, password)
                },
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(12.dp),
                colors   = ButtonDefaults.buttonColors(
                    containerColor = Amber,
                    contentColor   = Color.Black,
                ),
                enabled = !loading,
            ) {
                if (loading) {
                    CircularProgressIndicator(
                        modifier    = Modifier.size(20.dp),
                        color       = Color.Black,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(
                        if (isRegister) (if (ku) "Qeyd bibe" else "Kayıt ol")
                        else (if (ku) "Têkeve" else "Giriş yap"),
                        fontWeight = FontWeight.SemiBold,
                        modifier   = Modifier.padding(vertical = 4.dp),
                    )
                }
            }

            TextButton(onClick = { isRegister = !isRegister }) {
                Text(
                    if (isRegister)
                        (if (ku) "Hesabê te heye? Têkeve" else "Zaten hesabın var mı? Giriş yap")
                    else
                        (if (ku) "Hesabê te tune? Qeyd bibe" else "Hesabın yok mu? Kayıt ol"),
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
        )
    }
}

@Composable
private fun ForgotPasswordDialog(
    prefillEmail: String,
    onDismiss   : () -> Unit,
    vm          : AuthViewModel,
    ku          : Boolean = false,
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
                if (ku) "Şîreya Xwe Ji Bîr Kir" else "Şifremi Unuttum",
                color      = OnBackground,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            if (success) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("✅", fontSize = 32.sp)
                    Text(
                        if (ku) "Lînka sifirkirina şîfreyê hate şandin. E-postaya xwe kontrol bike."
                        else "Şifre sıfırlama bağlantısı gönderildi. E-posta kutunuzu kontrol edin.",
                        color    = OnBackground,
                        fontSize = 14.sp,
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        if (ku) "E-postaya qeydkirî binivîse, em lînka sifirkirinê bişînin."
                        else "Kayıtlı e-posta adresinizi girin, şifre sıfırlama bağlantısı göndereceğiz.",
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
                        Text(if (ku) "Bişîne" else "Gönder", color = Amber, fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                TextButton(onClick = onDismiss) {
                    Text(if (ku) "Temam" else "Tamam", color = Amber, fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            if (!success) {
                TextButton(onClick = { if (!loading) onDismiss() }) {
                    Text(if (ku) "Betal bike" else "İptal", color = Muted)
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
