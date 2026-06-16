package com.heftreng.app.ads

import android.app.Activity
import android.app.Application
import android.os.Bundle
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
        adsVmRef  = adsVm
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

    // ── Interstitial göster (hazırsa) ────────────────────────────────────────
    fun tryShowInterstitial(onDismiss: () -> Unit = {}) {
        val activity = activityRef ?: return
        val adsVm    = adsVmRef    ?: return
        if (!canShowInterstitial()) return

        lastShownAtCount = screenCount
        adsVm.showInterstitial(activity) { onDismiss() }
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
