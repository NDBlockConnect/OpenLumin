# Fabric 26.1.2

Minecraft 26.1.2 Fabric adapter and the initial OpenLumin 26.1 OpenGL port baseline.

- Java: 25
- Fabric Loader: 0.19.3
- Fabric API: 0.155.2+26.1.2
- Fabric Loom: 1.17.17

The first milestone is a clean loader build. Rendering subsystems will be migrated incrementally from the 1.21.10 baseline instead of sharing its source set directly.

## Migration status (2026-08-08)

Shared platform abstraction and the minimal GPU API layer are migrated and compile-verified against Minecraft 26.1.2:

- `platform/` — `LuminPlatform` (26.1 contract), `PlatformRegistry`, `Fabric2612Platform`
- `LuminRenderSystem` — frame lifecycle, ortho projection via `Projection` + `ProjectionMatrixBuffer`, `LuminRenderTarget`
- `LuminTexture` — independent `GpuSampler` restored via `SamplerCache`
- `buffer/` — `LuminRingBuffer` (long GPU offsets), `BufferUtils`
- `holders/`, `utils/render/ScissorUtils`, `renderers/IRenderer`, `api/` enums

Key 26.1.2 API deltas handled: `writeTransform` drops the lineWidth argument, `Identifier` replaces `ResourceLocation`, samplers are first-class objects bound at draw time, buffer offsets are `long`, and `createRenderPass` takes label + optional clears.

Next wave: immediate renderer (tessellator, vertex formats, pipelines, shader program), then 2D, text and 3D.
