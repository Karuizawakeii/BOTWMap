# -*- coding: utf-8 -*-
import io

BASE = r"D:\repos\BOTWMap\app\src\main\java\com\botwmap\app"


def ensure_imports(s, imports):
    lines = s.split("\n")
    last = max(i for i, l in enumerate(lines) if l.startswith("import "))
    miss = [x for x in imports if "import " + x not in s]
    for j, im in enumerate(miss):
        lines.insert(last + 1 + j, "import " + im)
    return "\n".join(lines)


# ========== 0) 公共玻璃颜色辅助 ==========
p = BASE + r"\ui\effect\liquid\LiquidGlassPanel.kt"
s = io.open(p, encoding="utf-8").read()
if "glassSurfaceColor" not in s:
    s = s.replace(
        "import androidx.compose.ui.unit.dp",
        "import androidx.compose.material3.MaterialTheme\nimport androidx.compose.ui.graphics.Color\nimport androidx.compose.ui.unit.dp",
        1,
    )
    s += "\n\n/** 玻璃容器表面色（半透明，透出背景 —— 与液态玻璃同视觉） */\n@Composable\nfun glassSurfaceColor(alpha: Float = 0.30f): Color =\n    MaterialTheme.colorScheme.surface.copy(alpha = alpha)\n\n/** 玻璃边框色（细描边） */\n@Composable\nfun glassBorderColor(alpha: Float = 0.35f): Color =\n    MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)\n\n/** 玻璃选中态表面色（主题色半透明） */\n@Composable\nfun glassSelectedColor(alpha: Float = 0.55f): Color =\n    MaterialTheme.colorScheme.primary.copy(alpha = alpha)\n"
    io.open(p, "w", encoding="utf-8").write(s)
print("OK helpers")

# ========== 1) 图层页 ==========
p = BASE + r"\ui\layers\LayersPanel.kt"
s = io.open(p, encoding="utf-8").read()

old = "                    OutlinedButton(onClick = onClearAll) { Text(\"清空\") }\n                    Spacer(Modifier.width(8.dp))\n                    Button(onClick = onSelectAll) { Text(\"全选\") }"
new = (
    "                    OutlinedButton(\n"
    "                        onClick = onClearAll,\n"
    "                        border = BorderStroke(1.dp, glassBorderColor()),\n"
    "                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),\n"
    "                    ) { Text(\"清空\") }\n"
    "                    Spacer(Modifier.width(8.dp))\n"
    "                    Button(\n"
    "                        onClick = onSelectAll,\n"
    "                        colors = ButtonDefaults.buttonColors(containerColor = glassSelectedColor()),\n"
    "                    ) { Text(\"全选\") }"
)
assert old in s, "btn1"
s = s.replace(old, new, 1)

old = "                            OutlinedButton(onClick = { onSetMaterials(emptySet()) }) { Text(\"清空\") }\n                            Spacer(Modifier.width(8.dp))\n                            Button(onClick = {\n                                onSetMaterials(materials.map { it.id }.toSet())\n                            }) { Text(\"全选\") }"
new = (
    "                            OutlinedButton(\n"
    "                                onClick = { onSetMaterials(emptySet()) },\n"
    "                                border = BorderStroke(1.dp, glassBorderColor()),\n"
    "                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),\n"
    "                            ) { Text(\"清空\") }\n"
    "                            Spacer(Modifier.width(8.dp))\n"
    "                            Button(\n"
    "                                onClick = { onSetMaterials(materials.map { it.id }.toSet()) },\n"
    "                                colors = ButtonDefaults.buttonColors(containerColor = glassSelectedColor()),\n"
    "                            ) { Text(\"全选\") }"
)
assert old in s, "btn2"
s = s.replace(old, new, 1)

old = (
    "                                        FilterChip(\n"
    "                                            colors = FilterChipDefaults.filterChipColors(\n"
    "                                                containerColor = Color.Transparent,\n"
    "                                            ),\n"
    "                                            selected = cat.key in enabledLayerIds,"
)
new = (
    "                                        FilterChip(\n"
    "                                            colors = FilterChipDefaults.filterChipColors(\n"
    "                                                containerColor = glassSurfaceColor(0.28f),\n"
    "                                                selectedContainerColor = glassSelectedColor(),\n"
    "                                                labelColor = MaterialTheme.colorScheme.onSurface,\n"
    "                                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary,\n"
    "                                            ),\n"
    "                                            border = BorderStroke(\n"
    "                                                1.dp,\n"
    "                                                if (cat.key in enabledLayerIds) glassSelectedColor(0.7f)\n"
    "                                                else glassBorderColor(0.30f),\n"
    "                                            ),\n"
    "                                            selected = cat.key in enabledLayerIds,"
)
assert old in s, "chip"
s = s.replace(old, new, 1)

