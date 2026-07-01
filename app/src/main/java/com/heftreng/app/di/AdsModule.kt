package com.heftreng.app.di

import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.heftreng.app.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * AdsModule — FirebaseRemoteConfig singleton DI bağlaması.
 *
 * Interval burada AYARLANMIYOR — RemoteConfigManager.init() kendi
 * setConfigSettingsAsync() çağrısını yapıyor. Bu modülde ikinci bir
 * setConfigSettingsAsync() çağrısı olmasın; iki çağrı yarış yaratıyor
 * ve hangisi kazanırsa onun interval'ı geçerli oluyordu (tutarsız).
 *
 * RemoteConfigManager interval'ları:
 *   Debug  → 0s   (her açılışta anında günceller)
 *   Prod   → 3600s (1 saat — konsol değişikliği max 1 saatte devreye girer)
 */
@Module
@InstallIn(SingletonComponent::class)
object AdsModule {

    @Provides
    @Singleton
    fun provideFirebaseRemoteConfig(): FirebaseRemoteConfig =
        FirebaseRemoteConfig.getInstance()
}
