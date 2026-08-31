// WP-1 M1：区块构建执行层（纯 CPU，零 Minecraft 依赖，见 docs/design/WP1_chunk_engine.md）
// 根项目 subprojects {} 已提供 java-library / toolchain 21 / UTF-8 / maven-publish

tasks.register<JavaExec>("selfTest") {
    group = "verification"
    description = "Runs lumin-chunk M1 pure-CPU self tests (no JUnit dependency)."
    classpath = sourceSets["test"].runtimeClasspath
    mainClass = "io.github.openlumin.chunk.LuminChunkM1SelfTest"
    maxHeapSize = "256m"
    jvmArgs("-XX:+UseSerialGC")
}
