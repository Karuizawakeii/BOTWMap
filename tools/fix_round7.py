# -*- coding: utf-8 -*-
"""面板改独立页面 + 移除全部模糊方案"""
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

def drop_imports(s, imports):
    for im in imports:
        s = s.replace("import " + im + "\n", "")
    return s

# ========== 1) LayersPanel -> LayersScreen ==========
p = "ui/layers/LayersPanel.kt"
s = load(p)
s = drop_imports(s, [
    "com.kyant.backdrop.backdrops.LayerBackdrop",
    "com.kyant.backdrop.drawBackdrop",
    "com.kyant.backdrop.effects.blur",
    "androidx.compose.ui.graphics.RectangleShape",
])
s = ensure_imports(s, [
    "androidx.compose.foundation.background",
    "androidx.compose.foundation.layout.fillMaxSize",
    "androidx.compose.material.icons.automirrored.filled.ArrowBack",
    "androidx.compose.material3.IconButton",
    "androidx.compose.material3.Scaffold",
    "androidx.compose.material3.TopAppBar",
    "androidx.compose.material3.TopAppBarDefaults",
    "androidx.lifecycle.compose.collectAsStateWithLifecycle",
    "com.botwmap.app.ui.map.MapEvent",
    "com.botwmap.app.ui.map.MapViewModel",
    "com.botwmap.app.ui.navigation.GlobalBackground",
    "org.koin.compose.koinInject",
    "org.koin.compose.viewmodel.koinViewModel",
])

old = """/**
 * 图层管理面板（ModalBottomSheet）：探索 / 材料双 Tab。
 * 探索 = 点位类目分组（任务/装备/宝箱/探索收集/设施地点/敌人）+ 全选本组；
 * 材料 = 6 类采集分组（N种/M点）+ 组内多选，刷点实时上图。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LayersPanel(
    visible: Boolean,
    categoriesByGroup: Map<LayerGroup, List<MarkerCategory>>,
    enabledLayerIds: Set<String>,
    materials: List<MaterialItem>,
    enabledMaterialIds: Set<String>,
    iconRepo: IconRepository,
    onToggle: (String, Boolean) -> Unit,
    onSelectAll: () -> Unit,
    onClearAll: () -> Unit,
    onToggleMaterial: (Int, Boolean) -> Unit,
    onSetMaterials: (Set<Int>) -> Unit,
    onDismiss: () -> Unit,
    backdrop: LayerBackdrop? = null,
) {
    if (!visible) return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val collapsedGroups = remember { mutableStateMapOf<LayerGroup, Boolean>() }
    var tab by remember { mutableIntStateOf(0) }
    var matQuery by remember { mutableStateOf(TextFieldValue("")) }
    val collapsedMatGroups = remember { mutableStateMapOf<String, Boolean>() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        scrimColor = Color.Black.copy(alpha = 0.45f),
        containerColor = if (backdrop != null) {
            Color.Transparent
        } else {
            MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
        },
    ) {
        CompositionLocalProvider(LocalContentColor provides Color(0xFFF2F2F2)) {
        Column(
            Modifier
                .fillMaxWidth()"""
