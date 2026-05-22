package com.heftreng.app

import android.app.Application
import android.util.Log
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class HeftrangApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // Test cihazı olarak ekle — reklamlar test modunda yüklenir
        // Kendi cihazının test ID'sini logcat'ten al:
        // "Use RequestConfiguration.Builder().setTestDeviceIds(Arrays.asList("XXXX")) ..."
        MobileAds.setRequestConfiguration(
            RequestConfiguration.Builder()
                .setTestDeviceIds(listOf(
                    "EMULATOR",
                    "C84376E1EF997B7B30871490FB336DF4",  // ee61e8bf-564d-4409-b0e3-62f2c15c7cd6
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
