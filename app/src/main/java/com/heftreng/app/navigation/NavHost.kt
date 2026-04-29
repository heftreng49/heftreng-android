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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import coil.compose.AsyncImage
import com.heftreng.app.ui.screens.admin.AdminScreen
import com.heftreng.app.ui.screens.auth.AuthScreen
import com.heftreng.app.ui.screens.feed.FeedScreen
import com.heftreng.app.ui.screens.kurdi.KurdiScreen
import com.heftreng.app.ui.screens.messages.ConversationsScreen
import com.heftreng.app.ui.screens.messages.MessageDetailScreen
import com.heftreng.app.ui.screens.notifications.NotificationsScreen
import com.heftreng.app.ui.screens.feed.PostDetailScreen
import com.heftreng.app.ui.screens.profile.EditProfileScreen
import com.heftreng.app.ui.screens.profile.ProfileScreen
import com.heftreng.app.ui.screens.readinglist.ReadingListScreen
import com.heftreng.app.ui.screens.search.SearchScreen
import com.heftreng.app.ui.screens.serials.ChapterReadScreen
import com.heftreng.app.ui.screens.serials.SerialDetailScreen
import com.heftreng.app.ui.screens.serials.SerialsScreen
import com.heftreng.app.ui.screens.settings.SettingsScreen
import com.heftreng.app.ui.theme.*
import com.heftreng.app.viewmodel.AuthViewModel
import com.heftreng.app.viewmodel.MessagesViewModel
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
    object Admin         : Screen("admin")
    object Serials       : Screen("serials")
    object SerialDetail  : Screen("serial/{serialId}") { fun go(id: String) = "serial/$id" }
    object ReadingList   : Screen("reading_list/{uid}") { fun go(uid: String) = "reading_list/$uid" }
    object Search        : Screen("search")
}

data class BottomNavItem(
    val route       : String,
    val label       : String,
    val labelKu     : String,
    val icon        : ImageVector,
    val iconSelected: ImageVector,
)

val bottomNavItems = listOf(
    BottomNavItem(Screen.Feed.route,          "Feed",        "Nivîs",    Icons.Outlined.DynamicFeed,      Icons.Filled.DynamicFeed),
    BottomNavItem(Screen.Search.route,        "Ara",         "Bigere",   Icons.Outlined.Search,            Icons.Filled.Search),
    BottomNavItem(Screen.Messages.route,      "Mesajlar",    "Peyam",    Icons.Outlined.ChatBubbleOutline, Icons.Filled.ChatBubble),
    BottomNavItem(Screen.Notifications.route, "Bildirimler", "Agahdarî", Icons.Outlined.NotificationsNone, Icons.Filled.Notifications),
    BottomNavItem("profile/me",               "Profil",      "Profîl",   Icons.Outlined.PersonOutline,     Icons.Filled.Person),
)

private val bottomNavRoutes = setOf(
    Screen.Feed.route, Screen.Search.route, Screen.Messages.route,
    Screen.Notifications.route, "profile/me",
)

