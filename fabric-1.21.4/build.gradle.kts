plugins {
    id("fabric-loom") version "1.7-SNAPSHOT"
}

// stubs sourceSet: 提供 1.21.6+ 新渲染 API（GpuTexture、RenderPass 等）的编译期占位。
// 与 NeoForge 1.21.4 模块共享同一套桩类保持源码同步。
val stubs by sourceSets.creating {
    java.srcDirs("src/stubs/java")
    compileClasspath += sourceSets.main.get().compileClasspath
}

// main sourceSet 编译时，stubs 必须排在 Minecraft 之前，
// 确保适配类（GpuTexture、RenderPass 等）先于 Fabric 的 Minecraft jar 生效
sourceSets.main.get().apply {
    compileClasspath = stubs.output + compileClasspath
}

dependencies {
    implementation(project(":common"))

    // Fabric
    minecraft("com.mojang:minecraft:1.21.4")
    mappings(loom.officialMojangMappings())
    modImplementation("net.fabricmc:fabric-loader:0.19.3")
    modImplementation("net.fabricmc.fabric-api:fabric-api:0.114.1+1.21.4")

    // Annotations
    compileOnly("com.google.code.findbugs:jsr305:3.0.2")
    compileOnly("org.jetbrains:annotations:24.0.1")
}

tasks {
    jar {
        from(project(":common").sourceSets.main.get().output)
        archiveBaseName.set("OpenLumin-fabric-1.21.4")
    }

    processResources {
        from(project(":common").sourceSets.main.get().resources)

        inputs.property("version", project.version)

        filesMatching("fabric.mod.json") {
            expand("version" to project.version)
        }
    }
}
