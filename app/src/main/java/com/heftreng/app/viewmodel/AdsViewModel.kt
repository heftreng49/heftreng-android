package com.heftreng.app.viewmodel

import android.app.Activity
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.ads.*
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.firebase.firestore.FirebaseFirestore
import com.heftreng.app.data.model.AdMobTestIds
import com.heftreng.app.data.model.AdMobProdIds
import com.heftreng.app.data.model.CmsAdConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@HiltViewModel
class AdsViewModel @Inject constructor(
    private val firestore: FirebaseFirestore,
) : ViewModel() {

    // ── Konfigürasyonlar ──────────────────────────────────────────────────────
    private val _bannerConfig       = MutableStateFlow<CmsAdConfig?>(null)
    val bannerConfig = _bannerConfig.asStateFlow()

    private val _interstitialConfig = MutableStateFlow<CmsAdConfig?>(null)
    val interstitialConfig = _interstitialConfig.asStateFlow()

    private val _rewardedConfig     = MutableStateFlow<CmsAdConfig?>(null)
    val rewardedConfig = _rewardedConfig.asStateFlow()

    // ── Global kill switch ────────────────────────────────────────────────────
    private val _adsEnabled         = MutableStateFlow(true)
    val adsEnabled = _adsEnabled.asStateFlow()

    // ── Yüklenmiş reklamlar ───────────────────────────────────────────────────
    private var interstitialAd: InterstitialAd? = null
    private var rewardedAd:     RewardedAd?     = null

    // ── Firestore'dan config yükle ────────────────────────────────────────────
    fun loadAdConfigs() {
        viewModelScope.launch {
            try {
                val snap = firestore.collection("cms_ads").get().await()

                // Global kill switch — cms_ads/global belgesi
                val global = snap.documents.find { it.id == "global" }
                _adsEnabled.value = global?.getBoolean("enabled") ?: true

                snap.documents.forEach { doc ->
                    if (doc.id == "global") return@forEach
                    val d = doc.data ?: return@forEach
                    val config = CmsAdConfig(
                        id       = doc.id,
                        unitId   = d["unitId"]   as? String  ?: "",
                        enabled  = d["enabled"]  as? Boolean ?: false,
                        testMode = d["testMode"] as? Boolean ?: true,
                        position = (d["position"]  as? Long)?.toInt() ?: 5,
                        frequency = (d["frequency"] as? Long)?.toInt() ?: 3,
                        xpReward = (d["xpReward"]  as? Long)?.toInt() ?: 50,
                    )
                    when (doc.id) {
                        "banner_feed"       -> _bannerConfig.value = config
                        "interstitial_serial" -> _interstitialConfig.value = config
                        "rewarded_xp"       -> _rewardedConfig.value = config
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // ── Interstitial yükle ────────────────────────────────────────────────────
    fun loadInterstitial(context: Context) {
        val config = _interstitialConfig.value ?: return
        if (!config.enabled || !_adsEnabled.value) return

        val unitId = if (config.testMode) AdMobTestIds.INTERSTITIAL else AdMobProdIds.INTERSTITIAL
        if (unitId.isBlank()) return

        val adRequest = AdRequest.Builder().build()
        InterstitialAd.load(
            context, unitId, adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                }
                override fun onAdFailedToLoad(error: LoadAdError) {
                    interstitialAd = null
                }
            }
        )
    }

    fun showInterstitial(activity: Activity, onDismiss: () -> Unit = {}) {
        val ad = interstitialAd
        if (ad != null) {
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    interstitialAd = null
                    onDismiss()
                    loadInterstitial(activity)   // Sonraki için önceden yükle
                }
                override fun onAdFailedToShowFullScreenContent(e: AdError) {
                    interstitialAd = null
                    onDismiss()
                }
            }
            ad.show(activity)
        } else {
            onDismiss()
        }
    }

    // ── Rewarded yükle ────────────────────────────────────────────────────────
    fun loadRewarded(context: Context) {
        val config = _rewardedConfig.value ?: return
        if (!config.enabled || !_adsEnabled.value) return

        val unitId = if (config.testMode) AdMobTestIds.REWARDED else AdMobProdIds.REWARDED
        if (unitId.isBlank()) return

        val adRequest = AdRequest.Builder().build()
        RewardedAd.load(
            context, unitId, adRequest,
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    rewardedAd = ad
                }
                override fun onAdFailedToLoad(error: LoadAdError) {
                    rewardedAd = null
                }
            }
        )
    }

    fun showRewarded(activity: Activity, onRewarded: (Int) -> Unit, onDismiss: () -> Unit = {}) {
        val ad     = rewardedAd ?: run { onDismiss(); return }
        val config = _rewardedConfig.value

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                rewardedAd = null
                onDismiss()
                loadRewarded(activity)
            }
            override fun onAdFailedToShowFullScreenContent(e: AdError) {
                rewardedAd = null
                onDismiss()
            }
        }
        ad.show(activity) { onRewarded(config?.xpReward ?: 50) }
    }

    // ── Aktif banner unit ID'sini döndür ─────────────────────────────────────
    fun bannerUnitId(): String? {
        val config = _bannerConfig.value ?: return null
        if (!config.enabled || !_adsEnabled.value) return null
        return if (config.testMode) AdMobTestIds.BANNER else AdMobProdIds.BANNER
    }

    // ── Banner pozisyonu ──────────────────────────────────────────────────────
    fun bannerPosition(): Int = _bannerConfig.value?.position ?: 5

    override fun onCleared() {
        super.onCleared()
        interstitialAd = null
        rewardedAd     = null
    }
}
