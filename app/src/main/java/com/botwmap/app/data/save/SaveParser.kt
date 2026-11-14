package com.botwmap.app.data.save

/**
 * game_data.sav 解析（移植自原项目 save_upload.js，纯本地不联网）。
 *
 * 格式：12 字节头，0x0C 起 [hash u32][value u32] 扁平表。
 * 端序按头部版本字段判定：Switch（Ryujinx）=小端 / Wii U（Cemu）=大端。
 * 校验：版本白名单 + 条目数 ≥10000（拦 caption.sav/option.sav）+ 文件名黑名单。
 */
data class SaveResult(
    /** 命中的完成点位 id（神庙/塔/克洛格/回忆/神兽） */
    val completedIds: Set<String>,
    /** 命中的任务 key（quests.json 的 key） */
    val questKeys: Set<String>,
    /** 讨伐命中的像素坐标 [px,py]（由调用方匹配最近点位） */
    val enemyPts: List<Pair<Float, Float>>,
    val playtimeSec: Int,
    /** 摘要计数：shrine/tower/korok/memory/beast/quest/enemy */
    val counts: Map<String, Int>,
    val platform: String,
)

object SaveParser {

    private val VALID_VERSIONS = intArrayOf(
        0x24E2, 0x24EE, 0x2588, 0x29C0, 0x2A46, 0x3EF8, 0x3EF9, 0x471A, 0x471B, 0x471E, 0x4730,
    )
    private const val H_PLAYTIME = 0x73C29681L
    private const val H_KOROK_CNT = 0x8A94E07AL
    private const val MIN_ENTRIES = 10_000

    class ParseException(message: String) : Exception(message)

    /**
     * @param flags      save_flags.json：[[h, type, id]]
     * @param questFlags quest_flags.json：[[h, questKey]]
     * @param enemyPts   enemy_points.json：[[h, px, py]]
     */
    fun parse(
        bytes: ByteArray,
        fileName: String,
        flags: List<Triple<Long, String, String>>,
        questFlags: List<Pair<Long, String>>,
        enemyPts: List<Triple<Long, Float, Float>>,
    ): SaveResult {
        val lower = fileName.lowercase()
        if (lower.endsWith("caption.sav") || lower.endsWith("option.sav")) {
            throw ParseException("这是 caption.sav / option.sav（存档预览或设置文件），请选择同文件夹下的 game_data.sav")
        }
        if (bytes.size < 1024) throw ParseException("文件太小，可能不是有效的存档")

        val littleEndian = detectEndian(bytes) ?: throw ParseException(
            "这不是旷野之息的存档（版本标识不匹配）——王国之泪等其它游戏的存档无法解析",
        )

        val flagMap = HashMap<Long, Pair<String, String>>(flags.size)
        for ((h, t, id) in flags) flagMap[h] = t to id
        val questMap = HashMap<Long, String>(questFlags.size)
        for ((h, key) in questFlags) questMap[h] = key
        val enemyMap = HashMap<Long, Pair<Float, Float>>(enemyPts.size)
        for ((h, px, py) in enemyPts) enemyMap[h] = px to py

        val completed = HashSet<String>()
        val quests = HashSet<String>()
        val enemies = ArrayList<Pair<Float, Float>>()
        val counts = HashMap<String, Int>()
        var playtime = 0
        var entries = 0

        var o = 0x0C
        while (o + 8 <= bytes.size) {
            val h = u32(bytes, o, littleEndian)
            val v = u32(bytes, o + 4, littleEndian)
            entries++
            when {
                h == H_PLAYTIME -> playtime = v.toInt()
                v != 0L -> {
                    val q = questMap[h]
                    if (q != null) {
                        quests.add(q)
                        counts["quest"] = (counts["quest"] ?: 0) + 1
                    } else {
                        val e = enemyMap[h]
                        if (e != null) {
                            enemies.add(e)
                            counts["enemy"] = (counts["enemy"] ?: 0) + 1
                        } else {
                            val f = flagMap[h]
                            if (f != null) {
                                completed.add(f.second)
                                counts[f.first] = (counts[f.first] ?: 0) + 1
                            }
                        }
                    }
                }
            }
            o += 8
        }

        if (entries < MIN_ENTRIES) {
            throw ParseException("存档条目数不足（$entries），请选择 game_data.sav（主存档文件，不是 caption.sav / option.sav）")
        }

        return SaveResult(
            completedIds = completed,
            questKeys = quests,
            enemyPts = enemies,
            playtimeSec = playtime,
            counts = counts,
            platform = if (littleEndian) "Switch（Ryujinx）" else "Wii U（Cemu）",
        )
    }

    /** 版本白名单判定端序：LE 命中=Switch，BE 命中=Cemu，都无法识别=非本作存档 */
    private fun detectEndian(bytes: ByteArray): Boolean? {
        if (bytes.size < 4) return null
        val vLE = u32(bytes, 0, true)
        val vBE = u32(bytes, 0, false)
        if (vLE in VALID_VERSIONS.map { it.toLong() }) return true
        if (vBE in VALID_VERSIONS.map { it.toLong() }) return false
        return null
    }

    /** 读无符号 u32（返回 Long 0..2^32-1，与 JSON number 同口径） */
    private fun u32(b: ByteArray, o: Int, le: Boolean): Long {
        return if (le) {
            (b[o].toLong() and 0xFF) or
                ((b[o + 1].toLong() and 0xFF) shl 8) or
                ((b[o + 2].toLong() and 0xFF) shl 16) or
                ((b[o + 3].toLong() and 0xFF) shl 24)
        } else {
            ((b[o].toLong() and 0xFF) shl 24) or
                ((b[o + 1].toLong() and 0xFF) shl 16) or
                ((b[o + 2].toLong() and 0xFF) shl 8) or
                (b[o + 3].toLong() and 0xFF)
        }
    }
}
