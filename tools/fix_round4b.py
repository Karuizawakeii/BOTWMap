# -*- coding: utf-8 -*-
"""剩余修复：面板模糊 + Dex Tab + Quests 进度条（AppNavHost/MapScreen 已完成）"""
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

# ========== LayersPanel ==========
p = "ui/layers/LayersPanel.kt"
s = load(p)
s = ensure_imports(s, [
    "com.kyant.backdrop.backdrops.LayerBackdrop",
    "com.kyant.backdrop.drawBackdrop",
    "com.kyant.backdrop.effects.blur",
    "androidx.compose.ui.graphics.RectangleShape",
    "androidx.compose.foundation.layout.Box",
])
old = """    onDismiss: () -> Unit,
) {
    if (!visible) return"""
new = """    onDismiss: () -> Unit,
    backdrop: LayerBackdrop? = null,
) {
    if (!visible) return"""
assert old in s, "lp sig"
s = s.replace(old, new, 1)

old = """    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.Transparent,
    ) {
        Column("""
new = """    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = if (backdrop != null) {
            Color.Transparent
        } else {
            MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
        },
    ) {
        Box(Modifier.fillMaxWidth()) {
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
assert old in s, "lp sheet"
s = s.replace(old, new, 1)

# 闭合 Box：在 ModalBottomSheet 的 } 之前（rfind 最后的 8/4/0 结构）
tail = "        }\n    }\n}"
idx = s.rfind(tail)
assert idx > 0, "lp close"
s = s[:idx] + "        }\n" + s[idx:]
save(p, s)
print("OK LayersPanel")

# ========== StatsPanel ==========
p = "ui/stats/StatsPanel.kt"
s = load(p)
s = ensure_imports(s, [
    "com.kyant.backdrop.backdrops.LayerBackdrop",
    "com.kyant.backdrop.drawBackdrop",
    "com.kyant.backdrop.effects.blur",
    "androidx.compose.ui.graphics.RectangleShape",
    "androidx.compose.foundation.layout.Box",
])
old = """    onDismiss: () -> Unit,
) {
    if (!visible) return"""
new = """    onDismiss: () -> Unit,
    backdrop: LayerBackdrop? = null,
) {
    if (!visible) return"""
assert old in s, "sp sig"
s = s.replace(old, new, 1)

old = """    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.Transparent,
    ) {
        Column("""
new = """    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = if (backdrop != null) {
            Color.Transparent
        } else {
            MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
        },
    ) {
        Box(Modifier.fillMaxWidth()) {
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
assert old in s, "sp sheet"
s = s.replace(old, new, 1)

tail = "        }\n    }\n}"
idx = s.rfind(tail)
assert idx > 0, "sp close"
s = s[:idx] + "        }\n" + s[idx:]
save(p, s)
print("OK StatsPanel")

# ========== DexScreen：TabRow 透明 ==========
p = "ui/dex/DexScreen.kt"
s = load(p)
old = "            TabRow(selectedTabIndex = tab) {"
new = "            TabRow(selectedTabIndex = tab, containerColor = Color.Transparent) {"
assert old in s, "dex tab"
s = s.replace(old, new, 1)
save(p, s)
print("OK Dex")

# ========== QuestsScreen：进度条轨道 + 卡片更透 ==========
p = "ui/quests/QuestsScreen.kt"
s = load(p)
old = """                LinearProgressIndicator(
                    progress = {
                        if (filtered.isEmpty()) 0f else doneCount.toFloat() / filtered.size
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp),
                )"""
new = """                LinearProgressIndicator(
                    progress = {
                        if (filtered.isEmpty()) 0f else doneCount.toFloat() / filtered.size
                    },
                    trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp),
                )"""
assert old in s, "q progress"
s = s.replace(old, new, 1)
assert "}).copy(alpha = 0.78f)," in s, "q card alpha"
s = s.replace("}).copy(alpha = 0.78f),", "}).copy(alpha = 0.62f),", 1)
save(p, s)
print("OK Quests")

print("ALL DONE")
