package com.heftreng.app.ui.screens.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.heftreng.app.data.model.User
import com.heftreng.app.navigation.Screen
import com.heftreng.app.ui.theme.*
import com.heftreng.app.viewmodel.SearchViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    navController: NavController,
    vm           : SearchViewModel = hiltViewModel(),
) {
    val results     by vm.results.collectAsState()
    val suggestions by vm.suggestions.collectAsState()
    val loading     by vm.loading.collectAsState()

    var query        by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val focusManager   = LocalFocusManager.current

    LaunchedEffect(Unit) { vm.loadSuggestions() }

    Scaffold(
        containerColor = Background,
        topBar = {
            TopAppBar(
                title = {
                    OutlinedTextField(
                        value         = query,
                        onValueChange = { query = it; vm.search(it) },
                        placeholder   = { Text("Bikarhêner bigere…", color = Muted, fontSize = 14.sp) },
                        singleLine    = true,
                        modifier      = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
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
        LazyColumn(
            modifier       = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 80.dp),
        ) {
            // Arama sonuçları
            if (query.isNotEmpty()) {
                if (loading) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Amber, modifier = Modifier.size(28.dp))
                        }
                    }
                } else if (results.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Outlined.SearchOff, null, tint = Muted, modifier = Modifier.size(40.dp))
                                Spacer(Modifier.height(8.dp))
                                Text("Sonuç bulunamadı", color = Muted)
                            }
                        }
                    }
                } else {
                    items(results, key = { it.uid }) { user ->
                        UserRow(user, onClick = {
                            navController.navigate("profile/${user.uid}")
                        })
                        HorizontalDivider(color = Divider, thickness = 0.5.dp)
                    }
                }
            } else {
                // Takip önerileri
                if (suggestions.isNotEmpty()) {
                    item {
                        Text(
                            "Önerilenler",
                            color      = Amber,
                            fontWeight = FontWeight.Bold,
                            fontSize   = 13.sp,
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

@Composable
private fun UserRow(user: User, onClick: () -> Unit) {
    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
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
            if (user.bio.isNotBlank())
                Text(user.bio, color = Muted, fontSize = 12.sp, maxLines = 1)
        }
        Icon(Icons.Default.ChevronRight, null, tint = Muted)
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
            onClick = onFollow,
            shape   = RoundedCornerShape(20.dp),
            colors  = ButtonDefaults.buttonColors(containerColor = Primary, contentColor = Color.White),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
            modifier = Modifier.height(34.dp),
        ) {
            Text("Şopîne", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
