plugins {
    alias(libs.plugins.loom)
}

dependencies {
    minecraft(libs.minecraft)
    mappings(loom.officialMojangMappings())

    modImplementation(libs.fabric.loader)

    // include common project
    include(implementation(projects.common)!!)
}

tasks {
    processResources {
        inputs.property("version", project.version)
        filesMatching("fabric.mod.json") {
            expand("version" to project.version)
        }
    }

    jar {
        from("LICENSE") {
            rename { return@rename "${it}_stackdeobfuscator" }
        }
    }

    // no correctly set
    remapJar { archiveBaseName.set(jar.get().archiveBaseName) }
    remapSourcesJar { archiveBaseName.set(jar.get().archiveBaseName) }
}
