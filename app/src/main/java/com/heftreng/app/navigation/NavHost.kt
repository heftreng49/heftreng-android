package com.heftreng.app.navigation

import com.heftreng.app.ui.i18n.Strings
import com.heftreng.app.BuildConfig

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
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
import com.heftreng.app.viewmodel.AdminViewModel
import com.heftreng.app.viewmodel.StaffPermissions
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import coil.compose.AsyncImage
import com.heftreng.app.ui.screens.admin.AdminScreen
import com.heftreng.app.ui.screens.admin.CmsScreen
import com.heftreng.app.ui.screens.auth.AuthScreen
import com.heftreng.app.ui.screens.blog.BlogScreen
import com.heftreng.app.ui.screens.blog.BlogPostScreen
import com.heftreng.app.ui.screens.yazar.YazarScreen
import com.heftreng.app.ui.screens.feed.SavedPostsScreen
import com.heftreng.app.viewmodel.BlogViewModel
import com.heftreng.app.ui.screens.feed.FeedScreen
import com.heftreng.app.ui.screens.cms.CmsPageScreen
import com.heftreng.app.ui.screens.kurdi.KurdiScreen
import com.heftreng.app.ui.screens.kurdi.KurdiAdminScreen
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
import com.heftreng.app.ui.screens.quotes.AuthorDetailScreen
import com.heftreng.app.ui.screens.quotes.LibraryBookDetailScreen
import com.heftreng.app.ui.screens.quotes.AuthorQuotesSmartScreen
import com.heftreng.app.ui.screens.quotes.BookQuotesSmartScreen
import com.heftreng.app.ui.screens.library.LibraryScreen
import com.heftreng.app.ui.screens.settings.SettingsScreen
import com.heftreng.app.ui.theme.*
import com.heftreng.app.viewmodel.AppConfigViewModel
import com.heftreng.app.viewmodel.AuthViewModel
import com.heftreng.app.viewmodel.MessagesViewModel
import com.heftreng.app.viewmodel.NotificationsViewModel
import com.heftreng.app.viewmodel.SettingsViewModel
import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.window.Dialog
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import kotlinx.coroutines.launch

// ── Routes ───────────────────────────────────────────────────────────────────
sealed class Screen(val route: String) {
    object Auth          : Screen("auth")
    object Blog         : Screen("blog")
    object BlogPost     : Screen("blog_post/{postId}") { fun go(id: String) = "blog_post/$id" }
    object Feed          : Screen("feed")
    object Search        : Screen("search")
    object Messages      : Screen("messages")
    object Notifications : Screen("notifications")
    object Settings      : Screen("settings")
    object Kurdi         : Screen("kurdi")
    object Serials       : Screen("serials")
    object Admin         : Screen("admin")
    object Cms           : Screen("cms")
    object MessageDetail : Screen("message/{convId}") { fun go(id: String) = "message/$id" }
    object Profile       : Screen("profile/{uid}")    { fun go(uid: String) = "profile/$uid" }
    object EditProfile   : Screen("edit_profile")
    object PostDetail    : Screen("post/{postId}")    { fun go(id: String) = "post/$id" }
    object SerialDetail  : Screen("serial/{id}")      { fun go(id: String) = "serial/$id" }
    object Chapter       : Screen("chapter/{sid}/{cid}") { fun go(s: String, c: String) = "chapter/$s/$c" }
    object ReadingList   : Screen("reading_list/{uid}") { fun go(uid: String) = "reading_list/$uid" }
    object Books         : Screen("books")
    object Library       : Screen("library")
    object BookDetail    : Screen("book/{bookId}")         { fun go(id: String) = "book/$id" }
    object CmsPage       : Screen("cms_page/{slug}")       { fun go(slug: String) = "cms_page/$slug" }
    object Yazar         : Screen("yazar")
    object KurdiAdmin    : Screen("kurdi_admin")
    object SavedPosts    : Screen("saved_posts")
    object BookChapter   : Screen("book_chapter/{bid}/{cid}") { fun go(b: String, c: String) = "book_chapter/$b/$c" }
}

// ── Alt bar ───────────────────────────────────────────────────────────────────
data class BottomNavItem(val route: String, val label: String, val icon: ImageVector, val iconSel: ImageVector)

private val bottomNavRoutes = setOf(
    Screen.Feed.route, Screen.Blog.route, Screen.Library.route,
    Screen.Kurdi.route, "profile/me",
)

