name: RHI Backend Change (v26.0)
about: 修改 LuminRHI 接口或后端实现 / Changes to LuminRHI interface or backend implementations
title: "[RHI] "
labels: ["rhi", "v26.0", "breaking-change"]
assignees: []

---

## 背景 / Background

LuminRHI 是 OpenLumin 渲染硬件抽象（设计见 `docs/RHI_DESIGN.md`）。修改接口或后端实现前请确认以下事项。

LuminRHI is OpenLumin's render hardware interface (see `docs/RHI_DESIGN.md`). Before changing the interface or a backend, confirm the following.

## 影响范围 / Scope

- [ ] 接口 (io.github.openlumin.rhi.*)
- [ ] 26.2 GL 后端 (fabric-26.2/src/.../io/github/openlumin/rhi/gl/)
- [ ] 26.1.2 (fabric-26.1.2/) — 仅接口，无后端
- [ ] neoforge-26.2 (neoforge-26.2/)
- [ ] 业务层（render2d / render3d / immediate / shaders）

## 兼容性 / Compatibility

- [ ] 向后兼容（业务层不需要改）
- [ ] 破坏性（需要先和 v26.0-26.2 维护者协调）

## 验证 / Verification

- [ ] compileJava 双版本通过
- [ ] 26.2 fabric + NeoForge 自测渲染 7 图元
- [ ] 没有遗漏字节序（ARGB.toABGR 调用点）
- [ ] 没有遗漏 drawIndexed 参数序（参 docs/26.2 RHI 修复实证）

## 关联 / Related

- docs/RHI_DESIGN.md 章节:
- 关联 commit:
