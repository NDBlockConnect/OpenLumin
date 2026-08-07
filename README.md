# OpenLumin

[简体中文](README_zh.md) | **English**

**OpenLumin** is a high-performance 2D/3D rendering library for Minecraft Java Edition mods.  
Originally extracted from the Epsilon HvH client (NekoyaHouse), now maintained as a standalone library to provide a unified, version-agnostic rendering API for any mod.

> **v26.0 Alpha 1 development is in progress.** Fabric 1.21.10 and NeoForge 1.21.10 have passed full 2D/3D runtime verification.
> [Download](releases/v26.0-alpha.1) | [Release Notes](releases/v26.0-alpha.1/RELEASE_NOTES.md) | [Test Results](memory/FACT.md#架构重构完整完成)

## Features

### 2D Rendering
- **Immediate Mode Renderer** - High-performance batched rendering
  - Rectangles, rounded rectangles, triangles, shadows
  - Texture rendering with LRU cache (256 entries)
  - TTF font rendering with anti-aliasing
  - Precise scissor coordinate transformation

### 3D World Rendering
- **World-Space Geometry** - Render in 3D world
  - Filled/outline boxes
  - Blur box effects
  - Free-form line rendering

### Shader System
- **Complete GLSL Support**
  - Gaussian blur shader (with rounded rect mask)
  - FXAA anti-aliasing
  - Color filter shader
  - Procedural background effects (black hole, alien terrain, clouds)

### Architecture
- **LuminShot Platform Abstraction** - Cross-loader compatibility layer
  - `LuminPlatform` interface
  - `PlatformRegistry` registration mechanism
  - Fabric and NeoForge 1.21.10 implementations (Modern GPU API)
- **Ring Buffer GPU Management** - Dynamic capacity expansion
- **Framebuffer Management** - Render target system

## Installation

### For Mod Developers

1. Download `openlumin-fabric-1.21.10-v26.0-alpha.1.jar` from [releases](releases/v26.0-alpha.1)
2. Add to your mod's `libs/` folder
3. Add dependency in `build.gradle.kts`:

```kotlin
dependencies {
    modImplementation(files("libs/openlumin-fabric-1.21.10-v26.0-alpha.1.jar"))
}
```

### For Testing

1. Download both jars from [releases](releases/v26.0-alpha.1):
   - `openlumin-fabric-1.21.10-v26.0-alpha.1.jar` (library)
   - `openlumin-testmod-fabric-1.21.10-v26.0-alpha.1.jar` (test mod)
2. Place both in your Minecraft `mods/` folder
3. Launch game - see 11 test elements in top-right corner

## Quick Start

### 2D Rendering

```java
import io.github.openlumin.schedulers.render2d.Render2DScheduler;
import java.awt.Color;

// Get scheduler instance
Render2DScheduler scheduler = new Render2DScheduler();
var layer = scheduler.layer(0);

// Add rounded rectangle
layer.addRoundRect(10, 10, 100, 50, 5, Color.WHITE);

// Add gradient text
layer.addGradientText("OpenLumin", 10, 70, 1.0f, 
    Color.ORANGE, Color.CYAN, fontLoader);

// Flush to screen
scheduler.flushAndClear();
```

### 3D Rendering

```java
import io.github.openlumin.schedulers.render3d.Render3DScheduler;
import net.minecraft.world.phys.AABB;

// Add outline box around player
AABB box = player.getBoundingBox();
Render3DScheduler.INSTANCE.addOutlineBox(box, 0xFFFF0000, 2.0f);

// Add RGB axes
Vec3 center = player.position();
Render3DScheduler.INSTANCE.addLine(center, center.add(3, 0, 0), Color.RED, 1.5f);
```

## Project Structure

```
OpenLumin/
├── fabric-1.21.10/          # 1.21.10 OpenGL business-code baseline
├── fabric-26.1.2/           # Fabric 26.1.2 rendering baseline (build+load verified)
├── fabric-26.2/             # Fabric 26.2 Vulkan baseline (build+load verified)
├── fabric-1.21.4/           # Fabric 1.21.4 (Legacy OpenGL reference)
├── neoforge-1.21.10/        # NeoForge adapter reusing the 1.21.10 baseline
├── neoforge-26.1.2/         # NeoForge 26.1.2 adapter (build+load verified)
├── neoforge-26.2/           # NeoForge 26.2 adapter (build+load verified)
├── neoforge-1.21.4/         # NeoForge 1.21.4 (Legacy reference)
├── openlumin-testmod/       # Test mod (separate project)
└── releases/                # Release packages
```

## Architecture

### LuminShot Platform Abstraction Layer

```
┌─────────────────────────────────────┐
│  OpenLumin Business Layer           │
│  (Lumin2D, Lumin3D, Shaders, etc.)  │
└─────────────────────────────────────┘
                ↓↑
┌─────────────────────────────────────┐
│  LuminShot Platform (Abstract)      │
│  - getDevice()                      │
│  - getDynamicUniforms()             │
│  - writeTransform()                 │
│  - resolveColorView/DepthView()     │
└─────────────────────────────────────┘
                ↓↑
┌─────────────────────────────────────┐
│  Platform Implementations           │
│  - Fabric1210Platform (Modern API)  │
│  - NeoForge1210Platform (Modern API)│
│  - Fabric1214Platform (Legacy GL)   │
└─────────────────────────────────────┘
```

## Version Support

| Minecraft | Fabric | NeoForge | Status |
|-----------|--------|----------|--------|
| 1.21.10   | ✅      | ✅        | Alpha 1 complete |
| 1.21.4    | ✅      | ✅        | Reference |
| 26.1.2    | ✅      | ✅        | Build+load verified; in-game rendering pending |
| 26.2      | ✅      | ✅        | Build+load verified; Vulkan baseline, rendering pending |

Forge is not supported for Minecraft 1.21.x or newer. OpenLumin focuses its modern product line on Fabric and NeoForge.

## Development Roadmap

- **Alpha 1** 🔄 - Basic LuminGraphics API across 1.21.10, 26.1.2, and 26.2 on Fabric and NeoForge
- **Alpha 2** 🔜 - Performance optimization (Sodium/Iris research)
- **Alpha 3** 🔜 - Advanced lighting, entity rendering
- **Alpha 4** 🔜 - Replace Sodium/Iris/Optifine
- **Alpha 5+** 🔜 - v26.0 stable release

## Documentation

- [Release Notes](releases/v26.0-alpha.1/RELEASE_NOTES.md)
- [Project Knowledge Base (Chinese)](memory/FACT.md)
- [API Design](docs/API_DESIGN.md)
- [Migration Guide](VERSION_MIGRATION.md)

## Contributing

Contributions are welcome! Please submit issues and pull requests.

## License

This project is licensed under GPL-3.0-only. See [LICENSE](LICENSE) for details.

## Acknowledgments

Extracted from Epsilon HvH mod. Thanks to original authors Chen_Meng and 06789.
