# OpenLumin v26.0 Alpha 1 - 渲染验证成功交接文档

**日期**: 2026-07-26  
**状态**: ✅ fabric-1.21.10 LuminImmediateRenderer 渲染验证通过  
**版本**: v26.0 Alpha 1

---

## 🎯 本次验证目标

验证 OpenLumin 在 Fabric 1.21.10 上的基础渲染能力，确认现代 GPU API 路径（RenderPass + UBO）可以正确工作。

## ✅ 验证结果

**成功**：三个彩色矩形正确显示在主菜单左上角 (10, 10) 位置

- **蓝色矩形**：0xFF4488FF，位置 (10, 10)，尺寸 200×40
- **绿色矩形**：0xFF44CC55，位置 (10, 60)，尺寸 200×40
- **半透明白色矩形**：0x8DFFFFFF，位置 (10, 110)，尺寸 200×40

**技术细节**：
- 使用 `LuminImmediateRenderer.beginPosColorQuads()`
- 通过 `LuminRenderPipelines.RECTANGLE` 管线渲染
- shader: `openlumin:rectangle.vsh` + `rectangle.fsh`
- 投影矩阵：`CachedOrthoProjectionMatrixBuffer.get(screenW, screenH, true)`
- UBO 绑定：`RenderPass.bindDefaultUniforms()` 成功传递 Projection + DynamicTransforms

---

## 🔧 关键修复：Shader UBO 绑定格式不匹配

### 问题症状

- 所有渲染操作（setPipeline、bindDefaultUniforms、drawIndexed）日志显示成功
- 顶点坐标在有效范围内 (10-210, 10-150)
- ModelViewMatrix 为单位矩阵，投影矩阵计算正确
- **但屏幕上什么都不显示**

### 根本原因

OpenLumin 的 shader include 文件使用了 **standalone uniform** 声明：

```glsl
// common/src/main/resources/assets/minecraft/shaders/include/dynamictransforms.glsl
// 旧版（错误）
uniform mat4 ModelViewMat;
uniform mat3 NormalMat;
```

而 MC 1.21.10 期望的是 **UBO 格式**：

```glsl
// MC 1.21.10 官方格式（正确）
layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
    float LineWidth;
};
```

**技术解释**：
- `RenderPass.bindDefaultUniforms()` 将数据写入 **UBO 绑定点**
- Shader 中的 **standalone uniform** 不会从 UBO 绑定点读取数据
- 结果：shader 读到的所有矩阵都是零，顶点变换到 (0,0,0,0)，全部被裁剪

### 修复措施

1. **更新 dynamictransforms.glsl**：
   ```glsl
   #version 330
   
   layout(std140) uniform DynamicTransforms {
       mat4 ModelViewMat;
       vec4 ColorModulator;
       vec3 ModelOffset;
       mat4 TextureMat;
       float LineWidth;
   };
   ```

2. **创建 projection.glsl**：
   ```glsl
   #version 330
   
   layout(std140) uniform Projection {
       mat4 ProjMat;
   };
   
   vec4 projection_from_position(vec4 position) {
       vec4 projection = position * 0.5;
       projection.xy = vec2(projection.x + projection.w, projection.y + projection.w);
       projection.zw = position.zw;
       return projection;
   }
   ```

3. **修改 rectangle.vsh**：
   ```glsl
   #version 410 core
   
   #moj_import <minecraft:dynamictransforms.glsl>
   #moj_import <minecraft:projection.glsl>  // 新增
   
   layout(location = 0) in vec3 Position;
   layout(location = 1) in vec4 Color;
   
   layout(location = 0) out vec4 v_Color;
   
   void main() {
       gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
       v_Color = Color;
   }
   ```

4. **fabric-1.21.10 构建配置修正**：
   ```kotlin
   // fabric-1.21.10/build.gradle.kts
   tasks.processResources {
       from(project(":common").sourceSets.main.get().resources)
       // 排除会覆盖 MC 内建 include 的文件
       exclude("assets/minecraft/shaders/include/dynamictransforms.glsl")
   }
   ```

---

## 📊 验证环境

