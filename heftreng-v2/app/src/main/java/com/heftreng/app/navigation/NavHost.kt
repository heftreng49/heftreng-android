package com.heftreng.app.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.*
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import coil.compose.AsyncImage
import com.heftreng.app.ui.screens.auth.AuthScreen
import com.heftreng.app.ui.screens.books.BooksScreen
import com.heftreng.app.ui.screens.feed.FeedScreen
import com.heftreng.app.ui.screens.feed.PostDetailScreen
import com.heftreng.app.ui.screens.kurdi.KurdiScreen
import com.heftreng.app.ui.screens.kurdi.LessonDetailScreen
import com.heftreng.app.ui.screens.messages.ConversationsScreen
import com.heftreng.app.ui.screens.messages.MessageDetailScreen
import com.heftreng.app.ui.screens.notifications.NotificationsScreen
import com.heftreng.app.ui.screens.profile.EditProfileScreen
import com.heftreng.app.ui.screens.profile.ProfileScreen
import com.heftreng.app.ui.theme.*
import com.heftreng.app.viewmodel.AuthViewModel
import com.heftreng.app.viewmodel.FeedViewModel
import com.heftreng.app.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch

// ── Rotalar ──────────────────────────────────────────────────
sealed class Screen(val route: String) {
    object Auth          : Screen("auth")
    object Feed          : Screen("feed")
    object Kurdi         : Screen("kurdi")
    object Books         : Screen("books")
    object Messages      : Screen("messages")
    object Notifications : Screen("notifications")
    object Profile       : Screen("profile/{uid}")        { fun go(uid: String) = "profile/$uid" }
    object EditProfile   : Screen("edit_profile")
    object PostDetail    : Screen("post/{postId}")        { fun go(id: String)  = "post/$id"     }
    object LessonDetail  : Screen("lesson/{lessonId}")    { fun go(id: String)  = "lesson/$id"   }
    object MessageDetail : Screen("message/{convId}")     { fun go(id: String)  = "message/$id"  }
}

// ── Alt navigasyon öğeleri ────────────────────────────────────
data class BottomNavItem(
    val route       : String,
    val label       : String,
    val icon        : ImageVector,
    val iconSelected: ImageVector,
)

val bottomNavItems = listOf(
    BottomNavItem(Screen.Feed.route,          "Nivîs",    Icons.Outlined.DynamicFeed,      Icons.Filled.DynamicFeed),
    BottomNavItem(Screen.Kurdi.route,         "Kurdî",    Icons.Outlined.Translate,         Icons.Filled.Translate),
    BottomNavItem(Screen.Messages.route,      "Peyam",    Icons.Outlined.ChatBubbleOutline, Icons.Filled.ChatBubble),
    BottomNavItem(Screen.Notifications.route, "Agahdarî", Icons.Outlined.NotificationsNone, Icons.Filled.Notifications),
    BottomNavItem("profile/me",               "Profîl",   Icons.Outlined.PersonOutline,     Icons.Filled.Person),
)

private val bottomRoutes = setOf(
    Screen.Feed.route,
    Screen.Kurdi.route,
    Screen.Books.route,
    Screen.Messages.route,
    Screen.Notifications.route,
    "profile/me",
)

