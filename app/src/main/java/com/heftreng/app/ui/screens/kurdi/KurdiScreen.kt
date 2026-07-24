package com.heftreng.app.ui.screens.kurdi

// ═══════════════════════════════════════════════════════════════════════════
//  KurdiScreen — Site teması (heft-reng.blogspot.com) ile tam uyumlu
//
//  Site yapısı:
//  - kf_units   → Üniteler (Destpêk, Jimare, Reng…)
//  - kf_lessons → Her üniteye ait dersler
//  - kf_vocab   → Kelime kartları
//  - kf_exercises → Sorular (mcq / fill / match)
//
//  Ekranlar:
//  1. Ana ekran  → XP kartı + günlük hedef + ünite yol haritası
//  2. Ders ekranı → Kelime flash cards + sorular + XP ödülü
//  3. Ferheng    → Sözlük (kf_dict, Firebase)
//  4. Rêziman    → Dilbilgisi (kf_grammar, Firebase)
//  5. AI Ders    → OpenRouter Gemini ile üretilen ders
// ═══════════════════════════════════════════════════════════════════════════

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.rememberGraphicsLayer
import com.heftreng.app.utils.ShareTarget
import com.heftreng.app.utils.shareBitmap
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.debounce
import androidx.compose.foundation.clickable
import androidx.navigation.NavController
import com.heftreng.app.ui.i18n.Strings
import com.heftreng.app.ui.theme.*
import com.heftreng.app.ads.AdPlacement
import com.heftreng.app.ads.RemoteConfigManager
import com.heftreng.app.ui.component.AdSlotView
import com.heftreng.app.ui.component.RichTextEditor
import com.heftreng.app.ui.component.spansToHtml
import com.heftreng.app.ui.component.htmlStrip
import com.heftreng.app.ui.component.htmlToSpans
import com.heftreng.app.viewmodel.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.graphicsLayer

