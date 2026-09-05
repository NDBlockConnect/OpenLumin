# WP-1 设计草案：区块引擎接入层（LuminChunk）

# WP-1 Design Draft: Chunk Engine Integration Layer (LuminChunk)

> 状态：M1 已实现（2026-08-31，lumin-chunk/ 模块，纯 CPU 自测通过）· M2 已实现（2026-08-31/09-01，
> Store 账本层入 lumin-chunk + 26.1.2/26.2 双基线 GPU 层，Gradle 全链路编译验证通过）·
> 参照：`_refers/sodium`（compile/executor/render）·
> GitHub@NDBlockConnect | BlockConnect@StarsailsClover

---

## 1. 目标与边界 / Goals & Boundaries

**目标**：以**纯库 API**（无游戏行为）提供区块网格的构建、上传与渲染能力，
供 OpenLumin P0 能力超集（WP-2 Shaderpack 宿主、WP-3 render graph）与下游消费方使用。

**边界（不做）**：方块状态解析、光照传播计算、区块事件监听——这些属游戏侧，
由消费方（或未来 LuminWorld 模块）桥接进本层。

## 2. 三层架构 / Three-Layer Architecture（Sodium 参照映射）

```
┌─────────────────────────────────────────────────────┐
│ Build 层  LuminChunkBuilder                          │
│  任务化多线程网格构建（Sodium ChunkBuilder 参照）        │
│  LuminChunkJob/JobTyped/JobQueue/JobResult            │
│  Worker 线程本地上下文 + 任务窃取 + 忙碌度反馈            │
├─────────────────────────────────────────────────────┤
│ Store 层  LuminChunkStore                            │
│  区域(Region)化 GPU 缓冲管理（Sodium region/storage）   │
│  基于 LuminRingBuffer/GpuBuffer（26.x 能力映射）        │
│  Arena 分配 + 帧内 staging（Sodium gpu/arena 参照）     │
├─────────────────────────────────────────────────────┤
│ Render 层  LuminSectionRenderer                      │
│  Section 级 render pass 提交（多 draw/间接 draw 预留）   │
│  26.1.2: drawIndexed(经典序) / 26.2: 新参数序           │
└─────────────────────────────────────────────────────┘
```

## 3. 核心 API 草案 / Core API Draft

```java
// ── Build 层 ──
public final class LuminChunkBuilder implements AutoCloseable {
    public LuminChunkBuilder(LuminVertexType vertexType, int threads);
    // 类型化任务：TASK 产出 OUTPUT；important 插队；consumer 回调
    public <T extends LuminChunkTask<O>, O extends LuminBuildOutput>
        LuminChunkJobTyped<T, O> scheduleTask(T task, boolean important,
                                              Consumer<LuminChunkJobResult<O>> consumer);
    public boolean isQueueEmpty();
    public float getBusyFraction(long frameDuration); // 节流依据
    public void shutdown();                           // 优雅停机（drain）
}

public interface LuminChunkTask<O extends LuminBuildOutput> {
    O build(LuminBuildContext context, CancellationToken cancel);
}

public interface LuminBuildOutput extends AutoCloseable { /* section 网格数据包 */ }
public interface CancellationToken { boolean isCancelled(); }

// ── Store 层 ──
public final class LuminChunkStore implements AutoCloseable {
    public LuminChunkRegion acquireRegion(long regionKey);   // 区域化缓冲
    public void retireRegion(long regionKey);                // 帧末回收（审计规则：幂等）
    public LuminStagingArena staging();                      // 帧内上传 arena
}

// ── Render 层 ──
public final class LuminSectionRenderer {
    // 26.1.2 与 26.2 的 drawIndexed 参数序差异封装在此唯一出口（教训：参数序必须单点封装）
    public void drawSection(RenderPass pass, LuminSectionDraw draw);
    public void drawSectionsIndirect(CommandEncoder enc, LuminSectionBatch batch); // M3
}
```

## 4. 双基线差异处理 / Dual-Baseline Handling

| 差异点 | 26.1.2 | 26.2 | 封装策略 |
|---|---|---|---|
| drawIndexed 参数序 | 经典序 | `(indexCount, icount, firstIdx, baseVtx, baseInst)` | `LuminSectionRenderer` 唯一出口 |
| 顶点格式 | 元素注册 | 字符串语义名 + GpuFormat | `LuminVertexType` 按基线提供实现 |
| uniform 绑定 | withUniform | BindGroupLayout | Section 材质经平台抽象绑定 |
| 缓冲映射 | CommandEncoder.mapBuffer | GpuBuffer.map | LuminRingBuffer 已适配（保持） |

## 5. 分期交付 / Milestones

