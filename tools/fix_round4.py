# -*- coding: utf-8 -*-
"""底栏恢复透图 + 面板背景模糊 + 任务/图鉴残余白块"""
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

# ========== 1) AppNavHost：背景图移回采样区（底栏恢复透图）+ 动态铺底 ==========
p = "ui/navigation/AppNavHost.kt"
s = load(p)

# 1a. 删旧 backdrop 块（在 bgBitmap 之前）
old = """    // 液态玻璃底栏：采样 backdrop（Android 12+ RuntimeShader，低版本自动纯色）
    val blurEnabled = rememberBlurEffectsEnabled()
    // 采样纹理铺 surface 底色：底栏玻璃只含主题色+页面内容，不含全局背景图
    val backdrop = if (blurEnabled) {
        rememberScreenBackdrop(backgroundColor = MaterialTheme.colorScheme.surface)
    } else {
        null
    }
    val barBlur by repository.customBarBlur.collectAsStateWithLifecycle(initialValue = 8f)"""
new = """    // 液态玻璃底栏：采样 backdrop（Android 12+ RuntimeShader，低版本自动纯色）
    val blurEnabled = rememberBlurEffectsEnabled()
    val barBlur by repository.customBarBlur.collectAsStateWithLifecycle(initialValue = 8f)"""
assert old in s, "remove old backdrop"
s = s.replace(old, new, 1)

# 1b. bgBitmap 之后创建 backdrop（有背景图不铺底=底栏透图；无图铺 surface 防黑洞）
old = """    val bgBitmap = remember(bgFile) {
        bgFile?.let { name ->
            runCatching {
                BitmapFactory.decodeFile(File(context.filesDir, name).absolutePath)
            }.getOrNull()?.asImageBitmap()
        }
    }
"""
new = """    val bgBitmap = remember(bgFile) {
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
"""
assert old in s, "insert backdrop after bitmap"
s = s.replace(old, new, 1)

# 1c. 背景图+scrim 移回采样 Box 内
old = """    Box(Modifier.fillMaxSize()) {
        // ---- 全局背景图（采样区外：页面透出、底栏玻璃不显示图片）----
        if (bgBitmap != null && bgEnabled) {
            Image(
                bitmap = bgBitmap,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    // 轻微放大抵消 RenderEffect 模糊的边缘透明渐变
                    .graphicsLayer {
                        scaleX = 1.08f
                        scaleY = 1.08f
                        alpha = bgAlpha.coerceIn(0f, 1f)
                    }
                    .then(if (bgBlur > 0f) Modifier.blur(bgBlur.dp) else Modifier),
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

        // ---- 采样区：仅页面内容（纹理由 rememberScreenBackdrop 铺底色）----
        Box(
            Modifier
                .fillMaxSize()
                .then(if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier),
        ) {
            Scaffold("""
new = """    Box(Modifier.fillMaxSize()) {
        // ---- 采样区：背景图 + 蒙层 + 页面内容（底栏玻璃采样并模糊它们）----
        Box(
            Modifier
                .fillMaxSize()
                .then(if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier),
        ) {
            if (bgBitmap != null && bgEnabled) {
                Image(
                    bitmap = bgBitmap,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        // 轻微放大抵消 RenderEffect 模糊的边缘透明渐变
                        .graphicsLayer {
                            scaleX = 1.08f
                            scaleY = 1.08f
                            alpha = bgAlpha.coerceIn(0f, 1f)
                        }
                        .then(if (bgBlur > 0f) Modifier.blur(bgBlur.dp) else Modifier),
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

            Scaffold("""
assert old in s, "move bg back"
s = s.replace(old, new, 1)
save(p, s)
print("OK AppNavHost")

# ========== 2) MapScreen：地图采样区 + 面板外置 ==========
p = "ui/map/MapScreen.kt"
s = load(p)
s = ensure_imports(s, [
    "com.botwmap.app.ui.effect.rememberBlurEffectsEnabled",
    "com.kyant.backdrop.backdrops.LayerBackdrop",
    "com.kyant.backdrop.backdrops.rememberLayerBackdrop",
])

old = """    Box(Modifier.fillMaxSize()) {
        MapCanvas("""
new = """    Box(Modifier.fillMaxSize()) {
        // 地图自建采样层：面板在采样区外做背景模糊（模糊其后的地图）
        val mapBackdrop = if (rememberBlurEffectsEnabled()) rememberLayerBackdrop() else null
        Box(
            Modifier
                .fillMaxSize()
                .then(
                    if (mapBackdrop != null) Modifier.layerBackdrop(mapBackdrop) else Modifier,
                ),
        ) {
        MapCanvas("""
assert old in s, "map inner box open"
s = s.replace(old, new, 1)

old = """        // ---- 图层 / 统计面板 ----
        LayersPanel("""
new = """        }
        // ---- 图层 / 统计面板（采样区外：drawBackdrop 模糊地图）----
        LayersPanel(
            backdrop = mapBackdrop,"""
assert old in s, "map close inner"
s = s.replace(old, new, 1)

old = """        StatsPanel(
            visible = state.statsVisible,"""
