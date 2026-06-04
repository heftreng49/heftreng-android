package com.heftreng.app.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.google.firebase.auth.FirebaseAuth

// Admin email kaldırıldı — Firestore admins/{uid} kontrolü kullanılıyor

/**
 * Global admin debug paneli.
 * NavHost içinde Box'ın son elemanı olarak koy.
 * Sadece admin email ile giriş yapıldığında görünür.
 *
 * Kullanım:
 *   AdminDebugOverlay(
 *       entries = listOf(
 *           "myUid" to myUid,
 *           "screen" to "FeedScreen",
 *       )
 *   )
 *
 * Ya da global state olmadan, sadece floating bug butonu:
 *   AdminDebugOverlay()
 */

// Global debug log — her ekrandan buraya yazılır
object DebugLog {
    val entries = androidx.compose.runtime.mutableStateListOf<Pair<String, String>>()

    fun put(key: String, value: String) {
        val idx = entries.indexOfFirst { it.first == key }
        if (idx >= 0) entries[idx] = key to value
        else entries.add(key to value)
    }

    fun section(name: String) {
        put("── $name ──", "")
    }

    fun clear() = entries.clear()
}

@Composable
fun AdminDebugOverlay() {
    var isAdmin by remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        val listener = com.google.firebase.auth.FirebaseAuth.AuthStateListener { auth ->
            isAdmin = false // Firestore kontrolü LaunchedEffect'te yapılıyor
        }
        com.google.firebase.auth.FirebaseAuth.getInstance().addAuthStateListener(listener)
        // İlk değeri hemen set et
        isAdmin = false // Firestore kontrolü LaunchedEffect'te yapılıyor
        onDispose { com.google.firebase.auth.FirebaseAuth.getInstance().removeAuthStateListener(listener) }
    }
    if (!isAdmin) return

    var expanded by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(999f),
        contentAlignment = Alignment.BottomEnd,
    ) {
        // Panel
        AnimatedVisibility(
            visible = expanded,
            enter   = slideInVertically { it },
            exit    = slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.5f)
                    .background(Color(0xF0101010), RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .border(1.dp, Color(0xFF333333), RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .padding(12.dp),
            ) {
                // Başlık
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "🐛 Admin Debug",
                        color      = Color(0xFFF59E0B),
                        fontWeight = FontWeight.Bold,
                        fontSize   = 13.sp,
                    )
                    Row {
                        IconButton(
                            onClick  = {
                                val text = DebugLog.entries.joinToString("\n") { "${it.first}: ${it.second}" }
                                clipboard.setText(AnnotatedString(text))
                            },
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(Icons.Default.ContentCopy, null, tint = Color(0xFF888888), modifier = Modifier.size(16.dp))
                        }
                        IconButton(
                            onClick  = { expanded = false },
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(Icons.Default.Close, null, tint = Color(0xFF888888), modifier = Modifier.size(16.dp))
                        }
                    }
                }

                HorizontalDivider(color = Color(0xFF333333))
                Spacer(Modifier.height(8.dp))

                // Log satırları
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    if (DebugLog.entries.isEmpty()) {
                        Text("Henüz log yok.", color = Color(0xFF666666), fontSize = 11.sp)
                    } else {
                        DebugLog.entries.forEach { (key, value) ->
                            if (value.isEmpty()) {
                                // Section başlığı
                                Text(
                                    key,
                                    color      = Color(0xFFF59E0B),
                                    fontSize   = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier   = Modifier.padding(top = 4.dp),
                                )
                            } else {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        key,
                                        color      = Color(0xFF888888),
                                        fontSize   = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        modifier   = Modifier.weight(1f),
                                    )
                                    Text(
                                        value,
                                        color      = if (value == "true") Color(0xFF4ADE80)
                                                     else if (value == "false") Color(0xFFEF4444)
                                                     else Color(0xFFE2E8F0),
                                        fontSize   = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        modifier   = Modifier.weight(2f),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Floating bug butonu
        if (!expanded) {
            Box(
                modifier = Modifier
                    .padding(end = 16.dp, bottom = 80.dp)
                    .size(44.dp)
                    .background(Color(0xCCF59E0B), CircleShape)
                    .clickable { expanded = true },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.BugReport,
                    contentDescription = "Debug",
                    tint     = Color.Black,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}
