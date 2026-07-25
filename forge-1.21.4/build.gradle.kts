import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.tasks.bundling.DuplicatesStrategy

plugins {
    id("net.minecraftforge.gradle") version "6.0.24"
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

minecraft {
    mappings("official", "1.21.4")
}

// stubs sourceSet: 为 MC 新渲染 API 提供编译占位，不进入运行时
val stubs by sourceSets.creating {
    java.srcDirs("src/stubs/java")
    compileClasspath += sourceSets.main.get().compileClasspath
}
sourceSets.main.get().apply {
    compileClasspath = stubs.output + compileClasspath
}

dependencies {
    implementation(project(":common"))

    // Forge & Minecraft
    minecraft("net.minecraftforge:forge:1.21.4-54.0.27")

    // Annotations
    compileOnly("com.google.code.findbugs:jsr305:3.0.2")
    compileOnly("org.jetbrains:annotations:24.0.1")
}

tasks {
    named<ShadowJar>("shadowJar") {
        from(project(":common").sourceSets.main.get().output)
        archiveBaseName.set("OpenLumin-forge-1.21.4")
        configurations = listOf(project.configurations.runtimeClasspath.get())
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }

    processResources {
        from(project(":common").sourceSets.main.get().resources)
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE

        inputs.property("version", project.version)

        filesMatching("META-INF/mods.toml") {
            expand("version" to project.version)
        }
    }
}