- **M1（Alpha 2）**：Build 层 API + 单线程参考实现 + 单元测试（网格生成正确性、取消语义、
  队列节流）——纯 CPU，不触 GPU，规避环境性验证风险。
  **✅ 已实现（2026-08-31）**：`lumin-chunk/` 模块（根工程子项目，零 MC 依赖，Java 21）。
  交付：`LuminChunkBuilder`（N Worker + 全局 important 队列 + 轮转派发 + 队尾窃取 + 忙碌度节流 +
  优雅停机 drain）、`LuminChunkTask`/`LuminBuildOutput`/`LuminBuildContext`/`LuminChunkJob`/
  `LuminChunkJobResult`、`CancellationToken`、`BusyTracker`（时间戳账本，合成时间可单测）。
  自测：`LuminChunkM1SelfTest`（9 节，零依赖运行器，`gradlew :lumin-chunk:selfTest` 或
  `java -cp` 直接跑）覆盖网格正确性/扇出窃取/important 优先/排队取消/构建中取消/失败隔离/
  BusyTracker 数学/忙碌度范围/停机排空，连续 10 轮全绿。
  **与草案 v0.1 的偏差（记录在案）**：`scheduleTask` 签名简化为 `<O> scheduleTask(LuminChunkTask<O>,
  boolean, Consumer<LuminChunkJobResult<O>>)`（草案的 `<T extends LuminChunkTask<O>, O>` 对 lambda
  推断不友好且无增益）；构造器暂为 `(int threads)`，`LuminVertexType` 类型化推迟到 M2 随 Store 层
  一起定形；契约补充：`isQueueEmpty()`=无排队任务（不含正在执行的），consumer 在完成信号之后
  于 Worker 线程异步回调（await 不保证 consumer 已返回），取消的已构建产出由构建器代为关闭。
- **M2**：Store 层 + Render 层（26.1.2 基线先行，26.2 跟进）；与 LuminRenderTarget/后处理链集成。
  **✅ 核心已实现（2026-08-31）**：
  - **Store 账本层（lumin-chunk，纯 CPU 零 GPU 依赖）**：`store/LuminRegionAllocator`
    （first-fit 空闲链分配器：对齐/拆块/前后合并/统计，空间不足返回 -1——扩容决策留给持有方）、
    `store/LuminChunkStoreLedger`（regionKey→分配器对 + acquire 幂等/retire 幂等/retire 后
    acquire 同 key=重建）、`store/LuminSectionAllocation`（槽位 record）。自测 4 节并入
    `LuminChunkM2SelfTest`（聚合 M1 回归），javac 连跑全绿。
  - **26.1.2 GPU 层（fabric-26.1.2 新增 io.github.openlumin.chunk 包）**：
    `LuminSectionDraw`（不可变绘制数据包）、`LuminSectionRenderer`（**drawIndexed 唯一出口**，
    javadoc 载双基线参数序对照表）、`LuminChunkStore`（regionKey→vertex/index GpuBuffer 对 +
    map 写入上传（LuminRingBuffer 同款路径）+ 槽位分配/归还 + retire 立即销毁；容量不足抛出，
    不自动扩容——重放属游戏侧边界）。
  - **工程接线**：lumin-chunk 经 `:lumin-chunk:publishToMavenLocal` 分发（artifactId
    OpenLumin-lumin-chunk），fabric-26.1.2 加 mavenLocal() 仓库 + implementation 依赖。
  - **⚠️ 26.1.2 drawIndexed/draw 真实签名（javap 字节码实证，本轮核实）**：
    `drawIndexed(baseVertex, firstIndex, indexCount, instanceCount)`、
    `draw(firstVertex, vertexCount)`（instanceCount 恒 1）。证据链：GuiRenderer 压栈序
    (baseVertex, 0, indexCount, 1) → GlRenderPass 转发 → GlCommandEncoder.drawFromBuffers 四分支
    GL 消费（glDrawElementsInstancedBaseVertex(count, first×indexBytes, baseVertex, instances) /
    _drawArrays(mode, a, c)）。与 26.2 序差异巨大（26.2=drawIndexed(indexCount, instanceCount,
    firstIndex, baseVertex, baseInstance)）——单点封装必要性实证。另发现 26.1.2 已有
    `drawMultipleIndexed`（M3 多 draw 的 API 面现成）。
  - **26.2 GPU 层（fabric-26.2 同包名对称实现，2026-09-01）**：差异点逐项 javap 核实后落地——
    `LuminSectionDraw` 收 `GpuBufferSlice` + 顶层 `com.mojang.blaze3d.IndexType`；
    `LuminSectionRenderer.drawSection` 以 26.2 序提交
    `drawIndexed(indexCount, 1, firstIndex, baseVertex, 0)`；`LuminChunkStore` 上传走
    `GpuBufferSlice.map`（26.2 重构路径，非 encoder.mapBuffer）。javac + loom compileJava
    （Gradle 9.7.0 + Loom 1.18，daemon JVM 25）双绿。另实证 26.2 vanilla 已有
    `multiDrawIndexed`/`drawIndexedIndirect`/`multiDraw`/`drawIndirect`（M3 批量路径候选面）。
  - **待办**：游戏内渲染验证（需 mdl+Despotes 全链路；mdl 测试实例已被清理需重建）。
    （Gradle 全链路 publishToMavenLocal + 26.1.2/26.2 compileJava 已于 2026-09-01 复验全绿。）
- **M3**：多 draw/indirect、遮挡剔除接口、半透明排序（Sodium BSP 参照）。

## 6. 审计锚点（D7）

- Sodium 参照仅学习架构语义，**不移植代码**（其许可与我们的发布形态需法务确认前零拷贝）。
- 现有 🟡 资产并入前审计：LuminRingBuffer（持久映射语义）、Render3DScheduler（相机相对数学）。
- 验收用例先行：M1 的单测覆盖 = M2 GPU 实现的对照基准。

---

*GitHub@NDBlockConnect | BlockConnect@StarsailsClover*
