# WP-1 M3 设计：批量提交 / 遮挡剔除 / 半透明排序（能力+性能超集）

# WP-1 M3 Design: Batch Submission / Occlusion Culling / Translucent Sorting

> 状态：设计草案 v0.1（2026-09-01）· 上游：M2 双基线 Store/Render 层（0472589, 1e3f3c5）·
> 参照：`_refers/sodium`（occlusion / translucent_sorting / async）· 零拷码纪律（D7）·
> GitHub@NDBlockConnect | BlockConnect@StarsailsClover

---

## 1. 目标与边界 / Goals & Boundaries

**目标**：在 M1（构建执行）+ M2（Store/Render 层）之上补齐区块引擎的性能三件套，
使 OpenLumin 区块渲染达到对 Sodium 的**能力超集 + 性能超集**（ROADMAP §1.3 验收：
同场景 A/B 帧率 ≥100%，宁保守发布）。

**边界（不做）**：
- 方块遮挡图数据（方块级透明性/连通性）——游戏侧（消费方）桥接，本层只消费
  "section 邻接可见位"抽象输入；
- GPU compute 排序、GPU 遮挡查询（occlusion query）——列为 M4+ 研究项（26.2 Vulkan
  compute 可用后评估）；
- NVIDIA 闭源 SDK 的实际接入（WP-4/WP-5 范围）——M3 只预留接口缝（§3d）。

## 2. Vanilla 批量 API 面（javap 实证汇总 / Verified Batch API Surface）

| API | 26.1.2 | 26.2 | 备注 |
|---|---|---|---|
| `RenderPass.drawMultipleIndexed` | ✅ `Collection<Draw<T>>, GpuBuffer, IndexType, Collection<String>, T` | ✅ 同签名 | 聚合多 draw，`Draw` record 含 `slot/vertexBuffer/indexBuffer/indexType/firstIndex/indexCount/baseVertex` + **per-draw uniform 上传钩子**（`BiConsumer<T, UniformUploader>`） |
| `RenderPass.multiDrawIndexed` | ❌ | ✅ `(IntBuffer, int, int, int)` + `(PointerBuffer, IntBuffer, IntBuffer, int)` | IntBuffer 各参数槽位语义**实现期 javap 压实**（铁律） |
| `RenderPass.drawIndexedIndirect` | ❌ | ✅ `(GpuBufferSlice, int)` | indirect 通路（Vulkan 基线原生） |
| `RenderPass.multiDraw` / `drawIndirect` | ❌ | ✅ | 非索引对应物 |
| 后端覆盖 | GL（GlRenderPass） | GL + Vulkan 双实现 | 批量路径双后端可用 |

**结论**：M3 的批量提交在两基线都有 vanilla 原生通路，无需自建命令缓冲；
26.2 的 indirect/multiDraw 额外构成 GPU 驱动渲染（GPU-driven）的演进候选。

## 3. 子系统设计 / Subsystem Design

### 3a. 批量提交（Multi-Draw Batch Submission）

```java
// LuminSectionRenderer 扩展（单点出口原则不变）
public final class LuminSectionBatch {
    List<LuminSectionDraw> draws();          // M2 的 LuminSectionDraw 扩展 per-draw uniform 钩子
    GpuBuffer sharedIndexBuffer();           // 同 region 共享 IBO 时非 null
    IndexType indexType();
}
public void drawSectionsBatched(RenderPass pass, LuminSectionBatch batch);
```

- **26.1.2 路径**：`drawMultipleIndexed`——`LuminSectionDraw` 增补可选 uniform 钩子字段，
  映射到 vanilla `Draw` record；共享 IBO 时单次 setIndexBuffer。
- **26.2 路径**：优先 `multiDrawIndexed`（firstIndex/baseVertex 列表 → IntBuffer），
  per-draw uniform 异构场景回退 `drawMultipleIndexed`；语义压实后定稿。
- **回退链**：批量 API → 逐 draw 循环（M2 路径），单点开关。
- **批次构建**：region 分组 + 索引缓冲共享判定 + 材质/管线分桶（衔接 Render2DScheduler
  的既有合批思路）。

### 3b. 遮挡剔除接口（Occlusion Culling，Sodium OcclusionCuller 语义参照）

Sodium 模型提炼（源码研读，零拷码）：
- **三级可见性**：frustum visible ⊃ regular visible ⊃ wide visible（严格蕴含链，反向不成立）；
- **方向对位编码**：6 方向邻接可见性 → `(from, to)` 对编码进 long 位掩码
  （含 UP/DOWN、N/S、W/E 全遮挡对常量）——连通性查询 O(1)；
- **异步遍历**：DoubleBufferedQueue（读写双缓冲）+ volatile token 源 + CancellationToken，
  相机节拍独立于渲染帧（AsyncCameraTimingControl）。

OpenLumin 纯库化 API 草案：

```java
public interface LuminSectionVisibilityGraph {
    // 消费方提供：section 邻接表 + 方向对可见位（方块遮挡图的游戏侧抽象）
    long visibilityBits(long sectionKey);            // 6 邻方向可见位掩码
    boolean exists(long sectionKey);
}
public final class LuminOcclusionCuller implements AutoCloseable {
    public LuminOcclusionCuller(LuminSectionVisibilityGraph graph, int threads);
    public LuminCullResult cull(LuminCullRequest request, CancellationToken cancel);
    // LuminCullRequest: 相机/视口/搜索距离（regular|local 分级）/是否启用遮挡剔除
    // LuminCullResult: 可见 section 集（frustum 标记位）+ 遍历统计
}
```

