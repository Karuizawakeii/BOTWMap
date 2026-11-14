package com.botwmap.app.ui.navigation

import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.ui.draw.blur
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.botwmap.app.data.repository.MapRepository
import com.botwmap.app.ui.dex.DexScreen
import com.botwmap.app.ui.effect.liquid.LegadoLiquidFloatingBottomBar
import com.botwmap.app.ui.effect.liquid.LiquidNavItem
import com.botwmap.app.ui.effect.rememberBlurEffectsEnabled
import com.botwmap.app.ui.map.MapScreen
import com.botwmap.app.ui.onboarding.OnboardingScreen
import com.botwmap.app.ui.quests.QuestsScreen
import com.botwmap.app.ui.settings.SettingsScreen
import com.kyant.backdrop.backdrops.layerBackdrop
import com.botwmap.app.ui.effect.rememberScreenBackdrop
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject
import java.io.File
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.vibrancy
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues

/** Type-Safe 路由定义 */
@Serializable data object OnboardingRoute
@Serializable data object MapRoute
@Serializable data object QuestRoute
@Serializable data object DexRoute
@Serializable data object SettingsRoute
@Serializable data object LayersRoute
@Serializable data object StatsRoute

private data class BottomTab(
    val route: Any,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
)

// Tab 索引（0=地图,1=任务,2=图鉴,3=设置；非 Tab 路由=-1）
private fun tabIndex(route: String?): Int = bottomTabs.indexOfFirst { tab ->
    route != null && route.contains(tab.route::class.simpleName ?: "~")
}

// 切换方向：Tab 间按索引差；非 Tab（图层/进度等子页面）push=1（右进）/ pop=-1（返回左进）
private fun dirOf(initialRoute: String?, targetRoute: String?, isPop: Boolean): Int {
    val i0 = tabIndex(initialRoute)
    val i1 = tabIndex(targetRoute)
    return if (i0 >= 0 && i1 >= 0 && i1 != i0) {
        if (i1 > i0) 1 else -1
    } else {
        if (isPop) -1 else 1
    }
}

private val bottomTabs = listOf(
    BottomTab(MapRoute, "地图", Icons.Filled.Map),
    BottomTab(QuestRoute, "任务", Icons.Filled.Assignment),
    BottomTab(DexRoute, "图鉴", Icons.Filled.Book),
    BottomTab(SettingsRoute, "设置", Icons.Filled.Settings),
)

