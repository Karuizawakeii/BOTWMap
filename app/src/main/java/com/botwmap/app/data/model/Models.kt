package com.botwmap.app.data.model

import kotlinx.serialization.Serializable

/** 图层分组（面板折叠展示）；JSON 中以枚举 name 存储 */
@Serializable
enum class LayerGroup(val displayZh: String) {
    EXPLORE("探索收集"),
    FACILITY("设施"),
    ENEMY("敌人"),
    QUEST("任务"),
    LOOT("宝箱"),
    GEAR("装备"),
    MATERIAL("材料"),
}

/** 类目定义（原项目 27 类） */
@Serializable
data class MarkerCategory(
    val key: String,
    val cn: String,
    val en: String,
    val group: LayerGroup,
    /** assets/icons/ 下的图标文件名（如 icons_01.png），可为空 */
    val icon: String = "",
)

/**
 * 地图点位。坐标为原项目逻辑坐标系（mapW×mapH = 24000×20000），
 * 与瓦片坐标偏移 (4384, 6384) 配套，保证标记与底图对齐。
 */
@Serializable
data class MapPoint(
    val id: String,
    val name: String,
    val en: String = "",
    /** 类目 key，见 [MarkerCategory.key] */
    val cat: String,
    val px: Double,
    val py: Double,
    val region: String = "",
    val hint: String = "",
    /** assets/icons/ 下的图标文件名，可为空 */
    val icon: String = "",
) {
    /** 归一化坐标 0..1（搜索/分享用） */
    val nx: Double get() = px / MAP_W
    val ny: Double get() = py / MAP_H

    /** 游戏内坐标：gameX = (px-12000)/2, gameZ = (py-10000)/2 */
    val gameX: Int get() = ((px - 12000.0) / 2).toInt()
    val gameZ: Int get() = ((py - 10000.0) / 2).toInt()

    companion object {
        const val MAP_W = 24000.0
        const val MAP_H = 20000.0
    }
}

/** assets/data/markers.json 根结构 */
@Serializable
data class MarkersFile(
    val mapW: Int,
    val mapH: Int,
    val version: String = "",
    val categories: List<MarkerCategory>,
    val markers: List<MapPoint>,
)

/** 地名标签（regions.json）：lv r1 大区 / r2 地形 / r3 细地名 */
@Serializable
data class RegionLabel(
    val n: String,
    val en: String = "",
    val px: List<Double>,
    val lv: String,
)

/** 瓦片包索引（tiles_idx.json）：key "z_tx_ty" -> [packIdx, offset, length] */
@Serializable
data class TilesIdx(
    val packs: List<String>,
    val idx: Map<String, List<Int>>,
)

/** 克洛格条目（koroks.json） */
@Serializable
data class KorokItem(
    val id: String,
    val no: Int,
    val hintCn: String = "",
    val hintEn: String = "",
    /** assets/korok/ 下截图文件名 */
    val img: String = "",
)

/** 材料条目（materials.json） */
@Serializable
data class MaterialItem(
    val id: Int,
    val cat: String,
    val cn: String,
    val en: String = "",
    val entry: String = "",
    /** 固定刷点数 */
    val count: Int = 0,
)

@Serializable
data class MaterialsFile(
    val total: Int = 0,
    val materials: List<MaterialItem> = emptyList(),
    /** 扁平刷点 [id,px,py, id,px,py, ...]（13524 点），Repository 分组 */
    val pointsFlat: List<Int> = emptyList(),
)

/** 龙飞行轨迹（dragon_paths.json）：segments 为多段折线 */
@Serializable
data class DragonPathItem(
    val name: String,
    val color: String,
    val start: List<Double> = emptyList(),
    val segments: List<List<List<Double>>> = emptyList(),
)

@Serializable
data class DragonPathsFile(
    val dragons: List<DragonPathItem> = emptyList(),
)

/** 克洛格移动轨迹（korok_paths.json）：97 条，key = 克洛格 id */
@Serializable
data class KorokPathItem(
    val id: String,
    val no: Int = 0,
    val type: String = "",
    val locCn: String = "",
    val path: List<List<Double>> = emptyList(),
)

@Serializable
data class KorokPathsFile(
    val total: Int = 0,
    val paths: List<KorokPathItem> = emptyList(),
)

/** 防具条目（armors.json） */
@Serializable
data class ArmorItem(
    val id: String,
    val name: String,
    val en: String = "",
    val part: String = "",
    val def: Int = 0,
    val set: String = "",
)

@Serializable
data class ArmorsFile(
    val total: Int = 0,
    val items: List<ArmorItem> = emptyList(),
)

/** 武器条目（weapons.json） */
@Serializable
data class WeaponItem(
    val id: String,
    val name: String,
    val en: String = "",
    val type: String = "",
    val atk: Int = 0,
)

@Serializable
data class WeaponsFile(
    val total: Int = 0,
    val items: List<WeaponItem> = emptyList(),
)

/** 任务条目（quests.json）：cat = main / shrine / side / dlc */
@Serializable
data class QuestItem(
    val key: String,
    val no: Int = 0,
    val groupTotal: Int = 0,
    val cat: String,
    val name: String,
    val en: String = "",
    val region: String = "",
    val npc: String = "",
    val desc: String = "",
    /** 流程点位（逻辑坐标 24000×20000，可多个） */
    val pts: List<List<Double>> = emptyList(),
) {
    val firstPt: Pair<Double, Double>?
        get() = pts.firstOrNull()?.let { (it.getOrNull(0) ?: 0.0) to (it.getOrNull(1) ?: 0.0) }
}

@Serializable
data class QuestsFile(
    val total: Int = 0,
    val quests: List<QuestItem> = emptyList(),
)

/** 用户自定义标记 */
@Serializable
data class CustomMarker(
    val id: String,
    val title: String,
    val note: String = "",
    val px: Double,
    val py: Double,
    val createdAt: Long,
)

/**
 * 分享码负载：图层 + 完成进度 + 自定义标记 + 显示开关。
 * JSON → Base64 URL-safe 即为可复制的分享码。
 */
@Serializable
data class SharePayload(
    val v: Int = 1,
    val layers: Set<String> = emptySet(),
    val points: Set<String> = emptySet(),
    val quests: Set<String> = emptySet(),
    val custom: List<CustomMarker> = emptyList(),
    val showCompleted: Boolean = true,
    val showRegions: Boolean = true,
    val showPaths: Boolean = true,
)
