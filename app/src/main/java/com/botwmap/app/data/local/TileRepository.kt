package com.botwmap.app.data.local

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.ui.graphics.asImageBitmap
import com.botwmap.app.data.model.TilesIdx
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.FileInputStream

/**
 * 瓦片仓库：与原网页版一致的分级加载策略。
 * - z ≤ 3：assets/tiles/{z}_{tx}_{ty}.webp 单文件
 * - z ≥ 4：pack 文件按 [offset, length] 切片（AssetFileDescriptor 随机读）
 * - 降级兜底：目标瓦片未就绪时用已缓存低阶瓦片放大
 * - LRU 缓存（按字节），加载完成后 version +1 触发地图重绘
 *
 * 瓦片 256px；逻辑坐标(24000×20000) ↔ zoom7 瓦片像素偏移 (4384, 6384)。
 */
class TileRepository(private val context: Context) {

    /** 低阶瓦片命中信息：用于放大绘制 */
    data class FallbackTile(
        val bitmap: Bitmap,
        val image: androidx.compose.ui.graphics.ImageBitmap,
        val zoom: Int,
        val tx: Int,
        val ty: Int,
        /** 相对目标级别的层级差 d（低阶瓦片覆盖 2^d × 2^d 个目标瓦片） */
        val levelDiff: Int,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    @Volatile
    var tilesIdx: TilesIdx? = null
        private set

    // LRU：256*256*4 = 256KB/张，64MB ≈ 256 张
    // 驱逐不手动 recycle：绘制中的 bitmap 若被回收会崩溃，交由 GC 释放 native 内存
    private val cache = object : LruCache<String, Bitmap>(64 * 1024 * 1024) {}
    // ImageBitmap 包装缓存：draw 热路径零分配（包装与 Bitmap 共享像素，仅对象头开销）
    private val imageCache = object : LruCache<String, androidx.compose.ui.graphics.ImageBitmap>(
        16 * 1024 * 1024,
    ) {}
    private val inFlight = mutableSetOf<String>()

    private val _version = MutableStateFlow(0)
    /** 每完成一次瓦片加载 +1，地图订阅后重绘 */
    val version: StateFlow<Int> = _version.asStateFlow()

    suspend fun ensureIdx() {
        if (tilesIdx == null) {
            tilesIdx = AssetDataSource(context).loadTilesIdx()
        }
    }

    /** 缓存命中取瓦片（主线程调用，不阻塞） */
    fun get(key: String): Bitmap? = cache.get(key)

    /** 取 ImageBitmap（包装结果缓存，绘制热路径零分配） */
    fun getImage(key: String): androidx.compose.ui.graphics.ImageBitmap? {
        imageCache.get(key)?.let { return it }
        val bmp = cache.get(key) ?: return null
        if (bmp.isRecycled) return null
        val img = bmp.asImageBitmap()
        imageCache.put(key, img)
        return img
    }

    /**
     * 查已缓存的低阶瓦片作为兜底（对应原项目 _findCachedFallback）。
     * @return 命中的低阶瓦片及层级差，最多向上找 4 级
     */
    fun fallback(z: Int, tx: Int, ty: Int): FallbackTile? {
        for (d in 1..4) {
            val lz = z - d
            if (lz < 1) break
            val c = 1 shl d
            val a = tx / c
            val b = ty / c
            val bmp = cache.get("${lz}_${a}_${b}") ?: continue
            if (!bmp.isRecycled) {
                val key = "${lz}_${a}_${b}"
                val img = imageCache.get(key) ?: bmp.asImageBitmap().also { imageCache.put(key, it) }
                return FallbackTile(bmp, img, lz, a, b, d)
            }
        }
        return null
    }

    /** 请求加载瓦片（幂等：缓存命中或加载中直接返回） */
    fun request(z: Int, tx: Int, ty: Int) {
        val key = "${z}_${tx}_${ty}"
        if (cache.get(key) != null) return
        scope.launch {
            mutex.withLock {
                if (inFlight.contains(key) || cache.get(key) != null) return@withLock
                inFlight.add(key)
            }
            try {
                val bytes = readTileBytes(z, tx, ty) ?: return@launch
                val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@launch
                cache.put(key, bmp)
                _version.value = _version.value + 1
            } catch (_: Exception) {
                // 瓦片缺失（如级别边界外）静默忽略
            } finally {
                mutex.withLock { inFlight.remove(key) }
            }
        }
    }

    private fun readTileBytes(z: Int, tx: Int, ty: Int): ByteArray? {
        val key = "${z}_${tx}_${ty}"
        return if (z <= 3) {
            runCatching {
                context.assets.open("tiles/$key.webp").use { it.readBytes() }
            }.getOrNull()
        } else {
            val idx = tilesIdx ?: return null
            val entry = idx.idx[key] ?: return null
            val packName = idx.packs.getOrNull(entry[0]) ?: return null
            readPackSlice(packName, entry[1].toLong(), entry[2])
        }
    }

    /** 从 pack 文件切出 [offset, offset+length) 区间（一段完整的 webp） */
    private fun readPackSlice(packName: String, offset: Long, length: Int): ByteArray? {
        return runCatching {
            val afd = context.assets.openFd("tiles/$packName")
            afd.use { desc ->
                FileInputStream(desc.fileDescriptor).use { fis ->
                    fis.channel.position(desc.startOffset + offset)
                    val buf = ByteArray(length)
                    var read = 0
                    while (read < length) {
                        val n = fis.read(buf, read, length - read)
                        if (n < 0) break
                        read += n
                    }
                    if (read == length) buf else null
                }
            }
        }.getOrNull()
    }
}
