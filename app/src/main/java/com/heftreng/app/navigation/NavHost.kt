package com.heftreng.app.navigation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
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
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import coil.compose.AsyncImage
import com.heftreng.app.ui.screens.auth.AuthScreen
import com.heftreng.app.ui.screens.feed.FeedScreen
import com.heftreng.app.ui.screens.feed.PostDetailScreen
import com.heftreng.app.ui.screens.kurdi.KurdiScreen
import com.heftreng.app.ui.screens.kurdi.LessonDetailScreen
import com.heftreng.app.ui.screens.messages.ConversationsScreen
import com.heftreng.app.ui.screens.messages.MessageDetailScreen
import com.heftreng.app.ui.screens.notifications.NotificationsScreen
import com.heftreng.app.ui.screens.profile.EditProfileScreen
import com.heftreng.app.ui.screens.profile.ProfileScreen
import com.heftreng.app.ui.screens.settings.SettingsScreen
import com.heftreng.app.ui.theme.*
import com.heftreng.app.viewmodel.AuthViewModel
import com.heftreng.app.viewmodel.FeedViewModel
import kotlinx.coroutines.launch

// ── Global uygulama tercihleri (Compose state) ────────────────────────────────
object AppPrefs {
    var darkMode    by mutableStateOf(true)
    var accentColor by mutableStateOf(Amber)
    var fontSize    by mutableStateOf(15)
    var language    by mutableStateOf("tr")   // "tr" | "ku"
}

// ── Routes ────────────────────────────────────────────────────────────────────
sealed class Screen(val route: String) {
    object Auth          : Screen("auth")
    object Feed          : Screen("feed")
    object Kurdi         : Screen("kurdi")
    object Messages      : Screen("messages")
    object MessageDetail : Screen("message/{convId}")      { fun go(id: String)  = "message/$id" }
    object Notifications : Screen("notifications")
    object Profile       : Screen("profile/{uid}")         { fun go(uid: String) = "profile/$uid" }
    object EditProfile   : Screen("edit_profile")
    object LessonDetail  : Screen("lesson/{lessonId}")     { fun go(id: String)  = "lesson/$id" }
    object PostDetail    : Screen("post_detail/{postId}")  { fun go(id: String)  = "post_detail/$id" }
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
    BottomNavItem(Screen.Kurdi.route,         "Kurdî",    Icons.Outlined.Translate,         Icons.Filled.Translate),
    BottomNavItem(Screen.Messages.route,      "Peyam",    Icons.Outlined.ChatBubbleOutline, Icons.Filled.ChatBubble),
    BottomNavItem(Screen.Notifications.route, "Agahdarî", Icons.Outlined.NotificationsNone, Icons.Filled.Notifications),
    BottomNavItem("profile/me",               "Profîl",   Icons.Outlined.PersonOutline,     Icons.Filled.Person),
)

private val bottomNavRoutes = setOf(
    Screen.Feed.route, Screen.Kurdi.route, Screen.Messages.route,
    Screen.Notifications.route, "profile/me",
)

