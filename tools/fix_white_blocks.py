# -*- coding: utf-8 -*-
"""底栏去图片 + 全面透明化白块 + 地图按钮避让 + 搜索框圆角"""
import io, re

BASE = r"D:\repos\BOTWMap\app\src\main\java\com\botwmap\app"

def load(rel):
    return io.open(BASE + "\\" + rel, encoding="utf-8").read()

def save(rel, s):
    io.open(BASE + "\\" + rel, "w", encoding="utf-8").write(s)

def ensure_imports(s, imports):
    lines = s.split("\n")
    # 在最后一个 import 后插入缺失项
    last_import = max(i for i, l in enumerate(lines) if l.startswith("import "))
    missing = [im for im in imports if ("import " + im) not in s]
    if missing:
        for j, im in enumerate(missing):
            lines.insert(last_import + 1 + j, "import " + im)
    return "\n".join(lines)

# ========== 1) AppNavHost：背景图移出采样区 + 底栏不传图 + 屏幕底色采样 ==========
p = "ui/navigation/AppNavHost.kt"
s = load(p)
# 1a. 采样 backdrop 改为 rememberScreenBackdrop（纹理铺 surface 底，防透明黑洞）
old = """    val blurEnabled = rememberBlurEffectsEnabled()
    val backdrop = if (blurEnabled) rememberLayerBackdrop() else null"""
new = """    val blurEnabled = rememberBlurEffectsEnabled()
    // 采样纹理铺 surface 底色：底栏玻璃只含主题色+页面内容，不含全局背景图
    val backdrop = if (blurEnabled) {
        rememberScreenBackdrop(backgroundColor = MaterialTheme.colorScheme.surface)
    } else {
        null
    }"""
assert old in s, "backdrop"
s = s.replace(old, new, 1)
s = s.replace("import com.kyant.backdrop.backdrops.rememberLayerBackdrop",
              "import com.botwmap.app.ui.effect.rememberScreenBackdrop", 1)
if "import com.kyant.backdrop.backdrops.rememberLayerBackdrop" in s:
    s = s.replace("import com.kyant.backdrop.backdrops.rememberLayerBackdrop\n", "", 1)

