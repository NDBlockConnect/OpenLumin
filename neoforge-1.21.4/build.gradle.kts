plugins {
    id("net.neoforged.gradle.userdev") version "7.0.163"
}

configurations.all {
    resolutionStrategy.eachDependency {
        if (requested.group == "org.ow2.asm") {
            useVersion("9.7")
            because("NeoForge BootstrapLauncher requires ASM 9.7 on module path; 9.5 conflicts")
        }
    }
}

// stubs sourceSet: 仅编译时使用，不进入运行时模块路径，避免 JPMS 包冲突
val stubs by sourceSets.creating {
    java.srcDirs("src/stubs/java")
    compileClasspath += sourceSets.main.get().compileClasspath
}

// main sourceSet 编译时，stubs 必须排在 NeoForge 之前，
// 确保我们的适配类（BakedGlyph interface、RenderTarget 扩展方法等）优先生效
sourceSets.main.get().apply {
    compileClasspath = stubs.output + compileClasspath
}

dependencies {
    implementation(project(":common"))

    // NeoForge & Minecraft
    implementation("net.neoforged:neoforge:21.4.27-beta")

    // Annotations
    compileOnly("com.google.code.findbugs:jsr305:3.0.2")
    compileOnly("org.jetbrains:annotations:24.0.1")
}

tasks {
    // 将 common 模块的类复制到 main classes 目录，
    // 让 FML UserdevLocator 能在 openlumin 模块中找到所有公共类
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
        archiveBaseName.set("OpenLumin-neoforge-1.21.4")
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }

    processResources {
        from(project(":common").sourceSets.main.get().resources)
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE

        inputs.property("version", project.version)
        filesMatching("META-INF/neoforge.mods.toml") {
            expand("version" to project.version)
        }
    }
}
