# BOTWMap · 海拉鲁互动地图（Android）

《塞尔达传说：旷野之息》互动地图 Android 客户端：**离线全量数据**、收集进度追踪、任务与图鉴、液态玻璃 UI。

## 功能

- **离线高清地图**：海拉鲁全图高清瓦片，双层缓存（首屏全量预载 + 细节按需），缩放流畅不空白
- **3038 个点位 · 27 类图层**：神庙、塔、村庄、克洛格、回忆、神兽、任务点等，支持多选图层
- **克洛格图鉴**：900 克洛格位置 + 中文解法 + 解法截图查看
- **材料刷点**：74 种材料 13524 个刷点，图鉴一键定位到地图
- **任务列表**：主线挑战 / 神庙挑战 / 支线任务 152 条，跨页定位流程坐标
- **装备图鉴**：90 武器 / 31 防具，属性与套装一览
- **收集进度**：点位完成打勾、进度统计面板
- **自定义标记**：地图任意位置添加标题/备注
- **分享码**：图层选择、完成进度、自定义标记一键导出/导入
- **存档加载**：解析模拟器存档，自动同步讨伐进度
- **液态玻璃 UI**：底栏、卡片、搜索框统一液态玻璃风格（Android 12+），模糊度/透明度可调

## 构建

```bash
# 要求：JDK 21、Android SDK（compileSdk 36+）
gradle assembleRelease   # 或 ./gradlew assembleDebug
```

APK 输出：`app/build/outputs/apk/release/app-release.apk`

## 致谢

本项目的完成离不开以下开源项目与素材来源：

### 数据与素材

- **[yalincc/BOTWmap](https://github.com/yalincc/BOTWmap)**（MIT License）
  互动地图的点位、克洛格解法、任务、材料/装备数据与地图瓦片、图标的来源。
  本项目通过 `tools/convert-data.js` 将其数据转换为 Android assets。
- **《塞尔达传说：旷野之息》（The Legend of Zelda: Breath of the Wild）** — Nintendo
  游戏名称、地名、物品名、地图图像与图标素材均归 Nintendo 及相关著作权人所有，仅作粉丝作品非商业用途引用。

### 开源库与设计参考

- **[HapeLee/Matter](https://github.com/HapeLee/Matter)** — 液态玻璃 UI 设计与悬浮底栏（Legado 风格液态玻璃底栏）的实现参考
- **[Kyant0](https://github.com/Kyant0)** — `io.github.kyant0:backdrop` / `shapes`：液态玻璃渲染库（vibrancy / blur / lens 折射）
- **[Jetpack Compose](https://developer.android.com/jetpack/compose)** / **Material 3** — Android 现代 UI 框架
- **[Insert-Koin](https://insert-koin.io/)** — 依赖注入
- **[Ktor](https://ktor.io/)** / **[kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization)** — 网络与 JSON 序列化
- **[AndroidX](https://developer.android.com/jetpack/androidx)** — Lifecycle、Navigation、DataStore 等基础组件

## 免责声明

1. **非官方作品**：本项目为粉丝自制的非商业学习作品，与 Nintendo、任天堂（香港）有限公司及任何官方机构**无任何关联**，未获官方授权。
2. **素材版权**：游戏名称、商标、地图图像、图标及其他游戏素材的著作权归 **Nintendo / 著作权人**所有。如有权利方认为本项目存在不当使用，请通过 Issue 联系，我们将立即处理。
3. **数据来源**：地图点位与相关数据转换自 [yalincc/BOTWmap](https://github.com/yalincc/BOTWmap)（MIT 许可），已按其许可要求署名。
4. **非商业用途**：本项目及其衍生作品**禁止用于任何商业目的**。
5. **风险自担**：软件按"现状"提供，不提供任何明示或暗示的担保；因使用本项目产生的任何直接或间接损失，作者不承担责任。
6. **存档安全**：加载存档功能会读取本地文件，请自行备份重要数据。

## 许可证

[AGPL-3.0](./LICENSE)

```
Copyright (C) 2026 BOTWMap contributors
```

本项目源码以 **GNU Affero General Public License v3.0（AGPL-3.0）** 发布。
基于本项目的服务端/网络分发同样需要开源其修改后的源码。第三方素材与开源库仍遵循其各自的原始许可证（见上方致谢）。
