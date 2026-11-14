package com.botwmap.app.ui.quests

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.botwmap.app.data.model.QuestItem
import com.botwmap.app.data.repository.MapRepository
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import com.botwmap.app.ui.navigation.GlobalBackground
import com.botwmap.app.ui.effect.liquid.glassSurfaceColor
import com.botwmap.app.ui.effect.liquid.glassBorderColor
import androidx.compose.foundation.BorderStroke
import com.botwmap.app.ui.effect.liquid.LiquidGlassPanel
import com.botwmap.app.ui.effect.liquid.liquidGlassSurface

private val CAT_META = linkedMapOf(
    "main" to ("主线挑战" to Color(0xFFEAC27E)),
    "shrine" to ("神庙挑战" to Color(0xFF6FB3E0)),
    "side" to ("支线任务" to Color(0xFF7EC8A9)),
    "dlc" to ("DLC 任务" to Color(0xFFC58FE0)),
)

/**
 * 任务列表页：152 个任务按 4 分类分组（组头可展开/收起，风格对齐图层面板），
 * 搜索 + 完成勾选 + 详情展开，"在地图查看"经焦点请求跳转地图定位。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuestsScreen(
    onNavigateToMap: () -> Unit,
    repository: MapRepository = koinInject(),
) {
    val static by repository.static.collectAsStateWithLifecycle()
    val completed by repository.completedQuests.collectAsStateWithLifecycle(initialValue = emptySet())
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf(TextFieldValue("")) }
    var expandedKey by remember { mutableStateOf<String?>(null) }
    val collapsedCats = remember { mutableStateMapOf<String, Boolean>() }

    val quests = static?.quests ?: emptyList()
    val q = query.text.trim()
    val filtered = remember(quests, q) {
        if (q.isEmpty()) quests
        else quests.filter {
            it.name.contains(q, true) || it.en.contains(q, true) ||
                it.region.contains(q, true) || it.npc.contains(q, true)
        }
    }

// 不透明背景层：转场中不透出旧页（残留），与全局背景同 Crop 对齐
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
    ) {
    GlobalBackground()

        Scaffold(
        containerColor = Color.Transparent,
        // 只避让状态栏：底部不消费 inset，列表可滚到屏幕最底（手势条区）
        contentWindowInsets = WindowInsets.statusBars,
        topBar = {
            TopAppBar(
                title = { Text("任务") },
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
            // 总进度
            val doneCount = filtered.count { it.key in completed }
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Row {
                    Text(
                        text = if (q.isEmpty()) "总进度" else "搜索结果",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "$doneCount / ${filtered.size}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = {
                        if (filtered.isEmpty()) 0f else doneCount.toFloat() / filtered.size
                    },
                    trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp),
                )
            }

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("搜索任务名 / 区域 / NPC（中英文）") },
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
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .liquidGlassSurface(
                        shape = RoundedCornerShape(50),
                        cornerRadius = 28.dp,
                    ),
            )

            // 内容延伸到底（piliplus 风格）：底部透明，底栏悬浮其上
            LazyColumn(contentPadding = PaddingValues(bottom = 220.dp)) {
                CAT_META.forEach { (cat, meta) ->
                    val (catName, catColor) = meta
                    val group = filtered.filter { it.cat == cat }
                    if (group.isEmpty()) return@forEach
                    val collapsed = collapsedCats[cat] ?: false
                    val groupDone = group.count { it.key in completed }
                    val allDone = groupDone == group.size

                    // 组头（与图层面板探索 Tab 同风格：大字组名 + 计数 + 操作 + 右侧箭头）
                    item(key = "head_$cat") {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { collapsedCats[cat] = !collapsed }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = catName,
                                style = MaterialTheme.typography.titleMedium,
                                color = catColor,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = "$groupDone / ${group.size}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.width(6.dp))
                            TextButton(onClick = {
                                scope.launch {
                                    group.forEach {
                                        repository.setQuestCompleted(it.key, !allDone)
                                    }
                                }
                            }) {
                                Text(if (allDone) "清本组" else "完成本组")
                            }
                            Icon(
                                imageVector = if (collapsed) Icons.Filled.ExpandMore
                                else Icons.Filled.ExpandLess,
                                contentDescription = null,
                            )
                        }
                    }

                    if (!collapsed) {
                        items(group, key = { it.key }) { quest ->
                            QuestRow(
                                quest = quest,
                                done = quest.key in completed,
                                expanded = expandedKey == quest.key,
                                catColor = catColor,
                                onToggle = {
                                    scope.launch {
                                        repository.setQuestCompleted(
                                            quest.key,
                                            quest.key !in completed,
                                        )
                                    }
                                },
                                onExpand = {
                                    expandedKey =
                                        if (expandedKey == quest.key) null else quest.key
                                },
                                onShowOnMap = {
                                    quest.firstPt?.let { (px, py) ->
                                        repository.requestMapFocus(px, py)
                                        onNavigateToMap()
                                    }
                                },
                            )
                        }
                        item(key = "div_$cat") { HorizontalDivider() }
                    }
                }

                if (filtered.isEmpty()) {
                    item {
                        Text(
                            text = if (q.isEmpty()) "暂无任务数据" else "无匹配「$q」",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
    }
}

@Composable
private fun QuestRow(
    quest: QuestItem,
    done: Boolean,
    expanded: Boolean,
    catColor: androidx.compose.ui.graphics.Color,
    onToggle: () -> Unit,
    onExpand: () -> Unit,
    onShowOnMap: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onExpand)
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        LiquidGlassPanel(
            shape = RoundedCornerShape(16.dp),
            cornerRadius = 16.dp,
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "#${quest.no}",
                            style = MaterialTheme.typography.labelMedium,
                            color = catColor,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.padding(horizontal = 4.dp))
                        Text(
                            text = quest.name,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (done) FontWeight.Normal else FontWeight.Medium,
                            color = if (done) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )
                    }
                    if (quest.region.isNotBlank()) {
                        Text(
                            text = quest.region +
                                if (quest.npc.isNotBlank()) " · ${quest.npc}" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                IconButton(onClick = onToggle) {
                    Icon(
                        imageVector = if (done) Icons.Filled.CheckCircle
                        else Icons.Outlined.RadioButtonUnchecked,
                        contentDescription = if (done) "取消完成" else "标记完成",
                        tint = if (done) Color(0xFF2E7D32)
                        else MaterialTheme.colorScheme.outline,
                    )
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                ) {
                    if (quest.en.isNotBlank()) {
                        Text(
                            text = quest.en,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                        Spacer(Modifier.height(6.dp))
                    }
                    if (quest.desc.isNotBlank()) {
                        Text(
                            text = quest.desc,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    TextButton(onClick = onShowOnMap) {
                        Icon(
                            Icons.Filled.Map,
                            contentDescription = null,
                            modifier = Modifier.height(18.dp),
                        )
                        Spacer(Modifier.padding(horizontal = 4.dp))
                        Text("在地图查看")
                    }
                }
            }
        }
    }
}
