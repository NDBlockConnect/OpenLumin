# P0 超集前调：五竞品原理级研读（Sodium / Iris / OptiFine / SuperResolution / Nvidium）

# P0 Superset Pre-Investigation: Principle-Level Study of Five Competitors

> 状态：前调完成（2026-09-12）· 方法：参考仓库源码研读（原理级，**零拷码**）·
> 产出：原理清单 + 许可红线 + 超集实施排序 ·
> GitHub@NDBlockConnect | BlockConnect@StarsailsClover

---

## 0. 许可红线（先于一切技术结论）

| 参照 | 许可 | 对 OpenLumin 的约束 |
|---|---|---|
| **Sodium** | **PolyForm Shield 1.0.0**（非 GPL！含**非竞争条款**） | 仅原理级可学；**禁止**代码移植（含近似转录）。且若 OpenLumin 被定位为"Sodium 替代品"商业分发，会触碰非竞争条款 → 市场定位需避让 |
| **Iris** | **LGPL-3.0** | 原理级可学；链接 LGPL 需遵守重链接与源码可得条款 → 自研实现，不静态合入 |
| **Embeddium** | LGPL-3.0（Sodium 系派生） | 同上 |
| **SuperResolution** | **GPL-3.0**（本体）+ **LGPL-3.0**（native，README 误称 MIT）+ 捆绑 NVIDIA NGX/AMD FidelityFX/Intel XeSS/Qualcomm SGSR SDK 条款 | **最高红线**：D2 决议已定"绝不引入 superresolution 代码"。仅可学输入契约与集成结构；SGSR 着色器为 BSD-3、FSR 着色器为 MIT（单文件另有许可），但**其集成代码是 GPL，一律不引** |
| **Nvidium** | **LGPL-3.0** | 原理级可学；实现须独立 |
| **OptiFine** | 闭源（反编译为社区产物） | 只以**公开特性清单与 shaderpack 格式约定**为目标面（Iris 已提供格式权威参照） |

**结论**：全部走**净室（clean-room）原理复现**——只记录"做什么、为什么、算法形貌"，不保留任何源码片段。D2/D7 决议与本表一致。

---

## 1. Sodium 原理清单（性能超集基准）

### 1.1 构建管线（Scheduling）
- **固定工作线程池**：`clamp(max(cores/3, cores-6), 1, 10)`，线程优先级降 2 级；每线程有**线程本地** scratch（避免跨核缓存同步）。
- **任务即值对象**：任务自包含（section 引用 + 提交帧 + 绝对/相对相机坐标），构建于提交时预算出 `estimatedDuration/estimatedSize/estimatedUploadDuration`。
- **在线成本估计**：按任务类维护"耗时 = a·工作量 + b"的**指数衰减线性回归**（新样本权重上限 5%），实际执行后回灌训练。
- **帧预算机制（核心）**：`busyFraction = 队列总估计耗时 /(帧时长 × 线程数)`；
  `remainingCapacity = max(0, 线程数 × 平均帧时长 − 队列总估计耗时)`，其中平均帧时长为 EMA（比例 0.05，钳制 1–100 ms）。
  每帧只提交 ≤ 剩余容量 的任务；上传另有独立预算（时长 30% 帧时长、字节上限取 staging 容量 80%）。
- **三档延迟（DeferMode）**：`ZERO_FRAMES`（本帧阻塞完成）/`ONE_FRAMES`（下帧）/`ALWAYS`（延后）。近距与玩家触发更新走 ZERO_FRAMES + important。
- **主线程任务窃取**：等待本帧任务完成时，主线程对未开始任务 `trySteal` 内联执行（"等待但做有用功"）。
- **可见性过滤提交**：仅对渲染树可见（或树外）的 section 提交重建。

### 1.2 顶点压缩
- **20 字节/顶点**（4 顶点 = 80 字节/quad）。
- 位置：section 局部量化，范围 8±16（norm=(8+v)/32），**20 位/轴**（60 位拆两 word）；shader 端反量化。
- 纹理：quad 4 个 UV 求质心，每顶点存 **15 位量化 + 1 位方向符号**（向质心收缩一个可寻址单位以防图集渗色）——只存符号、GPU 重建 epsilon。
- 光照：sky/block 各钳制 [8,248] 打包 16 位；另 8 位材质位 + 8 位 section 索引（供 per-draw uniform 查表）。

