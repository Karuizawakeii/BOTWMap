# -*- coding: utf-8 -*-
import io

p = r"D:\repos\BOTWMap\app\src\main\java\com\botwmap\app\ui\dex\DexScreen.kt"
s = io.open(p, encoding="utf-8").read()


def ensure_imports(s, imports):
    lines = s.split("\n")
    last = max(i for i, l in enumerate(lines) if l.startswith("import "))
    miss = [x for x in imports if "import " + x not in s]
    for j, im in enumerate(miss):
        lines.insert(last + 1 + j, "import " + im)
    return "\n".join(lines)


# ===== 材料行 =====
old = """            items(group, key = { "mat_${it.id}" }) { m ->
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    leadingContent = {
                        com.botwmap.app.ui.components.AssetThumb(
                            path = "materials/${m.entry}.webp",
                            size = 44.dp,
                            contentDescription = m.cn,
                        )
                    },
                    headlineContent = { Text(m.cn) },
                    supportingContent = {
                        Text(
                            buildString {
                                if (m.en.isNotBlank()) append(m.en).append(" · ")
                                append("刷点 ").append(m.count)
                            },
                            style = MaterialTheme.typography.bodySmall,
                        )
                    },
                    trailingContent = {
                        if (m.count > 0) {
                            IconButton(onClick = { onShowMap(m.id) }) {
                                Icon(
                                    Icons.Filled.Map,
                                    contentDescription = "在地图查看刷点",
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    },
                )
                HorizontalDivider()
            }"""
new = """            items(group, key = { "mat_${it.id}" }) { m ->
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
            }"""
assert old in s, "mat row"
s = s.replace(old, new, 1)

# ===== 武器行 =====
old = """            items(group, key = { it.id }) { w ->
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    leadingContent = {
                        com.botwmap.app.ui.components.AssetThumb(
                            path = "icons/eq-weapon.png",
                            size = 44.dp,
                            contentDescription = w.type,
                        )
                    },
                    headlineContent = { Text(w.name) },
                    supportingContent = {
                        Text(
                            "${w.en} · 攻击 ${w.atk}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    },
                    trailingContent = {
                        Text(
                            "ATK ${w.atk}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    },
                )
                HorizontalDivider()
            }"""
new = """            items(group, key = { it.id }) { w ->
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
            }"""
assert old in s, "weapon row"
s = s.replace(old, new, 1)

# ===== 防具行 =====
old = """            items(group, key = { it.id }) { a ->
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    leadingContent = {
                        com.botwmap.app.ui.components.AssetThumb(
                            path = "icons/eq-armor.png",
                            size = 44.dp,
                            contentDescription = a.part,
                        )
                    },
                    headlineContent = { Text(a.name) },
                    supportingContent = {
                        Text(
                            buildString {
                                if (a.set.isNotBlank()) append(a.set).append(" · ")
                                append(a.en)
                            },
                            style = MaterialTheme.typography.bodySmall,
                        )
                    },
                    trailingContent = {
                        Text(
                            "DEF ${a.def}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    },
                )
                HorizontalDivider()
            }"""
new = """            items(group, key = { it.id }) { a ->
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
            }"""
assert old in s, "armor row"
s = s.replace(old, new, 1)

# ===== 公共行组件（与任务页卡片同结构：外层 h16 v4 间距 + Card 玻璃 + Row h12 v10） =====
anchor = "/** 材料分组顺序（与图层面板一致） */"
assert anchor in s, "anchor"
helpers = """/** 图鉴行卡片：与任务页任务卡片同款（玻璃半透明 + 细描边 + 同间距） */
@Composable
private fun DexRowCard(content: @Composable RowScope.() -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = glassSurfaceColor(0.28f)),
        border = BorderStroke(1.dp, glassBorderColor(0.25f)),
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

/** 材料分组顺序（与图层面板一致） */"""
s = s.replace(anchor, helpers, 1)

s = ensure_imports(s, [
    "androidx.compose.foundation.BorderStroke",
    "androidx.compose.foundation.layout.RowScope",
    "androidx.compose.foundation.layout.Column",
    "androidx.compose.foundation.layout.fillMaxWidth",
    "androidx.compose.foundation.layout.padding",
    "androidx.compose.foundation.layout.width",
    "androidx.compose.material3.Card",
    "androidx.compose.material3.CardDefaults",
    "androidx.compose.ui.Alignment",
    "androidx.compose.ui.Modifier",
    "androidx.compose.ui.unit.dp",
    "com.botwmap.app.ui.effect.liquid.glassSurfaceColor",
    "com.botwmap.app.ui.effect.liquid.glassBorderColor",
])

io.open(p, "w", encoding="utf-8").write(s)
print("OK delta:", s.count("{") - s.count("}"))
