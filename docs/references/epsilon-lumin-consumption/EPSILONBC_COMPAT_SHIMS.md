# EpsilonBC 内建等价类与兼容 Shim 实录

> 实证结论：让"为外部 lumingraphics 编写的代码"编译并运行在 EpsilonBC 内建
> `gui.lib.*` 树上，**仅需 5 类 shim**。这是 OpenLumin 设计兼容层时的成本基线。

<!-- GitHub@NDBlockConnect | BlockConnect@StarsailsClover -->

## 0. 血统关系

```
Epsilon(旧) common/gui/lib/*  ──提炼──▶  slmpc/lumingraphics(-ui) v1.2.5
        │                                      │
        │ 同源保留                              │ upstream Epsilon 26.1.x 消费
        ▼                                      ▼
EpsilonBC gui.lib.*（内建，26.2 基线）    为外部库编写的新代码（Lua 系统 #420 等）
```

两支 API 90% 同名同形；差异集中在：运行时包装（MinecraftUiRuntime2612）、
文本度量字体参数（String vs TtfFontLoader）、主题颜色包装（EpsilonUiTheme.lumin）。

## 1. 五类 Shim（EpsilonBC 已落地，路径可查）

### Shim 1 — import 重映射（纯机械）

| 外部类 | 内建等价 |
|--------|----------|
| `lumingraphics.ui.tree.UiTree` | `gui.lib.UiTree` |
| `lumingraphics.ui.geometry.UiRect` | `gui.lib.UiRect` |
| `lumingraphics.ui.text.UiTextMetrics` | `gui.lib.UiTextMetrics` |
| `lumingraphics.ui.render.UiContentBuffer` | `gui.lib.render.UiContentBuffer` |
| `lumingraphics.ui.render.UiRenderBatch` | `gui.lib.render.UiRenderBatch` |
| `lumingraphics.ui.scene.UiLayer/UiScene` | `gui.lib.scene.UiLayer/UiScene` |
| `lumingraphics.ui.state.UiInvalidationState` | `gui.lib.state.UiInvalidationState` |
| `lumingraphics.text.icon.IconChars` | `graphics.text.IconChars` |

### Shim 2 — `BuiltInTextMetrics`（gui/lib/BuiltInTextMetrics.java）

- 实现 `UiTextMetrics`，内部委托 `TextRenderer.create()`；
- **提供字符串字体名重载** `textWidth(s, scale, "epsilon-icons")`，
  `resolveFont()` 把上游字体名映射到 `StaticFontLoader.{ICONS,JURA_LIGHT,OSAKA_CHIPS}`；
- 教训：**接口同时收 `TtfFontLoader` 与 `String` 时，`null` 字面量产生歧义**
  ——调用点需显式 `(String) null` 或 `(TtfFontLoader) null`。

### Shim 3 — `UiTree` 缺口方法补齐

- `nodeCount()`：上游有、内建缺 → 3 行补齐（空帧跳过依赖它）；
- `Scope.text(..., String fontName)` / `Scope.texture(Identifier, x,y,w,h, color)`：
  上游便捷重载 → 委托到内建 TtfFontLoader/UV 全参版本。

### Shim 4 — `EpsilonUiTheme.lumin(...)` 静态桥

上游以 `EpsilonUiTheme.lumin(color)` / `lumin()` 包装主题色与主题实例；
内建树直接消费 `Color`/单例 → 恒等桥接即可，语义无损。

### Shim 5 — 运行时包装替换（唯一需要动控制流的）

| 外部模式 | 内建模式 |
|----------|----------|
| `MinecraftUiRuntime2612.current()` | （无需；直接用单例/静态） |
| `runtime.createScene(theme)` | `new UiScene(theme)` |
| `runtime.render(scene, layer, tree)` | `scene.beginFrame(); scene.submit(layer, rel, tree); scene.endFrame();` |
| `runtime.textMetrics()` | `BuiltInTextMetrics.get()` |
| `ClientSetting.configureMinecraftFonts(runtime)` | （内建无此钩子；删除调用点） |

样本：`scripting/lua/render/LuaRender2DService.java`（内建适配版，含 HUD 注入
HudElementHolder 的 beginFrame 窗口提交）。

## 2. 已知语义差异（适配时需人工确认）

1. **viewport 鼠标参数**：内建 `Scope.viewport` 有带/不带 mouseX,mouseY 两个重载；
   上游部分调用点省略鼠标 → 悬停判定行为需逐点核对。
2. **triangle 语义**：内建 `(centerX, centerY, size, progress)` vs 上游 `(x, y, w, h)`；
   EpsilonBC 的 LuaUiContext 用 `center=x+w/2, size=max(w,h), progress=1` 映射。
3. **texture UV**：内建必须显式 UV；上游有整图重载（已补 shim 3）。
4. **UiScene 生命周期**：内建无 runtime 失效概念；Screen.removed 时手动 `close()`。

## 3. 对 OpenLumin 兼容层的启示

1. 提供 `lumingraphics-ui` 的 **类型别名/依赖倒置**（让消费方 import OpenLumin 包名）
   即可吃下 90% 迁移成本——剩余 10% 是 Shim 5 的控制流改写。
2. `UiTextMetrics` 建议同时保留 `TtfFontLoader` 与 `String` 双重载，
   并在 Javadoc 明确 null 歧义规则。
3. `nodeCount()`/空帧跳过、`viewport` 鼠标重载这类"边角便利 API"
   是真实消费者（Epsilon GUI + Lua）实际依赖的，不是过度设计。
