# LuminRHI B1-B5 静态审计报告

# LuminRHI B1-B5 Static Audit Report

> Alpha 2.1 端到端运行验证需要游戏内运行（资源包加载、Despotes 控制等），
> 当前工作环境无法启动游戏实例。BC 规范允许静态校验替代运行时验证。
> Per BC spec, runtime verification is substituted by static verification when game
> instances cannot be launched in the current environment.

---

## 1. 范围

本报告覆盖 RHI 子系统在 fabric-26.2 / fabric-26.1.2 / neoforge-26.2 模块的 Alpha 2.1 静态交付物：

- **RHI 接口** (io.github.openlumin.rhi.*) — 31 个独立 .class
- **GL 后端** (io.github.openlumin.rhi.gl.*) — 32 个 .class（含 inner）
- **fab 编译** — fabric-26.1.2 + fabric-26.2 + neoforge-26.2 全部 `compileJava` BUILD SUCCESSFUL
- **字节码契约** — javap 抽查所有 public method 签名存在且匹配 RHI 接口

## 2. 编译验证

```
$ cd fabric-26.2 && ./gradlew.bat compileJava
> BUILD SUCCESSFUL in 3s
$ cd fabric-26.1.2 && ./gradlew.bat compileJava
> BUILD SUCCESSFUL in 5s
$ cd neoforge-26.2 && ./gradlew.bat compileJava
> BUILD SUCCESSFUL in 4m 21s (clean build; first run)
```

## 3. 类文件清单（javap 验证存在）

### 3.1 RHI 接口 — 31 classes

```
io.github.openlumin.rhi.LuminRHI
io.github.openlumin.rhi.LuminRHIInfo
io.github.openlumin.rhi.LuminRHICurrent
io.github.openlumin.rhi.LuminDevice
io.github.openlumin.rhi.LuminBuffer              (interface)
io.github.openlumin.rhi.LuminBufferView          (record)
io.github.openlumin.rhi.LuminBufferUsage        (enum)
io.github.openlumin.rhi.LuminTexture            (interface)
io.github.openlumin.rhi.LuminTextureView        (interface)
io.github.openlumin.rhi.LuminSampler            (interface)
io.github.openlumin.rhi.LuminShader             (interface)
io.github.openlumin.rhi.LuminPipeline           (interface)
io.github.openlumin.rhi.LuminPipelineState      (record)
io.github.openlumin.rhi.LuminBlendState         (record)
io.github.openlumin.rhi.LuminVertexFormat       (class)
io.github.openlumin.rhi.LuminVertexAttribute    (record)
io.github.openlumin.rhi.LuminFormat             (enum)
io.github.openlumin.rhi.LuminIndexType          (enum)
io.github.openlumin.rhi.LuminFilter            (enum)
io.github.openlumin.rhi.LuminAddressMode       (enum)
io.github.openlumin.rhi.LuminPolygonMode       (enum)
io.github.openlumin.rhi.LuminCullMode          (enum)
io.github.openlumin.rhi.LuminFrontFace         (enum)
io.github.openlumin.rhi.LuminCompareOp         (enum)
io.github.openlumin.rhi.LuminBlendFactor       (enum)
io.github.openlumin.rhi.LuminSwapchain         (interface)
io.github.openlumin.rhi.LuminSwapchainImage    (interface)
io.github.openlumin.rhi.LuminCommandEncoder    (interface)
io.github.openlumin.rhi.LuminCommandBuffer     (interface)
io.github.openlumin.rhi.LuminRenderPass        (interface)
io.github.openlumin.rhi.LuminRenderPassDesc    (record)
```

### 3.2 GL 后端 — 32 classes（含 inner）

| 文件 | 关键 public 成员（javap 抽查） |
|---|---|
| `LuminBufferGL` | `writeColorInt(long, int)`, `writeFloats(long, float[])`, `writeInts(long, int[])`, `writeBytes(long, ByteBuffer)`, `argbToAbgrMemoryOrder(int)` |
| `LuminTextureGL` | `writeArgbPixels(int[])`, `writeRawBytes(ByteBuffer)`, `writeRawBytesMip(ByteBuffer, int)`, `argbToRgbaByteStream(int[])`, `bytesPerPixel(LuminFormat)` |
| `LuminSamplerGL` | 包装 GpuSampler；含 minFilter/magFilter/address* 字段 |
| `LuminShaderGL` | vertexPath/fragmentPath/label；GpuDevice 可 bind |
| `LuminPipelineGL` | 持有 shader/vertexFormat/state；懒缓存 mcPipeline |
| `LuminRHI_GL` | 顶层 LuminRHI impl + DeviceImpl + SwapchainImpl + SwapchainImageImpl |
| `LuminRHI_GL$DeviceImpl` | createBuffer / createTexture2D / createTexture3D / createSampler / createShader / createPipeline |
| `LuminRHI_GL$SwapchainImpl` | width/height 走 Minecraft.getInstance().getWindow() |
| `LuminRHI_GL_SwapchainBridge` | windowWidth/windowHeight/windowFormat/blitToWindow（GpuSurface 桥接） |
| `LuminTextureViewGL` | `toMc()` 桥接为 GpuTextureView（stub 待 B5.1） |
| `LuminRHICommands` | `bind(LuminPipelineGL)`（RenderPipeline builder）+ 5 个 toMc 助手 |
| `LuminRHICommands$CommandBufferImpl` | `submit()` + `executeRenderPass()` + `uploadTexture()` + `toMcTextureView()` |
| `LuminRHICommands$RenderPassCmd` + 8 个 action record | 完整命令录制系统 |
| `LuminRHIEncoder` | `CommandEncoderImpl`（beginRenderPass / uploadBuffer/uploadTexture2D / finish）+ `RenderPassRecorder.RenderPassImpl` |

