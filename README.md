# OpenLumin

> 跨平台 Minecraft 渲染库（Fabric + NeoForge）——v26.0 渲染超集路线
> Cross-platform Minecraft rendering library — v26.0 rendering superset roadmap

---

## 状态 / Status

| 阶段 | 状态 | 备注 |
|---|---|---|
| Alpha 1（1.21.10/26.1.2/26.2 × Fabric/NeoForge 六目标渲染） | ✅ 已发布 v26.0-alpha.1 | 六目标全部渲染实证；drawIndexed 参数序 + ARGB 字节序修复在 26.2 实证 |
| Alpha 2（RHI 后端化） | 🚧 接口完成，后端 scaffold | 32 个 RHI 公共接口 + 9 个 26.2 GL 后端文件（stub） |
| Alpha 3（光线 / 实体 / 机制） | 📋 计划中 | 参见 `ROADMAP_v26.md` |
| Alpha 4（自研核心：LuminSR / LuminLang / LuminAnimation / 地形网格） | 📋 计划中 | 参见 `ROADMAP_v26.md` |
| Alpha 5（DX12 + Metal 一等公民 + RHI 收敛） | 📋 计划中 | 26.2 已具备现代 RHI 抽象基础；DX12 native 集成和 Metal 桥接待规划 |
| Alpha 4.5（3D 资产与 Demo 录制） | 📋 提案阶段 | 参见 `PROPOSALS.md` |

## 仓库结构 / Repository Structure

```
OpenLumin/
├── fabric-26.1.2/                 # 1.21.10 业务代码基底（GL 路径，OpenGL 4.1+）
├── fabric-26.2/                   # 26.2 Fabric 模块（GL + Vulkan via MC 26.2 GpuDevice）
├── neoforge-1.21.10/              # 1.21.10 NeoForge 模块
├── neoforge-26.1.2/               # 26.1.2 NeoForge 模块
├── neoforge-26.2/                 # 26.2 NeoForge 模块
├── openlumin-testmod/             # 1.21.10 单独测试模组（fabric + neoforge 子模块）
├── _refers/                       # 本地参考项目克隆（sodium, iris, modernui, arc3d, ...）— gitignored
├── docs/                          # 设计文档 + 路线图 + 提案
│   ├── RHI_DESIGN.md              # 渲染硬件抽象设计
│   ├── ROADMAP_v26.md             # v26.0 战略路线图
│   └── PROPOSALS.md               # 生态提案（BlockBuster / 模型 / Demo 录制）
├── .github/                       # GitHub 模板（PR / Issue / CODEOWNERS）
├── .devres/                       # 本地 Agent 工作区模板（gitignored）
├── CHANGELOG.md                   # 发布历史
├── sync-gradle-to-m2.sh           # maven 缓存 SSL 问题绕过脚本
└── memory/                        # 项目知识库（gitignored；本地维护）
```

## 分支策略 / Branching

- `main` — 受保护 / 只读历史
- `fabric-1.21.10` — 1.21.10 维护线（v26.0-alpha.1 已发布）
- `v26.0` — v26.0 主开发线（包含 1.21.10 + 26.1.2 + 26.2 + Alpha 2 起的所有 26.x 工作）
- `v26.0-26.2` — 26.2 专用子线（Phase B 后端稳定化 + 26.2 Release 候选）

详见 `docs/ROADMAP_v26.md` 与 `.github/PULL_REQUEST_TEMPLATE.md`。

## 贡献 / Contributing

按技能规范 `bc-developmentndebugging`：

- 每个主版本独立分支
- 中文 commit message 短标题 + 详情，必要时英文
- SSH 签名（`commit.gpgsign=true`）
- 新增代码 / 文档每约 50 行加水印：`// GitHub@NDBlockConnect | BlockConnect@StarsailsClover`
- 详情见 `.github/PULL_REQUEST_TEMPLATE.md`

## 关联项目 / Related Projects

- [EpsilonBC](https://github.com/NekoyaHouse/Epsilon) — OpenLumin 的下游消费方
- [ModernUI](https://github.com/BloCamLimb/ModernUI) / [Arc3D](https://github.com/BloCamLimb/Arc3D) — 架构参照（LGPL-3.0）
- [Sodium](https://github.com/CaffeineMC/sodium) / [Embeddium](https://github.com/FiniteReality/embeddium) / [Iris](https://github.com/IrisShaders/Iris) — 渲染超集对标
- [Geckolib](https://github.com/bernie-g/geckolib) — 骨骼动画参考
- [superresolution](https://github.com/IReallyWantToSleep/superresolution) — 超分算法参数参考（仅协议，不集成）
- [Project-Crystal-Fracture](https://github.com/Hismeo/Project-Crystal-Fracture) — 下游 showcase 候选

## 许可 / License

TBD（v26.0 正式版前定稿）

---

<!-- GitHub@NDBlockConnect | BlockConnect@StarsailsClover -->
