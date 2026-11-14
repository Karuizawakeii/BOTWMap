package com.botwmap.app.ui.detail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.botwmap.app.data.model.CustomMarker
import com.botwmap.app.data.model.KorokItem
import com.botwmap.app.data.model.MapPoint
import com.botwmap.app.data.model.MarkerCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import com.botwmap.app.ui.effect.liquid.buttonSpecularDark
import com.botwmap.app.ui.effect.liquid.buttonSpecularLight
import com.botwmap.app.ui.effect.liquid.liquidGlassShadow
import com.botwmap.app.ui.effect.liquid.rememberLiquidGlassSurfaceSpec
import com.botwmap.app.ui.effect.rememberBlurEffectsEnabled
import com.botwmap.app.ui.navigation.LocalLiquidBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.botwmap.app.data.repository.MapRepository
import org.koin.core.context.KoinContext
import org.koin.compose.koinInject
import com.botwmap.app.ui.effect.liquid.LiquidGlassPanel

/**
 * 点位详情底部卡片：常规点位 / 自定义标记 / 材料刷点共用容器。
 * 克洛格（cat=seed）显示中文解法与截图；材料显示 TOTK 风格详情（图/区域/坐标/用途）。
 */
@Composable
fun PointDetailCard(
    point: MapPoint?,
    custom: CustomMarker?,
    category: MarkerCategory?,
    korok: KorokItem?,
    material: com.botwmap.app.ui.map.MaterialPointDetail? = null,
    completed: Boolean,
    onToggleCompleted: (String) -> Unit,
    onDeleteCustom: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val visible = point != null || custom != null || material != null
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it / 2 }) + fadeOut(),
    ) {
        LiquidGlassPanel(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            shape = RoundedCornerShape(24.dp),
        ) {
            if (point != null) {
                PointContent(
                    point = point,
                    category = category,
                    korok = korok,
                    completed = completed,
                    onToggleCompleted = onToggleCompleted,
                    onDismiss = onDismiss,
                )
            } else if (material != null) {
                MaterialContent(detail = material, onDismiss = onDismiss)
            } else if (custom != null) {
                CustomContent(
                    custom = custom,
                    onDelete = { onDeleteCustom(custom.id) },
                    onDismiss = onDismiss,
                )
            }
        }
    }
}

/** 「标记为已完成」玻璃按钮：与卡片同一套 Matter 玻璃（模糊半透明，非纯色） */
@Composable
private fun GlassToggleButton(
    completed: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val backdrop = LocalLiquidBackdrop.current
    val spec = rememberLiquidGlassSurfaceSpec()
    val shape = RoundedCornerShape(28.dp)
    val blurActive = rememberBlurEffectsEnabled() && backdrop != null
    val repository: MapRepository = koinInject()
    val barBlur by repository.customBarBlur.collectAsStateWithLifecycle(initialValue = 8f)
    val barAlpha by repository.customBarAlpha.collectAsStateWithLifecycle(initialValue = 0.4f)
    val btnModifier = if (blurActive && backdrop != null) {
        modifier.drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = {
                vibrancy()
                blur(barBlur.dp.toPx())
            },
            onDrawSurface = {
                drawRoundRect(
                    color = Color.White.copy(alpha = barAlpha.coerceIn(0f, 0.45f)),
                    cornerRadius = CornerRadius(28.dp.toPx()),
                )
            },
        )
    } else {
        modifier.background(
            if (spec.isDark) Color.White.copy(alpha = 0.14f)
            else spec.fallbackSurfaceColor,
            shape,
        )
    }
    Box(
        btnModifier
            .clip(shape)
            .height(48.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (completed) Icons.Filled.CheckCircle
                else Icons.Outlined.RadioButtonUnchecked,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(if (completed) "已完成 · 点击取消" else "标记为已完成")
        }
    }
}

@Composable
private fun PointContent(
    point: MapPoint,
    category: MarkerCategory?,
    korok: KorokItem?,
    completed: Boolean,
    onToggleCompleted: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var showKorokImage by remember { mutableStateOf(false) }

    Column(Modifier.padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.Place,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = point.name,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (point.en.isNotBlank()) {
                    Text(
                        text = point.en,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Filled.Close, contentDescription = "关闭")
            }
        }

        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            category?.let {
                AssistChip(onClick = {}, label = { Text(it.cn) })
            }
            if (point.region.isNotBlank()) {
                AssistChip(onClick = {}, label = { Text(point.region) })
            }
        }

        if (point.hint.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = point.hint,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // 克洛格：中文解法 + 截图
        if (korok != null && korok.hintCn.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = "解法：${korok.hintCn}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            if (korok.img.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { showKorokImage = true }) {
                    Text("查看解法截图")
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            text = "游戏坐标 (${point.gameX}, ${point.gameZ}) · 地图 (${point.px.toInt()}, ${point.py.toInt()})",
            style = MaterialTheme.typography.labelMedium.liquidGlassShadow(),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
        )

        Spacer(Modifier.height(16.dp))

        GlassToggleButton(
            completed = completed,
            onClick = { onToggleCompleted(point.id) },
            modifier = Modifier.fillMaxWidth(),
        )
    }

    val korokImg = korok?.img.orEmpty()
    if (showKorokImage && korokImg.isNotBlank()) {
        AssetViewerDialog(path = "korok/$korokImg", title = "克洛格解法", onDismiss = { showKorokImage = false })
    }
}

