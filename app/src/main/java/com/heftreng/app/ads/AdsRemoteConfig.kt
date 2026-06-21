package com.heftreng.app.ads

import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.ktx.remoteConfig
import com.google.firebase.remoteconfig.ktx.remoteConfigSettings
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.tasks.await

/**
 * Firebase Remote Config wrapper — reklam aç/kapat ve frekans yönetimi.
 *
 * Remote Config'te tanımlanacak parametreler:
 *   ads_enabled          : boolean  — global reklam açma/kapatma
 *   banner_feed_enabled  : boolean
 *   banner_blog_enabled  : boolean
 *   banner_lib_enabled   : boolean
 *   banner_kurdi_enabled : boolean
 *   native_feed_enabled  : boolean
 *   native_feed_position : number   — kaçıncı postta bir native (varsayılan 5)
 *   native_feed_frequency: number   — kaç posttan bir tekrar (varsayılan 3)
 *   native_blog_enabled  : boolean
 *   interstitial_enabled : boolean
 *   rewarded_enabled     : boolean
 *   rewarded_daily_limit : number   — kullanıcı başına günlük max (varsayılan 3)
 */
object AdsRemoteConfig {

    private val rc: FirebaseRemoteConfig by lazy {
        Firebase.remoteConfig.apply {
            setConfigSettingsAsync(
                remoteConfigSettings {
                    // Prod: 1 saat, Dev: 0 (her zaman fetch et)
                    minimumFetchIntervalInSeconds = 3600L
                }
            )
            // ── Varsayılan değerler — Remote Config'e erişilemeyen durumlarda ──
            // Bu değerler hardcode'dur; Remote Config override eder.
            setDefaultsAsync(
                mapOf(
                    "ads_enabled"           to true,
                    "banner_feed_enabled"   to true,
                    "banner_blog_enabled"   to true,
                    "banner_lib_enabled"    to true,
                    "banner_kurdi_enabled"  to true,
                    "native_feed_enabled"   to true,
                    "native_feed_position"  to 5L,
                    "native_feed_frequency" to 3L,
                    "native_blog_enabled"   to true,
                    "interstitial_enabled"  to true,
                    "rewarded_enabled"      to true,
                    "rewarded_daily_limit"  to 3L,
                )
            )
        }
    }

    /** Uygulama başlarken bir kez çağır — cache'den anlık aktive olur */
    suspend fun initialize() {
        try {
            rc.fetchAndActivate().await()
        } catch (_: Exception) {
            // Network yoksa varsayılan değerlerle çalışmaya devam eder
            rc.activate().await()
        }
    }

    // ── Global ───────────────────────────────────────────────────────────────
    val adsEnabled          get() = rc.getBoolean("ads_enabled")

    // ── Banner ───────────────────────────────────────────────────────────────
    val bannerFeedEnabled   get() = adsEnabled && rc.getBoolean("banner_feed_enabled")
    val bannerBlogEnabled   get() = adsEnabled && rc.getBoolean("banner_blog_enabled")
    val bannerLibEnabled    get() = adsEnabled && rc.getBoolean("banner_lib_enabled")
    val bannerKurdiEnabled  get() = adsEnabled && rc.getBoolean("banner_kurdi_enabled")

    // ── Native ───────────────────────────────────────────────────────────────
    val nativeFeedEnabled   get() = adsEnabled && rc.getBoolean("native_feed_enabled")
    val nativeFeedPosition  get() = rc.getLong("native_feed_position").toInt()
    val nativeFeedFrequency get() = rc.getLong("native_feed_frequency").toInt()
    val nativeBlogEnabled   get() = adsEnabled && rc.getBoolean("native_blog_enabled")

    // ── Tam ekran ────────────────────────────────────────────────────────────
    val interstitialEnabled get() = adsEnabled && rc.getBoolean("interstitial_enabled")
    val rewardedEnabled     get() = adsEnabled && rc.getBoolean("rewarded_enabled")
    val rewardedDailyLimit  get() = rc.getLong("rewarded_daily_limit").toInt()
}
