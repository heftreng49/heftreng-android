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
    }

    override fun onCreate() {
        super.onCreate()

        FirebaseApp.initializeApp(this)
        FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
            PlayIntegrityAppCheckProviderFactory.getInstance()
        )

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
}
