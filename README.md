# OpenLumin

[简体中文](README_zh.md) | **English**

**OpenLumin** is a high-performance 2D/3D rendering library for Minecraft Java Edition mods.  
Originally extracted from the Epsilon HvH client (NekoyaHouse), now maintained as a standalone library to provide a unified, version-agnostic rendering API for any mod.

> **v26.0 Alpha 1** — Fabric 1.21.10 is now available. More platforms coming soon.

## 特性

- **高性能 2D 渲染** - 基于即时模式的批处理渲染系统
  - 矩形、圆角矩形、三角形、阴影渲染
  - 纹理渲染与缓存（LRU 策略，256 条目容量）
  - TTF 字体渲染，支持抗锯齿
  - 精确的裁剪区域（Scissor）坐标转换

- **3D 世界渲染** - 世界空间几何渲染
  - 填充/线框盒子渲染
  - 模糊盒子效果
  - 自由线段渲染

- **着色器系统** - 完整的 GLSL 着色器支持
  - 模糊着色器（高斯模糊）
  - FXAA 抗锯齿
  - 滤镜着色器
  - 菜单背景特效（黑洞、外星地形、云层等）

- **渲染管线** - 灵活的渲染管线抽象
  - GPU 缓冲区管理（Ring Buffer 架构）
  - 帧缓冲（Framebuffer）管理
  - 渲染目标（Render Target）系统

## 核心优化

本库包含以下关键优化：

1. **P1 Scissor 坐标精度优化** - 通过显式 double 类型转换减少浮点误差累积
2. **P1 纹理缓存优化** - LRU 策略自动淘汰，防止内存泄漏
3. **P0 渲染帧生命周期** - 正确的 `beginRenderFrame` 注入时机

## 使用方法

### Gradle 依赖

```kotlin
repositories {
    maven("https://github.com/NDBlockConnect/OpenLumin")
}

dependencies {
    implementation("io.github.openlumin:OpenLumin:1.0.0")
}
```

### 基础示例

```java
import io.github.openlumin.LuminRenderSystem;
import io.github.openlumin.schedulers.render2d.Render2DScheduler;
import io.github.openlumin.schedulers.render3d.Render3DScheduler;

// 2D 渲染
Render2DScheduler.INSTANCE.addRoundRect(
    10, 10, 100, 50,  // x, y, width, height
    5,                 // radius
    0xFFFFFFFF         // color (ARGB)
);

// 3D 渲染
AABB box = new AABB(0, 0, 0, 1, 1, 1);
Render3DScheduler.INSTANCE.addOutlineBox(box, Color.RED);
```

### 渲染管线集成

```java
import io.github.openlumin.LuminRenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;

public void onRender2D(PoseStack poseStack) {
    Render2DScheduler.INSTANCE.flush();
}

public void onRender3D(PoseStack poseStack) {
    Render3DScheduler.INSTANCE.flush(poseStack);
}
```

## 架构说明

### 目录结构

```
io.github.openlumin/
├── buffer/              # GPU 缓冲区管理
├── immediate/           # 即时模式渲染器
├── renderers/           # 专用渲染器（矩形、纹理等）
├── schedulers/          # 渲染调度器（2D/3D）
├── shaders/             # 着色器封装
├── text/                # 文本渲染系统
├── holders/             # 资源持有者（缓存、渲染目标）
├── utils/               # 工具类（裁剪、颜色等）
└── LuminRenderSystem    # 核心渲染系统入口
```

### 关键组件

- **LuminRenderSystem** - 全局渲染状态管理
- **Render2DScheduler** - 2D 渲染命令调度
- **Render3DScheduler** - 3D 渲染命令调度（需手动调用 flush）
- **LuminImmediateRenderer** - 即时模式顶点提交
- **TextureCacheHolder** - 纹理缓存管理（LRU）

## 注意事项

1. **Render3DScheduler 不再自动订阅事件** - 调用方需要在适当的渲染事件中手动调用 `flush(poseStack)`
2. **默认启用 TTF 抗锯齿** - `Render2DScheduler` 固定使用 `TTF_FONT_AA` 管线
3. **GUI 缩放获取** - 直接从 `WindowRenderState.guiScale` 读取，不再依赖 `ClientSetting`

## 许可证

本项目采用 GPL-3.0-only 许可证。详见 [LICENSE](LICENSE) 文件。

## 贡献

欢迎提交 Issue 和 Pull Request！

## 致谢

本库从 Epsilon HvH 模组提取，感谢原作者 Chen_Meng 和 06789 的贡献。