// ── NavHost ───────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HeftrangNavHost(initialRoute: String? = null) {
    val navController              = rememberNavController()
    val authVm   : AuthViewModel   = hiltViewModel()
    val settingsVm: SettingsViewModel = hiltViewModel()
    val currentUser by authVm.currentUser.collectAsState()
    val isDark      by settingsVm.darkMode.collectAsState()
    val language    by settingsVm.language.collectAsState()
    val isAdmin     = settingsVm.isAdmin
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope       = rememberCoroutineScope()

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

        // Mesaj badge — okunmamış sayısı
        val messagesVm: MessagesViewModel = hiltViewModel()
        val totalUnread by messagesVm.totalUnread.collectAsState()
        LaunchedEffect(Unit) { messagesVm.listenConversations() }


        // Bildirimden gelen deep link
        LaunchedEffect(initialRoute) {
            initialRoute?.let { target ->
                try { navController.navigate(target) } catch (_: Exception) {}
            }
        }

        ModalNavigationDrawer(
            drawerState   = drawerState,
            drawerContent = {
                DrawerContent(
                    user         = currentUser,
                    currentRoute = currentRoute,
                    isDark       = isDark,
                    language     = language,
                    isAdmin      = isAdmin,
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
                                val label = if (language == "ku") item.labelKu else item.label
                                NavigationBarItem(
                                    selected = selected,
                                    onClick  = {
                                        navController.navigate(item.route) {
                                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                            launchSingleTop = true
                                            restoreState    = true
                                        }
                                    },
                                    icon   = {
                                if (item.route == Screen.Messages.route && totalUnread > 0) {
                                    BadgedBox(badge = {
                                        Badge(containerColor = Error) {
                                            Text(if (totalUnread > 9) "9+" else totalUnread.toString(),
                                                color = Color.White, fontSize = 9.sp)
                                        }
                                    }) {
                                        Icon(if (selected) item.iconSelected else item.icon, label)
                                    }
                                } else {
                                    Icon(if (selected) item.iconSelected else item.icon, label)
                                }
                            },
                                    label  = { Text(label, fontSize = 10.sp) },
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
                            language      = language,
                        )
                    }
                    composable(Screen.Kurdi.route) { KurdiScreen() }
                    composable(Screen.Messages.route) { ConversationsScreen(navController, language) }
                    composable("message/{convId}",
                        arguments = listOf(navArgument("convId") { type = NavType.StringType })) { back ->
                        MessageDetailScreen(
                            convId        = back.arguments?.getString("convId") ?: "",
                            navController = navController,
                            language      = language,
                        )
                    }
                    composable(Screen.Notifications.route) { NotificationsScreen(navController) }
                    composable("profile/{uid}",
                        arguments = listOf(navArgument("uid") { type = NavType.StringType })) { back ->
                        ProfileScreen(
                            uid           = back.arguments?.getString("uid") ?: "me",
                            navController = navController,
                            language      = language,
                        )
                    }
                    composable(Screen.EditProfile.route) { EditProfileScreen(navController) }
                    composable("post/{postId}",
                        arguments = listOf(navArgument("postId") { type = NavType.StringType })) { back ->
                        PostDetailScreen(
                            postId        = back.arguments?.getString("postId") ?: "",
                            navController = navController,
                            viewModel     = hiltViewModel(), // FeedViewModel
                        )
                    }
                    composable(Screen.Search.route) { SearchScreen(navController) }
                    composable(Screen.Admin.route) { AdminScreen(navController) }
                    composable(Screen.Serials.route) { SerialsScreen(navController, language) }
                    composable("serial/{serialId}",
                        arguments = listOf(navArgument("serialId") { type = NavType.StringType })) { back ->
                        SerialDetailScreen(
                            serialId      = back.arguments?.getString("serialId") ?: "",
                            navController = navController,
                        )
                    }
                    composable("chapter/{serialId}/{chapterId}",
                        arguments = listOf(
                            navArgument("serialId")  { type = NavType.StringType },
                            navArgument("chapterId") { type = NavType.StringType },
                        )) { back ->
                        ChapterReadScreen(
                            serialId      = back.arguments?.getString("serialId") ?: "",
                            chapterId     = back.arguments?.getString("chapterId") ?: "",
                            navController = navController,
                        )
                    }
                    composable("reading_list/{uid}",
                        arguments = listOf(navArgument("uid") { type = NavType.StringType })) { back ->
                        ReadingListScreen(
                            uid           = back.arguments?.getString("uid") ?: "",
                            navController = navController,
                        )
                    }
                    composable(Screen.Settings.route) { SettingsScreen(navController) }
                }
            }
        }
    }
}

