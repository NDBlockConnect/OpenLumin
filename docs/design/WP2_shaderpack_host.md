# WP-2 设计：Shaderpack 宿主（Iris 超集）与 Render Graph

# WP-2 Design: Shaderpack Host (Iris Superset) & Render Graph

> 状态：设计草案 v0.1（2026-09-17）· 上游：`docs/audit/P0_superset_principles.md`（Iris 原理清单 §2）·
> 依赖：WP-1（区块引擎）已完成 CPU 侧全链 · 下游：WP-3 后处理链 / WP-4 厂商层 / WP-5 插帧低延迟 ·
> 许可纪律：Iris = LGPL-3.0（**零代码移植**，仅原理复现）；OptiFine 闭源（仅格式/行为对齐）·
> GitHub@NDBlockConnect | BlockConnect@StarsailsClover

---

## 1. 目标与边界 / Goals & Boundaries

**目标**：把 OptiFine 格式的 shaderpack 承接下来并**超越 Iris** 的能力面——解析、编译到
OpenLumin 自己的管线抽象、以 render graph 编排 shadow/主 pass/后处理，并原生支持 26.x 的
现代 GPU 能力（compute / SSBO / image / indirect）。

**边界（明确不做）**：
- **不移植 Iris/OptiFine 代码**（LGPL/闭源）：只复现"格式约定 + 行为语义"；
- **不做 shaderpack 作者侧工具**（编辑器/调试器 UI）——那是 LuminUI 线（Alpha 3+）的事；
- **不做 GLSL→SPIR-V 编译器**（用平台层既有编译路径；跨后端编译链策略见 §9）。

**超集定义（相对 Iris）**：
| 维度 | Iris | OpenLumin WP-2 目标 |
|---|---|---|
| 格式 | OptiFine 子集 + Iris 扩展 | 同语法面 + **显式能力协商**（旗标 → 可用/降级/拒绝） |
| 编排 | 固定 pass 序（硬编码时序） | **render graph**（依赖声明式调度 + 资源别名 + 自动屏障） |
| 后端 | 仅 GL | 平台后端矩阵（26.2 Vulkan / 26.1.2 GL / 未来 DX12·Metal） |
| 现代能力 | compute/SSBO/image（Iris 1.4–1.6 逐步加） | 全量 + indirect compute + 多 pass 间显式依赖 |
| 运动向量/深度/抖动 | 由包自行提供（引擎不保证） | **引擎侧保证产出**（WP-5 插帧/超分的前提，见 §8） |
| 诊断 | 有限日志 | 声明式校验（解析期错误定位 + 运行期 pass 追踪） |

## 2. 格式模型 / Shaderpack Format Model（原理参照 Iris §2.1）

### 2.1 目录与程序族
```
<pack>/
  shaders/
    <program>.vsh|.fsh|.gsh|.tcs|.tes|.csh     # 源文件（按文件名约定发现）
    include/…                                   # #include 目标（本地 include 图）
    world0/ world-1/ world1/ …                  # 维度文件夹（整体替换，非合并）
    shaders.properties  dimension.properties  block.properties  item.properties  entity.properties
    lang/…
```
程序族（与 Iris 对齐，作为兼容基线）：
- 组：`setup` / `begin` / `shadow` / `shadowcomp` / `prepare` / `gbuffers` / `deferred` / `composite` / `final`；
- gbuffer 程序：basic/line/textured/textured_lit/skybasic/skytextured/clouds/terrain/terrain_solid/
  terrain_cutout/damagedblock/block/block_translucent/beaconbeam/item/entities/entities_translucent/
  entities_glowing/lightning/particles/particles_translucent/armor_glint/spidereyes/hand/weather/water/hand_water；
- shadow 程序：shadow/shadow_solid/shadow_cutout/shadow_water/shadow_entities/shadow_lightning/shadow_block；
- 数组：`deferred*` `composite*` `shadowcomp*` `prepare*` `begin*` `setup*`（编号上限见 §2.4）；
- 回退链：`Water→Terrain→TexturedLit→Textured→Basic`（**链由引擎定义**，非包声明）。

