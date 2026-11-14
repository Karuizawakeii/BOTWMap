# -*- coding: utf-8 -*-
"""重构 SettingsScreen：外观分组 + md3Music 界面背景块 + 透明行"""
import io

p = r"D:\repos\BOTWMap\app\src\main\java\com\botwmap\app\ui\settings\SettingsScreen.kt"
s = io.open(p, encoding="utf-8").read()

# ---- 1) imports ----
def ensure(before, insert):
    global s
    if insert.split("\n")[0] not in s:
        assert before in s, "import anchor: " + before
        s = s.replace(before, insert, 1)

ensure("import androidx.compose.material.icons.filled.ContentCopy",
       "import androidx.compose.material.icons.filled.BlurOn\n"
       "import androidx.compose.material.icons.filled.ContentCopy\n"
       "import androidx.compose.material.icons.filled.DeleteSweep\n"
       "import androidx.compose.material.icons.filled.Image\n"
       "import androidx.compose.material.icons.filled.WaterDrop")
ensure("import androidx.compose.material.icons.automirrored.filled.ArrowBack",
       "import androidx.compose.material.icons.automirrored.filled.ArrowBack\n"
       "import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos")
ensure("import androidx.compose.material3.ListItem\n",
       "import androidx.compose.material3.ListItem\n"
       "import androidx.compose.material3.ListItemDefaults\n")
ensure("import androidx.compose.ui.graphics.Color\n",
       "import androidx.compose.ui.graphics.Color\n"
       "import androidx.compose.ui.graphics.asImageBitmap\n"
       "import androidx.compose.ui.graphics.vector.ImageVector\n")
ensure("import androidx.compose.ui.text.AnnotatedString\n",
       "import androidx.compose.ui.text.AnnotatedString\n"
       "import androidx.compose.ui.text.font.FontWeight\n"
       "import androidx.compose.ui.text.style.TextAlign\n")
ensure("import androidx.compose.ui.unit.dp\n",
       "import androidx.compose.ui.unit.dp\n"
       "import androidx.compose.ui.draw.clip\n"
       "import androidx.compose.foundation.clickable\n"
       "import androidx.compose.foundation.shape.RoundedCornerShape\n"
       "import androidx.compose.ui.layout.ContentScale\n")

# ---- 2) 所有现有 ListItem 加透明 colors ----
s = s.replace("ListItem(\n",
              "ListItem(\n                colors = ListItemDefaults.colors(containerColor = Color.Transparent),\n")

# ---- 3) state ----
old = """    val barBgFile by repository.customBarBgFile.collectAsStateWithLifecycle(initialValue = null)
    val scope = androidx.compose.runtime.rememberCoroutineScope()"""
new = """    val barBgFile by repository.customBarBgFile.collectAsStateWithLifecycle(initialValue = null)
    val bgEnabled by repository.bgImageEnabled.collectAsStateWithLifecycle(initialValue = true)
    val bgBlur by repository.bgImageBlur.collectAsStateWithLifecycle(initialValue = 0f)
    val bgAlpha by repository.bgImageAlpha.collectAsStateWithLifecycle(initialValue = 1f)
    val scope = androidx.compose.runtime.rememberCoroutineScope()"""
assert old in s, "state"
s = s.replace(old, new, 1)

old = """    // 背景图片 UI 状态
    var bgMsg by remember { mutableStateOf<String?>(null) }"""
new = """    // 背景图片 UI 状态
    var bgMsg by remember { mutableStateOf<String?>(null) }
    val bgPreviewBitmap = remember(barBgFile) {
        barBgFile?.let { name ->
            runCatching {
                android.graphics.BitmapFactory.decodeFile(
                    java.io.File(appContext.filesDir, name).absolutePath,
                )
            }.getOrNull()?.asImageBitmap()
        }
    }"""
assert old in s, "preview"
s = s.replace(old, new, 1)

# ---- 4) SectionHeader 插入 ----
old = """            // 主题模式
            ListItem("""
new = """            SectionHeader("外观")

            // 主题模式
            ListItem("""
assert old in s, "sec1"
s = s.replace(old, new, 1)

old = """            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                headlineContent = { Text("显示已完成标记") },"""
new = """            SectionHeader("地图显示")

            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                headlineContent = { Text("显示已完成标记") },"""
assert old in s, "sec2"
s = s.replace(old, new, 1)

old = """            // ---- 分享与备份 ----"""
new = """            SectionHeader("其他")

            // ---- 分享与备份 ----"""
assert old in s, "sec3"
s = s.replace(old, new, 1)

# ---- 5) 旧 App 背景图区块 -> md3Music 界面背景块 ----
start = s.index("            // ---- App 背景图 + 底栏玻璃参数 ----")
end_marker = """                bgMsg?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
            }
"""
end = s.index(end_marker, start) + len(end_marker)
new_block = """            // ---- 界面背景（md3Music 风格）----
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
                            java.io.File(appContext.filesDir, "liquid_bar_bg.png").delete()
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

            // ---- 底栏玻璃（独立于背景图）----
            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                headlineContent = { Text("底栏玻璃") },
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
"""
s = s[:start] + new_block + s[end:]

# ---- 6) 底部留白 96 -> 160（edge-to-edge：底栏全高 + 导航条）----
s = s.replace("Spacer(Modifier.height(96.dp))", "Spacer(Modifier.height(160.dp))")

# ---- 7) 文件尾加 helper 函数 ----
helpers = """

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
"""
s = s.rstrip() + "\n" + helpers
io.open(p, "w", encoding="utf-8").write(s)
print("OK SettingsScreen")