// -- Ana ekran ----------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KurdiScreen(
    language : String = "tr",
    vm       : KurdiViewModel = hiltViewModel(),
    adminVm  : AdminViewModel,
    adsVm    : AdsViewModel = hiltViewModel(),
    navController : NavController? = null,
    feedVm   : FeedViewModel = hiltViewModel(),
    deepLinkLessonId  : String? = null,
    deepLinkGrammarId : String? = null,
) {
    val units       by vm.units.collectAsState()
    val lessons     by vm.lessons.collectAsState()
    val doneIds     by vm.doneIds.collectAsState()
    val xp          by vm.xp.collectAsState()
    val streak      by vm.streak.collectAsState()
    val level       by vm.level.collectAsState()
    val loading     by vm.loading.collectAsState()
    val activeLesson by vm.activeLesson.collectAsState()
    val toast       by vm.toast.collectAsState()
    val isAdmin     by adminVm.isAdmin.collectAsState()
    val lastLessonXp    by vm.lastLessonXp.collectAsState()
    val tempUnlockedIds by vm.tempUnlockedIds.collectAsState()
    val streakBroke     by vm.streakBroke.collectAsState()
    val canDoubleXp             = adsVm.canShowScenario(AdsViewModel.RewardType.DOUBLE_XP)
    val canUnlockLesson         = adsVm.canShowScenario(AdsViewModel.RewardType.UNLOCK_LESSON)
    val canSaveStreak           = adsVm.canShowScenario(AdsViewModel.RewardType.SAVE_STREAK)
    val remainingAds            = adsVm.remainingRewardedAds.collectAsState().value
    // Reklam config'i UnitsTab içinde, kendi plan'ını hesaplar (bkz. UnitsTab).

    // Native ad havuzunu önceden doldur — CMS config beklemeden ANINDA tetiklenir.
    val context  = androidx.compose.ui.platform.LocalContext.current
    val activity = context as? android.app.Activity

    // adsVm.initPrefs(context) ve adsVm.loadAdConfigs() KALDIRILDI:
    // initPrefs() boş bir no-op'tu; loadAdConfigs() ise config'i AdsViewModel.init{}
    // içinde zaten yüklenmişken her Kürtçe Dersler ekranı açılışında tekrar
    // Firestore'a gidiyordu (gereksiz gecikme + double-read).

    // Ödüllü reklam sonrası uygulanan senaryo
    var pendingRewardType by remember { mutableStateOf<AdsViewModel.RewardType?>(null) }
    var pendingUnlockId   by remember { mutableStateOf("") }
    // Dialog state — kilitli derse tıklanınca reklam izle/iptal seçeneği gösterilir
    var unlockDialogLessonId    by remember { mutableStateOf("") }
    var unlockDialogLessonTitle by remember { mutableStateOf("") }

    // -- Kilit Açma Dialog — reklam izleyerek dersi aç -------------------------
    if (unlockDialogLessonId.isNotBlank()) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { unlockDialogLessonId = ""; unlockDialogLessonTitle = "" },
            containerColor   = com.heftreng.app.ui.theme.HeftSurface,
            icon = {
                androidx.compose.foundation.layout.Box(
                    modifier = androidx.compose.ui.Modifier
                        .size(48.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = androidx.compose.ui.Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = androidx.compose.ui.Modifier.size(28.dp),
                    )
                }
            },
            title = {
                androidx.compose.material3.Text(
                    text = if (language == "ku") "Ders Kilîtkirî" else "Ders Kilitli",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                )
            },
            text = {
                androidx.compose.material3.Text(
                    text = if (language == "ku")
                        ""${unlockDialogLessonTitle}" dersê kilît e. Ji bo vekirin reklamekê temaşe bike."
                    else
                        ""${unlockDialogLessonTitle}" dersi kilitli. Kilidi açmak için bir reklam izle.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            confirmButton = {
                val lockedIdSnapshot = unlockDialogLessonId
                androidx.compose.material3.Button(
                    onClick = {
                        unlockDialogLessonId = ""
                        unlockDialogLessonTitle = ""
                        activity?.let {
                            adsVm.showRewarded(
                                activity     = it,
                                rewardType   = AdsViewModel.RewardType.UNLOCK_LESSON,
                                onRewarded   = { _, _ ->
                                    vm.tempUnlockLesson(lockedIdSnapshot)
                                    vm.openLesson(lockedIdSnapshot)
                                },
                                onAdNotReady = { vm.showAdNotReadyToast(language) },
                            )
                        }
                    }
                ) {
                    Icon(Icons.Filled.PlayCircle, contentDescription = null,
                        modifier = androidx.compose.ui.Modifier.size(16.dp))
                    androidx.compose.foundation.layout.Spacer(
                        androidx.compose.ui.Modifier.width(6.dp))
                    androidx.compose.material3.Text(
                        if (language == "ku") "Reklamê Temaşe Bike" else "Reklamı İzle"
                    )
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(
                    onClick = { unlockDialogLessonId = ""; unlockDialogLessonTitle = "" }
                ) {
                    androidx.compose.material3.Text(
                        if (language == "ku") "Bişkojk" else "İptal"
                    )
                }
            },
        )
    }

    // -- Senaryo 3 — Streak Kurtarma Dialog -----------------------------------
    if (streakBroke && canSaveStreak) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { vm.dismissStreakBroke() },
            containerColor   = com.heftreng.app.ui.theme.HeftSurface,
            title = {
                androidx.compose.foundation.layout.Row(
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                ) {
                    Text("🔥", fontSize = 28.sp)
                    Text(
                        if (language == "ku") "Zincîra te qut bû!" else "Seriniz bozuldu!",
                        color = com.heftreng.app.ui.theme.OnBackground,
                        fontWeight = FontWeight.Bold,
                    )
                }
            },
            text = {
                Text(
                    if (language == "ku") "Duh ders nexwendî û zincîra te sifir bû. Vîdyoyek kurt temaşe bike û zincîra xwe xilas bike!"
                    else "Dün ders çalışmayı unuttun. Kısa bir video izleyerek serinizi kurtarabilirsin!",
                    color = com.heftreng.app.ui.theme.Muted,
                )
            },
            confirmButton = {
                androidx.compose.material3.Button(
                    onClick = {
                        activity?.let {
                            adsVm.showRewarded(
                                activity    = it,
                                rewardType  = AdsViewModel.RewardType.SAVE_STREAK,
                                onRewarded  = { _, _ -> vm.saveStreak() },
                                onDismiss   = { vm.dismissStreakBroke() },
                                onLimitReached = { vm.dismissStreakBroke() },
                                onAdNotReady   = { vm.showAdNotReadyToast(language); vm.dismissStreakBroke() },
                            )
                        }
                    },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFEF4444),
                        contentColor   = Color.White,
                    ),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text(
                        if (language == "ku") "🔥 Zincîrê Xilas Bike" else "🔥 Seriyi Kurtar",
                        fontWeight = FontWeight.Bold,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { vm.dismissStreakBroke() }) {
                    Text(if (language == "ku") "Berde" else "Vazgeç", color = com.heftreng.app.ui.theme.Muted)
                }
            },
        )
    }

    var selectedTab by remember { mutableStateOf(if (deepLinkGrammarId != null) 1 else 0) }
    // YZ Ders sekmesi sadece admin'e görünür
    val tabs = buildList {
        add(Strings.kurdiUnits(language))
        // add(Strings.kurdiDict(language))  // Ferheng — geçici olarak gizlendi
        add(Strings.kurdiGrammar(language))
        add(Strings.kurdiLeaderboard(language))
    }

    // ── Deep-link: feed'den gelen "dersi aç" isteği — dersler yüklenince tetiklenir ──
    // HATA DÜZELTMESİ: Önceden kilit kontrolü yapılmıyordu, kilitli dersler de açılıyordu.
    // Şimdi: doneIds ve tempUnlockedIds kontrol ediliyor; kilitliyse unlock dialog gösteriliyor.
    var deepLinkLessonHandled by remember { mutableStateOf(false) }
    var deepLinkLockedId      by remember { mutableStateOf("") }
    LaunchedEffect(deepLinkLessonId, lessons, doneIds) {
        if (deepLinkLessonId != null && !deepLinkLessonHandled && lessons.any { it.id == deepLinkLessonId }) {
            deepLinkLessonHandled = true
            val idx         = lessons.indexOfFirst { it.id == deepLinkLessonId }
            val firstNotDone = lessons.indexOfFirst { it.id !in doneIds }.takeIf { it >= 0 } ?: 0
            val isTempUnlocked = deepLinkLessonId in tempUnlockedIds
            val isLocked    = idx > firstNotDone && deepLinkLessonId !in doneIds && !isTempUnlocked
            if (!isLocked) {
                vm.openLesson(deepLinkLessonId)
            } else {
                // Kilitli — ödüllü reklam dialog'unu tetikle (normal ders listesiyle aynı akış)
                deepLinkLockedId = deepLinkLessonId
            }
        }
    }
    // Deep-link kilitli ders → dialog göster (direkt reklam açma!)
    LaunchedEffect(deepLinkLockedId) {
        if (deepLinkLockedId.isNotBlank()) {
            val lockedId = deepLinkLockedId
            deepLinkLockedId = ""
            val lesson = lessons.find { it.id == lockedId }
            unlockDialogLessonTitle = if (language == "ku")
                (lesson?.nameKu?.ifBlank { lesson.nameTr } ?: "")
            else
                (lesson?.nameTr ?: "")
            unlockDialogLessonId = lockedId
        }
    }

    // ── Deep-link: feed'den gelen "gramer kuralını aç" isteği — GrammarTab'a iletilir ──
    var deepLinkGrammarHandled by remember { mutableStateOf(false) }

    // Toast
    LaunchedEffect(toast) {
        if (toast != null) kotlinx.coroutines.delay(2000)
        vm.clearToast()
    }

    // Tamamlanan son dersi tutar — Çift XP sheet'inde "Feed'de Paylaş" butonu için
    var lastCompletedLesson by remember { mutableStateOf<KfLesson?>(null) }

    // Aktif ders varsa ders ekranını göster
    // -- Senaryo 1 — Çift XP BottomSheet (ders tamamlandıktan sonra) ----------
    var showDoubleXpSheet by remember { mutableStateOf(false) }
    LaunchedEffect(lastLessonXp) {
        if (lastLessonXp > 0) showDoubleXpSheet = true
    }
    if (showDoubleXpSheet && canDoubleXp) {
        androidx.compose.material3.ModalBottomSheet(
            onDismissRequest = { showDoubleXpSheet = false },
            containerColor   = com.heftreng.app.ui.theme.HeftSurface,
        ) {
            androidx.compose.foundation.layout.Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
            ) {
                Text("🎉", fontSize = 48.sp)
                Text(
                    Strings.doubleXpTitle(language, lastLessonXp),
                    color = com.heftreng.app.ui.theme.OnBackground,
                    fontWeight = FontWeight.Bold, fontSize = 18.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                Surface(shape = RoundedCornerShape(12.dp), color = com.heftreng.app.ui.theme.Amber.copy(0.15f)) {
                    Text(
                        Strings.doubleXpOffer(language, lastLessonXp * 2),
                        color = com.heftreng.app.ui.theme.Amber, fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    )
                }
                androidx.compose.material3.Button(
                    onClick = {
                        showDoubleXpSheet = false
                        activity?.let {
                            adsVm.showRewarded(
                                activity     = it,
                                rewardType   = AdsViewModel.RewardType.DOUBLE_XP,
                                onRewarded   = { _, _ -> vm.doubleLastLessonXp() },
                                onAdNotReady = { vm.showAdNotReadyToast(language) },
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape    = RoundedCornerShape(12.dp),
                    colors   = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = com.heftreng.app.ui.theme.Amber, contentColor = Color.Black,
                    ),
                ) { Text(Strings.doubleXpClaim(language), fontWeight = FontWeight.Bold) }
                // Dersi feed'de paylaş — tamamlanan son ders bilgisi vm.lastCompletedLesson'da
                lastCompletedLesson?.let { lc ->
                    OutlinedButton(
                        onClick = {
                            showDoubleXpSheet = false
                            feedVm.repostKfLesson(
                                lessonId    = lc.id,
                                lessonTitle = if (language == "ku") lc.nameKu.ifBlank { lc.nameTr } else lc.nameTr,
                                lessonTip   = lc.tip,
                                emoji       = lc.emoji,
                                onResult    = { ok ->
                                    vm.showToast(if (ok) Strings.shareLessonSuccess(language) else Strings.shareFailed(language))
                                },
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape    = RoundedCornerShape(12.dp),
                    ) {
                        Icon(Icons.Default.Share, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(Strings.shareToFeed(language))
                    }
                }
                TextButton(onClick = { showDoubleXpSheet = false }) {
                    Text(Strings.noThanks(language), color = com.heftreng.app.ui.theme.Muted)
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    if (activeLesson != null) {
        LessonScreen(
            activeLesson = activeLesson!!,
            language     = language,
            onComplete   = { earned ->
                lastCompletedLesson = activeLesson!!.lesson
                vm.completeLesson(activeLesson!!.lesson.id, earned)
                vm.closeLesson()
            },
            onClose      = { vm.closeLesson() },
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .statusBarsPadding()
    ) {
        // Başlık
        Text(
            Strings.kurdiTitle(language),
            fontWeight = FontWeight.ExtraBold,
            color      = OnBackground,
            fontSize   = 20.sp,
            modifier   = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 8.dp),
        )

        // XP & Streak kartı
        var showAchievementShareSheet by remember { mutableStateOf(false) }
        var achievementShareTarget by remember { mutableStateOf<com.heftreng.app.utils.ShareTarget?>(null) }

        XpStreakCard(xp = xp, streak = streak, level = level, language = language,
            remainingAds = remainingAds,
            onShare = { showAchievementShareSheet = true })

        // Paylaş menüsü — Ana Sayfa (feed) / Instagram / WhatsApp / Diğer
        if (showAchievementShareSheet) {
            androidx.compose.material3.ModalBottomSheet(
                onDismissRequest = { showAchievementShareSheet = false },
                containerColor   = com.heftreng.app.ui.theme.HeftSurface,
            ) {
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                    // Ana Sayfa — feed'e post olarak paylaş
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showAchievementShareSheet = false
                                feedVm.repostKfAchievement(
                                    level = level, xp = xp, streak = streak,
                                    onResult = { ok ->
                                        vm.showToast(if (ok) Strings.shareAchievementSuccess(language) else Strings.shareFailed(language))
                                    },
                                )
                            }
                            .padding(vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(Icons.Default.DynamicFeed, null, tint = Primary, modifier = Modifier.size(20.dp))
                        Text(Strings.shareHomeFeed(language), color = com.heftreng.app.ui.theme.OnBackground, fontSize = 15.sp)
                    }
                    // Instagram / WhatsApp / Diğer — görsel kart olarak dışa paylaş
                    listOf(
                        Triple("Instagram", Color(0xFFE1306C), com.heftreng.app.utils.ShareTarget.INSTAGRAM),
                        Triple("WhatsApp", Color(0xFF25D366), com.heftreng.app.utils.ShareTarget.WHATSAPP),
                        Triple(if (language == "ku") "Yên Din" else "Diğer", com.heftreng.app.ui.theme.Muted, com.heftreng.app.utils.ShareTarget.ANY),
                    ).forEach { (label, tint, target) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showAchievementShareSheet = false
                                    achievementShareTarget = target
                                }
                                .padding(vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(Icons.Default.Share, null, tint = tint, modifier = Modifier.size(20.dp))
                            Text(label, color = com.heftreng.app.ui.theme.OnBackground, fontSize = 15.sp)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }

        // Paylaşım önizleme dialogu — başarı kartını bitmap'e çevirip seçilen hedefe gönderir
        if (achievementShareTarget != null) {
            val fbUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
            com.heftreng.app.ui.component.SharePreviewDialog(
                post = com.heftreng.app.data.model.Post(
                    displayName  = fbUser?.displayName ?: "",
                    photoURL     = fbUser?.photoUrl?.toString() ?: "",
                    repostType   = "kf_achievement",
                    repostLevel  = level,
                    repostXp     = xp,
                    repostStreak = streak,
                ),
                target    = achievementShareTarget!!,
                onDismiss = { achievementShareTarget = null },
                language  = language,
            )
        }

        Spacer(Modifier.height(8.dp))

        // Sekmeler
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor   = Background,
            contentColor     = Primary,
            indicator        = { tabPositions ->
                if (tabPositions.isNotEmpty() && selectedTab < tabPositions.size) {
                    Box(
                        Modifier
                            .tabIndicatorOffset(tabPositions[selectedTab])
                            .height(2.dp)
                            .background(Primary)
                    )
                }
            },
        ) {
            tabs.forEachIndexed { i, title ->
                Tab(
                    selected               = selectedTab == i,
                    onClick                = { selectedTab = i },
                    text                   = { Text(title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                    selectedContentColor   = Primary,
                    unselectedContentColor = Muted,
                )
            }
        }

        // İçerik
        when (selectedTab) {
            0 -> UnitsTab(
                units           = units,
                lessons         = lessons,
                doneIds         = doneIds,
                loading         = loading,
                language        = language,
                tempUnlockedIds = tempUnlockedIds,
                canWatchAd      = canUnlockLesson,
                adsVm           = adsVm,
                onNext   = { vm.getNextLesson()?.let { vm.openLesson(it.id) } },
                onOpen   = { lessonId -> vm.openLesson(lessonId) },
                onShare  = { lesson ->
                    feedVm.repostKfLesson(
                        lessonId    = lesson.id,
                        lessonTitle = if (language == "ku") lesson.nameKu.ifBlank { lesson.nameTr } else lesson.nameTr,
                        lessonTip   = lesson.tip,
                        emoji       = lesson.emoji,
                        onResult    = { ok ->
                            vm.showToast(if (ok) Strings.shareLessonSuccess(language) else Strings.shareFailed(language))
                        },
                    )
                },
                onLockedClick = { lessonId ->
                    val lesson = lessons.find { it.id == lessonId }
                    unlockDialogLessonTitle = if (language == "ku")
                        (lesson?.nameKu?.ifBlank { lesson.nameTr } ?: "")
                    else
                        (lesson?.nameTr ?: "")
                    unlockDialogLessonId = lessonId
                },
            )
            // 1 -> DictionaryTab — Ferheng geçici olarak gizlendi
            1 -> GrammarTab(
                language, isAdmin = isAdmin, vm = vm,
                deepLinkRuleId = if (!deepLinkGrammarHandled) deepLinkGrammarId else null,
                onDeepLinkHandled = { deepLinkGrammarHandled = true },
                feedVm = feedVm,
            )

            2 -> {
                val leaderboard        by vm.leaderboard.collectAsState()
                val leaderboardLoading by vm.leaderboardLoading.collectAsState()
                LaunchedEffect(Unit) { vm.loadLeaderboard() }
                LeaderboardTab(
                    entries       = leaderboard,
                    loading       = leaderboardLoading,
                    language      = language,
                    navController = navController,
                )
            }
        }
    }

    // Toast snackbar
    if (toast != null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            Surface(
                modifier = Modifier.padding(24.dp).navigationBarsPadding(),
                shape    = RoundedCornerShape(24.dp),
                color    = Primary,
            ) {
                Text(
                    toast ?: "",
                    color      = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize   = 14.sp,
                    modifier   = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                )
            }
        }
    }
}

// -- XP / Streak kartı --------------------------------------------------------
// ── Podium Bloğu (Liderlik Top 3) ────────────────────────────────────────────
@Composable
private fun PodiumBlock(
    entry      : KurdiViewModel.LeaderEntry,
    medalColor : Color,
    heightDp   : Int,
    ku         : Boolean,
    onClick    : () -> Unit = {},
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom,
        modifier = Modifier.width(90.dp).clickable(onClick = onClick),
    ) {
        // İsim
        Text(
            entry.displayName.ifBlank { if (ku) "Bikarhêner" else "Kullanıcı" },
            color      = OnBackground,
            fontSize   = 10.sp,
            fontWeight = FontWeight.Medium,
            maxLines   = 1,
            overflow   = TextOverflow.Ellipsis,
            textAlign  = TextAlign.Center,
        )
        Spacer(Modifier.height(3.dp))
        // XP
        Text(
            "${entry.kfXp} XP",
            color    = medalColor,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(4.dp))
        // Avatar + madalya
        Box(contentAlignment = Alignment.BottomEnd) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(Primary, PrimaryLight)))
                    .border(2.dp, medalColor, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (entry.photoURL.isNotBlank()) {
                    coil.compose.AsyncImage(
                        model = coil.request.ImageRequest.Builder(
                            androidx.compose.ui.platform.LocalContext.current
                        ).data(entry.photoURL).crossfade(true).build(),
                        contentDescription = null,
                        contentScale       = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier           = Modifier.fillMaxSize(),
                    )
                } else {
                    Text(
                        entry.displayName.firstOrNull()?.uppercase() ?: "?",
                        color      = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize   = 16.sp,
                    )
                }
            }
            Text(
                when (entry.rank) { 1 -> "🥇"; 2 -> "🥈"; else -> "🥉" },
                fontSize = 14.sp,
            )
        }
        Spacer(Modifier.height(4.dp))
        // Podyum bloğu
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(heightDp.dp)
                .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                .background(medalColor.copy(alpha = 0.18f))
                .border(
                    1.dp,
                    medalColor.copy(alpha = 0.4f),
                    RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "#${entry.rank}",
                color      = medalColor,
                fontWeight = FontWeight.Black,
                fontSize   = 16.sp,
            )
        }
    }
}

// ── Liderlik Tablosu Sekmesi ──────────────────────────────────────────────────
@Composable
private fun LeaderboardTab(
    entries  : List<KurdiViewModel.LeaderEntry>,
    loading  : Boolean,
    language : String,
    navController : NavController? = null,
) {
    val ku     = language == "ku"
    val gold   = Color(0xFFFFD700)
    val silver = Color(0xFFC0C0C0)
    val bronze = Color(0xFFCD7F32)

    Column(modifier = Modifier.fillMaxSize()) {
        // Gradient başlık banner
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(listOf(Color(0xFF1A1333), Color(0xFF0F1E2E)))
                )
                .padding(horizontal = 20.dp, vertical = 18.dp),
        ) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("🏆", fontSize = 28.sp)
                    Column {
                        Text(
                            Strings.leaderboardTitle(language),
                            fontWeight = FontWeight.Black,
                            fontSize   = 18.sp,
                            color      = OnBackground,
                        )
                        Text(
                            if (ku) "Baştirîn 20 xwendekar" else "En iyi 20 öğrenci",
                            color    = Muted,
                            fontSize = 12.sp,
                        )
                    }
                }
                // Top 3 podium (sadece yeterli veri varsa)
                if (entries.size >= 3) {
                    Spacer(Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        // 2. sıra
                        entries.getOrNull(1)?.let { e ->
                            PodiumBlock(entry = e, medalColor = silver, heightDp = 70, ku = ku,
                                onClick = { navController?.navigate("profile/${e.uid}") })
                        }
                        // 1. sıra (ortada + daha yüksek)
                        entries.getOrNull(0)?.let { e ->
                            PodiumBlock(entry = e, medalColor = gold, heightDp = 90, ku = ku,
                                onClick = { navController?.navigate("profile/${e.uid}") })
                        }
                        // 3. sıra
                        entries.getOrNull(2)?.let { e ->
                            PodiumBlock(entry = e, medalColor = bronze, heightDp = 58, ku = ku,
                                onClick = { navController?.navigate("profile/${e.uid}") })
                        }
                    }
                }
            }
        }
        HorizontalDivider(color = Divider, thickness = 0.5.dp)

        when {
            loading -> Box(
                Modifier.fillMaxWidth().height(300.dp),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator(color = Primary, modifier = Modifier.size(32.dp)) }

            entries.isEmpty() -> Box(
                Modifier.fillMaxWidth().height(300.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("🏅", fontSize = 40.sp)
                    Text(Strings.leaderboardEmpty(language), color = Muted, fontSize = 14.sp)
                }
            }

            else -> LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
                items(entries, key = { it.uid }) { entry ->
                    val rankColor = when (entry.rank) {
                        1 -> gold
                        2 -> silver
                        3 -> bronze
                        else -> Muted
                    }
                    val rowBg = if (entry.isMe) Primary.copy(alpha = 0.08f) else Color.Transparent
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(rowBg)
                            .clickable { navController?.navigate("profile/${entry.uid}") }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        // Sıra numarası / madalya
                        Box(
                            modifier = Modifier.size(32.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            when (entry.rank) {
                                1 -> Text("🥇", fontSize = 22.sp)
                                2 -> Text("🥈", fontSize = 22.sp)
                                3 -> Text("🥉", fontSize = 22.sp)
                                else -> Text(
                                    "${entry.rank}.",
                                    color      = rankColor,
                                    fontWeight = FontWeight.Bold,
                                    fontSize   = 14.sp,
                                )
                            }
                        }

                        // Avatar
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(listOf(Primary, PrimaryLight))
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (entry.photoURL.isNotBlank()) {
                                coil.compose.AsyncImage(
                                    model = coil.request.ImageRequest.Builder(
                                        androidx.compose.ui.platform.LocalContext.current
                                    ).data(entry.photoURL).crossfade(true).build(),
                                    contentDescription = null,
                                    contentScale       = androidx.compose.ui.layout.ContentScale.Crop,
                                    modifier           = Modifier.fillMaxSize(),
                                )
                            } else {
                                Text(
                                    entry.displayName.firstOrNull()?.uppercase() ?: "?",
                                    color      = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize   = 16.sp,
                                )
                            }
                        }

                        // İsim + XP
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    entry.displayName.ifBlank { if (ku) "Bikarhêner" else "Kullanıcı" },
                                    color      = OnBackground,
                                    fontWeight = if (entry.isMe) FontWeight.Bold else FontWeight.Medium,
                                    fontSize   = 14.sp,
                                    maxLines   = 1,
                                    overflow   = TextOverflow.Ellipsis,
                                    modifier   = Modifier.weight(1f, fill = false),
                                )
                                if (entry.isMe) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = Primary,
                                    ) {
                                        Text(
                                            Strings.leaderboardYou(language),
                                            color    = Color.White,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        )
                                    }
                                }
                            }
                            Text(
                                "${entry.kfXp} XP",
                                color      = Primary,
                                fontSize   = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }

                        // Kf_xp büyük gösterim (sağ taraf)
                        if (entry.rank <= 3) {
                            Text(
                                "★",
                                color    = rankColor,
                                fontSize = 18.sp,
                            )
                        }
                    }
                    HorizontalDivider(
                        color     = Divider,
                        thickness = 0.5.dp,
                        modifier  = Modifier.padding(start = 78.dp),
                    )
                }
            }
        }
    }
}

