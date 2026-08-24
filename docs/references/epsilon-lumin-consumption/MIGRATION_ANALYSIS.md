# upstream Epsilon 渲染迁移解剖（commit d3baeed8, PR #419）

> "将渲染系统用 Lumin Graphics 代替" — 149 文件，+2,357 / −13,527 行
> 作者 Chen_Meng（slmpc），2026-08-09。与 slmpc/lumingraphics v1.2.5、slmpc/prismrhi v0.2.2 配套。

<!-- GitHub@NDBlockConnect | BlockConnect@StarsailsClover -->

## 1. 删除清单（= 迁入外部库的内容）

### graphics/renderers/（全部）
RectRenderer、RoundRectRenderer、RoundRectOutlineRenderer、ShadowRenderer、
TextRenderer、TextureRenderer、TriangleRenderer、IRenderer

### graphics/schedulers/render2d/（全部）
Render2DScheduler（994 行）、Render2DCommand/Kind/Scissor/Texture/Bounds

### graphics/text/（全部）
IconChars（3463 行）、StaticFontLoader、SystemEmojiAtlas、GlyphDescriptor、
IFontLoader、ITextRenderer、minecraft/EpsilonFontGlyph、EpsilonFontMetrics、
EpsilonTextRenderable、ttf/TtfFontFile、TtfFontLoader、TtfGlyph、TtfGlyphAtlas、TtfTextRenderer

### gui/lib/（全部 — 即 EpsilonBC 保留的内建树）
UiRect、UiTextMetrics、UiTheme、UiTree（1152 行）、control/UiScrollBar、
render/LuminUiRenderer、UiContentBuffer、UiRenderBatch、scene/UiLayer、UiLayerStack、UiScene、
state/UiInvalidationState

### 其他
holders/RendererHolder、TextureCacheHolder、utils/render/EpsilonGuiRenderer（614 行）

**合计 ≈ 13.5k 行** —— 与 lumingraphics-ui + lumingraphics-mc-26.1.2 的公开面一一对应。

## 2. 保留/新增

- 保留：`graphics/LuminRenderSystem`（仅 getScaledWidth/Height 等少量静态）、
  `LuminRenderPipelines`、`LuminVertexFormats`（供 3D/世界渲染路径）
- 新增：`gui/utils/UiCoordinateMapper`（MC 物理像素 ↔ GUI 投影坐标换算）、
  `utils/client/FontPathResolver`（自定义字体路径解析）
- 全部 Screen/Widget/Popup 重写为 runtime.render(...) 提交模式

## 3. 构建布线（common/build.gradle.kts @ upstream）

```kotlin
val luminGraphicsMcFabric by configurations.creating { isCanBeResolved = true; isCanBeConsumed = false }
val luminGraphicsMcNeoForge by configurations.creating { /* 同上 */ }

dependencies {
    luminGraphicsMcFabric(libs.lumin.graphics.mc.fabric.v2612) { isTransitive = false }
    luminGraphicsMcNeoForge(libs.lumin.graphics.mc.neoforge.v2612) { isTransitive = false }
    compileOnly(libs.lumin.graphics.ui)
    compileOnly(libs.lumin.graphics.mc.common.v2612)
    compileOnly(libs.lumin.graphics.mc.bridge.contract) { isTransitive = false }
    compileOnly(libs.prism.rhi.backend.opengl41) { isTransitive = false }
    compileOnly(libs.prism.rhi.backend.opengl46) { isTransitive = false }
}
```

### verifyLuminJarInJar 校验门（值得抄的工程实践）

`verifyLuminJarInJarArchives` 任务在构建后用 ZipFile 检查最终 jar：
1. Fabric 产物 `META-INF/jars/` 必须含且仅含对应 loader 的 Lumin-MC jar；
2. **不得**嵌入 `mc-26.1.2-common` / `bridge-contract`（compileOnly 纯编译期）；
3. 必须恰好嵌入 1 个 `luaj-jse-*`（Lua 运行时单实例约束）；
4. 必须含 `fabric.mod.json` / `META-INF/jarjar/metadata.json` 元数据。

## 4. Prism RHI

- 仅两个 OpenGL 后端构件（41/46），`isTransitive=false` 纯编译期；
- 推断：Lumin Graphics 的绘制后端经 Prism RHI 抽象，运行时由 mc 桥接构件选择；
- OpenLumin 的 Vulkan 基线（26.2）与 RHI 思路一致，可评估直接以 Prism RHI 作为
  后端抽象层而非自造 RHI。

## 5. 与 EpsilonBC 分叉的差异要点

| 维度 | upstream 26.1.x | EpsilonBC v26.0-alpha.3 |
|------|-----------------|--------------------------|
| UI 树 | 外部 `lumingraphics.ui.tree.UiTree` | 内建 `gui.lib.UiTree`（同源前身） |
| 文本度量字体参数 | 字符串名（"epsilon-icons"） | `TtfFontLoader` 类型 |
| 场景创建 | `runtime.createScene(theme)` | `new UiScene(theme)` 直构 |
| 帧提交 | `runtime.render(scene, callback)` | `scene.beginFrame/submit/endFrame` |
| MC 坐标 | `UiCoordinateMapper` 双向映射 | 直接 MC 坐标 |
| 26.2 支持 | 无（26.1.2 基线） | 有（26.2 基线 + Vulkan） |