### 2.2 include 图与预处理
- `#include` 在**任何条件编译之前**展开（故 include guard 无效——与 Iris 同语义，须在文档中明示）；
- include 图做**环检测**（DFS + 路径集），自环/环路直接报错并给出闭环路径（诊断优于 Iris）；
- 预处理：`#define` 选项先作用于图，再整体展开；`#version`/`#extension` **提取到顶部回填**
  （严格驱动兼容）；
- 环境宏：`MC_VERSION`/`MC_GL_VERSION`/`MC_GLSL_VERSION`/`MC_OS_*`/`MC_GL_VENDOR_*`/
  `MC_GL_RENDERER_*`/`MC_<EXT>`/`IS_IRIS`（**兼容别名**，值为 OpenLumin 标识）/
  `LUMIN_VERSION`（自有扩展）/`MAX_COLOR_BUFFERS`/`BIOME_*`/`CAT_*`/`PPT_*`/`MC_RENDER_STAGE_*`。

### 2.3 指令面（源内 const + 魔法注释）
- 常量：`colortexNFormat`/`colortexNClear`/`colortexNClearColor`/`colortexNMipmapEnabled`/
  `shadowMapResolution`/`shadowDistance`/`shadowMapFov`/`shadowNearPlane`/`shadowFarPlane`/
  `shadowIntervalSize`/`shadowtexNMipmap`/`shadowcolorNFormat|Clear|ClearColor`/`workGroups`/`workGroupsRender`；
- 注释：`/* DRAWBUFFERS:NNNN */`（后者胜）、`/* RENDERTARGETS:… */`、`/* SHADOWRES:… */`。

### 2.4 属性文件面（shaders.properties）
开关类 / pass 类（`scale.<pass>`、`size.buffer.<b>`、`alphaTest.<pass>`、`blend.<pass>[.<b>]`、
`indirect.<pass>`、`flip.<pass>.<b>`、`program.<name>.enabled`）/ 资源类（`texture.<stage>.<sampler>`、
`customTexture.*`、`image.*`、`texture.noise`）/ 缓冲类（`bufferObject.<idx>`）/ uniform 类
（`uniform.<type>.<name>`、`variable.<type>.<name>`：**表达式求值 + 依赖拓扑排序 + 未用剪枝**）/
菜单类（`sliders`/`screen*`/`profile.*`）/ 特性类（`iris.features.required|optional`）。

**属性顺序语义**：首声明优先（OptiFine 对齐）——必须用**保序**容器实现。

## 3. 架构 / Architecture

```
             ┌──────────────────────────────────────────────┐
 pack dir →  │ 1. Discovery   目录扫描 + 维度映射             │
             ├──────────────────────────────────────────────┤
             │ 2. Preprocess  include 图 + 选项 + 预处理器    │  ← 纯 CPU 可单测
             ├──────────────────────────────────────────────┤
             │ 3. Parse       指令/属性 → ShaderpackIR      │  ← 纯 CPU 可单测
             ├──────────────────────────────────────────────┤
             │ 4. Plan        IR → PassGraph（依赖/资源）    │  ← 纯 CPU 可单测（核心）
             ├──────────────────────────────────────────────┤
             │ 5. Compile     PassGraph → LuminRenderPipelines│ ← 平台层
             ├──────────────────────────────────────────────┤
             │ 6. Execute     RenderGraph 每帧调度 + 屏障     │ ← 平台层
             └──────────────────────────────────────────────┘
```

**分层原则（可测性优先）**：1–4 层**纯 CPU、零 GPU 依赖**，放 `lumin-shaderpack/`（新模块，
与 `lumin-chunk` 同级），可像 WP-1 一样全量单测；5–6 层在平台模块内（26.1.2/26.2）。

### 3.1 ShaderpackIR（第 3 层产物）
```java
public record ShaderpackIR(
    PackMetadata metadata,                    // 包名/版本/来源
    Map<LuminProgramId, ProgramSource> programs,
    PackDirectives directives,                // 全局开关、scale、blend、buffer 尺寸…
    Map<LuminTargetId, TargetSpec> targets,   // colortex*/depthtex*/shadowtex*/shadowcolor*
    List<UniformDeclaration> customUniforms,  // 表达式 + 类型 + 依赖
    Set<LuminFeatureFlag> requiredFeatures,
    Set<LuminFeatureFlag> optionalFeatures,
    List<Diagnostic> diagnostics) {}          // 解析期诊断（含位置）
```

