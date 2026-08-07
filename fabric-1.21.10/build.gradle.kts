plugins {
    id("fabric-loom") version "1.7-SNAPSHOT"
}

// fabric-1.21.10 是 OpenGL 基底版本，包含完整业务代码 + 平台抽象实现
// 其他平台通过 Gradle sourceSets 复用此模块的代码

dependencies {
    minecraft("com.mojang:minecraft:1.21.10")
    mappings(loom.officialMojangMappings())
    modImplementation("net.fabricmc:fabric-loader:0.15.11")
    modImplementation("net.fabricmc.fabric-api:fabric-api:0.100.0+1.21")
    compileOnly("com.google.code.findbugs:jsr305:3.0.2")
    compileOnly("org.jetbrains:annotations:24.0.1")
}

tasks {
    jar {
        archiveBaseName.set("OpenLumin-fabric-1.21.10")
    }

    processResources {
        inputs.property("version", project.version)
        filesMatching("fabric.mod.json") {
            expand("version" to project.version)
        }
    }
}
