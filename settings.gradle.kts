rootProject.name = "OpenLumin"

pluginManagement {
    repositories {
        maven("https://maven.aliyun.com/repository/gradle-plugin")
        maven("https://maven.aliyun.com/repository/public")
        gradlePluginPortal()
        maven("https://maven.fabricmc.net/")
        maven("https://maven.neoforged.net/releases")
        maven("https://maven.minecraftforge.net/")
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
        maven("https://maven.minecraftforge.net/")
        maven("https://libraries.minecraft.net/")
    }
}

// Common module (version-agnostic core)
include("common")

// ========================================
// 主分支（main）- 当前活跃开发版本
// ========================================
// 基底开发版本（OpenGL）
include("fabric-1.21.10")   // OpenGL 基底 - Phase 2 已完成
include("neoforge-1.21.10")  // OpenGL 基底 - Phase 3 待实施

// 参考实现（旧版 OpenGL）
include("fabric-1.21.4")     // 完整 OpenGL 实现参考
include("neoforge-1.21.4")   // 完整 OpenGL 实现参考

// ========================================
// 其他版本已移至独立分支
// ========================================
// - forge-1.21.10 → 分支 forge-1.21.10
// - forge-1.21.4  → 分支 forge-1.21.4
// - neoforge-26.1 → 分支 neoforge-26.1
// - neoforge-26.2 → 分支 neoforge-26.2
// - 1.20.x, 1.19.x, 1.18.x 等 → 各自独立分支

