package com.botwmap.app.data.local

import android.content.Context
import com.botwmap.app.core.network.HttpClientFactory
import com.botwmap.app.data.model.ArmorItem
import com.botwmap.app.data.model.ArmorsFile
import com.botwmap.app.data.model.DragonPathItem
import com.botwmap.app.data.model.DragonPathsFile
import com.botwmap.app.data.model.KorokItem
import com.botwmap.app.data.model.KorokPathItem
import com.botwmap.app.data.model.KorokPathsFile
import com.botwmap.app.data.model.MarkersFile
import com.botwmap.app.data.model.MaterialItem
import com.botwmap.app.data.model.MaterialsFile
import com.botwmap.app.data.model.QuestItem
import com.botwmap.app.data.model.QuestsFile
import com.botwmap.app.data.model.RegionLabel
import com.botwmap.app.data.model.TilesIdx
import com.botwmap.app.data.model.WeaponItem
import com.botwmap.app.data.model.WeaponsFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.float

/**
 * assets 数据源：读取转换后的 JSON（markers / tiles_idx / regions / koroks）。
 * 全部离线，IO 在 Dispatchers.IO 执行。
 */
class AssetDataSource(private val context: Context) {

    suspend fun loadMarkers(): MarkersFile = withContext(Dispatchers.IO) {
        context.assets.open("data/markers.json").use {
            HttpClientFactory.json.decodeFromStream(MarkersFile.serializer(), it)
        }
    }

    suspend fun loadTilesIdx(): TilesIdx = withContext(Dispatchers.IO) {
        context.assets.open("data/tiles_idx.json").use {
            HttpClientFactory.json.decodeFromStream(TilesIdx.serializer(), it)
        }
    }

    suspend fun loadRegions(): List<RegionLabel> = withContext(Dispatchers.IO) {
        context.assets.open("data/regions.json").use {
            HttpClientFactory.json.decodeFromStream(ListSerializer(RegionLabel.serializer()), it)
        }
    }

    suspend fun loadKoroks(): List<KorokItem> = withContext(Dispatchers.IO) {
        context.assets.open("data/koroks.json").use {
            val root = HttpClientFactory.json.decodeFromStream(JsonObject.serializer(), it)
            val items = root["items"]?.toString() ?: "[]"
            HttpClientFactory.json.decodeFromStream(
                ListSerializer(KorokItem.serializer()),
                items.byteInputStream(),
            )
        }
    }

    suspend fun loadMaterials(): MaterialsFile = withContext(Dispatchers.IO) {
        context.assets.open("data/materials.json").use {
            HttpClientFactory.json.decodeFromStream(MaterialsFile.serializer(), it)
        }
    }

    suspend fun loadDragonPaths(): List<DragonPathItem> = withContext(Dispatchers.IO) {
        context.assets.open("data/dragon_paths.json").use {
            HttpClientFactory.json.decodeFromStream(DragonPathsFile.serializer(), it).dragons
        }
    }

    suspend fun loadKorokPaths(): List<KorokPathItem> = withContext(Dispatchers.IO) {
        context.assets.open("data/korok_paths.json").use {
            HttpClientFactory.json.decodeFromStream(KorokPathsFile.serializer(), it).paths
        }
    }

    suspend fun loadArmors(): List<ArmorItem> = withContext(Dispatchers.IO) {
        context.assets.open("data/armors.json").use {
            HttpClientFactory.json.decodeFromStream(ArmorsFile.serializer(), it).items
        }
    }

    suspend fun loadWeapons(): List<WeaponItem> = withContext(Dispatchers.IO) {
        context.assets.open("data/weapons.json").use {
            HttpClientFactory.json.decodeFromStream(WeaponsFile.serializer(), it).items
        }
    }

    suspend fun loadQuests(): List<QuestItem> = withContext(Dispatchers.IO) {
        context.assets.open("data/quests.json").use {
            HttpClientFactory.json.decodeFromStream(QuestsFile.serializer(), it).quests
        }
    }

    /** 存档查找表（加载存档用）：save_flags / quest_flags / enemy_points */
    suspend fun loadSaveTables(): SaveTables = withContext(Dispatchers.IO) {
        fun arr(name: String): List<List<kotlinx.serialization.json.JsonElement>> =
            context.assets.open("data/$name").use {
                HttpClientFactory.json.decodeFromStream(
                    kotlinx.serialization.json.JsonArray.serializer(), it,
                )
            }.map { e ->
                (e as kotlinx.serialization.json.JsonArray).map { it }
            }

        val flags = arr("save_flags.json").map {
            Triple(
                it[0].jsonPrimitive.long,
                it[1].jsonPrimitive.content,
                it[2].jsonPrimitive.content,
            )
        }
        val quests = arr("quest_flags.json").map {
            it[0].jsonPrimitive.long to it[1].jsonPrimitive.content
        }
        val enemies = arr("enemy_points.json").map {
            Triple(
                it[0].jsonPrimitive.long,
                it[1].jsonPrimitive.float,
                it[2].jsonPrimitive.float,
            )
        }
        SaveTables(flags, quests, enemies)
    }
}

/** 存档查找表三件套 */
data class SaveTables(
    /** [[h, type, id]] 神庙/塔/克洛格/回忆/神兽 */
    val flags: List<Triple<Long, String, String>>,
    /** [[h, questKey]] 任务完成 */
    val questFlags: List<Pair<Long, String>>,
    /** [[h, px, py]] 讨伐点 */
    val enemyPts: List<Triple<Long, Float, Float>>,
)
