plugins {
    `java-library`
    `maven-publish`
}

group = "io.github.openlumin"
version = "1.0.0"

// Common configuration for all subprojects
subprojects {
    apply(plugin = "java-library")
    apply(plugin = "maven-publish")

    group = rootProject.group
    version = rootProject.version

    repositories {
        maven("https://maven.aliyun.com/repository/central")
        maven("https://maven.aliyun.com/repository/public")
        mavenCentral()
    }

    java {
        toolchain {
            languageVersion = JavaLanguageVersion.of(21)
        }
        withSourcesJar()
    }

    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
        options.release = 21
    }

    publishing {
        publications {
            create<MavenPublication>("maven") {
                from(components["java"])

                artifactId = "${rootProject.name}-${project.name}"

                pom {
                    name = "OpenLumin - ${project.name}"
                    description = "A high-performance 2D/3D rendering library for Minecraft ${project.name}"
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
}
