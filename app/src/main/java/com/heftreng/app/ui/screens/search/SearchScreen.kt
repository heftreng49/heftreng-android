package com.heftreng.app.ui.screens.search

import androidx.compose.foundation.layout.Box

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.heftreng.app.data.model.User
import com.heftreng.app.navigation.Screen
import com.heftreng.app.ui.theme.*
import com.heftreng.app.viewmodel.SearchResult
import com.heftreng.app.viewmodel.SearchViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    navController: NavController,
    language     : String = "tr",
    vm           : SearchViewModel = hiltViewModel(),
) {
    val results        by vm.results.collectAsState()
    val searchResults  by vm.searchResults.collectAsState()
    val suggestions    by vm.suggestions.collectAsState()
    val loading        by vm.loading.collectAsState()
    val activeTab      by vm.activeTab.collectAsState()

    var query          by remember { mutableStateOf("") }
    val focusManager   = LocalFocusManager.current

    val ku = language == "ku"
    val tabs = listOf(
        Triple(if (ku) "Hemû"      else "Hepsi",   Icons.Outlined.Search,        0),
        Triple(if (ku) "Kes"       else "Kişi",    Icons.Outlined.PersonOutline, 1),
        Triple(if (ku) "Nivîs"     else "Gönderi", Icons.Outlined.DynamicFeed,   2),
        Triple(if (ku) "Rêzik"     else "Seri",    Icons.Outlined.AutoStories,   3),
        Triple(if (ku) "Pirtûk"    else "Kitap",   Icons.Outlined.MenuBook,      4),
        Triple(if (ku) "Gotinên"   else "Alıntı",  Icons.Outlined.FormatQuote,   5),
    )

    LaunchedEffect(Unit) { vm.loadSuggestions() }

    Scaffold(
        containerColor = Background,
        topBar = {
            TopAppBar(
                title = {
                    OutlinedTextField(
                        value         = query,
                        onValueChange = { query = it; if (it.length >= 2) vm.search(it) else if (it.isEmpty()) vm.search("") },
                        placeholder   = { Text(if (language == "ku") "Bikarhêner, nivîs, pirtûk..." else "Kullanıcı, gönderi, kitap...", color = Muted, fontSize = 13.sp) },
                        singleLine    = true,
                        modifier      = Modifier.fillMaxWidth(),
                        shape         = RoundedCornerShape(24.dp),
                        colors        = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor      = Amber,
                            unfocusedBorderColor    = Divider,
                            focusedTextColor        = OnBackground,
                            unfocusedTextColor      = OnBackground,
                            unfocusedContainerColor = SurfaceVar,
                            focusedContainerColor   = SurfaceVar,
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = {
                            focusManager.clearFocus()
                            vm.search(query)
                        }),
                        leadingIcon  = { Icon(Icons.Default.Search, null, tint = Muted) },
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { query = ""; vm.search("") }) {
                                    Icon(Icons.Default.Close, null, tint = Muted)
                                }
                            }
                        },
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Background),
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // Sekmeler — sadece arama yapılınca görünür
            if (query.length >= 2) {
                TabRow(
                    selectedTabIndex = activeTab,
                    containerColor   = Background,
                    contentColor     = Amber,
                    indicator = { positions ->
                        Box(Modifier.tabIndicatorOffset(positions[activeTab]).height(2.dp).background(Amber))
                    }
                ) {
                    tabs.forEach { (label, icon, idx) ->
                        Tab(
                            selected = activeTab == idx,
                            onClick  = { vm.setTab(idx) },
                            selectedContentColor   = Amber,
                            unselectedContentColor = Muted,
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Icon(icon, null, modifier = Modifier.size(14.dp))
                                Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }

            LazyColumn(
                modifier       = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 80.dp),
            ) {
                if (query.length >= 2) {
                    // Arama sonuçları — sekmeye göre filtrele
                    val filtered = when (activeTab) {
                        1    -> searchResults.filter { it.type == "user" }
                        2    -> searchResults.filter { it.type == "post" }
                        3    -> searchResults.filter { it.type == "serial" }
                        4    -> searchResults.filter { it.type == "book" }
                        5    -> searchResults.filter { it.type == "author" || it.type == "book_quote" }
                        else -> searchResults
                    }

                    if (loading) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = Amber, modifier = Modifier.size(28.dp))
                            }
                        }
                    } else if (filtered.isEmpty()) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Outlined.SearchOff, null, tint = Muted, modifier = Modifier.size(40.dp))
                                    Spacer(Modifier.height(8.dp))
                                    Text(if (ku) "Encam nehat dîtin" else "Sonuç bulunamadı", color = Muted)
                                }
                            }
                        }
                    } else {
                        items(filtered, key = { it.type + it.id }) { result ->
                            SearchResultRow(result, onClick = {
                                when (result.type) {
                                    "user"   -> navController.navigate("profile/${result.uid}")
                                    "post"   -> navController.navigate(Screen.PostDetail.go(result.id))
                                    "serial" -> navController.navigate(Screen.SerialDetail.go(result.id))
                                    "book"       -> navController.navigate("book/${result.id}")
                                    "author"     -> navController.navigate("author_quotes/${java.net.URLEncoder.encode(result.id, "UTF-8")}")
                                    "book_quote" -> navController.navigate("book_quotes/${java.net.URLEncoder.encode(result.id, "UTF-8")}")
                                }
                            })
                            HorizontalDivider(color = Divider, thickness = 0.5.dp)
                        }
                    }
                } else {
                    // Öneri listesi
                    if (suggestions.isNotEmpty()) {
                        item {
                            Text(
                                "Önerilen Kişiler",
                                color      = Amber,
                                fontWeight = FontWeight.Bold,
                                fontSize   = 12.sp,
                                modifier   = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            )
                        }
                        items(suggestions, key = { it.uid }) { user ->
                            SuggestionRow(
                                user    = user,
                                onClick = { navController.navigate("profile/${user.uid}") },
                                onFollow = { vm.toggleFollow(user.uid) },
                            )
                            HorizontalDivider(color = Divider, thickness = 0.5.dp)
                        }
                    }
                }
            }
        }
    }
}

