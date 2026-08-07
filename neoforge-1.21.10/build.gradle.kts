plugins {
    id("net.neoforged.gradle.userdev") version "7.0.179"
}

configurations.all {
    resolutionStrategy.eachDependency {
        if (requested.group == "org.ow2.asm") {
            useVersion("9.7")
            because("NeoForge BootstrapLauncher requires ASM 9.7 on module path; 9.5 conflicts")
        }
    }
}

runs {
    configureEach {
        systemProperty("forge.logging.console.level", "debug")
    }
}

minecraft {
    accessTransformers {
        file("src/main/resources/META-INF/accesstransformer.cfg")
    }
}

// 复用 fabric-1.21.10 的业务代码（基底版本）
val fabricBase = project(":fabric-1.21.10")

sourceSets {
    main {
        java {
            // 复用 fabric-1.21.10 的所有业务代码
            srcDir(fabricBase.file("src/main/java"))
        }
        resources {
            // 复用 fabric-1.21.10 的资源文件
            srcDir(fabricBase.file("src/main/resources"))
        }
    }
}

// 编译时确保 platform/ 下的 NeoForge 实现优先
sourceSets.main.get().apply {
    java {
        // neoforge-1.21.10 的平台实现优先级最高
        srcDir("src/main/java")
        // 排除 Fabric 加载器特定文件
        exclude("io/github/openlumin/OpenLuminFabric1210Client.java")
        exclude("io/github/openlumin/platform/Fabric1210Platform.java")
        // 排除 Fabric 的 Mixin 类（NeoForge 有自己的副本）
        exclude {
            it.file.path.contains("fabric-1.21.10") && it.file.path.contains("mixin")
        }
        // 排除来自 fabric-1.21.10 的 RenderSystemShim（neoforge-1.21.10 有自己的副本）
        exclude {
            it.file.path.contains("fabric-1.21.10") && it.file.path.contains("compat/RenderSystemShim.java")
        }
    }
    resources {
        // 排除 Fabric 模组配置文件
        exclude("fabric.mod.json")
        // 保留 openlumin.mixins.json（NeoForge 也需要 Mixin 进行生命周期管理）
    }
}

// 关键：jar 打包时排除与 Minecraft 原生 API 冲突的适配层类
tasks.named<Jar>("jar") {
    exclude("com/mojang/blaze3d/platform/GpuBuffer.class")
    exclude("com/mojang/blaze3d/platform/GpuBuffer\$*.class")
    exclude("com/mojang/blaze3d/platform/GpuTexture.class")
    exclude("com/mojang/blaze3d/platform/GpuTextureView.class")
    exclude("com/mojang/blaze3d/platform/GpuSampler.class")
    exclude("com/mojang/blaze3d/platform/FilterMode.class")
    exclude("com/mojang/blaze3d/platform/CompareOp.class")
    exclude("com/mojang/blaze3d/buffers/GpuBufferSlice.class")
    exclude("com/mojang/blaze3d/systems/RenderSystemExtensions.class")
    exclude("com/mojang/blaze3d/systems/CommandEncoder.class")
    exclude("com/mojang/blaze3d/systems/RenderPass.class")
    exclude("com/mojang/blaze3d/pipeline/**")
    exclude("net/minecraft/client/renderer/Projection.class")
    exclude("net/minecraft/client/renderer/ProjectionMatrixBuffer.class")
}

dependencies {
    // NeoForge & Minecraft
    implementation("net.neoforged:neoforge:21.10.64")

    // Annotations
    compileOnly("com.google.code.findbugs:jsr305:3.0.2")
    compileOnly("org.jetbrains:annotations:24.0.1")
}

tasks {
    jar {
        archiveBaseName.set("OpenLumin-neoforge-1.21.10")
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }

    processResources {
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE

        inputs.property("version", project.version)
        filesMatching("META-INF/neoforge.mods.toml") {
            expand("version" to project.version)
        }
    }

    named<Jar>("sourcesJar") {
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }
}