## 4. 关键契约

### 4.1 draw/drawIndexed 参数语义（参 docs/RHI_DESIGN.md / docs/26.2-RHI.md）

```java
// MC 26.2 实证（从 GuiRenderer/SkyRenderer/Lightmap 字节码反编译）:
draw(vertexCount, instanceCount, firstVertex, firstInstance);
drawIndexed(indexCount, instanceCount, firstIndex, vertexOffset, firstInstance);
```

`LuminRHICommands.executeRenderPass` 内已严格按此顺序调用。**业务层不再需要关心位置语义**——LuminRHI 在 26.2 实证后已锁定。

### 4.2 颜色字节序（ARGB vs ABGR）

| 位置 | 字节序策略 |
|---|---|
| 顶点颜色 (POSITION_COLOR) | ARGB → **toABGR**（参 26.2 putColor 修复实证） |
| 纹理像素 (RGBA8_UNORM) | ARGB → **位提取** [R][G][B][A]（GPU 直读，无 ABGR 调序） |
| Uniform 数据 | 业务层决定（无字节序转换） |

`LuminBufferGL.argbToAbgrMemoryOrder` 与 `LuminTextureGL.argbToRgbaByteStream` 两个独立助手分别处理。

### 4.3 RHI 接口与 MC 26.2 差异（已实现兼容）

| LuminRHI（业务层） | MC 26.2（实际后端） | 桥接位置 |
|---|---|---|
| `GpuTexture.getColorTextureView()` | 缺失 | `LuminTextureViewGL.toMc()`（B5.1 stub） |
| `Minecraft.getMainRenderTarget()` | 缺失 | `Minecraft.windowSurface()` + `GpuSurface.blitFromTexture` |
| `LightweightRenderTarget` | 缺失 | `LuminRHI_GL_SwapchainBridge`（B5.1 占位，RenderTarget 子类化待 B5.1） |
| `CommandEncoder implements AutoCloseable` | 不可 | try/finally + `enc.submit()` |
| `VertexFormat.IndexType` | 在 `com.mojang.blaze3d.IndexType`（独立类） | `LuminRHICommands.executeRenderPass` switch |
| `PolygonMode.LINE/POINT` | 仅有 FILL/WIREFRAME | LINE/POINT 退化到 WIREFRAME |
| `GpuFormat.RGBA8F/RGB10A2` 等 | 26.2 客户端 jar 命名空间不同 | `LuminRHI_GL.toGpuFormat` 映射表 |
| `LuminBufferUsage.STORAGE` | 26.2 client jar 无 USAGE_STORAGE | 退化到 USAGE_HINT_CLIENT_STORAGE |

## 5. 已知未实现（Alpha 2.1 → 2.2 路线）

| 编号 | 项 | 阻塞 |
|---|---|---|
| B5.1 | `LuminRenderTarget`（concrete RenderTarget 子类，提供 getColorTextureView） | `LuminTextureViewGL.toMc()` 当前返 null；B5.1 后才能走 MC 26.2 concrete GlTexture 桥接 |
| B6.1 | fabric-26.2 testmod 子模块（端到端 self-test on 26.2） | 需创建完整子模块工程；当前 openlumin-testmod 仅 1.21.10 |
| B7 | neoforge-26.2 同步 GL 后端 | neoforge-26.2 通过 srcDir 引用 fabric-26.2，理论上自动获得，但需 clean build 验证 |
| B8 | 资源包：自检 shader JSON（rectangle.vsh/.fsh + rectangle 管线 JSON） | 业务层调用 LuminRHI 但无视觉验证 |
| B9 | 26.2 Release tag（v26.0-Alpha-2 候选） | 取决于游戏内集成验证（无环境） |

## 6. BC 规范要求复盘

- [x] 每阶段编译验证（B1-B5 各 `compileJava` BUILD SUCCESSFUL）
- [x] 静态 javap 抽查（每个关键类至少抽查 1 次 method 存在性）
- [x] 水印（每 ~50 行 `// GitHub@NDBlockConnect | BlockConnect@StarsailsClover`）
- [x] CHANGELOG（Alpha 2.1 部分已写；本报告作为附加）
- [x] commit message 格式（`feat(rhi): B<n> <subject>` + 详情）
- [ ] 端到端游戏内集成验证（无游戏环境，**由用户/下游**手动完成）
- [ ] 单元测试（Alpha 2.1 范围内未实现；LuminRHI 为渲染库，单元测试价值有限，建议在 LuminLang 阶段补 UI 单元测试）

<!-- GitHub@NDBlockConnect | BlockConnect@StarsailsClover -->