- **Minecraft**: 1.21.10
- **Fabric Loader**: 0.19.3
- **Fabric API**: 0.138.4+1.21.10
- **启动器**: mdl v26.0.0-alpha.3
- **实例路径**: `C:\Users\Sails\AppData\Roaming\mdl\instances\openlumin-fabric-1.21.10\`
- **测试时间**: 2026-07-26 18:52

**日志确认**：
- ✅ 模组加载成功：`openlumin 1.0.0`
- ✅ HudRenderCallback 持续触发
- ✅ 渲染操作完成，无 OpenGL 错误
- ⚠️ 仅有 Realms 认证失败（离线启动正常现象，与渲染无关）

---

## 🎓 关键经验教训

### Shader Uniform 绑定调试黄金法则

1. **编译通过 ≠ 运行有效**  
   桩类（stubs）可以骗过编译器，但运行时是 no-op 或返回零值

2. **日志无错 ≠ 渲染成功**  
   所有 OpenGL 操作可能都成功执行，但 shader 收到的可能是零矩阵

3. **排查优先级**  
   坐标空间 → 投影矩阵 → **uniform 绑定格式** ← 最隐蔽的坑

4. **验证手段**  
   提取 MC jar 中的官方 shader include 文件，直接对比格式差异：
   ```bash
   jar xf ~/.gradle/caches/fabric-loom/.../minecraft-merged-1.21.10-*.jar \
       assets/minecraft/shaders/include/dynamictransforms.glsl
   ```

5. **UBO vs Standalone Uniform**  
   - `layout(std140) uniform Block { mat4 Mat; }` ← 从 UBO 绑定点读数据
   - `uniform mat4 Mat;` ← 需要 `glUniform*` 显式设置，不受 UBO 影响

### 桩类使用判断标准

**判断桩是否安全的黄金原则**：
```bash
jar tf <MC-jar-path> | grep <类路径>
```
- **有结果** → 删除桩，让 MC 提供真实实现
- **无结果** → 桩/shim 安全，可以保留

**已确认在 MC 1.21.10 中存在（删除了桩）**：
- `com/mojang/blaze3d/pipeline/BlendFunction` — 删除桩避免 `NoSuchFieldError: GLINT`
- `net/minecraft/client/input/MouseButtonEvent` — 删除桩避免构造函数签名不匹配

**已确认在 MC 1.21.10 中不存在（桩安全）**：
- `com.mojang.blaze3d.platform.{GpuSampler, FilterMode, TextureFormat, AddressMode, CompareOp}`
- `com.mojang.blaze3d.pipeline.{ColorTargetState, DepthStencilState}`
- `com.mojang.blaze3d.font.SheetGlyphInfo`
- `com.mojang.blaze3d.systems.RenderSystemExtensions`（OpenLumin 自己的委托类）
- `net.minecraft.client.renderer.rendertype.TextureTransform`

---

## 🔜 下一步工作

### 立即待办（高优先级）

1. **启用其他 Renderer 测试**  
   取消注释 `OpenLuminFabric1210Client.java` 中的：
   - `RoundRectRenderer` - 圆角矩形渲染
   - `Render2DScheduler` - 批量 2D 渲染调度
   - `TtfFontLoader` - TTF 字体渲染
   
   验证这些组件是否也能正常工作。

2. **清理调试日志**  
   移除以下文件中的过量 `System.out.println`：
   - `LuminImmediateRenderer.java`
   - `LuminRenderSystem.java`
   - `OpenLuminFabric1210Client.java`
   
   保留必要的错误日志即可。

3. **验证其他 shader**  
   检查以下 shader 是否需要相同的 UBO 修复：
   - `round_rectangle.vsh` - 圆角矩形
   - `ellipse.vsh` - 椭圆
   - `line.vsh` - 线段
   - 其他使用 ModelViewMat/ProjMat 的 shader

### 中期计划

4. **3D 渲染钩子实现**  
   - WorldRenderEvents 在 fabric-rendering-v1 16.2.0+1.21.10 中已移除
   - 需通过 Mixin 注入 `LevelRenderer.renderLevel()` 实现 3D 渲染钩子
   - 验证 `Render3DScheduler` 的轮廓框、填充盒、坐标轴渲染

5. **neoforge-1.21.10 验证**  
   测试 NeoForge 平台是否也能正常渲染（应该可以，使用相同的现代 GPU API）

6. **性能优化**  
   - 检查 UBO 环形缓冲区是否正确复用
   - 验证 `endDynamicUniformFrame()` 是否正确重置写入指针
   - 测量帧率和渲染开销

### 长期计划

7. **填充 1.20.x 模块**  
   - neoforge-1.20.6, 1.20.5, 1.20.4, 1.20.2（已创建骨架）
   - fabric-1.20.6, 1.20.5, 1.20.4, 1.20.2, 1.20.1（已创建骨架）
   - 复制 stubs 目录，补全入口类

8. **旧版 OpenGL 后端实现**  
   为 stubs 路径模块（1.21.1/1.21.3/1.20.x）实现真实的旧版 OpenGL 后端：
   - 使用 `RenderSystem` + `BufferBuilder`/`Tesselator` 立即模式
   - 替换 no-op 桩类为真实 OpenGL 调用
   - 工作量大，但必须完成以支持全版本覆盖

---

## 📝 相关文件清单

### 核心渲染类（已验证）
- `fabric-1.21.10/src/main/java/io/github/openlumin/OpenLuminFabric1210Client.java` - 入口 + 测试代码
- `fabric-1.21.10/src/main/java/io/github/openlumin/LuminRenderSystem.java` - 投影矩阵 + 帧生命周期管理
- `fabric-1.21.10/src/main/java/io/github/openlumin/LuminRenderPipelines.java` - 渲染管线定义
- `fabric-1.21.10/src/main/java/io/github/openlumin/immediate/LuminImmediateRenderer.java` - 即时模式渲染器

### Shader 文件（已修复）
- `common/src/main/resources/assets/openlumin/shaders/rectangle.vsh` - 顶点着色器
- `common/src/main/resources/assets/openlumin/shaders/rectangle.fsh` - 片段着色器
- `common/src/main/resources/assets/minecraft/shaders/include/dynamictransforms.glsl` - ✅ 已修复为 UBO 格式
- `common/src/main/resources/assets/minecraft/shaders/include/projection.glsl` - ✅ 新创建

### 构建配置（已修正）
- `fabric-1.21.10/build.gradle.kts` - ✅ 添加了 `exclude("assets/minecraft/shaders/include/dynamictransforms.glsl")`
- `settings.gradle.kts` - 项目结构定义
- `gradle.properties` - Gradle 配置

### 桩类（Stubs）
- `fabric-1.21.10/src/stubs/java/com/mojang/blaze3d/platform/GpuSampler.java` - 采样器桩
- `fabric-1.21.10/src/stubs/java/com/mojang/blaze3d/pipeline/ColorTargetState.java` - 颜色目标桩
- `fabric-1.21.10/src/stubs/java/com/mojang/blaze3d/font/SheetGlyphInfo.java` - 字形桩
- ...（约 20 个桩类）

---

## 🚀 快速部署命令

### 构建 + 部署 + 启动测试
```powershell
# 设置 JAVA_HOME
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot"

# 构建 fabric-1.21.10 模块
cd C:\Users\Sails\Documents\Workspace\NormalWorkspace\Coding\Minecraft\OpenLumin
.\gradlew :fabric-1.21.10:build

# 部署到测试实例
Copy-Item fabric-1.21.10\build\libs\fabric-1.21.10-1.0.0.jar `
    $env:APPDATA\mdl\instances\openlumin-fabric-1.21.10\mods\openlumin-fabric-1.21.10.jar -Force

# 启动游戏
cd mdl-v26.0-alpha.3-windows-x86_64
.\mdl.exe launch openlumin-fabric-1.21.10 --username TestPlayer
```

### 查看日志
```powershell
Get-Content $env:APPDATA\mdl\instances\openlumin-fabric-1.21.10\logs\latest.log -Tail 50
```

---

## 📖 参考资料

- **MC 1.21.10 渲染 API 文档**: yarn mappings 1.21.10 javadoc
- **NeoForge 1.21.6 Primer**: 列出了现代渲染 API 的引入时间点
- **Fabric API**: fabric-rendering-v1 16.2.0+1.21.10
- **OpenLumin 项目文档**: `memory/FACT.md` - 完整的技术架构和踩坑记录

---

**签字**: Kiro (AI Assistant)  
**审核**: 待泽川确认  
**状态**: ✅ 交接完成，等待下一步指示
