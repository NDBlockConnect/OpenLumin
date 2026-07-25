plugins {
    id("fabric-loom") version "1.7-SNAPSHOT"
}

/**
 * fabric-1.21.10 中覆盖了 common 里的这些文件（使用 1.21.10 原生 API）。
 * 构建时需从 common srcDir 中排除这些文件，避免 "duplicate class" 错误。
 */
val overriddenCommonFiles = setOf(
    "io/github/openlumin/LuminTexture.java",
    "io/github/openlumin/LuminRenderSystem.java",
    "io/github/openlumin/shaders/FilterShader.java",
    "io/github/openlumin/shaders/FXAAShader.java",
    "io/github/openlumin/shaders/BlurShader.java",
    "io/github/openlumin/shaders/GlslSandBox.java",
    "io/github/openlumin/immediate/LuminImmediateRenderer.java",
    "io/github/openlumin/renderers/TextureRenderer.java",
    // 1.21.10 API 适配 override
    "io/github/openlumin/buffer/LuminRingBuffer.java",
    "io/github/openlumin/renderers/TriangleRenderer.java",
    "io/github/openlumin/text/ttf/TtfGlyphAtlas.java",
    "io/github/openlumin/text/minecraft/EpsilonFontGlyph.java",
    "io/github/openlumin/schedulers/render2d/Render2DScheduler.java",
    "io/github/openlumin/text/ttf/TtfTextRenderer.java",
    "io/github/openlumin/LuminRenderPipelines.java",
    "io/github/openlumin/schedulers/render3d/Render3DScheduler.java",
    "io/github/openlumin/text/SystemEmojiAtlas.java",
    // 1.21.10: GENERIC usage index 必须为 0
    "io/github/openlumin/LuminVertexFormats.java",
    // 1.21.10: 补充 RenderSystem.bindDefaultUniforms(pass)
    "io/github/openlumin/renderers/RoundRectRenderer.java",
    // 1.21.10: GpuBuffer 从 platform 包移至 buffers 包，且 1.21.10 使用 MC 原生 DynamicUniformStorage
    "io/github/openlumin/impl/DynamicUniformStorage.java",
)

sourceSets {
    main {
        java {
            // 直接把 common 源码纳入本模块，用原生 1.21.10 API 重新编译
            srcDir(project(":common").file("src/main/java"))
        }
    }
}

dependencies {
    minecraft("com.mojang:minecraft:1.21.10")
    mappings(loom.officialMojangMappings())
    modImplementation("net.fabricmc:fabric-loader:0.15.11")
    modImplementation("net.fabricmc.fabric-api:fabric-api:0.100.0+1.21")
    compileOnly("com.google.code.findbugs:jsr305:3.0.2")
    compileOnly("org.jetbrains:annotations:24.0.1")
}

tasks {
    compileJava {
        // 过滤掉 common 中被本模块覆盖的文件，避免 "duplicate class" 编译错误
        val commonSrcDir = project(":common").file("src/main/java").canonicalPath
        source = source.filter { file ->
            val normalized = file.canonicalPath.replace('\\', '/')
            val commonNorm = commonSrcDir.replace('\\', '/')
            if (!normalized.startsWith("$commonNorm/")) return@filter true
            val relPath = normalized.removePrefix("$commonNorm/")
            relPath !in overriddenCommonFiles
        }.asFileTree
    }

    jar {
        archiveBaseName.set("OpenLumin-fabric-1.21.10")
    }

    withType<org.gradle.jvm.tasks.Jar>().configureEach {
        // common srcDir 中被覆盖的文件会同时出现在 common 和本模块，导致 sourcesJar 重复
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }

    processResources {
        from(project(":common").sourceSets.main.get().resources)
        // 1.21.10 原生已有完整版本，排除仅为 NeoForge 1.21.4 提供的 MC namespace shader include
        // 否则会覆盖 MC 内建的 dynamictransforms.glsl（含 ColorModulator、ModelOffset 等 uniform），
        // 导致所有 MC 内建 shader 编译失败
        exclude("assets/minecraft/shaders/include/dynamictransforms.glsl")
        inputs.property("version", project.version)
        filesMatching("fabric.mod.json") {
            expand("version" to project.version)
        }
    }
}
