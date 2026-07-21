# OpenLumin API 抽象层设计

## 设计原则

1. **最小化抽象** - 只抽象真正需要跨版本变化的部分
2. **零开销** - 接口调用不引入性能损失（尽量用 default 方法或 final wrapper）
3. **类型安全** - 使用泛型避免运行时类型转换
4. **向后兼容** - 新版本特性通过 Optional 暴露

## 核心抽象接口

### 1. 渲染上下文 (RenderContext)

**职责**: 封装窗口状态、矩阵栈、GUI 缩放

**版本差异**:
- 1.21.4+: `WindowRenderState` (不可变状态对象)
- 1.20.x-1.17.x: `Window` + `PoseStack`
- 1.16.x: `MainWindow` + `MatrixStack`

```java
package io.github.openlumin.api;

public interface RenderContext {
    /** GUI 缩放因子 */
    double getGuiScale();
    
    /** 缩放后的屏幕宽度 */
    float getScaledWidth();
    
    /** 缩放后的屏幕高度 */
    float getScaledHeight();
    
    /** 原始窗口宽度（像素） */
    int getFramebufferWidth();
    
    /** 原始窗口高度（像素） */
    int getFramebufferHeight();
    
    /** 推送矩阵（进入新坐标空间） */
    void pushMatrix();
    
    /** 弹出矩阵（恢复上一个坐标空间） */
    void popMatrix();
    
    /** 平移 */
    void translate(double x, double y, double z);
    
    /** 缩放 */
    void scale(double x, double y, double z);
    
    /** 旋转（角度制） */
    void rotate(float angle, float x, float y, float z);
}
```

### 2. GPU 缓冲管理 (GpuBufferApi)

**职责**: 封装 GPU 缓冲的创建、映射、上传

**版本差异**:
- 1.21.4+: `GpuBuffer` + `CommandEncoder`
- 1.20.x-1.17.x: `VertexBuffer` + `BufferBuilder`
- 1.16.x: 直接 OpenGL (`glBufferData`)

```java
package io.github.openlumin.api;

import java.nio.ByteBuffer;

public interface GpuBufferApi {
    /** 创建 GPU 缓冲 */
    GpuBufferHandle createBuffer(long sizeBytes, BufferUsage usage);
    
    /** 映射缓冲到 CPU 可写内存 */
    ByteBuffer mapBuffer(GpuBufferHandle handle, long offset, long length);
    
    /** 解除映射（提交到 GPU）*/
    void unmapBuffer(GpuBufferHandle handle);
    
    /** 绑定缓冲到渲染管线 */
    void bindBuffer(GpuBufferHandle handle, int bindingPoint);
    
    /** 释放缓冲 */
    void deleteBuffer(GpuBufferHandle handle);
    
    enum BufferUsage {
        STATIC_DRAW,   // 写一次，读多次
        DYNAMIC_DRAW,  // 频繁更新
        STREAM_DRAW    // 每帧更新
    }
}
```

### 3. 顶点格式 (VertexFormatApi)

**职责**: 定义顶点属性布局

**版本差异**:
- 1.21.4+: `VertexFormat` (不可变)
- 1.20.x: `VertexFormat` (建造者模式)
- 1.16.x: `VertexFormat` (枚举)

```java
package io.github.openlumin.api;

public interface VertexFormatApi {
    /** 位置 (vec3) */
    VertexFormatHandle POSITION_3F();
    
    /** 位置 + 颜色 (vec3 + rgba) */
    VertexFormatHandle POSITION_COLOR();
    
    /** 位置 + UV (vec3 + vec2) */
    VertexFormatHandle POSITION_TEX();
    
    /** 位置 + 颜色 + UV (vec3 + rgba + vec2) */
    VertexFormatHandle POSITION_COLOR_TEX();
    
    /** 自定义格式 */
    VertexFormatHandle custom(VertexAttribute... attributes);
    
    /** 获取步长（字节） */
    int getStride(VertexFormatHandle format);
}
```

### 4. 渲染管线 (RenderPipelineApi)

**职责**: 封装着色器程序、混合模式、深度测试

**版本差异**:
- 1.21.4+: `RenderPipeline` (新架构)
- 1.20.x-1.17.x: `RenderType` + `ShaderInstance`
- 1.16.x: 手动 OpenGL 状态管理

