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

// 注意：stubs sourceSet 仅用于 common 模块编译（common 在 build.gradle.kts 里引用 stubs.output）。
// neoforge-1.21.4/main 里所有 openlumin 源已经改用 io.github.openlumin.shim.<原FQN>，
// 不再需要 stubs 参与 main 编译。放弃 stubs 后不会有 JPMS 拆包冲突。
val stubs by sourceSets.creating {
    java.srcDirs("src/stubs/java")
    compileClasspath += sourceSets.main.get().compileClasspath
}

dependencies {
    implementation(project(":common"))

    // NeoForge & Minecraft
    implementation("net.neoforged:neoforge:21.4.157")

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
            // 注意：DuplicatesStrategy.EXCLUDE 只防止多个 from() 之间的重复，
            // 不保护目标目录中已存在的文件。必须用 eachFile 手动跳过已存在的文件，
            // 否则 common 的类（如 LuminRenderSystem）会覆盖平台专属版本。
            val destDir = sourceSets.main.get().output.classesDirs.singleFile
            copy {
                from(project(":common").sourceSets.main.get().output.classesDirs)
                into(destDir)
                eachFile {
                    if (File(destDir, this.relativePath.pathString).exists()) {
                        this.exclude()
                    }
                }
                includeEmptyDirs = false
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
