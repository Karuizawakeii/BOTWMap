# -*- coding: utf-8 -*-
"""铺满全屏 + 底栏反色 + 液态玻璃按钮 + 滑动转场 + 裁切修复"""
import io

BASE = r"D:\repos\BOTWMap\app\src\main\java\com\botwmap\app"

def load(rel):
    return io.open(BASE + "\\" + rel, encoding="utf-8").read()

def save(rel, s):
    io.open(BASE + "\\" + rel, "w", encoding="utf-8").write(s)

def ensure_imports(s, imports):
    lines = s.split("\n")
    last_import = max(i for i, l in enumerate(lines) if l.startswith("import "))
    missing = [im for im in imports if ("import " + im) not in s]
    if missing:
        for j, im in enumerate(missing):
            lines.insert(last_import + 1 + j, "import " + im)
    return "\n".join(lines)

# ========== 1) 地图默认铺满全屏（cover 而非 contain） ==========
p = "ui/map/MapCameraState.kt"
s = load(p)
old = """        if (!viewInitialized) {
            scale = fitScale.coerceIn(minScale, maxScale)
            offset = Offset(
                (size.width - MAP_W * scale) / 2f,
                (size.height - MAP_H * scale) / 2f,
            )
            viewInitialized = true"""
new = """        if (!viewInitialized) {
            // 默认铺满全屏（cover）：短边 fit、长边溢出裁切，无上下留白
            val coverScale = maxOf(size.width / MAP_W, size.height / MAP_H)
            scale = coverScale.coerceIn(minScale, maxScale)
            offset = Offset(
                (size.width - MAP_W * scale) / 2f,
                (size.height - MAP_H * scale) / 2f,
            )
            viewInitialized = true"""
assert old in s, "cover"
s = s.replace(old, new, 1)
save(p, s)
print("OK Camera cover")

# ========== 2) Legado 底栏：contentTint 反色参数 ==========
p = "ui/effect/liquid/LegadoLiquidFloatingBottomBar.kt"
s = load(p)
old = """    /** 模糊开启时的容器透明度（0..1）；对应设置页「透明度」 */
    containerAlpha: Float = 0.40f,
) {"""
new = """    /** 模糊开启时的容器透明度（0..1）；对应设置页「透明度」 */
    containerAlpha: Float = 0.40f,
    /** 图标/文字反色：null = 跟随主题；非 null = 按背景亮度指定（自动反色） */
    contentTint: Color? = null,
) {"""
assert old in s, "legado sig"
s = s.replace(old, new, 1)
old = """    val surfaceContainer = MaterialTheme.colorScheme.surface
    val accentColor = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant"""
new = """    val surfaceContainer = MaterialTheme.colorScheme.surface
    val accentColor = contentTint ?: MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = contentTint?.copy(alpha = 0.72f)
        ?: MaterialTheme.colorScheme.onSurfaceVariant"""
assert old in s, "legado tint"
s = s.replace(old, new, 1)
save(p, s)
print("OK Legado tint")

# ========== 3) AppNavHost：LocalLiquidBackdrop + 亮度反色 + 滑动转场 ==========
p = "ui/navigation/AppNavHost.kt"
s = load(p)
s = ensure_imports(s, [
    "androidx.compose.animation.core.tween",
    "androidx.compose.animation.slideInHorizontally",
    "androidx.compose.animation.slideOutHorizontally",
    "androidx.compose.ui.graphics.luminance",
    "com.kyant.backdrop.backdrops.LayerBackdrop",
    "com.kyant.backdrop.drawBackdrop",
    "com.kyant.backdrop.effects.blur",
    "com.kyant.backdrop.effects.vibrancy",
    "com.botwmap.app.ui.effect.rememberBlurEffectsEnabled",
])

