rootProject.name = "OpenLumin"

pluginManagement {
    repositories {
        maven("https://maven.aliyun.com/repository/gradle-plugin")
        maven("https://maven.aliyun.com/repository/public")
        gradlePluginPortal()
        maven("https://maven.fabricmc.net/")
        maven("https://maven.neoforged.net/releases")
        maven("https://maven.architectury.dev/")
    }
}

dependencyResolutionManagement {
    repositories {
        maven("https://maven.aliyun.com/repository/central")
        maven("https://maven.aliyun.com/repository/public")
        mavenCentral()
        maven("https://maven.fabricmc.net/")
        maven("https://maven.neoforged.net/releases")
        maven("https://libraries.minecraft.net/")
    }
}

// ========================================
// 主分支（main）- 当前活跃开发版本
// ========================================
// 基底版本 1: fabric-1.21.10（OpenGL 基底，包含完整业务代码 + 平台抽象）
include("fabric-1.21.10")

// 基底版本 2: fabric-26.2（Vulkan 基底，待创建）
// include("fabric-26.2")

// 其他平台通过 Gradle sourceSets 复用基底版本的业务代码
include("neoforge-1.21.10")  // 复用 fabric-1.21.10 业务代码

// 参考实现（旧版 OpenGL，独立维护）
include("fabric-1.21.4")     // 完整 OpenGL 实现参考
include("neoforge-1.21.4")   // 完整 OpenGL 实现参考

// ========================================
// 其他版本已移至独立分支
// ========================================
// - neoforge-26.1 → 分支 neoforge-26.1
// - neoforge-26.2 → 分支 neoforge-26.2
// - 1.20.x, 1.19.x, 1.18.x 等 → 各自独立分支

