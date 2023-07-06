dependencies {
    api(libs.fabric.mappingio)

    // only int -> object maps required for fastutil
    compileOnly(libs.bundles.builtin)
}
