# -*- coding: utf-8 -*-
"""整页模糊：全屏模糊层 + 底栏感知 + 面板浅色字体"""
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

# ========== 1) MapRepository：panelVisible ==========
p = "data/repository/MapRepository.kt"
s = load(p)
old = """    private val _mapFocusRequest = MutableStateFlow<Pair<Double, Double>?>(null)"""
new = """    private val _mapFocusRequest = MutableStateFlow<Pair<Double, Double>?>(null)

    /** 进度/图层面板是否打开（底栏感知：面板打开时底栏同步加强模糊） */
    private val _panelVisible = MutableStateFlow(false)
    val panelVisible: Flow<Boolean> = _panelVisible

    fun setPanelVisible(visible: Boolean) {
        _panelVisible.value = visible
    }"""
assert old in s, "repo"
s = s.replace(old, new, 1)
save(p, s)
print("OK Repository")

# ========== 2) MapScreen：全屏模糊层 + 同步 panelVisible ==========
p = "ui/map/MapScreen.kt"
s = load(p)
s = ensure_imports(s, [
    "androidx.compose.runtime.DisposableEffect",
    "androidx.compose.ui.graphics.RectangleShape",
    "com.kyant.backdrop.drawBackdrop",
    "com.kyant.backdrop.effects.blur",
    "org.koin.compose.koinInject",
    "com.botwmap.app.data.repository.MapRepository",
])

# repository 注入（MapScreen 函数参数）——找 fun MapScreen( 的参数表尾
import re
m = re.search(r"fun MapScreen\(([^)]*)\)", s)
assert m, "map fun"
if "repository" not in m.group(1):
    params = m.group(1).rstrip()
    if not params.endswith(","):
        params += ","
    params += "\n    repository: MapRepository = koinInject(),"
    s = s[:m.start(1)] + params + s[m.end(1):]

# panelVisible 同步：在 Box(Modifier.fillMaxSize()) 根 Box 内开头
old = """    Box(Modifier.fillMaxSize()) {
        // 地图自建采样层：面板在采样区外做背景模糊（模糊其后的地图）"""
new = """    Box(Modifier.fillMaxSize()) {
        // 面板可见状态同步给底栏（面板打开时底栏加强模糊融入整页）
        val panelOn = state.layersPanelVisible || state.statsVisible
        LaunchedEffect(panelOn) { repository.setPanelVisible(panelOn) }
        DisposableEffect(Unit) {
            onDispose { repository.setPanelVisible(false) }
        }

        // 地图自建采样层：面板在采样区外做背景模糊（模糊其后的地图）"""
assert old in s, "map sync"
s = s.replace(old, new, 1)

# 全屏模糊层：插在采样区闭合之后、面板之前
old = """        }
        // ---- 图层 / 统计面板（采样区外：drawBackdrop 模糊地图）----
        LayersPanel("""
new = """        }

        // ---- 面板打开：全屏背景模糊（模糊地图+按钮，sheet 浮于其上）----
        if (panelOn && mapBackdrop != null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .drawBackdrop(
                        backdrop = mapBackdrop,
                        shape = { RectangleShape },
                        effects = { blur(28.dp.toPx()) },
                    ),
            )
        }

        // ---- 图层 / 统计面板（清晰浮于全屏模糊层之上）----
        LayersPanel("""
assert old in s, "map fullscreen blur"
s = s.replace(old, new, 1)
save(p, s)
print("OK MapScreen")

# ========== 3) AppNavHost：底栏面板感知 ==========
p = "ui/navigation/AppNavHost.kt"
s = load(p)
old = """    val barBlur by repository.customBarBlur.collectAsStateWithLifecycle(initialValue = 8f)"""
new = """    val barBlur by repository.customBarBlur.collectAsStateWithLifecycle(initialValue = 8f)
    val panelVisible by repository.panelVisible.collectAsStateWithLifecycle(initialValue = false)"""
assert old in s, "nav state"
s = s.replace(old, new, 1)
old = """                    blurRadius = barBlur.dp,
                    containerAlpha = barAlpha,
                )"""
new = """                    // 面板打开时底栏加强模糊+近不透明，融入整页模糊
                    blurRadius = if (panelVisible) 28.dp else barBlur.dp,
                    containerAlpha = if (panelVisible) 0.95f else barAlpha,
                )"""
assert old in s, "nav bar"
s = s.replace(old, new, 1)
save(p, s)
print("OK AppNavHost")

# ========== 4) LayersPanel：删内部模糊 + 浅色字体 + Tab/chips 浅色 ==========
p = "ui/layers/LayersPanel.kt"
s = load(p)
s = ensure_imports(s, [
    "androidx.compose.runtime.CompositionLocalProvider",
    "androidx.compose.material3.LocalContentColor",
])
old = """        Box(Modifier.fillMaxWidth()) {
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
        Column("""
new = """        CompositionLocalProvider(LocalContentColor provides Color(0xFFF2F2F2)) {
        Column("""
assert old in s, "lp blur box"
s = s.replace(old, new, 1)
# Tab 文字浅色
old = """                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("探索") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("材料") })"""
new = """                Tab(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    text = {
                        Text(
                            "探索",
                            color = if (tab == 0) Color.White else Color.White.copy(alpha = 0.6f),
                        )
                    },
                )
                Tab(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    text = {
                        Text(
                            "材料",
                            color = if (tab == 1) Color.White else Color.White.copy(alpha = 0.6f),
                        )
                    },
                )"""
assert old in s, "lp tabs"
s = s.replace(old, new, 1)
# chips label 浅色（在已有的透明 colors 基础上补 labelColor）
s = s.replace(
    "colors = FilterChipDefaults.filterChipColors(\n                            containerColor = Color.Transparent,\n                        ),",
    "colors = FilterChipDefaults.filterChipColors(\n"
    "                            containerColor = Color.Transparent,\n"
    "                            labelColor = Color(0xFFE8E8E8),\n"
    "                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,\n"
    "                        ),",
)
save(p, s)
print("OK LayersPanel")

# ========== 5) StatsPanel：删内部模糊 + 浅色字体 ==========
p = "ui/stats/StatsPanel.kt"
s = load(p)
s = ensure_imports(s, [
    "androidx.compose.runtime.CompositionLocalProvider",
    "androidx.compose.material3.LocalContentColor",
])
old = """        Box(Modifier.fillMaxWidth()) {
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
        Column("""
new = """        CompositionLocalProvider(LocalContentColor provides Color(0xFFF2F2F2)) {
        Column("""
assert old in s, "sp blur box"
s = s.replace(old, new, 1)
save(p, s)
print("OK StatsPanel")

print("ALL DONE")