// ── NavHost ───────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HeftrangNavHost() {
    val navController         = rememberNavController()
    val authVm: AuthViewModel = hiltViewModel()
    val feedVm: FeedViewModel = hiltViewModel()
    val currentUser           by authVm.currentUser.collectAsState()
    val drawerState           = rememberDrawerState(DrawerValue.Closed)
    val scope                 = rememberCoroutineScope()

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
    val showBottom   = currentRoute in bottomNavRoutes

    ModalNavigationDrawer(
        drawerState   = drawerState,
        drawerContent = {
            DrawerContent(
                user         = currentUser,
                currentRoute = currentRoute,
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
                                    selectedIconColor   = AppPrefs.accentColor,
                                    selectedTextColor   = AppPrefs.accentColor,
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
                    FeedScreen(navController = navController, onOpenDrawer = { scope.launch { drawerState.open() } })
                }
                composable(
                    route     = Screen.PostDetail.route,
                    arguments = listOf(navArgument("postId") { type = NavType.StringType })
                ) { back ->
                    PostDetailScreen(
                        navController = navController,
                        viewModel     = feedVm,
                        postId        = back.arguments?.getString("postId") ?: "",
                    )
                }
                composable(Screen.Kurdi.route) { KurdiScreen(navController = navController) }
                composable("lesson/{lessonId}") { back ->
                    LessonDetailScreen(lessonId = back.arguments?.getString("lessonId") ?: "", navController = navController)
                }
                composable(Screen.Messages.route) { ConversationsScreen(navController) }
                composable("message/{convId}") { back ->
                    MessageDetailScreen(convId = back.arguments?.getString("convId") ?: "", navController = navController)
                }
                composable(Screen.Notifications.route) { NotificationsScreen(navController) }
                composable("profile/{uid}") { back ->
                    ProfileScreen(uid = back.arguments?.getString("uid") ?: "me", navController = navController)
                }
                composable(Screen.EditProfile.route) { EditProfileScreen(navController) }
                composable(Screen.Settings.route)    { SettingsScreen(navController) }
            }
        }
    }
}

