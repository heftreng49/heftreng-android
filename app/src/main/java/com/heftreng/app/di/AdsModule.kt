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
 * RemoteConfigManager interval: Debug → 0s, Prod → 43200s (12 saat)
 */
@Module
@InstallIn(SingletonComponent::class)
object AdsModule {

    @Provides
    @Singleton
    fun provideFirebaseRemoteConfig(): FirebaseRemoteConfig =
        FirebaseRemoteConfig.getInstance()
}
