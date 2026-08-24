# Lumin Graphics — Epsilon 消费面清单（2026-08 快照）

> 数据源：`git grep "import com.github.slmpc" upstream/26.1.x -- '*.java'`
> 每一个条目都是 OpenLumin 必须保持兼容的真实公开 API。

<!-- GitHub@NDBlockConnect | BlockConnect@StarsailsClover -->

## 1. Maven 坐标（gradle/libs.versions.toml @ upstream/26.1.x）

```toml
lumin-graphics = "1.2.5"
prism-rhi = "0.2.2"

lumin-graphics-ui = { module = "com.github.slmpc.lumingraphics:lumin-graphics-ui", version.ref = "lumin-graphics" }
lumin-graphics-mc-fabric-v2612 = { module = "com.github.slmpc.lumingraphics.mc:lumin-graphics-mc-fabric-26.1.2", ... }
lumin-graphics-mc-neoforge-v2612 = { module = "com.github.slmpc.lumingraphics.mc:lumin-graphics-mc-neoforge-26.1.2", ... }
lumin-graphics-mc-common-v2612 = { module = "com.github.slmpc.lumingraphics.mc:mc-26.1.2-common", ... }
lumin-graphics-mc-bridge-contract = { module = "com.github.slmpc.lumingraphics.mc:bridge-contract", ... }
prism-rhi-backend-opengl41 = { module = "com.github.slmpc.prismrhi:prism-rhi-backend-opengl41", ... }
prism-rhi-backend-opengl46 = { module = "com.github.slmpc.prismrhi:prism-rhi-backend-opengl46", ... }
```

分发形态：`lumin-graphics-mc-fabric-26.1.2` 以 Fabric Jar-in-Jar 进入 mod jar
（`META-INF/jars/`），NeoForge 侧走 `META-INF/jarjar/`；
`mc-26.1.2-common` 与 `bridge-contract` 仅 compileOnly、**不**嵌入最终产物。

## 2. 类消费频次矩阵（import 计数）

| 类 | 引用数 | 包 | 备注 |
|----|-------|-----|------|
| `UiTree` | 72 | `ui.tree` | 声明式 UI 树；`Scope` DSL + `UiTree.from(scope)` |
| `UiRect` | 56 | `ui.geometry` | 不可变矩形 record（x/y/width/height + atOrigin/relativeTo/contains） |
| `UiTextMetrics` | 55 | `ui.text` | 文本度量接口；**textWidth/textHeight 接受字体名字符串** |
| `MinecraftUiRuntime2612`（mc.v2612.* 合计） | 41 | `mc.v2612.runtime` 等 | `current()` / `createScene(theme)` / `render(scene, layer, tree)` / `textMetrics()` |
| `UiRenderBatch` | 20 | `ui.render` | 帧批次；`batch(layer[, relativeLayer])` |
| `IconChars` | 11 | `text.icon` | 图标字形常量表（`CODE`/`REFRESH`/`KEYBOARD_ARROW_DOWN`…） |
| `UiContentBuffer` | 10 | `ui.render` | 视口内容缓冲；构造签名 `(UiTheme)` |
| `UiScene` | 10 | `ui.scene` | `submit(layer[, rel], tree)` / `close()`；经 runtime 创建 |
| `UiLayer` | 10 | `ui.scene` | `CHROME(100) / CONTENT(200) / POPUP(400)` |
| `UiInvalidationState` | 6 | `ui.state` | 脏标记 + 内容签名重建判定 |
| `UiTheme` | 2 | `ui.theme` | 主题接口（controlRadius/textPrimary/…） |
| `UiScrollBar` | 2 | `ui.control` | 滚动条控件 |
| `LuminColor` | 2 | `core.geometry` | 颜色工具 |
| `TextRenderer`（text.render） | 1 | `text.render` | 字体渲染器 |
| `UiAnimation` | 1 | `ui.animation` | 动画 |
| `SelectionRange` | 1 | `ui.control` | 文本选区 |
| `EmojiGlyph` | 1 | `text.emoji` | Emoji 字形 |

另有 MC 桥接类（`mc.v2612.*`）：`MinecraftBlurRegion2612`、`MinecraftGlyphAtlasTexture2612`、
`MinecraftGuiExtractionBridge2612`、`MinecraftFontAdapter2612`、`TextRenderableAdapter`。

## 3. 关键调用形态（从消费代码提炼）

```java
// 场景生命周期（Screen 级）
MinecraftUiRuntime2612 runtime = MinecraftUiRuntime2612.current();
UiScene scene = runtime.createScene(EpsilonUiTheme.lumin());
runtime.render(scene, activeScene -> { /* batch()/submit() */ });
scene.close(); // runtime 变更或 Screen.removed 时

// 树构建 → 提交
UiTree tree = UiTree.build(scope -> { scope.rect(...); scope.layer(0, child -> ...); });
scene.submit(UiLayer.CONTENT, relativeLayer, tree);
if (tree.nodeCount() > 0) { /* 跳过空帧 */ }

// 文本度量（字体名为字符串！）
UiTextMetrics m = runtime.textMetrics();
m.textWidth("text", 0.6f, "epsilon-icons");
m.textHeight(0.6f, null);

// HUD/Level 独立树（模块侧渲染，非 Screen）
runtime.render(scene(runtime), UiLayer.CONTENT, tree);
```

## 4. 兼容性预算建议（给 OpenLumin）

- **P0 签名冻结**：`UiTree.Scope` 全部绘制方法、`UiRect` record 分量、
  `UiTextMetrics` 的字符串字体重载。任何一处破坏 = 72/56/55 个消费点连锁爆炸。
- **P1 语义冻结**：`UiLayer` 数值（CHROME=100/CONTENT=200/POPUP=400）已被
  relativeLayer 叠加逻辑隐式依赖。
- **P2 可演进**：`mc.v2612.*` 桥接类（版本专属，消费方应通过 bridge-contract 间接依赖）。