new = """/**
 * 图层管理页（独立页面）：探索 / 材料双 Tab。
 * 探索 = 点位类目分组（任务/装备/宝箱/探索收集/设施地点/敌人）+ 全选本组；
 * 材料 = 6 类采集分组（N种/M点）+ 组内多选，刷点实时上图。
 * 不透明页面：surface 垫底 + 自定义背景图（GlobalBackground）。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LayersScreen(
    onBack: () -> Unit,
    viewModel: MapViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val iconRepo: IconRepository = koinInject()
    // 原面板参数别名（内容零改动）
    val categoriesByGroup = state.categoriesByGroup
    val enabledLayerIds = state.enabledLayerIds
    val materials = state.materials
    val enabledMaterialIds = state.enabledMaterialIds
    val onToggle: (String, Boolean) -> Unit = { id, e ->
        viewModel.onEvent(MapEvent.ToggleLayer(id, e))
    }
    val onSelectAll: () -> Unit = { viewModel.onEvent(MapEvent.SelectAllLayers) }
    val onClearAll: () -> Unit = { viewModel.onEvent(MapEvent.ClearAllLayers) }
    val onToggleMaterial: (Int, Boolean) -> Unit = { id, e ->
        viewModel.onEvent(MapEvent.ToggleMaterial(id, e))
    }
    val onSetMaterials: (Set<Int>) -> Unit = { ids ->
        viewModel.onEvent(MapEvent.SetAllMaterials(ids))
    }

    val collapsedGroups = remember { mutableStateMapOf<LayerGroup, Boolean>() }
    var tab by remember { mutableIntStateOf(0) }
    var matQuery by remember { mutableStateOf(TextFieldValue("")) }
    val collapsedMatGroups = remember { mutableStateMapOf<String, Boolean>() }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("图层") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.surface),
        ) {
            GlobalBackground()
            CompositionLocalProvider(LocalContentColor provides Color(0xFFF2F2F2)) {
        Column(
            Modifier
                .fillMaxSize()"""
assert old in s, "lp head"
s = s.replace(old, new, 1)

# 尾部：ModalBottomSheet lambda 闭 -> Box + Scaffold content 闭
old = """        }
        }
    }
}

private fun sumCats"""
new = """        }
        }
    }
    }
}

private fun sumCats"""
assert old in s, "lp tail"
s = s.replace(old, new, 1)

# 两处 LazyColumn 加 weight（页面内填满剩余高度）
s = s.replace(
    "LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {",
    "LazyColumn(\n                    modifier = Modifier.weight(1f),\n"
    "                    verticalArrangement = Arrangement.spacedBy(4.dp),\n                ) {",
)
save(p, s)
print("OK LayersScreen")

# ========== 2) StatsPanel -> StatsScreen ==========
p = "ui/stats/StatsPanel.kt"
s = load(p)
s = drop_imports(s, [
    "com.kyant.backdrop.backdrops.LayerBackdrop",
    "com.kyant.backdrop.drawBackdrop",
    "com.kyant.backdrop.effects.blur",
    "androidx.compose.ui.graphics.RectangleShape",
])
s = ensure_imports(s, [
    "androidx.compose.foundation.background",
    "androidx.compose.foundation.layout.fillMaxSize",
    "androidx.compose.material.icons.automirrored.filled.ArrowBack",
    "androidx.compose.material3.IconButton",
    "androidx.compose.material3.Scaffold",
    "androidx.compose.material3.TopAppBar",
    "androidx.compose.material3.TopAppBarDefaults",
    "androidx.lifecycle.compose.collectAsStateWithLifecycle",
    "com.botwmap.app.ui.map.MapViewModel",
    "com.botwmap.app.ui.navigation.GlobalBackground",
    "org.koin.compose.viewmodel.koinViewModel",
])

old = """/**
 * 收集进度统计面板：总完成度 + 按分组/类目完成度。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsPanel(
    visible: Boolean,
    categories: List<MarkerCategory>,
    progress: Map<String, Pair<Int, Int>>,
    total: Pair<Int, Int>,
    onDismiss: () -> Unit,
    backdrop: LayerBackdrop? = null,
) {
    if (!visible) return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        scrimColor = Color.Black.copy(alpha = 0.45f),
        containerColor = if (backdrop != null) {
            Color.Transparent
        } else {
            MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
        },
    ) {
        CompositionLocalProvider(LocalContentColor provides Color(0xFFF2F2F2)) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("收集进度", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "关闭")
                }
            }

            Spacer(Modifier.height(8.dp))

            // 总完成度"""
