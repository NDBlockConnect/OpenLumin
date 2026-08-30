# WP-1 设计草案：区块引擎接入层（LuminChunk）

# WP-1 Design Draft: Chunk Engine Integration Layer (LuminChunk)

> 状态：设计草案 v0.1 · 参照：`_refers/sodium`（compile/executor/render）·
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
- **M2**：Store 层 + Render 层（26.1.2 基线先行，26.2 跟进）；与 LuminRenderTarget/后处理链集成。
- **M3**：多 draw/indirect、遮挡剔除接口、半透明排序（Sodium BSP 参照）。

## 6. 审计锚点（D7）

- Sodium 参照仅学习架构语义，**不移植代码**（其许可与我们的发布形态需法务确认前零拷贝）。
- 现有 🟡 资产并入前审计：LuminRingBuffer（持久映射语义）、Render3DScheduler（相机相对数学）。
- 验收用例先行：M1 的单测覆盖 = M2 GPU 实现的对照基准。

---

*GitHub@NDBlockConnect | BlockConnect@StarsailsClover*
