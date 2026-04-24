package com.heftreng.app.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
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
    BottomNavItem(Screen.Feed.route,          "Nivîs",      Icons.Outlined.DynamicFeed,         Icons.Filled.DynamicFeed),
    BottomNavItem(Screen.Kurdi.route,         "Kurdî",      Icons.Outlined.Translate,            Icons.Filled.Translate),
    BottomNavItem(Screen.Messages.route,      "Peyam",      Icons.Outlined.ChatBubbleOutline,    Icons.Filled.ChatBubble),
    BottomNavItem(Screen.Notifications.route, "Agahdarî",   Icons.Outlined.NotificationsNone,    Icons.Filled.Notifications),
    BottomNavItem("profile/me",               "Profîl",     Icons.Outlined.PersonOutline,        Icons.Filled.Person),
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
    val showBottom = bottomNavItems.any { it.route == currentRoute }

    Scaffold(
        containerColor = Background,
        bottomBar = {
            if (showBottom) {
                NavigationBar(containerColor = Background, tonalElevation = 0.dp) {
                    bottomNavItems.forEach { item ->
                        val selected = currentRoute == item.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(if (selected) item.iconSelected else item.icon, contentDescription = item.label) },
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
        NavHost(navController = navController, startDestination = Screen.Feed.route, modifier = Modifier.padding(innerPadding)) {
            composable(Screen.Feed.route)          { FeedScreen(navController) }
            composable(Screen.Kurdi.route)         { KurdiScreen() }
            composable(Screen.Messages.route)      { ConversationsScreen(navController) }
            composable("message/{convId}") { back ->
                MessageDetailScreen(convId = back.arguments?.getString("convId") ?: "", navController = navController)
            }
            composable(Screen.Notifications.route) { NotificationsScreen(navController) }
            composable("profile/{uid}") { back ->
                ProfileScreen(uid = back.arguments?.getString("uid") ?: "me", navController = navController)
            }
            composable(Screen.EditProfile.route)   { EditProfileScreen(navController) }
        }
    }
}
