# Fabric 26.1.2

Minecraft 26.1.2 Fabric adapter and the initial OpenLumin 26.1 OpenGL port baseline.

- Java: 25
- Fabric Loader: 0.19.3
- Fabric API: 0.155.2+26.1.2
- Fabric Loom: 1.17.17

The first milestone is a clean loader build. Rendering subsystems will be migrated incrementally from the 1.21.10 baseline instead of sharing its source set directly.

## Migration status (2026-08-08)

Shared platform abstraction, the minimal GPU API layer, and the immediate renderer wave are migrated and compile-verified against Minecraft 26.1.2:

- `platform/` — `LuminPlatform` (26.1 contract), `PlatformRegistry`, `Fabric2612Platform`
- `LuminRenderSystem` — frame lifecycle, ortho projection via `Projection` + `ProjectionMatrixBuffer`, `LuminRenderTarget`
- `LuminTexture` — independent `GpuSampler` restored via `SamplerCache`
- `buffer/` — `LuminRingBuffer` (long GPU offsets), `BufferUtils`
- `holders/` — `RenderTargetHolder`, `RendererHolder`, `TextureCacheHolder` (Identifier-keyed LRU)
- `utils/render/ScissorUtils`, `renderers/IRenderer`, `api/` enums
- `immediate/` — `LuminImmediateRenderer` (ring-buffer channels), `LuminTessellator`
- `LuminVertexFormats` — 26.1.2 record-style `VertexFormatElement.register(id, index, type, normalized, count)`
- `LuminRenderPipelines` — all OpenLumin pipelines; blending moved into `withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))`
- `shaders/ShaderProgram` — raw GLSL loader with `#moj_import` preprocessing

Key 26.1.2 API deltas handled: `writeTransform` drops the lineWidth argument, `Identifier` replaces `ResourceLocation`, samplers are first-class objects bound via `RenderPass.bindTexture(name, view, sampler)`, buffer offsets are `long`, `createRenderPass` takes label + optional clears, and `VertexFormatElement` lost its Usage parameter.

Shader note: the 26.1.2 official `dynamictransforms.glsl` UBO has no LineWidth member, matching the 4-arg `writeTransform`; OpenLumin 26.1 shader assets still need to be ported alongside the 2D wave.

Next wave: 2D scheduler + shape renderers + shader assets, then text and 3D (Mixin descriptors).
