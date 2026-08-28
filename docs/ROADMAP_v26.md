# OpenLumin v26.0 战略路线图

# OpenLumin v26.0 Strategic Roadmap

> 状态：v26.0 大方向定稿 · Status: v26.0 strategic direction finalized
> 修订：2026-08-26（重大架构升级） · GitHub@NDBlockConnect | BlockConnect@StarsailsClover

---

## 0. v26.0 总目标 / v26.0 North Star

OpenLumin v26.0 目标：**Minecraft 客户端渲染超集**——在功能集与性能上同时超越现有权威方案。

- **超集目标（必达）**：**Iris Shaders**（着色管线兼容 + 光影包支持）、**OptiFine**（全部特性：动态光源、连接纹理、随机实体材质、HD 字体、远景等）、**Sodium**（chunk mesh 重构 + 视锥剔除 + 不可见块裁切 + 多核生成）、**Embeddium**（Sodium 的 NeoForge/Fabric 端口 + 兼容层）的功能并集；并超越。
- **GPU 优化**：**Nvidia/AMD 系显卡优化**（vendor-specific 路径：NvAPI / AGS 检测、硬件 schema 优先、内存放置策略、shader 缓存策略）、**插帧**（frame interpolation，对 AMD FSR/Intel XeSS frame gen 与 Nvidia Frame Warp 接口）、**低延迟**（latency reduction：present-time optimization + input-to-photon 测量）、**渲染机制优化**（BDFGC、GPU 驱动路径、direct state access、persistent-mapped buffers、bindless textures）。
- **全平台 RHI**：**Vulkan**（1.0+ 基底）、**DirectX 12**（Windows + Xbox GDK 远期）、**Apple Metal**（macOS/iOS）、**OpenGL**（向后兼容旧基线）、**OpenGL ES**（Android/MobileGlues 兼容子集）。**DX12 与 Metal 一同被支持**——这是 26.0 的硬指标，意味着本库在 Windows / Mac 上是 native 一等公民。
- **现代 Skia 级动画**（自研，类 Skia）：骨骼动画、贝塞尔路径、PathMeasure、形状插值、Color 插值、Layout 过渡、MVVM 生命周期——在 Minecraft 渲染线程可达的目标。提供 LuminGraphics 级别的现代矢量/动画 API。
- **类 Skia + 类 CSS 的声明式 UI 语言 LuminLang**（自研）：基于 LuminGraphics 引擎的声明式 UI DSL（CSS 风格选择器 + 布局 + 主题），可被第三方模组用来构建 HUD/界面。比硬编码 Java/GUI 更易用。
- **类 SR 自研超分**（不兼容 superresolution）：OpenLumin 自研超分 pass，命名 `LuminSR`。命名/语义独立于 superresolution mod，避免 GPL 传染。算法参考 FSR1/2/3 + SGSR1/2 + DLSS + XeSS，但实现与调度归我们。AMD FSR Frame Generation 集成作为插帧基线。
- **类 NoCubes 自研网格**（做得比 NoCubes 更好）：OpenLumin 库 API 暴露"区块 → 任意网格"接口。算法超越 NoCubes 之处：等值面+多分辨率 LOD+异步生成+GPU 端优化+保持硬边锐利度。

---

## 1. 战略子线 / Strategic Subtracks

| 优先级 | 子线 | 关键目标 |
|---|---|---|
| P0 | **渲染超集**（Iris+OptiFine+Sodium+Embeddium） | 功能/性能超集，v26.0 主线 |
| P0 | **全平台 RHI**（Vulkan/DX12/Metal/GL/GLES） | DX12 + Metal 是硬指标 |
| P0 | **GPU 优化**（Nvidia/AMD、插帧、低延迟） | 高级游戏体验基线 |
| P1 | **类 Skia 动画**（LuminAnimation） | 现代 UI/动效基础 |
| P1 | **类 CSS UI 语言 LuminLang** | 声明式 UI DSL |
| P1 | **3D 资产与 Demo 录制**（BlockBuster 全家桶 + 模型支持） | 见 `docs/PROPOSALS.md` |
| P2 | **类 SR 自研 LuminSR** | 超分 + 插帧 |
| P2 | **类 NoCubes 自研地形网格** | 库 API 扩展 |
| P2 | **下游生态** | Project-Crystal-Fracture 等 showcase 合作 |

---

## 2. Alpha 阶段重排 / Alpha Roadmap

Alpha 编号重新规划，反映 v26.0 新目标。Alpha 1 已是历史里程碑（1.21.10/26.1.2/26.2 六目标渲染验证完成）；v26.0 正式线从 Alpha 2 开始重做。

### Alpha 2 — 全平台 RHI 与 Sodium 兼容超集