### 1.3 Region / Arena / Staging / Multi-draw
- **8×4×8 = 256 section/region**（2 的幂 → 移位运算）；region 内共享少数大缓冲。
- **每 region 每 pass 一次 multi-draw**：`glMultiDrawElementsBaseVertex`（GL）/ `multiDrawIndexedEX`(VK multi_draw) / `drawIndexedIndirect`(VK indirect 回退)——**CPU 生成命令，无 GPU 剔除**。
- **Arena 子分配**：free/used 双链表 + best-fit + 分配拆分/释放合并；region 独占或跨 region 共享（共享型带**增量碎片整理**，每帧预算 ~32 拷贝与 ~32KB/GiB，方向交替、最多 5 步、空闲 <3% 跳过）。
- **Staging 环缓冲**：默认 32 MB，持久映射，写游标环形回绕（跨尾拆两段传输）；`flush()` 落 fence，`flip()` 轮询 fence 回收空间；相邻传输合并后统一 copy。
- **Section 绘制元数据 = 原生 48 字节数组**（非对象）：base_vertex / slice_mask / facing 列表 / 各朝向顶点数。
- **共享索引缓冲**：预生成 `0,1,2, 2,3,0` 递增模式，供无序半透明与不透明几何复用。

### 1.4 遮挡剔除（三级）
- **6 方向连通位域**：`long`，`bit(from,to)=from*8+to`（≤48 位）；`getConnections(vis, incomingMask)` 用移位-或**折叠 64→6 位** O(1) 得"出向集合"。
- **方向可见图构建**：16³ 占据位图；全满→全遮挡；<256 填充→全通；否则对 **4 个基准视角**做泛洪求面间可达，得 1 或 4 个 VisibilitySet。
- **三级 tier**：`WIDE`（宽度 1、不做视锥测试）⊃ `REGULAR`（宽度 0）⊃ `LOCAL`（另加视锥 + **射线测试** `RAY_TEST_MAX_STEPS=12`，跳过 <48² 近距）。
- **BFS 携带入向方向集**，逐 section：合并方向集 → 应用**角度掩码**（相机轴向偏移占优则封对应反向对）→ `getConnections` 求出向 → 与**外向方向**（背离原点，宽档 ±1 chunk 容差）相交 → 与邻接掩码相交 → 逐方向尝试访问。
- **圆柱距离测试**（`dx²+dz²<d² && |dy|<d`，对齐原版雾模型）；**斜率/角度遮挡**用 6 个 10 位量化角打包进 long + 32×32 角度 LUT + SWAR 并行比较。
- **Morton（Z 序）位树**：`long[64×64]` + 两级 reduce 数组；`prepareForTraversal` 建 reduce 层；遍历按**近到远**子序递归，带 `INSIDE_FRUSTUM/INSIDE_DISTANCE` 快速跳过。
- **树复用**：相机每轴移动 ≤ bfsWidth chunk 且 buildDistance ≥ 搜索距离则复用；三棵树同时产出（WIDE/REGULAR/LOCAL），取**最窄有效者**生成渲染列表。
- **异步单线程剔除**（专用线程），相机大步移动（>32 格）回退同步。

### 1.5 半透明排序
- **正确性最小化分类**（关键洞察）：每 section 一次性判定"能证明正确的最弱排序纪律"：
  ≤1 平面 / 恰好 2 个相对轴对齐面 / 几何贴合 section 包围盒凸壳 → **NONE**（用共享索引缓冲）；
  法线相对（轴对齐反对）→ **STATIC_NORMAL_RELATIVE**（按 `dot(normal,pos)` 升序，一次基数排序）；
  可拓扑化且 quad 数 ≤ 阈值表 → **STATIC_TOPO**；否则 → **DYNAMIC（BSP）**。
- **BSP 节点族**：`InnerPartition`（轴对齐分区搜索，区间首末侧点打包排序）→ 依分区数分 `InnerBinaryPartition` / `InnerMultiPartition`；无法排序的相交几何用 `InnerFixedDouble`；叶 `Single/Double/Multi`。**索引压缩**（RLE/位打包，头 32 位含宽度/数量/首索引）。
- **节点复用**：深度 1 子节点且 quad 数 >30 时记录 `NodeReuseData(quadHash, 压缩索引, indexCount, maxIndex)`；重建时 hash 与数量一致则复用子树（固定偏移或索引重映射），**触发平面得以保留**。
- **触发器**：GFNI 按清理后法线分组，每组区间树（`IntervalTree<Double>`）存相机沿法线投影的可行区间，相机移动投影命中即触发组内全部 section（**双向触发**，BSP 与单向法线平面通用）；Direct 触发器用"累积行进距离"红黑树，近距用距离触发（1 格）、远距用角度触发（10°），均带 0.9 提前因子防抖。
- **每帧索引重写**：`writeIndexBuffer(cameraPos)` 写进已有 GPU 槽位（可在线程池执行，结果上传）；无序 section 走共享索引。

### 1.6 实体剔除
- **无完整实体遮挡剔除**：以渲染树作保守可见性判据（AABB → 是否落在可见 section 集），**仍需叠加原版视锥测试**；体积 > 16³×50、发光、显示名实体跳过。

