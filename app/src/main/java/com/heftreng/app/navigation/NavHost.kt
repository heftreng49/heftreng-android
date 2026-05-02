package com.heftreng.app.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.heftreng.app.ui.screens.admin.AdminScreen
import com.heftreng.app.ui.screens.auth.AuthScreen
import com.heftreng.app.ui.screens.feed.FeedScreen
import com.heftreng.app.ui.screens.kurdi.KurdiScreen
import com.heftreng.app.ui.screens.messages.ConversationsScreen
import com.heftreng.app.ui.screens.messages.MessageDetailScreen
import com.heftreng.app.ui.screens.notifications.NotificationsScreen
import com.heftreng.app.ui.screens.post.SinglePostScreen
import com.heftreng.app.ui.screens.profile.EditProfileScreen
import com.heftreng.app.ui.screens.profile.ProfileScreen
import com.heftreng.app.ui.screens.readinglist.ReadingListScreen
import com.heftreng.app.ui.screens.search.SearchScreen
import com.heftreng.app.ui.screens.books.BookChapterReadScreen
import com.heftreng.app.ui.screens.books.BookDetailScreen
import com.heftreng.app.ui.screens.books.BooksScreen
import com.heftreng.app.ui.screens.serials.ChapterReadScreen
import com.heftreng.app.ui.screens.serials.SerialDetailScreen
import com.heftreng.app.ui.screens.serials.SerialsScreen
import com.heftreng.app.ui.screens.settings.SettingsScreen
import com.heftreng.app.ui.theme.*
import com.heftreng.app.viewmodel.AuthViewModel
import com.heftreng.app.viewmodel.MessagesViewModel
import com.heftreng.app.viewmodel.NotificationsViewModel
import com.heftreng.app.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch

// ── Routes ───────────────────────────────────────────────────────────────────
sealed class Screen(val route: String) {
    object Auth          : Screen("auth")
    object Feed          : Screen("feed")
    object Search        : Screen("search")
    object Messages      : Screen("messages")
    object Notifications : Screen("notifications")
    object Settings      : Screen("settings")
    object Kurdi         : Screen("kurdi")
    object Serials       : Screen("serials")
    object Admin         : Screen("admin")
    object MessageDetail : Screen("message/{convId}") { fun go(id: String) = "message/$id" }
    object Profile       : Screen("profile/{uid}")    { fun go(uid: String) = "profile/$uid" }
    object EditProfile   : Screen("edit_profile")
    object PostDetail    : Screen("post/{postId}")    { fun go(id: String) = "post/$id" }
    object SerialDetail  : Screen("serial/{id}")      { fun go(id: String) = "serial/$id" }
    object Chapter       : Screen("chapter/{sid}/{cid}") { fun go(s: String, c: String) = "chapter/$s/$c" }
    object ReadingList   : Screen("reading_list/{uid}") { fun go(uid: String) = "reading_list/$uid" }
    object Books         : Screen("books")
    object BookDetail    : Screen("book/{bookId}")         { fun go(id: String) = "book/$id" }
    object BookChapter   : Screen("book_chapter/{bid}/{cid}") { fun go(b: String, c: String) = "book_chapter/$b/$c" }
}

// ── Alt bar — temadaki gibi: Nivîs | Bigere | Pirtûk | Profîl ───────────────
data class BottomNavItem(val route: String, val label: String, val icon: ImageVector, val iconSel: ImageVector)

val bottomNavItems = listOf(
    BottomNavItem(Screen.Feed.route,    "Nivîs",  Icons.Outlined.DynamicFeed,  Icons.Filled.DynamicFeed),
    BottomNavItem(Screen.Search.route,  "Bigere", Icons.Outlined.Search,       Icons.Filled.Search),
    BottomNavItem(Screen.Serials.route, "Pirtûk", Icons.Outlined.AutoStories,  Icons.Filled.AutoStories),
    BottomNavItem(Screen.Kurdi.route,   "Kurdî",  Icons.Outlined.Translate,    Icons.Filled.Translate),
    BottomNavItem("profile/me",         "Profîl", Icons.Outlined.PersonOutline,Icons.Filled.Person),
)