// ── DrawerContent ─────────────────────────────────────────────────────────────
@Composable
private fun DrawerContent(
    user        : com.google.firebase.auth.FirebaseUser?,
    currentRoute: String?,
    onNavigate  : (String) -> Unit,
    onSignOut   : () -> Unit,
) {
    val scrollState = rememberScrollState()

    ModalDrawerSheet(
        drawerContainerColor = Surface,
        drawerShape          = RoundedCornerShape(topEnd = 20.dp, bottomEnd = 20.dp),
        modifier             = Modifier.fillMaxHeight(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(bottom = 16.dp),
        ) {
            Spacer(Modifier.height(40.dp))

            // Kullanıcı başlığı
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                AsyncImage(
                    model = user?.photoUrl, contentDescription = user?.displayName,
                    modifier     = Modifier.size(64.dp).clip(CircleShape).background(SurfaceVar),
                    contentScale = ContentScale.Crop,
                )
                Spacer(Modifier.height(10.dp))
                Text(user?.displayName ?: "", fontWeight = FontWeight.Bold, color = OnBackground, fontSize = 16.sp)
                if (!user?.email.isNullOrBlank())
                    Text(user?.email ?: "", color = Muted, fontSize = 12.sp)
            }

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp), color = Divider)

            DrawerSectionLabel("Sayfalar / Rûpel", Icons.Default.Apps)

            listOf(
                Triple(Screen.Feed.route,          "Nivîs / Feed",     Icons.Outlined.DynamicFeed),
                Triple(Screen.Kurdi.route,         "Kurdî Fêrbibe",    Icons.Outlined.Translate),
                Triple(Screen.Messages.route,      "Peyam",            Icons.Outlined.ChatBubbleOutline),
                Triple(Screen.Notifications.route, "Agahdarî",         Icons.Outlined.NotificationsNone),
                Triple("profile/me",               "Profîla Min",      Icons.Outlined.PersonOutline),
                Triple(Screen.Settings.route,      "Mîheng / Ayarlar", Icons.Outlined.Settings),
            ).forEach { (route, label, icon) ->
                val sel = currentRoute == route
                NavigationDrawerItem(
                    label    = { Text(label, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal, fontSize = 14.sp) },
                    icon     = { Icon(icon, contentDescription = label, modifier = Modifier.size(20.dp)) },
                    selected = sel,
                    onClick  = { onNavigate(route) },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 1.dp),
                    colors   = NavigationDrawerItemDefaults.colors(
                        selectedContainerColor   = AppPrefs.accentColor.copy(alpha = 0.15f),
                        unselectedContainerColor = Color.Transparent,
                        selectedIconColor        = AppPrefs.accentColor,
                        selectedTextColor        = AppPrefs.accentColor,
                        unselectedIconColor      = Muted,
                        unselectedTextColor      = OnBackground,
                    ),
                )
            }

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), color = Divider)

            // ── Ayarlar bölümü ────────────────────────────────────────────
            DrawerSectionLabel("Mîheng / Ayarlar", Icons.Default.Tune)

            // Karanlık mod
            DrawerSettingRow("Karanlık Mod / Moda Tarî", Icons.Default.DarkMode) {
                Switch(
                    checked         = AppPrefs.darkMode,
                    onCheckedChange = { AppPrefs.darkMode = it },
                    colors          = SwitchDefaults.colors(
                        checkedThumbColor  = Color.Black,
                        checkedTrackColor  = AppPrefs.accentColor,
                        uncheckedThumbColor = Muted,
                        uncheckedTrackColor = SurfaceVar,
                    ),
                    modifier = Modifier.height(24.dp),
                )
            }

            // Yazı boyutu
            DrawerSettingRow("Yazı Boyutu / Mezinahî", Icons.Default.FormatSize) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { if (AppPrefs.fontSize > 12) AppPrefs.fontSize-- }, modifier = Modifier.size(28.dp)) {
                        Text("−", color = AppPrefs.accentColor, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                    Text("${AppPrefs.fontSize}sp", color = OnBackground, fontSize = 11.sp,
                        modifier = Modifier.width(34.dp), textAlign = TextAlign.Center)
                    IconButton(onClick = { if (AppPrefs.fontSize < 22) AppPrefs.fontSize++ }, modifier = Modifier.size(28.dp)) {
                        Text("+", color = AppPrefs.accentColor, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
            }

            // Dil
            DrawerSettingRow("Ziman / Dil", Icons.Default.Translate) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("tr" to "TR", "ku" to "KU").forEach { (code, label) ->
                        val sel = AppPrefs.language == code
                        Button(
                            onClick  = { AppPrefs.language = code },
                            modifier = Modifier.height(28.dp),
                            shape    = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                            colors   = ButtonDefaults.buttonColors(
                                containerColor = if (sel) AppPrefs.accentColor else SurfaceVar,
                                contentColor   = if (sel) Color.Black else Muted,
                            ),
                        ) { Text(label, fontSize = 11.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal) }
                    }
                }
            }

            // Renk seçici
            DrawerSectionLabel("Vurgu Rengi / Reng", Icons.Default.Palette)
            Row(modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(
                    Color(0xFFF59E0B),
                    Color(0xFF6366F1),
                    Color(0xFFEF4444),
                    Color(0xFF10B981),
                    Color(0xFF0EA5E9),
                ).forEach { color ->
                    val selected = AppPrefs.accentColor == color
                    Box(
                        modifier          = Modifier.size(if (selected) 32.dp else 28.dp).clip(CircleShape).background(color).clickable { AppPrefs.accentColor = color },
                        contentAlignment  = Alignment.Center,
                    ) {
                        if (selected) Icon(Icons.Default.Check, null, tint = Color.Black, modifier = Modifier.size(14.dp))
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), color = Divider)

            NavigationDrawerItem(
                label    = { Text("Derketin / Çıkış Yap", color = Color(0xFFEF4444), fontSize = 14.sp) },
                icon     = { Icon(Icons.Default.Logout, null, tint = Color(0xFFEF4444), modifier = Modifier.size(20.dp)) },
                selected = false,
                onClick  = onSignOut,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 1.dp),
                colors   = NavigationDrawerItemDefaults.colors(unselectedContainerColor = Color.Transparent),
            )
            Spacer(Modifier.height(8.dp))
            Text("© 2026 Heftreng", color = Muted, fontSize = 10.sp,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp), textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun DrawerSectionLabel(label: String, icon: ImageVector) {
    Row(
        modifier              = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, null, tint = AppPrefs.accentColor, modifier = Modifier.size(13.dp))
        Text(label, color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
    }
}

@Composable
private fun DrawerSettingRow(label: String, icon: ImageVector, control: @Composable () -> Unit) {
    Row(
        modifier              = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, null, tint = Muted, modifier = Modifier.size(18.dp))
            Text(label, color = OnBackground, fontSize = 13.sp)
        }
        control()
    }
}
