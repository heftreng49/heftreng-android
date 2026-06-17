package com.heftreng.app

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import com.heftreng.app.util.AppLifecycleObserver
import com.heftreng.app.navigation.HeftrangNavHost
import com.heftreng.app.ui.theme.HeftrangTheme
import com.heftreng.app.viewmodel.AuthViewModel
import com.heftreng.app.viewmodel.SettingsViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    // Bildirimden gelen deep link hedefi
    private var pendingNavTarget: String? = null

    // SettingsViewModel — darkMode tercihi için
    private val settingsVm: SettingsViewModel by viewModels()
    private val authVm: AuthViewModel by viewModels()
    private val adsVm: com.heftreng.app.viewmodel.AdsViewModel by viewModels()

    // ScreenTracker — Hilt singleton, lifecycle callbacks için kayıt edilecek
    @javax.inject.Inject lateinit var screenTracker: com.heftreng.app.ads.ScreenTracker

    // ── In-App Update ──────────────────────────────────────────────────────────
    private val appUpdateManager by lazy { AppUpdateManagerFactory.create(this) }

    private val installStateListener = InstallStateUpdatedListener { state ->
        if (state.installStatus() == InstallStatus.DOWNLOADED) {
            // İndirme tamamlandı — hemen uygula
            appUpdateManager.completeUpdate()
        }
    }

    private val updateResultLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result: ActivityResult ->
        if (result.resultCode == Activity.RESULT_CANCELED) {
            // Kullanıcı güncellemeyi erteledi — sorun değil
            Log.d("InAppUpdate", "Güncelleme ertelendi")
        }
    }

    // POST_NOTIFICATIONS izin launcher (Android 13+)
    private val notifPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) initFcm()
        // İzin reddedilse bile token'ı senkronize et (sessiz push çalışır)
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid != null) authVm.syncFcmTokenWithContext(this, uid)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Foreground/background durumunu tüm ViewModel'lar için tek noktadan takip et
        AppLifecycleObserver.register()

        // EdgeToEdge modunu aktifleştiriyoruz — sistem çubuklarını şeffaf yapar
        enableEdgeToEdge()
        // NOT: WindowCompat.setDecorFitsSystemWindows kaldırıldı,
        // enableEdgeToEdge() zaten bunu yapıyor (Android 15 uyumlu)

        // AdMob SDK başlatma — reklam yüklenmeden önce mutlaka çağrılmalı
        com.google.android.gms.ads.MobileAds.initialize(this) { initStatus ->
            android.util.Log.d("AdMob", "SDK hazır: ${initStatus.adapterStatusMap}")
        }

        // ScreenTracker — activity lifecycle dinleyicisi + adsVm bağla
        application.registerActivityLifecycleCallbacks(screenTracker)
        screenTracker.bind(adsVm, this)

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

        // Güncelleme kontrolü
        checkForUpdate()
    }

    // ── Güncelleme kontrolü ────────────────────────────────────────────────────
    private fun checkForUpdate() {
        try {
            appUpdateManager.registerListener(installStateListener)
            appUpdateManager.appUpdateInfo.addOnSuccessListener { info ->
                try {
                    when {
                        info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE
                        && info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE) -> {
                            appUpdateManager.startUpdateFlowForResult(
                                info,
                                updateResultLauncher,
                                AppUpdateOptions.newBuilder(AppUpdateType.FLEXIBLE).build(),
                            )
                        }
                        info.installStatus() == InstallStatus.DOWNLOADED -> {
                            appUpdateManager.completeUpdate()
                        }
                    }
                } catch (e: Exception) {
                    // FLEXIBLE update bazı cihaz/emülatörlerde desteklenmiyor
                    Log.w("InAppUpdate", "Update başlatılamadı: ${e.message}")
                }
            }.addOnFailureListener {
                Log.d("InAppUpdate", "Güncelleme kontrolü başarısız: ${it.message}")
            }
        } catch (e: Exception) {
            Log.w("InAppUpdate", "checkForUpdate hata: ${e.message}")
        }
    }

    override fun onResume() {
        super.onResume()
        // Uygulama arka plandan dönünce tekrar kontrol et
        appUpdateManager.appUpdateInfo.addOnSuccessListener { info ->
            if (info.installStatus() == InstallStatus.DOWNLOADED) {
                appUpdateManager.completeUpdate()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        appUpdateManager.unregisterListener(installStateListener)
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