new = """/**
 * 收集进度页（独立页面）：总完成度 + 按分组/类目完成度。
 * 不透明页面：surface 垫底 + 自定义背景图（GlobalBackground）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    onBack: () -> Unit,
    viewModel: MapViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val categories = state.categories
    val progress = state.progress
    val total = state.totalProgress

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("收集进度") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.surface),
        ) {
            GlobalBackground()
            CompositionLocalProvider(LocalContentColor provides Color(0xFFF2F2F2)) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
        ) {
            Spacer(Modifier.height(8.dp))

            // 总完成度"""
assert old in s, "sp head"
s = s.replace(old, new, 1)

# 尾部闭合
old = """        }
        }
    }
}"""
new = """        }
        }
    }
    }
}"""
assert old in s, "sp tail"
s = s.replace(old, new, 1)

# LazyColumn 加 weight
old = "            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {"
new = """            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {"""
assert old in s, "sp lazy"
s = s.replace(old, new, 1)
save(p, s)
print("OK StatsScreen")

# ========== 3) MapScreen：去模糊、按钮导航、删面板调用 ==========
p = "ui/map/MapScreen.kt"
s = load(p)

# 签名加回调
old = """    repository: MapRepository = koinInject(),"""
new = """    repository: MapRepository = koinInject(),
    onOpenLayers: () -> Unit = {},
    onOpenStats: () -> Unit = {},"""
assert old in s, "map sig"
s = s.replace(old, new, 1)

# 删 panelOn 同步块
old = """        // 面板可见状态同步给底栏（面板打开时底栏加强模糊融入整页）
        val panelOn = state.layersPanelVisible || state.statsVisible
        LaunchedEffect(panelOn) { repository.setPanelVisible(panelOn) }
        DisposableEffect(Unit) {
            onDispose { repository.setPanelVisible(false) }
        }

        // 地图自建采样层：面板在采样区外做背景模糊（模糊其后的地图）
        val mapBackdrop = if (rememberBlurEffectsEnabled()) rememberLayerBackdrop() else null
        Box(
            Modifier
                .fillMaxSize()
                .then(
                    if (mapBackdrop != null) Modifier.layerBackdrop(mapBackdrop) else Modifier,
                ),
        ) {
        // 背景图进采样区：面板打开时全屏模糊覆盖背景图+地图（而非只模糊地图）
        GlobalBackground()
        MapCanvas("""
new = """        MapCanvas("""
assert old in s, "map remove backdrop"
s = s.replace(old, new, 1)

# 删内层闭 + 全屏模糊层
old = """        }

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
        LayersPanel(
            backdrop = mapBackdrop,
            visible = state.layersPanelVisible,
            categoriesByGroup = state.categoriesByGroup,
            enabledLayerIds = state.enabledLayerIds,
            materials = state.materials,
            enabledMaterialIds = state.enabledMaterialIds,
            iconRepo = iconRepo,
            onToggle = { id, enabled -> viewModel.onEvent(MapEvent.ToggleLayer(id, enabled)) },
            onSelectAll = { viewModel.onEvent(MapEvent.SelectAllLayers) },
            onClearAll = { viewModel.onEvent(MapEvent.ClearAllLayers) },
            onToggleMaterial = { id, enabled ->
                viewModel.onEvent(MapEvent.ToggleMaterial(id, enabled))
            },
            onSetMaterials = { ids -> viewModel.onEvent(MapEvent.SetAllMaterials(ids)) },
            onDismiss = { viewModel.onEvent(MapEvent.CloseLayersPanel) },
        )

        StatsPanel(
            backdrop = mapBackdrop,
            visible = state.statsVisible,
            categories = state.categories,
            progress = state.progress,
            total = state.totalProgress,
            onDismiss = { viewModel.onEvent(MapEvent.CloseStats) },
        )
    }
}"""
new = """    }
}"""
assert old in s, "map remove panels"
s = s.replace(old, new, 1)

