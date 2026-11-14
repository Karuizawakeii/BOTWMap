package com.botwmap.app.ui.dex

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.botwmap.app.data.repository.MapRepository
import org.koin.compose.koinInject
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ListItemDefaults
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Box
import com.botwmap.app.ui.navigation.GlobalBackground
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import com.botwmap.app.ui.effect.liquid.glassSurfaceColor
import com.botwmap.app.ui.effect.liquid.glassBorderColor
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import com.botwmap.app.ui.effect.liquid.LiquidGlassPanel
import com.botwmap.app.ui.effect.liquid.liquidGlassSurface

/**
 * 图鉴页：材料 / 武器 / 防具三个 Tab，支持中英文搜索。
 * 材料条目可跳地图查看刷点（onShowMaterialOnMap 传材料 id）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DexScreen(
    onShowMaterialOnMap: (Int) -> Unit = {},
    repository: MapRepository = koinInject(),
) {
    val static by repository.static.collectAsStateWithLifecycle()
    var tab by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf(TextFieldValue("")) }
    val tabs = listOf("材料", "武器", "防具")

// 不透明背景层：转场中不透出旧页（残留），与全局背景同 Crop 对齐
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
    ) {
    GlobalBackground()

        Scaffold(
        // 只避让状态栏：手势导航条区透明，内容延伸到屏幕最底
        contentWindowInsets = WindowInsets.statusBars,
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("图鉴") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                ),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            TabRow(selectedTabIndex = tab, containerColor = Color.Transparent) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = tab == index,
                        onClick = { tab = index },
                        text = { Text(title) },
                    )
                }
            }

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("搜索名称（中/英文）")},
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
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .liquidGlassSurface(
                        shape = RoundedCornerShape(50),
                        cornerRadius = 28.dp,
                    ),
            )

            val q = query.text.trim()
            when (tab) {
                0 -> MaterialList(
                    materials = static?.materials ?: emptyList(),
                    q = q,
                    onShowMap = onShowMaterialOnMap,
                )
                1 -> WeaponList(static?.weapons ?: emptyList(), q)
                2 -> ArmorList(static?.armors ?: emptyList(), q)
            }
        }
    }
    }
}

/** 分组头（与图层面板探索 Tab 同风格：大字组名 + 计数 + 右侧箭头，点击展开/收起） */
@Composable
private fun DexGroupHeader(
    title: String,
    count: Int,
    collapsed: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "$count",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(6.dp))
        Icon(
            imageVector = if (collapsed) Icons.Filled.ExpandMore else Icons.Filled.ExpandLess,
            contentDescription = null,
        )
    }
}

