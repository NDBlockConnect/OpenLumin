// WP-2 M1：Shaderpack 解析层（纯 CPU，零 Minecraft 依赖，见 docs/design/WP2_shaderpack_host.md）
// 根项目 subprojects {} 已提供 java-library / toolchain 21 / UTF-8 / maven-publish

tasks.register<JavaExec>("selfTest") {
    group = "verification"
    description = "Runs lumin-shaderpack M1..M2 + M6 capability + preprocessor self tests (aggregated, no JUnit dependency)."
    classpath = sourceSets["test"].runtimeClasspath
    mainClass = "io.github.openlumin.shaderpack.LuminPreprocessorSelfTest"
    maxHeapSize = "256m"
    jvmArgs("-XX:+UseSerialGC")
}
