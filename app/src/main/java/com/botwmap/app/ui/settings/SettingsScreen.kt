package com.botwmap.app.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.provider.OpenableColumns
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.botwmap.app.data.repository.MapRepository
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import com.botwmap.app.ui.navigation.GlobalBackground
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars

/**
 * 设置页：主题模式 / 动态色彩 / 完成标记显示 / 地名标签 / 关于。
 * 直接经 Repository → DataStore 持久化，变更即时生效。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    repository: MapRepository = koinInject(),
) {
    val themeMode by repository.themeMode.collectAsStateWithLifecycle(initialValue = "system")
    val dynamicColor by repository.dynamicColor.collectAsStateWithLifecycle(initialValue = false)
    val showCompleted by repository.showCompleted.collectAsStateWithLifecycle(initialValue = true)
    val showRegions by repository.showRegions.collectAsStateWithLifecycle(initialValue = true)
    val showPaths by repository.showPaths.collectAsStateWithLifecycle(initialValue = true)
    val barBlur by repository.customBarBlur.collectAsStateWithLifecycle(initialValue = 8f)
    val barAlpha by repository.customBarAlpha.collectAsStateWithLifecycle(initialValue = 0.4f)
    val barBgFile by repository.customBarBgFile.collectAsStateWithLifecycle(initialValue = null)
    val bgEnabled by repository.bgImageEnabled.collectAsStateWithLifecycle(initialValue = true)
    val bgBlur by repository.bgImageBlur.collectAsStateWithLifecycle(initialValue = 0f)
    val bgAlpha by repository.bgImageAlpha.collectAsStateWithLifecycle(initialValue = 1f)
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    // 分享码 UI 状态
    val clipboard = LocalClipboardManager.current
    var exportMsg by remember { mutableStateOf<String?>(null) }
    var importExpanded by remember { mutableStateOf(false) }
    var codeInput by remember { mutableStateOf("") }
    var importError by remember { mutableStateOf<String?>(null) }
    var showImportConfirm by remember { mutableStateOf(false) }

    // 加载存档 UI 状态
    var saveMsg by remember { mutableStateOf<String?>(null) }
    var saveHelpExpanded by remember { mutableStateOf(false) }
    val appContext = LocalContext.current

    // 背景图片 UI 状态
    var bgMsg by remember { mutableStateOf<String?>(null) }
    val bgPreviewBitmap = remember(barBgFile) {
        barBgFile?.let { name ->
            runCatching {
                android.graphics.BitmapFactory.decodeFile(
                    java.io.File(appContext.filesDir, name).absolutePath,
                )
            }.getOrNull()?.asImageBitmap()
        }
    }
    val bgLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                // 文件名带时间戳：同名覆盖不会触发 bgFile 变化 → 换图不生效
                appContext.filesDir.listFiles()?.forEach { f ->
                    if (f.name.startsWith("bg_") || f.name == "liquid_bar_bg.png") f.delete()
                }
                val name = "bg_${System.currentTimeMillis()}.png"
                appContext.contentResolver.openInputStream(uri)?.use { input ->
                    java.io.File(appContext.filesDir, name).outputStream().use { output ->
                        input.copyTo(output)
                    }
                } ?: error("无法读取图片")
                repository.setCustomBarBgFile(name)
            }
                .onSuccess { bgMsg = "背景图片已更新，底栏立即生效" }
                .onFailure { bgMsg = "设置失败：${it.message}" }
        }
    }

    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            saveMsg = "正在解析存档…"
            runCatching {
                val name = appContext.contentResolver
                    .query(uri, null, null, null, null)?.use { c ->
                        val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (c.moveToFirst() && i >= 0) c.getString(i) else null
                    } ?: "game_data.sav"
                val bytes = appContext.contentResolver.openInputStream(uri)
                    ?.use { it.readBytes() } ?: error("无法读取文件")
                repository.applySave(bytes, name)
            }
                .onSuccess { saveMsg = it }
                .onFailure { saveMsg = "加载失败：${it.message}" }
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
        // 只避让状态栏：手势导航条区透明，内容延伸到屏幕最底
        contentWindowInsets = WindowInsets.statusBars,
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("设置") },
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
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            SectionHeader("外观")

            // 主题模式
            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                headlineContent = { Text("外观主题") },
                supportingContent = { Text("跟随系统，或固定浅色 / 深色") },
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            ) {
                listOf("system" to "跟随系统", "light" to "浅色", "dark" to "深色").forEach { (mode, label) ->
                    FilterChip(
                        selected = themeMode == mode,
                        onClick = { scope.launch { repository.setThemeMode(mode) } },
                        label = { Text(label) },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = Color.Transparent,
                        ),
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                headlineContent = { Text("动态色彩") },
                supportingContent = { Text("根据壁纸取色（Android 12+）") },
                trailingContent = {
                    Switch(
                        checked = dynamicColor,
                        onCheckedChange = { scope.launch { repository.setDynamicColor(it) } },
                    )
                },
            )

            SectionHeader("地图显示")

            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                headlineContent = { Text("显示已完成标记") },
                supportingContent = { Text("关闭后地图上隐藏已勾选的点位") },
                trailingContent = {
                    Switch(
                        checked = showCompleted,
                        onCheckedChange = { scope.launch { repository.setShowCompleted(it) } },
                    )
                },
            )

            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                headlineContent = { Text("显示地名标签") },
                supportingContent = { Text("大区 / 地形区名称叠加在地图上") },
                trailingContent = {
                    Switch(
                        checked = showRegions,
                        onCheckedChange = { scope.launch { repository.setShowRegions(it) } },
                    )
                },
            )

            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                headlineContent = { Text("显示龙飞行轨迹") },
                supportingContent = { Text("费罗龙 / 奥尔龙 / 聂尔龙路线与刷新点") },
                trailingContent = {
                    Switch(
                        checked = showPaths,
                        onCheckedChange = { scope.launch { repository.setShowPaths(it) } },
                    )
                },
            )

            Spacer(Modifier.height(16.dp))

            // ---- 界面背景（md3Music 风格）----
            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                headlineContent = { Text("界面背景") },
                supportingContent = { Text("选择一张图片作为整个应用的背景") },
            )
            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                headlineContent = { Text("启用自定义背景图片") },
                trailingContent = {
                    Switch(
                        checked = bgEnabled,
                        onCheckedChange = { scope.launch { repository.setBgImageEnabled(it) } },
                    )
                },
            )
            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                headlineContent = { Text(if (barBgFile != null) "更换背景图片" else "选择背景图片") },
                leadingContent = {
                    Icon(Icons.Filled.Image, contentDescription = null)
                },
                trailingContent = {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForwardIos,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                    )
                },
                modifier = Modifier.clickable { bgLauncher.launch(arrayOf("image/*")) },
            )
            if (bgPreviewBitmap != null) {
                Image(
                    bitmap = bgPreviewBitmap,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(12.dp)),
                )
                Spacer(Modifier.height(4.dp))
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    headlineContent = {
                        Text("清除背景图片", color = MaterialTheme.colorScheme.error)
                    },
                    leadingContent = {
                        Icon(
                            Icons.Filled.DeleteSweep,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                        )
                    },
                    modifier = Modifier.clickable {
                        scope.launch {
                            barBgFile?.let { java.io.File(appContext.filesDir, it).delete() }
                            repository.setCustomBarBgFile(null)
                            bgMsg = "已清除背景图片"
                        }
                    },
                )
            }
            IconSliderRow(
                icon = Icons.Filled.BlurOn,
                value = bgBlur,
                range = 0f..24f,
                valueText = if (bgBlur <= 0f) "关闭" else "%.0f dp".format(bgBlur),
                onValueChange = { scope.launch { repository.setBgImageBlur(it) } },
            )
            IconSliderRow(
                icon = Icons.Filled.WaterDrop,
                value = bgAlpha,
                range = 0.1f..1f,
                valueText = "%.0f%%".format(bgAlpha * 100),
                onValueChange = { scope.launch { repository.setBgImageAlpha(it) } },
            )
            bgMsg?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }

            // ---- 液态玻璃（独立于背景图，全局玻璃统一调节）----
            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                headlineContent = { Text("液态玻璃") },
                supportingContent = {
                    Text("液态玻璃底栏的模糊与透明（Android 12+ 生效，低版本自动用纯色）")
                },
            )
            IconSliderRow(
                icon = Icons.Filled.BlurOn,
                value = barBlur,
                range = 0f..24f,
                valueText = if (barBlur <= 0f) "关闭" else "%.0f dp".format(barBlur),
                onValueChange = { scope.launch { repository.setCustomBarBlur(it) } },
            )
            IconSliderRow(
                icon = Icons.Filled.WaterDrop,
                value = barAlpha,
                range = 0.1f..1f,
                valueText = "%.0f%%".format(barAlpha * 100),
                onValueChange = { scope.launch { repository.setCustomBarAlpha(it) } },
            )

            SectionHeader("其他")

            // ---- 分享与备份 ----
            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                headlineContent = { Text("分享与备份") },
                supportingContent = {
                    Text("导出图层、完成进度与自定义标记为分享码；粘贴分享码可恢复他人状态")
                },
            )

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            runCatching { repository.exportShareCode() }
                                .onSuccess { code ->
                                    clipboard.setText(AnnotatedString(code))
                                    exportMsg = "已复制分享码（${code.length / 1024} KB），可粘贴发送给好友"
                                }
                                .onFailure { exportMsg = "导出失败：${it.message}" }
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        Icons.Filled.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("导出")
                }
                OutlinedButton(
                    onClick = { importExpanded = !importExpanded },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        Icons.Filled.Share,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("导入")
                }
            }

            exportMsg?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }

            if (importExpanded) {
                OutlinedTextField(
                    value = codeInput,
                    onValueChange = { codeInput = it; importError = null },
                    placeholder = { Text("粘贴分享码") },
                    isError = importError != null,
                    supportingText = importError?.let { { Text(it) } },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = { showImportConfirm = true },
                        enabled = codeInput.isNotBlank(),
                        modifier = Modifier.weight(1f),
                    ) { Text("解析并导入") }
                    OutlinedButton(
                        onClick = {
                            importExpanded = false
                            codeInput = ""
                            importError = null
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text("取消") }
                }
            }

            Spacer(Modifier.height(16.dp))

            // ---- 加载存档（game_data.sav）----
            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                headlineContent = { Text("加载存档") },
                supportingContent = {
                    Text(
                        "解析 Ryujinx / Cemu 的 game_data.sav，自动标记已收集进度" +
                            "（神庙/塔/克洛格/回忆/神兽/任务/讨伐）；文件仅在本机解析，不会上传",
                    )
                },
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = { saveLauncher.launch(arrayOf("*/*")) },
                    modifier = Modifier.weight(1f),
                ) { Text("选择 game_data.sav") }
                OutlinedButton(
                    onClick = { saveHelpExpanded = !saveHelpExpanded },
                    modifier = Modifier.weight(1f),
                ) { Text(if (saveHelpExpanded) "收起" else "存档在哪？") }
            }
            if (saveHelpExpanded) {
                Text(
                    text = "先把电脑上的存档传到手机（数据线/互传/微信均可），再点上方按钮选择：\n\n" +
                        "Ryujinx（Switch 模拟器）：\n" +
                        "%APPDATA%\\Ryujinx\\bis\\user\\save\\0000000000000008\\0\\1\\game_data.sav\n" +
                        "进游戏 ID 目录 …\\0 后还有一层存档槽文件夹（0、1、2…），" +
                        "挑文件修改时间最新的 game_data.sav；找不到时 Win+R 输入 %APPDATA%\\Ryujinx\\bis\\user\\save 逐步进入。\n\n" +
                        "Cemu（Wii U 模拟器）：\n" +
                        "%APPDATA%\\Cemu\\mlc01\\usr\\save\\00050000\\101c9300\\user\\8000001\\1\\game_data.sav\n" +
                        "同样先进入存档槽文件夹（0、1、2…）再挑 game_data.sav；101c9300 为日版" +
                        "（中文补丁一般基于日版 rom），美版 101c9400、欧版 101c9500。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }
            saveMsg?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (it.startsWith("加载失败")) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }

            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                headlineContent = { Text("关于") },
                supportingContent = {
                    Text(
                        "BOTW 地图 · 基于 yalincc/BOTWmap 数据\n" +
                            "完全离线 · 点位 3038 · 瓦片 7 级",
                    )
                },
            )
            Spacer(Modifier.height(160.dp))
        }
    }

    // 导入覆盖确认
    if (showImportConfirm) {
        AlertDialog(
            onDismissRequest = { showImportConfirm = false },
            title = { Text("导入分享码？") },
            text = { Text("将覆盖当前的图层开关、完成进度与自定义标记，此操作不可撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    showImportConfirm = false
                    scope.launch {
                        runCatching { repository.importShareCode(codeInput) }
                            .onSuccess { payload ->
                                importExpanded = false
                                codeInput = ""
                                exportMsg = "导入成功：图层 ${payload.layers.size} · " +
                                    "点位进度 ${payload.points.size} · 任务 ${payload.quests.size} · " +
                                    "标记 ${payload.custom.size}"
                            }
                            .onFailure {
                                importError = "无效的分享码（${it::class.simpleName}）"
                            }
                    }
                }) { Text("确认导入") }
            },
            dismissButton = {
                TextButton(onClick = { showImportConfirm = false }) { Text("取消") }
            },
        )
    }
    }
}


/** 设置分组标题 */
@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 4.dp),
    )
}

/** 带图标的滑条行：图标 + Slider + 右侧数值（md3Music 样式） */
@Composable
private fun IconSliderRow(
    icon: ImageVector,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    valueText: String,
    onValueChange: (Float) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 10.dp),
        )
        Text(
            text = valueText,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.End,
            modifier = Modifier.width(56.dp),
        )
    }
}