| 主题 | 内容 | 验收 |
|---|---|---|
| 2.1 RHI 后端化 | 把 LuminPlatform 抽象完善为 GL/Vulkan/Metal/DX12 四后端；现有 GL 路径先收敛（GL 4.1+ 基线对齐 26.1.2 测试矩阵） | 四后端 hello triangle |
| 2.2 块网格重构 | 借鉴 Sodium 的 chunk meshing（fan / greedy / translucent quad sorting）；接入 Iris 顶点格式兼容性 | 视觉无损 vs Sodium；同等或更优 FPS |
| 2.3 Sodium 视锥剔除 | 实现 sodium 内置的 frustum culling + 不可见面裁切 | 启用后帧时间↓ 30-50% |
| 2.4 Iris 光影接口层 | 实现 Iris shaderpack JSON 加载 + uniform 协议（mc_Projection、gbuffer samplers 等）的兼容层，使 OpenLumin 在装了 Iris 视觉包的实例上也能跑 | 跑通 ComplementaryReimagined 等主流光影包 |

### Alpha 3 — OptiFine 超集 + Embeddium 兼容

| 主题 | 内容 | 验收 |
|---|---|---|
| 3.1 OptiFine 特性全适配 | 动态光源、连接纹理、随机实体材质、HD 字体支持、远景层级、智能树叶、波浪形方块……一项项与 OptiFine 行为对齐并提供配置接口 | OpenLumin 启用了的 OpenLumin 行为 == OptiFine 启用了同选项的 OptiFine 行为 |
| 3.2 Embeddium 兼容 | 在 NeoForge 上同时以 Embeddium 替代（Embeddium 协议兼容 Sodium；我们就是 Sodium 超集，因此 26.x NeoForge 装载需等价） | 与 Embeddium 互不冲突 / 可叠加 |
| 3.3 GPU 优化（Pass 1） | 插帧基础：AMD FSR Frame Generation 适配 + Nvidia Frame Warp 集成；延迟监测（PresentMon 接口） | 帧间隔波动 -50% |
| 3.4 类 Skia 动画起步 | LuminAnimation 核心：Animation / AnimatedValue / PathMeasure / Bezier 路径动画；MVVM 框架最小可用 | 一个示范 HUD 用 LuminAnimation 平滑缩放/淡入 |

### Alpha 4 — 自研核心（S 类）

| 主题 | 内容 | 验收 |
|---|---|---|
| 4.1 LuminSR 自研超分 | FSR2-style 空间超分 pass + FSR3-style 插帧集成；不调用 superresolution mod 的代码，独立 native + Java 实现 | 4K 下 1.5x 缩放视觉等同 FSR2 Quality；插帧启用 → 帧率翻倍 |
| 4.2 LuminLang 类 CSS UI 语言 | 声明式 DSL（类 CSS 选择器 + 样式 + 布局 + 主题/动画绑定）；编译器 = LuminLang → LuminGraphics 操作码 | 一个完整 HUD（角色面板）用 LuminLang 重写，行为与现有一致 + 动画效果 |
| 4.3 GPU 优化（Pass 2） | Nvidia NvAPI 检测 + AGS 检测 → vendor-specific 路径（硬件内存预算、shader 缓存 LRU 优化、direct storage 探针）；AMD Vulkan 路径优化 | vendor 探测命中后帧时间↓ 5-15% |
| 4.4 类 NoCubes 自研网格 | 库 API：`BlockMeshProvider`（输入 = 块状态 + 邻块，输出 = 任意 mesh）；自研算法：等值面 (marching cubes) + 自适应 LOD + GPU 端 meshlet + 硬边保护 | 取代 NoCubes：视觉更锐、生成更快、GPU 端可选 |

### Alpha 4.5 — 3D 资产与 Demo 录制（详见 `docs/PROPOSALS.md`）

| 主题 | 内容 | 验收 |
|---|---|---|
| 4.5.1 3D 模型格式支持 | `LuminModel` 抽象层：玩家皮肤/实体模型统一接口；多格式加载器（BBM/BBE/BBAnim = BlockBuster 系；YesSteveModels 兼容层 = YSM legacy → 新格式运行时兼容；预留 `.geo` / `.anim` 后缀 = Bedrock 几何兼容路径） | 第三方 BBM 模型在游戏中正确加载并播放动画；YSM legacy 模型自动兼容或工具迁移 |
| 4.5.2 BlockBuster 全家桶（自研） | `LuminDirector`（导演/镜头控制）+ `LuminScene`（场景图）+ `LuminAnimator`（关键帧编辑器 + 曲线 + 导出器）；运行式录制：世界内 GUI 编排镜头；导出为 `.lumiscn`（JSON + LuminLang 脚本 + 世界帧序列） | 用 LuminDirector 在世界里编排一段玩家视角移动+动画，导出后 LuminLang HUD 可无缝回放 |
| 4.5.3 游戏内录制（类 CS2 Demo） | `LuminRecorder`：记录玩家输入流 + 服务器/世界 tick 流 + 渲染关键帧，输出为 `.lumidemo`（紧凑二进制：输入流 + 世界 delta + 摄像机轨迹 + 音频时间戳）；`LuminReplay`：时间线 UI（LuminLang 表达），支持视角切换、速度 0.25x–4x、关键帧标记 | 录制 → 关闭游戏 → 打开 .lumidemo → 时间线任意拖动回放；与 VCR-mod 等不冲突 |