---

## 2. Iris 原理清单（Shaderpack 宿主超集基准）

### 2.1 格式与解析
- 目录约定：`shaders/` 下 `.vsh/.fsh/.gsh/.tcs/.tes/.csh`；程序组 `setup/begin/shadow/shadowcomp/prepare/gbuffers/deferred/composite/final/dh`。
- gbuffer 程序族 26 项（basic/line/textured/textured_lit/skybasic/skytextured/clouds/terrain/terrain_solid/terrain_cutout/damagedblock/block/block_translucent/beaconbeam/item/entities/entities_translucent/entities_glowing/lightning/particles/particles_translucent/armor_glint/spidereyes/hand/weather/water/hand_water）。
- **维度覆盖 = 整体替换而非合并**（`world0/` 存在即完全接管该维度）。
- **回退链**：Water→Terrain→TexturedLit→Textured→Basic，逐级解析。
- 无 vsh 的 fsh 自动补 GLSL120 `ftransform()` 桩（legacy 兼容）。
- **include 图**：`#include`（非 `moj_import`）先于任何 `#ifdef` 展开 → include guard 无效；DFS 检测自环/环路；记忆化扁平化。
- **属性文件**：ISO-8859-1 读取 + 预处理；**首声明优先**顺序语义（OptiFine 对齐）。
- **预处理器**：JCPP；因 JCPP 无法上提 `#extension`，先把 `#version/#extension` 换成哨兵、预处理后回填顶部（严格驱动兼容）；剥离 NUL 字节（Chocapic 兼容）。
- 环境宏：`MC_VERSION/MC_GL_VERSION/MC_GLSL_VERSION/MC_OS_*/MC_GL_VENDOR_*/MC_GL_RENDERER_*/MC_<GL扩展>/IS_IRIS/MAX_COLOR_BUFFERS/BIOME_<id>/CAT_*/PPT_*/MC_RENDER_STAGE_<phase>/COLOR_SPACE_*/IRIS_FEATURE_*`。
- **source 级指令**：`const colortexNFormat/Clear/ClearColor`、`shadowMapResolution/shadowDistance/shadowMapFov/...`、魔法注释 `/* DRAWBUFFERS:NNNN */`（后出现者胜）、`/* RENDRTARGETS:... */`、`/* SHADOWRES/... */`、compute 的 `const ivec3 workGroups`。
- **shaders.properties 指令面**（实现要点清单）：开关类（clouds/sun/moon/stars/sky/vignette/backFace.*/separateAo/particles.ordering/frustum.culling/occlusion.culling/shadow.enabled/...）、pass 类（scale.<pass>/size.buffer.<buf>/alphaTest.<pass>/blend.<pass>.<buf>/indirect.<pass>/flip.<pass>.<buf>/program.<name>.enabled）、资源类（texture.<stage>.<sampler>/customTexture.<name>/image.<name>/texture.noise）、缓冲类（bufferObject.<idx>）、uniform 类（uniform.<type>.<name>、variable.<type>.<name>——表达式求值 + 依赖拓扑排序）、菜单类（sliders/screen/profile.<name>）、特性类（iris.features.required/optional）。
- **能力旗标**：`SEPARATE_HARDWARE_SAMPLERS/HIGHER_SHADOWCOLOR/CUSTOM_IMAGES/PER_BUFFER_BLENDING/COMPUTE_SHADERS/TESSELLATION_SHADERS/ENTITY_TRANSLUCENT/REVERSED_CULLING/BLOCK_EMISSION_ATTRIBUTE/CAN_DISABLE_WEATHER/SSBO/FADE_VARIABLE/TEXTURE_FILTERING`；必需旗标不可用 → 报错并禁用 shader。

### 2.2 Pass 编排（帧序，必记）
```
clear images → shadow compute/clear → [setup compute（仅尺寸变化时）] → clear colortex
→ begin 合成 → shadow 渲染 → prepare 合成
→ 不透明/镂空 gbuffers → beginHand（拷贝 pre-hand 深度）
→ deferred → 半透明 gbuffers → composite → final → 色彩空间转换
```
- 合成 pass 枚举序：`BEGIN(0) → PREPARE(1) → DEFERRED(2) → COMPOSITE(3)`；每组最多 **100** 个编号程序；compute 每 pass 最多 **27** 个子变体（`_a.._z`）。
- 视口缩放 `scale.<pass> = <scale> [offX offY]`；每 buffer 尺寸 `size.buffer.<buf>`。

### 2.3 Render targets
- colortex 上限 **32**（惯例 16：colortex0–15；0–7 有 legacy 别名 gcolor/gdepth/gnormal/composite/gaux1–4）。
- 深度三张：`depthtex0`（主）/`depthtex1`（半透明前拷贝）/`depthtex2`（手部前拷贝）。
- 阴影：`shadowtex0/1` + `shadowcolor0..N`（OptiFine 兼容 2 / Iris 扩展 8）。
- **main/alt 乒乓**：写时按 flipped 集合选 alt/main，采样取反；pass 后写过的 buffer 翻转（可被 `flip.<pass>.<buffer>=false` 阻止）；三个快照点（shadow 前/prepare 后/半透明后）。
- 默认清屏色：index0 = 雾色(α=1)、index1 = 白、其余 = 透明黑；gdepth 声明时 colortex1 自动升 RGBA32F。
- 清屏 pass 按 (尺寸, 色) 分组，每批 ≤ `GL_MAX_DRAW_BUFFERS`，每批**两遍**（alt 与 main 都要初始化）。
- 惰性分配（首次使用才建）；resize 触发重建。

### 2.4 Uniform 系统
- 三类：内建清单（相机/矩阵/时间/天体/雾/世界/IdMap/动态 per-draw）、自定义（表达式 + 拓扑排序 + 缓存 + 未用剪枝）、动态（状态通知器驱动，状态变才传）。
- 更新节奏：`ONCE / PER_TICK / PER_FRAME` + 动态通知器（绑纹理、混合函数、雾起止、阶段切换、fallback 实体）。
- 相机**范围移位**（|x|,|z| < 30000，瞬移阈值 1000）保 float 精度。
- 矩阵全套：当前/上一帧的 modelView/projection/view-projection + 逆矩阵 + 阴影矩阵。

### 2.5 阴影
- **单张正交阴影图**（无级联），默认 1024²（常用 1024–4096），`DEPTH32`。
- 半平面长度 = `shadowDistance`（默认 **160 m**）；near/far 默认 −100.05 / 156.0。
- **网格吸附**（`shadowIntervalSize` 默认 2 m）防抖动；投影旋转 = 绕 X 90° → 绕 Z 按阴影角 → 绕 X 按 `sunPathRotation`。
- 剔除档：DEFAULT/DISTANCE/ADVANCED（用 gbuffer 视锥 ⊗ 光向构造剔除视锥）/SAFE_ZONE；体素化检测到时回退。
- 渲染序：关背面剔除 → 不透明地形 → 实体/方块实体 → 半透明地形 → 拷 `shadowtex1` → 生成 mipmap → `shadowcomp`。
- 采样别名：`shadowtex0`/`shadow`/`watershadow`、`shadowtex1`、`shadowcolorN`、硬件滤波别名 `shadowtex0HW/1HW`；深度 swizzle R→RGB,1 兼容老包。

### 2.6 兼容层（Sodium 协作，超集关键）
- **Sodium 程序接管**：重定向 `ShaderChunkRenderer.compileProgram` → Iris 的 pass→ProgramId 映射；绑定 Iris framebuffer；shadow pass 换独立 UBO、禁用 Sodium 面剔除与共享索引排序。
- **顶点属性桥接**：Sodium 打包顶点解码（`_vert_position/_vert_tex_diffuse_coord/_vert_tex_light_coord/_vert_color/_draw_id`）+ `u_RegionOffset`；注入 `mc_Entity`(blockId)、`at_midBlock`、`at_tangent`、`mc_midTexCoord`、`mc_Entity` 发射值。
- 原版路径：`IrisVertexFormats` 扩元素（TERRAIN 加 mc_Entity/mc_midTexCoord/at_tangent/at_midBlock；ENTITY/GLYPH 加 iris_Entity）。
- **IdMap**：`block.properties`/`item.properties`/`entity.properties` + tag 展开（`IRIS_TAG_SUPPORT`），blockstate→int（首匹配胜）。
- PBR：labPBR 法线/高光图集化，暴露 `normals`/`specular` 采样器 + `MC_NORMAL_MAP/MC_SPECULAR_MAP` 宏。

### 2.7 性能机制
- 源码变换 **LRU 缓存**（容量 400，键 = 源码 + 参数）；程序惰性编译（`ShaderMap/ShaderLoadingMap`，仅编译实际使用者）。
- framebuffer / clear pass 仅在 resize 时重建；uniform 变更才上传；自定义 uniform 一次拓扑排序 + 剪枝。
- 保留 Sodium 的 draw 批处理（只换程序与 framebuffer）；纹理单元 0/1/2 保留给 albedo/overlay/lightmap，只绑 shader 实际声明的采样器，pass 后全部解绑。

---

## 3. Nvidium 原理清单（GPU 驱动 + 硬件加速超集基准）

> 定位：Sodium 的**替代渲染后端**；硬依赖 NV 扩展，缺一即整机禁用回退 Sodium。LGPL-3.0。

### 3.1 架构与帧流
- Mixin 接管 Sodium：`renderLayer` 被取消，SOLID → `renderFrame`，TRANSLUCENT → `renderTranslucent`；Sodium 构建结果由 `repackage` 转 16 字节顶点。
- 每帧序：CPU 视锥筛 region（按距离排序上传）→ 上传 scene UBO → 提交元数据/上传 → **上一帧地形 pass**（写深度，供遮挡测试）→ **region 遮挡光栅** → barrier → **section 遮挡光栅 + 生成命令缓冲** → **时间性地形 pass**（本帧新可见 section）→ region 可见性回读（淘汰统计）→ 半透明排序 compute → 半透明 pass。

### 3.2 Mesh/Task Shader 地形
- **`GL_NV_mesh_shader`**（NV 版，非 EXT）。**每 region 一个 task workgroup**，发出 N 个 mesh workgroup（每 section 一个，含面可见性裁剪）。
- Task：`local_size_x=1`；按 section 可见性位早退；按 **7 个 Sodium 面范围**与相对 chunk 位置过滤（3 负轴 / 3 正轴 / unassigned）；打包成 8 个 (index-limit, vertex-offset) bins；`gl_TaskCountNV = ceil(quadCount/16)`。
- Mesh：`local_size_x=16`，`max_vertices=64, max_primitives=32`；**每 invocation 16 quad**；顶点拉取（vertex pulling）；屏幕空间 AABB 剔除退化三角形；subgroup 前缀和压缩三角形/顶点；`gl_PrimitiveID = (id<<4)|(lodBias<<2)|alphaCutoff|tri` 供片元重取顶点。
- **顶点布局 16 字节**：`.x` = posX|posY(16+16)；`.y` = posZ(16) | meta 8 位（bit2 是否有 mipping、bit0-1 alpha cutoff 索引）| 光照 block 8 位；`.z` = 颜色 RGB 24 位（alpha 预乘）| 光照 sky 8 位；`.w` = UV u|v(16+16)。位置尺度 32/65536、UV 1/32768、alpha cutoff LUT {0, 0.1, 0.5}。
- 命令：`glMultiDrawMeshTasksIndirectNV`，每条 8 字节 `(count, visOutBase)`。

### 3.3 GPU 遮挡（两级、时间性、无深度金字塔）
- **无软件 Z 金字塔、无 visibility buffer**：直接用**硬件深度测试 + `GL_NV_representative_fragment_test`**求解遮挡。
- **region 级**：region 盒（8 角 + 0.1/16 膨胀）光栅，片元写 `regionVisibility[id]=1`（相机在 region 内则 0）。
- **section 级**：section 盒（8 角 + 0.1 膨胀）光栅，12 三角形；片元写 `sectionVisibility[id]`；task 同时写两套命令缓冲（地形正序 / 半透明**逆序**以保证 back-to-front）。
- **时间性可见性**：每 section 一个 `uint8`，**8 帧位历史**（每帧左移）；时间性 pass 只画 `(data & 3) == 1`（上帧不可见、本帧可见）的 section——新暴露几何即使会被深度测掉也必须画。
- 同步：region/section 光栅后 `GL_SHADER_STORAGE_BARRIER_BIT`；时间性地形前 `GL_COMMAND_BARRIER_BIT`。
- 淘汰：第二个 region 可见性查询 pass 异步回读，按"最久未见 + 视锥出现 >200 次"淘汰 region。

### 3.4 半透明
- 独立 mesh-shader 路径，复用同一可见性；命令**逆 region 序**近似 back-to-front。
- 档位：NONE / **SECTIONS**（每 region 256 元素 bitonic 排序网络按曼哈顿距离排名，写入 section header 位 18–25，task 用低 8 位做间接） / **QUADS**（默认；每帧每 workgroup 一次奇偶换位步，直接在 GPU 地形缓冲内**就地交换**相邻 quad；帧号 `&1` 抖动避免竞争）。
- 源码注释自认"cursed and incorrect"——**工程诚实**：近似排序 + 时间收敛。

### 3.5 内存/带宽
- 单静态 UBO（`SceneData`）内嵌**全部 64 位设备指针**（region/section 元数据、可见性、命令缓冲、地形顶点、变换/原点数组、统计），经 `NV_uniform_buffer_unified_memory` 绑定。
- 地形：**80 GB 稀疏缓冲**（`ARB_sparse_buffer`，**1 MB 页**按需提交；注释：NV 驱动碎片整理差故取大页）；Linux 回退固定缓冲（驱动不一致）。
- Section 元数据 **32 字节**（header + renderRanges 两组 ivec4）；region 元数据 **16 字节**；变换数组 1024×64 B + 原点数组 1024×8 B。
- 上传：32 MB 持久映射客户端 staging；下载：8 MB 环（`cpuRenderAheadLimit+1`）。

### 3.6 硬性扩展门槛（Turing+）
必需：`GL_NV_mesh_shader`、`NV_uniform_buffer_unified_memory`、`NV_vertex_buffer_unified_memory`、`NV_representative_fragment_test`、`ARB_sparse_buffer`、`NV_bindless_multi_draw_indirect`；着色器另需 `NV_gpu_shader5`、`NV_bindless_texture`、`NV_shader_buffer_load`、`NV_fragment_shader_barycentric`、`KHR_shader_subgroup_*`、`ARB_shading_language_include`。
驱动坑（可复用知识）：代表片元测试须开深度测试但关深度写；`glClearNamedBufferSubData` 不可靠（改上传零）；稀疏页需 1 MB。

### 3.7 无 LOD、无实体加速
- **不做几何 LOD/简化**（无距离降级、无渐进网格）——收益全来自 draw call 消除与遮挡；距离行为仅体现为 region 淘汰与 `region_keep_distance`（默认 32 region = 4096 格）。
- 实体不加速；仅以异步 BFS 的可见集驱动**方块实体**与动画精灵的可见性。
- README **无任何 FPS 倍率承诺**（社区传闻不算仓库主张）。

---

## 4. SuperResolution 原理清单（超分/插帧/低延迟超集基准）

> **GPL-3.0 红线**：只学输入契约与结构，零代码引入（D2）。

### 4.1 统一输入契约（核心洞察）
所有时间性超分（DLSS / FSR2-3 / XeSS / SGSR2）共享**同一输入集**：
- 颜色（内部 RGBA16F）、深度（R32F）、**运动向量（RG16F，单位 = 渲染分辨率像素）**、曝光、抖动偏移与序列、当前+上一帧 VM/VP 矩阵、near/far/FOV、帧时长、预曝光、reset 标志。
- 空间性（FSR1 / SGSR1）仅需颜色（+锐化参数）。

### 4.2 接入结构
- **降分辨率渲染再上采样回主 framebuffer**：世界渲染期间换用缩放 RenderTarget，`onRenderWorldEnd` 内派发算法 → `glBlitFramebuffer` 回原目标（**在 GUI/HUD 之前**，故 HUD 不参与上采样）。
- 捕获模式 A（GameRenderer.renderLevel 前后）/ B（LevelRenderer）/ C（另捕获手部到全分辨率 HAND 目标，上采样后回贴）。
- 拷贝后需 **Y 翻转 + MV.y 取反**（GL↔VK 约定差）；深度/mv 于预处理器中处理。
- **抖动**：Halton(2,3)，`phaseCount = 8 × (display/render)²`，`x = halton(i%phaseCount+1, 2) − 0.5`。**关键**：该 mod 在纯原版路径**不把抖动注入投影矩阵**（传 0），仅 shaderpack 路径由包负责抖动 → 自研实现必须真正抖动投影矩阵并在重投影时消抖。
- **运动向量**：纯原版路径传全零 RG16F；真 MV 依赖 shaderpack 提供 → **自研必须自己算 MV**（用上一帧 VP 重投影：`clipToPrevClip = prevVP × inverse(curVP)`）。
- 深度约定：捕获 R32F；FSR2 声明 `enableDepthInverted(false)`、`deviceDepthNegativeOneToOne(false)`；MC 为反向 Z（near=1/far=0）→ 需显式约定与转换。

### 4.3 各算法原理
- **DLSS（NGX/Vulkan）**：创建 DLSS feature（render→target 尺寸），每帧 evaluate 传 color/depth/MV/exposure/jitter/**`motionVectorScale = 渲染宽高`**/preExposure/frameDelta；旗标 `MV_LOW_RES`（恒开）+ `AUTO_EXPOSURE`/`HDR`/`MV_JITTERED`（来自配置）；渲染 preset（F/J/K/L/M = 6/10/11/12/13，J/K/L/M 为 DLSS4 代 transformer 档）；质量档倍率 3.0/2.0/1.724/1.5/1.0。DLSS-RR 另需漫反射/镜面反照率、法线、粗糙度、镜面 MV、命中距离等 G-buffer 输入。
- **FSR2/FSR3（FidelityFX/Vulkan）**：同一输入集 + `motionVectorScale = 渲染宽高` + **锐化开启（RCAS）** + camera near/far/fov + `viewSpaceToMetersFactor` + reset；reactive/transparency mask 字段存在但**未接**（走自动 reactive）。
- **FSR1**：纯空间两 pass compute（EASU 上采样 + RCAS 锐化），仅颜色。
- **SGSR1**：**纯空间、仅颜色**的边自适应上采样——快 Lanczos2 加权 + 边缘"投票"（阈值 8/255）+ 增量钳制 ±23/255（EAS 滤波）。
- **SGSR2**：**时间性**（2 或 3 pass compute）：pass1 颜色→YCoCg 打包 + 运动/深度/alpha 缓冲；pass2 相机 MV + 深度剪切 + 亮度历史/深度分离（Ksep=1.37e-5）+ alpha 计算；pass3 历史混合（快 Lanczos，缩放因子 `min(20, (display/render)³)`）。内置"相机静止检测"（连续 5 帧 VM/VP 差 <1e-5 则 `minLerpContribution=0.3` 防过糊）。
- **XeSS**：黑盒 DLL（Vulkan API），同样时间性输入契约；**必须用 `xessGetOptimalInputResolution` 而非硬编码倍率**（1.3+）；DP4a 跨厂商 / XMX 优先。

