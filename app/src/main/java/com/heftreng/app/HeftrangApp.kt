package com.heftreng.app

import android.app.Application
import android.util.Log
import coil.Coil
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.google.android.gms.ads.MobileAds
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

        // MobileAds başlatması MainActivity.onCreate'da ConsentHelper sonrasında yapılır.
        // Application context'te Activity gerekmediği için sadece SDK'yı burada hazırla.
        // Gerçek initialize() çağrısı MainActivity'de UMP onayından sonra gelir.
        // Ancak ConsentHelper'sız cihazlar (GDPR/CCPA dışı) için burada da başlatalım —
        // MainActivity zaten ikinci kez çağırsa sorun olmaz (idempotent).
        MobileAds.initialize(this) { initStatus ->
            _sdkReady.value = true
            if (Log.isLoggable("AdMob", Log.DEBUG)) {
                initStatus.adapterStatusMap.forEach { (adapter, status) ->
                    Log.d("AdMob", "Adapter: $adapter → ${status.initializationState}")
                }
            }
        }
    }
}