private val bottomNavRoutes = setOf(
    Screen.Feed.route, Screen.Search.route, Screen.Serials.route,
    Screen.Kurdi.route, "profile/me",
)

// ── NavHost ───────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HeftrangNavHost(initialRoute: String? = null) {
    val navController  = rememberNavController()
    val authVm         : AuthViewModel          = hiltViewModel()
    val settingsVm     : SettingsViewModel      = hiltViewModel()
    val notifVm        : NotificationsViewModel = hiltViewModel()
    val msgsVm         : MessagesViewModel      = hiltViewModel()

    val currentUser by authVm.currentUser.collectAsState()
    val isDark      by settingsVm.darkMode.collectAsState()
    val language    by settingsVm.language.collectAsState()
    val totalUnread by msgsVm.totalUnread.collectAsState()
    val unreadNotif by notifVm.unreadCount.collectAsState()

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope       = rememberCoroutineScope()

    if (currentUser == null) {
        // Tema MainActivity'de zaten uygulanıyor
        AuthScreen(onAuthSuccess = {
            navController.navigate(Screen.Feed.route) {
                popUpTo(Screen.Auth.route) { inclusive = true }
            }
        })
        return
    }

    // Mesaj badge için app açılır açılmaz conversations dinle
    LaunchedEffect(currentUser?.uid) {
        if (currentUser != null) {
            msgsVm.listenConversations()
            notifVm.load()
        }
    }

    // Tema MainActivity'de uygulanıyor, burada tekrar sarmalamaya gerek yok
    LaunchedEffect(initialRoute) {
        initialRoute?.let { try { navController.navigate(it) } catch (_: Exception) {} }
    }

    val navBackStack  by navController.currentBackStackEntryAsState()
    val currentRoute  = navBackStack?.destination?.route
    val showBottom    = currentRoute in bottomNavRoutes
    val isAdmin       = settingsVm.isAdmin

    ModalNavigationDrawer(
        drawerState   = drawerState,
        drawerContent = {
            DrawerContent(
                currentUser  = currentUser,
                isDark       = isDark,
                language     = language,
                isAdmin      = isAdmin,
                totalUnread  = totalUnread,
                unreadNotif  = unreadNotif,
                onNavigate   = { route ->
                    scope.launch { drawerState.close() }
                    navController.navigate(route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState    = true
                    }
                },
                onSignOut = {
                    scope.launch { drawerState.close() }
                    authVm.signOut()
                },
            )
        }
    ) {
        Scaffold(
            containerColor = Background,
            // ── ÜST BAR — sadece Feed ekranında NavHost yönetir ──
            topBar = {
                if (currentRoute == Screen.Feed.route) {
                    TopAppBar(
                        title = {
                            Text(
                                "Heftreng",
                                fontWeight = FontWeight.ExtraBold,
                                color      = Primary,
                                fontSize   = 20.sp,
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Default.Menu, null, tint = OnBackground)
                            }
                        },
                        actions = {
                            // Bildirim butonu — badge ile
                            BadgedBox(
                                badge = {
                                    if (unreadNotif > 0) Badge {
                                        Text(if (unreadNotif > 9) "9+" else unreadNotif.toString())
                                    }
                                }
                            ) {
                                IconButton(onClick = { navController.navigate(Screen.Notifications.route) }) {
                                    Icon(Icons.Outlined.NotificationsNone, null, tint = OnBackground)
                                }
                            }
                            // Mesaj butonu — badge ile
                            BadgedBox(
                                badge = {
                                    if (totalUnread > 0) Badge {
                                        Text(if (totalUnread > 9) "9+" else totalUnread.toString())
                                    }
                                }
                            ) {
                                IconButton(onClick = { navController.navigate(Screen.Messages.route) }) {
                                    Icon(Icons.Outlined.ChatBubbleOutline, null, tint = OnBackground)
                                }
                            }
                            // Avatar
                            IconButton(onClick = { navController.navigate("profile/me") }) {
                                AsyncImage(
                                    model              = currentUser?.photoUrl,
                                    contentDescription = null,
                                    modifier           = Modifier.size(32.dp).clip(CircleShape).background(SurfaceVar),
                                    contentScale       = ContentScale.Crop,
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Background),
                    )
                }
            },
            // ── ALT BAR — temadaki gibi ──────────────────────────────────
            bottomBar = {
                if (showBottom) {
                    NavigationBar(
                        containerColor = HeftSurface,
                        tonalElevation = 0.dp,
                    ) {
                        bottomNavItems.forEach { item ->
                            val selected = currentRoute == item.route ||
                                (item.route == "profile/me" && currentRoute?.startsWith("profile/") == true)
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
                                icon = {
                                    val msgBadge   = item.route == Screen.Messages.route && totalUnread > 0
                                    val notifBadge = item.route == Screen.Notifications.route && unreadNotif > 0
                                    if (msgBadge || notifBadge) {
                                        val cnt = if (msgBadge) totalUnread else unreadNotif
                                        BadgedBox(badge = {
                                            Badge(containerColor = Error) {
                                                Text(
                                                    if (cnt > 9) "9+" else cnt.toString(),
                                                    color    = Color.White,
                                                    fontSize = 9.sp,
                                                )
                                            }
                                        }) {
                                            Icon(if (selected) item.iconSel else item.icon, item.label)
                                        }
                                    } else {
                                        Icon(if (selected) item.iconSel else item.icon, item.label)
                                    }
                                },
                                label = {
                                    Text(item.label, fontSize = 10.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor   = Amber,
                                    selectedTextColor   = Amber,
                                    unselectedIconColor = Muted,
                                    unselectedTextColor = Muted,
                                    indicatorColor      = Amber.copy(alpha = 0.15f),
                                ),
                            )
                        }
                    }
                }
            },
        ) { innerPadding ->
            NavHost(
                navController    = navController,
                startDestination = Screen.Feed.route,
                modifier         = Modifier.padding(innerPadding),
            ) {
                composable(Screen.Feed.route) {
                    FeedScreen(navController = navController)
                }
                composable(Screen.Search.route) { SearchScreen(navController) }
                composable(Screen.Serials.route) { SerialsScreen(navController, language) }
                composable(Screen.Kurdi.route)   { KurdiScreen(language = language) }
                composable("profile/{uid}") { back ->
                    ProfileScreen(
                        uid           = back.arguments?.getString("uid") ?: "me",
                        navController = navController,
                    )
                }
                composable(Screen.Messages.route) {
                    ConversationsScreen(navController, language)
                }
                composable("message/{convId}") { back ->
                    MessageDetailScreen(
                        convId        = back.arguments?.getString("convId") ?: "",
                        navController = navController,
                    )
                }
                composable(Screen.Notifications.route) {
                    NotificationsScreen(navController)
                }
                composable(Screen.EditProfile.route) { EditProfileScreen(navController) }
                composable(Screen.Admin.route)    { AdminScreen(navController) }
                composable(Screen.Settings.route) { SettingsScreen(navController) }
                composable("post/{postId}") { back ->
                    SinglePostScreen(
                        postId        = back.arguments?.getString("postId") ?: "",
                        navController = navController,
                    )
                }
                composable("serial/{id}") { back ->
                    SerialDetailScreen(
                        serialId      = back.arguments?.getString("id") ?: "",
                        navController = navController,
                    )
                }
                composable("chapter/{sid}/{cid}") { back ->
                    ChapterReadScreen(
                        serialId      = back.arguments?.getString("sid") ?: "",
                        chapterId     = back.arguments?.getString("cid") ?: "",
                        navController = navController,
                    )
                }
                composable(Screen.Books.route) { BooksScreen(navController, language) }
                composable("book/{bookId}") { back ->
                    val bookId = back.arguments?.getString("bookId") ?: ""
                    BookDetailScreen(bookId = bookId, navController = navController, language = language)
                }
                composable("book_chapter/{bid}/{cid}") { back ->
                    val bid = back.arguments?.getString("bid") ?: ""
                    val cid = back.arguments?.getString("cid") ?: ""
                    BookChapterReadScreen(bookId = bid, chapterId = cid, navController = navController)
                }
                composable("reading_list/{uid}") { back ->
                    ReadingListScreen(
                        uid           = back.arguments?.getString("uid") ?: "",
                        navController = navController,
                    )
                }
            }
        }
    }
}