### Alpha 5 — DX12/Metal 一等公民 + RHI 收敛

| 主题 | 内容 | 验收 |
|---|---|---|
| 5.1 DX12 后端（Windows + Xbox GDK 远期） | LWJGL 无官方 D3D12 绑定 → 选型：自建 COM 互操作 native（参考 superresolution 的 `native:buildNative` + MinGW/CMake 组织） | Windows 上选 RHI = DirectX 12 完整工作（光影、视锥、超分、Skia 动画、Sodium 兼容） |
| 5.2 Apple Metal 后端 | macOS / iOS native；Apple Silicon 性能优化 | macOS 原生 RHI = Metal；iOS 上能跑 OpenLumin（结合 MobileGlues 路径或纯 GLES） |
| 5.3 全平台 RHI 一致性测试 | 同一 shader / 同一 LuminLang UI 在 4 后端渲染结果像素级一致 | 截图 diff < 可视阈值 |
| 5.4 低延迟最终调优 | 输入到光子测量全链路 + present 同步策略（DX12：frame pacing；Vulkan：present modes；Metal：CADisplayLink） | 端到端延迟 -30% |

---

## 3. 旧路线项的状态 / Status of Prior Items

旧 ROADMAP v26 的"参照外部项目"研究已沉淀为前置知识，不影响新方向：

- **Arc3D** 架构参照仍然有效，但其模块化（core/engine/backend 分层）作为 LuminPlatform 后端化的具体模板是 RHI 改造的最直接学习材料。
- **ModernUI**：**决策已定——不走 ModernUI 集成路线。** OpenLumin 走自研 Skia 风格动画 + LuminLang 声明式 UI。ModernUI 项目作为公开/参考研究保留，但不做集成。
- **superresolution** 的超分算法作为 LuminSR 的参考依据；不集成其代码（LuminSR 自研避免 GPL 传染）。
- **Project-Crystal-Fracture / SlideShow** 作为下游 showcase 候选保留。

---

## 4. 风险与红线 / Risks & Red Lines

| 项 | 风险 | 对策 |
|---|---|---|
| Iris/OptiFine/Sodium 行为对齐 | 工作量巨大、易陷入逆向工程 | 优先以"协议兼容"为目标（输入/输出等价 + 配置文件格式兼容），不逆向源码；利用公开文档与社区资源 |
| DX12 native 层 | 维护负担、LWJGL 缺口 | 参考 superresolution 的 native 模块组织；CMake + MinGW；条件编译 |
| Metal 后端 | macOS-only 开发资源 | CI 矩阵含 macOS runner；先 LWJGL nativemodule 集成（如有） |
| 插帧与延迟 | 驱动差异大 | 通过 vendor API 抽象；失败时降级为静态显示 |
| LuminSR 不兼容 superresolution | 与 SR 互斥 | 文档明示：与 superresolution mod 不兼容，OpenLumin 启用了就不要再装 SR |
| LuminLang 接受度 | 新 DSL 学习成本 | 文档 + 转换工具（CSS/JSON → LuminLang） |

---

## 5. 开放决策 / Open Decisions（已收敛大半）

1. ~~UI track：自研 vs 集成 ModernUI~~ → **已定：自研 LuminAnimation + LuminLang**
2. ~~超分集成：自研 vs 协议兼容 superresolution~~ → **已定：自研 LuminSR，与 superresolution mod 不兼容**
3. DX12：自建 COM 互操作 vs 待 LWJGL 官方 vs 第三方 binding → Alpha 5 评估
4. Metal：LWJGL nativemodule（待 LWJGL 3.4 文档确认） vs 自建 → Alpha 5
5. 插帧 vendor 优先级：Nvidia Frame Warp（需 RTX） + AMD FSR FG（开放 + FSR3 许可） vs Intel XeSS-FG（学术） → Alpha 3 spike

---

*GitHub@NDBlockConnect | BlockConnect@StarsailsClover*
