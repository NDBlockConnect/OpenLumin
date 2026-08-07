plugins {
    id("net.neoforged.gradle.userdev") version "7.0.179"
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

version = "1.0.0"
group = "io.github.openlumin"

runs {
    create("client") {
        client()
        workingDirectory(project.file("run"))
        systemProperty("forge.logging.console.level", "debug")
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("net.neoforged:neoforge:21.10.64")

    // 依赖 OpenLumin 库（从父项目）
    implementation(files("../../neoforge-1.21.10/build/libs/OpenLumin-neoforge-1.21.10-1.0.0.jar"))
}

tasks.processResources {
    inputs.property("version", project.version)

    filesMatching("META-INF/neoforge.mods.toml") {
        expand("version" to project.version)
    }
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.release.set(21)
}

tasks.named<Jar>("jar") {
    archiveBaseName.set("openlumin-testmod-neoforge-1.21.10")
}
