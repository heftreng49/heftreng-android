package com.heftreng.app.ui.component

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.heftreng.app.ui.theme.*
import kotlinx.coroutines.delay

// ── Shimmer Brush ─────────────────────────────────────────────────────────────
@Composable
fun shimmerBrush(): Brush {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val x by transition.animateFloat(
        initialValue  = 0f,
        targetValue   = 1200f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing)),
        label         = "shimmerX",
    )
    val base  = Shimmer
    val light = Shimmer.copy(alpha = Shimmer.alpha * 0.3f)
    return Brush.linearGradient(
        colors = listOf(base, light, base),
        start  = Offset(x - 300f, 0f),
        end    = Offset(x + 300f, 0f),
    )
}

// ── Skeleton parçaları ────────────────────────────────────────────────────────
@Composable
fun SkeletonBox(modifier: Modifier = Modifier, radius: Dp = 8.dp) {
    Box(modifier.clip(RoundedCornerShape(radius)).background(shimmerBrush()))
}

@Composable
fun SkeletonCircle(size: Dp = 44.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(shimmerBrush()))
}

// ── PostCard Skeleton ─────────────────────────────────────────────────────────
@Composable
fun PostCardSkeleton() {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 5.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(HeftCard)
            .padding(horizontal = 15.dp, vertical = 13.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SkeletonCircle(44.dp)
            Spacer(Modifier.width(10.dp))
            Column {
                SkeletonBox(Modifier.width(130.dp).height(13.dp))
                Spacer(Modifier.height(5.dp))
                SkeletonBox(Modifier.width(80.dp).height(10.dp))
            }
        }
        Spacer(Modifier.height(13.dp))
        SkeletonBox(Modifier.fillMaxWidth().height(13.dp))
        Spacer(Modifier.height(6.dp))
        SkeletonBox(Modifier.fillMaxWidth(0.8f).height(13.dp))
        Spacer(Modifier.height(6.dp))
        SkeletonBox(Modifier.fillMaxWidth(0.55f).height(13.dp))
        Spacer(Modifier.height(13.dp))
        SkeletonBox(Modifier.fillMaxWidth().height(190.dp), radius = 12.dp)
        Spacer(Modifier.height(13.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            SkeletonBox(Modifier.width(44.dp).height(18.dp))
            SkeletonBox(Modifier.width(44.dp).height(18.dp))
            SkeletonBox(Modifier.width(44.dp).height(18.dp))
        }
    }
}

// ── Profil Header Skeleton ────────────────────────────────────────────────────
@Composable
fun ProfileHeaderSkeleton() {
    Column(Modifier.fillMaxWidth()) {
        SkeletonBox(Modifier.fillMaxWidth().height(100.dp), radius = 0.dp)
        Column(Modifier.padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                SkeletonCircle(76.dp)
                Spacer(Modifier.weight(1f))
                SkeletonBox(Modifier.width(100.dp).height(36.dp), radius = 10.dp)
            }
            Spacer(Modifier.height(12.dp))
            SkeletonBox(Modifier.width(160.dp).height(16.dp))
            Spacer(Modifier.height(6.dp))
            SkeletonBox(Modifier.width(100.dp).height(12.dp))
            Spacer(Modifier.height(8.dp))
            SkeletonBox(Modifier.fillMaxWidth(0.9f).height(12.dp))
            Spacer(Modifier.height(4.dp))
            SkeletonBox(Modifier.fillMaxWidth(0.7f).height(12.dp))
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                repeat(3) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        SkeletonBox(Modifier.width(38.dp).height(18.dp))
                        Spacer(Modifier.height(4.dp))
                        SkeletonBox(Modifier.width(54.dp).height(11.dp))
                    }
                }
            }
        }
    }
}

// ── Search Row Skeleton ───────────────────────────────────────────────────────
@Composable
fun SearchRowSkeleton() {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SkeletonCircle(44.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            SkeletonBox(Modifier.width(140.dp).height(13.dp))
            Spacer(Modifier.height(5.dp))
            SkeletonBox(Modifier.width(90.dp).height(11.dp))
        }
    }
}

// ── Önerilen Kart Skeleton (LazyRow) ─────────────────────────────────────────
@Composable
fun SuggestedCardSkeleton() {
    Column(
        Modifier
            .width(120.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(HeftCard)
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SkeletonCircle(64.dp)
        Spacer(Modifier.height(8.dp))
        SkeletonBox(Modifier.width(80.dp).height(12.dp))
        Spacer(Modifier.height(4.dp))
        SkeletonBox(Modifier.width(60.dp).height(10.dp))
        Spacer(Modifier.height(10.dp))
        SkeletonBox(Modifier.fillMaxWidth().height(28.dp), radius = 20.dp)
    }
}

// ── Double-tap Heart Burst ────────────────────────────────────────────────────
@Composable
fun HeartBurstOverlay(visible: Boolean, onEnd: () -> Unit) {
    var show by remember { mutableStateOf(false) }
    LaunchedEffect(visible) {
        if (visible) { show = true; delay(650); show = false; onEnd() }
    }
    AnimatedVisibility(
        visible = show,
        enter   = scaleIn(spring(Spring.DampingRatioLowBouncy, Spring.StiffnessLow), 0.25f) + fadeIn(tween(80)),
        exit    = scaleOut(tween(200), 1.5f) + fadeOut(tween(200)),
    ) {
        Box(Modifier.fillMaxWidth().height(240.dp), Alignment.Center) {
            Icon(Icons.Filled.Favorite, null, tint = Color(0xFFFF3A5C), modifier = Modifier.size(88.dp))
        }
    }
}

// ── Haptic ────────────────────────────────────────────────────────────────────
enum class HapticType { LIGHT, MEDIUM, HEAVY, DOUBLE }

fun triggerHaptic(context: Context, type: HapticType = HapticType.LIGHT) {
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            val effect = when (type) {
                HapticType.LIGHT  -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
                HapticType.MEDIUM -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
                HapticType.HEAVY  -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
                HapticType.DOUBLE -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK)
            }
            vm.defaultVibrator.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            val v = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            val ms = when (type) { HapticType.LIGHT -> 18L; HapticType.MEDIUM -> 36L; HapticType.HEAVY -> 55L; HapticType.DOUBLE -> 70L }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
            else @Suppress("DEPRECATION") v.vibrate(ms)
        }
    } catch (_: Exception) {}
}

// ── Item giriş animasyonu ─────────────────────────────────────────────────────
fun itemEnterTransition(delayMs: Int = 0): EnterTransition =
    fadeIn(tween(200, delayMs)) +
    slideInVertically(spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow)) { it / 5 }

// ── Sayfa geçiş animasyonları ─────────────────────────────────────────────────
val slideInFromRight: EnterTransition  = slideInHorizontally(tween(280, easing = FastOutSlowInEasing)) { it } + fadeIn(tween(220))
val slideOutToLeft:   ExitTransition   = slideOutHorizontally(tween(280, easing = FastOutSlowInEasing)) { -it / 3 } + fadeOut(tween(200))
val slideInFromLeft:  EnterTransition  = slideInHorizontally(tween(280, easing = FastOutSlowInEasing)) { -it } + fadeIn(tween(220))
val slideOutToRight:  ExitTransition   = slideOutHorizontally(tween(280, easing = FastOutSlowInEasing)) { it } + fadeOut(tween(200))
