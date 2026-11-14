package com.botwmap.app.data.repository

import com.botwmap.app.core.datastore.UserPreferencesDataStore
import com.botwmap.app.data.local.AssetDataSource
import com.botwmap.app.data.model.ArmorItem
import com.botwmap.app.data.model.CustomMarker
import com.botwmap.app.data.model.DragonPathItem
import com.botwmap.app.data.model.KorokItem
import com.botwmap.app.data.model.KorokPathItem
import com.botwmap.app.data.model.LayerGroup
import com.botwmap.app.data.model.MapPoint
import com.botwmap.app.data.model.MarkerCategory
import com.botwmap.app.data.model.MaterialItem
import com.botwmap.app.data.model.QuestItem
import com.botwmap.app.data.model.RegionLabel
import com.botwmap.app.data.model.SharePayload
import com.botwmap.app.data.model.WeaponItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 统一数据入口：assets 静态数据（点位/类目/地名/克洛格）+
 * DataStore 持久化（图层开关/完成进度/自定义标记/显示设置）。
 */
class MapRepository(
    private val prefs: UserPreferencesDataStore,
    private val assets: AssetDataSource,
) {

    // ---- 静态数据（启动时从 assets 加载一次）----

    data class StaticData(
        val categories: List<MarkerCategory>,
        val points: List<MapPoint>,
        val regions: List<RegionLabel>,
        val koroks: List<KorokItem>,
        val materials: List<MaterialItem>,
        val armors: List<ArmorItem>,
        val weapons: List<WeaponItem>,
        val quests: List<QuestItem>,
        val dragonPaths: List<DragonPathItem>,
        val korokPathsById: Map<String, KorokPathItem>,
        /** 材料刷点按材料 id 分组（逻辑坐标） */
        val materialPointsBy: Map<Int, List<Pair<Float, Float>>>,
    ) {
        val categoriesByKey: Map<String, MarkerCategory> = categories.associateBy { it.key }
        val pointsById: Map<String, MapPoint> = points.associateBy { it.id }
        val koroksById: Map<String, KorokItem> = koroks.associateBy { it.id }
        val categoriesByGroup: Map<LayerGroup, List<MarkerCategory>> =
            categories.groupBy { it.group }
        val questsByKey: Map<String, QuestItem> = quests.associateBy { it.key }
        val materialsById: Map<Int, MaterialItem> = materials.associateBy { it.id }
    }

    private val _static = MutableStateFlow<StaticData?>(null)
    val static: StateFlow<StaticData?> = _static.asStateFlow()

    suspend fun loadStatic() {
        if (_static.value != null) return
        val markersFile = assets.loadMarkers()
        val regions = assets.loadRegions()
        val koroks = assets.loadKoroks()
        val materialsFile = assets.loadMaterials()
        val armors = assets.loadArmors()
        val weapons = assets.loadWeapons()
        val quests = assets.loadQuests()
        val dragonPaths = assets.loadDragonPaths()
        val korokPaths = assets.loadKorokPaths()

        // 扁平刷点 [id,px,py,...] → 按 id 分组
        val matPoints = HashMap<Int, ArrayList<Pair<Float, Float>>>()
        val flat = materialsFile.pointsFlat
        var i = 0
        while (i + 2 < flat.size) {
            val id = flat[i]
            val px = flat[i + 1].toFloat()
            val py = flat[i + 2].toFloat()
            matPoints.getOrPut(id) { ArrayList() }.add(px to py)
            i += 3
        }

        _static.value = StaticData(
            categories = markersFile.categories,
            points = markersFile.markers,
            regions = regions,
            koroks = koroks,
            materials = materialsFile.materials,
            armors = armors,
            weapons = weapons,
            quests = quests,
            dragonPaths = dragonPaths,
            korokPathsById = korokPaths.associateBy { it.id },
            materialPointsBy = matPoints,
        )
    }

    fun pointById(id: String): MapPoint? = _static.value?.pointsById?.get(id)

    // ---- 图层开关（key = 类目 cat，DataStore 持久化）----

    val enabledLayers: Flow<Set<String>> = prefs.enabledLayers

    suspend fun setLayerEnabled(layerId: String, enabled: Boolean) =
        prefs.setLayerEnabled(layerId, enabled)

    suspend fun setAllLayers(layerIds: Set<String>) = prefs.setAllLayers(layerIds)

    // ---- 材料图层（多选，key = 材料 id）----

    val enabledMaterials: Flow<Set<String>> = prefs.enabledMaterials

    suspend fun setMaterialEnabled(materialId: Int, enabled: Boolean) =
        prefs.setMaterialEnabled(materialId, enabled)

    suspend fun setAllMaterials(materialIds: Set<Int>) = prefs.setAllMaterials(materialIds)

    // ---- 完成进度（DataStore 持久化）----

    val completedPoints: Flow<Set<String>> = prefs.completedPoints

    suspend fun setPointCompleted(pointId: String, completed: Boolean) =
        prefs.setPointCompleted(pointId, completed)

    /** 任务完成态（独立于点位完成态） */
    val completedQuests: Flow<Set<String>> = prefs.completedQuests

    suspend fun setQuestCompleted(questKey: String, completed: Boolean) =
        prefs.setQuestCompleted(questKey, completed)

    // ---- 跨页地图定位请求（任务列表 → 地图）----

    private val _mapFocusRequest = MutableStateFlow<Pair<Double, Double>?>(null)
    val mapFocusRequest: StateFlow<Pair<Double, Double>?> = _mapFocusRequest.asStateFlow()

    fun requestMapFocus(px: Double, py: Double) {
        _mapFocusRequest.value = px to py
    }

    fun consumeMapFocus() {
        _mapFocusRequest.value = null
    }

    // ---- 图鉴选中的材料（跨页 → 地图刷点显示）----

    private val _selectedMaterialId = MutableStateFlow<Int?>(null)
    val selectedMaterialId: StateFlow<Int?> = _selectedMaterialId.asStateFlow()

    fun selectMaterial(id: Int?) {
        _selectedMaterialId.value = id
    }

    /** 选中材料的刷点（无选中返回 null） */
    fun materialPointsFor(id: Int?): List<Pair<Float, Float>>? =
        id?.let { _static.value?.materialPointsBy?.get(it) }

    fun materialById(id: Int?): MaterialItem? =
        id?.let { _static.value?.materialsById?.get(it) }

    /** 按类目统计完成度：catKey -> (completed, total) */
    fun progressByCategory(completed: Set<String>): Map<String, Pair<Int, Int>> {
        val data = _static.value ?: return emptyMap()
        return data.points
            .groupBy { it.cat }
            .mapValues { (_, list) -> list.count { it.id in completed } to list.size }
    }

    // ---- 搜索（中/英文名 + 区域）----

    fun search(query: String, limit: Int = 30): List<MapPoint> {
        val data = _static.value ?: return emptyList()
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        return data.points.asSequence()
            .filter {
                it.name.contains(q, ignoreCase = true) ||
                    it.en.contains(q, ignoreCase = true) ||
                    it.region.contains(q, ignoreCase = true)
            }
            .take(limit)
            .toList()
    }

    // ---- 自定义标记（JSON in DataStore）----

    private val _customMarkers = MutableStateFlow<List<CustomMarker>>(emptyList())
    val customMarkers: StateFlow<List<CustomMarker>> = _customMarkers.asStateFlow()

    suspend fun loadCustomMarkers() {
        val json = prefs.customMarkersJson.first()
        runCatching { Json.decodeFromString<List<CustomMarker>>(json) }
            .onSuccess { _customMarkers.value = it }
    }

    suspend fun addCustomMarker(title: String, note: String, px: Double, py: Double) {
        val marker = CustomMarker(
            id = "custom_${System.currentTimeMillis()}",
            title = title,
            note = note,
            px = px,
            py = py,
            createdAt = System.currentTimeMillis(),
        )
        _customMarkers.update { it + marker }
        persistMarkers()
    }

    suspend fun deleteCustomMarker(id: String) {
        _customMarkers.update { list -> list.filterNot { it.id == id } }
        persistMarkers()
    }

    private suspend fun persistMarkers() {
        prefs.saveCustomMarkersJson(Json.encodeToString(_customMarkers.value))
    }

    // ---- 分享码（JSON → Base64 URL-safe）----

    /** 导出当前状态为可复制的分享码 */
    suspend fun exportShareCode(): String {
        val payload = SharePayload(
            layers = prefs.enabledLayers.first(),
            points = prefs.completedPoints.first(),
            quests = prefs.completedQuests.first(),
            custom = _customMarkers.value,
            showCompleted = prefs.showCompleted.first(),
            showRegions = prefs.showRegions.first(),
            showPaths = prefs.showPaths.first(),
        )
        val json = Json.encodeToString(payload)
        return android.util.Base64.encodeToString(
            json.toByteArray(Charsets.UTF_8),
            android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP,
        )
    }

    /**
     * 导入分享码（覆盖式）。
     * @return 成功返回导入统计，失败抛异常（调用方提示"无效的分享码"）。
     */
    suspend fun importShareCode(code: String): SharePayload {
        val decoded = android.util.Base64.decode(
            code.trim(),
            android.util.Base64.URL_SAFE,
        )
        val payload = Json.decodeFromString<SharePayload>(String(decoded, Charsets.UTF_8))
        require(payload.v == 1) { "不支持的分享码版本: ${payload.v}" }
        prefs.importAll(
            layers = payload.layers,
            points = payload.points,
            quests = payload.quests,
            customJson = Json.encodeToString(payload.custom),
            showCompleted = payload.showCompleted,
            showRegions = payload.showRegions,
            showPaths = payload.showPaths,
        )
        _customMarkers.value = payload.custom
        return payload
    }

    // ---- 存档加载（game_data.sav，移植自原项目 save_upload.js）----

    @Volatile
    private var saveTables: com.botwmap.app.data.local.SaveTables? = null

    /**
     * 解析 game_data.sav 并吸收合并完成进度（存档 ∪ 手动标记）。
     * @return 成功返回摘要文案（如「神庙 60 · 塔 12 · 任务 30（游玩 88.5 小时）」）
     * @throws com.botwmap.app.data.save.SaveParser.ParseException 校验失败
     */
    suspend fun applySave(bytes: ByteArray, fileName: String): String {
        val tables = saveTables ?: assets.loadSaveTables().also { saveTables = it }
        val result = com.botwmap.app.data.save.SaveParser.parse(
            bytes, fileName, tables.flags, tables.questFlags, tables.enemyPts,
        )

        // 讨伐坐标 → 最近的讨伐类点位（阈值 300 逻辑像素，1:1 贪心防抢）
        val enemyIds = HashSet<String>()
        val st = _static.value
        if (st != null && result.enemyPts.isNotEmpty()) {
            val targets = st.points.filter {
                it.cat == "hinox" || it.cat == "talus" || it.cat == "molduga"
            }
            val used = HashSet<String>()
            val cand = ArrayList<Triple<Float, String, String>>() // dist², ptKey, markerId
            for ((px, py) in result.enemyPts) {
                for (m in targets) {
                    val dx = m.px.toFloat() - px
                    val dy = m.py.toFloat() - py
                    val d2 = dx * dx + dy * dy
                    if (d2 < 300f * 300f) cand.add(Triple(d2, "$px,$py", m.id))
                }
            }
            cand.sortBy { it.first }
            val usedPt = HashSet<String>()
            for ((_, ptKey, id) in cand) {
                if (ptKey in usedPt || id in used) continue
                usedPt.add(ptKey)
                used.add(id)
                enemyIds.add(id)
            }
        }

        val points = result.completedIds + enemyIds
        prefs.applySaveProgress(points, result.questKeys)

        val cn = mapOf(
            "shrine" to "神庙", "tower" to "塔", "korok" to "克洛格",
            "memory" to "回忆", "beast" to "神兽",
        )
        val parts = ArrayList<String>()
        for ((k, label) in cn) {
            result.counts[k]?.let { if (it > 0) parts.add("$label $it") }
        }
        result.counts["quest"]?.let { if (it > 0) parts.add("任务 $it") }
        if (enemyIds.isNotEmpty()) parts.add("讨伐 ${enemyIds.size}")
        val time = if (result.playtimeSec > 0) {
            "（游玩 ${"%.1f".format(result.playtimeSec / 3600f)} 小时）"
        } else {
            "（游玩时间未记录）"
        }
        return "已从 ${result.platform} 存档加载 ✓ " +
            (parts.joinToString(" · ").ifEmpty { "无新增进度" }) + " $time"
    }

    // ---- 显示设置 ----

    val showCompleted: Flow<Boolean> = prefs.showCompleted

    suspend fun setShowCompleted(show: Boolean) = prefs.setShowCompleted(show)

    val showRegions: Flow<Boolean> = prefs.showRegions

    suspend fun setShowRegions(show: Boolean) = prefs.setShowRegions(show)

    val showPaths: Flow<Boolean> = prefs.showPaths

    suspend fun setShowPaths(show: Boolean) = prefs.setShowPaths(show)

    val themeMode: Flow<String> = prefs.themeMode

    suspend fun setThemeMode(mode: String) = prefs.setThemeMode(mode)

    val dynamicColor: Flow<Boolean> = prefs.dynamicColor

    suspend fun setDynamicColor(enabled: Boolean) = prefs.setDynamicColor(enabled)

    val onboardingDone: Flow<Boolean> = prefs.onboardingDone

    suspend fun setOnboardingDone(done: Boolean) = prefs.setOnboardingDone(done)

    // ---- 悬浮底栏背景（液态玻璃自定义）----

    val customBarBlur: Flow<Float> = prefs.customBarBlur

    suspend fun setCustomBarBlur(value: Float) = prefs.setCustomBarBlur(value)

    val customBarAlpha: Flow<Float> = prefs.customBarAlpha

    suspend fun setCustomBarAlpha(value: Float) = prefs.setCustomBarAlpha(value)

    /** 背景图片文件名（null = 未设置） */
    val customBarBgFile: Flow<String?> = prefs.customBarBgFile

    suspend fun setCustomBarBgFile(fileName: String?) = prefs.setCustomBarBgFile(fileName)

    // ---- 全局背景图 ----

    val bgImageEnabled: Flow<Boolean> = prefs.bgImageEnabled

    suspend fun setBgImageEnabled(enabled: Boolean) = prefs.setBgImageEnabled(enabled)

    val bgImageBlur: Flow<Float> = prefs.bgImageBlur

    suspend fun setBgImageBlur(value: Float) = prefs.setBgImageBlur(value)

    val bgImageAlpha: Flow<Float> = prefs.bgImageAlpha

    suspend fun setBgImageAlpha(value: Float) = prefs.setBgImageAlpha(value)
}