### 3.2 PassGraph（第 4 层产物，核心）
```java
public final class PassGraph {
    // 节点 = pass；边 = 资源依赖（读/写），由 DRAWBUFFERS 与采样器引用推导
    public List<PassNode> topologicalOrder();
    public Set<ResourceHazard> hazards();          // 读后写/写后读 → 屏障需求
    public Map<ResourceId, ResourceLifetime> lifetimes();  // 别名机会（WP-3）
}
public record PassNode(LuminProgramId program, int index, Set<ResourceId> reads, Set<ResourceId> writes,
                       ViewportSpec viewport, BlendSpec blend, AlphaTestSpec alphaTest) {}
```

**语义要点**：
- **传统 pass 序是 PassGraph 的一个特例**：把 Iris 固定时序表达为显式依赖边，从而既能兼容
  Iris 包（按同序调度、结果一致），又能让扩展包声明额外依赖（超集）；
- 资源写入集来自 `DRAWBUFFERS/RENDERTARGETS`，读取集来自 shader 中声明的采样器 + 光线步进等；
- 屏障/别名由 hazards/lifetimes 推导（WP-3 复用同一 `PassGraph`，避免两套编排逻辑）。

## 4. 渲染图执行 / Render Graph Execution（第 6 层）

每帧：
1. **帧前**：`RenderGraph.beginFrame(camera, viewport)`；尺寸变化 → 重建资源（对齐 Iris "resize 才重建"）；
2. **资源分配**：colortex/depthtex/shadowtex 惰性分配；**main/alt 乒乓**（写时选 alt、采样取反）；
   别名分析复用已死资源（WP-3 的收益点）；
3. **清屏**：按 (尺寸, 清屏色) 分组批处理，每批 ≤ `GL_MAX_DRAW_BUFFERS`，alt/main **各一遍**；
4. **pass 调度**：按 topologicalOrder 执行；shadow 分支先于主分支；
5. **屏障**：由 hazards 在前置 pass 后插入（内存屏障/依赖）；
6. **present 交接**：`final` 后交回主帧流程。

**帧序（与 Iris 对齐的默认序，作为 PassGraph 的默认依赖集）**：
```
clear images → shadow compute → setup(compute) → begin → shadow → prepare
→ 不透明/镂空 gbuffers → (beginHand) → deferred → 半透明 gbuffers → composite → final → 色彩空间
```

## 5. 资源模型 / Resource Model（原理参照 Iris §2.3）

| 资源 | 规格 | 备注 |
|---|---|---|
| colortex0..31 | 默认 RGBA；`colortexNFormat` 可改；清屏色默认 0=雾色(α=1)/1=白/其余透明黑 | 惯例 16 个（0–15）+ legacy 别名 gcolor/gdepth/gnormal/composite/gaux1–4 |
| depthtex0/1/2 | 世界深度 / 半透明前拷贝 / 手部前拷贝 | 拷贝策略：首次 `glCopyTexImage2D`、后续 blit |
| shadowtex0/1 | 全阴影深度 / 排除半透明的阴影深度 | 单张正交阴影图（§6） |
| shadowcolor0..N | 默认 2（OF 兼容）/ 扩展至 8 | 由能力旗标决定 |
| SSBO（bufferObject.N） | 尺寸 + 初始数据文件 | `indirect.<pass>` 支持间接派发 |
| image（`image.*`） | load/store 图像 | 需 `CUSTOM_IMAGES` 能力 |

**能力旗标**（`LuminFeatureFlag`）：`SEPARATE_HARDWARE_SAMPLERS` / `HIGHER_SHADOWCOLOR` /
`CUSTOM_IMAGES` / `PER_BUFFER_BLENDING` / `COMPUTE_SHADERS` / `TESSELLATION_SHADERS` /
`ENTITY_TRANSLUCENT` / `REVERSED_CULLING` / `BLOCK_EMISSION_ATTRIBUTE` / `CAN_DISABLE_WEATHER` /
`SSBO` / `FADE_VARIABLE` / `TEXTURE_FILTERING`。
**协商语义**：`required` 不满足 → **明确禁用 shader 并给出原因**（不静默降级）；
`optional` 不满足 → 以 `false` 提供给包（包可自行分支）。

## 6. 阴影 pass / Shadow Pass（原理参照 Iris §2.5）