// ── NavHost ───────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HeftrangNavHost(initialRoute: String? = null) {
    val navController  = rememberNavController()
    val authVm         : AuthViewModel          = hiltViewModel()
    val settingsVm     : SettingsViewModel      = hiltViewModel()
    val adminVm        : AdminViewModel          = hiltViewModel()
    val notifVm        : NotificationsViewModel = hiltViewModel()
    val msgsVm         : MessagesViewModel      = hiltViewModel()
    val blogVm         : BlogViewModel          = hiltViewModel()
    val appConfigVm    : AppConfigViewModel     = hiltViewModel()

    val currentUser by authVm.currentUser.collectAsState()
    val isDark         by settingsVm.darkMode.collectAsState()
    val savedAccounts  by authVm.savedAccounts.collectAsState()
    val switchToGoogle by authVm.switchToGoogle.collectAsState()
    val verificationPending by authVm.verificationPending.collectAsState()
    var showVerifyBanner by remember { mutableStateOf(false) }
    var showAccountSwitch by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current

    // Email doğrulama soft banner — login sonrası bir kez göster
    LaunchedEffect(verificationPending) {
        if (verificationPending) {
            showVerifyBanner = true
            authVm.clearVerificationPending()
        }
    }

    // Google ile hesap geçişi launcher
    val switchGoogleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)
                account.idToken?.let { authVm.signInWithGoogle(it) }
            } catch (_: ApiException) {}
        }
    }
    LaunchedEffect(switchToGoogle) {
        if (switchToGoogle) {
            val client = authVm.getGoogleSignInClient(context)
            client.signOut().addOnCompleteListener {
                switchGoogleLauncher.launch(client.signInIntent)
            }
            authVm.clearSwitchToGoogle()
        }
    }
    val language    by settingsVm.language.collectAsState()
    val totalUnread by msgsVm.totalUnread.collectAsState()
    val appConfig   by appConfigVm.config.collectAsState()
    val configLoaded by appConfigVm.loaded.collectAsState()
    // Dile göre alt bar etiketleri
    val bottomNavItems = listOf(
        BottomNavItem(Screen.Feed.route,    Strings.navFeed(language),    Icons.Outlined.DynamicFeed,  Icons.Filled.DynamicFeed),
        BottomNavItem(Screen.Blog.route,    Strings.navBlog(language),    Icons.Outlined.Article,       Icons.Filled.Article),
        BottomNavItem(Screen.Library.route, Strings.navLibrary(language), Icons.Outlined.LocalLibrary,  Icons.Filled.LocalLibrary),
        BottomNavItem(Screen.Kurdi.route,   Strings.navKurdi(language),   Icons.Outlined.Translate,     Icons.Filled.Translate),
        BottomNavItem("profile/me",         Strings.navProfile(language), Icons.Outlined.PersonOutline, Icons.Filled.Person),
    )
    val unreadNotif by notifVm.unreadCount.collectAsState()

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope       = rememberCoroutineScope()

    // ── Bakım modu ────────────────────────────────────────────────────────────
    if (configLoaded && appConfig.maintenanceMode) {
        Box(
            modifier         = Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color(0xFF0A0A14)),
            contentAlignment = androidx.compose.ui.Alignment.Center,
        ) {
            androidx.compose.material3.Surface(
                shape          = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
                color          = androidx.compose.ui.graphics.Color(0xFF1A1A2E),
                modifier       = Modifier.padding(32.dp),
            ) {
                Column(
                    modifier              = Modifier.padding(28.dp),
                    horizontalAlignment   = androidx.compose.ui.Alignment.CenterHorizontally,
                    verticalArrangement   = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
                ) {
                    Text("🔧", fontSize = 40.sp)
                    Text(
                        "Bakım Modu",
                        color      = androidx.compose.ui.graphics.Color(0xFFFFB300),
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        fontSize   = 18.sp,
                    )
                    Text(
                        appConfig.maintenanceMessage,
                        color    = androidx.compose.ui.graphics.Color(0xFF888899),
                        fontSize = 14.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
        }
        return
    }

    // ── Zorunlu güncelleme kontrolü ─────────────────────────────────────────
    if (configLoaded && appConfig.minVersion > com.heftreng.app.BuildConfig.VERSION_CODE) {
        Box(
            modifier         = Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color(0xFF0A0A14)),
            contentAlignment = androidx.compose.ui.Alignment.Center,
        ) {
            androidx.compose.material3.Surface(
                shape   = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
                color   = androidx.compose.ui.graphics.Color(0xFF1A1A2E),
                modifier = Modifier.padding(32.dp),
            ) {
                Column(
                    modifier            = Modifier.padding(28.dp),
                    horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp),
                ) {
                    Text("🚀", fontSize = 48.sp)
                    Text(
                        "Güncelleme Gerekli",
                        color      = androidx.compose.ui.graphics.Color(0xFFFFB300),
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        fontSize   = 20.sp,
                    )
                    Text(
                        "Bu sürüm artık desteklenmiyor. Uygulamayı kullanmaya devam etmek için lütfen güncelleyin.",
                        color     = androidx.compose.ui.graphics.Color(0xFF888899),
                        fontSize  = 14.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        lineHeight = 20.sp,
                    )
                    val ctx = androidx.compose.ui.platform.LocalContext.current
                    androidx.compose.material3.Button(
                        onClick = {
                            val intent = android.content.Intent(
                                android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse(
                                    "https://play.google.com/store/apps/details?id=${ctx.packageName}"
                                )
                            )
                            ctx.startActivity(intent)
                        },
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = androidx.compose.ui.graphics.Color(0xFF7C4DFF),
                        ),
                        shape  = androidx.compose.foundation.shape.RoundedCornerShape(50),
                    ) {
                        Text(
                            "Şimdi Güncelle",
                            color      = androidx.compose.ui.graphics.Color.White,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            modifier   = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                    }
                }
            }
        }
        return
    }

    if (currentUser == null) {
        // Tema MainActivity'de zaten uygulanıyor
        AuthScreen(onAuthSuccess = {
            navController.navigate(Screen.Feed.route) {
                popUpTo(Screen.Auth.route) { this.inclusive = true }
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
    val perms         by adminVm.perms.collectAsState()
    val isAdmin        = perms?.isStaff() == true
    val staffPerms     = perms ?: StaffPermissions()

    LaunchedEffect(Unit) { adminVm.checkAdmin() }

    ModalNavigationDrawer(
        drawerState   = drawerState,
        drawerContent = {
            DrawerContent(
                currentUser  = currentUser,
                isDark       = isDark,
                language     = language,
                isAdmin      = isAdmin,
                staffPerms   = staffPerms,
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
            // ime insets iç ekranlar tarafından yönetiliyor — burada sadece sistem barları
            contentWindowInsets = WindowInsets(0,0,0,0),
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
                            // Arama butonu
                            IconButton(onClick = { navController.navigate(Screen.Search.route) }) {
                                Icon(Icons.Outlined.Search, null, tint = OnBackground)
                            }
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
                                    } else if (item.route == "profile/me") {
                                        // ── Instagram gibi: profil ikonu = avatar, uzun bas = hesap değiştir
                                        val avatarUrl = currentUser?.photoUrl?.toString()
                                        Box(
                                            modifier = Modifier
                                                .size(28.dp)
                                                .clip(CircleShape)
                                                .background(if (selected) Amber.copy(alpha = 0.2f) else Muted.copy(alpha = 0.15f))
                                                .combinedClickable(
                                                    onClick     = {
                                                        navController.navigate("profile/me") {
                                                            popUpTo(navController.graph.findStartDestination().id) {
                                                                saveState = true
                                                            }
                                                            launchSingleTop = true
                                                            restoreState    = true
                                                        }
                                                    },
                                                    onLongClick = { if (savedAccounts.size > 1) showAccountSwitch = true },
                                                ),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            if (!avatarUrl.isNullOrBlank()) {
                                                AsyncImage(
                                                    model              = avatarUrl,
                                                    contentDescription = null,
                                                    modifier           = Modifier.fillMaxSize(),
                                                    contentScale       = ContentScale.Crop,
                                                )
                                            } else {
                                                Icon(
                                                    if (selected) item.iconSel else item.icon,
                                                    item.label,
                                                    modifier = Modifier.size(20.dp),
                                                )
                                            }
                                        }
                                        // Halka: aktif göstergesi
                                        if (selected) {
                                            Box(
                                                modifier = Modifier
                                                    .padding(top = 2.dp)
                                                    .size(4.dp)
                                                    .clip(CircleShape)
                                                    .background(Amber),
                                            )
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
            // ── Hesap Değiştirme Dialog (Instagram stili) ─────────────────
            if (showAccountSwitch) {
                InstagramAccountSwitcherDialog(
                    accounts     = savedAccounts,
                    currentEmail = currentUser?.email ?: "",
                    language     = language,
                    onSelect     = { account ->
                        showAccountSwitch = false
                        authVm.switchAccount(account, context)
                    },
                    onRemove     = { email -> authVm.removeAccount(email) },
                    onAddAccount = {
                        showAccountSwitch = false
                        authVm.signOut()
                    },
                    onDismiss    = { showAccountSwitch = false },
                )
            }

            // ── Email doğrulama soft banner ───────────────────────────────
            if (showVerifyBanner) {
                Box(
                    modifier = Modifier
                        .padding(innerPadding)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Amber.copy(alpha = 0.15f))
                        .border(1.dp, Amber.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                        .padding(12.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(Icons.Default.MarkEmailUnread, null, tint = Amber, modifier = Modifier.size(20.dp))
                        Text(
                            if (language == "ku") "Ji kerema xwe emaila xwe piştrast bike. Lînk hat şandin."
                            else "Lütfen e-posta adresini doğrula. Doğrulama bağlantısı gönderildi.",
                            color    = Amber,
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { showVerifyBanner = false }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, null, tint = Amber, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            NavHost(
                navController    = navController,
                startDestination = Screen.Feed.route,
                modifier         = Modifier.padding(innerPadding),
            ) {
                composable(Screen.Blog.route) {
                    BlogScreen(navController = navController, vm = blogVm, language = language)
                }
                composable("blog_post/{postId}") { back ->
                    BlogPostScreen(
                        postId        = back.arguments?.getString("postId") ?: "",
                        navController = navController,
                        vm            = blogVm,
                    )
                }
                composable(Screen.Feed.route) {
                    FeedScreen(navController = navController, language = language)
                }
                composable(Screen.Search.route) { SearchScreen(navController, language = language) }
                composable(Screen.Serials.route) { BooksScreen(navController, language) }
                composable(Screen.Library.route) { LibraryScreen(navController, language) }
                composable(Screen.Kurdi.route)   { KurdiScreen(language = language, adminVm = adminVm) }
                composable("profile/{uid}") { back ->
                    ProfileScreen(
                        uid           = back.arguments?.getString("uid") ?: "me",
                        navController = navController,
                        language      = language,
                    )
                }
                composable(Screen.Messages.route) {
                    ConversationsScreen(navController, language)
                }
                composable("message/{convId}") { back ->
                    MessageDetailScreen(
                        convId        = back.arguments?.getString("convId") ?: "",
                        navController = navController,
                        language      = language,
                    )
                }
                composable(Screen.Notifications.route) {
                    NotificationsScreen(navController, notifVm, language = language)
                }
                composable(Screen.EditProfile.route) { EditProfileScreen(navController, language = language) }

                composable(Screen.CmsPage.route) { back ->
                    val slug = back.arguments?.getString("slug") ?: ""
                    CmsPageScreen(navController = navController, slug = slug)
                }
                composable(Screen.Admin.route)    { AdminScreen(navController) }
                composable(Screen.Cms.route)      { CmsScreen(navController) }
                composable(Screen.Yazar.route)    { YazarScreen(navController) }
                composable(Screen.KurdiAdmin.route) {
                    if (isAdmin) KurdiAdminScreen(navController)
                    else { LaunchedEffect(Unit) { navController.popBackStack() } }
                }
                composable(Screen.Settings.route) { SettingsScreen(navController) }
                composable("post/{postId}") { back ->
                    SinglePostScreen(
                        postId        = back.arguments?.getString("postId") ?: "",
                        navController = navController,
                        language      = language,
                    )
                }
                // serial/{id} → birleşik BookDetailScreen'e yönlendir (type=serial)
                composable("serial/{id}") { back ->
                    BookDetailScreen(
                        bookId        = back.arguments?.getString("id") ?: "",
                        type          = "serial",
                        navController = navController,
                        language      = language,
                    )
                }
                // chapter/{sid}/{cid} → birleşik okuma ekranına yönlendir
                composable("chapter/{sid}/{cid}") { back ->
                    BookChapterReadScreen(
                        parentId      = back.arguments?.getString("sid") ?: "",
                        chapterId     = back.arguments?.getString("cid") ?: "",
                        type          = "serial",
                        navController = navController,
                        language      = language,
                    )
                }
                // ── Kütüphane: Yazar Detay ─────────────────────────────
                composable("author_detail/{authorId}") { back ->
                    AuthorDetailScreen(
                        authorId      = back.arguments?.getString("authorId") ?: "",
                        navController = navController,
                    )
                }
                // ── Kütüphane: Kitap Detay ─────────────────────────────
                composable("library_book_detail/{bookId}") { back ->
                    LibraryBookDetailScreen(
                        bookId        = back.arguments?.getString("bookId") ?: "",
                        navController = navController,
                    )
                }
                // ── Legacy: ad üzerinden akıllı yönlendirme ─────────────
                // Firestore'da önce library kaydı aranır; bulunursa detail ekranı açılır,
                // bulunamazsa eski liste ekranı gösterilir (tam uyumluluk).
                composable("author_quotes/{author}") { back ->
                    val author = java.net.URLDecoder.decode(
                        back.arguments?.getString("author") ?: "", "UTF-8")
                    AuthorQuotesSmartScreen(
                        authorName    = author,
                        navController = navController,
                    )
                }
                composable("book_quotes/{book}") { back ->
                    val book = java.net.URLDecoder.decode(
                        back.arguments?.getString("book") ?: "", "UTF-8")
                    BookQuotesSmartScreen(
                        bookName      = book,
                        navController = navController,
                    )
                }
                // Adım 3.1 — Screen.Books route kaldırıldı. Screen.Serials.route tek giriş noktası.
                // book/{bookId}?type=book|serial
                composable(
                    route = "book/{bookId}?type={type}",
                    arguments = listOf(
                        androidx.navigation.navArgument("bookId") { type = androidx.navigation.NavType.StringType },
                        androidx.navigation.navArgument("type")   { type = androidx.navigation.NavType.StringType; defaultValue = "book" },
                    )
                ) { back ->
                    BookDetailScreen(
                        bookId        = back.arguments?.getString("bookId") ?: "",
                        type          = back.arguments?.getString("type") ?: "book",
                        navController = navController,
                        language      = language,
                    )
                }
                composable(Screen.SavedPosts.route) {
                    SavedPostsScreen(navController = navController)
                }
                // book_chapter/{bid}/{cid}?type=book|serial
                composable(
                    route = "book_chapter/{bid}/{cid}?type={type}",
                    arguments = listOf(
                        androidx.navigation.navArgument("bid")  { type = androidx.navigation.NavType.StringType },
                        androidx.navigation.navArgument("cid")  { type = androidx.navigation.NavType.StringType },
                        androidx.navigation.navArgument("type") { type = androidx.navigation.NavType.StringType; defaultValue = "book" },
                    )
                ) { back ->
                    BookChapterReadScreen(
                        parentId      = back.arguments?.getString("bid") ?: "",
                        chapterId     = back.arguments?.getString("cid") ?: "",
                        type          = back.arguments?.getString("type") ?: "book",
                        navController = navController,
                        language      = language,
                    )
                }
                composable("reading_list/{uid}") { back ->
                    ReadingListScreen(
                        uid           = back.arguments?.getString("uid") ?: "",
                        navController = navController,
                        language      = language,
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
    staffPerms  : StaffPermissions = StaffPermissions(),
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
            val notifLabel = Strings.navNotifs(language) + if (unreadNotif > 0) " ($unreadNotif)" else ""
            val msgLabel   = Strings.navMessages(language) + if (totalUnread > 0) " ($totalUnread)" else ""
            val items = listOf(
                Triple(Icons.Outlined.DynamicFeed,       Strings.navFeed(language),     Screen.Feed.route),
                Triple(Icons.Outlined.Search,            Strings.navSearch(language),   Screen.Search.route),
                Triple(Icons.Outlined.AutoStories,       Strings.navBooks(language),    Screen.Serials.route),
                Triple(Icons.Outlined.Translate,         Strings.navKurdi(language),    Screen.Kurdi.route),
                Triple(Icons.Outlined.NotificationsNone, notifLabel,                    Screen.Notifications.route),
                Triple(Icons.Outlined.ChatBubbleOutline, msgLabel,                      Screen.Messages.route),
                Triple(Icons.Outlined.Settings,          Strings.navSettings(language), Screen.Settings.route),
                Triple(Icons.Outlined.Edit,              Strings.yazarTitle(language),  Screen.Yazar.route),
                Triple(Icons.Outlined.Bookmarks,         Strings.savedPosts(language),  Screen.SavedPosts.route),
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

            if (isAdmin || staffPerms.isStaff()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onNavigate(Screen.Cms.route) }
                        .padding(horizontal = 10.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Dashboard, null, tint = Amber, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(if (language == "ku") "CMS Birêvebirî" else "CMS Yönetimi", color = Amber, fontSize = 14.sp)
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
                Text(if (isDark) (if (language == "ku") "Moda Tarî" else "Karanlık") else (if (language == "ku") "Moda Ronahî" else "Aydınlık"), color = OnBackground, fontSize = 13.sp, modifier = Modifier.weight(1f))
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

            Spacer(Modifier.height(6.dp))

            // ── Dil değişimi ───────────────────────────────────────────
            Row(
                modifier          = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(Icons.Outlined.Translate, null, tint = Amber, modifier = Modifier.size(18.dp))
                Button(
                    onClick  = { settingsVm.setLanguage(if (language == "tr") "ku" else "tr") },
                    modifier = Modifier.weight(1f),
                    shape    = RoundedCornerShape(8.dp),
                    colors   = ButtonDefaults.buttonColors(
                        containerColor = Amber,
                        contentColor   = Color.Black,
                    ),
                    contentPadding = PaddingValues(vertical = 6.dp),
                ) {
                    Text(
                        if (language == "ku") "Kurdî" else "Türkçe",
                        fontSize   = 13.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // Çıkış
            TextButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.Logout, null, tint = Color(0xFFEF4444), modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(Strings.logout(language) + if (language == "ku") "" else " / Derketin", color = Color(0xFFEF4444), fontSize = 13.sp)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Instagram Stili Hesap Değiştirici
//  Tetikleme: Bottom bar profil ikonuna uzun basış
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun InstagramAccountSwitcherDialog(
    accounts    : List<AuthViewModel.SavedAccount>,
    currentEmail: String,
    language    : String,
    onSelect    : (AuthViewModel.SavedAccount) -> Unit,
    onRemove    : (String) -> Unit,
    onAddAccount: () -> Unit,
    onDismiss   : () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape         = RoundedCornerShape(20.dp),
            color         = HeftSurface,
            tonalElevation = 8.dp,
            modifier       = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(vertical = 8.dp)) {

                // Başlık
                Text(
                    if (language == "ku") "Hesabê hilbijêre" else "Hesap seç",
                    modifier   = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                    color      = OnBackground,
                    fontWeight = FontWeight.Bold,
                    fontSize   = 16.sp,
                )

                HorizontalDivider(color = Divider)

                // Hesap listesi
                accounts.forEach { account ->
                    val isCurrent = account.email == currentEmail
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (!isCurrent)
                                    Modifier.clickable { onSelect(account) }
                                else Modifier
                            )
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        // Avatar
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(CircleShape)
                                .background(Primary.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (account.photoURL.isNotBlank()) {
                                AsyncImage(
                                    model              = account.photoURL,
                                    contentDescription = null,
                                    modifier           = Modifier.fillMaxSize(),
                                    contentScale       = ContentScale.Crop,
                                )
                            } else {
                                Text(
                                    account.displayName.firstOrNull()?.uppercase() ?: "?",
                                    color      = Primary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize   = 18.sp,
                                )
                            }
                            // Aktif yeşil nokta
                            if (isCurrent) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .size(13.dp)
                                        .clip(CircleShape)
                                        .background(HeftSurface)
                                        .padding(2.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(CircleShape)
                                            .background(Color(0xFF22C55E))
                                    )
                                }
                            }
                        }

                        // İsim + email
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                account.displayName.ifBlank { account.email.substringBefore("@") },
                                color      = OnBackground,
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                fontSize   = 15.sp,
                                maxLines   = 1,
                            )
                            Text(
                                account.email,
                                color    = Muted,
                                fontSize = 12.sp,
                                maxLines = 1,
                            )
                        }

                        // Aktif checkmark veya kaldır butonu
                        if (isCurrent) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint     = Amber,
                                modifier = Modifier.size(22.dp),
                            )
                        } else {
                            IconButton(
                                onClick  = { onRemove(account.email) },
                                modifier = Modifier.size(34.dp),
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = null,
                                    tint     = Muted,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                    HorizontalDivider(
                        color    = Divider,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }

                // Hesap ekle butonu
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onAddAccount() }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Box(
                        modifier         = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(SurfaceVar),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Default.Add, null, tint = OnBackground, modifier = Modifier.size(22.dp))
                    }
                    Text(
                        if (language == "ku") "Hesabekî din lê zêde bike" else "Hesap ekle",
                        color      = OnBackground,
                        fontWeight = FontWeight.Medium,
                        fontSize   = 15.sp,
                    )
                }
            }
        }
    }
}
