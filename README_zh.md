# OpenLumin

**English** | [简体中文](README_zh.md)

**OpenLumin** 是专为 Minecraft Java Edition 模组设计的高性能 2D/3D 渲染库。  
最初从 Epsilon HvH 客户端（NekoyaHouse）中提取，现作为独立库维护，为任意模组提供统一、跨版本的渲染 API。

> **v26.0 Alpha 1** — Fabric 1.21.10 现已发布，更多平台持续接入中。

---

## 核心特性

| 特性 | 说明 |
|------|------|
| 🎨 **2D 即时渲染** | 矩形、圆角矩形、椭圆、弧形、阴影、渐变，一行代码完成 |
| ✏️ **TTF 字体渲染** | 加载系统/自定义 TTF，支持 SDF 抗锯齿，图集批处理 |
| 🌍 **3D 世界渲染** | 线框盒体、填充盒体、自由线段，世界空间直接绘制 |
| ✨ **后处理特效** | 高斯模糊、FXAA 抗锯齿、颜色滤镜 |
| 🎬 **动态 GLSL Sandbox** | 运行时编译自定义着色器，内置程序化背景特效 |
| 📦 **声明式调度器** | `Render2DScheduler` 按 layer 批量提交，自动合批 |
| 🔄 **跨平台/跨版本** | Fabric / NeoForge / Forge，MC 1.14 ~ 1.21.10 |

---

## 当前发布状态

| 平台 | MC 版本 | 状态 |
|------|---------|------|
| Fabric | 1.21.10 | ✅ **Alpha 1** |
| NeoForge | 1.21.10 | 🔧 开发中 |
| 其他版本 | - | 🗓 规划中 |

---

## 快速接入（下游模组）

### 1. 添加依赖

**`build.gradle.kts`**：
```kotlin
repositories {
    maven("https://jitpack.io")
    // 或 maven("https://github.com/NDBlockConnect/OpenLumin/releases")
}

dependencies {
    modImplementation("io.github.openlumin:openlumin-fabric-1.21.10:1.0.0")
}
```

**`fabric.mod.json`**：
```json
{
  "depends": {
    "openlumin": ">=1.0.0"
  }
}
```

### 2. 最简示例

在 `RenderGuiEvent`（Fabric：`HudRenderCallback`）中绘制一个圆角矩形：

```java
import io.github.openlumin.OpenLumin;
import io.github.openlumin.LuminRenderSystem;

// 在 HUD 渲染回调中：
LuminRenderSystem.applyOrthoProjection();
OpenLumin.draw.roundRect(10, 10, 200, 50, 8, new Color(0x44, 0x88, 0xFF));
```

更多示例见 [快速上手指南](docs/GETTING_STARTED.md)。

---

## 文档

- 📖 [快速上手指南](docs/GETTING_STARTED.md) — 完整接入流程，含代码示例
- 📚 [API 参考手册](docs/API_REFERENCE.md) — 所有公开 API 的完整说明
- 📋 [更新日志](CHANGELOG.md)

---

## 许可证

本项目采用 [GPL-3.0-only](LICENSE) 协议。

---

## 致谢

本库从 Epsilon HvH 模组提取，感谢原作者 Chen_Meng 和 06789 的贡献。
