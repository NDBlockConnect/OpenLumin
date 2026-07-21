# OpenLumin 代码库隔离方案

## 🎯 目标

解决当前 48 个版本模块共享根目录 `src/` 的问题，实现：
1. **版本隔离** - 每个版本有独立的源码目录
2. **代码复用** - 版本无关代码集中管理
3. **API 抽象** - 跨版本兼容的接口设计

## 🏗️ 新架构设计

### 目录结构

```
OpenLumin/
├── common/
│   └── src/main/java/io/github/openlumin/
│       ├── api/                        # 抽象接口层
│       │   ├── RenderContext.java      # 渲染上下文抽象
│       │   ├── VertexBuilder.java      # 顶点构建抽象
│       │   ├── ShaderProgram.java      # 着色器抽象
│       │   └── TextureManager.java     # 纹理管理抽象
│       │
│       ├── core/                       # 核心逻辑（版本无关）
│       │   ├── RingBuffer.java         # GPU 缓冲管理
│       │   ├── LRUCache.java           # LRU 缓存
│       │   ├── AtlasAllocator.java     # 图集分配器
│       │   └── SDFGenerator.java       # SDF 生成器
│       │
│       ├── math/                       # 数学工具
│       │   ├── Transform.java
│       │   └── BoundingBox.java
│       │
│       └── utils/                      # 工具类
│           ├── ColorUtils.java
│           └── ResourceLoader.java
│
├── neoforge-1.21.4/
│   └── src/main/java/io/github/openlumin/
│       ├── impl/                       # 版本适配实现
│       │   ├── NeoForge1214RenderContext.java
│       │   ├── NeoForge1214VertexBuilder.java
│       │   └── Platform.java           # 平台初始化入口
│       │
│       └── [符号链接到 common/core/]   # 继承核心代码
│
├── fabric-1.20.1/
│   └── src/main/java/io/github/openlumin/
│       ├── impl/
│       │   ├── Fabric1201RenderContext.java
│       │   └── Platform.java
│       │
│       └── [符号链接]
│
└── forge-1.16.5/
    └── src/main/java/io/github/openlumin/
        ├── impl/
        │   ├── Forge1165RenderContext.java  # 处理无 Blaze3D 的情况
        │   └── Platform.java
        │
        └── [符号链接]
```

## 🔧 实施步骤

### Phase 1: 提取版本无关代码 (4 小时)

**1.1 分析依赖关系**
```bash
# 扫描 Minecraft 特定 API 调用
grep -r "import net.minecraft" src/main/java
grep -r "import com.mojang.blaze3d" src/main/java
```

**1.2 识别核心模块**
- ✅ 可提取到 common: RingBuffer, LRUCache, ColorUtils
- ⚠️ 需要抽象: LuminRenderSystem, LuminVertexFormats
- ❌ 版本特定: renderers/, schedulers/

**1.3 创建抽象接口**
```java
// common/src/main/java/io/github/openlumin/api/RenderContext.java
package io.github.openlumin.api;

public interface RenderContext {
    double getGuiScale();
    float getScaledWidth();
    float getScaledHeight();
    void pushMatrix();
    void popMatrix();
    void translate(double x, double y, double z);
}
```

### Phase 2: 版本适配实现 (8 小时)

**2.1 按版本分界创建实现**

| 版本范围 | 实现类前缀 | 关键差异 |
|---------|-----------|---------|
| 26.2 → 1.21.x | Modern | WindowRenderState, 新版 VertexFormat |
| 1.20.x → 1.17.x | Standard | PoseStack, RenderSystem |
| 1.16.x | Legacy | MatrixStack, RenderSystem 旧 API |
| 1.15.x → 1.13.x | Ancient | 无 Blaze3D, 直接用 LWJGL |

**2.2 实现示例**
```java
// neoforge-1.21.4/src/main/java/io/github/openlumin/impl/NeoForge1214RenderContext.java
package io.github.openlumin.impl;

import io.github.openlumin.api.RenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.WindowRenderState;

public class NeoForge1214RenderContext implements RenderContext {
    private final WindowRenderState state;
    
    public NeoForge1214RenderContext() {
        this.state = Minecraft.getInstance().getWindow().getRenderState();
    }
    
    @Override
    public double getGuiScale() {
        return state.guiScale();
    }
    
    @Override
    public float getScaledWidth() {
        return (float) (state.screenWidth() / state.guiScale());
    }
    
    // ... 其他实现
}
```

```java
// forge-1.16.5/src/main/java/io/github/openlumin/impl/Forge1165RenderContext.java
package io.github.openlumin.impl;

import io.github.openlumin.api.RenderContext;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.matrix.MatrixStack;

public class Forge1165RenderContext implements RenderContext {
    private final Minecraft mc;
    private final MatrixStack stack;
    
    public Forge1165RenderContext(MatrixStack stack) {
        this.mc = Minecraft.getInstance();
        this.stack = stack;
    }
    
    @Override
    public double getGuiScale() {
        return mc.getWindow().getGuiScale();
    }
    
    // ... 使用 MatrixStack 实现
}
```

### Phase 3: 符号链接设置 (1 小时)

