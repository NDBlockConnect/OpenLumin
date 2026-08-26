# Changelog

All notable changes to OpenLumin are documented here.

## [v26.0-alpha.1] — iteration (2026-08)

### Fixed — 26.1.2
- `BlurShader.ensureBoxProgram`: `blur_3d_box` pipeline was missing `DynamicTransforms`/`Projection`
  UBO declarations and a `POSITION_COLOR`/`QUADS` vertex format (vanilla `POST_PROCESSING_SNIPPET`
  declares neither — verified via `javap` on the deobf jar). The 3D box blur could never render.

### Fixed — 26.2
- **Rendering was fully blind.** Root cause: `drawIndexed` argument order. Vanilla semantics are
  `drawIndexed(indexCount, instanceCount, firstIndex, baseVertex, baseInstance)` (verified against
  `GuiRenderer`/`SkyRenderer`/`Lightmap` bytecode); we passed
  `(baseVertex, 0, indexCount, 1, 1)` — i.e. `indexCount = 0`, a silent no-op draw.
  All 11 `drawIndexed` and 5 `draw` call sites rewritten to the real semantics.
- **Vertex colors were R/B swapped.** `putColor` wrote the raw ARGB int; the 26.2 `COLOR` element is
  strict RGBA8_UNORM memory order. Vanilla swizzles through `ARGB.toABGR` — we now do the same
  (matches `RoundRectRenderer`, which was already correct).
- `BlurShader.ensureBoxProgram`: same missing-declaration fix as 26.1.2, via a new
  `LuminRenderPipelines.BOX_BLUR_LAYOUT` plus `BASE_SNIPPET` matrices and a `POSITION_COLOR`/`QUADS`
  vertex binding.

### Added — self-test infrastructure (26.1.2 / 26.2, Fabric + NeoForge)
- `test/SelfTestRenderer` + `mixin/GameRendererSelfTestMixin`: draws 7 known 2D primitives on the
  main menu and submits player outline/fill boxes plus RGB axes in-world. On by default in test
  builds (`-Dopenlumin.selftest.disabled=true` or `OPENLUMIN_SELFTEST=0` disables); flip to opt-in
  before any public release.
- NeoForge modules consume the fabric sources via `srcDir` with their own mixin copies.

### Verified
- In-game rendering confirmed on all six Alpha 1 targets: 1.21.10 / 26.1.2 / 26.2 × Fabric/NeoForge
  (menu 2D, in-world HUD 2D, in-world 3D box + RGB axes; screenshots in session records).

### Known issues
- 26.2: 3D line/fill colors were observed R/B swapped before the fix; post-fix in-world color
  re-confirmation pending a stable session.
- Startup log may show transient `Couldn't compile pipeline openlumin:...` lines — preload-time
  noise only, draws compile lazily and succeed (verified by rendering).

<!-- GitHub@NDBlockConnect | BlockConnect@StarsailsClover -->