### 4.4 插帧与低延迟
- **DLSS-FG**：需 **HUD-less 颜色 + 深度 + MV + Vulkan 呈现 + Reflex 低延迟**；generated frame 用**保留 present id**（real id 步长 16，生成帧占低位）保证单调，并以 `VK_LATENCY_MARKER_OUT_OF_BAND_*` 标记使驱动不计入延迟节奏。
- **Reflex**：两条路——Streamline 插件（Win/NV）与 `VK_NV_low_latency2`（Linux 等）；标记点：SIMULATION_START/END、RENDERSUBMIT_START/END、PRESENT_START/END、LATENCY_PING、TRIGGER_FLASH；swapchain 上设 sleep mode + 时间线信号量睡眠；`frameLimitUs()` 按"实际+生成帧数"折算帧率上限。
- FG 要求 shaderpack 环境（原版路径破坏 UI 呈现）。

---

## 5. OptiFine 特性面（闭源；以公开特性清单 + Iris 格式约定为权威）

- **Shaderpack 格式**：Iris 已完整实现（§2）——OpenLumin 的兼容目标即"Iris 语法超集"，无需另寻 OptiFine 源码。
- **非 shader 特性面**（P0 兼容清单，需自研或对齐）：
  1. **CTM / 连接纹理**（含 connected textures、随机纹理 RandomEntities/RandomMobs、Better Grass/Snow）；
  2. **自定义天空/雾/天气/日月/星空**（shaderpack 内已有部分指令；非 shader 路径需引擎侧实现）；
  3. **动态光照/平滑光照**与旧版光照兼容（`oldLighting`）；
  4. **性能选项语义对齐**（render distance 扩展、chunk fade、entity distance、particle 限制）；
  5. **视距扩展 + 雾效**（与 Nvidium 的 `region_keep_distance` 同类）。
- 定位：OptiFine 兼容面 = **格式与行为对齐**，实现全部自研（Iris 的 LGPL 实现仅作行为参照）。

---

## 6. 能力超集矩阵（前调后定稿）

| 能力域 | 参照基准 | OpenLumin 现状 | 目标（超集定义） | 归属 |
|---|---|---|---|---|
| 区块构建调度 | Sodium 在线成本估计 + 帧预算 | 🟡 M1 执行器（**无成本估计/帧预算**） | 估计器 + 三档延迟 + 主线程窃取 + 可见性过滤 | WP-1 M4 |
| 顶点压缩 | Sodium 20 B/顶点 | ❌ | 量化顶点格式（≤20 B）+ shader 反量化 | WP-1 M4 |
| Region/Arena/上传 | Sodium arena + staging 环 + fence | 🟡 M2 账本（**无 arena 碎片整理/staging 环**） | 共享 arena + 增量碎片整理 + staging 环 | WP-1 M4 |
| 多 draw | Sodium 三后端回退链 | 🟡 M3a（drawMultipleIndexed 主路径） | VK multi_draw / indirect 回退 + CPU 命令生成 | WP-1 M5 |
| 遮挡剔除 | Sodium 三级 + 位树 + 斜率 | 🟡 M3b（**单级 BFS + 无位树/斜率/射线**） | 三级 tier + Morton 位树 + 射线本地档 | WP-1 M5 |
| 半透明排序 | Sodium 正确性最小化分类 + 触发器 | 🟡 M3c（**仅三策略 + 无触发器/复用**） | 分类启发式 + 触发器（GFNI/Direct）+ 节点复用 | WP-1 M6 |
| 实体剔除 | Sodium 树查询 | ❌ | 树查询 + 体积/发光豁免 | WP-1 M6 |
| Shaderpack 宿主 | Iris 全链 | ❌ | 解析器 + pass 编排 + targets + uniform + shadow + 旗标 | WP-2 |
| OptiFine 兼容面 | OptiFine 格式/行为 | ❌ | CTM/随机纹理/自定义天空/旧光照 | WP-2b |
| 后处理/render graph | Iris pass 编排 | 🟡 雏形 | render graph（资源别名/屏障） | WP-3 |
| 超分/插帧 | SR 统一输入契约 | ❌ | 自研 FS R2/SGSR2 类 + DLSS-D via NvAPI + FG 缝 | WP-4/WP-5 |
| 低延迟 | SR Reflex/`VK_NV_low_latency2` | ❌ | `LuminFramePacing` 接口 + 标记点 + sleep mode | WP-5 |
| GPU 驱动（NV 独有） | Nvidium mesh/task + 时间性遮挡 | ❌ | 研究线（**EXT_mesh_shader 优先于 NV**，跨厂商） | Alpha 4+ |
| 硬件加速 RTX | （尚无参照实现） | ❌ | Vulkan RT 研究线 | Alpha 4+ |

