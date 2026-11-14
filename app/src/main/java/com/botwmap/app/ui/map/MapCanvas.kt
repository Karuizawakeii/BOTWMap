package com.botwmap.app.ui.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.botwmap.app.data.local.HdTileRepository
import com.botwmap.app.data.local.IconRepository
import androidx.compose.ui.graphics.ImageBitmap
import com.botwmap.app.data.model.CustomMarker
import com.botwmap.app.data.model.DragonPathItem
import com.botwmap.app.data.model.MapPoint
import com.botwmap.app.data.model.RegionLabel
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 地图容器：真实瓦片渲染（分级加载 + 降级兜底）+ 图标标记 + 地名标签。
 * 与原网页版 _drawTiles / _findCachedFallback 逻辑对齐。
 */
@Composable
fun MapCanvas(
    points: List<MapPoint>,
    customMarkers: List<CustomMarker>,
    completedIds: Set<String>,
    selectedId: String?,
    selectedCustomId: String?,
    regions: List<RegionLabel>,
    showRegions: Boolean = true,
    materialPoints: List<MaterialPt> = emptyList(),
    dragonPaths: List<DragonPathItem> = emptyList(),
    korokPath: List<Pair<Float, Float>> = emptyList(),
    showPaths: Boolean = true,
    camera: MapCameraState,
    tileRepo: com.botwmap.app.data.local.HdTileRepository,
    iconRepo: IconRepository,
    modifier: Modifier = Modifier,
    onTapPoint: (MapPoint) -> Unit,
    onTapCustom: (CustomMarker) -> Unit,
    onTapEmpty: () -> Unit,
    onLongPress: (Float, Float) -> Unit,
    /** 命中材料刷点：(材料id, 逻辑px, 逻辑py) */
    onTapMaterial: (Int, Float, Float) -> Unit = { _, _, _ -> },
) {
    // 瓦片/图标 version 在 draw 阶段读取（.value）：加载完成只触发重绘，不重组 composition
    val textMeasurer = rememberTextMeasurer()

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { camera.onViewportSize(Size(it.width.toFloat(), it.height.toFloat())) }
            .pointerInput(Unit) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    camera.transform(centroid, pan, zoom)
                }
            }
            .pointerInput(points, customMarkers, materialPoints) {
                detectTapGestures(
                    onTap = { screenPos ->
                        // 三类目标统一取「各自阈值内最近者」——点谁开谁，
                        // 避免常规点位固定优先抢走材料/自定义标记的点击
                        var bestD2 = Float.MAX_VALUE
                        var bestPoint: MapPoint? = null
                        var bestCustom: CustomMarker? = null
                        var bestMat: MaterialPt? = null

                        for (p in points) {
                            val pos = camera.toScreen(p.px, p.py)
                            val dx = pos.x - screenPos.x
                            val dy = pos.y - screenPos.y
                            val d2 = dx * dx + dy * dy
                            if (d2 < 48f * 48f && d2 < bestD2) {
                                bestD2 = d2
                                bestPoint = p
                                bestCustom = null
                                bestMat = null
                            }
                        }
                        for (m in customMarkers) {
                            val pos = camera.toScreen(m.px, m.py)
                            val dx = pos.x - screenPos.x
                            val dy = pos.y - screenPos.y
                            val d2 = dx * dx + dy * dy
                            if (d2 < 44f * 44f && d2 < bestD2) {
                                bestD2 = d2
                                bestPoint = null
                                bestCustom = m
                                bestMat = null
                            }
                        }
                        for (pt in materialPoints) {
                            val pos = camera.toScreen(pt.px.toDouble(), pt.py.toDouble())
                            val dx = pos.x - screenPos.x
                            val dy = pos.y - screenPos.y
                            val d2 = dx * dx + dy * dy
                            if (d2 < 42f * 42f && d2 < bestD2) {
                                bestD2 = d2
                                bestPoint = null
                                bestCustom = null
                                bestMat = pt
                            }
                        }

                        when {
                            bestPoint != null -> onTapPoint(bestPoint)
                            bestCustom != null -> onTapCustom(bestCustom)
                            bestMat != null -> onTapMaterial(bestMat.matId, bestMat.px, bestMat.py)
                            else -> onTapEmpty()
                        }
                    },
                    onLongPress = { screenPos ->
                        val mapPt = camera.toMapPoint(screenPos)
                        onLongPress(mapPt.x, mapPt.y)
                    },
                    onDoubleTap = { camera.doubleTap(it) },
                )
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            // draw 阶段读 version：缓存更新只失效重绘，不触发 composition 重组
            @Suppress("UNUSED_EXPRESSION") tileRepo.version.value
            @Suppress("UNUSED_EXPRESSION") iconRepo.version.value

            drawHdLayer(camera, tileRepo)
            if (showRegions) {
                drawRegionLabels(camera, regions, textMeasurer, camera.fitScale)
            }
            if (showPaths && dragonPaths.isNotEmpty()) {
                drawDragonPaths(camera, dragonPaths)
            }
            if (korokPath.size >= 2) {
                drawPolyline(camera, korokPath, Color(0xFF66BB6A), strokeWidth = 5f)
            }
            if (materialPoints.isNotEmpty()) {
                drawMaterialPoints(camera, materialPoints, iconRepo)
            }
            drawMarkers(camera, points, completedIds, selectedId, iconRepo)
            drawCustomMarkers(camera, customMarkers, selectedCustomId)
        }
    }
}