```java
package io.github.openlumin.api;

public interface RenderPipelineApi {
    /** 创建渲染管线 */
    RenderPipelineHandle createPipeline(PipelineDescriptor descriptor);
    
    /** 激活管线 */
    void usePipeline(RenderPipelineHandle handle);
    
    /** 设置 Uniform */
    void setUniform(String name, Object value);
    
    /** 绘制 */
    void draw(DrawCommand command);
    
    class PipelineDescriptor {
        public ShaderHandle vertexShader;
        public ShaderHandle fragmentShader;
        public VertexFormatHandle vertexFormat;
        public BlendMode blendMode;
        public DepthTest depthTest;
        public CullMode cullMode;
    }
    
    enum BlendMode {
        NONE,
        ALPHA,           // 标准 alpha 混合
        ADDITIVE,        // 相加混合（光效）
        MULTIPLY         // 相乘混合（阴影）
    }
}
```

### 5. 纹理管理 (TextureApi)

**职责**: 加载、绑定、采样纹理

**版本差异**:
- 1.21.4+: `GpuTexture` + `GpuSampler`
- 1.20.x: `AbstractTexture` + `TextureManager`
- 1.16.x: 直接 OpenGL ID

```java
package io.github.openlumin.api;

public interface TextureApi {
    /** 创建纹理 */
    TextureHandle createTexture(int width, int height, TextureFormat format);
    
    /** 上传像素数据 */
    void uploadTexture(TextureHandle handle, ByteBuffer pixels);
    
    /** 绑定到纹理单元 */
    void bindTexture(TextureHandle handle, int unit);
    
    /** 设置采样器 */
    void setSampler(TextureHandle handle, SamplerDescriptor sampler);
    
    class SamplerDescriptor {
        public FilterMode minFilter = FilterMode.LINEAR;
        public FilterMode magFilter = FilterMode.LINEAR;
        public WrapMode wrapS = WrapMode.CLAMP;
        public WrapMode wrapT = WrapMode.CLAMP;
    }
    
    enum FilterMode { NEAREST, LINEAR, MIPMAP }
    enum WrapMode { CLAMP, REPEAT, MIRROR }
}
```

## 句柄类型系统

使用类型安全的句柄避免混淆：

```java
package io.github.openlumin.api;

/** 基础句柄接口 */
public sealed interface Handle permits 
    GpuBufferHandle, 
    VertexFormatHandle, 
    RenderPipelineHandle,
    ShaderHandle,
    TextureHandle {
    
    /** 获取原生对象（用于平台特定操作）*/
    Object nativeHandle();
}

public record GpuBufferHandle(Object nativeHandle) implements Handle {}
public record VertexFormatHandle(Object nativeHandle) implements Handle {}
public record RenderPipelineHandle(Object nativeHandle) implements Handle 
public record ShaderHandle(Object nativeHandle) implements Handle {}
public record TextureHandle(Object nativeHandle) implements Handle {}
```

## 平台实现注册

```java
package io.github.openlumin.api;

public class LuminApi {
    private static RenderContext renderContext;
    private static GpuBufferApi gpuBuffer;
    private static VertexFormatApi vertexFormat;
    private static RenderPipelineApi renderPipeline;
    private static TextureApi texture;
    
    /** 由各版本模块的 Platform.java 调用 */
    public static void initialize(
        RenderContext ctx,
        GpuBufferApi buf,
        VertexFormatApi vfmt,
        RenderPipelineApi pipe,
        TextureApi tex
    ) {
        renderContext = ctx;
        gpuBuffer = buf;
        vertexFormat = vfmt;
        renderPipeline = pipe;
        texture = tex;
    }
    
    public static RenderContext context() { return renderContext; }
    public static GpuBufferApi buffer() { return gpuBuffer; }
    public static VertexFormatApi format() { return vertexFormat; }
    public static RenderPipelineApi pipeline() { return renderPipeline; }
    public static TextureApi texture() { return texture; }
}
```

## 版本实现示例

### NeoForge 1.21.4

