package com.heftreng.app.ui.screens.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.heftreng.app.ui.theme.*
import com.heftreng.app.viewmodel.AdminViewModel

// ─────────────────────────────────────────────────────────────────────────────
//  AccountMergeScreen — Hesap Birleştirme
//
//  Kullanıcı eski hesabına (mail/şifre kaybı) erişemiyorsa, yeni bir hesap
//  açar. Admin bu ekrandan eski UID'nin tüm verisini (postlar, takipçiler,
//  mesajlar, Kurdî ilerlemesi vb.) yeni UID'ye taşır.
//
//  Akış: önce "Önizle" (dryRun) ile ne kadar veri taşınacağı gösterilir,
//  admin kontrol edip "Onayla ve Taşı" ile gerçek işlemi başlatır.
//  Bu iki adımlı yapı yanlışlıkla veri taşımayı önler.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun AccountMergeScreen(
    navController: NavController,
    adminVm: AdminViewModel = hiltViewModel(),
) {
    var oldUid by remember { mutableStateOf("") }
    var newUid by remember { mutableStateOf("") }

    val loading by adminVm.mergeLoading.collectAsState()
    val preview by adminVm.mergePreview.collectAsState()
    val result   by adminVm.mergeResult.collectAsState()

    Scaffold(
        containerColor = Background,
        topBar = {
            TopAppBar(
                title = { Text("Hesap Birleştirme", color = OnBackground, fontWeight = FontWeight.Bold, fontSize = 16.sp) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = OnBackground)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Background),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                "Kullanıcı eski hesabına (mail/şifre kaybı) erişemiyorsa, yeni bir " +
                "hesapla kayıt olur. Bu ekran eski hesabın verisini yeni hesaba taşır. " +
                "Eski veri SİLİNMEZ, sadece kopyalanır ve \"merged\" olarak işaretlenir.",
                color = Muted, fontSize = 12.sp,
            )

            OutlinedTextField(
                value = oldUid,
                onValueChange = { oldUid = it.trim() },
                label = { Text("Eski UID (kaybedilen hesap)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            OutlinedTextField(
                value = newUid,
                onValueChange = { newUid = it.trim() },
                label = { Text("Yeni UID (aktif hesap)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { adminVm.previewAccountMerge(oldUid, newUid) },
                    enabled = !loading && oldUid.isNotBlank() && newUid.isNotBlank(),
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceVar, contentColor = OnBackground),
                ) { Text("Önizle") }

                Button(
                    onClick = { adminVm.confirmAccountMerge(oldUid, newUid) },
                    enabled = !loading && preview != null,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = Color.Black),
                ) { Text("Onayla ve Taşı", fontWeight = FontWeight.Bold) }
            }

            if (loading) {
                CircularProgressIndicator(color = Amber, modifier = Modifier.align(Alignment.CenterHorizontally))
            }

            result?.let { msg ->
                Text(
                    msg,
                    color = if (msg.startsWith("✓")) Color(0xFF4CAF50) else Color(0xFFE57373),
                    fontSize = 13.sp, fontWeight = FontWeight.Medium,
                )
            }

            preview?.let { counts ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(SurfaceVar)
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("Taşınacak veriler:", color = OnBackground, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    counts.forEach { (key, value) ->
                        val count = (value as? Number)?.toInt() ?: 0
                        if (count > 0) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(key, color = Muted, fontSize = 12.sp)
                                Text("$count", color = OnBackground, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                    val total = counts.values.sumOf { (it as? Number)?.toInt() ?: 0 }
                    if (total == 0) {
                        Text("Eski UID'ye ait taşınacak veri bulunamadı.", color = Muted, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
