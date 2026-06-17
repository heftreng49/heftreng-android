package com.heftreng.app.util

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Uygulama genelinde foreground / background durumunu takip eder.
 *
 * ProcessLifecycleOwner: tüm Activity'lerin lifecycle'ını birleştirir.
 * - onStart  → uygulama foreground'a geçti (herhangi bir ekran açık)
 * - onStop   → uygulama tamamen arka plana geçti (hiçbir ekran görünmüyor)
 *
 * Kullanım:
 *   AppLifecycleObserver.isInForeground.collectAsState()
 *   AppLifecycleObserver.onForeground { ... }  // listener
 */
object AppLifecycleObserver : DefaultLifecycleObserver {

    private val _isInForeground = MutableStateFlow(false)
    val isInForeground = _isInForeground.asStateFlow()

    private val foregroundCallbacks  = mutableListOf<() -> Unit>()
    private val backgroundCallbacks  = mutableListOf<() -> Unit>()

    private var registered = false

    /** MainActivity.onCreate() veya Application'dan bir kez çağır. */
    fun register() {
        if (registered) return
        registered = true
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        _isInForeground.value = true
        foregroundCallbacks.forEach { it() }
    }

    override fun onStop(owner: LifecycleOwner) {
        _isInForeground.value = false
        backgroundCallbacks.forEach { it() }
    }

    /**
     * Uygulama foreground'a her geçişinde çağrılacak lambda ekle.
     * ViewModel'lar onCleared() içinde [removeForegroundCallback] ile temizlemeli.
     */
    fun addForegroundCallback(cb: () -> Unit) {
        if (cb !in foregroundCallbacks) foregroundCallbacks.add(cb)
    }

    fun addBackgroundCallback(cb: () -> Unit) {
        if (cb !in backgroundCallbacks) backgroundCallbacks.add(cb)
    }

    fun removeForegroundCallback(cb: () -> Unit) { foregroundCallbacks.remove(cb) }
    fun removeBackgroundCallback(cb: () -> Unit) { backgroundCallbacks.remove(cb) }
}
