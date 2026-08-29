package com.heftreng.app

import android.app.Application
import coil.Coil
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

@HiltAndroidApp
class HeftrangApp : Application() {

    companion object {
        private val _sdkReady = MutableStateFlow(false)
        val sdkReady = _sdkReady.asStateFlow()

        /** MainActivity'deki UMP onay callback'inden çağrılır. */
        fun notifySdkReady() { _sdkReady.value = true }

        // Huawei ve GMS'siz diğer cihazlarda (AppGallery, bazı Çin OEM'leri vb.)
        // Google Play Services YOK. Firebase (Auth/Firestore/Messaging/AppCheck)
        // ve AdMob bu servise dayandığı için, GMS yoksa initializeApp() gibi
        // çağrılar Application.onCreate() içinde crash edip uygulamayı hiç
        // AÇILAMAZ hale getirebiliyordu. Bu bayrak sayesinde:
        //  - GMS varsa: her şey eskisi gibi çalışır (davranış değişmedi)
        //  - GMS yoksa: Firebase/AdMob'a hiç dokunulmaz, uygulama açılır;
        //    feed/kütüphane gibi Supabase üzerinden çalışan içerik (varsa)
        //    etkilenmez, sadece push bildirim / reklam / Firestore'a bağlı
        //    özellikler sessizce devre dışı kalır.
        // NOT: Bu, "gerçek Huawei desteği" (HMS entegrasyonu) DEĞİL — sadece
        // GMS'siz bir cihazda uygulamanın crash olmadan açılmasını sağlar.
        private val _isGmsAvailable = MutableStateFlow(true)
        val isGmsAvailable = _isGmsAvailable.asStateFlow()
    }

    override fun onCreate() {
        super.onCreate()

        // AdFreeManager — rewarded izleme sonrası 2 saat reklamsız
        com.heftreng.app.ads.AdFreeManager.init(this)

        val gmsAvailable = checkGmsAvailability()
        _isGmsAvailable.value = gmsAvailable

        if (gmsAvailable) {
            try {
                FirebaseApp.initializeApp(this)
                FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                    PlayIntegrityAppCheckProviderFactory.getInstance()
                )
            } catch (e: Exception) {
                // GoogleApiAvailability "mevcut" dese bile sürüm çok eski/uyumsuz
                // olabilir — burada da yine çökmeyelim, sadece Firebase'siz devam.
                android.util.Log.e("HeftrangApp", "Firebase init başarısız (GMS uyumsuz olabilir): ${e.message}")
                _isGmsAvailable.value = false
            }
        } else {
            android.util.Log.w(
                "HeftrangApp",
                "Google Play Services bulunamadı (Huawei/GMS'siz cihaz) — " +
                    "Firebase, AdMob ve push bildirimleri bu oturumda devre dışı."
            )
        }

        // Coil, GMS'e bağımlı değil — her koşulda kurulabilir.
        Coil.setImageLoader(
            ImageLoader.Builder(this)
                .memoryCache {
                    MemoryCache.Builder(this)
                        .maxSizePercent(0.20)
                        .build()
                }
                .diskCache {
                    DiskCache.Builder()
                        .directory(cacheDir.resolve("image_cache"))
                        .maxSizeBytes(150L * 1024 * 1024)
                        .build()
                }
                .crossfade(true)
                .build()
        )

        // MobileAds.initialize() BURADAN KALDIRILDI.
        //
        // ÖNCEKİ HALİ BUGGY'DI: Burada şartsız initialize() çağırmak, aşağıda
        // açıklanan UMP rıza akışını (MainActivity → ConsentHelper) tamamen
        // ANLAMSIZ kılıyordu — çünkü sdkReady, hangisi önce biterse ondan true
        // oluyordu, ve Application.onCreate() her zaman MainActivity'den (ve
        // onun ağ çağrısı gerektiren UMP adımından) önce/aynı anda çalıştığı
        // için pratikte HER ZAMAN rıza beklenmeden true oluyordu.
        //
        // ŞİMDİ: SDK başlatması SADECE MainActivity.onCreate() içinde,
        // ConsentHelper.initialize() → onCanRequestAds callback'inden sonra
        // yapılıyor (bkz. MainActivity.kt, notifySdkReady()). ConsentHelper
        // artık dahili bir zaman aşımı korumasına sahip (CONSENT_TIMEOUT_MS)
        // — yani bu callback ağ sorunu olsa bile en fazla ~4 saniye içinde
        // garanti tetiklenir, "reklamlar hiç yüklenmiyor" riski kalmaz.
    }

    /**
     * GMS gerçekten kullanılabilir mi kontrol eder — sadece "yüklü mü" değil,
     * sürümü uygulamanın ihtiyacını karşılıyor mu (isGooglePlayServicesAvailable
     * SUCCESS dışında bir kod dönerse GMS ya yok ya da güncellenmesi gerekiyor).
     */
    private fun checkGmsAvailability(): Boolean {
        return try {
            val availability = com.google.android.gms.common.GoogleApiAvailability.getInstance()
            val result = availability.isGooglePlayServicesAvailable(this)
            result == com.google.android.gms.common.ConnectionResult.SUCCESS
        } catch (e: Exception) {
            // GoogleApiAvailability sınıfının kendisi bulunamazsa (çok nadir,
            // ama GMS'siz bazı özel ROM'larda mümkün) burada da çökmeyelim.
            android.util.Log.e("HeftrangApp", "GMS kontrolü başarısız: ${e.message}")
            false
        }
    }
}
