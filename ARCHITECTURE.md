# OpenLumin 基础设施架构文档

## 项目概述

OpenLumin 是一个为 Minecraft 模组开发设计的高性能 2D/3D 渲染库，支持从最新的 2026.x 版本到 1.13.2 的所有主流版本和加载器组合。

## 架构设计原则

### 1. 基础设施级别的稳定性
- **零破坏性变更**：所有版本共享统一的 API 接口
- **向后兼容**：新版本功能不影响旧版本使用
- **独立构建**：每个版本模块可独立编译和发布

### 2. 模块化设计
```
OpenLumin/
├── common/                    # 核心逻辑层（无 Minecraft 依赖）
│   ├── 数据结构
│   ├── 算法实现
│   └── 着色器资源
│
├── <loader>-<version>/        # 版本适配层
│   ├── Minecraft API 桥接
│   ├── 加载器特定集成
│   └── 版本差异抽象
```

### 3. 版本覆盖矩阵

| 版本系列 | NeoForge | Fabric | Forge | 总计 |
|---------|----------|--------|-------|------|
| 26.x    | 2        | -      | -     | 2    |
| 1.21.x  | 4        | 4      | 4     | 12   |
| 1.20.x  | 5        | 6      | 6     | 17   |
| 1.19.x  | -        | 3      | 3     | 6    |
| 1.18.x  | -        | 1      | 1     | 2    |
| 1.17.x  | -        | 1      | 1     | 2    |
| 1.16.x  | -        | 1      | 1     | 2    |
| 1.15.x  | -        | 1      | 1     | 2    |
| 1.14.x  | -        | 1      | 1     | 2    |
| 1.13.x  | -        | 1      | 1     | 2    |
| **总计** | **11**   | **19** | **19** | **49** |

## 核心技术栈

### 渲染抽象层
- **顶点格式**：自定义 VertexFormat（SDF 圆角、纹理 UV）
- **缓冲管理**：Ring Buffer 架构，减少 GPU 上传
- **批处理优化**：合并同类渲染调用

### 跨版本兼容策略

#### API 重大变更点
1. **1.17+**: `MatrixStack` → `PoseStack`
2. **1.16+**: RenderSystem 重构
3. **1.15-**: Blaze3D 包不存在，使用 LWJGL 直接调用
4. **26.x**: 全新版本体系，API 可能存在未知变更

#### 适配方案
- **接口统一**：common 定义抽象接口
- **实现分离**：每个版本模块提供具体实现
- **编译时绑定**：Gradle 依赖配置决定加载的实现

## 构建系统

### Gradle 多模块架构
```kotlin
// settings.gradle.kts
include("common")
include("neoforge-26.2")
include("fabric-1.21.4")
// ... 49 个模块
```

### 依赖管理
```
common (无 MC 依赖)
  ↓
版本模块 (依赖 common + MC)
  ↓
最终 JAR (包含 common + 版本代码)
```

## 发布策略

### Maven 坐标规范
```
io.github.openlumin:OpenLumin-<loader>-<version>:1.0.0
```

示例：
- `io.github.openlumin:OpenLumin-neoforge-1.21.4:1.0.0`
- `io.github.openlumin:OpenLumin-fabric-1.20.1:1.0.0`

### CI/CD 流程
1. **单元测试**：验证 common 逻辑正确性
2. **编译测试**：所有 49 个模块编译通过
3. **集成测试**：在真实 Minecraft 环境中测试
4. **发布**：上传到 Maven Central 和 GitHub Releases

## 维护规范

### 代码修改流程
1. 修改 `common/` 核心逻辑
2. 更新受影响的版本模块适配代码
3. 运行 `./gradlew build` 验证所有模块
4. 提交前检查所有版本编译通过

### 版本更新策略
- **补丁版本** (1.0.x)：Bug 修复，不改 API
- **次版本** (1.x.0)：新增功能，保持兼容
- **主版本** (x.0.0)：破坏性变更，需迁移指南

## 性能指标

### 渲染性能目标
- **2D 批处理**：单帧 10000+ 矩形 @ 60 FPS
- **TTF 字体**：Atlas 缓存命中率 > 95%
- **GPU 上传**：Ring Buffer 复用率 > 80%

### 内存占用目标
- **纹理缓存**：256 条目 LRU，约 64MB
- **顶点缓冲**：每 Renderer 默认 16KB，可配置

## 开发者接口承诺

### 稳定 API
```java
// 这些接口在所有版本中保持一致
Render2DScheduler.INSTANCE.addRoundRect(x, y, w, h, radius, color);
Render3DScheduler.INSTANCE.addOutlineBox(aabb, color);
```

### 扩展点
```java
// 允许自定义 Renderer
public interface IRenderer {
    void addVertex(...);
    void draw();
    void clear();
}
```

## 社区贡献指南

### 新增版本支持
1. 在 `settings.gradle.kts` 添加模块
2. 创建 `<loader>-<version>/build.gradle.kts`
3. 复制最接近版本的适配代码
4. 调整 API 差异
5. 提交 PR 附带测试截图

### Bug 报告要求
- Minecraft 版本
- 加载器类型和版本
- 复现步骤
- 崩溃日志

---

**文档版本**: 1.0.0  
**最后更新**: 2026-07-21  
**维护者**: NDBlockConnect Team
