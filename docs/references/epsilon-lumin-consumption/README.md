# Epsilon / EpsilonBC → Lumin Graphics 消费面参考

> 来源：NekoyaHouse/Epsilon `26.1.x`（2026-08 强推后的 Lumin Graphics 迁移版）
> 与 BlockConnect/EpsilonBC `v26.0-alpha.3`（内建 gui.lib 血统版本）的对照分析。
> 用途：为 OpenLumin 提供真实的"消费者如何使用渲染库"契约样本。

<!-- GitHub@NDBlockConnect | BlockConnect@StarsailsClover -->

## 文档索引

| 文档 | 内容 |
|------|------|
| [API_CONSUMER_SURFACE.md](API_CONSUMER_SURFACE.md) | upstream Epsilon 消费的 Lumin Graphics 类清单、频次、Maven 坐标 |
| [MIGRATION_ANALYSIS.md](MIGRATION_ANALYSIS.md) | d3baeed8 迁移解剖：删了什么、依赖怎么布、JiJ 校验门、Prism RHI |
| [EPSILONBC_COMPAT_SHIMS.md](EPSILONBC_COMPAT_SHIMS.md) | EpsilonBC 内建等价类 + 兼容 shim（OpenLumin 兼容层设计参考） |

## 核心结论（TL;DR）

1. **UiTree / UiRect / UiTextMetrics 是三大顶点 API**（72/56/55 处引用）——OpenLumin 的兼容性预算应优先保证这三者的签名稳定。
2. upstream Epsilon 已完全删除内嵌渲染栈（-13,527 行），改为 4 个 Lumin Graphics Maven 构件 + 2 个 Prism RHI 后端构件，Jar-in-Jar 分发。
3. EpsilonBC（本 fork）保留了内建 `gui.lib.*`（同源前身），并已验证：**仅靠 5 个小型 shim 即可让为外部库编写的代码编译进内建树**（见 EPSILONBC_COMPAT_SHIMS.md）——这是 OpenLumin 兼容层成本的有力实证。
4. Lua 脚本系统（#420）的渲染面（LuaRender2DService/LuaUiContext）是"新代码直写 Lumin Graphics"的活样本，其内建适配版已在 EpsilonBC 落地。
