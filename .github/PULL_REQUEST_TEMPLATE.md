# Pull Request 模板

## 标题

`<type>(<scope>): <subject>`

类型 type: `feat` / `fix` / `chore` / `docs` / `ref` / `test` / `build`
范围 scope: 模块名（如 `rhi`, `render2d`, `26.2` 等）
主题 subject: 一句话描述改动

## 描述

- 改了什么 / Why / How
- 关联 issue（关闭/相关 #编号）
- 关联 commit

## 检查清单

- [ ] 编译通过（compileJava 至少目标模块）
- [ ] 已加水印（新增代码 / 文档每 ~50 行 `GitHub@NDBlockConnect | BlockConnect@StarsailsClover`）
- [ ] CHANGELOG.md 更新（如有用户可见改动）
- [ ] ROADMAP_v26.md / FACT.md（如有路线/事实变更）更新
- [ ] 中文 commit message 短标题 + 英文/中文详情（首行 ≤50 字符）
- [ ] SSH 签名 (`commit.gpgsign = true`)

## 协议 / Licensing

- 不引入 GPL 传染代码
- Arc3D/ModernUI 走 LGPL 链接隔离
- superresolution 仅协议参考，不拷代码

## 关联 / Related

- ROADMAP_v26.md 章节:
- Alpha 阶段:
