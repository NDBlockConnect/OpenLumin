# OpenLumin 快速启动指南

## 📦 项目结构概览

OpenLumin 现已支持从 **2026.x (26.2)** 到 **1.13.2** 的 **50 个版本×加载器组合**。

```
50 个模块 = 1 common + 11 NeoForge + 19 Fabric + 19 Forge
```

## 🚀 快速开始

### 1. 验证环境

```bash
# 检查 Java 版本 (需要 21+)
java -version

# 检查 Gradle 版本
./gradlew --version
```

### 2. 构建单个版本（推荐）

```bash
# 示例：构建 NeoForge 1.21.4
./gradlew :neoforge-1.21.4:build

# 示例：构建 Fabric 1.20.1
./gradlew :fabric-1.20.1:build

# 示例：构建 Forge 1.19.2
./gradlew :forge-1.19.2:build
```

### 3. 批量构建（需要时间）

```bash
# Phase 1: 现代版本 (26.2 → 1.20.2) - 约 28 模块
./build-phase1.sh

# 或者构建所有模块（预计 4+ 小时）
./gradlew build
```

## 📂 模块查找

### 按 Minecraft 版本查找

```bash
# 查看某个版本的所有加载器
ls -d *-1.21.4

# 输出示例:
# neoforge-1.21.4/
# fabric-1.21.4/
# forge-1.21.4/
```

### 按加载器查找

```bash
# 查看所有 NeoForge 版本
ls -d neoforge-*/

# 查看所有 Fabric 版本
ls -d fabric-*/

# 查看所有 Forge 版本
ls -d forge-*/
```

## 🔧 开发工作流

### 修改渲染逻辑

1. **选择基准版本**（推荐 1.21.4）
   ```bash
   cd neoforge-1.21.4/src/main/java/io/github/openlumin
   ```

2. **修改代码**
   ```bash
   # 修改任意 .java 文件
   vim LuminRenderSystem.java
   ```

3. **验证构建**
   ```bash
   ./gradlew :neoforge-1.21.4:build
   ```

4. **复制到其他版本**
   ```bash
   # 如果 API 兼容，直接复制
   cp -r neoforge-1.21.4/src/main/java/* fabric-1.21.4/src/main/java/
   ```

### 添加新 Renderer

```bash
# 1. 在基准版本创建
cd neoforge-1.21.4/src/main/java/io/github/openlumin/renderers
vim NewRenderer.java

# 2. 复制到所有版本
for dir in */src/main/java/io/github/openlumin/renderers; do
    cp neoforge-1.21.4/src/main/java/io/github/openlumin/renderers/NewRenderer.java "$dir/"
done

# 3. 批量验证
./gradlew build
```

## 📋 版本矩阵速查

### 现代版本 (1.20.2+)

| 版本 | NeoForge | Fabric | Forge |
|------|----------|--------|-------|
| 26.2 | ✅ | - | - |
| 26.1 | ✅ | - | - |
| 1.21.10 | ✅ | ✅ | ✅ |
| 1.21.4 | ✅ | ✅ | ✅ |
| 1.21.3 | ✅ | ✅ | ✅ |
| 1.21.1 | ✅ | ✅ | ✅ |
| 1.20.6 | ✅ | ✅ | ✅ |
| 1.20.5 | ✅ | ✅ | ✅ |
| 1.20.4 | ✅ | ✅ | ✅ |
| 1.20.2 | ✅ | ✅ | ✅ |

### 中期版本 (1.17.1 - 1.20.1)

| 版本 | NeoForge | Fabric | Forge |
|------|----------|--------|-------|
| 1.20.1 | - | ✅ | ✅ |
| 1.19.4 | - | ✅ | ✅ |
| 1.19.3 | - | ✅ | ✅ |
| 1.19.2 | - | ✅ | ✅ |
| 1.18.2 | - | ✅ | ✅ |
| 1.17.1 | - | ✅ | ✅ |

### 旧版本 (1.13.2 - 1.16.5)

| 版本 | Fabric | Forge | 备注 |
|------|--------|-------|------|
| 1.16.5 | ✅ | ✅ | RenderSystem 重构边界 |
| 1.15.2 | ✅ | ✅ | - |
| 1.14.4 | ✅ | ✅ | - |
| 1.13.2 | ✅ | ✅ | Blaze3D 不存在 |

## 🛠️ 常用命令

```bash
# 清理所有构建产物
./gradlew clean

# 仅编译不打包
./gradlew :neoforge-1.21.4:compileJava

# 查看依赖树
./gradlew :neoforge-1.21.4:dependencies

# 刷新依赖缓存
./gradlew --refresh-dependencies

# 并行构建（加速）
./gradlew build --parallel --max-workers=8

# 查看所有任务
./gradlew tasks
```

## 📚 Maven 依赖使用

### 使用者接入方式

```gradle
repositories {
    maven("https://maven.pkg.github.com/NDBlockConnect/OpenLumin")
}

dependencies {
    // NeoForge 1.21.4
    implementation("io.github.openlumin:OpenLumin-neoforge-1.21.4:1.0.0")

    // Fabric 1.20.1
    implementation("io.github.openlumin:OpenLumin-fabric-1.20.1:1.0.0")

    // Forge 1.19.2
    implementation("io.github.openlumin:OpenLumin-forge-1.19.2:1.0.0")
}
```

## 🐛 故障排查

### 构建失败

```bash
# 1. 清理并重试
./gradlew clean build

# 2. 检查特定模块日志
./gradlew :neoforge-1.21.4:build --stacktrace --info

# 3. 刷新依赖
./gradlew --refresh-dependencies build
```

### 内存不足

```gradle
// 编辑 gradle.properties
org.gradle.jvmargs=-Xmx8G -XX:MaxMetaspaceSize=1G
```

### 编码问题 (Windows)

```bash
# 在 PowerShell 中设置
$env:JAVA_TOOL_OPTIONS="-Dfile.encoding=UTF-8"
```

## 📞 获取帮助

- **文档**: 查看 `ARCHITECTURE.md` 了解架构
- **状态**: 查看 `BUILD_STATUS.md` 了解当前进度
- **Issues**: https://github.com/NDBlockConnect/OpenLumin/issues

## ⚡ 性能优化建议

```gradle
// 在 gradle.properties 添加:
org.gradle.parallel=true
org.gradle.caching=true
org.gradle.configureondemand=true
org.gradle.daemon=true
```

---

**提示**: 首次构建会下载大量依赖，建议使用 VPN 或配置镜像源。
