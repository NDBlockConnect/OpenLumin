# OpenLumin API 参考手册

> **版本**：v26.0 Alpha 1（1.0.0）  
> **平台**：Fabric 1.21.10

---

## 目录

- [OpenLumin（主入口）](#openlumin)
- [LuminRenderSystem](#luminrendersystem)
- [LuminRenderPipelines](#luminrenderpipelines)
- [LuminImmediateRenderer](#luminimmediaterenderer)
- [RoundRectRenderer](#roundrectrenderer)
- [Render2DScheduler](#render2dscheduler)
- [Render3DScheduler](#render3dscheduler)
- [LuminVertexFormats](#luminvertexformats)

---

## OpenLumin

`io.github.openlumin.OpenLumin`

主入口类，提供所有高级渲染 API 的静态快捷方法。

### 模糊

```java
// 区域高斯模糊
public static void blur(float x, float y, float width, float height, float radius, float strength)

// 四角独立圆角遮罩模糊
public static void blur(float x, float y, float width, float height,
    float rTL, float rTR, float rBR, float rBL, float strength)

// 3D 世界空间模糊盒
public static void blur3DBox(AABB box, double strength)
```

### FXAA

```java
public static void fxaa()                          // 对当前帧缓冲执行 FXAA
public static void fxaa(RenderTarget target)       // 对指定 RenderTarget 执行 FXAA
```

### 颜色滤镜

```java
public static void filter(Color color)             // 颜色叠加滤镜
public static void filter(RenderTarget target, Color color)
public static void dim(int alpha)                  // 暗色遮罩（0-255）
public static void filterArgb(int argb)            // ARGB 格式颜色滤镜
```

### GLSL Sandbox（程序化背景）

```java
// 自定义片段着色器
public static void sandbox(ResourceLocation fragmentShader, double mouseX, double mouseY)
public static void sandbox(ResourceLocation fragmentShader, double mouseX, double mouseY, long startTimeMs)

// 内置背景特效
public static void sandboxSea(double mouseX, double mouseY)        // 海洋
public static void sandboxClouds(double mouseX, double mouseY)     // 云层
public static void sandboxAlien(double mouseX, double mouseY)      // 外星地形
public static void sandboxInferno(double mouseX, double mouseY)    // 地狱火焰
public static void sandboxPlanet(double mouseX, double mouseY)     // 星球
public static void sandboxBlackHole(double mouseX, double mouseY)  // 黑洞
public static void sandboxMinecraft(double mouseX, double mouseY)  // Minecraft 风格

// 状态管理
public static void sandboxResetTime()
public static void sandboxClose()
```

---

## LuminRenderSystem

`io.github.openlumin.LuminRenderSystem`

核心渲染系统，提供坐标换算、帧生命周期管理、渲染目标管理等功能。

### 帧生命周期

```java
// 每帧开始时调用（递增帧ID，清理退休缓冲区）
public static void beginRenderFrame()

// 获取当前帧ID
public static long getRenderFrameId()

// 每帧结束时调用（重置 DynamicUniform 写指针）
public static void endDynamicUniformFrame()

// 销毁所有 GPU 资源（模组卸载时调用）
public static void destroyAll()
```

### 投影矩阵

```java
// 设置正交投影矩阵：(0,0) 左上角，(w,h) 右下角，z ∈ [-1000, 1000]
public static void applyOrthoProjection()
```

### 坐标换算

```java
// GUI 缩放信息
public static double getGuiScale()
public static float getScaledWidth()
public static float getScaledHeight()
public static int getScaledWidthInt()
public static int getScaledHeightInt()

// GUI 坐标 → 帧缓冲 Scissor 坐标
public static ScissorRect toFramebufferScissor(float x, float y, float width, float height)
public static ScissorRect toFramebufferScissor(float x, float y, float width, float height, float guiHeight)
```

### 渲染目标

```java
public static void setActiveTarget(@Nullable LuminRenderTarget target)
public static @Nullable LuminRenderTarget getActiveTarget()
```

### LuminRenderTarget（内部类）

```java
// 创建渲染目标
public static LuminRenderTarget create(String name, int width, int height)
public static LuminRenderTarget createWithDepth(String name, int width, int height)

// 操作
public void resize(int newWidth, int newHeight)
public void clear()
public void close()

// 访问
public int width()
public int height()
public ResourceLocation getIdentifier()
```

---

## LuminRenderPipelines

`io.github.openlumin.LuminRenderPipelines`

所有内置渲染管线常量。

| 常量 | 用途 | 顶点格式 |
|------|------|---------|
| `RECTANGLE` | 纯色矩形 | `POSITION_COLOR` |
| `ROUND_RECT` | 圆角矩形 | `ROUND_RECT` |
| `ROUND_RECT_OUTLINE` | 圆角矩形描边 | `ROUND_RECT_OUTLINE` |
| `ELLIPSE` | 椭圆 | `ROUND_RECT` |
| `ARC` | 弧形 | `ROUND_RECT` |
| `SHADOW` | 阴影 | `ROUND_RECT` |
| `TEXTURE` | 带圆角剪裁的纹理 | `TEXTURE` |
| `TRIANGLE` | 三角形 | `POSITION_COLOR` |
| `TTF_FONT_AA` | TTF 抗锯齿文字 | `POSITION_TEX_COLOR` |
| `TTF_FONT_NO_AA` | TTF 无抗锯齿文字 | `POSITION_TEX_COLOR` |

---

## LuminImmediateRenderer

`io.github.openlumin.immediate.LuminImmediateRenderer`

低级即时模式渲染器，直接提交顶点数据。使用 Ring Buffer（3槽）管理 VBO。

### 开始批次

```java
// 纯色四边形（用于矩形）
public static PosColorQuads beginPosColorQuads(RenderPipeline pipeline)

// 纯色三角带
public static PosColorTriangleStrip beginPosColorTriangleStrip(RenderPipeline pipeline)

// 纯色三角扇
public static PosColorTriangleFan beginPosColorTriangleFan(RenderPipeline pipeline)

// 纹理四边形
public static PosTexColorQuads beginPosTexColorQuads(RenderPipeline pipeline, ResourceLocation texture)

// 线段
public static Lines beginLines(RenderPipeline pipeline)

// 帧结束（ring buffer rotate）
public static void endFrame()
```

### Builder 类

**PosColorQuads / PosColorTriangleStrip / PosColorTriangleFan**：

```java
// 添加一个顶点（matrix 为 ModelView 矩阵，color 为 ARGB 格式）
public void vertex(Matrix4f matrix, float x, float y, float z, int color)

// 提交本批次绘制
public void end()
```

**PosTexColorQuads**：

```java
public void vertex(Matrix4f matrix, float x, float y, float z, float u, float v, int color)
public void end()
```

**Lines**：

```java
public void vertex(Matrix4f matrix, PoseStack.Pose pose, float x, float y, float z,
    int color, float nx, float ny, float nz, float width)
public void end()
```

### 使用注意

- `begin*` 和 `end()` 必须配对调用
- `endFrame()` 每帧调用一次（通常在帧渲染结束的 finally 块中）
- 同一个 channel 不支持并发：第一个 `beginPosColorQuads` 的 `end()` 调用之前不能再次调用 `beginPosColorQuads`

---

## RoundRectRenderer

`io.github.openlumin.renderers.RoundRectRenderer`

高性能圆角矩形渲染器，使用 SDF（Signed Distance Field）着色器实现平滑抗锯齿。

### 创建

```java
// 在渲染线程上创建（自动注册到 RendererHolder）
public static RoundRectRenderer create()
```

### 添加绘制命令

```java
// 均匀圆角
public void addRoundRect(float x, float y, float width, float height, float radius, Color color)

// 四角独立圆角（rTL=左上, rTR=右上, rBR=右下, rBL=左下）
public void addRoundRect(float x, float y, float width, float height,
    float rTL, float rTR, float rBR, float rBL, Color color)

// 竖向渐变
public void addVerticalGradient(float x, float y, float width, float height,
    float radius, Color top, Color bottom)

// 横向渐变
public void addHorizontalGradient(float x, float y, float width, float height,
    float radius, Color left, Color right)

// 四角独立颜色渐变（颜色顺序：左上、左下、右下、右上）
public void addRoundRectGradient(float x, float y, float width, float height,
    float rTL, float rTR, float rBR, float rBL,
    Color cTL, Color cBL, Color cBR, Color cTR)
```

### Scissor 裁剪

```java
public void setScissor(int x, int y, int width, int height)
public void clearScissor()
```

### 提交与清理

```java
public void draw()     // 独立 RenderPass 绘制
public void clear()    // 清空缓冲区
public void close()    // 销毁 GPU 资源（模组卸载时调用）
```

---

## Render2DScheduler

`io.github.openlumin.schedulers.render2d.Render2DScheduler`

声明式 2D 渲染调度器，支持多层（layer）排序和自动合批优化。

### 构造

```java
public Render2DScheduler()
public Render2DScheduler(int quadtreeThreshold)  // 自定义合批阈值（默认 192）
```

### 核心 API

```java
// 获取/创建指定 layer 的提交句柄（layer 越小越先渲染）
public LayerHandle layer(int layer)

// 提交全部 layer 并清空
public void flushAndClear()

// 仅提交（不清空）
public void flush()

// 提交指定 layer
public void flushLayer(int layer)

// 清空
public void clear()
public void clearLayer(int layer)

// 状态查询
public boolean isEmpty()
```

### LayerHandle（通过 layer(int) 获取）

```java
// 圆角矩形
void addRoundRect(float x, float y, float w, float h, float r, Color color)
void addRoundRect(float x, float y, float w, float h, float r, Color colorFrom, Color colorTo)  // 渐变

// 描边圆角矩形
void addRoundRectOutline(float x, float y, float w, float h, float r, float outlineWidth, Color color)

// 椭圆
void addEllipse(float x, float y, float w, float h, Color color)

// 弧形
void addArc(float x, float y, float w, float h, float startAngle, float sweepAngle, Color color)

// 矩形（纯色）
void addRect(float x, float y, float w, float h, Color color)

// 三角形
void addTriangle(float x1, float y1, float x2, float y2, float x3, float y3, Color color)

// 阴影
void addShadow(float x, float y, float w, float h, float r, float blurRadius, Color color)

// 纹理
void addTexture(float x, float y, float w, float h, float r, ResourceLocation texture)

// 文字（需要先调用 TtfFontLoader.beginRenderFrame()）
void addText(String text, float x, float y, float scale, Color color, TtfFontLoader font)
void addGradientText(String text, float x, float y, float scale,
    Color colorFrom, Color colorTo, TtfFontLoader font)
```

---

## Render3DScheduler

`io.github.openlumin.schedulers.render3d.Render3DScheduler`

3D 世界空间渲染调度器。在 HUD 帧中 schedule，在世界渲染末尾 flush。

### 访问

```java
public static final Render3DScheduler INSTANCE;
```

### 添加命令

```java
// 线框盒体
public void addOutlineBox(AABB box, int color, float lineWidth)
public void addOutlineBox(AABB box, Color color, float lineWidth)

// 填充半透明盒体
public void addFilledBox(AABB box, Color color)

// 自由线段
public void addLine(Vec3 from, Vec3 to, Color color, float lineWidth)
```

### Flush

```java
// 在 WorldRenderLastCallback 中调用
public void flush(PoseStack poseStack)
```

---

## LuminVertexFormats

`io.github.openlumin.LuminVertexFormats`

OpenLumin 自定义顶点格式，通常不需要直接使用（由内置 Pipeline 自动引用）。

| 格式 | 用途 | 包含字段 |
|------|------|---------|
| `ROUND_RECT` | 圆角矩形 / 椭圆 / 弧 / 阴影 | Position + Color + InnerRect + Radius |
| `ROUND_RECT_OUTLINE` | 圆角描边 | Position + Color + InnerRect + Radius + OutlineWidth |
| `TEXTURE` | 带圆角剪裁纹理 | Position + Color + UV + InnerRect + Radius |
| `POSITION_COLOR_NORMAL_LINE_WIDTH` | 线段 | Position + Color + Normal + LineWidth |
