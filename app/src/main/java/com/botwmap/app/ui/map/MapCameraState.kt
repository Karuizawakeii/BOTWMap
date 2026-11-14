package com.botwmap.app.ui.map

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.IntOffset
import kotlin.math.log2
import kotlin.math.roundToInt

/**
 * 地图相机：与原网页版一致的坐标与缩放语义。
 * - 世界尺寸固定 24000×20000（逻辑坐标，点位 px 直接可用）
 * - scale ∈ [fit×0.85, 1.0]，scale=1 对应 zoom7 最高清瓦片
 * - 瓦片级别 z = clamp(round(log2(scale)) + 7, 1, 7)
 */
class MapCameraState {

    companion object {
        const val MAP_W = 24000f
        const val MAP_H = 20000f

        /** 逻辑坐标 ↔ zoom7 瓦片像素偏移 */
        const val TILE_OFFSET_X = 4384f
        const val TILE_OFFSET_Y = 6384f

        fun zoomForScale(scale: Float): Int {
            val z = Math.round(log2(scale.toDouble()).toFloat()) + 7
            return minOf(7, maxOf(1, z))
        }
    }

    /** 当前缩放（1f = zoom7 1:1） */
    var scale by mutableFloatStateOf(0.1f)
        private set

    /** 平移偏移（屏幕像素） */
    var offset by mutableStateOf(Offset.Zero)
        private set

    var minScale = 0.05f * 0.85f
        private set

    /** 上限 1.5：高清源 3000px/块 = 世界 2000×1.5，scale=1.5 时 1:1 像素 */
    var maxScale = 1.5f
        private set

    /** 全图适配 scale（地名分级基准） */
    var fitScale = 0.05f
        private set

    var viewportSize by mutableStateOf(Size.Zero)
        private set

    private var viewInitialized = false

    /** 视口尺寸变化：首布局 fit；之后保持视图中心不重置用户视角 */
    fun onViewportSize(size: Size) {
        if (size == viewportSize || size == Size.Zero) return
        val old = viewportSize
        viewportSize = size
        fitScale = minOf(size.width / MAP_W, size.height / MAP_H)
        // 缩小下限 = cover：地图刚好覆盖屏幕（含沉浸式状态栏区），不能再缩小
        val coverScale = maxOf(size.width / MAP_W, size.height / MAP_H)
        minScale = coverScale
        maxScale = 1.5f
        if (!viewInitialized) {
            scale = coverScale.coerceIn(minScale, maxScale)
            offset = Offset(
                (size.width - MAP_W * scale) / 2f,
                (size.height - MAP_H * scale) / 2f,
            )
            viewInitialized = true
        } else {
            val centerMap = Offset(
                (old.width / 2f - offset.x) / scale,
                (old.height / 2f - offset.y) / scale,
            )
            scale = scale.coerceIn(minScale, maxScale)
            offset = Offset(
                size.width / 2f - centerMap.x * scale,
                size.height / 2f - centerMap.y * scale,
            )
            clampOffset()
        }
    }

    /** 双指缩放 + 拖动 */
    fun transform(centroid: Offset, pan: Offset, zoom: Float) {
        val oldScale = scale
        val newScale = (oldScale * zoom).coerceIn(minScale, maxScale)
        val effectiveZoom = newScale / oldScale
        val anchor = centroid - offset
        offset = centroid - anchor * effectiveZoom + pan
        scale = newScale
        clampOffset()
    }

    /** 双击：放大一档，已到顶则回到全图适配 */
    fun doubleTap(at: Offset) {
        val target = when {
            scale < fitScale * 0.95f -> fitScale
            scale < maxScale -> (scale * 2f).coerceAtMost(maxScale)
            else -> fitScale
        }
        zoomTo(target.coerceIn(minScale, maxScale), at)
    }

    fun zoomTo(targetScale: Float, anchor: Offset) {
        val newScale = targetScale.coerceIn(minScale, maxScale)
        val effectiveZoom = newScale / scale
        val a = anchor - offset
        offset = anchor - a * effectiveZoom
        scale = newScale
        clampOffset()
    }

    /** 定位到逻辑坐标点并放大（搜索结果 / 详情跳转） */
    fun focusOn(px: Double, py: Double, targetScale: Float = 0.5f) {
        if (viewportSize == Size.Zero) return
        scale = targetScale.coerceIn(minScale, maxScale)
        offset = Offset(
            viewportSize.width / 2f - px.toFloat() * scale,
            viewportSize.height / 2f - py.toFloat() * scale,
        )
    }

    private fun clampOffset() {
        val worldW = MAP_W * scale
        val worldH = MAP_H * scale
        val view = viewportSize
        val x = if (worldW <= view.width) (view.width - worldW) / 2f
        else offset.x.coerceIn(view.width - worldW, 0f)
        val y = if (worldH <= view.height) (view.height - worldH) / 2f
        else offset.y.coerceIn(view.height - worldH, 0f)
        offset = Offset(x, y)
    }

    /** 逻辑坐标 -> 屏幕像素 */
    fun toScreen(px: Double, py: Double): IntOffset {
        val sx = offset.x + px.toFloat() * scale
        val sy = offset.y + py.toFloat() * scale
        return IntOffset(sx.roundToInt(), sy.roundToInt())
    }

    /** 屏幕像素 -> 逻辑坐标（钳制在地图范围内） */
    fun toMapPoint(screen: Offset): Offset {
        val mx = ((screen.x - offset.x) / scale).toDouble().coerceIn(0.0, 24000.0)
        val my = ((screen.y - offset.y) / scale).toDouble().coerceIn(0.0, 20000.0)
        return Offset(mx.toFloat(), my.toFloat())
    }
}
