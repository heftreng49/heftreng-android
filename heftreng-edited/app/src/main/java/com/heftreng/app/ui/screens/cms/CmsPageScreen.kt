package com.heftreng.app.ui.screens.cms

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.google.firebase.firestore.FirebaseFirestore
import com.heftreng.app.ui.theme.*
import kotlinx.coroutines.tasks.await

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CmsPageScreen(
    navController: NavController,
    slug         : String,
) {
    var title   by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var error   by remember { mutableStateOf(false) }

    LaunchedEffect(slug) {
        try {
            val db   = FirebaseFirestore.getInstance()
            val snap = db.collection("cms_pages")
                .whereEqualTo("slug", slug)
                .limit(1)
                .get().await()

            if (!snap.isEmpty) {
                val doc = snap.documents.first()
                title   = doc.getString("title")   ?: slug
                content = doc.getString("content") ?: ""
            } else {
                error = true
            }
        } catch (e: Exception) {
            error = true
        } finally {
            loading = false
        }
    }

    Scaffold(
        containerColor = Background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        title.ifBlank { slug },
                        color      = OnBackground,
                        fontSize   = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = OnBackground)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Background),
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.TopStart,
        ) {
            when {
                loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Primary)
                    }
                }
                error -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Sayfa bulunamadı", color = Muted, fontSize = 15.sp)
                            Spacer(Modifier.height(8.dp))
                            Text(slug, color = Muted, fontSize = 12.sp)
                        }
                    }
                }
                else -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                    ) {
                        if (title.isNotBlank()) {
                            Text(
                                title,
                                color      = OnBackground,
                                fontSize   = 20.sp,
                                fontWeight = FontWeight.Bold,
                                lineHeight = 28.sp,
                            )
                            Spacer(Modifier.height(12.dp))
                            HorizontalDivider(color = Divider)
                            Spacer(Modifier.height(16.dp))
                        }
                        Text(
                            content,
                            color      = OnSurface,
                            fontSize   = 15.sp,
                            lineHeight = 24.sp,
                        )
                    }
                }
            }
        }
    }
}