---

## 7. 原理级提升要点（前调结论 / 立即适用的改进）

1. **M1 执行器缺"帧预算"**：当前只有 `getBusyFraction`，无"每帧提交上限 = 剩余容量"。Sodium 的做法是**先算预算再提交**；我们应在 M4 补在线耗时估计（先用固定常数，后加回归）与三档延迟。
2. **M3b 剔除缺"树复用 + 三级 tier"**：我们每帧全量 BFS；Sodium 用"相机位移 ≤ 宽度则复用 Morton 位树"避免重建。M5 补位树与复用是**数量级**改进。
3. **M3c 排序缺"正确性最小化分类"**：Sodium 让多数 section 落到 NONE/STATIC（零每帧成本），只有少数走 BSP。我们目前三策略并列，**应由启发式自动判定**——这是省 CPU 的关键。
4. **M2 账本缺 arena 碎片整理与 staging 环**：当前 region 满即抛（消费方重建）。Sodium 用增量碎片整理 + fence 回收的 staging 环把上传成本摊平。
5. **顶点格式未压缩**：Sodium 20 B/顶点（含 20 位量化位置与 15+1 位 UV）是带宽基本盘，我们的格式尚未定型——M4 定型时应直接采用量化设计。
6. **多 draw 后端回退链**：Sodium 依能力选 `multi_draw` 或 `indirect`；我们 26.2 的 `multiDrawIndexed` 三缓冲重载 + `drawIndexedIndirect` 已实证可用，应补能力探测与回退链（M5）。
7. **超分输入契约先行**：即使超分在 WP-4/WP-5，**MV/深度/抖动的产出位置**必须在 WP-3 render graph 定型时预留（否则后期返工）。抖动必须真正注入投影矩阵并在重投影消抖——这是 SR 参照实现的**共同缺口**，我们要一次做对。
8. **FG/Reflex 的 present-id 单调性**：插帧帧用保留 id 且标记 out-of-band，是"插帧不破坏低延迟"的关键机制，需在呈现路径设计时预留。
9. **跨厂商优先**：Nvidium 的 NV 独有路线（mesh shader + 代表片元测试）不可移植到 AMD/Intel；OpenLumin 作为**跨平台库**应优先 `EXT_mesh_shader` + 通用遮挡查询，NV 快路径作为可选加速（同时规避 LGPL/闭源风险）。
10. **许可纪律**：Sodium 是 PolyForm Shield（非 GPL）——我们的"超集"叙事应避免"替代 Sodium 商业分发"的表述；Iris/Nvidium LGPL 仅原理参照；SR GPL 零接触。

---

## 8. 下一步（按序）

1. **WP-1 M4 设计+实现**：构建调度增强（成本估计 + 帧预算 + 三档延迟）+ 量化顶点格式 + arena 碎片整理/staging 环（对齐 §7.1/7.4/7.5）。
2. **WP-1 M5**：多 draw 能力回退链 + 三级遮挡（Morton 位树、树复用、射线本地档、斜率）（§7.2/7.6）。
3. **WP-1 M6**：半透明分类启发式 + 触发器 + 节点复用 + 实体剔除（§7.3）。
4. **WP-2 设计**：Shaderpack 宿主按 Iris §2 全链拆解（解析器/编排/targets/uniform/shadow/旗标），并在 render graph 设计里**预留 MV/深度/抖动产出位**（§7.7）。
5. **WP-4/WP-5 设计**：超分自研（FSR2/SGSR2 类）+ DLSS-D via NvAPI + FG/Reflex 缝（§7.8/7.9）。
6. 每项完成后回写本文件与 ROADMAP_v26.md 的 WP 状态；行为级结论一律以运行用例为准（D7）。

---

*本文档为原理级研读记录（零拷码）。所有实现须独立编写；引用仅限公开算法与格式约定。*
*GitHub@NDBlockConnect | BlockConnect@StarsailsClover*