// ════════════════════════════════════════════════════════════
//  ANA NAVHOST
// ════════════════════════════════════════════════════════════
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HeftrangNavHost(
    settingsVm: SettingsViewModel = hiltViewModel(),
) {
    val navController         = rememberNavController()
    val authVm: AuthViewModel = hiltViewModel()
    val feedVm: FeedViewModel = hiltViewModel()
    val currentUser           by authVm.currentUser.collectAsState()
    val darkMode              by settingsVm.darkMode.collectAsState()
    val accentColor           by settingsVm.accent.collectAsState()
    val fontSize              by settingsVm.fontSize.collectAsState()
    val drawerState           = rememberDrawerState(DrawerValue.Closed)
    val scope                 = rememberCoroutineScope()

    // Giriş yapılmamışsa Auth ekranı
    if (currentUser == null) {
        AuthScreen(onAuthSuccess = {
            navController.navigate(Screen.Feed.route) {
                popUpTo(Screen.Auth.route) { inclusive = true }
            }
        })
        return
    }

    val navBackStack by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStack?.destination?.route
    val showBottom   = currentRoute in bottomRoutes

    ModalNavigationDrawer(
        drawerState   = drawerState,
        drawerContent = {
            DrawerContent(
                user        = currentUser,
                currentRoute= currentRoute,
                darkMode    = darkMode,
                accentColor = accentColor,
                fontSize    = fontSize,
                onNavigate  = { route ->
                    scope.launch { drawerState.close() }
                    navController.navigate(route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState    = true
                    }
                },
                onToggleDark= { settingsVm.toggleDarkMode() },
                onFontSize  = { settingsVm.setFontSize(it) },
                onAccent    = { settingsVm.setAccent(it) },
                onSignOut   = {
                    scope.launch { drawerState.close() }
                    authVm.signOut()
                },
            )
        },
    ) {
        Scaffold(
            containerColor = bg(),
            bottomBar = {
                if (showBottom) {
                    NavigationBar(containerColor = bg(), tonalElevation = 0.dp) {
                        bottomNavItems.forEach { item ->
                            val selected = currentRoute == item.route
                            NavigationBarItem(
                                selected = selected,
                                onClick  = {
                                    navController.navigate(item.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState    = true
                                    }
                                },
                                icon   = {
                                    Icon(
                                        if (selected) item.iconSelected else item.icon,
                                        contentDescription = item.label,
                                    )
                                },
                                label  = { Text(item.label, fontSize = 10.sp) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor   = accentColor,
                                    selectedTextColor   = accentColor,
                                    unselectedIconColor = muted(),
                                    unselectedTextColor = muted(),
                                    indicatorColor      = bg(),
                                ),
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            NavHost(
                navController    = navController,
                startDestination = Screen.Feed.route,
                modifier         = Modifier.padding(innerPadding),
            ) {
                // Feed
                composable(Screen.Feed.route) {
                    FeedScreen(
                        navController = navController,
                        onOpenDrawer  = { scope.launch { drawerState.open() } },
                        vm            = feedVm,
                    )
                }
                // Post detay
                composable(
                    route     = Screen.PostDetail.route,
                    arguments = listOf(navArgument("postId") { type = NavType.StringType }),
                ) { back ->
                    PostDetailScreen(
                        navController = navController,
                        viewModel     = feedVm,
                        postId        = back.arguments?.getString("postId") ?: "",
                    )
                }
                // Kurdî
                composable(Screen.Kurdi.route) {
                    KurdiScreen(navController = navController)
                }
                // Ders detay
                composable(
                    route     = Screen.LessonDetail.route,
                    arguments = listOf(navArgument("lessonId") { type = NavType.StringType }),
                ) { back ->
                    LessonDetailScreen(
                        lessonId      = back.arguments?.getString("lessonId") ?: "",
                        navController = navController,
                    )
                }
                // Kitaplar
                composable(Screen.Books.route) {
                    BooksScreen()
                }
                // Mesajlar
                composable(Screen.Messages.route) {
                    ConversationsScreen(navController = navController)
                }
                // Mesaj detay
                composable(
                    route     = Screen.MessageDetail.route,
                    arguments = listOf(navArgument("convId") { type = NavType.StringType }),
                ) { back ->
                    MessageDetailScreen(
                        convId        = back.arguments?.getString("convId") ?: "",
                        navController = navController,
                    )
                }
                // Bildirimler
                composable(Screen.Notifications.route) {
                    NotificationsScreen(navController = navController)
                }
                // Profil (uid veya "me")
                composable(
                    route     = Screen.Profile.route,
                    arguments = listOf(navArgument("uid") { type = NavType.StringType }),
                ) { back ->
                    ProfileScreen(
                        uid           = back.arguments?.getString("uid") ?: "me",
                        navController = navController,
                    )
                }
                // Profil düzenle
                composable(Screen.EditProfile.route) {
                    EditProfileScreen(navController = navController)
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════
//  DRAWER İÇERİĞİ
// ════════════════════════════════════════════════════════════
@Composable
private fun DrawerContent(
    user        : com.google.firebase.auth.FirebaseUser?,
    currentRoute: String?,
    darkMode    : Boolean,
    accentColor : Color,
    fontSize    : Int,
    onNavigate  : (String) -> Unit,
    onToggleDark: () -> Unit,
    onFontSize  : (Int) -> Unit,
    onAccent    : (Color) -> Unit,
    onSignOut   : () -> Unit,
) {
    val scroll = rememberScrollState()

    ModalDrawerSheet(
        drawerContainerColor = surf(),
        drawerShape          = RoundedCornerShape(topEnd = 20.dp, bottomEnd = 20.dp),
        modifier             = Modifier.fillMaxHeight().widthIn(max = 300.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(bottom = 24.dp),
        ) {
            Spacer(Modifier.height(44.dp))

            // ── Kullanıcı özeti ─────────────────────────
            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                Box(
                    modifier         = Modifier.size(64.dp).clip(CircleShape).background(surfVar()),
                    contentAlignment = Alignment.Center,
                ) {
                    if (user?.photoUrl != null) {
                        AsyncImage(
                            model              = user.photoUrl,
                            contentDescription = user.displayName,
                            modifier           = Modifier.fillMaxSize(),
                            contentScale       = ContentScale.Crop,
                        )
                    } else {
                        Text(
                            user?.displayName?.firstOrNull()?.uppercase() ?: "H",
                            color = accentColor, fontWeight = FontWeight.Bold, fontSize = 22.sp,
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(user?.displayName ?: "", fontWeight = FontWeight.Bold, color = onBg(), fontSize = 16.sp)
                if (!user?.email.isNullOrBlank())
                    Text(user?.email ?: "", color = muted(), fontSize = 12.sp)
            }

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                color    = divider(),
            )

            // ── Sayfalar ────────────────────────────────
            DrawerLabel("Rûpel / Sayfalar", Icons.Default.Apps)

            listOf(
                Triple(Screen.Feed.route,          "Nivîs / Feed",    Icons.Outlined.DynamicFeed),
                Triple(Screen.Kurdi.route,         "Kurdî Fêrbibe",   Icons.Outlined.Translate),
                Triple(Screen.Books.route,         "Kitêbxane",       Icons.Outlined.MenuBook),
                Triple(Screen.Messages.route,      "Peyam",           Icons.Outlined.ChatBubbleOutline),
                Triple(Screen.Notifications.route, "Agahdarî",        Icons.Outlined.NotificationsNone),
                Triple("profile/me",               "Profîla Min",     Icons.Outlined.PersonOutline),
            ).forEach { (route, label, icon) ->
                val sel = currentRoute == route
                NavigationDrawerItem(
                    label    = {
                        Text(
                            label,
                            fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal,
                            fontSize   = 14.sp,
                        )
                    },
                    icon     = { Icon(icon, label, modifier = Modifier.size(20.dp)) },
                    selected = sel,
                    onClick  = { onNavigate(route) },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 1.dp),
                    colors   = NavigationDrawerItemDefaults.colors(
                        selectedContainerColor   = accentColor.copy(alpha = 0.15f),
                        unselectedContainerColor = Color.Transparent,
                        selectedIconColor        = accentColor,
                        selectedTextColor        = accentColor,
                        unselectedIconColor      = muted(),
                        unselectedTextColor      = onBg(),
                    ),
                )
            }

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                color    = divider(),
            )

            // ── Görünüm Ayarları ─────────────────────────
            DrawerLabel("Mîheng / Ayarlar", Icons.Default.Tune)

            // Karanlık / Açık Mod
            DrawerRow(
                label   = if (darkMode) "Moda Tarî / Karanlık" else "Moda Ronahî / Açık",
                icon    = Icons.Default.DarkMode,
            ) {
                Switch(
                    checked         = darkMode,
                    onCheckedChange = { onToggleDark() },
                    colors          = SwitchDefaults.colors(
                        checkedThumbColor   = Color.Black,
                        checkedTrackColor   = accentColor,
                        uncheckedThumbColor = Color.White,
                        uncheckedTrackColor = surfVar(),
                    ),
                    modifier = Modifier.height(24.dp),
                )
            }

            // Yazı boyutu
            DrawerRow(label = "Tîp / Yazı Boyutu", icon = Icons.Default.FormatSize) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick  = { onFontSize(fontSize - 1) },
                        modifier = Modifier.size(28.dp),
                    ) {
                        Text("−", color = accentColor, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                    Text(
                        "${fontSize}sp",
                        color    = onBg(),
                        fontSize = 12.sp,
                        modifier = Modifier.width(40.dp),
                        textAlign= TextAlign.Center,
                    )
                    IconButton(
                        onClick  = { onFontSize(fontSize + 1) },
                        modifier = Modifier.size(28.dp),
                    ) {
                        Text("+", color = accentColor, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                color    = divider(),
            )

            // Vurgu rengi seçici
            DrawerLabel("Reng / Tema Rengi", Icons.Default.Palette)
            Row(
                modifier              = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                listOf(
                    Color(0xFFF59E0B), // Amber (orijinal)
                    Color(0xFF6366F1), // Indigo
                    Color(0xFFEF4444), // Kırmızı
                    Color(0xFF10B981), // Yeşil
                    Color(0xFF0EA5E9), // Mavi
                    Color(0xFFEC4899), // Pembe
                    Color(0xFF8B5CF6), // Mor
                ).forEach { color ->
                    val isSelected = accentColor == color
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(color)
                            .clickable { onAccent(color) },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (isSelected) {
                            Icon(
                                Icons.Default.Check, null,
                                tint     = Color.Black,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                color    = divider(),
            )

            // Çıkış
            NavigationDrawerItem(
                label    = { Text("Derketin / Çıkış Yap", color = Color(0xFFEF4444), fontSize = 14.sp) },
                icon     = {
                    Icon(
                        Icons.Default.Logout, "Çıkış",
                        tint     = Color(0xFFEF4444),
                        modifier = Modifier.size(20.dp),
                    )
                },
                selected = false,
                onClick  = onSignOut,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 1.dp),
                colors   = NavigationDrawerItemDefaults.colors(
                    unselectedContainerColor = Color.Transparent,
                ),
            )

            Spacer(Modifier.height(8.dp))

            Text(
                "© 2026 Heftreng",
                color     = muted(),
                fontSize  = 10.sp,
                modifier  = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                textAlign = TextAlign.Center,
            )
        }
    }
}

// ── Drawer yardımcıları ───────────────────────────────────────
@Composable
private fun DrawerLabel(text: String, icon: ImageVector) {
    Row(
        modifier              = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, null, tint = accent(), modifier = Modifier.size(13.dp))
        Text(
            text,
            color        = muted(),
            fontSize     = 10.sp,
            fontWeight   = FontWeight.Bold,
            letterSpacing= 0.8.sp,
        )
    }
}

@Composable
private fun DrawerRow(
    label  : String,
    icon   : ImageVector,
    control: @Composable () -> Unit,
) {
    Row(
        modifier              = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(icon, null, tint = muted(), modifier = Modifier.size(18.dp))
            Text(label, color = onBg(), fontSize = 13.sp)
        }
        control()
    }
}
