package com.heftreng.app

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
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
import com.heftreng.app.util.ConsentHelper
import com.google.android.gms.ads.MobileAds
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

        val gmsAvailable = com.heftreng.app.HeftrangApp.isGmsAvailable.value

        // ── UMP Onay Akışı ────────────────────────────────────────────────
        // GDPR/CCPA bölgelerinde kullanıcıya onay formu gösterilir.
        // Türkiye gibi dışarıdaki bölgelerde form çıkmaz, hemen devam edilir.
        // MobileAds.initialize() UMP onayından SONRA çağrılmalı (Google zorunluluğu).
        //
        // GMS'siz cihazda (Huawei vb.) hem UMP hem MobileAds Google Play
        // Services'e dayanır — bu adımı tamamen atlıyoruz. sdkReady hiç
        // true olmaz, bu da AdsViewModel'in reklam yüklemeyi hiç denememesini
        // sağlar (zaten unitId/config çağrıları network'e gitmeden sessizce
        // no-op kalır çünkü sdkReady beklenen ilk adım).
        if (gmsAvailable) {
            ConsentHelper.initialize(
                activity           = this,
                debugMode          = false,      // prod'da false — test için true yap + testDeviceHashedId ekle
                testDeviceHashedId = null,
                onCanRequestAds    = {
                    // UMP tamamlandı → AdMob SDK'yı (yeniden) başlat
                    // Native Ad Validator: debug build'de test device listesi BOŞ bırakılırsa
                    // validator dialog gösterilmez. Emülatör otomatik test cihazı sayılır
                    // ama fiziksel cihazı test listesine eklememişsek dialog çıkmaz.
                    MobileAds.initialize(this) { initStatus ->
                        com.heftreng.app.HeftrangApp.notifySdkReady()
                        android.util.Log.d("AdMob", "SDK hazır (UMP sonrası)")
                    }
                },
            )
        } else {
            Log.w("MainActivity", "GMS yok — UMP/AdMob atlandı, uygulama reklamsız/push'suz modda açılıyor")
        }
        
        // Foreground/background durumunu tüm ViewModel'lar için tek noktadan takip et
        AppLifecycleObserver.register()

        // ÇÖZÜLDÜ (crash raporu): enableEdgeToEdge() bazı cihaz/OEM/Android
        // sürümü kombinasyonlarında decorView'in child view hiyerarşisi henüz
        // tam kurulmamışken çağrılırsa AndroidX içinde
        // "ViewGroup.getChildAt(int) on a null object reference" ile
        // RASTGELE (cihaza/soğuk-sıcak başlatmaya göre değişen) bir çökmeye
        // yol açan bilinen bir timing sorunu var. Bu çağrı asıl işlevsel bir
        // gereklilik değil (sadece sistem çubuklarını şeffaf yapıyor), bu
        // yüzden try-catch ile sarmalanıyor: en kötü ihtimalle edge-to-edge
        // görünüm bir seferliğine uygulanmaz ama uygulama asla bu yüzden
        // çökmez.
        try {
            enableEdgeToEdge()
        } catch (e: Exception) {
            Log.w("MainActivity", "enableEdgeToEdge başarısız, atlanıyor: ${e.message}")
        }

        // Android 15 uyumluluğu: LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES deprecated.
        // Bazı SDK'lar (AdMob, Firebase) kendi içlerinde shortEdges kullanabiliyor.
        // Runtime'da ALWAYS ile override ederek Play Console uyarısını gideriyoruz.
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                window.attributes = window.attributes.also { attrs ->
                    attrs.layoutInDisplayCutoutMode =
                        android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                }
            }
        } catch (e: Exception) {
            Log.w("MainActivity", "layoutInDisplayCutoutMode ayarlanamadı: ${e.message}")
        }

        // AdMob SDK zaten HeftrangApp.onCreate() içinde (Application seviyesinde,
        // en erken nokta) başlatıldı. Config yükleme ise AdsViewModel'in init{}
        // bloğunda TEK SEFER tetiklenir (bkz. AdsViewModel.kt). Burada tekrar
        // adsVm.loadAdConfigs() çağırmak aynı Firestore isteğini ikinci kez
        // atıyordu — gereksiz ağ trafiği ve gecikme yaratan bir hataydı, kaldırıldı.

        // ScreenTracker — activity lifecycle dinleyicisi + adsVm bağla
        application.registerActivityLifecycleCallbacks(screenTracker)
        screenTracker.bind(adsVm, this)

        // Bildirimden gelen intent'i al
        pendingNavTarget = intent?.getStringExtra("navigate_to")

        // ÇÖZÜLDÜ (Play Console crash raporu — Redmi Note 8/MIUI'de HER
        // SEFERİNDE tekrarlanan kalıcı çökme): androidx.activity.compose'un
        // setContent() implementasyonu şu satırla başlıyor:
        //   window.decorView.findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
        // "Bir sonraki frame'de tekrar dene" yaklaşımı bu cihazda işe
        // yaramadı, çünkü sorun geçici bir zamanlama meselesi değil: bazı
        // MIUI/EMUI sürümlerinde decorView'in "android.R.id.content" view'i
        // standart Android'den farklı kurulduğu için findViewById() HİÇBİR
        // ZAMAN normal View dönmüyor, her denemede aynı NPE tekrar oluşuyor.
        // Çözüm: setContent()'i hiç çağırmıyoruz. Bunun yerine kendi
        // ComposeView'imizi oluşturup doğrudan setContentView() ile activity'nin
        // köküne bağlıyoruz — bu, AndroidX'in içindeki problemli
        // "var olan ComposeView'i bul" adımını tamamen atlıyor.
        //
        // NOT: Owner'ları (Lifecycle/ViewModelStore/SavedState) burada MANUEL
        // set etmiyoruz — bir önceki denemede bunu yapmıştık ama
        // setViewTreeLifecycleOwner/setViewTreeViewModelStoreOwner fonksiyon
        // imzaları androidx.lifecycle sürümüne göre değişebiliyor ve CI'da
        // derleme hatası verdi ("Unresolved reference"). Buna hiç gerek yok:
        // ComponentActivity'nin kendi super.onCreate()'i bu owner'ları zaten
        // decorView'e set eder, ve View hiyerarşisinde child view'ler
        // (setContentView() ile decorView'in altına eklenen bu ComposeView
        // dahil) bunları YUKARI doğru arayarak otomatik miras alır — normal
        // setContent() akışı da zaten bu mekanizmaya güvenir, ekstra bir şey
        // yapmaz.
        safeSetContent()

        // Bildirim izni iste (FCM → GMS gerektirir)
        if (gmsAvailable) requestNotificationPermission()

        // Güncelleme kontrolü (Play Core → GMS/Play Store gerektirir)
        if (gmsAvailable) checkForUpdate()

        // Play Store puan/yorum kutusu — fırsatçı, kendi kotamızla (spam olmasın)
        com.heftreng.app.util.InAppReviewHelper.maybeRequestReview(this)

        // Günlük Kurdî ders hatırlatıcısı — her gün saat 20:00
        com.heftreng.app.worker.KurdiReminderWorker.schedule(this, hourOfDay = 20)
    }

    // ── Play Console crash raporu (Redmi Note 8 Pro / Android 10, build 1462) ──
    // NullPointerException: "ViewGroup.getChildAt(int) on a null object reference"
    // androidx.activity.compose.ComponentActivityKt.setContent içinde oluşuyordu.
    //
    // O raporun ait olduğu build'de bu ekran hâlâ AndroidX'in DOĞRUDAN
    // `setContent { }` extension'ını kullanıyordu — bu extension, ilk
    // çağrıldığında window.decorView.findViewById<ViewGroup>(android.R.id.content)
    // üzerinden VAR OLAN bir ComposeView arar (yeniden kullanmak için); bazı
    // MIUI/EMUI sürümlerinde bu view beklenen hiyerarşide bulunamıyor ve
    // getChildAt(0) çağrısı null referans üzerinde patlıyor.
    //
    // Çözüm iki katmanlı:
    //  1) setContent {} yerine KENDİ ComposeView'imizi oluşturup doğrudan
    //     setContentView() ile bağlıyoruz — bu, AndroidX'in problemli "var
    //     olan ComposeView'i bul" adımını tamamen atlıyor (kök sebep düzeltmesi).
    //  2) Ek güvenlik ağı: setContentView()'in kendisi de (Window/decorView
    //     kurulumuna dokunduğu için) teorik olarak aynı aileden bir NPE'ye
    //     açık olabilir — bu yüzden tüm blok try-catch ile sarmalanıyor.
    //     Başarısız olursa boş bir Compose ekranı yerine, kullanıcıyı en
    //     azından çökmeden bir hata ekranıyla karşılıyoruz (activity'yi
    //     sonlandırıp yeniden başlatmaya yönlendirmek yerine, sessiz crash'ten
    //     çok daha iyi bir kullanıcı deneyimi).
    private fun safeSetContent() {
        try {
            val composeView = androidx.compose.ui.platform.ComposeView(this).apply {
                setContent {
                    // ÇÖZÜLDÜ: Eskiden sadece settingsVm.darkMode (sabit Boolean)
                    // okunuyordu — "system" tercihi hiç ele alınmıyordu, uygulama
                    // telefonun karanlık/aydınlık mod değişikliğine hiç tepki
                    // vermiyordu. Artık themeMode "system" ise gerçek zamanlı
                    // isSystemInDarkTheme() kullanılıyor — telefonun teması
                    // değişince (örn. güneş batımı otomatik koyu moda geçince)
                    // uygulama da anında buna uyuyor.
                    val themeMode by settingsVm.themeMode.collectAsState()
                    val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
                    val isDark = when (themeMode) {
                        "light" -> false
                        "dark"  -> true
                        else    -> systemDark   // "system"
                    }
                    HeftrangTheme(darkMode = isDark) {
                        HeftrangNavHost(initialRoute = pendingNavTarget)
                    }
                }
            }
            setContentView(composeView)
        } catch (e: Exception) {
            // Son çare: en azından uygulama sessizce çökmesin. Bu durumda
            // kullanıcıya basit, Compose'a bağımlı olmayan bir View tabanlı
            // hata ekranı gösteriyoruz — Play Console'a yine hata raporu
            // düşer ama kullanıcı en azından uygulamayı kapatıp tekrar
            // açabileceğini anlayan bir ekran görür, ani kapanma yaşamaz.
            Log.e("MainActivity", "safeSetContent başarısız, fallback ekranı gösteriliyor", e)
            try {
                val fallback = android.widget.TextView(this).apply {
                    text = "Heftreng şu anda açılamadı.\nLütfen uygulamayı kapatıp tekrar deneyin."
                    gravity = android.view.Gravity.CENTER
                    setPadding(48, 48, 48, 48)
                    textSize = 16f
                }
                setContentView(fallback)
            } catch (_: Exception) {
                // Buraya kadar gelindiyse gerçekten sistem seviyesinde ciddi
                // bir sorun var — yapılabilecek başka bir şey yok, activity
                // Android tarafından zaten sonlandırılacaktır.
            }
        }
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
        // Reklam lifecycle — banner resume + native pool yenileme
        adsVm.onAppForeground()
        // Uygulama arka plandan dönünce güncelleme kontrol et
        appUpdateManager.appUpdateInfo.addOnSuccessListener { info ->
            if (info.installStatus() == InstallStatus.DOWNLOADED) {
                appUpdateManager.completeUpdate()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        // Banner'ları durdur — Google politikası
        adsVm.onAppBackground()
    }

    override fun onDestroy() {
        super.onDestroy()
        appUpdateManager.unregisterListener(installStateListener)
        application.unregisterActivityLifecycleCallbacks(screenTracker)
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
