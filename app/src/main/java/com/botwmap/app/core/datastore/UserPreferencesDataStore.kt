package com.botwmap.app.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "botw_map_settings")

/**
 * 用户偏好持久化：图层开关、完成进度、自定义标记、主题设置。
 * 单一 Preferences DataStore，key 按功能分组。
 */
class UserPreferencesDataStore(private val context: Context) {

    private object Keys {
        val ENABLED_LAYERS = stringSetPreferencesKey("enabled_layers")
        val ENABLED_MATERIALS = stringSetPreferencesKey("enabled_materials")
        val COMPLETED_POINTS = stringSetPreferencesKey("completed_points")
        val COMPLETED_QUESTS = stringSetPreferencesKey("completed_quests")
        val CUSTOM_MARKERS = stringPreferencesKey("custom_markers_json")
        val THEME_MODE = stringPreferencesKey("theme_mode") // system | light | dark
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val SHOW_COMPLETED = booleanPreferencesKey("show_completed_markers")
        val SHOW_REGIONS = booleanPreferencesKey("show_region_labels")
        val SHOW_PATHS = booleanPreferencesKey("show_dragon_paths")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val BAR_BLUR = floatPreferencesKey("liquid_bar_blur_dp") // 悬浮底栏模糊度 dp
        val BAR_ALPHA = floatPreferencesKey("liquid_bar_alpha") // 悬浮底栏容器透明度
        val BAR_BG_FILE = stringPreferencesKey("liquid_bar_bg_file") // 背景图片文件名（私有目录）
        val BG_ENABLED = booleanPreferencesKey("bg_image_enabled") // 全局背景图开关
        val BG_BLUR = floatPreferencesKey("bg_image_blur_dp") // 背景图模糊度 dp
        val BG_ALPHA = floatPreferencesKey("bg_image_alpha") // 背景图透明度
    }

    // ---- 图层 ----

    val enabledLayers: Flow<Set<String>> =
        context.dataStore.data.map { it[Keys.ENABLED_LAYERS] ?: DEFAULT_LAYERS }

