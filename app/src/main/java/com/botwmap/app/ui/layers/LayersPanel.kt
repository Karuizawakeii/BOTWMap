package com.botwmap.app.ui.layers

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.Image
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.botwmap.app.data.model.LayerGroup
import com.botwmap.app.data.model.MarkerCategory
import com.botwmap.app.data.model.MaterialItem
import com.botwmap.app.data.local.IconRepository
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.TabRowDefaults
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.LocalContentColor
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.botwmap.app.ui.map.MapEvent
import com.botwmap.app.ui.map.MapViewModel
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.statusBars
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.graphics.RectangleShape
import com.botwmap.app.ui.navigation.GlobalBackground
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.foundation.shape.RoundedCornerShape
import com.botwmap.app.ui.effect.liquid.glassSurfaceColor
import com.botwmap.app.ui.effect.liquid.glassBorderColor
import com.botwmap.app.ui.effect.liquid.glassSelectedColor
import com.botwmap.app.ui.effect.liquid.liquidGlassSurface

/** 材料分组顺序（对齐原项目侧栏） */
private val MATERIAL_CATS = listOf("植物", "蘑菇", "水果", "昆虫", "鱼", "矿物")

/**
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

    // 不透明背景层：fillMaxSize 全屏（NavHost 已 edge-to-edge 从屏幕顶开始，不再 offset）
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
    ) {
    GlobalBackground()

// 底部预留：仅系统手势条（WindowInsets 官方 API）+ 余量 —— 二级页无自定义底栏
    val navBottomPad = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val listBottomPad = navBottomPad + 24.dp

    Scaffold(
        containerColor = Color.Transparent,
        // 只避让状态栏：标题不被状态栏遮挡；底部不消费 inset，内容到屏幕最底
        contentWindowInsets = WindowInsets.statusBars,
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
        CompositionLocalProvider(LocalContentColor provides Color(0xFF2E2E2E)) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
                .padding(bottom = 48.dp),
        ) {
            // ---- 双 Tab ----
            TabRow(
                selectedTabIndex = tab,
                containerColor = Color.Transparent,
            ) {
                Tab(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    text = {
                        Text(
                            "探索",
                            color = if (tab == 0) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    },
                )
                Tab(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    text = {
                        Text(
                            "材料",
                            color = if (tab == 1) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    },
                )
            }

            Spacer(Modifier.height(10.dp))

            if (tab == 0) {
                // ---- 探索：按钮行 + 类目分组 ----
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(
                        onClick = onClearAll,
                        border = BorderStroke(1.dp, glassBorderColor()),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
                    ) { Text("清空") }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = onSelectAll,
                        colors = ButtonDefaults.buttonColors(containerColor = glassSelectedColor()),
                    ) { Text("全选") }
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "${enabledLayerIds.size}/${sumCats(categoriesByGroup)} 图层",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(Modifier.height(4.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = listBottomPad),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    LayerGroup.entries.forEach { group ->
                        val groupCats = categoriesByGroup[group] ?: emptyList()
                        if (groupCats.isEmpty()) return@forEach
                        val collapsed = collapsedGroups[group] ?: false
                        val allOn = groupCats.all { it.key in enabledLayerIds }

                        item(key = "header_$group") {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { collapsedGroups[group] = !collapsed }
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = group.displayZh,
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    text = "${groupCats.count { it.key in enabledLayerIds }}/${groupCats.size}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.width(6.dp))
                                TextButton(onClick = {
                                    val ids = groupCats.map { it.key }
                                    if (allOn) {
                                        ids.forEach { onToggle(it, false) }
                                    } else {
                                        ids.forEach { onToggle(it, true) }
                                    }
                                }) {
                                    Text(
                                        text = if (allOn) "清本组" else "全选本组",
                                        style = MaterialTheme.typography.labelMedium,
                                    )
                                }
                                Icon(
                                    imageVector = if (collapsed) Icons.Filled.ExpandMore
                                    else Icons.Filled.ExpandLess,
                                    contentDescription = null,
                                )
                            }
                        }

                        item(key = "chips_$group") {
                            AnimatedVisibility(
                                visible = !collapsed,
                                enter = expandVertically(),
                                exit = shrinkVertically(),
                            ) {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.padding(bottom = 8.dp),
                                ) {
                                    groupCats.forEach { cat ->
                                        FilterChip(
                                            colors = FilterChipDefaults.filterChipColors(
                                                containerColor = glassSurfaceColor(0.28f),
                                                selectedContainerColor = glassSelectedColor(),
                                                labelColor = MaterialTheme.colorScheme.onSurface,
                                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                            ),
                                            border = BorderStroke(
                                                1.dp,
                                                if (cat.key in enabledLayerIds) glassSelectedColor(0.7f)
                                                else glassBorderColor(0.30f),
                                            ),
                                            selected = cat.key in enabledLayerIds,
                                            onClick = {
                                                onToggle(cat.key, cat.key !in enabledLayerIds)
                                            },
                                            label = {
                                                // key(version) 使图标加载完成触发 label 重组
                                                // （且该读取参与 key 计算，R8 不会优化掉订阅）
                                                key(iconRepo.version.value) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                    ) {
                                                        if (cat.icon.isNotBlank()) {
                                                            val img = iconRepo.getImage(cat.icon)
                                                            if (img != null) {
                                                                Image(
                                                                    bitmap = img,
                                                                    contentDescription = null,
                                                                    modifier = Modifier.size(16.dp),
                                                                )
                                                            } else {
                                                                iconRepo.request(cat.icon)
                                                                Spacer(Modifier.size(16.dp))
                                                            }
                                                            Spacer(Modifier.width(6.dp))
                                                        }
                                                        Text(cat.cn)
                                                    }
                                                }
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // ---- 材料：搜索 + 6 类采集分组 ----
                OutlinedTextField(
                    value = matQuery,
                    onValueChange = { matQuery = it },
                    placeholder = { Text("搜索材料（如：生命、松露）") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(50),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = glassBorderColor(0.55f),
                        unfocusedBorderColor = glassBorderColor(0.30f),
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .liquidGlassSurface(
                            shape = RoundedCornerShape(50),
                            cornerRadius = 28.dp,
                        ),
                )

                val q = matQuery.text.trim()
                val filteredMats = remember(materials, q) {
                    if (q.isEmpty()) materials
                    else materials.filter {
                        it.cn.contains(q, true) || it.en.contains(q, true) || it.cat.contains(q, true)
                    }
                }

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = listBottomPad),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    // 按钮行（与探索 Tab 一致：清空 / 全选 / 计数）
                    item(key = "mat_header") {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            OutlinedButton(
                                onClick = { onSetMaterials(emptySet()) },
                                border = BorderStroke(1.dp, glassBorderColor()),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
                            ) { Text("清空") }
                            Spacer(Modifier.width(8.dp))
                            Button(
                                onClick = { onSetMaterials(materials.map { it.id }.toSet()) },
                                colors = ButtonDefaults.buttonColors(containerColor = glassSelectedColor()),
                            ) { Text("全选") }
                            Spacer(Modifier.weight(1f))
                            Text(
                                text = "${enabledMaterialIds.size}/${materials.size} 材料",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    MATERIAL_CATS.forEach { cat ->
                        val group = filteredMats.filter { it.cat == cat }
                        if (group.isEmpty()) return@forEach
                        val collapsed = collapsedMatGroups[cat] ?: false
                        val allGroupOn = group.all { it.id.toString() in enabledMaterialIds }
                        val totalPts = group.sumOf { it.count }

                        item(key = "mat_$cat") {
                            Column {
                                // 组头（与探索 Tab 同风格：大字组名 + 计数 + 操作 + 右侧箭头）
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable { collapsedMatGroups[cat] = !collapsed }
                                        .padding(vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = cat,
                                        style = MaterialTheme.typography.titleMedium,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Text(
                                        text = "${group.size}种 / ${totalPts}点",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    TextButton(onClick = {
                                        onSetMaterials(
                                            if (allGroupOn) {
                                                enabledMaterialIds.mapNotNull { it.toInt() }.toSet() -
                                                    group.map { it.id }.toSet()
                                            } else {
                                                enabledMaterialIds.mapNotNull { it.toInt() }.toSet() +
                                                    group.map { it.id }.toSet()
                                            },
                                        )
                                    }) {
                                        Text(if (allGroupOn) "清本组" else "全选本组")
                                    }
                                    Icon(
                                        imageVector = if (collapsed) Icons.Filled.ExpandMore
                                        else Icons.Filled.ExpandLess,
                                        contentDescription = null,
                                    )
                                }

                                AnimatedVisibility(
                                    visible = !collapsed,
                                    enter = expandVertically(),
                                    exit = shrinkVertically(),
                                ) {
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.padding(bottom = 8.dp),
                                    ) {
                                        group.forEach { m ->
                                            FilterChip(
                                                colors = FilterChipDefaults.filterChipColors(
                                                    containerColor = Color.Transparent,
                                                ),
                                                selected = m.id.toString() in enabledMaterialIds,
                                                onClick = {
                                                    onToggleMaterial(
                                                        m.id,
                                                        m.id.toString() !in enabledMaterialIds,
                                                    )
                                                },
                                                label = {
                                                    key(iconRepo.version.value) {
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                        ) {
                                                            if (m.entry.isNotBlank()) {
                                                                val img = iconRepo
                                                                    .getImage("materials/${m.entry}.webp")
                                                                if (img != null) {
                                                                    Image(
                                                                        bitmap = img,
                                                                        contentDescription = null,
                                                                        modifier = Modifier.size(16.dp),
                                                                    )
                                                                } else {
                                                                    iconRepo.request("materials/${m.entry}.webp")
                                                                    Spacer(Modifier.size(16.dp))
                                                                }
                                                                Spacer(Modifier.width(6.dp))
                                                            }
                                                            Text("${m.cn} ${m.count}")
                                                        }
                                                    }
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (filteredMats.isEmpty()) {
                        item(key = "mat_empty") {
                            Text(
                                text = "无匹配「$q」",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
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

private fun sumCats(groups: Map<LayerGroup, List<MarkerCategory>>): Int =
    groups.values.sumOf { it.size }
