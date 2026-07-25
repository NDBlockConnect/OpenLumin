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

dependencies {
    implementation(project(":common"))

    // NeoForge & Minecraft
    implementation("net.neoforged:neoforge:26.2.0")

    // Annotations
    compileOnly("com.google.code.findbugs:jsr305:3.0.2")
    compileOnly("org.jetbrains:annotations:24.0.1")
}

tasks {
    named("classes") {
        dependsOn(project(":common").tasks.named("classes"))
        doLast {
            copy {
                from(project(":common").sourceSets.main.get().output.classesDirs)
                into(sourceSets.main.get().output.classesDirs.singleFile)
                duplicatesStrategy = DuplicatesStrategy.EXCLUDE
            }
        }
    }

    jar {
        from(project(":common").sourceSets.main.get().output)
        archiveBaseName.set("OpenLumin-neoforge-26.2")
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }

    processResources {
        from(project(":common").sourceSets.main.get().resources)
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE

        // 排除 neoforge-1.21.4 专用的 dynamictransforms 桩，MC 26.2 自带完整版
        exclude("assets/minecraft/shaders/include/dynamictransforms.glsl")

        inputs.property("version", project.version)
        filesMatching("META-INF/neoforge.mods.toml") {
            expand("version" to project.version)
        }
    }
}
