package com.botwmap.app.data.save

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SaveParserTest {

    // 与 assets 数据表首条对应
    private val flags = listOf(Triple(2906973L, "korok", "Seed896"))
    private val quests = listOf(3602154444L to "side_135")
    private val enemies = listOf(Triple(23395861L, 8208f, 14089.4f))

    /** 构造合成 game_data.sav：version 头 + 12 字节头 + 条目表 */
    private fun buildSav(littleEndian: Boolean, extra: List<Pair<Long, Long>>, entries: Int): ByteArray {
        val bytes = ByteArray(12 + entries * 8)
        val version = 0x471E
        if (littleEndian) {
            // 4 字节小端：1E 47 00 00
            bytes[0] = (version and 0xFF).toByte()
            bytes[1] = ((version shr 8) and 0xFF).toByte()
        } else {
            // 4 字节大端：00 00 47 1E
            bytes[2] = ((version shr 8) and 0xFF).toByte()
            bytes[3] = (version and 0xFF).toByte()
        }
        var o = 12
        for ((h, v) in extra) {
            writeU32(bytes, o, h, littleEndian)
            writeU32(bytes, o + 4, v, littleEndian)
            o += 8
        }
        return bytes
    }

    private fun writeU32(b: ByteArray, o: Int, v: Long, le: Boolean) {
        if (le) {
            b[o] = (v and 0xFF).toByte()
            b[o + 1] = ((v shr 8) and 0xFF).toByte()
            b[o + 2] = ((v shr 16) and 0xFF).toByte()
            b[o + 3] = ((v shr 24) and 0xFF).toByte()
        } else {
            b[o] = ((v shr 24) and 0xFF).toByte()
            b[o + 1] = ((v shr 16) and 0xFF).toByte()
            b[o + 2] = ((v shr 8) and 0xFF).toByte()
            b[o + 3] = (v and 0xFF).toByte()
        }
    }

    @Test
    fun parseLittleEndianHitAllTables() {
        val bytes = buildSav(
            littleEndian = true,
            extra = listOf(
                0x73C29681L to 36000L,          // PlayTime 10h
                2906973L to 1L,                 // korok Seed896
                3602154444L to 1L,              // quest side_135
                23395861L to 1L,                // enemy 讨伐点
            ),
            entries = 10_001,
        )
        val r = SaveParser.parse(bytes, "game_data.sav", flags, quests, enemies)
        assertTrue("Seed896 应命中", r.completedIds.contains("Seed896"))
        assertTrue("side_135 应命中", r.questKeys.contains("side_135"))
        assertEquals(1, r.enemyPts.size)
        assertEquals(8208f, r.enemyPts[0].first, 0.01f)
        assertEquals(36000, r.playtimeSec)
        assertEquals(1, r.counts["korok"])
        assertEquals(1, r.counts["quest"])
        assertEquals("Switch（Ryujinx）", r.platform)
    }

    @Test
    fun parseBigEndianCemu() {
        val bytes = buildSav(
            littleEndian = false,
            extra = listOf(2906973L to 1L),
            entries = 10_001,
        )
        val r = SaveParser.parse(bytes, "game_data.sav", flags, quests, enemies)
        assertTrue(r.completedIds.contains("Seed896"))
        assertEquals("Wii U（Cemu）", r.platform)
    }

    @Test(expected = SaveParser.ParseException::class)
    fun rejectCaptionSav() {
        val bytes = buildSav(true, emptyList(), 10_001)
        SaveParser.parse(bytes, "caption.sav", flags, quests, enemies)
    }

    @Test(expected = SaveParser.ParseException::class)
    fun rejectBadVersion() {
        val bytes = ByteArray(12 + 10_001 * 8) // version = 0
        SaveParser.parse(bytes, "game_data.sav", flags, quests, enemies)
    }

    @Test(expected = SaveParser.ParseException::class)
    fun rejectTooFewEntries() {
        val bytes = buildSav(true, emptyList(), 189)
        SaveParser.parse(bytes, "game_data.sav", flags, quests, enemies)
    }
}