- **单张正交阴影图**，sun/moon 对齐；默认 1024²（`shadowMapResolution`，常用 1024–4096）；
- **网格吸附** `shadowIntervalSize`（默认 2 m）消抖动；
- 半平面长度 = `shadowDistance`（默认 160 m）；near/far 由 `shadowNearPlane`/`shadowFarPlane` 覆盖；
- 模型视图：绕 X 90° → 绕 Z 按阴影角 → 绕 X 按 `sunPathRotation`，再网格吸附；
- **剔除档**：DEFAULT / DISTANCE / **ADVANCED**（gbuffer 视锥 ⊗ 光向构造，仅画可能投影者）/ SAFE_ZONE；
- 渲染序：关背面剔除 → 不透明地形 → 实体/方块实体 → 半透明地形 → 拷 shadowtex1 → mipmap → shadowcomp；
- 采样别名：`shadowtex0`/`shadow`/`watershadow`/`shadowtex1`/`shadowcolorN` + 硬件滤波别名
  `shadowtex0HW/1HW`；深度 swizzle `R→RGB,1`（老包兼容）。

## 7. 与 WP-1 的接线 / Integration with WP-1

- **地形 pass**：shaderpack 的 `gbuffers_terrain*` 程序替换默认地形管线；
  `LuminSectionRenderer` 的 draw 出口不变（唯一出口原则），只换 pipeline/framebuffer；
  **顶点属性桥接**：区块量化顶点（M4b 的 20 B 布局）需要向 shader 暴露
  `mc_Entity`(blockId)/`at_midBlock`/`at_tangent`/`mc_midTexCoord`/发光值 ——
  **设计决策**：这些属性**扩展量化顶点格式**（在 20 B 之外增补可选属性块，按需启用），
  避免像 Iris 那样对每种属性组合做格式特化（保持单一格式 + 可选尾部）。
- **批次**：WP-1 的 `LuminBatchPlanner` 保持；shaderpack 只影响 pipeline 与 uniform 绑定；
- **遮挡剔除**：与 shaderpack 正交（可见集驱动批次，与 pack 无关）；
- **阴影 pass 的地形**：复用同一条区块几何，仅换 shadow 程序与阴影 framebuffer。

## 8. 运动向量 / 深度 / 抖动 产出（WP-4/WP-5 前提，前调 §7.7）

> 前调结论：SuperResolution 参照实现在纯原版路径**既不给 MV 也不抖动投影**，导致其超分质量依赖
> shaderpack；**OpenLumin 必须由引擎保证这三件事**。

- **抖动（jitter）**：引擎提供 `LuminFrameJitter`（Halton(2,3)，`phaseCount = 8 × (display/render)²`），
  **注入投影矩阵**并在渲染时生效；同时把 `jitterOffset` 与上一帧投影矩阵交给后处理；
- **运动向量（MV）**：
  - 首选：shaderpack 输出（`motion_vectors` 类输入）——按 WP-2 的 target 模型接入；
  - 回退：引擎**深度重投影**（用当前/上一帧 VP 构造 `clipToPrevClip`），保证无 shaderpack 也可用；
- **深度**：统一约定（`R32F`，反向 Z，`near=1/far=0`）+ 显式转换点（避免 SR 参照的约定歧义）；
- **接口缝**：`LuminUpscaleInputs`（color/depth/mv/exposure/jitter/matrices/near-far-fov/reset），
  WP-4/WP-5 只消费该结构；WP-2/WP-3 负责**填充**它。

## 9. 跨后端编译链 / Backend Shader Compilation（决策待定项）

- GL 后端：GLSL 直接编译（26.1.2 基线）；
- Vulkan 后端（26.2）：走平台层既有 SPIR-V 路径；
- DX12/Metal（D3）：GLSL→SPIR-V→(DXIL/MSL)，或按后端双源——**Alpha 2 出决策文档**（ROADMAP §4）；
- WP-2 的抽象：**PassGraph 之上不做后端特化**，编译在平台层 `LuminShaderCompiler` 内完成。

## 10. 分期交付 / Milestones