// ── DrawerContent ─────────────────────────────────────────────────────────────
@Composable
private fun DrawerContent(
    user        : com.google.firebase.auth.FirebaseUser?,
    currentRoute: String?,
    isDark      : Boolean,
    language    : String,
    isAdmin     : Boolean,
    onToggleDark: () -> Unit,
    onSetLang   : (String) -> Unit,
    onNavigate  : (String) -> Unit,
    onSignOut   : () -> Unit,
) {
    val scroll = rememberScrollState()
    val tr: (String, String) -> String = { tr, ku -> if (language == "ku") ku else tr }

    ModalDrawerSheet(drawerContainerColor = HeftSurface) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .verticalScroll(scroll)
                .padding(horizontal = 12.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            // Kullanıcı kartı
            Row(
                modifier              = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Primary.copy(alpha = 0.12f))
                    .clickable { onNavigate("profile/me") }
                    .padding(12.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AsyncImage(
                    model = user?.photoUrl, contentDescription = "Profil",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(44.dp).clip(CircleShape).background(SurfaceVar),
                )
                Column {
                    Text(user?.displayName ?: "heftreng", fontWeight = FontWeight.Bold, color = OnBackground, fontSize = 15.sp)
                    Text(user?.email ?: "", color = Muted, fontSize = 11.sp)
                }
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = Divider)
            Spacer(Modifier.height(6.dp))

            // Sayfalar
            DrawerLabel(tr("Sayfalar", "Rûpel"))
            listOf(
                Triple(Screen.Feed.route,     tr("Feed / Akış", "Nivîs"),          Icons.Outlined.DynamicFeed),
                Triple(Screen.Search.route,   tr("Ara", "Bigere"),                  Icons.Outlined.Search),
                Triple("profile/me",          tr("Profilim", "Profîla Min"),         Icons.Outlined.PersonOutline),
                Triple(Screen.Kurdi.route,    tr("Kürtçe Öğren", "Kurdî Fêrbibe"),  Icons.Outlined.Translate),
                // Seriler → Kitap Yazma olarak gösterilir
                Triple(Screen.Serials.route,  tr("Kitap Yazma", "Nivîsandina Pirtûkê"), Icons.Outlined.AutoStories),
                Triple(Screen.Settings.route, tr("Ayarlar", "Mîheng"),               Icons.Outlined.Settings),
            ).forEach { (route, label, icon) ->
                DrawerNavItem(label, icon, currentRoute == route) { onNavigate(route) }
            }

            // Admin sadece siirgibi49@gmail.com için görünür
            if (isAdmin) {
                Spacer(Modifier.height(4.dp))
                HorizontalDivider(color = Divider)
                Spacer(Modifier.height(4.dp))
                DrawerLabel("Admin")
                DrawerNavItem("Admin Paneli", Icons.Default.AdminPanelSettings, currentRoute == "admin",
                    tint = Color(0xFFF59E0B)) { onNavigate("admin") }
            }

            Spacer(Modifier.weight(1f))
            HorizontalDivider(color = Divider)
            Spacer(Modifier.height(8.dp))

            // Karanlık mod
            Row(
                modifier          = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    if (isDark) Icons.Filled.DarkMode else Icons.Outlined.LightMode,
                    null, tint = Amber, modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    if (isDark) tr("Karanlık Mod", "Moda Tarî")
                    else tr("Aydınlık Mod", "Moda Ronahî"),
                    color = OnBackground, fontSize = 13.sp, modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = isDark, onCheckedChange = { onToggleDark() },
                    colors  = SwitchDefaults.colors(
                        checkedThumbColor   = Amber, checkedTrackColor   = Amber.copy(alpha = 0.3f),
                        uncheckedThumbColor = Muted, uncheckedTrackColor = Muted.copy(alpha = 0.2f),
                    ),
                )
            }

            Spacer(Modifier.height(8.dp))

            // Dil seçimi — çalışan toggle
            Row(
                modifier          = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Translate, null, tint = Primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text(tr("Dil / Ziman", "Ziman"), color = OnBackground, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("tr" to "TR", "ku" to "KU").forEach { (code, label) ->
                        val sel = language == code
                        Button(
                            onClick = { onSetLang(code) },
                            modifier = Modifier.height(30.dp),
                            shape   = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                            colors  = ButtonDefaults.buttonColors(
                                containerColor = if (sel) Primary else SurfaceVar,
                                contentColor   = if (sel) Color.White else Muted,
                            ),
                        ) { Text(label, fontSize = 12.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal) }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Çıkış
            TextButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.Logout, null, tint = Error, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(tr("Çıkış Yap", "Derketin"), color = Error, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun DrawerLabel(text: String) {
    Text(text, color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 8.dp, top = 6.dp, bottom = 2.dp))
}

@Composable
private fun DrawerNavItem(label: String, icon: ImageVector, selected: Boolean,
    tint: Color? = null, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(11.dp))
            .background(if (selected) Primary.copy(alpha = 0.15f) else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null,
            tint = tint ?: if (selected) Primary else Muted,
            modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(label,
            color = tint ?: if (selected) Primary else OnBackground,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            fontSize = 14.sp)
    }
}
