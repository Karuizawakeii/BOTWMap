package com.botwmap.app.ui.effect.liquid

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Shape
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.botwmap.app.data.repository.MapRepository
import com.botwmap.app.ui.effect.rememberBlurEffectsEnabled
import com.botwmap.app.ui.navigation.LocalLiquidBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.vibrancy
import org.koin.compose.koinInject

/**
 * 通用液态玻璃面板：与底栏/详情卡片同一套 Matter 玻璃
 * （vibrancy + blur + 半透明表面），模糊度/透明度跟随设置页「液态玻璃」滑条。
 * 无 RuntimeShader / 无 backdrop 时回退半透明表面。
 */
@Composable
fun LiquidGlassPanel(
    modifier: Modifier = Modifier,
    shape: Shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
    cornerRadius: Dp = 24.dp,
    content: @Composable () -> Unit,
) {
    val backdrop = LocalLiquidBackdrop.current
    val spec = rememberLiquidGlassSurfaceSpec()
    val blurActive = rememberBlurEffectsEnabled() && backdrop != null
    val repository: MapRepository = koinInject()
    val barBlur by repository.customBarBlur.collectAsStateWithLifecycle(initialValue = 8f)
    val barAlpha by repository.customBarAlpha.collectAsStateWithLifecycle(initialValue = 0.4f)
    val container = if (blurActive && backdrop != null) {
        modifier.drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = {
                vibrancy()
                blur(barBlur.dp.toPx())
            },
            onDrawSurface = {
                drawRoundRect(
                    color = spec.surfaceColor.copy(alpha = barAlpha.coerceIn(0f, 0.75f)),
                    cornerRadius = CornerRadius(cornerRadius.toPx()),
                )
            },
        )
    } else {
        modifier.background(spec.fallbackSurfaceColor, shape)
    }
    Box(container.clip(shape)) {
        content()
    }
}


/** 玻璃容器表面色（半透明，透出背景 —— 与液态玻璃同视觉） */
@Composable
fun glassSurfaceColor(alpha: Float = 0.30f): Color =
    MaterialTheme.colorScheme.surface.copy(alpha = alpha)

/** 玻璃边框色（细描边） */
@Composable
fun glassBorderColor(alpha: Float = 0.35f): Color =
    MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)

/** 玻璃选中态表面色（主题色半透明） */
@Composable
fun glassSelectedColor(alpha: Float = 0.55f): Color =
    MaterialTheme.colorScheme.primary.copy(alpha = alpha)


/**
 * 搜索框等输入控件的液态玻璃背景（与底栏同款：vibrancy + blur + 半透明表面）。
 * 加在控件 modifier 上；控件自身 container 色应设为 Transparent 以透出玻璃。
 */
@Composable
fun Modifier.liquidGlassSurface(
    shape: Shape = androidx.compose.foundation.shape.RoundedCornerShape(50),
    cornerRadius: Dp = 24.dp,
): Modifier {
    val backdrop = LocalLiquidBackdrop.current
    val spec = rememberLiquidGlassSurfaceSpec()
    val blurActive = rememberBlurEffectsEnabled() && backdrop != null
    val repository: MapRepository = koinInject()
    val barBlur by repository.customBarBlur.collectAsStateWithLifecycle(initialValue = 8f)
    val barAlpha by repository.customBarAlpha.collectAsStateWithLifecycle(initialValue = 0.4f)
    return if (blurActive && backdrop != null) {
        this.drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = {
                vibrancy()
                blur(barBlur.dp.toPx())
            },
            onDrawSurface = {
                drawRoundRect(
                    color = spec.surfaceColor.copy(alpha = barAlpha.coerceIn(0f, 0.75f)),
                    cornerRadius = CornerRadius(cornerRadius.toPx()),
                )
            },
        )
    } else {
        this.background(glassSurfaceColor(0.25f), shape)
    }
}
