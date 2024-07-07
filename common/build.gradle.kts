dependencies {
    api(libs.fabric.mappingio)

    // only int -> object maps required for fastutil
    compileOnly(libs.bundles.builtin)
}

tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("fabric.mod.json") {
        expand("version" to project.version)
    }
}
