# -*- coding: utf-8 -*-
"""整页模糊补全：共享背景层进地图采样区 + 底栏磨砂参数"""
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

# ========== 1) AppNavHost：LocalBackground + GlobalBackground + Provider ==========
p = "ui/navigation/AppNavHost.kt"
s = load(p)
s = ensure_imports(s, [
    "androidx.compose.runtime.CompositionLocalProvider",
    "androidx.compose.runtime.staticCompositionLocalOf",
    "androidx.compose.ui.graphics.ImageBitmap",
])

# 1a. 追加 LocalBackground + GlobalBackground（文件尾）
helpers = '''

/** 全局背景状态（AppNavHost 提供，地图采样区复用同一份 bitmap） */
data class GlobalBackgroundState(
    val bitmap: ImageBitmap?,
    val enabled: Boolean,
    val blurDp: Float,
    val alpha: Float,
)

val LocalBackground = staticCompositionLocalOf {
    GlobalBackgroundState(bitmap = null, enabled = false, blurDp = 0f, alpha = 1f)
}

/** 全局背景图层：设置页选择的图片 + 可读性蒙层（fillMaxSize 铺底） */
@Composable
fun GlobalBackground() {
    val bg = LocalBackground.current
    val bitmap = bg.bitmap ?: return
    if (!bg.enabled) return
    Image(
        bitmap = bitmap,
        contentDescription = null,
        modifier = Modifier
            .fillMaxSize()
            // 轻微放大抵消 RenderEffect 模糊的边缘透明渐变
            .graphicsLayer {
                scaleX = 1.08f
                scaleY = 1.08f
                alpha = bg.alpha.coerceIn(0f, 1f)
            }
            .then(if (bg.blurDp > 0f) Modifier.blur(bg.blurDp.dp) else Modifier),
        contentScale = ContentScale.Crop,
    )
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    Box(
        Modifier
            .fillMaxSize()
            .background(
                if (isDark) {
                    Color.Black.copy(alpha = 0.35f)
                } else {
                    Color.White.copy(alpha = 0.45f)
                },
            ),
    )
}
'''
s = s.rstrip() + "\n" + helpers

# 1b. 采样区内联背景块替换为 GlobalBackground()
old = """            if (bgBitmap != null && bgEnabled) {
                Image(
                    bitmap = bgBitmap,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        // 轻微放大抵消 RenderEffect 模糊的边缘透明渐变
                        .graphicsLayer {
                            scaleX = 1.08f
                            scaleY = 1.08f
                            alpha = bgAlpha.coerceIn(0f, 1f)
                        }
                        .then(if (bgBlur > 0f) Modifier.blur(bgBlur.dp) else Modifier),
                    contentScale = ContentScale.Crop,
                )
                val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            if (isDark) {
                                Color.Black.copy(alpha = 0.35f)
                            } else {
                                Color.White.copy(alpha = 0.45f)
                            },
                        ),
                )
            }

            Scaffold("""
new = """            GlobalBackground()

            Scaffold("""
assert old in s, "replace inline bg"
s = s.replace(old, new, 1)

# 1c. Provider 包 root Box + 底栏磨砂参数
old = """    Box(Modifier.fillMaxSize()) {
        // ---- 采样区：背景图 + 蒙层 + 页面内容（底栏玻璃采样并模糊它们）----"""
new = """    CompositionLocalProvider(
        LocalBackground provides GlobalBackgroundState(
            bitmap = bgBitmap,
            enabled = bgEnabled,
            blurDp = bgBlur,
            alpha = bgAlpha,
        ),
    ) {
    Box(Modifier.fillMaxSize()) {
        // ---- 采样区：背景图 + 蒙层 + 页面内容（底栏玻璃采样并模糊它们）----"""
assert old in s, "provider open"
s = s.replace(old, new, 1)

# 闭合 Provider：文件尾 AppNavHost 函数的最后一个 }
old = """                    blurRadius = if (panelVisible) 28.dp else barBlur.dp,
                    containerAlpha = if (panelVisible) 0.95f else barAlpha,
                )
            }
        }
    }
}"""
new = """                    blurRadius = if (panelVisible) 28.dp else barBlur.dp,
                    containerAlpha = if (panelVisible) 0.5f else barAlpha,
                )
            }
        }
    }
    }
}"""
assert old in s, "provider close"
s = s.replace(old, new, 1)
save(p, s)
print("OK AppNavHost")

# ========== 2) MapScreen：内层采样区画 GlobalBackground ==========
p = "ui/map/MapScreen.kt"
s = load(p)
s = ensure_imports(s, ["com.botwmap.app.ui.navigation.GlobalBackground"])
old = """                ),
        ) {
        MapCanvas("""
new = """                ),
        ) {
        // 背景图进采样区：面板打开时全屏模糊覆盖背景图+地图（而非只模糊地图）
        GlobalBackground()
        MapCanvas("""
assert old in s, "map bg"
s = s.replace(old, new, 1)
save(p, s)
print("OK MapScreen")

print("ALL DONE")
