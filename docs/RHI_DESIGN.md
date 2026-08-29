# OpenLumin v26.0 RHI（Render Hardware Interface）设计

# OpenLumin v26.0 RHI Design

> 状态：v26.0 Alpha 2 设计 · Status: v26.0 Alpha 2 design
> 修订：2026-08-26 · GitHub@NDBlockConnect | BlockConnect@StarsailsClover

---

## 0. 现状与目标

### 0.1 现状

当前 `LuminPlatform`（v26.0-Alpha 1）直接委托到 Minecraft 26.x 的 `com.mojang.blaze3d.*` 现代 GPU API：
- `RenderSystem.getDevice()`（实际为 `GpuDevice`）
- `GpuBuffer` / `GpuTexture` / `GpuSampler` / `RenderPass`（MC 的 RHI 抽象）
- 26.1.2 = GL backend only；26.2 = GL + Vulkan backend（MC 自家）

**根本限制**：
- 24.x/1.21.10 用旧 `RenderSystem`（无 RHI 抽象）→ 单独 `LuminPlatform` 分支
- MC 26.2+ 引入 `BindGroupLayout` / `multi-pass frame graph` → LuminPlatform 接口已部分过时（接口参数仍按 26.1.2 设计）
- **没有 DX12 / Metal 后端**——MC 26.x 计划中无 D3D12
- LWJGL 无官方 D3D12 / Metal 绑定

### 0.2 v26.0 目标

**LuminRHI**——OpenLumin 自己的渲染硬件抽象，**与 MC GpuDevice 解耦**，可独立替换后端：

| 后端 | 路径 | 状态 |
|---|---|---|
| **OpenGL 4.1+ / GLES 3.0+**（基线） | LWJGL 3.4 `GL41`/`GLES30` 绑定 | Alpha 2 |
| **Vulkan 1.0+** | LWJGL `vulkan` + VMA | Alpha 2 spike → Alpha 5 |
| **DirectX 12** | 自建 COM 互操作 native（参照 superresolution 的 `native:buildNative` + MinGW/CMake；或第三方 binding） | Alpha 5 |
| **Apple Metal** | LWJGL nativemodule（如有）+ ObjC bridge；或 MoltenVK 抽象层 | Alpha 5 |

> 决策点：是否把 **MC 26.x 自身**（`com.mojang.blaze3d.*`）作为更高级的"Portable Backend"——它已支持 GL+Vulkan；我们写 LuminRHI 适配器，让 LuminPlatform 桥接到 LuminRHI。这样 26.x 用户的 Lumin 路径 = LuminRHI 接口 → MC GpuDevice 适配器 → 任意 MC 后端。**两条路径并存**：(a) 走 LuminRHI（自由选后端）；(b) 走 MC 26.x 抽象（用户安装什么 MC 自带什么后端）。

---

## 1. LuminRHI 接口（v1 草案）

### 1.1 分层

```
┌────────────────────────────────────────────────────────────┐
│  LuminRenderCore  (render2d, render3d, immediate, shaders) │  ← 与后端无关的业务层
└──────────┬─────────────────────────────────────────────────┘
           │ LuminRHI 接口
┌──────────┴─────────────────────────────────────────────────┐
│  Backends: LuminRHI_GL | LuminRHI_Vk | LuminRHI_DX12 | LuminRHI_Metal │
└────────────────────────────────────────────────────────────┘
```

### 1.2 核心类型（伪代码）