old = (
    "                OutlinedTextField(\n"
    "                    value = matQuery,\n"
    "                    onValueChange = { matQuery = it },\n"
    "                    placeholder = { Text(\"搜索材料（如：生命、松露）\") },\n"
    "                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },\n"
    "                    singleLine = true,\n"
    "                    modifier = Modifier\n"
    "                        .fillMaxWidth()\n"
    "                        .padding(bottom = 8.dp),\n"
    "                )"
)
new = (
    "                OutlinedTextField(\n"
    "                    value = matQuery,\n"
    "                    onValueChange = { matQuery = it },\n"
    "                    placeholder = { Text(\"搜索材料（如：生命、松露）\") },\n"
    "                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },\n"
    "                    singleLine = true,\n"
    "                    shape = RoundedCornerShape(50),\n"
    "                    colors = OutlinedTextFieldDefaults.colors(\n"
    "                        focusedBorderColor = glassBorderColor(0.55f),\n"
    "                        unfocusedBorderColor = glassBorderColor(0.30f),\n"
    "                        focusedContainerColor = glassSurfaceColor(0.25f),\n"
    "                        unfocusedContainerColor = glassSurfaceColor(0.25f),\n"
    "                    ),\n"
    "                    modifier = Modifier\n"
    "                        .fillMaxWidth()\n"
    "                        .padding(bottom = 8.dp),\n"
    "                )"
)
assert old in s, "mat search"
s = s.replace(old, new, 1)

s = ensure_imports(s, [
    "androidx.compose.foundation.BorderStroke",
    "androidx.compose.material3.ButtonDefaults",
    "androidx.compose.material3.OutlinedTextFieldDefaults",
    "androidx.compose.foundation.shape.RoundedCornerShape",
    "com.botwmap.app.ui.effect.liquid.glassSurfaceColor",
    "com.botwmap.app.ui.effect.liquid.glassBorderColor",
    "com.botwmap.app.ui.effect.liquid.glassSelectedColor",
])
io.open(p, "w", encoding="utf-8").write(s)
print("OK layers")

# ========== 2) 图鉴搜索框 ==========
p = BASE + r"\ui\dex\DexScreen.kt"
s = io.open(p, encoding="utf-8").read()
old = (
    "                colors = OutlinedTextFieldDefaults.colors(\n"
    "                    focusedContainerColor = Color.Transparent,\n"
    "                    unfocusedContainerColor = Color.Transparent,\n"
    "                ),\n"
    "                modifier = Modifier\n"
    "                    .fillMaxWidth()\n"
    "                    .padding(horizontal = 16.dp, vertical = 8.dp),"
)
new = (
    "                colors = OutlinedTextFieldDefaults.colors(\n"
    "                    focusedBorderColor = glassBorderColor(0.55f),\n"
    "                    unfocusedBorderColor = glassBorderColor(0.30f),\n"
    "                    focusedContainerColor = glassSurfaceColor(0.25f),\n"
    "                    unfocusedContainerColor = glassSurfaceColor(0.25f),\n"
    "                ),\n"
    "                modifier = Modifier\n"
    "                    .fillMaxWidth()\n"
    "                    .padding(horizontal = 16.dp, vertical = 8.dp),"
)
assert old in s, "dex search"
s = s.replace(old, new, 1)
s = ensure_imports(s, [
    "com.botwmap.app.ui.effect.liquid.glassSurfaceColor",
    "com.botwmap.app.ui.effect.liquid.glassBorderColor",
])
io.open(p, "w", encoding="utf-8").write(s)
print("OK dex")

# ========== 3) 任务页 ==========
p = BASE + r"\ui\quests\QuestsScreen.kt"
s = io.open(p, encoding="utf-8").read()
old = (
    "                colors = OutlinedTextFieldDefaults.colors(\n"
    "                    focusedContainerColor = Color.Transparent,\n"
    "                    unfocusedContainerColor = Color.Transparent,\n"
    "                ),\n"
    "                modifier = Modifier\n"
    "                    .fillMaxWidth()\n"
    "                    .padding(horizontal = 16.dp, vertical = 4.dp),"
)
new = (
    "                colors = OutlinedTextFieldDefaults.colors(\n"
    "                    focusedBorderColor = glassBorderColor(0.55f),\n"
    "                    unfocusedBorderColor = glassBorderColor(0.30f),\n"
    "                    focusedContainerColor = glassSurfaceColor(0.25f),\n"
    "                    unfocusedContainerColor = glassSurfaceColor(0.25f),\n"
    "                ),\n"
    "                modifier = Modifier\n"
    "                    .fillMaxWidth()\n"
    "                    .padding(horizontal = 16.dp, vertical = 4.dp),"
)
assert old in s, "quest search"
s = s.replace(old, new, 1)

old = (
    "        Card(\n"
    "            colors = CardDefaults.cardColors(\n"
    "                containerColor = (if (expanded) {\n"
    "                    MaterialTheme.colorScheme.surfaceContainerHigh\n"
    "                } else {\n"
    "                    MaterialTheme.colorScheme.surfaceContainerLow\n"
    "                }).copy(alpha = 0.62f),\n"
    "            ),\n"
    "        ) {"
)
new = (
    "        Card(\n"
    "            colors = CardDefaults.cardColors(\n"
    "                containerColor = if (expanded) glassSurfaceColor(0.42f) else glassSurfaceColor(0.28f),\n"
    "            ),\n"
    "            border = CardDefaults.cardBorder(\n"
    "                borderColor = glassBorderColor(0.25f),\n"
    "            ),\n"
    "        ) {"
)
assert old in s, "quest card"
s = s.replace(old, new, 1)
s = ensure_imports(s, [
    "com.botwmap.app.ui.effect.liquid.glassSurfaceColor",
    "com.botwmap.app.ui.effect.liquid.glassBorderColor",
])
io.open(p, "w", encoding="utf-8").write(s)
print("OK quests")
print("ALL DONE")
