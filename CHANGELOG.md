# Changelog

所有 OpenLumin 重要变更都记录在此文件。

# Changelog

All notable changes to OpenLumin are recorded here.

---

## [v26.0-alpha.2] — 计划中 / Planned

### Alpha 2.1（基础设施已就位，待稳定化）

#### Added — RHI 抽象层
- 32 个 LuminRHI 公共接口（`io.github.openlumin.rhi.*`）：`LuminRHI`、`LuminDevice`、`LuminCommandEncoder`、`LuminCommandBuffer`、`LuminRenderPass`、`LuminRenderPassDesc`、`LuminSwapchain`、`LuminSwapchainImage`、`LuminBuffer/Texture/Sampler/Shader/Pipeline/PipelineState/BlendState`、`LuminBufferView/TextureView/VertexFormat/VertexAttribute`、枚举（Format / IndexType / Filter / AddressMode / PolygonMode / CullMode / FrontFace / CompareOp / BlendFactor / BufferUsage）。每个公共类型独立文件（一文件一公共类），便于跨包引用并避免 Java 编译歧义。
- 9 个 LuminRHI_GL 后端文件（`io.github.openlumin.rhi.gl.*`）：`LuminRHI_GL`（含 `DeviceImpl` + `SwapchainImpl` + 5 个枚举转换器）、`LuminBufferGL/TextureGL/TextureViewGL/SamplerGL/ShaderGL/PipelineGL`、`LuminRHICommands`（含 8 个 record 命令 + 接口+基底+封装）、`LuminRHIEncoder`（含 `CommandEncoderImpl` + `RenderPassRecorder/Impl`）。仅 fabric-26.2 编译。
- `LuminRHICurrent`（顶级访问器）支持后端运行期切换。

#### 关键 API 约定
- **draw/drawIndexed 参数按 MC 26.2 实证语义**：
  - `draw(vertexCount, instanceCount, firstVertex, firstInstance)` — 4 参
  - `drawIndexed(indexCount, instanceCount, firstIndex, vertexOffset, firstInstance)` — 5 参（indexCount 在前，baseVertex 第四参）
  - 实证来源：反汇编 vanilla `GuiRenderer`/`SkyRenderer`/`Lightmap` 字节码压栈序列
- **颜色字节序**：写入 GpuBuffer 调用 `ARGB.toABGR(color)` 调序（实证：26.2 RGBA8_UNORM 属性按 [R][G][B][A] 读取小端字节序为 [BB][GG][RR][AA]）
- **接口设计文档**：`docs/RHI_DESIGN.md` 详细描述 4 步迁移路径 + 5 后端实现策略 + 决策表

#### Deferred to Alpha 2.1（后端完整实现）
- `LuminRHICommands.executeRenderPass` 完整 MC 26.2 RenderPass 翻译（setPipeline / setVertexBuffer / setIndexBuffer / setUniformBuffer / bindTexture / draw / drawIndexed 翻译链）
- `LuminBufferGL.uploadBuffer`（持久映射 + ARGB.toABGR 调序写入）
- `LuminTextureGL.uploadTexture`（GpuTexture.write + 格式转换）
- `LuminRHICommands.bind()` 路径解析（ResourceProvider 异步加载 + RenderPipeline.builder）
- `LuminRHI_GL.SwapchainImpl` 桥接到 MC 26.2 `Minecraft.getMainRenderTarget`（26.2 client jar 缺此方法，需走 `Fabric2622Platform` 现有 surface 路径）
- neoforge-26.2 后端同步

#### 已知 MC 26.2 client jar API 缺项（已用回退或 throw）
- `GpuFormat` 缺 `DEPTH32` / `DEPTH32_STENCIL8` / `SRGB8_ALPHA8` / `R11G11B10_FLOAT`（alpha 2.1：探测 deobf 或 ComponentType 子表）
- `AddressMode` 缺 `MIRRORED_REPEAT`（回退 REPEAT）
- `GpuTexture` 缺 `getColorTextureView()`（应在 RenderTarget 上）
- `Minecraft.getMainRenderTarget()` 在 26.2 client jar 缺（需走 Fabric2622Platform 桥接）

### Added — Docs
- `docs/RHI_DESIGN.md`（v1 设计）：LuminRHI 接口 / 资源类型 / 命令录制 / 交换链；4 步迁移路径；DX12/Metal/GLES 兼容子集；ABC vs Pristine Pipeline 决策表
- `docs/ROADMAP_v26.md`（已更新到 v2）：v26.0 North Star + 战略子线 P0-P2 + Alpha 2-5 路线
- `docs/PROPOSALS.md`：PRO-001 BlockBuster 全家桶 / PRO-002 3D 模型支持 / PRO-003 CS2 Demo 录制

### Repository Hygiene
- 分支结构：`main`（保护）/ `fabric-1.21.10`（1.21.10 维护）/ `v26.0`（主开发）/ `v26.0-26.2`（26.2 Release 候选）
- `.gitignore` 强化：`.devres/`、`_refers/`、所有临时文档模式（`*_SUMMARY.md` / `*_STATUS.md` / `*_PLAN.md` 等）已屏蔽
- `.devres/README.md` 工作区模板（gitignored；记录项目方约定）
- `.github/ISSUE_TEMPLATE/` 三类（bug / alpha / rhi）+ `PULL_REQUEST_TEMPLATE.md` + `CODEOWNERS`
- `CHANGELOG.md`（本文档）改为模块化
- `README.md` 重写为状态徽章 + 目录 + 分支 + 关联

---

## [v26.0-alpha.1] — 2026-08（已发布）

详见 git history（commits `8fedf2b` 之前的全部工作）。

- 1.21.10 / 26.1.2 / 26.2 × Fabric/NeoForge 六目标渲染实证
- `SelfTestRenderer` + `GameRendererSelfTestMixin`（菜单 7 图元 + 世界 3D 盒体 + RGB 轴）
- 26.2 渲染修复：
  - `BlurShader.ensureBoxProgram` 补 UBO 声明 + POSITION_COLOR/QUADS
  - `LuminImmediateRenderer` 11 处 `drawIndexed` / 5 处 `draw` 按真实参数语义
  - `LuminImmediateRenderer.putColor` 改 `ARGB.toABGR` 调序
- Despotes v26.11 WS 自动化驱动（点击 / F5 / 帧缓冲截图）+ HTTP 回退
- 13 个参考项目克隆到 `_refers/`（sodium / embeddium / iris / modernui / arc3d / geckolib / superresolution / slideshow / mobileglues / crystal-fracture / blockbuster-particle-extract / + mirror）

<!-- GitHub@NDBlockConnect | BlockConnect@StarsailsClover -->
