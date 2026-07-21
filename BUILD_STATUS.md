# OpenLumin 跨版本构建系统

## 🎯 项目状态

### 已完成的基础设施

✅ **多模块架构** - 49 个模块 (1 common + 48 版本模块)
✅ **版本配置中心** - buildSrc/Versions.kt 统一管理
✅ **自动化生成** - generate-modules.py 批量创建模块
✅ **构建系统** - settings.gradle.kts 和根 build.gradle.kts
✅ **元数据模板** - fabric.mod.json / neoforge.mods.toml / forge mods.toml

### 模块覆盖范围

| 加载器 | 模块数 | 版本范围 |
|--------|--------|----------|
| **NeoForge** | 11 | 26.2, 26.1, 1.21.x, 1.20.x |
| **Fabric** | 19 | 1.21.x → 1.13.2 |
| **Forge** | 19 | 1.21.x → 1.13.2 |
| **Common** | 1 | 版本无关核心 |
| **总计** | **50** | - |

### 模块列表

```
2026.x 系列:
  - neoforge-26.2
  - neoforge-26.1

1.21.x 系列 (12 模块):
  - neoforge-1.21.10, fabric-1.21.10, forge-1.21.10
  - neoforge-1.21.4, fabric-1.21.4, forge-1.21.4
  - neoforge-1.21.3, fabric-1.21.3, forge-1.21.3
  - neoforge-1.21.1, fabric-1.21.1, forge-1.21.1

1.20.x 系列 (17 模块):
  - neoforge-1.20.6, fabric-1.20.6, forge-1.20.6
  - neoforge-1.20.5, fabric-1.20.5, forge-1.20.5
  - neoforge-1.20.4, fabric-1.20.4, forge-1.20.4
  - neoforge-1.20.2, fabric-1.20.2, forge-1.20.2
  - fabric-1.20.1, forge-1.20.1

1.19.x 系列 (6 模块):
  - fabric-1.19.4, forge-1.19.4
  - fabric-1.19.3, forge-1.19.3
  - fabric-1.19.2, forge-1.19.2

1.18.x → 1.13.x 系列 (10 模块):
  - fabric-1.18.2, forge-1.18.2
  - fabric-1.17.1, forge-1.17.1
  - fabric-1.16.5, forge-1.16.5
  - fabric-1.15.2, forge-1.15.2
  - fabric-1.14.4, forge-1.14.4
  - fabric-1.13.2, forge-1.13.2
```

## 🏗️ 架构说明

### 核心设计

```
OpenLumin/
├── common/                    # 核心代码（无 MC 依赖）
│   ├── src/main/java         # （空，实际代码在各版本模块）
│   └── src/main/resources    # 着色器、纹理等资源
│
├── <loader>-<version>/       # 版本适配模块
│   ├── build.gradle.kts      # 依赖 common + MC
│   ├── src/main/java         # 从根 src/ 复制或链接
│   └── src/main/resources    # 模组元数据 + common 资源
│
├── buildSrc/                 # 版本配置中心
│   └── src/main/kotlin/Versions.kt
│
├── generate-modules.py       # 模块生成器
└── build-phase1.sh          # 分阶段构建脚本
```

### 依赖关系

```
common (核心资源)
  ↓
版本模块 (MC API 适配)
  ↓
最终 JAR (common + 版本代码)
```

## 🚀 下一步工作

### Phase 1: 核心代码迁移

当前 `src/main/java` 中的代码需要：

1. **分析 API 差异** - 识别跨版本不兼容的调用
2. **提取抽象层** - 创建版本无关接口
3. **实现适配层** - 每个版本实现具体接口

### Phase 2: 验证构建

```bash
# 方法 1: 完整构建（需要 4+ 小时）
./gradlew build

# 方法 2: 分阶段构建
./build-phase1.sh  # Phase 1: 26.2 → 1.20.2 (28 模块)
./build-phase2.sh  # Phase 2: 1.20.1 → 1.17.1 (8 模块)
./build-phase3.sh  # Phase 3: 1.16.5 → 1.13.2 (10 模块)

# 方法 3: 单模块验证
./gradlew :neoforge-1.21.4:build
```

### Phase 3: API 抽象化

需要处理的关键 API 差异：

| 版本分界 | API 变更 | 受影响模块 |
|---------|---------|-----------|
| **1.17+** | `MatrixStack` → `PoseStack` | 所有渲染调用 |
| **1.16+** | RenderSystem 重构 | immediate/, renderers/ |
| **1.15-** | 无 Blaze3D 包 | 需直接用 LWJGL |
| **26.x** | 新版本体系（未知变更） | 全部 |

### Phase 4: CI/CD 配置

- [ ] GitHub Actions 矩阵构建
- [ ] 自动发布到 Maven Central
- [ ] 版本标签策略

## 📊 当前代码分析

### 现有依赖（from src/）

**Minecraft 包**:
- `net.minecraft.client.*`
- `net.minecraft.world.phys.AABB`
- `net.minecraft.resources.Identifier`

**Blaze3D 包**:
- `com.mojang.blaze3d.vertex.*`
- `com.mojang.blaze3d.pipeline.*`
- `com.mojang.blaze3d.systems.RenderSystem`

### 需要抽象的模块

1. **LuminRenderSystem.java** - 依赖 `WindowRenderState` (1.21.4+)
2. **LuminVertexFormats.java** - `VertexFormat.builder()` API
3. **renderers/** - 所有 Renderer 使用 PoseStack
4. **schedulers/** - 2D/3D 渲染调度器
5. **shaders/** - 着色器加载和绑定

## 🛠️ 开发指南

### 添加新版本支持

```bash
# 1. 编辑 generate-modules.py，添加版本配置
# 2. 重新生成
python generate-modules.py

# 3. 编辑 settings.gradle.kts，添加 include()
# 4. 更新 buildSrc/Versions.kt

# 5. 构建验证
./gradlew :<模块名>:build
```

### 修改核心逻辑

```bash
# 1. 修改任意版本模块的 src/main/java
# 2. 复制相同逻辑到其他版本（如需要）
# 3. 处理 API 差异
# 4. 验证所有受影响版本
./gradlew build
```

## 📝 文件清单

- [x] `settings.gradle.kts` - 50 个模块定义
- [x] `build.gradle.kts` - 根项目配置
- [x] `buildSrc/` - 版本配置中心
- [x] `common/build.gradle.kts` - 核心模块
- [x] `generate-modules.py` - 模块生成器
- [x] `build-phase1.sh` - 分阶段构建脚本
- [x] `ARCHITECTURE.md` - 架构文档
- [x] `BUILD_STATUS.md` - 本文档

## ⏱️ 预估工作量

| 任务 | 预估时间 | 优先级 |
|------|---------|-------|
| API 抽象层设计 | 8 小时 | P0 |
| 1.21.x 适配实现 | 16 小时 | P0 |
| 1.20.x 适配实现 | 12 小时 | P1 |
| 1.19.x - 1.17.x 适配 | 16 小时 | P1 |
| 1.16.x - 1.13.x 适配 | 20 小时 | P2 |
| 26.x 探索性适配 | 8 小时 | P2 |
| CI/CD 配置 | 4 小时 | P1 |
| **总计** | **84 小时** | - |

---

**最后更新**: 2026-07-21  
**基础设施状态**: ✅ 完成  
**下一步**: API 抽象层设计
