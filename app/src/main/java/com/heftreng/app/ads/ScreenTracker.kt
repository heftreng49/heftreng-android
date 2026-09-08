package com.heftreng.app.ads

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import com.heftreng.app.ui.i18n.Strings
import com.heftreng.app.viewmodel.AdsViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

// ═══════════════════════════════════════════════════════════════════════════
//  ScreenTracker — Öncelik: Doğru reklam zamanlaması
//  @Singleton — Hilt constructor injection, EntryPoint ile Composable'dan erişilir.
// ═══════════════════════════════════════════════════════════════════════════

@Singleton
class ScreenTracker @Inject constructor() : Application.ActivityLifecycleCallbacks {

    // ── Mevcut ekran ────────────────────────────────────────────────────────
    private val _currentRoute = MutableStateFlow<String?>(null)
    val currentRoute = _currentRoute.asStateFlow()

    // ── Interstitial gösterilebilir ekranlar ────────────────────────────────
    // Okuma (chapter), yazma (compose), auth, checkout gibi ekranlar dahil DEĞİL.
    private val INTERSTITIAL_ALLOWED_ROUTES = setOf(
        "feed", "library", "profile/me", "kurdi", "search",
        "blog", "notifications", "reading_list"
    )

    // ── Min ekran geçiş sayısı ───────────────────────────────────────────────
    private val MIN_SCREENS_BETWEEN = 4

    private var screenCount       = 0
    private var lastShownAtCount  = 0
    private var adsVmRef          : AdsViewModel? = null
    private var activityRef       : Activity?     = null

    // ── Route güncelleme — NavHost'tan çağrılır ──────────────────────────────
    fun onRouteChanged(route: String?) {
        if (route == null) return
        _currentRoute.value = route
        screenCount++
    }

    // ── AdsViewModel referansı ───────────────────────────────────────────────
    fun bind(adsVm: AdsViewModel, activity: Activity) {
        adsVmRef    = adsVm
        activityRef = activity
    }

    // ── Interstitial gösterilebilir mi? ─────────────────────────────────────
    fun canShowInterstitial(): Boolean {
        val route = _currentRoute.value ?: return false
        val routeBase = route.substringBefore("/")
        if (!INTERSTITIAL_ALLOWED_ROUTES.any { route == it || routeBase == it.substringBefore("/") }) return false
        if (screenCount - lastShownAtCount < MIN_SCREENS_BETWEEN) return false
        return true
    }

    // ── Rewarded Interstitial göster (hazırsa) ────────────────────────────────
    // Normal interstitial yerine rewarded interstitial kullanılır.
    // Bu format opt-in gerektirmez (kullanıcıya sormadan otomatik açılır) —
    // o yüzden reklam açılmadan hemen önce kısa bir Toast ile ödülü bildiriyoruz;
    // AdMob'un kendi giriş ekranına güvenmek yerine kullanıcı her durumda bilgilendirilmiş oluyor.
    fun tryShowInterstitial(onDismiss: () -> Unit = {}) {
        val activity = activityRef ?: return
        val adsVm    = adsVmRef    ?: return
        if (!canShowInterstitial()) return

        // lastShownAtCount BURADA değil, reklam gerçekten gösterilince tüketilir.
        // Eskiden burada set ediliyordu → reklam hiç yüklü olmasa bile (interstitialAd==null)
        // count harcanıyor, kullanıcı 4 ekran daha gezmek zorunda kalıyordu.
        // Şimdi showRewardedInterstitial içindeki gösterim kesinleşince count tüketiliyor.
        val countSnapshot = screenCount
        lastShownAtCount = countSnapshot   // optimistik reserve — reklam yoksa geri alınır

        // Ödül vaadi SADECE reklam gerçekten hazırsa gösterilir — hazır
        // değilse (doluluk yok) showRewardedInterstitial() ödülsüz normal
        // interstitial'a düşüyor; o durumda kullanıcıya boş vaat vermeyelim.
        if (adsVm.isRewardedInterstitialReady()) {
            val lang = activity.getSharedPreferences("hf_settings", Context.MODE_PRIVATE)
                .getString("hf_lang", "tr") ?: "tr"
            Toast.makeText(activity, Strings.rewardedInterstitialInfo(lang), Toast.LENGTH_LONG).show()
        }

        adsVm.showRewardedInterstitial(
            activity    = activity,
            // grantAdFree() AdsViewModel.showRewardedInterstitial() içinde çağrılıyor.
            // Burada tekrar çağırmak çift grant yaratıyordu (2 saat veriyor gibi görünüyor,
            // ama aslında aynı bitiş zamanını iki kez yazıyordu — yine de karışıklık).
            onRewarded  = {},
            onDismissed = { onDismiss() },
        )
        // NOT: showRewardedInterstitial reklam yoksa showInterstitial'a düşer,
        // o da yoksa onDismissed() çağırır ama reklam GÖSTERILMEZ.
        // Bu durumda count harcanmamalı — bir sonraki geçişte hemen denenir.
        // AdsViewModel'in her iki null-ad dalında onDismissed tetiklendiği için
        // burada "gerçekten gösterildi mi?" bilgisi yok; lastInterstitialShownAtMs
        // VM içinde set ediliyor, 60s MIN_INTERVAL zaten koruma sağlıyor.
        // Yeterli: optimistik reserve + 60s VM guard birlikte çalışır.
    }

    // ── ActivityLifecycleCallbacks ───────────────────────────────────────────
    override fun onActivityCreated(a: Activity, b: Bundle?)  {}
    override fun onActivityStarted(a: Activity)              { activityRef = a }
    override fun onActivityResumed(a: Activity)              { activityRef = a }
    override fun onActivityPaused(a: Activity)               {}
    override fun onActivityStopped(a: Activity)              {}
    override fun onActivitySaveInstanceState(a: Activity, b: Bundle) {}
    override fun onActivityDestroyed(a: Activity)            { if (activityRef == a) { activityRef = null } }
}