    suspend fun setLayerEnabled(layerId: String, enabled: Boolean) {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.ENABLED_LAYERS] ?: DEFAULT_LAYERS
            prefs[Keys.ENABLED_LAYERS] =
                if (enabled) current + layerId else current - layerId
        }
    }

    suspend fun setAllLayers(layerIds: Set<String>) {
        context.dataStore.edit { it[Keys.ENABLED_LAYERS] = layerIds }
    }

    // ---- 材料图层（id = 材料 id 字符串，默认全不选）----

    val enabledMaterials: Flow<Set<String>> =
        context.dataStore.data.map { it[Keys.ENABLED_MATERIALS] ?: emptySet() }

    suspend fun setMaterialEnabled(materialId: Int, enabled: Boolean) {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.ENABLED_MATERIALS] ?: emptySet()
            val key = materialId.toString()
            prefs[Keys.ENABLED_MATERIALS] =
                if (enabled) current + key else current - key
        }
    }

    suspend fun setAllMaterials(materialIds: Set<Int>) {
        context.dataStore.edit {
            it[Keys.ENABLED_MATERIALS] = materialIds.map(Int::toString).toSet()
        }
    }

    // ---- 完成进度 ----

    val completedPoints: Flow<Set<String>> =
        context.dataStore.data.map { it[Keys.COMPLETED_POINTS] ?: emptySet() }

    suspend fun setPointCompleted(pointId: String, completed: Boolean) {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.COMPLETED_POINTS] ?: emptySet()
            prefs[Keys.COMPLETED_POINTS] =
                if (completed) current + pointId else current - pointId
        }
    }

    /** 任务完成态（key = QuestItem.key，与点位完成态独立） */
    val completedQuests: Flow<Set<String>> =
        context.dataStore.data.map { it[Keys.COMPLETED_QUESTS] ?: emptySet() }

    suspend fun setQuestCompleted(questKey: String, completed: Boolean) {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.COMPLETED_QUESTS] ?: emptySet()
            prefs[Keys.COMPLETED_QUESTS] =
                if (completed) current + questKey else current - questKey
        }
    }

    // ---- 自定义标记（JSON 序列化，Repository 负责编解码）----

    val customMarkersJson: Flow<String> =
        context.dataStore.data.map { it[Keys.CUSTOM_MARKERS] ?: "[]" }

    suspend fun saveCustomMarkersJson(json: String) {
        context.dataStore.edit { it[Keys.CUSTOM_MARKERS] = json }
    }

    /** 分享码导入：单次事务覆盖图层/进度/标记/显示开关 */
    suspend fun importAll(
        layers: Set<String>,
        points: Set<String>,
        quests: Set<String>,
        customJson: String,
        showCompleted: Boolean,
        showRegions: Boolean,
        showPaths: Boolean,
    ) {
        context.dataStore.edit { p ->
            p[Keys.ENABLED_LAYERS] = layers
            p[Keys.COMPLETED_POINTS] = points
            p[Keys.COMPLETED_QUESTS] = quests
            p[Keys.CUSTOM_MARKERS] = customJson
            p[Keys.SHOW_COMPLETED] = showCompleted
            p[Keys.SHOW_REGIONS] = showRegions
            p[Keys.SHOW_PATHS] = showPaths
        }
    }

    /** 存档导入：吸收合并（存档 ∪ 手动标记），单次事务写入 */
    suspend fun applySaveProgress(points: Set<String>, quests: Set<String>) {
        context.dataStore.edit { p ->
            p[Keys.COMPLETED_POINTS] = (p[Keys.COMPLETED_POINTS] ?: emptySet()) + points
            p[Keys.COMPLETED_QUESTS] = (p[Keys.COMPLETED_QUESTS] ?: emptySet()) + quests
        }
    }

    // ---- 显示设置 ----

    val showCompleted: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.SHOW_COMPLETED] ?: true }

    suspend fun setShowCompleted(show: Boolean) {
        context.dataStore.edit { it[Keys.SHOW_COMPLETED] = show }
    }

    val dynamicColor: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.DYNAMIC_COLOR] ?: false }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.dataStore.edit { it[Keys.DYNAMIC_COLOR] = enabled }
    }

    // ---- 悬浮底栏背景（液态玻璃自定义）----

    /** 模糊度（dp，0 = 关闭模糊），默认 8 */
    val customBarBlur: Flow<Float> =
        context.dataStore.data.map { it[Keys.BAR_BLUR] ?: 8f }

    suspend fun setCustomBarBlur(value: Float) {
        context.dataStore.edit { it[Keys.BAR_BLUR] = value.coerceIn(0f, 24f) }
    }

    /** 容器透明度（0.1..1），默认 0.4 */
    val customBarAlpha: Flow<Float> =
        context.dataStore.data.map { it[Keys.BAR_ALPHA] ?: 0.4f }

    suspend fun setCustomBarAlpha(value: Float) {
        context.dataStore.edit { it[Keys.BAR_ALPHA] = value.coerceIn(0.1f, 1f) }
    }

    // ---- 全局背景图（界面背景，md3Music 风格）----

    /** 背景图启用开关（选图后默认启用） */
    val bgImageEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.BG_ENABLED] ?: true }

    suspend fun setBgImageEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.BG_ENABLED] = enabled }
    }

    /** 背景图模糊度（dp），默认 0 = 清晰 */
    val bgImageBlur: Flow<Float> =
        context.dataStore.data.map { it[Keys.BG_BLUR] ?: 0f }

    suspend fun setBgImageBlur(value: Float) {
        context.dataStore.edit { it[Keys.BG_BLUR] = value.coerceIn(0f, 24f) }
    }

    /** 背景图透明度（0.1..1），默认 1 = 不透明 */
    val bgImageAlpha: Flow<Float> =
        context.dataStore.data.map { it[Keys.BG_ALPHA] ?: 1f }

    suspend fun setBgImageAlpha(value: Float) {
        context.dataStore.edit { it[Keys.BG_ALPHA] = value.coerceIn(0.1f, 1f) }
    }

    /** 底栏背景图片（filesDir 下文件名；null = 未设置，用默认纯色） */
    val customBarBgFile: Flow<String?> =
        context.dataStore.data.map { it[Keys.BAR_BG_FILE] }

    suspend fun setCustomBarBgFile(fileName: String?) {
        context.dataStore.edit { p ->
            if (fileName == null) p.remove(Keys.BAR_BG_FILE)
            else p[Keys.BAR_BG_FILE] = fileName
        }
    }

    /** 主题模式：system / light / dark */
    val themeMode: Flow<String> =
        context.dataStore.data.map { it[Keys.THEME_MODE] ?: "system" }

    suspend fun setThemeMode(mode: String) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode }
    }

    /** 地名标签显示开关 */
    val showRegions: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.SHOW_REGIONS] ?: true }

    suspend fun setShowRegions(show: Boolean) {
        context.dataStore.edit { it[Keys.SHOW_REGIONS] = show }
    }

    /** 龙飞行轨迹显示开关 */
    val showPaths: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.SHOW_PATHS] ?: true }

    suspend fun setShowPaths(show: Boolean) {
        context.dataStore.edit { it[Keys.SHOW_PATHS] = show }
    }

    val onboardingDone: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.ONBOARDING_DONE] ?: false }

    suspend fun setOnboardingDone(done: Boolean) {
        context.dataStore.edit { it[Keys.ONBOARDING_DONE] = done }
    }

    companion object {
        /** 首次启动默认开启的图层（key = 原项目类目 cat；宝箱点位密集默认关闭） */
        val DEFAULT_LAYERS: Set<String> = setOf(
            "tower", "shrine", "seed", "memory", "beast",
            "mainquest", "shrinequest", "sidequest", "village", "stable",
        )
    }
}
