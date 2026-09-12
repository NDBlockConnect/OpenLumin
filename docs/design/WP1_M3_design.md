# WP-1 M3 设计：批量提交 / 遮挡剔除 / 半透明排序（能力+性能超集）

# WP-1 M3 Design: Batch Submission / Occlusion Culling / Translucent Sorting

> 状态：M3a/M3b/M3c 全部实现（2026-09-01；批量提交 · 遮挡剔除 · 半透明排序，双基线/纯 CPU 分层验证）·
> 上游：M2 双基线 Store/Render 层（0472589, 1e3f3c5）·
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
| `RenderPass.drawMultipleIndexed` | ✅ `Collection<Draw<T>>, GpuBuffer, IndexType, Collection<String>, T` | ✅ 同签名 | 聚合多 draw，`Draw` record 双基线同构（`slot`=顶点槽位/`vertexBuffer`=**整 buffer**/`indexBuffer`/`indexType`/`firstIndex`/`indexCount`/`baseVertex` + per-draw uniform 钩子 `BiConsumer<T, UniformUploader>`）；**26.2 注意：Draw 不接受 slice，整 buffer 绑定需 baseVertex 折算 slice 偏移** |
| `RenderPass.multiDrawIndexed` | ❌ | ✅ `(IntBuffer, int, int, int)` + `(PointerBuffer, IntBuffer, IntBuffer, int)` | **实证（javap）**：单 IntBuffer 重载 = interleaved direct 特性，GL 后端直接抛 `UnsupportedOperationException`（仅 Vulkan）；三缓冲重载 = `(firstIndex 偏移数组, counts, baseVertices, 统一 instanceCount)`，GL 经 `executeDraws` 可用 |
| `RenderPass.drawIndexedIndirect` | ❌ | ✅ `(GpuBufferSlice, int)` | indirect 通路（26.2 GL+Vulkan 双后端均有实现） |
| `RenderPass.multiDraw` / `drawIndirect` | ❌ | ✅ | 非索引对应物 |
| 后端覆盖 | GL（GlRenderPass） | GL + Vulkan 双实现 | 批量路径双后端可用 |

**结论**：M3a 主路径统一 `drawMultipleIndexed`（双基线双后端全可用，语义可预期）；
26.2 的 `multiDrawIndexed`（三缓冲重载）与 `drawIndexedIndirect` 为性能优化候选路径
（需后端 capability 探测，M3a+ 评估）；26.2 的 indirect/multiDraw 额外构成 GPU 驱动
渲染（GPU-driven）的演进候选。

**M3a 实现状态（2026-09-01，双基线四重验证全绿：javac ×2 + loom compileJava ×2）**：
- 双基线新增 `LuminSectionBatch`（record：draws 列表 + sharedIndexBuffer/sharedIndexType
  自动判定）与 `LuminSectionRenderer.drawSectionsBatched`（drawMultipleIndexed 主路径）；
  逐 draw 路径保留为回退（`drawSections`/`drawSection`）。
- 26.2 的 `LuminSectionDraw` 增补 `strideBytes` 字段：批量通路把 slice 偏移按 stride
  折算进 baseVertex（偏移不对齐 stride 时抛 IAE）；单 draw 的 slice 精确绑定不受影响。

## 3. 子系统设计 / Subsystem Design

### 3a. 批量提交（Multi-Draw Batch Submission）✅ 已实现（M3a，2026-09-01）

```java
// 双基线 LuminSectionRenderer 扩展（单点出口原则不变；26.2 版 LuminSectionDraw 含 strideBytes）
public record LuminSectionBatch(List<LuminSectionDraw> draws) { /* sharedIndexBuffer/Type 自动判定 */ }
public void drawSectionsBatched(RenderPass pass, LuminSectionBatch batch);  // drawMultipleIndexed 主路径
public void drawSections(RenderPass pass, List<LuminSectionDraw> draws);    // 逐 draw 回退（保留）
```

- **主路径**：`drawMultipleIndexed`（26.1.2 唯一批量通路；26.2 同）——per-draw 绑定与
  uniform 上传在 vanilla 循环内完成，省 Java 层调用栈；IBO/IndexType 混用批次由
  vanilla 逐 draw 重绑，语义与逐 draw 等价。
- **26.2 slice 折算**：vanilla Draw 整 buffer 绑定，`baseVertex' = sliceOffset/stride + baseVertex`。
- **后续优化路径（未实现）**：26.2 `multiDrawIndexed` 三缓冲重载（需 GL/Vulkan capability
  探测——GL 的 interleaved 重载抛异常）、`drawIndexedIndirect`（GPU-driven 演进）。

