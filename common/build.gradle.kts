repositories {
    maven("https://maven.aliyun.com/repository/central")
    maven("https://maven.aliyun.com/repository/public")
    mavenCentral()
}

dependencies {
    // Annotations only - no Minecraft dependencies
    compileOnly("com.google.code.findbugs:jsr305:3.0.2")
    compileOnly("org.jetbrains:annotations:24.0.1")
    compileOnly("org.joml:joml:1.10.5")
}
