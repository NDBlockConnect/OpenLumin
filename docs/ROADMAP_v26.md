# OpenLumin v26.0 大版本生态研究与规划

# OpenLumin v26.0 Ecosystem Research & Roadmap

> 状态：规划草案（Alpha 2–5 映射） · Status: planning draft
> 日期：2026-08-26 · GitHub@NDBlockConnect | BlockConnect@StarsailsClover

---

## 1. 研究对象总览 / Research Targets

| 项目 | 类型 | 许可 | 与 OpenLumin 的关系 |
|---|---|---|---|
| [NoCubes](https://modrinth.com/mod/nocubes) | Modrinth Mod | — | 地形网格化（立方体→平滑网格）参考 |
| [Project-Crystal-Fracture](https://github.com/Hismeo/Project-Crystal-Fracture) | 整合包+定制模组（1.21.1） | — | 下游消费者：高机动动作肉鸽俯视角，含 3D 地图、nsight GPU 剖析 |
| [MobileGlues](https://github.com/MobileGL-Dev/MobileGlues) | GL ES→桌面 GL 转译层 | — | 移动端平台路径：OpenLumin 的 GLES 兼容子集目标 |
| [superresolution](https://github.com/IReallyWantToSleep/superresolution) | Mod（GPL-3.0 / native MIT） | GPL-3.0/MIT | 超分算法集成参考：FSR1/2/3、SGSR1/2、DLSS、XeSS |
| [SlideShow](https://github.com/teaconmc/SlideShow) | Mod（TeaCon） | — | 世界内媒体/幻灯片渲染用例 |
| [Arc3D](https://github.com/BloCamLimb/Arc3D) | Java 图形引擎（LGPL-3.0） | LGPL-3.0 | RHI 架构参照：GL/GLES/Vulkan 三后端、SPIR-V 编译器、Maven Central |
| [ModernUI-MC](https://github.com/BloCamLimb/ModernUI-MC) | Mod / UI 框架（LGPL-3.0） | LGPL-3.0 | UI 引擎参照：文本布局(HarfBuzz)、SDF 文本、共享 GL 上下文存活 |
| Polytone / AsyncParticles / EMF / EntityCulling / Grassier Grass / Vibrancy / Just Like Rays / Better Lightmap / Rainfall / Particle Rain | Modrinth Mods | 各异 | Alpha 2/3 的机制与特效参考族 |

---

## 2. 按 Alpha 阶段映射 / Phase Mapping

### Alpha 2 — 性能与机制研究（对应现有路线）

| 方向 | 参照项目 | OpenLumin 落点 |
|---|---|---|
| 遮蔽剔除 | EntityCulling | Render3DScheduler/实体渲染路径增加遮挡查询剔除；研究其 Raycast 剔除策略 |
| 异步粒子 | AsyncParticles | 粒子系统异步化（现 LuminImmediateRenderer 为同步提交）；评估跨线程命令录制 |
| 超分辨率 | superresolution | 以 LuminRenderPipelines 后处理位集成 FSR2/SGSR2；依赖 GL4.3+DSA/SpirV（26.x 基线满足）；**GLES 层（MobileGlues）下 compute/DSA 部分不可用，需保留无超分降级路径** |
| 光照图 | Better Lightmap | Lightmap 重构方案对比（3D 光照图/色温） |
| 地形网格 | NoCubes | 区块网格重映射研究（marching cubes/greedy meshing）；属游戏侧机制，评估以库形式输出网格工具 |

### Alpha 3 — 光线计算、实体渲染与机制

| 方向 | 参照项目 | OpenLumin 落点 |
|---|---|---|
| 体积光/God Rays | Just Like Rays | 后处理光线投射 pass（复用 LuminRenderTarget/后处理管线） |
| 环境光/氛围 | Vibrancy | 光照传播与颜色分级参考 |
| 天气表现 | Rainfall / Particle Rain | 粒子+后处理天气系统；与 AsyncParticles 方案合流 |
| 植被 | Grassier Grass | 草地渲染变体（顶点动画/覆盖网格） |
| 实体模型特性 | Entity Model Features | 资源包驱动的实体模型状态机；实体渲染机制研究的一部分 |
| 视觉定制 | Polytone | 资源包驱动的方块/实体视觉定制协议参考 |

### UI 方向（新模块候选 / UI track）

| 参照 | 评估 |
|---|---|
| ModernUI-MC | 成熟 UI 引擎（文本布局 HarfBuzz、SDF 文本、MVVM、RTL），LGPL 可链接。**决策点：OpenLumin UI 模块走自研（对齐 LuminGraphics 的 ui 模块规划）还是集成 ModernUI**。建议 Alpha 2 先做集成可行性 spike（共享 GL 上下文、与 LuminPlatform 的线程门禁对接） |
| Arc3D | **RHI 架构首要参照**：GL/GLES/Vulkan 三后端、shader 编译器模块、granite 资源管理、Maven Central 发布实践。其模块划分（core/engine/backend 分离）可直接借鉴到 LuminPlatform 的后端化改造 |
| SlideShow | 世界内媒体渲染用例：纹理流送+世界内投影；可作为 OpenLumin 纹理 API 的验收用例与下游伙伴（TeaCon 社区） |

### 平台扩展 / Platform track（DX12 与移动端）

| 方向 | 评估 |
|---|---|
| **DX12 支持** | 动机：Windows 主平台原生后端、Xbox/GDK 远期。**约束：LWJGL 无官方 D3D12 绑定**。可行路径：(a) 第三方 D3D12 绑定（社区 binding 成熟度待评估）；(b) 自建 COM 互操作 native 层（参考 superresolution 的 native 模块组织：Gradle `native:buildNative` + MinGW/CMake）；(c) 经 Arc3D 若其未来增加 D3D12 后端。**建议列入 v26.0 后期（Alpha 4+）预研，先以 RHI 接口收敛为前提**——当前 LuminPlatform 抽象已隔离平台差异，是 DX12 后端的前置条件 |
| **移动端（MobileGlues）** | GLES 转译层的已知缺口：compute shader、DSA、SpirV 二进制部分不可用（superresolution 实测）。OpenLumin 需维持一条 **GLES 兼容管线子集**（无 compute、无 DSA、无 SpirV 二进制的回退路径），26.1.2 基线（GL 410 core + 传统路径）天然更接近该子集。与 MobileGL-Dev 保持上游对话 |

### 下游生态 / Downstream

- **Project-Crystal-Fracture**：高机动动作肉鸽俯视角整合包（1.21.1），需要高帧率相机控制、3D 地图（MapShow 已独立）、GPU 剖析（nsight 目录）。**行动**：作为 OpenLumin 的下游 showcase 合作候选；其需求（相机、UI、3D 地图）反哺 API 设计。
- **SlideShow（TeaCon）**：会议级媒体渲染用例。

---

## 3. 风险与许可 / Risks & Licensing

| 项 | 风险 | 对策 |
|---|---|---|
| superresolution GPL-3.0 | 集成其代码会传染许可 | 只做**协议级兼容**（超分 pass 自研或用 MIT native 库思路），不拷代码 |
| Arc3D/ModernUI LGPL-3.0 | 可链接、需隔离 | 以依赖方式引入，不内联源码；遵守 NOTICE |
| DX12 无 LWJGL 绑定 | native 层维护成本 | Alpha 4+ 预研；先收敛 LuminPlatform RHI 接口 |
| GLES 子集约束 | 功能降级路径长期共存 | 管线特性矩阵进入 CI（按后端标记 skip） |
| 多 RHI 并存（Arc3D/PrismRHI/LuminPlatform） | 生态碎片化 | 借鉴而非对抗；UI track 决策时评估与 LuminGraphics 的 ui 模块合流 |

---

## 4. 决议待定 / Open Decisions

1. UI track：自研 vs 集成 ModernUI（Alpha 2 spike 后定）
2. DX12：预研启动时点（建议 Alpha 3 末评估 LWJGL 生态进展）
3. 超分集成：自研 FSR2 pass vs 协议兼容 superresolution（许可评估后定）
4. NoCubes 类地形网格：是否纳入库 API（游戏机制 vs 库能力边界）

---

*GitHub@NDBlockConnect | BlockConnect@StarsailsClover*