### 3b. 遮挡剔除接口（Occlusion Culling，Sodium OcclusionCuller 语义参照）✅ 已实现（M3b，2026-09-01）

Sodium 模型提炼（源码研读，零拷码）：
- **三级可见性**：frustum visible ⊃ regular visible ⊃ wide visible（严格蕴含链，反向不成立）；
- **方向对位编码**：6 方向邻接可见性 → `(from, to)` 对编码进 long 位掩码
  （含 UP/DOWN、N/S、W/E 全遮挡对常量）——连通性查询 O(1)；
- **异步遍历**：DoubleBufferedQueue（读写双缓冲）+ volatile token 源 + CancellationToken，
  相机节拍独立于渲染帧（AsyncCameraTimingControl）。

OpenLumin 纯库化 API 草案：

```java
public interface LuminSectionVisibilityGraph {
    // 消费方提供：section 存在性 + 6 方向遍历位（方块遮挡图的游戏侧抽象，需线程安全）
    boolean exists(LuminSectionPos pos);
    long visibilityBits(LuminSectionPos pos);        // 6 位：bit d = 沿方向 d 连通
}
public final class LuminOcclusionCuller {
    public LuminOcclusionCuller(LuminSectionVisibilityGraph graph);   // 单线程 BFS（Sodium 同款）
    public LuminCullResult cull(LuminCullRequest request, CancellationToken cancel);
    // LuminCullRequest: 相机 section/视锥桥接/半径/occlusionEnabled（false=纯视锥对照模式）
    // LuminCullResult: 可见 section 集 + 遍历统计 + cancelled
}
```

- 复用 M1 `CancellationToken`；**遍历为单线程 BFS**（Sodium 同款——其源码 TODO 亦将多线程
  分区遍历列为未做，M3b 不做假并行；设计草案原 `threads` 参数撤销，多线程遍历列 M4 研究）。
- **实现交付**（lumin-chunk `cull/` 包，纯 CPU 全量单测）：`LuminGraphDirection`（6 方向 +
  opposite）、`LuminVisibilityEncoding`（36 位有向对编码 + 3 轴全遮挡对掩码 + 6 位遍历位）、
  `LuminSectionPos`（21 位/轴打包，越界抛）、`LuminSectionVisibilityGraph`（消费方桥接：
  exists + visibilityBits，要求线程安全）、`LuminFrustumTest`（视锥桥接）、`LuminCullRequest`
  （相机/视锥/半径/开关）、`LuminCullResult`（可见集 + 遍历统计 + 取消标记）、
  `LuminOcclusionCuller`（BFS：视锥失败不阻断扩展——相机身后仍是通路；取消逐迭代检查）。
  自测 9 节入 `LuminChunkM3SelfTest`（聚合 M2+M1），javac 5/5 + Gradle selfTest 全绿；
  期间修正 3 处测试断言/语义错（位编码差值、墙体层可达性、取消检查粒度）。
- **衔接**：可见集 → 批次构建（3a 只为可见 section 生成 draw）→ 未可见 section 的
  Store 槽位可回收（retire 决策留消费方）。
- 验收：对全量渲染的 draw call 削减率与 Sodium 同场景对齐（±5%）——待游戏侧桥接后验证。

### 3c. 半透明排序（Translucent Sorting，Sodium translucent_sorting 语义参照）✅ 已实现（M3c，2026-09-01）

Sodium 模型提炼：
- **数据三态**：NoData（无需排序）/ Static（拓扑序固定，法线轴对齐）/ Dynamic（需 BSP）；
- **BSP 树**：分区节点族（BinaryPartition / FixedDouble / MultiPartition）+
  叶子族（Single / Multi / Double），工作区持 TQuad 数组、可用索引池、
  **节点重用**（prepareNodeReuse/allowNodeReuse）与**触发法线量化**；
- **触发器**：Direct（法线直触发）+ GFNI（网格无关法线索引），相机移动触发重排序；
- **产出**：`Sorter.writeIndexBuffer(cameraPos)` —— CPU 排序直接回写索引缓冲。

**实现交付**（lumin-chunk `sort/` 包，纯 CPU 全量单测）：

```java
public enum LuminSortStrategy { NONE, STATIC_TOPO, DYNAMIC_BSP }
public interface LuminTranslucentSorter extends AutoCloseable {
    LuminSortStrategy strategy();
    List<LuminTranslucentQuad> sort(LuminCameraState camera, List<LuminTranslucentQuad> quads);
}
```

