package com.heftreng.app.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.heftreng.app.ui.screens.profile.EditProfileScreen
import com.heftreng.app.ui.screens.profile.ProfileScreen
import com.heftreng.app.ui.theme.*
import com.heftreng.app.viewmodel.AuthViewModel
import kotlinx.coroutines.launch

sealed class Screen(val route: String) {
    object Auth          : Screen("auth")
    object Feed          : Screen("feed")
    object Kurdi         : Screen("kurdi")
    object Messages      : Screen("messages")
    object MessageDetail : Screen("message/{convId}") { fun go(id: String) = "message/$id" }
    object Notifications : Screen("notifications")
    object Profile       : Screen("profile/{uid}") { fun go(uid: String) = "profile/$uid" }
    object EditProfile   : Screen("edit_profile")
}

data class BottomNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val iconSelected: ImageVector,
)

val bottomNavItems = listOf(
    BottomNavItem(Screen.Feed.route,          "Nivîs",    Icons.Outlined.DynamicFeed,         Icons.Filled.DynamicFeed),
    BottomNavItem(Screen.Kurdi.route,         "Kurdî",    Icons.Outlined.Translate,            Icons.Filled.Translate),
    BottomNavItem(Screen.Messages.route,      "Peyam",    Icons.Outlined.ChatBubbleOutline,    Icons.Filled.ChatBubble),
    BottomNavItem(Screen.Notifications.route, "Agahdarî", Icons.Outlined.NotificationsNone,    Icons.Filled.Notifications),
    BottomNavItem("profile/me",               "Profîl",   Icons.Outlined.PersonOutline,        Icons.Filled.Person),
)

private val bottomNavRoutes = setOf(
    Screen.Feed.route,
    Screen.Kurdi.route,
    Screen.Messages.route,
    Screen.Notifications.route,
    "profile/me",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HeftrangNavHost() {
    val navController         = rememberNavController()
    val authVm: AuthViewModel = hiltViewModel()
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
            bottomBar      = {
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
                composable(Screen.Kurdi.route)         { KurdiScreen() }
                composable(Screen.Messages.route)      { ConversationsScreen(navController) }
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
                composable(Screen.EditProfile.route)   { EditProfileScreen(navController) }
            }
        }
    }
}

// ── Drawer ───────────────────────────────────────────────────────────────────

@Composable
private fun DrawerContent(
    user: com.google.firebase.auth.FirebaseUser?,
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    onSignOut: () -> Unit,
) {
    ModalDrawerSheet(
        drawerContainerColor = Surface,
        drawerShape          = RoundedCornerShape(topEnd = 20.dp, bottomEnd = 20.dp),
    ) {
        Spacer(Modifier.height(40.dp))

        // User header
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            AsyncImage(
                model              = user?.photoUrl,
                contentDescription = user?.displayName,
                modifier           = Modifier.size(64.dp).clip(CircleShape).background(SurfaceVar),
                contentScale       = ContentScale.Crop,
            )
            Spacer(Modifier.height(10.dp))
            Text(user?.displayName ?: "", fontWeight = FontWeight.Bold, color = OnBackground, fontSize = 16.sp)
            if (!user?.email.isNullOrBlank())
                Text(user?.email ?: "", color = Muted, fontSize = 13.sp)
        }

        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp), color = Divider)

        val drawerItems = listOf(
            Triple(Screen.Feed.route,          "Nivîs",         Icons.Outlined.DynamicFeed),
            Triple(Screen.Kurdi.route,         "Kurdî Fêrbibe", Icons.Outlined.Translate),
            Triple(Screen.Messages.route,      "Peyam",         Icons.Outlined.ChatBubbleOutline),
            Triple(Screen.Notifications.route, "Agahdarî",      Icons.Outlined.NotificationsNone),
            Triple("profile/me",               "Profîla Min",   Icons.Outlined.PersonOutline),
        )

        drawerItems.forEach { (route, label, icon) ->
            val sel = currentRoute == route
            NavigationDrawerItem(
                label    = { Text(label, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal) },
                icon     = { Icon(icon, contentDescription = label) },
                selected = sel,
                onClick  = { onNavigate(route) },
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                colors   = NavigationDrawerItemDefaults.colors(
                    selectedContainerColor   = Amber.copy(alpha = 0.15f),
                    unselectedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                    selectedIconColor        = Amber,
                    selectedTextColor        = Amber,
                    unselectedIconColor      = Muted,
                    unselectedTextColor      = OnBackground,
                ),
            )
        }

        Spacer(Modifier.weight(1f))
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = Divider)

        NavigationDrawerItem(
            label    = { Text("Derketin / Çıkış Yap", color = OnBackground) },
            icon     = { Icon(Icons.Default.Logout, contentDescription = "Çıkış", tint = Muted) },
            selected = false,
            onClick  = onSignOut,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            colors   = NavigationDrawerItemDefaults.colors(
                unselectedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
            ),
        )
        Spacer(Modifier.height(16.dp))
    }
}