/**
 * 高清地图层：120 块 3000² WebP（世界 ×1.5 直接映射，无边距）。
 * - 按视口取块，采样率自适应缩放（源≈屏幕 1:1）
 * - 阶梯兜底：先画已有更粗采样，再请求目标采样
 */
private fun DrawScope.drawHdLayer(camera: MapCameraState, repo: HdTileRepository) {
    val pad = 32f
    val scale = camera.scale
    val view = camera.offset
    val vw = size.width
    val vh = size.height

    // 视口世界范围
    val wx0 = (-pad - view.x) / scale
    val wy0 = (-pad - view.y) / scale
    val wx1 = (vw + pad - view.x) / scale
    val wy1 = (vh + pad - view.y) / scale
    if (wx0 >= wx1 || wy0 >= wy1) return

    val c0 = (floor(wx0 / HdTileRepository.WORLD_PER_BLOCK).toInt()).coerceIn(0, HdTileRepository.COLS - 1)
    val r0 = (floor(wy0 / HdTileRepository.WORLD_PER_BLOCK).toInt()).coerceIn(0, HdTileRepository.ROWS - 1)
    val c1 = (floor(wx1 / HdTileRepository.WORLD_PER_BLOCK).toInt()).coerceIn(0, HdTileRepository.COLS - 1)
    val r1 = (floor(wy1 / HdTileRepository.WORLD_PER_BLOCK).toInt()).coerceIn(0, HdTileRepository.ROWS - 1)

    // 清晰档：源略大于屏幕（1.2×内保留细节）；预算档与滞回由仓库统一解析
    val blockScreen = HdTileRepository.WORLD_PER_BLOCK * scale
    var clear = 1
    while (clear < 8 && HdTileRepository.SRC / (clear * 2) >= blockScreen * 1.2f) {
        clear *= 2
    }
    val blocks = (c1 - c0 + 1).toLong() * (r1 - r0 + 1)
    val sample = repo.resolveSample(blocks, clear)

    var requested = 0
    for (r in r0..r1) {
        for (c in c0..c1) {
            val sx = view.x + c * HdTileRepository.WORLD_PER_BLOCK * scale
            val sy = view.y + r * HdTileRepository.WORLD_PER_BLOCK * scale
            if (sx > vw || sy > vh || sx + blockScreen < 0 || sy + blockScreen < 0) continue
            val dst = (blockScreen + 1f).toInt().coerceAtLeast(1)

            // detail 优先，base（预载底图）兜底——画面永远完整，缩放不清空
            val detail = repo.getImage(c, r, sample)
            val img = detail ?: repo.getImage(c, r, HdTileRepository.BASE_SAMPLE)
            if (img != null) {
                drawImage(
                    image = img,
                    dstOffset = IntOffset(sx.roundToInt(), sy.roundToInt()),
                    dstSize = IntSize(dst, dst),
                    filterQuality = FilterQuality.Medium,
                )
            }
            // detail 缺失才请求（sample==8 时 detail 即 base，命中后不再请求）
            if (detail == null && requested < 12) {
                requested++
                repo.request(c, r, sample)
            }
        }
    }
}

/** 地名标签：r1 大区 / r2 地形按缩放分级显示（对齐原项目分级策略） */
private fun DrawScope.drawRegionLabels(
    camera: MapCameraState,
    regions: List<RegionLabel>,
    textMeasurer: TextMeasurer,
    fitScale: Float,
) {
    if (regions.isEmpty()) return
    val g1 = fitScale * 2
    val g2 = fitScale * 4
    val level = when {
        camera.scale < g1 -> "r1"
        camera.scale < g2 -> "r2"
        else -> "r3" // 细地名（367 条，视口裁剪后每帧仅数十条）
    }
    val textColor = Color(0xFFBFAE68)
    val strokeColor = Color(0xFF3F3010)
    val fontSize = when (level) {
        "r1" -> 16.sp
        "r2" -> 13.sp
        else -> 11.sp
    }

    for (region in regions) {
        if (region.lv != level) continue
        val pos = camera.toScreen(region.px[0], region.px[1])
        if (pos.x < -100 || pos.y < -40 || pos.x > size.width + 100 || pos.y > size.height + 40) {
            continue
        }
        val layout = textMeasurer.measure(
            text = region.n,
            style = TextStyle(fontSize = fontSize, textAlign = TextAlign.Center),
        )
        val left = pos.x - layout.size.width / 2
        val top = pos.y - layout.size.height / 2
        // 描边提高可读性
        drawText(textLayoutResult = layout, topLeft = Offset(left + 1.5f, top + 1.5f), color = strokeColor)
        drawText(textLayoutResult = layout, topLeft = Offset(left.toFloat(), top.toFloat()), color = textColor)
    }
}

