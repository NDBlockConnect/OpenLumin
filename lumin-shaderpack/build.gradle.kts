// WP-2 M1：Shaderpack 解析层（纯 CPU，零 Minecraft 依赖，见 docs/design/WP2_shaderpack_host.md）
// 根项目 subprojects {} 已提供 java-library / toolchain 21 / UTF-8 / maven-publish

dependencies {
    // 天象/阴影相机数学（与 MC 自身捆绑的库同源；MIT）
    implementation("org.joml:joml:1.10.8")
}

tasks.register<JavaExec>("selfTest") {
    group = "verification"
    description = "Runs lumin-shaderpack M1..M2 + M6 capability + preprocessor + M4 plan + translator + frame uniforms + UBO block self tests (aggregated, no JUnit dependency)."
    classpath = sourceSets["test"].runtimeClasspath
    mainClass = "io.github.openlumin.shaderpack.LuminUniformBlockSelfTest"
    maxHeapSize = "256m"
    jvmArgs("-XX:+UseSerialGC")
}
