plugins {
    `java-library`
    `maven-publish`
}

group = "io.github.openlumin"
version = "1.0.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
    withSourcesJar()
}

repositories {
    mavenCentral()
    maven("https://maven.neoforged.net/releases") {
        name = "NeoForged"
    }
}

dependencies {
    // Minecraft dependencies (provided by the loader)
    compileOnly("net.minecraft:minecraft:1.21.4")
    compileOnly("com.mojang:blaze3d:1.0.0")

    // Annotations
    compileOnly("com.google.code.findbugs:jsr305:3.0.2")
    compileOnly("org.jetbrains:annotations:24.0.1")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.release = 21
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])

            pom {
                name = "OpenLumin"
                description = "A high-performance 2D/3D rendering library for Minecraft"
                url = "https://github.com/NDBlockConnect/OpenLumin"

                licenses {
                    license {
                        name = "GPL-3.0-only"
                        url = "https://www.gnu.org/licenses/gpl-3.0.html"
                    }
                }

                developers {
                    developer {
                        id = "ndblockconnect"
                        name = "NDBlockConnect Team"
                    }
                }

                scm {
                    connection = "scm:git:git://github.com/NDBlockConnect/OpenLumin.git"
                    developerConnection = "scm:git:ssh://github.com:NDBlockConnect/OpenLumin.git"
                    url = "https://github.com/NDBlockConnect/OpenLumin"
                }
            }
        }
    }
}