/** 材料刷点详情（TOTK 风格：图鉴图 + 区域/坐标/位置/用途/编号/图层） */
@Composable
private fun MaterialContent(
    detail: com.botwmap.app.ui.map.MaterialPointDetail,
    onDismiss: () -> Unit,
) {
    val m = detail.material
    var showImage by remember { mutableStateOf(false) }

    Column(Modifier.padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AssistChip(onClick = {}, label = { Text(m.cat) })
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onDismiss) {
                Icon(Icons.Filled.Close, contentDescription = "关闭")
            }
        }

        Spacer(Modifier.height(4.dp))
        Text(m.cn, style = MaterialTheme.typography.titleLarge.liquidGlassShadow())
        if (m.en.isNotBlank()) {
            Text(
                text = m.en,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(12.dp))
        Box(
            Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            com.botwmap.app.ui.components.AssetImage(
                path = "materials/${m.entry}.webp",
                contentDescription = m.cn,
                modifier = Modifier
                    .size(140.dp)
                    .clickable { showImage = true },
                fallback = {
                    Box(Modifier.size(140.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(28.dp))
                    }
                },
            )
        }

        Spacer(Modifier.height(12.dp))
        DetailRow("区　域", detail.region)
        DetailRow("坐　标", "X ${detail.gameX} · Z ${detail.gameZ}")
        DetailRow("位　置", detail.posText)
        DetailRow("用　途", detail.usage, valueColor = Color(0xFFE8B04A))
        DetailRow("编　号", m.entry)
        DetailRow(
            label = "图　层",
            value = if (detail.layerOn) "已开启" else "未开启",
            valueColor = if (detail.layerOn) Color(0xFF7DFFA0)
            else MaterialTheme.colorScheme.outline,
        )
    }

    if (showImage) {
        AssetViewerDialog(
            path = "materials/${m.entry}.webp",
            title = m.cn,
            onDismiss = { showImage = false },
        )
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
    valueColor: Color = Color.Unspecified,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(72.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = if (valueColor == Color.Unspecified) {
                MaterialTheme.colorScheme.onSurface
            } else {
                valueColor
            },
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * 通用图片查看器（全屏）：
 * 初始按 min(屏宽92%, 原图×3) 显示保清晰；双指缩放（以手势中心为锚）、拖动、双击复位。
 */
@Composable
private fun AssetViewerDialog(path: String, title: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val bitmap by produceState<android.graphics.Bitmap?>(initialValue = null, path) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.assets.open(path).use {
                    android.graphics.BitmapFactory.decodeStream(it)
                }
            }.getOrNull()
        }
    }

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    val screenWidthDp = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp

    Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.96f))
                .pointerInput(Unit) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        val newScale = (scale * zoom).coerceIn(1f, 10f)
                        val e = newScale / scale
                        // graphicsLayer pivot 为图片中心（居中=屏幕中心 C）：
                        // t' = t·e + (a − C)·(1 − e) + pan（以手势中心 a 为缩放锚）
                        val c = androidx.compose.ui.geometry.Offset(
                            size.width / 2f,
                            size.height / 2f,
                        )
                        offset = offset * e + (centroid - c) * (1f - e) + pan
                        scale = newScale
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = {
                            scale = 1f
                            offset = androidx.compose.ui.geometry.Offset.Zero
                        },
                        onTap = { onDismiss() },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            if (bitmap != null) {
                val bmp = bitmap!!
                // 初始显示：原图 3 物理像素倍封顶（避免小图被拉到全屏糊掉），屏宽 92% 封顶
                val nativeWdp = bmp.width * 3f / density
                val displayW = minOf(screenWidthDp * 0.92f, nativeWdp)
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .width(displayW.dp)
                        .aspectRatio(bmp.width.toFloat() / bmp.height.toFloat())
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = offset.x
                            translationY = offset.y
                        },
                    contentScale = ContentScale.Fit,
                )
            } else {
                CircularProgressIndicator(
                    Modifier.size(48.dp),
                    color = Color.White,
                )
            }

            // 顶部操作栏
            Row(
                Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 8.dp, vertical = 44.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "关闭",
                        tint = Color.White,
                    )
                }
            }

            // 底部操作提示
            Text(
                text = "双指缩放 · 拖动平移 · 双击复位 · 轻触关闭",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.7f),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp),
            )
        }
    }
}

@Composable
private fun CustomContent(
    custom: CustomMarker,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    Column(Modifier.padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.Place,
                contentDescription = null,
                tint = Color(0xFFFF8F00),
            )
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(custom.title, style = MaterialTheme.typography.titleLarge.liquidGlassShadow())
                if (custom.note.isNotBlank()) {
                    Text(
                        text = custom.note,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Filled.Close, contentDescription = "关闭")
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = "自定义标记 · 地图 (${custom.px.toInt()}, ${custom.py.toInt()})",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.outline,
        )
    }
}
