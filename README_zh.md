# OpenLumin

[English](README.md) | **简体中文**

**OpenLumin** 是专为 Minecraft Java Edition 模组设计的高性能 2D/3D 渲染库。  
最初从 Epsilon HvH 客户端（NekoyaHouse）中提取，现作为独立库维护，为任意模组提供统一、跨版本的渲染 API。

> **v26.0 Alpha 1 正在开发。** Fabric 1.21.10 与 NeoForge 1.21.10 均已通过完整的 2D/3D 运行验证。
> [下载](releases/v26.0-alpha.1) | [发布说明](releases/v26.0-alpha.1/RELEASE_NOTES.md) | [测试结果](memory/FACT.md#架构重构完整完成)

## 特性

### 2D 渲染
- **即时模式渲染器** - 高性能批量渲染
  - 矩形、圆角矩形、三角形、阴影
  - 纹理渲染（256 条目 LRU 缓存）
  - TTF 字体渲染（抗锯齿）
  - 精确的裁剪坐标变换

### 3D 世界渲染
- **世界空间几何体** - 在 3D 世界中渲染
  - 填充/线框盒子
  - 模糊盒效果
  - 自由线条渲染

### Shader 系统
- **完整的 GLSL 支持**
  - 高斯模糊着色器（支持圆角矩形遮罩）
  - FXAA 抗锯齿
  - 颜色滤镜着色器
  - 程序化背景特效（黑洞、外星地形、云层）

### 架构
- **LuminShot 平台抽象** - 跨加载器兼容层
  - `LuminPlatform` 接口
  - `PlatformRegistry` 注册机制
  - Fabric 与 NeoForge 1.21.10 平台实现（现代 GPU API）
- **环形缓冲区 GPU 管理** - 动态容量扩展
- **帧缓冲管理** - 渲染目标系统

## 安装

### 模组开发者

1. 从 [releases](releases/v26.0-alpha.1) 下载 `openlumin-fabric-1.21.10-v26.0-alpha.1.jar`
2. 添加到模组的 `libs/` 文件夹
3. 在 `build.gradle.kts` 中添加依赖：

```kotlin
dependencies {
    modImplementation(files("libs/openlumin-fabric-1.21.10-v26.0-alpha.1.jar"))
}
```

### 测试

1. 从 [releases](releases/v26.0-alpha.1) 下载两个 jar 文件：
   - `openlumin-fabric-1.21.10-v26.0-alpha.1.jar`（库）
   - `openlumin-testmod-fabric-1.21.10-v26.0-alpha.1.jar`（测试模组）
2. 将两个文件都放入 Minecraft `mods/` 文件夹
3. 启动游戏 - 在右上角查看 11 个测试元素

## 快速开始

### 2D 渲染

```java
import io.github.openlumin.schedulers.render2d.Render2DScheduler;
import java.awt.Color;

// 获取调度器实例
Render2DScheduler scheduler = new Render2DScheduler();
var layer = scheduler.layer(0);

// 添加圆角矩形
layer.addRoundRect(10, 10, 100, 50, 5, Color.WHITE);

// 添加渐变文字
layer.addGradientText("OpenLumin", 10, 70, 1.0f, 
    Color.ORANGE, Color.CYAN, fontLoader);

// 刷新到屏幕
scheduler.flushAndClear();
```

### 3D 渲染

```java
import io.github.openlumin.schedulers.render3d.Render3DScheduler;
import net.minecraft.world.phys.AABB;

// 在玩家周围添加轮廓框
AABB box = player.getBoundingBox();
Render3DScheduler.INSTANCE.addOutlineBox(box, 0xFFFF0000, 2.0f);

// 添加 RGB 坐标轴
Vec3 center = player.position();
Render3DScheduler.INSTANCE.addLine(center, center.add(3, 0, 0), Color.RED, 1.5f);
```

## 项目结构

```
OpenLumin/
├── fabric-1.21.10/          # 1.21.10 OpenGL 业务代码基底
├── fabric-26.1.2/           # Fabric 26.1.2 渲染基线（构建+加载验证通过）
├── fabric-26.2/             # Fabric 26.2 Vulkan 基线（构建+加载验证通过）
├── fabric-1.21.4/           # Fabric 1.21.4（旧版 OpenGL 参考）
├── neoforge-1.21.10/        # 复用 1.21.10 基底的 NeoForge 适配层
├── neoforge-26.1.2/         # NeoForge 26.1.2 适配层（构建+加载验证通过）
├── neoforge-26.2/           # NeoForge 26.2 适配层（构建+加载验证通过）
├── neoforge-1.21.4/         # NeoForge 1.21.4（旧版参考）
├── openlumin-testmod/       # 测试模组（独立项目）
└── releases/                # 发布包
```

## 架构

### LuminShot 平台抽象层

```
┌─────────────────────────────────────┐
│  OpenLumin 业务层                    │
│  (Lumin2D, Lumin3D, Shaders, 等)   │
└─────────────────────────────────────┘
                ↓↑
┌─────────────────────────────────────┐
│  LuminShot Platform（抽象）         │
│  - getDevice()                      │
│  - getDynamicUniforms()             │
│  - writeTransform()                 │
│  - resolveColorView/DepthView()     │
└─────────────────────────────────────┘
                ↓↑
┌─────────────────────────────────────┐
│  平台实现                            │
│  - Fabric1210Platform (现代 API)    │
│  - NeoForge1210Platform (现代 API)  │
│  - Fabric1214Platform (旧版 GL)     │
└─────────────────────────────────────┘
```

## 版本支持

| Minecraft | Fabric | NeoForge | 状态 |
|-----------|--------|----------|--------|
| 1.21.10   | ✅      | ✅        | Alpha 1 已完成 |
| 1.21.4    | ✅      | ✅        | 参考 |
| 26.1.2    | ✅      | ✅        | 构建+加载验证通过，渲染显示待实测 |
| 26.2      | ✅      | ✅        | 构建+加载验证通过，渲染显示待实测（Vulkan 基底） |

Minecraft 1.21.x 及以上版本不支持 Forge；OpenLumin 的现代产品线集中维护 Fabric 与 NeoForge。

## 开发路线图

- **Alpha 1** 🔄 - 在 Fabric 与 NeoForge 上支持 1.21.10、26.1.2、26.2 的基础 LuminGraphics API
- **Alpha 2** 🔜 - 性能优化（Sodium/Iris 研究）
- **Alpha 3** 🔜 - 高级光照、实体渲染
- **Alpha 4** 🔜 - 替代 Sodium/Iris/Optifine
- **Alpha 5+** 🔜 - v26.0 稳定版

## 文档

- [发布说明](releases/v26.0-alpha.1/RELEASE_NOTES.md)
- [项目知识库](memory/FACT.md)
- [API 设计](docs/API_DESIGN.md)
- [迁移指南](VERSION_MIGRATION.md)

## 贡献

欢迎贡献！请提交 issue 和 pull request。

## 许可证

本项目采用 GPL-3.0-only 协议。详见 [LICENSE](LICENSE)。

## 致谢

从 Epsilon HvH 模组提取。感谢原作者 Chen_Meng 和 06789。
