package com.heftreng.app.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import coil.compose.AsyncImage
import com.heftreng.app.ui.screens.auth.AuthScreen
import com.heftreng.app.ui.screens.feed.FeedScreen
import com.heftreng.app.ui.screens.kurdi.KurdiScreen
import com.heftreng.app.ui.screens.messages.ConversationsScreen
import com.heftreng.app.ui.screens.messages.MessageDetailScreen
import com.heftreng.app.ui.screens.notifications.NotificationsScreen
import com.heftreng.app.ui.screens.post.SinglePostScreen
import com.heftreng.app.ui.screens.profile.EditProfileScreen
import com.heftreng.app.ui.screens.profile.ProfileScreen
import com.heftreng.app.ui.screens.settings.SettingsScreen
import com.heftreng.app.ui.screens.admin.AdminScreen
import com.heftreng.app.ui.screens.search.SearchScreen
import com.heftreng.app.ui.screens.readinglist.ReadingListScreen
import com.heftreng.app.ui.screens.serials.ChapterReadScreen
import com.heftreng.app.ui.screens.serials.SerialDetailScreen
import com.heftreng.app.ui.screens.serials.SerialsScreen
import com.heftreng.app.ui.theme.*
import com.heftreng.app.viewmodel.AuthViewModel
import com.heftreng.app.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch

// ── Routes ────────────────────────────────────────────────────────────────────

sealed class Screen(val route: String) {
    object Auth          : Screen("auth")
    object Feed          : Screen("feed")
    object Kurdi         : Screen("kurdi")
    object Messages      : Screen("messages")
    object MessageDetail : Screen("message/{convId}") { fun go(id: String) = "message/$id" }
    object Notifications : Screen("notifications")
    object Profile       : Screen("profile/{uid}")   { fun go(uid: String) = "profile/$uid" }
    object EditProfile   : Screen("edit_profile")
    object PostDetail    : Screen("post/{postId}")   { fun go(id: String)  = "post/$id" }
    object Settings      : Screen("settings")
}

data class BottomNavItem(
    val route       : String,
    val label       : String,
    val icon        : ImageVector,
    val iconSelected: ImageVector,
)

val bottomNavItems = listOf(
    BottomNavItem(Screen.Feed.route,          "Nivîs",    Icons.Outlined.DynamicFeed,      Icons.Filled.DynamicFeed),
    BottomNavItem("search",                   "Bigere",   Icons.Outlined.Search,            Icons.Filled.Search),
    BottomNavItem(Screen.Messages.route,      "Peyam",    Icons.Outlined.ChatBubbleOutline, Icons.Filled.ChatBubble),
    BottomNavItem(Screen.Notifications.route, "Agahdarî", Icons.Outlined.NotificationsNone, Icons.Filled.Notifications),
    BottomNavItem("profile/me",               "Profîl",   Icons.Outlined.PersonOutline,     Icons.Filled.Person),
)

private val bottomNavRoutes = setOf(
    Screen.Feed.route,
    "search",
    Screen.Messages.route,
    Screen.Notifications.route,
    "profile/me",
)