@Composable
fun AppNavHost(repository: MapRepository = koinInject()) {
    val onboardingDone by repository.onboardingDone.collectAsStateWithLifecycle(initialValue = null)

    // DataStore 首读完成前显示空白，避免闪烁引导页
    if (onboardingDone == null) {
        Box(Modifier.fillMaxSize())
        return
    }

    val navController = rememberNavController()
    val scope = rememberCoroutineScope()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    // 自定义悬浮底栏只在主 Tab 页显示；二级页面（图层/进度等带返回箭头的页面）隐藏
    val showBottomBar = currentRoute != null &&
        bottomTabs.any { tab -> currentRoute.contains(tab.route::class.simpleName ?: "~") }
    val selectedIndex = bottomTabs.indexOfFirst {
        currentRoute?.contains(it.route::class.simpleName ?: "~") == true
    }.coerceAtLeast(0)

    fun navigateTo(index: Int) {
        navController.navigate(bottomTabs[index].route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    // 液态玻璃底栏：采样 backdrop（Android 12+ RuntimeShader，低版本自动纯色）
    val blurEnabled = rememberBlurEffectsEnabled()
    val barBlur by repository.customBarBlur.collectAsStateWithLifecycle(initialValue = 8f)

    val barAlpha by repository.customBarAlpha.collectAsStateWithLifecycle(initialValue = 0.4f)

    // 全局背景图（设置页选择，与底栏共用同一张）
    val bgEnabled by repository.bgImageEnabled.collectAsStateWithLifecycle(initialValue = true)
    val bgBlur by repository.bgImageBlur.collectAsStateWithLifecycle(initialValue = 0f)
    val bgAlpha by repository.bgImageAlpha.collectAsStateWithLifecycle(initialValue = 1f)
    val context = LocalContext.current
    val bgFile by repository.customBarBgFile.collectAsStateWithLifecycle(initialValue = null)
    val bgBitmap = remember(bgFile) {
        bgFile?.let { name ->
            runCatching {
                BitmapFactory.decodeFile(File(context.filesDir, name).absolutePath)
            }.getOrNull()?.asImageBitmap()
        }
    }

    // 有背景图：纹理不铺底（底栏玻璃透过背景图）；无图：铺 surface 防透明黑洞
    val backdrop = if (blurEnabled) {
        rememberScreenBackdrop(
            backgroundColor = if (bgBitmap != null && bgEnabled) {
                Color.Transparent
            } else {
                MaterialTheme.colorScheme.surface
            },
        )
    } else {
        null
    }
    // 页面内容专用采样层：只含背景图（不含页面自身）——
    // 任务/图鉴等页内玻璃采样它，避免「采样包含自身」的渲染树循环（hwui 栈溢出闪退）
    val pageBackdrop = if (blurEnabled) {
        rememberScreenBackdrop(
            backgroundColor = if (bgBitmap != null && bgEnabled) {
                Color.Transparent
            } else {
                MaterialTheme.colorScheme.surface
            },
        )
    } else {
        null
    }

    // 背景亮度（图 + 蒙层综合）：底栏图标/文字自动反色
    val isDarkSurface = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val barContentTint: Color? = remember(bgBitmap, bgEnabled, isDarkSurface) {
        val bmp = bgBitmap
        if (bmp == null || !bgEnabled) {
            null // 无背景图：跟随主题
        } else {
            val probe = android.graphics.Bitmap.createScaledBitmap(bmp.asAndroidBitmap(), 24, 24, true)
            var sum = 0f
            val px = IntArray(24 * 24)
            probe.getPixels(px, 0, 24, 0, 0, 24, 24)
            for (c in px) {
                val r = ((c shr 16) and 0xFF) / 255f
                val g = ((c shr 8) and 0xFF) / 255f
                val b = (c and 0xFF) / 255f
                sum += (0.2126f * r + 0.7152f * g + 0.0722f * b)
            }
            if (probe !== bmp.asAndroidBitmap()) probe.recycle()
            var lum = sum / px.size
            lum = if (isDarkSurface) lum * 0.65f else lum * 0.55f + 0.45f
            if (lum >= 0.5f) Color(0xE61C1C1E) else Color(0xF5FFFFFF)
        }
    }

    CompositionLocalProvider(
        LocalBackground provides GlobalBackgroundState(
            bitmap = bgBitmap,
            enabled = bgEnabled,
            blurDp = bgBlur,
            alpha = bgAlpha,
        ),
        LocalLiquidBackdrop provides backdrop,
    ) {
    Box(Modifier.fillMaxSize()) {
        // ---- 采样区：背景图 + 蒙层 + 页面内容（底栏玻璃采样并模糊它们）----
        Box(
            Modifier
                .fillMaxSize()
                .then(if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier),
        ) {
            // 背景层独立成 pageBackdrop 采样区（页面内玻璃只采样背景，不含页面内容）
            Box(
                Modifier
                    .fillMaxSize()
                    .then(if (pageBackdrop != null) Modifier.layerBackdrop(pageBackdrop) else Modifier),
            ) {
                GlobalBackground()
            }

            Scaffold(
                containerColor = Color.Transparent,
                // Edge-to-Edge：content 全屏（不消费底部 inset），内容画到手势条区
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
            ) { padding ->
                CompositionLocalProvider(LocalLiquidBackdrop provides pageBackdrop) {
                NavHost(
                    navController = navController,
                    startDestination = if (onboardingDone == true) MapRoute else OnboardingRoute,
                    // 底部不预留：内容延伸到底栏之下，底栏真悬浮
                    // clipToBounds：动画内容不画到容器外（防边缘缝隙）
                    modifier = Modifier
                        .padding(PaddingValues(top = padding.calculateTopPadding()))
                        .clipToBounds(),
                    // 方向按 Tab 索引差计算（禁写死）：目标>当前→新页右进旧页左出；否则反向
                    // 进出同时长同 easing：两页贴合移动，任何时刻无缝隙（不同时长会露出背景竖条）
                    enterTransition = {
                        val d = dirOf(initialState.destination.route, targetState.destination.route, isPop = false)
                        slideInHorizontally(tween(320)) { fullWidth -> fullWidth * d }
                    },
                    exitTransition = {
                        val d = dirOf(initialState.destination.route, targetState.destination.route, isPop = false)
                        slideOutHorizontally(tween(320)) { fullWidth -> -fullWidth * d }
                    },
                    popEnterTransition = {
                        val d = dirOf(initialState.destination.route, targetState.destination.route, isPop = true)
                        slideInHorizontally(tween(320)) { fullWidth -> fullWidth * d }
                    },
                    popExitTransition = {
                        val d = dirOf(initialState.destination.route, targetState.destination.route, isPop = true)
                        slideOutHorizontally(tween(320)) { fullWidth -> -fullWidth * d }
                    },
                ) {
                    composable<OnboardingRoute> {
                        OnboardingScreen(
                            onDone = {
                                scope.launch { repository.setOnboardingDone(true) }
                                navController.navigate(MapRoute) {
                                    popUpTo<OnboardingRoute> { inclusive = true }
                                }
                            },
                        )
                    }
                    composable<MapRoute> {
                        MapScreen(
                            onOpenLayers = { navController.navigate(LayersRoute) },
                            onOpenStats = { navController.navigate(StatsRoute) },
                        )
                    }
                    composable<LayersRoute> {
                        com.botwmap.app.ui.layers.LayersScreen(
                            onBack = { navController.popBackStack() },
                        )
                    }
                    composable<StatsRoute> {
                        com.botwmap.app.ui.stats.StatsScreen(
                            onBack = { navController.popBackStack() },
                        )
                    }
                    composable<QuestRoute> {
                        QuestsScreen(
                            onNavigateToMap = {
                                navController.navigate(MapRoute) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                        )
                    }
                    composable<DexRoute> {
                        DexScreen(
                            onShowMaterialOnMap = { matId ->
                                // 启用该材料图层 + 定位第一个刷点 + 跳地图
                                scope.launch { repository.setMaterialEnabled(matId, true) }
                                repository.materialPointsFor(matId)
                                    ?.firstOrNull()
                                    ?.let { (px, py) ->
                                        repository.requestMapFocus(px.toDouble(), py.toDouble())
                                    }
                                navController.navigate(MapRoute) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                        )
                    }
                    composable<SettingsRoute> {
                        SettingsScreen(onBack = { navController.popBackStack() })
                    }
                }
                } // CompositionLocalProvider(LocalLiquidBackdrop provides pageBackdrop)
            }
        }

        // ---- 底栏（采样区外，悬浮于内容之上；小白条自动避让）----
        AnimatedVisibility(
            visible = showBottomBar,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                LegadoLiquidFloatingBottomBar(
                    items = bottomTabs.map { LiquidNavItem(it.label, it.icon) },
                    selectedIndex = selectedIndex,
                    onItemSelected = ::navigateTo,
                    backdrop = backdrop,
                    blurRadius = barBlur.dp,
                    containerAlpha = barAlpha,
                    contentTint = barContentTint,
                )
            }
        }
    }
    }
}


/** 液态玻璃采样层（AppNavHost 提供，地图按钮等消费） */
val LocalLiquidBackdrop = staticCompositionLocalOf<LayerBackdrop?> { null }

/**
 * 液态玻璃背景：RuntimeShader 可用时采样 backdrop 做 vibrancy+blur 玻璃，
 * 否则回退半透明底色。
 */
@Composable
fun Modifier.liquidGlass(fallback: Color): Modifier {
    val backdrop = LocalLiquidBackdrop.current
    val repository: MapRepository = koinInject()
    // 与底栏共用「模糊度 / 透明度」滑条
    val barBlur by repository.customBarBlur.collectAsStateWithLifecycle(initialValue = 8f)
    val barAlpha by repository.customBarAlpha.collectAsStateWithLifecycle(initialValue = 0.4f)
    val alpha = barAlpha.coerceIn(0f, 1f)
    val enabled = rememberBlurEffectsEnabled() && backdrop != null && barBlur > 0f
    return if (enabled) {
        this.drawBackdrop(
            backdrop = backdrop,
            shape = { CircleShape },
            effects = { blur(barBlur.dp.toPx()) },
            // 圆形覆盖（矩形会在图标后留下方块）
            onDrawSurface = { drawCircle(fallback.copy(alpha = alpha)) },
        )
    } else {
        this.background(fallback.copy(alpha = alpha.coerceAtLeast(0.55f)), CircleShape)
    }
}

/** 全局背景状态（AppNavHost 提供，地图采样区复用同一份 bitmap） */
data class GlobalBackgroundState(
    val bitmap: ImageBitmap?,
    val enabled: Boolean,
    val blurDp: Float,
    val alpha: Float,
)

val LocalBackground = staticCompositionLocalOf {
    GlobalBackgroundState(bitmap = null, enabled = false, blurDp = 0f, alpha = 1f)
}

/** 全局背景图层：设置页选择的图片 + 可读性蒙层（fillMaxSize 铺底） */
@Composable
fun GlobalBackground() {
    val bg = LocalBackground.current
    val bitmap = bg.bitmap ?: return
    if (!bg.enabled) return
    Image(
        bitmap = bitmap,
        contentDescription = null,
        modifier = Modifier
            .fillMaxSize()
            // 轻微放大抵消 RenderEffect 模糊的边缘透明渐变
            .graphicsLayer {
                scaleX = 1.08f
                scaleY = 1.08f
                alpha = bg.alpha.coerceIn(0f, 1f)
            }
            .then(if (bg.blurDp > 0f) Modifier.blur(bg.blurDp.dp) else Modifier),
        contentScale = ContentScale.Crop,
    )
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    Box(
        Modifier
            .fillMaxSize()
            .background(
                if (isDark) {
                    Color.Black.copy(alpha = 0.35f)
                } else {
                    Color.White.copy(alpha = 0.45f)
                },
            ),
    )
}