// ── XP / Streak Kartı ─────────────────────────────────────────────────────────
@Composable
private fun XpStreakCard(
    xp           : Int,
    streak       : Int,
    level        : Int,
    language     : String,
    remainingAds : Int = 3,
    onShare      : (() -> Unit)? = null,
) {
    val ku       = language == "ku"
    val progress = if (xp <= 0) 0f else ((xp % 100) / 100f).coerceIn(0f, 1f)
    val xpToNext = 100 - (xp % 100)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        // Gradient arka plan
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(
                    Brush.linearGradient(
                        listOf(
                            Primary.copy(alpha = 0.18f),
                            PrimaryLight.copy(alpha = 0.08f),
                        )
                    )
                )
                .border(1.dp, Primary.copy(alpha = 0.25f), RoundedCornerShape(20.dp))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Üst satır: Seviye rozeti + Streak + XP sayacı
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // Seviye rozeti
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(Primary),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                if (ku) "Ast" else "Sv.",
                                color    = Color.White.copy(alpha = 0.7f),
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                "$level",
                                color      = Color.White,
                                fontWeight = FontWeight.Black,
                                fontSize   = 18.sp,
                            )
                        }
                    }

                    // XP çubuğu
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "$xp XP",
                                color      = Primary,
                                fontWeight = FontWeight.Bold,
                                fontSize   = 14.sp,
                            )
                            Text(
                                if (ku) "+$xpToNext ast" else "+$xpToNext sonraki",
                                color    = Muted,
                                fontSize = 11.sp,
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(10.dp)
                                .clip(RoundedCornerShape(5.dp))
                                .background(SurfaceVar)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(progress)
                                    .height(10.dp)
                                    .background(
                                        Brush.horizontalGradient(listOf(Primary, PrimaryLight)),
                                        RoundedCornerShape(5.dp),
                                    )
                            )
                        }
                    }

                    // Streak
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Amber.copy(alpha = 0.12f))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    ) {
                        Text("🔥", fontSize = 20.sp)
                        Text(
                            "$streak",
                            fontWeight = FontWeight.Black,
                            color      = Amber,
                            fontSize   = 16.sp,
                        )
                        Text(
                            if (ku) "Zincîr" else "Seri",
                            color    = Muted,
                            fontSize = 9.sp,
                        )
                    }
                }

                // Reklam hakkı göstergesi
                if (remainingAds < 3) {
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider(color = Divider, thickness = 0.5.dp)
                    Spacer(Modifier.height(10.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text("▶️", fontSize = 13.sp)
                        repeat(3) { i ->
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (i < remainingAds) Amber else Divider)
                            )
                        }
                        Spacer(Modifier.width(2.dp))
                        Text(
                            if (ku) "$remainingAds mafê vîdyoyê mayî"
                            else    "$remainingAds ödüllü reklam hakkı kaldı",
                            color    = Muted,
                            fontSize = 11.sp,
                        )
                    }
                }

                // Başarını paylaş — tıklayınca Ana Sayfa/Instagram/WhatsApp/Diğer seçim menüsü açılır
                if (onShare != null) {
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onShare() }
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.Share, null, tint = Primary, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            Strings.shareAchievement(language),
                            color = Primary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

// -- Ünite yol haritası sekmesi -----------------------------------------------
@Composable
private fun UnitsTab(
    units           : List<KfUnit>,
    lessons         : List<KfLesson>,
    doneIds         : Set<String>,
    loading         : Boolean,
    language        : String,
    tempUnlockedIds : Set<String> = emptySet(),
    canWatchAd      : Boolean = false,
    adsVm           : AdsViewModel,
    onNext          : () -> Unit,
    onOpen          : (String) -> Unit,
    onLockedClick   : (String) -> Unit = {},
    onShare         : ((KfLesson) -> Unit)? = null,
) {
    when {
        loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Primary)
        }
        units.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("📚", fontSize = 40.sp)
                Spacer(Modifier.height(8.dp))
                Text(Strings.lessonNotFound(language), color = Muted, fontSize = 14.sp)
            }
        }
        else -> {
        val adConfigs by adsVm.allConfigs.collectAsState()
        val kurdiAdPlan = remember(units.size, adConfigs) {
            adsVm.planFor(
                screenKey = "kurdi",
                itemCount = units.size,
                nativeKey = RemoteConfigManager.KEY_NATIVE_KURDI,
                bannerKey = RemoteConfigManager.KEY_BANNER_KURDI,
            )
        }
        val kurdiListState = rememberLazyListState()

        // ── Reklam önden-ısıtma — diğer ekranlarla (Feed/Library/Blog) aynı desen ──
        // ÖNCEDEN: kurdiAdPlan hesaplanıyor ve AdSlotView render ediliyordu ama
        // hiçbir yerde warmVisiblePositions çağrılmıyordu — yani requestBanner/
        // requestNative HİÇ tetiklenmiyordu. Sonuç: kullanıcı reklam pozisyonuna
        // gelse bile isLoaded hep false kalıyor, sonsuz shimmer görünüyordu,
        // gerçek reklam asla yüklenmiyordu. AdMob envanteri bu ekranda hiç
        // kullanılmıyordu.
        LaunchedEffect(kurdiListState, kurdiAdPlan) {
            adsVm.warmVisiblePositions(kurdiAdPlan, firstVisibleIndex = 0, maxInitialAds = 3)
            snapshotFlow { kurdiListState.firstVisibleItemIndex }
                .debounce(300L) // hızlı scroll'da her kart için istek atılmasın
                .collect { firstVisible ->
                    adsVm.warmVisiblePositions(kurdiAdPlan, firstVisibleIndex = firstVisible)
                }
        }

        // ── Ekran kapanırken temizlik — diğer ekranlarla aynı ────────────────
        // ÖNCEDEN bu da yoktu: ısıtılan slotlar (varsayımsal olarak ısıtılsaydı)
        // ekrandan çıkınca hiç serbest bırakılmayacaktı (bellek sızıntısı riski,
        // bkz. FeedScreen'deki aynı yorum).
        DisposableEffect(Unit) {
            onDispose {
                adsVm.releaseBanners("kurdi_banner_")
                adsVm.releaseAllNatives("kurdi_native_")
            }
        }

        LazyColumn(
            state          = kurdiListState,
            modifier       = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            // Günlük hedef kartı
            item {
                DailyNudgeCard(
                    language = language,
                    lessons  = lessons,
                    doneIds  = doneIds,
                    onClick  = onNext,
                )
            }

            // Her ünite
            units.forEachIndexed { unitIndex, unit ->
                val unitLessons = lessons
                    .filter { it.unitId == unit.id }
                    .sortedBy { it.order }
                val done  = unitLessons.count { it.id in doneIds }
                val total = unitLessons.size
                val pct   = if (total > 0) done * 100 / total else 0
                val color = parseColor(unit.color)

                // Reklam yerleşimi adPlan'dan gelir — banner/native çakışması
                // yapısal olarak imkansız (bkz. AdPlanner.kt). item(){} bloğu
                // koşulsuz eklenir (forEachIndexed @Composable bağlam değildir),
                // içeriği plan'a bakarak kendi kararını verir.
                kurdiAdPlan[unitIndex]?.let { placement ->
                    item(key = "ad_${unit.id}") {
                        AdSlotView(placement = placement, adsVm = adsVm, modifier = Modifier.padding(vertical = 8.dp))
                    }
                }

                item(key = "unit_${unit.id}") {
                    UnitHeader(unit = unit, done = done, total = total, pct = pct, color = color)
                }

                if (unitLessons.isNotEmpty()) {
                    item(key = "path_${unit.id}") {
                        LessonPath(
                            lessons         = unitLessons,
                            doneIds         = doneIds,
                            color           = color,
                            language        = language,
                            tempUnlockedIds = tempUnlockedIds,
                            canWatchAd      = canWatchAd,
                            onOpen          = onOpen,
                            onLockedClick   = onLockedClick,
                            onShare         = onShare,
                        )
                    }
                }
            }
        }
        }
    }
}

// -- Günlük hedef kartı (site: .kp-daily-nudge) ------------------------------
@Composable
private fun DailyNudgeCard(
    language : String,
    lessons  : List<KfLesson>,
    doneIds  : Set<String>,
    onClick  : () -> Unit,
) {
    val hasNext = lessons.any { it.id !in doneIds }
    if (!hasNext) return

    val doneTodayCount = doneIds.size
    val totalCount     = lessons.size

    val nudgeScale by animateFloatAsState(1f, spring(Spring.DampingRatioMediumBouncy), label = "nudge")

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        Primary.copy(alpha = 0.22f),
                        GradientEnd.copy(alpha = 0.10f),
                    )
                )
            )
            .border(1.dp, Primary.copy(alpha = 0.30f), RoundedCornerShape(20.dp))
            .clickable { onClick() }
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // İkon
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Primary.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Text("🎯", fontSize = 26.sp)
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    Strings.dailyGoal(language),
                    fontWeight    = FontWeight.Bold,
                    color         = OnBackground,
                    fontSize      = 15.sp,
                    letterSpacing = (-0.3).sp,
                )
                Text(
                    Strings.dailyGoalDesc(language),
                    color    = Muted,
                    fontSize = 12.sp,
                )
                if (doneTodayCount > 0) {
                    Spacer(Modifier.height(6.dp))
                    // Mini progress
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(SurfaceVar)
                    ) {
                        val pct = (doneTodayCount.toFloat() / totalCount.toFloat()).coerceIn(0f, 1f)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(pct)
                                .height(4.dp)
                                .background(
                                    Brush.horizontalGradient(listOf(Primary, GradientEnd)),
                                    RoundedCornerShape(2.dp),
                                )
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        if (language == "ku") "$doneTodayCount / $totalCount qediya"
                        else "$doneTodayCount / $totalCount tamamlandı",
                        color      = Primary,
                        fontSize   = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            // Başla butonu
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        Brush.linearGradient(listOf(Primary, GradientEnd))
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Text(
                    Strings.startLesson(language),
                    color         = Color.White,
                    fontWeight    = FontWeight.Bold,
                    fontSize      = 12.sp,
                    letterSpacing = 0.2.sp,
                )
            }
        }
    }
}