// ── Ortak arama sonucu satırı ─────────────────────────────────────────────────
@Composable
private fun SearchResultRow(result: SearchResult, onClick: () -> Unit) {
    val (typeIcon, typeColor) = when (result.type) {
        "post"   -> Icons.Outlined.DynamicFeed to Primary
        "serial" -> Icons.Outlined.AutoStories  to Color(0xFF8B5CF6)
        else     -> Icons.Outlined.PersonOutline to Amber
    }
    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier         = Modifier.size(44.dp).clip(if (result.type == "user") CircleShape else RoundedCornerShape(10.dp)).background(SurfaceVar),
            contentAlignment = Alignment.Center,
        ) {
            if (result.imageUrl.isNotBlank()) {
                AsyncImage(
                    model              = result.imageUrl,
                    contentDescription = null,
                    modifier           = Modifier.fillMaxSize(),
                    contentScale       = ContentScale.Crop,
                )
            } else {
                Icon(typeIcon, null, tint = typeColor, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(result.title, fontWeight = FontWeight.SemiBold, color = OnBackground, fontSize = 14.sp,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (result.subtitle.isNotBlank())
                Text(result.subtitle, color = Muted, fontSize = 12.sp, maxLines = 1)
        }
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = typeColor.copy(alpha = 0.12f),
        ) {
            Text(
                when (result.type) { "post" -> "Gönderi"; "serial" -> "Seri"; "book" -> "Kitap"; "author" -> "Yazar"; "book_quote" -> "Kitap Alıntısı"; else -> "Kişi" },
                color    = typeColor,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun SuggestionRow(user: User, onClick: () -> Unit, onFollow: () -> Unit) {
    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model              = user.photoURL.ifEmpty { null },
            contentDescription = null,
            modifier           = Modifier.size(44.dp).clip(CircleShape).background(SurfaceVar),
            contentScale       = ContentScale.Crop,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(user.displayName.ifBlank { "Bênas" }, fontWeight = FontWeight.SemiBold, color = OnBackground, fontSize = 14.sp)
            if (user.username.isNotBlank())
                Text("@${user.username}", color = Muted, fontSize = 12.sp)
        }
        Button(
            onClick        = onFollow,
            shape          = RoundedCornerShape(20.dp),
            colors         = ButtonDefaults.buttonColors(containerColor = Primary, contentColor = Color.White),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
            modifier       = Modifier.height(34.dp),
        ) {
            Text("Şopîne", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
