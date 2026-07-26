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
// 2026.x+ (New versioning system)
// ========================================
include("neoforge-26.2")
include("neoforge-26.1")

// ========================================
// 1.21.x (11+ versions)
// ========================================
include("neoforge-1.21.10")
include("fabric-1.21.10")
// include("forge-1.21.10")  // TODO: Forge 尚未发布 1.21.10 版本，暂时禁用

include("neoforge-1.21.4")
include("fabric-1.21.4")
include("forge-1.21.4")

include("neoforge-1.21.3")
include("fabric-1.21.3")
include("forge-1.21.3")

include("neoforge-1.21.1")
include("fabric-1.21.1")
include("forge-1.21.1")

// ========================================
// 1.20.x
// ========================================
include("neoforge-1.20.6")
include("fabric-1.20.6")
include("forge-1.20.6")

include("neoforge-1.20.5")
include("fabric-1.20.5")
include("forge-1.20.5")

include("neoforge-1.20.4")
include("fabric-1.20.4")
include("forge-1.20.4")

include("neoforge-1.20.2")
include("fabric-1.20.2")
include("forge-1.20.2")

include("fabric-1.20.1")
include("forge-1.20.1")

// ========================================
// 1.19.x
// ========================================
include("fabric-1.19.4")
include("forge-1.19.4")

include("fabric-1.19.3")
include("forge-1.19.3")

include("fabric-1.19.2")
include("forge-1.19.2")

// ========================================
// 1.18.x
// ========================================
include("fabric-1.18.2")
include("forge-1.18.2")

// ========================================
// 1.17.x (MatrixStack → PoseStack boundary)
// ========================================
include("fabric-1.17.1")
include("forge-1.17.1")

// ========================================
// 1.16.x
// ========================================
include("fabric-1.16.5")
include("forge-1.16.5")

// ========================================
// 1.15.x
// ========================================
include("fabric-1.15.2")
include("forge-1.15.2")

// ========================================
// 1.14.x
// ========================================
include("fabric-1.14.4")
include("forge-1.14.4")

// ========================================
// 1.13.x (oldest supported)
// ========================================
include("fabric-1.13.2")
include("forge-1.13.2")
