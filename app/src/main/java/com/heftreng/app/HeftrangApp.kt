package com.heftreng.app

import android.app.Application
import com.google.android.gms.ads.MobileAds
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class HeftrangApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // AdMob SDK başlatma — uygulama açılışında bir kez çalışır
        MobileAds.initialize(this)
    }
}
