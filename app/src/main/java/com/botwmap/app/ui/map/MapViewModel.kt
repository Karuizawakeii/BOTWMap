package com.botwmap.app.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.botwmap.app.data.model.CustomMarker
import com.botwmap.app.data.model.DragonPathItem
import com.botwmap.app.data.model.KorokItem
import com.botwmap.app.data.model.LayerGroup
import com.botwmap.app.data.model.MapPoint
import com.botwmap.app.data.model.MarkerCategory
import com.botwmap.app.data.model.MaterialItem
import com.botwmap.app.data.model.RegionLabel
import com.botwmap.app.data.repository.MapRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 地图页 UI 状态（UDF：Composable 只渲染此状态）。
 */
data class MapUiState(
    val loading: Boolean = true,
    /** 当前图层开关下可见的点位 */
    val visiblePoints: List<MapPoint> = emptyList(),
    val categories: List<MarkerCategory> = emptyList(),
    val categoriesByKey: Map<String, MarkerCategory> = emptyMap(),
    val categoriesByGroup: Map<LayerGroup, List<MarkerCategory>> = emptyMap(),
    /** 全部材料（图层面板材料 Tab 用） */
    val materials: List<MaterialItem> = emptyList(),
    val enabledLayerIds: Set<String> = emptySet(),
    val completedIds: Set<String> = emptySet(),
    /** catKey -> (completed, total) */
    val progress: Map<String, Pair<Int, Int>> = emptyMap(),
    val regions: List<RegionLabel> = emptyList(),
    val customMarkers: List<CustomMarker> = emptyList(),
    /** 详情卡片选中的常规点位 / 自定义标记 */
    val selectedPoint: MapPoint? = null,
    val selectedCustom: CustomMarker? = null,
    /** 选中点位对应的克洛格解法（cat=seed 时非空） */
    val selectedKorok: KorokItem? = null,
    val showCompleted: Boolean = true,
    val showRegions: Boolean = true,
    val showPaths: Boolean = true,
    /** 材料图层：启用的材料 id 集合（持久化） */
    val enabledMaterialIds: Set<String> = emptySet(),
    /** 启用材料的合并刷点（带材料 id，供命中检测） */
    val materialPoints: List<MaterialPt> = emptyList(),
    /** 点击材料刷点的详情卡（TOTK 风格：图/区域/坐标/位置/用途/编号/图层） */
    val materialDetail: MaterialPointDetail? = null,
    /** 龙飞行轨迹（3 龙多段折线） */
    val dragonPaths: List<DragonPathItem> = emptyList(),
    /** 当前选中克洛格的移动轨迹（无则空） */
    val korokPath: List<Pair<Float, Float>> = emptyList(),
    val layersPanelVisible: Boolean = false,
    val statsVisible: Boolean = false,
    val searchVisible: Boolean = false,
    val searchQuery: String = "",
    val searchResults: List<MapPoint> = emptyList(),
    /** 长按地图后待添加的自定义标记位置（逻辑坐标） */
    val pendingPin: Pair<Float, Float>? = null,
    /** 需要相机聚焦的目标（逻辑坐标），消费后置 null */
    val focusTarget: Pair<Double, Double>? = null,
) {
    /** 总完成度 (completed, total) */
    val totalProgress: Pair<Int, Int> =
        progress.values.fold(0 to 0) { acc, v -> (acc.first + v.first) to (acc.second + v.second) }
}

