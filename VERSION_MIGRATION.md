# OpenLumin 版本文件夹迁移记录

## 2026-07-29 文件结构优化

从主分支移除以下文件夹，转为独立分支管理：

### 已移除（待创建分支）

| 文件夹 | Java 文件数 | 建议分支名 | 状态 |
|--------|------------|-----------|------|
| neoforge-26.1 | 33 | neoforge-26.1 | 骨架完成，待填充 |
| neoforge-26.2 | 33 | neoforge-26.2 | Vulkan 基底预留 |

### 主分支保留（当前活跃开发）

| 文件夹 | Java 文件数 | 用途 |
|--------|------------|------|
| fabric-1.21.10 | 35 | OpenGL 基底 - Phase 2 已完成 |
| neoforge-1.21.10 | 34 | OpenGL 基底 - 已完成并通过 2D/3D 运行验证 |
| fabric-1.21.4 | 104 | 旧版 OpenGL 参考实现 |
| neoforge-1.21.4 | 147 | 旧版 OpenGL 参考实现 |

## 分支策略

- **主分支（main）**：只包含当前活跃开发的 4 个版本
- **版本分支**：每个 Minecraft 版本/加载器组合一个分支
- **加载器范围**：Minecraft 1.21.x 及以上仅维护 Fabric 和 NeoForge，不再创建或适配 Forge 模块
- **新功能开发**：先在基底版本（fabric-1.21.10 或 neoforge-26.2）完成，再向其他版本移植

## 2026-08-06 Alpha 1 路线调整

- Minecraft 1.21.x 及以上停止 Forge 支持，现代产品线固定为 Fabric + NeoForge。
- Alpha 1 从 9 个目标缩减为 6 个目标：1.21.10、26.1.2、26.2 各自的 Fabric 与 NeoForge。
- `fabric-26.1.2` 与 `neoforge-26.1.2` 独立加载器骨架均已构建通过。
- 26.1.2 使用 Java 25；Fabric 使用 Loom 1.17.17、Loader 0.19.3、Fabric API 0.155.2，NeoForge 使用 26.1.2.94 与 NeoGradle 7.1.38。
