package com.heftreng.app.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import com.heftreng.app.ui.screens.auth.AuthScreen
import com.heftreng.app.ui.screens.feed.FeedScreen
import com.heftreng.app.ui.screens.messages.ConversationsScreen
import com.heftreng.app.ui.screens.messages.MessageDetailScreen
import com.heftreng.app.ui.screens.notifications.NotificationsScreen
import com.heftreng.app.ui.screens.profile.ProfileScreen
import com.heftreng.app.ui.screens.profile.EditProfileScreen
import com.heftreng.app.ui.theme.Amber
import com.heftreng.app.ui.theme.Background
import com.heftreng.app.ui.theme.Divider
import com.heftreng.app.ui.theme.Muted
import com.heftreng.app.viewmodel.AuthViewModel

sealed class Screen(val route: String) {
    object Auth           : Screen("auth")
    object Feed           : Screen("feed")
    object Notifications  : Screen("notifications")
    object Messages       : Screen("messages")
    object MessageDetail  : Screen("message/{conversationId}") {
        fun go(id: String) = "message/$id"
    }
    object Profile        : Screen("profile/{uid}") {
        fun go(uid: String) = "profile/$uid"
        fun me() = "profile/me"
    }
    object EditProfile    : Screen("edit_profile")
}

data class BottomNavItem(
    val screen: Screen,
    val label: String,
    val icon: ImageVector,
    val iconSelected: ImageVector,
)

val bottomNavItems = listOf(
    BottomNavItem(Screen.Feed,          "Feed",       Icons.Outlined.Home,           Icons.Filled.Home),
    BottomNavItem(Screen.Notifications, "Bildirim",   Icons.Outlined.Notifications,  Icons.Filled.Notifications),
    BottomNavItem(Screen.Messages,      "Mesajlar",   Icons.Outlined.MailOutline,    Icons.Filled.Mail),
    BottomNavItem(Screen.Profile.run { Screen("profile/me") }, "Profil", Icons.Outlined.Person, Icons.Filled.Person),
)

@Composable
fun HeftrangNavHost() {
    val navController = rememberNavController()
    val authVm: AuthViewModel = hiltViewModel()
    val currentUser by authVm.currentUser.collectAsState()

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

    val showBottomBar = bottomNavItems.any { it.screen.route == currentRoute } ||
        currentRoute == "profile/me"

    Scaffold(
        containerColor = Background,
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = Background,
                    tonalElevation = 0.dp,
                ) {
                    bottomNavItems.forEach { item ->
                        val selected = currentRoute == item.screen.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(item.screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    if (selected) item.iconSelected else item.icon,
                                    contentDescription = item.label,
                                )
                            },
                            label = { Text(item.label) },
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
                FeedScreen(navController = navController)
            }
            composable(Screen.Notifications.route) {
                NotificationsScreen(navController = navController)
            }
            composable(Screen.Messages.route) {
                ConversationsScreen(navController = navController)
            }
            composable("message/{conversationId}") { back ->
                val convId = back.arguments?.getString("conversationId") ?: ""
                MessageDetailScreen(conversationId = convId, navController = navController)
            }
            composable("profile/{uid}") { back ->
                val uid = back.arguments?.getString("uid") ?: "me"
                ProfileScreen(uid = uid, navController = navController)
            }
            composable(Screen.EditProfile.route) {
                EditProfileScreen(navController = navController)
            }
        }
    }
}