- **M1（纯 CPU，可全量单测）** ✅ **已实现（2026-09-17）**：模块 `lumin-shaderpack/`——
  目录发现 + include 图（含环检测与缺失目标追踪）+ 属性/指令解析 + `ShaderpackIR`；
  自测 10 节全部使用**合成夹具**（仓库不存第三方 pack），javac 与 Gradle selfTest 全绿。
  实现期修复 4 个真实缺陷（include 图覆盖面、缺失 include 未报告、续行空白、属性去引号），
  详见 `memory/FACT.md` 的「WP-2 M1 解析层落地」节；另确认**维度文件夹变体共享程序标识**
  （按维度在编译期选择）这一语义细节。
- **M2（纯 CPU）** ✅ **已实现（2026-09-17，提交 41ebf3e）**：`PassGraph` 构建（依赖推导、hazard
  分析、拓扑序）+ 与 Iris 默认时序的**等价性验证**（同包语义下拓扑序必须能复现 Iris 固定序）；
- **预处理层（第 2 层）** ✅ **已实现（2026-10-05，提交 4595489）**：`ShaderpackPreprocessor`——
  include 展开（include guard 无效语义回归锁定）、`#version/#extension` 顶部回填（含冲突检测）、
  选项/环境 define 注入；`ShaderpackIR` 携带 include 图、`LuminProgramSource` 记录阶段源路径；
- **M3（平台层）** ✅ **首片（2026-10-05，提交 8cb6e12/6304818/4595489）**：
  `ShaderpackIR`+`PassGraph` → 逐程序 `RenderPipeline`（26.1.2 GL 先行）。合成族 pass 采用
  引擎全屏三角形约定（`gl_VertexID`、EMPTY+TRIANGLES，与 vanilla `minecraft:core/screenquad`
  同构）；缺 vsh 的包回退内建 `openlumin:shaderpack/_fullscreen`；部署源经预处理（include 展开 +
  版本回填）；blend 指令 int 编码 → 26.1.2 枚举。**compute 程序显式延后**（26.1.2 pipeline 包
  无计算管线抽象，javap 实证）；几何 pass 延后至 M7（WP-1 接线）；
- **M4（平台层）**：资源模型（colortex/depthtex/shadowtex + 乒乓 + 清屏批处理）+ 默认帧序执行；
- **M5（平台层）**：阴影 pass（正交 + 吸附 + 三种剔除档）；
- **M6（能力协商核心）** ✅ **已实现（2026-10-05，提交 f35e480）**：纯 CPU
  `LuminCapabilityNegotiator`（required 缺失 → 明确拒绝并列出旗标，绝不静默降级；optional
  拆分为 provided/unavailable），26.1.2 编译器接入（拒绝即无管线 + ERROR 诊断）；
  **运行期 pass 追踪与解析期错误定位增强待做**；
- **M7（集成）**：与 WP-1 地形/实体接线 + `LuminUpscaleInputs` 填充（§8）+ 26.2 Vulkan 跟进。

## 11. 验收 / Acceptance

- **格式兼容**：能解析并执行一份**公开的 OptiFine 格式 pack**（不打包进仓库；测试时由使用者提供，
  仅记录"是否通过"与失败原因）——**仓库内不存第三方 pack**（许可与体积纪律）；
- **能力面**：Iris 特性清单逐项对照表（可解析/可执行/明确不支持 + 原因）；
- **正确性**：与 Iris 同场景 A/B 截图对比（光照/阴影/后处理观感一致）；
- **性能**：render graph 的别名/屏障不得引入额外 pass；与 Iris 同场景帧率 ≥100%；
- **审计锚点（D7）**：Iris 仅原理参照（LGPL 零移植）；我们的解析器/PassGraph/执行器**自研**，
  第三方 pack 仅作运行期验证输入。

## 12. 风险 / Risks

1. **格式面漂移**：OptiFine 语法无正式规范（以 Iris 实现为准）→ 以"能力清单 + 逐项验收"固定边界；
2. **时序等价性**：render graph 的自由度可能改变观感 → M2 强制"Iris 默认序可复现"作为回归门槛；
3. **平台差异**：26.1.2 GL 与 26.2 Vulkan 的 target/sampler 语义差异 → 资源模型抽象先行、
   后端特化后置（M3/M4 先在 26.1.2）；
4. **许可**：绝不引入 Iris/OptiFine 代码或资源；测试 pack 不入库。

---

*本设计以 `docs/audit/P0_superset_principles.md` 的 Iris 原理清单为基线，逐项表述为可实现规格。*
*GitHub@NDBlockConnect | BlockConnect@StarsailsClover*