```java
package io.github.openlumin.impl.neoforge1214;

import io.github.openlumin.api.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.state.WindowRenderState;

public class NeoForge1214RenderContext implements RenderContext {
    private final WindowRenderState state;
    
    public NeoForge1214RenderContext() {
        this.state = Minecraft.getInstance().getWindow().getRenderState();
    }
    
    @Override
    public double getGuiScale() {
        return state.guiScale();
    }
    
    @Override
    public float getScaledWidth() {
        return (float) (state.screenWidth() / state.guiScale());
    }
    
    @Override
    public float getScaledHeight() {
        return (float) (state.screenHeight() / state.guiScale());
    }
    
    @Override
    public int getFramebufferWidth() {
        return state.framebufferWidth();
    }
    
    @Override
    public int getFramebufferHeight() {
        return state.framebufferHeight();
    }
    
    @Override
    public void pushMatrix() {
        // 1.21.4 使用 GameRenderState 的矩阵栈
        var gameState = Minecraft.getInstance().getGameRenderer().getRenderState();
        gameState.pushPose();
    }
    
    @Override
    public void popMatrix() {
        var gameState = Minecraft.getInstance().getGameRenderer().getRenderState();
        gameState.popPose();
    }
    
    @Override
    public void translate(double x, double y, double z) {
        var gameState = Minecraft.getInstance().getGameRenderer().getRenderState();
        gameState.pose().translate((float) x, (float) y, (float) z);
    }
    
    @Override
    public void scale(double x, double y, double z) {
        var gameState = Minecraft.getInstance().getGameRenderer().getRenderState();
        gameState.pose().scale((float) x, (float) y, (float) z);
    }
    
    @Override
    public void rotate(float angle, float x, float y, float z) {
        var gameState = Minecraft.getInstance().getGameRenderer().getRenderState();
        gameState.pose().mulPose(new Quaternionf().rotationAxis(
            (float) Math.toRadians(angle), x, y, z
        ));
    }
}
```

### Forge 1.16.5

```java
package io.github.openlumin.impl.forge1165;

import io.github.openlumin.api.*;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.matrix.MatrixStack;

public class Forge1165RenderContext implements RenderContext {
    private final Minecraft mc;
    private MatrixStack matrixStack;
    
    public Forge1165RenderContext() {
        this.mc = Minecraft.getInstance();
    }
    
    /** 每帧需要外部传入 MatrixStack */
    public void setMatrixStack(MatrixStack stack) {
        this.matrixStack = stack;
    }
    
    @Override
    public double getGuiScale() {
        return mc.getWindow().getGuiScale();
    }
    
    @Override
    public float getScaledWidth() {
        return (float) (mc.getWindow().getWidth() / mc.getWindow().getGuiScale());
    }
    
    @Override
    public float getScaledHeight() {
        return (float) (mc.getWindow().getHeight() / mc.getWindow().getGuiScale());
    }
    
    @Override
    public int getFramebufferWidth() {
        return mc.getWindow().getWidth();
    }
    
    @Override
    public int getFramebufferHeight() {
        return mc.getWindow().getHeight();
    }
    
    @Override
    public void pushMatrix() {
        matrixStack.pushPose();
    }
    
    @Override
    public void popMatrix() {
        matrixStack.popPose();
    }
    
    @Override
    public void translate(double x, double y, double z) {
        matrixStack.translate(x, y, z);
    }
    
    @Override
    public void scale(double x, double y, double z) {
        matrixStack.scale((float) x, (float) y, (float) z);
    }
    
    @Override
    public void rotate(float angle, float x, float y, float z) {
        matrixStack.mulPose(Vector3f.of(x, y, z).rotationDegrees(angle));
    }
}
```

## 迁移步骤

### Phase 1: 创建 API 模块 (2 小时)
1. 创建 `common/src/main/java/io/github/openlumin/api/`
2. 定义所有接口（无实现）
3. 创建句柄类型
4. 创建 `LuminApi` 注册中心

### Phase 2: 实现代表性版本 (6 小时)
1. NeoForge 1.21.4 (最新特性)
2. Fabric 1.20.1 (主流版本)
3. Forge 1.16.5 (旧版兼容)

### Phase 3: 迁移核心代码 (4 小时)
1. 修改 `LuminRingBuffer` 使用 `GpuBufferApi`
2. 修改渲染器使用 `RenderPipelineApi`
3. 修改 `ScissorUtils` 使用 `RenderContext`

### Phase 4: 批量复制模式 (3 小时)
1. 复制 1.21.4 模式到所有 1.21.x
2. 复制 1.20.1 模式到 1.20.x - 1.17.x
3. 复制 1.16.5 模式到 1.16.x - 1.13.x

---

**设计日期**: 2026-07-21  
**状态**: 详细设计阶段  
**下一步**: 等待构建验证 API 可行性
