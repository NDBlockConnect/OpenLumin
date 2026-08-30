# OpenLumin v26.0 大版本规划（决议版 v2）

# OpenLumin v26.0 Major Version Roadmap (Decision-locked v2)

> 状态：战略决议已锁定（2026-08-26） · Status: strategy locked
> 决议人：StarsailsClover · 执行：NDBlockConnect · GitHub@NDBlockConnect | BlockConnect@StarsailsClover

---

## 0. 战略决议 / Strategic Decisions（本文件为准，覆盖 v1 研究稿）

| # | 决议 | 内容 |
|---|------|------|
| D1 | **UI 自研** | 走 ModernUI 类路线：自研 UI 引擎，内置 Skia 类动画库 + 类 CSS 语言 **LuminLang**；不集成 ModernUI |
| D2 | **超分自研** | 自研类 SR 实现（FSR2/SGSR2 类），不兼容、不引入 superresolution 代码 |
| D3 | **双新后端** | DX12 与 Apple Metal 一并纳入 OpenLumin 后端矩阵（与 GL/Vulkan 并列） |
| D4 | **地形网格入库** | NoCubes 类地形网格化纳入库 API，目标**超越 NoCubes** |
| D5 | **P0 首要** | 首要目标：**Iris Shaders / OptiFine / Sodium / Embeddium 能力超集**，并含 Nv/AMD 显卡优化、插帧、低延迟、渲染机制优化 |
| D6 | **新增三线** | (a) BlockBuster 类全家桶：动画导演/渲染/导出；(b) 3D 模型支持（游戏内实体、玩家皮肤；类 YesSteveModel 兼容）；(c) 游戏内录制 + CS2 类 Demo 系统 |
| D7 | **严格审计** | 忽略（不信任）已有实现的部分代码，逐项严格审计后并入 |

---

## 1. P0：渲染能力超集 / Rendering Capability Superset（首要）

**目标定义**：OpenLumin 单库同时覆盖并超越 Sodium（性能）、Iris（shaderpack 管线）、OptiFine（兼容面+特性）、Embeddium（分支生态）的能力面，且首发即带厂商级优化。

### 1.1 能力矩阵（审计基线 / audit baseline）

> 审计方法：以四竞品的公开功能清单逐项列矩阵，标注 OpenLumin 现状
> （✅ 已有 / 🟡 部分（须严格审计）/ ❌ 缺失），每项给出实现要点与验收用例。

| 能力域 | 代表能力 | OpenLumin 现状（审计初判） |
|---|---|---|
| 区块渲染引擎 | 分区 draw call、multi-draw、贪心面剔除、GPU 驱动 | ❌（当前仅 2D/3D 图元与后处理；区块引擎属游戏侧接入） |
| Shaderpack 管线 | 多 pass 组合、shadow pass、自定义 uniform/буфер、CS 阶段 | ❌（拥有 RenderPipeline 抽象与后处理链雏形 🟡） |
| 兼容面 | OptiFine shaderpack 语法、CTM/连接纹理、RandomEntities、自定义天空 | ❌ |
| 实体渲染优化 | 距离/遮挡剔除、实例化 | 🟡（有 Render3DScheduler，无剔除/实例化） |
| 粒子/天气 | 异步粒子、体积天气 | ❌（参照 AsyncParticles/Rainfall/Particle Rain） |
| 后处理 | 泛光/景深/运动模糊/TAA | 🟡（Blur/FXAA/Filter/沙盒已验证；缺完整链） |
| 超分/插帧 | FSR/SGSR/DLSS、帧生成 | ❌（D2 自研） |
| 低延迟 | Reflex 类低延迟模式 | ❌（Nv/AMD SDK） |
| 厂商优化 | NvAPI/AGS 集成、驱动提示、resizable BAR 提示 | ❌ |
| 文本/UI | SDF 文本、声明式 UI | 🟡（TTF 渲染已验证；UI 走 D1） |

### 1.2 实现要点（P0 工作包）

- **WP-1 区块引擎接入层**：以库 API 暴露区块网格构建/上传/多 draw 能力；审计 26.x 基线的
  VertexFormat/GpuBuffer 能力是否满足 multi-draw（26.2 的 drawIndexedIndirect 已在 vanilla API 面）。
- **WP-2 Shaderpack 宿主**：shaderpack 解析（Iris 语法为超集目标）→ 编译为 LuminRenderPipelines
  组合；shadow pass 与主 pass 的 render graph；Iris 兼容层 + OptiFine 语法兼容层分两期。
- **WP-3 后处理链**：render graph（pass 调度、资源别名、自动屏障）——当前 post 链的严格审计后重写。
- **WP-4 厂商层**：NvAPI（Nvidia：FG/Reflex/DLSS-D）与 AGS（AMD：FSR/AFMF）native 桥；
  组织规范：native 层独立 Gradle 模块 + MIT 许可（参照 superresolution 的组织方式，代码自研）。
- **WP-5 插帧/低延迟**：FG 需运动向量——依赖 WP-2 的 shaderpack 输出 MV 或深度重投影回退；
  低延迟模式：帧排队控制 + 输入采样对齐（Nv Reflex 类协议）。
- **WP-6 严格审计制度**：上述所有"已有实现"（🟡 项）先审计后并入：逐文件列清单 →
  行为验证用例 → 通过才允许进入新管线；不通过的重写（D7）。

### 1.3 验收（P0 Definition of Done）

