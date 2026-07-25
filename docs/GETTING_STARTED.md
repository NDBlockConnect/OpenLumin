# OpenLumin 快速上手指南

本文档面向想要在自己的模组中接入 OpenLumin 渲染能力的开发者。

---

## 目录

1. [环境要求](#环境要求)
2. [添加依赖](#添加依赖)
3. [渲染生命周期](#渲染生命周期)
4. [2D 渲染示例](#2d-渲染示例)
5. [3D 渲染示例](#3d-渲染示例)
6. [TTF 字体渲染](#ttf-字体渲染)
7. [后处理特效](#后处理特效)
8. [常见问题](#常见问题)

---

## 环境要求

| 项目 | 要求 |
|------|------|
| Java | 21+ |
| Minecraft | 1.21.10 |
| Fabric Loader | >= 0.15.0 |
| OpenLumin | 1.0.0（v26.0 Alpha 1） |

---

## 添加依赖

### build.gradle.kts（Fabric）

```kotlin
repositories {
    maven("https://jitpack.io")
}

dependencies {
    // 仅编译期依赖（用户已安装 OpenLumin 模组）
    modCompileOnly("io.github.openlumin:openlumin-fabric-1.21.10:1.0.0")
    // 如需打包进 jar（不推荐，会导致体积膨胀）：
    // modImplementation("io.github.openlumin:openlumin-fabric-1.21.10:1.0.0")
}
```

### fabric.mod.json

```json
{
  "depends": {
    "openlumin": ">=1.0.0"
  }
}
```

---

## 渲染生命周期

OpenLumin 的渲染调用**必须在渲染线程上进行**，推荐在以下事件中使用：

| 渲染类型 | 推荐事件（Fabric） | 说明 |
|----------|-------------------|------|
| 2D HUD | `HudRenderCallback.EVENT` | GUI 渲染后执行 |
| 3D 世界 | `WorldRenderLastCallback.EVENT` | 世界渲染末尾执行 |

### 标准帧结构

```java
// HudRenderCallback 注册示例（Fabric 1.21.10）
HudRenderCallback.EVENT.register((drawContext, tickDeltaManager) -> {
    // 1. 开始帧（必须首先调用）
    LuminRenderSystem.beginRenderFrame();
    
    // 2. 设置正交投影矩阵（2D 渲染前必须调用）
    LuminRenderSystem.applyOrthoProjection();
    
    // 3. 渲染内容...
    myRender2D();
    
    // 4. 结束帧（必须最后调用，推荐放在 finally 中）
    LuminImmediateRenderer.endFrame();
    LuminRenderSystem.endDynamicUniformFrame();
});
```

---

## 2D 渲染示例

### 使用 Render2DScheduler（推荐）

`Render2DScheduler` 支持声明式提交，自动按 layer 排序和合批，适合复杂 UI 场景。

```java
import io.github.openlumin.schedulers.render2d.Render2DScheduler;
import io.github.openlumin.text.ttf.TtfFontLoader;
import java.awt.Color;
import java.nio.file.Path;

public class MyHud {
    private final Render2DScheduler scheduler = new Render2DScheduler();
    private final TtfFontLoader font = new TtfFontLoader(Path.of("C:/Windows/Fonts/arial.ttf"));

    public void render(int screenW, int screenH) {
        var layer = scheduler.layer(0);
        
        // 圆角矩形
        layer.addRoundRect(10, 10, 200, 50, 8, new Color(0x44, 0x88, 0xFF));
        
        // 渐变圆角矩形
        layer.addRoundRect(10, 70, 200, 50, 8, new Color(0xFF, 0x44, 0x44), new Color(0xFF, 0xAA, 0x00));
        
        // 椭圆
        layer.addEllipse(10, 130, 100, 40, new Color(0x88, 0xFF, 0x44));
        
        // 文字
        TtfFontLoader.beginRenderFrame();
        layer.addText("Hello, OpenLumin!", 10, 190, 1.0f, Color.WHITE, font);
        
        // 提交绘制
        scheduler.flushAndClear();
    }
}
```

### 使用 LuminImmediateRenderer（低级接口）

适合需要精确控制单个批次的场景：

```java
import io.github.openlumin.immediate.LuminImmediateRenderer;
import io.github.openlumin.LuminRenderPipelines;
import com.mojang.blaze3d.systems.RenderSystem;

// 绘制纯色矩形
LuminImmediateRenderer.PosColorQuads builder =
    LuminImmediateRenderer.beginPosColorQuads(LuminRenderPipelines.RECTANGLE);

Matrix4f matrix = RenderSystem.getModelViewMatrix();
float x = 10, y = 10, w = 200, h = 50;
int color = 0xFF4488FF; // ARGB

builder.vertex(matrix, x,     y,     0, color);
builder.vertex(matrix, x,     y + h, 0, color);
builder.vertex(matrix, x + w, y + h, 0, color);
builder.vertex(matrix, x + w, y,     0, color);
builder.end(); // 立即提交绘制
```

### 使用 RoundRectRenderer（独立渲染器）

适合在单帧内批量提交多个圆角矩形：

```java
import io.github.openlumin.renderers.RoundRectRenderer;
import java.awt.Color;

public class MyComponent {
    // 在渲染线程上延迟初始化
    private RoundRectRenderer renderer;
    
    private RoundRectRenderer renderer() {
        if (renderer == null) renderer = RoundRectRenderer.create();
        return renderer;
    }
    
    public void render() {
        // 添加绘制命令
        renderer().addRoundRect(10, 10, 200, 50, 8, new Color(0x44, 0x88, 0xFF));
        renderer().addVerticalGradient(10, 70, 200, 50, 8,
            new Color(0xFF, 0x44, 0x44), new Color(0xFF, 0xAA, 0x00));
        
        // 批量提交
        renderer().draw();
        renderer().clear();
    }
}
```

---

## 3D 渲染示例

3D 渲染需要在世界渲染末尾（`WorldRenderLastCallback`）调用 flush。

```java
import io.github.openlumin.schedulers.render3d.Render3DScheduler;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.awt.Color;

// 在 GUI 帧中 schedule（比如 HudRenderCallback 中）
public void scheduleWorldRender() {
    LocalPlayer player = Minecraft.getInstance().player;
    if (player == null) return;
    
    // 玩家碰撞箱轮廓
    AABB box = player.getBoundingBox().inflate(0.1);
    Render3DScheduler.INSTANCE.addOutlineBox(box, 0xFFFF3333, 2.0f);
    
    // 半透明填充
    Render3DScheduler.INSTANCE.addFilledBox(box, new Color(0xFF, 0x33, 0x33, 0x44));
    
    // 坐标轴线（从玩家中心出发）
    Vec3 center = player.position().add(0, player.getBbHeight() / 2.0, 0);
    Render3DScheduler.INSTANCE.addLine(center, center.add(2, 0, 0), new Color(0xFF, 0x44, 0x44), 1.5f);
    Render3DScheduler.INSTANCE.addLine(center, center.add(0, 2, 0), new Color(0x44, 0xFF, 0x44), 1.5f);
    Render3DScheduler.INSTANCE.addLine(center, center.add(0, 0, 2), new Color(0x44, 0x44, 0xFF), 1.5f);
}

// 在 WorldRenderLastCallback 中 flush
WorldRenderLastCallback.EVENT.register((context, tickDelta) -> {
    Render3DScheduler.INSTANCE.flush(context.matrixStack());
});
```

---

## TTF 字体渲染

```java
import io.github.openlumin.text.ttf.TtfFontLoader;
import java.awt.Color;
import java.nio.file.Path;

public class MyText {
    // 加载字体（在渲染线程上初始化）
    private final TtfFontLoader arial = new TtfFontLoader(Path.of("C:/Windows/Fonts/arial.ttf"));
    
    public void render(Render2DScheduler.LayerHandle layer) {
        // 每帧开始时调用一次
        TtfFontLoader.beginRenderFrame();
        
        // 普通文字
        layer.addText("Hello, World!", 10, 10, 1.0f, Color.WHITE, arial);
        
        // 渐变文字
        layer.addGradientText("Gradient", 10, 40, 1.0f,
            new Color(0xFF, 0x88, 0x00), new Color(0x00, 0xCC, 0xFF), arial);
    }
}
```

---

## 后处理特效

后处理特效在当前帧缓冲上操作，应在 2D 渲染**之前**调用（否则会影响已绘制的内容）。

```java
import io.github.openlumin.OpenLumin;

// 模糊指定区域
OpenLumin.blur(0, 0, screenW, screenH, 8f, 1.0f);

// FXAA 抗锯齿（作用于整个屏幕）
OpenLumin.fxaa();

// 颜色滤镜（半透明暗色遮罩）
OpenLumin.dim(120); // alpha 0~255

// 程序化背景（适用于主菜单）
OpenLumin.sandboxSea(mouseX, mouseY);
```

---

## 常见问题

**Q：渲染调用放在什么时候？**  
A：所有渲染调用必须在渲染线程上，通常在 `HudRenderCallback.EVENT` 或 `WorldRenderLastCallback.EVENT` 中。

**Q：可以在 `tick` 事件里调用渲染 API 吗？**  
A：不可以，tick 在游戏线程（Server/Logic Thread）上运行，渲染 API 不是线程安全的。

**Q：如何实现 Scissor 裁剪？**  
A：使用 `LuminRenderSystem.toFramebufferScissor(x, y, w, h)` 转换 GUI 坐标到帧缓冲坐标，然后配合 `GL11.glScissor` 使用。

**Q：为什么什么都没有渲染出来？**  
A：检查以下几点：
1. 是否调用了 `LuminRenderSystem.applyOrthoProjection()`？
2. 是否调用了 `LuminRenderSystem.beginRenderFrame()`？
3. 渲染调用是否在 `endFrame()`/`flushAndClear()` 之前？
4. 坐标是否超出了屏幕范围？
