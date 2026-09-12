# P0 审计：渲染能力超集（Sodium/Iris/Embeddium 对照）

# P0 Audit: Rendering Capability Superset (vs Sodium/Iris/Embeddium)

> 状态：审计启动（架构侦察完成，逐项深审待续）
> 参照仓库：`_refers/{sodium,iris,embeddium}` · GitHub@NDBlockConnect | BlockConnect@StarsailsClover

---

## 1. Sodium 架构地图（区块渲染引擎参照）

源：`_refers/sodium/common/src/main/java/net/caffeinemc/mods/sodium/client/`

| 包 | 职责 | 对 OpenLumin 的映射 |
|---|---|---|
| `render/chunk/compile/{buffers,estimation,executor,pipeline,tasks}` | 区块构建管线（任务化、预算化执行器） | WP-1 区块引擎接入层的**执行器模型**参照 |
| `render/chunk/terrain/material/parameters` | 地形材质参数化 | 材质通道设计参照 |
| `render/chunk/translucent_sorting/{bsp_tree,quad,trigger}` | 半透明排序（BSP 树 + 触发器） | 半透明排序能力（OpenLumin 当前 ❌） |
| `render/chunk/occlusion` | 区块遮挡 | 与 EntityCulling 参照合并研究 |
| `render/chunk/{region,storage,tree}` | 区域化管理/存储/树 | 区块存储抽象 |
| `render/chunk/vertex/{builder,format,impl}` | 顶点构建与格式 | 与 LuminVertexFormats 对接点 |
| `gpu/arena/staging`、`gpu/device/{backend,batch,context}` | GPU 内存 arena + 设备抽象 | **LuminPlatform 后端化改造的直接参照**（与 Arc3D 对照研究） |
| `model/quad`、`model/light/{flat,smooth}` | 四边形/光照数据模型 | 光照数据管线参照 |
| `render/chunk/async/{AsyncRenderTask,CullResult,CullTask}` | 异步渲染任务与剔除 | WP-3 异步化参照 |

**关键洞察**：Sodium 的 `gpu/device/backend` 与 Arc3D 的 backend 模块、PrismRHI 的 backend 概念三者同构——
RHI 后端化是社区共识方向；OpenLumin 的 LuminPlatform 收敛应吸收三者语义（borrowed/rebuilt 生命周期、arena 分配、batch 提交）。

## 2. Iris 架构地图（Shaderpack 宿主参照）

源：`_refers/iris/common/src/main/java/net/irisshaders/iris/`

| 包 | 职责 | 对 OpenLumin 的映射 |
|---|---|---|
| `pipeline` | 渲染管线调度（多 pass 编排） | WP-2 render graph 的编排参照 |
| `shaderpack` | pack 解析/加载 | WP-2 解析器（Iris 语法超集目标） |
| `shadows` | shadow pass | shadow 渲染路径 |
| `uniforms` | uniform 系统（自定义 uniform/семплер） | 与 LuminRenderSystem.writeTransform 体系对接 |
| `targets` | render targets 管理 | 与 LuminRenderTarget 对接 |
| `gl` | GL 层操作 | 平台层职责（我们的 LuminPlatform） |
| `compat` | 兼容层（Sodium/OptiFine 等） | 兼容策略参照 |
| `pbr`、`pathways`、`layer`、`parsing`、`features` | PBR/渲染路径/层/解析/特性 | Alpha 3 特性线 |

**关键洞察**：Iris 的 `pipeline`+`shaderpack`+`targets` 三分即 WP-2 的骨架；
其 `docs/guide.md` 是 shaderpack 语法的能力清单来源（超集目标的逐项对照基础）。

## 3. Embeddium（分支差异参照）

单 src 树（Forge 系组织）。审计重点：它与 Sodium 上游的**分叉补丁集**（Forge/NeoForge 适配 +
性能微调），用于 P0 的 NeoForge 路线兼容决策。

## 4. 能力矩阵（初判 → 深审待办）

> **深审已完成（2026-09-12）**：五竞品原理级研读结果见
> [`P0_superset_principles.md`](P0_superset_principles.md)（Sodium/Iris/OptiFine/SuperResolution/Nvidium
> 原理清单 + 许可红线 + 超集矩阵 + 原理级提升要点）。本表"深审待办"列的结论已并入该文件 §6/§7。

| 能力 | Sodium 参照 | Iris 参照 | OpenLumin 现状 | 深审待办 |
|---|---|---|---|---|
| 区块构建管线 | compile/* | — | ❌ | 读 compile/executor+pipeline，产出 WP-1 设计 |
| 半透明排序 | translucent_sorting (BSP) | — | ❌ | BSP vs 每 quad 深度排序权衡 |
| GPU arena/设备抽象 | gpu/* | — | 🟡（LuminRingBuffer 仅3槽） | 与 Arc3D granite 对照 |
| 遮挡剔除 | occlusion + async | — | ❌ | 合并 EntityCulling 研究 |
| Shaderpack 解析 | — | shaderpack/ | ❌ | 语法能力清单（docs/guide.md）逐项对照 |
| 多 pass 编排 | — | pipeline/ | ❌ | render graph 设计输入 |
| Shadow pass | — | shadows/ | ❌ | 阴影目标/矩阵管理 |
| Uniform 系统 | — | uniforms/ | 🟡（writeTransform） | 自定义 uniform 协议 |
| Render targets | — | targets/ | 🟡（LuminRenderTarget） | MRT/尺寸链 |
| 兼容层 | — | compat/ | — | 兼容策略（最后做） |

## 5. 下一步（按序）

1. 深读 `sodium compile/executor + pipeline` → WP-1 设计文档（区块引擎接入层 API 草案）
2. 深读 `iris pipeline + shaderpack` → WP-2 render graph 设计文档
3. `iris docs/guide.md` → Iris 语法能力清单（超集目标 checklist）
4. Embeddium fork diff → NeoForge 路线兼容决策
5. 每项产出后更新本文件与 ROADMAP_v26.md 的 WP 状态

---

*审计纪律（D7）：本文件仅记录"已读到的结构"；行为级结论须以运行验证用例为准。*
*GitHub@NDBlockConnect | BlockConnect@StarsailsClover*
