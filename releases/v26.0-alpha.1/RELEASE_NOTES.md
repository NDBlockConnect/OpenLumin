# OpenLumin v26.0 Alpha 1 发布说明

发布日期：2026-07-29

## 概述

OpenLumin v26.0 Alpha 1 是项目的首个公开测试版本，包含基础的 2D/3D 渲染功能和全新的平台抽象层架构。

## 支持版本

| 平台组合 | 状态 | JAR |
|---|---|---|
| Minecraft 1.21.10 + Fabric | ✅ 发布，2D/3D 运行验证通过 | `openlumin-fabric-1.21.10-v26.0-alpha.1.jar` |
| Minecraft 1.21.10 + NeoForge 21.10.64 | ✅ 运行验证通过 | （经 sourceSets 与 Fabric 同基底） |
| Minecraft 26.1.2 + Fabric 0.19.3 | ✅ 构建+加载验证通过，渲染显示待实测 | `openlumin-fabric-26.1.2-v26.0-alpha.1.jar` |
| Minecraft 26.1.2 + NeoForge 26.1.2.94 | ✅ 构建+加载验证通过，渲染显示待实测 | `openlumin-neoforge-26.1.2-v26.0-alpha.1.jar` |
| Minecraft 26.2 + Fabric 0.19.3 | ✅ 构建+加载验证通过，渲染显示待实测 | `openlumin-fabric-26.2-v26.0-alpha.1.jar` |
| Minecraft 26.2 + NeoForge 26.2.0.51-beta | ✅ 构建+加载验证通过，渲染显示待实测 | `openlumin-neoforge-26.2-v26.0-alpha.1.jar` |

测试模组（仅 1.21.10）：`openlumin-testmod-fabric-1.21.10-v26.0-alpha.1.jar`

## 新增功能

### 核心渲染系统
- ✅ **LuminImmediateRenderer** - 即时模式渲染器
  - 支持 Position+Color、Position+Tex+Color、Lines 等多种顶点格式
  - 支持 Quads、TriangleStrip、TriangleFan 等绘制模式
- ✅ **RoundRectRenderer** - 圆角矩形渲染器
  - 支持四角独立圆角半径
  - 支持纯色和渐变填充
  - SDF 抗锯齿
- ✅ **Render2DScheduler** - 2D 渲染调度器
  - 声明式 API
  - Layer 层级管理
  - Scissor 裁剪支持
- ✅ **Render3DScheduler** - 3D 世界渲染调度器
  - 线框盒子、填充盒子
  - 3D 线条渲染
  - 自动深度测试和混合

### Shader 系统
- ✅ **FilterShader** - 颜色滤镜后处理
- ✅ **FXAAShader** - 快速抗锯齿
- ✅ **BlurShader** - 高斯模糊（支持圆角矩形遮罩）
- ✅ **GlslSandBox** - 运行时 shader 编译

### 字体渲染
- ✅ **TtfFontLoader** - TTF 字体加载和渲染
  - 纯色文字
  - 渐变文字
  - 抗锯齿

### 架构改进
- ✅ **LuminShot Platform 抽象层** - 隔离平台差异
  - `LuminPlatform` 接口定义
  - `PlatformRegistry` 注册机制
  - `Fabric1210Platform` 实现（现代 GPU API）
  - `NeoForge1210Platform` 实现（现代 GPU API）
- ✅ **测试与库分离** - `openlumin-testmod` 独立测试模组
- ✅ **项目结构优化** - 主分支只保留 4 个活跃版本

## 安装方法

### 使用 OpenLumin 库（开发者）

1. 下载 `openlumin-fabric-1.21.10-v26.0-alpha.1.jar`
2. 放入 Minecraft 实例的 `mods/` 目录
3. 在你的模组中添加依赖（Gradle）：
   ```kotlin
   modImplementation(files("path/to/openlumin-fabric-1.21.10-v26.0-alpha.1.jar"))
   ```

### 测试渲染功能（用户）

1. 下载两个 jar 文件：
   - `openlumin-fabric-1.21.10-v26.0-alpha.1.jar`（主库）
   - `openlumin-testmod-fabric-1.21.10-v26.0-alpha.1.jar`（测试模组）
2. 都放入 Minecraft 实例的 `mods/` 目录
3. 启动游戏，在主菜单右上角可以看到 11 个测试图元
4. 进入世界后，玩家周围会显示红色盒子和 RGB 坐标轴

## 测试用例说明

### 2D 渲染（主菜单右上角）
1-3. **纯色矩形**：蓝色、绿色、半透明白色
4-6. **圆角矩形**：橙色纯色、紫→青渐变、绿色独立圆角
7-9. **Scheduler 图元**：红色圆角矩形、黄色椭圆、半透明蓝色矩形
10-11. **TTF 文字**："OpenLumin 1.21.10"（白色）、"Gradient Text"（橙→青渐变）

### 3D 渲染（进入世界后）
12. **玩家线框盒子**：红色，线宽 2.0
13. **玩家填充盒子**：半透明红色
14. **RGB 坐标轴**：红色 X 轴、绿色 Y 轴、蓝色 Z 轴（各 3 格长）

## 已知问题

- ⚠️ 26.1.2/26.2 四个 JAR 已通过构建与 MDL 加载验证（模组加载、平台注册成功），游戏内渲染显示效果待实测确认
- ⚠️ Minecraft 1.21.x 及以上不提供 Forge 版本
- ⚠️ 26.2 为 Vulkan 基底，渲染 API 与 26.1 差异较大（bind group、GpuFormat、PrimitiveTopology），shader 资源需在实测中核对属性绑定
- ⚠️ 部分 Shader 功能（3D box blur）未实现
- ⚠️ 字体渲染需要手动指定 TTF 文件路径

## 技术细节

### 架构重构
- **Phase 1**: 创建 LuminShot Platform 抽象层 ✅
- **Phase 2**: 重构业务层使用抽象接口 ✅
- **Phase 3**: 扩展到 NeoForge 1.21.10 ✅
- **Phase 4**: 推广到 26.1.2/26.2 的 Fabric 与 NeoForge，共 6 个 Alpha 1 目标 ✅（构建+加载验证通过；26.1.2/26.2 渲染显示待游戏内实测）

### 渲染验证
- ✅ 平台抽象层运行时验证通过
- ✅ Dynamic Transforms UBO 自动扩容正常
- ✅ 2D/3D 渲染管线完整测试通过

### 性能
- UBO 环形缓冲区动态扩容（2 → 8192）
- Shader 预编译缓存
- 批量渲染优化

## 下一步计划（Alpha 2）

1. **26.1.2/26.2 游戏内渲染实测与修正**（shader 属性绑定、采样器、混合状态）
2. **Alpha 2 性能研究**：Sodium、Iris、Optifine 参考实现
3. **完成六个平台目标后进入 Alpha 2 性能研究**

## 反馈与支持

- GitHub Issues: https://github.com/your-org/OpenLumin/issues
- Discord: [待添加]

---

**警告**：这是 Alpha 测试版本，可能包含未知 bug。不建议在生产环境使用。