/** 常规点位标记：图标优先，无图标按类目画形状；完成态加绿环 */
private fun DrawScope.drawMarkers(
    camera: MapCameraState,
    points: List<MapPoint>,
    completedIds: Set<String>,
    selectedId: String?,
    iconRepo: IconRepository,
) {
    val view = size
    for (point in points) {
        val p = camera.toScreen(point.px, point.py)
        val cx = p.x.toFloat()
        val cy = p.y.toFloat()
        if (cx < -48f || cy < -48f || cx > view.width + 48f || cy > view.height + 48f) continue

        val selected = point.id == selectedId
        val completed = point.id in completedIds
        val icon = point.icon.takeIf { it.isNotBlank() }?.let { name ->
            iconRepo.getImage(name) ?: run { iconRepo.request(name); null }
        }

        // 图标屏幕尺寸按 dp 基准（22dp ≈ 66px@3x，选中 27dp），跨密度一致
        val half = (if (selected) 32f else 26f).dp.toPx() / 2f
        if (icon != null) {
            // Compose drawImage（缓存的 ImageBitmap，无每点 Paint/RectF/native 分配）
            val ratio = half * 2f / max(icon.width, icon.height)
            val dw = (icon.width * ratio).roundToInt().coerceAtLeast(1)
            val dh = (icon.height * ratio).roundToInt().coerceAtLeast(1)
            drawImage(
                image = icon,
                dstOffset = IntOffset(
                    (cx - dw / 2f).roundToInt(),
                    (cy - dh / 2f).roundToInt(),
                ),
                dstSize = IntSize(dw, dh),
                filterQuality = FilterQuality.Medium,
            )
        } else {
            // 形状兜底：按类目着色的菱形
            val path = Path().apply {
                moveTo(cx, cy - half)
                lineTo(cx + half, cy)
                lineTo(cx, cy + half)
                lineTo(cx - half, cy)
                close()
            }
            drawPath(path, Color.White)
            drawPath(path, catColor(point.cat), style = Stroke(width = if (selected) 4f else 2.5f))
        }

        if (completed) {
            // 完成徽章：图标右上角绿色对勾（对齐原项目 badge 样式）
            val br = half * 0.55f
            val bx = cx + half * 0.8f
            val by = cy - half * 0.8f
            drawCircle(Color(0xFF4CAF50), br, Offset(bx, by))
            drawCircle(Color.White, br, Offset(bx, by), style = Stroke(width = br * 0.22f))
            val tick = Path().apply {
                moveTo(bx - br * 0.42f, by + br * 0.02f)
                lineTo(bx - br * 0.1f, by + br * 0.36f)
                lineTo(bx + br * 0.46f, by - br * 0.34f)
            }
            drawPath(
                tick,
                Color.White,
                style = Stroke(
                    width = br * 0.3f,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                ),
            )
        }
    }
}

/** 自定义标记：图钉形（圆头 + 针脚） */
private fun DrawScope.drawCustomMarkers(
    camera: MapCameraState,
    markers: List<CustomMarker>,
    selectedId: String?,
) {
    val view = size
    for (marker in markers) {
        val p = camera.toScreen(marker.px, marker.py)
        val cx = p.x.toFloat()
        val cy = p.y.toFloat()
        if (cx < -40f || cy < -40f || cx > view.width + 40f || cy > view.height + 40f) continue

        val selected = marker.id == selectedId
        val r = if (selected) 16f else 12f
        val tail = Path().apply {
            moveTo(cx - r * 0.5f, cy + r * 0.5f)
            lineTo(cx, cy + r * 1.6f)
            lineTo(cx + r * 0.5f, cy + r * 0.5f)
            close()
        }
        drawPath(tail, Color(0xFFE65100))
        drawCircle(Color(0xFFFF8F00), r, Offset(cx, cy))
        drawCircle(Color.White, r, Offset(cx, cy), style = Stroke(width = 3f))
        drawCircle(Color.White, r * 0.35f, Offset(cx, cy))
    }
}

