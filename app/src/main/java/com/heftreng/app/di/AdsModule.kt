package com.heftreng.app.di

import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import com.heftreng.app.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * AdsModule — Reklam altyapısı için DI bağlamaları.
 *
 * NEDEN BURASI?
 * FirebaseRemoteConfig singleton olmalı — her yerde aynı instance.
 * Hilt bunu @Singleton ile garanti eder.
 * RemoteConfigManager @Inject constructor ile kendisi inject edilir,
 * burada sadece FirebaseRemoteConfig instance'ı sağlanıyor.
 */
@Module
@InstallIn(SingletonComponent::class)
object AdsModule {

    @Provides
    @Singleton
    fun provideFirebaseRemoteConfig(): FirebaseRemoteConfig {
        val config = FirebaseRemoteConfig.getInstance()
        val settings = FirebaseRemoteConfigSettings.Builder()
            // Debug build'de 0 → her fetchAndActivate() anında network'e gider
            // (Firebase konsolunda değer değiştirince anında uygulamaya yansır)
            // Release build'de 43200 → 12 saat cache, API maliyeti sıfır
            .setMinimumFetchIntervalInSeconds(
                if (BuildConfig.DEBUG) 0L else 43_200L
            )
            .build()
        config.setConfigSettingsAsync(settings)
        return config
    }
}
