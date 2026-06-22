package com.heftreng.app

import android.app.Application
import android.util.Log
import coil.Coil
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

@HiltAndroidApp
class HeftrangApp : Application() {

    companion object {
        /**
         * MobileAds.initialize() tamamlanmadan reklam isteği gönderilmemeli.
         * Erken istek → AdMob sessiz red → istek sayılır ama gösterim olmaz → fill rate düşer.
         * AdEngine her istekten önce bu flag true olana kadar bekler (genellikle <200ms).
         */
        private val _sdkReady = MutableStateFlow(false)
        val sdkReady = _sdkReady.asStateFlow()
    }

    override fun onCreate() {
        super.onCreate()

        // Firebase App Check — Play Integrity
        FirebaseApp.initializeApp(this)

        // Coil — disk + memory cache yapılandırması (68 AsyncImage için kritik)
        Coil.setImageLoader(
            ImageLoader.Builder(this)
                .memoryCache {
                    MemoryCache.Builder(this)
                        .maxSizePercent(0.20) // RAM'in %20'si
                        .build()
                }
                .diskCache {
                    DiskCache.Builder()
                        .directory(cacheDir.resolve("image_cache"))
                        .maxSizeBytes(150L * 1024 * 1024) // 150 MB
                        .build()
                }
                .crossfade(true)
                .build()
        )
        val appCheck = FirebaseAppCheck.getInstance()

        // Release build: sadece gerçek Play Store APK'ları geçer
        // Debug build için ayrı provider gerekmez — enforce açılmadan test edilebilir
        appCheck.installAppCheckProviderFactory(
            PlayIntegrityAppCheckProviderFactory.getInstance()
        )

        // Test cihazı olarak ekle — reklamlar test modunda yüklenir
        MobileAds.setRequestConfiguration(
            RequestConfiguration.Builder()
                .setTestDeviceIds(listOf(
                    "EMULATOR",
                    "C84376E1EF997B7B30871490FB336DF4",
                ))
                .build()
        )

        // AdMob SDK başlatma — callback'te sdkReady flag SET edilir
        MobileAds.initialize(this) { initStatus ->
            _sdkReady.value = true   // ← AdEngine artık istek atabilir
            if (Log.isLoggable("AdMob", Log.DEBUG)) {
                initStatus.adapterStatusMap.forEach { (adapter, status) ->
                    Log.d("AdMob", "Adapter: $adapter → ${status.initializationState}")
                }
            }
        }
    }
}