# 1b. 背景图+scrim 移出采样区（放采样 Box 之前）
old = """    Box(Modifier.fillMaxSize()) {
        // ---- 采样区：背景图 + 可读性蒙层 + 页面内容（底栏在区外，避免自采样）----
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
new = """    Box(Modifier.fillMaxSize()) {
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
assert old in s, "move bg"
s = s.replace(old, new, 1)

# 1c. 底栏不传 backgroundImage
old = """                    blurRadius = barBlur.dp,
                    containerAlpha = barAlpha,
                    backgroundImage = bgBitmap,
                )"""
new = """                    blurRadius = barBlur.dp,
                    containerAlpha = barAlpha,
                )"""
assert old in s, "bar param"
s = s.replace(old, new, 1)
save(p, s)
print("OK AppNavHost")

# ========== 2) Legado：删 backgroundImage 参数与图层 ==========
p = "ui/effect/liquid/LegadoLiquidFloatingBottomBar.kt"
s = load(p)
old = """    /** 自定义背景图片（设置页选择）；null = 默认纯色容器 */
    backgroundImage: ImageBitmap? = null,
) {"""
new = """) {"""
assert old in s, "legado param"
s = s.replace(old, new, 1)
old = """    // 有玻璃采样或有图片层时容器半透明（否则不透明容器会盖掉下层图片）
    val containerColor = if (isBlurEnabled || backgroundImage != null) {"""
new = """    val containerColor = if (isBlurEnabled) {"""
assert old in s, "legado color"
s = s.replace(old, new, 1)
# 删图片层块
start = s.index("            // 图片层仅在无玻璃采样时兜底")
end_marker = """            }
            Box(
                Modifier
                    .matchParentSize()
                    .graphicsLayer { translationX = panelOffset }
                    .then(
                        if (isBlurEnabled) {"""
end = s.index(end_marker, start)
s = s[:start] + "            Box(\n" + s[end + len("            }\n            Box(\n"):]
# ImageBitmap import 若无他用则删
if s.count("ImageBitmap") == 1 and "import androidx.compose.ui.graphics.ImageBitmap" in s:
    s = s.replace("import androidx.compose.ui.graphics.ImageBitmap\n", "", 1)
if "Image(" not in s and "import androidx.compose.foundation.Image" in s:
    s = s.replace("import androidx.compose.foundation.Image\n", "", 1)
save(p, s)
print("OK Legado")

# ========== 3) QuestsScreen：TopAppBar 透明 + 搜索框胶囊 + 卡片半透明 ==========
p = "ui/quests/QuestsScreen.kt"
s = load(p)
s = ensure_imports(s, [
    "androidx.compose.material3.TopAppBarDefaults",
    "androidx.compose.material3.OutlinedTextFieldDefaults",
    "androidx.compose.foundation.shape.RoundedCornerShape",
])
old = '        topBar = { TopAppBar(title = { Text("任务") }) },'
new = """        topBar = {
            TopAppBar(
                title = { Text("任务") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                ),
            )
        },"""
assert old in s, "q topapp"
s = s.replace(old, new, 1)
old = """            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("搜索任务名 / 区域 / NPC（中英文）") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            )"""
new = """            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("搜索任务名 / 区域 / NPC（中英文）") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(50),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            )"""
assert old in s, "q search"
s = s.replace(old, new, 1)
old = """                containerColor = if (expanded) {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                } else {
                    MaterialTheme.colorScheme.surfaceContainerLow
                },"""
new = """                containerColor = (if (expanded) {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                } else {
                    MaterialTheme.colorScheme.surfaceContainerLow
                }).copy(alpha = 0.78f),"""
assert old in s, "q card"
s = s.replace(old, new, 1)
save(p, s)
print("OK Quests")

# ========== 4) DexScreen：TopAppBar 透明 + 搜索框胶囊 + 条目透明 ==========
p = "ui/dex/DexScreen.kt"
s = load(p)
s = ensure_imports(s, [
    "androidx.compose.material3.TopAppBarDefaults",
    "androidx.compose.material3.OutlinedTextFieldDefaults",
    "androidx.compose.material3.ListItemDefaults",
    "androidx.compose.foundation.shape.RoundedCornerShape",
])
old = '        topBar = { TopAppBar(title = { Text("图鉴") }) },'
new = """        topBar = {
            TopAppBar(
                title = { Text("图鉴") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                ),
            )
        },"""
assert old in s, "d topapp"
s = s.replace(old, new, 1)
old = """            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("搜索名称（中/英文）")},
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )"""
new = """            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("搜索名称（中/英文）")},
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(50),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )"""
assert old in s, "d search"
s = s.replace(old, new, 1)
# 所有 ListItem 透明
s = s.replace("ListItem(\n",
              "ListItem(\n                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),\n")
save(p, s)
print("OK Dex")

# ========== 5) SettingsScreen：TopAppBar 透明 + chips 透明 ==========
p = "ui/settings/SettingsScreen.kt"
s = load(p)
s = ensure_imports(s, [
    "androidx.compose.material3.TopAppBarDefaults",
    "androidx.compose.material3.FilterChipDefaults",
])
old = """            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {"""
new = """            TopAppBar(
                title = { Text("设置") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                ),
                navigationIcon = {"""
assert old in s, "s topapp"
s = s.replace(old, new, 1)
old = """                    FilterChip(
                        selected = themeMode == mode,
                        onClick = { scope.launch { repository.setThemeMode(mode) } },
                        label = { Text(label) },
                        modifier = Modifier.padding(end = 8.dp),
                    )"""
new = """                    FilterChip(
                        selected = themeMode == mode,
                        onClick = { scope.launch { repository.setThemeMode(mode) } },
                        label = { Text(label) },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = Color.Transparent,
                        ),
                        modifier = Modifier.padding(end = 8.dp),
                    )"""
assert old in s, "s chips"
s = s.replace(old, new, 1)
save(p, s)
print("OK Settings")

# ========== 6) LayersPanel：sheet 透明 + TabRow 透明 + chips 透明 ==========
p = "ui/layers/LayersPanel.kt"
s = load(p)
s = ensure_imports(s, [
    "androidx.compose.ui.graphics.Color",
    "androidx.compose.material3.FilterChipDefaults",
    "androidx.compose.material3.TabRowDefaults",
])
old = "        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,"
new = "        containerColor = Color.Transparent,"
assert old in s, "lp sheet"
s = s.replace(old, new, 1)
old = "            TabRow(selectedTabIndex = tab) {"
new = """            TabRow(
                selectedTabIndex = tab,
                containerColor = Color.Transparent,
            ) {"""
assert old in s, "lp tab"
s = s.replace(old, new, 1)
# FilterChip 透明（两处相同结构则全替）
old = "                                        FilterChip("
# 逐个：在 FilterChip( 后插 colors——用正则找 label 行后插太复杂，直接给 FilterChip 加 colors 参数：
s = re.sub(
    r"FilterChip\(\n(\s+)selected = ",
    lambda m: "FilterChip(\n" + m.group(1) + "colors = FilterChipDefaults.filterChipColors(\n"
              + m.group(1) + "    containerColor = Color.Transparent,\n" + m.group(1) + "),\n"
              + m.group(1) + "selected = ",
    s,
)
save(p, s)
print("OK LayersPanel")

# ========== 7) StatsPanel：sheet 透明 ==========
p = "ui/stats/StatsPanel.kt"
s = load(p)
s = ensure_imports(s, ["androidx.compose.ui.graphics.Color"])
old = "        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,"
new = "        containerColor = Color.Transparent,"
assert old in s, "sp sheet"
s = s.replace(old, new, 1)
save(p, s)
print("OK StatsPanel")

# ========== 8) MapScreen：按钮避让 + 半透明 ==========
p = "ui/map/MapScreen.kt"
s = load(p)
old = """        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp),"""
new = """        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                // 避开悬浮底栏（56dp 胶囊 + 间距 + 导航条）
                .padding(start = 20.dp, end = 20.dp, bottom = 170.dp),"""
assert old in s, "map col"
s = s.replace(old, new, 1)
old = """                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ) {
                Icon(Icons.Filled.BarChart, contentDescription = "收集进度")"""
new = """                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.88f),
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ) {
                Icon(Icons.Filled.BarChart, contentDescription = "收集进度")"""
assert old in s, "map fab1"
s = s.replace(old, new, 1)
old = """                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Icon(Icons.Filled.Layers, contentDescription = "图层")"""
new = """                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.88f),
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Icon(Icons.Filled.Layers, contentDescription = "图层")"""
assert old in s, "map fab2"
s = s.replace(old, new, 1)
old = """                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shadowElevation = 4.dp,
                ) {"""
new = """                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.75f),
                    shadowElevation = 4.dp,
                ) {"""
assert old in s, "map search btn"
s = s.replace(old, new, 1)
save(p, s)
print("OK MapScreen")

print("ALL DONE")