- 同场景 A/B：与 Sodium 同屏帧率 ≥ 100%（宁可保守发布）；
- Iris 示例 shaderpack（如 Complementary 精简集）可加载运行；
- OptiFine 语法子集：CTM/自定义天空可用；
- Nv/AMD：FG 与低延迟模式在各自驱动面板可见并可编程开关；
- 全部验收经 Despotes WS 自动化截图/指标流水（复用本轮建设的管线）。

---

## 2. UI 线（D1）：LuminUI = ModernUI 类自研

- **LuminUI** 引擎：UI 树/布局/MVVM/输入，对齐 ModernUI 的成熟面而不引入其代码。
- **Skia 类动画库**（内置）：矢量图形、路径测量、混合模式（42 种 Photoshop 混合对齐 ModernUI 3.10 的方向）、
  关键帧/过渡框架；GPU 加速（走 LuminPlatform）。
- **LuminLang**：类 CSS 声明式样式语言（选择器/盒模型/动画关键帧/主题变量），
  编译期校验 + 热重载；语言规范草案在 Alpha 2 产出。
- **架构约束**：单向依赖 `core → render → text → ui`（对齐 LuminGraphics 生态方向），UI 不反向依赖游戏层。
- **参照（仅学习，不抄代码）**：ModernUI 的文本整形（HarfBuzz）、SDF 文本、42 混合模式、共享上下文存活证明。

## 3. 超分线（D2）：LuminSR 自研

- 自研实现取向：**FSR2/SGSR2 类**（开放披露算法，EAS+RCAS/时空累积），DLSS-D 经 NvAPI（WP-4）以 SDK 方式接。
- 接入点：render graph 的 upsampling 节点（前于 HUD，后于 3D）；MV 来源按 WP-5。
- 验收：内/外部质量对比（PSNR/SSIM 脚本）、动态场景鬼影用例；GLES 回退路径 = 关闭（超分不进 GLES 子集）。

## 4. 后端线（D3）：DX12 + Apple Metal

- **前置条件**：LuminPlatform 完成后端无关化收敛（当前 GL 绑定点全部下沉）。
- **DX12**：native 桥（自建 COM 互操作，MinGW/CMake 或 MSVC；参照自研 native 组织模式）；
  管线/描述符/同步模型映射由 RHI 层承担。
- **Metal**：经 LWJGL 的 Metal bindings（org.lwjgl.metal，macOS）+ MSL shader 编译路径
  （Arc3D 的 compiler 模块思路可借鉴：IR→MSL）。
- **排序**：Alpha 3 末启动 DX12 spike；Metal 随 DX12 的 RHI 收敛后跟进（macOS 测试机依赖另计）。
- **shader 资产策略**：GLSL→SPIR-V→(DXIL/MSL) 编译链，或按后端维护双源——Alpha 2 出决策文档。

## 5. 地形网格线（D4）：LuminMesh 库 API

- 目标：**超越 NoCubes**——网格生成（marching cubes/greedy meshing 双策略）、LOD 链、
  与区块引擎（WP-1）共享上传路径、面级材质通道、可脚本化密度场。
- 验收：同视角下顶点数/帧时间对比 NoCubes 公开数据；API 以纯库形式（无游戏行为）。

## 6. 动画导演线（D6a）：LuminDirector 全家桶（BlockBuster 类）

- 三件套：**导演**（场景/时间轴/关键帧/多 actor 回放）、**渲染**（离线帧序列导出、相机轨）、
  **导出**（视频/帧序列编码，依赖 WP-4 native 层的编码器桥）。
- 与 D6c 录制线共享回放内核（确定性重放）。

## 7. 3D 模型线（D6b）：LuminModel

- 通用 3D 模型加载（游戏内实体、玩家皮肤挂点、动画骨架）；
- **类 YesSteveModel 兼容层**：YSM 模型格式/动画协议兼容为目标（逆向其公开格式文档为准）。
- 与 LuminDirector 的 actor 系统共享模型运行时。

## 8. 录制线（D6c）：LuminReplay + CS2 类 Demo

- 游戏内录制（输入/实体状态确定性流）→ demo 文件（版本化 schema）→ 回放（确定性重放 + 自由相机）。
- 与 LuminDirector 导演轨复用时间轴；与录制伦理/体积控制（zstd 分块、实体裁剪）纳入设计。

## 9. 阶段重排 / Phase Re-plan（以 P0 为锚）

| 阶段 | 内容（重排后） |
|---|---|
| **Alpha 2** | P0 审计与 WP-1/WP-3 启动；LuminUI/LuminLang 规范草案；render graph 设计定稿 |
| **Alpha 3** | WP-2 Shaderpack 宿主（Iris 超集首版）；LuminUI 首版；LuminSR 首版（FSR2 类）；DX12 spike |
| **Alpha 4** | P0 收口（OptiFine 兼容面、Nv/AMD FG/低延迟）；LuminMesh；DX12 后端首版 |
| **Alpha 5 → v26.0 正式** | Metal 后端；LuminDirector/LuminModel/LuminReplay 全家桶；正式发布门槛（三轮生产验证） |

## 10. 审计与治理（D7 制度化）

- 每个工作包附**审计清单**：输入=现有代码逐文件；动作=行为验证用例编写→执行→判定
  （保留/重写/废弃）；输出=审计记录入 FACT.md 对应章节。
- 已知须审计的"部分实现"：26.x 各基线的 post 链、Render3DScheduler、TTF 文本、
  RingBuffer（持久映射语义）、BlurShader（3D box 从未实战验证过全场景）。
- 审计红线：🟡 项未过审计不得进入新管线；❌ 项按 WP 新建。

---

*本文为 v26.0 大版本的执行基准；变更需经决议记录追加，不覆盖历史决议。*
*GitHub@NDBlockConnect | BlockConnect@StarsailsClover*