/** UI 事件：Composable → ViewModel */
sealed interface MapEvent {
    data class ToggleLayer(val layerId: String, val enabled: Boolean) : MapEvent
    data object SelectAllLayers : MapEvent
    data object ClearAllLayers : MapEvent
    data class SelectPoint(val point: MapPoint?) : MapEvent
    data class SelectCustom(val marker: CustomMarker?) : MapEvent
    data class ToggleCompleted(val pointId: String) : MapEvent
    data object OpenLayersPanel : MapEvent
    data object CloseLayersPanel : MapEvent
    data object OpenStats : MapEvent
    data object CloseStats : MapEvent
    data object OpenSearch : MapEvent
    data object CloseSearch : MapEvent
    data class SearchQuery(val query: String) : MapEvent
    /** 长按地图空白处（逻辑坐标） */
    data class MapLongPress(val px: Float, val py: Float) : MapEvent
    data object DismissPendingPin : MapEvent
    data class AddCustomMarker(val title: String, val note: String) : MapEvent
    data class DeleteCustomMarker(val id: String) : MapEvent
    /** 请求相机聚焦到逻辑坐标（搜索结果跳转） */
    data class FocusAt(val px: Double, val py: Double) : MapEvent
    data object FocusHandled : MapEvent
    /** 清除材料详情卡（点地图空白 / 关闭卡片） */
    data object ClearMaterialDetail : MapEvent
    /** 材料图层开关 */
    data class ToggleMaterial(val materialId: Int, val enabled: Boolean) : MapEvent
    /** 材料图层批量设置（全选本组 / 清空） */
    data class SetAllMaterials(val ids: Set<Int>) : MapEvent
    /** 点击材料刷点：(材料id, 逻辑px, 逻辑py) */
    data class TapMaterialPoint(val matId: Int, val px: Float, val py: Float) : MapEvent
}

/** 材料刷点（带材料 id 与图鉴图 entry，供绘制图标） */
data class MaterialPt(val matId: Int, val px: Float, val py: Float, val entry: String)

/** 材料刷点命中（原始坐标） */
internal data class MaterialHit(val matId: Int, val px: Float, val py: Float, val index: Int)

/** 材料刷点详情卡数据 */
data class MaterialPointDetail(
    val material: MaterialItem,
    /** 最近地名 */
    val region: String,
    /** 游戏坐标 X/Z */
    val gameX: Int,
    val gameZ: Int,
    /** 第 n / total 个位置 */
    val posText: String,
    val usage: String,
    /** 该材料刷点当前是否显示 */
    val layerOn: Boolean,
)

