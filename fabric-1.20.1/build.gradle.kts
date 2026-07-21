plugins {
    id("fabric-loom") version "1.7-SNAPSHOT"
}

dependencies {
    implementation(project(":common"))

    // Fabric
    minecraft("com.mojang:minecraft:1.20.1")
    mappings(loom.officialMojangMappings())
    modImplementation("net.fabricmc:fabric-loader:0.15.11")
    modImplementation("net.fabricmc.fabric-api:fabric-api:0.92.2+1.20.1")

    // Annotations
    compileOnly("com.google.code.findbugs:jsr305:3.0.2")
    compileOnly("org.jetbrains:annotations:24.0.1")
}

tasks {
    jar {
        from(project(":common").sourceSets.main.get().output)
        archiveBaseName.set("OpenLumin-fabric-1.20.1")
    }

    processResources {
        from(project(":common").sourceSets.main.get().resources)

        inputs.property("version", project.version)

        filesMatching("fabric.mod.json") {
            expand("version" to project.version)
        }
    }
}