// ── NavHost ───────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HeftrangNavHost() {
    val navController             = rememberNavController()
    val authVm   : AuthViewModel  = hiltViewModel()
    val settingsVm: SettingsViewModel = hiltViewModel()
    val currentUser by authVm.currentUser.collectAsState()
    val isDark      by settingsVm.darkMode.collectAsState()
    val language    by settingsVm.language.collectAsState()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope       = rememberCoroutineScope()

    // Auth kontrolü tema dışında — route graph her zaman tam oluşur
    if (currentUser == null) {
        HeftrangTheme(darkMode = isDark) {
            AuthScreen(onAuthSuccess = {
                navController.navigate(Screen.Feed.route) {
                    popUpTo(Screen.Auth.route) { inclusive = true }
                }
            })
        }
        return
    }

    HeftrangTheme(darkMode = isDark) {

        val navBackStack by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStack?.destination?.route
        val showBottom   = currentRoute in bottomNavRoutes

        ModalNavigationDrawer(
            drawerState   = drawerState,
            drawerContent = {
                DrawerContent(
                    user         = currentUser,
                    currentRoute = currentRoute,
                    isDark       = isDark,
                    language     = language,
                    onToggleDark = { settingsVm.toggleDarkMode() },
                    onSetLang    = { settingsVm.setLanguage(it) },
                    onNavigate   = { route ->
                        scope.launch { drawerState.close() }
                        navController.navigate(route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState    = true
                        }
                    },
                    onSignOut = {
                        scope.launch { drawerState.close() }
                        authVm.signOut()
                    },
                )
            },
        ) {
            Scaffold(
                containerColor = Background,
                bottomBar = {
                    if (showBottom) {
                        NavigationBar(containerColor = Background, tonalElevation = 0.dp) {
                            bottomNavItems.forEach { item ->
                                val selected = currentRoute == item.route
                                NavigationBarItem(
                                    selected = selected,
                                    onClick  = {
                                        navController.navigate(item.route) {
                                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                            launchSingleTop = true
                                            restoreState    = true
                                        }
                                    },
                                    icon   = { Icon(if (selected) item.iconSelected else item.icon, contentDescription = item.label) },
                                    label  = { Text(item.label, fontSize = 11.sp) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor   = Amber,
                                        selectedTextColor   = Amber,
                                        unselectedIconColor = Muted,
                                        unselectedTextColor = Muted,
                                        indicatorColor      = Background,
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
                    composable(Screen.Feed.route) {
                        FeedScreen(
                            navController = navController,
                            onOpenDrawer  = { scope.launch { drawerState.open() } },
                        )
                    }
                    composable(Screen.Kurdi.route)    { KurdiScreen() }
                    composable(Screen.Messages.route) { ConversationsScreen(navController) }
                    composable("message/{convId}") { back ->
                        MessageDetailScreen(
                            convId        = back.arguments?.getString("convId") ?: "",
                            navController = navController,
                        )
                    }
                    composable(Screen.Notifications.route) { NotificationsScreen(navController) }
                    composable("profile/{uid}") { back ->
                        ProfileScreen(
                            uid           = back.arguments?.getString("uid") ?: "me",
                            navController = navController,
                        )
                    }
                    composable(Screen.EditProfile.route) { EditProfileScreen(navController) }
                    composable(Screen.PostDetail.route) { back ->
                        SinglePostScreen(
                            postId        = back.arguments?.getString("postId") ?: "",
                            navController = navController,
                        )
                    }
                    
                    composable("search") { SearchScreen(navController) }
                    composable("admin")  { AdminScreen(navController)  }
                    composable(Screen.Settings.route) {
                        SettingsScreen(navController = navController)
                    }
                }
            }
        }
    }
}

// ── DrawerContent ─────────────────────────────────────────────────────────────

@Composable
private fun DrawerContent(
    user         : com.google.firebase.auth.FirebaseUser?,
    currentRoute : String?,
    isDark       : Boolean,
    language     : String,
    onToggleDark : () -> Unit,
    onSetLang    : (String) -> Unit,
    onNavigate   : (String) -> Unit,
    onSignOut    : () -> Unit,
) {
    ModalDrawerSheet(
        drawerContainerColor = HeftSurface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(horizontal = 16.dp, vertical = 24.dp),
        ) {
            // Profil özeti
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model             = user?.photoUrl,
                    contentDescription = "Profil",
                    contentScale      = ContentScale.Crop,
                    modifier          = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(SurfaceVar),
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        user?.displayName ?: "heftreng",
                        fontWeight = FontWeight.Bold,
                        color      = OnBackground,
                        fontSize   = 16.sp,
                    )
                    Text(
                        user?.email ?: "",
                        color    = Muted,
                        fontSize = 12.sp,
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            HorizontalDivider(color = Divider)
            Spacer(Modifier.height(16.dp))

            // Navigasyon öğeleri
            val navItems = listOf(
                Triple(Icons.Outlined.Home,             "Feed / Nivîs",           Screen.Feed.route),
                Triple(Icons.Outlined.Person,           "Profil / Profîl",         "profile/me"),
                Triple(Icons.Outlined.Translate,        "Kurdî Fêrbibe",           "kurdi"),
                Triple(Icons.Outlined.AutoStories,      "Seriler",                 "serials"),
                Triple(Icons.Outlined.Settings,         "Ayarlar / Mîheng",        Screen.Settings.route),
            )
            navItems.forEach { (icon, label, route) ->
                val selected = currentRoute == route
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (selected) Primary.copy(alpha = 0.15f) else Color.Transparent)
                        .clickable { onNavigate(route) }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(icon, null, tint = if (selected) Primary else Muted, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(
                        label,
                        color      = if (selected) Primary else OnBackground,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        fontSize   = 14.sp,
                    )
                }
                Spacer(Modifier.height(2.dp))
            }

            Spacer(Modifier.weight(1f))
            HorizontalDivider(color = Divider)
            Spacer(Modifier.height(12.dp))

            // Karanlık mod toggle
            Row(
                modifier          = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    if (isDark) Icons.Filled.DarkMode else Icons.Outlined.LightMode,
                    null,
                    tint     = Amber,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    if (isDark) "Karanlık Mod" else "Aydınlık Mod",
                    color    = OnBackground,
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked         = isDark,
                    onCheckedChange = { onToggleDark() },
                    colors          = SwitchDefaults.colors(
                        checkedThumbColor   = Amber,
                        checkedTrackColor   = Amber.copy(alpha = 0.3f),
                        uncheckedThumbColor = Muted,
                        uncheckedTrackColor = Muted.copy(alpha = 0.2f),
                    ),
                )
            }

            Spacer(Modifier.height(8.dp))

            // Çıkış
            TextButton(
                onClick  = onSignOut,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Outlined.Logout, null, tint = Error, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Çıkış / Derketin", color = Error, fontSize = 13.sp)
            }
        }
    }
}