class MapViewModel(
    private val repository: MapRepository,
) : ViewModel() {

    private val selectedPoint = MutableStateFlow<MapPoint?>(null)
    private val selectedCustom = MutableStateFlow<CustomMarker?>(null)
    private val layersPanelVisible = MutableStateFlow(false)
    private val statsVisible = MutableStateFlow(false)
    private val searchVisible = MutableStateFlow(false)
    private val searchQuery = MutableStateFlow("")
    private val pendingPin = MutableStateFlow<Pair<Float, Float>?>(null)
    private val focusTarget = MutableStateFlow<Pair<Double, Double>?>(null)
    private val materialHit = MutableStateFlow<MaterialHit?>(null)

    private data class UiPart<A, B, C, D, E>(
        val a: A, val b: B, val c: C, val d: D, val e: E,
    )

    val uiState: StateFlow<MapUiState> = combine(
        combine(
            repository.static,
            repository.enabledLayers,
            repository.completedPoints,
            combine(
                repository.showCompleted,
                repository.showRegions,
                repository.showPaths,
            ) { done, regions, paths -> Triple(done, regions, paths) },
            repository.customMarkers,
        ) { static, enabled, completed, displayFlags, custom ->
            DataPart(
                static, enabled, completed,
                displayFlags.first, displayFlags.second, displayFlags.third,
                custom,
            )
        },
        combine(
            selectedPoint, selectedCustom, layersPanelVisible, statsVisible, searchVisible,
        ) { s, c, l, st, se -> UiPart(s, c, l, st, se) },
        combine(searchQuery, pendingPin, focusTarget, materialHit, repository.enabledMaterials) {
                q, pin, focus, hit, matIds ->
            MatExtra(q, pin, focus, hit, matIds)
        },
    ) { data, ui, extra ->
        val (static, enabled, completed, showCompleted, showRegions, showPaths, custom) = data
        val (selected, customSel, panel, stats, searchVis) = ui
        val (query, pin, focus, hit, matIds) = extra

        val visible = if (static == null) emptyList() else static.points.filter { p ->
            p.cat in enabled && (showCompleted || p.id !in completed)
        }
        // 启用材料的合并刷点
        val matPoints = if (static == null || matIds.isEmpty()) emptyList()
        else buildList {
            for (idStr in matIds) {
                val id = idStr.toIntOrNull() ?: continue
                val pts = static.materialPointsBy[id] ?: continue
                val entry = static.materialsById[id]?.entry ?: continue
                for ((px, py) in pts) add(MaterialPt(id, px, py, entry))
            }
        }
        val korokPath = selected?.let { static?.korokPathsById?.get(it.id)?.path }
            ?.mapNotNull { pt ->
                val x = pt.getOrNull(0) ?: return@mapNotNull null
                val y = pt.getOrNull(1) ?: return@mapNotNull null
                x.toFloat() to y.toFloat()
            } ?: emptyList()
        val matDetail = if (static == null || hit == null) null else run {
            val m = static.materialsById[hit.matId] ?: return@run null
            val pts = static.materialPointsBy[hit.matId] ?: emptyList()
            MaterialPointDetail(
                material = m,
                region = nearestRegion(static.regions, hit.px, hit.py),
                gameX = ((hit.px - 12000f) / 2f).toInt(),
                gameZ = ((hit.py - 10000f) / 2f).toInt(),
                posText = if (pts.isEmpty()) "无固定刷点"
                else "第 ${hit.index + 1} / ${pts.size} 个位置",
                usage = matUsage(m.cat, m.cn),
                layerOn = hit.matId.toString() in matIds,
            )
        }
        MapUiState(
            loading = static == null,
            visiblePoints = visible,
            categories = static?.categories ?: emptyList(),
            categoriesByKey = static?.categoriesByKey ?: emptyMap(),
            categoriesByGroup = static?.categoriesByGroup ?: emptyMap(),
            materials = static?.materials ?: emptyList(),
            enabledLayerIds = enabled,
            completedIds = completed,
            progress = if (static == null) emptyMap() else static.points
                .groupBy { it.cat }
                .mapValues { (_, list) -> list.count { it.id in completed } to list.size },
            regions = static?.regions ?: emptyList(),
            customMarkers = custom,
            selectedPoint = selected,
            selectedCustom = customSel,
            selectedKorok = selected?.let { static?.koroksById?.get(it.id) },
            showCompleted = showCompleted,
            showRegions = showRegions,
            showPaths = showPaths,
            enabledMaterialIds = matIds,
            materialPoints = matPoints,
            materialDetail = matDetail,
            dragonPaths = static?.dragonPaths ?: emptyList(),
            korokPath = korokPath,
            layersPanelVisible = panel,
            statsVisible = stats,
            searchVisible = searchVis,
            searchQuery = query,
            searchResults = repository.search(query),
            pendingPin = pin,
            focusTarget = focus,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MapUiState())

    private data class DataPart(
        val static: com.botwmap.app.data.repository.MapRepository.StaticData?,
        val enabled: Set<String>,
        val completed: Set<String>,
        val showCompleted: Boolean,
        val showRegions: Boolean,
        val showPaths: Boolean,
        val custom: List<CustomMarker>,
    )

    private data class MatExtra(
        val query: String,
        val pin: Pair<Float, Float>?,
        val focus: Pair<Double, Double>?,
        val hit: MaterialHit?,
        val matIds: Set<String>,
    )

    init {
        viewModelScope.launch {
            repository.loadStatic()
            repository.loadCustomMarkers()
        }
        // 跨页定位请求（任务列表"在地图查看"）
        viewModelScope.launch {
            repository.mapFocusRequest.collect { focus ->
                if (focus != null) {
                    focusTarget.value = focus
                    repository.consumeMapFocus()
                }
            }
        }
    }

    fun onEvent(event: MapEvent) {
        when (event) {
            is MapEvent.ToggleLayer -> viewModelScope.launch {
                repository.setLayerEnabled(event.layerId, event.enabled)
            }

            is MapEvent.SelectAllLayers -> viewModelScope.launch {
                val all = uiState.value.categories.map { it.key }.toSet()
                repository.setAllLayers(all)
            }

            is MapEvent.ClearAllLayers -> viewModelScope.launch {
                repository.setAllLayers(emptySet())
            }

            is MapEvent.SelectPoint -> {
                selectedPoint.value = event.point
                if (event.point != null) selectedCustom.value = null
            }

            is MapEvent.SelectCustom -> {
                selectedCustom.value = event.marker
                if (event.marker != null) selectedPoint.value = null
            }

            is MapEvent.ToggleCompleted -> viewModelScope.launch {
                val isCompleted = event.pointId in uiState.value.completedIds
                repository.setPointCompleted(event.pointId, !isCompleted)
            }

            is MapEvent.OpenLayersPanel -> layersPanelVisible.value = true
            is MapEvent.CloseLayersPanel -> layersPanelVisible.value = false
            is MapEvent.OpenStats -> statsVisible.value = true
            is MapEvent.CloseStats -> statsVisible.value = false
            is MapEvent.OpenSearch -> searchVisible.value = true
            is MapEvent.CloseSearch -> {
                searchVisible.value = false
                searchQuery.value = ""
            }

            is MapEvent.SearchQuery -> searchQuery.value = event.query
            is MapEvent.MapLongPress -> pendingPin.value = event.px to event.py
            is MapEvent.DismissPendingPin -> pendingPin.value = null
            is MapEvent.AddCustomMarker -> viewModelScope.launch {
                val pin = pendingPin.value ?: return@launch
                repository.addCustomMarker(
                    event.title, event.note,
                    pin.first.toDouble(), pin.second.toDouble(),
                )
                pendingPin.value = null
            }

            is MapEvent.DeleteCustomMarker -> viewModelScope.launch {
                repository.deleteCustomMarker(event.id)
                selectedCustom.value = null
            }

            is MapEvent.FocusAt -> focusTarget.value = event.px to event.py
            is MapEvent.FocusHandled -> focusTarget.value = null
            is MapEvent.ClearMaterialDetail -> materialHit.value = null
            is MapEvent.ToggleMaterial -> viewModelScope.launch {
                repository.setMaterialEnabled(event.materialId, event.enabled)
            }
            is MapEvent.SetAllMaterials -> viewModelScope.launch {
                repository.setAllMaterials(event.ids)
            }
            is MapEvent.TapMaterialPoint -> {
                // 在该材料的刷点中找最近序号（详情卡"第 n / total"）
                val pts = repository.static.value?.materialPointsBy?.get(event.matId).orEmpty()
                var best = 0
                var bd = Float.MAX_VALUE
                pts.forEachIndexed { i, (x, y) ->
                    val dx = x - event.px
                    val dy = y - event.py
                    val d = dx * dx + dy * dy
                    if (d < bd) {
                        bd = d
                        best = i
                    }
                }
                materialHit.value = MaterialHit(event.matId, event.px, event.py, best)
            }
        }
    }
}

/** 最近地名（r1-r3 全层级，对齐原项目 nearestRegion） */
private fun nearestRegion(
    regions: List<com.botwmap.app.data.model.RegionLabel>,
    px: Float,
    py: Float,
): String {
    var best: com.botwmap.app.data.model.RegionLabel? = null
    var bd = Float.MAX_VALUE
    for (r in regions) {
        val rx = r.px.getOrNull(0)?.toFloat() ?: continue
        val ry = r.px.getOrNull(1)?.toFloat() ?: continue
        val dx = rx - px
        val dy = ry - py
        val d = dx * dx + dy * dy
        if (d < bd) {
            bd = d
            best = r
        }
    }
    return best?.n ?: "—"
}

/** 材料用途（对齐原项目 matUsage：特例 + 类目规则） */
private fun matUsage(cat: String, cn: String): String {
    when (cn) {
        "精灵" -> return "特殊道具（回复生命 / 护身）"
        "静谧公主" -> return "特殊材料（任务 / 防具升级）"
        "毅力胡萝卜" -> return "料理材料（补充精力圈）"
    }
    return when (cat) {
        "昆虫" -> "防具升级素材"
        "矿物" -> "强化材料（防具 / 珠宝）"
        "鱼" -> if (cn in UPGRADE_FISH) "防具升级素材" else "料理材料"
        else -> "料理材料"
    }
}

private val UPGRADE_FISH = setOf(
    "潜行鳟鱼", "铠甲鳟鱼", "大剑鳟鱼", "精力鲈鱼", "远昔骨舌鱼",
)