// -- Ünite başlık kartı -------------------------------------------------------
@Composable
private fun UnitHeader(
    unit  : KfUnit,
    done  : Int,
    total : Int,
    pct   : Int,
    color : Color,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(HeftSurface)
            .border(1.dp, color.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
    ) {
        // Sol renkli şerit
        Box(
            modifier = Modifier
                .width(4.dp)
                .matchParentSize()
                .background(color, RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp))
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 14.dp, top = 14.dp, bottom = 14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(color.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(unit.icon, fontSize = 24.sp)
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        unit.ttl,
                        fontWeight = FontWeight.Bold,
                        color      = OnBackground,
                        fontSize   = 15.sp,
                    )
                    if (unit.desc.isNotBlank()) {
                        Text(unit.desc, color = Muted, fontSize = 12.sp)
                    }
                }
                // Yuvarlak % rozeti
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(color.copy(alpha = if (pct == 100) 1f else 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (pct == 100) "✓" else "$pct%",
                        color      = if (pct == 100) Color.White else color,
                        fontWeight = FontWeight.Bold,
                        fontSize   = if (pct == 100) 18.sp else 12.sp,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            // Progress bar animasyonlu + ders sayısı
            val animatedPct by animateFloatAsState(
                targetValue   = pct / 100f,
                animationSpec = tween(600, easing = FastOutSlowInEasing),
                label         = "unitProgress",
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(SurfaceVar)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(animatedPct)
                            .height(8.dp)
                            .background(
                                Brush.horizontalGradient(listOf(color, color.copy(alpha = 0.7f))),
                                RoundedCornerShape(4.dp),
                            )
                    )
                }
                Text(
                    "$done/$total",
                    color      = if (pct == 100) color else Muted,
                    fontSize   = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

// -- Ders yolu (site: .kp-lesson-path — daireler yol şeklinde) ----------------
@Composable
private fun LessonPath(
    lessons         : List<KfLesson>,
    doneIds         : Set<String>,
    color           : Color,
    language        : String = "tr",
    tempUnlockedIds : Set<String> = emptySet(),
    canWatchAd      : Boolean = false,
    onOpen          : (String) -> Unit,
    onLockedClick   : (String) -> Unit = {},
    onShare         : ((KfLesson) -> Unit)? = null,
) {
    val firstNotDone = lessons.indexOfFirst { it.id !in doneIds }
        .let { if (it == -1) lessons.size else it }

    Column(
        modifier            = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        lessons.forEachIndexed { index, lesson ->
            val isDone         = lesson.id in doneIds
            val isTempUnlocked = lesson.id in tempUnlockedIds
            // isDone dersler hiçbir zaman isActive sayılmaz; tempUnlock sadece kilitli dersleri açar
            val isActive       = !isDone && (index == firstNotDone || isTempUnlocked)
            val isLocked       = !isDone && index > firstNotDone && !isTempUnlocked

            LessonPathNode(
                lesson     = lesson,
                isDone     = isDone,
                isActive   = isActive,
                isLocked   = isLocked,
                color      = color,
                index      = index,
                total      = lessons.size,
                language   = language,
                canWatchAd = canWatchAd,
                onClick    = {
                    if (!isLocked) onOpen(lesson.id)
                    else onLockedClick(lesson.id)
                },
                onShare    = if (isDone && onShare != null) { { onShare(lesson) } } else null,
            )
        }
    }
}

@Composable
private fun LessonPathNode(
    lesson      : KfLesson,
    isDone      : Boolean,
    isActive    : Boolean,
    isLocked    : Boolean,
    color       : Color,
    index       : Int,
    total       : Int,
    language    : String = "tr",
    canWatchAd  : Boolean = false,
    onClick     : () -> Unit,
    onShare     : (() -> Unit)? = null,
) {
    val ku = language == "ku"
    var showUnlockDialog by remember { mutableStateOf(false) }

    // Kilitli ders — reklam izleyerek aç dialog
    if (showUnlockDialog) {
        AlertDialog(
            onDismissRequest = { showUnlockDialog = false },
            containerColor   = HeftSurface,
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("🔒", fontSize = 20.sp)
                    Text(
                        if (ku) "Ders Kilîtkirî" else "Ders Kilitli",
                        color      = OnBackground,
                        fontWeight = FontWeight.Bold,
                        fontSize   = 16.sp,
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (ku) "Ev ders kilîtkirî ye. Tu dikarî vîdyoyekê temaşe bikî û vê dersê vekî."
                        else    "Bu ders kilitli. Kısa bir video izleyerek bu dersi hemen açabilirsin.",
                        color    = Muted,
                        fontSize = 14.sp,
                    )
                    if (!canWatchAd) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Amber.copy(alpha = 0.12f),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("⚠️", fontSize = 14.sp)
                                Text(
                                    if (ku) "Îro mafê vîdyoyê nemaye."
                                    else    "Bugünkü reklam hakkın doldu.",
                                    color    = Amber,
                                    fontSize = 12.sp,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                if (canWatchAd) {
                    Button(
                        onClick = { showUnlockDialog = false; onClick() },
                        colors  = ButtonDefaults.buttonColors(containerColor = Primary),
                        shape   = RoundedCornerShape(10.dp),
                    ) {
                        Text("▶  " + if (ku) "Vîdyo temaşe bike û veke" else "Video izle ve aç", color = Color.White)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showUnlockDialog = false }) {
                    Text(if (ku) "Betal bike" else "İptal", color = Muted)
                }
            },
        )
    }
    // Zigzag: sol (0.15) → orta (0.42) → sağ (0.67) → orta (0.42)
    val offsets = listOf(0.13f, 0.40f, 0.65f, 0.40f)
    val hAlign  = offsets[index % offsets.size]

    // Basış animasyonu
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val nodeScale by animateFloatAsState(
        targetValue   = if (pressed) 0.90f else 1f,
        animationSpec = spring(Spring.DampingRatioMediumBouncy),
        label         = "nodeScale",
    )

    // Aktif ders için nabız animasyonu
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue  = 0.5f,
        targetValue   = 1f,
        animationSpec = infiniteRepeatable(tween<Float>(900), RepeatMode.Reverse),
        label         = "pulseAlpha",
    )

    Column(
        modifier            = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start,
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Spacer(Modifier.fillMaxWidth(hAlign))
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.graphicsLayer { scaleX = nodeScale; scaleY = nodeScale },
            ) {
                // Üst bağlantı
                if (index > 0) {
                    Box(
                        Modifier
                            .width(2.5.dp)
                            .height(24.dp)
                            .background(
                                if (isDone)
                                    Brush.verticalGradient(listOf(color, color.copy(alpha = 0.4f)))
                                else
                                    Brush.verticalGradient(listOf(Divider, Divider)),
                                RoundedCornerShape(1.dp),
                            )
                    )
                }

                // Ders düğmesi
                Box(
                    modifier = Modifier
                        .size(62.dp)
                        .then(
                            if (isActive) Modifier
                                .shadow(12.dp, CircleShape, spotColor = color.copy(alpha = 0.6f))
                            else Modifier
                        )
                        .clip(CircleShape)
                        .background(
                            when {
                                isDone   -> Brush.linearGradient(listOf(color, color.copy(alpha = 0.8f)))
                                isActive -> Brush.linearGradient(listOf(color, GradientEnd.copy(alpha = 0.8f)))
                                isLocked -> Brush.linearGradient(listOf(SurfaceVar, SurfaceVar))
                                else     -> Brush.linearGradient(listOf(SurfaceVar, SurfaceVar))
                            }
                        )
                        .border(
                            width = when {
                                isActive -> 2.5.dp
                                isDone   -> 0.dp
                                else     -> 1.5.dp
                            },
                            brush = if (isActive)
                                Brush.linearGradient(listOf(color, GradientEnd))
                            else if (isLocked)
                                Brush.linearGradient(listOf(Divider, Divider))
                            else
                                Brush.linearGradient(listOf(color.copy(alpha = 0.5f), color.copy(alpha = 0.3f))),
                            shape = CircleShape,
                        )
                        .clickable(
                            interactionSource = interactionSource,
                            indication        = null,
                        ) {
                            if (isLocked) showUnlockDialog = true
                            else onClick()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    when {
                        isDone   -> Text("⭐", fontSize = 24.sp)
                        isLocked -> Icon(
                            Icons.Default.Lock,
                            null,
                            tint     = Muted,
                            modifier = Modifier.size(22.dp),
                        )
                        isActive -> Text(lesson.emoji, fontSize = 24.sp)
                        else     -> Text(
                            lesson.emoji,
                            fontSize = 22.sp,
                            modifier = Modifier.graphicsLayer { alpha = 0.55f },
                        )
                    }
                }

                Spacer(Modifier.height(6.dp))

                // Ders adı
                Text(
                    lesson.nameTr,
                    color         = when {
                        isActive -> OnBackground
                        isLocked -> Muted.copy(alpha = 0.6f)
                        isDone   -> OnSurface
                        else     -> Muted
                    },
                    fontSize      = 10.sp,
                    fontWeight    = if (isActive) FontWeight.Bold else FontWeight.Normal,
                    textAlign     = TextAlign.Center,
                    maxLines      = 2,
                    overflow      = TextOverflow.Ellipsis,
                    letterSpacing = 0.1.sp,
                    modifier      = Modifier.width(72.dp),
                )

                // "BAŞLA" pill — sadece aktif (henüz tamamlanmamış, kilitli olmayan) ders
                if (isActive) {
                    Spacer(Modifier.height(5.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(
                                Brush.horizontalGradient(listOf(color, GradientEnd)),
                            )
                            .graphicsLayer { alpha = pulseAlpha }
                            .clickable { onClick() }
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                    ) {
                        Text(
                            Strings.startLesson(language),
                            color         = Color.White,
                            fontWeight    = FontWeight.ExtraBold,
                            fontSize      = 9.sp,
                            letterSpacing = 0.5.sp,
                        )
                    }
                }

                // "PAYLAŞ" pill — tamamlanan dersler için (isActive'den bağımsız)
                if (isDone && onShare != null) {
                    Spacer(Modifier.height(5.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(color.copy(alpha = 0.15f))
                            .border(1.dp, color.copy(alpha = 0.45f), RoundedCornerShape(20.dp))
                            .clickable { onShare() }
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            Icon(
                                Icons.Default.Share,
                                contentDescription = null,
                                tint     = color,
                                modifier = Modifier.size(9.dp),
                            )
                            Text(
                                Strings.shareToFeed(language),
                                color         = color,
                                fontWeight    = FontWeight.Bold,
                                fontSize      = 8.5.sp,
                                letterSpacing = 0.2.sp,
                            )
                        }
                    }
                }

                // "VİDEO İZLE" pill — kilitli dersler için (isActive/isDone'dan bağımsız)
                if (isLocked) {
                    Spacer(Modifier.height(5.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(Amber.copy(alpha = 0.15f))
                            .border(1.dp, Amber.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                            .clickable { showUnlockDialog = true }
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    ) {
                        Text(
                            Strings.unlockWithVideo(language),
                            color         = Amber,
                            fontWeight    = FontWeight.Bold,
                            fontSize      = 8.5.sp,
                            letterSpacing = 0.2.sp,
                        )
                    }
                }

                // Alt bağlantı
                if (index < total - 1) {
                    Spacer(Modifier.height(2.dp))
                    Box(
                        Modifier
                            .width(2.5.dp)
                            .height(24.dp)
                            .background(
                                if (isDone)
                                    Brush.verticalGradient(listOf(color.copy(alpha = 0.4f), color))
                                else
                                    Brush.verticalGradient(listOf(Divider, Divider)),
                                RoundedCornerShape(1.dp),
                            )
                    )
                }
            }
        }
    }
}

// -- Ders yapma ekranı ---------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LessonScreen(
    activeLesson : ActiveLesson,
    language     : String = "tr",
    vm           : KurdiViewModel = hiltViewModel(),
    onComplete   : (Int) -> Unit,
    onClose      : () -> Unit,
) {
    val lesson    = activeLesson.lesson
    val vocab     = activeLesson.vocab
    val exercises = activeLesson.exercises
    val totalSteps = vocab.size + exercises.size

    // -- TEK state makinesi — step değişince her şey sıfırlanır --------------
    var step         by remember { mutableIntStateOf(0) }
    var correctCount by remember { mutableIntStateOf(0) }
    // ÖNCEDEN: ders sonunda admin'in elle girdiği TEK sabit XP veriliyordu, soru
    // tipi/doğru-yanlış hiç fark etmiyordu. Artık her doğru cevaplanan egzersizin
    // tipine göre (bkz. KfExerciseXp) burada gerçek zamanlı toplanıyor.
    var earnedXp     by remember { mutableIntStateOf(0) }
    // Ders ortasında üst çubukta gösterilen "+X XP" rozeti — ÖNCEDEN burada
    // lesson.xp (admin'in elle girdiği sabit değer) gösteriliyordu. XP artık
    // egzersiz tiplerine göre otomatik hesaplandığı için, o sabit değer gerçek
    // kazanılabilecek miktarla artık UYUŞMUYOR (yanıltıcı olurdu). Bunun yerine
    // bu dersteki tüm egzersizlerin azami toplamını ("hepsini doğru yaparsan
    // kazanacağın XP") gösteriyoruz — gerçek davranışla tutarlı.
    val maxPossibleXp = remember(exercises) { exercises.sumOf { KfExerciseXp.forType(it.type) } }

    // Adım hesapları
    val vocabDone = vocab.isEmpty() || step >= vocab.size
    val exStep    = if (vocabDone) (if (vocab.isEmpty()) step else step - vocab.size) else -1
    val exDone    = exercises.isEmpty() || exStep >= exercises.size
    val currentEx = if (vocabDone && exStep >= 0 && !exDone) exercises.getOrNull(exStep) else null
    val allDone   = vocabDone && exDone

    // -- Egzersiz başına sıfırlanan state — key(step) ile yönetilir ----------
    // Bu state'ler BuildExercise/MatchExercise içinde key(step) sayesinde
    // step her değiştiğinde otomatik sıfırlanır
    var showResult  by remember(step) { mutableStateOf(false) }
    var selectedAns by remember(step) { mutableStateOf<String?>(null) }
    var fillAnswer  by remember(step) { mutableStateOf("") }
    var buildResult by remember(step) { mutableStateOf<Boolean?>(null) } // null=bekliyor, true=doğru, false=yanlış

    // Devam butonu
    val canAdvance = when {
        allDone    -> true
        !vocabDone -> true
        currentEx?.type == "build"  -> buildResult != null
        currentEx?.type == "match"  -> showResult
        currentEx != null           -> showResult
        else -> false
    }
    val nextLabel = when {
        allDone                              -> Strings.finishLesson(language)
        !vocabDone && step == vocab.size - 1 -> Strings.toQuestions(language)
        !vocabDone                           -> Strings.nextQuestion(language)
        exStep == exercises.size - 1         -> Strings.complete(language)
        else                                 -> Strings.continueLesson(language)
    }

    var showReportDialog by remember { mutableStateOf(false) }
    var reportSent       by remember { mutableStateOf(false) }
    val lessonToast       by vm.toast.collectAsState()

    // Ekran görüntüsü paylaşımı — o anki kelime/egzersiz kartını yakalar
    val context           = androidx.compose.ui.platform.LocalContext.current
    val shareScope        = rememberCoroutineScope()
    val lessonGraphicsLayer = rememberGraphicsLayer()
    var showLessonShareSheet by remember { mutableStateOf(false) }
    var lessonCapturing   by remember { mutableStateOf(false) }

    LaunchedEffect(lessonToast) {
        if (lessonToast != null) {
            kotlinx.coroutines.delay(2500)
            vm.clearToast()
        }
    }

    Scaffold(
        containerColor = Background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(lesson.nameTr, fontWeight = FontWeight.Bold, color = OnBackground, fontSize = 15.sp)
                        if (totalSteps > 0) {
                            LinearProgressIndicator(
                                progress   = { step.toFloat() / totalSteps.coerceAtLeast(1) },
                                modifier   = Modifier.fillMaxWidth().height(4.dp).padding(top = 2.dp),
                                color      = Primary,
                                trackColor = SurfaceVar,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, null, tint = OnBackground)
                    }
                },
                actions = {
                    // Ekran görüntüsü olarak paylaş — o anki kelime/egzersiz kartı
                    IconButton(onClick = { showLessonShareSheet = true }) {
                        Icon(Icons.Default.Share, contentDescription = Strings.shareAsImage(language),
                            tint = Muted, modifier = Modifier.size(20.dp))
                    }
                    // Hata Bildir butonu
                    IconButton(onClick = { showReportDialog = true }) {
                        Icon(Icons.Default.Flag, contentDescription = "Hata Bildir",
                            tint = if (reportSent) Color(0xFF22C55E) else Muted,
                            modifier = Modifier.size(20.dp))
                    }
                    Surface(shape = RoundedCornerShape(20.dp), color = Amber.copy(alpha = 0.15f)) {
                        Text("+$maxPossibleXp XP", color = Amber, fontWeight = FontWeight.Bold, fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Background),
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Background)
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .navigationBarsPadding(),
            ) {
                // Sonuç bandı
                val showBand = showResult || buildResult != null
                if (showBand && !allDone && currentEx != null) {
                    val isOk = when (currentEx.type) {
                        "build" -> buildResult == true
                        "match" -> true
                        "fill"  -> fillAnswer.trim().equals(currentEx.answer, ignoreCase = true)
                        "mcq"   -> selectedAns?.trim().equals(currentEx.answer.trim(), ignoreCase = true) == true
                        else    -> true
                    }
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        shape    = RoundedCornerShape(12.dp),
                        color    = if (isOk) Color(0xFF22C55E).copy(0.15f) else Color(0xFFEF4444).copy(0.15f),
                    ) {
                        Row(
                            Modifier.padding(12.dp),
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                if (isOk) "✓" else "✗",
                                color      = if (isOk) Color(0xFF22C55E) else Color(0xFFEF4444),
                                fontWeight = FontWeight.Black,
                                fontSize   = 18.sp,
                            )
                            Column {
                                Text(
                                    if (isOk) Strings.correctAnswer(language) else Strings.wrongAnswer(language),
                                    color      = if (isOk) Color(0xFF22C55E) else Color(0xFFEF4444),
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize   = 14.sp,
                                )
                                if (!isOk && currentEx.type != "build") {
                                    Text(
                                        Strings.correctAnswerIs(language, currentEx.answer),
                                        color    = Color(0xFFEF4444).copy(0.8f),
                                        fontSize = 12.sp,
                                    )
                                }
                                if (!isOk && currentEx.type == "build") {
                                    Text(
                                        "Doğru sıra: ${currentEx.words.joinToString(" ")}",
                                        color    = Color(0xFFEF4444).copy(0.8f),
                                        fontSize = 12.sp,
                                    )
                                }
                            }
                        }
                    }
                }

                Button(
                    onClick = {
                        when {
                            allDone    -> onComplete(earnedXp)
                            !vocabDone -> step++
                            canAdvance -> step++
                        }
                    },
                    enabled  = canAdvance,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape    = RoundedCornerShape(14.dp),
                    colors   = ButtonDefaults.buttonColors(
                        containerColor         = if (allDone) Amber else Primary,
                        disabledContainerColor = SurfaceVar,
                    ),
                ) {
                    Text(nextLabel, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawWithContent {
                    lessonGraphicsLayer.record { this@drawWithContent.drawContent() }
                    drawLayer(lessonGraphicsLayer)
                },
        ) {
        when {
            // -- Tamamlandı ----------------------------------------------------
            allDone -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(24.dp),
                ) {
                    Text("🎉", fontSize = 72.sp)
                    Text("Ders Tamamlandı!", fontWeight = FontWeight.ExtraBold, color = OnBackground, fontSize = 22.sp)
                    Surface(shape = RoundedCornerShape(20.dp), color = Amber.copy(0.15f)) {
                        Text("+$earnedXp XP kazandın!", color = Amber, fontWeight = FontWeight.Bold,
                            fontSize = 16.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp))
                    }
                    Text("$correctCount doğru cevap ✓", color = Color(0xFF22C55E), fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick  = { onComplete(earnedXp) },
                        modifier = Modifier.fillMaxWidth(),
                        shape    = RoundedCornerShape(14.dp),
                        colors   = ButtonDefaults.buttonColors(containerColor = Amber),
                    ) { Text("Devam", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp) }
                }
            }

            // -- Kelime kartı --------------------------------------------------
            !vocabDone -> {
                val voc = vocab[step]
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp),
                    ) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape    = RoundedCornerShape(24.dp),
                            color    = HeftSurface,
                        ) {
                            Column(
                                Modifier.padding(36.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                if (voc.e.isNotBlank()) {
                                    Text(voc.e, fontSize = 60.sp)
                                    Spacer(Modifier.height(16.dp))
                                }
                                Text(voc.ku, fontWeight = FontWeight.ExtraBold, color = OnBackground, fontSize = 32.sp, textAlign = TextAlign.Center)
                                if (voc.kp.isNotBlank()) {
                                    Spacer(Modifier.height(6.dp))
                                    Text("/${voc.kp}/", color = Muted, fontSize = 14.sp)
                                }
                                Spacer(Modifier.height(16.dp))
                                HorizontalDivider(color = Divider)
                                Spacer(Modifier.height(16.dp))
                                Text(voc.tr, fontWeight = FontWeight.SemiBold, color = Primary, fontSize = 24.sp, textAlign = TextAlign.Center)
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        Text("${step + 1} / ${vocab.size}", color = Muted, fontSize = 13.sp)
                    }
                }
            }

            // -- Egzersiz ------------------------------------------------------
            currentEx != null -> {
                val ex = currentEx
                // key(step) kritik — step değişince tüm egzersiz composable'ı sıfırdan oluşturulur
                key(step) {
                    LazyColumn(
                        modifier            = Modifier.fillMaxSize().padding(padding),
                        contentPadding      = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        // Soru başlığı
                        item {
                            when (ex.type) {
                                "build", "match" -> {} // build ve match kendi başlığını gösterir
                                else -> {
                                    Text(
                                        ex.question,
                                        fontWeight = FontWeight.ExtraBold,
                                        color      = OnBackground,
                                        fontSize   = 20.sp,
                                        textAlign  = TextAlign.Center,
                                        modifier   = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                    )
                                    if (ex.questionTr.isNotBlank()) {
                                        Text(ex.questionTr, color = Muted, fontSize = 13.sp,
                                            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                                    }
                                    Spacer(Modifier.height(24.dp))
                                }
                            }
                        }

                        when (ex.type) {
                            // -- Çoktan seçmeli --------------------------------
                            "mcq" -> {
                                // remember LazyListScope dışında item{} içinde kullanılmalı
                                item {
                                    val options = remember(ex.id) {
                                        listOf(ex.optA, ex.optB, ex.optC, ex.optD)
                                            .filter { it.isNotBlank() }
                                            .shuffled()
                                    }
                                    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                                        options.forEach { opt ->
                                    val isSel     = selectedAns == opt
                                    val isCorrect = showResult && opt.trim().equals(ex.answer.trim(), ignoreCase = true)
                                    val isWrong   = showResult && isSel && !opt.trim().equals(ex.answer.trim(), ignoreCase = true)
                                            Surface(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable(enabled = !showResult) {
                                                        selectedAns = opt
                                                        showResult  = true
                                                        if (opt.trim().equals(ex.answer.trim(), ignoreCase = true)) {
                                                            correctCount++
                                                            earnedXp += KfExerciseXp.forType("mcq")
                                                        }
                                                    },
                                                shape  = RoundedCornerShape(14.dp),
                                                color  = when {
                                                    isCorrect -> Color(0xFF22C55E).copy(0.12f)
                                                    isWrong   -> Color(0xFFEF4444).copy(0.12f)
                                                    isSel     -> Primary.copy(0.12f)
                                                    else      -> HeftSurface
                                                },
                                                border = BorderStroke(2.dp, when {
                                                    isCorrect -> Color(0xFF22C55E)
                                                    isWrong   -> Color(0xFFEF4444)
                                                    isSel     -> Primary
                                                    else      -> Divider
                                                }),
                                            ) {
                                                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                                    Text(opt, color = OnBackground, fontWeight = FontWeight.SemiBold,
                                                        fontSize = 15.sp, modifier = Modifier.weight(1f))
                                                    if (showResult) {
                                                        Icon(
                                                            if (isCorrect) Icons.Default.CheckCircle
                                                            else if (isWrong) Icons.Default.Cancel
                                                            else Icons.Default.RadioButtonUnchecked,
                                                            null,
                                                            tint     = when { isCorrect -> Color(0xFF22C55E); isWrong -> Color(0xFFEF4444); else -> Divider },
                                                            modifier = Modifier.size(20.dp),
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // -- Boşluk doldur ---------------------------------
                            "fill" -> item {
                                val isCorrectFill = fillAnswer.trim().equals(ex.answer.trim(), ignoreCase = true)
                                // Türkçe ipucu varsa göster
                                if (ex.tr.isNotBlank()) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = Primary.copy(alpha = 0.08f),
                                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            Text("💡", fontSize = 14.sp)
                                            Text(ex.tr, color = Primary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                        }
                                    }
                                }
                                OutlinedTextField(
                                    value         = fillAnswer,
                                    onValueChange = { if (!showResult) fillAnswer = it },
                                    placeholder   = { Text(Strings.typeAnswer(language), color = Muted) },
                                    modifier      = Modifier.fillMaxWidth(),
                                    shape         = RoundedCornerShape(12.dp),
                                    singleLine    = true,
                                    readOnly      = showResult,
                                    colors        = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor      = if (showResult && isCorrectFill) Color(0xFF22C55E) else if (showResult) Color(0xFFEF4444) else Primary,
                                        unfocusedBorderColor    = if (showResult && isCorrectFill) Color(0xFF22C55E) else if (showResult) Color(0xFFEF4444) else Divider,
                                        focusedTextColor        = OnBackground,
                                        unfocusedTextColor      = OnBackground,
                                        unfocusedContainerColor = HeftSurface,
                                        focusedContainerColor   = HeftSurface,
                                    ),
                                )
                                Spacer(Modifier.height(12.dp))
                                if (!showResult) {
                                    Button(
                                        onClick  = {
                                            showResult = true
                                            if (fillAnswer.trim().equals(ex.answer.trim(), ignoreCase = true)) {
                                                correctCount++
                                                earnedXp += KfExerciseXp.forType("fill")
                                            }
                                        },
                                        enabled  = fillAnswer.isNotBlank(),
                                        modifier = Modifier.fillMaxWidth().height(48.dp),
                                        shape    = RoundedCornerShape(12.dp),
                                        colors   = ButtonDefaults.buttonColors(containerColor = Primary),
                                    ) { Text("Kontrol Et", color = Color.White, fontWeight = FontWeight.ExtraBold) }
                                } else if (!isCorrectFill) {
                                    // Yanlışsa tekrar deneme imkanı ver
                                    OutlinedButton(
                                        onClick  = {
                                            fillAnswer = ""
                                            showResult = false
                                        },
                                        modifier = Modifier.fillMaxWidth().height(48.dp),
                                        shape    = RoundedCornerShape(12.dp),
                                        border   = BorderStroke(2.dp, Primary),
                                    ) {
                                        Icon(Icons.Default.Refresh, null, tint = Primary, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Tekrar Dene", color = Primary, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            // -- Eşleştir --------------------------------------
                            "match" -> item {
                                MatchExercise(
                                    pairs     = ex.pairs,
                                    onAllDone = { showResult = true; earnedXp += KfExerciseXp.forType("match") },
                                )
                            }

                            // -- Cümle kur -------------------------------------
                            "build" -> item {
                                BuildExercise(
                                    question  = ex.question,
                                    words     = ex.words,
                                    tr        = ex.tr,
                                    language  = language,
                                    onChecked = { isOk ->
                                        buildResult = isOk
                                        if (isOk) {
                                            correctCount++
                                            earnedXp += KfExerciseXp.forType("build")
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
        }
    }

    // -- Hata Bildir Dialog ----------------------------------------------------
    if (showReportDialog) {
        var reportText by remember { mutableStateOf("") }
        // Dialog açıldığı andaki egzersiz bilgisini yakala
        val reportExIndex    = remember { exStep.takeIf { !vocabDone.not() && it >= 0 } }
        val reportExType     = remember { currentEx?.type }
        val reportExQuestion = remember { currentEx?.tr ?: currentEx?.answer }
        AlertDialog(
            onDismissRequest = { showReportDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Flag, null, tint = Color(0xFFEF4444), modifier = Modifier.size(18.dp))
                    Text("Hata Bildir", color = OnBackground, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val exerciseInfo = if (reportExIndex != null) " · Egzersiz ${reportExIndex + 1}" else ""
                    Text("«${lesson.nameTr}»$exerciseInfo dersinde bir hata mı buldun?", color = Muted, fontSize = 13.sp)
                    OutlinedTextField(
                        value = reportText, onValueChange = { reportText = it },
                        placeholder = { Text("Hatayı açıkla…", color = Muted, fontSize = 13.sp) },
                        modifier = Modifier.fillMaxWidth().height(100.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Primary, unfocusedBorderColor = SurfaceVar,
                            focusedTextColor = OnBackground, unfocusedTextColor = OnBackground,
                        ),
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (reportText.isNotBlank()) {
                            vm.reportLessonError(
                                lessonId          = lesson.id,
                                lessonName        = lesson.nameTr,
                                message           = reportText,
                                exerciseIndex     = reportExIndex,
                                exerciseType      = reportExType,
                                exerciseQuestion  = reportExQuestion,
                                onDone            = { reportSent = true; showReportDialog = false },
                            )
                        }
                    },
                    enabled = reportText.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                    shape  = RoundedCornerShape(10.dp),
                ) { Text("Gönder", color = Color.White, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { showReportDialog = false }) { Text("İptal", color = Muted) } },
            containerColor = HeftSurface,
        )
    }

    // Toast snackbar — reportLessonError gibi hataları burada gösterir.
    // ÖNCEDEN: vm.toast hiç dinlenmiyordu, bu yüzden "Hata Bildir" başarısız
    // olduğunda kullanıcıya hiçbir geri bildirim verilmiyordu (sessiz başarısızlık).
    if (lessonToast != null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            Surface(
                modifier = Modifier.padding(24.dp).navigationBarsPadding(),
                shape    = RoundedCornerShape(24.dp),
                color    = Primary,
            ) {
                Text(
                    lessonToast ?: "",
                    color      = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize   = 14.sp,
                    modifier   = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                )
            }
        }
    }

    // Ekran görüntüsü paylaşım menüsü — o anki kelime/egzersiz kartını Instagram/WhatsApp/Diğer'e gönderir
    if (showLessonShareSheet) {
        androidx.compose.material3.ModalBottomSheet(
            onDismissRequest = { showLessonShareSheet = false },
            containerColor   = HeftSurface,
        ) {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                listOf(
                    Triple("Instagram", Color(0xFFE1306C), ShareTarget.INSTAGRAM),
                    Triple("WhatsApp", Color(0xFF25D366), ShareTarget.WHATSAPP),
                    Triple(if (language == "ku") "Yên Din" else "Diğer", Muted, ShareTarget.ANY),
                ).forEach { (label, tint, target) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (lessonCapturing) return@clickable
                                lessonCapturing = true
                                showLessonShareSheet = false
                                shareScope.launch {
                                    kotlinx.coroutines.delay(80)
                                    val bmp = lessonGraphicsLayer
                                        .toImageBitmap()
                                        .asAndroidBitmap()
                                        .copy(android.graphics.Bitmap.Config.ARGB_8888, false)
                                    shareBitmap(context, bmp, target)
                                    lessonCapturing = false
                                }
                            }
                            .padding(vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(Icons.Default.Share, null, tint = tint, modifier = Modifier.size(20.dp))
                        Text(label, color = OnBackground, fontSize = 15.sp)
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

// -- Eşleştirme egzersizi (match) ---------------------------------------------
@Composable
private fun MatchExercise(
    pairs    : List<Pair<String, String>>,
    onAllDone: () -> Unit,
) {
    if (pairs.isEmpty()) return
    val shuffledRight = remember(pairs) { pairs.map { it.second }.shuffled() }
    var selectedLeft  by remember { mutableStateOf<String?>(null) }
    var selectedRight by remember { mutableStateOf<String?>(null) }
    val matched       = remember { mutableStateListOf<String>() }  // matched left keys
    val wrongLeft     = remember { mutableStateOf<String?>(null) }
    val wrongRight    = remember { mutableStateOf<String?>(null) }

    // Snapshot ile race condition önlenir: l ve r aynı snapshot'ta okunur,
    // ardından tek seferde sıfırlanır — böylece LaunchedEffect iki kez tetiklenmez.
    LaunchedEffect(selectedLeft, selectedRight) {
        val l = selectedLeft ?: return@LaunchedEffect
        val r = selectedRight ?: return@LaunchedEffect
        // İkisi de doluysa işlemi yap, önce state'i sıfırla
        selectedLeft  = null
        selectedRight = null
        val correct = pairs.find { it.first == l }?.second == r
        if (correct) {
            if (l !in matched) {
                matched.add(l)
            }
            if (matched.size == pairs.size) onAllDone()
        } else {
            wrongLeft.value = l; wrongRight.value = r
            kotlinx.coroutines.delay(500)
            wrongLeft.value = null; wrongRight.value = null
        }
    }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
        Text(
            "Eşleştir", color = Muted, fontSize = 12.sp,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            // Sol — Kürtçe
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                pairs.forEach { (ku, _) ->
                    val isMatched  = ku in matched
                    val isSelected = selectedLeft == ku
                    val isWrong    = wrongLeft.value == ku
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !isMatched) { selectedLeft = ku },
                        shape  = RoundedCornerShape(10.dp),
                        color  = when {
                            isMatched  -> Color(0xFF22C55E).copy(0.12f)
                            isWrong    -> Color(0xFFEF4444).copy(0.12f)
                            isSelected -> Primary.copy(0.15f)
                            else       -> HeftSurface
                        },
                        border = BorderStroke(
                            1.5.dp,
                            when {
                                isMatched  -> Color(0xFF22C55E)
                                isWrong    -> Color(0xFFEF4444)
                                isSelected -> Primary
                                else       -> Divider
                            }
                        ),
                    ) {
                        Text(
                            ku,
                            color      = if (isMatched) Color(0xFF22C55E) else OnBackground,
                            fontWeight = FontWeight.Bold,
                            fontSize   = 13.sp,
                            textAlign  = TextAlign.Center,
                            modifier   = Modifier.padding(12.dp).fillMaxWidth(),
                        )
                    }
                }
            }
            // Sağ — Türkçe (karıştırılmış)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                shuffledRight.forEach { tr ->
                    val matchedLeft = pairs.find { it.second == tr }?.first
                    val isMatched   = matchedLeft != null && matchedLeft in matched
                    val isSelected  = selectedRight == tr
                    val isWrong     = wrongRight.value == tr
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !isMatched) { selectedRight = tr },
                        shape  = RoundedCornerShape(10.dp),
                        color  = when {
                            isMatched  -> Color(0xFF22C55E).copy(0.12f)
                            isWrong    -> Color(0xFFEF4444).copy(0.12f)
                            isSelected -> Primary.copy(0.15f)
                            else       -> HeftSurface
                        },
                        border = BorderStroke(
                            1.5.dp,
                            when {
                                isMatched  -> Color(0xFF22C55E)
                                isWrong    -> Color(0xFFEF4444)
                                isSelected -> Primary
                                else       -> Divider
                            }
                        ),
                    ) {
                        Text(
                            tr,
                            color      = if (isMatched) Color(0xFF22C55E) else OnBackground,
                            fontWeight = FontWeight.Medium,
                            fontSize   = 13.sp,
                            textAlign  = TextAlign.Center,
                            modifier   = Modifier.padding(12.dp).fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

// -- Cümle kurma egzersizi (build) — web temasıyla birebir aynı mantık ----------
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun BuildExercise(
    question : String  = "",
    words    : List<String>,
    tr       : String,
    language : String  = "tr",
    onChecked: (Boolean) -> Unit,
) {
    // Bank: words'ü karıştır — her kelime kaç kez varsa o kadar görünür
    val bank    = remember(words) { words.shuffled() }
    // placed: kullanıcının seçtiği kelimeler (bank index bazlı takip)
    val usedIdx = remember { mutableStateListOf<Int>() }
    val placed  = remember { mutableStateListOf<String>() }
    var checked by remember { mutableStateOf(false) }
    var correct by remember { mutableStateOf(false) }

    Column(
        modifier            = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Soru başlığı
        if (question.isNotBlank()) {
            Text(
                question,
                fontWeight = FontWeight.ExtraBold,
                color      = OnBackground,
                fontSize   = 20.sp,
                textAlign  = TextAlign.Center,
                modifier   = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            )
        }
        // Türkçe ipucu — web: .kp-build-tr
        if (tr.isNotBlank()) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = SurfaceVar,
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
            ) {
                Row(
                    Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text("🇹🇷 ", fontSize = 14.sp)
                    Text(tr, color = OnBackground, fontWeight = FontWeight.Bold, fontSize = 14.sp, textAlign = TextAlign.Center)
                }
            }
        }

        // Yerleştirme alanı — web: #kpBuildArea
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(
                    when {
                        !checked -> HeftSurface
                        correct  -> Color(0xFF22C55E).copy(0.08f)
                        else     -> Color(0xFFEF4444).copy(0.08f)
                    }
                )
                .border(
                    2.dp,
                    when {
                        !checked -> Divider
                        correct  -> Color(0xFF22C55E)
                        else     -> Color(0xFFEF4444)
                    },
                    RoundedCornerShape(12.dp),
                )
                .padding(12.dp)
                .heightIn(min = 56.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (placed.isEmpty()) {
                Text(Strings.tapInOrder(language), color = Muted, fontSize = 13.sp)
            } else {
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalArrangement   = Arrangement.spacedBy(7.dp),
                ) {
                    placed.forEachIndexed { i, word ->
                        // Tıklayınca geri al
                        Surface(
                            modifier = Modifier.clickable(enabled = !checked) {
                                placed.removeAt(i)
                                // Bu kelimenin en son kullanılan bank index'ini serbest bırak
                                val bankIdx = usedIdx.lastOrNull { bank.getOrNull(it) == word }
                                if (bankIdx != null) usedIdx.remove(bankIdx)
                            },
                            shape  = RoundedCornerShape(9.dp),
                            color  = Primary.copy(0.15f),
                            border = BorderStroke(2.dp, Primary),
                        ) {
                            Text(
                                word,
                                color      = Primary,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize   = 14.sp,
                                modifier   = Modifier.padding(horizontal = 13.dp, vertical = 8.dp),
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // Kelime bankası — web: #kpBuildBank
        androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement   = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            bank.forEachIndexed { idx, word ->
                val isUsed = idx in usedIdx
                Surface(
                    modifier = Modifier
                        .alpha(if (isUsed) 0.2f else 1f)
                        .clickable(enabled = !checked && !isUsed) {
                            placed.add(word)
                            usedIdx.add(idx)
                        },
                    shape  = RoundedCornerShape(10.dp),
                    color  = HeftSurface,
                    border = BorderStroke(2.dp, if (isUsed) Divider.copy(alpha = 0.3f) else Divider),
                ) {
                    Text(
                        word,
                        color      = OnBackground,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize   = 14.sp,
                        modifier   = Modifier.padding(horizontal = 15.dp, vertical = 10.dp),
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // Kontrol Et butonu — sadece tüm kelimeler seçildiğinde aktif
        if (!checked) {
            Button(
                onClick = {
                    // Web temasıyla aynı: given.trim().toLowerCase() === expected.trim().toLowerCase()
                    val given    = placed.joinToString(" ").trim().lowercase()
                    val expected = words.joinToString(" ").trim().lowercase()
                    correct = given == expected
                    checked = true
                    onChecked(correct)
                },
                enabled  = placed.size == words.size,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape    = RoundedCornerShape(12.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = Primary),
            ) {
                Text(Strings.checkAnswer(language).uppercase(), color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, letterSpacing = 0.5.sp)
            }
        }
    }
}

// -- Sözlük sekmesi -----------------------------------------------------------
@Composable
private fun DictionaryTab(language: String, isAdmin: Boolean = false, vm: KurdiViewModel) {
    val entries  by vm.dictEntries.collectAsState()
    val loading  by vm.dictLoading.collectAsState()
    var query    by remember { mutableStateOf("") }
    var showAdd  by remember { mutableStateOf(false) }

    // İlk açılışta yükle
    LaunchedEffect(Unit) { vm.loadDict() }

    val filtered = remember(entries, query) {
        if (query.isBlank()) entries
        else entries.filter {
            it.ku.contains(query, ignoreCase = true) || it.tr.contains(query, ignoreCase = true)
        }
    }

    Column(Modifier.fillMaxSize()) {
        // Arama + Admin ekle butonu
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value         = query,
                onValueChange = { query = it },
                modifier      = Modifier.weight(1f),
                placeholder   = { Text(if (language == "ku") "Bigere…" else "Ara…", color = Muted, fontSize = 13.sp) },
                leadingIcon   = { Icon(Icons.Default.Search, null, tint = Muted, modifier = Modifier.size(18.dp)) },
                trailingIcon  = if (query.isNotEmpty()) {{ IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, null, tint = Muted, modifier = Modifier.size(16.dp)) } }} else null,
                singleLine    = true,
                colors        = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = Primary, unfocusedBorderColor = SurfaceVar,
                    focusedTextColor     = OnBackground, unfocusedTextColor = OnBackground,
                    unfocusedContainerColor = HeftSurface, focusedContainerColor = HeftSurface,
                ),
                shape = RoundedCornerShape(12.dp),
            )
            if (isAdmin) {
                IconButton(
                    onClick = { showAdd = true },
                    modifier = Modifier
                        .size(48.dp)
                        .background(Primary, RoundedCornerShape(12.dp)),
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Kelime Ekle", tint = Color.White)
                }
            }
        }

        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Primary)
            }
        } else if (filtered.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("📖", fontSize = 40.sp)
                    Text(if (query.isNotBlank()) "Sonuç bulunamadı" else "Henüz kelime eklenmemiş",
                        color = Muted, fontSize = 14.sp)
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                item {
                    Text("${filtered.size} kelime", color = Muted, fontSize = 11.sp,
                        modifier = Modifier.padding(bottom = 4.dp))
                }
                items(filtered, key = { it.id }) { entry ->
                    DictEntryCard(entry = entry, isAdmin = isAdmin, onDelete = { vm.deleteDictEntry(entry.id) })
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }

    // Admin — Kelime Ekle dialog
    if (showAdd) {
        AddDictEntryDialog(
            language = language,
            onDismiss = { showAdd = false },
            onSave = { ku, tr, kp, e, cat ->
                vm.addDictEntry(ku, tr, kp, e, cat) { showAdd = false }
            },
        )
    }
}

@Composable
private fun DictEntryCard(entry: DictEntry, isAdmin: Boolean, onDelete: () -> Unit) {
    var showConfirm by remember { mutableStateOf(false) }
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = HeftSurface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(entry.e.ifBlank { "📖" }, fontSize = 26.sp)
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(entry.ku, fontWeight = FontWeight.Bold, color = OnBackground, fontSize = 14.sp)
                    if (entry.kp.isNotBlank())
                        Text("/${entry.kp}/", color = Muted, fontSize = 11.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                }
                Text(entry.tr, color = Muted, fontSize = 13.sp)
                if (entry.category.isNotBlank())
                    Surface(shape = RoundedCornerShape(20.dp), color = Primary.copy(alpha = 0.15f),
                        modifier = Modifier.padding(top = 4.dp)) {
                        Text(entry.category, color = Primary, fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                    }
            }
            if (isAdmin) {
                IconButton(onClick = { showConfirm = true }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Delete, null, tint = Muted, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text("Sil?", color = OnBackground) },
            text  = { Text("«${entry.ku}» silinsin mi?", color = Muted) },
            confirmButton = { TextButton(onClick = { onDelete(); showConfirm = false }) { Text("Sil", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { showConfirm = false }) { Text("İptal", color = Muted) } },
            containerColor = HeftSurface,
        )
    }
}

@Composable
private fun AddDictEntryDialog(language: String, onDismiss: () -> Unit, onSave: (String, String, String, String, String) -> Unit) {
    var ku  by remember { mutableStateOf("") }
    var tr  by remember { mutableStateOf("") }
    var kp  by remember { mutableStateOf("") }
    var e   by remember { mutableStateOf("📖") }
    var cat by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Kelime Ekle", color = OnBackground, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    Triple(ku,  { v: String -> ku = v },  "Kürtçe (ku) *"),
                    Triple(tr,  { v: String -> tr = v },  "Türkçe (tr) *"),
                    Triple(kp,  { v: String -> kp = v },  "Telaffuz (kp)"),
                    Triple(e,   { v: String -> e  = v },  "Emoji"),
                    Triple(cat, { v: String -> cat = v }, "Kategori"),
                ).forEach { (value, setter, label) ->
                    OutlinedTextField(
                        value = value, onValueChange = setter,
                        label = { Text(label, fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth(), singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Primary, unfocusedBorderColor = SurfaceVar,
                            focusedTextColor = OnBackground, unfocusedTextColor = OnBackground),
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick  = { if (ku.isNotBlank() && tr.isNotBlank()) onSave(ku, tr, kp, e, cat) },
                enabled  = ku.isNotBlank() && tr.isNotBlank(),
                colors   = ButtonDefaults.buttonColors(containerColor = Primary),
                shape    = RoundedCornerShape(10.dp),
            ) { Text("Kaydet", color = Color.White) }
        },
        dismissButton = { TextButton(onDismiss) { Text("İptal", color = Muted) } },
        containerColor = HeftSurface,
    )
}

// -- Dilbilgisi sekmesi -------------------------------------------------------
@Composable
private fun GrammarTab(
    language: String,
    isAdmin: Boolean = false,
    vm: KurdiViewModel,
    deepLinkRuleId: String? = null,
    onDeepLinkHandled: () -> Unit = {},
    feedVm: FeedViewModel,
) {
    val rules   by vm.grammarRules.collectAsState()
    val loading by vm.grammarLoading.collectAsState()
    var showAdd   by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<GrammarRule?>(null) }

    LaunchedEffect(Unit) { vm.loadGrammar() }

    // ── Deep-link: feed'den gelen gramer kuralı — kurallar yüklenince otomatik aç ──
    var autoOpenRuleId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(deepLinkRuleId, rules) {
        if (deepLinkRuleId != null && rules.any { it.id == deepLinkRuleId }) {
            autoOpenRuleId = deepLinkRuleId
            onDeepLinkHandled()
        }
    }

    // Kural sayısını banner olarak göster
    val ruleCount = rules.size

    Box(Modifier.fillMaxSize()) {
        when {
            loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Primary, strokeWidth = 2.dp)
                }
            }
            rules.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("📖", fontSize = 48.sp)
                        Text(
                            if (isAdmin) "Henüz kural eklenmemiş" else Strings.comingSoon(language),
                            color = Muted, fontSize = 14.sp,
                        )
                    }
                }
            }
            else -> {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 100.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    // Üst bilgi kartı
                    item {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Primary.copy(alpha = 0.10f),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Text("📚", fontSize = 28.sp)
                                Column {
                                    Text(
                                        if (language == "ku") "Rêziman" else "Dilbilgisi",
                                        fontWeight = FontWeight.Bold,
                                        color = Primary,
                                        fontSize = 15.sp,
                                    )
                                    Text(
                                        "$ruleCount ${if (language == "ku") "rêziman" else "kural"}",
                                        color = Muted,
                                        fontSize = 12.sp,
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                    }

                    items(rules, key = { it.id }) { rule ->
                        GrammarRuleCard(
                            rule     = rule,
                            language = language,
                            isAdmin  = isAdmin,
                            index    = rules.indexOf(rule),
                            onDelete = { vm.deleteGrammarRule(rule.id) },
                            onEdit   = if (isAdmin) { r -> editTarget = r } else null,
                            autoOpen = rule.id == autoOpenRuleId,
                            onAutoOpenConsumed = { autoOpenRuleId = null },
                            feedVm   = feedVm,
                            onShareResult = { msg -> vm.showToast(msg) },
                        )
                    }
                }
            }
        }

        // Admin ekle butonu — sağ alt köşe FAB
        if (isAdmin) {
            androidx.compose.material3.FloatingActionButton(
                onClick          = { showAdd = true },
                containerColor   = Primary,
                contentColor     = Color.White,
                shape            = RoundedCornerShape(16.dp),
                modifier         = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 20.dp, bottom = 20.dp),
            ) {
                Row(
                    verticalAlignment    = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier             = Modifier.padding(horizontal = 16.dp),
                ) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                    Text("Ekle", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }
    }

    if (showAdd) {
        AddGrammarRuleDialog(
            onDismiss = { showAdd = false },
            onSave = { title, titleTr, content, contentTr ->
                vm.addGrammarRule(title, titleTr, content, contentTr) { showAdd = false }
            },
        )
    }

    editTarget?.let { target ->
        AddGrammarRuleDialog(
            onDismiss = { editTarget = null },
            initial   = target,
            onSave    = { title, titleTr, content, contentTr ->
                vm.updateGrammarRule(target.id, title, titleTr, content, contentTr) { editTarget = null }
            },
        )
    }
}



// Kural sırasına göre tekrarlayan renk paleti (Primary tonları)
private val grammarCardAccents = listOf(
    0xFF7C3AED, // mor
    0xFF6D28D9, // koyu mor
    0xFF8B5CF6, // açık mor
    0xFF5B21B6, // derin mor
    0xFF9333EA, // parlak mor
)

@Composable
private fun GrammarRuleCard(
    rule: GrammarRule,
    language: String,
    isAdmin: Boolean,
    index: Int,
    onDelete: () -> Unit,
    onEdit: ((GrammarRule) -> Unit)? = null,
    autoOpen: Boolean = false,
    onAutoOpenConsumed: () -> Unit = {},
    feedVm: FeedViewModel = hiltViewModel(),
    onShareResult: (String) -> Unit = {},
) {
    var showDetail  by remember { mutableStateOf(false) }
    var showConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(autoOpen) {
        if (autoOpen) {
            showDetail = true
            onAutoOpenConsumed()
        }
    }

    val accentColor    = Color(grammarCardAccents[index % grammarCardAccents.size])
    val displayTitle   = if (language == "ku") rule.title else rule.titleTr.ifBlank { rule.title }
    val displayContent = if (language == "ku") rule.content else rule.contentTr.ifBlank { rule.content }
    val hasKuSubtitle  = language != "ku" && rule.title.isNotBlank() && rule.title != rule.titleTr

    Surface(
        shape    = RoundedCornerShape(16.dp),
        color    = HeftSurface,
        modifier = Modifier.fillMaxWidth(),
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showDetail = true }
                .padding(start = 0.dp, end = 12.dp, top = 0.dp, bottom = 0.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Sol renkli şerit + numara
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(64.dp)
                    .background(
                        color = accentColor,
                        shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp),
                    )
            )
            Box(
                modifier = Modifier
                    .padding(start = 12.dp)
                    .size(32.dp)
                    .background(accentColor.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "${index + 1}",
                    color      = accentColor,
                    fontSize   = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f).padding(vertical = 14.dp)) {
                Text(
                    displayTitle,
                    fontWeight = FontWeight.Bold,
                    color      = OnBackground,
                    fontSize   = 14.sp,
                    lineHeight = 18.sp,
                )
                if (hasKuSubtitle) {
                    Text(
                        rule.title,
                        color    = accentColor,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            // Admin butonları
            if (isAdmin) {
                if (onEdit != null) {
                    IconButton(onClick = { onEdit(rule) }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Edit, null, tint = Primary.copy(alpha = 0.7f), modifier = Modifier.size(15.dp))
                    }
                }
                IconButton(onClick = { showConfirm = true }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Delete, null, tint = Muted, modifier = Modifier.size(16.dp))
                }
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint     = accentColor.copy(alpha = 0.6f),
                modifier = Modifier.size(20.dp),
            )
        }
    }

    // ── Tam ekran içerik Dialog'u ────────────────────────────────────────────
    if (showDetail) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showDetail = false },
            properties = androidx.compose.ui.window.DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows  = false,
            ),
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color    = Background,
            ) {
                Scaffold(
                    containerColor = Background,
                    topBar = {
                        TopAppBar(
                            title = {
                                Column {
                                    Text(
                                        displayTitle,
                                        fontWeight = FontWeight.ExtraBold,
                                        color      = OnBackground,
                                        fontSize   = 16.sp,
                                    )
                                    if (hasKuSubtitle) {
                                        Text(
                                            rule.title,
                                            color    = accentColor,
                                            fontSize = 11.sp,
                                        )
                                    }
                                }
                            },
                            navigationIcon = {
                                IconButton(onClick = { showDetail = false }) {
                                    Icon(Icons.Default.Close, null, tint = OnBackground)
                                }
                            },
                            actions = {
                                // Feed'de paylaş
                                IconButton(onClick = {
                                    feedVm.repostGrammarRule(
                                        ruleId      = rule.id,
                                        ruleTitle   = displayTitle,
                                        rulePreview = displayContent,
                                        onResult    = { ok ->
                                            onShareResult(if (ok) Strings.shareGrammarSuccess(language) else Strings.shareFailed(language))
                                        },
                                    )
                                }) {
                                    Icon(Icons.Default.Share, contentDescription = Strings.shareGrammarRule(language), tint = accentColor, modifier = Modifier.size(18.dp))
                                }
                                // Sol renkli numara rozeti
                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = accentColor.copy(alpha = 0.15f),
                                ) {
                                    Text(
                                        "${index + 1}",
                                        color      = accentColor,
                                        fontWeight = FontWeight.Bold,
                                        fontSize   = 12.sp,
                                        modifier   = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                            },
                            colors = TopAppBarDefaults.topAppBarColors(containerColor = Background),
                        )
                    },
                ) { pad ->
                    // Üst renkli şerit
                    Column(modifier = Modifier.fillMaxSize()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .background(accentColor),
                        )
                        androidx.compose.foundation.lazy.LazyColumn(
                            modifier       = Modifier
                                .fillMaxSize()
                                .padding(pad),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                start = 20.dp, end = 20.dp, top = 16.dp, bottom = 32.dp,
                            ),
                        ) {
                            item {
                                GrammarRichContent(
                                    html        = displayContent,
                                    accentColor = accentColor,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text("Sil?", color = OnBackground) },
            text  = { Text("«${rule.titleTr.ifBlank { rule.title }}» silinsin mi?", color = Muted) },
            confirmButton = {
                TextButton(onClick = { onDelete(); showConfirm = false }) {
                    Text("Sil", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }) { Text("İptal", color = Muted) }
            },
            containerColor = HeftSurface,
        )
    }
}

// ── Zengin İçerik Renderer (kart içi HTML gösterimi) ─────────────────────────
// HTML'yi <table> bloklarına ve metin bloklarına ayırır; her birini ayrı render eder.
@Composable
private fun GrammarRichContent(html: String, accentColor: Color) {
    if (html.isBlank()) return

    // HTML'yi tablo ve metin bloklarına böl
    val blocks = splitHtmlBlocks(html)

    Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)) {
        blocks.forEach { block ->
            if (block.isTable) {
                GrammarHtmlTable(html = block.content, accentColor = accentColor)
            } else {
                GrammarTextBlock(html = block.content, accentColor = accentColor)
            }
        }
    }
}

private data class HtmlBlock(val content: String, val isTable: Boolean)

/** HTML'yi <table>…</table> ve metin parçalarına böler */
private fun splitHtmlBlocks(html: String): List<HtmlBlock> {
    val result  = mutableListOf<HtmlBlock>()
    val tableRx = Regex("<table[^>]*>.*?</table>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    var last    = 0
    tableRx.findAll(html).forEach { m ->
        if (m.range.first > last) {
            val text = html.substring(last, m.range.first).trim()
            if (text.isNotEmpty()) result.add(HtmlBlock(text, false))
        }
        result.add(HtmlBlock(m.value, true))
        last = m.range.last + 1
    }
    if (last < html.length) {
        val text = html.substring(last).trim()
        if (text.isNotEmpty()) result.add(HtmlBlock(text, false))
    }
    return result
}

/** Markdown içeriği Markwon ile render eder */
@Composable
private fun GrammarTextBlock(html: String, accentColor: Color) {
    com.heftreng.app.ui.component.MarkdownView(
        markdown = html,
        modifier = Modifier.fillMaxWidth(),
    )
}

// Tablo renderer
@Composable
private fun GrammarHtmlTable(html: String, accentColor: Color) {
    val rows = Regex("<tr[^>]*>(.*?)</tr>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        .findAll(html).map { row ->
            Regex("<t[dh][^>]*>(.*?)</t[dh]>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
                .findAll(row.groupValues[1])
                .map { cell ->
                    cell.groupValues[1]
                        .replace(Regex("<[^>]+>"), "")
                        .replace("&amp;","&").replace("&lt;","<").replace("&gt;",">")
                        .replace("&nbsp;"," ").trim()
                }.toList()
        }.filter { it.isNotEmpty() }.toList()

    if (rows.isEmpty()) {
        Text(html.replace(Regex("<[^>]+>"), "").trim(),
            color = OnBackground.copy(alpha = 0.88f), fontSize = 13.5.sp, lineHeight = 22.sp)
        return
    }

    val colCount = rows.maxOf { it.size }
    Surface(
        shape    = RoundedCornerShape(10.dp),
        border   = androidx.compose.foundation.BorderStroke(1.dp, accentColor.copy(alpha = 0.25f)),
        color    = Color.Transparent,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            rows.forEachIndexed { ri, cells ->
                val isHeader = ri == 0
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            when {
                                isHeader  -> accentColor.copy(alpha = 0.15f)
                                ri % 2 == 0 -> accentColor.copy(alpha = 0.04f)
                                else      -> Color.Transparent
                            }
                        ),
                ) {
                    for (ci in 0 until colCount) {
                        val cellText = cells.getOrElse(ci) { "" }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .then(
                                    if (ci > 0) Modifier.border(
                                        start = 1.dp,
                                        color = accentColor.copy(alpha = 0.18f),
                                    ) else Modifier
                                )
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                        ) {
                            Text(
                                cellText,
                                color      = if (isHeader) accentColor else OnBackground.copy(alpha = 0.88f),
                                fontWeight = if (isHeader) FontWeight.Bold else FontWeight.Normal,
                                fontSize   = 12.5.sp,
                                lineHeight = 18.sp,
                            )
                        }
                    }
                }
                if (ri < rows.size - 1) {
                    HorizontalDivider(color = accentColor.copy(alpha = 0.18f), thickness = 0.5.dp)
                }
            }
        }
    }
}

// Modifier extension for border on one side
private fun Modifier.border(start: androidx.compose.ui.unit.Dp, color: Color): Modifier =
    this.then(Modifier.drawWithContent {
        drawContent()
        drawLine(
            color      = color,
            start      = androidx.compose.ui.geometry.Offset(0f, 0f),
            end        = androidx.compose.ui.geometry.Offset(0f, size.height),
            strokeWidth = start.toPx(),
        )
    })

// ── Kural Ekle / Düzenle — Tam Ekran Dialog (RichTextEditor'lı) ──────────────
@Composable
private fun AddGrammarRuleDialog(
    onDismiss : () -> Unit,
    onSave    : (String, String, String, String) -> Unit,
    initial   : GrammarRule? = null,
) {
    var title     by remember { mutableStateOf(initial?.title     ?: "") }
    var titleTr   by remember { mutableStateOf(initial?.titleTr   ?: "") }
    var content   by remember { mutableStateOf(initial?.content   ?: "") }
    var contentTr by remember { mutableStateOf(initial?.contentTr ?: "") }
    // 0=Kürtçe 1=Türkçe editör sekmesi
    var editorTab by remember { mutableIntStateOf(0) }

    val isEdit = initial != null

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.97f)
                .fillMaxHeight(0.92f),
            shape  = RoundedCornerShape(20.dp),
            color  = Background,
        ) {
            Scaffold(
                containerColor = Background,
                topBar = {
                    TopAppBar(
                        title = {
                            Text(
                                if (isEdit) "Kuralı Düzenle" else "Yeni Kural Ekle",
                                fontWeight = FontWeight.ExtraBold,
                                color = OnBackground, fontSize = 16.sp,
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Default.Close, null, tint = Muted)
                            }
                        },
                        actions = {
                            Button(
                                onClick = {
                                    if (title.isNotBlank() && titleTr.isNotBlank())
                                        onSave(title, titleTr, content, contentTr)
                                },
                                enabled = title.isNotBlank() && titleTr.isNotBlank(),
                                colors  = ButtonDefaults.buttonColors(containerColor = Primary),
                                shape   = RoundedCornerShape(10.dp),
                                modifier = Modifier.padding(end = 8.dp),
                            ) {
                                Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Kaydet", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Background),
                    )
                },
            ) { pad ->
                androidx.compose.foundation.lazy.LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(pad),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // Başlıklar
                    item {
                        Text("Başlıklar", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        OutlinedTextField(
                            value    = title, onValueChange = { title = it },
                            label    = { Text("📚 Başlık (Kurmancî) *", fontSize = 12.sp) },
                            modifier = Modifier.fillMaxWidth(), singleLine = true,
                            colors   = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor   = Primary, unfocusedBorderColor = SurfaceVar,
                                focusedTextColor     = OnBackground, unfocusedTextColor = OnBackground,
                                focusedLabelColor    = Primary, unfocusedLabelColor = Muted,
                            ),
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value    = titleTr, onValueChange = { titleTr = it },
                            label    = { Text("🇹🇷 Başlık (Türkçe) *", fontSize = 12.sp) },
                            modifier = Modifier.fillMaxWidth(), singleLine = true,
                            colors   = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor   = Primary, unfocusedBorderColor = SurfaceVar,
                                focusedTextColor     = OnBackground, unfocusedTextColor = OnBackground,
                                focusedLabelColor    = Primary, unfocusedLabelColor = Muted,
                            ),
                        )
                    }

                    // İçerik editörü sekmeli
                    item {
                        HorizontalDivider(color = Divider, thickness = 0.5.dp)
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            listOf("📚 Kurmancî", "🇹🇷 Türkçe").forEachIndexed { i, label ->
                                FilterChip(
                                    selected = editorTab == i,
                                    onClick  = { editorTab = i },
                                    label    = { Text(label, fontSize = 12.sp) },
                                    colors   = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor     = Primary,
                                        selectedLabelColor         = Color.White,
                                        containerColor             = HeftSurface,
                                        labelColor                 = Muted,
                                    ),
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape    = RoundedCornerShape(12.dp),
                            color    = HeftSurface,
                        ) {
                            Column(modifier = Modifier.padding(4.dp)) {
                                // Açıklama
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Icon(Icons.Default.Edit, null, tint = Primary, modifier = Modifier.size(14.dp))
                                    Text(
                                if (editorTab == 0) "Kurmancî içerik — Markdown destekler"
                                        else "Türkçe içerik — Markdown destekler",
                                        color = Muted, fontSize = 11.sp,
                                    )
                                }
                                if (editorTab == 0) {
                                    RichTextEditor(
                                        value    = content,
                                        onChange = { content = it },
                                        modifier = Modifier.fillMaxWidth(),
                                        placeholder = "Kurmancî dilbilgisi kuralını buraya yazın...",
                                    )
                                } else {
                                    RichTextEditor(
                                        value    = contentTr,
                                        onChange = { contentTr = it },
                                        modifier = Modifier.fillMaxWidth(),
                                        placeholder = "Türkçe açıklamayı buraya yazın...",
                                    )
                                }
                            }
                        }
                    }

                    item {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Primary.copy(alpha = 0.08f),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("💡 Markdown Sözdizimi", color = Primary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Text(
                                    "**kalın**  _italik_  ~~üstü çizili~~\n" +
                                    "## Başlık  ### Alt Başlık\n" +
                                    "- liste öğesi  > alıntı  `kod`\n" +
                                    "| Kürtçe | Türkçe |\n|--------|--------|\n| Silav  | Merhaba |",
                                    color    = Muted,
                                    fontSize = 10.5.sp,
                                    lineHeight = 16.sp,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// -- Yardımcılar --------------------------------------------------------------
private fun parseColor(hex: String): Color {
    return try { Color(android.graphics.Color.parseColor(hex)) }
    catch (e: Exception) { Color(0xFF8B5CF6) }
}
