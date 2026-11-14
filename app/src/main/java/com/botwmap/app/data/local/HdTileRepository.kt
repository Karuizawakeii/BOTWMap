package com.botwmap.app.data.local

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 高清地图块仓库：assets/hd/MapTex_{A-L}-{0-9}.webp（120 块 × 3000²，
 * 全图 36000×30000 = 世界 24000×20000 × 1.5，无边距直接映射）。
 *
 * 双层缓存：
 * - base：s=8 全量 120 块（562KB/块 ≈ 67MB），启动即预载、永不逐出——
 *   首屏打开即完整，缩放变档时始终有底图兜底（画面不空白、不闪烁）
 * - detail：细档（s<8）按需解码，LRU 32MB 自然淘汰、变档不清空——
 *   预算保证「视口所需集合」驻留，旧档残留由 LRU 优先逐出
 */
class HdTileRepository(private val context: Context) {

    companion object {
        const val COLS = 12
        const val ROWS = 10
        const val SRC = 3000 // 源块边长 px
        const val WORLD_PER_BLOCK = 2000f // 每块覆盖世界单位
        const val HD_SCALE = 1.5f // 高清 px = 世界 × 1.5
        const val BASE_SAMPLE = 8 // 底图档（全量预载）
        private const val DETAIL_BUDGET = 32L * 1024 * 1024
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // base：固定 120 条，HashMap 即可（同步保护）
    private val baseCache = HashMap<String, ImageBitmap>()

    // detail：细档 LRU（按字节）
    private val detailCache = object : LruCache<String, ImageBitmap>(DETAIL_BUDGET.toInt()) {
        override fun sizeOf(key: String, value: ImageBitmap): Int {
            val s = key.substringAfterLast(',').toIntOrNull() ?: BASE_SAMPLE
            val px = SRC / s
            return px * px * 4
        }
    }

    private val inFlight = mutableSetOf<String>()

    @Volatile
    private var preloaded = false

    private val _version = mutableIntStateOf(0)

    /**
     * 解码完成计数。必须是 Compose Snapshot State——draw 阶段读 .value
     * 才能注册观察、解码完成自动失效重绘。
     */
    val version: MutableIntState = _version

    init {
        preload()
    }

    /** 启动预载全量底图（后台顺序解码，约 2-4s 完成，逐块触发重绘） */
    fun preload() {
        if (preloaded) return
        preloaded = true
        scope.launch {
            for (r in 0 until ROWS) {
                for (c in 0 until COLS) {
                    val key = key(c, r, BASE_SAMPLE)
                    synchronized(baseCache) {
                        if (baseCache.containsKey(key)) continue
                    }
                    try {
                        val img = decode(c, r, BASE_SAMPLE) ?: continue
                        synchronized(baseCache) { baseCache[key] = img }
                        _version.intValue = _version.intValue + 1
                    } catch (_: Exception) {
                        // 单块失败跳过
                    }
                }
            }
        }
    }

    fun getImage(col: Int, row: Int, sample: Int): ImageBitmap? {
        val key = key(col, row, sample)
        if (sample >= BASE_SAMPLE) {
            synchronized(baseCache) { return baseCache[key] }
        }
        return detailCache.get(key)
    }

    fun request(col: Int, row: Int, sample: Int) {
        if (col !in 0 until COLS || row !in 0 until ROWS) return
        if (getImage(col, row, sample) != null) return
        val key = key(col, row, sample)
        synchronized(inFlight) {
            if (!inFlight.add(key)) return
        }
        scope.launch {
            try {
                val img = decode(col, row, sample) ?: return@launch
                if (sample >= BASE_SAMPLE) {
                    synchronized(baseCache) { baseCache[key] = img }
                } else {
                    detailCache.put(key, img)
                }
                _version.intValue = _version.intValue + 1
            } catch (_: Exception) {
                // 块缺失忽略
            } finally {
                synchronized(inFlight) { inFlight.remove(key) }
            }
        }
    }

    /**
     * detail 档位（纯计算，不清缓存）：清晰档与 32MB 预算取 max。
     * fit 全图时返回 BASE_SAMPLE=8 → 直接命中 base，无 detail 请求。
     */
    fun resolveSample(blocks: Long, clear: Int): Int {
        val b = blocks.coerceAtLeast(1)
        val budget = DETAIL_BUDGET * 4 / 5
        var s = 1
        while (s < BASE_SAMPLE) {
            val px = SRC / s
            if (px.toLong() * px * 4 * b <= budget) break
            s *= 2
        }
        return maxOf(s, clear)
    }

    private fun decode(col: Int, row: Int, sample: Int): ImageBitmap? {
        val name = "hd/MapTex_${'A' + col}-$row.webp"
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample.coerceIn(1, BASE_SAMPLE)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val bmp = context.assets.open(name).use { input ->
            BitmapFactory.decodeStream(input, null, opts)
        } ?: return null
        return bmp.asImageBitmap()
    }

    private fun key(col: Int, row: Int, sample: Int) = "$col,$row,$sample"
}
