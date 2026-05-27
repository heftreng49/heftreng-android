package com.heftreng.app

import android.app.Application
import android.util.Log
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class HeftrangApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // Firebase App Check — Play Integrity
        FirebaseApp.initializeApp(this)
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

        // AdMob SDK başlatma
        MobileAds.initialize(this) { initStatus ->
            val statusMap = initStatus.adapterStatusMap
            for ((adapter, status) in statusMap) {
                Log.d("AdMob", "Adapter: $adapter, Status: ${status.initializationState}")
            }
        }
    }
}
