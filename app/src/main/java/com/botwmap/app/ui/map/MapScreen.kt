package com.botwmap.app.ui.map

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.botwmap.app.data.local.HdTileRepository
import com.botwmap.app.data.local.IconRepository
import com.botwmap.app.ui.detail.PointDetailCard
import org.koin.compose.viewmodel.koinViewModel
import org.koin.compose.koinInject
import com.botwmap.app.data.repository.MapRepository
import com.botwmap.app.ui.navigation.liquidGlass
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.CompositionLocalProvider
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.botwmap.app.ui.effect.rememberBlurEffectsEnabled
import com.botwmap.app.ui.navigation.LocalLiquidBackdrop
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import com.botwmap.app.ui.effect.liquid.LiquidBottomBarTotalHeight
import com.botwmap.app.ui.effect.liquid.LiquidGlassIconButton
import com.botwmap.app.ui.effect.liquid.LiquidGlassPanel
import com.botwmap.app.ui.effect.liquid.liquidGlassShadow

/**
 * 地图主屏：瓦片地图 + 搜索 + 图层 + 进度统计 + 详情卡片 + 自定义标记。
 * 严格 UDF：只渲染 [MapUiState]，交互全部转为 [MapEvent]。
 */
@Composable
fun MapScreen(
    viewModel: MapViewModel = koinViewModel(),
    repository: MapRepository = koinInject(),
    onOpenLayers: () -> Unit = {},
    onOpenStats: () -> Unit = {},
    tileRepo: HdTileRepository = org.koin.compose.koinInject(),
    iconRepo: IconRepository = org.koin.compose.koinInject(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val camera = remember { MapCameraState() }

    // 地图独立采样层：按钮玻璃采样「地图内容」，按钮自身在采样区外，
    // 避免按钮 drawBackdrop 引用包含自身的全局层导致渲染树循环（hwui 栈溢出崩溃）
    val mapBackdrop = if (rememberBlurEffectsEnabled()) rememberLayerBackdrop() else null
    CompositionLocalProvider(LocalLiquidBackdrop provides mapBackdrop) {

    // 搜索结果跳转：仅 focusTarget 变化时聚焦（手动点标记不移动视图）
    LaunchedEffect(state.focusTarget) {
        state.focusTarget?.let { (px, py) ->
            camera.focusOn(px, py)
            viewModel.onEvent(MapEvent.FocusHandled)
        }
    }

    // Edge-to-Edge：root 全屏 + 不透明背景（过渡期间不透出下层背景图）
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF121518)),
    ) {
        // 采样层只含地图：按钮在层外采样它（含自身会渲染树循环 → hwui 栈溢出）
        Box(
            Modifier
                .fillMaxSize()
                .then(if (mapBackdrop != null) Modifier.layerBackdrop(mapBackdrop) else Modifier),
        ) {
        MapCanvas(
            points = state.visiblePoints,
            customMarkers = state.customMarkers,
            completedIds = state.completedIds,
            selectedId = state.selectedPoint?.id,
            selectedCustomId = state.selectedCustom?.id,
            regions = state.regions,
            showRegions = state.showRegions,
            materialPoints = state.materialPoints,
            dragonPaths = state.dragonPaths,
            korokPath = state.korokPath,
            showPaths = state.showPaths,
            camera = camera,
            tileRepo = tileRepo,
            iconRepo = iconRepo,
            onTapPoint = { viewModel.onEvent(MapEvent.SelectPoint(it)) },
            onTapCustom = { viewModel.onEvent(MapEvent.SelectCustom(it)) },
            onTapEmpty = {
                viewModel.onEvent(MapEvent.SelectPoint(null))
                viewModel.onEvent(MapEvent.SelectCustom(null))
                viewModel.onEvent(MapEvent.ClearMaterialDetail)
            },
            onLongPress = { px, py -> viewModel.onEvent(MapEvent.MapLongPress(px, py)) },
            onTapMaterial = { matId, px, py ->
                viewModel.onEvent(MapEvent.TapMaterialPoint(matId, px, py))
            },
        )
        } // 采样层闭：只含地图；搜索钮/功能钮在层外

        // ---- 顶部搜索 ----
        SearchBar(
            visible = state.searchVisible,
            query = state.searchQuery,
            results = state.searchResults,
            categoriesByKey = state.categoriesByKey,
            onQuery = { viewModel.onEvent(MapEvent.SearchQuery(it)) },
            onClose = { viewModel.onEvent(MapEvent.CloseSearch) },
            onResultClick = { point ->
                viewModel.onEvent(MapEvent.SelectPoint(point))
                viewModel.onEvent(MapEvent.FocusAt(point.px, point.py))
                viewModel.onEvent(MapEvent.CloseSearch)
            },
            modifier = Modifier.align(Alignment.TopCenter),
        )

        if (!state.searchVisible) {
            IconButton(
                onClick = { viewModel.onEvent(MapEvent.OpenSearch) },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 12.dp, top = 44.dp),
            ) {
                Box(
                    Modifier
                        .liquidGlass(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(11.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = "搜索",
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }

        // ---- 材料图层提示条 ----
        if (state.enabledMaterialIds.isNotEmpty()) {
            LiquidGlassPanel(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 104.dp),
                shape = RoundedCornerShape(20.dp),
                cornerRadius = 20.dp,
            ) {
                Row(
                    Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "材料图层 ${state.enabledMaterialIds.size} 种 · ${state.materialPoints.size} 刷点 · 图层面板可管理",
                        style = MaterialTheme.typography.labelLarge.liquidGlassShadow(),
                    )
                }
            }
        }

        // ---- 右下功能按钮 ----
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                // 避开悬浮底栏（56dp 胶囊 + 间距 + 导航条）
                .padding(start = 20.dp, end = 20.dp, bottom = 170.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LiquidGlassIconButton(
                onClick = onOpenStats,
                backdrop = mapBackdrop,
                size = 56.dp,
            ) {
                Icon(Icons.Filled.BarChart, contentDescription = "收集进度")
            }
            LiquidGlassIconButton(
                onClick = onOpenLayers,
                backdrop = mapBackdrop,
                size = 56.dp,
            ) {
                Icon(Icons.Filled.Layers, contentDescription = "图层")
            }
        }

        // ---- 底部详情卡片 ----
        val selected = state.selectedPoint
        PointDetailCard(
            point = selected,
            custom = state.selectedCustom,
            category = selected?.let { state.categoriesByKey[it.cat] },
            korok = state.selectedKorok,
            material = state.materialDetail,
            completed = selected != null && selected.id in state.completedIds,
            onToggleCompleted = { viewModel.onEvent(MapEvent.ToggleCompleted(it)) },
            onDeleteCustom = { viewModel.onEvent(MapEvent.DeleteCustomMarker(it)) },
            onDismiss = {
                viewModel.onEvent(MapEvent.SelectPoint(null))
                viewModel.onEvent(MapEvent.SelectCustom(null))
                viewModel.onEvent(MapEvent.ClearMaterialDetail)
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                // 避让悬浮底栏 + 系统手势条（insets 计算，弹窗底部按钮不被底栏遮挡）
                .padding(
                    bottom = LiquidBottomBarTotalHeight +
                        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
                ),
        )

        // ---- 加载中 ----
        if (state.loading) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text("正在加载地图数据…", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        // ---- 长按添加自定义标记 ----
        if (state.pendingPin != null) {
            AddPinDialog(
                onConfirm = { title, note ->
                    viewModel.onEvent(MapEvent.AddCustomMarker(title, note))
                },
                onDismiss = { viewModel.onEvent(MapEvent.DismissPendingPin) },
            )
        }

    }
    }
}

/** 顶部搜索栏：展开时显示输入框 + 结果列表 */
@Composable
private fun SearchBar(
    visible: Boolean,
    query: String,
    results: List<com.botwmap.app.data.model.MapPoint>,
    categoriesByKey: Map<String, com.botwmap.app.data.model.MarkerCategory>,
    onQuery: (String) -> Unit,
    onClose: () -> Unit,
    onResultClick: (com.botwmap.app.data.model.MapPoint) -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
    ) {
        val focusRequester = remember { FocusRequester() }
        LaunchedEffect(Unit) { focusRequester.requestFocus() }

        LiquidGlassPanel(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 44.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            cornerRadius = 28.dp,
        ) {
            Column {
                Row(
                    Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "收起搜索")
                    }
                    OutlinedTextField(
                        value = query,
                        onValueChange = onQuery,
                        placeholder = { Text("搜索点位 / 区域（中英文）") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(focusRequester),
                        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                            unfocusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                            focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                            unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                        ),
                    )
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { onQuery("") }) {
                            Icon(Icons.Filled.Close, contentDescription = "清空")
                        }
                    }
                }

                if (query.isNotBlank()) {
                    if (results.isEmpty()) {
                        Text(
                            text = "无匹配结果",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                        )
                    } else {
                        LazyColumn(Modifier.heightIn(max = 320.dp)) {
                            items(results, key = { it.id }) { point ->
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable { onResultClick(point) }
                                        .padding(horizontal = 20.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            text = point.name,
                                            style = MaterialTheme.typography.bodyLarge,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            text = listOfNotNull(
                                                categoriesByKey[point.cat]?.cn,
                                                point.region.ifBlank { null },
                                                point.en.ifBlank { null },
                                            ).joinToString(" · "),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    Icon(
                                        Icons.Filled.Search,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.outline,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 长按地图后输入标题/备注，添加自定义标记 */
@Composable
private fun AddPinDialog(
    onConfirm: (title: String, note: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加自定义标记") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("标题 *") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("备注（可选）") },
                    minLines = 2,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (title.isNotBlank()) onConfirm(title.trim(), note.trim()) },
                enabled = title.isNotBlank(),
            ) { Text("添加") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