/** 龙飞行轨迹：多段折线（半透明）+ 刷新点圆点 */
private fun DrawScope.drawDragonPaths(
    camera: MapCameraState,
    dragons: List<DragonPathItem>,
) {
    val view = size
    val colorMap = mapOf(
        "farosh" to Color(0xFFFDD835), // 费罗龙 黄
        "dinraal" to Color(0xFFEF5350), // 奥尔龙 红
        "naydra" to Color(0xFF42A5F5), // 聂尔龙 蓝
    )
    for (dragon in dragons) {
        val color = colorMap[dragon.color] ?: Color(0xFFB0BEC5)
        for (segment in dragon.segments) {
            if (segment.size < 2) continue
            val path = Path()
            var started = false
            var visible = false
            for (pt in segment) {
                val x = pt.getOrNull(0)?.toFloat() ?: continue
                val y = pt.getOrNull(1)?.toFloat() ?: continue
                val p = camera.toScreen(x.toDouble(), y.toDouble())
                if (!started) {
                    path.moveTo(p.x.toFloat(), p.y.toFloat())
                    started = true
                } else {
                    path.lineTo(p.x.toFloat(), p.y.toFloat())
                }
                if (p.x >= -80 && p.y >= -80 && p.x <= view.width + 80 && p.y <= view.height + 80) {
                    visible = true
                }
            }
            if (visible) {
                drawPath(
                    path,
                    color = color.copy(alpha = 0.55f),
                    style = Stroke(
                        width = 5f,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                    ),
                )
            }
        }
        // 刷新点（start）
        val sx = dragon.start.getOrNull(0)
        val sy = dragon.start.getOrNull(1)
        if (sx != null && sy != null) {
            val p = camera.toScreen(sx, sy)
            val cx = p.x.toFloat()
            val cy = p.y.toFloat()
            if (cx >= -20 && cy >= -20 && cx <= view.width + 20 && cy <= view.height + 20) {
                drawCircle(color.copy(alpha = 0.9f), 9f, Offset(cx, cy))
                drawCircle(Color.White, 9f, Offset(cx, cy), style = Stroke(width = 3f))
            }
        }
    }
}

/** 通用折线（克洛格轨迹等） */
private fun DrawScope.drawPolyline(
    camera: MapCameraState,
    points: List<Pair<Float, Float>>,
    color: Color,
    strokeWidth: Float,
) {
    if (points.size < 2) return
    val view = size
    val path = Path()
    var visible = false
    points.forEachIndexed { index, (x, y) ->
        val p = camera.toScreen(x.toDouble(), y.toDouble())
        if (index == 0) path.moveTo(p.x.toFloat(), p.y.toFloat())
        else path.lineTo(p.x.toFloat(), p.y.toFloat())
        if (p.x >= -80 && p.y >= -80 && p.x <= view.width + 80 && p.y <= view.height + 80) {
            visible = true
        }
    }
    if (visible) {
        drawPath(
            path,
            color = color.copy(alpha = 0.85f),
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}

/** 材料刷点：橙色小圆（选中材料时显示，视口裁剪） */
private fun DrawScope.drawMaterialPoints(
    camera: MapCameraState,
    points: List<MaterialPt>,
    iconRepo: IconRepository,
) {
    val view = size
    val dot = Color(0xFFFF8F00)
    val half = 18f.dp.toPx() / 2f // 材料图鉴图标 18dp
    for (pt in points) {
        val p = camera.toScreen(pt.px.toDouble(), pt.py.toDouble())
        val cx = p.x.toFloat()
        val cy = p.y.toFloat()
        if (cx < -24f || cy < -24f || cx > view.width + 24f || cy > view.height + 24f) continue

        // 材料图鉴图（materials/{entry}.webp），未加载完先画橙点兜底
        val key = "materials/${pt.entry}.webp"
        val img = iconRepo.getImage(key)
        if (img != null) {
            val ratio = half * 2f / max(img.width, img.height)
            val dw = (img.width * ratio).roundToInt().coerceAtLeast(1)
            val dh = (img.height * ratio).roundToInt().coerceAtLeast(1)
            drawImage(
                image = img,
                dstOffset = IntOffset(
                    (cx - dw / 2f).roundToInt(),
                    (cy - dh / 2f).roundToInt(),
                ),
                dstSize = IntSize(dw, dh),
                filterQuality = FilterQuality.Medium,
            )
        } else {
            iconRepo.request(key)
            drawCircle(dot, 6f, Offset(cx, cy))
            drawCircle(Color.White, 6f, Offset(cx, cy), style = Stroke(width = 2f))
        }
    }
}

/** 类目无图标时的兜底色 */
private fun catColor(cat: String): Color = when (cat) {
    "memory" -> Color(0xFF8E24AA)
    "seed" -> Color(0xFF43A047)
    "mainquest", "shrinequest", "sidequest", "objective" -> Color(0xFF1E88E5)
    "beast" -> Color(0xFFD84315)
    "treasure" -> Color(0xFFF9A825)
    else -> Color(0xFF546E7A)
}
