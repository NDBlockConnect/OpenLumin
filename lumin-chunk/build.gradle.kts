// WP-1：区块构建执行层 + Store 账本 + 遮挡剔除 + 半透明排序 + 构建调度（纯 CPU，零 Minecraft 依赖）
// 根项目 subprojects {} 已提供 java-library / toolchain 21 / UTF-8 / maven-publish

tasks.register<JavaExec>("selfTest") {
    group = "verification"
    description = "Runs lumin-chunk M1..M4b pure-CPU self tests (aggregated, no JUnit dependency)."
    classpath = sourceSets["test"].runtimeClasspath
    mainClass = "io.github.openlumin.chunk.LuminChunkM4bSelfTest"
    maxHeapSize = "256m"
    jvmArgs("-XX:+UseSerialGC")
}
