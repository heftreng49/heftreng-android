package com.heftreng.app.ui.screens.auth

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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

@Composable
fun AuthScreen(
    onAuthSuccess: () -> Unit,
    vm: AuthViewModel = hiltViewModel(),
) {
    val context     = LocalContext.current
    val currentUser by vm.currentUser.collectAsState()
    val loading     by vm.loading.collectAsState()
    val error       by vm.error.collectAsState()

    var isRegister  by remember { mutableStateOf(false) }
    var email       by remember { mutableStateOf("") }
    var password    by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var showPw      by remember { mutableStateOf(false) }

    LaunchedEffect(currentUser) { if (currentUser != null) onAuthSuccess() }

    val googleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            try {
                GoogleSignIn.getSignedInAccountFromIntent(result.data)
                    .getResult(ApiException::class.java).idToken
                    ?.let { vm.signInWithGoogle(it) }
            } catch (_: ApiException) {}
        }
    }

    Box(
        modifier         = Modifier.fillMaxSize().background(bg()),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier            = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Logo
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text          = "heftreng",
                    fontSize      = 42.sp,
                    fontWeight    = FontWeight.Bold,
                    color         = accent(),
                    letterSpacing = (-1).sp,
                )
                Text(
                    text     = if (isRegister) "Hesabek nû çêke" else "Xêr hatî",
                    fontSize = 14.sp,
                    color    = muted(),
                )
            }

            Spacer(Modifier.height(8.dp))

            // Google giriş
            OutlinedButton(
                onClick = {
                    val client = vm.getGoogleSignInClient(context)
                    googleLauncher.launch(client.signInIntent)
                },
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(12.dp),
                colors   = ButtonDefaults.outlinedButtonColors(contentColor = onBg()),
                border   = androidx.compose.foundation.BorderStroke(1.dp, divider()),
            ) {
                Text("Google re berdewam bike", modifier = Modifier.padding(vertical = 4.dp))
            }

            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                HorizontalDivider(modifier = Modifier.weight(1f), color = divider())
                Text("  an  ", color = muted(), fontSize = 12.sp)
                HorizontalDivider(modifier = Modifier.weight(1f), color = divider())
            }

            AnimatedVisibility(visible = isRegister) {
                OutlinedTextField(
                    value = displayName, onValueChange = { displayName = it },
                    label = { Text("Navê xwe / Adın") },
                    modifier = Modifier.fillMaxWidth(),
                    shape    = RoundedCornerShape(12.dp),
                    colors   = heftrangFieldColors(),
                    singleLine = true,
                )
            }

            OutlinedTextField(
                value = email, onValueChange = { email = it },
                label = { Text("E-posta") },
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(12.dp),
                colors   = heftrangFieldColors(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                singleLine = true,
            )

            OutlinedTextField(
                value = password, onValueChange = { password = it },
                label = { Text("Şîfre / Parola") },
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(12.dp),
                colors   = heftrangFieldColors(),
                visualTransformation = if (showPw) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showPw = !showPw }) {
                        Icon(
                            if (showPw) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            null, tint = muted(),
                        )
                    }
                },
                singleLine = true,
            )

            error?.let {
                Text(it, color = Error, fontSize = 13.sp)
                LaunchedEffect(it) { vm.clearError() }
            }

            Button(
                onClick = {
                    if (isRegister) vm.registerWithEmail(email, password, displayName)
                    else vm.signInWithEmail(email, password)
                },
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(12.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = accent(), contentColor = Color.Black),
                enabled  = !loading,
            ) {
                if (loading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.Black, strokeWidth = 2.dp)
                } else {
                    Text(
                        if (isRegister) "Tomar bibe" else "Têkeve",
                        fontWeight = FontWeight.SemiBold,
                        modifier   = Modifier.padding(vertical = 4.dp),
                    )
                }
            }

            TextButton(onClick = { isRegister = !isRegister }) {
                Text(
                    if (isRegister) "Hesabek te heye? Têkeve" else "Hesabek te tune? Tomar bibe",
                    color    = accent(),
                    fontSize = 13.sp,
                )
            }
        }
    }
}

// Paylaşılan TextField renkleri — tüm formlarda kullanılır
@Composable
fun heftrangFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor      = accent(),
    unfocusedBorderColor    = divider(),
    focusedLabelColor       = accent(),
    unfocusedLabelColor     = muted(),
    cursorColor             = accent(),
    focusedTextColor        = onBg(),
    unfocusedTextColor      = onBg(),
    unfocusedContainerColor = surf(),
    focusedContainerColor   = surf(),
)