# 3a. LocalLiquidBackdrop + liquidGlass 扩展（追加文件尾 helpers 前）
old = """/** 全局背景状态（AppNavHost 提供，地图采样区复用同一份 bitmap） */"""
new = """/** 液态玻璃采样层（AppNavHost 提供，地图按钮等消费） */
val LocalLiquidBackdrop = staticCompositionLocalOf<LayerBackdrop?> { null }

/**
 * 液态玻璃背景：RuntimeShader 可用时采样 backdrop 做 vibrancy+blur 玻璃，
 * 否则回退半透明底色。
 */
@Composable
fun Modifier.liquidGlass(fallback: Color): Modifier {
    val backdrop = LocalLiquidBackdrop.current
    val enabled = rememberBlurEffectsEnabled() && backdrop != null
    return if (enabled) {
        this.drawBackdrop(
            backdrop = backdrop,
            shape = { CircleShape },
            effects = {
                vibrancy()
                blur(18.dp.toPx())
            },
            onDrawSurface = { drawRect(fallback.copy(alpha = 0.38f)) },
        )
    } else {
        this.background(fallback.copy(alpha = 0.78f), CircleShape)
    }
}

/** 全局背景状态（AppNavHost 提供，地图采样区复用同一份 bitmap） */"""
assert old in s, "glass helper"
s = s.replace(old, new, 1)

# 3b. Provider 提供 LocalLiquidBackdrop（并入现有 Provider）
old = """    CompositionLocalProvider(
        LocalBackground provides GlobalBackgroundState(
            bitmap = bgBitmap,
            enabled = bgEnabled,
            blurDp = bgBlur,
            alpha = bgAlpha,
        ),
    ) {"""
new = """    CompositionLocalProvider(
        LocalBackground provides GlobalBackgroundState(
            bitmap = bgBitmap,
            enabled = bgEnabled,
            blurDp = bgBlur,
            alpha = bgAlpha,
        ),
        LocalLiquidBackdrop provides backdrop,
    ) {"""
assert old in s, "provider backdrop"
s = s.replace(old, new, 1)

# 3c. 背景亮度计算 + 底栏反色 tint
old = """    val barBlur by repository.customBarBlur.collectAsStateWithLifecycle(initialValue = 8f)"""
new = """    val barBlur by repository.customBarBlur.collectAsStateWithLifecycle(initialValue = 8f)

    // 背景亮度（图 + 蒙层综合）：底栏图标/文字自动反色
    val barContentTint: Color? = remember(bgBitmap, bgEnabled, bgBlur) {
        if (bgBitmap == null || !bgEnabled) {
            null // 无背景图：跟随主题
        } else {
            val androidBmp = bgBitmap!!.asAndroidBitmap()
            val probe = android.graphics.Bitmap.createScaledBitmap(androidBmp, 24, 24, true)
            var sum = 0f
            val px = IntArray(24 * 24)
            probe.getPixels(px, 0, 24, 0, 0, 24, 24)
            for (c in px) {
                val r = ((c shr 16) and 0xFF) / 255f
                val g = ((c shr 8) and 0xFF) / 255f
                val b = (c and 0xFF) / 255f
                sum += (0.2126f * r + 0.7152f * g + 0.0722f * b)
            }
            if (probe !== androidBmp) probe.recycle()
            var lum = sum / px.size
            // 蒙层综合：亮主题白蒙提亮、暗主题黑蒙压暗
            val isDarkSurface = MaterialTheme.colorScheme.surface.luminance() < 0.5f
            lum = if (isDarkSurface) lum * 0.65f else lum * 0.55f + 0.45f
            if (lum >= 0.5f) Color(0xE61C1C1E) else Color(0xF5FFFFFF)
        }
    }"""
assert old in s, "luminance"
s = s.replace(old, new, 1)

old = """                    blurRadius = barBlur.dp,
                    containerAlpha = barAlpha,
                )"""
new = """                    blurRadius = barBlur.dp,
                    containerAlpha = barAlpha,
                    contentTint = barContentTint,
                )"""
assert old in s, "legado call"
s = s.replace(old, new, 1)

