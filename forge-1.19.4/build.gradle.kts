import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    id("net.minecraftforge.gradle") version "6.0.24"
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

dependencies {
    implementation(project(":common"))

    // Forge & Minecraft
    minecraft("net.minecraftforge:forge:1.19.4-45.2.0")

    // Annotations
    compileOnly("com.google.code.findbugs:jsr305:3.0.2")
    compileOnly("org.jetbrains:annotations:24.0.1")
}

tasks {
    named<ShadowJar>("shadowJar") {
        from(project(":common").sourceSets.main.get().output)
        archiveBaseName.set("OpenLumin-forge-1.19.4")
        configurations = listOf(project.configurations.runtimeClasspath.get())
    }

    processResources {
        from(project(":common").sourceSets.main.get().resources)

        inputs.property("version", project.version)

        filesMatching("META-INF/mods.toml") {
            expand("version" to project.version)
        }
    }
}
