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

    // not correctly set
    remapJar { archiveBaseName.set(jar.get().archiveBaseName) }
    remapSourcesJar { archiveBaseName.set(jar.get().archiveBaseName) }
}

loom {
    mixin {
        defaultRefmapName.set("${rootProject.name.lowercase()}-refmap.json")
    }
}
