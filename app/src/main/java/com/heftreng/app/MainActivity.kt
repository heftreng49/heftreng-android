package com.heftreng.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import com.heftreng.app.navigation.HeftrangNavHost
import com.heftreng.app.ui.theme.HeftrangTheme
import com.heftreng.app.viewmodel.SettingsViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    // Bildirimden gelen deep link hedefi
    private var pendingNavTarget: String? = null

    // SettingsViewModel — darkMode tercihi için
    private val settingsVm: SettingsViewModel by viewModels()

    // POST_NOTIFICATIONS izin launcher (Android 13+)
    private val notifPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) initFcm()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Bildirimden gelen intent'i al
        pendingNavTarget = intent?.getStringExtra("navigate_to")

        setContent {
            // SettingsViewModel'dan reactive dark/light modu oku
            val isDark by settingsVm.darkMode.collectAsState()

            HeftrangTheme(darkMode = isDark) {
                HeftrangNavHost(initialRoute = pendingNavTarget)
            }
        }

        // Bildirim izni iste
        requestNotificationPermission()
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        // Uygulama açıkken gelen bildirim tıklaması
        intent.getStringExtra("navigate_to")?.let { target ->
            pendingNavTarget = target
        }
    }

    // ── Bildirim izni ─────────────────────────────────────────────────────────
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> {
                    initFcm()
                }
                else -> {
                    notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        } else {
            // Android 12 ve altı — izin gerekmez
            initFcm()
        }
    }

    // ── FCM Token al ve Firestore'a kaydet ────────────────────────────────────
    private fun initFcm() {
        FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
            val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return@addOnSuccessListener
            FirebaseFirestore.getInstance()
                .collection("users")
                .document(uid)
                .update(mapOf(
                    "fcmToken"     to token,
                    "fcmUpdatedAt" to FieldValue.serverTimestamp(),
                ))
        }

        // Token yenileme aboneliği
        FirebaseMessaging.getInstance().isAutoInitEnabled = true
    }
}
