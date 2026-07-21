plugins {
    id("net.neoforged.gradle.userdev") version "7.0.163"
}

dependencies {
    implementation(project(":common"))

    // NeoForge & Minecraft
    implementation("net.neoforged:neoforge:21.3.0-beta")

    // Annotations
    compileOnly("com.google.code.findbugs:jsr305:3.0.2")
    compileOnly("org.jetbrains:annotations:24.0.1")
}

tasks {
    jar {
        from(project(":common").sourceSets.main.get().output)
        archiveBaseName.set("OpenLumin-neoforge-1.21.3")
    }

    processResources {
        from(project(":common").sourceSets.main.get().resources)
    }
}