/** 图鉴行卡片：与任务页任务卡片同款（玻璃半透明 + 细描边 + 同间距） */
@Composable
private fun DexRowCard(content: @Composable RowScope.() -> Unit) {
    LiquidGlassPanel(
        shape = RoundedCornerShape(16.dp),
        cornerRadius = 16.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

/** 行内主/副文本（与任务卡片的 Column 布局一致） */
@Composable
private fun RowScope.DexRowTexts(title: String, subtitle: String) {
    Column(Modifier.weight(1f)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 材料分组顺序（与图层面板一致） */
private val MAT_ORDER = listOf("植物", "蘑菇", "水果", "昆虫", "鱼", "矿物")
private val WEAPON_ORDER = listOf("单手剑", "双手剑", "长枪", "弓", "盾")
private val ARMOR_ORDER = listOf("头", "衣", "裤")

@Composable
private fun MaterialList(
    materials: List<com.botwmap.app.data.model.MaterialItem>,
    q: String,
    onShowMap: (Int) -> Unit,
) {
    val filtered = remember(materials, q) {
        if (q.isEmpty()) materials
        else materials.filter {
            it.cn.contains(q, true) || it.en.contains(q, true) || it.cat.contains(q, true)
        }
    }
    val collapsed = remember { mutableStateMapOf<String, Boolean>() }
    LazyColumn(contentPadding = PaddingValues(bottom = 160.dp)) {
        MAT_ORDER.forEach { cat ->
            val group = filtered.filter { it.cat == cat }
            if (group.isEmpty()) return@forEach
            val isCollapsed = collapsed[cat] ?: false
            item(key = "mat_h_$cat") {
                DexGroupHeader(
                    title = cat,
                    count = group.size,
                    collapsed = isCollapsed,
                    onToggle = { collapsed[cat] = !isCollapsed },
                )
            }
            if (isCollapsed) return@forEach
            items(group, key = { "mat_${it.id}" }) { m ->
                DexRowCard {
                    com.botwmap.app.ui.components.AssetThumb(
                        path = "materials/${m.entry}.webp",
                        size = 44.dp,
                        contentDescription = m.cn,
                    )
                    DexRowTexts(
                        title = m.cn,
                        subtitle = buildString {
                            if (m.en.isNotBlank()) append(m.en).append(" · ")
                            append("刷点 ").append(m.count)
                        },
                    )
                    if (m.count > 0) {
                        IconButton(onClick = { onShowMap(m.id) }) {
                            Icon(
                                Icons.Filled.Map,
                                contentDescription = "在地图查看刷点",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        }
        if (filtered.isEmpty()) EmptyHint(q)
    }
}

@Composable
private fun WeaponList(
    weapons: List<com.botwmap.app.data.model.WeaponItem>,
    q: String,
) {
    val filtered = remember(weapons, q) {
        if (q.isEmpty()) weapons
        else weapons.filter {
            it.name.contains(q, true) || it.en.contains(q, true) || it.type.contains(q, true)
        }
    }
    val collapsed = remember { mutableStateMapOf<String, Boolean>() }
    LazyColumn(contentPadding = PaddingValues(bottom = 160.dp)) {
        WEAPON_ORDER.forEach { type ->
            val group = filtered.filter { it.type == type }
            if (group.isEmpty()) return@forEach
            val isCollapsed = collapsed[type] ?: false
            item(key = "w_h_$type") {
                DexGroupHeader(
                    title = type,
                    count = group.size,
                    collapsed = isCollapsed,
                    onToggle = { collapsed[type] = !isCollapsed },
                )
            }
            if (isCollapsed) return@forEach
            items(group, key = { it.id }) { w ->
                DexRowCard {
                    com.botwmap.app.ui.components.AssetThumb(
                        path = "icons/eq-weapon.png",
                        size = 44.dp,
                        contentDescription = w.type,
                    )
                    DexRowTexts(
                        title = w.name,
                        subtitle = "${w.en} · 攻击 ${w.atk}",
                    )
                    Text(
                        "ATK ${w.atk}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
        if (filtered.isEmpty()) EmptyHint(q)
    }
}

@Composable
private fun ArmorList(
    armors: List<com.botwmap.app.data.model.ArmorItem>,
    q: String,
) {
    val filtered = remember(armors, q) {
        if (q.isEmpty()) armors
        else armors.filter {
            it.name.contains(q, true) || it.en.contains(q, true) ||
                it.set.contains(q, true) || it.part.contains(q, true)
        }
    }
    val collapsed = remember { mutableStateMapOf<String, Boolean>() }
    LazyColumn(contentPadding = PaddingValues(bottom = 160.dp)) {
        ARMOR_ORDER.forEach { part ->
            val group = filtered.filter { it.part == part }
            if (group.isEmpty()) return@forEach
            val isCollapsed = collapsed[part] ?: false
            item(key = "a_h_$part") {
                DexGroupHeader(
                    title = part,
                    count = group.size,
                    collapsed = isCollapsed,
                    onToggle = { collapsed[part] = !isCollapsed },
                )
            }
            if (isCollapsed) return@forEach
            items(group, key = { it.id }) { a ->
                DexRowCard {
                    com.botwmap.app.ui.components.AssetThumb(
                        path = "icons/eq-armor.png",
                        size = 44.dp,
                        contentDescription = a.part,
                    )
                    DexRowTexts(
                        title = a.name,
                        subtitle = buildString {
                            if (a.set.isNotBlank()) append(a.set).append(" · ")
                            append(a.en)
                        },
                    )
                    Text(
                        "DEF ${a.def}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
        if (filtered.isEmpty()) EmptyHint(q)
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.EmptyHint(q: String) {
    item {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
        ) {
            Text(
                text = if (q.isEmpty()) "暂无数据" else "无匹配「$q」",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
