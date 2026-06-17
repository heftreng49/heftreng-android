package com.heftreng.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

// ── Geçici test ViewModel — bağlantı çalışıyor mu doğrular ──────────────────
// Supabase kurulumu tamamlandıktan sonra bu dosyayı SİLEBİLİRSİN.
// Kullanım:
//   val vm: SupabaseTestViewModel = hiltViewModel()
//   LaunchedEffect(Unit) { vm.ping() }
//   val status by vm.status.collectAsState()
// ─────────────────────────────────────────────────────────────────────────────

sealed interface PingStatus {
    data object Idle    : PingStatus
    data object Loading : PingStatus
    data class  Success(val message: String) : PingStatus
    data class  Error(val message: String)   : PingStatus
}

@HiltViewModel
class SupabaseTestViewModel @Inject constructor(
    private val supabase: SupabaseClient,
) : ViewModel() {

    private val _status = MutableStateFlow<PingStatus>(PingStatus.Idle)
    val status = _status.asStateFlow()

    // Sadece bağlantıyı kontrol eder — herhangi bir tablo yoksa
    // "0 rows" döner ama hata vermez → bağlantı başarılı demektir.
    fun ping() {
        _status.value = PingStatus.Loading
        viewModelScope.launch {
            try {
                // authors tablosu henüz yoksa bile 200 döner (boş array)
                // Tabloyu Supabase'de oluşturduktan sonra burayı test edebilirsin.
                supabase.postgrest["authors"]
                    .select { limit(1) }
                _status.value = PingStatus.Success("✅ Supabase bağlantısı başarılı!")
            } catch (e: Exception) {
                _status.value = PingStatus.Error("❌ ${e.message}")
            }
        }
    }
}