- `LuminTranslucentQuad`（4 顶点索引 + 4 角点坐标 + 面平面方程，消费方从 section 网格预计算）、
  `LuminCameraState`（section 坐标 + section 内局部坐标，BSP 侧判定的输入）、
  `NoneSorter`（原序直出）、`StaticTopoSorter`（构造期给定排列，每帧零开销）、
  `DynamicBSPSorter`（二叉树分区 + 相机侧序遍历；分区面取离几何中心最近的四边形的面平面；
  跨越/共面四边形挂在分区节点自身的 spanning 列表，**不剖切**——剖切方案列后续优化）。
- **与草案的偏差**：产出为「排序后的列表」而非直接写 ByteBuffer——索引写入格式
  （短/整、三角化与否）由消费方决定，库层不绑定索引编码；`LuminCameraPos` 更名
  `LuminCameraState` 并显式区分 section 坐标与 section 内局部坐标。
- **Store 联动**：重排序 = 索引槽位 `free` + `upload`（重传索引数据），顶点槽位不动——
  复用 M2 账本，无新 GPU 路径；
- 触发阈值（相机位移/角度变化）与每帧预算衔接 M1 `getBusyFraction` 节流（消费方策略）；
- M4+ 候选：26.2 Vulkan compute 半透明排序（GPU 侧），待 WP-3 render graph 落地后评估。

**实现期发现并修复的两个真实缺陷（自测驱动，均会导致静默错序）**：
1. **叶阈值过大 = 静默退回原序**：初版 `LEAF_THRESHOLD = 4`，而常见 section 的半透明
   四边形数 ≤4，BSP 根本不建树 → 恒为输入序（既非前后序也无告警）。阈值降为 1，
   小列表也走完整分区遍历。
2. **共面分区四边形导致无限递归**：分区四边形自身与分区面共面，`classify` 把「全共面」
   归入 BACK → 其递归子集仍含该四边形 → 反复选中同一分区面 → `StackOverflowError`。
   修复：共面（无前无后）归入 SPANNING，保证分区四边形从递归子集移除，递归严格收缩。
   该缺陷在小图（2 四边形）即复现，属 BSP 实现的经典陷阱。

另做一处边界加固：剔除遍历的距离平方由 `int` 改 `long`（±2^20 section 坐标的
距离平方可达 3×2^40，`int` 溢出会错误放行超距 section）。

自测 7 节入 `LuminChunkM3SelfTest`（NoneSorter 保序、StaticTopo 排列、BSP 前后序、
BSP 跨越四边形、BSP 全量保留、BSP 相机相关性、加 M3b/3a 回归），javac 5/5 +
Gradle selfTest 全绿。

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

- **M3a 批量提交** ✅ 已实现（2026-09-01，双基线 javac + loom 四绿）；
  验收 = 单 pass draw call 数 ≤ region 数，CPU 提交时间 ≤ 逐 draw 的 50%（待游戏侧桥接验证）。
- **M3b 遮挡剔除** ✅ 已实现（2026-09-01，纯 CPU 9 节单测）；
  验收 = 合成可见图单测通过，与全量渲染 draw call 削减对齐 Sodium ±5%（待游戏侧桥接验证）。
- **M3c 半透明排序** ✅ 已实现（2026-09-01，纯 CPU 7 节单测；含 2 个真实缺陷修复）；
  验收 = 合成场景（交叠半透明 quad）正确性用例（深度序逐帧比对参考实现）待游戏侧验证。
- **总验收**（性能超集）：Despotes WS 流水 A/B 截图 + 指标（同场景 vs Sodium：
  帧率 ≥100%、draw call ≤、CPU 帧时间分布）；行为级结论以运行用例为准（D7）。

## 5. 审计锚点 / Audit Anchors

- Sodium 参照仅学习语义（三级可见性、位编码、BSP 节点族、触发器、Sorter 回写模型），
  **零代码移植**；许可确认前不拷任何文件（含测试数据）。
  **⚠️ 许可更正（2026-09-12 前调）**：Sodium 为 **PolyForm Shield 1.0.0（含非竞争条款）**，
  非 GPL/LGPL；原理复现可行，但禁止代码移植，且避免"Sodium 替代品"商业定位表述。
- multiDrawIndexed IntBuffer 槽位语义、drawMultipleIndexed 的 uniform 上传时序：
  **实现期逐版本 javap 压实**（铁律 3/4）。
- 既有 🟡 资产并入前审计：Render2DScheduler 合批（相机相对数学）、LuminRingBuffer
  （持久映射语义）——M3a 批次构建触碰前逐文件审计（WP-6 制度）。

---

## 6. M4 构建调度增强（前调驱动的原理级提升，2026-09-12）

> 依据 `docs/audit/P0_superset_principles.md` §7.1：M1 执行器原先只有"忙碌度反馈"，
> **缺"每帧提交预算"**——这正是 Sodium 调度模型的核心。M4a 补齐。

