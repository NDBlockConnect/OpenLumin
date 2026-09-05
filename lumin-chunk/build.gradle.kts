// WP-1：区块构建执行层 + Store 账本 + 遮挡剔除（纯 CPU，零 Minecraft 依赖，见 docs/design/WP1_chunk_engine.md）
// 根项目 subprojects {} 已提供 java-library / toolchain 21 / UTF-8 / maven-publish

tasks.register<JavaExec>("selfTest") {
    group = "verification"
    description = "Runs lumin-chunk M1+M2+M3 pure-CPU self tests (aggregated, no JUnit dependency)."
    classpath = sourceSets["test"].runtimeClasspath
    mainClass = "io.github.openlumin.chunk.LuminChunkM3SelfTest"
    maxHeapSize = "256m"
    jvmArgs("-XX:+UseSerialGC")
}
