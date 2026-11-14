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
 * 标记图标缓存：assets/icons/{name} 按需解码（60 个小 PNG，约 8KB/个）。
 * 加载完成 version +1 触发地图重绘。
 * getImage 返回缓存的 ImageBitmap 包装，避免绘制时每帧重复包装分配。
 */
class IconRepository(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val cache = LruCache<String, Bitmap>(8 * 1024 * 1024)
    private val imageCache = LruCache<String, ImageBitmap>(4 * 1024 * 1024)
    private val inFlight = mutableSetOf<String>()

    private val _version = mutableIntStateOf(0)

    /** Compose Snapshot State：draw 读 .value 注册观察，加载完成自动重绘 */
    val version: MutableIntState = _version

    /** 归一化 key：含 '/' 视为完整 assets 路径，否则按 icons/ 前缀 */
    private fun keyOf(name: String): String =
        if (name.contains('/')) name else "icons/$name"

    fun get(name: String): Bitmap? = cache.get(keyOf(name))

    /** 取 ImageBitmap（包装结果缓存，draw 热路径零分配） */
    fun getImage(name: String): ImageBitmap? {
        val key = keyOf(name)
        imageCache.get(key)?.let { return it }
        val bmp = cache.get(key) ?: return null
        if (bmp.isRecycled) return null
        val img = bmp.asImageBitmap()
        imageCache.put(key, img)
        return img
    }

    fun request(name: String) {
        val key = keyOf(name)
        if (key.isBlank() || cache.get(key) != null) return
        synchronized(inFlight) {
            if (!inFlight.add(key)) return
        }
        scope.launch {
            try {
                val bytes = context.assets.open(key).use { it.readBytes() }
                val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@launch
                cache.put(key, bmp)
                _version.intValue = _version.intValue + 1
            } catch (_: Exception) {
                // 图标缺失忽略
            } finally {
                synchronized(inFlight) { inFlight.remove(key) }
            }
        }
    }
}