**交付**（`lumin-chunk` 新增 `schedule/` 包，纯 CPU）：
- `LuminJobEffort`：`(effort, durationNanos)` 训练样本。
- `LuminDurationEstimator`：**在线指数衰减线性回归**（`duration ≈ slope×effort + intercept`），
  新样本权重上限 5%，工作量方差不足时只更新截距，负斜率钳平为 0；
  样本不足时退化为保守常量（5 ms）。**并发安全**（样本来自多 Worker，读取来自调度线程）。
- `LuminFrameBudget`：平均帧时长 **增量式 EMA**（每步 ≥1 ns，保证恒定输入精确收敛，
  避免朴素浮点 EMA 在目标附近停滞）+ 队列估计账目（**原子量**）+ 忙碌度 + 剩余容量 +
  上传时长预算（30% 帧时长，下限 10 ms）。
- `LuminSubmissionBudget`：**逐任务扣减**的双重约束（时长 + 上传时长/字节），
  拒绝时不扣减；本帧阻塞档忽略上传约束。
- `LuminDeferMode`：`ZERO_FRAMES / ONE_FRAME / ALWAYS_FRAME` 三档，含上传预算豁免策略。
- `LuminChunkBuilder` 集成：`scheduleTask(..., effortHint)` 提交时预算、执行后回灌实测；
  暴露 `frameBudget()` / `estimateDurationNanos()` / 估计器斜率截距；`LuminChunkJob` 增
  `effortHint()` / `estimatedDurationNanos()`。

**实现期发现并修复的两个真实缺陷**：
1. **预算账目并发丢失更新**：`onQueued`（调度线程）与 `onCompleted`（Worker 线程）并发
   修改普通 `long` → 计数漂移、永不归零（测试间歇性超时暴露）。改为 `AtomicLong` +
   CAS 钳零；估计器同步化。**教训：把"每帧账目"接进多线程执行器时，账目本身的并发性
   必须先设计**。
2. **浮点 EMA 收敛停滞**：`round(0.95·v + 0.05·target)` 在目标附近停在 target−20 ns
   永不精确到达（0.95 的浮点表示导致）。改为增量式（步长 `round(0.05·delta)`，至少 1 ns）。

自测 8 节入 `LuminChunkM4SelfTest`（估计器回退/收敛/负斜率钳平、帧预算数学、EMA 收敛与钳制、
提交预算双约束、延迟档策略、构建器集成），javac 8/8 + Gradle selfTest（M1..M4 聚合）全绿。

**后续（M4b/M4c，原理见前调 §7）**：量化顶点格式定型（≤20 B/顶点）、arena 增量碎片整理与
staging 环、多 draw 能力回退链（M5）、三级遮挡与 Morton 位树（M5）、半透明分类启发式（M6）。

## 7. M4b 量化顶点格式（前调驱动的原理级提升，2026-09-12）

> 依据前调 §7.5：顶点格式未压缩是带宽基本盘，Sodium 以 20 B/顶点（20 位量化位置 +
> 15+1 位 UV）为基准。M4b 在纯 CPU 层定型该格式（编解码器 + 单测），26.x 基线的
> shader 端反量化随 M5 GPU 层接入。

**交付**（lumin-chunk `vertex/` 包）：
- `LuminQuantizedVertexFormat`：布局常量与编解码工具。**20 字节/顶点 = 5 个 32 位字**：
  word0/1 = 位置（section 局部 (8+v)/32 归一化，20 位/轴共 60 位拆两 word）；
  word2 = RGBA8 颜色；word3 = 纹理（u/v 各 15 位 + 各 1 位渗色偏置符号）；
  word4 = 光照（block/sky 各 8 位，钳制 [8,248]）+ material 8 位 + sectionIndex 8 位。
  着色器反量化尺度 VERTEX_SCALE=32/2^20、偏移 −8。
- `LuminVertexWriter`：quad 质心记录（`beginQuad`）+ 逐顶点编码（渗色偏置可自动按质心
  判定或显式覆盖）。
- `LuminVertexReader`：对称解码（测试与工具链用；GPU 端为 shader 反量化）。

**实现期发现并修复的真实缺陷**：颜色分量打包用 `& 0xFF` **回绕**（300→44），
正确语义是**饱和钳制** [0,255]——回绕会把过曝/负值变成随机色相。已改 clamp8。

自测 5 节入 `LuminChunkM4bSelfTest`（往返精度/钳制语义/质心偏置/字节布局/步长计数），
javac 5/5 + Gradle selfTest（M1..M4b 聚合）全绿。

**后续（M4c，原理见前调 §7.4）**：arena 增量碎片整理与 staging 环。

---

*GitHub@NDBlockConnect | BlockConnect@StarsailsClover*