# 3d. NavHost 滑动转场（非渐隐）
old = """                NavHost(
                    navController = navController,
                    startDestination = if (onboardingDone == true) MapRoute else OnboardingRoute,
                    // 底部不预留：内容延伸到底栏之下，底栏真悬浮
                    modifier = Modifier.padding(PaddingValues(top = padding.calculateTopPadding())),
                ) {"""
new = """                NavHost(
                    navController = navController,
                    startDestination = if (onboardingDone == true) MapRoute else OnboardingRoute,
                    // 底部不预留：内容延伸到底栏之下，底栏真悬浮
                    modifier = Modifier.padding(PaddingValues(top = padding.calculateTopPadding())),
                    // 页面切换：纯滑动过渡（无渐隐）
                    enterTransition = { slideInHorizontally(tween(320)) { it } },
                    exitTransition = { slideOutHorizontally(tween(320)) { -it / 4 } },
                    popEnterTransition = { slideInHorizontally(tween(320)) { -it / 4 } },
                    popExitTransition = { slideOutHorizontally(tween(320)) { it } },
                ) {"""
assert old in s, "nav transitions"
s = s.replace(old, new, 1)
save(p, s)
print("OK AppNavHost")

# ========== 4) MapScreen：三按钮液态玻璃 + 进度钮对齐尺寸 ==========
p = "ui/map/MapScreen.kt"
s = load(p)
s = ensure_imports(s, [
    "com.botwmap.app.ui.navigation.liquidGlass",
])

# 4a. 搜索钮玻璃
old = """                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.75f),
                    shadowElevation = 4.dp,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = "搜索",
                        modifier = Modifier
                            .padding(10.dp)
                            .size(22.dp),
                    )
                }"""
new = """                Box(
                    Modifier
                        .liquidGlass(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(11.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = "搜索",
                        modifier = Modifier.size(22.dp),
                    )
                }"""
assert old in s, "search glass"
s = s.replace(old, new, 1)

# 4b. 进度钮改 FAB 尺寸（对齐筛选）+ 玻璃；筛选钮玻璃
old = """            SmallFloatingActionButton(
                onClick = onOpenStats,
                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.88f),
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ) {
                Icon(Icons.Filled.BarChart, contentDescription = "收集进度")
            }
            FloatingActionButton(
                onClick = onOpenLayers,
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.88f),
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Icon(Icons.Filled.Layers, contentDescription = "图层")
            }"""
new = """            FloatingActionButton(
                onClick = onOpenStats,
                modifier = Modifier.liquidGlass(MaterialTheme.colorScheme.surfaceContainerHigh),
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ) {
                Icon(Icons.Filled.BarChart, contentDescription = "收集进度")
            }
            FloatingActionButton(
                onClick = onOpenLayers,
                modifier = Modifier.liquidGlass(MaterialTheme.colorScheme.surfaceContainerHigh),
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ) {
                Icon(Icons.Filled.Layers, contentDescription = "图层")
            }"""
assert old in s, "fabs glass"
s = s.replace(old, new, 1)
save(p, s)
print("OK MapScreen buttons")

# ========== 5) 裁切修复：图层页 contentPadding + 任务页 padding 加大 ==========
p = "ui/layers/LayersPanel.kt"
s = load(p)
s = ensure_imports(s, ["androidx.compose.foundation.layout.PaddingValues"])
old = """                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {"""
cnt = s.count(old)
assert cnt == 2, "lp lazy: %d" % cnt
s = s.replace(
    old,
    """                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 40.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {""",
)
# Column 底部留白加大
s = s.replace(".padding(bottom = 32.dp),", ".padding(bottom = 48.dp),", 1)
save(p, s)
print("OK Layers padding")

p = "ui/quests/QuestsScreen.kt"
s = load(p)
old = "LazyColumn(contentPadding = PaddingValues(bottom = 160.dp)) {"
new = "LazyColumn(contentPadding = PaddingValues(bottom = 220.dp)) {"
assert old in s, "quest pad"
s = s.replace(old, new, 1)
save(p, s)
print("OK Quests padding")

print("ALL DONE")