new = """        StatsPanel(
            backdrop = mapBackdrop,
            visible = state.statsVisible,"""
assert old in s, "stats backdrop"
s = s.replace(old, new, 1)
save(p, s)
print("OK MapScreen")

# ========== 3) LayersPanel：backdrop 模糊背景 ==========
p = "ui/layers/LayersPanel.kt"
s = load(p)
s = ensure_imports(s, [
    "com.kyant.backdrop.backdrops.LayerBackdrop",
    "com.kyant.backdrop.drawBackdrop",
    "com.kyant.backdrop.effects.blur",
    "androidx.compose.ui.graphics.RectangleShape",
])
old = """    onDismiss: () -> Unit,
) {
    if (!visible) return"""
new = """    onDismiss: () -> Unit,
    backdrop: LayerBackdrop? = null,
) {
    if (!visible) return"""
assert old in s, "lp sig"
s = s.replace(old, new, 1)

old = """    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.Transparent,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
        ) {
            // ---- 双 Tab ----"""
new = """    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = if (backdrop != null) {
            Color.Transparent
        } else {
            MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
        },
    ) {
        Box(Modifier.fillMaxWidth()) {
            // 背景模糊：采样其后的地图
            if (backdrop != null) {
                Box(
                    Modifier
                        .matchParentSize()
                        .drawBackdrop(
                            backdrop = backdrop,
                            shape = { RectangleShape },
                            effects = { blur(24.dp.toPx()) },
                        ),
                )
            }
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
        ) {
            // ---- 双 Tab ----"""
assert old in s, "lp sheet"
s = s.replace(old, new, 1)
# 闭合 Box：在 ModalBottomSheet content 的最后一个 } 前——找函数尾部结构：
# LayersPanel 函数以 ModalBottomSheet(...) { Column { ... } } 结束；在 Column 闭合后补 Box 闭合
# 定位：函数体内最后一个 "    )\n}" 模式（ModalBottomSheet 的闭合）
old = """    )
}"""
# 可能有多处；只替换最后一次出现（函数尾）
idx = s.rfind(old)
assert idx > 0, "lp close"
s = s[:idx] + "        }\n    )\n}" + s[idx + len(old):]
save(p, s)
print("OK LayersPanel")

# ========== 4) StatsPanel：同 ==========
p = "ui/stats/StatsPanel.kt"
s = load(p)
s = ensure_imports(s, [
    "com.kyant.backdrop.backdrops.LayerBackdrop",
    "com.kyant.backdrop.drawBackdrop",
    "com.kyant.backdrop.effects.blur",
    "androidx.compose.ui.graphics.RectangleShape",
    "androidx.compose.foundation.layout.Box",
])
old = """    onDismiss: () -> Unit,
) {
    if (!visible) return"""
new = """    onDismiss: () -> Unit,
    backdrop: LayerBackdrop? = null,
) {
    if (!visible) return"""
assert old in s, "sp sig"
s = s.replace(old, new, 1)

old = """    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.Transparent,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("收集进度", style = MaterialTheme.typography.headlineSmall)"""
new = """    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = if (backdrop != null) {
            Color.Transparent
        } else {
            MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
        },
    ) {
        Box(Modifier.fillMaxWidth()) {
            // 背景模糊：采样其后的地图
            if (backdrop != null) {
                Box(
                    Modifier
                        .matchParentSize()
                        .drawBackdrop(
                            backdrop = backdrop,
                            shape = { RectangleShape },
                            effects = { blur(24.dp.toPx()) },
                        ),
                )
            }
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("收集进度", style = MaterialTheme.typography.headlineSmall)"""
assert old in s, "sp sheet"
s = s.replace(old, new, 1)
idx = s.rfind("    )\n}")
assert idx > 0, "sp close"
s = s[:idx] + "        }\n    )\n}" + s[idx + len("    )\n}"):]
save(p, s)
print("OK StatsPanel")

# ========== 5) DexScreen：TabRow 透明 ==========
p = "ui/dex/DexScreen.kt"
s = load(p)
old = "            TabRow(selectedTabIndex = tab) {"
new = "            TabRow(selectedTabIndex = tab, containerColor = Color.Transparent) {"
assert old in s, "dex tab"
s = s.replace(old, new, 1)
save(p, s)
print("OK Dex")

# ========== 6) QuestsScreen：进度条轨道半透明 + 卡片更透 ==========
p = "ui/quests/QuestsScreen.kt"
s = load(p)
old = """                LinearProgressIndicator(
                    progress = {
                        if (filtered.isEmpty()) 0f else doneCount.toFloat() / filtered.size
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp),
                )"""
new = """                LinearProgressIndicator(
                    progress = {
                        if (filtered.isEmpty()) 0f else doneCount.toFloat() / filtered.size
                    },
                    trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp),
                )"""
assert old in s, "q progress"
s = s.replace(old, new, 1)
s = s.replace("}).copy(alpha = 0.78f),", "}).copy(alpha = 0.62f),", 1)
save(p, s)
print("OK Quests")

print("ALL DONE")