# 按钮改导航回调
old = "                onClick = { viewModel.onEvent(MapEvent.OpenStats) },"
new = "                onClick = onOpenStats,"
assert old in s, "map stats btn"
s = s.replace(old, new, 1)
old = "                onClick = { viewModel.onEvent(MapEvent.OpenLayersPanel) },"
new = "                onClick = onOpenLayers,"
assert old in s, "map layers btn"
s = s.replace(old, new, 1)

# 清理 imports
s = drop_imports(s, [
    "androidx.compose.runtime.DisposableEffect",
    "androidx.compose.ui.graphics.RectangleShape",
    "com.kyant.backdrop.drawBackdrop",
    "com.kyant.backdrop.effects.blur",
    "com.kyant.backdrop.backdrops.LayerBackdrop",
    "com.kyant.backdrop.backdrops.rememberLayerBackdrop",
    "com.kyant.backdrop.backdrops.layerBackdrop",
    "com.botwmap.app.ui.effect.rememberBlurEffectsEnabled",
    "com.botwmap.app.ui.navigation.GlobalBackground",
])
save(p, s)
print("OK MapScreen")

# ========== 4) AppNavHost：路由 + 删 panelVisible ==========
p = "ui/navigation/AppNavHost.kt"
s = load(p)

# 路由对象（找 MapRoute 定义处）
if "object LayersRoute" not in s:
    anchor = "@Serializable\ndata object MapRoute"
    if anchor in s:
        s = s.replace(anchor, anchor + "\n\n@Serializable\ndata object LayersRoute\n\n@Serializable\ndata object StatsRoute", 1)
    else:
        # 找任意 @Serializable data object
        import re
        m = re.search(r"@Serializable\ndata object \w+", s)
        assert m, "route anchor"
        block = m.group(0)
        s = s.replace(block, block + "\n\n@Serializable\ndata object LayersRoute\n\n@Serializable\ndata object StatsRoute", 1)

# MapScreen 调用加回调
old = """        composable<MapRoute> {
            MapScreen(
                onOpenSettings = { navController.navigate(SettingsRoute) },
            )
        }"""
new = """        composable<MapRoute> {
            MapScreen(
                onOpenSettings = { navController.navigate(SettingsRoute) },
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
        }"""
assert old in s, "nav routes"
s = s.replace(old, new, 1)

# 删 panelVisible state
old = """    val panelVisible by repository.panelVisible.collectAsStateWithLifecycle(initialValue = false)
"""
assert old in s, "nav panel state"
s = s.replace(old, "", 1)

# 底栏恢复常参数
old = """                    // 面板打开时底栏加强模糊+近不透明，融入整页模糊
                    blurRadius = if (panelVisible) 28.dp else barBlur.dp,
                    containerAlpha = if (panelVisible) 0.5f else barAlpha,
                )"""
new = """                    blurRadius = barBlur.dp,
                    containerAlpha = barAlpha,
                )"""
assert old in s, "nav bar params"
s = s.replace(old, new, 1)

# 底栏整条模糊删
old = """                    .padding(vertical = 14.dp)
                    // 面板打开：底栏整条模糊融入整页
                    .then(if (panelVisible) Modifier.blur(18.dp) else Modifier),"""
new = """                    .padding(vertical = 14.dp),"""
assert old in s, "nav bar blur"
s = s.replace(old, new, 1)
save(p, s)
print("OK AppNavHost")

# ========== 5) Repository 删 panelVisible ==========
p = "data/repository/MapRepository.kt"
s = load(p)
old = """

    /** 进度/图层面板是否打开（底栏感知：面板打开时底栏同步加强模糊） */
    private val _panelVisible = MutableStateFlow(false)
    val panelVisible: Flow<Boolean> = _panelVisible

    fun setPanelVisible(visible: Boolean) {
        _panelVisible.value = visible
    }"""
assert old in s, "repo panel"
s = s.replace(old, "", 1)
save(p, s)
print("OK Repository")

print("ALL DONE")
