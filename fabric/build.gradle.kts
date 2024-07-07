plugins {
    alias(libs.plugins.loom)
}

dependencies {
    minecraft(libs.minecraft)
    mappings(loom.officialMojangMappings())

    modImplementation(libs.fabric.loader)

    // include common project
    include(implementation(projects.stackDeobfuscatorCommon)!!)

    // "include" doesn't include jars transitively
    include(libs.fabric.mappingio)
}

tasks {
    processResources {
        inputs.property("version", project.version)
        filesMatching("fabric.mod.json") {
            expand("version" to project.version)
        }
    }

    sequenceOf(remapJar, remapSourcesJar).forEach { task ->
        task {
            archiveBaseName = jar.get().archiveBaseName
            // this somehow errors when trying to just reuse
            // the destination directory set in normal jar task
            destinationDirectory = rootProject.layout.buildDirectory.dir("libs")
        }
    }
}

loom {
    mixin {
        defaultRefmapName.set("${rootProject.name.lowercase()}-refmap.json")
    }
}