**3.1 Windows 符号链接**
```powershell
# 为每个版本模块创建符号链接
$modules = @(
    "neoforge-1.21.4",
    "fabric-1.20.1",
    "forge-1.16.5"
    # ... 全部 48 个
)

foreach ($module in $modules) {
    $target = "common\src\main\java\io\github\openlumin\core"
    $link = "$module\src\main\java\io\github\openlumin\core"
    
    New-Item -ItemType SymbolicLink -Path $link -Target $target
}
```

**3.2 Git 处理**
```gitignore
# .gitignore 添加
# 不追踪符号链接目标内容（由 common/ 管理）
*/src/main/java/io/github/openlumin/core
*/src/main/java/io/github/openlumin/math
*/src/main/java/io/github/openlumin/utils
```

### Phase 4: 构建配置调整 (2 小时)

**4.1 更新版本模块依赖**
```kotlin
// neoforge-1.21.4/build.gradle.kts
dependencies {
    // 编译时依赖 common API
    compileOnly(project(":common"))
    
    // 运行时将 common 打包进 JAR
    implementation(project(":common", "shadow"))
}
```

**4.2 Common 模块配置**
```kotlin
// common/build.gradle.kts
plugins {
    id("java-library")
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

configurations {
    create("shadow")
}

dependencies {
    // 只依赖版本无关库
    compileOnly("org.joml:joml:1.10.5")
    compileOnly("org.jetbrains:annotations:24.0.1")
    
    // 不依赖任何 Minecraft API
}
```

## 📊 代码分类结果

### 可直接提取到 common/core/

| 文件 | 理由 |
|------|------|
| `utils/ColorUtils.java` | 纯数学计算 |
| `utils/GeometryUtils.java` | 纯几何算法 |
| `cache/LRUCache.java` | 通用缓存 |
| `buffer/RingBuffer.java` | 通用缓冲 |
| `sdf/SDFGenerator.java` | 纯算法 |

### 需要抽象后提取到 common/api/

| 文件 | 抽象接口 | 实现位置 |
|------|---------|---------|
| `LuminRenderSystem.java` | `RenderContext` | `*/impl/` |
| `LuminVertexFormats.java` | `VertexBuilder` | `*/impl/` |
| `renderers/*.java` | `Renderer` | `*/impl/renderers/` |
| `shaders/ShaderProgram.java` | `ShaderProgram` | `*/impl/shaders/` |

### 必须保留在版本模块

| 文件 | 理由 |
|------|------|
| `Platform.java` | 加载器特定初始化 |
| `mixins/*.java` | Mixin 必须在模块内 |
| `*/impl/*.java` | 版本适配实现 |

## 🔄 迁移策略

### 渐进式迁移（推荐）

**步骤 1**: 先迁移 3 个代表性版本
```
1. neoforge-1.21.4  (最新, WindowRenderState)
2. fabric-1.20.1    (主流, PoseStack)
3. forge-1.16.5     (旧版, MatrixStack)
```

**步骤 2**: 验证这 3 个版本构建通过
```bash
./gradlew :neoforge-1.21.4:build
./gradlew :fabric-1.20.1:build
./gradlew :forge-1.16.5:build
```

**步骤 3**: 复制模式到相似版本
```
neoforge-1.21.4 模式 → 所有 1.21.x NeoForge
fabric-1.20.1 模式  → 所有 1.20.x - 1.17.x Fabric
forge-1.16.5 模式   → 所有 1.16.x - 1.13.x Forge
```

**步骤 4**: 批量生成符号链接
```bash
python setup-code-isolation.py
```

## 🎯 预期效果

### 构建改进
- ✅ 每个模块独立追踪依赖
- ✅ 增量编译仅影响相关模块
- ✅ 并行构建效率提升 80%

### 代码管理
- ✅ 版本无关代码改一次生效全部
- ✅ 版本特定代码互不干扰
- ✅ 新增版本只需实现 impl/ 层

### 开发体验
- ✅ IDE 正确识别每个模块的类路径
- ✅ 重构工具可安全使用
- ✅ 代码导航更清晰

## ⚠️ 潜在问题

### 符号链接限制
- Windows 需要管理员权限（或开发者模式）
- Git 对符号链接的支持依赖配置

**解决方案**: 提供 Gradle 任务自动设置
```kotlin
// build.gradle.kts
tasks.register("setupSymlinks") {
    doLast {
        // 自动创建符号链接或硬链接
    }
}
```

### 类加载冲突
- 同一个类可能在多个模块出现

**解决方案**: 使用唯一的包名
```
common/: io.github.openlumin.core.*
neoforge-1.21.4/: io.github.openlumin.impl.neoforge1214.*
```

## 📝 下一步行动

1. ⏳ 等待 Gradle 下载完成
2. ⏳ 分析现有代码依赖关系
3. ⏳ 创建 API 抽象接口
4. ⏳ 实现 3 个代表性版本
5. ⏳ 生成自动化脚本
6. ⏳ 批量迁移所有版本

---

**文档日期**: 2026-07-21  
**状态**: 设计阶段  
**预估工时**: 15 小时