```java
// 顶级 RHI 接口
public interface LuminRHI {
    LuminRHIInfo info();                        // 后端名称 / 版本 / 能力
    LuminDevice device();
    LuminSwapchain swapchain();                 // 后端 surface 抽象
    LuminCommandEncoder createEncoder();       // 命令录制器
    void submit(LuminCommandBuffer buffer);    // 提交到设备队列
    void present(LuminSwapchainImage image);   // 显示
}

// 设备能力声明
public record LuminRHIInfo(
    String backendName,          // "OpenGL 4.1" / "Vulkan 1.3.x" / "D3D12" / "Metal"
    String version,
    int maxTextureSize,
    int maxUniformBufferSize,
    boolean supportsBindless,     // bindless textures
    boolean supportsGeometryShader,
    boolean supportsTessellation,
    boolean supportsCompute,      // 重要：GLES 兼容路径可能 false
    boolean supportsFloatTextures,
    // ... 完整能力集（见枚举表）
) {}

// 命令录制器
public interface LuminCommandEncoder {
    // 渲染通道
    LuminRenderPass beginRenderPass(LuminRenderPassDesc desc);
    // 资源上传
    LuminBuffer uploadBuffer(LuminBufferDesc desc, ByteBuffer data);
    LuminTexture uploadTexture(LuminTextureDesc desc, ByteBuffer pixels);
    // 命令缓冲
    LuminCommandBuffer finish();
}

// 渲染通道
public interface LuminRenderPass {
    void setPipeline(LuminPipeline pipeline);
    void setVertexBuffer(int slot, LuminBufferView view);
    void setIndexBuffer(LuminBuffer buffer, LuminIndexType type);
    void setUniformBuffer(int slot, String name, LuminBufferView view);
    void setTexture(int slot, String name, LuminTextureView view, LuminSampler sampler);
    void draw(int vertexCount, int instanceCount, int firstVertex, int firstInstance);
    void drawIndexed(int indexCount, int instanceCount, int firstIndex, int vertexOffset, int firstInstance);
    void end();
}
```

> 关键设计点：参数顺序遵循 vanilla MC 26.2 的 `drawIndexed`/`draw` 真实语义（indexCount 在前，baseVertex 第四参）——参 PRO-001 修复实证。

### 1.3 资源类型

```java
public interface LuminBuffer {
    long size();
    LuminBufferView view(long offset, long length);
    void close();
}
public interface LuminBufferView {
    long offset();
    long length();
}
public interface LuminTexture {
    int width();
    int height();
    int depth();
    LuminFormat format();
    LuminTextureView view();
    void close();
}
public interface LuminSampler {
    LuminFilter minFilter();
    LuminFilter magFilter();
    LuminAddressMode addressU();
    LuminAddressMode addressV();
    LuminAddressMode addressW();
}
// Pipeline（着色器 + 顶点格式 + 状态）
public record LuminPipeline(
    LuminVertexFormat vertexFormat,
    LuminShader shader,
    LuminPipelineState state
) {}
public record LuminPipelineState(
    LuminPolygonMode polygonMode,
    LuminCullMode cullMode,
    LuminFrontFace frontFace,
    boolean depthTest,
    boolean depthWrite,
    LuminCompareOp depthCompare,
    LuminBlendState blend
) {}
```

### 1.4 交换链

```java
public interface LuminSwapchain {
    int width();
    int height();
    LuminFormat format();
    LuminSwapchainImage acquireNextImage();   // 等待 vsync / 获取当前帧
    void present(LuminSwapchainImage image, boolean vsync);
    void resize(int width, int height);      // 窗口大小变更
    void close();
}
```

### 1.5 格式枚举

```java
public enum LuminFormat {
    R8G8B8A8_UNORM, R8G8B8A8_SRGB,
    B8G8R8A8_UNORM, B8G8R8A8_SRGB,  // 跨后端字节序差异（LuminRHI 抽象后业务层无感）
    R16G16B16A16_FLOAT,
    R32G32B32A32_FLOAT,
    D32_FLOAT, D24_UNORM_S8_UINT, D32_FLOAT_S8X24_UINT,
    BC1_UNORM, BC3_UNORM, BC5_UNORM, BC7_UNORM,  // 压缩纹理
    R11G11B10_FLOAT,    // 备用
    A8_UNORM
}
```

> 字节序：LuminRHI 暴露 `view()` 返回后端无关的 `LuminBufferView`；后端内部处理 RGBA↔BGRA 转换（如 26.2 那次 putColor ABGR 修复就是个例证）。**绝不让业务层写裸字节**。

---

## 2. 现有 LuminPlatform → LuminRHI 迁移路径

### 2.1 短期（Alpha 2）

| 步骤 | 内容 | 验收 |
|---|---|---|
| 2.1.1 | 在 fabric-26.1.2 与 fabric-26.2 模块各新建 `LuminRHI` 包，定义接口（`LuminRHI.java` + 资源/命令/管线类型） | 接口编译过；无业务改动 |
| 2.1.2 | 新建 `LuminRHI_GL` 后端：包装 MC 26.1.2 的 `com.mojang.blaze3d.*` GpuDevice（GpuBuffer/GpuTexture/RenderPass 桥接到 LuminRHI） | 现有 26.1.2 测试套件（自测图元）仍渲染正确 |
| 2.1.3 | `LuminPlatform` 重命名为 `LuminPlatformBridge`（适配器）：将 7 个业务方法桥接到 `LuminRHI` | 现有 LuminImmediateRenderer/Render2DScheduler 仍工作（接口方法签名不变） |
| 2.1.4 | 新增 `LuminRHI_GL_ES`（GLES 3.0+ 兼容子集）后端：去掉 compute shader / DSA / SPIR-V binary 路径；用老 `GpuBuffer` 数据传输路径 | 同一业务层在 GLES 后端也能跑（图像质量可降，但能跑）|