- 复用 M1 `CancellationToken` 与 Worker 池模式；剔除线程与构建线程共享忙碌度节流。
- **衔接**：可见集 → 批次构建（3a 只为可见 section 生成 draw）→ 未可见 section 的
  Store 槽位可回收（retire 决策留消费方）。
- 验收：对全量渲染的 draw call 削减率与 Sodium 同场景对齐（±5%）。

### 3c. 半透明排序（Translucent Sorting，Sodium translucent_sorting 语义参照）

Sodium 模型提炼：
- **数据三态**：NoData（无需排序）/ Static（拓扑序固定，法线轴对齐）/ Dynamic（需 BSP）；
- **BSP 树**：分区节点族（BinaryPartition / FixedDouble / MultiPartition）+
  叶子族（Single / Multi / Double），工作区持 TQuad 数组、可用索引池、
  **节点重用**（prepareNodeReuse/allowNodeReuse）与**触发法线量化**；
- **触发器**：Direct（法线直触发）+ GFNI（网格无关法线索引），相机移动触发重排序；
- **产出**：`Sorter.writeIndexBuffer(cameraPos)` —— CPU 排序直接回写索引缓冲。

OpenLumin API 草案：

```java
public enum LuminSortStrategy { NONE, STATIC_TOPO, DYNAMIC_BSP }
public interface LuminTranslucentSorter extends AutoCloseable {
    LuminSortStrategy strategy();
    // 排序产出写回调用方提供的索引缓冲（与 Store 槽位联动）
    void writeIndexBuffer(LuminCameraPos cameraPos, ByteBuffer indexBufferOut);
}
```

- **Store 联动**：重排序 = 索引槽位 `free` + `upload`（重传索引数据），顶点槽位不动——
  复用 M2 账本，无新 GPU 路径；
- 触发阈值（相机位移/角度变化）与每帧预算衔接 M1 `getBusyFraction` 节流；
- M4+ 候选：26.2 Vulkan compute 半透明排序（GPU 侧），待 WP-3 render graph 落地后评估。

### 3d. NVIDIA 技术位（WP-4/WP-5 接口缝，M3 只留缝不实现）

| 技术 | 定位 | M3 预留缝 |
|---|---|---|
| **低延迟（Reflex 类）** | WP-5：帧排队深度控制 + 输入采样对齐 | present 路径钩子接口 `LuminFramePacing`（帧提交前可插桩：fence/队列深度查询/标记位）；M3 记录缝位不实现 |
| **DLSS 4/5（DLSS-D）** | WP-4 经 NvAPI SDK 接入（闭源 SDK，native 层 MIT 独立模块）；**D2 红线**：开放路径走自研 FSR2/SGSR2 类，绝不引入 superresolution GPL 代码 | render graph 的 MV 输出槽（WP-2 shaderpack 输出运动向量，或深度重投影回退）；M3 半透明排序/批次构建的帧序编排为 MV 生成留帧内位置 |
| **RTX / 光追** | 26.2 Vulkan 基线的 `VK_KHR_ray_tracing_pipeline` 扩展研究线（D3 Vulkan 后端之后）；DLSS Ray Reconstruction 随之 | 无（记录研究项：需 WP-2 shaderpack 生态先行，M3 不占预算） |

**定位纪律**：低延迟/插帧/超分是 P0 验收项（ROADMAP §1.3），但**排序在 WP-4/WP-5**；
M3 的职责是把区块渲染的帧内编排（批次序、pass 序）整理成可插桩形态，
使后续 Reflex/DLSS 接入不需要重排区块管线。

## 4. 分期与验收 / Milestones & Acceptance

- **M3a 批量提交**：LuminSectionBatch + 双基线批量路径 + 回退链；
  验收 = 单 pass draw call 数 ≤ region 数，CPU 提交时间 ≤ 逐 draw 的 50%。
- **M3b 遮挡剔除**：LuminOcclusionCuller + 可见图抽象 + 异步遍历；
  验收 = 测试图（合成可见图）单测 + 与全量渲染 draw call 削减对齐 Sodium ±5%。
- **M3c 半透明排序**：三策略 + 触发器 + Store 槽位联动；
  验收 = 合成场景（交叠半透明 quad）正确性用例（深度序逐帧比对参考实现）。
- **总验收**（性能超集）：Despotes WS 流水 A/B 截图 + 指标（同场景 vs Sodium：
  帧率 ≥100%、draw call ≤、CPU 帧时间分布）；行为级结论以运行用例为准（D7）。

## 5. 审计锚点 / Audit Anchors

- Sodium 参照仅学习语义（三级可见性、位编码、BSP 节点族、触发器、Sorter 回写模型），
  **零代码移植**；许可确认前不拷任何文件（含测试数据）。
- multiDrawIndexed IntBuffer 槽位语义、drawMultipleIndexed 的 uniform 上传时序：
  **实现期逐版本 javap 压实**（铁律 3/4）。
- 既有 🟡 资产并入前审计：Render2DScheduler 合批（相机相对数学）、LuminRingBuffer
  （持久映射语义）——M3a 批次构建触碰前逐文件审计（WP-6 制度）。

---

*GitHub@NDBlockConnect | BlockConnect@StarsailsClover*