// ── Sol Drawer ────────────────────────────────────────────────────────────────
@Composable
fun DrawerContent(
    currentUser : com.google.firebase.auth.FirebaseUser?,
    isDark      : Boolean,
    language    : String,
    isAdmin     : Boolean,
    totalUnread : Int,
    unreadNotif : Int,
    onNavigate  : (String) -> Unit,
    onSignOut   : () -> Unit,
) {
    val settingsVm: SettingsViewModel = hiltViewModel()

    ModalDrawerSheet(drawerContainerColor = HeftSurface) {
        Column(
            modifier = Modifier.fillMaxHeight().padding(horizontal = 16.dp, vertical = 20.dp)
        ) {
            // ── Logo ───────────────────────────────────────────────────
            Text("Heftreng", fontWeight = FontWeight.ExtraBold, color = Primary, fontSize = 22.sp)
            Spacer(Modifier.height(16.dp))

            // ── Profil özeti ───────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onNavigate("profile/me") }
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AsyncImage(
                    model              = currentUser?.photoUrl,
                    contentDescription = null,
                    modifier           = Modifier.size(44.dp).clip(CircleShape).background(SurfaceVar),
                    contentScale       = ContentScale.Crop,
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(currentUser?.displayName ?: "", fontWeight = FontWeight.Bold, color = OnBackground, fontSize = 15.sp)
                    Text(currentUser?.email ?: "", color = Muted, fontSize = 12.sp)
                }
            }

            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = Divider)
            Spacer(Modifier.height(8.dp))

            // ── Navigasyon ─────────────────────────────────────────────
            val items = listOf(
                Triple(Icons.Outlined.DynamicFeed,    "Nivîs / Feed",           Screen.Feed.route),
                Triple(Icons.Outlined.Search,         "Bigere / Ara",           Screen.Search.route),
                Triple(Icons.Outlined.AutoStories,    "Pirtûk / Seriler",       Screen.Serials.route),
                Triple(Icons.Outlined.MenuBook,       "Kitaplar / Pirtûk",      Screen.Books.route),
                Triple(Icons.Outlined.Translate,      "Kurdî Fêrbibe",          Screen.Kurdi.route),
                Triple(Icons.Outlined.NotificationsNone, "Agahdarî / Bildirimler (${if (unreadNotif>0) unreadNotif else ""})", Screen.Notifications.route),
                Triple(Icons.Outlined.ChatBubbleOutline, "Peyam / Mesajlar (${if (totalUnread>0) totalUnread else ""})", Screen.Messages.route),
                Triple(Icons.Outlined.Settings,       "Mîheng / Ayarlar",       Screen.Settings.route),
            )

            items.forEach { (icon, label, route) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onNavigate(route) }
                        .padding(horizontal = 10.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(icon, null, tint = Muted, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(label, color = OnBackground, fontSize = 14.sp)
                }
            }

            if (isAdmin) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onNavigate(Screen.Admin.route) }
                        .padding(horizontal = 10.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.AdminPanelSettings, null, tint = Error, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("Admin Paneli", color = Error, fontSize = 14.sp)
                }
            }

            Spacer(Modifier.weight(1f))
            HorizontalDivider(color = Divider)
            Spacer(Modifier.height(8.dp))

            // ── Dark mode toggle ───────────────────────────────────────
            Row(
                modifier          = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    if (isDark) Icons.Filled.DarkMode else Icons.Outlined.LightMode,
                    null, tint = Amber, modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(if (isDark) "Karanlık" else "Aydınlık", color = OnBackground, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Switch(
                    checked = isDark,
                    onCheckedChange = { settingsVm.toggleDarkMode() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor   = Amber,
                        checkedTrackColor   = Amber.copy(alpha = 0.3f),
                        uncheckedThumbColor = Muted,
                        uncheckedTrackColor = Muted.copy(alpha = 0.2f),
                    ),
                )
            }

            Spacer(Modifier.height(8.dp))

            // Çıkış
            TextButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.Logout, null, tint = Color(0xFFEF4444), modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Derketin / Çıkış", color = Color(0xFFEF4444), fontSize = 13.sp)
            }
        }
    }
}