### 2.2 中期（Alpha 5 之前）

| 步骤 | 内容 |
|---|---|
| 2.2.1 | `LuminRHI_Vk` 适配 MC 26.2 自家 Vulkan 后端 |
| 2.2.2 | `LuminRHI_Vk_Standalone` 独立实现（用 LWJGL + VMA），不依赖 MC——给 24.x/1.21.10 用户使用 |
| 2.2.3 | `LuminRHI_DX12` + `LuminRHI_Metal`（Alpha 5）|

### 2.3 业务层迁移

`LuminImmediateRenderer`、`Render2DScheduler`、`Render3DScheduler`、`shaders/*` 全部改为：
- 通过 `LuminRHI` 接口调用
- 颜色字节序由后端处理（业务层只传 int Color）
- `drawIndexed` / `draw` 参数按统一真实语义

---

## 3. 资产序列化

- LuminRHI 接口与资源对象独立于后端，跨后端资源兼容（同一 buffer 在 GL/Vk 后端可序列化保存为 .bin 文件，加载时重建）
- `LuminSerializer`：Java 默认序列化（自用）或 CBOR/protobuf（与 LuminLang 互操作）
- `.lumimodel` / `.lumiscn` / `.lumidemo` 等均跨后端可重放

---

## 4. 风险与红线

| 项 | 风险 | 对策 |
|---|---|---|
| MC GpuDevice 抽象 vs 我们的 LuminRHI 抽象 | 双重抽象性能开销 | LuminRHI 编译期内联；业务层只与接口打交道 |
| LWJGL 无 D3D12 绑定 | Alpha 5 工作量 | 评估第三方 binding + 自建 COM 互操作两条路 |
| LWJGL Metal 限制 | macOS 开发资源 | CI macOS runner 提前验证 |
| MC 26.2 BindGroupLayout 抽象泄漏到 LuminRHI | 抽象泄漏 | LuminRHI 用"按名绑定"语义（业务层只管资源 + 槽位），由后端适配器负责翻译到 MC GpuBufferSlice/BindGroupLayout |
| 性能 vs 抽象损失 | 接口调用开销 | 热路径（draw / bind）用对象缓存 + 内联 |

---

## 5. 开放决策

1. LuminRHI 是否暴露 `bindless texture`（bindless、bindless index）作为一等能力？建议：v1 不暴露，业务层走"按名绑定"足够；bindless 在后端内做优化
2. LuminRHI 是否支持 "compute shader" 抽象？Alpha 2 决策：与 LuminRHI_GL_ES 的"无 compute"兼容路径冲突 → 建议 v1 不抽象 compute，超分/计算任务走专有 LuminCompute 接口（GLES 路径不参与）
3. 颜色字节序策略：单端解析（后端内部统一为 RGBA8） vs 双端暴露（业务层按"后端期望"写）？建议：单端解析，业务层永不见 ABGR

---

## 6. 与现有项目代码的桥接示例

```java
// 当前 26.2 路径（改前）
public RenderPass createRenderPass(GpuTextureView color, GpuTextureView depth) {
    return RenderSystem.getDevice().createCommandEncoder().createRenderPass(
        () -> "lumin", color, OptionalInt.empty(), depth, OptionalDouble.empty());
}

// 改后：LuminRHI 桥接
public RenderPass createRenderPass(GpuTextureView color, GpuTextureView depth) {
    LuminRHI rhi = LuminRHI.current();
    var desc = new LuminRenderPassDesc(/* color = */ rhi.wrapTexture(color),
                                         /* depth = */ depth != null ? rhi.wrapTexture(depth) : null);
    return new MC26RenderPassBridge(rhi.createEncoder().beginRenderPass(desc), color, depth);
}
```

→ 业务层 `LuminImmediateRenderer` 等不用改，仍然调 `PlatformRegistry.get().createRenderPass(...)`。

---

*GitHub@NDBlockConnect | BlockConnect@StarsailsClover*
