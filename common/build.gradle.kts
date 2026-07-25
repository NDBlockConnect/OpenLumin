plugins {
    id("net.neoforged.gradle.userdev") version "7.0.179"
}

configurations.all {
    resolutionStrategy.eachDependency {
        if (requested.group == "org.ow2.asm") {
            useVersion("9.7")
        }
    }
}

// stubs sourceSet：复用 neoforge-1.21.4 的适配层，仅用于编译，不进入运行时
val stubs by sourceSets.creating {
    java.srcDirs("${rootProject.projectDir}/neoforge-1.21.4/src/stubs/java")
    compileClasspath += sourceSets.main.get().compileClasspath
}

sourceSets.main.get().apply {
    compileClasspath = stubs.output + compileClasspath
}

dependencies {
    implementation("net.neoforged:neoforge:21.4.27-beta")

    // Annotations
    compileOnly("com.google.code.findbugs:jsr305:3.0.2")
    compileOnly("org.jetbrains:annotations:24.0.1")
}

